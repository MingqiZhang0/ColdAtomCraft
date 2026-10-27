## D9 ideal optical power observation: pure science vs instrument boundary

`physics-core/optics/GaussianCircularAperture` integrates a circular Gaussian
aperture (including offset) and maintains collected/uncollected optical W.
`IdealCircularDetectorFace` declares a lab-local front-facing detector normal;
reverse and oblique incidence or insufficient coordinate resolution fail closed.
`experiment-core/IdealOpticalPowerMeter` converts this calculated **ideal optical
power** into an immutable SI Measurement at a declared simulation timestamp.
It is an observer, not a powered device, photodiode electronics model, world
mutation, or approved Material/BIO model. It accepts matched D7 mode power at
its reference plane or a single D5 Gaussian plus perpendicular face. D8's
geometric Jones ray alone cannot supply a spatial beam profile.

This reference leaves instrument calibration, Blender geometry/units, real
NeoForge authority, detector absorption and measurement noise for separate
Owner-governed work. See `docs/OPT_001_IDEAL_POWER_DETECTION.md`.

## D8 geometric optical interfaces (draft, no NeoForge adapter)

`physics-core` now has a *separate* finite ray/port reference in
`RayFrame3` / `GeometricJonesRay`. `OpticalPlane3` computes an oriented,
front-face-only intersection. `PlanarFresnelInterface` preserves both
reflected and transmitted energy-accounted ports, including complex TIR
reflection phases; `IdealCalibratedPlaneMirror` is a distinct lossless
mirror with externally supplied s/p phases. These are independent of
`experiment-core` and do NOT mutate the Minecraft world. Only an explicit
adapter at a calibrated D7 reference plane can pass mode amplitudes to the
ray model; full Gaussian finite-beam refraction is NOT implemented. Details:
`docs/OPT_001_GEOMETRIC_FRESNEL_REFERENCE.md`.

## D7 optics reference sublayer (2026-10-26)

`physics-core/optics` now has a phase-aware `ParaxialModeField`, immutable
`OpticalPathTrace` and matched-mode `IdealTwoPortBeamSplitter`. A trace is
one optical branch; two-port scattering is explicitly separate, so both
coherent output powers stay in the budget. The modal phase includes carrier
phase plus Gouy phase, not just ABCD q transport. These are independent
laboratory optical quantities, not server/world-authorized commands.

`phaseReferenceKey` and `modeFrameKey` are only declared calibration
labels. The required NeoForge world-space port geometry, mirror orientation,
coating, aperture and actual Blender asset are NOT implemented. A phase-only
mirror proxy must not be mistaken for physical reflection. See
`docs/OPT_001_OPTICAL_PATH_REFERENCE.md` for scope and equations.

## D4 performance and validation split

The Ledger stores a monotonic per-run claim marker solely to avoid repeatedly
scanning all event settlements when a run is abandoned. The release verifier
performs JDK25 Gradle/JUnit plus compiled-class/JAR consistency checks only;
it does not replace a real NeoForge client/server load test. Formal runtime
integration, server authority and crash recovery are still unimplemented.

# D3 architecture change — in-memory apparatus lease gate

`ExperimentRunScheduler.createExperiment(ExperimentReservation)` atomically records
`(dimension,blockX,blockY,blockZ)` apparatus occupancy for a common half-open
`ServerTickWindow` using per-key `TreeMap` floor/ceiling conflict checks.
Multiple optical devices can be reserved as one all-or-none group. Claimed session
windows remain in-memory tombstones; unclaimed whole-session cancellation releases
all keys and invalidates attempts. The removed no-argument session API prevents
bypass through another session in a single scheduler instance.

Before calling the existing world/coordinator claim path, the scheduler verifies
that the candidate source device lies in the reserved apparatus set and that
`ServerMutationFacts.currentServerTick` lies in the reserved scheduling window.
This is **not** a proof of ServerLevel origin; no production adapter exists yet.
A future single authoritative server allocator is required, with persistent
recovery policy, actual world-authoritative apparatus ownership and current clock.
Solver time in nanoseconds is independent of server scheduling ticks.

Historical R2/D1/D2 architecture descriptions below remain for provenance.

---

# CAC M0-R2.1-D2 architectural delta

- `WorldEffectLedger.claim` now checks a secondary `(runId,stepIndex,eventIndex)` uniqueness index, independent of model provenance. Full `TransferEventKey` remains for outcome lookup and audit.
- `ExperimentRunScheduler` encapsulates a private Ledger and CommitCoordinator, issues server-local logical `Session` and attempt tokens, and fences aborted unclaimed attempts. Claim / abandon operations are serialized; claims block restart in the same session.
- **Deliberately unsolved:** independent Sessions are not checked for shared apparatus / physical operation / time. The NeoForge adapter and authoritative scheduler must solve this before world mutation, along with crash-safe delivery semantics.
- **D2 is pure Java only.** No new NeoForge API, rendering, physical formulas, or owner-owned Blender assets.

---

# Architecture boundary — M0-R2.1-D1 (M0 still open)

**Status:** engineering proposal implemented only where explicitly indicated.
**Scientific algorithms:** OPT-001, OPT-002, THM-001 and BIO-001 remain PENDING_OWNER_MODEL.

M0-R2 adds explicit calculation-step/time-interval/event identities, a pure
exactly-once claim ledger, loaded-state snapshots, a server-mutation facts
contract and a server-owned model catalog. None of these applies a world effect.

## Principle: three separate coordinate domains

1. **Minecraft world blocks:** dynamic, chunked, server-authoritative; positive X=east, Y=up, Z=south.
2. **Device Blender coordinates:** Blender Z=up, local -Y=front; 1 Blender unit = 1 physical meter at the current handoff specification. Model origin is the base-footprint center. `DevicePlacement` is implemented and independently tested for four facing orientations.
3. **Experiment/laboratory SI meters:** local origin chosen by each experiment. Laser optics, atomic clouds and wavefunctions use local coordinates and must NOT inherit MC block granularity or large absolute coordinates. `BeamInputs` is a validated *settings record only*, not an optical solver.

The backend stores precision microscopic state in the laboratory frame. Rendering can exaggerate apparent atom-cloud size without modifying physics.

**Example:** A laser emitter Empty at Blender-local `(0, -0.25, 0.4)` points toward Blender-local `-Y`. A south-facing MC block at `(x,y,z)` has a base-footprint pivot `(x+.5,y,z+.5)`; with the provisional 1 m/block conversion, its transformed optical output is `(x+.5,y+.4,z+.75)`. This is **engineering geometry**, not a physical propagation result.

## M0 build boundaries

```
physics-core (pure Java mathematical carriers, not yet a solver)
        ^
experiment-core (model authorization, instrument readings, asset placement,
                 version-tagged world-effect candidates)
        ^
neoforge-mod (server lifecycle and world adapter: future implementation)
```

- Pure core imports no Minecraft, NeoForge, RenderSystem, GLFW, or client code. Because `physics-core` and `experiment-core` have no Minecraft dependency, a client-only import there fails compilation rather than relying on review.
- Physics calculations never call `Level#setBlock`, `Entity#hurt` or packet-sending APIs.
- Normal instruments publish `Measurement` values, not arbitrary internal ground truth.
- Gameplay results MUST be produced by server-side, approved and numerically verified models, followed by a current-world revision check and permissions checks.
- The existence of `EnergyTransferCandidate` does NOT establish any particular laser absorption, material heating or biological damage law.
- Network/save data may name a `ModelReference`; only the server-owned immutable `ModelCatalog` can resolve it to an approval record.

## World state version and async lifecycle

Current pure-Java sequence (no mutation implemented):

1. On the main server thread, capture `DeviceSnapshot`, `TargetSnapshot` and a `WorldReadSet` containing every consulted chunk. Each chunk records `loadEpoch`, `contentRevision` and `loaded`.
2. Call `WorldEffectLedger.beginCalculation(step)` before any solver work. A duplicate step or overlapping interval in the same run is rejected; a run explicitly abandoned after a stale result cannot be restarted.
3. Run read-only physics calculations from immutable snapshots (if significant work is justified). Keep game world mutations off worker threads.
4. Return candidates with model revision, step identity, unique event index and the **full** `WorldReadSet`; not just the endpoint chunk.
5. Call `WorldEffectGate.precheck`. It checks the supplied model definition's approval/verification status, every revision, the device state and target identity/position. This is a precheck only; the subsequent coordinator independently resolves the model from a trusted server catalog.
6. On the main server thread, independently capture fresh server facts and call `ServerEffectGate`. The future adapter must verify target identity/position, chunk load state and permissions again.
7. Call `WorldEffectLedger.claim` immediately before the future mutation. Claim once; on failure do not retry.
8. Apply approved game effects or discard stale candidates. **After a stale rejection before any settlement Claim, the scheduler may call `abandonRun(oldRunId)` and perform a genuine recomputation only under a fresh server-owned run ID.** Late results from an abandoned run are rejected. If any transfer has been claimed, abandonment fails closed; no automatic replay is authorized. Record rejection and disposition in diagnostic logs.

`WorldReadSet` + `WorldEffectGate` implement only the first layer of these checks.
World snapshot collection, revision incrementing, event integration, permissions,
target validation, persistence and the final NeoForge mutation adapter are
**not yet implemented**.

**Important limitation:** the chunk-version records do not constitute a NeoForge
world-change tracker. Until a real adapter subscribes to or otherwise accounts
for every relevant change and unload/reload, a matching record cannot be treated
as proof that the world is unchanged.

For a ray crossing multiple chunks, every chunk affecting propagation must be included. If a changed mirror or shutter lies in an earlier chunk, an endpoint-only token is insufficient. Actual dynamic entities should be checked against the current server state; old motion samples must not be trusted.

## Rendering and asset conversion

- `.blend` remains the owner's editable authority.
- `.glb` is an **interchange** deliverable; native NeoForge does not imply automatic GLB block-model loading.
- Blender tool can export `CAC_ROOT` descendants and optical port transforms; this export path has not been run in Blender yet.
- Runtime renderer selection (JSON/static model vs custom animated renderer) occurs after assessing the real owner-supplied geometry.
- Beware glTF's standard Y-up export conversion: Blender-local anchor coordinates and converted GLB mesh coordinates are not interchangeable without a documented import transform. `DevicePlacement.blenderToGltf`, `gltfPointToWorldBlocks` and `gltfDirectionToWorld` make the relation executable and tested.
- Client laser visualizations can be simplified but may not decide hits, damage, energy deposition or measurement values.
- Manifest-schema PASS, actual Blender-export PASS and Minecraft renderer/integration PASS are three distinct gates. Only the first is currently substantiated; see `docs/BLENDER_ASSET_HANDOFF.md`.

## Persistence and networking design (not yet implemented)

- Persist dimension/device identity, schema version, experiment clock and approved model revision.
- Persist only the minimum state needed to resume an experiment; transient world proposals cannot be applied after restart without new validation.
- Use data migration for new schema versions; malformed saves must never silently delete a whole experiment.
- Server sends only render/observation snapshots to clients; commands require server-side access/ownership validation.
- Never assume unloaded chunks remain active. Define stop, freeze or detach behavior for each experiment when the owner authorizes the physical model.
- Never persist a world-effect claim as already applied unless the mutation and the claim commit atomically. M0-R2's ledger is in-memory only and intentionally does not survive restart.
- Never persist or deserialize `ModelDefinition` approval fields from a client or save. Persist only `ModelReference` and resolve it against the server bootstrap catalog.

## Performance policy

- Optics path caching invalidated by relevant block, optics and shutter events, not every tick for every beam.
- Broad-phase loaded chunk/entity collision queries before precision hit checks.
- Limit segment count, splits, ray length, work queues and queued asynchronous results.
- Measure server tick time and client frame time separately.
- Never identify a good FPS screenshot as proof the dedicated server maintains 20 TPS.

## R2.1-D1 run lifecycle refinement

`WorldEffectLedger.abandonRun(runId)` is a deliberately small pure-Java fence for stale, **unclaimed** calculation runs. It returns `ABANDONED`, `ALREADY_ABANDONED`, `UNKNOWN_RUN`, `HAS_SETTLEMENT_CLAIMS`, or `INVALID`. The aborted run identity and all its step tombstones remain in memory. `beginCalculation` and `claim` both reject this run ID. `ServerEffectCommitCoordinator` surfaces this as `RUN_ABANDONED` after current server-state revalidation.

Synchronization prevents a same-ledger race between claiming an event and retiring its run. **It does not bind separate run IDs to the same logical experiment**, prevent an external scheduler from reusing a physical interval after successful world mutation, persist tombstones after restart, or close the final NeoForge TOCTOU window. These remain requirements of the future server-owned scheduling and mutation adapter. See `docs/WORLD_INTERACTION_CONTRACT.md`.


## D6 pure-Java optical reference extension

`physics-core/optics` now holds reference-only `ComplexAmplitude`, `JonesVector`,
`JonesMatrix`, `JonesGaussianBeam`, `VectorFieldPhasor` and `CoherentOptics`.
These compute fully polarized, monochromatic, locally paraxial electric phasors,
separate coherent-vs-incoherent combinations and distinguish electric energy
density, magnetic energy density and leading-order Poynting transport.
They are **not** `WorldEffectCandidate` producers and have no NeoForge,
server/client or permission dependency. D5 `GaussianPlane` q/ABCD transfer is
still independent from the new physical-path phase references; no automatic
interferometer lens-path propagation or source-to-world adapter exists.
See `OPT_001_COMPLEX_FIELD_REFERENCE.md`.
