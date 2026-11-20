"""Build or install the standalone BYgram plugin development skills."""
import argparse
import base64
import io
from pathlib import Path, PurePosixPath
import sys
import tempfile
import textwrap
import zipfile


PAYLOAD = None

TARGETS = {
    "codex": (".agents/skills/bygram-plugin", "shared"),
    "gemini": (".agents/skills/bygram-plugin", "shared"),
    "cursor": (".agents/skills/bygram-plugin", "shared"),
    "claude": (".claude/skills/bygram-plugin", "claude"),
    "copilot": (".copilot/skills/bygram-plugin", "copilot"),
    "deepseek": ("bygram-skills/deepseek", "shared"),
}

PROMPT = "references/docs/plugins/deepseek-prompt.md"
WORKFLOW_LINK = "../../../docs/plugins/agent-workflow.md"
PORTABLE_WORKFLOW_LINK = "references/docs/plugins/agent-workflow.md"


def build(output):
    source = Path(__file__).resolve()
    if output.suffix != ".py" or output.resolve() == source:
        raise ValueError("Choose a separate .py file for the standalone installer")
    repo = source.parent.parent
    docs = repo / "docs/plugins"
    bundled = [
        repo / "LICENSE",
        repo / "third_party/plugin-runtime-notices.md",
        *(docs / name for name in (
            "README.md", "agent-workflow.md", "deepseek-prompt.md", "dev-server.md",
            "exteragram-port.md", "manifest-v1.schema.json", "sdk-v1.md", "testing-and-release.md",
        )),
        *docs.glob("examples/*/main.py"),
        *docs.glob("examples/*/manifest.json"),
        *(repo / "Tools" / name for name in (
            "package_plugin.py", "port_extera_plugin.py", "plugin_dev.py",
        )),
    ]
    python = repo / "TMessagesProj/src/main/python"
    bundled.extend(python / name for name in (
        "elyx.py", "bygram/__init__.py", "bygram/hooks.py", "bygram/telegram.py",
        "bygram/_elyx.py", "bygram/_port_extera.py", "bygram/_port_elyx.py", "bygram/_mobile_pypi.py",
    ))

    contents = {}
    for path in bundled:
        data = path.read_bytes()
        if path == docs / "README.md":
            data = data.replace(b"](agent-skills.md)", b"](agent-workflow.md)")
            online = b"](https://github.com/BYsquad/BYgram-Android/blob/main/docs/plugins/"
            for name in (b"implementation-plan.md", b"paid-catalog-plan.md", b"paid-catalog.md"):
                data = data.replace(b"](" + name + b")", online + name + b")")
        contents[f"references/{path.relative_to(repo).as_posix()}"] = data

    for client, location in (("shared", ".agents"), ("claude", ".claude"), ("copilot", ".github")):
        path = repo / location / "skills/bygram-plugin/SKILL.md"
        skill = path.read_text(encoding="utf-8")
        if WORKFLOW_LINK not in skill:
            raise ValueError(f"Missing workflow link in {path}")
        skill = skill.replace(WORKFLOW_LINK, PORTABLE_WORKFLOW_LINK)
        contents[f"{client}/SKILL.md"] = skill.encode("utf-8")

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, data in sorted(contents.items()):
            archive.writestr(zipfile.ZipInfo(name), data, compress_type=zipfile.ZIP_DEFLATED)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    payload = "\n".join(f"    {part!r}" for part in textwrap.wrap(encoded, 96))
    installer = source.read_text(encoding="utf-8").replace(
        "\nPAYLOAD = None\n", f"\nPAYLOAD = (\n{payload}\n)\n", 1
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as temporary:
        staged = Path(temporary) / output.name
        staged.write_text(installer, encoding="utf-8")
        staged.replace(output)
    print(output.resolve())


def install(home, requested):
    clients = TARGETS if requested == "all" else (requested,)
    destinations = dict(TARGETS[client] for client in clients)
    with zipfile.ZipFile(io.BytesIO(base64.b64decode(PAYLOAD))) as archive:
        references = {}
        for name in archive.namelist():
            if not name.startswith("references/"):
                continue
            path = PurePosixPath(name)
            if ".." in path.parts or "\\" in name or ":" in name:
                raise ValueError(f"Invalid bundled path: {name}")
            references[path] = archive.read(name)

        pending = []
        for relative, variant in destinations.items():
            destination = home / relative
            files = {**references, PurePosixPath("SKILL.md"): archive.read(f"{variant}/SKILL.md")}
            if destination.is_symlink() or destination.exists():
                if destination.is_dir() and not destination.is_symlink() and all(
                    (destination / path).is_file() and (destination / path).read_bytes() == data
                    for path, data in files.items()
                ):
                    print(f"Already installed: {destination}")
                    continue
                raise FileExistsError(
                    f"{destination} already exists with different content. "
                    "Move it aside and run the installer again."
                )
            pending.append((destination, files))

    for destination, files in pending:
        destination.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=destination.parent) as temporary:
            staged = Path(temporary) / destination.name
            for path, data in files.items():
                target = staged / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            if destination.exists() or destination.is_symlink():
                raise FileExistsError(f"{destination} appeared during installation")
            staged.rename(destination)
        print(f"Installed: {destination}")
    if "deepseek" in clients:
        print(f"DeepSeek prompt: {home / TARGETS['deepseek'][0] / PROMPT}")


def main():
    if PAYLOAD is None:
        parser = argparse.ArgumentParser(description="Build the standalone BYgram skills installer.")
        parser.add_argument("--build", type=Path, required=True, metavar="OUTPUT.py")
        output = parser.parse_args().build
    else:
        parser = argparse.ArgumentParser(description="Install the BYgram plugin development skills.")
        parser.add_argument("client", nargs="?", default="all", choices=("all", *TARGETS))
        client = parser.parse_args().client
    try:
        if PAYLOAD is None:
            build(output)
        else:
            install(Path.home(), client)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
