#!/usr/bin/env python3
"""Standalone D21B Java math/wire checks, no Minecraft dependencies."""
from pathlib import Path
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[1]

def main():
    with tempfile.TemporaryDirectory(prefix='cac-d21b-') as temp:
        output = Path(temp)/'classes'
        output.mkdir()
        src = [ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement'/n
               for n in ('PowerTrace.java','PowerTraceMenuCodec.java','PowerTracePage.java')]
        src.append(ROOT/'verification/D21BMeasurementUiSmoke.java')
        compile_cmd = ['javac','--release','21','-encoding','UTF-8','-Xlint:all','-Werror',
                       '-d',str(output),*(str(path) for path in src)]
        for cmd in (compile_cmd,['java','-cp',str(output),
                                 'dev.coldatomcraft.verification.D21BMeasurementUiSmoke']):
            if result := subprocess.run(cmd,cwd=ROOT,check=False).returncode:
                return result
    return 0

if __name__ == '__main__':
    sys.exit(main())
