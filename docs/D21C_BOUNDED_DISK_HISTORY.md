# CAC D21C: bounded, server-authorized on-disk history (DEV)

**Status:** development checkpoint. This is neither a release nor a claim of
Java 25 / NeoForge 26.3 in-game verification. The first implementation is
read-only historical **CSV page browsing**, not a client file-downloader or a
laboratory clock. Its scope is deliberately smaller than all future D21C goals.

## Current player workflow

1. Place a D21 diagnostic laser, a matching D14 power meter, and exactly one
   adjacent diagnostic power logger. Start recording; server-side samples append
   to `world/coldatomcraft/measurements/<server-generated-identity>.csv` every
   100 **server game ticks**. Never describe these ticks as elapsed SI seconds.
2. Right-click the logger to open D21B. Default **Recent 512** displays the
   in-world saved ring. Press **Disk history** to query the actual CSV from disk.
3. The newest archive page holds up to 32 unmodified samples. **Older/Newer**
   navigates the archive, with a five-game-tick per-menu navigation throttle.
   The title `Archive page 2+` means additional older pages exist, not that the
   entire file has been scanned and its global page count is known.
4. Press **Refresh** while in history to open a *new* latest-file snapshot. An
   existing history session has a fixed `snapshotEnd`; recording in parallel
   does not introduce newer samples into existing archive pages. **Recent 512**
   returns to live D21B. Its one/five/fifteen-minute filters work only in recent
   mode; a history page shows actual CSV records, not an interpolated timeseries.
5. In archive mode **Export CSV/SVG** exports only the displayed 32-point page,
   overwriting a pair of server-side `.history-page.csv` and `.history-page.svg`
   files. The underlying lifetime CSV is not modified. In recent mode the old
   D21B Export operation is unchanged. No arbitrary disk path or CSV bytes are
   transferred to a remote player's client.

## Bounded read and data integrity rules

- Reader `PowerTraceHistory` is independent of Minecraft and uses the already
  agreed append-only D21A format. Records are `(gameTick,status,watts)`, with
  *empty* watts for invalid statuses; real measured 0 W is still a valid sample.
- A history page reads at most **16 KiB of row data** plus a fixed header and
  boundary verification. Maximum physical record length is 128 bytes, and the
  archive is limited to the previous **64 MiB** per recorder. A single request
  never scans a multi-month CSV to count all pages.
- Only the server BlockEntity chooses archive identity from its persistent ID,
  dimension and position. Every menu button rechecks the actual same-world,
  same-BlockEntity, within-eight-block permission (`stillValid`). No file path,
  recorder ID or byte cursor is accepted from the client.
- Reader rejects symlinks, corrupt headers, non-monotonic records within each
  page, non-ASCII data, invalid status/power combinations, torn terminal lines,
  oversized rows, invalid cursors and archives larger than 64 MiB.
- If an archive read fails, the session does not display suspect points;
  recording is paused and the logger enters a storage-error state. Restore or
  recreate the recorder after investigating the server files. The client sees
  only a general error, never a raw server filesystem path.
- File-size anchored snapshots are **not** immutable content hashes: a server
  administrator replacing bytes in-place could invalidate a snapshot without
  changing size. This is a trusted local research profile, not tamper-evident
  data storage. Filesystem accesses are byte-bounded, not guaranteed
  wall-clock latency-bounded on arbitrary disks.

## Acceptance gates

1. Independent Java 21 smoke via `python tools/check_d21c_history.py` against
   real CSV files (not a mocked optical solver); verify reverse and forward
   pagination, 64-bit tick fields, append-during-snapshot, invalid data, EOF
   tearing, malformed rows and symlink handling.
2. Java JUnit 5 `PowerTraceHistoryTest` must pass under official JDK 25 Gradle;
   independent offline compatibility execution is only preliminary evidence.
3. `verification/test_d21c_history_contract.py` checks menu authority,
   16-KiB read budget, CI integration, localization and zero impact on optics.
4. Official `:physics-core:test :experiment-core:test :neoforge-mod:build`
   and JAR inventory run in CI on the actual pushed version. Isolated mock
   GUI or Java 21 tests are NOT Minecraft 26.3 release evidence.
5. In-game: run more than 512 samples, compare oldest and newest values between
   CSV and GUI, browse while sampling, inspect valid 0 W and invalid gaps,
   export two distinct pages and verify only the bounded page export is
   overwritten. Test server reboot, logger replacement, other dimensions,
   corrupt CSV, multiple players and unloaded chunks. Back up the world.

## Deferred work, explicitly NOT delivered by D21C first increment

- Arbitrary direct timestamp search (currently Older/Newer page traversal).
- Multi-recorder overlays and power vs stage position/angle plots.
- Per-player owner permissions or a cryptographically signed provenance log.
- Direct client-side download of the full server CSV/SVG; recorders remain
  accessible to nearby players under the existing D21B DEV permission model.
- SI wall-clock timestamps, calibrated power-meter response/noise, index files,
  bounded disk I/O *time*, and Create-powered automated precision stages.

**Design consequence:** D21C's archive reader and UI are strictly observation
and export systems. They do not change the D14 optical solver, device control,
manual laser ON/OFF, energy accounting or physics model approval state.
