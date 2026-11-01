"""Check the final Full APK's runtime payload and 64-bit ELF load alignment."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile

ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}
RUNTIMES = {"libaliuhook.so", "libchaquopy_java.so", "libpython3.11.so"}


def load_alignments(data):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected little-endian ELF64")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size < 56 or count == 0 or offset + size * count > len(data):
        raise ValueError("Invalid program headers")
    result = []
    for index in range(count):
        entry = offset + size * index
        if struct.unpack_from("<I", data, entry)[0] == 1:
            file_offset, address = struct.unpack_from("<QQ", data, entry + 8)
            alignment = struct.unpack_from("<Q", data, entry + 48)[0]
            if alignment < 16384 or alignment & (alignment - 1) or (address - file_offset) % 16384:
                raise ValueError("PT_LOAD is not 16 KiB compatible")
            result.append(alignment)
    if not result:
        raise ValueError("Missing PT_LOAD")
    return result


def self_check():
    data = bytearray(120)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<Q", data, 112, 16384)
    assert load_alignments(data) == [16384]
    struct.pack_into("<Q", data, 112, 4096)
    try: load_alignments(data)
    except ValueError: pass
    else: raise AssertionError("Accepted 4 KiB ELF")
    struct.pack_into("<Q", data, 112, 16384)
    inner, outer = io.BytesIO(), io.BytesIO()
    with zipfile.ZipFile(inner, "w") as archive: archive.writestr("module.so", data)
    with zipfile.ZipFile(outer, "w") as archive:
        archive.writestr("assets/chaquopy/stdlib-x86_64.imy", inner.getvalue())
    with zipfile.ZipFile(outer) as archive:
        entries = list(native_entries(archive))
        assert entries[0][0] == "assets/chaquopy/stdlib-x86_64.imy!module.so"
        assert load_alignments(entries[0][1]) == [16384]


def native_entries(archive):
    for name in sorted(archive.namelist()):
        if name.endswith(".so"):
            yield name, archive.read(name)
        elif name.startswith("assets/chaquopy/") and name.endswith(".imy"):
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as asset:
                if asset.testzip() is not None: raise ValueError("Invalid Python asset ZIP: " + name)
                for child in sorted(asset.namelist()):
                    if child.endswith(".so"): yield name + "!" + child, asset.read(child)


def check(apk):
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    report = {"apk": str(apk.resolve()), "bytes": apk.stat().st_size,
              "sha256": digest, "native": []}
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("Invalid APK ZIP")
        for abi in ABIS:
            for library in RUNTIMES:
                if f"lib/{abi}/{library}" not in names:
                    raise ValueError(f"Missing runtime: {abi}/{library}")
        if not any(name.startswith("assets/chaquopy/") for name in names):
            raise ValueError("Missing Python assets")
        for name, data in native_entries(archive):
            if data[:5] == b"\x7fELF\x01": continue
            try: alignments = load_alignments(data)
            except ValueError as error: raise ValueError(f"{name}: {error}") from error
            report["native"].append({"file": name, "bytes": len(data),
                "sha256": hashlib.sha256(data).hexdigest(), "alignments": alignments})
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, nargs="?")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    self_check()
    if args.apk:
        result = check(args.apk)
        if args.report: args.report.write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(f"PASS: Full runtime in four ABIs, ZIP CRC, {len(result['native'])} ELF64 libraries aligned for 16 KiB; SHA-256 {result['sha256']}")
    else:
        print("PASS: ELF checker accepts 16 KiB, rejects 4 KiB and inspects nested Python native assets")
