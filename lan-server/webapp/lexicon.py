"""On-demand, source-attributed word cache. Large ECDICT data stays on the LAN host."""
import concurrent.futures
import contextlib
import datetime as dt
import json
import re
import sqlite3
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from .db import dumps

SOURCES = ("ecdict", "dictionaryapi", "tatoeba", "datamuse")
WORD = re.compile(r"[A-Za-z][A-Za-z' -]{0,63}\Z")


class Lexicon:
    def __init__(self, runtime):
        self.path = Path(runtime) / "lexicon.sqlite3"
        self.ecdict = Path(runtime) / "sources/stardict.db"
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        with self.connect() as c:
            c.execute("CREATE TABLE IF NOT EXISTS source_cache (word TEXT NOT NULL, source TEXT NOT NULL, "
                      "payload TEXT NOT NULL, status TEXT NOT NULL, fetched_at TEXT NOT NULL, retry_at REAL NOT NULL, "
                      "PRIMARY KEY(word,source))")
            c.execute("CREATE TABLE IF NOT EXISTS lesson_cache (word TEXT NOT NULL, prompt_hash TEXT NOT NULL, "
                      "model TEXT NOT NULL, payload TEXT NOT NULL, approved INTEGER NOT NULL DEFAULT 0, "
                      "created_at TEXT NOT NULL, PRIMARY KEY(word,prompt_hash))")
        self.path.chmod(0o600)

    @contextlib.contextmanager
    def connect(self):
        c = sqlite3.connect(self.path, timeout=15)
        c.row_factory = sqlite3.Row
        c.execute("PRAGMA busy_timeout=15000")
        try:
            yield c
            c.commit()
        finally:
            c.close()

    def cached(self, word, source):
        with self.connect() as c:
            row = c.execute("SELECT payload,status,retry_at FROM source_cache WHERE word=? AND source=?", (word, source)).fetchone()
        if not row:
            return None
        return {"data": json.loads(row["payload"]), "status": row["status"], "retryAt": row["retry_at"]}

    def save(self, word, source, data, status="ok", retry_at=0):
        stamp = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
        with self.connect() as c:
            c.execute("INSERT INTO source_cache VALUES (?,?,?,?,?,?) ON CONFLICT(word,source) DO UPDATE SET "
                      "payload=excluded.payload,status=excluded.status,fetched_at=excluded.fetched_at,retry_at=excluded.retry_at",
                      (word, source, dumps(data), status, stamp, retry_at))

    @staticmethod
    def request(url, timeout=8):
        req = urllib.request.Request(url, headers={"User-Agent": "StudyDesk/1.0 (personal learning)", "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read(750_001)
            if len(raw) > 750_000:
                raise ValueError("外部词典返回数据过大")
            return json.loads(raw)

    def ecdict_data(self, word):
        if not self.ecdict.is_file():
            raise FileNotFoundError("ECDICT 尚未安装到服务器本地")
        uri = self.ecdict.resolve().as_uri() + "?mode=ro"
        with sqlite3.connect(uri, uri=True) as c:
            row = c.execute("SELECT word,phonetic,definition,translation,pos,collins,oxford,tag,bnc,frq,exchange "
                            "FROM stardict WHERE word=? COLLATE NOCASE LIMIT 1", (word,)).fetchone()
        return dict(zip(("word", "phonetic", "definition", "translation", "pos", "collins", "oxford", "tag", "bnc", "frq", "exchange"), row)) if row else {}

    def dictionary_data(self, word):
        data = self.request("https://api.dictionaryapi.dev/api/v2/entries/en/" + urllib.parse.quote(word), timeout=4)
        if not isinstance(data, list):
            return {}
        entries = []
        for entry in data[:3]:
            if not isinstance(entry, dict):
                continue
            meanings = []
            for meaning in entry.get("meanings", [])[:5]:
                meanings.append({"pos": meaning.get("partOfSpeech", ""),
                                 "definitions": [{"definition": d.get("definition", ""), "example": d.get("example", "")}
                                                 for d in meaning.get("definitions", [])[:3]]})
            entries.append({"word": entry.get("word", ""), "phonetic": entry.get("phonetic", ""),
                            "phonetics": [{"text": p.get("text", ""), "audio": p.get("audio", "")}
                                          for p in entry.get("phonetics", [])[:8]],
                            "meanings": meanings, "license": entry.get("license", {})})
        return {"entries": entries}

    def tatoeba_data(self, word):
        params = urllib.parse.urlencode({"q": word, "lang": "eng", "sort": "relevance", "word_count": "5-20"})
        data = self.request("https://api.tatoeba.org/v1/sentences?" + params)
        rows = data.get("data", []) if isinstance(data, dict) else []
        result = []
        target = re.compile(r"\b" + re.escape(word) + r"\b", re.I)
        for row in rows:
            sentence = row.get("text", "") if isinstance(row, dict) else ""
            if not target.search(sentence) or not 10 <= len(sentence) <= 180:
                continue
            result.append({"id": row.get("id"), "text": sentence, "owner": row.get("owner", ""),
                           "license": row.get("license", "CC BY 2.0 FR")})
            if len(result) == 3:
                break
        return {"sentences": result}

    def datamuse_data(self, word):
        base = "https://api.datamuse.com/words?"
        result = {}
        for name, params in (("synonyms", {"rel_syn": word, "max": 8}),
                             ("associations", {"rel_trg": word, "max": 8}),
                             ("followers", {"rel_bga": word, "max": 6})):
            data = self.request(base + urllib.parse.urlencode(params))
            result[name] = [row["word"] for row in data if isinstance(row, dict) and isinstance(row.get("word"), str)] if isinstance(data, list) else []
        return result

    def get(self, raw_word, remote=True):
        word = raw_word.strip().casefold()
        if not WORD.fullmatch(word):
            raise ValueError("单词格式不正确")
        functions = {"ecdict": self.ecdict_data, "dictionaryapi": self.dictionary_data,
                     "tatoeba": self.tatoeba_data, "datamuse": self.datamuse_data}
        with self.lock:
            missing = [source for source in SOURCES if (source == "ecdict" or remote)
                       and (not (row := self.cached(word, source)) or row["status"] != "ok" and row["retryAt"] < time.time())]
            if missing:
                with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
                    futures = {source: pool.submit(functions[source], word) for source in missing}
                    for source, future in futures.items():
                        try:
                            self.save(word, source, future.result(), "ok")
                        except (OSError, ValueError, urllib.error.URLError, sqlite3.Error):
                            self.save(word, source, {}, "unavailable", time.time() + 3600)
            return {"word": word, "sources": {source: self.cached(word, source) or
                    {"data": {}, "status": "pending", "retryAt": 0} for source in SOURCES}}
