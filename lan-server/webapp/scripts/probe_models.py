#!/usr/bin/env python3
"""Probe only text models present in the user's positive-quota snapshot."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from webapp.db import Database
from webapp.gemini import Gemini

if __name__ == "__main__":
    db = Database(Path(__file__).resolve().parents[1] / "runtime/studydesk.sqlite3")
    result = Gemini(db).probe()
    target = Path(__file__).resolve().parents[2] / "docs/gemini_model_probe.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    for m in result["models"]:
        print(m["id"], m["status"], m.get("message"), f"used={m['used']}/{m['rpd']}", flush=True)
