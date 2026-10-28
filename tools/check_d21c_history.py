#!/usr/bin/env python3
"""Offline historical CSV paging smoke, independent from Minecraft APIs."""
import subprocess
import sys
import tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def main():
    with tempfile.TemporaryDirectory(prefix='cac-d21c-') as scratch:
        sources = [
            ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement'/name
            for name in ('PowerTrace.java', 'PowerTracePage.java', 'PowerTraceComparison.java',
                         'PowerTraceArchive.java', 'PowerTraceHistory.java')
        ]
        sources.append(ROOT/'verification/D21CArchiveHistorySmoke.java')
        classes = Path(scratch)/'classes'
        classes.mkdir()
        for cmd in (
            ['javac','--release','21','-encoding','UTF-8','-Xlint:all','-Werror','-d',str(classes),
             *(str(src) for src in sources)],
            ['java','-cp',str(classes),'dev.coldatomcraft.verification.D21CArchiveHistorySmoke'],
        ):
            result = subprocess.run(cmd, cwd=ROOT, check=False)
            if result.returncode: return result.returncode
    return 0

if __name__ == '__main__':
    sys.exit(main())
