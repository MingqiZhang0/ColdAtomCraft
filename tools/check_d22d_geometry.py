#!/usr/bin/env python3
"""D22D strict offline geometry smoke. No MC or authenticated encoder.

Optional --demo-csv PATH writes a new REFERENCE_ONLY theory CSV, not a sample.
"""
from pathlib import Path
import argparse
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PHY = ROOT / 'physics-core/src/main/java/dev/coldatomcraft/physics'
MOTION = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--demo-csv', type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='cac-d22d-') as folder:
        files = [PHY / 'Vec3.java', *sorted((PHY / 'optics').glob('*.java')),
                 MOTION / 'LaboratoryMountFrame.java',
                 MOTION / 'StageSurfacePrediction.java',
                 ROOT / 'verification/D22D-geometry/ContinuousGeometrySmoke.java',
                 ROOT / 'verification/D22D-geometry/GeometryDemo.java']
        subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8',
                        '-Xlint:all', '-Werror', '-d', folder,
                        *(str(path) for path in files)], cwd=ROOT, check=True)
        subprocess.run(['java', '-cp', folder,
                        'dev.coldatomcraft.verification.ContinuousGeometrySmoke'],
                       cwd=ROOT, check=True)
        if args.demo_csv is not None:
            with args.demo_csv.open('x', encoding='utf-8', newline='') as output:
                subprocess.run(['java', '-cp', folder,
                                'dev.coldatomcraft.verification.GeometryDemo'],
                               cwd=ROOT, check=True, stdout=output)
            print(f'D22D REFERENCE_ONLY demo written to {args.demo_csv}')


if __name__ == '__main__':
    main()
