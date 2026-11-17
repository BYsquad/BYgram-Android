"""Exercise real signed profile-background routes against a disposable local MySQL database."""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import itertools

ROOT = Path(__file__).resolve().parents[1]
PHP = shutil.which("php")
DATABASE = "bygram_background_test_" + str(os.getpid())

with tempfile.TemporaryDirectory(prefix="bygram-background-api-") as directory:
    work = Path(directory)
    shutil.copytree(ROOT / "server/activity-api", work / "api", ignore=shutil.ignore_patterns("config.php"))
    # This suite deliberately never reads production config.php.
    config = {"db_dsn": f"mysql:host=127.0.0.1;port=3306;dbname={DATABASE};charset=utf8mb4", "db_user": "root", "db_password": "root", "bot_token": "local-test-token", "bot_username": "BygramTestBot", "manager_username": "BygramTestManager"}
    (work / "config.json").write_text(json.dumps(config))
    (work / "api/config.php").write_text("<?php return json_decode(file_get_contents(__DIR__ . '/../config.json'), true);")
    (work / "openssl.cnf").write_text("[req]\ndistinguished_name=req_dn\n[req_dn]\n")
    setup = work / "setup.php"
    setup.write_text("""<?php
    $pdo = new PDO('mysql:host=127.0.0.1;port=3306', 'root', 'root', [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
    $database = $argv[1];
    if (!preg_match('/^bygram_background_test_[0-9]+$/D', $database)) exit(1);
    if (($argv[2] ?? '') === 'drop') { $pdo->exec("DROP DATABASE IF EXISTS `$database`"); exit; }
    $pdo->exec("CREATE DATABASE `$database` CHARACTER SET utf8mb4"); $pdo->exec("USE `$database`");
    $pdo->exec(file_get_contents(__DIR__ . '/api/schema.sql'));
    $key=openssl_pkey_new(['private_key_bits'=>2048,'private_key_type'=>OPENSSL_KEYTYPE_EC,'curve_name'=>'prime256v1','config'=>__DIR__.'/openssl.cnf']);
    openssl_pkey_export($key, $private, null, ['config'=>__DIR__.'/openssl.cnf']); file_put_contents(__DIR__.'/key.pem', $private);
    $public=openssl_pkey_get_details($key)['key'];
    $public=preg_replace('/-----[^-]+-----|\\s/', '', $public);
    $pdo->prepare('INSERT INTO typing_devices (telegram_id,key_fingerprint,public_key) VALUES (123,?,?)')->execute([str_repeat('a',64),$public]);
    $pdo->exec('INSERT INTO users (telegram_id) VALUES (123)');
    $pdo->prepare('INSERT INTO licensed_devices (id,telegram_id,key_fingerprint,public_key,device_name) VALUES (?,123,?,?,?)')
        ->execute([str_repeat('a',32),hash('sha256',base64_decode($public)),$public,'Verified fixture']);
    $pdo->prepare('INSERT INTO activity_sessions (telegram_id,device_id,access_hash,refresh_hash,access_expires_at,refresh_expires_at) VALUES (123,?,?,?,UTC_TIMESTAMP()+INTERVAL 1 HOUR,UTC_TIMESTAMP()+INTERVAL 1 DAY)')
        ->execute([str_repeat('a',32),hash('sha256','test-access'),hash('sha256','test-refresh')]);
    $pdo->exec('ALTER TABLE licensed_devices DROP COLUMN account_confirmed');
    $pdo->exec('ALTER TABLE typing_devices DROP COLUMN account_confirmed');
    require __DIR__.'/api/migrations/20261024_access_policy.php';
    require __DIR__.'/api/migrations/20261024_access_policy.php';
    if ((int)$pdo->query('SELECT account_confirmed FROM licensed_devices WHERE telegram_id=123')->fetchColumn() !== 1) throw new RuntimeException('Legacy device lost confirmation');
    """)
    signer = work / "sign.php"
    signer.write_text("<?php openssl_sign(file_get_contents($argv[1]), $signature, file_get_contents(__DIR__.'/'.($argv[2]??'key.pem')), OPENSSL_ALGO_SHA256); echo base64_encode($signature);")
    fixture = work / "fixture.php"
    fixture.write_text("""<?php
    require __DIR__.'/api/bootstrap.php'; $pdo=db();
    switch ($argv[1]) {
      case 'reset':
        $pdo->exec('UPDATE licensed_devices SET revoked_at=NULL,account_confirmed=1 WHERE telegram_id=123');
        $pdo->exec('UPDATE typing_devices SET account_confirmed=1 WHERE telegram_id=123');
        $pdo->exec('UPDATE activity_sessions SET revoked_at=NULL,access_expires_at=UTC_TIMESTAMP()+INTERVAL 1 HOUR WHERE telegram_id=123');break;
      case 'revoke':
        $pdo->exec('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=123');
        $pdo->exec('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=123');break;
      case 'unconfirm':
        $pdo->exec('UPDATE licensed_devices SET account_confirmed=0 WHERE telegram_id=123');
        $pdo->exec('UPDATE typing_devices SET account_confirmed=0 WHERE telegram_id=123');break;
      case 'unbound':
        if ((int)$pdo->query('SELECT COUNT(*) FROM activity_sessions WHERE telegram_id=456 AND device_id IS NULL')->fetchColumn() < 2
            || (int)$pdo->query('SELECT COUNT(*) FROM licensed_devices WHERE telegram_id=456')->fetchColumn()
            || (int)$pdo->query('SELECT COUNT(*) FROM typing_devices WHERE telegram_id=456')->fetchColumn()) exit(2);
        break;
      case 'expire':
        $pdo->exec('UPDATE activity_sessions SET access_expires_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE telegram_id=123');break;
      case 'limits': $pdo->exec('DELETE FROM api_rate_limits');break;
      default: exit(1);
    }
    """)
    try:
        subprocess.run([PHP, str(setup), DATABASE], check=True, capture_output=True)
    except subprocess.CalledProcessError as error:
        subprocess.run([PHP, str(setup), DATABASE, "drop"], check=True, capture_output=True)
        raise RuntimeError(error.stderr.decode(errors="replace")) from error
    server = None
    try:
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        server = subprocess.Popen([PHP, "-S", f"127.0.0.1:{port}", "-t", str(work / "api")], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        for _ in range(100):
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.1): break
            except OSError: time.sleep(0.05)
        base = f"http://127.0.0.1:{port}/"

        def request(payload, signed=False, headers=None, key_name="key.pem"):
            body = json.dumps(payload, separators=(",", ":")).encode()
            headers = dict(headers or {"Content-Type": "application/json"})
            if signed:
                timestamp, nonce = str(int(time.time())), str(uuid.uuid4())
                canonical = f"POST\n/\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}"
                (work / "canonical.txt").write_bytes(canonical.encode())
                signature = subprocess.check_output([PHP, str(signer), str(work / "canonical.txt"), key_name]).decode()
                headers.update({"X-BYgram-Timestamp": timestamp, "X-BYgram-Nonce": nonce, "X-BYgram-Signature": signature})
            try:
                response = urllib.request.urlopen(urllib.request.Request(base, data=body, headers=headers), timeout=5)
            except urllib.error.HTTPError as error: response = error
            with response: return response.status, json.load(response), headers

        put = {"action": "profile_background_put", "user_id": 123, "image": "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"}
        assert request(put)[0] == 401
        assert request(dict(put, user_id=456), signed=True)[0] == 401
        status, response, headers = request(put, signed=True)
        assert status == 200 and response["ok"], (status, response)
        assert request(put, headers=headers)[0] == 409, "Replayed upload accepted"
        status, response, _ = request({"action": "profile_background_get", "owner_id": 123})
        image = base64.b64decode(put["image"])
        assert status == 200 and response["revision"] == hashlib.sha256(image).hexdigest() and response["mime"] == "image/gif"
        with urllib.request.urlopen(base + "profile-background.php?user_id=123&v=" + response["revision"]) as downloaded:
            assert downloaded.read() == image and downloaded.headers["Content-Type"] == "image/gif"
        assert request(dict(put, image=base64.b64encode(b"<svg/>").decode()), signed=True)[0] == 422
        assert request(dict(put, image=""), signed=True)[0] == 200
        assert request({"action": "profile_background_get", "owner_id": 123})[1]["revision"] == ""
        print("PASS: real HTTP signatures, owner isolation, nonce replay, GIF upload/download, invalid image and deletion")
        auth = {"Content-Type": "application/json", "Authorization": "Bearer test-access"}
        status, response, _ = request({"action": "license"}, signed=True, headers=auth)
        assert status == 200 and response["licensed"], (status, response)
        assert request({"action": "license"}, headers=auth)[0] == 401, "Legacy request must still use a signature"
        assert request({"action": "license"}, signed=True)[0] == 401, "Legacy request must still use a session"
        def configure(**values):
            config.update(values)
            (work / "config.json").write_text(json.dumps(config))

        def mutate(action):
            subprocess.run([PHP, str(fixture), action], check=True, capture_output=True)

        for confirmation, signatures, revocation in itertools.product([False, True], repeat=3):
            configure(account_confirmation_enabled=confirmation, request_signature_checks_enabled=signatures, device_revocation_checks_enabled=revocation)
            mutate("reset")
            assert request({"action": "license"}, signed=True, headers=auth)[0] == 200
            assert request({"action": "license"}, headers=auth)[0] == (401 if signatures else 200)
            mutate("revoke")
            assert request({"action": "license"}, signed=True, headers=auth)[0] == (401 if revocation else 200)
            mutate("reset")
            mutate("unconfirm")
            assert request({"action": "license"}, signed=True, headers=auth)[0] == (401 if confirmation else 200)
        print("PASS: all eight combinations of confirmation, signatures and revocation")

        configure(account_confirmation_enabled=False, request_signature_checks_enabled=False, device_revocation_checks_enabled=False)
        mutate("reset")
        assert request({"action": "license"})[0] == 401, "Bearer token is still required"
        assert request({"action": "license"}, headers=auth)[0] == 200
        assert request({"action": "license"}, headers=auth)[0] == 200, "Unsigned requests must not collide on an empty nonce"
        mutate("expire")
        assert request({"action": "license"}, headers=auth)[0] == 401, "Expiry must remain enforced"
        mutate("reset")
        assert request({"action": "typing_put", "user_id": 999, "statuses": {"0": "test"}})[0] == 200
        assert request({"action": "typing_put", "user_id": 0, "statuses": {}})[0] == 400
        assert request({"action": "typing_put", "user_id": 999, "statuses": {"0": "a" * 65}})[0] == 400
        assert request(dict(put, user_id=999))[0] == 200

        mutate("limits")
        old = request({"action": "device_start", "user_id": 456})[1]
        assert old["state"] == "pending_proof"
        older_session = request({"action": "device_prove", "activation": old["activation"], "device_name": "Ignored"})[1]
        session = request({"action": "activity_start", "user_id": 456})[1]
        assert session["access_token"] != older_session["access_token"]
        auth = {"Authorization": "Bearer " + session["access_token"], "Content-Type": "application/json"}
        assert request({"action": "activity_get"}, headers=auth)[0] == 200
        assert request({"action": "refresh", "refresh_token": session["refresh_token"]})[0] == 200
        assert request({"action": "activity_get"}, headers={**auth, "Authorization": "Bearer " + older_session["access_token"]})[0] == 200
        mutate("unbound")

        configure(account_confirmation_enabled=True, request_signature_checks_enabled=True)
        assert request({"action": "activity_start", "user_id": 456})[0] == 401
        start = request({"action": "account_login_start", "user_id": 456})[1]
        exchange = {"action": "account_login_exchange", "user_id": 456, "exchange_token": start["exchange_token"]}
        assert request(exchange)[1] == {"ok": True, "expired": False}
        target = base + start["auth_url"].split("/")[-1]
        identity = {"id": 456, "first_name": "Account fixture", "auth_date": int(time.time())}
        payload = "\n".join(f"{key}={value}" for key, value in sorted(identity.items())).encode()
        identity["hash"] = hmac.new(hashlib.sha256(config["bot_token"].encode()).digest(), payload, "sha256").hexdigest()
        assert json.load(urllib.request.urlopen(urllib.request.Request(target, data=json.dumps(identity).encode())))["ok"]
        token = request(exchange)[1]["account_token"]
        assert request(exchange)[1]["expired"]
        flame = {"action": "flame_get", "user_id": 456, "peer_id": 789}
        auth = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
        assert request(flame)[0] == 401
        assert request(dict(flame, user_id=457), headers=auth)[0] == 401
        assert request(flame, headers=auth)[0] == 200
        assert request({"action": "activity_start", "user_id": 456}, headers=auth)[1]["access_token"]
        mutate("unbound")
        print("PASS: independent account sessions, one-time exchange, account isolation, no device records")
    finally:
        if server:
            server.terminate()
            server.wait(timeout=5)
        subprocess.run([PHP, str(setup), DATABASE, "drop"], check=True, capture_output=True)
