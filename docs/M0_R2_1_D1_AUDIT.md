# CAC-M0-R2.1-D1 — Code Director Engineering Checkpoint Audit

**Date:** 2026-10-26  
**Input:** `ColdAtomCraft-M0-R2.1-Checkpoint.zip` (SHA-256 `bf7bdcaac986906631a078b5047080fbcd9b89c7854a3da39f3c6893091024c1`).  
**Parent uploaded R2.1 work ZIP:** SHA-256 `a172f466a620cf8de5a3a6243ec0384c121750066be7cffa4de3bdf8b6937567`.  
**Implementation:** ChatGPT temporary engineering continuation based on DeepSeek's unfinished R2.1 checkpoint.  
**Approval authority:** Owner directs science, Blender geometry and M0 closure.  
**Status:** **DIRECTOR CHECKPOINT / NOT A RELEASE / M0 OPEN**.

## 1. Executive decision

The partial R2.1 input already addressed the three previously reproduced P1 defects in pure Java: complete candidate-to-precheck binding, registered time-interval verification, and an explicit server revalidation/claim coordinator contract. Those code changes were retained; no owner-supplied optical, thermal, material or biological model was fabricated.

This D1 continuation resolves a remaining **workflow ambiguity**: a stale physical solver run cannot be re-executed with the same `(runId, stepIndex)` while `beginCalculation` has already reserved that identity. A new `abandonRun(runId)` method retires **unclaimed** runs and blocks all late claims from them; genuine recomputation requires a new server-issued run ID. The coordinator surfaces this rejection explicitly as `RUN_ABANDONED`.

**Security limitation:** a run with *any* claimed event cannot be abandoned, because even FAILED or CLAIMED events may have partially or fully changed the world. A future authoritative experiment scheduler must serialize different run IDs representing the same logical experiment. This ledger does not implement cross-run exclusivity, persistence, atomic world mutations, or a live NeoForge server adapter. This D1 contract is not production TOCTOU closure.

## 2. Actual source modifications

- `WorldEffectLedger.java`: `AbandonResult` enum, `abandonRun` and `isRunAbandoned`, in-memory run tombstones, `RUN_ABANDONED` results from begin/claim, serialized with existing claim lock. Abort checks all claimed events and refuses nonempty settlement history.
- `ServerEffectCommitCoordinator.java`: distinct `Decision.RUN_ABANDONED` rather than a generic ledger rejection.
- `WorldEffectLedgerRunAbandonmentTest.java`: five new formal JUnit test methods (compiled and executed in the offline compatibility sweep, **not run under JUnit engine here**).
- Existing stale-recomputation tests in `ServerEffectCommitCoordinatorTest` and `PrecheckCandidateBindingTest`: remove misleading same-run actual re-execution assumptions, assert explicit abandonment and fresh run ID.
- `verification/Smoke.java`: additional lifecycle checks and 100 two-thread Claim/Abandon contests per run; latest output 363 engineering checks.
- `README.md`, architecture, world contract, tests, changelog, decisions, performance and present audit: updated to distinguish **historic M0-R2**, **incoming R2.1**, and **current D1** evidence.

The complete relative-source diff and file list appear in `verification/M0-R2.1-D1/source-diff.patch` and `file-diff.csv`.

## 3. Evidence: executed here vs previously reported

| Task / gate | Status | Exact scope |
| --- | --- | --- |
| Original checkpoint integrity | **PASS** | `sha256sum -c` of 141-file input manifest before editing |
| Pure-Java main source compilation | **PASS** | `javac --release 21`; current Linux OpenJDK 21, **not** Java 25 |
| Java standalone smoke | **PASS** | 363/363 checks per run; 3 consecutive runs, 100 Claim/Abandon two-thread contests each |
| Python asset validator tests | **PASS** | 17/17, `python -m unittest discover ...` |
| Ad-hoc reflection assertion sweep | **PASS, NOT JUNIT** | 118/118 methods from original + new `@Test` sources compiled with a temporary local compatibility API; not JUnit engine or Gradle results |
| Static pure-core import boundary scan | **PASS** | No `net.minecraft`/`net.neoforged` imports in physics/experiment main sources |
| Model approval / actual game mutation scan | **PASS for current empty adapter** | No production owner-approved models or active world mutation API calls in NeoForge main sources |
| Ledger benchmark rerun on incoming and D1 | **PASS as observation** | Same Linux JDK 21 / `-Xmx768m` host, synthetic 1k/10k/50k steps and 20k claim workload; see `PERFORMANCE_AUDIT.md` |
| Historic R2.1 JUnit XML | **RECORDED, NOT RE-RUN** | Incoming uploaded evidence showed 113/113 pass from DeepSeek's intermediate Windows worktree; does not cover new D1 tests |
| Official JDK 25 Gradle/JUnit for D1 | **NOT RUN** | Current environment has only JDK 21 and cannot resolve the required remote build repositories |
| D1 full NeoForge `:neoforge-mod:build` | **NOT RUN** | Same dependency/toolchain limitation |
| D1 packaged JAR contents / installed-JAR runtime | **NOT RUN** | No D1 distributable JAR generated; old JARs in evidence folders are historical, **not D1 outputs** |
| D1 development client/dedicated server | **NOT RUN** | Previous M0-R2 client/server success does not verify changed D1 tree |
| GitHub CI / GameTestServer | **NOT RUN** | No CI runner; no legitimate GameTest implemented |
| Blender actual export / rendering | **BLOCKED / NOT RUN** | No Owner `.blend` device asset supplied, no new renderer implemented |
| Owner scientific model / project Gate | **PENDING** | No Owner approval to close M0 or implement particular OPT/thermal/damage equations |

Executed evidence files: `verification/M0-R2.1-D1/`.

## 4. Failure/retry semantic matrix

| Situation | D1 behavior | Status |
| --- | --- | --- |
| Stale candidate rejected before any claim | No settlement consumed; run **may be abandoned** explicitly | Implemented in pure Java |
| Run successfully abandoned | New steps and late claims under old run ID are rejected | Implemented and tested |
| Original solver truly re-executes | Requires a different, server-issued run ID | Contract/test; no production scheduler yet |
| Abandon competing with Claim | Inside one synchronized ledger, Claim or Abandon wins, never both | 100 repeated two-thread smoke contests plus test source |
| Any event CLAIMED/APPLIED/FAILED | Abandon refused; no automatic replay | Implemented, fail closed |
| New run created while unrelated old run has claimed events | **Not prevented**: lacks logical-experiment binding | Future P0 for world mutation |
| Claim then server/world exception | Claim stays terminal or explicitly FAILED; possible lost/partial effect | Existing code contract; not transactional |
| Restart then retry | Ledger/tombstones lost; exactly-once across restart not guaranteed | Future durable recovery |

## 5. Performance and memory

- Step registration remains indexed by TreeMap, expected O(log n), with an additional expected O(1) set membership check.
- `abandonRun` scans existing settlement keys, O(M), under the ledger lock. It is deliberately an exceptional path, never an every-tick operation; a per-run claim counter may be introduced only if profiling warrants it.
- On the same JDK 21 host, 50,000 registrations had medians of **27.040 ms** (incoming baseline) vs **28.173 ms** (D1); this small difference is inconclusive, not a formal performance regression. Retention estimates were about **8.249 MiB** for 50,000 steps in both runs.
- The ledger has **unbounded in-memory history**; no unsafe eviction was implemented.

## 6. Source trust boundaries preserved

- `physics-core` and `experiment-core` remain pure Java without Minecraft or NeoForge class imports.
- `WorldEffectGate.Precheck` is data-only, not a grant of server mutation authority.
- `ServerEffectCommitCoordinator` only reaches the in-memory settlement state `CLAIMED`; no world mutation exists.
- Synthetic `OWNER_APPROVED` fixtures are used exclusively by tests or standalone verification, never as production Owner approval.
- `Blender` visual assets were not generated or modified, and no OPT-001 optical equations were selected in this engineering round.

## 7. Unresolved release blockers

1. **P0 for any real world modification:** implement a NeoForge server-authoritative snapshot/permission/commit adapter, with a scheduler that keeps different run IDs from duplicating the same logical experiment.
2. **P0 for crash-safe world-effect delivery:** specify atomic mutation/settlement/durable recovery semantics, or define an explicit weaker delivery guarantee acceptable for gameplay before enabling destructive effects.
3. **Required to claim D1 build parity:** re-run full formal JDK 25 JUnit + clean/no-cache Gradle build, verify the newly built D1 JAR's contents, and test the installed JAR in an independent NeoForge instance.
4. **Required for M0-G5:** Owner reviews/accepts the current engineering and scientific handoff gates. Blender delivery and scientific equations are separate future stages, not implied by M0-Java smoke.
5. **For long runs:** plan bounded experiment memory, tombstone lifetime and performance under real server TPS load.

**Director conclusion:** D1 is a useful, self-consistent pure-Java engineering continuation with measured tests. It is **NOT READY FOR FULL M0 OR PRODUCTION-WORLD-EFFECT RELEASE**; status `READY_FOR_JDK25_BUILD_AND_DIRECTOR_RECHECK`.
