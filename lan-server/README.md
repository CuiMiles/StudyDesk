# StudyDesk LAN server

See [the complete deployment and usage guide](webapp/README.md).

Run from this directory with Python 3.11+: `python3 -m webapp.server --port 8765`.
Copy `.env.example` to `.env` and fill in your own keys on the server only. No provider keys or user study records are bundled.

Optional pre-generated vocabulary content is reused read-only from `../app/src/main/assets/content.db`.
