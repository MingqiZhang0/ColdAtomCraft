# CAC M0-R2.1-D3 — Independent code director audit

**Date:** 2026-10-26  
**Input:** `ColdAtomCraft-M0-R2.1-D2-DirectorCheckpoint.zip`  
**Scope:** Minecraft-independent physical apparatus reservation and inter-session scheduling window invariants; no scientific model or real game mutation.  
**Status:** ENGINEERING CHECKPOINT; **M0 NOT CLOSED**.

## 1. Freeze and evidence

- D2 archive extracted into a separate immutable base directory; verified **179/179 SHA-256 entries** against the archive's manifest before edits.
- D2 RED reproduction compiled/runs against frozen D2 main source (JDK21): two independent logical sessions both return `CLAIMED` for the same apparatus/simulation interval. Log: `verification/M0-R2.1-D3/logs/d2-resource-conflict-red.log`.
- Baseline D2 source is not rewritten; D3 source lives in a separately named directory. D2 red probe source is archived as `.java.d2-only.txt` and is intentionally not part of D3's compilation.

## 2. Root problem

D2 `ExperimentRunScheduler.createExperiment()` allocated a session without any apparatus/physical-operation identity or scheduling window. The fact that UUIDs differed allowed independent sessions to reserve the same solver-time slice and both claim world effects. Per-run event-slot deduplication was insufficient because run identities differed.

**Important limit:** the original RED fixture's shared solver-time interval is not proof of a shared *real-world clock*. D3 adds a distinct server scheduling window to make cross-session conflict checks well-defined. No server adapter yet binds these requests to live devices or a reliable clock.

## 3. D3 implementation

1. Added `ApparatusKey`: dimension plus device block coordinates. Device ID is deliberately omitted, so replacing an apparatus at the same block position cannot evade a pending reservation.
2. Added `ServerTickWindow`: half-open `[startTick,endTick)` on **one server-wide scheduling timeline**, separate from `SimulationInterval` in numerical nanoseconds. Intervals touching at one endpoint do not overlap.
3. Added `ExperimentReservation`: canonical immutable nonempty apparatus set plus scheduling window, with a direct source-device membership predicate.
4. Changed `ExperimentRunScheduler.createExperiment` to require a reservation. The no-argument API was intentionally removed to eliminate the obvious no-reservation bypass. Allocation checks all keys and refuses the entire request if any overlaps; a TreeMap floor/ceiling check indexes ordered windows per apparatus.
5. Added `cancelUnclaimedSession`: invalidates stale attempt and releases **all** reserved apparatus keys only when no event has ever been CLAIMED. Attempt retries preserve the original lease. Claimed sessions (even when FAILED) keep their windows as conservative in-memory tombstones.
6. Extended `ServerMutationFacts` with `currentServerTick`; scheduler submission rejects a candidate whose source position is not in the reserved apparatus set or whose current tick lies outside its reserved scheduling window. This is a breaking pure-Java API change: the record now accepts seven components instead of six. The provided bool/tick record does not prove the data originated from `ServerLevel`.
7. Updated existing scheduler JUnit-source tests and standalone Smoke to use the mandatory reservation contract. Added `ExperimentReservationTest` with 18 independent JUnit-source methods, testing overlap, boundaries, atomic multi-device conflict, disjoint/multiple dimensions, replacement device identity, cancellation, retry, claim tombstones, synthetic clock constraints, deterministic random interval-oracle agreement, concurrent allocation and cancel/claim arbitration. No new `ModelApproval.OWNER_APPROVED` entries in production.

## 4. Re-verified evidence (current D3 source)

| Verification | Status | Evidence / limitations |
| --- | --- | --- |
| D2 frozen archive manifest | **PASS 179/179** | SHA-256 verified at extraction |
| D2 cross-session collision | **RED REPRODUCED** | `logs/d2-resource-conflict-red.log` |
| D3 pure Java compile and engineering smoke (JDK 21) | **PASS 383/383** | `logs/java21-smoke.log`, `logs/local-offline-all.log` |
| Java test methods with historical custom assertions runner | **PASS 149/149, NOT JUNIT** | `logs/java21-offline-sweep.log`, `logs/local-offline-all.log` |
| Python asset tests | **PASS 17/17** | `logs/python-assets.log`, `logs/local-offline-all.log` |
| Deterministic index oracle and concurrency regressions | **PASS under offline runner** | Included in 149; no formal JUnit engine was available |
| Reservation microbenchmark | **OBSERVATIONAL** | `logs/reservation-benchmark-java21.log` and `docs/PERFORMANCE_AUDIT.md` |
| JDK25 + official Gradle / JUnit5 on D3 | **NOT RUN** | Only JDK21 installed; external artifact repositories unreachable |
| NeoForge D3 build + distributable JAR verification | **NOT RUN** | Requires official JDK25, NeoForge/Gradle dependencies |
| D3 client/dedicated server/save reload | **NOT RUN** | No D3 NeoForge JAR produced; R2 historic runtime != D3 evidence |
| Installed production JAR in isolated instance | **NOT RUN** | Same blocker |
| Single authoritative NeoForge scheduler and world/tick provenance | **NOT IMPLEMENTED** | Pure Java objects cannot establish server trust |
| Crash-safe world effect delivery / persistent leases | **NOT IMPLEMENTED** | In-memory only; no cross-restart exactly-once |
| Model formulas, Owner Blender assets, laser/thermal/biological effects | **NOT IMPLEMENTED / BLOCKED** | Science/assets remain Owner-controlled |

No GPU/GLB renderer, weapon/damage logic or speculative atomic-physics calculations were introduced.

## 5. Security and delivery semantics

- **Safety within one scheduler instance:** overlapping server-tick reservation windows for any common apparatus block position cannot be allocated twice, and reserve-all-or-none is synchronized. Old attempts from cancelled sessions cannot submit. A claim keeps an in-memory tombstone; time ranges can overlap only for different physical device positions or across deliberately nonoverlapping windows.
- **Not guaranteed:** the existence/trustworthiness of the apparatus, authenticity of `currentServerTick`, shared clock epochs/dimension consistency, no overlap across separate scheduler instances or process restarts, persistent reservations, absence of claim→mutate race, completeness of optical-path apparatus set, world-change revision tracking, permissions and gameplay mutation atomicity.
- **Known bypass:** separate independently instantiated schedulers can each reserve the same apparatus and window. An adversarial caller that supplies fabricated device, window or tick facts also bypasses the *pure-Java* record predicates. These are explicitly outside D3's current guarantee and must be closed by one future server-owned NeoForge adapter before world effects are enabled.
- **Failure policy:** `CLAIMED` is not `APPLIED`; `FAILED` may already have partially modified world. Neither allows automatic replay. Cancelled unclaimed sessions may free leases because they have never obtained an effect claim. No crash-safe exactly-once is promised.

## 6. Migration and integration handoff

**Breaking signatures:**

```java
// D2 (unsafe; no longer available)
ExperimentRunScheduler.StartedSession created = scheduler.createExperiment();
ServerMutationFacts facts = new ServerMutationFacts(true, true, true, true, true, true);

// D3 (pure-Java contract; authoritative input is NOT yet implemented)
ExperimentReservation reservation = ExperimentReservation.single(
    new ApparatusKey("minecraft:overworld", 8, 64, 8),
    new ServerTickWindow(1000, 1020));
ExperimentRunScheduler.CreateOutcome allocated = scheduler.createExperiment(reservation);
ServerMutationFacts newFacts = new ServerMutationFacts(true, true, true, true, true, true, 1001);
```

Future DeepSeek/NeoForge responsibilities: create a SINGLE trusted scheduler per server allocation domain; derive `ApparatusKey` from `ServerLevel` block entity state; define one stable trusted server tick/epoch; validate all reserved and dependent apparatus and permissions immediately before mutation; ensure each real effect's mutation tick is inside the lease; reject untrusted client requests; establish pending/claim/settlement persistence and recovery; handle apparatus unload or movement; add server TPS and soak tests. Without this no production damage or world mutations may be enabled.

## 7. Performance and retention

The indexed lease check is expected **O(k log n)** for `k` reserved apparatus and up to `n` intervals per apparatus, excluding UUID overhead, with linear-memory retained leases and sessions. Pure Java microbenchmark medians for 1k/10k/50k session allocations were **7.745 / 18.863 / 63.165 ms**, with noisy outliers. The corresponding study and sample list are in `docs/PERFORMANCE_AUDIT.md`. No server TPS, solver performance or long-running world retention was measured; do not compare directly with D2's *different* ledger-step benchmark.

## 8. Director Gate disposition

- **D3 local contract / pure Java:** CONDITIONAL PASS (JDK21 compile + standalone + offline assertions).
- **D3 formal JDK25 Gradle/JUnit and NeoForge runtime:** NOT RUN; cannot promote historical R2 PASS.
- **Production server-world effects:** NOT IMPLEMENTED; forbidden until authoritative ServerLevel adapter and gameplay delivery agreement exist.
- **M0 overall:** **OPEN**, awaiting formal toolchain verification and Owner signoff.

**Next recommended task:** obtain JDK25/Gradle Maven access and run an actual *fresh D3* clean build, JUnit, `verifyDistributableJar`, dedicated-server/client smoke and isolated installed JAR validation; independently review the one-server allocator and crash/recovery policy before enabling OPT-001 in the world.
