"""Smoke test the distributable installer without a checkout in its working directory."""
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
TARGETS = (
    ".agents/skills/bygram-plugin",
    ".claude/skills/bygram-plugin",
    ".copilot/skills/bygram-plugin",
    "bygram-skills/deepseek",
)


def run(*args, env=None, cwd=None, check=True):
    return subprocess.run([sys.executable, *args], env=env, cwd=cwd, text=True,
                          capture_output=True, check=check)


def main():
    with tempfile.TemporaryDirectory(prefix="bygram-skill-installer-") as directory:
        work = Path(directory)
        installer = work / "install_bygram_skills.py"
        home = work / "home"
        env = dict(os.environ, HOME=str(home), USERPROFILE=str(home), PYTHONDONTWRITEBYTECODE="1")
        env.pop("PYTHONPATH", None)
        run(ROOT / "Tools/install_bygram_skills.py", "--build", installer)
        run(installer, "claude", env=env, cwd=work)
        assert [name for name in TARGETS if (home / name).exists()] == [TARGETS[1]]
        run(installer, env=env, cwd=work)
        assert run(installer, env=env, cwd=work).stdout.count("Already installed:") == len(TARGETS)

        for name in TARGETS:
            skill = home / name
            prompt = skill / "references/docs/plugins/deepseek-prompt.md"
            for doc in (skill / "SKILL.md", prompt):
                instructions = doc.read_text(encoding="utf-8")
                links = re.findall(r"\]\(([^() \n]+)\)", instructions)
                assert all(link.startswith("https://") for link in links), (doc, links)
                assert "https://byxarek.site/TEST-PROJECT/BYgram/docs/#sdk" in instructions, doc
                assert "agent-workflow.md" not in instructions, doc
            assert list(prompt.parent.glob("*.md")) == [prompt]

        standalone = home / TARGETS[0] / "references"
        package = work / "hello.byplugin"
        run(standalone / "Tools/package_plugin.py", standalone / "docs/plugins/examples/hello",
            package, env=env, cwd=work)
        with zipfile.ZipFile(package) as archive:
            assert set(archive.namelist()) == {"manifest.json", "main.py"} and archive.testzip() is None
        legacy = work / "legacy.plugin"
        legacy.write_text(
            'from base_plugin import BasePlugin\n'
            '__id__ = "installer_test"\n'
            '__name__ = "Installer test"\n'
            '__version__ = "1.0.0"\n'
            'class TestPlugin(BasePlugin):\n    pass\n',
            encoding="utf-8",
        )
        converted = work / "converted.byplugin"
        run(standalone / "Tools/port_extera_plugin.py", legacy, converted, env=env, cwd=work)
        with zipfile.ZipFile(converted) as archive:
            assert archive.read("main.py") == legacy.read_bytes() and archive.testzip() is None
        run(standalone / "Tools/plugin_dev.py", "--help", env=env, cwd=work)

        skill = home / TARGETS[1] / "SKILL.md"
        skill.write_text("Local edits", encoding="utf-8")
        conflict = run(installer, "claude", env=env, cwd=work, check=False)
        assert conflict.returncode == 1 and "different content" in conflict.stderr
        assert skill.read_text(encoding="utf-8") == "Local edits"
        print("PASS: offline install, selection, repeat, conflict, references, packaging and conversion")


if __name__ == "__main__":
    main()
