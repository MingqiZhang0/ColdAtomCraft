#!/usr/bin/env python3
"""D22E offline read-only world snapshot smoke (not Java25 or game proof)."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PHY = ROOT / 'physics-core/src/main/java/dev/coldatomcraft/physics'
EXP = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment'

def main():
    files = [PHY / 'Vec3.java', *sorted((PHY / 'optics').glob('*.java')),
             EXP / 'LaserTuning.java',
             EXP / 'motion/LaboratoryMountFrame.java',
             EXP / 'motion/LoadedApparatusSnapshot.java',
             EXP / 'motion/PersistentApparatusIdentity.java',
             ROOT / 'verification/D22E-loaded-world/LoadedWorldSmoke.java']
    with tempfile.TemporaryDirectory(prefix='cac-d22e-') as directory:
        subprocess.run(['javac','--release','21','-encoding','UTF-8',
                        '-Xlint:all','-Werror','-d',directory,
                        *(str(p) for p in files)],cwd=ROOT,check=True)
        subprocess.run(['java','-cp',directory,
                        'dev.coldatomcraft.verification.LoadedWorldSmoke'],
                       cwd=ROOT,check=True)

if __name__ == '__main__':
    main()
