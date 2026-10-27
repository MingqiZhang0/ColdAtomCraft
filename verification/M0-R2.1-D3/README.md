# D3 local verification evidence — 2026-10-26

This folder contains **new D3 evidence only**. Older R2/D1/D2 outputs remain historical and do NOT validate D3.

## Completed locally (Linux, JDK 21)

- `logs/d2-resource-conflict-red.log` — reproduced D2 before-fix behavior against frozen D2 classes (two independent sessions both CLAIMED). `probes/D2ResourceConflictRed.java` is intentionally **D2-only source**, because D3 removes `createExperiment()` without a reservation.
- `logs/java21-smoke.log` — Java 21 `--release 21` compile of current pure-Java main + `verification/Smoke.java`, then runtime engineering smoke: **383/383 PASS**.
- `logs/java21-offline-sweep.log` — independent historical offline assertion shim compiling main and test sources, executing **149/149** `@Test` methods, **NOT JUnit Jupiter / Platform / Gradle**. Never report as formal JUnit.
- `logs/python-assets.log` — Python unittest actual run: **17/17 PASS**.
- `logs/reservation-benchmark-java21.log` — same-process synthetic microbenchmark of **apparatus reservation + UUID session issuance**. Observational, not a Minecraft TPS or optics solver benchmark.

## Reproduce offline checks

From the extracted D3 root, with JDK 21 or higher (with JDK 25 recommended):

```sh
mkdir -p /tmp/cac-d3-classes
find physics-core/src/main/java experiment-core/src/main/java -name '*.java' > /tmp/cac-d3-core-sources.txt
echo verification/Smoke.java >> /tmp/cac-d3-core-sources.txt
javac --release 21 -encoding UTF-8 -d /tmp/cac-d3-classes @/tmp/cac-d3-core-sources.txt
java -cp /tmp/cac-d3-classes dev.coldatomcraft.verification.Smoke
python3 -m unittest discover -s verification -p test_asset_validator.py -v
```

D2's archived temporary JUnit `Assertions` shim and `OfflineTestRunner` are used *only* for an approximate offline assertion sweep. For formal JUnit5 use the checked-in Gradle build and JDK25.

## NOT RUN / missing gates

- **JDK 25 / Gradle 9.2.1 / actual JUnit 5**: NOT RUN (current container only has JDK21; remote repositories unreachable).
- **NeoForge 26.3 build + D3 distributable JAR**: NOT RUN.
- **D3 client, dedicated server, installed-JAR runtime**: NOT RUN.
- **Actual ServerLevel/world-change tracker, permission capture, damage or physics**: NOT IMPLEMENTED.
- **Owner Blender device model/export/render**: NOT IMPLEMENTED or BLOCKED on user handoff.

No new OWNER_APPROVED physics model, laser damage, real world mutation, or release is claimed.
