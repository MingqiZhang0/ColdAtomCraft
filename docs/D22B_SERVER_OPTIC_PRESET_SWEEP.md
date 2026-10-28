# D22B — Server-driven diagnostic optical preset scan (engineering preview)

**Status:** Source implementation + offline verification. The D22A precision SI motor
and encoder model is **not** connected to a Minecraft motor. Formal Java 25,
NeoForge 26.3, singleplayer and dedicated-server acceptance are pending.

## Installation and operation

Build the new `diagnostic_auto_preset_stage` creative item. Place one CAC optical
table, the automatic scan stage directly above it, and a *stateless D14 optic*
(mirror, 50:50 splitter, ideal thin lens or HWP) directly above the stage.
Place an existing D14 power meter within 8 blocks of the stage; the nearest
loaded meter is selected, and tied nearest meters refuse startup. Configure
laser propagation and optional recorder manually as usual.

- Right-click the new scan stage: start a new four-preset scan; right-click again
  while it runs: stop it.
- The stage sets the mounted D14 optic's actual `port` selector to 0, 1, 2, 3.
  Incoming photon-travel direction is held fixed. The actual D14 optical solver
  sees the same blockstate and produces the power-meter reading.
- Each preset must remain unchanged for **five consecutive server ticks**
  before a reading is requested; reading happens on the subsequent tick.
- Sneak-right-click after completing the scan exports `.csv` and `.svg` to
  `<server-world>/coldatomcraft/measurements/` with a fixed bounded filename.
  A complete scan is also exported automatically upon completion.
- The GUI is not part of D22B: the player receives server system messages and
  opens CSV/SVG from the server save. Existing D21 workstation and original
  long-term logger are unaffected.

## Data integrity

CSV schema is exactly:

```csv
optic_preset,server_game_tick,status,watts
0,123,MEASURED,0.01
1,129,NO_BEAM,
```

`NO_BEAM`, `MULTIPLE_UNRESOLVED` and `INCOMPLETE` remain invalid observations
with blank power; zero **is** a valid numeric measurement only when status is
`MEASURED`. The SVG x axis is **integer optical selector**, NOT calibrated
angle (radians/degrees), motor encoder position (nm), or any physical travel.
No interpolated samples are added. The scan result is capped at four points,
validated when reloaded, and stored in the stage's BlockEntity data.

The server owns the stage's block ticker. The controller cannot issue direct
laser ON commands; it changes only a mounted optic's selector. Missing ticks,
missing meter or optic, changed optic selector or incoming port, changed meter
aperture/facing, and unloaded chunks stop the scan. Completion or cancellation
attempts to restore the initial optic selector **only** when the last commanded
state is still present. A chunk unload or block removal can strand the optic at
the last commanded state, requiring manual inspection. The scan run itself is
never persisted or automatically resumed after a restart.

## What it intentionally does *not* mean

- No Create SU/FE energy, actual motor, electronic servo, calibrated encoder,
  stage position feedback, fine rotation, stage zero or mechanical error model.
- D14 supports four *discrete optical parameters*; a lens's selector changes
  its ideal focal length, while a HWP's selector changes its Jones optical axis
  in increments of 22.5 degrees. Mirror selectors change output direction.
  These are NOT interchangeable notions of mechanical stage angle.
- A HWP without a polarizing analyzer should not produce Malus-law power
  oscillations on the non-polarizing D14 meter. A flat/invalid scan is honest.
- The per-stage four-point export is not the D21 long-term power CSV; they are
  separate measurement sources. The stage cannot prove a source did not change
  during its five-tick settling window, only that optic and meter states match.
- Vanilla stateless BlockState replacement by an identical new block is not
  distinguishable without a future unique mount identity. Future multi-user
  control must add a block identity/version lease and permission checks.

## Acceptance gates

1. Offline `python tools/check_d22b_preset_scan.py` plus formal JUnit 5 tests.
2. Full `python -m unittest discover -s verification -p 'test_*.py' -v`, D21
   asset tests and canonical Git manifest after local staging.
3. JDK 25 CI `:experiment-core:test :neoforge-mod:build` on the user's branch.
4. Singleplayer and dedicated server: creative registry, actual four optical
   states reflected in D14 readings, CSV/SVG, restart, cancellation, chunk
   unload, duplicated/removed meter, player interference, source count limits.
5. Future D22C: owner-approved SI table coordinates, continuous optical
   geometry/analysers, actual encoder and power bus, stage GUI and true
   calibrated `P(theta)` / `P(x)` curves.
