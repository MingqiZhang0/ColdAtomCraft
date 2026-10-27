# Cold Atom Craft — M0-R2 engineering audit

**Audit date:** 2026-10-26 (America/Denver)  
**Task:** CAC-M0-R2  
**Technical reviewer:** ChatGPT  
**Implementation:** DeepSeek  
**Owner approval of scientific models:** not granted in this round  
**M0 overall status:** **NOT COMPLETE**

## 1. Direct answer

**Can Cold Atom Craft actually start in Minecraft 26.3 + NeoForge?**

**Yes for the tested development environment:** the NeoForge development client and dedicated server were both started successfully, loaded `Cold Atom Craft 0.0.1-dev` alongside Minecraft `26.3` and NeoForge `26.3.0.99`, and completed clean shutdowns. A singleplayer world was created, saved, exited and re-entered. The dedicated server was restarted twice and stopped gracefully through RCON.

**Not yet proven:** installation and launch of the packaged JAR in a separate production Minecraft/NeoForge instance. Development runs load the compiled source sets, not the released JAR. JAR **content** is verified, but installed-JAR runtime is **NOT RUN**.

**M0 is not accepted:** Owner scientific approval (M0-G5), actual Blender owner assets, actual laser physics, and production-install verification remain outstanding. No world-damage or heating model is implemented.

## 2. Baseline and versions

| Component | M0-R1 | M0-R2 final | Evidence |
| --- | --- | --- | --- |
| Minecraft | 26.3 | 26.3 | `gradle.properties` |
| NeoForge | 26.3.0.84 (provisional) | **26.3.0.99** | client/server log mod list; Maven availability |
| Java | 25 target | **Temurin 25.0.3+9-LTS** | `verification/M0-R2/environment.txt` |
| Gradle | 9.2.1, wrapper missing | **9.2.1, complete wrapper** | `gradle/README.md` |
| ModDevGradle | 2.0.148 | 2.0.148 | `neoforge-mod/build.gradle` |
| Build path | default decompile/recompile | **official `disableRecompilation = true`** | `neoforge-mod/build.gradle` |
| OS | not executed | Windows 11 amd64 | environment log |

`disableRecompilation` does **not** disable compilation of the mod itself. All project sources are compiled by javac 25; only Minecraft's local decompile/recompile stage is skipped in favor of NeoForge's precompiled development artifacts.

## 3. P0 — build path isolation and outcome

### 3.1 Initial failure

The first real Gradle build resolved NeoForge and executed `physics-core`/`experiment-core` JUnit successfully, then failed in `:neoforge-mod:createMinecraftArtifacts` during NeoFormRuntime recompilation:

```text
ERROR Line: 1, ..net.minecraft.world.waypoints.package-info...: null
in /net/minecraft/world/waypoints/package-info.java
```

A diagnostic probe later showed the underlying diagnostic:

```text
compiler.err.class.cant.write
error while writing net.minecraft.world.waypoints.package-info: null
```

This occurred while writing the generated class into NFRT's JAR output filesystem. It was reproduced from the exact transformed source/classpath in a standalone JavaCompiler probe, but **manual javac results are retained only as an investigation lead, not as proof of a NeoForge API defect**.

### 3.2 Version attempts (recorded, not hidden)

| Attempt | Result | Disposition |
| --- | --- | --- |
| NeoForge 26.3.0.84 + default recompile | FAIL (NFRT package-info write) | Superseded |
| NeoForge 26.3.0.98 + default recompile | FAIL (same NFRT issue) | Superseded |
| NeoForge 26.3.0.99 + default recompile | FAIL (same NFRT issue) | Superseded for build; .99 retained as runtime version |
| NeoForge 26.3.0.84 + ECJ | FAIL (ECJ treated flexible constructor bodies as preview) | Diagnostic only |
| NeoForge 26.3.0.99 + `disableRecompilation` | **PASS** | Final |
| Official MDK control, 26.3.0.99 + `disableRecompilation` | **PASS** | Control experiment |
| JDK 25.0.4.1 + default recompile | FAIL (same NFRT issue) | Not used for final |

The failed-attempt logs are retained in `verification/M0-R2/logs/`:

- `build-attempt-26.3.0.84-nfrt-fail.log`
- `build-attempt-ecj-fail.log`
- `build-attempt-26.3.0.98-nfrt-fail.log`
- `build-attempt-26.3.0.99-nfrt-fail.log`
- `build-attempt-jdk25.0.4.1-nfrt-fail.log`
- `build-attempt-verbose-diagnostic.log`
- `probe-package-info-write-fail.log`
 The final deliverable does not depend on the default recompile path.

### 3.3 Official MDK control

A pristine copy of `NeoForgeMDKs/MDK-26.3-ModDevGradle` (commit `543f871769bba1b5fcd138359e80f74739442167`) was configured with the same:

- NeoForge `26.3.0.99`
- JDK `25.0.3`
- Gradle `9.2.1` (official MDK wrapper)
- `enable { version = ...; disableRecompilation = true }`

Result: `BUILD SUCCESSFUL`. Log: `verification/M0-R2/logs/mdk-control.log`.

This establishes that the project configuration is compatible with the chosen baseline; the default NFRT recompilation failure is an environment/tooling problem rather than a project source incompatibility.

### 3.4 Final build

Command (no build cache, all tasks rerun):

```text
.\gradlew.bat clean :physics-core:test :experiment-core:test :neoforge-mod:build \
  --no-build-cache --rerun-tasks --no-daemon --console=plain --stacktrace
```

Result: **BUILD SUCCESSFUL — 18/18 tasks executed**; `verifyDistributableJar` passed.  
Log: [`build-final.log`](../verification/M0-R2/logs/build-final.log).

### 3.5 JAR content

Artifact: [`coldatomcraft-0.0.1-dev.jar`](../verification/M0-R2/artifacts/coldatomcraft-0.0.1-dev.jar)  
SHA-256: `4eba8c7438d17824aceda415dbac6785ce3368304e7d7ec6ad82d800b0a6961c`  
Size: 45,387 bytes

Checked entries include:

- `dev/coldatomcraft/neoforge/ColdAtomCraft.class`
- `dev/coldatomcraft/physics/Vec3.class`
- `dev/coldatomcraft/physics/BeamInputs.class`
- `dev/coldatomcraft/experiment/ModelDefinition.class`
- `dev/coldatomcraft/experiment/world/EnergyTransferCandidate.class`
- `dev/coldatomcraft/experiment/world/WorldEffectGate.class`
- `dev/coldatomcraft/experiment/world/DevicePlacement.class`
- `META-INF/neoforge.mods.toml`

Metadata declares `coldatomcraft`, version `0.0.1-dev`, required NeoForge range `[26.3.0.99,)` and Minecraft `[26.3]`. Full listing: [`jar-contents.txt`](../verification/M0-R2/artifacts/jar-contents.txt).

## 4. P0 — tests

| Test group | Result | Notes |
| --- | --- | --- |
| `physics-core` JUnit | PASS 6/6 | `Vec3Test` 4, `BeamInputsTest` 2 |
| `experiment-core` JUnit | PASS 43/43 | includes 31 world-contract tests |
| Total JUnit | **PASS 49/49** | 0 failures, 0 errors, 0 skipped |
| Standalone smoke | PASS 46/46 | no Minecraft classpath, Java 25 `--release 25` |
| Python asset tests | PASS 17/17 | schema, paths, GLB structure |
| Example manifest | PASS as SPEC ONLY | missing owner assets intentional |

JUnit XML is archived in [`verification/M0-R2/junit/`](../verification/M0-R2/junit/).  
Build log: [`build-final.log`](../verification/M0-R2/logs/build-final.log).  
Smoke log: [`smoke.log`](../verification/M0-R2/logs/smoke.log).  
Python log: [`python-tests.log`](../verification/M0-R2/logs/python-tests.log).

## 5. P2 — real runtime verification

### 5.1 Development client

- Launched `:neoforge-mod:runClient` with JDK 25.0.3.
- Window title: `Minecraft NeoForge* 26.3`.
- Mod list: Cold Atom Craft `0.0.1-dev`, Minecraft `26.3`, NeoForge `26.3.0.99`.
- OpenGL/SDL window, resource reload, sound engine and texture atlases initialized.
- No class-load or mod-registration exception attributable to Cold Atom Craft.
- Graceful close: `Stopping!`, FML loader closed, client exit code **0**.
- Evidence: [`client.log`](../verification/M0-R2/logs/client.log), screenshots 01–02.

### 5.2 Singleplayer create / save / exit / re-enter

- Created world `New World`; integrated server logged `Starting integrated minecraft server version 26.3`, loaded spawn chunks and logged `Dev[...] logged in`.
- Exited via window close; logs show players/worlds saved for overworld, nether and end, then `Stopping!`.
- Relaunched with optional run property `-PcacQuickPlayWorld="New World"`; server loaded the existing world (`Loading 49 chunks`), player joined, same world rendered.
- Second exit also saved all three dimensions and exited with code 0.
- Evidence: [`client.log`](../verification/M0-R2/logs/client.log), [`client-reenter.log`](../verification/M0-R2/logs/client-reenter.log), screenshots 03–07.

This is an actual save/reload test, not a simulated one. The optional quick-play argument only selects the already-created world; it is not required for normal client launch.

### 5.3 Dedicated server

- `:neoforge-mod:runServer` started a **DEDICATED_SERVER** environment.
- Server mod list included Cold Atom Craft `0.0.1-dev`, Minecraft `26.3`, NeoForge `26.3.0.99`.
- `Done (4.757s)` on first boot; `Done (0.365s)` after restart.
- RCON enabled locally only for deterministic graceful stop. `stop` triggered `Stopping server`, `Saving players`, `Saving worlds`, saves for overworld/nether/end, then `BUILD SUCCESSFUL` (exit 0).
- Two controlled restarts/stops passed. An earlier first boot was force-terminated **only** to reconfigure the local RCON test settings; that forced teardown is not counted as a graceful-stop pass.
- The development server did **not** create an `eula.txt` or stop for EULA; no EULA acceptance was written. A normal production server may still require owner acceptance.
- Evidence: [`server-first-boot-forced.log`](../verification/M0-R2/logs/server-first-boot-forced.log), [`server-run1.log`](../verification/M0-R2/logs/server-run1.log), [`server-run2.log`](../verification/M0-R2/logs/server-run2.log).

### 5.4 Runtime limitations / warnings

Observed non-mod warnings include:

- `WMIQueryHandler` COM access denied for host hardware queries (sandbox restriction).
- Missing chat services public key for offline dev player (expected offline-mode).
- `Requested post effect does not exist: minecraft:end_of_frame` (NeoForge/client warning; not a Cold Atom Craft class/registration error).
- Gradle file-watcher native exception under the sandbox; build still completed successfully.
- First dedicated boot logged a missing `server.properties` and generated defaults; subsequent boots loaded properties normally.

No Cold Atom Craft registration, class-loading, side-only or initialization exception was found in the client/server logs.

## 6. P1 — world interaction contract review

Implemented in `experiment-core` with no Minecraft dependency:

- `WorldEffectGate.precheck` checks model ID/revision, owner approval, numerical verification, full read set, current device snapshot and current target snapshot. It has no apply/mutate method; a structural test enforces this.
- `EnergyTransferCandidate` carries a complete `CalculationStep`, unique event index, device/target snapshots and full `WorldReadSet`. Duplicate identity is `(model, revision, run, step, event)` and excludes target/payload so a duplicate cannot be made “new” by editing its payload.
- `WorldEffectLedger.beginCalculation` rejects duplicate steps and overlapping intervals within a run. `claim` requires an allowed matching precheck and a registered step; `markApplied`/`markFailed` make the outcome terminal.
- A crash between claim and outcome leaves the event `CLAIMED` and unclaimable; this fails closed (possible lost effect, not double effect).
- **Cross-restart idempotency is explicitly not guaranteed.** A new process has no durable claim table; the limitation is both documented and tested.
- `WorldRevision` includes `loadEpoch`, `contentRevision` and `loaded`; `WorldReadSet` permits only loaded, single-dimension, duplicate-free chunks.
- `ServerEffectGate`/`ServerMutationFacts` define the final server-thread checks (target identity, position, load state, permission). The actual NeoForge adapter is **NOT IMPLEMENTED**.
- `ModelCatalog` is immutable and server-bootstrap populated; `ModelReference` is the only model type allowed across network/save boundaries and contains no approval fields. Owner approval and numerical verification remain independent.
- No production approved model exists. Approved model definitions occur only in synthetic tests.

Tests added/updated: 49 JUnit total, including concurrent exactly-once claim, changed payload/target duplicate, terminal failure, restart limitation, unloaded chunks, stale revisions, precheck/no-mutation API, model catalog/reference boundaries, and GLB/Blender coordinate equivalence.

## 7. P2/D — Blender asset interface

- Retained Blender `+Z` up / `-Y` forward and four cardinal transforms.
- Added explicit glTF interchange axes `+Y` up / `+Z` forward and `DevicePlacement.blenderToGltf`, `gltfPointToWorldBlocks`, `gltfDirectionToWorld`.
- Tests prove Blender-space and glTF-space conversion agree for all four facings and that the glTF forward `+Z` maps correctly.
- Validator now checks anchor prefix/role consistency, unit `forwardVectorLocal`, axis/vector agreement, ready-state GLB axes, `.blend` header structure and GLB chunk structure.
- Exporter writes `interchangeAxes`, requires a laser output anchor for `laser_source`, and refuses to write a manifest without a nonempty GLB.

**Evidence levels:**

1. Manifest/schema: **PASS as SPEC ONLY**.
2. Actual Blender export: **NOT RUN** — no Blender executable or owner `.blend`.
3. Minecraft rendering/integration acceptance: **NOT RUN** — no renderer, no model, no in-game visual acceptance.

No owner model was delivered; no model file or rendering claim is fabricated.

## 8. Deliverable status matrix

| Item | Status | Notes |
| --- | --- | --- |
| Full modified source | PASS | `ColdAtomCraft-M0-R2/` |
| Source ZIP | PASS | `ColdAtomCraft-M0-R2-Source.zip` |
| R1 → R2 file change list | PASS | `docs/CHANGED_FILES.md` |
| Gradle/JDK versions | PASS | `verification/M0-R2/environment.txt` |
| Formal compile/JUnit | PASS | 49/49, no-cache build |
| Client/server launch logs | PASS | retained |
| Singleplayer create/save/re-enter | PASS | retained |
| Dedicated server restart/stop | PASS | RCON graceful stop; two runs |
| JAR + content check | PASS | content verified; installed-JAR runtime NOT RUN |
| World-contract tests | PASS | 31 tests in experiment-core |
| All unfinished tests and reasons | PASS | this report + `TEST_PLAN.md` |
| SHA-256 manifest | PASS | `MANIFEST_SHA256.txt` |
| M0-R2 audit report | PASS | this document |
| Owner M0 acceptance | NOT RUN / PENDING | Owner authority |
| Blender export/in-game rendering | NOT RUN | no owner asset/Blender |
| Production-installed JAR runtime | NOT RUN | dev source-set runs only |
| GameTestServer | NOT RUN | no GameTests; not counted as pass |
| CI runner execution | NOT RUN | workflow repaired/static only |
| Remote push/release | NOT RUN | prohibited without authorization |

## 9. Unresolved / next actions

1. Owner sign-off of M0-G5 scientific intake and asset handoff convention.
2. Owner provides a real `.blend`/GLB; perform actual Blender export and Minecraft renderer integration.
3. Install the packaged JAR in a clean production NeoForge client/server and verify loader/runtime behavior.
4. Implement a real NeoForge snapshot/chunk-change tracker and server mutation adapter; current revision data is not a world-change tracker.
5. Implement or explicitly accept the durable, cross-restart ledger design before enabling any world effect.
6. Run CI on an actual GitHub runner.
7. Add real GameTests only when meaningful server-side tests exist; do not count a no-test GameTestServer exit.
8. Only after separate owner-approved OPT-001/THM-001/BIO-001 models: add physics and any gameplay effects.

## 10. Compliance statement

- No laser propagation, absorption, heating or biological damage formula was added.
- No model was marked owner-approved.
- No unsupported assertion was deleted to make tests pass.
- No simulated output was substituted for Gradle/JUnit/runtime results.
- No client-only dependency was introduced into `physics-core` or `experiment-core`.
- No unnecessary third-party mod dependency was added.
- No remote push or release was performed.
- M0 is **not** declared complete.
