#!/usr/bin/env python3
"""Exercise original offline bot setup and observe a bot shot in MicroEmulator."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
classes = root / 'build/bot-audit'
classes.mkdir(parents=True, exist_ok=True)
out = root / 'test-results/bot-audit'
out.mkdir(parents=True, exist_ok=True)
emu = root / '.tools/microemulator.jar'
subprocess.run(['java','-jar',str(root/'.tools/ecj.jar'),'-1.8','-nowarn',
                '-classpath',str(emu),'-d',str(classes),
                str(root/'tests/EmulatorClient.java'),str(root/'tests/BotAudit.java')],check=True)
with (out/'audit.log').open('w') as log:
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(emu),
                    'BotAudit',str(root/'dist/ARMY2_Online_IOS.jar'),str(out)],
                   stdout=log,stderr=log,check=True,timeout=100)
log=(out/'audit.log').read_text()
assert 'BOT_AUDIT_PASS' in log
for line in log.splitlines():
    if line.startswith(('BOT_TURN_INDEX=','BOT_SHOT_OBSERVED=','BOT_PLAYER_MOVE=',
                        'BOT_AIM ', 'BOT_HP_PLAYER=', 'BOT_AUDIT_PASS')):
        print(line)
print('Images and complete log:',out)
