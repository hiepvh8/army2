#!/usr/bin/env python3
"""Exercise a Java ME JAR in a disposable emulator process; never edit the JAR."""
import argparse
from datetime import datetime, timezone
import hashlib
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('jar', nargs='?', default=str(ROOT.parent / 'IOS_TuTien.jar'))
args = parser.parse_args()
jar = Path(args.jar).resolve(strict=True)
with zipfile.ZipFile(jar) as z:
    if z.testzip() is not None:
        raise SystemExit('JAR ZIP integrity check failed')
print('JAR SHA-256:', hashlib.sha256(jar.read_bytes()).hexdigest(), flush=True)
output = ROOT / 'test-results/original-jar' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')
output.mkdir(parents=True)
emulator, compiler = ROOT / '.tools/microemulator.jar', ROOT / '.tools/ecj.jar'
if not emulator.is_file() or not compiler.is_file():
    raise SystemExit('Missing tools: run python3 tools/build_client.py and python3 tools/test_emulator.py first')
with tempfile.TemporaryDirectory() as temporary:
    classes = Path(temporary) / 'classes'
    classes.mkdir()
    subprocess.run(['java', '-jar', str(compiler), '-1.8', '-nowarn', '-cp', str(emulator),
                    '-d', str(classes), str(ROOT / 'tests/OriginalAudit.java')], check=True)
    with (output / 'run.log').open('w') as log:
        result = subprocess.run(['java', '-Djava.awt.headless=true',
                                 '-Dmicroemulator.config.path=' + str(Path(temporary) / 'config'),
                                 '-cp', str(classes) + ':' + str(emulator), 'OriginalAudit', str(jar), str(output)],
                                stdout=log, stderr=log, timeout=120)
    content = (output / 'run.log').read_text(errors='replace')
    for line in content.splitlines():
        if any(label in line for label in ('LOGIN_FAILED:', 'REGISTER_FAILED:', 'CONTINUE_FAILED:',
                                            'SETTINGS_SAVE_PASS', 'SAVE_RELOAD_PASS', 'BOSS_LIST_SCREEN=',
                                            'BOSS_READY_SCREEN=', 'BOSS_BATTLE_SCREEN=', 'ORIGINAL_AUDIT_PASS')):
            print(line)
    print('Screenshots and log:', output)
    if result.returncode or 'ORIGINAL_AUDIT_PASS' not in content:
        raise SystemExit('Audit was incomplete; inspect run.log')
    if 'FAILED:' in content:
        print('Audit completed WITH failures; do not interpret completion as a pass.')
