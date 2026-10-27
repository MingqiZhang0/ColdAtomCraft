# CAC D14 - 3D optical bench development checkpoint (source only)

Adds six-direction diagnostic optical ports (mirror, ideal 50:50 splitter,
ideal thin lens and half-wave plate), bounded ray/ABCD/Jones propagation,
shared read-only client/server solver, six-way power meter and D13 multi-beam
particle budget. Source/wavelength/waist GUI controls remain D12 development
fixtures; no Create integration, MOT, BEC, laser damage or Blender assets.

**D14 JAVA 25/NeoForge CI AND IN-GAME VALIDATION HAVE NOT BEEN RUN.**
The existing GitHub main/D12 successful workflow is not D14 evidence.
Pure Java reference and static integration tests are available locally;
see [D14 manual and physics boundary](docs/D14_THREE_DIMENSIONAL_BENCH.md)
and [D14 verification](verification/D14-three-dimensional-optics/README.md).
No release, GitHub PR, branch or push was performed.

---

# D13 MULTI-BEAM VISUAL HOTFIX

This checkpoint removes D12's arbitrary three-visible-laser cap. A shared dust-particle budget (1200 particles / 4 ticks) scales sample density across nearby enabled lasers while leaving measured power and saved settings untouched. See `docs/D13_MULTI_BEAM.md`.

# CAC D12 — Laser parameter UI and per-device settings (development preview)

**Status: SOURCE CHECKPOINT — NeoForge 26.3 JDK25/CI compilation and in-game UI remain unverified.**

D12 builds on D11's Gaussian-particle beam. **Right-click the diagnostic laser to open
its parameter screen** instead of toggling it. The panel contains an ON/OFF
button, left/right rotation, and sliders for output power (1–200 mW), vacuum
wavelength (400–1100 nm), waist radius (0.10–3.00 mm), and waist longitudinal
position (−2.00 to +2.00 m from the output port).

Settings are individually stored in a `LaserSettingsBlockEntity` and saved to
the world. The vanilla container button channel authorizes server changes;
finite data slots mirror values to the client. The D10 ideal power meter and the
D11 false-colour Gaussian beam use the *same* per-device `LaserTuning`.
Controls do not invent absorption, heating, radiation damage, automatic cold
atom cooling, or manufacturing characteristics. The 24-block straight-path
cap is an instrument PREVIEW limitation, not a propagation law.

**This ZIP includes D11 + D12** because the user's GitHub `main` had D10 at
package preparation. Use the embedded one-file PowerShell installer for GitHub
upload, or import the whole source directory in a clean repository.

For testing and operating instructions: [D12 GUI guide](docs/D12_LASER_GUI.md).
For the independent Java reference smoke: `bash verification/D12-laser-gui/run_d12_offline.sh`.
On JDK25/NeoForge: `python tools/verify_m0.py`, then playtest the JAR after CI
success. This environment has JDK21 only: do not treat offline tests as formal
NeoForge or runtime proof.

---

# CAC-M0-R2.1-D10 — Placeholder interactive apparatus (DEV ONLY)

**D10 contains provisional Minecraft-native cuboid models for a diagnostic laser
and ideal power meter. They are NOT the Owner's Blender assets and all physical
source parameters are clearly labelled test fixtures.** D10 is a source-only
NeoForge preview: **JDK25 compilation, client/server loading, in-game click
behavior and actual model rendering are NOT YET VERIFIED** on this host.

- Two placeable test blocks and creative tab, laser ON/OFF and horizontal facing,
  meter sample action and three aperture presets.
- Server-only linear, air-only, 24-block scan with strict blocked/off/misaligned/
  unloaded/no-source statuses. Measurement calls the existing D9 ideal Gaussian
  power-meter mathematics; does not modify or damage the world.
- Device states use blockstates (no extra NBT or BlockEntity); placeholder JSON
  models, item definitions and languages are supplied.
- Offline smoke: `sh verification/D10-placeholder/run_preview_offline.sh`.
- Full JUnit-source offline runner: `sh verification/M0-R2.1-D3/run_local_offline.sh`.
- Formal JDK25: `python tools/verify_m0.py` then launch client.
- **Step-by-step in-game acceptance tests:** [D10 playtest](docs/D10_PLACEHOLDER_PLAYTEST.md).
- [D10 engineering audit](docs/D10_ENGINEERING_AUDIT.md).

---

# CAC M0-R2.1-D9 — Ideal experimental optical-power observation (2026-10-26)

**D9 is an offline scientific/measurement REFERENCE, not a calibrated hardware
instrument, an Owner-approved production model or a playable NeoForge detector.**
M0 JDK25/NeoForge runtime remains unverified for this D9 source.

This checkpoint retains D5 Gaussian beams, D6 complex/Jones fields, D7
matched-mode phase/beam-splitter optics and D8 geometric/Fresnel interfaces,
then adds a **finite circular-aperture ideal power receiver**:

- `GaussianCircularAperture`: centred closed-form and decentered numerical TEM00
  power integration, with captured/uncollected W budget;
- `IdealCircularDetectorFace`: lab-local 3D front-facing normal-incidence
  geometry, including lateral offset and coordinate-ULP checks;
- `IdealOpticalPowerMeter`: explicit ideal W observation, time stamp, zero
  *idealized model* uncertainty, no invented calibration/electronics;
- coherent Mach–Zehnder two-port finite-aperture measurements.

Independent Decimal80 Poisson/Gamma reference comparisons and optional SciPy
stress checks cross-validate the different numerical integration method.
**A virtual reading never consumes optical power or authorizes world effects.**

**D9 independently executed:** 383/383 engineering smoke; 287/287
JUnit-source methods via an explicitly NON-JUNIT offline runner; 30/30 Python
engineering tests; 17/17 D9 power-meter smoke; 26/26 new Decimal80 aperture
reference values and 1,500/1,500 optional seeded SciPy comparisons. Original
D5–D8 reference oracles also passed. **Formal JDK25 is BLOCKED (exit 2),
D9 NeoForge build/runtime NOT RUN.** See the D9 audit for exact evidence.

D9 offline checks:

```bash
sh verification/M0-R2.1-D9/run_d9_offline.sh
sh verification/M0-R2.1-D3/run_local_offline.sh
```

Formal on a prepared JDK25/NeoForge machine:

```bash
python tools/verify_m0.py
```

Read [D9 physical assumptions](docs/OPT_001_IDEAL_POWER_DETECTION.md),
[D9 evidence](verification/M0-R2.1-D9/README.md), and
[D9 director audit](docs/M0_R2_1_D9_AUDIT.md).

---

# CAC M0-R2.1-D8 — 3D experimental reflection/Fresnel scientific reference (2026-10-26)

**D8 is an offline Physics Core draft, NOT a playable NeoForge mod or an
Owner-approved calibrated optical model. M0 remains OPEN.**

Building on frozen D7 (287/287 manifest entries verified), this checkpoint
implements local 3D optical ray frames, right-handed s/p polarization bases,
front-face planar intersection, single-interface dielectric Fresnel r/t
with **power-normalized** transmission, total internal reflection phases,
and a separate calibrated **lossless planar mirror**. No coating properties,
finite-beam boundary integrals or material heating are fabricated.

At a flat mirror `q_out=q_in` in the outgoing forward coordinate: the
transverse Gaussian wavefront is not conjugated. This is checked at several
nonzero radial offsets. D8 does NOT replace D7's coherent path/q solver:
using a D7 reference-plane adapter requires explicit local optical frames,
and a ray phase must not be counted twice alongside D7 freeSpace/Gouy.

**Current-host evidence:** 383/383 engineering smoke, 255/255 Java test-source
methods with an offline **NON-JUNIT** shim, 18/18 D8 ray smoke,
146/146 Decimal80 Fresnel reference comparisons, and historical D5/D6/D7
oracles 36+39+29. JDK25 Gradle/JUnit **BLOCKED**, D8 NeoForge build/runtime
**NOT RUN** on this JDK21 host. No ModelCatalog production OWNER_APPROVED entry.

D8 offline verification:

```bash
sh verification/M0-R2.1-D8/run_d8_offline.sh
```

Formal JDK25 Gradle/JUnit/JAR verification on an appropriately configured host:

```bash
python tools/verify_m0.py
```

Read [D8 optical science and conventions](docs/OPT_001_GEOMETRIC_FRESNEL_REFERENCE.md),
[D8 verification evidence](verification/M0-R2.1-D8/README.md) and
[D8 audit](docs/M0_R2_1_D8_AUDIT.md).

---

# CAC-M0-R2.1-D7 — phase-tracked experimental optical-path reference (2026-10-26)

**D7 is a scientific numerical DRAFT, not an Owner-approved production model,
NeoForge playable laser or official M0 runtime sign-off.** M0 JDK25 / NeoForge
verification on the **D7 source** is BLOCKED/NOT RUN in this JDK21 host.

From the frozen, SHA-verified D6 source, D7 introduces a power-normalized,
phase-tracked Gaussian/Jones optical mode, lossless symmetric two-port beam
splitter and immutable optical path trace. The Mach-Zehnder regression shows
phase-controlled port redistribution **without creating power**. It refuses
mismatched carrier/q/coherence/basis inputs rather than silently promoting
an unmatched field to a single Gaussian mode.

- JUnit 5 *source* methods: 224 total (30 new); offline reflection **NON-JUNIT**
  methods pass; D7 standalone optical smoke 19; independent Decimal80 oracle
  29 scalar comparisons. Retains the D5 36 and D6 39 independent checks.
- Engineering build scripts and CI now include D6 and D7 science checks and
  require all JUnit class reports, not just a high aggregate test count.
- Experimental optical phase requires calibrated path lengths. The ULP
  precision diagnostic explicitly warns against using large MC world
  coordinates as interferometric phase.
- **No real mirror geometry, coatings, Fresnel interfaces, splitter orientation,
  materials, atoms, detectors, Blender model or world damage implemented.**

Read [D7 scientific reference](docs/OPT_001_OPTICAL_PATH_REFERENCE.md),
[D7 evidence](verification/M0-R2.1-D7/README.md) and
[D7 director audit](docs/M0_R2_1_D7_AUDIT.md).

Offline reference command (uses JDK21 and NON-JUNIT shim):

```sh
sh verification/M0-R2.1-D7/run_d7_offline.sh
```

Formal JDK25/Gradle/JUnit/JAR gate (on a properly configured build host):

```sh
python tools/verify_m0.py
```

---

# Cold Atom Craft — M0-R2.1-D6 Complex-field and Jones optics reference (2026-10-26)

**Two separate Gate statuses:** M0 NeoForge build/runtime for **D6** remains
**BLOCKED/NOT RUN** on this JDK21/offline host; D6 adds a dependency-free
scientific **DRAFT REFERENCE**, not an Owner-approved model, a Blender asset,
or a playable laser.

D6 expands the D5 Gaussian optics reference into **complex phasor/Jones
polarization, ideal waveplates/polarizers, coherent/phase-incoherent field
superposition, standing-wave Gouy-phase effects, and separate electric vs
magnetic cycle-averaged energy and leading-order paraxial Poynting flux**.
These are optical field *observables*, not a material-absorption model or a
world energy mutation API. No `ModelCatalog` production approval is changed.

**New validation:** 23 JUnit-source test methods (194 total in a **NON-JUNIT**
reflection runner), 19 standalone D6 smoke checks, **39 independent 70-digit
Decimal scalar comparisons**, retaining all 36 D5 independent 80-digit checks.
The previously audited 383 engineering smoke tests and 30 Python engineering
tests remain; see the [scientific reference](docs/OPT_001_COMPLEX_FIELD_REFERENCE.md)
and [D6 director audit](docs/M0_R2_1_D6_AUDIT.md).

```shell
bash verification/M0-R2.1-D6/run_d6_offline.sh
# Formal JDK25/Gradle/JUnit/JAR gate (on a machine with dependencies):
python tools/verify_m0.py
```

**No phase-accurate interferometer after arbitrary ABCD optics is implied.**
The branch geometry, phase reference and actual source/optical element path
must be supplied by the future experiment hardware model. Do not infer input
energy conservation from superposing arbitrary beam phasors: a real interferometer
requires the other output ports and optical boundary conditions.

---

# Cold Atom Craft — M0-R2.1-D5 OPT-001 optics reference (2026-10-26)

**Two separate statuses:** (1) D4 M0 NeoForge build/runtime Gate remains **BLOCKED/NOT RUN** on this JDK21 offline host; (2) D5 adds a **draft physical calculation reference**, not a new playable laser or an Owner-approved production model.

Added a dependency-free, laboratory-local SI **scalar CW TEM00 Gaussian beam**, ideal free-space / thin-lens ABCD q-parameter transport, and a lossless **GaussianPlane** for lens-plane irradiance in `physics-core`. Unlike a game damage model, it tracks the true Gaussian radial intensity and integrates to the entered optical power. The model is explicitly valid only within the vacuum paraxial approximation. It neither chooses hardware waist placement silently nor simulates absorption, vector polarization, interference, atom cooling or MC world damage.

New tests: 18 JUnit5-source methods, 21 pure Java optics smoke assertions, **36 independent Decimal(80-digit) oracle comparisons**. Original pure core/scheduler checks and asset tools are retained; see [OPT-001 physics reference](docs/OPT_001_GAUSSIAN_REFERENCE.md) and [D5 evidence report](docs/M0_R2_1_D5_AUDIT.md).

Commands on any suitable JDK21+ host for **offline reference-only** verification:

```shell
python3 verification/M0-R2.1-D5/verify_optics_reference.py
bash verification/M0-R2.1-D5/run_d5_offline.sh
```

On a machine with genuine JDK25 + Gradle/NeoForge dependencies, run `python tools/verify_m0.py` for the actual Gradle/JUnit/JAR Gate; the script does not test Minecraft client/server installation or require an EULA. **Do not confuse offline method sweeps with formal JUnit.**

---

# Cold Atom Craft — M0-R2.1-D4 verification + ledger hot-path checkpoint

**Not a NeoForge release. M0 remains OPEN. No physical laser or Blender asset is implemented.**

D4 makes a narrow, tested pure-Java performance correction: a claim monotonic
per-run history index replaces the old all-settlement scan during stale-run
abandonment. Even an APPLIED, FAILED or pending CLAIMED event still forbids
abandonment. Four new JUnit-source regressions test these invariants. This does
not provide disk persistence or cross-restart delivery guarantees.

D4 also adds two independently testable release-validation tools:

- `tools/verify_m0.py`: opt-in local JDK 25 Gradle/JUnit/JAR **content** evidence
  runner. It does not launch Minecraft or accept the EULA. It exits BLOCKED
  if JDK25 is unavailable; see `docs/RUN_ON_WINDOWS.md`.
- `tools/inspect_mod_jar.py`: validates metadata, classfile major 69, forbidden
  probes and **every actual compiled `.class` in all three source modules**
  against the packaged JAR; does not prove the JAR loads in-game.

**Verified here (JDK21/offline):** 153/153 assertion-runner methods (NOT
JUnit), 383/383 existing Java smoke checks, 28/28 Python unit tests, and a
D3-vs-D4 observational benchmark. **JDK25 / Gradle / new NeoForge JAR: NOT
RUN/BLOCKED** on this host, which has JDK21 and cannot reach the repositories.
D3/R2 old game-launch logs are historical, NOT D4 runtime evidence.

See [D4 audit](docs/M0_R2_1_D4_AUDIT.md) and [D4 evidence](verification/M0-R2.1-D4/README.md).

---

# CAC-M0-R2.1-D3 — director engineering checkpoint (2026-10-26)

**Status: pure-Java engineering checkpoint, NOT a NeoForge release; M0 remains open.**

D3 addresses the **proven D2 cross-session apparatus/time collision** by requiring every new
logical experiment session to atomically reserve a nonempty set of apparatus block positions
for one *server-wide scheduling tick window*. Only one same-scheduler reservation may own
an apparatus during overlapping windows. Overlap checks use per-apparatus ordered maps;
multi-apparatus reservations are all-or-none. The previously unrestricted
`createExperiment()` API was removed rather than leaving an easy bypass.

An attempt may submit a candidate only from a reserved source apparatus and only when
`ServerMutationFacts.currentServerTick` lies in the session's reserved half-open window.
Simulation nanoseconds (`SimulationInterval`) are **not** identified with Minecraft ticks.
Cancellation releases a session's reservations ONLY when no effect has ever been claimed;
settlement history leaves an in-memory tombstone, including after FAILED.

**D3 local evidence**: D2 RED reproduced (both duplicate sessions claimed); D3 Java21 pure
core smoke **383/383 PASS**; existing and new Java methods **149/149 PASS in a NON-JUNIT
offline reflection harness**; Python asset tests **17/17 PASS**. Formal JDK25 Gradle/JUnit,
NeoForge build, distributable JAR, D3 game runtime and production world adapter are
**NOT RUN / NOT IMPLEMENTED**. Historical R2 runtime tests do not validate D3.

**Strict scope**: All D3 reservations, tick facts and ownership handles are ordinary Java
objects, not server-authenticated proof. Two `ExperimentRunScheduler` instances or a caller
fabricating device/time claims can bypass the in-memory separation; a real server adapter
must own the *single* authoritative scheduler per scheduling domain, validate the entire
world state, model, device, target, permissions and tick on the server thread, and choose a
crash/restart recovery policy before any actual world mutation. No Owner physical model was
created or modified, and no Blender asset was fabricated.

See [D3 audit](docs/M0_R2_1_D3_AUDIT.md), [D3 evidence](verification/M0-R2.1-D3/README.md),
and the [world interaction contract](docs/WORLD_INTERACTION_CONTRACT.md).

---

## Historical D2 README (the below section is not D3 validation)

# Cold Atom Craft (CAC)

**Status: M0-R2.1-D2 code director checkpoint, 2026-10-26 — NOT a release; M0 remains open.**

D2 adds provenance-independent event-slot deduplication and an in-memory scheduler-issued logical experiment attempt manager (intended for future server integration). **No playable laser, Owner-approved physical formula, real world damage, or production NeoForge mutation adapter has been added.** See [D2 audit](docs/M0_R2_1_D2_AUDIT.md) and [D2 evidence](verification/M0-R2.1-D2/README.md).

**D2 verification:** Java 21 standalone engineering checks 379/379 PASS; ad-hoc assertion reflection sweep 131/131 PASS (**NOT JUnit**); Python asset tests 17/17 PASS. Formal Java 25 Gradle/JUnit and the D2 NeoForge build/runtime are **NOT RUN**; historical M0-R2 runtime evidence does not verify D2.

---

## Archived D1 README (historical snapshot; NOT D2 verification)

**Status:** M0-R2.1-D1 director engineering checkpoint (2026-10-26). **M0 remains open; no playable laser or owner-approved physical models are included.**

Cold Atom Craft is a Minecraft Java 26.3 + NeoForge cold-atom laboratory project: laser optics and world interaction, then MOT, BEC, optical lattices and a neutral-atom quantum computing branch. Apparatus should affect and be affected by the actual Minecraft world, not just a GUI progress bar.

## Division of responsibility

- **Project owner:** research direction, physical equations and assumptions, key scientific choices, Blender source assets/visual design, final release decisions.
- **ChatGPT (code director):** architecture, source review, safety contracts, verification planning and interim engineering fixes.
- **DeepSeek (lead programmer):** primary Java/NeoForge implementation, build/test execution and performance work (current deliverable is a director checkpoint, not a DeepSeek release).

Owner approval and actual numerical verification are tracked separately. Physical models for ray transport, heating and injury have **not** been accepted and are **not** included.

## M0-R2.1-D1 status and provenance

This directory is an **engineering checkpoint derived from** the uploaded `ColdAtomCraft-M0-R2.1-Checkpoint.zip`, not a validated Java 25/NeoForge release artifact.

- R2.1 introduced complete-candidate precheck binding, exact interval matching, server-side revalidation *contracts*, and a per-run interval index. Historic 113/113 JUnit XML results came from DeepSeek's intermediate Windows run; they are not new final-build results.
- D1 adds `WorldEffectLedger.abandonRun` with fail-closed behavior: only a run with **no settlement claims** can be retired; late claims and new steps under that run ID are rejected. Re-executing the solver must use a distinct server-owned run ID.
- D1 adds a 5-method JUnit regression class and aligns two existing stale-recalculation tests with that lifecycle contract. A standalone smoke program also exercises a repeated Claim/Abandon race.
- On the available **Linux JDK 21** environment: standalone `Smoke` **363/363 PASS**, Python asset tests **17/17 PASS**, and **118 test methods** passed an independent temporary offline assertion runner. **The latter is not the JUnit engine.** This environment lacks JDK 25 and reachable dependency repositories; formal Gradle/JUnit/NeoForge build and production JAR installation of D1 are **NOT RUN**.
- Previous Minecraft 26.3/NeoForge 26.3.0.99 client/server success is M0-R2 historical evidence, **not evidence for this modified D1 code**.

See [D1 audit](docs/M0_R2_1_D1_AUDIT.md), [world contract](docs/WORLD_INTERACTION_CONTRACT.md), and [performance audit](docs/PERFORMANCE_AUDIT.md). The Owner has not closed M0.

## Verified M0-R2 baseline

| Item | Pinned value |
| --- | --- |
| Minecraft Java Edition | `26.3` |
| NeoForge | `26.3.0.99` |
| JDK | Eclipse Temurin `25.0.3+9-LTS` (Java toolchain 25) |
| Gradle | `9.2.1` via checked-in wrapper |
| ModDevGradle | `2.0.148`, official `disableRecompilation = true` pipeline |
| Mod ID / version | `coldatomcraft` / `0.0.1-dev` |

The checked-in wrapper includes `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` and a verified distribution checksum. See [Gradle wrapper provenance](gradle/README.md).

## Quick start

JDK 25 must be installed. From the project root:

```text
.\gradlew.bat :physics-core:test :experiment-core:test :neoforge-mod:build
```

Linux/macOS: `./gradlew ...`.

Development runs:

```text
.\gradlew.bat :neoforge-mod:runClient
.\gradlew.bat :neoforge-mod:runServer
```

A normal dedicated server may require the user to accept the Minecraft EULA; the project does not accept it on the owner's behalf. The M0-R2 development-server run did not create an EULA file.

## Current evidence

- `physics-core` and `experiment-core` JUnit: **49/49 PASS** in the historic M0-R2 audit; later R2.1 submission contains **113/113 PASS** XML (not a D1 final-build result).
- D1 independent standalone Java smoke: **363/363 PASS** under JDK 21 (no Minecraft classpath).
- Python asset-manifest tests: **17/17 PASS**.
- `:neoforge-mod:build` and JAR-content verification: **PASS** with the official non-recompilation pipeline.
- Official MDK control project with the same runtime baseline: **PASS**.
- Development client load and singleplayer world create/save/exit/re-enter: **PASS**.
- Dedicated server load and two graceful restart/stop cycles: **PASS**.
- Production-installed JAR launch, GameTestServer, Blender export and Minecraft rendering remain **NOT RUN**; see [test status](docs/TEST_PLAN.md) and the [audit report](docs/M0_R2_AUDIT.md).

The local NeoFormRuntime decompile/recompile path failed while writing `package-info.class`. M0-R2 uses the official ModDevGradle `disableRecompilation` option instead; project sources are still compiled by javac 25. The failure and the control experiment are documented, not hidden.

## Project layout

| Folder | Current capability |
| --- | --- |
| `physics-core/` | Pure Java validated vectors and candidate laser input data; **no optical solver yet** |
| `experiment-core/` | Instrument readings, owner-approval/verification gates, server-owned model catalog, Blender placement conversion, calculation/event ledger, revision-tagged world-effect data carriers |
| `neoforge-mod/` | Minimal NeoForge entrypoint; **no registered playable device blocks** |
| `assets/` | Example manifest; **no owner-made 3D model included** |
| `tools/blender/` | Optional Blender handoff exporter, syntax-checked only |
| `tools/validate_assets.py` | Manifest/schema/GLB-structure validation, not rendering verification |
| `verification/` | Offline Java smoke, Python validator tests and M0-R2 run evidence |
| `docs/` | Charter, scientific intake, architecture, world contract, Blender handoff, audit and test status |

## Offline checks

```powershell
.\tools\verify-core.ps1
python -m unittest discover -s verification -p 'test_*.py'
python .\tools\validate_assets.py .\assets\manifests\laser_source.example.json
```

The example manifest must report `SPEC ONLY`, because no Blender or GLB model exists.

## Next actions

1. Execute **D1** on a JDK 25/NeoForge 26.3.0.99 environment: formal JUnit, clean/no-cache Gradle build, JAR content checks and, if possible, separate production-instance JAR installation. Do not substitute M0-R2's historical JAR.
2. Run CI on a real GitHub runner and retain the URL/log.
3. Require the future server experiment scheduler to serialize logical-experiment retries: the ledger can abandon only unclaimed runs; it does not prevent independently invented run IDs from duplicating earlier world effects.
4. Build a real NeoForge server snapshot/mutation adapter and durable recovery protocol **before enabling any physical world effect**.
5. Accept the owner's first laser `.blend`/GLB handoff and science intake before implementing OPT-001 in the game. Physically meaningful formulas remain owner-controlled.

Primary docs: [D1 Audit](docs/M0_R2_1_D1_AUDIT.md) · [Charter](docs/PROJECT_CHARTER.md) · [Architecture](docs/ARCHITECTURE.md) · [World Interaction](docs/WORLD_INTERACTION_CONTRACT.md) · [Science Intake](docs/M1_SCIENCE_INTAKE.md) · [Blender Handoff](docs/BLENDER_ASSET_HANDOFF.md) · [M0-R2 Audit](docs/M0_R2_AUDIT.md).

## D11 diagnostic laser beam (visual preview)

D11 adds client-side, **false-colour red** particles along the D10 laser direction, with a Gaussian radius trend, 24-block air-only range, and stopping at physical obstructions and power-meter faces. The 780 nm demonstration laser is near-infrared; the visible line is a **diagnostic visualization**, not an assertion that the real beam glows in clean air. It does not modify D9 measured power or apply any world effect. See [D11_VISIBLE_BEAM.md](docs/D11_VISIBLE_BEAM.md).

**Formal JDK25/NeoForge validation and in-game screenshots remain pending on this D11 change.**
