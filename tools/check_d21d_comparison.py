#!/usr/bin/env python3
"""D21D independent multi-channel oracle: real pure Java, not NeoForge runtime."""
from pathlib import Path
import subprocess
import tempfile
import sys

ROOT=Path(__file__).resolve().parents[1]
MEASURE=ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement'

def main():
    with tempfile.TemporaryDirectory(prefix='cac-d21d-') as tmp:
        output=Path(tmp)/'classes'
        output.mkdir()
        sources=[MEASURE/x for x in ('PowerTrace.java','PowerTraceArchive.java',
                  'PowerTraceHistory.java','PowerTracePage.java','PowerTraceComparison.java')]
        sources.append(ROOT/'verification/D21DMultiChannelSmoke.java')
        for command in (
            ['javac','--release','21','-encoding','UTF-8','-Xlint:all','-Werror',
             '-d',str(output),*(str(path) for path in sources)],
            ['java','-cp',str(output),'dev.coldatomcraft.verification.D21DMultiChannelSmoke']):
            if (code:=subprocess.run(command,cwd=ROOT,check=False).returncode):
                return code
    return 0

if __name__=='__main__':sys.exit(main())
