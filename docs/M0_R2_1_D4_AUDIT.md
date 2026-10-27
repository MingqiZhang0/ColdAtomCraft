# Cold Atom Craft — M0-R2.1-D4 Code Director Audit

**Date:** 2026-10-26  
**Input:** `ColdAtomCraft-M0-R2.1-D3-DirectorCheckpoint.zip`  
**Scope:** in-memory stale-run settlement lookup + reproducible release content tests.  
**State:** **ENGINEERING CHECKPOINT, M0 STILL OPEN**.

## 1. Evidence and frozen baseline

- Extracted the official D3 checkpoint in an independent directory, not in place.
- Verified **198/198** frozen D3 SHA-256 manifest members before edits.
- Executed D3 offline regression tests from the original source before making changes: **149/149** method sweep PASS, 17 Python asset PASS. Not formal JUnit.
- Maintained a separate `ColdAtomCraft-M0-R2.1-D4` tree; D3 remains frozen.

## 2. Actual code change: stale-run abandonment claim-history index

Previously `WorldEffectLedger.abandonRun` scanned **all** entries in
`settlements`, regardless of the run. After many legitimate physics events,
repeatedly abandoning stale but still unclaimed independent runs could perform
unbounded cumulative historic scans. D4 adds a monotonic `runsWithClaims` set,
updated inside the existing synchronized claim transition along with
`claimedSlots` and `settlements`. `abandonRun` now checks that set once.

**Safety invariants preserved:**

1. A run is in `runsWithClaims` only after a successful `CLAIMED` transition.
2. The marker is never removed by `markApplied`, `markFailed` or duplicate calls.
3. CLAIMED, APPLIED and FAILED events all prohibit run abandonment.
4. A rejected candidate cannot poison the run; unclaimed stale runs can still retire.
5. Synchronization of claim and abandon remains atomic for this one Ledger.
6. No change to event identity, source/target snapshots, Owner model authority,
   server/world adapter or scientific formulas.

**Performance:** previously abandoned-run lookup was `O(number of settlements)`;
a hash-set membership check now has `O(1)` expected cost, at the expense of one
additional monotonic index entry per run that has ever claimed an event. This
is **not** a memory-reclamation design. All existing settlement records and
tombstones remain retained; cross-restart exactly-once is still absent.

## 3. Same-environment performance comparison

JDK 21 in the current Linux runtime; 16,000 successful synthetic claims in one
run, followed by 4,000 registered-and-abandoned independent zero-claim runs.
Three separate JVM processes were used for D3 and D4. Results in
`verification/M0-R2.1-D4/logs/claim-history-d3-vs-d4.log`.

| Baseline | Samples (ms) | Median (ms) |
| --- | --- | ---: |
| frozen D3 | 392.362 / 439.052 / 431.817 | 431.817 |
| D4 | 14.100 / 9.332 / 10.951 | 10.951 |

These are observational microbenchmark timings, **not** TPS or a guarantee of
identical speedup under the actual Minecraft server/JDK25. The benchmark uses
synthetic test-only physics fixtures.

## 4. Distribution integrity and independent evidence collection

- Added `tools/inspect_mod_jar.py` to validate the NeoForge metadata, Java25
  classfile major 69, forbidden test/probe classes, and **every** compiled class
  in `physics-core`, `experiment-core` and `neoforge-mod` against the ZIP/JAR
  entries. No more reliance on an outdated short list alone.
- Tightened `verifyDistributableJar` in `neoforge-mod/build.gradle` to cover
  the newly introduced D2/D3 scheduling and server fact classes.
- Added `tools/verify_m0.py`, an explicit JDK25 preflight + full Gradle/JUnit
  XML + release JAR inspection runner. It records machine-readable PASS, FAIL,
  BLOCKED and NOT RUN evidence. It never runs Minecraft or accepts the EULA.
- Updated the GitHub Actions CI configuration to require actual JUnit XML and
  the new class-by-class JAR inspection, if/when a repository with CI exists.
- Added 7 synthetic JAR inspector tests and 4 JUnit XML evidence-parser tests.
  A historic R2 JAR is correctly rejected as a D4 candidate because new
  scheduler classes do not exist in it. This is an **expected negative test**,
  not a failure of the previously archived R2 build.

## 5. Executed verification and strict limitations

| Verification | Status | Evidence |
| --- | --- | --- |
| D3 manifest | PASS 198/198 | initial input rehash |
| Original D3 suite | PASS (149/149 non-JUnit, 17 asset) | D4 `d3-baseline-offline.log` |
| D4 pure-Java smoke | PASS 383/383 under JDK21 | `logs/d4-final-offline.log` |
| D4 JUnit-source method sweep | PASS 153/153 via offline shim; **NOT JUNIT** | `logs/d4-final-offline.log` |
| D4 Python unit tests | PASS 28/28 | `logs/python-final.log` |
| D3 vs D4 performance | OBSERVATIONAL | `logs/claim-history-d3-vs-d4.log` |
| JDK25 preflight in current runtime | BLOCKED (javac 21) | `local-preflight/report.json` |
| D4 actual Gradle JUnit engine | NOT RUN | Requires JDK25 and reachable dependencies |
| D4 actual NeoForge build | NOT RUN | Same missing prerequisites |
| D4 distributable JAR runtime | NOT RUN | No D4 JAR has been produced |
| D4 installed client/dedicated server startup | NOT RUN | Separate runtime validation required |
| Live server scheduler/tick/world adapter | NOT IMPLEMENTED | Pure Java contracts only |
| Actual Owner Blender asset and OPT-001 world propagation | NOT IMPLEMENTED | Owner-model / asset handoff not present |

The only Java test execution in this environment uses JDK21 and an independent
assertion shim; it is **not** a replacement for the full Java25 JUnit5 engine.
Historic M0-R2 JDK25/NeoForge and server logs still establish only the old R2
commit, not any R2.1-D1/D2/D3/D4 source state.

## 6. Remaining high-priority gates

1. Run `python tools/verify_m0.py` on the D4 source with real JDK25 +
   Gradle/NeoForge dependency access; retain the `report.json`, original JUnit
   XML, build log, and D4 JAR. The script's PASS status covers only these checks.
2. Independently install and run **that exact D4 JAR** in the matching Minecraft
   26.3 + NeoForge 26.3.0.99 environment, including a client and dedicated server
   (observe EULA and Owner authority; do not edit EULA on the owner's behalf).
3. Implement one actual authoritative `ServerLevel` scheduling domain and
   server-owned device identity/time/world snapshot capture. Current pure Java
   records cannot authenticate their input. All actual world effects remain
   disabled until a safe server mutation and recovery policy is defined.
4. Owner retains physics model and Blender asset decisions. No actual Gaussian
   optics or cold-atom equations have been implemented in D4.

**Director disposition:** narrow D4 pure Java correction PASS under its stated
local test limits; **M0 overall OPEN**; D4 official JDK25/NeoForge Gate remains
BLOCKED/NOT RUN, and production world effects remain forbidden.
