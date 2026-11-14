#!/usr/bin/env python3
"""Start a local backend and open the Java ME JAR on a graphical desktop."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
EMULATOR_SHA = 'dbd5f3eb8365d3e839d6a203149e0e3776fc1a0585e16ac1fc23f76c9fcae1c6'
EMULATOR_URL = 'https://repo.maven.apache.org/maven2/org/microemu/microemulator/2.0.4/microemulator-2.0.4.jar'


def ensure_emulator():
    path = ROOT / '.tools/microemulator.jar'
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        with urllib.request.urlopen(EMULATOR_URL, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != EMULATOR_SHA:
            raise RuntimeError('Checksum MicroEmulator khong dung.')
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temp:
            temp.write(data)
            name = temp.name
        os.replace(name, path)
    if hashlib.sha256(path.read_bytes()).hexdigest() != EMULATOR_SHA:
        raise RuntimeError('Checksum MicroEmulator khong dung.')
    return path


def health():
    try:
        with urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=1) as response:
            data = json.load(response)
        return data.get('status') == 'ok' and data.get('protocol') == 1
    except (OSError, ValueError, urllib.error.URLError):
        return False


def main():
    parser = argparse.ArgumentParser(description='Mo JAR Java ME va server local de thu nghiem')
    parser.add_argument('--check', action='store_true', help='kiem tra JAR, emulator va backend ma khong mo cua so')
    args = parser.parse_args()
    jar = ROOT / 'dist/ARMY2_Online_IOS.jar'
    if not jar.exists():
        subprocess.run([sys.executable, str(ROOT / 'tools/build_client.py')], cwd=ROOT, check=True)
    emulator = ensure_emulator()
    if not args.check and not (os.environ.get('DISPLAY') or os.environ.get('WAYLAND_DISPLAY')):
        raise RuntimeError('Chua co man hinh do hoa. Mo Desktop cua moi truong, roi chay lai lenh trong terminal cua Desktop.')
    server = None
    try:
        if not health():
            (ROOT / 'data').mkdir(exist_ok=True)
            server = subprocess.Popen([sys.executable, '-m', 'server.main', '--host', '127.0.0.1', '--port', '8080',
                                       '--db', str(ROOT / 'data/army2.sqlite3')], cwd=ROOT)
            for _ in range(50):
                if health():
                    break
                if server.poll() is not None:
                    raise RuntimeError('Khong khoi dong duoc server. Kiem tra cong 8080.')
                time.sleep(.1)
            else:
                raise RuntimeError('Server khong san sang sau 5 giay.')
        if args.check:
            print('JAR, MicroEmulator va server local: OK')
            return
        print('Dang mo MicroEmulator; giu terminal nay mo trong luc choi.', flush=True)
        subprocess.run(['java', '-jar', str(emulator), str(jar)], cwd=ROOT, check=True)
    finally:
        if server is not None:
            server.terminate()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError) as exc:
        print('Khong mo duoc gia lap:', exc, file=sys.stderr)
        sys.exit(1)
