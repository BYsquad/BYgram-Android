"""Exercise account-authenticated Activity API routes against a disposable local database."""
import base64
import hashlib
import hmac
import json
import os
import re
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
PHP = shutil.which("php")
DATABASE = "bygram_background_test_" + str(os.getpid())
DB_HOST = os.environ.get("BYGRAM_TEST_DB_HOST", "127.0.0.1")

with tempfile.TemporaryDirectory(prefix="bygram-background-api-") as directory:
    work = Path(directory)
    shutil.copytree(ROOT / "server/activity-api", work / "api", ignore=shutil.ignore_patterns("config.php"))
    # This suite deliberately never reads production config.php.
    config = {"db_dsn": f"mysql:host={DB_HOST};port=3306;dbname={DATABASE};charset=utf8mb4", "db_user": "root", "db_password": "root", "bot_token": "local-test-token", "bot_username": "BygramTestBot", "manager_username": "BygramTestManager", "account_mini_app": "bygram", "plugin_store_enabled": True,
              "plugin_store_url": "https://checkout.example.invalid/api"}
    (work / "config.json").write_text(json.dumps(config))
    (work / "api/config.php").write_text("<?php return json_decode(file_get_contents(__DIR__ . '/../config.json'), true);")
    setup = work / "setup.php"
    setup.write_text("""<?php
    $host = getenv('BYGRAM_TEST_DB_HOST') ?: '127.0.0.1';
    $pdo = new PDO('mysql:host=' . $host . ';port=3306', 'root', 'root', [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
    $database = $argv[1];
    if (!preg_match('/^bygram_background_test_[0-9]+$/D', $database)) exit(1);
    if (($argv[2] ?? '') === 'drop') { $pdo->exec("DROP DATABASE IF EXISTS `$database`"); exit; }
    $pdo->exec("CREATE DATABASE `$database` CHARACTER SET utf8mb4"); $pdo->exec("USE `$database`");
    $pdo->exec(file_get_contents(__DIR__ . '/api/schema.sql'));
    """)
    fixture = work / "fixture.php"
    fixture.write_text("""<?php
    require __DIR__.'/api/bootstrap.php'; $pdo=db();
    $pdo->exec('UPDATE activity_sessions SET access_expires_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND');
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

        def request(payload, token=None):
            headers = {"Content-Type": "application/json"}
            if token:
                headers["Authorization"] = f"Bearer {token}"
            data = json.dumps(payload).encode()
            try:
                response = urllib.request.urlopen(urllib.request.Request(base, data=data, headers=headers), timeout=5)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                return response.status, json.load(response)

        for action in ("device_start", "device_prove", "device_status", "device_replace"):
            assert request({"action": action, "user_id": 123})[0] == 404
        assert request({"action": "activity_start", "user_id": 123})[0] == 401
        start = request({"action": "account_login_start", "user_id": 123})[1]
        fields = {"auth_date": str(int(time.time())), "start_param": start["start_param"], "user": '{"id":123}'}
        secret = hmac.new(b"WebAppData", config["bot_token"].encode(), "sha256").digest()
        payload = "\n".join(f"{key}={value}" for key, value in sorted(fields.items()))
        fields["hash"] = hmac.new(secret, payload.encode(), "sha256").hexdigest()
        login = {"action": "account_login_webapp", "user_id": 123, "exchange_token": start["exchange_token"],
                 "init_data": urllib.parse.urlencode(fields)}
        assert request(dict(login, user_id=456))[1] == {"ok": False}
        assert request(dict(login, exchange_token="A"*43))[1] == {"ok": False}
        assert request(dict(login, init_data=login["init_data"] + "&auth_date=0"))[1] == {"ok": False}
        token = request(login)[1]["account_token"]
        assert request(login)[1] == {"ok": False}, "A login attempt must be consumed exactly once"
        put = {"action": "profile_background_put", "user_id": 123, "image": "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"}
        assert request(put)[0] == 401
        assert request(dict(put, user_id=456), token)[0] == 401
        assert request(put, token)[0] == 200
        image = base64.b64decode(put["image"])
        response = request({"action": "profile_background_get", "owner_id": 123})[1]
        assert response["revision"] == hashlib.sha256(image).hexdigest() and response["mime"] == "image/gif"
        with urllib.request.urlopen(base + "profile-background.php?user_id=123&v=" + response["revision"]) as downloaded:
            assert downloaded.read() == image
        assert request(dict(put, image="PHN2Zy8+"), token)[0] == 422
        assert request(dict(put, image=""), token)[0] == 200
        session = request({"action": "activity_start", "user_id": 123}, token)[1]
        assert request({"action": "activity_get"}, session["access_token"])[0] == 200
        subprocess.run([PHP, str(fixture)], check=True)
        assert request({"action": "activity_get"}, session["access_token"])[0] == 401
        refresh = request({"action": "refresh", "refresh_token": session["refresh_token"]})[1]
        assert request({"action": "activity_get"}, refresh["access_token"])[0] == 200
        assert request({"action": "store_author_portal", "user_id": 123})[0] == 401
        portal = request({"action": "store_author_portal", "user_id": 123}, token)[1]["url"]
        ticket = urllib.parse.parse_qs(urllib.parse.urlsplit(portal).fragment)["ticket"][0]
        with urllib.request.urlopen(base + "plugin-store-web.php?wallet=1") as page:
            csrf = re.search(r'csrf: "([0-9a-f]{64})"', page.read().decode())[1]
            cookie = page.headers["Set-Cookie"].split(";")[0]
        data = urllib.parse.urlencode({"ticket": ticket, "csrf": csrf}).encode()
        target = base + "plugin-store-web.php?wallet=1&action=portal_login"
        with urllib.request.urlopen(urllib.request.Request(target, data=data, headers={"Cookie": cookie})) as response:
            assert json.load(response)["ok"]
        try:
            urllib.request.urlopen(urllib.request.Request(target, data=data, headers={"Cookie": cookie}))
            raise AssertionError("Portal ticket reuse accepted")
        except urllib.error.HTTPError as error:
            assert error.code == 403
        print("PASS: removed routes, Mini App signature, account isolation, single-use login, background access, refresh and one-time author portal ticket")
    finally:
        if server:
            server.terminate()
            server.wait(timeout=5)
        subprocess.run([PHP, str(setup), DATABASE, "drop"], check=True, capture_output=True)
