import datetime as dt
import hashlib
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = Path(__file__).parent / "content"
START = dt.date(2026, 9, 14)
HOLIDAYS = {"2026-09-25", "2026-10-01", "2026-10-02", "2026-10-03", "2027-01-01"}
SUMMER = ["08:00-08:50", "09:00-09:50", "10:10-11:00", "11:10-12:00", "14:30-15:20", "15:30-16:20",
          "16:40-17:30", "17:40-18:30", "19:40-20:30", "20:40-21:30", "21:40-22:30"]
WINTER = SUMMER[:4] + ["14:00-14:50", "15:00-15:50", "16:10-17:00", "17:10-18:00", "19:10-20:00", "20:10-21:00", "21:10-22:00"]


def date_of(week, weekday):
    return (START + dt.timedelta(days=(week - 1) * 7 + weekday - 1)).isoformat()


def week_of(day):
    return (dt.date.fromisoformat(day) - START).days // 7 + 1


def sections(value):
    if not isinstance(value, list) or not value or len(value) > 11 or any(type(n) is not int or not 1 <= n <= 11 for n in value) or len(set(value)) != len(value):
        raise ValueError("节次应为 1–11 的不重复整数列表")
    return sorted(value)


def validate_schedule(value):
    if not isinstance(value, dict) or value.get("semesterId") != "2026-fall" or value.get("schemaVersion") != 1:
        raise ValueError("需要 schemaVersion: 1、semesterId: 2026-fall 的课表 JSON")
    courses = value.get("courses")
    if not isinstance(courses, list) or len(courses) > 500:
        raise ValueError("courses 必须是最多 500 门课的列表")
    ids = set()
    cleaned = []
    for i, course in enumerate(courses):
        if not isinstance(course, dict):
            raise ValueError("课程格式不正确")
        c = {k: course.get(k, "") for k in ("name", "className", "room", "note")}
        if any(not isinstance(v, str) or len(v) > 500 for v in c.values()) or not c["name"].strip():
            raise ValueError("课程名称不能为空，文本最长 500 字")
        c["id"] = course.get("id", "import-" + str(i))
        if not isinstance(c["id"], str) or not c["id"] or len(c["id"]) > 120 or c["id"] in ids:
            raise ValueError("课程 ID 无效或重复")
        ids.add(c["id"])
        c["weekday"] = course.get("weekday")
        if type(c["weekday"]) is not int or not 1 <= c["weekday"] <= 7:
            raise ValueError("星期必须是 1–7")
        c["sections"] = sections(course.get("sections"))
        weeks = course.get("weeks")
        if not isinstance(weeks, list) or not weeks or any(type(n) is not int or not 1 <= n <= 18 for n in weeks) or len(set(weeks)) != len(weeks):
            raise ValueError("周次应为 1–18 的不重复整数列表")
        c["weeks"] = sorted(weeks)
        c["teachers"] = course.get("teachers", [])
        if not isinstance(c["teachers"], list) or len(c["teachers"]) > 20 or any(not isinstance(t, str) or len(t) > 500 for t in c["teachers"]):
            raise ValueError("教师应为文本列表")
        cleaned.append(c)
    adjustments = value.get("adjustments", [])
    if not isinstance(adjustments, list) or len(adjustments) > 10000:
        raise ValueError("调整记录格式不正确")
    by_id = {c["id"]: c for c in cleaned}
    seen = set()
    clean_adjustments = []
    for a in adjustments:
        if not isinstance(a, dict) or a.get("courseId") not in by_id:
            raise ValueError("调整记录的课程不存在")
        c = by_id[a["courseId"]]
        original, target = a.get("originalDate"), a.get("date")
        if original not in [date_of(w, c["weekday"]) for w in c["weeks"]]:
            raise ValueError("调整的原日期不是该课程的上课日期")
        try:
            valid = len(target) == 10 and dt.date.fromisoformat(target).isoformat() == target and 1 <= week_of(target) <= 18
        except (ValueError, TypeError):
            valid = False
        if not valid:
            raise ValueError("调整目标日期必须在本学期内")
        key = (c["id"], original)
        if key in seen:
            raise ValueError("同一堂课不能有重复调整")
        seen.add(key)
        if type(a.get("cancelled")) is not bool or not isinstance(a.get("room"), str) or len(a["room"]) > 500:
            raise ValueError("调整状态或教室格式错误")
        clean_adjustments.append({"courseId": c["id"], "originalDate": original, "date": target,
                                  "cancelled": a["cancelled"], "room": a["room"], "sections": sections(a.get("sections"))})
    return {"schemaVersion": 1, "semesterId": "2026-fall", "courses": cleaned, "adjustments": clean_adjustments}


def schedule(value, week):
    days = [date_of(week, i) for i in range(1, 8)]
    adjustments = {(a["courseId"], a["originalDate"]): a for a in value.get("adjustments", [])}
    result = []
    for course in value["courses"]:
        for w in course["weeks"]:
            original = date_of(w, course["weekday"])
            a = adjustments.get((course["id"], original))
            if a and a["cancelled"] or not a and original in HOLIDAYS:
                continue
            target = a["date"] if a else original
            if target not in days:
                continue
            nums = a["sections"] if a else course["sections"]
            ranges = []
            for n in nums:
                if ranges and ranges[-1][-1] + 1 == n:
                    ranges[-1].append(n)
                else:
                    ranges.append([n])
            for r in ranges:
                result.append({"course": course, "date": target, "originalDate": original,
                               "sections": r, "room": a["room"] if a else course["room"], "adjusted": bool(a),
                               "members": [{"courseId": course["id"], "originalDate": original}]})
    def norm(s):
        return "".join(s.split()).replace("（", "(").replace("）", ")")
    def key(o):
        c = o["course"]
        return (o["date"], o["originalDate"], norm(c["name"]), norm(o["room"]), norm(c["className"]),
                c["note"].strip(), tuple(sorted(map(norm, c["teachers"]))), o["adjusted"])
    merged = []
    for o in sorted(result, key=lambda x: (x["date"], x["sections"][0])):
        last = next((x for x in merged if key(x) == key(o) and x["sections"][-1] + 1 == o["sections"][0]), None)
        if last:
            last["sections"] += o["sections"]
            last["members"] += [m for m in o["members"] if m not in last["members"]]
        else:
            merged.append(o)
    for o in merged:
        slots = SUMMER if "05-01" <= o["date"][5:] < "10-01" else WINTER
        o["time"] = slots[o["sections"][0]-1].split("-")[0] + "–" + slots[o["sections"][-1]-1].split("-")[1]
        o["conflicts"] = sum(1 for x in merged if x["date"] == o["date"] and set(x["sections"]) & set(o["sections"]))
    return {"week": week, "days": days, "items": merged, "holidays": [d for d in days if d in HOLIDAYS],
            "times": SUMMER if "05-01" <= days[0][5:] < "10-01" else WINTER}


class Content:
    def __init__(self):
        def commonjs(path):
            return json.loads(path.read_text().split("module.exports=", 1)[1].rstrip(";\n"))
        catalog = commonjs(ROOT / "miniprogram/core/vocabulary/catalog.js")
        self.manifest = catalog["manifest"]
        self.words = {}
        for shard in self.manifest["shards"]:
            for entry in commonjs(ROOT / "miniprogram" / shard / "data.js"):
                self.words[entry["id"]] = entry
        self.index = catalog["entries"]
        for e in self.index:
            self.words[e["id"]]["level"] = e["level"]
        self.books = {"general-10000": {"id": "general-10000", "title": "通用词汇 · 10,000", "entries": self.index}}
        self.dictionary_path = next((p for p in (
            ROOT / "StudyDesk/app/src/main/assets/content.db",
            ROOT.parent / "app/src/main/assets/content.db") if p.is_file()), None)
        self.deep_ids = {}
        if self.dictionary_path:
            # Reuse the existing Android dictionary and pre-generated lessons, without touching model hosts.
            with sqlite3.connect(self.dictionary_path.resolve().as_uri() + "?mode=ro", uri=True) as c:
                by_name = {entry["word"].casefold(): entry["id"] for entry in self.index}
                ielts = []
                for row in c.execute("SELECT b.word_id,w.headword,w.pronunciation,w.forms_json,b.gloss_zh "
                                     "FROM book_entry b JOIN word w ON b.word_id=w.id "
                                     "WHERE b.book_id='ielts-word-list' ORDER BY b.position"):
                    android_id, headword, ipa, forms, gloss = row
                    wid = by_name.get(headword.casefold(), android_id)
                    if wid not in self.words:
                        senses = [{"id": sid, "pos": pos, "definition": definition,
                                   "examples": json.loads(examples), "synonyms": json.loads(synonyms)}
                                  for sid, pos, definition, examples, synonyms in c.execute(
                                      "SELECT id,pos,definition_en,examples_json,synonyms_json FROM sense "
                                      "WHERE word_id=? ORDER BY source_order", (android_id,))]
                        self.words[wid] = {"id": wid, "word": headword, "ipa": ipa, "variants": json.loads(forms),
                                           "senses": senses, "zh": gloss, "level": "L3"}
                    elif not self.words[wid].get("zh"):
                        self.words[wid]["zh"] = gloss
                    ielts.append({"id": wid, "word": headword, "level": self.words[wid]["level"], "deep": False})
                self.books["ielts-word-list"] = {"id": "ielts-word-list", "title": "雅思词表 · 3,611", "entries": ielts}
                glossary = {word.casefold(): gloss for word, gloss in c.execute(
                    "SELECT w.headword,b.gloss_zh FROM word w JOIN book_entry b ON b.word_id=w.id")}
                self.deep_ids = {word.casefold(): wid for word, wid in c.execute(
                    "SELECT w.headword,w.id FROM word w JOIN generation g ON g.word_id=w.id")}
            for entry in self.words.values():
                if not entry.get("zh") and entry["word"].casefold() in glossary:
                    entry["zh"] = glossary[entry["word"].casefold()]
            for entry in self.index:
                entry["deep"] = entry.get("deep", False) or entry["word"].casefold() in self.deep_ids
            for entry in self.books["ielts-word-list"]["entries"]:
                entry["deep"] = entry["word"].casefold() in self.deep_ids
        # Drop-in catalog interface: one JSON manifest per future word book.
        by_name = {entry["word"].casefold(): entry["id"] for entry in self.words.values()}
        for path in sorted((CONTENT / "books").glob("*.json")):
            manifest = json.loads(path.read_text())
            book_id, title, rows = manifest.get("id"), manifest.get("title"), manifest.get("entries")
            if (not isinstance(book_id, str) or not re.fullmatch(r"[a-z0-9-]{3,48}", book_id)
                    or book_id in self.books or not isinstance(title, str) or not 1 <= len(title) <= 60
                    or not isinstance(rows, list) or not 1 <= len(rows) <= 50000):
                raise ValueError("词书清单格式不正确: " + path.name)
            entries = []
            for item in rows:
                word = item.get("word") if isinstance(item, dict) else item
                if not isinstance(word, str) or not re.fullmatch(r"[A-Za-z][A-Za-z' -]{0,63}", word):
                    raise ValueError("词书单词格式不正确: " + path.name)
                wid = by_name.get(word.casefold())
                if not wid:
                    wid = "custom-" + hashlib.sha256(word.casefold().encode()).hexdigest()[:16]
                    self.words[wid] = {"id": wid, "word": word, "ipa": "", "variants": [],
                                       "senses": [], "zh": "", "level": "L3"}
                    by_name[word.casefold()] = wid
                entries.append({"id": wid, "word": word, "level": self.words[wid]["level"], "deep": False})
            self.books[book_id] = {"id": book_id, "title": title, "entries": entries}
        self.papers = json.loads((CONTENT / "papers.json").read_text())
        self.exercises = json.loads((CONTENT / "exercises.json").read_text())
        self.exercise_map = {e["id"]: e for e in self.exercises}
        self.paper_map = {p["id"]: p for p in self.papers}

    def word(self, wid):
        word = dict(self.words[wid])
        deep_id = self.deep_ids.get(word["word"].casefold())
        if deep_id and self.dictionary_path:
            with sqlite3.connect(self.dictionary_path.resolve().as_uri() + "?mode=ro", uri=True) as c:
                row = c.execute("SELECT payload_json,model,status FROM generation WHERE word_id=?", (deep_id,)).fetchone()
            if row:
                data = json.loads(row[0])
                fields = [("concrete_image", "1 · 画面锚定"), ("synonyms_comparison", "2 · 近义词对比"),
                          ("register_and_contexts", "3 · 语域与语境"), ("collocations", "4 · 搭配与语义韵"),
                          ("associations", "5 · 联想网络"), ("integrated_example", "6 · 综合示例")]
                word["deep"] = {"status": row[2], "model": row[1], "blocks": [
                    {"title": title, "en": data.get(key, "") + ("\n\n" + data.get("integrated_example_mapping", "") if key == "integrated_example" else ""),
                     "zh": data.get(key + "_zh", "") + ("\n\n" + data.get("integrated_example_mapping_zh", "") if key == "integrated_example" else "")}
                    for key, title in fields if isinstance(data.get(key), str) and data[key]]}
        return word

    def exercise(self, eid, answer=False):
        e = self.exercise_map[eid].copy()
        if not answer:
            e.pop("reference", None)
        e["paper"] = self.paper_map[e["paperId"]]
        return e
