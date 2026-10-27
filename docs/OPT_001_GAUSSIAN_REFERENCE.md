# OPT-001 — Experimental Gaussian laser numerical reference (D5)

**Scientific standing:** reference implementation of standard experimental
**continuous-wave, vacuum, scalar, paraxial TEM00 Gaussian optics**. The Owner
has chosen *real laboratory optics* as the design direction. This document does
**not** mark a production model `OWNER_APPROVED`: the physical emission plane,
actual experimental instrument parameters, model domain thresholds, validation
against a chosen apparatus, and source of the final scientific revision have
not all been specified. ModelCatalog remains unchanged. This is not a playable
Minecraft beam or a world-mutation implementation.

## Inputs and coordinate contract

- `P` [W]: time-averaged continuous-wave optical power, nonnegative.
- `lambda` [m]: vacuum wavelength (no refractive-index model at D5).
- `w0` [m]: 1/e²-irradiance **waist radius**, not laser-body radius.
- `waistPositionLabMeters` [m]: **physical beam waist centre** in a local laboratory frame.
- `directionLab` [unitless]: normalized local propagation direction.
- `z` [m]: signed distance along the axis measured from the waist, which may be
  upstream or downstream of the physical source port.
- `r` [m]: nonnegative transverse distance to the axis at the given `z`.

**Critical modelling distinction:** pre-existing `BeamInputs` describes an
*output port position*. Its `waistRadiusMeters` alone cannot establish that the
waist occurs at that port. We do **not** silently convert `BeamInputs` into a
`GaussianBeam`. An actual laser profile/hardware handoff must provide the waist
location relative to the emitter before that adapter is implemented.

## Governing equations

For an ideal TEM00 beam in vacuum, using the standard scalar paraxial solution:

\[
z_R=\frac{\pi w_0^2}{\lambda}, \qquad
\theta_0 = \frac{\lambda}{\pi w_0}, \qquad
w(z) = w_0\sqrt{1+(z/z_R)^2}=\sqrt{w_0^2+(\theta_0 z)^2}.
\]

\[
I(r,z)=\frac{2P}{\pi w(z)^2}\exp\!\left(-\frac{2r^2}{w(z)^2}\right),
\qquad \int_0^{\infty}2\pi r I(r,z)\,dr=P.
\]

The power transmitted by an ideal coaxial, unobstructed circular aperture of
radius `a` is:

\[
P_a(z)=P\left[1-\exp\left(-\frac{2a^2}{w(z)^2}\right)\right].
\]

Wavefront curvature and Gouy phase:

\[
R(z)=z\bigl(1+(z_R/z)^2\bigr)\;(z\ne0),\quad
R(0)=\infty,\quad \psi(z)=\arctan(z/z_R).
\]

For paraxial first-order **same-index vacuum** ray transfer:

\[
q(z)=z+i z_R,\qquad
\frac{1}{q}=\frac{1}{R}-i\frac{\lambda}{\pi w^2},\qquad
q_2=\frac{Aq_1+B}{Cq_1+D}.
\]

The only supported optical elements in the numerical reference are **free
space** (`A=D=1, B=L, C=0`) and **ideal thin lens** (`A=D=1, B=0, C=-1/f`).
Both use determinant `AD-BC=+1`. A negative `f` is diverging. The matrices
transform **q**, not polarization or transmitted optical power.
`GaussianPlane` carries the unchanged CW power through an ideal lossless
lens/free-space train and supplies the output-plane radial irradiance and
encircled power. Its plane has no global coordinate or hardware geometry. To propagate a
laser with a specified power, the higher-level experiment model must carry
`P` separately and account for all losses explicitly. This package does not
implement mirrors, surfaces, absorbers, diffraction at apertures, optical
coherence, or the atom–light interaction.

## Numerical accuracy and domain safeguards

- Distances stay in **SI metres**, never Minecraft block coordinates.
- Internally the beam waist position is stored independently of an apparatus
  emitter port. The axis is normalized without overflow for finite nonzero
  vectors.
- `Math.hypot` avoids squaring a potentially huge `z/zR` unnecessarily.
- `Math.expm1` preserves tiny encircled power fractions where ordinary
  `1-exp(x)` loses precision.
- The reciprocal-q formula for the output beam radius follows the complex
  parameter relation. The ABCD implementation uses stable complex division
  for its real part and **the determinant identity**
  `Im(q_out) = (AD-BC) * Im(q_in) / |C q_in + D|²`
  for its imaginary part. This avoids catastrophic cancellation.
- Inputs that are negative, non-finite or lead to non-representable derived
  quantities fail explicitly. The ideal curvature is infinite at the waist,
  which is a mathematical convention rather than a numerical calculation bug.
- A numeric solution remaining finite does **not** certify paraxial validity.
  The dimensionless divergence `lambda/(pi*w0)` is exposed, but no arbitrary
  hard-coded Owner acceptance threshold is imposed. `theta0 << 1`, negligible
  aberrations/truncation and near-TEM00 mode quality are physical preconditions.
- Real-life high-NA focusing, arbitrary mode profiles, pulses, absorptive
  media, vector polarization, coherent beam superposition, AOM/EOM frequency
  changes, laser linewidth, beamsplitters and interference require separate
  approved models and tests.

## Independent verification

`physics-core/src/test/java/dev/coldatomcraft/physics/optics/`
contains formal JUnit 5 **source** covering the analytic limits, power
normalization by independent numerical radial quadrature, negative axial
coordinates, arbitrary laboratory-frame direction, ABCD composition, lens
plane continuity, q-based curvature, invalid inputs and extreme focusing.

`verification/OpticsSmoke.java` is a dependency-free reference smoke test.

`verification/M0-R2.1-D5/verify_optics_reference.py` uses Python stdlib Decimal
with 80 digits and a separate algebraic calculation of ABCD complex division.
It compiles and runs a Java probe in a temporary directory and compares 6
Gaussian cases × 4 observables, 3 ABCD cases × 2 observables and 1 ideal
lens-plane case × 6 observables: **36 independent scalar comparisons**. The script does not depend on Python
`mpmath`, Gradle or NeoForge. It does require a working Java compiler/runtime.
The numerical comparison tolerances are deliberately small but not a guarantee
that real laser data will match a perfect TEM00 beam.

**Reference authority:** [RP Photonics, Gaussian Beams](https://www.rp-photonics.com/gaussian_beams.html),
which states the Gaussian intensity, Rayleigh range, curvature and q/ABCD
relationships, alongside standard textbooks A. E. Siegman, *Lasers* and
B. E. A. Saleh & M. C. Teich, *Fundamentals of Photonics*. The independent
numerical oracle here validates the implemented formulas, not an individual
real laser against measurement.

## Release and safety status

`OPT-001` at D5 is `NUMERICAL_REFERENCE_ONLY` and not registered in a trusted
production `ModelCatalog`. Consequently it cannot authorize a
`WorldEffectLedger` / `ServerEffectCommitCoordinator` energy transfer.
No Minecraft world effects, heat equations, entity damage or Blender model
are produced. **D5 requires a formal JDK25/JUnit/NeoForge build and actual
installation before the engineering milestone can be accepted.**
