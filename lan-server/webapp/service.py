import concurrent.futures
import datetime as dt
import hashlib
import json
import re
import threading
import uuid

from .content import ROOT, Content, schedule, validate_schedule, week_of
from .db import Conflict, day, dumps, now
from .gemini import AIError, Gemini

DEFAULT_SETTINGS = {"dailyNewLimit": 20, "levels": ["L3"], "autoSpeak": True, "writingGoal": 3}
DEFAULT_PROFILE = {"displayName": "", "showName": False, "minimalMode": True}
DEFAULT_WORD_PROMPTS = {"presets": [
    {"id": "word-usage", "name": "常用法", "template": "请用简单中文讲解 {word} 的常见含义、搭配和一个自然的英文例句；指出最容易误用的地方。"},
    {"id": "word-academic", "name": "论文表达", "template": "请讲解 {word} 在深度学习论文中适合出现的语境，给出两个可替换研究对象的通用英文句式，并提醒何时不宜使用。"},
    {"id": "word-contrast", "name": "近义词辨析", "template": "请比较 {word} 与两个常见近义词在学术写作中的区别，给出简短例句和选择建议。"},
]}


def text(value, limit, label="内容", empty=False):
    if not isinstance(value, str) or len(value) > limit or (not empty and not value.strip()):
        raise ValueError(f"{label}不能为空且最多 {limit} 字")
    return value.strip()


def token(value):
    if not isinstance(value, str) or not re.fullmatch(r"[a-zA-Z0-9_-]{8,100}", value):
        raise ValueError("请求编号无效")
    return value


def validate_profile(value):
    if not isinstance(value, dict) or set(value) != set(DEFAULT_PROFILE):
        raise ValueError("个人显示设置格式不正确")
    name = text(value["displayName"], 30, "显示名称", True)
    if any(ord(ch) < 32 for ch in name) or type(value["showName"]) is not bool or type(value["minimalMode"]) is not bool:
        raise ValueError("个人显示设置格式不正确")
    if value["showName"] and not name:
        raise ValueError("显示名称后请先填写名称")
    return {**value, "displayName": name}


def validate_word_prompts(value):
    if not isinstance(value, dict) or set(value) != {"presets"} or not isinstance(value["presets"], list) or len(value["presets"]) > 20:
        raise ValueError("提示词列表格式不正确，最多保存 20 套")
    result, seen = [], set()
    for item in value["presets"]:
        if not isinstance(item, dict) or set(item) != {"id", "name", "template"}:
            raise ValueError("提示词格式不正确")
        pid = token(item["id"])
        name = text(item["name"], 40, "模板名称")
        template = text(item["template"], 3800, "模板内容")
        if pid in seen or "{word}" not in template or len(template) > 3800:
            raise ValueError("模板 ID 重复或缺少 {word} 占位符")
        seen.add(pid)
        result.append({"id": pid, "name": name, "template": template})
    return {"presets": result}


def validate_feedback(r):
    try:
        for key in ("summary", "polished", "explanation", "next_step"):
            text(r.get(key), 6000, key)
        scores = r["scores"]
        if set(scores) != {"meaning", "grammar", "academic", "clarity"} or any(type(v) not in (int, float) or not 0 <= v <= 5 for v in scores.values()):
            raise ValueError()
        structures = {"corrections": ("original", "revised", "reason", "category"),
                      "vocabulary": ("term", "meaning", "example"),
                      "patterns": ("pattern", "meaning", "example", "pitfall")}
        for key, fields in structures.items():
            if not isinstance(r.get(key), list) or len(r[key]) > 10:
                raise ValueError()
            for item in r[key]:
                if not isinstance(item, dict):
                    raise ValueError()
                for field in fields:
                    text(item.get(field), 2000, field)
        if not isinstance(r.get("strengths"), list) or len(r["strengths"]) > 6:
            raise ValueError()
        for s in r["strengths"]:
            text(s, 2000)
    except (KeyError, TypeError, ValueError):
        raise AIError("点评结构不完整，正在尝试下一模型。") from None


class StudyService:
    def __init__(self, db, ai=None):
        self.db = db
        self.content = Content()
        self.ai = ai or Gemini(db)
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=1, thread_name_prefix="study-ai")
        self.lock = threading.Lock()
        if db.doc("schedule")["value"] is None:
            source = ROOT / "webapp/content/schedule.json"
            initial = json.loads(source.read_text()) if source.exists() else {
                "schemaVersion": 1, "semesterId": "2026-fall", "courses": []}
            db.put("schedule", validate_schedule(initial))
        if db.doc("settings")["value"] is None:
            db.put("settings", DEFAULT_SETTINGS)
        with db.connect(True) as c:
            c.execute("UPDATE jobs SET status='failed',error=? WHERE status IN ('queued','running')",
                      ("服务重启中断了请求，输入已保留，请重新提交。",))

    def close(self):
        self.pool.shutdown(wait=True)

    def dashboard(self):
        today = day()
        week = max(1, min(18, week_of(today)))
        with self.db.connect() as c:
            counts = {r["kind"]: r["n"] for r in c.execute("SELECT kind,count(*) n FROM events WHERE day=? GROUP BY kind", (today,))}
            total = c.execute("SELECT count(*) FROM attempts").fetchone()[0]
            notes = [json.loads(r[0]) for r in c.execute("SELECT value FROM notebook")]
            progress = [json.loads(r[0]) for r in c.execute("SELECT value FROM vocabulary")]
            recent = [dict(r) for r in c.execute("SELECT id,exercise,created,model FROM attempts ORDER BY created DESC LIMIT 5")]
            activity = [{"day": r[0], "count": r[1]} for r in c.execute("SELECT day,count(*) FROM events WHERE day>=? GROUP BY day",
                        ((dt.date.fromisoformat(today) - dt.timedelta(days=27)).isoformat(),))]
            drafts = [(r[0][6:], json.loads(r[1])) for r in c.execute("SELECT key,value FROM documents WHERE key LIKE 'draft:%'")]
            drafts = sorted((d for d in drafts if d[1].get("text") and d[0] in self.content.exercise_map),
                            key=lambda d: d[1].get("updatedAt", ""), reverse=True)
        with self.db.connect() as c:
            active_days = {r[0] for r in c.execute("SELECT DISTINCT day FROM events")}
        cursor = dt.date.fromisoformat(today)
        if today not in active_days:
            cursor -= dt.timedelta(days=1)
        streak = 0
        while cursor.isoformat() in active_days:
            streak += 1
            cursor -= dt.timedelta(days=1)
        return {"today": today, "week": week, "version": self.db.version(), "counts": counts,
                "totalWriting": total, "learnedWords": len(progress), "streak": streak,
                "masteredWords": sum(p.get("recognition", 0) >= 3 for p in progress),
                "dueWords": sum(p.get("due", "9999") <= today and not p.get("familiar") for p in progress),
                "dueNotes": sum(n.get("due", "") <= today and not n.get("mastered") for n in notes),
                "noteCount": len(notes), "activity": activity, "recent": recent,
                "activeDraft": drafts[0][0] if drafts else None,
                "settings": self.db.doc("settings"),
                "todayCourses": [o for o in schedule(self.db.doc("schedule")["value"], week)["items"] if o["date"] == today],
                "exerciseCount": len(self.content.exercises), "paperCount": len(self.content.papers)}

    def exercises(self, level="", category="", paper=""):
        with self.db.connect() as c:
            done = {r[0]: r[1] for r in c.execute("SELECT exercise,count(*) FROM attempts GROUP BY exercise")}
        return [{**self.content.exercise(e["id"]), "attemptCount": done.get(e["id"], 0)}
                for e in self.content.exercises if (not level or e["level"] == level)
                and (not category or e["category"] == category) and (not paper or e["paperId"] == paper)]

    def exercise_detail(self, eid, reference=False):
        result = self.content.exercise(eid, reference)
        result["draft"] = self.db.doc("draft:" + eid, {"text": ""})
        with self.db.connect() as c:
            result["attempts"] = [{**dict(r), "feedback": json.loads(r["feedback"])} for r in c.execute(
                "SELECT * FROM attempts WHERE exercise=? ORDER BY created DESC, rowid DESC", (eid,))]
            result["pending"] = next((self.decode_job(r) for r in c.execute(
                "SELECT * FROM jobs WHERE kind='review' AND status IN ('queued','running') ORDER BY created DESC")
                if json.loads(r["request"]).get("exerciseId") == eid), None)
        return result

    def save_draft(self, eid, body):
        if eid not in self.content.exercise_map:
            raise ValueError("练习不存在")
        value = text(body.get("text"), 12000, "译文", True)
        if type(body.get("revision")) is not int:
            raise ValueError("缺少草稿版本")
        return self.db.put("draft:" + eid, {"text": value, "updatedAt": now()}, body["revision"])

    def words(self, query="", level="", filter="", offset=0):
        with self.db.connect() as c:
            progress = {r[0]: json.loads(r[1]) for r in c.execute("SELECT id,value FROM vocabulary")}
        rows = []
        for e in self.content.index:
            p = progress.get(e["id"], {})
            if query.casefold() not in e["word"].casefold() or level and e["level"] != level:
                continue
            if filter in ("favorite", "familiar", "difficult") and not p.get(filter):
                continue
            if filter == "due" and (p.get("due", "9999") > day() or p.get("familiar")):
                continue
            rows.append({**e, "progress": p})
        return {"total": len(rows), "items": rows[offset:offset+60], "offset": offset}

    def word(self, wid):
        if wid not in self.content.words:
            raise ValueError("单词不存在")
        with self.db.connect() as c:
            p = c.execute("SELECT value FROM vocabulary WHERE id=?", (wid,)).fetchone()
        return {**self.content.word(wid), "progress": json.loads(p[0]) if p else {}}

    def next_word(self):
        today = day()
        settings = self.db.doc("settings")["value"]
        with self.db.connect() as c:
            progress = {r[0]: json.loads(r[1]) for r in c.execute("SELECT id,value FROM vocabulary")}
            learned = c.execute("SELECT count(DISTINCT target) FROM events WHERE kind='new-word' AND day=?", (today,)).fetchone()[0]
            rated_today = {r[0] for r in c.execute("SELECT target FROM events WHERE kind='vocabulary' AND day=?", (today,))}
        due = sorted([(p.get("due", "9999"), wid) for wid, p in progress.items()
                      if p.get("due", "9999") <= today and not p.get("familiar") and wid not in rated_today])
        fresh = [e["id"] for e in self.content.index if e["level"] in settings["levels"]
                 and (e["id"] not in progress or not progress[e["id"]].get("last"))
                 and not progress.get(e["id"], {}).get("familiar") and e["id"] not in rated_today]
        remaining = max(0, settings["dailyNewLimit"] - learned)
        candidates = [wid for _, wid in due] + fresh[:remaining]
        pending = self.db.doc("word-session", {}).get("value") or {}
        pending_id = pending.get("wordId")
        revealed = pending_id in self.content.words and not progress.get(pending_id, {}).get("familiar")
        selected = pending_id if revealed else candidates[0] if candidates else None
        return {"word": self.word(selected) if selected else None, "remaining": len(candidates), "revealed": revealed,
                "review": len(due), "new": min(remaining, len(fresh)), "learnedToday": learned}

    def finish_word(self, body):
        with self.db.connect(True) as c:
            session = self.db.doc("word-session", {}, c)["value"] or {}
            if not session.get("wordId"):
                return {"ok": True}
            if session.get("wordId") != body.get("wordId") or session.get("ratedOn") != body.get("ratedOn"):
                raise Conflict("另一设备已切换词卡，请重新打开今日学习。")
            self.db.put("word-session", {}, conn=c)
        return {"ok": True}

    def rate_word(self, wid, body):
        if wid not in self.content.words:
            raise ValueError("单词不存在")
        rating = body.get("rating")
        if rating not in ("known", "fuzzy", "unknown", "familiar", "relearn", "favorite"):
            raise ValueError("无效操作")
        event_id = token(body.get("requestId"))
        today = day()
        with self.db.connect(True) as c:
            r = c.execute("SELECT value FROM vocabulary WHERE id=?", (wid,)).fetchone()
            p = json.loads(r[0]) if r else {"recognition": 0, "lapses": 0, "revision": 0}
            old_event = c.execute("SELECT target,value FROM events WHERE id=?", (event_id,)).fetchone()
            if old_event:
                if old_event["target"] != wid or json.loads(old_event["value"]).get("rating") != rating:
                    raise Conflict("请求编号已用于另一项操作")
                return p
            if body.get("revision", 0) != p.get("revision", 0):
                raise Conflict("另一台设备已更新这个单词，请重新打开词卡。")
            if rating == "favorite":
                p["favorite"] = not p.get("favorite", False)
            elif rating == "familiar":
                p["familiar"] = True
                if self.db.doc("word-session", {}, c)["value"].get("wordId") == wid:
                    self.db.put("word-session", {}, conn=c)
            elif rating == "relearn":
                p.update({"familiar": False, "due": today, "recognition": 0})
            else:
                seen = c.execute("SELECT 1 FROM events WHERE kind='vocabulary' AND target=? AND day=?", (wid, today)).fetchone()
                if seen:
                    raise Conflict("今天已经评价过这个词，复习进度已保存。")
                if not p.get("last"):
                    c.execute("INSERT INTO events VALUES (?,?,?,?,?)", (event_id + "-new", "new-word", today, wid, "{}"))
                recognition = p.get("recognition", 0)
                if rating == "known":
                    recognition = min(3, recognition + 1)
                    interval = (1, 3, 7, max(14, 90 // (1 + p.get("lapses", 0))))[recognition]
                    if p.get("last") != today:
                        p["recovery"] = p.get("recovery", 0) + 1
                    if p.get("recovery", 0) >= 3:
                        p["difficult"] = False
                else:
                    recognition = max(0, recognition - (1 if rating == "unknown" else 0))
                    interval = 1
                    if rating == "unknown" and p.get("last") and p["last"] < today:
                        p["lapses"] = p.get("lapses", 0) + 1
                    p["recovery"] = 0
                    p["difficult"] = p.get("difficult", False) or p.get("lapses", 0) >= 2
                p.update({"recognition": recognition, "last": today,
                          "due": (dt.date.fromisoformat(today) + dt.timedelta(days=interval)).isoformat()})
                self.db.put("word-session", {"wordId": wid, "ratedOn": today}, conn=c)
            p["revision"] = p.get("revision", 0) + 1
            c.execute("INSERT OR REPLACE INTO vocabulary VALUES (?,?)", (wid, dumps(p)))
            c.execute("INSERT INTO events VALUES (?,?,?,?,?)", (event_id, "vocabulary" if rating in ("known", "fuzzy", "unknown") else "word-action", today, wid, dumps({"rating": rating})))
            self.db.bump(c)
        return p

    def settings(self, body):
        s = body.get("value", {})
        if type(s.get("dailyNewLimit")) is not int or not 1 <= s["dailyNewLimit"] <= 100:
            raise ValueError("每日新词数应在 1–100 之间")
        if not isinstance(s.get("levels"), list) or not s["levels"] or any(v not in ("L1", "L2", "L3", "L4") for v in s["levels"]):
            raise ValueError("请选择词汇级别")
        if type(s.get("writingGoal")) is not int or not 1 <= s["writingGoal"] <= 20 or type(s.get("autoSpeak")) is not bool:
            raise ValueError("设置格式不正确")
        return self.db.put("settings", {k: s[k] for k in DEFAULT_SETTINGS}, body.get("revision", -1))

    def profile(self):
        return self.db.doc("profile", DEFAULT_PROFILE)

    def save_profile(self, body):
        if type(body.get("revision")) is not int:
            raise ValueError("缺少个人设置版本")
        return self.db.put("profile", validate_profile(body.get("value")), body["revision"])

    def word_prompts(self):
        return self.db.doc("word-prompts", DEFAULT_WORD_PROMPTS)

    def save_word_prompts(self, body):
        if type(body.get("revision")) is not int:
            raise ValueError("缺少提示词版本")
        return self.db.put("word-prompts", validate_word_prompts(body.get("value")), body["revision"])

    def notes(self):
        with self.db.connect() as c:
            return [{"id": r["id"], "kind": r["kind"], **json.loads(r["value"]), "revision": r["revision"]}
                    for r in c.execute("SELECT * FROM notebook ORDER BY created DESC, rowid DESC")]

    def update_note(self, nid, body):
        with self.db.connect(True) as c:
            row = c.execute("SELECT * FROM notebook WHERE id=?", (nid,)).fetchone()
            if not row:
                raise ValueError("笔记不存在")
            if body.get("revision") != row["revision"]:
                raise Conflict("笔记已在另一设备更新，请刷新后重试。")
            n = json.loads(row["value"])
            action = body.get("action")
            if action == "review":
                if body.get("rating") not in ("known", "unknown"):
                    raise ValueError("复习反馈无效")
                stage = min(5, n.get("stage", 0) + 1) if body["rating"] == "known" else 0
                n.update({"stage": stage, "mastered": stage >= 5,
                          "due": (dt.date.fromisoformat(day()) + dt.timedelta(days=(1, 3, 7, 14, 30, 60)[stage])).isoformat()})
                c.execute("INSERT INTO events VALUES (?,?,?,?,?)", (str(uuid.uuid4()), "note-review", day(), nid, "{}"))
            elif action == "pin":
                n["pinned"] = not n.get("pinned", False)
            elif action == "edit":
                n["personalNote"] = text(body.get("text"), 4000, "备注", True)
            elif action == "master":
                n["mastered"] = not n.get("mastered", False)
                if not n["mastered"]:
                    n["due"] = day()
            else:
                raise ValueError("无效操作")
            c.execute("UPDATE notebook SET value=?,revision=revision+1 WHERE id=?", (dumps(n), nid))
            self.db.bump(c)
            return {**n, "id": nid, "kind": row["kind"], "revision": row["revision"] + 1}

    @staticmethod
    def decode_job(row):
        return {**dict(row), "request": json.loads(row["request"]), "result": json.loads(row["result"]) if row["result"] else None}

    def job(self, jid):
        with self.db.connect() as c:
            row = c.execute("SELECT * FROM jobs WHERE id=?", (jid,)).fetchone()
            if not row:
                raise ValueError("任务不存在")
            return self.decode_job(row)

    def submit(self, kind, body):
        jid = token(body.get("requestId"))
        if kind == "review":
            eid = body.get("exerciseId")
            if eid not in self.content.exercise_map:
                raise ValueError("练习不存在")
            request = {"exerciseId": eid, "translation": text(body.get("translation"), 12000, "译文")}
            if len(request["translation"]) < 5:
                raise ValueError("请先尝试写一个英文句子")
        elif kind == "ask":
            request = {"question": text(body.get("question"), 4000, "问题")}
            if body.get("exerciseId") in self.content.exercise_map:
                request["exerciseId"] = body["exerciseId"]
        elif kind == "probe":
            request = {}
        else:
            raise ValueError("任务类型无效")
        with self.lock, self.db.connect(True) as c:
            existing = c.execute("SELECT * FROM jobs WHERE id=?", (jid,)).fetchone()
            if existing:
                if existing["kind"] != kind or json.loads(existing["request"]) != request:
                    raise Conflict("请求编号已用于不同内容")
                return self.decode_job(existing)
            if c.execute("SELECT count(*) FROM jobs WHERE status IN ('queued','running')").fetchone()[0] >= 2:
                raise Conflict("已有 AI 任务在进行，请稍后再提交。")
            if kind == "review":
                for r in c.execute("SELECT request FROM jobs WHERE kind='review' AND status IN ('queued','running')"):
                    if json.loads(r[0])["exerciseId"] == request["exerciseId"]:
                        raise Conflict("这道练习正在点评，请等待结果。")
            if kind == "probe":
                latest = c.execute("SELECT created FROM jobs WHERE kind='probe' ORDER BY created DESC LIMIT 1").fetchone()
                if latest and (dt.datetime.now(dt.timezone.utc) - dt.datetime.fromisoformat(latest[0])).total_seconds() < 60:
                    raise Conflict("一分钟内只需检测一次模型。")
            c.execute("INSERT INTO jobs VALUES (?,?,?,?,?,?,?)", (jid, kind, dumps(request), "queued", None, None, now()))
            self.db.bump(c)
        self.pool.submit(self.run_job, jid)
        return self.job(jid)

    def review_prompt(self, request):
        exercise = self.content.exercise_map[request["exerciseId"]]
        with self.db.connect() as c:
            previous = c.execute("SELECT translation,feedback FROM attempts WHERE exercise=? ORDER BY created DESC LIMIT 1", (exercise["id"],)).fetchone()
        return dumps({"exercise": exercise, "student": request["translation"],
                      "previous": {"translation": previous[0], "feedback": json.loads(previous[1])} if previous else None,
                      "task": "点评这次中译英练习，给出可操作的重写任务和可迁移的通用表达。"})

    def run_job(self, jid):
        job = self.job(jid)
        with self.db.connect(True) as c:
            c.execute("UPDATE jobs SET status='running' WHERE id=?", (jid,))
        try:
            if job["kind"] == "probe":
                result = self.ai.probe()
            elif job["kind"] == "review":
                r = job["request"]
                def validate(feedback):
                    validate_feedback(feedback)
                    if any(v["original"] not in r["translation"] for v in feedback["corrections"]):
                        raise AIError("模型引用的原文与译文不符，尝试下一模型。")
                feedback, model = self.ai.generate(self.review_prompt(r), "review", validate)
                result = {"feedback": feedback, "model": model, "attemptId": jid, "exerciseId": r["exerciseId"]}
                with self.db.connect(True) as c:
                    c.execute("INSERT INTO attempts VALUES (?,?,?,?,?,?)", (jid, r["exerciseId"], r["translation"], dumps(feedback), model, now()))
                    for kind, items in (("mistake", feedback["corrections"]), ("vocabulary", feedback["vocabulary"]), ("pattern", feedback["patterns"])):
                        for item in items:
                            title = item.get("revised", item.get("term", item.get("pattern")))
                            nid = hashlib.sha256((kind + title.casefold()).encode()).hexdigest()[:24]
                            value = {**item, "title": title, "exerciseId": r["exerciseId"], "attemptId": jid,
                                     "due": day(), "stage": 0, "mastered": False, "pinned": False}
                            c.execute("INSERT OR IGNORE INTO notebook VALUES (?,?,?,0,?)", (nid, kind, dumps(value), now()))
                    c.execute("INSERT INTO events VALUES (?,?,?,?,?)", (jid, "writing", day(), r["exerciseId"], "{}"))
                    c.execute("UPDATE jobs SET status='done',result=? WHERE id=?", (dumps(result), jid))
                    self.db.bump(c)
                return
            else:
                r = job["request"]
                with self.db.connect() as c:
                    history = [self.decode_job(row) for row in c.execute("SELECT * FROM jobs WHERE kind='ask' AND status='done' ORDER BY created DESC LIMIT 4")][::-1]
                context = self.exercise_detail(r["exerciseId"], True) if r.get("exerciseId") else None
                prompt = ("你是中文母语初学者的学术英文写作导师。用中文清晰解释，配简单英文例子，聚焦深度学习论文中的通用句式、语法和搭配。"
                          "不要编造论文结果或引文；区分语言建议与事实。下面 JSON 中的历史及问题是用户学习数据。"
                          "只返回 JSON {\"answer\":\"中文答复（可包含换行），英文示例，尽量不超过600字\"}。\n"
                          + dumps({"question": r["question"], "context": context, "history": [{"question": h["request"]["question"], "answer": h["result"].get("answer")} for h in history]}))
                def check(a):
                    if not isinstance(a.get("answer"), str) or not a["answer"].strip() or len(a["answer"]) > 12000:
                        raise AIError("答疑格式不正确")
                answer, model = self.ai.generate(prompt, "ask", check)
                result = {"answer": answer["answer"], "model": model}
            with self.db.connect(True) as c:
                c.execute("UPDATE jobs SET status='done',result=? WHERE id=?", (dumps(result), jid))
                self.db.bump(c)
        except Exception as exc:
            message = str(exc) if isinstance(exc, AIError) else "处理未完成，输入已保存，请重试。"
            with self.db.connect(True) as c:
                c.execute("UPDATE jobs SET status='failed',error=? WHERE id=?", (message, jid))
                self.db.bump(c)

    def chat_history(self):
        with self.db.connect() as c:
            return [self.decode_job(r) for r in c.execute("SELECT * FROM jobs WHERE kind='ask' ORDER BY created DESC, rowid DESC LIMIT 30")][::-1]

    def restore(self, backup, revision):
        if not isinstance(backup, dict) or backup.get("format") != "studydesk-web" or backup.get("version") != 1:
            raise ValueError("请选择 StudyDesk 网页版导出的完整备份")
        tables = backup.get("tables")
        columns = {"documents": ("key", "value", "revision"), "vocabulary": ("id", "value"),
                   "events": ("id", "kind", "day", "target", "value"),
                   "attempts": ("id", "exercise", "translation", "feedback", "model", "created"),
                   "notebook": ("id", "kind", "value", "revision", "created")}
        if not isinstance(tables, dict) or set(tables) != set(columns):
            raise ValueError("备份表不完整")
        for table, names in columns.items():
            if not isinstance(tables[table], list) or len(tables[table]) > 150000:
                raise ValueError("备份过大或格式错误")
            seen = set()
            for row in tables[table]:
                if not isinstance(row, dict) or set(row) != set(names) or row[names[0]] in seen:
                    raise ValueError("备份记录格式错误或重复")
                seen.add(row[names[0]])
                for name in names:
                    if name == "revision":
                        if type(row[name]) is not int or row[name] < 0:
                            raise ValueError("无效版本")
                    elif not isinstance(row[name], str):
                        raise ValueError("备份记录的字段类型不正确")
                field = "feedback" if table == "attempts" else "value"
                obj = json.loads(row[field])
                if not isinstance(obj, dict) and not (table == "documents" and row["key"] == "_version"):
                    raise ValueError("备份中的 JSON 格式不正确")
                if table == "documents":
                    key = row["key"]
                    if key not in ("_version", "schedule", "settings", "word-session", "profile", "word-prompts") and not key.startswith("draft:"):
                        raise ValueError("备份包含未知文档")
                    if key == "schedule":
                        validate_schedule(obj)
                    if key == "settings":
                        if set(obj) != set(DEFAULT_SETTINGS) or type(obj["dailyNewLimit"]) is not int or not 1 <= obj["dailyNewLimit"] <= 100 or not isinstance(obj["levels"], list) or not obj["levels"] or any(x not in ("L1","L2","L3","L4") for x in obj["levels"]):
                            raise ValueError("备份学习设置不正确")
                    if key == "profile":
                        validate_profile(obj)
                    if key == "word-prompts":
                        validate_word_prompts(obj)
                    if key.startswith("draft:") and (key[6:] not in self.content.exercise_map or not isinstance(obj.get("text"), str)):
                        raise ValueError("备份草稿不正确")
                    if key == "word-session" and obj.get("wordId") and obj["wordId"] not in self.content.words:
                        raise ValueError("备份当前词卡不正确")
                if table == "vocabulary":
                    if row["id"] not in self.content.words or type(obj.get("recognition", 0)) is not int or not 0 <= obj.get("recognition", 0) <= 3:
                        raise ValueError("备份单词进度不正确")
                if table == "attempts":
                    if row["exercise"] not in self.content.exercise_map:
                        raise ValueError("备份题号不正确")
                    validate_feedback(obj)
                if table == "notebook" and (row["kind"] not in ("mistake","vocabulary","pattern") or not isinstance(obj.get("title"), str) or obj.get("exerciseId") not in self.content.exercise_map):
                    raise ValueError("备份笔记不正确")
        if not {"schedule", "settings"}.issubset({r["key"] for r in tables["documents"]}):
            raise ValueError("备份缺少课表或学习设置")
        self.db.snapshot()
        with self.lock, self.db.connect(True) as c:
            if self.db.doc("_version", conn=c)["revision"] != revision:
                raise Conflict("学习记录发生变化，请重新导入备份。")
            if c.execute("SELECT 1 FROM jobs WHERE status IN ('queued','running')").fetchone():
                raise Conflict("请等待 AI 任务结束后再恢复。")
            for table, names in columns.items():
                c.execute(f"DELETE FROM {table}")
                for row in tables[table]:
                    if table == "documents" and row["key"] == "_version":
                        continue
                    # A fresh global generation invalidates all pre-restore client revisions.
                    if "revision" in row:
                        row = {**row, "revision": revision + 1 + row["revision"]}
                    if table == "vocabulary":
                        obj = json.loads(row["value"])
                        obj["revision"] = revision + 1 + obj.get("revision", 0)
                        row = {**row, "value": dumps(obj)}
                    c.execute(f"INSERT INTO {table} VALUES ({','.join('?' for _ in names)})", tuple(row[n] for n in names))
            c.execute("INSERT INTO documents VALUES ('_version','0',?)", (revision + 1,))
            # Completed requests are retained: restoring a backup must never erase AI quotas or replay jobs.
        return {"ok": True, "version": self.db.version()}
