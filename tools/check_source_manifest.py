#!/usr/bin/env python3
"""Verify a reproducible ColdAtomCraft source inventory.

A Git checkout hashes canonical *indexed Git blobs*, not working-tree bytes.
This prevents Windows line-ending conversion and uncommitted historical ZIP
logs from corrupting the cross-platform source inventory. It also rejects
unstaged changes to tracked files, ensuring the index represents the checkout.

A standalone source ZIP without .git retains a strict on-disk file inventory.
This is a content-integrity check, not a cryptographic provenance signature.
The manifest itself is intentionally excluded from its own inventory.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_NAME = 'MANIFEST_SHA256.txt'
SKIP_DIRS = {'.git', '.gradle', '.idea', '__pycache__', 'build', 'out'}
SKIP_FILES = {'.DS_Store', MANIFEST_NAME}
SKIP_SUFFIXES = {'.pyc', '.pyo', '.class', '.tmp', '.swp'}
LOCAL_UPDATERS = {
    'Apply-D10-API-Fix.ps1', 'Apply-D10-Laser-Facing-Fix.ps1',
    'Apply-D11-Visible-Beam.ps1', 'Apply-D12-Laser-GUI.ps1',
    'Apply-D13-Multi-Beam.ps1', 'Apply-D14-Optical-Bench.ps1',
    'Apply-D14-CI-Manifest-Fix.ps1',
}


class InventoryError(Exception):
    pass


def _git(*args: str, stdin: bytes | None = None) -> bytes:
    try:
        process = subprocess.run(['git', *args], cwd=ROOT, input=stdin,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                 check=False)
    except OSError as exc:
        raise InventoryError(f'git unavailable: {exc}') from exc
    if process.returncode:
        reason = process.stderr.decode('utf-8', 'replace').strip()
        raise InventoryError(f'git {" ".join(args)} failed: {reason}')
    return process.stdout


def _is_checkout() -> bool:
    try:
        top = _git('rev-parse', '--show-toplevel').decode('utf-8', 'replace').strip()
    except InventoryError:
        return False
    return Path(top).resolve() == ROOT.resolve()


def _tracked_git_blob_ids() -> dict[str, str]:
    records: dict[str, str] = {}
    for record in _git('ls-files', '--stage', '-z').split(b'\0'):
        if not record:
            continue
        try:
            metadata, name = record.split(b'\t', 1)
            mode, sha, stage = metadata.split(b' ')
        except ValueError as exc:
            raise InventoryError('invalid Git index entry') from exc
        if stage != b'0':
            raise InventoryError('unmerged index entries; resolve conflicts first')
        if mode == b'160000':
            raise InventoryError('Git submodules require a separate inventory policy')
        path = name.decode('utf-8', 'surrogateescape')
        if path == MANIFEST_NAME:
            continue
        if not path or path.startswith('/') or '\\' in path or '..' in path.split('/'):
            raise InventoryError(f'unsafe tracked path: {path!r}')
        if path in records:
            raise InventoryError(f'duplicate tracked path: {path!r}')
        if len(sha) not in (40, 64):
            raise InventoryError(f'unsupported Git object ID: {path!r}')
        records[path] = sha.decode('ascii')
    if not records:
        raise InventoryError('no tracked source files; refusing an empty inventory')
    return records


def _git_objects(shas: set[str]) -> dict[str, bytes]:
    ordered = sorted(shas)
    output = _git('cat-file', '--batch', stdin=('\n'.join(ordered) + '\n').encode('ascii'))
    cursor = 0
    blobs: dict[str, bytes] = {}
    for wanted in ordered:
        finish = output.find(b'\n', cursor)
        if finish < 0:
            raise InventoryError('truncated git cat-file header')
        header = output[cursor:finish].split(b' ')
        if len(header) != 3 or header[0].decode('ascii') != wanted or header[1] != b'blob':
            raise InventoryError('unexpected git cat-file response')
        try:
            length = int(header[2])
        except ValueError as exc:
            raise InventoryError('malformed Git object size') from exc
        start = finish + 1
        end = start + length
        if end >= len(output) or output[end:end + 1] != b'\n':
            raise InventoryError('incomplete Git blob content')
        blobs[wanted] = output[start:end]
        cursor = end + 1
    if cursor != len(output):
        raise InventoryError('extra data after Git object stream')
    return blobs


def _checkout_inventory() -> dict[str, str]:
    # In normal CI the checkout and index agree. If an asset generator changes
    # tracked files, reject that instead of hiding it in an index-only digest.
    unstaged = _git('diff', '--name-only', '-z').split(b'\0')
    changed = [name.decode('utf-8', 'replace') for name in unstaged
               if name and name != MANIFEST_NAME.encode('utf-8')]
    if changed:
        raise InventoryError('unstaged changes in tracked files: ' + ', '.join(changed[:12]))
    indexed = _tracked_git_blob_ids()
    objects = _git_objects(set(indexed.values()))
    return {name: hashlib.sha256(objects[blob_id]).hexdigest()
            for name, blob_id in sorted(indexed.items())}


def _eligible(path: Path) -> bool:
    relative = path.relative_to(ROOT)
    return (path.is_file() and not path.is_symlink()
            and not any(part in SKIP_DIRS for part in relative.parts[:-1])
            and relative.name not in SKIP_FILES
            and not (len(relative.parts) == 1 and relative.name in LOCAL_UPDATERS)
            and relative.suffix not in SKIP_SUFFIXES)


def _archive_inventory() -> dict[str, str]:
    return {file.relative_to(ROOT).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest()
            for file in sorted(ROOT.rglob('*')) if _eligible(file)}


def _parse_manifest(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise InventoryError('missing MANIFEST_SHA256.txt')
    result: dict[str, str] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        if len(line) < 67 or line[64:66] != '  ' or any(c not in '0123456789abcdef' for c in line[:64]):
            raise InventoryError(f'malformed digest line {index}')
        name = line[66:]
        if (not name or name.startswith('/') or '\\' in name
                or any(part in ('', '.', '..') for part in name.split('/'))
                or name == MANIFEST_NAME or name in result):
            raise InventoryError(f'unsafe or duplicate path on line {index}: {name!r}')
        result[name] = line[:64]
    return result


def _verify(actual: dict[str, str], manifest: Path) -> int:
    found = _parse_manifest(manifest)
    missing = sorted(set(actual) - set(found))
    extra = sorted(set(found) - set(actual))
    changed = sorted(name for name in actual.keys() & found.keys() if actual[name] != found[name])
    if missing or extra or changed:
        for kind, entries in [('MISSING', missing), ('EXTRA', extra), ('MISMATCH', changed)]:
            for name in entries[:30]:
                print(f'FAIL {kind}: {name}')
        print(f'FAIL source manifest: {len(actual)} actual, {len(found)} listed, '
              f'{len(missing)} missing, {len(extra)} extra, {len(changed)} changed')
        return 1
    print(f'PASS source integrity inventory: {len(actual)}/{len(found)} SHA-256 entries match')
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--update', action='store_true',
                        help='explicitly regenerate the inventory (not used by CI)')
    parser.add_argument('--mode', choices=('auto', 'checkout', 'archive'), default='auto',
                        help='checkout hashes Git-index blobs; archive hashes shipped files')
    args = parser.parse_args()
    try:
        mode = ('checkout' if _is_checkout() else 'archive') if args.mode == 'auto' else args.mode
        if mode == 'checkout' and not _is_checkout():
            raise InventoryError('checkout mode requires Git repository root')
        actual = _checkout_inventory() if mode == 'checkout' else _archive_inventory()
        manifest = ROOT / MANIFEST_NAME
        if args.update:
            manifest.write_text(''.join(f'{digest}  {name}\n'
                                        for name, digest in sorted(actual.items())),
                                encoding='utf-8', newline='\n')
            print(f'WROTE {len(actual)} {mode} SHA-256 entries')
        return _verify(actual, manifest)
    except (InventoryError, UnicodeError, OSError, ValueError) as exc:
        print(f'FAIL source manifest: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
