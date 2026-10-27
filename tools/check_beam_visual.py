#!/usr/bin/env python3
"""D11 offline cross-platform visual trace check using javac (not NeoForge)."""
from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="cac-beam-") as temporary:
        classes = Path(temporary) / "classes"
        classes.mkdir()
        sources = sorted((ROOT / "physics-core/src/main/java").rglob("*.java"))
        sources.extend(sorted((ROOT / "experiment-core/src/main/java").rglob("*.java")))
        sources.append(ROOT / "verification/D11-visible-beam/BeamVisualSmoke.java")
        javac = ["javac", "--release", "21", "-encoding", "UTF-8", "-Xlint:all", "-Werror",
                 "-d", str(classes), *[str(x) for x in sources]]
        if (r := subprocess.run(javac, cwd=ROOT, check=False)).returncode:
            return r
        return subprocess.run(["java", "-cp", str(classes),
                               "dev.coldatomcraft.verification.BeamVisualSmoke"],
                              cwd=ROOT, check=False).returncode


if __name__ == "__main__":
    sys.exit(main())
