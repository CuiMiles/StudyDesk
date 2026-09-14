#!/usr/bin/env python3
"""OpenAI-compatible offline batch generator for 5-step vocabulary content."""
import argparse
import datetime
import hashlib
import json
import os
import re
import sqlite3
import sys
from typing import Tuple, Dict, List, Optional, Any
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FIELDS = [
    "concrete_image",
    "synonyms_comparison",
    "register_and_contexts",
    "collocations",
    "associations",
    "integrated_example",
]

def count_words(text: str) -> int:
    return len(re.findall(r"[A-Za-z0-9]+(?:'[A-Za-z0-9]+)?", text))

def validate_payload(data: dict, target_word: str) -> Tuple[bool, str]:
    if not isinstance(data, dict):
        return False, "Payload must be a JSON object"
    for field in REQUIRED_FIELDS:
        val = data.get(field)
        if not val or not isinstance(val, str) or not val.strip():
            return False, f"Missing or empty required field: {field}"

    synonyms = data.get("synonyms_comparison", "")
    # Check for at least 3 distinct comparison terms or mentions
    synonym_items = re.findall(r"(?:^|\n|\d+\.|\*|-)\s*([A-Za-z -]{2,})", synonyms)
    if len(synonym_items) < 3 and len(re.findall(r"[A-Za-z]{3,}", synonyms)) < 6:
        return False, "synonyms_comparison must contrast at least 3 synonyms"

    example = data.get("integrated_example", "")
    words = count_words(example)
    if words < 40 or words > 120:
        return False, f"integrated_example word count {words} out of expected 50-100 range"

    return True, "ok"

def compute_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def check_tips_file(tips_path: Path) -> str:
    if not tips_path.exists():
        raise ValueError(f"Tips file does not exist: {tips_path}")
    content = tips_path.read_text(encoding="utf-8").strip()
    if not content:
        raise ValueError(f"5steps_tips.txt is empty (0 bytes). Cannot proceed without complete prompt instructions: {tips_path}")
    return content

def run_batch(
    tips_path: Path,
    db_path: Path,
    model: str = "gpt-4o-mini",
    api_base: str = "https://api.openai.com/v1",
    api_key_env: str = "OPENAI_API_KEY",
    limit: int = 10,
    dry_run: bool = True,
    resume: bool = True,
    report_path: Optional[Path] = None,
) -> Dict[str, Any]:
    tips = check_tips_file(tips_path)
    prompt_hash = compute_hash(tips)

    if not db_path.exists():
        raise FileNotFoundError(f"Database not found: {db_path}")

    conn = sqlite3.connect(str(db_path))
    conn.execute("PRAGMA foreign_keys = ON;")

    # Find words with valid primary sense
    query = """
    SELECT w.id, w.headword, s.id, s.definition_en, s.examples_json
    FROM word w
    JOIN sense s ON w.id = s.word_id
    WHERE w.status != 'missing'
    GROUP BY w.id
    ORDER BY w.id
    """
    candidates = conn.execute(query).fetchall()

    if resume:
        generated_ids = set(r[0] for r in conn.execute("SELECT word_id FROM generation").fetchall())
        candidates = [c for c in candidates if c[0] not in generated_ids]

    if limit > 0:
        candidates = candidates[:limit]

    report = {
        "timestamp": datetime.datetime.utcnow().isoformat() + "Z",
        "dry_run": dry_run,
        "prompt_sha256": prompt_hash,
        "model": model,
        "total_queued": len(candidates),
        "succeeded": 0,
        "failed": 0,
        "errors": [],
    }

    if dry_run:
        report["status"] = f"Dry-run completed successfully. {len(candidates)} candidates inspected."
        conn.close()
        if report_path:
            report_path.parent.mkdir(parents=True, exist_ok=True)
            report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        return report

    api_key = os.getenv(api_key_env, "").strip()
    if not api_key:
        conn.close()
        raise ValueError(f"Environment variable {api_key_env} is not set or empty.")

    url = api_base.rstrip("/") + "/chat/completions"

    for wid, headword, sense_id, defn, examples_json in candidates:
        input_fingerprint = compute_hash(f"{headword}:{sense_id}:{defn}")
        user_prompt = (
            f"Target word: {headword}\n"
            f"Sense ID: {sense_id}\n"
            f"Definition: {defn}\n"
            f"Follow the 5-step format instructions and output strictly valid JSON matching required fields."
        )
        req_body = {
            "model": model,
            "messages": [
                {"role": "system", "content": tips},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.3,
            "response_format": {"type": "json_object"},
        }
        req_data = json.dumps(req_body).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=req_data,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {api_key}",
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(req, timeout=45) as resp:
                result = json.loads(resp.read().decode("utf-8"))
            content_str = result["choices"][0]["message"]["content"]
            payload = json.loads(content_str)
            valid, reason = validate_payload(payload, headword)
            if not valid:
                report["failed"] += 1
                report["errors"].append({"word": headword, "reason": reason})
                continue

            now_str = datetime.datetime.utcnow().isoformat() + "Z"
            with conn:
                conn.execute(
                    """
                    INSERT OR REPLACE INTO generation
                    (word_id, sense_id, prompt_sha256, input_sha256, model, generated_at, status, payload_json)
                    VALUES (?, ?, ?, ?, ?, ?, 'generated', ?)
                    """,
                    (wid, sense_id, prompt_hash, input_fingerprint, model, now_str, json.dumps(payload, ensure_ascii=False)),
                )
            report["succeeded"] += 1

        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                conn.close()
                raise PermissionError(f"HTTP {e.code} Authentication/Permission failed. Aborting immediately.")
            report["failed"] += 1
            report["errors"].append({"word": headword, "reason": f"HTTP {e.code}: {e.reason}"})
        except Exception as e:
            report["failed"] += 1
            report["errors"].append({"word": headword, "reason": str(e)})

    conn.close()
    if report_path:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    return report

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Batch generate 5-step content")
    parser.add_argument("--tips", type=Path, default=ROOT / "sources/5steps_tips.txt")
    parser.add_argument("--db", type=Path, default=ROOT / "app/src/main/assets/content.db")
    parser.add_argument("--model", type=str, default="gpt-4o-mini")
    parser.add_argument("--api-base", type=str, default="https://api.openai.com/v1")
    parser.add_argument("--api-key-env", type=str, default="OPENAI_API_KEY")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--dry-run", action="store_true", default=False)
    parser.add_argument("--no-dry-run", dest="dry_run", action="store_false")
    parser.add_argument("--report", type=Path, default=ROOT / "docs/generation-report.json")
    args = parser.parse_args()

    try:
        res = run_batch(
            tips_path=args.tips,
            db_path=args.db,
            model=args.model,
            api_base=args.api_base,
            api_key_env=args.api_key_env,
            limit=args.limit,
            dry_run=args.dry_run,
            report_path=args.report,
        )
        print(json.dumps(res, ensure_ascii=False, indent=2))
    except Exception as err:
        print(f"Error: {err}", file=sys.stderr)
        sys.exit(1)
