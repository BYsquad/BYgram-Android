"""Exercise real signed profile-background routes against a disposable local MySQL database."""
import base64
import hashlib
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

ROOT = Path(__file__).resolve().parents[1]
PHP = shutil.which("php")
DATABASE = "bygram_background_test_" + str(os.getpid())

with tempfile.TemporaryDirectory(prefix="bygram-background-api-") as directory:
    work = Path(directory)
    shutil.copytree(ROOT / "server/activity-api", work / "api", ignore=shutil.ignore_patterns("config.php"))
    # This suite deliberately never reads production config.php.
    config = {"db_dsn": f"mysql:host=127.0.0.1;port=3306;dbname={DATABASE};charset=utf8mb4", "db_user": "root", "db_password": "root"}
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
    """)
    signer = work / "sign.php"
    signer.write_text("<?php openssl_sign(file_get_contents($argv[1]), $signature, file_get_contents(__DIR__.'/key.pem'), OPENSSL_ALGO_SHA256); echo base64_encode($signature);")
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

        def request(payload, signed=False, headers=None):
            body = json.dumps(payload, separators=(",", ":")).encode()
            headers = dict(headers or {"Content-Type": "application/json"})
            if signed:
                timestamp, nonce = str(int(time.time())), str(uuid.uuid4())
                canonical = f"POST\n/\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}"
                (work / "canonical.txt").write_bytes(canonical.encode())
                signature = subprocess.check_output([PHP, str(signer), str(work / "canonical.txt")]).decode()
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
    finally:
        if server:
            server.terminate()
            server.wait(timeout=5)
        subprocess.run([PHP, str(setup), DATABASE, "drop"], check=True, capture_output=True)
