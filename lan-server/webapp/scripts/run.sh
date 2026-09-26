#!/usr/bin/env bash
set -euo pipefail
STUDYDESK_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$STUDYDESK_ROOT"
if [[ -n "${STUDYDESK_PYTHON:-}" ]]; then
  STUDYDESK_EXEC="$STUDYDESK_PYTHON"
elif [[ -x /home/CuiMinghao/envs/multimodal/bin/python ]]; then
  STUDYDESK_EXEC=/home/CuiMinghao/envs/multimodal/bin/python
else
  STUDYDESK_EXEC=python3
fi
"$STUDYDESK_EXEC" -c 'import sys; assert sys.version_info >= (3,11), "StudyDesk requires Python 3.11+"'
exec "$STUDYDESK_EXEC" -m webapp.server "$@"
