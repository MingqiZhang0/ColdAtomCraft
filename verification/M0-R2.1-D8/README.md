# D8 verification evidence and provenance

- Input: `ColdAtomCraft-M0-R2.1-D7-DirectorCheckpoint.zip`, SHA-256
  `1a44ccde9e59ce6fbaa6ec9fe812b0a85d76778cc405ebf1c27114f503183c09`.
- D7 original manifest: **287/287** file SHA-256 matches, ZIP CRC clean.
- D8 `run_d8_offline.sh`: existing Java21 core 383/383; Java test-source
  255/255 in explicit **NON-JUNIT** assertion harness; Gaussian smoke 21,
  D6 smoke 19, D7 smoke 19, D8 geometry/Fresnel smoke 18; D5/D6/D7
  oracle comparisons 36/39/29; D8 independent Decimal80 Fresnel 146.
- D8 JUnit5 source regressions: 31 new @Test methods in three classes;
  new checks include tangent E continuity, s/p sign gauge, Brewster/TIR,
  arbitrary 3D tilted normal, rotation covariance, ideal mirror q and
  radial phase continuity, floating coordinate precision guards.
- Python engineering tests: all 30/30 retained (see offline log).
- Java21 strict `javac -Xlint:all -Werror`: see `java21-strict.log`.
- Real JDK25 gate: `python tools/verify_m0.py --evidence
  verification/M0-R2.1-D8/jdk25-preflight` returned exit 2 BLOCKED,
  `java` / `javac` are both OpenJDK 21.0.12.1.
- DNS resolution for required Gradle, NeoForge and JDK repository domains
  failed on this execution host. The formal Gradle/JUnit/JAR and Minecraft
  run gates were **not executed for D8**. Historical R2 startup evidence
  is retained but DOES NOT certify this source.
- No user Blender .blend, calibration tables, installed JAR, world mutation,
  production ModelCatalog approval or experiment hardware was created.

Logs, Java probes, numeric oracle, exact engineering diff and preflight
report are included. The frozen SHA manifest is generated at final packaging.
