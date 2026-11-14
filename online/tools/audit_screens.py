#!/usr/bin/env python3
"""Run screen audit on a disposable local account and real MicroEmulator HTTP."""
from pathlib import Path
import subprocess
import sys
import tempfile
import threading

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from server.main import Application,make_server

classes=ROOT/'build/emulator-test'
classes.mkdir(parents=True,exist_ok=True)
emulator=ROOT/'.tools/microemulator.jar'
subprocess.run(['java','-jar',str(ROOT/'.tools/ecj.jar'),'-1.8','-nowarn','-classpath',str(emulator),
                '-d',str(classes),str(ROOT/'tests/EmulatorClient.java'),str(ROOT/'tests/ScreenAudit.java')],check=True)
out=ROOT/'test-results/screen-audit'
out.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory() as tmp:
    app=Application(str(Path(tmp)/'audit.sqlite3'))
    http=make_server('127.0.0.1',0,app)
    thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
    try:
        with (out/'audit.log').open('w') as log:
            subprocess.run(['java','-Djava.awt.headless=true','-Dmicroemulator.config.path='+str(Path(tmp)/'config'),
                            '-cp',str(classes)+':'+str(emulator),'ScreenAudit',str(ROOT/'dist/ARMY2_Online_IOS.jar'),
                            'http://127.0.0.1:'+str(http.server_port),str(out)],cwd=out,stdout=log,stderr=log,
                           timeout=120,check=True)
        assert 'SCREEN_AUDIT_PASS' in (out/'audit.log').read_text()
        print('PASS: screen navigation, native text input, validation, registration, login and logout')
    finally:
        http.shutdown();http.server_close();thread.join();app.game.db.close()
