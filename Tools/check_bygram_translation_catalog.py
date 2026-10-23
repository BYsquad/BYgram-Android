"""Check Android resource coverage and the public-catalog import contract.

Run: python Tools/check_bygram_translation_catalog.py PATH_TO_CATALOG
"""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from sync_bygram_translations import android_entries, android_xml, entries, validate_translation

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("catalog", type=Path)
args = parser.parse_args()
res = ROOT / "TMessagesProj/src/main/res"
files = ("bygram_strings.xml", "bygram_preferences.xml")
english = {}
for filename in files:
    english.update(entries(res / "values" / filename))
for lang, folder in (("en", "values"), ("ru", "values-ru"), ("uk", "values-uk")):
    for filename in files:
        path = res / folder / filename
        public = args.catalog / "locales" / lang / filename
        assert android_entries(path) == android_entries(public), f"Catalog drift: {lang}/{filename}"
        validate_translation(english, entries(path), path)

references = set()
for path in (ROOT / "TMessagesProj/src/main/java").rglob("*.java"):
    references.update(re.findall(r"R\.(?:string|array)\.(Bygram\w+)", path.read_text(encoding="utf-8")))
assert references <= english.keys(), f"Missing BYgram resources: {sorted(references - english.keys())}"

with tempfile.TemporaryDirectory(prefix="bygram-catalog-") as directory:
    path = Path(directory) / "strings.xml"
    path.write_text('''<resources>
    <string name="BygramDesktopOnly">Windows</string>
    <string name="BygramText">Keep %1$s unchanged</string>
    <string-array name="BygramOptions"><item>A</item><item>B</item></string-array>
</resources>''', encoding="utf-8")
    assert "BygramDesktopOnly" not in android_entries(path)
    assert b"BygramDesktopOnly" not in android_xml(path)
    assert b"Keep %1$s unchanged" in android_xml(path)
    source = entries(path)
    for invalid in (
        '<string name="BygramText">Lost argument</string>',
        '<string-array name="BygramOptions"><item>A</item></string-array>',
        '<string name="Unknown">Extra</string>',
    ):
        path.write_text("<resources>" + invalid + "</resources>", encoding="utf-8")
        try:
            validate_translation(source, entries(path), path)
        except ValueError:
            pass
        else:
            raise AssertionError("Invalid catalog accepted: " + invalid)
subprocess.run([sys.executable, str(ROOT / "Tools/sync_bygram_translations.py"), str(args.catalog), "--check"], check=True)
print(f"PASS: {len(references)} Android resource references, catalog parity, desktop exclusion and import validation")
