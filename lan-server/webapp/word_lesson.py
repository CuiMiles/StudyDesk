"""Staged five-step mnemonic lessons; only Gemini 3.5 Flash-Lite is eligible."""
import hashlib
import json
import re

from .content import ROOT
from .db import dumps, now, quota_day
from .gemini import AIError, LESSON_SCHEMA, VERIFY_SCHEMA, accounts
from .tavily import TavilyError

MODEL = "gemini-3.5-flash-lite"
VALIDATION_VERSION = "3"
SYSTEM = ROOT / "webapp/content/word_lesson_system.txt"
FIELDS = ("image", "spectrum", "register", "contexts", "tone", "collocations", "network", "integrated")


def clean(value, max_length=400):
    if not isinstance(value, str) or not value.strip() or len(value) > max_length:
        raise AIError("词义解析内容缺失或过长")
    # The UI supplies semantic headings and emphasis; model markup is never displayed verbatim.
    return re.sub(r"(?:\*\*|__|`|^\s*#{1,4}\s*)", "", value.strip())


def validate_lesson(value, target="", prior=None):
    if not isinstance(value, dict) or any(field not in value for field in FIELDS):
        raise AIError("词义解析结构不完整")
    result = {k: clean(value[k], 280) for k in ("image", "register", "tone", "network")}
    for name, expected, fields in (("spectrum", (3, 5), ("word", "contrast")),
                                   ("contexts", (2, 2), ("example", "note")),
                                   ("collocations", (2, 3), ("phrase", "note"))):
        rows = value[name]
        if not isinstance(rows, list) or not expected[0] <= len(rows) <= expected[1]:
            raise AIError("词义解析条目不足")
        result[name] = [{key: clean(row.get(key), 180) for key in fields} for row in rows if isinstance(row, dict)]
        if len(result[name]) != len(rows):
            raise AIError("词义解析条目格式不正确")
    if target and (any(item["word"].casefold() == target.casefold() for item in result["spectrum"])
                   or len({item["word"].casefold() for item in result["spectrum"]}) < 3):
        raise AIError("近义词光谱必须包含三个不同于目标词的词")
    if target and any(not re.search(r"\b" + re.escape(target) + r"\b", row["example"], re.I)
                      for row in result["contexts"]):
        raise AIError("正反例句都必须保留目标词")
    example = value["integrated"]
    if not isinstance(example, dict):
        raise AIError("综合例句缺失")
    result["integrated"] = {key: clean(example.get(key), 240) for key in ("english", "chinese")}
    chinese_count = len(re.findall(r"[\u4e00-\u9fff]", dumps(result)))
    if not 180 <= chinese_count <= 640:
        raise AIError("词义解析需使用简明中文且控制长度")
    if len(re.findall(r"[\u4e00-\u9fff]", result["network"])) < 18:
        raise AIError("联想网络需要用中文串成具体故事")
    network_words = {word.casefold() for word in re.findall(r"[A-Za-z][A-Za-z'-]*", result["network"])}
    if len(network_words - {target.casefold()}) < 3:
        raise AIError("联想网络缺少三个英文概念")
    if prior and len(prior) >= 3 and not set(word.casefold() for word in prior[:3]).issubset(network_words):
        raise AIError("联想网络没有用上已学单词")
    return result


class WordLesson:
    def __init__(self, lexicon, gemini, tavily=None):
        self.lexicon = lexicon
        self.gemini = gemini
        self.tavily = tavily

    def prompt_hash(self, architecture):
        source = (VALIDATION_VERSION + SYSTEM.read_text() + dumps(self.gemini.profiles["word_lesson"])
                  + dumps(LESSON_SCHEMA) + architecture)
        if architecture == "verified":
            source += (ROOT / "webapp/content/word_verify_system.txt").read_text()
            source += dumps(self.gemini.profiles["word_verify"]) + dumps(VERIFY_SCHEMA)
        return hashlib.sha256(source.encode()).hexdigest()[:20]

    def cached(self, word, architecture="local"):
        with self.lexicon.connect() as c:
            row = c.execute("SELECT model,payload,approved,created_at FROM lesson_cache WHERE word=? AND prompt_hash=?",
                            (word.casefold(), self.prompt_hash(architecture))).fetchone()
        if not row:
            return None
        payload = json.loads(row["payload"])
        return {"model": row["model"], **(payload if "lesson" in payload else {"lesson": payload}),
                "approved": bool(row["approved"]), "createdAt": row["created_at"]}

    def model_json(self, prompt, purpose, validator):
        cfg = next(item for item in self.gemini.config if item["id"] == MODEL)
        candidates = []
        with self.gemini.db.connect() as c:
            for account in accounts():
                state = self.gemini.state(MODEL, c, account["id"])
                used = c.execute("SELECT count(*) FROM ai_calls WHERE model=? AND day=? AND account=?",
                                 (MODEL, quota_day(), account["group"])).fetchone()[0]
                candidates.append((0 if state.get("status") == "available" else 1, used, account))
        for _, _, account in sorted(candidates, key=lambda x: (x[0], x[1])):
            try:
                response = self.gemini.generate_one({**cfg, "account": account["id"], "group": account["group"]},
                                                    prompt, purpose)
                return validator(response)
            except AIError:
                continue
        raise AIError("Gemini 3.5 Flash-Lite 暂未生成完整解析；未切换到其他模型。")

    def lexical_facts(self, word, prior):
        evidence = self.lexicon.get(word)
        sources = evidence["sources"]
        ec = sources["ecdict"]["data"]
        dictionary = sources["dictionaryapi"]["data"].get("entries", [])
        tatoeba = sources["tatoeba"]["data"].get("sentences", [])
        datamuse = sources["datamuse"]["data"]
        return {"word": word,
                "ecdict": {k: ec.get(k) for k in ("definition", "translation", "pos", "tag")},
                "dictionary": [{"meanings": item.get("meanings", [])[:3]} for item in dictionary[:1]],
                "tatoeba": [r["text"] for r in tatoeba[:2]],
                "related": {k: datamuse.get(k, [])[:6] for k in ("synonyms", "associations", "followers")},
                "previousWords": prior or []}

    def web_facts(self, word, draft=None):
        if self.tavily is None:
            raise AIError("尚未配置可用的 Tavily 搜索")
        domains = ["dictionary.cambridge.org", "merriam-webster.com", "oxfordlearnersdictionaries.com"]
        response = self.tavily.search(f'"{word}" meaning collocations learner dictionary', depth="advanced",
                                      max_results=5, domains=domains, exact_match=True)
        results = [r for r in response["results"] if re.search(r"\b" + re.escape(word) + r"\b", r["title"], re.I)
                   or word in r["url"].casefold()]
        facts = [{"title": r["title"], "url": r["url"], "content": r["content"][:700]}
                 for r in results[:5]]
        if draft:
            # An apparently bad collocation is the easiest way to misteach a learner.
            # Check the adjective+noun pair separately; absence from search is not proof of an error.
            negative = draft["lesson"]["contexts"][1]["example"]
            match = re.search(r"\b" + re.escape(word) + r"\s+([A-Za-z]+)", negative, re.I)
            if match:
                phrase = f"{word} {match.group(1)}"
                try:
                    focused = self.tavily.search(f'"{phrase}" dictionary examples', depth="basic",
                                                 max_results=4, domains=domains, exact_match=True)
                    known = {row["url"] for row in facts}
                    facts.extend({"title": r["title"], "url": r["url"], "content": r["content"][:700]}
                                 for r in focused["results"] if r["url"] not in known and phrase in
                                 (r["title"] + " " + r["content"]).casefold())
                except TavilyError:
                    pass
        return facts[:8]

    def save(self, word, architecture, lesson, sources=None, checks=None):
        payload = {"architecture": architecture, "lesson": lesson,
                   "sources": sources or [], "checks": checks or []}
        with self.lexicon.connect() as c:
            c.execute("INSERT OR REPLACE INTO lesson_cache VALUES (?,?,?,?,?,?)",
                      (word, self.prompt_hash(architecture), MODEL, dumps(payload), 0, now()))
        return {"model": MODEL, **payload, "approved": False, "createdAt": now()}

    def generate(self, raw_word, prior=None, architecture="local"):
        word = raw_word.strip().casefold()
        if architecture not in ("local", "grounded", "verified"):
            raise ValueError("未知的词义解析架构")
        existing = self.cached(word, architecture)
        if existing:
            return existing
        facts = self.lexical_facts(word, prior)
        if architecture == "verified":
            draft = self.generate(word, prior, "local")
            evidence = self.web_facts(word, draft)
            prompt = "逐项复核以下词义解析，只保留自然、准确的内容。\n" + dumps(
                {"word": word, "draft": draft["lesson"], "lexicalEvidence": facts, "webEvidence": evidence})
            def verify(response):
                issues = response.get("issues")
                if not isinstance(issues, list) or len(issues) > 8:
                    raise AIError("复核记录格式不正确")
                checks = [{k: clean(row.get(k), 180) for k in ("field", "claim", "correction")}
                          for row in issues if isinstance(row, dict)]
                if len(checks) != len(issues):
                    raise AIError("复核记录格式不正确")
                return validate_lesson(response.get("lesson"), word, prior), checks
            lesson, checks = self.model_json(prompt, "word_verify", verify)
            return self.save(word, architecture, lesson, [r["url"] for r in evidence], checks)
        evidence = self.web_facts(word) if architecture == "grounded" else []
        prompt = "请解析这个词。词典与例句为参考数据，可能不完整或有噪声：\n" + dumps(
            {"lexicalEvidence": facts, "webEvidence": evidence})
        lesson = self.model_json(prompt, "word_lesson", lambda r: validate_lesson(r, word, prior))
        return self.save(word, architecture, lesson, [r["url"] for r in evidence])
