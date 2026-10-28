#!/usr/bin/env python3
"""D22C physics-only Java smoke. Requires JDK21+, no NeoForge dependencies."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PHYSICS = ROOT / 'physics-core/src/main/java/dev/coldatomcraft/physics'
MOTION = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion'


def main():
    with tempfile.TemporaryDirectory(prefix='cac-d22c-') as folder:
        sources = [PHYSICS/'Vec3.java',
                   *sorted((PHYSICS/'optics').glob('*.java')),
                   MOTION/'StageScanPlan.java',
                   MOTION/'ContinuousBenchScanReference.java',
                   ROOT/'verification/D22C-continuous-optics/ContinuousOpticsSmoke.java',
                   ROOT/'verification/D22C-continuous-optics/ContinuousOpticsDemo.java']
        subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8',
                        '-Xlint:all', '-Werror', '-d', folder,
                        *(str(x) for x in sources)], check=True, cwd=ROOT)
        subprocess.run(['java', '-cp', folder,
                        'dev.coldatomcraft.verification.ContinuousOpticsSmoke'],
                       check=True, cwd=ROOT)


if __name__ == '__main__':
    main()
