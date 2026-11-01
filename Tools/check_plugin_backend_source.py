"""Run current HookBridge source in an installed Debug BYgram on a disposable device, without an APK.

Temporarily replaces the plugin registry, restores it in finally, and never touches Telegram auth.
Use only a dedicated test installation: native failures can kill the application process.
The renamed bridge relaxes only the availability predicate, never hooks that predicate or changes ART flags.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.bygram.mobile"
VERSION = "1.1.4-api37.1-bygram.3"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--work", required=True, type=Path)
    args = parser.parse_args()
    work = args.work.resolve()
    if not work.is_relative_to(ROOT / "output"):
        raise ValueError("Keep probe artifacts under the workspace output directory")
    work.mkdir(parents=True, exist_ok=False)
    sdk = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
    adb = sdk / "platform-tools/adb.exe"

    def device(*command, check=True, binary=False, timeout=60):
        result = subprocess.run([str(adb), "-s", args.serial, *command], capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stdout.decode(errors="replace") + result.stderr.decode(errors="replace"))
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()

    def shell(script):
        return device("shell", f"run-as {PACKAGE} sh -c {shlex.quote(script)}")

    def build(*command):
        result = subprocess.run([str(arg) for arg in command], capture_output=True, text=True)
        with (work / "build.log").open("a", encoding="utf-8") as log:
            log.write(result.stdout + result.stderr)
        if result.returncode:
            raise RuntimeError("Probe compilation failed; see build.log")

    flags = device("shell", "dumpsys", "package", PACKAGE)
    if "DEBUGGABLE" not in flags:
        raise RuntimeError("This source probe requires an already installed Debug BYgram")
    abi = device("shell", "getprop", "ro.product.cpu.abi")
    if abi not in ("x86_64", "arm64-v8a"):
        raise RuntimeError("This raw backend probe requires a 64-bit test device")
    source = ROOT / "TMessagesProj/src/full/java/org/telegram/messenger/plugins/HookBridge.java"
    source_bytes = source.read_bytes()
    bridge = source_bytes.decode("utf-8").replace("HookBridge", "ExperimentalHookBridge")
    start = bridge.index("    public static synchronized boolean available() {")
    end = bridge.index("    public static void verify()", start)
    bridge = bridge[:start] + """    public static synchronized boolean available() {
        return android.os.Process.is64Bit() && Build.VERSION.SDK_INT >= 24 && Build.VERSION.SDK_INT <= 37;
    }
""" + bridge[end:]
    java_source = work / "ExperimentalHookBridge.java"
    java_source.write_text(bridge, encoding="utf-8")
    classes = work / "classes"; classes.mkdir()
    dex = work / "dex"; dex.mkdir()
    compile_root = ROOT / "TMessagesProj/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
    with zipfile.ZipFile(work / "sdk-types.jar", "w", zipfile.ZIP_DEFLATED) as jar:
        files = list((compile_root / "org/telegram/messenger/plugins").glob("*.class"))
        if not files:
            raise RuntimeError("Compile Full Debug Java before running this probe")
        for file in files: jar.write(file, file.relative_to(compile_root).as_posix())
    aar = ROOT / f"third_party/maven/com/aliucord/Aliuhook/{VERSION}/Aliuhook-{VERSION}.aar"
    with zipfile.ZipFile(aar) as archive:
        (work / "aliuhook.jar").write_bytes(archive.read("classes.jar"))
        native_hash = hashlib.sha256(archive.read(f"jni/{abi}/libaliuhook.so")).hexdigest()
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    python_jar = next((cache / "com.chaquo.python.runtime/chaquopy_java/17.2.0").rglob("*.jar"))
    gson = next((cache / "com.google.code.gson/gson/2.11.0").rglob("*.jar"))
    android = sdk / "platforms/android-36/android.jar"
    cp = os.pathsep.join(map(str, (android, work / "sdk-types.jar", work / "aliuhook.jar", python_jar, gson)))
    build("javac", "-encoding", "UTF-8", "-cp", cp, "-d", classes, java_source, ROOT / "Tools/PluginDebugContextFactory.java")
    build(sdk / "build-tools/36.0.0/d8.bat", "--min-api", "24", "--lib", android,
          "--classpath", work / "sdk-types.jar", "--classpath", work / "aliuhook.jar", "--classpath", python_jar,
          "--output", dex, *(str(file) for file in classes.rglob("*.class")))
    print("Compiled current source; no APK created", flush=True)
    token = uuid.uuid4().hex
    plugin_id = "test.bygram.probe_" + token[:8]
    remote = "/data/local/tmp/bygram-source-" + token
    code = "REMOTE_ROOT = " + repr(remote) + "\n" + (ROOT / "Tools/fixtures/plugins/source-backend-main.py").read_text(encoding="utf-8")
    manifest = dict(manifest_version=1, id=plugin_id, name="SDK source fixture", description="", author="BYgram",
                    version="1.0.0", entrypoint="main.py:Main", sdk=dict(min=1, max=1), python="3.11",
                    capabilities=[], account_scope="global", settings_schema=1)
    (work / "main.py").write_text(code, encoding="utf-8")
    (work / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    package_hash = hashlib.sha256((code + json.dumps(manifest)).encode()).hexdigest()
    record = dict(manifest=manifest, hash=package_hash, approved=package_hash, desired=True, installed=1, source="local_file")
    (work / "registry.json").write_text(json.dumps(dict(schema=1, engine=True, plugins={plugin_id: record})), encoding="utf-8")
    device("shell", "am", "force-stop", PACKAGE)
    names = ["no_backup/plugins/registry.json", "no_backup/plugins/registry.previous.json",
             "no_backup/plugins/activation", "no_backup/plugins/startup", "no_backup/plugin-safe-mode"]
    paths = [name + suffix for name in names for suffix in ("", ".bak", ".new")]
    originals = {}
    for path in paths:
        present = shell(f"if test -f {path}; then echo yes; fi") == "yes"
        originals[path] = device("exec-out", "run-as", PACKAGE, "cat", path, binary=True) if present else None
    for path in names[:2]:
        if originals[path] and json.loads(originals[path]).get("plugins"):
            raise RuntimeError("Use an empty test plugin registry; package collection would affect installed plugins")
    restore = {}
    for index, (path, data) in enumerate(originals.items()):
        file = work / f"restore-{index}"
        if data is not None: file.write_bytes(data)
        restore[path] = file.name if data is not None else None
    (work / "restore-manifest.json").write_text(json.dumps(restore, indent=2), encoding="utf-8")
    report = dict(serial=args.serial, abi=abi, api=device("shell", "getprop", "ro.build.version.sdk"),
                  build=device("shell", "getprop", "ro.build.fingerprint"),
                  source_sha256=hashlib.sha256(source_bytes).hexdigest(),
                  dex_sha256=hashlib.sha256((dex / "classes.dex").read_bytes()).hexdigest(), result="")
    try:
        device("shell", "mkdir", "-p", remote)
        for file in (dex / "classes.dex", work / "main.py", work / "manifest.json", work / "registry.json",
                     ROOT / "TMessagesProj/src/main/python/bygram/_selftest.py"):
            device("push", str(file), remote + "/" + file.name)
        device("shell", "chmod", "444", remote + "/classes.dex")
        package_dir = "no_backup/plugins/packages/" + package_hash
        shell(f"mkdir -p {package_dir}; cp {remote}/main.py {remote}/manifest.json {package_dir}/; cp {remote}/registry.json no_backup/plugins/registry.json; "
              + "rm -f " + " ".join(path for path in paths if "registry" not in path or path.endswith((".bak", ".new"))))
        device("shell", "am", "start", "-W", "-n", PACKAGE + "/org.telegram.ui.LaunchActivity")
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            result = device("shell", "run-as", PACKAGE, "cat", f"no_backup/plugins/data/{plugin_id}/result.txt", check=False)
            if result.startswith(("PASS:", "FAIL:")):
                report["result"] = result; break
            time.sleep(1)
        assert report["result"].startswith("PASS:"), report["result"] or "No result (crash/hang)"
        pid = device("shell", "pidof", PACKAGE)
        assert re.fullmatch(r"\d+", pid), pid
        maps = device("shell", "run-as", PACKAGE, "cat", f"/proc/{pid}/maps")
        loaded = next(line.split()[-1] for line in maps.splitlines() if line.endswith("/libaliuhook.so"))
        report["native_sha256"] = device("shell", "run-as", PACKAGE, "sha256sum", loaded).split()[0]
        assert report["native_sha256"] == native_hash, "Installed native backend differs from pinned AAR"
        time.sleep(5)
        report["process_survived"] = device("shell", "pidof", PACKAGE) == pid
        assert report["process_survived"], "Process crashed after result"
        print(report["result"], flush=True)
    finally:
        try:
            device("shell", "am", "force-stop", PACKAGE, check=False)
            for path, name in restore.items():
                if name is None: shell("rm -f " + path)
                else:
                    device("push", str(work / name), remote + "/restore")
                    shell(f"cp {remote}/restore {path}")
            shell(f"rm -rf no_backup/plugins/packages/{package_hash} no_backup/plugins/data/{plugin_id}")
        except Exception as error:
            report["cleanup_error"] = str(error)
            raise
        finally:
            (work / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__": main()
