#!/usr/bin/env python3
"""D22A deterministic real Java smoke; no NeoForge, actual motor or encoder."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    with tempfile.TemporaryDirectory(prefix='cac-d22-') as folder:
        classes = Path(folder)/'classes'
        classes.mkdir()
        source = ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment'
        paths = [source/'measurement/PowerTrace.java',
                 *[source/'motion'/name for name in (
                     'StageScanPlan.java', 'StageScanController.java',
                     'StageScanDataset.java')],
                 ROOT/'verification/D22StageScanSmoke.java']
        for command in (
            ['javac','--release','21','-encoding','UTF-8','-Xlint:all','-Werror',
             '-d',str(classes),*(str(path) for path in paths)],
            ['java','-cp',str(classes),'dev.coldatomcraft.verification.D22StageScanSmoke']
        ):
            result = subprocess.run(command,cwd=ROOT,check=False)
            if result.returncode: return result.returncode
    return 0


if __name__ == '__main__':
    sys.exit(main())
