#!/usr/bin/env python3
"""Install the official ECDICT SQLite release into ignored server runtime storage."""
import os
import shutil
import sqlite3
import tempfile
import urllib.request
import zipfile
from pathlib import Path

URL = "https://github.com/skywind3000/ECDICT/releases/download/1.0.28/ecdict-sqlite-28.zip"
RUNTIME = Path(__file__).resolve().parents[1] / "runtime"
SOURCES = RUNTIME / "sources"

if __name__ == "__main__":
    SOURCES.mkdir(parents=True, exist_ok=True)
    target = SOURCES / "stardict.db"
    if target.is_file():
        print("ECDICT 已安装：", target)
        raise SystemExit(0)
    with tempfile.TemporaryDirectory(prefix="ecdict-", dir=SOURCES) as folder:
        archive = Path(folder) / "release.zip"
        req = urllib.request.Request(URL, headers={"User-Agent": "StudyDesk/1.0"})
        with urllib.request.urlopen(req, timeout=90) as response, archive.open("wb") as output:
            shutil.copyfileobj(response, output, 1024 * 1024)
        with zipfile.ZipFile(archive) as z, z.open("stardict.db") as source:
            unpacked = Path(folder) / "stardict.db"
            with unpacked.open("wb") as output:
                shutil.copyfileobj(source, output, 1024 * 1024)
        with sqlite3.connect(unpacked) as c:
            count = c.execute("SELECT count(*) FROM stardict").fetchone()[0]
        if count < 1_000_000:
            raise RuntimeError("ECDICT 文件内容不完整")
        os.chmod(unpacked, 0o600)
        os.replace(unpacked, target)
    print("ECDICT 已安装：", target, "词条数：", count)
