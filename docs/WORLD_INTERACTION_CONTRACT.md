## D4 note — monotonic run claim history

`WorldEffectLedger` now indexes the fact that a calculation run has *ever*
claimed an event. It is set at the same synchronized moment as the event's
CLAIMED record and remains true after APPLIED or FAILED. The abandoned-run
check no longer scans every historical event. This changes performance, **not**
the permission to retry, or the lack of production `ServerLevel` and
cross-restart exactly-once guarantees.

# D3 addendum — apparatus reservation and two clocks (2026-10-26)

**Implemented in the pure Java scheduler:** session creation requires `ExperimentReservation`,
containing one or more `ApparatusKey` locations and a half-open `ServerTickWindow`.
Same-scheduler overlapping requests for any common apparatus are rejected before any lease
is inserted; accepted windows are indexed by start tick per apparatus for logarithmic
neighbor lookup. Unclaimed session cancellation frees all its leases and fences its old
attempt. An already CLAIMED event leaves the session's leased time window as an in-memory
tombstone, even after `markFailed`. Retry within one session retains its original lease.

**Two time meanings must never be conflated:** `ServerTickWindow` uses a single trusted
server-wide scheduling clock in ticks. `CalculationStep.interval()` holds simulation-time
nanoseconds. Their endpoints must NOT be compared directly. The scheduler checks
`ServerMutationFacts.currentServerTick` at submission; its record is a data carrier and cannot
attest that this tick actually came from the server. The real adapter must obtain and recheck
that tick immediately before application.

**Boundaries:** apparatus reservation only checks source apparatus and the keys declared by
its trusted future server adapter. The current implementation has NO live `ServerLevel`
identity capture, no enforced uniqueness across multiple scheduler instances or reboots,
no durable reservation persistence, no automatic TTL reclamation of claimed lease history,
no protection when a client/server falsely declares the scheduling time or device, and no
permission to mutate the world. The bare public Ledger/Coordinator are still internal
building blocks in practice; future production adapters MUST NOT expose them as alternative
commit paths. Apparatus locations alone cannot substitute for future device UUID, chunk
load epochs, placement change events, or actual optical-path dependency tracking.

Cancellation after any settlement claim is conservatively denied; legitimate later
operations can reserve **nonoverlapping** tick windows. The absence of real device clock
validation means D3 is a server integration *contract*, NOT a solved world security layer.

---

## D2 historical contract (the text below predates D3)

# M0-R2.1-D2 world-interaction contract addendum

## D2 provenance-independent event uniqueness

Each event slot `(runId, stepIndex, eventIndex)` may be claimed at most once within a `WorldEffectLedger` instance. `TransferEventKey` retains `(modelId, revision, runId, stepIndex, eventIndex)` for audit provenance, but changing a model identifier or revision can no longer evade the physical slot reservation. The map `claimedEventForSlot` allows inspection of which full key first claimed a slot. This does **not** prevent two independently allocated run IDs from representing the same physical operation.

## D2 experiment attempt fencing

`ExperimentRunScheduler` is the new preferred pure-Java orchestration contract. It privately owns the ledger and coordinator, allocates local `Session` and `Attempt` handles (without proving server provenance), and serializes begin/submit/abandon/retry under one monitor. A stale attempt with zero claims may be retired and reissued inside its original session; old callbacks are rejected. Once any event is claimed, that session cannot be abandoned and reissued. This is tested under repeated Claim/Abandon competition.

**Not a production authority:** a new independent session can still duplicate the same apparatus/time operation. The bare `WorldEffectLedger` and `ServerEffectCommitCoordinator` remain public legacy building blocks and can be misused; no live NeoForge mutation code currently calls them. Before enabling real effects, an authoritative server adapter must enforce use of an owned scheduling path, device/time overlap policies, fresh snapshots and permissions, and an accepted crash/recovery contract. Session tokens are JVM-scoped; their UUID strings are not authorization.

---

# World interaction contract — v0.3 (M0-R2.1-D1)

**Status:** engineering contract implemented in pure Java and tests; **no Minecraft world mutation is implemented**.
**Owner principle:** physical apparatus *affects and is affected by* the Minecraft world.

## Mandatory invariants

1. A single authoritative numerical state drives instrument readings, world physics consequences and client presentation.
2. No permanent Minecraft world modification from an unapproved or numerically unverified scientific model.
3. Every transfer across layers states its SI units and the underlying model revision.
4. Candidate world changes are validated on the server; player permissions and dedicated-server operation are first-class constraints.
5. No effects are applied from snapshots that have become stale, including an intermediate chunk of a multi-chunk beam path.
6. A laser's rendered color or brightness does not determine its physical damage; low-intensity or infrared beams cannot be made into weapons by particle visualizations.
7. Zero net absorption is not secretly mapped to heat or damage. All model-to-Minecraft conversion constants require owner approval and documentation.
8. Unloaded areas must not silently receive world changes after chunk reload.
9. Passing the pure precheck is **never** permission to mutate the world.
10. A calculation step may be executed at most once **per run ID**, and a transfer may be claimed at most once per event key **per ledger lifetime**. A genuine rerun of an invalidated step requires explicit abandonment of an unclaimed run and a distinct server-generated run ID.
11. A run that has already claimed any event (including APPLIED or FAILED) must never be abandoned and automatically replayed: partial or complete world effects may already exist.

## Pipeline and ownership

```
server-owned model catalog + owner approval records
    -> authoritative server calculation scheduler
    -> CalculationStep(server run ID, step index, simulation interval)
    -> approved + numerically verified model (future OPT-001/THM-001/BIO-001)
    -> EnergyTransferCandidate(event index, device/target snapshots, full WorldReadSet)
    -> WorldEffectGate.precheck        (pure, read-only, no mutation authority)
    -> fresh server target/device/load/permission facts
    -> WorldEffectLedger.claim         (single claim per event key, in memory)
    -> [FUTURE] NeoForge server adapter mutation on the server thread
```

No production mutation stage after `claim` exists in M0-R2.1-D1. There is no implemented laser, damage, heating or block mutation.

## C1 — calculation steps, intervals and transfer events

- `SimulationInterval(startNanos, endNanos)` is half-open `[start, end)` on the server simulation clock.
- `CalculationStep(calculationRunId, stepIndex, interval)` identifies one solver step. `calculationRunId` is allocated by the server, never accepted from a client.
- `WorldEffectLedger.beginCalculation` is called **before** the solver runs. It rejects:
  - a repeated `(calculationRunId, stepIndex)`, even if the interval or payload differs;
  - a different step in the same run whose interval overlaps a previously reserved interval.
- `EnergyTransferCandidate.eventIndex` is unique among the events of its step. `TransferEventKey` is `(modelId, modelRevision, calculationRunId, stepIndex, eventIndex)`. Target, energy and snapshots are deliberately excluded from the key, so a duplicate cannot become new by editing its payload or target.
- `WorldEffectLedger.claim` requires an allowed, matching precheck and a registered step. It returns `CLAIMED` once and `DUPLICATE_TRANSFER` thereafter. The claim is made immediately before application.
- After application the caller must call `markApplied(eventKey)`. After an exception or failed mutation it must call `markFailed(eventKey, reason)`. `FAILED` and `APPLIED` are terminal in that ledger; neither can be claimed again.
- If a process crashes between `claim` and outcome recording, the event remains `CLAIMED` and is still not retried in that process. This fails closed with a possible lost effect rather than a double effect.
- The ledger is in-memory only. A new server process starts with `UNCLAIMED` state: **cross-restart exactly-once is NOT implemented or guaranteed**. A durable claim committed atomically with the world mutation is future work.
- A chunk/device/target unload makes the precheck fail; no claim should be made. The final server adapter must also re-check load state immediately before mutation.
- Tests use only the Minecraft-free `FakeWorldState` test double, terminal-outcome tests, an explicit restart-limitation test, and a 16-thread concurrent claim test. Exactly one concurrent claim wins.

## C1.1 — Explicit stale-run abandonment (D1)

- The authoritative scheduler may mark an **unclaimed** calculation run stale by calling `WorldEffectLedger.abandonRun(oldRunId)` **after** its server-state check rejects a result and **before** re-running the solver.
- The method is synchronized with `claim` and returns `ABANDONED` only for an existing run with **zero settlement claims**. This is conservative: an event marked `FAILED` may have partially affected the world, so any claimed outcome prevents abandoning the run.
- After success, existing registered steps remain reserved as tombstones. `beginCalculation` returns `RUN_ABANDONED` and `claim` returns `RUN_ABANDONED`; `ServerEffectCommitCoordinator` maps the latter to `Decision.RUN_ABANDONED`.
- A **new server-issued calculationRunId** is required for the next actual solver execution. Same-run rechecking a cached candidate is not equivalent to executing the solver again.
- This is an **in-process run fence only**. A production scheduler must also serialize replacement attempts belonging to the same logical experiment. Nothing prevents an unrelated caller from starting an arbitrary second run whose physical interval overlaps a run with already-claimed effects.
- Abandonment and claim are mutually exclusive within a single ledger instance. Across restarts, run status is lost. A durable server-state protocol remains future work.

## C2 — stale results, load state and version data

- `WorldRevision` captures dimension, chunk coordinates, `loadEpoch`, `contentRevision` and `loaded`.
- `WorldReadSet` is canonical, single-dimension, duplicate-free and **may contain only loaded chunks**. It includes every chunk read by the calculation, not just the endpoint chunk.
- `EnergyTransferCandidate` requires the source-device and target chunks to appear in that read set.
- `DeviceSnapshot` and `TargetSnapshot` carry identity, position, state revision and load state.
- `WorldEffectGate.precheck` requires the current read set, device snapshot and target snapshot to equal the candidate's snapshots. A changed intermediate chunk, reload epoch, changed device revision, moved target or unloaded source/target is rejected.

**Limitation:** these records are data carriers. M0-R2 has **no NeoForge world-change tracker, chunk event subscription, or authoritative snapshot collector**. Equality of these records is only as trustworthy as a future adapter that populates and invalidates them. A matching read set must not be interpreted as proof that the world is unchanged or that a mutation is safe.

## C3 — authorization and permissions boundary

- `WorldEffectGate` is precheck-only. It has no `apply`, `hurt`, `setBlock` or packet-sending method; a structural test enforces the absence of such names.
- `ServerEffectGate` defines the facts a server adapter must obtain fresh on the server thread:
  - execution is on the server thread;
  - target identity is resolved from authoritative server state;
  - target position is current;
  - target chunk is loaded;
  - the acting player/server permission allows the modification.
- `ServerMutationFacts` is an integration contract, **not** a security capability. The NeoForge adapter that constructs these facts from `ServerLevel`, live entities and permission APIs is **NOT IMPLEMENTED**.
- A client must never supply absorbed energy, model approval, target identity, permission flags or world coordinates as authority. Those are recomputed or resolved on the server; future packets may carry only a request and a server-resolved model reference.
- The precheck and claim must be repeated or freshly evaluated immediately before mutation. A TOCTOU window remains until the future adapter performs the final checks on the same server thread/tick.

## C4 — model approval boundary

- `ModelReference(modelId, revision)` is the only model data permitted across a network or save boundary. It contains no approval or verification fields.
- `ModelCatalog.fromTrustedServerBootstrap` creates an immutable server-owned map. Unknown or mismatched references fail closed. A reference to an existing `DRAFT` model still resolves to `DRAFT` and is rejected by `ModelGate`.
- `ModelGate.resolveOwnerApproved` and `resolveWorldEffectModel` require exact catalog resolution; they never accept a model definition from a packet or save.
- Owner approval and engineering numerical verification remain independent enums and separate checks.
- Java cannot prevent malicious code already running in the same JVM from constructing a false `ModelDefinition`. The practical boundary is: no production model catalog contains an approved model; only the server bootstrap may populate one; network/save code sees only references. This limitation is deliberate and documented rather than hidden behind a claim of cryptographic security.

## Current implementation status

| Item | Status | Evidence |
| --- | --- | --- |
| Pure-Java candidate/read-set/device/target validation | PASS for checkpoint | Previous R2.1 XML + D1 363-check Java smoke |
| Duplicate step/event prevention and unclaimed run retirement | PASS in D1 offline checks | 100 Claim/Abandon races; added `WorldEffectLedgerRunAbandonmentTest` for JDK 25 JUnit follow-up |
| Precheck cannot mutate | PASS | no mutation API; structural test |
| Server permission/target adapter | NOT IMPLEMENTED | interface contract only |
| Persistent cross-restart ledger | NOT IMPLEMENTED | documented limitation |
| NeoForge world-change tracking | NOT IMPLEMENTED | documented limitation |
| Actual laser/world effects | NOT IMPLEMENTED | requires owner-approved models |

**D1 verification scope:** independent Linux JDK 21 Smoke 363/363 and Python 17/17 are PASS; independent temporary reflection assertion runner executed 118 existing/new test methods without the JUnit engine. Formal JDK 25/JUnit/NeoForge and installed-JAR tests on the D1 tree are **NOT RUN**, not PASS. Previous XML/build/runtime results belong to their earlier trees.
