"""Secret-safe Tavily usage-aware rotation and persistent search cache."""
import ast
import datetime as dt
import hashlib
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

from .content import ROOT
from .db import dumps

API = "https://api.tavily.com"


class TavilyError(Exception):
    pass


def read_keys():
    path = ROOT / ".env"
    if not path.is_file():
        return []
    values = []
    for line in path.read_text().splitlines():
        match = re.match(r"^\s*(?:export\s+)?TAVILY_API_KEYS\s*=\s*(.*?)\s*$", line)
        if match:
            value = match[1].strip()
            for parser in (json.loads, ast.literal_eval):
                try:
                    values = parser(value)
                    break
                except (ValueError, SyntaxError):
                    pass
            break
    if not isinstance(values, list) or any(not isinstance(v, str) or not v.startswith("tvly-") for v in values):
        return []
    return list(dict.fromkeys(values))


class Tavily:
    def __init__(self, db):
        self.db = db
        self.keys = read_keys()
        self.ids = [hashlib.sha256(key.encode()).hexdigest()[:24] for key in self.keys]
        self.lock = threading.RLock()
        proxy = os.environ.get("STUDYDESK_TAVILY_PROXY")
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({"https": proxy})
                                                   if proxy else urllib.request.ProxyHandler({}))
        with db.connect(True) as c:
            c.execute("CREATE TABLE IF NOT EXISTS tavily_accounts (id TEXT PRIMARY KEY, status TEXT NOT NULL, "
                      "key_usage INTEGER NOT NULL DEFAULT 0, key_limit INTEGER NOT NULL DEFAULT 0, "
                      "account_usage INTEGER NOT NULL DEFAULT 0, account_limit INTEGER NOT NULL DEFAULT 0, "
                      "checked_at REAL NOT NULL DEFAULT 0, cooldown REAL NOT NULL DEFAULT 0)")
            c.execute("CREATE TABLE IF NOT EXISTS tavily_calls (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                      "key_id TEXT NOT NULL, started REAL NOT NULL, credits INTEGER NOT NULL, status TEXT NOT NULL)")
            c.execute("CREATE TABLE IF NOT EXISTS tavily_cache (query_hash TEXT PRIMARY KEY, "
                      "payload TEXT NOT NULL, created REAL NOT NULL)")

    def request(self, key, path, payload=None, timeout=20):
        headers = {"Authorization": "Bearer " + key, "Accept": "application/json"}
        if payload is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(API + path,
                                     data=dumps(payload).encode() if payload is not None else None,
                                     headers=headers)
        with self.opener.open(req, timeout=timeout) as response:
            raw = response.read(1_500_001)
            if len(raw) > 1_500_000:
                raise TavilyError("搜索响应过大")
            return json.loads(raw)

    def usage(self, index, force=False):
        key_id = self.ids[index]
        with self.db.connect() as c:
            row = c.execute("SELECT * FROM tavily_accounts WHERE id=?", (key_id,)).fetchone()
        if row and not force and time.time() - row["checked_at"] < 600:
            return dict(row)
        try:
            result = self.request(self.keys[index], "/usage", timeout=12)
            key, account = result.get("key", {}), result.get("account", {})
            plan_limit = account.get("plan_limit")
            key_limit = key.get("limit") if isinstance(key.get("limit"), int) else plan_limit
            if not isinstance(key_limit, int) or not isinstance(key.get("usage"), int):
                raise TavilyError("用量接口没有返回可用额度")
            record = {"id": key_id, "status": "available", "key_usage": key["usage"],
                      "key_limit": key_limit, "account_usage": int(account.get("plan_usage") or 0),
                      "account_limit": int(plan_limit or key_limit),
                      "checked_at": time.time(), "cooldown": 0}
        except urllib.error.HTTPError as exc:
            record = {"id": key_id, "status": "invalid" if exc.code == 401 else "cooldown", "key_usage": 0,
                      "key_limit": 0, "account_usage": 0, "account_limit": 0,
                      "checked_at": time.time(), "cooldown": time.time() + (120 if exc.code == 429 else 60)}
        except (OSError, ValueError, TavilyError):
            if row:
                return dict(row)
            record = {"id": key_id, "status": "unavailable", "key_usage": 0, "key_limit": 0,
                      "account_usage": 0, "account_limit": 0, "checked_at": time.time(), "cooldown": time.time() + 60}
        with self.db.connect(True) as c:
            c.execute("INSERT OR REPLACE INTO tavily_accounts VALUES (?,?,?,?,?,?,?,?)",
                      tuple(record[k] for k in ("id", "status", "key_usage", "key_limit", "account_usage",
                                                "account_limit", "checked_at", "cooldown")))
        return record

    def summary(self):
        with self.db.connect() as c:
            rows = {r["id"]: r for r in c.execute("SELECT * FROM tavily_accounts")}
        return [{"slot": i+1, "status": rows[key_id]["status"] if key_id in rows else "untested",
                 "remaining": max(0, min(rows[key_id]["key_limit"]-rows[key_id]["key_usage"],
                                         rows[key_id]["account_limit"]-rows[key_id]["account_usage"])) if key_id in rows else None}
                for i, key_id in enumerate(self.ids)]

    def probe_all(self, live_search=True):
        results = []
        for index in range(len(self.keys)):
            record = self.usage(index, force=True)
            state = "usage_ok" if record["status"] == "available" else record["status"]
            if live_search and state == "usage_ok" and self.remaining(record) > 0:
                try:
                    self._search(index, "rigorous definition Cambridge Dictionary", "basic", 1, [], False)
                    state = "search_ok"
                except TavilyError:
                    state = "search_failed"
            results.append({"slot": index+1, "status": state,
                            "remaining": self.summary()[index]["remaining"]})
        return results

    @staticmethod
    def remaining(row):
        if row["status"] != "available" or row["cooldown"] > time.time():
            return 0
        return max(0, min(row["key_limit"]-row["key_usage"], row["account_limit"]-row["account_usage"]))

    @staticmethod
    def allowed(row, domains):
        url = row.get("url", "") if isinstance(row, dict) else ""
        parsed = urllib.parse.urlsplit(url)
        return (parsed.scheme == "https" and bool(parsed.hostname) and
                (not domains or any(parsed.hostname == domain or parsed.hostname.endswith("." + domain)
                                    for domain in domains)))

    def filter_results(self, result, domains):
        return {**result, "results": [row for row in result["results"] if self.allowed(row, domains)]}

    def _search(self, index, query, depth, max_results, domains, exact_match):
        cost = 2 if depth == "advanced" else 1
        params = {"query": query, "search_depth": depth, "max_results": max_results,
                  "include_answer": False, "include_raw_content": False, "include_usage": True,
                  "topic": "general", "exact_match": exact_match}
        if domains:
            params["include_domains"] = domains
        started = time.time()
        try:
            result = self.request(self.keys[index], "/search", params)
            if not isinstance(result.get("results"), list):
                raise TavilyError("搜索没有返回结果列表")
            credits = result.get("usage", {}).get("credits", cost)
            credits = credits if isinstance(credits, int) and 0 < credits <= 20 else cost
            cleaned = {"query": query, "results": [{"title": str(r.get("title", ""))[:180],
                         "url": str(r.get("url", ""))[:1000], "content": str(r.get("content", ""))[:1400],
                         "score": r.get("score", 0)} for r in result["results"][:max_results]
                         if self.allowed(r, domains)],
                       "credits": credits, "searchedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")}
            with self.db.connect(True) as c:
                c.execute("UPDATE tavily_accounts SET key_usage=key_usage+?,account_usage=account_usage+? WHERE id=?",
                          (credits, credits, self.ids[index]))
                c.execute("INSERT INTO tavily_calls(key_id,started,credits,status) VALUES (?,?,?,?)",
                          (self.ids[index], started, credits, "ok"))
            return cleaned
        except urllib.error.HTTPError as exc:
            with self.db.connect(True) as c:
                c.execute("UPDATE tavily_accounts SET status=?,cooldown=? WHERE id=?",
                          ("exhausted" if exc.code in (432, 433) else "invalid" if exc.code == 401 else "available",
                           time.time() + (180 if exc.code == 429 else 0), self.ids[index]))
                c.execute("INSERT INTO tavily_calls(key_id,started,credits,status) VALUES (?,?,?,?)",
                          (self.ids[index], started, 0, "http_" + str(exc.code)))
            raise TavilyError("搜索接口 HTTP " + str(exc.code)) from None
        except (OSError, ValueError, TavilyError) as exc:
            with self.db.connect(True) as c:
                c.execute("UPDATE tavily_accounts SET cooldown=? WHERE id=?", (time.time()+60, self.ids[index]))
            raise TavilyError("搜索接口暂不可用") from None

    def search(self, query, depth="basic", max_results=4, domains=None, ttl_days=30, exact_match=False):
        if not isinstance(query, str) or not 1 <= len(query) <= 400 or depth not in ("basic", "advanced"):
            raise ValueError("搜索参数不正确")
        domains = domains or []
        if not isinstance(domains, list) or len(domains) > 8 or any(not re.fullmatch(r"[a-z0-9.-]+", d) for d in domains):
            raise ValueError("搜索域名不正确")
        key = hashlib.sha256(dumps([query, depth, max_results, domains, exact_match]).encode()).hexdigest()
        with self.lock:
            with self.db.connect() as c:
                cached = c.execute("SELECT payload,created FROM tavily_cache WHERE query_hash=?", (key,)).fetchone()
            if cached and time.time() - cached["created"] < ttl_days*86400:
                return {**self.filter_results(json.loads(cached["payload"]), domains), "cached": True}
            candidates = [(self.remaining(self.usage(i)), i) for i in range(len(self.keys))]
            candidates.sort(key=lambda item: (-item[0], item[1]))
            for remaining, index in candidates:
                if remaining < (2 if depth == "advanced" else 1):
                    continue
                try:
                    result = self._search(index, query, depth, max_results, domains, exact_match)
                    with self.db.connect(True) as c:
                        c.execute("INSERT OR REPLACE INTO tavily_cache VALUES (?,?,?)", (key, dumps(result), time.time()))
                    return {**result, "cached": False}
                except TavilyError:
                    continue
        raise TavilyError("所有 Tavily Key 暂不可用或额度已用完")
