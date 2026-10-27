# CAC M0-R2.1-D1 director validation evidence

These are **new** independent local checks of the D1 source tree, not transferred R2 build logs. The host has **OpenJDK 21**, not the required **JDK 25** NeoForge runtime. Do **not** cite these files as a Java 25 Gradle build or Minecraft client/server pass.

- `logs/original-integrity.log`: incoming checkpoint ZIP SHA-256 and 141-file manifest checks.
- `logs/jdk-env.log`: available Java/Python/host details.
- `logs/smoke-jdk21.log`: 3 independent smoke runs; each reports `PASS: 363`, including 100 synchronized Claim/Abandon two-thread contests.
- `logs/python-assets.log`: full 17-test standard-library Python unit-test output.
- `logs/offline-assertion-sweep.log`: **118 method** reflection sweep using a *temporary local* JUnit-compatible method/annotation shim (only used in this review sandbox). It is **not JUnit Jupiter's actual engine** and must not replace Gradle/JUnit acceptance.
- `logs/static-boundary-scan.log`: no Minecraft/NeoForge imports in the two Java cores and no production world effects.
- `performance/incoming-checkpoint-baseline-jdk21.log`: independent incoming baseline performance run on this host.
- `performance/d1-benchmark-jdk21.log`: independent D1 performance run on the same host and JVM version.
- `file-diff.csv`, `source-diff.patch`: D1 changes relative to verified original checkpoint.

**Reproduction on a full Windows JDK 25/NeoForge workstation:**

```powershell
.\tools\verify-core.ps1
python -m unittest discover -s verification -p 'test_*.py' -v
.\gradlew.bat clean :physics-core:test :experiment-core:test :neoforge-mod:build --no-build-cache --rerun-tasks
.\gradlew.bat :neoforge-mod:verifyDistributableJar
```

Keep the full JDK 25 run's logs/JUnit XML and final JAR hash. Running older R2 binaries does not validate the D1 source.
