#!/usr/bin/env python3
"""Single-user LAN HTTP service; Python 3.11+, no runtime dependencies."""
import argparse
import ipaddress
import json
import mimetypes
import os
import signal
import socket
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from .content import ROOT, schedule, validate_schedule
from .db import Conflict, Database, day, dumps
from .gemini import AIError
from .service import StudyService

STATIC = Path(__file__).parent / "static"


class Handler(BaseHTTPRequestHandler):
    server_version = "StudyDesk/1.0"
    protocol_version = "HTTP/1.1"

    def setup(self):
        super().setup()
        self.connection.settimeout(20)

    @property
    def app(self):
        return self.server.app

    def log_message(self, fmt, *args):
        # Do not log URLs, bodies, headers, user writing, or credentials.
        pass

    def finish_headers(self, code, content_type, size, headers=None):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(size))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; font-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()

    def respond(self, data, code=200, headers=None):
        body = dumps(data).encode()
        self.finish_headers(code, "application/json; charset=utf-8", len(body), headers)
        if self.command != "HEAD":
            self.wfile.write(body)

    def file(self, path, content_type=None, headers=None):
        data = path.read_bytes()
        self.finish_headers(200, content_type or mimetypes.guess_type(str(path))[0] or "application/octet-stream", len(data), headers)
        if self.command != "HEAD":
            self.wfile.write(data)

    def valid_host(self):
        raw = self.headers.get("Host", "")
        try:
            host = urllib.parse.urlsplit("http://" + raw).hostname
            if host in {"localhost", socket.gethostname().lower()}:
                return True
            addr = ipaddress.ip_address(host)
            return addr.is_private or addr.is_loopback
        except ValueError:
            return False

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        self.dispatch("GET")

    def do_POST(self):
        self.dispatch("POST")

    def dispatch(self, method):
        try:
            if not self.valid_host():
                self.respond({"error": "仅允许局域网地址访问"}, 403)
                return
            parsed = urllib.parse.urlsplit(self.path)
            path = parsed.path
            q = {k: v[0] for k, v in urllib.parse.parse_qs(parsed.query).items()}
            if method == "GET":
                self.get(path, q)
                return
            # Reject cross-site mutations. A custom header cannot be sent by an HTML form.
            origin = self.headers.get("Origin")
            if self.headers.get("X-StudyDesk") != "1" or self.headers.get_content_type() != "application/json" or (origin and urllib.parse.urlsplit(origin).netloc != self.headers.get("Host")):
                self.respond({"error": "请求来源无效，请刷新页面"}, 403)
                return
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= (32_000_000 if path == "/api/restore" else 2_000_000):
                self.close_connection = True
                self.respond({"error": "请求为空或超过大小限制"}, 413)
                return
            body = json.loads(self.rfile.read(size))
            if not isinstance(body, dict):
                raise ValueError("请求必须是 JSON 对象")
            self.post(path, body)
        except Conflict as exc:
            self.respond({"error": str(exc)}, 409)
        except AIError as exc:
            self.respond({"error": str(exc)}, 503)
        except (ValueError, TypeError, KeyError) as exc:
            self.respond({"error": str(exc) if isinstance(exc, ValueError) else "请求格式不正确"}, 400)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True
        except Exception as exc:
            print("request_failed", type(exc).__name__, flush=True)
            self.respond({"error": "服务暂时无法处理请求，已保存的数据不会丢失。"}, 500)

    def get(self, path, q):
        app = self.app
        if path == "/api/health":
            return self.respond({"ok": True, "service": "StudyDesk", "version": 1})
        if path == "/api/sync":
            return self.respond({"version": app.db.version(), "day": day()})
        if path == "/api/dashboard":
            return self.respond(app.dashboard())
        if path == "/api/models":
            return self.respond(app.ai.summary())
        if path == "/api/settings":
            return self.respond(app.db.doc("settings"))
        if path == "/api/profile":
            return self.respond(app.profile())
        if path == "/api/word-prompts":
            return self.respond(app.word_prompts())
        if path == "/api/papers":
            return self.respond(app.content.papers)
        if path == "/api/exercises":
            return self.respond(app.exercises(q.get("level", ""), q.get("category", ""), q.get("paper", "")))
        if path.startswith("/api/exercises/"):
            eid = path.rsplit("/", 1)[1]
            if eid not in app.content.exercise_map:
                return self.respond({"error": "练习不存在"}, 404)
            return self.respond(app.exercise_detail(eid, q.get("reference") == "1"))
        if path == "/api/vocabulary":
            return self.respond(app.words(q.get("q", "")[:100], q.get("level", ""), q.get("filter", ""), max(0, int(q.get("offset", 0)))))
        if path == "/api/vocabulary/next":
            return self.respond(app.next_word())
        if path.startswith("/api/vocabulary/"):
            return self.respond(app.word(urllib.parse.unquote(path.rsplit("/", 1)[1])))
        if path == "/api/notebook":
            return self.respond(app.notes())
        if path == "/api/schedule":
            value = app.db.doc("schedule")
            week = int(q.get("week", app.dashboard()["week"]))
            if not 1 <= week <= 18:
                raise ValueError("周次须在 1–18 之间")
            return self.respond({**value, **schedule(value["value"], week)})
        if path == "/api/chat":
            return self.respond(app.chat_history())
        if path.startswith("/api/jobs/"):
            return self.respond(app.job(path.rsplit("/", 1)[1]))
        if path == "/api/backup":
            return self.respond(app.db.backup(), headers={"Content-Disposition": 'attachment; filename="studydesk-' + day() + '.json"'})
        if path.startswith("/papers/") and path.endswith(".pdf"):
            pid = path.removeprefix("/papers/").removesuffix(".pdf")
            pdf = app.db.path.parent / "papers" / (pid + ".pdf")
            if pid in app.content.paper_map and pdf.is_file():
                return self.file(pdf, "application/pdf")
        if path.startswith("/licenses/"):
            name = path.removeprefix("/licenses/")
            choices = {"CC-BY-4.0.txt", "CC-BY-SA-4.0.txt", "wordfreq-NOTICE.txt"}
            if name in choices:
                return self.file(ROOT / "miniprogram/vocabulary/licenses" / name, "text/plain; charset=utf-8")
        files = {"/": "index.html", "/index.html": "index.html", "/app.js": "app.js", "/style.css": "style.css",
                 "/icon.svg": "icon.svg", "/manifest.webmanifest": "manifest.webmanifest"}
        if path in files:
            return self.file(STATIC / files[path])
        self.respond({"error": "页面不存在"}, 404)

    def post(self, path, body):
        app = self.app
        if path == "/api/settings":
            return self.respond(app.settings(body))
        if path == "/api/profile":
            return self.respond(app.save_profile(body))
        if path == "/api/word-prompts":
            return self.respond(app.save_word_prompts(body))
        if path == "/api/schedule":
            value = validate_schedule(body.get("value"))
            if type(body.get("revision")) is not int:
                raise ValueError("缺少课表版本")
            return self.respond(app.db.put("schedule", value, body["revision"]))
        if path.startswith("/api/drafts/"):
            return self.respond(app.save_draft(path.rsplit("/", 1)[1], body))
        if path == "/api/vocabulary/next":
            return self.respond(app.finish_word(body))
        if path.startswith("/api/vocabulary/"):
            return self.respond(app.rate_word(urllib.parse.unquote(path.rsplit("/", 1)[1]), body))
        if path.startswith("/api/notebook/"):
            return self.respond(app.update_note(path.rsplit("/", 1)[1], body))
        if path in ("/api/review", "/api/ask", "/api/probe"):
            return self.respond(app.submit(path.rsplit("/", 1)[1], body), 202)
        if path == "/api/restore":
            return self.respond(app.restore(body.get("backup"), body.get("revision")))
        return self.respond({"error": "接口不存在"}, 404)


def make_server(host, port, database):
    server = ThreadingHTTPServer((host, port), Handler)
    server.daemon_threads = True
    server.app = StudyService(Database(database))
    return server


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=int(os.environ.get("STUDYDESK_PORT", "8765")))
    parser.add_argument("--database", default=str(Path(__file__).parent / "runtime/studydesk.sqlite3"))
    args = parser.parse_args()
    os.umask(0o077)
    server = make_server(args.host, args.port, args.database)
    server.app.db.snapshot()
    stop = threading.Event()
    def backups():
        last = day()
        while not stop.wait(60):
            if day() != last:
                try:
                    server.app.db.snapshot()
                    last = day()
                    snapshots = sorted((server.app.db.path.parent / "backups").glob("studydesk-*.sqlite3"))
                    for p in snapshots[:-30]:
                        p.unlink()
                except OSError:
                    print("daily_backup_failed", flush=True)
    threading.Thread(target=backups, daemon=True).start()
    def shutdown(*_):
        stop.set()
        threading.Thread(target=server.shutdown, daemon=True).start()
    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)
    print(f"StudyDesk ready on {args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    finally:
        stop.set()
        server.server_close()
        server.app.close()


if __name__ == "__main__":
    main()
