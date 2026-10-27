#!/usr/bin/env python3
"""Compiles real pure Java core with JDK21+ and executes D14 physics smoke.

This does NOT substitute for JDK25, NeoForge Minecraft loading or JUnit5.
"""
from pathlib import Path
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[1]

def main():
    with tempfile.TemporaryDirectory(prefix='cac-d14-') as temp:
        classes = Path(temp) / 'classes'
        classes.mkdir()
        files = sorted((ROOT / 'physics-core/src/main/java').rglob('*.java'))
        files += sorted((ROOT / 'experiment-core/src/main/java').rglob('*.java'))
        files += [ROOT/'verification/D14-three-dimensional-optics/OpticalBench3DSmoke.java']
        lst = Path(temp)/'sources.txt'
        lst.write_text(''.join(str(p) + '\n' for p in files), encoding='utf-8')
        for cmd in (["javac","--release","21","-encoding","UTF-8","-Xlint:all","-Werror","-d",str(classes),"@"+str(lst)],
                    ["java","-cp",str(classes),"dev.coldatomcraft.verification.OpticalBench3DSmoke"]):
            if (code := subprocess.run(cmd, cwd=ROOT, check=False).returncode):
                return code
    return 0

if __name__=='__main__':
    sys.exit(main())
