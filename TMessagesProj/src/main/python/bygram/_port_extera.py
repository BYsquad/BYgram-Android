"""Wrap exteraGram source plugins in a BYgram package without executing them."""
import ast
import hashlib
import json
from pathlib import Path
import re
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from ._port_elyx import prepare_dependencies, prepare_elyx, write_environment


def catalog_metadata(source, archive):
    source = Path(source)
    if archive:
        with tempfile.TemporaryDirectory(prefix="bygram-catalog-") as directory:
            metadata, entrypoint = prepare_elyx(source, Path(directory), resolve_pypi=False, read_source=read_source,
                                                metadata_only=True)
    else:
        metadata, class_name = read_source(source.read_bytes(), source)
        entrypoint = f"main.py:{class_name}"
    return json.dumps(build_manifest(metadata, entrypoint, structured=archive), ensure_ascii=False)


def read_source(contents, source):
    tree = ast.parse(contents.decode("utf-8-sig"), filename=str(source))
    keys = {"__id__", "__name__", "__description__", "__author__", "__version__", "__requirements__", "__bygram_hooks__"}
    metadata = {}
    base_names = {"BasePlugin"}
    for statement in tree.body:
        if isinstance(statement, ast.ImportFrom) and statement.module == "base_plugin" and statement.level == 0:
            base_names.update(alias.asname or alias.name for alias in statement.names if alias.name == "BasePlugin")
        if isinstance(statement, (ast.Assign, ast.AnnAssign)) and statement.value is not None:
            targets = statement.targets if isinstance(statement, ast.Assign) else (statement.target,)
            for target in targets:
                if isinstance(target, ast.Name) and target.id in keys:
                    if target.id in metadata:
                        raise ValueError(f"Duplicate metadata {target.id}")
                    metadata[target.id] = ast.literal_eval(statement.value)
    classes = [statement.name for statement in tree.body if isinstance(statement, ast.ClassDef)
               and any(isinstance(base, ast.Name) and base.id in base_names
                       or isinstance(base, ast.Attribute) and isinstance(base.value, ast.Name)
                       and base.value.id == "base_plugin" and base.attr == "BasePlugin" for base in statement.bases)]
    if not classes:
        raise ValueError("Expected a top-level BasePlugin subclass")
    return {key[2:-2]: value for key, value in metadata.items()}, classes[0]


def bygram_id(original_id, *, structured):
    pattern = r"[A-Za-z0-9_]{2,32}" if structured else r"[A-Za-z][A-Za-z0-9_-]{1,31}"
    if not isinstance(original_id, str) or not re.fullmatch(pattern, original_id):
        raise ValueError("Invalid exteraGram plugin ID")
    digest = hashlib.sha256(original_id.encode("ascii")).hexdigest()[:16]
    normalized = original_id.lower().replace("-", "_")
    if not normalized[0].isalpha():
        normalized = "p_" + normalized
    return f"extera.{normalized}_{digest}"


def developer_id(original_id):
    return bygram_id(original_id, structured="-" not in original_id)


def normalize_version(version):
    if not isinstance(version, str) or not re.fullmatch(r"(?:0|[1-9][0-9]{0,9})(?:\.(?:0|[1-9][0-9]{0,9})){0,2}", version):
        raise ValueError("Use a numeric plugin version with up to three components")
    return version + ".0" * (2 - version.count("."))


def dependencies(root, include_urls=False):
    from ._port_elyx import ENVIRONMENT
    path = Path(root) / ENVIRONMENT
    if not path.is_file():
        return "{}"
    with path.open("rb") as stream:
        data = stream.read(1024 * 1024 + 1)
    if len(data) > 1024 * 1024:
        raise ValueError("ElyxEnvironmentLimitExceeded")
    requires = json.loads(data)["metainfo"].get("requires", {})
    if not isinstance(requires, dict) or len(requires) > 32:
        raise ValueError("InvalidElyxDependencies")
    result = {}
    for item in requires:
        dependency, separator, version = item.partition(" (")
        if separator and not version.endswith(")"):
            raise ValueError("InvalidElyxDependencies")
        plugin_id = bygram_id(dependency, structured=True)
        if plugin_id in result:
            raise ValueError("DuplicateElyxDependency")
        minimum = normalize_version(version.removesuffix(")")) if separator else "0.0.0"
        result[plugin_id] = {"version": minimum, "url": requires[item]} if include_urls else minimum
    return json.dumps(result)


def build_manifest(metadata, entrypoint, *, telegram_hooks=False, telegram_requests=False, send_text=False, media=False,
                   ui=False, message_menu=False, structured=False):
    plugin_id = bygram_id(metadata.get("id"), structured=structured)
    version = normalize_version(metadata.get("version", "1.0"))
    fields = {}
    for name, limit in (("name", 80), ("description", 1000), ("author", 120)):
        value = metadata.get(name, "")
        if not isinstance(value, str):
            raise ValueError(f"Invalid {name}")
        value = " ".join(value.split())
        if len(value.encode("utf-16-le")) // 2 > limit or (name == "name" and not value):
            raise ValueError(f"Invalid {name}")
        fields[name] = value
    capabilities = []
    if ui or message_menu:
        capabilities.append("ui")
    if message_menu:
        capabilities.append("messages.observe")
    if telegram_hooks:
        capabilities.append("hooks.telegram")
    if telegram_requests:
        capabilities.append("telegram.requests")
    if send_text:
        capabilities.append("messages.send")
    if media:
        capabilities.append("messages.media")
    return dict(manifest_version=1, id=plugin_id, version=version, entrypoint=entrypoint,
                    sdk={"min": 1, "max": 1}, python="3.11", settings_schema=1,
                    capabilities=capabilities,
                    account_scope="selected_accounts" if telegram_hooks or telegram_requests or send_text or media or message_menu else "global",
                    **fields)


def prepare_source(source, root, *, resolve_pypi, resolver=None, keep_wheels=False, **capabilities):
    if source.suffix == ".plugin":
        if source.stat().st_size > 2 * 1024 * 1024:
            raise ValueError("Expected an exteraGram .plugin source file of at most 2 MiB")
        contents = source.read_bytes()
        metadata, class_name = read_source(contents, source)
        manifest = build_manifest(metadata, f"main.py:{class_name}", **capabilities)
        (root / "main.py").write_bytes(contents)
        if metadata.get("requirements"):
            prepare_dependencies(root, metadata["requirements"], resolve_pypi=resolve_pypi, resolver=resolver)
            write_environment(root, {}, metadata, {})
    else:
        metadata, entrypoint = prepare_elyx(source, root, resolve_pypi=resolve_pypi, read_source=read_source,
                                            resolver=resolver, keep_wheels=keep_wheels)
        manifest = build_manifest(metadata, entrypoint, structured=True, **capabilities)
    declaration = root / "bygram-hooks.json"
    if declaration.is_file():
        if declaration.stat().st_size > 65536:
            raise ValueError("ART declaration exceeds 64 KiB")
        configure_hooks(manifest, json.loads(declaration.read_text(encoding="utf-8-sig")))
    elif "bygram_hooks" in metadata:
        configure_hooks(manifest, metadata["bygram_hooks"])
    (root / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    return manifest


def configure_hooks(manifest, declaration):
    for field in ("bygram", "telegram", "hook_targets"):
        manifest[field] = declaration[field]
    manifest["requires_hook_backend"] = True
    if "hooks.raw" not in manifest["capabilities"]:
        manifest["capabilities"].append("hooks.raw")


def convert_mobile(source, output, expected_id=None, expected_version=None, developer=False, *, resolver=None):
    source, output = Path(source), Path(output)
    if resolver is None:
        def resolver(requirements, destination):
            from ._mobile_pypi import resolve
            resolve(requirements, destination)
    with tempfile.TemporaryDirectory(prefix="bygram-convert-", dir=source.parent) as directory:
        root = Path(directory)
        manifest = prepare_source(source, root, resolve_pypi=resolver is not None, resolver=resolver, keep_wheels=developer, ui=True,
                                  message_menu=True, telegram_hooks=True, telegram_requests=True, send_text=True, media=True)
        if expected_id is not None and (manifest["id"] != bygram_id(expected_id, structured=source.suffix != ".plugin")
                                        or expected_version is not None and manifest["version"] != normalize_version(expected_version)):
            raise ValueError("CatalogPackageMismatch")
        files = sorted(path for path in root.rglob("*") if path.is_file())
        if len(files) > 512 or sum(path.stat().st_size for path in files) > 30 * 1024 * 1024:
            raise ValueError("PackageLimitExceeded")
        with ZipFile(output, "w") as archive:
            for path in files:
                entry = ZipInfo(path.relative_to(root).as_posix(), (2026, 1, 1, 0, 0, 0))
                entry.external_attr = 0o100644 << 16
                archive.writestr(entry, path.read_bytes(), compress_type=ZIP_DEFLATED)
        if output.stat().st_size > 10 * 1024 * 1024:
            raise ValueError("PackageLimitExceeded")
        return manifest


def port_options(parser):
    parser.add_argument("--telegram-hooks", action="store_true")
    parser.add_argument("--telegram-requests", action="store_true")
    parser.add_argument("--send-text", action="store_true")
    parser.add_argument("--media", action="store_true")
    parser.add_argument("--ui", action="store_true")
    parser.add_argument("--message-menu", action="store_true")
    parser.add_argument("--resolve-pypi", action="store_true", help="Bundle resolved pure-Python PyPI wheels at build time")
    parser.add_argument("--signing-key", type=Path)
    parser.add_argument("--hook-manifest", type=Path, help="BYgram and Telegram build ranges and exact ART hook targets")
