"""Package source files without importing plugin code. The Android host validates the result."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
import shutil
import subprocess
import tempfile
import base64


def package(source: Path, output: Path, signing_key: Path | None = None):
    source = source.resolve()
    if output.resolve().is_relative_to(source):
        raise ValueError("Output must be outside the source directory")
    if signing_key is not None and signing_key.resolve().is_relative_to(source):
        raise ValueError("The private signing key must be outside the plugin source directory")
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("manifest_version") != 1:
        raise ValueError("Unsupported manifest version")
    paths = sorted(source.rglob("*"))
    files = []
    for file in paths:
        if file.is_symlink(): raise ValueError("Symlink: " + str(file))
        if not file.is_file(): continue
        if "__pycache__" in file.parts or file.suffix.lower() in {".pyc", ".pyo"}: continue
        if file.suffix.lower() in {".so", ".pyd", ".dll", ".jar", ".dex", ".class", ".exe", ".apk", ".aar"}:
            raise ValueError("Unsupported file: " + str(file))
        if file.stat().st_size > 5 * 1024 * 1024: raise ValueError("File too large")
        files.append(file)
    if len(files) > 512 or sum(file.stat().st_size for file in files) > 30 * 1024 * 1024:
        raise ValueError("Package limit exceeded")
    contents = {file.relative_to(source).as_posix(): file.read_bytes() for file in files}
    if signing_key is not None:
        if "signature.json" in contents: raise ValueError("Remove the old signature.json before signing")
        openssl = shutil.which("openssl")
        if openssl is None and shutil.which("git"):
            bundled = Path(shutil.which("git")).resolve().parent.parent / "usr/bin/openssl.exe"
            if bundled.is_file(): openssl = str(bundled)
        if openssl is None: raise RuntimeError("OpenSSL is required to sign a plugin")
        payload = b"BYGRAM-PLUGIN-SIGNATURE-V1\n" + b"".join(
            f"{hashlib.sha256(contents[name]).hexdigest()} {len(contents[name])} {name}\n".encode("utf-8")
            for name in sorted(contents, key=lambda name: name.encode("utf-8")))
        with tempfile.TemporaryDirectory(prefix="bygram-sign-") as temporary:
            work = Path(temporary)
            (work / "payload").write_bytes(payload)
            key = subprocess.check_output([openssl, "pkey", "-in", str(signing_key.resolve()), "-pubout", "-outform", "DER"], stderr=subprocess.PIPE)
            subprocess.run([openssl, "dgst", "-sha256", "-sign", str(signing_key.resolve()), "-out", str(work / "signature"), str(work / "payload")], check=True, capture_output=True)
            contents["signature.json"] = json.dumps(dict(format=1, public_key=base64.b64encode(key).decode("ascii"),
                signature=base64.b64encode((work / "signature").read_bytes()).decode("ascii")), separators=(",", ":")).encode("utf-8")
        if len(contents) > 512 or sum(map(len, contents.values())) > 30 * 1024 * 1024: raise ValueError("Signed package limit exceeded")
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, data in contents.items():
                info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data)
        if output.stat().st_size > 10 * 1024 * 1024: raise ValueError("ZIP too large")
    except BaseException:
        output.unlink(missing_ok=True)
        raise
    print(f"{output}: SHA-256 {hashlib.sha256(output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--signing-key", type=Path, help="Author RSA private PEM key, kept outside plugin source")
    args = parser.parse_args()
    package(args.source, args.output, args.signing_key)
