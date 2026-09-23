"""Check that BYgram strings are reachable through Telegram's binary localization."""

import argparse
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_FILES = ("bygram_strings.xml", "bygram_preferences.xml")


def java_hash(value: str) -> int:
    result = 0
    for character in value:
        result = (31 * result + ord(character)) & 0xFFFFFFFF
    return result - 0x100000000 if result >= 0x80000000 else result


def localization_hashes(data: bytes) -> set[int]:
    count = struct.unpack_from("<i", data)[0]
    offset = 4
    hashes = set()
    for _ in range(count):
        value_hash = struct.unpack_from("<i", data, offset)[0]
        offset += 4
        first = data[offset]
        offset += 1
        if first == 254:
            length = int.from_bytes(data[offset : offset + 3], "little")
            offset += 3
            header_size = 4
        else:
            length = first
            header_size = 1
        offset += length + (-(header_size + length)) % 4
        hashes.add(value_hash)
    if offset != len(data):
        raise ValueError("Unexpected localization asset length")
    return hashes


def resource_names(locale: str) -> set[str]:
    directory = "values" if locale == "en" else f"values-{locale}"
    result = set()
    for filename in RESOURCE_FILES:
        root = ET.parse(ROOT / "TMessagesProj" / "src" / "main" / "res" / directory / filename).getroot()
        result.update(node.attrib["name"] for node in root.findall("string"))
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()

    with zipfile.ZipFile(args.apk) as apk:
        bindings = apk.read("assets/string_resource_ids.bin")
        binding_hashes = {value_hash for _, value_hash in struct.iter_unpack("<ii", bindings[4:])}
        failures = []
        for locale in ("en", "ru", "uk"):
            names = resource_names(locale)
            hashes = localization_hashes(apk.read(f"assets/localization_{locale}.bin"))
            missing = sorted(name for name in names if java_hash(name) not in hashes)
            failures.extend(f"{locale}: {name}" for name in missing)
            print(f"{locale}: {len(names) - len(missing)}/{len(names)} BYgram strings in localization asset")
            if locale == "en":
                unbound = sorted(name for name in names if java_hash(name) not in binding_hashes)
                failures.extend(f"binding: {name}" for name in unbound)

    if failures:
        raise SystemExit("FAIL: missing " + ", ".join(failures[:12]) + (" ..." if len(failures) > 12 else ""))
    print("PASS: all BYgram strings are available to LocaleController")


if __name__ == "__main__":
    main()
