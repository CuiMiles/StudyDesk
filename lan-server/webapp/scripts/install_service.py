#!/usr/bin/env python3
"""Install this checkout as a user-level systemd service; never includes provider keys."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--proxy", default="", help="Optional HTTP proxy used only for Gemini, e.g. http://127.0.0.1:17891")
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("invalid port")
    if any(c in args.proxy for c in '\n\r"'):
        parser.error("invalid proxy")
    config = Path.home() / ".config/systemd/user"
    config.mkdir(parents=True, exist_ok=True)
    unit = config / "studydesk-web.service"
    working_directory = str(ROOT).replace(" ", "\\x20")
    content = f'''[Unit]
Description=StudyDesk LAN learning workspace
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory={working_directory}
ExecStart="{sys.executable}" -m webapp.server --host 0.0.0.0 --port {args.port}
Environment=PYTHONUNBUFFERED=1
Environment=PYTHONDONTWRITEBYTECODE=1
Environment="STUDYDESK_GEMINI_PROXY={args.proxy}"
Restart=on-failure
RestartSec=5
TimeoutStopSec=90
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths="{ROOT / 'webapp/runtime'}"

[Install]
WantedBy=default.target
'''
    (ROOT / "webapp/runtime").mkdir(parents=True, exist_ok=True)
    os.chmod(ROOT / "webapp/runtime", 0o700)
    unit.write_text(content)
    subprocess.run(["systemctl", "--user", "daemon-reload"], check=True)
    subprocess.run(["systemctl", "--user", "enable", "--now", "studydesk-web.service"], check=True)
    print(f"Installed {unit}; listening on port {args.port}")
    print("For startup without login, the user needs loginctl enable-linger (if not already enabled).")
