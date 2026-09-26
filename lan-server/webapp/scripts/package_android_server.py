#!/usr/bin/env python3
"""Copy the reproducible LAN server into the Android repo, excluding all runtime data and credentials."""
import argparse
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--destination", default=str(ROOT / "StudyDesk/lan-server"))
    args = parser.parse_args()
    dest = Path(args.destination).resolve()
    if dest == ROOT:
        parser.error("destination must differ from the source checkout")
    dest.mkdir(parents=True, exist_ok=True)
    ignore = shutil.ignore_patterns("runtime", "__pycache__", "*.pyc", "artifacts", ".env", ".env.*")
    shutil.copytree(ROOT / "webapp", dest / "webapp", dirs_exist_ok=True, ignore=ignore)
    files = ["docs/gemini_api_limits.json", "miniprogram/core/vocabulary/catalog.js"]
    files += [f"miniprogram/worddata{i}/data.js" for i in range(6)]
    files += [str(p.relative_to(ROOT)) for p in (ROOT / "miniprogram/vocabulary/licenses").glob("*.txt")]
    for name in files:
        target = dest / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / name, target)
    (dest / ".env.example").write_text("GEMINI_API_KEY=\nGEMINI_API_KEY2=\nGEMINI_API_KEY3=\n")
    (dest / "README.md").write_text("# StudyDesk LAN server\n\nSee [the complete deployment and usage guide](webapp/README.md).\n\nRun from this directory with Python 3.11+: `python3 -m webapp.server --port 8765`.\nCopy `.env.example` to `.env` and fill in your own keys on the server only. No provider keys or user study records are bundled.\n\nOptional pre-generated vocabulary content is reused read-only from `../app/src/main/assets/content.db`.\n")
    print("Packaged source, dictionary shards and licenses into", dest)
