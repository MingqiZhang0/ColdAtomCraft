## CAC-M0-R2.1-D14 - 2026-10-27 - 3D diagnostic optical bench

- Introduced a bounded independent Java six-direction axis-aligned ideal
  optical network. Reused D5 Gaussian q, D6 Jones, and D9 ideal detector math.
- Added lossless 90-degree mirror, ideal 50:50 splitter, ideal thin lens,
  half-wave plate and 6-way detector as four new NeoForge optical device blocks
  plus upgraded meter orientation. Device settings persist in blockstates.
- Client beam display and server meter share the same read-only solver;
  unfinished/multibranch results refuse ambiguous meter readings.
- Fixed deterministic regeneration of D12 laser GUI localization; the legacy
  D10 placeholder generator formerly discarded 14 existing translation keys.
- Rebuilt and verified the complete SHA-256 source inventory with a new
  fail-closed manifest checker, fixing D13 archive inventory drift.
- Added invalid persisted laser-config fail-closed state while preserving
  compatible old saved fields and manual valid-parameter recovery.
- Added independent 3D Java smoke, formal JUnit 5 test source, contract tests,
  source/asset validation and CI/JAR evidence gates.
- Still NOT a physics-model approval, Minecraft Java 25 D14 build, runtime
  signoff, real optical coating model, Blender asset integration or world effects.

## CAC-M0-R2.1-D10 (placeholder apparatus)

- Added two DEV-only Minecraft cuboid equipment models, items, creative tab, server-side click interactions and blockstates.
- Added pure reference preview optical bench and conservative read-only optical path scan, linked to D9 ideal W power measurement.
- Added D10 test fixtures, blockstate/model/item/loot/language asset validator, CLI playtest and JAR release checks.
- Bumped development mod version to `0.0.2-dev-preview` to distinguish from old unbuilt source and R2/D9 JARs.
- D10 cannot be declared playable until a real JDK25/NeoForge build and actual game interaction test complete.

## CAC-M0-R2.1-D9 — 2026-10-26 (ideal optical power observation)

- Added finite circular Gaussian aperture integration with displaced aperture
  and robust analytic centred limit; power budget remains optical only.
- Added front-facing, normal-incidence 3D detector face with lab-coordinate
  precision rejection; no Minecraft block units assumed.
- Added an ideal, noninvasive W-valued `Measurement` adapter with explicit
  zero-model-uncertainty labeling, not a real instrument calibration.
- Added JUnit SOURCE tests, standalone power smoke, dependency-free Decimal80
  reference oracle, optional SciPy stress, and JDK25 CI/verification wiring.
- **Did not** add laser damage, heat, sensor electronic noise, Owner production
  approval, or actual Blender/NeoForge device integration.

## CAC-M0-R2.1-D8 · 2026-10-26 · Geometric optical interface and mirror reference

- Added right-handed `RayFrame3`, `GeometricJonesRay`, `LaboratoryJonesAmplitude`,
  `OpticalPlane3`, `PlanarFresnelInterface`, `IdealCalibratedPlaneMirror`.
- Implemented 3D specular reflection, Snell refraction, s/p polarization
  decomposition, signed Fresnel complex field amplitudes, power-normalized
  transmitted traveling modes and TIR phase-only reflected power.
- Explicitly **separated** one uncoated lossless dielectric interface from
  an ideal fully reflective mirror with externally supplied coating phases.
- Prevented incorrect plane-mirror Gaussian phase conjugation: outgoing-local
  `q` is unchanged at a flat mirror; radial phase continuity is tested.
- Added 31 JUnit5-source test methods, 18 standalone geometry smoke checks,
  146 independent Decimal80 Fresnel scalar comparisons and D8 scripts/docs.
- Formal JDK25/Gradle/JUnit, NeoForge build/runtime, Blender device integration
  and Owner production-model approval remain BLOCKED/NOT RUN/NOT IMPLEMENTED.

## 2026-10-26 — CAC-M0-R2.1-D7 (experimental optical path)

- Add `ParaxialModeField` with phase-aware vacuum Gaussian propagation,
  lens phase, uniform Jones optics, local beam sampling and path ULP diagnostics.
- Add `IdealTwoPortBeamSplitter`: symmetric lossless, unitary 2-port reference,
  retaining both output powers and rejecting mismatched modes.
- Add immutable `OpticalPathTrace` with tracked-mode power removal (not heat).
- Add 30 JUnit-source regression tests, D7 smoke/Decimal80 oracle, docs.
- Include D6/D7 optics smoke and oracles in Java25/CI gate; raise the required
  JUnit-source method count to 224 and require all new suite XML files.
- Keep OPT-001 scientific DRAFT and no world material interactions.
- Formal JDK25 NeoForge runtime for this D7 source remains unverified.

## CAC-M0-R2.1-D5 — OPT-001 draft optics reference

- Added `physics-core/.../optics/GaussianBeam`, `GaussianQ`, `ParaxialAbcd` and `GaussianPlane`: scalar vacuum CW TEM00 intensity, Gaussian q parameter, ideal thin lens and free propagation; all SI units, no game effects.
- Added 18 new JUnit5 test methods and standalone `OpticsSmoke` analytical checks; independent 80-digit Decimal oracle verifies 36 numeric values.
- Corrected strong-focusing ABCD imaginary-part cancellation by deriving `Im(q_out)` from the symplectic determinant identity; a pre-fix regression example with q = 10^16 + i failed.
- Exposed paraxial divergence diagnostic and separated physical waist location from a laser output port.
- Updated scripts/CI/JDK25 evidence runner to compile and verify this isolated optics reference, while explicitly retaining the unmet build/runtime Gate.
- No `OWNER_APPROVED` registry entry, world mutation, harm, atom scattering or Blender asset was implemented.

---

# Changelog

## M0-R2.1-D4 · 2026-10-26 · Director ledger index + JDK25 evidence tooling

- Replaced abandoned-run history's O(total settlements) scan with an O(1) monotonic per-run claims index, while conserving CLAIMED / APPLIED / FAILED retry prohibition.
- Added four JUnit-source regression cases for mixed terminal outcomes, unrelated runs, rejected claims and duplicate submissions.
- Added an independent JAR verifier, including comprehensive compiled class inventory and Java 25 classfile checks, with eleven synthetic Python tests.
- Added opt-in local JDK25 Gradle/JUnit/JAR evidence runner; observed clean **BLOCKED** status with JDK21. Does not claim actual NeoForge execution.
- Compared D3 vs D4 abandonment hot-path latency under one JDK21 host; microbenchmark only (NOT game TPS).
- No new science formulas, equipment, in-game mutation, EULA edits, or production model approvals.


## M0-R2.1-D3 · 2026-10-26 · Director in-memory scheduling contract checkpoint

- Reproduced D2 failure: independently created sessions can claim same apparatus/physical operation.
- Added mandatory immutable `ExperimentReservation`, `ApparatusKey` and `ServerTickWindow`.
- Updated scheduler to atomically reserve multi-apparatus server-tick windows with an indexed overlap check, removed unrestricted no-argument session creation.
- Added unclaimed full-session cancellation with fail-closed claim/history tombstones; retry keeps existing reservations.
- Bound candidate source apparatus and server tick into submit preconditions (`ServerMutationFacts` now carries currentServerTick).
- Added 18 JUnit-source regression methods in ExperimentReservationTest (existing 9 ExperimentRunSchedulerTest methods updated as necessary), while preserving historical testing scope.
- Current-host Java21 engineering smoke 383/383; 149/149 NON-JUNIT test method sweep; Python 17/17.
- JDK25 Gradle/JUnit, NeoForge build, D3 runtime, and live server adapter **NOT RUN**. No Owner models or gameplay changes.

## M0-R2.1-D2 · 2026-10-26 · Director checkpoint (NOT a release)

- Fixed a demonstrated duplicate claim when `(runId,stepIndex,eventIndex)` remained constant but model ID/revision differed; added independent event-slot index and audit lookup.
- Added `ExperimentRunScheduler`: scheduler-issued logical sessions and attempt tokens for future trusted server use, stale attempt fencing, replacement only after zero-claim abandonment, same-session no-replay after Claim.
- Added formal JUnit source tests for slot aliases and scheduler concurrency; independent JDK21 reflection shim executed 131/131 methods (not JUnit), standalone smoke 379/379 and Python asset suite 17/17.
- Captured a before-fix RED probe and noisy same-host D1/D2 microbenchmarks; retained all historic D1 evidence without claiming it as D2.
- No scientific equations, game-world effects, server world adapter or Blender model created; full Java25/Gradle/NeoForge D2 build NOT RUN.

## M0-R2.1-D1 · 2026-10-26 · Director checkpoint (not a release)

- Continued the partially completed DeepSeek R2.1 source without changing any physical model or Blender geometry.
- Introduced explicit `WorldEffectLedger.abandonRun` for stale, **unclaimed** calculation runs; refuses runs with any claimed event and fences late result claims under retired run IDs.
- Added `RUN_ABANDONED` to Ledger begin/claim and coordinator decision results; actual solver re-execution must use a new server-owned run ID.
- Added five JUnit test methods covering lifecycle, stale computation, terminal settlements, unknown runs and concurrent Claim/Abandon arbitration; aligned two misleading same-run recalculation tests with the new protocol.
- Expanded dependency-free Java smoke to 363 checks including 100 synchronized two-thread arbitration trials. Python asset tests remain 17/17.
- Independently exercised 118 test methods through a temporary offline assertion runner under JDK 21; **not a formal JUnit result**. Re-ran JDK 21 performance probe at 1k/10k/50k step scales; results are host-specific, not direct cross-host speedup claims.
- Updated world contract, architecture, decisions, audit and provenance; regenerated manifest. JDK 25 Gradle/NeoForge final build and production JAR installation remain NOT RUN for this D1 tree. M0 remains open.

## M0-R2 · 2026-10-26 · Build, runtime and world-contract audit

- Added the complete official Gradle `9.2.1` wrapper (`gradlew`, `gradlew.bat`, wrapper JAR) with a verified distribution SHA-256.
- Updated the runtime pin to NeoForge `26.3.0.99` and adopted the official ModDevGradle `disableRecompilation = true` pipeline after the local NFRT recompile path failed writing `package-info.class`; the official MDK control project passes with the same baseline.
- Repaired CI ordering so Java 25 is installed before any Java compile/test; switched to the checked-in wrapper and added JAR verification.
- Added build-time `verifyDistributableJar` checks for bundled `physics-core`/`experiment-core` classes and NeoForge metadata.
- Added calculation-step, simulation-interval, transfer-event and terminal settlement states; duplicate steps/events, overlapping intervals, failure and restart limitations are tested.
- Added loaded-state/device/target snapshots, precheck-only `WorldEffectGate`, server-mutation facts contract and immutable server-owned `ModelCatalog`/`ModelReference` boundary.
- Added explicit glTF `+Y` up / `+Z` forward conversion and tests; strengthened Blender manifest/GLB validation and exporter readiness checks.
- Executed 49/49 JUnit, 46/46 standalone checks, 17/17 Python tests, a clean NeoForge build, development client load, singleplayer create/save/re-enter, and two dedicated-server graceful restart cycles.
- Packaged-JAR installation, GameTestServer, CI runner execution, real Blender export and in-game rendering remain unverified/not run. No physics formulas or world damage were added. M0 remains open.

## M0-R1 · 2026-10-25 · Engineering foundation hardening

- Hardened `Vec3.normalized()` against subnormal or extremely large finite input; canonicalized signed zero.
- Separated scientific owner approval (`ModelApproval`) from actual implementation/numerical/game verification (`ModelVerification`), with a fail-closed gate.
- Explicitly named laboratory-local SI coordinates for laser input placeholders (still **not a laser model**).
- Added deterministic Blender-to-Minecraft placement transforms and cardinal direction / handedness checks.
- Added immutable version stamps for the **full chunk read footprint** so a modified intermediate mirror invalidates a stale multi-chunk ray result.
- Added data-only energy transfer candidates and preliminary fail-closed model/revision checks; **no world changes implemented**.
- Added JSON Blender asset manifest validator with filename safety, fields, units, transform anchors and basic file-header checks.
- Added optional Blender handoff exporter script; syntax-checked, not Blender-runtime-tested.
- Created dedicated science intake, architecture, world-interaction, Blender handoff and Windows verification documents.
- Enhanced offline smoke from 12 to 37 checks; added 13 Python asset-schema test cases.
- No unapproved physics formulas, damage/ignition rules, game device blocks or final Blender geometry added.
- JDK 25 Gradle compilation and MC client/server launch remain unverified.

## M0 · 2026-10-25 · Project initiation

- NeoForge scaffold and stand-alone physics/experiment core folders.
- Project charter and first scientific model intake contract.
- Offline Java 12-check initial smoke test.


## 2026-10-26 — CAC M0-R2.1-D6 (director numerical reference)

- Added complex-valued Gaussian field phasors with explicit waist-phase, local
  transverse Jones basis and phase-reference identity; no inferred laser-port waist.
- Added ideal linear polarizer, quarter-/half-wave retarder and Jones matrix
  composition, retaining relative/global phases for interferometry.
- Added coherent and incoherent superposition, including Cartesian transverse
  field rotation, cycle-averaged E/B energy densities and leading-order Poynting.
- Added 23 JUnit-source methods, 19 independent Java smoke checks, 39 new
  Decimal(70-digit) scalar comparisons, and updated formal verification threshold.
- Protected against accidental duplicate beam IDs, incoherent summing of a
  shared declared phase reference, carrier mismatch and implicit Jones gain.
- No Owner model approval, new block, damage law, optical coating, Blender mesh,
  Java 25 formal build or D6 game-runtime evidence. M0 remains OPEN.
