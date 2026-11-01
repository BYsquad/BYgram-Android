"""Build a curated static catalog. Publish packages first, index.json last."""
import hashlib
import json
import base64
import zipfile
from pathlib import Path
from package_plugin import package

ROOT = Path(__file__).resolve().parents[1]


def build(output, signing_key=None):
    output.mkdir(parents=True, exist_ok=True)
    entries = []
    for name in ("hello", "theme", "events", "accounts", "hooks", "composer"):
        source = ROOT / "docs/plugins/examples" / name
        temporary = output / (name + ".byplugin")
        package(source, temporary, signing_key)
        data = temporary.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        target = output / (digest + ".byplugin")
        temporary.replace(target)
        manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
        entry = dict(manifest=manifest, sha256=digest, size=len(data), file=target.name)
        with zipfile.ZipFile(target) as archive:
            if "signature.json" in archive.namelist():
                envelope = json.loads(archive.read("signature.json"))
                entry["author_key"] = hashlib.sha256(base64.b64decode(envelope["public_key"], validate=True)).hexdigest()
        entries.append(entry)
    index = dict(format=1, plugins=entries)
    pending = output / "index.json.new"
    pending.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")
    pending.replace(output / "index.json")
    return index


if __name__ == "__main__":
    build(ROOT / "server/site/plugins")
