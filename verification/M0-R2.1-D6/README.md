# D6 Independent Evidence Index

The D5 source was independently hash verified (242/242). This D6 branch adds
Jones/phasor optics without game-world effects or owner-approval changes.

## Executed on Linux OpenJDK 21.0.12.1

- `run_d6_offline.sh` -> `d6-offline-final.log`
  - Java engineering smoke 383/383;
  - JUnit-source methods executed through **NON-JUNIT** reflection assertion
    shim 194/194;
  - D5 optical smoke 21/21;
  - D6 coherent smoke 19/19;
  - D5 Decimal80 independent 36/36;
  - D6 Decimal70 independent 39/39;
  - Python tool tests 30/30.
- `java21-lint.log`: `javac --release 21 -Xlint:all` for all pure Java
  production sources, no warnings.
- `coherent-probe-values.log`: the actual Java phasor/interference numbers
  compared against the independent Decimal70 reference.
- `jdk25-preflight/report.json`: real attempted formal build preflight ->
  **BLOCKED**, because `java` and `javac` are 21, not 25.
- `source-diff.csv` and `source-diff.patch`: tracked changes relative to D5;
  generated after final docs and tests.

## Unverified

No D6 JDK25 Gradle/JUnit XML, D6 distributable JAR, client/server runtime,
Blender export or material interaction was executed here. D5/R2 logs are
retained as history only. `verify_coherent_reference.py` is standard-library
Python with no extra third-party packages; its output is numeric evidence,
not the actual JUnit platform.

See `docs/M0_R2_1_D6_AUDIT.md` and
`docs/OPT_001_COMPLEX_FIELD_REFERENCE.md`.
