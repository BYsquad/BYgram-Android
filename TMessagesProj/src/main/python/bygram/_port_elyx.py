"""Source Elyx archive preparation and pure-Python dependency bundling."""
import ast
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
from zipfile import ZipFile

ENVIRONMENT = "_bygram_elyx.json"
VENDOR = "_bygram_elyx_vendor"
MAX_TOTAL = 30 * 1024 * 1024
MAX_FILE = 5 * 1024 * 1024
PYTHON_311_MAGIC = bytes.fromhex("a70d0d0a")
FORMATS = (".yaml", ".yml", ".json", ".py")


def relative_path(value):
    if not isinstance(value, str):
        raise ValueError("Expected a relative archive path")
    parts = value.split("/")
    if len(parts) > 12 or any(
        not part or part in {".", ".."} or part.endswith((" ", "."))
        or re.search(r'[\\:*?"<>|\x00-\x1f\x7f]', part)
        or re.fullmatch(r"(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?", part, re.I)
        for part in parts
    ):
        raise ValueError(f"Invalid archive path: {value!r}")
    return Path(*parts)


def unpack(archive, root, limit=MAX_TOTAL, *, wheel=False):
    if archive.stat().st_size > 10 * 1024 * 1024:
        raise ValueError("Archive exceeds 10 MiB")
    total = 0
    names = set()
    cases = {}
    with ZipFile(archive) as source:
        entries = source.infolist()
        if len(entries) > 512:
            raise ValueError("Archive contains more than 512 entries")
        for info in entries:
            raw_name = info.orig_filename
            name = raw_name.removesuffix("/") if info.is_dir() else raw_name
            path = relative_path(name)
            folded = name.casefold()
            if folded in names:
                raise ValueError(f"Duplicate archive entry: {name}")
            names.add(folded)
            for parent in (*path.parents, path):
                spelling = parent.as_posix()
                if cases.setdefault(spelling.casefold(), spelling) != spelling:
                    raise ValueError(f"Case collision: {name}")
            mode = stat.S_IFMT(info.external_attr >> 16)
            if mode not in {0, stat.S_IFREG, stat.S_IFDIR} or info.flag_bits & 1:
                raise ValueError(f"Unsupported archive entry: {name}")
            if path.suffix.lower() in {".pyo", ".so", ".pyd", ".dll", ".dex", ".class", ".jar", ".apk", ".aar", ".exe", ".pth"}:
                raise ValueError(f"Unsupported source or wheel file: {name}")
            if wheel and path.parts[0].endswith(".data"):
                raise ValueError("Wheels with a .data installation layout are unsupported")
            total += info.file_size
            if total > limit or info.file_size > MAX_FILE:
                raise ValueError("Archive exceeds BYgram package limits")
            target = root / path
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(info) as stream:
                    data = stream.read(MAX_FILE + 1)
                if len(data) != info.file_size:
                    raise ValueError(f"Invalid archive entry size: {name}")
                if path.suffix.lower() == ".pyc" and (len(data) < 16 or data[:4] != PYTHON_311_MAGIC):
                    raise ValueError(f"Expected Python 3.11 bytecode: {name}")
                with target.open("xb") as output:
                    output.write(data)
    return total


def read_mapping(path):
    if path.stat().st_size > 1024 * 1024:
        raise ValueError(f"Elyx metadata exceeds 1 MiB: {path.name}")
    text = path.read_text(encoding="utf-8-sig")
    if path.suffix == ".json":
        value = json.loads(text)
    elif path.suffix in {".yaml", ".yml"}:
        try:
            import yaml
        except ImportError as error:
            raise ValueError("YAML conversion requires PyYAML in the desktop Python environment") from error
        value = yaml.safe_load(text)
    elif path.suffix == ".py":
        value = {}
        for statement in ast.parse(text, filename=str(path)).body:
            if isinstance(statement, ast.Expr) and isinstance(statement.value, ast.Constant) and isinstance(statement.value.value, str):
                continue
            if not isinstance(statement, ast.Assign) or any(not isinstance(target, ast.Name) for target in statement.targets):
                raise ValueError(f"Expected literal Python declarations in {path.name}")
            literal = ast.literal_eval(statement.value)
            value.update((target.id, literal) for target in statement.targets)
    else:
        raise ValueError(f"Unsupported metadata format: {path.name}")
    if not isinstance(value, dict) or any(not isinstance(key, str) for key in value):
        raise ValueError(f"Expected a mapping with string keys in {path.name}")
    return value


def discovery(root, stem, suffixes):
    return next((stem + suffix for suffix in suffixes if (root / (stem + suffix)).is_file()), None)


def requirement_fields(requirements):
    if isinstance(requirements, str):
        # Commas before a version operator or within extras belong to the same requirement.
        return re.split(r",\s*(?=[A-Za-z0-9])(?=[^\[\],]*(?:\[|,|$))", requirements)
    if isinstance(requirements, (list, tuple)):
        return requirements
    raise ValueError("Invalid PyPI requirements")


def prepare_dependencies(root, requirements, *, resolve_pypi, wheels=None, resolver=None, keep_wheels=False):
    if requirements and not resolve_pypi:
        raise ValueError("Use --resolve-pypi to bundle dependencies")
    with tempfile.TemporaryDirectory(prefix="bygram-wheels-") as directory:
        downloads = Path(directory)
        if requirements and resolver is not None:
            resolver(requirements, downloads)
        elif requirements:
            from pip._vendor.packaging.requirements import Requirement
            parsed = [Requirement(field.strip()) for field in requirement_fields(requirements)]
            if any(requirement.url or requirement.marker for requirement in parsed):
                raise ValueError("Direct URLs and environment markers are unsupported in PyPI declarations")
            env = {key: value for key, value in os.environ.items() if not key.upper().startswith("PIP_")}
            env["PIP_CONFIG_FILE"] = os.devnull
            subprocess.run([sys.executable, "-m", "pip", "--isolated", "--disable-pip-version-check", "download",
                            "--index-url", "https://pypi.org/simple", "--only-binary=:all:", "--platform", "any",
                            "--python-version", "3.11", "--implementation", "py", "--abi", "none",
                            "--dest", str(downloads), *(str(requirement) for requirement in parsed)], check=True, env=env)
        archives = sorted(downloads.glob("*.whl"))
        if wheels is not None:
            archives += sorted(wheels.glob("*.whl"))
        if not archives:
            return
        vendor = root / VENDOR
        vendor.mkdir()
        budget = MAX_TOTAL - sum(path.stat().st_size for path in root.rglob("*") if path.is_file())
        for archive in archives:
            if not re.search(r"-py(?:2\.py3|3(?:11)?)-none-any\.whl$", archive.name):
                raise ValueError(f"Expected a universal Python 3 wheel: {archive.name}")
            with ZipFile(archive) as wheel_file:
                metadata = [entry for entry in wheel_file.infolist() if entry.filename.endswith(".dist-info/WHEEL")]
                if len(metadata) != 1 or metadata[0].file_size > 65536:
                    raise ValueError(f"Missing wheel metadata: {archive.name}")
                if not re.search(r"^Root-Is-Purelib:\s*true\s*$", wheel_file.read(metadata[0]).decode("utf-8"), re.I | re.M):
                    raise ValueError(f"Expected a pure-Python wheel: {archive.name}")
            budget -= unpack(archive, vendor, budget, wheel=True)
            if not keep_wheels and wheels is not None and archive.parent == wheels:
                archive.unlink()


def write_environment(root, refmap, metadata, locales):
    encoder = json.JSONEncoder(ensure_ascii=False, allow_nan=False)
    total = 0
    with (root / ENVIRONMENT).open("xb") as output:
        for chunk in encoder.iterencode(dict(refmap=refmap, metainfo=metadata, locales=locales)):
            raw = chunk.encode("utf-8")
            total += len(raw)
            if total > 1024 * 1024:
                raise ValueError("Elyx environment exceeds 1 MiB")
            output.write(raw)


def prepare_elyx(source, root, *, resolve_pypi, read_source, resolver=None, metadata_only=False, keep_wheels=False):
    if not metadata_only and not source.name.lower().endswith((".elyx", ".eaf", ".elyx.zip", ".eaf.zip")):
        raise ValueError("Expected .plugin, .elyx or .eaf")
    unpack(source, root)
    if any((root / name).exists() for name in (ENVIRONMENT, VENDOR, "manifest.json", "signature.json")):
        raise ValueError("Reserved BYgram archive entry")
    refmap_name = discovery(root, "refmap", FORMATS[:-1])
    refmap = read_mapping(root / refmap_name) if refmap_name else {}
    main = refmap.get("main", "main.py" if (root / "main.py").is_file() else "main.pyc")
    entry = root / relative_path(main)
    if entry.suffix not in {".py", ".pyc"} or not entry.is_file() or "__pycache__" in Path(main).parts:
        raise ValueError("Elyx entrypoint must be a Python 3.11 module")
    metadata, class_name = ({}, "_ElyxEntry") if entry.suffix == ".pyc" else read_source(entry.read_bytes(), entry)
    meta = refmap.get("metainfo", discovery(root, "metainfo", FORMATS[:-1]))
    if meta is not None:
        metadata.update({key.removeprefix("__").removesuffix("__"): value
                         for key, value in read_mapping(root / relative_path(meta)).items()})
    if "requires" in metadata:
        requires = metadata["requires"]
        if not isinstance(requires, dict) or len(requires) > 32:
            raise ValueError("Invalid Elyx plugin dependencies")
        for dependency, url in requires.items():
            if not re.fullmatch(r"[A-Za-z0-9_]{2,32}(?: \((?:0|[1-9][0-9]{0,9})(?:\.(?:0|[1-9][0-9]{0,9})){0,2}\))?", dependency) or not isinstance(url, str):
                raise ValueError("Invalid Elyx plugin dependency")
    for key in ("assets", "wheels"):
        if key not in refmap and (root / key).is_dir():
            refmap[key] = key
        if key in refmap and not (root / relative_path(refmap[key])).is_dir():
            raise ValueError(f"Missing Elyx {key} directory")
    locales = {}
    strings = refmap.get("strings")
    if strings is not None:
        path = root / relative_path(strings)
        if not path.exists():
            raise ValueError("Missing Elyx localization path")
        for file in sorted(path.iterdir()) if path.is_dir() else [path]:
            if file.is_file() and file.suffix in FORMATS:
                locale = file.stem.rsplit("_", 1)[-1].lower() if "_" in file.stem else "en"
                locales.setdefault(locale, {}).update(read_mapping(file))
    manifest_metadata = dict(metadata)
    from elyx import Strings
    strings = Strings(locales)
    for key in ("name", "description"):
        value = manifest_metadata.get(key)
        if isinstance(value, str) and locales:
            manifest_metadata[key] = re.sub(r"\{([^{}]+)\}", lambda match: str(strings.get(match[1])), value)
    wheels = root / relative_path(refmap["wheels"]) if "wheels" in refmap else None
    if not metadata_only:
        prepare_dependencies(root, metadata.get("requirements"), resolve_pypi=resolve_pypi, wheels=wheels, resolver=resolver,
                             keep_wheels=keep_wheels)
        write_environment(root, refmap, metadata, locales)
    return manifest_metadata, f"{main}:{class_name}"
