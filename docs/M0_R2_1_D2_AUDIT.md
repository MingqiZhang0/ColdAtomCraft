# CAC M0-R2.1-D2 — code director audit

**Date:** 2026-10-26  
**Parent:** frozen `ColdAtomCraft-M0-R2.1-D1-DirectorCheckpoint.zip` (input integrity validated before copy).  
**Scope:** pure-Java event deduplication and one-logical-experiment run-attempt fencing; no Minecraft mutation or science model.

## Finding F-D2-01: model-revision event-slot bypass

Independently reproduced on the D1 main sources compiled with JDK 21. In a single run and step, events `(THM-001,A,run-one,0,0)` and `(THM-001,B,run-one,0,0)` both returned `CLAIMED`; the ledger count was 2. This violates the existing requirement that `eventIndex` identify **one event within a step**, regardless of which scientific model is used to produce it. The fixture's approved models are synthetic tests, NOT scientific Owner authorizations.

**Fix:** `WorldEffectLedger` now reserves `(calculationRunId,stepIndex,eventIndex)` in a second map on the same synchronized Claim transition, while retaining full `TransferEventKey` provenance for outcome reporting. Changing model ID, model revision, target, or energy cannot create a second claim for the same slot. `claimedEventForSlot` exposes the provenance of the first claim for diagnostics; `stateOf` remains a lookup for an exact transfer key.

**Result:** D2 reproduction: `A: CLAIMED`, `B: DUPLICATE_TRANSFER`, claim count 1. New formal JUnit source `LedgerEventSlotTest` covers model changes, terminal states, other legal slots, and a two-thread race.

## Finding F-D2-02: logical experiment replays across arbitrarily created run IDs

D1 can abandon an unclaimed run, but anyone holding its ledger could then create another run ID—even while the first run has claims—because the ledger cannot associate multiple run IDs with one logical experiment.

**Minimal contract improvement:** `ExperimentRunScheduler` owns a private ledger and pure-Java commit coordinator; it issues non-client-supplied `Session` and `Attempt` handles. `beginStep`, `submit`, `abandon`, `retry`, and outcome methods are serialized by the scheduler. A stale unclaimed attempt may be abandoned and replaced; old attempts are fenced. After ANY successful claim, abandonment and replacement in the **same session** are refused, including when that claim is later FAILED.

**Critical limitation:** this is **not** a physical apparatus/time scheduler. An independent new `Session` can still represent the same physical operation and claim another event. This residual gap is explicitly tested. Production code must not bypass the scheduler to use a bare `WorldEffectLedger` or `ServerEffectCommitCoordinator` directly, and a future NeoForge server allocator must enforce an authoritative apparatus/operation/time identity across sessions. Client-provided handles are not an authorization mechanism.

## Test and build evidence

| Validation | Result | Evidence |
| --- | --- | --- |
| D1 artifact manifest | PASS | archive `MANIFEST_SHA256.txt` checked on extraction |
| D1 slot bypass reproduction | **RED reproduced** | `verification/M0-R2.1-D2/logs/slot-defect-red.txt` |
| D2 slot bypass fix | **PASS** | synthetic Java slot probe and `LedgerEventSlotTest.java` |
| Java 21 main source compile | **PASS** | `javac --release 21` for physics-core and experiment-core |
| Standalone Java checks | **379/379 PASS** | `logs/java21-standalone-smoke.txt` |
| Offline reflection assertion run | **131/131 PASS, NOT JUNIT** | `logs/java21-non-junit-reflection-sweep.txt`; source in `offline-harness/` |
| Python assets | **17/17 PASS** | `logs/python-asset-tests.txt` |
| D1 vs D2 synthetic microbenchmark | **OBSERVATIONAL** | `performance/*.log`, five trials per workload |
| Official JDK 25 / Gradle/JUnit on D2 | **NOT RUN** | JDK 25 and remote repositories unavailable here |
| D2 NeoForge packaged JAR / installed runtime | **NOT RUN** | no D2 JAR generated |
| Real NeoForge mutation adapter / device scheduler | **NOT IMPLEMENTED** | no server-authoritative world-capture or device/time lock |
| Model equations and Blender assets | **NOT IMPLEMENTED** | remain Owner-controlled |

## Benchmark interpretation

Same-host JDK 21 microbenchmark, 5 samples per size after warm-up (timings in ms):

| Operation | D1 median | D2 median |
| --- | ---: | ---: |
| Register 1,000 steps | 1.735 | 1.532 |
| Register 10,000 steps | 6.752 | 6.614 |
| Register 50,000 steps | 30.394 | 27.841 |
| 20,000 claims + outcomes | 10.272 | 4.783 |

The claim workload has **large long-tail variation** (see raw trial logs), likely influenced by JIT/GC and host load. These samples do **not** establish that D2 is faster, nor do they measure Minecraft TPS, physics solver cost, memory leaks, or production world effects. The EventSlot index does add expected O(number of claims) memory. No history eviction has been introduced.

## Unresolved security / correctness gates

1. **P0 before real world mutation:** create a trusted NeoForge `ServerLevel` snapshot/permission/submit adapter, revalidating the entire read set and target on the server thread, with no uncontrolled async gap before mutation.
2. **P0 before real world mutation:** enforce device/physical-operation/time exclusivity **between different sessions**, not just attempts of one session.
3. **P0 for destructive effect delivery after crashes:** design and Owner-approve a gameplay delivery guarantee (such as explicit at-most-once with lost-effect risk) and a persistent recovery protocol if stronger semantics are required. No restart-safe exactly-once guarantee exists.
4. **P1 before long-running worlds:** bounded history and tombstone lifecycle policy, profiling with server TPS and actual chunk/entity loads.
5. **M0 source acceptance:** execute formal Java 25 Gradle/JUnit, verify D2 distributable JAR, client/server and isolated installed-JAR load. Historic R2 passing logs do not cover D2.
6. **Owner gate:** final M0 acceptance remains with Owner; physics and Blender remain separately controlled.

**Director assessment:** D2 is a tested pure-Java development checkpoint; **NOT a production-world-effect release**, and **M0 remains open**. No scientific model changed or fabricated.
