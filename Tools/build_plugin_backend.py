"""Rebuild the pinned hook AAR with patched static Dobby; never builds an APK.

Requires the pinned hook/Dobby git repositories and hook's generated prefab tree.
All edits and native builds happen in a new work directory, not in those repos.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tarfile
import zipfile

from check_plugin_apk import ABIS, load_alignments

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.1.4-api37.1-bygram.3"
HOOK = "43b0d8d01b580b8d4caa4aae7ed528ed80a35078"
DOBBY = "edb2af1216313cf6c0d6771be2b279c1db573faf"
SUBMODULES = {
    "lsplant": "06bd3412d639d6b0d76fe6c7388066fe44e8963d",
    "lsplant/lsplant/src/main/jni/external/dex_builder": "ac7fb2230954ee311808bad469b0db501f31bfb8",
    "lsplant/lsplant/src/main/jni/external/dex_builder/external/parallel_hashmap": "0cd57d29a959256ed66b2afdd1009928fc625d09",
    "xz": "1b6defd544914bfb4065e343296e5db64ef400e6",
}


def run(*command, **kwargs):
    subprocess.run([str(arg) for arg in command], check=True, **kwargs)


def export(repo, commit, destination):
    # Export committed bytes, ignoring any local working-tree experiments.
    data = subprocess.check_output(["git", "-C", str(repo), "archive", commit])
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        archive.extractall(destination, filter="data")


def compile_java(hook, sdk, original, work):
    with zipfile.ZipFile(original) as aar:
        baseline = work / "original-classes.jar"; baseline.write_bytes(aar.read("classes.jar"))
    classes = work / "java-classes"; classes.mkdir()
    run("javac", "--release", "11", "-encoding", "UTF-8", "-cp",
        os.pathsep.join(map(str, (sdk / "platforms/android-36/android.jar", baseline))), "-d", classes,
        hook / "core/src/main/java/de/robv/android/xposed/XposedBridge.java")
    result = work / "classes.jar"
    with zipfile.ZipFile(baseline) as source, zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            replacement = classes / entry.filename
            target.writestr(entry, replacement.read_bytes() if replacement.is_file() else source.read(entry))
    for name in ("de.robv.android.xposed.XposedBridge", "de.robv.android.xposed.XposedBridge$HookInfo", "de.robv.android.xposed.XposedBridge$CopyOnWriteSortedSet"):
        before = subprocess.check_output(["javap", "-public", "-classpath", str(baseline), name])
        after = subprocess.check_output(["javap", "-public", "-classpath", str(result), name])
        assert before == after, "Public Java API changed: " + name
    return result.read_bytes()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("hook", "dobby", "sdk", "prefab", "work"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    export(args.hook, HOOK, work / "hook")
    patch_environment = dict(os.environ, GIT_CEILING_DIRECTORIES=str(work))
    run("git", "apply", "--check", ROOT / "third_party/patches/aliuhook-backup-publication.patch", cwd=work / "hook", env=patch_environment)
    run("git", "apply", ROOT / "third_party/patches/aliuhook-backup-publication.patch", cwd=work / "hook", env=patch_environment)
    native = work / "hook/core/src/main/cpp"
    for path, commit in SUBMODULES.items():
        export(args.hook / "core/src/main/cpp" / path, commit, native / path)
    patch_environment = dict(os.environ, GIT_CEILING_DIRECTORIES=str(work))
    run("git", "apply", "--check", ROOT / "third_party/patches/lsplant-restore-access-flags.patch", cwd=native / "lsplant", env=patch_environment)
    run("git", "apply", ROOT / "third_party/patches/lsplant-restore-access-flags.patch", cwd=native / "lsplant", env=patch_environment)
    backup_patch = native / "lsplant_backup_id.cmake"
    backup_code = backup_patch.read_text()
    assert backup_code.count("DoHook(target, hook, backup)") == 2
    backup_patch.write_text(backup_code.replace("DoHook(target, hook, backup)", "DoHook(target, hook, backup, original_flags)"))
    dobby = work / "dobby"
    export(args.dobby, DOBBY, dobby)
    environment = dict(os.environ, GIT_CEILING_DIRECTORIES=str(work))
    run("git", "apply", "--check", ROOT / "third_party/patches/dobby-1.2-preserve-mappings.patch", cwd=dobby, env=environment)
    run("git", "apply", ROOT / "third_party/patches/dobby-1.2-preserve-mappings.patch", cwd=dobby, env=environment)
    assert "result != address" in (dobby / "source/UserMode/UnifiedInterface/platform-posix.cc").read_text()
    # NDK 30 headers cannot be included inside a function in this older C source.
    logging = dobby / "external/logging/logging.c"
    text = logging.read_text()
    assert text.count("#include <android/log.h>") == 1
    logging.write_text("#if defined(__ANDROID__)\n#include <android/log.h>\n#endif\n" + text.replace("#include <android/log.h>", ""))
    cmake_file = native / "CMakeLists.txt"
    text = cmake_file.read_text()
    assert text.count("find_package(dobby REQUIRED CONFIG)") == 1
    cmake_file.write_text(text.replace("find_package(dobby REQUIRED CONFIG)", '''add_library(dobby_fixed STATIC IMPORTED)
set_target_properties(dobby_fixed PROPERTIES IMPORTED_LOCATION "${DOBBY_ARCHIVE}"
    INTERFACE_INCLUDE_DIRECTORIES "${DOBBY_HEADERS}")
add_library(dobby::dobby ALIAS dobby_fixed)'''))
    sdk = args.sdk.resolve()
    ndk = sdk / "ndk/30.0.16248370"
    cmake = sdk / "cmake/4.1.2/bin/cmake.exe"
    common = ["-G", "Ninja", f"-DCMAKE_MAKE_PROGRAM={sdk / 'cmake/4.1.2/bin/ninja.exe'}",
              f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
              "-DANDROID_PLATFORM=android-21", "-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release",
              "-DCMAKE_C_FLAGS_RELEASE=", "-DCMAKE_CXX_FLAGS_RELEASE="]
    libraries = {}
    for abi in sorted(ABIS):
        build = work / abi
        run(cmake, "-S", dobby, "-B", build / "dobby", *common, f"-DANDROID_ABI={abi}",
            "-DCMAKE_POLICY_VERSION_MINIMUM=3.5", "-DDOBBY_GENERATE_SHARED=OFF",
            "-DPlugin.Android.BionicLinkerRestriction=ON")
        run(cmake, "--build", build / "dobby", "--parallel", "4")
        run(cmake, "-S", native, "-B", build / "hook", *common, f"-DANDROID_ABI={abi}",
            f"-DCMAKE_FIND_ROOT_PATH={(args.prefab.resolve() / abi / 'prefab').as_posix()}",
            f"-DDOBBY_ARCHIVE={(build / 'dobby/libdobby.a').as_posix()}",
            f"-DDOBBY_HEADERS={(dobby / 'include').as_posix()}")
        run(cmake, "--build", build / "hook", "--parallel", "4")
        library = build / "hook/libaliuhook.so"
        run(ndk / "toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe", "--strip-unneeded", library)
        data = library.read_bytes()
        if abi in ("arm64-v8a", "x86_64"):
            load_alignments(data)
        libraries[f"jni/{abi}/libaliuhook.so"] = data
    old_version = "1.1.4-api37.1"
    old = ROOT / "third_party/maven/com/aliucord/Aliuhook" / old_version
    original = old / f"Aliuhook-{old_version}.aar"
    assert hashlib.sha256(original.read_bytes()).hexdigest() == "01fa427a1e6d460069cc32c772d47219677885528697e5dfd91cfc823eda4189"
    replacements = dict(libraries, **{"classes.jar": compile_java(work / "hook", sdk, original, work)})
    result = work / f"Aliuhook-{VERSION}.aar"
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as target:
        assert set(libraries) <= set(source.namelist())
        for entry in source.infolist():
            target.writestr(entry, replacements.get(entry.filename, source.read(entry)))
    # Verify the public Java API and every unrelated AAR entry are unchanged.
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(result) as target:
        assert source.namelist() == target.namelist()
        for name in source.namelist():
            assert target.read(name) == (replacements[name] if name in replacements else source.read(name)), name
    metadata = {"version": VERSION, "hook": HOOK, "dobby": DOBBY,
                "submodules": SUBMODULES, "aar_sha256": hashlib.sha256(result.read_bytes()).hexdigest(),
                "java_sha256": hashlib.sha256(replacements["classes.jar"]).hexdigest(),
                "patch_sha256": {name: hashlib.sha256((ROOT / "third_party/patches" / name).read_bytes()).hexdigest() for name in ("dobby-1.2-preserve-mappings.patch", "lsplant-restore-access-flags.patch", "aliuhook-backup-publication.patch")},
                "native_sha256": {name: hashlib.sha256(data).hexdigest() for name, data in libraries.items()}}
    (work / "provenance.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metadata, indent=2))
    print("PASS: four ABI rebuild, ELF64 16 KiB, unchanged Java API and other AAR entries", flush=True)


if __name__ == "__main__":
    main()
