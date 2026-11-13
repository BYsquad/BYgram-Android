"""Convert an HTTPS extera-plugins.json index to a BYgram repository."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import tempfile
from urllib.parse import urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, build_opener
from zipfile import ZipFile

from port_extera_plugin import bygram_id, normalize_version, port, port_options


def https_url(url):
    parts = urlsplit(url)
    if parts.scheme != "https" or not parts.hostname or parts.username is not None or parts.password is not None or parts.fragment:
        raise ValueError("Repository downloads require HTTPS without credentials or fragments")
    return url


class HTTPSRedirects(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return super().redirect_request(request, fp, code, message, headers, https_url(newurl))


def download(url, limit):
    with build_opener(HTTPSRedirects).open(https_url(url), timeout=20) as response:
        data = response.read(limit + 1)
        location = response.geturl()
    if len(data) > limit:
        raise ValueError("Repository download exceeds size limit")
    return data, location


def convert(index_url, output, *, fetch=download, **options):
    raw_index, location = fetch(https_url(index_url), 1024 * 1024)
    index_url = https_url(location)
    index = json.loads(raw_index)
    if not isinstance(index, dict) or type(index.get("schema")) is not int or index["schema"] != 1:
        raise ValueError("Expected extera-plugins.json schema 1")
    entries = index.get("plugins")
    if not isinstance(entries, list) or len(entries) > 100:
        raise ValueError("BYgram repositories support up to 100 packages")
    output.mkdir(parents=True, exist_ok=True)
    result = []
    ids = set()
    with tempfile.TemporaryDirectory(prefix="bygram-repository-", dir=output) as directory:
        work = Path(directory)
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get("file"), str):
                raise ValueError("Invalid exteraGram repository entry")
            address = https_url(urljoin(index_url, entry["file"]))
            name = urlsplit(address).path.lower()
            suffix = next((ext for ext in (".elyx.zip", ".eaf.zip", ".elyx", ".eaf", ".plugin") if name.endswith(ext)), None)
            if suffix is None:
                raise ValueError(f"Unsupported plugin file: {entry['file']}")
            size, expected = entry.get("size"), entry.get("sha256")
            limit = (2 if suffix == ".plugin" else 10) * 1024 * 1024
            if type(size) is not int or not 0 < size <= limit or not isinstance(expected, str) or not re.fullmatch(r"[a-f0-9]{64}", expected):
                raise ValueError("Invalid package size or SHA-256 in repository")
            package_id = bygram_id(entry.get("id"), structured=suffix != ".plugin")
            if package_id in ids:
                raise ValueError(f"Duplicate repository plugin: {entry['id']}")
            ids.add(package_id)
            data, _ = fetch(address, size)
            if len(data) != size or hashlib.sha256(data).hexdigest() != expected:
                raise ValueError(f"Package hash or size mismatch: {entry['id']}")
            source = work / ("source" + suffix)
            source.write_bytes(data)
            target = work / "converted.byplugin"
            port(source, target, **options)
            with ZipFile(target) as archive:
                manifest = json.loads(archive.read("manifest.json"))
            if manifest["id"] != package_id or manifest["version"] != normalize_version(entry.get("version")):
                raise ValueError(f"Package identity or version mismatch: {entry['id']}")
            packed = target.read_bytes()
            digest = hashlib.sha256(packed).hexdigest()
            filename = digest + ".byplugin"
            target.replace(work / filename)
            result.append(dict(manifest=manifest, sha256=digest, size=len(packed), file=filename))
        index = work / "index.json"
        content = json.dumps(dict(format=1, plugins=result), ensure_ascii=False, indent=2)
        if len(content.encode("utf-8")) > 1024 * 1024:
            raise ValueError("BYgram index exceeds 1 MiB")
        index.write_text(content, encoding="utf-8")
        for entry in result:
            name = entry["file"]
            (work / name).replace(output / name)
        index.replace(output / "index.json")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("index_url")
    parser.add_argument("output", type=Path)
    port_options(parser)
    options = vars(parser.parse_args())
    convert(options.pop("index_url"), options.pop("output"), **options)
