## D9 future owner-science intake — instrument-specific parameters

The current D9 power-meter reference needs NO arbitrary calibration constants:
it reports only the aperture-integrated ideal optical W. When Owner supplies
an actual detector model, explicitly request its active area, aperture
geometry and axis, spectral responsivity or calibrated W transfer function,
measurement time averaging, bandwidth, noise/uncertainty, saturation,
reflection/absorption and face location in the local laboratory frame.
Do not infer these from a Blender mesh or treat missed aperture power as heat.
The first Blender power-meter model will also require an explicit incoming
optical port anchor and declared metre-to-world spatial mapping.

## D8 physical optics reference, still needing Owner apparatus parameters

D8 adds three-dimensional ray reflection, uncoated single-interface Fresnel
s/p coefficients, Snell refraction and an ideal phase-defined plane mirror.
Before claiming this as a real lab mirror, the Owner must provide or approve
coating/mirror type, wavelength/angular reflectance and phase behavior, port
coordinate conventions, actual component pose/Blender anchors and reference
plane, and any finite-beam/aperture assumptions. No values were invented.

## D7 scientific intake (NO implied defaults)

D7 now supplies matched-mode two-port interference with explicit phase and
power conservation, but does NOT calibrate any physical apparatus. Before
world integration, the Owner/model team must decide: laser reference-plane
position vs Blender output port; a phase-coherent source key with measured
stability; interferometer arm optical-path lengths and accuracy; beam splitter
port mapping plus wavelength/polarization-dependent scattering coefficients;
actual mirror surface geometry and s/p phase; mode overlap/waist alignment;
instrument aperture and detection model. No heat or atomic force can be
inferred from tracked-mode optical power removal.

# First Light scientific intake — OPT-001 owner worksheet

The Owner selected standard real experimental laser models. A narrow **draft** CW TEM00 vacuum Gaussian + ideal ABCD numerical reference now exists; see `OPT_001_GAUSSIAN_REFERENCE.md`. Exact emission-plane/waist geometry, instrument validity limits, measured hardware parameters and production-model signoff are still outstanding. These questions are not automatically answered by choosing Gaussian optics.

## Part 1: OPT-001 laser emission / propagation

- **Domain:** continuous-wave, pulsed, or both? Paraxial regime / distances / media?
- **Emission controls:** optical power `P` [W], vacuum wavelength `lambda` [m], waist radius [m], origin [lab m], propagation direction [unit vector]. Which additional controls are required immediately (linewidth, polarization, phase, coherence, beam profile)?
- **Law:** exact equations and assumptions for propagation. Is first implementation scalar paraxial Gaussian, ray optics, combined envelope, or another owner-selected model?
- **Boundary conditions:** emission plane, beam propagation cutoff, power conservation, treatment of air/vacuum, and source shutdown.
- **Numerical:** accepted algorithm and tolerances, handling of multiple intersecting beams, which fields are stored vs derived.
- **Checks:** analytic limits and representative reference cases, including source-off behavior and field integration checks.

## Part 2: OPT-002 optical components (separate gate)

- Surface geometry and intersection handling; reflection/transmission/scattering vs wavelength and polarization.
- Whether coherent fields are summed or independent beam powers are used.
- Requirements for attenuation and energy bookkeeping.

## Part 3: THM-001/BIO-001 (independent gates)

- Definition of absorbed energy and surface/body deposition area.
- Thermal state variables, conductive/convective/radiative cooling assumptions.
- How physical damage maps to MC block transitions and hearts; exact game-only calibration if any.
- Permissions and multiplayer damage behavior.

**Do not conflate the four model IDs.** One approved Gaussian source does not authorize ignition/damage. The engineering team first implements OPT-001 with independent benchmarks, then asks for the next scientific decision.


## D6 next-science questions (NOT defaults)

- Which laser source/wavelength and linewidth are to be treated as a single
  oscillator or experimentally phase-locked coherent group?
- What are the optical waist position and its phase origin relative to the
  actual Blender output port and beam propagation direction?
- Which optical-path elements, polarization conventions and phase reference
  must be supplied before interference or a standing wave is enabled in-game?
- The pure Java field models do not provide an experimentally calibrated
  source/splitter energy budget; do not use their local `|F|²` directly for
  Minecraft heat, light damage or atom cooling without later approved models.
