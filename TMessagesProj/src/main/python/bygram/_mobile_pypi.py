"""Resolve pure-Python wheels for packages converted on the device."""
import hashlib
from importlib import metadata as installed_metadata
import json
import platform
import ssl
from time import monotonic
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, HTTPSHandler, build_opener

import certifi
from packaging.requirements import Requirement
from packaging.specifiers import SpecifierSet
from packaging.utils import InvalidWheelFilename, canonicalize_name, parse_wheel_filename
from packaging.version import InvalidVersion, Version

from ._port_elyx import requirement_fields


def _https(url):
    parts = urlsplit(url)
    if parts.scheme != "https" or parts.netloc not in {"pypi.org", "files.pythonhosted.org"}:
        raise ValueError("InvalidPyPIUrl")
    return url


class _Redirects(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return super().redirect_request(request, fp, code, message, headers, _https(newurl))


def _get(opener, url, limit, deadline):
    if monotonic() >= deadline:
        raise TimeoutError("PyPIResolutionTimeout")
    with opener.open(_https(url), timeout=8) as response:
        data = bytearray()
        while True:
            if monotonic() >= deadline:
                raise TimeoutError("PyPIResolutionTimeout")
            block = response.read1(64 * 1024)
            if not block:
                return bytes(data)
            data.extend(block)
            if len(data) > limit:
                raise ValueError("PyPIDownloadLimitExceeded")


def _requirements(values, extras=("",)):
    result = []
    for value in values:
        item = Requirement(value.strip())
        if item.url:
            raise ValueError("PyPIDirectUrlUnsupported")
        if item.marker is None or any(item.marker.evaluate({"extra": extra}) for extra in extras):
            result.append(item)
    return result


def _wheel(name, version, releases, python):
    for entry in releases:
        if entry.get("yanked") or entry.get("packagetype") != "bdist_wheel":
            continue
        if entry.get("requires_python") and not SpecifierSet(entry["requires_python"]).contains(python):
            continue
        filename = entry["filename"]
        if "/" in filename or "\\" in filename or ":" in filename:
            continue
        try:
            wheel_name, wheel_version, _, tags = parse_wheel_filename(filename)
        except InvalidWheelFilename:
            continue
        if wheel_name == name and wheel_version == version and any(
            tag.interpreter in {"py3", "py311"} and tag.abi == "none" and tag.platform == "any" for tag in tags
        ):
            return entry
    return None


def resolve(requirements, destination, *, fetch=None):
    roots = _requirements(requirement_fields(requirements))
    deadline = monotonic() + 45
    if fetch is None:
        opener = build_opener(_Redirects, HTTPSHandler(context=ssl.create_default_context(cafile=certifi.where())))
        fetch = lambda url, limit: _get(opener, url, limit, deadline)
    python = Version(platform.python_version())
    projects, versions = {}, {}
    attempts = 0
    bundled = {}
    for name in ("packaging", "pyyaml", "certifi", "beautifulsoup4", "requests", "lxml", "pillow"):
        try:
            bundled[name] = Version(installed_metadata.version(name))
        except installed_metadata.PackageNotFoundError:
            pass

    def metadata(name, version=None):
        suffix = "" if version is None else "/" + quote(str(version), safe="")
        return json.loads(fetch(f"https://pypi.org/pypi/{quote(name, safe='')}{suffix}/json", 8 * 1024 * 1024))

    def candidates(name, constraints):
        if name in bundled and all(not requirement.extras and requirement.specifier.contains(bundled[name]) for requirement in constraints):
            yield bundled[name], None
        if name not in projects:
            available = []
            for raw, files in metadata(name)["releases"].items():
                try:
                    version = Version(raw)
                except InvalidVersion:
                    continue
                wheel = _wheel(name, version, files, python)
                if wheel:
                    available.append((version, wheel))
            projects[name] = sorted(available, key=lambda pair: pair[0], reverse=True)
        yield from ((version, wheel) for version, wheel in projects[name]
                    if all(requirement.specifier.contains(version) for requirement in constraints))

    def solve(selected):
        nonlocal attempts
        needs = {}
        pending = list(roots)
        while pending:
            requirement = pending.pop()
            name = canonicalize_name(requirement.name)
            constraints = needs.setdefault(name, set())
            if requirement in constraints:
                continue
            constraints.add(requirement)
            if len(needs) > 24:
                raise ValueError("PyPIDependencyLimitExceeded")
            if name in selected:
                version, wheel = selected[name]
                if not requirement.specifier.contains(version) or wheel is None and requirement.extras:
                    return None
                extras = ("", *requirement.extras)
                pending.extend(_requirements(versions[name, version, wheel is None], extras))
        unresolved = next((name for name in needs if name not in selected), None)
        if unresolved is None:
            return selected
        for version, wheel in candidates(unresolved, needs[unresolved]):
            attempts += 1
            if attempts > 96:
                raise ValueError("PyPIResolutionLimitExceeded")
            key = unresolved, version, wheel is None
            if key not in versions:
                versions[key] = () if wheel is None else metadata(unresolved, version)["info"].get("requires_dist") or ()
            result = solve({**selected, unresolved: (version, wheel)})
            if result is not None:
                return result
        return None

    selected = solve({})
    if selected is None:
        raise ValueError("PyPIDependenciesUnresolved")
    budget = 10 * 1024 * 1024
    for _, wheel in selected.values():
        if wheel is None:
            continue
        size = wheel["size"]
        if type(size) is not int or not 0 < size <= budget:
            raise ValueError("PyPIDownloadLimitExceeded")
        data = fetch(wheel["url"], size)
        if len(data) != size or hashlib.sha256(data).hexdigest() != wheel["digests"]["sha256"]:
            raise ValueError("PyPIWheelHashMismatch")
        (destination / wheel["filename"]).write_bytes(data)
        budget -= size
