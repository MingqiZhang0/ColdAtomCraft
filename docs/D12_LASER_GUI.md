# D12 — Laser parameter UI, saved per-source settings

## Scope and authority

This is the **D12 development interface**, replacing the diagnostic laser's
D10/D11 direct right-click ON/OFF action. It does **not** represent an approved
commercial source or cold-atom experimental calibration. It is a diagnostic
simulation of a single CW, ideal-vacuum, TEM00 Gaussian beam in a straight,
air-only optical path, with one Minecraft block provisionally equal to 1 m.

- **Interaction:** right-click the laser with an empty hand or held item opens a
  vanilla menu. Sneak-right-click no longer rotates the block.
- **Buttons:** ON/OFF, rotate left/right in 90° increments, close.
- **Sliders:** output power 1–200 mW (1 mW increments), vacuum wavelength
  400–1100 nm (1 nm), 1/e² intensity waist radius 0.10–3.00 mm (0.01 mm),
  waist position from -2.00 to +2.00 m from the laser port (0.05 m).
- **Semantics:** positive waist offset is a waist downstream of the laser port;
  negative offset is behind the output port. This coordinate is NOT the player
  or Minecraft world origin.
- **Source of truth:** the server's `LaserSettingsBlockEntity` persists all four
  parameters using Minecraft 26.3 `ValueInput` and `ValueOutput` methods.
  Changing `enabled` and `facing` uses existing blockstates.
- **Networking:** vanilla authenticated menu button packets with server-side
  `stillValid` and bounds checks. Server values sync through six container
  data slots. Client BE updates also sync particle visualization. No raw
  arbitrary client NBT payload is accepted. Data values are <= 3000 in
  magnitude, fitting vanilla signed-short menu data.
- **Two devices:** settings are independent; each measurement resolves the
  specific laser BE it finds. No global mutable 'current laser'.
- **Missing settings:** measurement fails closed and explicitly reports that
  configuration is absent or not yet loaded; never invents a source reading.
- **Visual display:** approximate visible-spectrum colours for 400–700 nm and
  false red for near-IR (>700 nm). Particle size and enlarged apparent radius
  are deliberately only visualization cues, not physical light scattering.

## Actual optical calculation

The pure Java `LaserTuning` creates the existing `GaussianBeam` using

- `P_W = P_mW * 1e-3`, `lambda_m = lambda_nm * 1e-9`,
  `w0_m = waist_um * 1e-6`, and `z_waist_m = offset_mm * 1e-3`.
- The waist origin is explicitly distinguished from the device output port.
- At detector face distance `d` from the port, evaluate the axial coordinate
  `z = d - z_waist`, using the inherited Gaussian field's exact
  `w(z)=hypot(w0, lambda*z/(pi*w0))` and D9 aperture integration.
- `PreviewBeamDisplay` uses the very same `LaserTuning` and formula, then applies
  its *display-only* exaggerated radius.
- ON/OFF remains the server's blockstate. No energy is deposited into a block
  or entity. The 24-block cap is a D10 fixture restriction.

## Installation and acceptance on Minecraft 26.3 / NeoForge 26.3.0.99

1. Apply the D12 all-in-one upgrade script to your Git checkout, push to GitHub,
   and open the latest **ColdAtomCraft CI** run.
2. Require a successful Java 25 Gradle build, JUnit XML, mandatory JAR class
   inspection and a `CAC-M0-R2.1-D12-laser-control-neoforge` artifact. An old
   D10 or D11 JAR is **not acceptable**.
3. Remove old ColdAtomCraft JARs from `mods`; add exactly one D12 JAR. Launch a
   test world; check client startup and dedicated server startup separately.
4. Place a laser, right-click: check the GUI opens (rather than toggling it).
   Test with a held item and empty hand. Use the ON/OFF button and verify the
   front LED and beam state.
5. Change wavelength between 532 nm and 780 nm: the beam shows approximate
   green vs *false red*; source power reading must not change solely because
   particle RGB changed. Radius/divergence and collected power may change.
6. Set power to 20 mW, read power meter; increase to 100 mW and verify the
   same geometry yields approximately 5 times the collected power.
7. Move waist location toward the detector and vary waist radius: confirm a
   physical change in measured capture and beam visual radius.
8. Open two lasers separately; change the first source's settings. The second
   source must retain independent data. Re-enter the saved world and check.
9. Rotate laser in GUI; verify FRONT matches the actual particle path and
   power meter acceptance. Verify rock blocks the path and the meter fails
   closed. Try the limit of 24 blocks.
10. In a multiplayer test, have a second nearby player view the same instrument;
    verify its saved state is shared. Walk over 8 blocks away while menu is
    open; controls must not change the far-away instrument. Check no phantom
    item duplication or NBT state leakage.

## Test evidence and open gates

- D12 independent pure Java source smoke: 304 assertions passed on JDK21.
- D11 original visual smoke: 278 assertions passed on JDK21.
- Existing engineering smoke: 383 assertions passed on JDK21.
- Existing + new JUnit-source methods: 326 methods passed under an **offline
  reflection shim (NOT official JUnit)**, plus JUnit source for 12 D12 tests.
- Python static/resource checks: checked in `verification/test_d12_gui_contract.py`.
- **No JDK25 NeoForge build, live client UI screenshots or dedicated-server
  runtime have been produced for D12 in this environment.** The GitHub CI result
  and in-game behavior are the required acceptance gate.

## Known restrictions

- Fixed single beam, ideal-vacuum, no optical power absorption, no calibrated
  output power response curve, no coherence tuning, no polarization UI yet.
- Current output direction is horizontal. Angle adjustments are 90°.
- Slider changes apply immediately on the server, with no queued experiment
  timing or scheduler integration; use this only for the development preview.
- The mounted source's block entity should be initialized automatically when
  upgrading an old D10 save. If an old pre-BE source fails to show its GUI,
  **replace that one source block**; do not overwrite the whole world.
- Rendering is vanilla particles, not a volumetric shader nor a physically
  measured luminous ray. 780 nm light is normally invisible in clear air.
