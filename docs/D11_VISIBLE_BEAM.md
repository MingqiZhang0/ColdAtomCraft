> **D12 update:** The original D11 right-click toggle/Shift-rotate controls are superseded. In D12, right-click opens the server-authoritative Laser Control GUI; power, wavelength, waist, waist offset, on/off and rotation are set there. See [D12_LASER_GUI.md](D12_LASER_GUI.md).

# D11 — In-game diagnostic Gaussian laser tracer (D10 continuation)

## Scope

This change adds a **client-side visible beam** to the existing D10 placeholder laser. Its source enable/facing properties are authoritative, server-synced block states. A client reads them and paints a **false-colour red** line of vanilla dust particles along the calculated straight beam. No new damage, heat, absorption, entity collision, beam-coherence, or material rules are introduced.

- Turn the diagnostic laser on/off by right-clicking. Shift+right-click rotates it.
- A lit emitter displays a thin beam starting at the optical port, propagating in `FACING` direction (which points toward the player upon first placement, as established in commit `4ed18f8`).
- The first non-air block stops the beam at its **near surface**. A correctly facing diagnostic power meter stops the beam at its **optical front port**. Unloaded chunks stop it before entering the unknown chunk.
- The range is the D10 limit of 24 inspected air cells; at most three emitters are shown locally, scanning a 16-block horizontal / 5-block vertical radius around the player, once every four client ticks.
- All rays are horizontal, straight, in air; the visualizer does not approximate optical reflection, refraction, interference, detector responsivity or nonlinear field effects.
- No glow lighting is emitted along the ray. The laser block's existing on-state light is unchanged. The visual particles can take a short time to fade after a source is switched off or rotated.

## Physics and display semantics

The *actual physical* radial beam size uses the inherited `GaussianBeam.radiusMeters(z)` with the D10 demonstration fixture:

- `lambda = 780 nm` (near-infrared, ordinarily not visible as a beam in clean air)
- `w0 = 0.5 mm` (TEM00 1/e^2 intensity radius)
- `P = 20 mW` (only used by existing D10 ideal power meter)
- `z_R = pi w0^2/lambda` and `w(z)=hypot(w0, lambda*z/(pi*w0))`
- D10 maps one block of apparatus separation to one metre for the preview.

The **visual-only display radius** in blocks is `min(0.16, 0.03 + 9 * w(z) [m])`. This is a deliberate camera-readable exaggeration; **never** use it for optical power, forces, interactions, or experiment data. A dust-particle core follows the beam axis, while sparse outer particles use an inverse-CDF Gaussian radial distribution after scaling. The particle RGB color is diagnostic, not an actual radiation spectrum or a description of scattered light in vacuum. The renderer does not simulate material scattering or a volumetric EM field.

## Acceptance checklist

1. Run JDK 25 GitHub Actions and confirm the **new `DiagnosticBeamParticles.class`** and **`PreviewBeamDisplay.class`** are present inside the actual distributable JAR.
2. Minecraft Java 26.3 + NeoForge 26.3.0.99, a new test world. Enable particles in video settings.
3. Place a laser and look at its red port; right-click to switch it ON. Check that the beam appears from that port and points toward the initial placer.
4. Shift+right-click to rotate it; the beam follows the new `FACING`. A few particles may remain briefly from the old direction.
5. Place a solid block in the ray: the beam should stop at its near surface and the receiver must report blocked if located beyond it.
6. Remove the block and put the correctly facing diagnostic power meter within 24 blocks. The beam must end at its front port; the ideal power reading should agree with D10.
7. Turn OFF laser. New particles stop spawning. Existing dust fades after its vanilla particle lifetime.
8. Try an incorrectly facing meter and a source in an unloaded chunk. There must be no fabricated measured power or beam through unknown cells.
9. Dedicated server: no client-only class should be loaded on physical server. Test nearby players separately (particles are independently computed per client).
10. For FPS impact, try 1–3 emitters and change particle quality; this is a bounded preview, not a production shader or emissive optical mesh.

## Verification status

- Independent pure Java/JDK 21 smoke checks: **run in this checkpoint**; see included logs.
- Official GitHub Actions **JDK 25 / NeoForge** compilation: **NOT YET RUN** on this D11 change.
- Client / dedicated server runtime, optical beam screenshot, GPU/FPS verification: **NOT YET RUN** on this D11 change.

The tested JDK 21 physics reference is not a substitute for formal NeoForge compilation or in-game verification.
