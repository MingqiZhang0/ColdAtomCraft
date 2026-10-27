# Cold Atom Craft - Project Charter

**Record:** CAC-INIT-2026-10-25  
**Effective date:** 2026-10-25  
**Phase:** M0-R2 engineering audit (M0 not yet passed)  
**Document status:** baseline v0.2 (scientific models remain subject to owner approval)

## Vision

Build an extensible Minecraft experimental physics environment where player-assembled apparatus participates in the game world. Players create, calibrate, operate, diagnose, and measure physical experiments; they do not merely craft single-block machines and wait for timers.

## Non-negotiable principles

1. Owner controls project direction, scientific modeling and scientific claims; engineering implements and verifies approved definitions.
2. Owner creates Blender meshes, materials, model details and visual assets. Engineering integrates assets with proper anchors, animations and renderer choices.
3. Apparatus affects the Minecraft world and is affected by the world, using a server-authoritative shared physical state.
4. Model scope, physical validity, approximations, numerical error and unit conventions must be explicit.
5. Measured observables and internal simulation truth are separated. Debugging tools may show truth; normal instruments show what can be measured.
6. The physics core must compile without Minecraft/NeoForge imports and have independent verification tests.
7. Safe save/load, bounded processing, multiplayer consistency and reproducibility are first-class requirements.
8. Every phase is gated by tests. No successful build or simulation may be claimed without execution evidence.

## Ownership matrix

| Work | Deciding owner | Implementer |
| --- | --- | --- |
| Research direction and releases | Project owner | Engineering advises |
| Mathematical physics and boundary conditions | Project owner | Engineering validates and implements |
| Blender device design and original meshes | Project owner | Engineering imports/animates |
| API and software architecture | Owner approves | Engineering |
| Java, NeoForge, rendering and networking code | Owner reviews | Engineering |
| Numerical, regression and performance tests | Owner reviews | Engineering |
| License and public publication | Project owner | Engineering prepares |

## Platform baseline

- Minecraft Java Edition: `26.3`
- Mod loader: `NeoForge 26.3.0.99`, pinned by decision CAC-006
- Java toolchain: `25` (validated with Eclipse Temurin `25.0.3+9-LTS`)
- Gradle tooling: `ModDevGradle 2.0.148`, `Gradle 9.2.1` via checked-in wrapper
- Build pipeline: official `disableRecompilation = true` (precompiled NeoForge artifacts)
- Mod ID: `coldatomcraft`
- Working package: `dev.coldatomcraft`
- Project version: `0.0.1-dev` (not a release)
- Public license selection: owner decision pending; current required mod metadata reads `All Rights Reserved` and does not grant redistribution rights.

## Scientific development roadmap

- **M0**: engineering scaffold, clean source boundaries, input schemas, independent numerical test entrypoint, save and compatibility design.
- **M1 / v0.1 First Light**: lasers, geometrical path, optics parts, measurements, absorbed heating and permitted entity/world effects. Specific formulas require owner approval.
- **M2 / v0.2 MOT**: vacuum, atomic source, magnetic fields, laser cooling and fluorescence detection.
- **M3 / v0.3 BEC**: trapping, evaporative cooling, model-based condensate characterization and TOF imaging.
- **M4 / v0.4 Quantum Gases**: lattices and selected many-body models; fermionic and bosonic assumptions remain distinct.
- **M5 / v0.5 Neutral Atoms**: tweezer arrays, initialization, small-scale gates and measurement. BEC is not a prerequisite for this track.

## M0 exit criteria

- `physics-core` tests pass in JDK 25.
- `experiment-core` tests pass and do not depend on Minecraft.
- The NeoForge mod **builds** under the pinned environment.
- Client and dedicated-server launch are separately verified.
- Version metadata and asset specifications are documented.
- The owner accepts the first scientific-model intake contract.

**Current status:** M0-R2 has a passing JDK 25 Gradle build, **49/49 JUnit**, **46/46** standalone checks, **17/17** Python manifest tests, verified JAR contents, a clean development-client and dedicated-server launch, singleplayer world create/save/re-enter, and two dedicated-server restarts with graceful RCON stop. The packaged JAR was not separately installed in a production instance, no GameTestServer run is claimed, and no owner Blender asset or scientific model exists. M0-G5 and owner acceptance remain pending. M0 is **not complete**.

## Change protocol

Changes to platform baseline, interfaces, model assumptions, world-damage defaults, or asset coordinate conventions must be recorded in `docs/DECISIONS.md`. Owner sign-off is required for scientific-model changes. Failed tests, unimplemented features and blocked build checks remain visible.
