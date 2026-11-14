#!/usr/bin/env python3
"""Run the shipped JAR in two independent MicroEmulator JVMs, with real networking."""
import hashlib
import urllib.request
import os
from pathlib import Path
import subprocess
import ssl
import sys
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from server.main import Application, make_server



def main():
    emulator = ROOT / '.tools/microemulator.jar'
    if not emulator.exists():
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/microemu/microemulator/2.0.4/microemulator-2.0.4.jar', timeout=60) as r:
            emulator.write_bytes(r.read())
    if hashlib.sha256(emulator.read_bytes()).hexdigest() != 'dbd5f3eb8365d3e839d6a203149e0e3776fc1a0585e16ac1fc23f76c9fcae1c6':
        raise SystemExit('Emulator checksum mismatch')
    classes = ROOT / 'build/emulator-test'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run(['java', '-jar', str(ROOT / '.tools/ecj.jar'), '-1.8', '-nowarn', '-classpath', str(emulator),
                    '-d', str(classes), str(ROOT / 'tests/EmulatorClient.java')], check=True)
    modes = sys.argv[1:] or ['duel', 'pvp', 'boss', 'offline']
    if any(mode not in ('duel', 'pvp', 'boss', 'offline') for mode in modes):
        raise SystemExit('Modes: duel, pvp, boss, offline')
    width, height = int(os.getenv('ARMY_TEST_WIDTH','480')), int(os.getenv('ARMY_TEST_HEIGHT','800'))
    if not (200 <= width <= 1024 and 240 <= height <= 1024):
        raise SystemExit('Unsupported test screen dimensions')
    for mode in modes:
        suffix = '' if (width,height)==(480,800) else f'-{width}x{height}'
        output = ROOT / 'test-results' / (mode + suffix)
        output.mkdir(parents=True, exist_ok=True)
        (output / 'room.txt').unlink(missing_ok=True)
        (output / 'guest-not-turn.txt').unlink(missing_ok=True)
        for role in ('host', 'guest'):
            (output / (role+'-result.txt')).unlink(missing_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            app = Application(str(Path(tmp) / 'game.db'))
            http = make_server('127.0.0.1', 0, app)
            java_tls = []
            scheme = 'http'
            if mode == 'boss':
                # A locally trusted test CA verifies the HTTPS code path without disabling TLS checks.
                cert, key, trust = [str(Path(tmp) / n) for n in ('cert.pem', 'key.pem', 'trust.p12')]
                subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                                '-keyout', key, '-out', cert, '-subj', '/CN=localhost',
                                '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'],
                               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run(['keytool', '-importcert', '-noprompt', '-alias', 'local-test', '-file', cert,
                                '-keystore', trust, '-storepass', 'test-only-password'],
                               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                tls.load_cert_chain(cert, key)
                http.socket = tls.wrap_socket(http.socket, server_side=True)
                java_tls = ['-Djavax.net.ssl.trustStore=' + trust, '-Djavax.net.ssl.trustStorePassword=test-only-password']
                scheme = 'https'
            worker = threading.Thread(target=http.serve_forever, daemon=True)
            worker.start()
            processes, logs = [], []
            try:
                roles = ('host',) if mode == 'offline' else ('host', 'guest')
                for role in roles:
                    import shutil
                    shutil.rmtree(output / (role + '-rms'), ignore_errors=True)
                    log = (output / (role + '.log')).open('w')
                    logs.append(log)
                    cmd = ['java', *java_tls, '-Djava.awt.headless=true', '-Darmy.test.width='+str(width), '-Darmy.test.height='+str(height), '-Dmicroemulator.config.path=' + str(Path(tmp) / role), '-cp', str(classes) + os.pathsep + str(emulator),
                           'EmulatorClient', str(ROOT / 'dist/ARMY2_Online_IOS.jar'),
                           scheme + '://127.0.0.1:' + str(http.server_port), str(output), role, mode]
                    processes.append(subprocess.Popen(cmd, cwd=output, stdout=log, stderr=log))
                for process in processes:
                    if process.wait(timeout=120) != 0:
                        raise RuntimeError('Emulator failed: inspect ' + str(output))
                for role in roles:
                    if ('EMULATOR_CLIENT_PASS ' + role) not in (output / (role + '.log')).read_text():
                        raise RuntimeError('Emulator did not complete test: ' + role)
                    print((output / (role + '-result.txt')).read_text())
            finally:
                for p in processes:
                    if p.poll() is None:
                        p.terminate()
                        p.wait(timeout=10)
                for log in logs:
                    log.close()
                http.shutdown(); http.server_close(); worker.join(); app.game.db.close()
    print('PASS: requested emulator modes completed: ' + ', '.join(modes))


if __name__ == '__main__':
    main()
