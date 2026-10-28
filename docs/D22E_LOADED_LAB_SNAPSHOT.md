# D22E — Server-loaded optical apparatus and nominal local-SI geometry

**Research status:** implementation + offline validation; **formal Java 25 / NeoForge
26.3 compilation and in-game tests remain pending**. This is not an owner-approved
hardware calibration, not an encoder, not a measurement, and not a permission
mechanism for an automatic laser or Create motor.

## Purpose

D22C/D22D predicted continuous TEM00 power in a declared local-SI frame, but
were disconnected from the actual Minecraft world. D22E supplies the **smallest
trustworthy read-only observation interface** over real, already-loaded CAC
blocks, without modifying the pre-existing D14 optical solver or inventing
continuous world intersections.

It provides two components:

- `LoadedApparatusSnapshot` (pure experiment-core): immutable per-device
  semantic states, spatial coordinates, observation lifetime and fail-closed
  revalidation, with no Minecraft imports.
- `LoadedLabGeometryWorld` (NeoForge-only): maps exact `ServerLevel` block
  addresses, BlockStates and applicable BlockEntities into the pure
  representation. It is explicitly synchronous and checks `hasChunkAt` **before**
  reading, with no `getChunk` or `getChunkAt` force-load route.

`DiagnosticOpticalTableBlock` adds an **empty-hand, read-only right-click
inspector** to the existing table ID. It checks the apparatus one block above,
reports its kind, nominal local centre and direction semantics in chat, or
reports a diagnostic failure. The optical-table asset, recipes and blockstate
remain unchanged. The D22B automatic preset scanner additionally acquires and
revalidates a four-device geometry snapshot at scan admission and every tick.
This improves instrumentation consistency; it does **not** add real motor ON.

## Coordinate meaning

With the selected table as the anchor, `LaboratoryMountFrame` converts the
*integer difference* between mounted blocks before floating conversion:

    r_lab = e_x * L * (b_x - b_x0)
          + e_y * L * (b_y - b_y0)
          + e_z * L * (b_z - b_z0).

The nominal factory uses `L=1 m per Minecraft block` **only as a D14 preview
convention**. It is not experimentally calibrated. The axis of a laser means
**emission travel**, the axis of a meter means **front-facing surface normal**,
and the D14 optical `PORT` means **incoming photon travel**, NOT the optical
model's visible front. The `LocalDevice` record exposes this meaning to avoid
the known D14 front/port convention confusion. Axes may also be expressed in
an explicitly supplied right-handed laboratory frame.

No sub-block or nanometre offset is inferred from a vanilla BlockState. No
encoder measurement, physical orientation quaternion, beam waist location or
calibration uncertainty is fabricated. The pure D22D model can later receive
trusted offsets from independently authenticated stage feedback.

## Scope and lifetime: one tick, no persistent trusted identity

`LoadedApparatusSnapshot.capture(...)` requires a finite list of **1–24 exact
positions**, no duplicates, on the owning server thread. This is intentional:
unbounded world scans are unsuitable for a synchronous laboratory workflow.
The captured metadata stores the server *world instance*, dimension ID and
server game Tick. `verify(...)` fails for a different server instance, dimension,
tick, unloaded chunk, changed BlockState or replaced BlockEntity instance.
It also checks that the world is still on its server thread. It does not read
on the client or expose Java object identities over the network.

**Critical limitation:** D14 mirrors, splitters, lenses, wave plates, meters,
manual stages and tables are stateless Minecraft blocks. They have
`BLOCKSTATE_ONLY` evidence: an operator could break and replace one with an
identical BlockState during the same Tick, and the read-only snapshot could
not distinguish it. Lasers and the automatic preset scanner own BlockEntities,
so `BLOCK_ENTITY_INSTANCE` can detect replacement **in the current JVM session**.
These object identities are NOT a persistent UUID and do not survive reboot.
Thus **even a VALID snapshot does not authorize powered motion, energy
withdrawal, laser activation, or closed-loop feedback**. Durable device IDs,
configuration revisioning and actual motor sensor attestation are a future
engineering task.

## Data provenance

Snapshot output is labelled `READ_ONLY_SERVER_SNAPSHOT` and never produces a
`PowerTrace.Sample`, `DiscretePresetSweep.Point` or experimental instrument
watts. The D22C/D22D prediction remains `REFERENCE_ONLY`, and the actual
D14 meter must independently supply any observed simulation value. Do not
silently merge, interpolate, equate or overwrite these categories.

D22E currently adds no new camera, optical propagation, continuous ray tracing,
visual model movement, persistent experiment sessions, Create dependency or
real power consumption. It cannot detect arbitrary obstacles on a sub-block
continuous ray path. Those need a separate owner-approved physics model.

## Test strategy

- `python tools/check_d22e_live_geometry.py`: strict `javac --release 21
  -Xlint:all -Werror` pure core smoke; deterministic lifecycle, large world
  anchors, state mutation, stale Tick, missing chunk, dimension changes and BE
  identity replacement.
- `python tools/check_d22e_junit_source.py`: compile/run the 18 new JUnit
  method bodies through a minimal offline API **shim**. Not formal JUnit 5.
- `python tools/check_d22e_game_adapter.py`: compile/run the *real adapter and
  table inspector source* against isolated, minimal API-shaped fake world
  types. Simulates real block classification, chat inspection and fail-closed
  access. It does not prove signatures against the actual NeoForge JAR.
- `python -m unittest discover -s verification -p 'test_*.py' -v`:
  cross-version checks including no chunk force-loading or write paths.
- JDK25 Gradle `:experiment-core:test :neoforge-mod:build` and actual in-world
  table click + scanner sampling are required **before declaring a playable
  milestone**. Test on a dedicated server, unloaded chunks, replacement,
  teleport, two worlds, and restart. Record TPS and avoid sustained scanning.

## Next necessary work

D22F should first achieve formal NeoForge build and gameplay QA of the D21–D22
instrument stack, then create persistent and explicitly permissioned device IDs
for the currently stateless optics/meter; associate those with source and
receiver geometry without false stable identity. After that, add a bounded
continuous physical ray/surface solver whose world-occlusion and paraxial
approximations are reviewed by the owner. Only an actual calibrated stage,
encoder and energy reservation mechanism can produce trustworthy feedback for
Create-actuated automatic measurements.
