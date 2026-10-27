#!/usr/bin/env python3
"""Verify that the source ZIP's exact shipped files match MANIFEST_SHA256.txt.

This is a reproducibility/completeness check, not a supply-chain signature.
Generated caches and build outputs are excluded intentionally. The manifest
itself is not self-hashed.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {'.git', '.gradle', '.idea', '__pycache__', 'build', 'out'}
SKIP_FILES = {'.DS_Store', 'MANIFEST_SHA256.txt'}
# Local PowerShell updaters are distribution helpers, not project source. Users
# put them beside gradlew.bat for a one-off update; never add them to the source
# inventory or mistake them for a new tracked project file.
LOCAL_UPDATERS = {
    'Apply-D10-API-Fix.ps1', 'Apply-D10-Laser-Facing-Fix.ps1',
    'Apply-D11-Visible-Beam.ps1', 'Apply-D12-Laser-GUI.ps1',
    'Apply-D13-Multi-Beam.ps1', 'Apply-D14-Optical-Bench.ps1',
}
SKIP_SUFFIXES = {'.pyc', '.pyo', '.class', '.tmp', '.swp'}


def eligible(path: Path) -> bool:
    relative = path.relative_to(ROOT)
    if not path.is_file() or path.is_symlink():
        return False
    return (not any(name in SKIP_DIRS for name in relative.parts[:-1])
            and relative.name not in SKIP_FILES
            and not (len(relative.parts) == 1 and relative.name in LOCAL_UPDATERS)
            and relative.suffix not in SKIP_SUFFIXES)


def entries() -> dict[str, str]:
    return {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(ROOT.rglob('*')) if eligible(p)}


def write_manifest(path: Path) -> None:
    calculated = entries()
    path.write_text(''.join(f'{digest}  {name}\n' for name, digest in sorted(calculated.items())),
                    encoding='utf-8')
    print(f'WROTE {len(calculated)} source entries: {path.name}')


def verify(path: Path) -> int:
    if not path.is_file():
        print('FAIL missing source integrity manifest')
        return 1
    found: dict[str, str] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if len(line) < 67 or line[64:66] != '  ' or not all(x in '0123456789abcdef' for x in line[:64]):
            print(f'FAIL malformed digest line {index}')
            return 1
        name = line[66:]
        if (not name or name.startswith('/') or '\\' in name
                or any(part in ('', '.', '..') for part in name.split('/'))
                or name in found):
            print(f'FAIL unsafe/duplicate path on line {index}: {name!r}')
            return 1
        found[name] = line[:64]
    actual = entries()
    missing = sorted(set(actual) - set(found))
    extra = sorted(set(found) - set(actual))
    changed = sorted(name for name in actual.keys() & found.keys() if actual[name] != found[name])
    if missing or extra or changed:
        for category, names in [('MISSING', missing), ('EXTRA', extra), ('MISMATCH', changed)]:
            for name in names[:30]:
                print(f'FAIL {category}: {name}')
        print(f'FAIL source manifest: {len(actual)} actual, {len(found)} listed, '
              f'{len(missing)} missing, {len(extra)} extra, {len(changed)} changed')
        return 1
    print(f'PASS source integrity inventory: {len(actual)}/{len(found)} SHA-256 entries match')
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--update', action='store_true', help='Rebuild manifest on explicit request')
    args = parser.parse_args()
    manifest = ROOT/'MANIFEST_SHA256.txt'
    if args.update:
        write_manifest(manifest)
    return verify(manifest)


if __name__ == '__main__':
    raise SystemExit(main())
