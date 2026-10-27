# CAC M0-R2.1-D14: buildable 3D diagnostic optical bench

## Status and exact scientific scope

This is a **development source checkpoint**, not an M0/M1 release and not an
Owner-approved calibrated optical instrument. The source archive includes a
shared, dependency-free Java optical-network reference and a provisional
Minecraft/NeoForge adapter. D14 Java 25/NeoForge build and live client/server
playtest **have not been performed for this source**. The latest historical
GitHub D12 CI result applies only to D12. No repository push is authorized.

The scene consists of a bounded network of axis-aligned, thin, ideal components:

| Diagnostic apparatus | Input and effect | Configuration |
| --- | --- | --- |
| Source (D12) | 1-200 mW, 400-1100 nm, TEM00 Gaussian | GUI controls, horizontal output |
| Planar mirror | Lossless 90-degree change in travel direction | One of 6 incoming and 4 orthogonal output directions |
| 50:50 splitter | Half of input watts straight, half reflected 90 degrees | Same port settings as mirror |
| Ideal thin lens | `q_out = q_in / (1 - q_in/f)` and unchanged source watts | `f = +0.5, +1, +2, -1` m |
| Half-wave plate | Jones transformation of normalized polarization | Axis 0, 22.5, 45, 67.5 degrees |
| Ideal power meter | One face-oriented circular aperture | Three radii: 0.5, 1, 2 mm; 6 face directions |

Each normal optical component is contained in one Minecraft block, with
one-dimensional port precision fixed by the diagnostic fixture. The component
geometry is **not** a physical finite beam-intercept or Fresnel/coating model.
A Minecraft block is provisionally one metre; the emitter and receiver faces
are at 7/16 metre from their respective block centres. This is a game-lab
coordinate convention, not an atomic microscopic lattice constant.

Input port `incoming` is **the direction of photon travel approaching the block**,
not the outward-looking surface normal or a source-facing arrow. The port is
**one-way** in this development fixture. Other incident directions are opaque.

## Player interaction, including vertical routes

1. In Minecraft 26.3 + NeoForge, open the creative `CAC Diagnostic Lab (DEV)` tab.
   Place the diagnostic laser, mirror, splitter, lens, half-wave plate and meter.
   The models are original-project placeholder cubes, **not** the Owner's Blender
   assets. There is no Create dependency, manufacturing tree, FE energy use or
   power connector in this checkpoint.
2. Right-click a laser to open its existing D12 control GUI. Use ON/OFF, four
   parameters and 90-degree horizontal direction. It cannot emit vertically.
3. Right-click any optical component to advance one of four modes. While
   sneaking, right-click to advance the incoming propagation direction through
   NORTH, EAST, SOUTH, WEST, UP, DOWN. Its overlay indicates the current input
   travel and either outgoing travel, focal length or half-wave plate angle.
4. Right-click a meter to read the server-calculated, ideal collected power.
   Sneak-right-click the meter to cycle its circular aperture radius. Meter
   orientation is selected by how it is placed; use a test scaffold to orient
   the meter downward for an upward-moving beam.
5. For a vertical mirror test, use a laser at `(0,64,0)` aimed EAST; mirror
   `(4,64,0)` configured incoming EAST, output UP; meter `(4,67,0)` with
   its face pointing DOWN. Before installing the mirror, verify that the beam
   reaches a straight EAST-facing meter. Remove obstructing blocks along the
   planned paths. Expect one vertical branch after the mirror.
6. For a splitter test, aim the same laser EAST at `(3,64,0)` splitter, input
   EAST/output SOUTH; place one WEST-facing meter farther EAST, and another
   NORTH-facing meter farther SOUTH. Lossless output powers should each be
   half of the incident total watts before any finite-aperture clipping.
7. Place a lens or half-wave plate in an otherwise unobstructed straight route.
   The beam display recomputes Gaussian width; the meter uses the same optical
   `q` and aperture integral. Waveplate polarization changes do not yet drive
   a polarization-sensitive detector, so they will not alter its ideal reading.
8. Save/re-enter the world to verify laser BlockEntity data, optics' 24-state
   blockstates, detector orientation and aperture presets. Repeat on a dedicated
   server with two clients. These are **required manual checks, not passed tests**.

## Numerical model and conservation

The internal mathematical state is a Gaussian complex beam parameter `q`,
unit-norm Jones polarization vector, wavelength in metres and separate optical
power in watts. The solver uses D5/D6/D9 scientific functions:

- Free space: ABCD `q -> q + L` and total optical watts unchanged.
- Thin lens: `q -> q / (1 - q/f)` with mode/intensity changing but not energy.
- HWP: ideal Jones matrix with unit polarization norm and watts conserved.
- Mirror: an ideal 90-degree Householder transport preserving Jones norm and
  optical watts; coating-dependent s/p phases are intentionally NOT modeled.
- 50:50 splitter: straight branch `P/2`, reflected branch `P/2`, with one
  conventional relative +pi/2 field phase. **Coherent recombination is NOT
  computed**, even though each branch retains a complex Jones amplitude.
- Detector: D9's ideal centered Gaussian circular aperture capture at the
  arrival spot size, after propagation through any preceding lens. Captured
  power is an observation, not power removed from world energy inventories.

A detector reading is VALID only if exactly one branch reaches its correct
input face, the trace is complete and the source is included in the bounded
local discovery. Two or more rays reaching the same face return
`MULTIPLE_UNRESOLVED`, not a fabricated sum; loops and safety truncation
return `INCOMPLETE`, not a valid power reading.

The component solver is independent of Minecraft. Both the client visualization
and authoritative read-only server power meter instantiate the **same solver**
from their respective currently loaded world blockstates. They are separate
world snapshots, not a protocol guaranteeing an exact time-synchronized image.
Client dust particles use false colour, exaggerated radius and a shared
1200-particle/4-tick global budget; near-IR is not claimed visible in clear air.

## Safety bounds and known limitations

- At most 24 scanned cell centres per straight leg, 160 total segments,
  16 optical components per branch and 512 sources. A geometric loop is
  detected; overflow leads to an incomplete trace. These are engine safety
  limits, NOT physical absorption or experimental coherence times.
- Client source discovery: 16-block horizontal radius, plus/minus 5 Y;
  server meter source discovery: 24-block horizontal radius, plus/minus 12 Y.
  Long reflected routes or sources outside the search may be missed.
- Source discovery scans nearby blocks; it is deliberately not a scalable
  optical-source registry. Server meter queries can incur a large scan and
  **must be profiled/rate-limited before public multiplayer release**.
- All other mod blocks, wrongly oriented optical ports, opaque blocks and
  unloaded chunks terminate the beam. No chunk force loading.
- Sources in a common overlapping region are not guaranteed to form coherent
  optical modes, and the current geometry does NOT calculate arbitrary phase
  delay, beam overlap, finite optic aperture or multiple-beam interference.
- Gaussian reference is paraxial TEM00 only. Large-NA focused beams, clipping,
  aberration, real polarization coating phases and spectral bandwidth are
  beyond this model. The 1 nm GUI resolution is not suitable for MHz-scale
  MOT laser detuning; a separate frequency-referenced optical controller is
  required before atomic cooling work.
- Laser power does not heat blocks, hurt entities, draw electrical energy,
  couple to real vacuum/MOT systems, or control experimental timing. All
  components are stand-ins requiring approval of physical assumptions.
- Invalid stored laser settings fail closed on the source search (rather than
  silently creating an energized 20 mW default); a user may re-apply a valid
  setting in the GUI. Old D12 saves without the new validity marker default
  to valid when all existing parameters pass validation. Destructive migration
  is not attempted.
- The original D10 source and meter IDs are unchanged. Detector's facing
  property expands from horizontal to six directions. Need an actual save/load
  compatibility test on a backed-up copy of an old world.

## Verification matrix

| Gate | Required evidence | D14 checkpoint status |
| --- | --- | --- |
| Pure Java | Independent 3D reference smoke, baseline parity, lossless 50:50, lens, HWP, loop, malformed values | PASS (run `python tools/check_d14_optics.py`) |
| Existing engineering | Offline pure-core and asset/contract regression scripts | PASS for executed tests, see D14 verification README |
| JUnit 5, Java 25 | `:physics-core:test :experiment-core:test` and XML for every test class | NOT RUN for D14 |
| NeoForge build | `:neoforge-mod:build` and verified distributable class/resource inventory | NOT RUN for D14 |
| Minecraft GUI/visuals | Six-direction optics, all models visible, power parity, save/reload | NOT RUN |
| Multiplayer/performance | Dedicated server two players, TPS/FPS, 4/8/16/32 beams | NOT RUN |
| Scientific Owner approval | Physical assumptions, model applicability, acceptance tolerances | NOT GRANTED |

Execute on a prepared official Java 25 / NeoForge host:

```sh
python tools/generate_preview_assets.py
python tools/check_d14_optics.py
python tools/check_multi_beam.py
python tools/check_source_manifest.py
python -m unittest discover -s verification -p 'test_*.py' -v
./gradlew clean :physics-core:test :experiment-core:test :neoforge-mod:build :neoforge-mod:verifyDistributableJar --no-daemon --stacktrace
python tools/inspect_mod_jar.py --root . --jar neoforge-mod/build/libs/coldatomcraft-0.0.6-dev-optical-bench.jar
```

Then play-test the JAR and both client/server logs. The command list is an
acceptance procedure; it is not an assertion that these final build and game
checks have already succeeded.
