#!/usr/bin/env python3
"""Reproducible additive CLDC build. Only the output JAR is modified."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAVEN = 'https://repo.maven.apache.org/maven2/'
ARTIFACTS = {
    'ecj.jar': ('org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar', 'f7686c4960cf70c2ebc5c500a73a8cfc04541b730c18f1c5c21329889b137f45'),
    'cldc.jar': ('org/microemu/cldcapi11/2.0.4/cldcapi11-2.0.4.jar', 'f86f41bfb4097e4bd9cdfeca528960f6977cbda0f5785342adef9909a6a3baef'),
    'midp.jar': ('org/microemu/midpapi20/2.0.4/midpapi20-2.0.4.jar', 'b7f6ed5e0344c7000643ecc114e6edff3587e8e77face546af0e12ffd6dbe826'),
    'proguard.jar': ('net/sf/proguard/proguard-base/6.2.2/proguard-base-6.2.2.jar', '7587a3a415d6aea1946f597f4babe710a245f085bac4dc5e7cda653acfc39b03'),
}
SOURCE_SHA = '27c94dc245cb70fe9514f9906a0a57209e63aaca2e670027b23037a606b95cc4'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--server', default='http://127.0.0.1:8080')
    args = parser.parse_args()
    endpoint = args.server.rstrip('/')
    if not (endpoint.startswith('https://') or endpoint.startswith('http://127.0.0.1:') or endpoint.startswith('http://localhost:')):
        parser.error('Use HTTPS for a remote server, HTTP only for loopback development.')
    if any(c in endpoint for c in '\r\n') or len(endpoint) > 220 or not endpoint.isascii():
        parser.error('Invalid server URL')
    tools, build, dist = [ROOT / n for n in ('.tools', 'build', 'dist')]
    for p in (tools, build / 'classes', dist):
        p.mkdir(parents=True, exist_ok=True)
    for name, (path, digest) in ARTIFACTS.items():
        target = tools / name
        if not target.exists():
            with urllib.request.urlopen(MAVEN + path, timeout=60) as response:
                target.write_bytes(response.read())
        if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            raise SystemExit('Checksum failed: ' + str(target))
    source = ROOT.parent / 'IOS_TuTien.jar'
    if hashlib.sha256(source.read_bytes()).hexdigest() != SOURCE_SHA:
        raise SystemExit('Original IOS_TuTien.jar differs from the inspected base.')
    classes = build / 'classes'
    for old in classes.rglob('*.class'):
        old.unlink()
    subprocess.run(['java', '-jar', str(tools / 'ecj.jar'), '-source', '1.3', '-target', '1.4',
                    '-nowarn', '-bootclasspath', str(tools / 'cldc.jar') + ':' + str(tools / 'midp.jar'),
                    '-classpath', str(source), '-d', str(classes),
                    *map(str, sorted((ROOT / 'client/src').rglob('*.java')))], check=True)
    raw, verified = build / 'raw.jar', build / 'verified.jar'
    with zipfile.ZipFile(raw, 'w') as z:
        for p in sorted(classes.rglob('*.class')):
            z.write(p, p.relative_to(classes).as_posix())
    # Preverify only new classes; original classes retain their existing stack maps.
    subprocess.run(['java', '-jar', str(tools / 'proguard.jar'),
                    '-injars', str(raw), '-outjars', str(verified),
                    '-libraryjars', str(tools / 'cldc.jar') + ':' + str(tools / 'midp.jar') + ':' + str(source),
                    '-dontshrink', '-dontoptimize', '-dontobfuscate', '-microedition', '-target', '1.4',
                    '-dontwarn', '-dontnote'], check=True)
    output = dist / 'ARMY2_Online_IOS.jar'
    attrs = {
        'Manifest-Version': '1.0',
        'MIDlet-1': 'ARMY2 Online Test, /icon.png, army2.online.OnlineMidlet',
        'MIDlet-Name': 'ARMY2_Online_Test', 'MIDlet-Vendor': 'army2-online', 'MIDlet-Version': '0.1.0',
        'MicroEdition-Configuration': 'CLDC-1.1', 'MicroEdition-Profile': 'MIDP-2.0',
        'MIDlet-Permissions-Opt': 'javax.microedition.io.Connector.http,javax.microedition.io.Connector.https',
        'Army2-Server': endpoint,
    }
    # Manifest continuation lines are at most 72 bytes.
    manifest = ''
    for k, v in attrs.items():
        line = k + ': ' + v
        while len(line) > 70:
            manifest += line[:70] + '\r\n'
            line = ' ' + line[70:]
        manifest += line + '\r\n'
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(verified) as patch, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
        def fixed_entry(name, content):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, content)
        fixed_entry('META-INF/MANIFEST.MF', manifest + '\r\n')
        for info in original.infolist():
            upper = info.filename.upper()
            if upper == 'META-INF/MANIFEST.MF' or (upper.startswith('META-INF/') and upper.endswith(('.SF', '.RSA', '.DSA'))):
                continue
            z.writestr(info, original.read(info.filename))
        for name in patch.namelist():
            if name.endswith('.class'):
                fixed_entry(name, patch.read(name))
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(output) as new:
        originals = [n for n in old.namelist() if n.endswith('.class')]
        assert all(old.read(n) == new.read(n) for n in originals)
        assert new.testzip() is None
    jad = manifest + 'MIDlet-Jar-URL: ARMY2_Online_IOS.jar\r\nMIDlet-Jar-Size: ' + str(output.stat().st_size) + '\r\n'
    (dist / 'ARMY2_Online_IOS.jad').write_text(jad, encoding='ascii', newline='')
    report = dict(base_sha256=SOURCE_SHA, output_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),
                  original_classes_preserved=len(originals), endpoint=endpoint, preverified=True)
    (dist / 'build.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
