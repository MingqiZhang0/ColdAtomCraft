# CAC D22F — Persistent apparatus identity for optical laboratory blocks

**Engineering status:** implemented source + independent offline tests; **NOT** yet
compiled against Java 25 / NeoForge 26.3 or verified in a running Minecraft
client and dedicated server. No motor or laser ON authorization is introduced.

## 1. What changed and why

D22E rightly refused to call a stateless optical BlockState a persistent device.
Replacing a meter or optic with an identical block in the same location could
fool repeated state-only checks. D22F gives **the four existing D14 optics,
power meter, and optical table** a small, stable server-side `BlockEntity` that
stores a version-4 random UUID and a 64-bit configuration revision. The optics
and meter retain their original block IDs, blockstate names, visible models,
aperture and `PORT`/`FACING` values, optical formulas and manual GUI behavior.

- `PersistentApparatusIdentity` is immutable, pure Java (`experiment-core`).
- `DiagnosticApparatusIdentityBlockEntity` is the NBT persistence adapter.
- A single new `diagnostic_apparatus_identity` BlockEntityType is registered for
  the six already registered block types; no new inventory item is needed.
- `LoadedApparatusSnapshot` can carry and check a durable `(dimension, block,
  device kind, UUID, revision)` reference **alongside** a same-tick JVM object
  reference; distinct evidence types are not confused with hardware proof.
- D22B's running scan pins exact BE objects and UUID/revisions for its table,
  optic and selected power meter. A replacement, edit or corrupted saved ID
  terminates the run. The controller still does not turn a laser ON.
- Existing D21 coarse manual slide continues to move the actual optic block,
  but this grid move recreates a NEW identity; it does not pretend to retain
  a physical instrument's identity across precise movement.
- `DiagnosticOpticBlock`/meter controlled `BlockState` edits advance revision.
  The BE's `setBlockState` hook plus checked controlled-write fallback avoid
  double-counting a normal MC state update.

## 2. Save/load and migration contract

Saved BE keys:

    d22f_apparatus_uuid       canonical lowercase v4 UUID string
    d22f_apparatus_revision   nonnegative signed 64-bit integer

| World event | Result |
| --- | --- |
| New placement | fresh random UUID, revision zero |
| Normal chunk unload/reload | UUID and revision restored from the block entity NBT |
| Normal save/restart | same ID/revision once the chunk is actually saved |
| Editing a discrete optical setting | same UUID; incremented revision |
| Breaking and placing the normal dropped item | freshly instantiated BE and UUID |
| Loading legacy D14–D22E block with *no saved key* | new UUID, explicitly `LEGACY_MIGRATED`, marked dirty for next save |
| Nonempty malformed ID, invalid UUID, negative or missing revision for an existing UUID, or missing ID with nonzero revision | `QUARANTINED`; no silent rekey; excluded from authorized scans |
| Revision reaches `Long.MAX_VALUE` and is edited again | `QUARANTINED` rather than integer wraparound |

**Migration limitation:** existing old chunks where Minecraft does not create
an instance of the newly registered BE automatically cannot be treated as
identity-bound. Such devices must be manually broken/replaced; never fabricate
continuity with a previous run. Backup the world before trying a new mod build.

**Do not claim tamper resistance:** privileged NBT editing, world rollback,
structure cloning or third-party block-moving mods can duplicate UUIDs or
revive old data. A single 24-device snapshot explicitly rejects duplicate IDs,
but a world-wide anti-clone registry, cryptographic provenance, access control
and permission/ownership policy do not yet exist. IDs are not a power ledger,
permission token, encoder, hardware proof or scientific calibration.

## 3. Liveness and safe scanning

Snapshots remain server-thread-only, loaded-chunk-only, and valid for **one
server Tick** in one world instance. They compare actual BlockState, saved
UUID + revision and current BlockEntity object reference. Identical replacement
is caught even if the new block has identical `PORT` and the same coordinates.

The D22B scanner keeps the original table/optic/power meter BE references for
its run. Every server Tick must match all three UUID/revision pairs and exact
BE object identities, plus original optic kind/incoming direction and meter
BlockState. The controller itself retains no running state across save/load:
server restarts and chunk unloads do not resume a scan. Only the scanner's own
verified selector writes may advance its bound revision; external changes cause
failure. Restoration is attempted only while the bound optic still matches.

**Correctness boundary:** world mutations by unrelated mods that bypass normal
BE state-update hooks may not reliably increment the revision. Per-Tick server
re-reading and BE identity provide additional checks, not a fully transactional
world-operation audit. No automatic power supply or motor actuation is granted.

## 4. Evidence and run instructions

All tests from the repository root:

    python tools/check_d22f_identity.py
    python tools/check_d22f_junit_source.py
    python tools/check_d22f_game_identity.py
    python tools/check_d22e_live_geometry.py
    python tools/check_d22e_game_adapter.py
    python tools/check_d22b_preset_scan.py
    python -m unittest discover -s verification -p 'test_*.py' -v

The first gate compiles real pure Java sources at `--release 21` and runs
randomized save/restore/revision and snapshot-replacement tests. The second
compiles 18 actual JUnit 5 method bodies with a minimal offline assertion shim
(**not official JUnit 5 evidence**). The third compiles the **real** D22F
BlockEntity class against isolated 26.3-shaped API stubs and exercises NBT
round trips; this is NOT the Java 25 / NeoForge runtime or binary API.

CI also has an independent official Java 25 Gradle/NeoForge build stage and
JUnit XML checks; they must run on a freshly pushed feature branch before any
claim of a buildable playable release.

## 5. Minecraft acceptance before enabling further automation

1. Back up an existing D22E world, build the D22F feature branch using the
   official Java 25 CI, and launch both integrated client and dedicated server.
2. Place an optical table, a D14 optic and a power meter. Check the table's
   inspector reports `PERSISTENT_BLOCK_ENTITY` and the same physical optic
   keeps working under D14 optics and D21 logging.
3. Save, unload and reload; verify the same UUID and revision in the **server
   BlockEntity NBT** (use development commands/logging, not player GUI).
4. Rotate a component or change meter aperture and verify revision increases.
5. Break and place an identical optic in the same position: UUID changes.
   Test during an active D22B scan; scanning must abort and never silently
   continue or overwrite the replacement.
6. Repeat for the meter and optical table. Test a deliberately corrupted NBT
   UUID, legacy world upgrade, server restart, and normal/manual grid motion.
7. Confirm no forced chunk loads, no client-side scan authorization, no
   unguarded laser ON, no fabricated motor encoder and no changes to D14's
   optical solver output.

D22F only establishes saved identity lineage for a limited set of DEV blocks.
The next step is official NeoForge game-build evidence and careful instrument
lifecycle acceptance, NOT treating UUID persistence as a complete control or
hardware security model.
