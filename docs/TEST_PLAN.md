## D10 placeholder interaction qualification

- **Offline-only:** 20 new JUnit-source methods (`PreviewOpticalBenchTest`, `PreviewBeamRouteTest`) plus reference smoke and model asset schema tests. Offline test shims do not count as official JUnit.
- **Actual-JDK25:** re-run formal Gradle tests and `verifyDistributableJar`; requires all registered class files and 19 named resource/class preview artifacts in the release JAR.
- **Actual game:** follow `docs/D10_PLACEHOLDER_PLAYTEST.md`; check inventory models, click routing, ON/OFF rendering, aperture cycling, obstruction, incorrect facing, persistence and correct W output.
- **Not acceptable as proof:** old R2 game screenshots or old R2/D9 mod JARs, static JSON checks, or unexecuted playtest commands.

## D9 optical measurement additions (2026-10-26, draft)

- Two new physics-core JUnit5 SOURCE classes test centred/decentred TEM00
  finite-aperture power, geometry/translation/rotation covariance, wrong-side
  and oblique-face rejection and large-coordinate precision loss.
- One new experiment-core JUnit5 SOURCE class tests ideal SI watt readings,
  noninvasiveness, simulation timestamps, Mach–Zehnder complementary ports,
  offset, free-space enlargement, and missing calibration authority.
- D9 standalone Java smoke: 17 checks. Independent Decimal80 power-aperture
  CDF oracle: 26 scenarios, derived from noncentral chi-square/Gamma/Poisson,
  independent of Java's angular quadrature.
- Optional SciPy reference stress: 1,500 seeded offset cases. This is not a
  mandatory dependency and must never be counted as a formal Java test.
- **Formal test count floor: 287** (255 prior + 32 D9 @Test source methods).
  `tools/verify_m0.py` additionally requires XML for EVERY present *Test.java
  class and requires the actual D9 compiled classes inside the distributable
  JAR. Offline reflection testing is not formal JUnit.
- D9 JDK25 Gradle/JUnit, packaged JAR and actual NeoForge runtime: pending on
  an appropriate prepared environment, not inferred from old R2 startup logs.

## CAC D8 reference verification (2026-10-26)

- D7 baseline verified 287/287 SHA-256 before modifications.
- New geometric Fresnel tests: normal incidence (s/p sign and power), Snell,
  Brewster, total internal reflection complex phases, boundary tangent E,
  near-critical classification, 3D tilted plane, rotation covariance, finite
  path phase, two-surface branch bookkeeping, invalid/grazing incidence,
  and loss of sub-micron geometric displacement at large coordinates.
- New mirror tests: 90-degree folding, phase/polarization, normal lab gauge,
  local Gaussian q continuity, radial wavefront phase continuity, explicit
  output mode-frame identity and invalid mismatched modal inputs.
- Offline JDK21: 255/255 test-source methods in NON-JUNIT reflection shim,
  engineering smoke 383/383, D8 smoke 18/18, D8 Decimal80 146/146,
  retained D5/D6/D7 oracle checks 36/39/29.
- `python tools/verify_m0.py`: JDK25 preflight BLOCKED on this host; therefore
  actual Gradle JUnit reports, Java25 JAR, D8 NeoForge game load NOT RUN.
- CI has JDK25 and minimum JUnit count 254; not run against a real runner here.

## D7 optical-path checkpoint (2026-10-26)

- New formal JUnit5 *source* classes: `ParaxialModeFieldTest` (13),
  `IdealTwoPortBeamSplitterTest` (13), `OpticalPathTraceTest` (4), total 30;
  the total suite now has **224** source `@Test` methods.
- D7 standalone `OpticalPathSmoke`: **19** assertions (pure JDK21 on this host).
- Decimal80 independent D7 optical-path oracle: **29** scalar comparisons;
  D5 36 and D6 39 independent comparisons retained.
- Offline assertion runner is **not** formal JUnit; CI and `verify_m0.py`
  explicitly require actual JUnit XML for every class on JDK25.
- D7 official JDK25 build/JUnit/JAR, Minecraft client/server and physically
  installed mod JAR: BLOCKED/NOT RUN on this Linux JDK21/no-download host.
- No material-energy, Blender model or production Owner-approved optics model.

# D4 current verification (2026-10-26)

| Gate | Current D4 result | Scope |
| --- | --- | --- |
| Frozen D3 archive integrity | PASS 198/198 | SHA-256 against signed-off D3 source manifest |
| D4 Java smoke under JDK21 | PASS 383/383 | NOT JDK25 |
| D4 JUnit-source methods under assertion shim | PASS 153/153 | NOT formal JUnit engine |
| D4 Python tests (including new JAR inspector cases) | PASS 28/28 | 17 asset + 7 inspector + 4 evidence parser |
| D3 vs D4 claim-history microbenchmark | OBSERVATIONAL | Same-host JDK21; see D4 evidence, NOT TPS |
| D4 JDK25 Gradle/JUnit | BLOCKED / NOT RUN | Local JDK21; no Gradle/Maven access |
| D4 official NeoForge JAR | NOT RUN | Cannot build in current environment |
| D4 independent installed-JAR / game runtime | NOT RUN | No D4 JAR exists yet |
| Live server adapter / atomic world effects | NOT IMPLEMENTED | Must precede real laser world effects |

`tools/verify_m0.py` is provided as a user-initiated formal JDK25 evidence
runner on a compatible machine. A script being present is not evidence it
completed these gates. Prior R2 installed/run evidence does not apply to D4.

---

# D3 validation status — 2026-10-26

| Check | Result |
| --- | --- |
| Frozen D2 SHA256 manifest | PASS 179/179 |
| D2 duplicate-session event claim | RED REPRODUCED (2 CLAIMED) |
| D3 atomic apparatus/time reservation and tick-window regression | PASS in offline harness |
| Java 21 core compile + smoke | PASS 383/383 |
| JUnit-source methods via independent reflection/assertion shim | PASS 149/149 — **NOT JUNIT** |
| Python assets | PASS 17/17 |
| Reservation microbenchmark | OBSERVATIONAL, results in D3 evidence |
| JDK25/Gradle 9.2.1 real JUnit run | NOT RUN |
| NeoForge D3 build + distributable JAR | NOT RUN |
| D3 installed game/client/server test | NOT RUN |
| Live `ServerLevel` authority and effect mutation | NOT IMPLEMENTED |

**WARNING:** D2/R2 historical build/client logs remain archived but are not D3 execution
proof. See [D3 audit](M0_R2_1_D3_AUDIT.md) and D3 `verification/` README.

---

## Historical test plans

# CAC M0-R2.1-D2 test status (2026-10-26)

| D2 test | Status | Detail |
| --- | --- | --- |
| Historical D1 input integrity | PASS | 156 D1 manifest members verified before D2 editing |
| Same event slot, changed model revision | **PASS** | D1 RED reproduced, D2 accepted first claim only |
| Java 21 pure core + smoke | **PASS 379/379** | `verification/M0-R2.1-D2/logs/java21-standalone-smoke.txt` |
| Full test methods with temporary assertion runner | **PASS 131/131, NOT JUNIT** | temporary source in `verification/M0-R2.1-D2/offline-harness/` |
| Python assets | **PASS 17/17** | `logs/python-asset-tests.txt` |
| D1 vs D2 microbenchmark | OBSERVATIONAL | same-host JDK 21; variable times, no TPS claim |
| Formal Java 25 / Gradle JUnit | **NOT RUN** | cannot resolve JDK 25 / repositories in this environment |
| NeoForge D2 build, JAR and development client/server | **NOT RUN** | no D2 distributable JAR created |
| Independent production installed JAR | **NOT RUN** | no D2 JAR or NeoForge installation |
| Actual Blender / optical / world effect | **NOT IMPLEMENTED** | Owner input and server adapter pending |

See [D2 full audit](M0_R2_1_D2_AUDIT.md). Earlier R2 and D1 reports below are historical, not final-D2 evidence.

---

# Director D1 addendum — 2026-10-26 (M0-R2.1, not M0 closure)

**Do not treat historical M0-R2 build/JAR/runtime logs as validation of modified D1 source.**

| D1 validation | Result | Evidence |
| --- | --- | --- |
| Original incoming checkpoint manifest | PASS | `sha256sum -c` before editing, 141/141 |
| Pure-Java main + smoke compiled on JDK 21 | PASS | `verification/M0-R2.1-D1/logs/smoke-jdk21.log` |
| Dependency-free Smoke, including 100 Claim/Abandon races | **PASS 363/363** | same log; three assertions per race plus prior checks |
| Python asset tests | **PASS 17/17** | `verification/M0-R2.1-D1/logs/python-assets.log` |
| Existing and added test methods via temporary ad-hoc JDK 21 reflection runner | **PASS 118/118** | `verification/M0-R2.1-D1/logs/offline-assertion-sweep.log`; **NOT JUnit** |
| Formal Gradle/JUnit under JDK 25 on D1 source | **NOT RUN** | JDK 25, Gradle dependencies and NeoForge artifacts unavailable in this environment |
| D1 NeoForge `:neoforge-mod:build` and JAR check | **NOT RUN** | same blocker; archived R2 build is historical only |
| D1 development client and server | **NOT RUN** | no D1 build or Minecraft runtime |
| D1 independent installed JAR | **NOT RUN** | no new release artifact built |
| Real Blender export / client rendering | **NOT RUN** | owner 3D model absent |

**New formal JUnit source:** `WorldEffectLedgerRunAbandonmentTest.java` (5 tests): run tombstone, unknown run, actual recalculation on new run, fail-closed abandonment after claim, and Claim/Abandon concurrency. Two previous tests were tightened to require a distinct run ID for actual solver reexecution. The real JUnit engine has not run this D1 test tree; run it with JDK 25 before merging or releasing.

**Important semantics:** D1's run fence prevents in-memory late claims from an explicitly abandoned run, but it does not link separate run IDs to one logical experiment. It does not implement real `ServerLevel` validation, world changes, durable commit, exactly-once across restart or any laser physics.

---

# M0 verification, provenance and gate status

**Revision:** M0-R2, 2026-10-26  
**Environment:** Windows 11 amd64; Eclipse Temurin JDK 25.0.3+9-LTS; Gradle 9.2.1; ModDevGradle 2.0.148; Minecraft 26.3; NeoForge 26.3.0.99; official `disableRecompilation = true` pipeline.

Status vocabulary: **PASS** = executed with retained evidence; **FAIL** = executed and failed; **BLOCKED** = could not execute for a stated external/authority reason; **NOT RUN** = no claim of execution.

## Executed checks

| Check | Result | Evidence / meaning |
| --- | --- | --- |
| R1 SHA-256 manifest verification before copy | PASS 50/50 | No corrupted R1 input file |
| `gradlew --version` | PASS | Gradle 9.2.1, JVM 25.0.3 |
| `clean :physics-core:test :experiment-core:test :neoforge-mod:build --no-build-cache --rerun-tasks` | PASS | `verification/M0-R2/logs/build-final.log` |
| JUnit (physics-core + experiment-core) | PASS 49/49 | 2+4+3+6+3+7+3+8+9+4; no failures/errors/skips |
| Standalone Java smoke (`--release 25`) | PASS 46/46 | No Minecraft classpath |
| Python asset/manifest tests | PASS 17/17 | Standard library only |
| Example manifest validation | PASS as SPEC ONLY | Missing owner `.blend`/GLB is intentional |
| Export helper syntax compile | PASS | `bpy` runtime not exercised |
| JAR required-class/metadata check (`verifyDistributableJar`) | PASS | `physics-core`, `experiment-core`, mod entrypoint and `META-INF/neoforge.mods.toml` present |
| Official MDK control project build, same baseline | PASS | `verification/M0-R2/logs/mdk-control.log`; proves project config not the cause of the NFRT failure |
| Development client startup | PASS | Loaded `Cold Atom Craft 0.0.1-dev`, MC 26.3, NeoForge 26.3.0.99; window/GL/audio/resource load; exit code 0 |
| Singleplayer world creation | PASS | World `New World` created; integrated server `Done`; screenshot and log |
| Singleplayer save + quit | PASS | `Saving worlds` for overworld/nether/end; `Stopping!`; client exit code 0 |
| Saved world re-entry | PASS | `--quickPlaySingleplayer "New World"`; loaded 49 chunks; same world; exit code 0 |
| Dedicated server startup | PASS | `Cold Atom Craft` in server mod list; `Done (4.757s)`; no class/registration exception |
| Preliminary server teardown | FORCED, NOT COUNTED | Force-terminated only to enable local RCON; not used as graceful-stop evidence |
| Dedicated server restart #1 | PASS | Existing world loaded; RCON `stop`; three dimensions saved; `BUILD SUCCESSFUL` |
| Dedicated server restart #2 | PASS | `Done (0.365s)` on existing world; RCON `stop`; saved; `BUILD SUCCESSFUL` |
| EULA acceptance required? | NO | The development server run did not create `eula.txt` or stop for EULA; no EULA value was written by this work |
| Packaged JAR installed into a separate production server/client | NOT RUN | Dev runs load source sets; JAR content is verified but installed-JAR runtime is a separate check |
| GameTestServer | NOT RUN | No GameTests were added; a no-test exit is not counted as a pass |
| CI workflow execution | NOT RUN | Workflow was repaired and statically reviewed; no GitHub runner was available |
| Actual Blender export | NOT RUN | No Blender executable/owner `.blend`; no model file fabricated |
| Minecraft GLB/device rendering | NOT RUN | No owner asset and no renderer integration |
| Multiplayer/client-to-dedicated-server join | NOT RUN | Not required for the M0-R2 local transport checks |
| Laser propagation/heating/injury | NOT IMPLEMENTED | Requires separate owner-approved scientific models |

## Build failure discovered and disposition

The default NeoFormRuntime 2.0.31 decompile/recompile path failed in `createMinecraftArtifacts` while writing `net.minecraft.world.waypoints.package-info` to its output JAR. A manual exact-source javac probe reproduced `compiler.err.class.cant.write` with a null cause. This is retained as a **tooling investigation lead**, not as proof of a NeoForge API defect.

M0-R2 uses the official ModDevGradle `enable { disableRecompilation = true }` option, which uses NeoForge's precompiled development artifacts and still compiles all project Java with javac 25. The official MDK control project with the same option and baseline also passes. The default recompile path is therefore **FAIL (tooling path)** while the chosen official path is **PASS**, and the distinction is explicit.

## Evidence locations

- `verification/M0-R2/logs/` — final build, failed build attempts, standalone, Python, client, server and MDK-control logs; `probe-package-info-write-fail.log` records the investigation lead.
- `verification/M0-R2/screenshots/` — client/menu/world screenshots.
- `verification/M0-R2/artifacts/` — distributable JAR and JAR content listing.
- `verification/M0-R2/environment.txt` — toolchain/host facts.
- `MANIFEST_SHA256.txt` — source/evidence checksums.
- `docs/M0_R2_AUDIT.md` — full audit and outstanding work.

## Gate status

| Gate | Status | Notes |
| --- | --- | --- |
| M0-G1: pure-core compiles, offline tests, no MC leakage | PASS | Java 25 `--release 25` smoke; unit tests |
| M0-G2: full JDK 25 Gradle/JUnit | PASS | 49/49, clean no-cache run |
| M0-G3: NeoForge mod compiles and JAR contains required classes/metadata | PASS for build/content | Installed-JAR runtime not run |
| M0-G4: development client and dedicated server launch; singleplayer save/reload; server restart | PASS | Logs/screenshots retained; no mod registration/class-load error observed |
| M0-G5: Owner accepts scientific intake and Blender handoff convention | PENDING | No owner acceptance/model delivered in this round |
| M0 overall | **NOT COMPLETE** | G5 pending; no physics; owner acceptance required |

The one-line answer to “can Cold Atom Craft actually start in Minecraft 26.3 + NeoForge?” is: **the development client and dedicated server were observed to start and load the mod successfully with the pinned M0-R2 baseline; the packaged JAR was not separately installed/loaded, and M0 is not complete.**


## D5 OPT-001 optical reference tests (no MC physical effects)

- 18 new JUnit5-source analytic, numerical integration, q-matrix, cancellation and invalid-input tests in physics-core.
- Standalone `verification/OpticsSmoke.java` contains 21 checks, compiled by the Java25 `tools/verify-core.*` scripts.
- Independent Python standard-library 80-digit Decimal oracle: `python verification/M0-R2.1-D5/verify_optics_reference.py`. Expected 6 Gaussian × 4 + 3 ABCD × 2 + 1 lossless-lens plane × 6 = 36 checked scalar values.
- `verification/M0-R2.1-D5/run_d5_offline.sh` runs all available offline checks, which remain non-JUnit. `tools/verify_m0.py` now requires at least **171 formal JUnit5 XML test cases** (D4 153 + D5 18); a JDK21 offline reflection runner is not formal JUnit.
- OPT-001 is not wired into ModelCatalog or Minecraft world mutation. Hardware/waist geometry and paraxial domain certification are future science decisions.
- JDK25/Gradle/NeoForge and packaged D5 JAR/runtime checks are BLOCKED or NOT RUN on the current host; prior R2 launch logs do not validate D5.


## D6 optics numerical reference gates (2026-10-26)

- JUnit test **source** total: 194 methods (D5 baseline 171 + 22 D6).
  Executed via NON-JUNIT offline assertion shim: PASS 194/194.
- D6 dedicated smoke: PASS 19/19 on JDK21; independent Decimal(70) oracle:
  PASS 39/39 new scalar comparisons. D5 36/36 Decimal(80) reference retained.
- Pure Java `-Xlint:all --release 21`: PASS. Python engineering: 30/30 PASS.
- JDK 25 real JUnit and Gradle/NeoForge build for **this D6 source**:
  BLOCKED/NOT RUN here because JDK25/dependency network unavailable.
- D6 packaged JAR standalone install / actual game runtime: NOT RUN.
- Interferometer after full ABCD optical path, physical phase/coherence
  calibration, atomic optical potential, material energy uptake: NOT IMPLEMENTED.
- Phase tests include spatially varying counterpropagating Gaussian Gouy
  phase, orthogonal polarizations, retarder global phase, power/energy-density
  distinction and source/carrier-group mismatch rejection.
