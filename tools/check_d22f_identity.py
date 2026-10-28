#!/usr/bin/env python3
"""Independent Java 21+ deterministic D22F identity/lineage check; NOT NeoForge."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment'
PHY = ROOT / 'physics-core/src/main/java/dev/coldatomcraft/physics'


def main():
    sources = [PHY/'Vec3.java', *sorted((PHY/'optics').glob('*.java')),
               CORE/'LaserTuning.java',
               CORE/'motion/LaboratoryMountFrame.java',
               CORE/'motion/PersistentApparatusIdentity.java',
               CORE/'motion/LoadedApparatusSnapshot.java',
               ROOT/'verification/D22F-persistent-identity/PersistentIdentitySmoke.java']
    with tempfile.TemporaryDirectory(prefix='cac-d22f-') as classes:
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-Xlint:all',
                        '-Werror','-d',classes,*map(str,sources)],check=True,cwd=ROOT)
        subprocess.run(['java','-cp',classes,
                        'dev.coldatomcraft.verification.PersistentIdentitySmoke'],check=True,cwd=ROOT)


if __name__ == '__main__':
    main()
