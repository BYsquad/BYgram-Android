"""Apply Elyx developer events to a source archive before conversion."""
import base64
import hashlib
from io import BytesIO
import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

from ._port_elyx import ENVIRONMENT, VENDOR, MAX_FILE, MAX_TOTAL, relative_path

_INTERNAL = {ENVIRONMENT, VENDOR, "manifest.json", "signature.json"}


def _path(name):
    path = relative_path(name)
    if path.parts[0] in _INTERNAL:
        raise ValueError("ReservedElyxPath")
    return path.as_posix()


def _sources(root):
    root = Path(root)
    return {file.relative_to(root).as_posix(): file
            for file in root.rglob("*") if file.is_file() and file.relative_to(root).parts[0] not in _INTERNAL}


def compare(root, payload):
    payload = json.loads(payload)
    local = payload.get("files", payload.get("hashes", {}))
    remote = {name: hashlib.sha256(file.read_bytes()).hexdigest() for name, file in _sources(root).items()}
    changed = [name for name, value in local.items() if remote.get(_path(name)) != value]
    removed = sorted(remote.keys() - local.keys())
    return json.dumps(dict(success=True, hashes=remote, changed=changed, removed=removed, errors=[]))


def patch(root, payload):
    sources = _sources(root)
    events = json.loads(payload)["changes"]
    if not isinstance(events, list) or len(events) > 1024:
        raise ValueError("InvalidElyxChanges")
    for event in events:
        kind, path = event["#"], _path(event["path"])
        directory = event.get("is_directory", False)
        matches = [name for name in sources if name == path or directory and name.startswith(path + "/")]
        if kind == "deleted":
            for name in matches:
                del sources[name]
        elif kind == "moved":
            destination = _path(event["dest_path"])
            moving = {destination + name[len(path):]: sources[name] for name in matches}
            if not matches or moving.keys() & (sources.keys() - set(matches)):
                raise ValueError("InvalidElyxMove")
            for name in matches:
                del sources[name]
            sources.update(moving)
        elif kind in {"created", "modified"}:
            if not directory:
                value = base64.b64decode(event["content"], validate=True)
                if len(value) > MAX_FILE:
                    raise ValueError("PackageLimitExceeded")
                sources[path] = value
        else:
            raise ValueError("InvalidElyxChange")
    if len(sources) > 512 or sum(value.stat().st_size if isinstance(value, Path) else len(value)
                                 for value in sources.values()) > MAX_TOTAL:
        raise ValueError("PackageLimitExceeded")
    result = BytesIO()
    with ZipFile(result, "w", ZIP_DEFLATED) as archive:
        for name, value in sorted(sources.items()):
            archive.writestr(name, value.read_bytes() if isinstance(value, Path) else value)
    if result.tell() > 10 * 1024 * 1024:
        raise ValueError("PackageLimitExceeded")
    return result.getvalue()
