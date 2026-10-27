# M0-R2.1-D2 evidence — 2026-10-26

D2 is a **director development checkpoint**, not a mod release. No Owner-approved physical model, world damage or functional laser device was added.

## Reproducible results from this Linux JDK 21 environment

- `logs/slot-defect-red.txt`: observed D1 bug, model A and model B both `CLAIMED` for one `(runId, stepIndex, eventIndex)`.
- D2 changes make this slot unique **independently of model ID or revision**.
- `logs/java21-standalone-smoke.txt`: **379 engineering checks pass** (the D1 smoke plus D2 checks).
- `logs/java21-non-junit-reflection-sweep.txt`: **131 tests pass under a separately authored assertion shim**, NOT the official JUnit runner. The shim sources are in `offline-harness/` and are excluded from production source sets.
- `logs/python-asset-tests.txt`: **17 tests pass** using Python stdlib unittest.
- `performance/D1-jdk21.log`, `D2-jdk21.log`: same-host comparison, 5 synthetic trials with noisy GC/JIT effects; not a TPS or production throughput claim.
- `logs/source-d1-zip-sha256.txt`: exact frozen D1 archive fingerprint.

### Repeat offline tests (JDK 21+, no Minecraft dependencies)

Run the repository's `tools/verify-core.sh` on a **JDK 25** host for the exact official project release target; that script intentionally uses `--release 25`.

To reproduce under JDK 21, run the Java standalone class by compiling both main source roots and `verification/Smoke.java` with `javac --release 21` (the main sources currently do not use JDK 25-only syntax). Run the Python asset suite with:

```bash
python -m unittest discover -s verification -p 'test_*.py' -v
```

The offline reflection runner is deliberately **not equivalent to JUnit**; do not count it as a Gradle/JUnit CI pass.

## Required gates still open

- Formal JDK 25 Gradle + JUnit + `:neoforge-mod:build` on D2: **NOT RUN** in this container (only JDK 21; DNS/repositories inaccessible).
- D2 client/server/packaged-JAR launch: **NOT RUN**. Historic M0-R2 runtime evidence belongs only to M0-R2.
- True NeoForge server snapshot/permission/mutation adapter and physical device/time scheduling: **NOT IMPLEMENTED**.
- Durability/exactly-once after restart: **NOT IMPLEMENTED**.
- Blender model / optical physical model: no Owner source submitted, **NOT IMPLEMENTED**.
