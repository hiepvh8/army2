#!/usr/bin/env python3
"""Package only server, deployment files and built client; never local DB/credentials."""
from pathlib import Path
import tarfile

root = Path(__file__).resolve().parents[1]
if not (root / 'dist/ARMY2_Online_IOS.jar').exists():
    raise SystemExit('Build the client first')
out = root / 'dist/army2-online-release.tar.gz'
with tarfile.open(out, 'w:gz') as archive:
    for folder, pattern in [('server', '*.py'), ('deploy', '*'), ('dist', '*.jar'), ('dist', '*.jad')]:
        for path in sorted((root / folder).glob(pattern)):
            if path.is_file():
                archive.add(path, arcname='army2-online/' + str(path.relative_to(root)))
    for path in [root / 'README.vi.md', *sorted((root / 'docs').glob('*.md'))]:
        archive.add(path, arcname='army2-online/' + str(path.relative_to(root)))
print(out)
