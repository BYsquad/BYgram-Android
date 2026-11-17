"""Cross-check real OpenSSL/PHP/Android-Java signature validators without executing plugin code."""
from pathlib import Path
import base64
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import zipfile
from package_plugin import package

ROOT = Path(__file__).resolve().parents[1]


def main():
    openssl = shutil.which("openssl")
    if openssl is None:
        openssl = str(Path(shutil.which("git")).resolve().parent.parent / "usr/bin/openssl.exe")
    gson = next((Path.home() / ".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0").rglob("*.jar"))
    with tempfile.TemporaryDirectory(prefix="bygram-signature-check-") as temporary:
        work = Path(temporary)
        key = work / "author.pem"
        subprocess.run([openssl, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(key)], check=True, capture_output=True)
        source = work / "source"
        shutil.copytree(ROOT / "docs/plugins/examples/hello", source)
        local_key = source / "private.pem"
        shutil.copyfile(key, local_key)
        try:
            package(source, work / "unsafe.byplugin", local_key)
            raise AssertionError("Private signing key in source accepted")
        except ValueError:
            assert not (work / "unsafe.byplugin").exists()
        local_key.unlink()
        (source / "\ue000.txt").write_text("private-use", encoding="utf-8")
        (source / "\U00010000.txt").write_text("supplementary", encoding="utf-8")
        archive = work / "signed.byplugin"
        package(source, archive, key)
        with zipfile.ZipFile(archive) as signed:
            fingerprint = hashlib.sha256(base64.b64decode(json.loads(signed.read("signature.json"))["public_key"])).hexdigest()
        java = ROOT / "TMessagesProj/src/plugins/java/org/telegram/messenger/plugins"
        subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(gson), "-d", str(work),
            str(ROOT / "Tools/fixtures/plugins/android/util/AtomicFile.java"), str(ROOT / "Tools/fixtures/plugins/android/util/Base64.java"),
            *(str(java / name) for name in ("PluginManifest.java", "PluginSignature.java", "PluginPackage.java", "PluginPackageAccess.java", "PluginStore.java", "PluginSettingsFile.java")),
            str(ROOT / "Tools/PluginSignatureCheck.java")], check=True)
        result = subprocess.check_output(["java", "-ea", "-cp", str(work) + os.pathsep + str(gson),
            "org.telegram.messenger.plugins.PluginSignatureCheck", str(ROOT / "Tools/fixtures/plugins/hello-manifest.json"), str(archive)], text=True)
        assert "AUTHOR_KEY=" + fingerprint in result
        code = 'require $argv[1]; $z=new ZipArchive(); $z->open($argv[2]); echo pluginAuthorSignature($z);'
        result = subprocess.check_output(["php", "-r", code, str(ROOT / "server/activity-api/plugin-catalog.php"), str(archive)], text=True)
        assert result == fingerprint
        tampered = work / "tampered.byplugin"
        with zipfile.ZipFile(archive) as original, zipfile.ZipFile(tampered, "w") as changed:
            for entry in original.infolist(): changed.writestr(entry, b"changed" if entry.filename == "main.py" else original.read(entry))
        result = subprocess.run(["php", "-r", code, str(ROOT / "server/activity-api/plugin-catalog.php"), str(tampered)], capture_output=True)
        assert result.returncode != 0, "PHP accepted tampered package"
        print("PASS: Python/OpenSSL signing agrees with Java and PHP, Unicode byte ordering and PHP tamper rejection")


if __name__ == "__main__":
    main()
