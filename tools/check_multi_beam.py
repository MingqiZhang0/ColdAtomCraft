#!/usr/bin/env python3
"""Portable offline Java21+ smoke of D13 particle-budget policy (not runtime MC)."""
from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    with tempfile.TemporaryDirectory(prefix='cac-d13-') as temp:
        temp_dir = Path(temp)
        output = temp_dir / 'classes'
        output.mkdir()
        files = sorted((ROOT / 'physics-core/src/main/java').rglob('*.java'))
        files.extend(sorted((ROOT / 'experiment-core/src/main/java').rglob('*.java')))
        files.append(ROOT / 'verification/D13-multi-beam/MultiBeamBudgetSmoke.java')
        sources = temp_dir / 'sources.txt'
        sources.write_text(''.join(str(x) + '\n' for x in files), encoding='utf-8')
        commands = (
            ['javac', '--release', '21', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
             '-d', str(output), '@' + str(sources)],
            ['java', '-cp', str(output), 'dev.coldatomcraft.verification.MultiBeamBudgetSmoke'],
        )
        for command in commands:
            run = subprocess.run(command, cwd=ROOT, check=False)
            if run.returncode != 0:
                return run.returncode
    print('D13 particle-budget smoke uses Java only; Minecraft client render needs separate CI/game validation.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
