# D13 — More than three simultaneously visible diagnostic beams

## Bug confirmed from a live D12 report

D12's client `DiagnosticBeamParticles` had a hard `MAX_VISIBLE_LASERS = 3` limit. Its
world scan stopped after the third laser, while `DiagnosticBeamScan` on the server
continued measuring every correctly connected laser. The disappearance was thus
visual, not a loss of optical power. This fix leaves all measurement, physics,
menu, persisted settings and block IDs unchanged.

## Client display budget

- The same nearby search area is used: 16-block horizontal radius, +/-5 Y blocks.
- Refresh every 4 client ticks, and do not force-load chunks.
- Collect ALL eligible enabled sources in the search area first; never stop
  scanning after the third source.
- Allocate **at most 1200 dust particles per refresh** across the valid traces.
  At normal four-laser setups with 24-block paths and 0.20-block spacing,
  each beam keeps its full sampling density: 4 * (121 core + 41 halo) = 648.
- At heavier counts automatically increase the sample spacing for all beams to
  keep the global particle cost bounded. The sampling/halo allocation is
  pure-Java and independently unit tested.
- In an extreme scene with more than 400 active visible traces, where even
  3 particles per source cannot fit, use rotating source slots. Such sources
  are time-sliced (temporarily blinking), not permanently excluded by scan order.
- 24-block propagation cap, beam-port offset, collisions, receivers and Gaussian
  radius/false colour are unchanged. Colour and visual radius are **never**
  optical measurements.
- Normal vanilla Minecraft particle settings, frustum/distance culling and
  global particle caps can further reduce visible particles; the renderer
  cannot guarantee all dust is drawn when global particle limits intervene.

## Acceptance

1. Verify GitHub Actions success for Minecraft 26.3 + NeoForge 26.3.0.99,
   Java 25; download `CAC-M0-R2.1-D13-multi-beam-neoforge` artifact and ensure
   only one `coldatomcraft` JAR exists in `mods`.
2. Put four lasers within the scan area; switch all ON with the GUI. All four
   beams should be visible simultaneously. A correctly positioned detector
   should still measure each beam as before.
3. Try eight, sixteen, then 32 lasers and compare FPS and beam continuity.
   Particle setting `All` is recommended for debugging.
4. Place a blocking stone and verify the beam stops at it. Move the receiver
   and vary power, wavelength, waist to ensure D12 settings still work.
5. Reload the world and verify laser settings have not been reset.

## Verification boundaries

Pure-Java/JDK21 test (also available as `python tools/check_multi_beam.py`) and Python contract test cover deterministic budget policy
and wiring. A successful JDK25 NeoForge build and Minecraft playtest are still
required before making runtime or performance claims.
