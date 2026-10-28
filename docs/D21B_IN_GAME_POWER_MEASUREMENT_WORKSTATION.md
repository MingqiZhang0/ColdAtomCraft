# D21B — In-game power measurement workstation (development preview)

Status: **implemented in source; not yet proven by JDK 25 NeoForge build or in-game launch.**
Scientific status: existing D14 **ideal, non-calibrated** Gaussian power detector; no real optical detector responsivity, detector noise, genuine wall-time timestamps or Create power supply.

## User interaction

1. Set up a working D14 laser and power meter and verify one-time meter output.
2. Place a D21A **Power Data Logger** adjacent to **exactly one** meter.
3. **Right-click** the recorder to open the D21B GUI. This changes the D21A right-click shortcut: recorder ON/OFF is now available inside the GUI, or by **sneak-right-click**.
4. Click **Start recording**. The server records one sample per 100 *server game ticks* while the recorder's chunk is ticking. At nominal 20 TPS this is about five wall-clock seconds; **this is not calibrated elapsed time**.
5. The GUI shows 32 samples per page, with **newest page first**. Hover a point to see the source game-tick index and recorded milliwatts, or the explicit invalid status. Scroll via **Older / Newer**. Cycle range between last **1, 5, 15 nominal minutes** and **all 512 preview samples**. The latest page updates automatically when a new sample is saved; historical pages remain a stable server snapshot until clicking Refresh.
6. **Export CSV / SVG** requests the existing D21A server-side archive/export; it reports the relative world directory to chat. It does **not** send arbitrary server files or download anything to the remote client. Files remain under the server world directory `coldatomcraft/measurements/`.

## Scientific and data semantics

- Source of every datum: `PowerLoggerBlockEntity.serverTick` → `DiagnosticBeamScan.sample(ServerLevel, meterPos)` → D14 optical trace. The workstation does **not** calculate readings on the client.
- Invalid statuses (`NO_BEAM`, `MULTIPLE_UNRESOLVED`, `INCOMPLETE`, `NO_METER`, `MULTIPLE_METERS`, `ADJACENT_UNLOADED`) are not zero-power readings. They appear as broken lines / orange gap markers, with no interpolated line segment crossing an invalid sample or a gap of more than `PowerTrace.MAX_CONNECTED_TICK_GAP = 100` ticks. A valid zero-power observation remains a valid data point at zero.
- The last 512 samples survive block entity save/load as D21A already defined. The full CSV may contain more; **D21B does not load arbitrary older portions of that CSV** into the game UI.
- Time range labels are nominal *game-tick* intervals (1m = 1200 ticks). Minecraft server pauses, TPS fluctuations and chunks not ticking mean elapsed wall-clock time is NOT represented accurately.
- The in-game preview transmits original 64-bit game ticks and 64-bit IEEE-754 power samples (with explicit status), split into signed 16-bit menu properties: 9 words/sample × 32 samples + 7 metadata words + 2 CRC32 words = 297 properties. This is a bounded, menu-authorized 32-point page; no general-purpose client file read endpoint is added.
- CRC32 of the complete menu page guards against transient mixed-page rendering during multi-property synchronization; it is **not** a cryptographic authentication mechanism. Server-side identity / proximity validation is separate and required for all menu buttons. The server rejects unknown buttons.

## Multiplayer / security / operations

- The vanilla authenticated container button channel is used; all actions require the same loaded block entity, same world block position and <= 8 blocks of player reach, checked server-side.
- Record / pause and export remain available to players physically interacting with a recorder, as in D21A. **There is not yet an apparatus ownership/permissions or claims system**. Integrate such policies before a public multiplayer server.
- Archive export is rate limited to at most once per 20 server game ticks per menu session. On failed export the existing D21A error lock stops future recording. The workstation itself creates no additional disk reads.
- Restart or chunk unload never silently resumes recording. It requires manual activation, and the GUI's `recording` indicator is authoritative only for the current server session.
- The D21 GUI translation keys are also added to `tools/generate_d21_assets.py`, so regenerating historical D14 placeholder assets does not erase the workstation UI strings.
- For earlier D21A installations, no block registry identifiers, blockstate schemas or saved NBT keys change. The new menu is registered separately and existing logger data is preserved.

## Verification

Run from the repository root:

```sh
python tools/check_d21b_ui.py
python -m unittest discover -s verification -p 'test_*.py' -v
python tools/check_d21_measurement.py
python tools/test_preview_assets.py
python tools/check_source_manifest.py --mode checkout
./gradlew :physics-core:test :experiment-core:test :neoforge-mod:build --no-daemon --stacktrace
```

The last Gradle command and game playtests require the pinned JDK 25 / NeoForge 26.3 environment. Do not claim these gates passed until they actually do. Perform the following game tests on a backed-up world:

- GUI opens with ordinary right-click; recording works with GUI Start/Pause and sneak-right-click, and old meter right-click is unchanged.
- Two player clients viewing the same recorder observe the same latest page after each server sample, including gap markers.
- Change/disable a laser, remove a meter, and unload its chunk; no fake zero sample or connected line should be shown.
- Older pages do not move when another sample arrives; Refresh returns to page zero; data remains after save/load.
- Players outside 8 blocks cannot keep controlling a stale menu; dismantling/replacing the recorder invalidates old menus.
- Export creates CSV/SVG **on the server**, without disclosing its absolute filesystem path or transferring a file to the client.
- 4/8/16 independent active recorders require actual TPS, server I/O rate and network bandwidth profiling prior to production multiplayer release.
