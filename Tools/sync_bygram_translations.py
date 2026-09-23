"""Import reviewed BYgram translations from the standalone public catalog.

Pass a local clone of BYxarek/BYgram-translations. This script reads XML data;
it never executes code from the public repository.
"""

import argparse
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


APP_RES = Path(__file__).resolve().parents[1] / "TMessagesProj" / "src" / "main" / "res"
FILES = ("bygram_strings.xml", "bygram_preferences.xml")
LOCALE = re.compile(r"[a-z]{2,3}(?:-r[A-Z]{2})?\Z")
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")


def tokens(node: ET.Element) -> Counter[str]:
    return Counter(token for token in PLACEHOLDER.findall("".join(node.itertext())) if token != "%%")


def entries(path: Path) -> dict[str, ET.Element]:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"Expected a regular XML file: {path}")
    root = ET.parse(path).getroot()
    if root.tag != "resources":
        raise ValueError(f"Expected <resources> in {path}")
    result = {}
    for node in root:
        if node.tag in ("string", "string-array"):
            name = node.get("name")
            if not name or name in result:
                raise ValueError(f"Missing or duplicate resource name in {path}: {name}")
            result[name] = node
    return result


def validate_translation(source: dict[str, ET.Element], translated: dict[str, ET.Element], path: Path) -> None:
    for name, node in translated.items():
        original = source.get(name)
        if original is None or original.tag != node.tag:
            raise ValueError(f"Unknown resource or wrong type in {path}: {name}")
        if node.tag == "string-array":
            original_items = original.findall("item")
            translated_items = node.findall("item")
            if len(original_items) != len(translated_items):
                raise ValueError(f"Array length differs in {path}: {name}")
            pairs = zip(original_items, translated_items)
        else:
            pairs = ((original, node),)
        if any(tokens(a) != tokens(b) for a, b in pairs):
            raise ValueError(f"Format placeholders differ in {path}: {name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("catalog", type=Path, help="local clone of BYxarek/BYgram-translations")
    parser.add_argument("--check", action="store_true", help="verify that app translations match the catalog")
    args = parser.parse_args()
    locales = args.catalog.resolve() / "locales"
    if locales.is_symlink() or not locales.is_dir():
        raise ValueError(f"Missing locales directory: {locales}")

    sources = {}
    for filename in FILES:
        public_source = locales / "en" / filename
        app_source = APP_RES / "values" / filename
        sources[filename] = entries(public_source)
        if public_source.read_bytes() != app_source.read_bytes():
            raise ValueError(f"English source differs from app; update both before import: {filename}")

    changes = []
    for directory in sorted(locales.iterdir()):
        if directory.name == "en":
            continue
        if directory.is_symlink() or not directory.is_dir() or not LOCALE.fullmatch(directory.name):
            raise ValueError(f"Invalid locale directory: {directory}")
        for filename in FILES:
            incoming = directory / filename
            if not incoming.exists():
                continue
            translated = entries(incoming)
            validate_translation(sources[filename], translated, incoming)
            destination = APP_RES / f"values-{directory.name}" / filename
            if not destination.exists() or destination.read_bytes() != incoming.read_bytes():
                changes.append((incoming, destination))

    if args.check and changes:
        raise ValueError("Catalog differs from app: " + ", ".join(str(path) for _, path in changes))
    for incoming, destination in changes:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(incoming, destination)
        print(f"Imported {incoming.parent.name}/{incoming.name}")
    print(f"BYgram translations: {'in sync' if not changes else f'{len(changes)} file(s) imported'}")


if __name__ == "__main__":
    try:
        main()
    except (ET.ParseError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
