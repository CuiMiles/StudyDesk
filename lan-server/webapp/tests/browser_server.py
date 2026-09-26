"""Isolated browser-test service; never writes production study records or calls Gemini."""
import tempfile
from pathlib import Path
from webapp.server import make_server
from webapp.tests.test_app import FakeAI

if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="studydesk-browser-") as folder:
        server = make_server("127.0.0.1", 18765, Path(folder) / "study.sqlite3")
        # Only the isolated test process swaps the generation function; retain model summary UI.
        server.app.ai.generate = FakeAI().generate
        print("Browser test server ready: 18765", flush=True)
        try:
            server.serve_forever()
        finally:
            server.server_close()
            server.app.close()
