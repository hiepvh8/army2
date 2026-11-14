#!/usr/bin/env python3
"""Exercise real original saves across two MIDlet installs and two restarts."""
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import threading

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from server.main import Application, make_server


def main():
    emulator=ROOT/'.tools/microemulator.jar'
    if not emulator.exists():
        raise SystemExit('Run tools/test_emulator.py once to install its pinned MicroEmulator.')
    classes=ROOT/'build/emulator-test';classes.mkdir(parents=True,exist_ok=True)
    subprocess.run(['java','-jar',str(ROOT/'.tools/ecj.jar'),'-1.8','-nowarn',
                    '-classpath',str(emulator),'-d',str(classes),
                    str(ROOT/'tests/EmulatorClient.java'),str(ROOT/'tests/ProfileEmulatorClient.java')],check=True)
    out=ROOT/'test-results/profile';out.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        app=Application(str(Path(tmp)/'game.sqlite3'))
        http=make_server('127.0.0.1',0,app)
        cert, key, trust = [str(Path(tmp)/name) for name in ('cert.pem','key.pem','trust.p12')]
        subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1',
                        '-keyout',key,'-out',cert,'-subj','/CN=localhost',
                        '-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],
                       check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        subprocess.run(['keytool','-importcert','-noprompt','-alias','local-test','-file',cert,
                        '-keystore',trust,'-storepass','test-only-password'],
                       check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(cert,key)
        http.socket=tls.wrap_socket(http.socket,server_side=True)
        worker=threading.Thread(target=http.serve_forever,daemon=True);worker.start()
        try:
            for role in ('host','guest','restore','verify','undo','verifyundo','conflict','remoteverify'):
                config=Path(tmp)/('host' if role in ('host','remoteverify') else 'guest')
                config.mkdir(exist_ok=True)
                cmd=['java','-Djava.awt.headless=true','-Duser.home='+str(config),
                     '-Djavax.net.ssl.trustStore='+trust,'-Djavax.net.ssl.trustStorePassword=test-only-password',
                     '-cp',str(classes)+':'+str(emulator),'ProfileEmulatorClient',
                     str(ROOT/'dist/ARMY2_Online_IOS.jar'),
                     'https://127.0.0.1:'+str(http.server_port),str(out),role]
                with (out/(role+'.log')).open('w') as log:
                    subprocess.run(cmd,cwd=out,stdout=log,stderr=log,check=True,timeout=100)
                if 'PROFILE_CLIENT_PASS '+role not in (out/(role+'.log')).read_text():
                    raise RuntimeError('Emulator did not complete '+role)
                print((out/(role+'-result.txt')).read_text(),flush=True)
            with app.lock:
                row=app.game.db.execute('SELECT revision FROM original_profiles').fetchone()
                assert row['revision']==2
        finally:
            http.shutdown();http.server_close();worker.join();app.game.db.close()
    print('PASS: original character transferred over HTTPS, restored and restored again across JVM restarts; original boss shot executed.')


if __name__=='__main__':main()
