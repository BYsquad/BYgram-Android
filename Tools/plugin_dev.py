"""Deploy, reload and debug BYgram plugin packages on an Android device."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shlex
import socket
import subprocess
import sys
import tarfile
import tempfile
import threading
import time
from urllib.request import urlopen
from uuid import uuid4
import zipfile

from package_plugin import package

PORT = 42690
MAX_PACKAGE = 10 * 1024 * 1024
ACTIVITY = "org.telegram.messenger.plugins.PluginDevActivity"
DEBUGPY_VERSION = "1.8.22"


def send(sock, token, command, **fields):
    request_id = uuid4().hex
    request = {"@": command, "#": request_id, "token": token, **fields}
    sock.sendall(json.dumps(request, ensure_ascii=False).encode("utf-8") + b"\n")
    return request_id


def receive(reader, request_id):
    line = reader.readline(1024 * 1024 + 1)
    if not line or len(line) > 1024 * 1024:
        raise OSError("Missing or oversized developer server response")
    result = json.loads(line)
    if result.get("#") != request_id:
        raise OSError(result.get("error", "Developer response ID mismatch"))
    if result.get("ok") is False:
        raise RuntimeError(result["error"])
    return result


def request(token, command, **fields):
    with socket.create_connection(("127.0.0.1", PORT), timeout=5) as sock:
        sock.settimeout(75)
        with sock.makefile("rb") as reader:
            return receive(reader, send(sock, token, command, **fields))


def package_bytes(source):
    if source.is_dir():
        with tempfile.TemporaryDirectory(prefix="bygram-dev-package-") as directory:
            target = Path(directory) / "project.byplugin"
            package(source, target)
            return target.read_bytes()
    if source.suffix != ".byplugin" or source.stat().st_size > MAX_PACKAGE:
        raise ValueError("Use a .byplugin of at most 10 MiB or a BYgram source directory")
    return source.read_bytes()


def deploy(token, contents, previous=None, installed=None):
    files = archive_files(contents)
    if previous is not None:
        changes = {name: base64.b64encode(data).decode("ascii") for name, data in files.items()
                   if data != previous.get(name)}
        removed = sorted(previous.keys() - files.keys())
        fields = dict(plugin_id=installed["plugin_id"], base_hash=installed["sha256"], changes=changes, removed=removed)
        if len(json.dumps(fields, ensure_ascii=False).encode("utf-8")) < (MAX_PACKAGE * 4 // 3):
            try:
                result = request(token, "patch_plugin", **fields)
            except RuntimeError as error:
                if "DeveloperBaseChanged" not in str(error):
                    raise
            else:
                print(f'{result["plugin_id"]} {result["sha256"]}\n{result["path"]}')
                return files, result
    result = request(token, "write_plugin", package=base64.b64encode(contents).decode("ascii"))
    print(f'{result["plugin_id"]} {result["sha256"]}\n{result["path"]}')
    return files, result


def archive_files(contents):
    with zipfile.ZipFile(io.BytesIO(contents)) as archive:
        entries = [entry for entry in archive.infolist() if not entry.is_dir()]
        if (len(entries) > 512 or sum(entry.file_size for entry in entries) > 30 * 1024 * 1024
                or any(entry.file_size > 5 * 1024 * 1024 for entry in entries)
                or len({entry.filename for entry in entries}) != len(entries)):
            raise ValueError("Invalid developer package")
        return {entry.filename: archive.read(entry) for entry in entries}


def file_stamp(source):
    if source.is_file():
        paths = [source]
    else:
        paths = sorted(path for path in source.rglob("*") if path.is_file()
                       and "__pycache__" not in path.relative_to(source).parts and path.suffix not in {".pyc", ".pyo"})
    return tuple((path, path.stat().st_size, path.stat().st_mtime_ns) for path in paths)


def watch(token, source):
    previous, deployed = None, None
    files, installed = None, None
    while True:
        try:
            stamp = file_stamp(source)
            if stamp != previous:
                time.sleep(0.35)
                if stamp == file_stamp(source):
                    contents = package_bytes(source)
                    digest = hashlib.sha256(contents).digest()
                    if digest != deployed:
                        files, installed = deploy(token, contents, files, installed)
                        deployed = digest
                    previous = stamp
        except (OSError, RuntimeError, ValueError) as failure:
            print(f"Deploy pending: {failure}", file=sys.stderr)
            time.sleep(2)
        time.sleep(0.35)


def debug(token, plugin_id, method):
    with socket.create_connection(("127.0.0.1", PORT), timeout=5) as sock:
        with sock.makefile("rb") as reader:
            request_id = send(sock, token, "start_debugger", plugin_id=plugin_id, method=method)
            if receive(reader, request_id).get("debugger") != "pdb":
                raise RuntimeError("Developer debugger unavailable")
            sock.settimeout(None)

            def terminal_input():
                try:
                    for line in sys.stdin:
                        sock.sendall(line.encode("utf-8"))
                    sock.sendall(b"quit\n")
                except OSError:
                    pass

            threading.Thread(target=terminal_input, daemon=True).start()
            while data := reader.read1(8192):
                sys.stdout.buffer.write(data)
                sys.stdout.buffer.flush()


def run_adb(args, *command, input=None):
    base = [args.adb]
    if args.serial:
        base += ["-s", args.serial]
    subprocess.run([*base, *command], input=input, check=True)


def debugger_sources(adapter, release):
    package_name = "debugpy" if adapter == "debugpy" else "pydevd-pycharm"
    with urlopen(f"https://pypi.org/pypi/{package_name}/{release}/json", timeout=20) as response:
        metadata = json.load(response)
    suffix = "none-any.whl" if adapter == "debugpy" else ".tar.gz"
    distribution = next((item for item in metadata["urls"] if item["filename"].endswith(suffix)), None)
    if distribution is None:
        raise ValueError(f"No Python helper archive for {package_name} {release}")
    limit = 32 * 1024 * 1024
    with urlopen(distribution["url"], timeout=30) as response:
        contents = response.read(limit + 1)
    if len(contents) > limit or hashlib.sha256(contents).hexdigest() != distribution["digests"]["sha256"]:
        raise ValueError("Debugger archive failed size or hash verification")
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
        if adapter == "debugpy":
            with zipfile.ZipFile(io.BytesIO(contents)) as source:
                for name in source.namelist():
                    if name.endswith((".py", ".txt", "LICENSE")):
                        target.writestr(name, source.read(name))
        else:
            with tarfile.open(fileobj=io.BytesIO(contents), mode="r:gz") as source:
                for item in source:
                    name = item.name.partition("/")[2]
                    if item.isfile() and (name.endswith(".py") or name in {"LICENSE", "VERSION"}):
                        target.writestr(name, source.extractfile(item).read())
    return output.getvalue()


def install_debugger(args):
    archive = debugger_sources(args.adapter, args.version)
    target = f"files/plugin-dev-{args.adapter}.zip"
    stage = f"{target}.{uuid4().hex}.tmp"
    digest = hashlib.sha256(archive).hexdigest()
    script = (f"umask 077; trap 'rm -f {stage}' EXIT; mkdir -p files && "
              f"base64 -d > {stage} && printf '%s  %s\\n' {digest} {stage} | sha256sum -c - >/dev/null && "
              f"mv {stage} {target}")
    run_adb(args, "shell", "-T", shlex.join(["run-as", args.app, "sh", "-c", script]),
            input=base64.b64encode(archive))
    print(f"Installed {args.adapter} {args.version} on the device.")


def debug_ide(args, token):
    device = f"tcp:{args.port}"
    run_adb(args, "reverse", "--no-rebind", device, device)
    try:
        with socket.create_connection(("127.0.0.1", PORT), timeout=5) as sock:
            sock.settimeout(None)
            with sock.makefile("rb") as reader:
                request_id = send(sock, token, "start_ide_debugger", adapter=args.adapter,
                                  port=args.port, plugin_id=args.plugin_id, method=args.method)
                print(f"Connecting to {args.adapter} on desktop loopback port {args.port}.", flush=True)
                receive(reader, request_id)
                print("Plugin method finished.")
    finally:
        run_adb(args, "reverse", "--remove", device)


def start(args, token):
    provision = shlex.join(["run-as", args.app, "sh", "-c", "umask 077; mkdir -p files && cat > files/plugin-dev-token"])
    run_adb(args, "shell", "-T", provision, input=token.encode("ascii"))
    run_adb(args, "forward", f"tcp:{PORT}", f"tcp:{PORT}")
    run_adb(args, "shell", "am", "start", "-n", f"{args.app}/{ACTIVITY}")
    for _ in range(60):
        try:
            request(token, "ping")
            print("BYgram plugin development server ready.")
            return
        except (OSError, RuntimeError):
            time.sleep(0.5)
    raise OSError("Developer server did not respond")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--app", default="com.bygram.mobile")
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("start", "attach", "stop", "list"):
        commands.add_parser(command)
    for command in ("deploy", "watch"):
        commands.add_parser(command).add_argument("source", type=Path)
    for command in ("enable", "disable", "reload", "remove"):
        commands.add_parser(command).add_argument("plugin_id")
    art = commands.add_parser("art-targets")
    art.add_argument("class_name")
    art.add_argument("method")
    debugger = commands.add_parser("debug")
    debugger.add_argument("plugin_id")
    debugger.add_argument("method")
    installer = commands.add_parser("install-debugger")
    installer.add_argument("adapter", choices=("debugpy", "pycharm"))
    installer.add_argument("--version")
    ide = commands.add_parser("debug-ide")
    ide.add_argument("adapter", choices=("debugpy", "pycharm"))
    ide.add_argument("plugin_id")
    ide.add_argument("method")
    ide.add_argument("--port", type=int, default=5678)
    args = parser.parse_args()
    if args.command == "install-debugger":
        if args.adapter == "debugpy" and args.version is None:
            args.version = DEBUGPY_VERSION
        if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){2}", args.version or ""):
            parser.error("Use --version X.Y.Z with the version required by the IDE")
        install_debugger(args)
        return
    if args.command == "stop":
        run_adb(args, "shell", "am", "start", "-a", "org.telegram.bygram.DEV_STOP", "-n", f"{args.app}/{ACTIVITY}")
        run_adb(args, "forward", "--remove", f"tcp:{PORT}")
        return
    token = os.environ.get("BYGRAM_DEV_TOKEN", "")
    if not re.fullmatch(r"[a-f0-9]{64}", token):
        parser.error("Set BYGRAM_DEV_TOKEN to a random 64-character lowercase hexadecimal value")
    if args.command == "start":
        start(args, token)
    elif args.command == "attach":
        run_adb(args, "forward", f"tcp:{PORT}", f"tcp:{PORT}")
        request(token, "ping")
        print("BYgram plugin development server ready.")
    elif args.command == "deploy":
        deploy(token, package_bytes(args.source))
    elif args.command == "watch":
        watch(token, args.source)
    elif args.command == "debug":
        debug(token, args.plugin_id, args.method)
    elif args.command == "art-targets":
        result = request(token, "art_targets", **{"class": args.class_name, "method": args.method})
        print(json.dumps({key: result[key] for key in ("bygram", "telegram", "hook_targets")}, indent=2))
    elif args.command == "debug-ide":
        if not 1024 <= args.port <= 65535 or args.port == PORT:
            parser.error("Use a debugger port in 1024–65535 other than the Dev Server port")
        debug_ide(args, token)
    else:
        command = "get_plugins" if args.command == "list" else args.command + "_plugin"
        result = request(token, command, **({"plugin_id": args.plugin_id} if hasattr(args, "plugin_id") else {}))
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
