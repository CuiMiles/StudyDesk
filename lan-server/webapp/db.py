import contextlib
import datetime as dt
import json
import sqlite3
from pathlib import Path
from zoneinfo import ZoneInfo


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")


def day():
    return dt.datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat()


def quota_day():
    return dt.datetime.now(ZoneInfo("America/Los_Angeles")).date().isoformat()


def dumps(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


class Conflict(Exception):
    pass


class Database:
    def __init__(self, path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as c:
            c.execute("PRAGMA journal_mode=WAL")
            c.executescript("""
                CREATE TABLE IF NOT EXISTS documents (
                    key TEXT PRIMARY KEY, value TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS vocabulary (
                    id TEXT PRIMARY KEY, value TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS events (
                    id TEXT PRIMARY KEY, kind TEXT NOT NULL, day TEXT NOT NULL,
                    target TEXT NOT NULL, value TEXT NOT NULL);
                CREATE INDEX IF NOT EXISTS events_day ON events(day, kind);
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY, kind TEXT NOT NULL, request TEXT NOT NULL,
                    status TEXT NOT NULL, result TEXT, error TEXT, created TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS attempts (
                    id TEXT PRIMARY KEY, exercise TEXT NOT NULL, translation TEXT NOT NULL,
                    feedback TEXT NOT NULL, model TEXT NOT NULL, created TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS notebook (
                    id TEXT PRIMARY KEY, kind TEXT NOT NULL, value TEXT NOT NULL,
                    revision INTEGER NOT NULL DEFAULT 0, created TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS ai_calls (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, model TEXT NOT NULL,
                    day TEXT NOT NULL, started REAL NOT NULL, purpose TEXT NOT NULL,
                    status TEXT NOT NULL, input_tokens INTEGER NOT NULL DEFAULT 0,
                    output_tokens INTEGER NOT NULL DEFAULT 0);
                CREATE INDEX IF NOT EXISTS ai_calls_budget ON ai_calls(model, day, started);
                CREATE TABLE IF NOT EXISTS models (
                    id TEXT PRIMARY KEY, value TEXT NOT NULL);
            """)
            if "account" not in {r[1] for r in c.execute("PRAGMA table_info(ai_calls)")}:
                c.execute("ALTER TABLE ai_calls ADD COLUMN account TEXT NOT NULL DEFAULT 'key1'")
            c.execute("CREATE INDEX IF NOT EXISTS ai_calls_account ON ai_calls(account,model,day,started)")
            # Migrate the first-key records without resetting any historic counters.
            for row in c.execute("SELECT id,value FROM models WHERE id NOT LIKE '%:%'").fetchall():
                c.execute("INSERT OR IGNORE INTO models VALUES (?,?)", ("key1:" + row[0], row[1]))
                c.execute("DELETE FROM models WHERE id=?", (row[0],))
        self.path.chmod(0o600)

    @contextlib.contextmanager
    def connect(self, write=False):
        c = sqlite3.connect(self.path, timeout=15)
        c.row_factory = sqlite3.Row
        c.execute("PRAGMA busy_timeout=15000")
        try:
            if write:
                c.execute("BEGIN IMMEDIATE")
            yield c
            c.commit()
        except BaseException:
            c.rollback()
            raise
        finally:
            c.close()

    def doc(self, key, default=None, conn=None):
        if conn is None:
            with self.connect() as c:
                return self.doc(key, default, c)
        row = conn.execute("SELECT value,revision FROM documents WHERE key=?", (key,)).fetchone()
        return {"value": json.loads(row["value"]) if row else default,
                "revision": row["revision"] if row else 0}

    def put(self, key, value, revision=None, conn=None):
        if conn is None:
            with self.connect(True) as c:
                return self.put(key, value, revision, c)
        old = self.doc(key, conn=conn)
        if revision is not None and revision != old["revision"]:
            raise Conflict("另一台设备已修改此内容，请刷新后重试；当前输入仍保留。")
        rev = old["revision"] + 1
        conn.execute("INSERT INTO documents VALUES (?,?,?) ON CONFLICT(key) "
                     "DO UPDATE SET value=excluded.value,revision=excluded.revision", (key, dumps(value), rev))
        self.bump(conn)
        return {"value": value, "revision": rev}

    def bump(self, c):
        c.execute("INSERT INTO documents VALUES ('_version','0',1) ON CONFLICT(key) "
                  "DO UPDATE SET revision=revision+1")

    def version(self):
        return self.doc("_version")["revision"]

    def backup(self):
        with self.connect() as c:
            c.execute("BEGIN")
            return {"format": "studydesk-web", "version": 1, "exportedAt": now(),
                    "tables": {name: [dict(r) for r in c.execute(f"SELECT * FROM {name}")]
                               for name in ("documents", "vocabulary", "events", "attempts", "notebook")}}

    def snapshot(self):
        folder = self.path.parent / "backups"
        folder.mkdir(exist_ok=True)
        target = folder / ("studydesk-" + dt.datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".sqlite3")
        with self.connect() as c, sqlite3.connect(target) as dest:
            c.backup(dest)
        target.chmod(0o600)
        return target
