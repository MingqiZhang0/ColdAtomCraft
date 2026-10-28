# CAC D21D — Two-channel optical observation comparison (development checkpoint)

**Status:** Source implementation; Java 21 offline numerical checks, mock GUI/menu
compilation, and Python contracts are **not** formal Java 25 / NeoForge 26.3
or Minecraft gameplay evidence. No scientific model approval implied.

## Why two channels, not a universal data bus yet?

D21C stores individual recorder streams on the server and provides bounded,
read-only historical paging. D21D makes two of these streams comparable on one
in-game graph **without** granting any new client filesystem capability and
without changing detector physics. The existing menu uses signed 16-bit data
slots and an entire-page checksum, so the secondary stream is capped at 32
samples per page. A many-channel network requires a separate transport design,
access/ownership controls and performance evidence.

## Player operation

1. Place two ordinary D21 power data loggers, each beside **exactly one** D14
   power meter. Align different optical paths to the meters. Start recording
   both (sneak-right-click each) and let them collect samples.
2. Open logger A with normal right-click. Press **Compare: OFF**. The server
   chooses the nearest other loaded logger within the bounded search region,
   within eight blocks of both the original recorder and the player.
   Search bounds are ±6 blocks X/Z, ±2 Y (up to 845 candidate cells scanned
   per button press). Tie breaking is deterministic using coordinates.
3. The graph displays A (mint) and B (orange), with B's relative `(dx,dy,dz)`
   offset shown in the legend. **Next recorder** cycles through eligible nearby
   devices; it does not accept a client-supplied BlockPos or path.
4. Both channels share the **absolute server game Tick** horizontal axis and a
   common vertical optical-power mW scale. Their sample times are kept as they
   were acquired. Each invalid point creates a gap *within that channel*; two
   different channels never get added, subtracted, aligned by interpolation,
   or silently declared coherent.
5. Existing Older/Newer buttons scroll A's bounded preview pages. Each B page
   consists only of samples whose actual Tick lies in A's displayed interval.
   More than 32 B samples in that interval causes the comparison mode to fail
   closed rather than discarding or smoothing points. Older pages stay stable
   as new data arrive. Refresh creates a new snapshot.
6. Export CSV/SVG from comparison mode writes `.compare-page.csv` and
   `.compare-page.svg` inside the **SERVER** world measurements directory.
   Channel-tagged CSV rows do not alter either raw long-term archive. This
   does not download files to multiplayer clients.
7. Clicking **Disk history** turns off comparison and retains D21C's
   bounded, single-channel archived paging. Historical dual-stream joins,
   arbitrary dates, and client downloads are **not implemented**.

## Security and validity boundaries

- Both devices must be the actual loaded `PowerLoggerBlockEntity` at trusted
  positions in the current server dimension. Player reach ≤8 blocks to each;
  secondary-to-primary block distance ≤8. No forced chunk loading.
- Nearby selection occurs on server button presses only; no new arbitrary
  client-selected instrument ID, path or raw CSV cursor.
- Replacing/unloading a logger, walking out of reach or switching dimensions
  invalidates the secondary channel and clears its menu page. The existing
  primary menu's `stillValid` gate remains unchanged.
- Both 32-point pages share a single CRC32 of their bounded 16-bit menu words.
  This detects ordinary mixed-frame synchronization, not malicious server-side
  tampering and not a cryptographic integrity guarantee.
- The displayed Tick axis is a **logical game clock**, not actual elapsed SI
  time. Two measurement periods can drift relative to external clocks as TPS
  changes. Cross-recorder sample timestamps are not assumed equal.
- The experiment does not acquire more optical power, supply energy, activate
  lasers automatically or change D14 propagation rules.
- Existing D21A/D21B/D21C save records and archive filenames remain intact.

## Regression gates

- `python tools/check_d21d_comparison.py` verifies real pure Java output.
- `python -m unittest discover -s verification -p 'test_*.py' -v` verifies
  compatibility and structural boundaries.
- `python tools/generate_d21_assets.py` and `tools/test_preview_assets.py`
  verify resources and regenerate bilingual comparison button labels.
- Official JDK 25 `:experiment-core:test :neoforge-mod:build`, package inventory,
  client GUI and a dedicated server two-player test remain required.

## Next scientific implementation

D21E should use independent SI laboratory coordinates for calibrated sample
clocks and instrument grouping, then implement a *bounded multi-channel
historical query* with explicit access policy. D22 motorized scan needs
feedback (commanded versus measured stage coordinates), dwell-time stability
and synchronized data capture. Do not treat a Minecraft one-block translation
as a micrometer experiment.
