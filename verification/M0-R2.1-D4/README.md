# CAC M0-R2.1-D4 — verification and provenance

This folder contains **current D4** executed local evidence and a standalone
benchmark; previous D3/JDK25 game logs elsewhere in the archive are historical.

- `logs/d3-baseline-offline.log`: frozen D3 test method baseline (non-JUnit).
- `logs/green-offline-initial.log`: first D4 index-modification confirmation.
- `logs/green-offline-index.log`: after adding four new JUnit-source test methods.
- `logs/d4-final-offline.log`: final D4 standalone, offline method and asset test results.
- `logs/python-final.log`: Python 28/28 tests, including 11 release-tool tests.
- `logs/claim-history-d3-vs-d4.log`: same-host synthetic runtime comparison.
- `logs/stale-r2-jar-inspection.log`: expected FAIL on historical R2 JAR as D4 package (negative control).
- `logs/jdk25-preflight.log` + `local-preflight/report.json`: script exercised
  and correctly returned BLOCKED under local JDK21.
- `probes/ClaimHistoryBenchmark.java`: independent test-only microbenchmark.
- `run_claim_benchmark.sh`: repeat comparison if D3 and D4 source trees coexist.
- `d3-to-d4-source.patch` and `source-diff.csv`: machine-readable provenance.

**Formal Gradle 9.2.1 / JUnit 5 / NeoForge 26.3.0.99 D4 build and Minecraft
client/server loading have not been run.** A script that can run on a JDK25
machine does not itself prove these tasks succeeded.
