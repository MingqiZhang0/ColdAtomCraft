# CAC OPT-001-D7: Phase-tracked paraxial modal optics (SCIENTIFIC DRAFT)

**Scope:** Independent Java reference in `physics-core`, no NeoForge integration,
no material absorption, no game world mutation and no Owner production-model
approval. It builds on D5 scalar Gaussian/ABCD and D6 Jones/coherent fields.

## Physical model and variables

A classical monochromatic, continuous-wave TEM00 beam in vacuum is represented
at a reference plane by:

- Vacuum carrier wavelength `lambda` in metres;
- Complex Gaussian parameter `q=x+i*y`, `y>0`, in metres;
- A transverse Jones modal amplitude `a=(a_x,a_y)`, complex `sqrt(W)`;
- A declared coherent laser phase reference key and spatial/polarization
  mode-frame key. These *strings are assertions*, not alignment measurements
  or server-world identity/permission information.

The optical power in that tracked spatial/polarization mode is
`P=|a_x|^2+|a_y|^2` [W]. This is deliberately distinct from a raw local
`|E|^2` electric intensity equivalent, a Poynting flux or power deposited
in a material.

The reference-plane transverse field uses the D6 time convention
`exp(-i omega t)`, the **existing D5 positive-imaginary q convention**
`1/q=1/R-i*lambda/(pi*w^2)` and transverse Gaussian shape:

```
F(r) = a * sqrt(2/(pi*w^2)) * exp(-r^2/w^2)
          * exp(+i*k*r^2/(2*R))  [sqrt(W/m^2)]
```

where `w=w(q,lambda)` and `1/R=Re(1/q)`. The signed axis and transverse
basis are **local optical-port inputs**, not MC world coordinates.

The field is normalized: integrating the transverse single-traveling-mode
intensity over the infinite plane yields `P`. D7 can sample this field
at a requested nonnegative transverse radius; it does not include apertures
or actual optical surface geometry.

## Free-space propagation and Gouy phase

For a physical path length `L>=0`, the ideal vacuum paraxial field obeys:

```
q_out = q_in + L
phi_out - phi_in = k*L + arg(conj(q_in)) - arg(conj(q_out))
                = k*L + atan2(y,x+L) - atan2(y,x)
a_out = a_in * exp(i*(phi_out-phi_in))
```

The second term is the **accumulated Gouy phase** of the normalized spatial
mode. At the waist `q_in=i*zR`, it becomes `-atan(L/zR)`.

A pure ABCD `q` transfer without this modal phase is not sufficient for an
interferometer. D7 keeps both separately.

`ParaxialModeField.freeSpace()` reduces `L` modulo `lambda` before converting
to carrier phase. The diagnostic `inputPathUlpPhaseRadians(L)` reports how
many carrier-phase radians correspond to **one double ULP of the supplied
optical path**. This is a numerical precision warning, NOT an instrument
uncertainty or an upper error bound. At large Minecraft world coordinates,
absolute metres in binary floating point may already lack sub-wavelength
precision. Use local experimental coordinates and calibrated path phase, not
large world-coordinate differences, for a real interferometer.

## Thin lens

An ideal, lossless infinitely thin lens at one plane implements:

```
q_out = q_in / (1-q_in/f)
1/R_out = 1/R_in - 1/f
P_out = P_in
```

The on-axis field at the lens plane is continuous **up to an explicitly
provided common phase**. The Gaussian field transverse curvature acquires
`-k*r^2/(2*f)` relative to the incident field. D7 takes the common
on-axis phase as an explicit argument, rather than inventing it from lens
thickness/coating/refractive index that the Owner has not specified.
This is valid only inside the ideal thin-lens/paraxial model. No Fresnel
loss, clipping, aberration, finite NA, or refractive-index dispersion.

## Passive Jones elements and path histories

A uniform passive Jones matrix transforms the modal 2-vector:
`a_out=J*a_in`. A passive-element method rejects gain for the actual input.
Named D6 Jones factories supply ideal polarizers, QWPs and HWPs. Uniform
phase is preserved; a plate's relative retardance alone never specifies its
full interferometric common path phase.

`OpticalPathTrace` is immutable and retains ordered operations and states.
Its `powerRemovedFromTrackedModeWatts` measures **loss from the tracked
mode only**, not guaranteed local heating/absorption. In particular, a
polarizer's removed mode power cannot be handed to `WorldEffectLedger` as
absorbed energy.

`idealMirrorPhaseOnly()` / `withIdealMirrorReferencePhaseRadians()` are
**phase-only route annotations**. They do not reflect a world-space ray,
rotate polarization axes, implement Fresnel s/p coefficients, or specify
coating reflectance. Full physical mirror rendering/reflection is
NOT IMPLEMENTED in D7.

## Symmetric lossless two-port beam splitter

For two matched coherent modal inputs `a_0` and `a_1`, the fixed phase-gauge
convention is:

```
[b_0] = [sqrt(T)  i*sqrt(1-T)] [a_0]
[b_1]   [i*sqrt(1-T)  sqrt(T)] [a_1]
```

`0<=T<=1` is a *power* transmission coefficient; the complex reflection
coefficient carries `+pi/2` relative phase. The matrix is unitary. It
operates independently on both Jones components and must preserve **the
sum of output powers**, including coherent two-input interference.
These phase signs are a *chosen symmetric port convention*, not a prediction
of a real beam splitter coating or which Blender side is physically labelled
port 0. A real laboratory must supply/calibrate its ports, s/p dependence,
coating phases and alignment.

The reference class refuses to collapse different carrier wavelengths,
phase origins, mode-frame declarations or unequal `q` into one Gaussian.
An actual unmatched-mode interferometer requires spatial overlap integrals,
multiple mode outputs or a full wave-optics field propagation method.
The solver deliberately fails rather than silently projecting mismatched
beams into a single TEM00 mode.

A classical single-input splitter has a zero-amplitude *other* input. This
is not a quantum-optics model of vacuum fluctuations or shot noise.

### Mach-Zehnder analytic test

Two ideal 50/50 splitters, identical mode-matched propagation in both arms,
and one arm with relative phase `delta` yield:

```
P_out_0 = P_in*sin^2(delta/2)
P_out_1 = P_in*cos^2(delta/2)
P_out_0 + P_out_1 = P_in
```

At `delta=0`, output 0 is dark and output 1 is bright; at `delta=pi` the
ports switch. No actual MC device geometry, arm lengths, beam spot overlap
or detector is implemented yet.

## Verification and provenance

- D6 frozen ZIP input hash: see `verification/M0-R2.1-D7/README.md`.
- Three new production classes: `ParaxialModeField`,
  `IdealTwoPortBeamSplitter`, `OpticalPathTrace`.
- Three new JUnit test source classes: 30 additional `@Test` methods;
  the complete 224 methods pass the D7 offline **NON-JUNIT** assertion sweep.
- D7 standalone smoke: 19 checks.
- A separate `Decimal(80 digits)` Python oracle independently validates
  29 free-space/Gouy/lens/Jones/beam-splitter/Mach-Zehnder/power-budget
  scalar quantities against the compiled Java implementation.
- Retains 36 D5 and 39 D6 high-precision oracle comparisons.
- Formal Java25 Gradle/JUnit, NeoForge 26.3 game/client/server and packaged
  JAR verification remain separate uncompleted gates on this host.

## Scientific and software limitations

No finite beam apertures, tilted-mirror geometry, s/p Fresnel coefficients,
rotating local polarization frames, mode-overlap loss, coherent beating,
partial coherence, pulse envelopes, finite laser linewidth, high-NA/vector
fields, atomic optical interactions or game-material heating are modeled.
Model creation is not an Owner production model approval.

**References:**
- RP Photonics, Gaussian beams and ABCD matrices:
  https://www.rp-photonics.com/gaussian_beams.html
  https://www.rp-photonics.com/abcd_matrix.html
- RP Photonics, wave-optics sign conventions:
  https://www.rp-photonics.com/sign_conventions_in_wave_optics.html
- Lossless two-port coupler unitarity, Sci Rep (2021):
  https://www.nature.com/articles/s41598-021-84588-w
- Finesse, accumulated Gouy phase:
  https://finesse.ifosim.org/docs/latest/usage/higher_order_modes/accumulated_gouy_phase.html
