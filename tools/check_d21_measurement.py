#!/usr/bin/env python3
"""D21 pure-Java measurement model, runnable without NeoForge dependencies."""
from pathlib import Path
import subprocess
import tempfile
import sys
ROOT=Path(__file__).resolve().parents[1]

def main():
    with tempfile.TemporaryDirectory(prefix='cac-d21-') as temp:
        classes=Path(temp)/'classes'
        classes.mkdir()
        files=sorted((ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/measurement').glob('*.java'))
        files.append(ROOT/'verification/D21-long-term-measurement/PowerTraceSmoke.java')
        lst=Path(temp)/'sources.txt'
        lst.write_text('\n'.join(map(str,files))+'\n',encoding='utf-8')
        for cmd in (["javac","--release","21","-encoding","UTF-8","-Xlint:all","-Werror","-d",str(classes),'@'+str(lst)],
                    ["java","-cp",str(classes),"dev.coldatomcraft.verification.PowerTraceSmoke"]):
            code=subprocess.run(cmd,cwd=ROOT,check=False).returncode
            if code: return code
    return 0
if __name__=='__main__': sys.exit(main())
