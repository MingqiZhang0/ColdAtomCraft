# CAC D11 — visible diagnostic beam director audit

## User request

After the D10 placeholder-laser facing fix, add a real in-game **visible laser path**, so interactions can be tested without Blender assets. Keep the research physics honest.

## Implemented

- Pure-Java, deterministic source->forward air-only beam segment tracing (`PreviewBeamDisplay`), with a 24-block maximum and distinct terminal states (receiver / solid obstruction / unloaded / limit).
- Reuse D5 Gaussian `w(z)` numerics and D10 demonstration parameters; do not re-derive a separate physics law.
- CLIENT-only `DiagnosticBeamParticles` listens to `ClientTickEvent.Post`, reads synchronized laser enable/facing state, draws coloured `DustParticleOptions` along the route.
- Particles have a thin axis and sparse off-axis Gaussian inverse-CDF sampling; the picture exaggerates the physical beam diameter and is NOT a calibrated instrument image.
- Bound local work: every four ticks, radius 16 blocks horizontally/5 vertically, max 3 displayed lasers, no chunk loading.
- Existing D9 power meter remains server-only and retains original aperture-based readings; no world effects.
- New JUnit source tests, independent Java21 smoke test, client hook static checks, Gradle JAR checks and GitHub CI wiring.
- `gradle.properties` mod version is incremented to `0.0.3-dev-beam-preview` so a player can distinguish this from the D10 preview JAR.

## Evidence collected in this container

- D11 read-only path and optics smoke: **278/278 PASS** under JDK21 (`verification/D11-visible-beam/logs/geometry-smoke.log`). This is not JUnit.
- Existing D10 measurement route and asset tests: **52/52 PASS + 8 asset tests PASS** under JDK21 (`verification/D11-visible-beam/logs/d10-regression.log`).
- `python -m unittest discover ... test_*.py`: **38/38 PASS**, including 8 new static client-registration/JAR-guard checks. Static checks are not the same as actual NeoForge compilation (`verification/D11-visible-beam/logs/python-regression.log`).
- Interface names `ClientTickEvent.Post`, `@EventBusSubscriber`, `DustParticleOptions(int,float)`, `ClientLevel.addParticle` and `MutableBlockPos.setY` were cross-referenced against 26.3 API docs, but **they have not been compiled against the actual 26.3 NeoForge libraries in this environment**.

## Not verified; must NOT be claimed

- JDK25 Gradle compilation of D11, and JUnit XML results after D11 changes: **NOT RUN**.
- NeoForge D11 actual 26.3 client and dedicated-server load: **NOT RUN**.
- Screenshot, smoothness/particles setting, GPU/CPU impact, multiplayer local observation: **NOT RUN**.
- Optical radiation, scattering in air, photonic particle dynamics, source spectral transfer, mirror reflection, actual radiation/material interaction: **NOT IMPLEMENTED**.

## Runtime acceptance

Use Minecraft Java 26.3 + NeoForge 26.3.0.99. Download the successful GitHub Actions artifact named `CAC-M0-R2.1-D11-visible-beam-neoforge` and extract the JAR. Put it into `mods` after removing the old D10 JAR (do not leave both versions). Test laser on/off, four rotation states, solid obstruction, correct/wrong-oriented receiver, save/reload and particle settings. Run a separate dedicated server compatibility check.

## Rendering alternatives and future hardening

This D11 component deliberately uses built-in particles to avoid unreleasable custom renderer code for the first interactive preview. For a smooth continuous volumetric beam and instant disappearance on OFF, replace the visual-only particle renderer with a client-side mesh/ribbon shader after the JDK25 build and playtest. Any new renderer must keep the same tracing/physics contracts and must not treat the false-colour width as a measurement.
