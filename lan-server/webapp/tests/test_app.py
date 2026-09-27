import concurrent.futures
import copy
import datetime as dt
import hashlib
import json
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch

from webapp.content import ROOT, date_of, schedule, validate_schedule
from webapp.db import Conflict, Database, day, quota_day
from webapp.gemini import AIError, Gemini, parse_json
from webapp.server import make_server
from webapp.service import StudyService, validate_feedback

FEEDBACK = {"scores": {"meaning": 3, "grammar": 2, "academic": 3, "clarity": 3}, "summary": "核心意思已经表达出来了。",
            "strengths": ["比较方向正确"], "corrections": [{"original": "more difficulty", "revised": "more difficult",
                "reason": "系动词后用形容词", "category": "语法"}],
            "polished": "Training becomes more difficult as model depth increases.",
            "explanation": "先描述现象，再说明变化条件。", "next_step": "尝试用 as 连接两个分句。",
            "vocabulary": [{"term": "increase", "meaning": "增加", "example": "The cost increases."}],
            "patterns": [{"pattern": "As [X] increases, [Y] becomes [adjective].", "meaning": "描述伴随变化",
                          "example": "As the task changes, evaluation becomes harder.", "pitfall": "不要把相关写成因果"}]}


class FakeAI:
    def generate(self, prompt, purpose, validate=None):
        value = copy.deepcopy(FEEDBACK) if purpose == "review" else {"answer": "请用形容词 difficult。"}
        if validate:
            validate(value)
        return value, "test-model · test-key"


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = Database(Path(self.temp.name) / "test.sqlite3")
        self.app = StudyService(self.db, FakeAI())

    def tearDown(self):
        self.app.close()
        self.temp.cleanup()

    def test_source_content_and_reference_hidden(self):
        self.assertEqual(10000, len(self.app.content.words))
        self.assertEqual(40, len(self.app.exercises()))
        self.assertNotIn("reference", self.app.exercise_detail("resnet-01"))
        self.assertIn("reference", self.app.exercise_detail("resnet-01", True))
        self.assertEqual(18, len(self.db.doc("schedule")["value"]["courses"]))
        sports = next(o for o in schedule(self.db.doc("schedule")["value"], 1)["items"]
                      if o["course"]["id"] == "c-sports-wed-3-4")
        self.assertEqual([3, 4], sports["sections"])
        self.assertEqual("2号巨构七楼羽毛球场", sports["room"])
        self.assertEqual(["胡浩"], sports["course"]["teachers"])
        self.assertFalse(any(o["course"]["id"] == "c-sports-wed-3-4" for o in schedule(self.db.doc("schedule")["value"], 9)["items"]))

    def test_draft_conflicts_preserve_newer_device(self):
        self.app.save_draft("resnet-01", {"text": "Device A writes.", "revision": 0})
        with self.assertRaises(Conflict):
            self.app.save_draft("resnet-01", {"text": "Device B overwrites.", "revision": 0})
        self.assertEqual("Device A writes.", self.db.doc("draft:resnet-01")["value"]["text"])
        self.assertEqual("resnet-01", self.app.dashboard()["activeDraft"])

    def test_chat_history_keeps_submission_order_with_same_second_timestamps(self):
        with self.db.connect(True) as c:
            for index in (1, 2):
                c.execute("INSERT INTO jobs VALUES (?,?,?,?,?,?,?)", (
                    f"chat-{index}", "ask", json.dumps({"question": f"question {index}"}),
                    "done", json.dumps({"answer": "ok", "model": "test"}), None,
                    "2026-09-27T02:00:00+00:00"))
        self.assertEqual(["question 1", "question 2"],
                         [job["request"]["question"] for job in self.app.chat_history()])

    def test_rating_idempotence_and_stale_device(self):
        wid = self.app.next_word()["word"]["id"]
        body = {"rating": "known", "requestId": "event-12345", "revision": 0}
        p = self.app.rate_word(wid, body)
        self.assertEqual(p, self.app.rate_word(wid, body))
        self.assertEqual(1, p["recognition"])
        with self.assertRaises(Conflict):
            self.app.rate_word(wid, {"rating": "unknown", "requestId": "event-67890", "revision": 0})
        with self.assertRaises(Conflict):
            self.app.rate_word(wid, {"rating": "known", "requestId": "event-99999", "revision": 1})
        self.assertEqual(1, self.app.dashboard()["counts"]["vocabulary"])

    def test_favorite_does_not_remove_unlearned_word(self):
        w = self.app.next_word()["word"]
        self.app.rate_word(w["id"], {"rating": "favorite", "requestId": "favorite-123", "revision": 0})
        self.assertEqual(w["id"], self.app.next_word()["word"]["id"])

    def test_revealed_card_survives_device_switch_until_next(self):
        w = self.app.next_word()["word"]
        p = self.app.rate_word(w["id"], {"rating": "known", "requestId": "pending-card-123", "revision": 0})
        pending = self.app.next_word()
        self.assertTrue(pending["revealed"])
        self.assertEqual(w["id"], pending["word"]["id"])
        self.app.finish_word({"wordId": w["id"], "ratedOn": p["last"]})
        self.assertFalse(self.app.next_word()["revealed"])
        self.assertNotEqual(w["id"], self.app.next_word()["word"]["id"])

    def test_cross_day_lapses_and_long_review(self):
        wid = self.app.next_word()["word"]["id"]
        revision = 0
        for i, rating in enumerate(["known", "known", "known", "unknown", "unknown"]):
            date = f"2026-10-{i+1:02d}"
            with patch("webapp.service.day", return_value=date):
                p = self.app.rate_word(wid, {"rating": rating, "requestId": "rate-abc-"+str(i), "revision": revision})
                revision = p["revision"]
            if i == 2:
                self.assertEqual("2027-01-01", p["due"])
        self.assertTrue(p["difficult"])
        self.assertEqual(1, p["recognition"])

    def test_real_seed_sunday_and_merged_ethics(self):
        s = self.db.doc("schedule")["value"]
        week = schedule(s, 7)["items"]
        sunday = [o for o in week if o["date"] == "2026-11-01"]
        self.assertEqual([5, 6], sunday[0]["sections"])
        ethics = [o for o in week if o["course"]["name"] == "工程伦理（二）"]
        self.assertEqual([9, 10, 11], ethics[0]["sections"])
        self.assertEqual(2, len(ethics[0]["members"]))
        week11 = [o for o in schedule(s, 11)["items"] if o["course"]["name"] == "工程伦理（二）"]
        self.assertEqual([9, 10], week11[0]["sections"])

    def test_holidays_moves_and_base_preserved(self):
        s = self.db.doc("schedule")["value"]
        c = next(c for c in s["courses"] if c["id"] == "c-material")
        original = copy.deepcopy(c)
        s["adjustments"] = [{"courseId": c["id"], "originalDate": "2026-09-14", "date": "2026-09-25", "sections": [3,4], "room": "新教室", "cancelled": False}]
        valid = validate_schedule(s)
        occurrences = schedule(valid, 2)["items"]
        self.assertTrue(any(o["date"] == "2026-09-25" and o["adjusted"] for o in occurrences))
        self.assertFalse(any(o["date"] == "2026-09-25" and not o["adjusted"] for o in occurrences))
        self.assertEqual(original, c)
        self.assertFalse(any(o["course"]["id"] == c["id"] for o in schedule(valid, 1)["items"]))

    def test_invalid_course_and_orphan_adjustment_rejected(self):
        s = self.db.doc("schedule")["value"]
        for bad in [[], [0], [1, 1], [True], ["2"]]:
            v = copy.deepcopy(s); v["courses"][0]["sections"] = bad
            with self.assertRaises(ValueError): validate_schedule(v)
        s["adjustments"] = [{"courseId": "missing"}]
        with self.assertRaises(ValueError): validate_schedule(s)

    def completed_review(self):
        body = {"requestId": "review-123456", "exerciseId": "resnet-01", "translation": "Training become more difficulty."}
        self.app.submit("review", body)
        deadline = time.monotonic()+3
        while self.app.job(body["requestId"])["status"] not in ("done", "failed") and time.monotonic()<deadline:
            time.sleep(.02)
        return body, self.app.job(body["requestId"])

    def test_review_job_archives_feedback_and_notes_once(self):
        body, job = self.completed_review()
        self.assertEqual("done", job["status"])
        self.assertEqual(3, len(self.app.notes()))
        self.app.submit("review", body)
        self.assertEqual(1, len(self.app.exercise_detail("resnet-01")["attempts"]))
        with self.assertRaises(Conflict): self.app.submit("review", {**body, "translation": "Different answer"})

    def test_backup_restore_transaction_and_quota_preserved(self):
        self.completed_review()
        backup = self.db.backup()
        with self.db.connect(True) as c:
            c.execute("INSERT INTO ai_calls(model,day,started,purpose,status) VALUES ('test',?,0,'review','ok')", (quota_day(),))
        self.app.save_draft("resnet-01", {"text": "More work", "revision": 0})
        self.app.restore(backup, self.db.version())
        self.assertEqual(3, len(self.app.notes()))
        with self.db.connect() as c:
            self.assertEqual(1, c.execute("SELECT count(*) FROM ai_calls").fetchone()[0])
        self.assertTrue(list((self.db.path.parent/"backups").glob("*.sqlite3")))
        malformed = copy.deepcopy(backup); malformed["tables"]["documents"][0]["value"] = "invalid"
        before = self.db.backup()["tables"]
        with self.assertRaises(ValueError): self.app.restore(malformed, self.db.version())
        self.assertEqual(before, self.db.backup()["tables"])

    def test_profile_and_word_prompts_sync_and_restore(self):
        profile = self.app.save_profile({"revision": 0, "value": {"displayName": "小崔", "showName": True, "minimalMode": False}})
        self.assertEqual("小崔", self.app.profile()["value"]["displayName"])
        with self.assertRaises(Conflict):
            self.app.save_profile({"revision": 0, "value": {"displayName": "旧设备", "showName": True, "minimalMode": True}})
        presets = {"presets": [{"id": "prompt-academic", "name": "论文用法", "template": "请比较 {word} 在方法和实验部分的用法。"}]}
        saved = self.app.save_word_prompts({"revision": 0, "value": presets})
        self.assertEqual(presets, self.app.word_prompts()["value"])
        with self.assertRaises(ValueError):
            self.app.save_word_prompts({"revision": saved["revision"], "value": {"presets": [{**presets["presets"][0], "template": "没有占位符"}]}})
        backup = self.db.backup()
        self.app.save_word_prompts({"revision": saved["revision"], "value": {"presets": []}})
        self.app.restore(backup, self.db.version())
        self.assertEqual("小崔", self.app.profile()["value"]["displayName"])
        self.assertEqual(presets, self.app.word_prompts()["value"])

    def test_note_review_and_conflicting_edit(self):
        self.completed_review()
        n = self.app.notes()[0]
        self.app.update_note(n["id"], {"revision": 0, "action": "review", "rating": "known"})
        with self.assertRaises(Conflict): self.app.update_note(n["id"], {"revision": 0, "action": "edit", "text": "old"})

    def test_malformed_ai_feedback_is_rejected(self):
        with self.assertRaises(AIError): parse_json('{"unfinished":')
        bad = copy.deepcopy(FEEDBACK); bad["scores"]["grammar"] = 99
        with self.assertRaises(AIError): validate_feedback(bad)


class QuotaTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = Database(Path(self.temp.name)/"quota.sqlite3")
        self.ai = Gemini(self.db)
        self.cfg = {**self.ai.config[0], "rpd": 3, "rpm": 99, "account": "key2", "group": "project2"}
        self.ai.update(self.cfg["id"], {"status": "available"}, "key2")
    def tearDown(self): self.temp.cleanup()

    def test_atomic_daily_reservation(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as p:
            result = list(p.map(lambda _: self.ai.reserve(self.cfg,"review", 100), range(10)))
        self.assertEqual(3, sum(r is not None for r in result))

    def test_project_groups_share_budget_and_new_day_resets(self):
        cfg = {**self.cfg, "rpd": 1}
        self.ai.update(cfg["id"], {"status": "available"}, "key3")
        self.assertIsNotNone(self.ai.reserve(cfg,"review",100))
        self.assertIsNone(self.ai.reserve({**cfg,"account":"key3"},"review",100))
        with patch("webapp.gemini.quota_day", return_value="2040-01-01"):
            self.assertIsNotNone(self.ai.reserve(cfg,"review",100))

    def test_models_outer_accounts_inner_before_downgrade(self):
        self.ai.config = [{"id":"strong"},{"id":"weak"}]
        calls=[]
        def generate_one(cfg,*_):
            calls.append((cfg["id"],cfg["account"]))
            if cfg["id"] == "strong" and cfg["account"] == "key2": return {"ok":True}
            raise AIError("exhausted")
        with patch("webapp.gemini.accounts", return_value=[{"id":"key1","group":"a"},{"id":"key2","group":"b"}]), patch.object(self.ai,"generate_one",side_effect=generate_one):
            _,model=self.ai.generate("prompt","review")
        self.assertEqual([("strong","key1"),("strong","key2")],calls)
        self.assertIn("strong",model)

    def test_minute_tokens_and_disabled_models(self):
        self.assertIsNone(self.ai.reserve({**self.cfg,"tpm":50},"review",100))
        self.ai.update(self.cfg["id"],{"status":"unavailable"},"key2")
        self.assertIsNone(self.ai.reserve(self.cfg,"review",10))


class HTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.server=make_server("127.0.0.1",0,Path(cls.temp.name)/"http.sqlite3")
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.url="http://127.0.0.1:"+str(cls.server.server_port)
        cls.client=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.server.app.close();cls.temp.cleanup()
    def request(self,path,body=None,headers=None):
        req=urllib.request.Request(self.url+path,data=json.dumps(body).encode() if body is not None else None,headers=headers or {})
        try:
            with self.client.open(req) as r:return r.status,r.read()
        except urllib.error.HTTPError as e:return e.code,e.read()
    def test_health_and_private_files(self):
        self.assertEqual(200,self.request('/api/health')[0])
        for p in ['/.env','/../.env','/runtime/studydesk.sqlite3','/%2e%2e/.env','/service.py']:
            self.assertEqual(404,self.request(p)[0])
    def test_cross_origin_and_rebinding_rejected(self):
        self.assertEqual(403,self.request('/api/settings',{}, {'Content-Type':'application/json'})[0])
        self.assertEqual(403,self.request('/api/settings',{}, {'Content-Type':'application/json','X-StudyDesk':'1','Origin':'https://evil.example'})[0])
        self.assertEqual(403,self.request('/api/health',headers={'Host':'evil.example'})[0])

    def test_lan_apk_publication_endpoints(self):
        with tempfile.TemporaryDirectory() as location, patch("webapp.server.RELEASES", Path(location)):
            self.assertEqual(404, self.request('/api/android/latest')[0])
            self.assertEqual(200, self.request('/install')[0])
            apk = b"example signed APK bytes"
            (Path(location) / "StudyDesk-7.apk").write_bytes(apk)
            release = {"packageName": "io.github.cuimiles.studydesk", "versionCode": 7,
                       "versionName": "1.2.0-lan", "size": len(apk), "sha256": hashlib.sha256(apk).hexdigest(),
                       "url": "/api/android/apk/7"}
            (Path(location) / "latest.json").write_text(json.dumps(release))
            self.assertEqual(release, json.loads(self.request('/api/android/latest')[1]))
            self.assertEqual(apk, self.request('/android.apk')[1])
            self.assertEqual(apk, self.request('/api/android/apk/7')[1])
            self.assertEqual(404, self.request('/api/android/apk/6')[0])
            self.assertEqual(404, self.request('/api/android/apk/../.env')[0])
            head = urllib.request.Request(self.url + '/android.apk', method='HEAD')
            with self.client.open(head) as response:
                self.assertEqual(len(apk), int(response.headers['Content-Length']))
                self.assertEqual(b'', response.read())
            release['url'] = '/../../.env'
            (Path(location) / "latest.json").write_text(json.dumps(release))
            self.assertEqual(404, self.request('/api/android/latest')[0])


if __name__ == "__main__": unittest.main()
