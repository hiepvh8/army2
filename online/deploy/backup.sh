#!/usr/bin/env bash
set -euo pipefail
umask 077
install -d -m 0700 /var/backups/army2-online
/usr/bin/python3 - <<'PY'
from datetime import datetime, timezone
from pathlib import Path
import sqlite3
source = Path('/var/lib/army2-online/army2.sqlite3')
if not source.exists():
    raise SystemExit('Database not created yet')
folder = Path('/var/backups/army2-online')
path = folder / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S') + '.sqlite3')
with sqlite3.connect('file:' + str(source) + '?mode=ro', uri=True) as src, sqlite3.connect(path) as dst:
    src.backup(dst)
    assert dst.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
for old in sorted(folder.glob('*.sqlite3'))[:-7]:
    old.unlink()
print('Backup complete:', path.name)
PY
