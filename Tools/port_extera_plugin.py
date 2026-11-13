"""Wrap exteraGram source plugins in a BYgram package without executing them."""
import argparse
import json
from pathlib import Path
import sys
import tempfile

from package_plugin import package

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/python"))
from bygram._port_extera import bygram_id, normalize_version, port_options, prepare_source


def port(source, output, *, signing_key=None, resolve_pypi=False, hook_manifest=None, **capabilities):
    if output.suffix != ".byplugin" or source.resolve() == output.resolve():
        raise ValueError("Output must be a separate .byplugin file")
    with tempfile.TemporaryDirectory(prefix="bygram-port-") as directory:
        root = Path(directory)
        manifest = prepare_source(source, root, resolve_pypi=resolve_pypi, **capabilities)
        if hook_manifest is not None:
            configuration = json.loads(hook_manifest.read_text(encoding="utf-8"))
            for field in ("bygram", "telegram", "hook_targets"):
                manifest[field] = configuration[field]
            manifest["requires_hook_backend"] = True
            manifest["capabilities"].append("hooks.raw")
            (root / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
        package(root, output, signing_key)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    port_options(parser)
    options = vars(parser.parse_args())
    port(options.pop("source"), options.pop("output"), **options)
