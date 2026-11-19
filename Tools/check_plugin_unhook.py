"""Test a native unhook candidate against real installed Python/Java, without building an APK."""
from pathlib import Path
import argparse
import os
import subprocess
import zipfile
import shlex

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    args = parser.parse_args()
    work = args.work.resolve()
    if not work.is_relative_to(ROOT / "output"): raise ValueError("Use a new output subdirectory")
    work.mkdir(parents=True, exist_ok=False)
    sdk = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
    adb = sdk / "platform-tools/adb.exe"
    def device(*command):
        return subprocess.check_output([str(adb), "-s", args.serial, *map(str, command)], text=True, encoding="utf-8", errors="replace").strip()
    abi = device("shell", "getprop", "ro.product.cpu.abi")
    with zipfile.ZipFile(args.aar) as aar:
        (work / "aliuhook.jar").write_bytes(aar.read("classes.jar"))
        (work / "libaliuhook.so").write_bytes(aar.read("jni/" + abi + "/libaliuhook.so"))
    source = ROOT / "TMessagesProj/src/plugins/java/org/telegram/messenger/plugins"
    candidate = (source / "HookBridge.java").read_text(encoding="utf-8")
    (work / "HookBridge.java").write_text(candidate, encoding="utf-8")
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    python = next((cache / "com.chaquo.python.runtime/chaquopy_java/17.2.0").rglob("*.jar"))
    gson = next((cache / "com.google.code.gson/gson/2.11.0").rglob("*.jar"))
    android = sdk / "platforms/android-36/android.jar"
    classes = work / "classes"; classes.mkdir()
    cp = os.pathsep.join(map(str, (android, python, gson, work / "aliuhook.jar")))
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", cp, "-d", str(classes),
        str(ROOT / "Tools/fixtures/plugins/gate/org/telegram/messenger/plugins/PluginContext.java"),
        str(source / "PluginScope.java"), str(source / "PluginManifest.java"), str(work / "HookBridge.java"),
        str(ROOT / "Tools/PluginDebugContextFactory.java"), str(ROOT / "Tools/PluginUnhookProbe.java")], check=True)
    dex = work / "dex"; dex.mkdir()
    selected = [*classes.glob("Plugin*.class"), *(classes / "org/telegram/messenger/plugins").glob("HookBridge*.class")]
    subprocess.run([str(sdk / "build-tools/36.0.0/d8.bat"), "--min-api", "24", "--lib", str(android),
        "--classpath", str(classes), "--classpath", str(python),
        "--output", str(dex), *map(str, selected), str(work / "aliuhook.jar")], check=True)
    remote = "/data/local/tmp/bygram-unhook-" + work.name
    device("shell", "mkdir", "-p", remote)
    private = device("shell", "run-as", "com.bygram.mobile", "pwd") + "/code_cache/bygram-unhook-" + work.name
    code = "SELFTEST = " + repr(remote + "/_selftest.py") + "\nCANDIDATE = " + repr(private + "/libaliuhook.so") + "\n" + (ROOT / "Tools/fixtures/plugins/unhook-check.py").read_text(encoding="utf-8")
    (work / "check.py").write_text(code, encoding="utf-8")
    for file in (work / "libaliuhook.so", dex / "classes.dex", work / "check.py", ROOT / "TMessagesProj/src/main/python/bygram/_selftest.py"):
        device("push", file, remote + "/" + file.name)
    device("shell", "chmod", "444", remote + "/classes.dex")
    device("shell", "run-as", "com.bygram.mobile", "mkdir", "-p", private)
    device("shell", "run-as", "com.bygram.mobile", "cp", remote + "/libaliuhook.so", private + "/libaliuhook.so")
    device("shell", "run-as", "com.bygram.mobile", "chmod", "444", private + "/libaliuhook.so")
    command = f"CLASSPATH={shlex.quote(remote + '/classes.dex')} timeout 120 app_process /system/bin PluginUnhookProbe {shlex.quote(remote + '/classes.dex')} {shlex.quote(private)} {shlex.quote(remote + '/check.py')}"
    result = subprocess.run([str(adb), "-s", args.serial, "shell", "run-as", "com.bygram.mobile", "sh", "-c", shlex.quote(command)], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=180)
    (work / "runtime.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    print(result.stdout[-4000:] + result.stderr[-1000:])
    if result.returncode or "PASS: physical unhook" not in result.stdout: raise RuntimeError("Native candidate failed; see runtime.log")


if __name__ == "__main__":
    main()
