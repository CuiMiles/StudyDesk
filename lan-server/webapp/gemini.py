"""Gemini REST client. Credentials are read only from the repository root .env.

The quota snapshot is an upper bound, never a claim about remaining upstream quota.
Every outgoing generation (including probes/failures) is reserved transactionally.
"""
import concurrent.futures
import datetime as dt
import json
import os
import re
import time
import urllib.error
import urllib.request
from pathlib import Path
from zoneinfo import ZoneInfo

from .db import dumps, now, quota_day

ROOT = Path(__file__).resolve().parents[1]
API = "https://generativelanguage.googleapis.com/v1beta/"
IDS = [
    ("gemini-3.8-flash", "Gemini 3.8 Flash"),
    ("gemini-3.7-flash", "Gemini 3.7 Flash"),
    ("gemini-3.6-flash", "Gemini 3.6 Flash"),
    ("gemini-3.5-flash", "Gemini 3.5 Flash"),
    ("gemini-3-flash-preview", "Gemini 3 Flash"),
    ("gemini-2.5-flash", "Gemini 2.5 Flash"),
    ("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite"),
    ("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite"),
    ("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
    ("gemma-4-31b-it", "Gemma 4 31B"),
    ("gemma-4-26b-a4b-it", "Gemma 4 26B"),
]


class AIError(Exception):
    pass


def env_values():
    path = ROOT / ".env"
    if not path.is_file():
        return {}
    result = {}
    for line in path.read_text().splitlines():
        match = re.match(r"^\s*(?:export\s+)?(GEMINI_API_KEY\d*(?:_QUOTA_GROUP)?)\s*=\s*(.*?)\s*$", line)
        if match:
            result[match[1]] = match[2].strip("\"'")
    return result


def accounts():
    values = env_values()
    result, seen = [], set()
    for name in sorted(values, key=lambda v: (len(v), v)):
        if not re.fullmatch(r"GEMINI_API_KEY\d*", name) or not values[name] or values[name] in seen:
            continue
        seen.add(values[name])
        alias = "key" + (name.removeprefix("GEMINI_API_KEY") or "1")
        group = values.get(name + "_QUOTA_GROUP", alias)
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,50}", group):
            group = alias
        result.append({"id": alias, "group": group, "env": name})
    return result


def read_key(account="key1"):
    values = env_values()
    return next((values.get(a["env"], "") for a in accounts() if a["id"] == account), "")


def parse_json(text):
    text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip())
    try:
        obj = json.loads(text)
    except (ValueError, TypeError):
        raise AIError("模型未返回完整的结构化点评，请重试；译文已保存。") from None
    if not isinstance(obj, dict):
        raise AIError("模型返回格式异常，请重试。")
    return obj


class Gemini:
    def __init__(self, db):
        self.db = db
        config = json.loads((ROOT / "docs/gemini_api_limits.json").read_text())
        limits = {r["name"]: r for r in config["model_limits"]}
        self.config = [{"id": mid, "name": label, "priority": i + 1, **{
            k: limits[label][k] for k in ("rpm", "tpm", "rpd")}} for i, (mid, label) in enumerate(IDS)]
        self.profiles = json.loads((ROOT / "webapp/content/ai_profiles.json").read_text())
        order = self.profiles["modelOrder"]
        self.config.sort(key=lambda m: order.index(m["id"]))
        for i, cfg in enumerate(self.config):
            cfg["priority"] = i + 1
        # An explicit proxy affects only this client. Otherwise respect the existing process configuration.
        proxy = os.environ.get("STUDYDESK_GEMINI_PROXY")
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({"https": proxy})
                                                  if proxy else urllib.request.ProxyHandler())

    def request(self, endpoint, payload=None, timeout=35, account="key1"):
        key = read_key(account)
        if not key:
            raise AIError("未在项目根目录 .env 找到 GEMINI_API_KEY。")
        req = urllib.request.Request(API + endpoint,
                                     data=dumps(payload).encode() if payload is not None else None,
                                     headers={"x-goog-api-key": key, "Content-Type": "application/json"})
        with self.opener.open(req, timeout=timeout) as response:
            return json.load(response)

    def state(self, mid, c, account="key1"):
        r = c.execute("SELECT value FROM models WHERE id=?", (account + ":" + mid,)).fetchone()
        return json.loads(r[0]) if r else {"status": "untested", "message": "尚未测试"}

    def update(self, mid, values, account="key1"):
        with self.db.connect(True) as c:
            state = self.state(mid, c, account)
            state.update(values)
            c.execute("INSERT OR REPLACE INTO models VALUES (?,?)", (account + ":" + mid, dumps(state)))

    def summary(self):
        today = quota_day()
        pacific = dt.datetime.now(ZoneInfo("America/Los_Angeles"))
        reset = dt.datetime.combine(pacific.date() + dt.timedelta(days=1), dt.time(), pacific.tzinfo)
        rows = []
        keys = accounts()
        with self.db.connect() as c:
            for cfg in self.config:
                per_key = []
                for account in keys:
                    state = self.state(cfg["id"], c, account["id"])
                    counts = c.execute("SELECT count(*) AS used, sum(status='ok') AS succeeded "
                                       "FROM ai_calls WHERE model=? AND day=? AND account=?",
                                       (cfg["id"], today, account["group"])).fetchone()
                    status = state.get("status")
                    if status == "exhausted" and state.get("day") != today:
                        status = "available"
                    if status == "available" and state.get("cooldown", 0) > time.time():
                        status = "cooldown"
                    per_key.append({**state, "account": account["id"], "group": account["group"],
                                    "status": status, "used": counts["used"], "rpd": cfg["rpd"],
                                    "succeeded": counts["succeeded"] or 0,
                                    "remaining": max(0, cfg["rpd"] - counts["used"])})
                grouped = {p["group"]: p for p in per_key}
                status = next((state for state in ("available", "cooldown", "untested", "exhausted")
                               if any(p["status"] == state for p in per_key)), "unavailable")
                rows.append({**cfg, "status": status, "accounts": per_key, "perAccountLimit": cfg["rpd"],
                             "rpd": cfg["rpd"] * len(grouped), "used": sum(p["used"] for p in grouped.values()),
                             "succeeded": sum(p["succeeded"] for p in grouped.values()),
                             "message": " · ".join(p["account"] + " " + p.get("message", "尚未检测") for p in per_key),
                             "remaining": sum(p["remaining"] for p in grouped.values())})
        return {"configured": bool(keys), "accountCount": len(keys), "accounts": [{"id": k["id"], "group": k["group"]} for k in keys],
                "quotaDay": today, "resetAt": reset.isoformat(),
                "timezone": "America/Los_Angeles", "models": rows,
                "note": "本地计数含测试和失败调用，并非 Google 剩余额度。配额按项目共享；同项目 Key 请配置相同 QUOTA_GROUP，其他程序用量不可见。先遍历强模型的所有 Key，再降级。",
                "profiles": self.profiles}

    def reserve(self, cfg, purpose, tokens, probe=False):
        with self.db.connect(True) as c:
            account, group = cfg.get("account", "key1"), cfg.get("group", "key1")
            state = self.state(cfg["id"], c, account)
            today, current = quota_day(), time.time()
            if not probe:
                if state.get("status") not in ("available", "exhausted"):
                    return None
                if state.get("status") == "exhausted" and state.get("day") == today:
                    return None
                if state.get("cooldown", 0) > current:
                    return None
            used = c.execute("SELECT count(*) FROM ai_calls WHERE model=? AND day=? AND account=?",
                             (cfg["id"], today, group)).fetchone()[0]
            minute = c.execute("SELECT count(*),coalesce(sum(input_tokens),0) FROM ai_calls "
                               "WHERE model=? AND started>? AND account=?", (cfg["id"], current - 60, group)).fetchone()
            if used >= cfg["rpd"] or minute[0] >= cfg["rpm"] or minute[1] + tokens > cfg["tpm"]:
                return None
            return c.execute("INSERT INTO ai_calls(model,day,started,purpose,status,input_tokens,account) "
                             "VALUES (?,?,?,?,?,?,?)", (cfg["id"], today, current, purpose, "pending", tokens, group)).lastrowid

    def generate_one(self, cfg, prompt, purpose, probe=False):
        profile = self.profiles["probe" if probe else purpose]
        system = (ROOT / "webapp/content" / profile["systemFile"]).read_text() if profile.get("systemFile") else ""
        # A UTF-8 byte bound is deliberately conservative for local TPM admission.
        estimated = len((prompt + system).encode())
        call_id = self.reserve(cfg, purpose, estimated, probe)
        if call_id is None:
            raise AIError("本地频率或每日预算已满")
        mid = cfg["id"]
        account = cfg.get("account", "key1")
        settings = {"temperature": profile["gemini3Temperature"] if mid.startswith("gemini-3") else profile["temperature"],
                    "maxOutputTokens": profile["maxOutputTokens"]}
        if not mid.startswith("gemma"):
            settings["responseMimeType"] = "application/json"
        if not probe and mid.startswith("gemini-3") and "lite" not in mid:
            settings["thinkingConfig"] = {"thinkingLevel": profile["thinkingLevel"]}
        if system and mid.startswith("gemma"):
            prompt = system + "\n\n用户学习数据：\n" + prompt
        payload = {"contents": [{"role": "user", "parts": [{"text": prompt}]}], "generationConfig": settings}
        if system and not mid.startswith("gemma"):
            payload["systemInstruction"] = {"parts": [{"text": system}]}
        started = time.monotonic()
        status, meta = "failed", {}
        try:
            response = self.request("models/" + mid + ":generateContent", payload, profile["timeout"], account)
            meta = response.get("usageMetadata", {})
            candidates = response.get("candidates", [])
            if not candidates or candidates[0].get("finishReason") not in (None, "STOP"):
                raise AIError("响应为空、被拦截或输出不完整")
            parts = candidates[0].get("content", {}).get("parts", [])
            text = "".join(p.get("text", "") for p in parts if not p.get("thought"))
            result = parse_json(text)
            if probe and result.get("ok") is not True:
                raise AIError("测试响应格式不正确")
            status = "ok"
            self.update(mid, {"status": "available", "checkedAt": now(), "message": "响应正常",
                              "latencyMs": round((time.monotonic() - started) * 1000), "cooldown": 0}, account)
            return result
        except urllib.error.HTTPError as exc:
            # Never persist or return raw upstream errors (which may contain request material).
            error_body = exc.read(100000).decode(errors="replace")
            daily = exc.code == 429 and bool(re.search(r"per.?day|perday|daily|limit:\s*0", error_body, re.I))
            state = "exhausted" if daily else "unavailable" if probe or exc.code in (400, 401, 403, 404) else "available"
            msg = f"HTTP {exc.code}" + (" · 当日额度不可用" if daily else " · 暂不可用")
            self.update(mid, {"status": state, "checkedAt": now(), "message": msg, "day": quota_day(),
                              "cooldown": time.time() + (90 if exc.code == 429 else 180)}, account)
            raise AIError(msg) from None
        except (OSError, ValueError, AIError) as exc:
            msg = str(exc) if isinstance(exc, AIError) else "连接超时或网络不可用"
            self.update(mid, {"status": "unavailable" if probe else "available", "checkedAt": now(),
                              "message": msg, "cooldown": time.time() + 180}, account)
            raise AIError(msg) from None
        finally:
            with self.db.connect(True) as c:
                c.execute("UPDATE ai_calls SET status=?,input_tokens=?,output_tokens=? WHERE id=?",
                          (status, meta.get("promptTokenCount", estimated),
                           meta.get("candidatesTokenCount", 0) + meta.get("thoughtsTokenCount", 0), call_id))

    def probe(self, only_untested=False):
        tasks = []
        for account in accounts():
            try:
                discovered = self.request("models?pageSize=1000", account=account["id"])
                valid = {m["name"].removeprefix("models/") for m in discovered.get("models", [])
                         if "generateContent" in m.get("supportedGenerationMethods", [])}
            except Exception:
                for cfg in self.config:
                    self.update(cfg["id"], {"status": "unavailable", "message": "模型列表获取失败", "checkedAt": now()}, account["id"])
                continue
            for cfg in self.config:
                with self.db.connect() as c:
                    if only_untested and self.state(cfg["id"], c, account["id"])["status"] != "untested":
                        continue
                tasks.append({**cfg, "account": account["id"], "group": account["group"], "discovered": cfg["id"] in valid})
        def run(cfg):
            if not cfg["discovered"]:
                self.update(cfg["id"], {"status": "unavailable", "message": "接口未列出此模型", "checkedAt": now()}, cfg["account"])
                return
            try:
                self.generate_one(cfg, 'Return only JSON: {"ok":true}', "probe", True)
            except AIError:
                pass
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            list(pool.map(run, tasks))
        return self.summary()

    def generate(self, prompt, purpose, validate=None):
        failures = []
        for cfg in self.config:
            for account in accounts():
                try:
                    result = self.generate_one({**cfg, "account": account["id"], "group": account["group"]}, prompt, purpose)
                    if validate:
                        validate(result)
                    return result, cfg["id"] + " · " + account["id"]
                except AIError as exc:
                    failures.append(str(exc))
        raise AIError("当前可用模型均未完成请求。请在设置中查看额度或重新检测；你的输入已保留。")
