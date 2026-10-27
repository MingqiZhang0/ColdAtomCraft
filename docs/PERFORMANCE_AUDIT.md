# D4 Claim-History Abandonment Microbenchmark

The D3 implementation called `abandonRun()` by scanning all settlement event
keys (`O(total previous events)` worst case for each fresh unclaimed run). D4
maintains `runsWithClaims` under the same synchronized ledger monitor and
checks only this monotonic index (`O(1)` expected hash lookup). It never clears
the flag for APPLIED or FAILED outcomes. Per-claim bookkeeping adds a hash-set
insert, with no extra game world work.

Same-host JDK 21, independent process repetitions, 16,000 claims in one run,
then 4,000 different no-claim runs registered and abandoned:

| Baseline | Run 1 | Run 2 | Run 3 | Median |
| --- | --- | --- | --- | --- |
| D3 scan | 392.362 ms | 439.052 ms | 431.817 ms | 431.817 ms |
| D4 index | 14.100 ms | 9.332 ms | 10.951 ms | 10.951 ms |

**Interpretation:** directional reduction for this exact synthetic workload,
not a performance SLA, not an authoritative JDK25 number, and NOT Minecraft TPS.
The tests retain all claim and settlement records in memory; growth and
crash/restart semantics are **unchanged**. The original measurements can be
rerun using `verification/M0-R2.1-D4/run_claim_benchmark.sh` with the D3
source directory beside D4. No production laser is benchmarked.

---

# D3 resource-reservation performance addendum (2026-10-26)

`ExperimentRunScheduler.createExperiment(ExperimentReservation)` now performs a
per-apparatus `TreeMap` predecessor/successor overlap check and inserts a lease
only if ALL requested apparatus are conflict-free. For `k` requested apparatus
and up to `n` windows per apparatus, expected worst-case within one scheduler:
**O(k log n)** index work, plus set validation, session issuance and UUID cost.
Claim operations are unchanged except for the apparatus set lookup and server-tick
containment check. The scheduler monitor serializes these short operations;
callers must not execute expensive physics or `ServerLevel` mutations under it.

Current-host OpenJDK 21 microbenchmark, `-Xmx1g`, five trials per size after
three 1,000-session warmups; each session reserves ONE common apparatus at a
disjoint window `[2*i,2*i+1)`. The benchmark includes UUID generation, Java
allocation and creation of an in-memory session/attempt + lease index. These
measurements are **observational**, not representative of optics workloads or
Minecraft TPS.

| Accepted reservations | Five-run median |
| --- | ---: |
| 1,000 | 7.745 ms |
| 10,000 | 18.863 ms |
| 50,000 | 63.165 ms |

The samples show JIT/GC outliers (see
`verification/M0-R2.1-D3/logs/reservation-benchmark-java21.log`); we make NO
claim of statistically established speedup, server tick latency bound, or
sufficient heap lifetime. A claimed session's reservation is intentionally
retained as an in-memory tombstone to prevent replay; **memory increases with
all distinct claims** until lifecycle and durable retention policies are
approved. Server-wide clock/identity validation and crash recovery are still
NOT IMPLEMENTED.

---

# D2 performance addendum — JDK 21 synthetic comparison

The D1 vs D2 5-trial benchmark was run on the **same available Linux JDK 21 machine**, not in Minecraft. Median results (ms): register 1,000: 1.735 vs 1.532; register 10,000: 6.752 vs 6.614; register 50,000: 30.394 vs 27.841; 20,000 claim+mark: 10.272 vs 4.783. Some claim trials had long GC/JIT-influenced tails, so the measurements **do not establish a speedup or performance regression**. The extra EventSlot map requires additional memory per claimed event. No TPS or persistent-world performance test was run. Raw logs: `verification/M0-R2.1-D2/performance/`.

`ExperimentRunScheduler` synchronizes short pure-Java metadata checks but cannot be treated as a license to perform Minecraft world mutation or expensive physics while its lock is held. No history cleanup or cross-restart semantics changed.

---

# WorldEffectLedger performance audit — M0-R2.1-D1

**Scope:** in-memory step-index registration and transfer claim, not a proof of world performance/TPS. 2026-10-26.

## Algorithmic complexity

- `beginCalculation` uses an ordered `TreeMap` per calculation run; a new interval needs the immediate predecessor and successor: **O(log n)** in that run, with a constant-time map lookup in expectation. An additional `abandonedRuns.contains(runId)` test is expected **O(1)**.
- `claim` uses HashMap lookups and candidate value comparison, expected **O(k)** in number of chunk revisions embedded in the candidate for its immutable read-set equality, plus expected O(1) ledger map lookups; synchronization serializes claims within the instance.
- `abandonRun` is an **exceptional, non-hot-path operation**. It scans all settlement keys to conservatively prevent retirement of a partially or fully applied run: **O(M)** in total claimed events, while sharing the Ledger lock. Future optimization may use a per-run claimed-event count when measurements justify it; do not build a generalized transaction system now.
- Memory grows with all registered steps, run interval indexes, settlement keys, failure reasons and abandoned-run tombstones; **no eviction**. Removing tombstones carelessly would re-enable duplicate claims. A durable lifecycle is still needed before indefinite simulated experiments.

## Independent measurements on the same available machine

Command: compile original checkpoint and D1 pure-Java sources under OpenJDK `21.0.12.1`, run `LedgerBenchmark` with `java -Xmx768m`, `3` repetitions per size after `3x1000` warm-up. Host: Linux amd64, 4 processors. The code and run size match, but the JVM/JIT/GC and OS introduce noise.

| Metric | Incoming checkpoint | D1 |
| --- | ---: | ---: |
| Register 1,000 steps, median | **1.102 ms** | **0.952 ms** |
| Register 10,000 steps, median | **7.205 ms** | **7.829 ms** |
| Register 50,000 steps, median | **27.040 ms** | **28.173 ms** |
| 50,000-step retention estimate | ~8.249 MiB | ~8.249 MiB |
| 20,000 claims + markApplied | 79.266 ms | 62.900 ms |

The ~1.1 ms median difference at 50,000 steps is not statistically sufficient to infer an actual regression; no meaningful structural change to the interval insertion algorithm occurred. The memory estimator forces GC and is only a rough heap delta. Raw full logs are in `verification/M0-R2.1-D1/performance/`.

**Previous DeepSeek baseline** (Windows/JDK 25) reported ~15.17 ms median for 50,000 registrations after the TreeMap optimization, vs ~10,489 ms in its previous unoptimized implementation. Different host/JVM data are not directly comparable with this D1 Linux/JDK 21 probe.

## Before enabling world interactions

1. Establish per-server-tick budgets, TPS timings and bounded snapshots on the real NeoForge dedicated server.
2. Add a server-owned scheduler with exclusive logical-experiment leases, abort/restart semantics and backpressure; this is not provided by the ledger.
3. Define a durable, crash-safe acknowledgement protocol if physical effects are ever applied; today cross-restart exactly-once is NOT guaranteed.
4. Re-test claim/abort hot paths under realistic peak numbers of chunks, entities and events, not just the small synthetic records in this benchmark.
