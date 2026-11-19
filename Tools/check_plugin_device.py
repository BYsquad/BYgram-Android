"""Run the fixed Debug fixture on an explicitly selected disposable Android device."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("apk", type=Path)
    parser.add_argument("--expect-hooks", action="store_true")
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    adb = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"
    package = "com.bygram.mobile"

    def run(*command, check=True, timeout=60):
        result = subprocess.run([str(adb), "-s", args.serial, *command], capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout.strip()

    print(run("install", "-r", str(args.apk.resolve()), timeout=180), flush=True)
    run("shell", "am", "force-stop", package)
    run("shell", "run-as", package, "rm", "-f", "files/plugin-self-check.txt")
    start = time.monotonic()
    print(run("shell", "am", "start", "-W", "-n",
              package + "/org.telegram.messenger.plugins.PluginDiagnosticActivity"), flush=True)
    result = ""
    while time.monotonic() - start < 120:
        result = run("shell", "run-as", package, "cat", "files/plugin-self-check.txt", check=False)
        if result:
            break
        time.sleep(1)
    page_size = run("shell", "getconf", "PAGE_SIZE", check=False)
    if not page_size:
        page_size = str(int(re.search(r"KernelPageSize:\s+(\d+)", run("shell", "cat", "/proc/self/smaps")).group(1)) * 1024)
    report = {"serial": args.serial, "api": run("shell", "getprop", "ro.build.version.sdk"),
              "build": run("shell", "getprop", "ro.build.fingerprint"),
              "abi": run("shell", "getprop", "ro.product.cpu.abi"),
              "page_size": page_size,
              "seconds": round(time.monotonic() - start, 2), "result": result,
              "expected_hooks": args.expect_hooks}
    if result.startswith("PASS:"):
        pid = run("shell", "pidof", package)
        assert pid.isdigit(), pid
        report["native_backend_loaded"] = "libaliuhook.so" in run(
            "shell", "run-as", package, "cat", f"/proc/{pid}/maps")
        # Catch a process which reports success and crashes during subsequent ART GC.
        time.sleep(5)
        report["process_survived"] = run("shell", "pidof", package, check=False) == pid
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2), flush=True)
    assert result.startswith("PASS:"), result
    expected = "two-plugin hook" if args.expect_hooks else "Raw hooks SKIPPED: HookBackendUnavailable"
    assert expected in result, result
    assert report["native_backend_loaded"] == args.expect_hooks, report
    assert report["process_survived"], report


if __name__ == "__main__":
    main()
