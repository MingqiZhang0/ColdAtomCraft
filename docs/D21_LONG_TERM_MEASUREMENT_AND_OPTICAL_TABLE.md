# CAC D21A — Long-term power recording and modular optical breadboard (DEV)

## Purpose and status

This is a **development diagnostic**, not a calibrated laboratory instrument,
not a physical motor model, and not M1/Owner scientific acceptance. It builds on
D14's read-only server-side optical-network reference, while preserving the
original detector's right-click measurement and sneak-click aperture controls.

The D21 devices are visible *Minecraft-native cuboid placeholders*; the
project Owner remains responsible for future Blender designs and calibrated
geometries. No new world-damage or automatic energy-supply capability is added.

## Playtest: long-term power measurement

1. In the creative `CAC Diagnostic Lab (DEV)` tab place a diagnostic laser and
   power meter in a valid optical route. Verify the meter's original one-shot
   reading using a normal right-click.
2. Place the `Power Data Logger` adjacent to exactly one meter (any of the six
   adjacent faces). Right-click the **logger**, not the meter, to begin recording.
3. Data logger samples approximately every **100 server game ticks**, nominally
   5 seconds at 20 TPS. Each individual logger has a deterministic position-
   staggered phase. Logging starts only by explicit player action; it never
   resumes automatically after a server restart or chunk reload.
4. The logger writes append-only CSV under the **server world save directory**:
   `coldatomcraft/measurements/<recorder identity>.csv`. CSV columns:
   `server_game_tick,status,captured_watts`.
5. Missing input meters, multiple adjacent meters, no laser, multi-beam
   interference, adjacent unloaded chunks and incomplete traces are **not** fabricated as zero-watt
   measurements. Their CSV rows contain a quality/status and an empty numeric
   field. A valid zero-watt result is `MEASURED,0.0`.
6. Right-click the logger again to pause. Sneak-right-click exports a standalone
   `*.svg` plot for the **latest 512 sampled points**; any invalid readings
   remain visible as gaps, including logging pauses longer than 100 ticks.
   The CSV retains the longer history up to a hard
   limit of **64 MiB per logger**, after which recording pauses.
7. To view the generated SVG/CSV, open the server save directory in an external
   spreadsheet or browser. On a **dedicated server**, these files reside on
   the server machine: players do **not** receive them automatically through
   a secure download or in-game chart interface. A future authenticated
   transfer and graph GUI is a separate engineering phase.
8. Test a missing meter, two adjacent meters, blocked beam, laser shutter
   toggle, chunk unloading, save/reload and a temporarily unavailable server
   disk. The saved preview is a bounded **512-point ring** while CSV is the
   longer-term archive.

### Time and accuracy

`server_game_tick` measures simulation scheduling, **not** wall-clock seconds.
At 20 TPS the nominal duration is `ticks/20`, but server stalls, restarts,
pauses and TPS variations change real elapsed time. No wall-clock timestamp,
calibrated responsivity/noise model, oscilloscope bandwidth or anti-aliasing
claim is made. Optical values are ideal D14 collected watts from the server
snapshot and inherit D14's bounded source-search/multipath limitations.
Data persistence uses ordinary BlockEntity saving for the recent window and
server-local append-only CSV for the long history. Append-only does **not**
mean crash-transactional: an interruption between the CSV append and chunk save
can make the last rows differ from the saved ring. The CSV is the archive of
record, and applications must retain duplicate/discontinuity metadata.

### Performance and security bounds

Sampling is disabled by default and staggered in time, and the per-device CSV
file is capped. However, D14's source search can still scan many block cells;
for dozens of active loggers, merge the D16 sparse-source optimization and
profile MSPT. Multiuser access control, long-term storage quotas across a
whole server, asynchronous batched writes, retention policies, and secure
file download are intentionally **not implemented** in D21A.

## Playtest: optical breadboard and manual stages

1. Place `Optical Breadboard` blocks to make the work surface. A single
   breadboard block still occupies a full Minecraft block; the top grid is
   **visual only**, not a millimeter pitch specification.
2. Put two `Manual Linear Rail` blocks next to each other **on top** of
   optical breadboard blocks, with their rail axes aligned. Mount a D14
   mirror/splitter/lens/half-wave plate in the block directly above the
   first rail. Right-click that rail to translate the optic **one whole block**
   along its stored facing. Sneak-right-click moves one block backwards.
   The destination must be loaded, empty and supported by an aligned rail
   on an optical breadboard. The rail itself does not move.
3. To rotate an optic, place a `Manual Rotary Mount` on the breadboard and a
   D14 optic above it. Right-click mount to rotate incoming direction 90 deg
   clockwise, sneak-right-click to reverse. The original selector is kept.
   This supports horizontal incident beams only; vertical incidence and
   arbitrary rotations need a more complete geometry model.
4. Verify the **actual beam path and detector output** after moving/turning:
   these stages modify real world block positions/blockstates, not just a
   visual model. They cannot translate existing laser or meter BlockEntities.

Neither stage is powered, nor is either a physical electric motor. A real
Create-backed gearmotor, micro-positioner, encoder, speed/acceleration,
backlash, swept-volume collision, transactional block movement and SI lab
transform remain separate work. The stage is a bounded game-world manual
prototype, not a nanometer-precision alignment device.

## Next phase proposal

- **D21B:** in-game graph screen, paging/export for client downloads, channel
  identity, consistent clock labels, and capture sampling with a controlled
  server-wide MSPT budget.
- **D22:** platform assembly and stable support geometry, device mounts,
  3-axis translation/rotation in SI coordinates, continuous beam geometry;
  stage hardware with Create when matching Minecraft/NeoForge versions exist.
- **D23:** server-authenticated experiment automation coupling stage motion,
  calibrated sensor, timing D17–D20 and energy ledger. No automatic laser ON
  from D21 alone.

## Verification boundary

This source adds JUnit5 measurement tests, a deterministic independent Java
smoke, Minecraft-specific static integration guards, asset validation, and JAR
inventory requirements. Passing those does **not** establish a Java25/NeoForge
compile, a live client/server launch or a gameplay report; the formal GitHub
Actions build and manual Minecraft acceptance are separate gates.
