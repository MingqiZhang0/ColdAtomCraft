# D9 reproducible verification

Input: frozen CAC-M0-R2.1-D8 (313/313 SHA-256, archive CRC clean).

## Local checks without Gradle

    sh verification/M0-R2.1-D9/run_d9_offline.sh
    sh verification/M0-R2.1-D3/run_local_offline.sh

`run_d9_offline.sh` re-runs the D5–D8 scientific oracles, plus the new D9
standalone smoke and independent Decimal80 Poisson/Gamma aperture oracle.
`run_local_offline.sh` compiles all available physics-core and experiment-core
JUnit SOURCE test methods with a local assertion shim. It is explicitly
**not JUnit Platform/Jupiter** and must never be counted as formal JUnit.

Both checks can run on JDK21 for compatibility evidence. The actual game
requires JDK25 plus configured NeoForge dependencies.

## Formal toolchain on a prepared JDK25 host

    python tools/verify_m0.py

The runner demands all JUnit XML suites, at least 287 formal tests and the
actual distributable JAR class contents. It does not launch the game.
D9's client/server and installed-JAR runtime gates remain NOT RUN until
there is direct run evidence from this exact D9 source.

Do not confuse a virtual optical readout with a calibrated device or a
world-mutation authorization.
