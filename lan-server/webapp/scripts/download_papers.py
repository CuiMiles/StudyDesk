#!/usr/bin/env python3
"""Download public author/conference PDFs for this user's local reading.
No Gemini calls. Keep original attribution; do not relicense the downloaded papers.
"""
import concurrent.futures
import hashlib
import json
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fetch(paper):
    folder = ROOT / "runtime/papers"
    folder.mkdir(parents=True, exist_ok=True)
    pdf = folder / (paper["id"] + ".pdf")
    if not pdf.exists():
        req = urllib.request.Request(paper["pdf"], headers={"User-Agent": "StudyDesk-local-reader/1.0"})
        # Conference hosts are directly accessible on this machine.
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=45) as r:
            data = r.read(35_000_000)
        if not data.startswith(b"%PDF-"):
            raise ValueError("Not a PDF: " + paper["id"])
        tmp = pdf.with_suffix(".tmp")
        tmp.write_bytes(data)
        tmp.replace(pdf)
    subprocess.run(["pdftotext", "-layout", str(pdf), str(pdf.with_suffix(".txt"))], check=True)
    return {"id": paper["id"], "bytes": pdf.stat().st_size,
            "sha256": hashlib.sha256(pdf.read_bytes()).hexdigest(), "source": paper["pdf"]}


if __name__ == "__main__":
    papers = json.loads((ROOT / "content/papers.json").read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(fetch, papers))
    (ROOT / "content/downloads.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps(results, ensure_ascii=False, indent=2))
