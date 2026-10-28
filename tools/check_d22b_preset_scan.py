#!/usr/bin/env python3
"""D22B offline four-preset measurement scan checks (Java 21+, no Minecraft runtime)."""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment'
FILES = [
    SRC/'measurement/PowerTrace.java',
    SRC/'motion/DiscretePresetSweep.java',
    SRC/'motion/DiscretePresetReport.java',
    ROOT/'verification/D22B-preset-stage/PresetSweepSmoke.java',
]

with tempfile.TemporaryDirectory(prefix='cac-d22b-') as folder:
    subprocess.run(['javac','--release','21','-Xlint:all','-Werror','-d', folder,
                    *(str(p) for p in FILES)], check=True)
    subprocess.run(['java','-cp',folder,'dev.coldatomcraft.verification.PresetSweepSmoke'],check=True)
