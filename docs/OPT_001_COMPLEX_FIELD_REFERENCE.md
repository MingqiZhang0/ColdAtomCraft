# OPT-001/CW-POL — Jones-polarized Gaussian phasors and coherent superposition (D6)

**Scientific standing: DRAFT NUMERICAL REFERENCE, NOT OWNER_APPROVED.**

The Owner chose **standard experimental optical models**. D6 extends the D5
continuous-wave (CW), TEM00, scalar **vacuum paraxial** Gaussian reference with
fully polarized Jones fields, uniform ideal polarization optics and coherent
field superposition. The extra components do **not** constitute an experiment-
calibrated instrument, a time-dependent atom–light model, a heat/absorption
calculation or a Minecraft world effect. No production ModelCatalog entry or
owner approval flag has been inserted.

## 1. Frames, units, phase conventions

- All positions, waist centres and beam direction vectors are in the **local
  laboratory frame**, in SI metres, NOT Minecraft block coordinates.
- Each `JonesGaussianBeam` declares a **beamId** (physical branch identity), a
  **phaseReferenceKey** (shared ideal carrier/time-phase origin), a D5
  `GaussianBeam`, a transverse-X laboratory unit direction and a Jones vector.
- The local right-handed basis is `e_x`, `e_y = k_hat x e_x`, `k_hat`.
  `e_x` must be orthogonal to the propagation axis; we reject incompatible
  frames rather than silently projecting an invalid vector.
- Jones amplitudes `Jx, Jy` are **dimensionless complex amplitudes**, with
  `|Jx|² + |Jy|² <= 1` for the passive reference. Physical remaining beam
  power after spatially uniform polarization optics is
  `P_source * (|Jx|²+|Jy|²)`.
- The peak-field phasor has the **`exp(-i omega t)` convention**. The phase at
  the waist `phi_0` must be referenced to a common physical time origin.
  The `phaseReferenceKey` is a declaration, not evidence that two sources are
  actually phase-locked.
- The internally stored Cartesian electric phasor `F` is scaled to units
  `sqrt(W/m²)` by `F = sqrt(epsilon_0*c/2) E_peak`, with `c = 299792458 m/s`.
  For a single paraxial travelling beam `|F|²` equals its irradiance.

## 2. Standard Gaussian complex field

At signed propagation coordinate `z`, transverse radius `r`, vacuum wave
number `k=2*pi/lambda`, waist `w0`, and Gouy phase `psi=atan(z/zR)`:

```text
phi(r,z) = k*z + k*r*r/(2*R(z)) - psi(z) + phi_0
F(r,z) = sqrt(2*P_source/(pi*w(z)^2)) * exp(-r*r/w(z)^2)
         * exp(i*phi(r,z)) * (Jx*e_x + Jy*e_y).
```

The `kr²/(2R)` term is zero at the waist `z=0`; our implementation computes
this term using a factorized expression to avoid unnecessarily squaring large
numbers. The rapid `kz` phase is reduced modulo `lambda` before multiplication
by `2*pi`. **This does not recover optical-phase precision absent from a
floating-point position supplied by the caller**. In particular, phase
sensitivity at large world coordinates requires an appropriate laboratory
local origin and/or an improved relative optical-path representation.

The field is a **mathematical paraxial propagation solution** at both positive
and negative `z`; it is not a claim that a physical laser source emits through
its housing or blocks behind its output port. Geometry, source aperture and
shutter must be enforced by future hardware/world adapters.

## 3. Ideal Jones elements

For linear polarizer angle `theta`, `u=(cos(theta),sin(theta))`, the ideal
projection matrix is `P=u*u^T`. A horizontally polarized input transmits
`cos²(theta)` of its power (Malus law).

For an ideal lossless thin retarder whose selected axis is `u`, perpendicular
axis `v=(-sin(theta),cos(theta))`, and **perpendicular relative phase +delta**:

```text
J(theta, delta) = u*u^T + exp(i*delta) * v*v^T.
```

`delta = pi/2` and `pi` correspond to quarter-wave and half-wave retardance.
The sign convention is tied to `exp(-i omega t)`, so helicity/left-vs-right
circular labels are deliberately avoided until a globally oriented
polarization/Zeeman convention is approved. The two-argument ideal retarder
fixes the selected eigenaxis phase at zero **only as a mathematical gauge
convention**. A real plate also contributes an unknown common propagation
phase depending on thickness and index. The three-argument `idealRetarder`
overload or `withUniformPhaseRadians` explicitly supplies that common path
phase when calibrated. **A waveplate's relative retardance alone does not
predict the absolute phase difference between two interferometer arms.**

These ideal elements have no finite aperture, refractive-index interface,
absorption-dependent heat or frequency change. The general record constructor
exists for composing reference matrices; only the named factories describe
ideal passive optical elements. A `JonesGaussianBeam` refuses Jones amplitudes
that imply appreciable gain (amplitude norm greater than unity).

## 4. Coherent interference, incoherence and energy transport

For fully coherent waves with the **same exact carrier wavelength** and
compatible declared phase origin:

```text
F_total = sum_j F_j
G_total = sum_j (k_hat_j x F_j)  [leading-order vacuum magnetic proxy]
I_electric_equivalent = |F_total|²
<S> = Re(F_total x conjugate(G_total))                 [W/m²]
u_E,avg = |F_total|² / (2*c)                         [J/m³]
u_B,avg = |G_total|² / (2*c)                         [J/m³]
u_total,avg = u_E,avg + u_B,avg.
```

**Crucial factor of two:** `|F|²` is **two times c times** the cycle-averaged
*electric* energy density, not `c*u_E`. For a single travelling plane wave,
`u_E = u_B = I/(2c)` and `u_total = I/c`.

For mutually incoherent / phase-averaged sources, **not** field amplitudes,
but the independent electric intensity equivalents add. The helper demands
distinct phase reference keys rather than silently averaging sources that
claim coherence. A partially coherent field, nonzero optical linewidth,
beating of different frequencies or a nonstationary relative phase requires
its own mutual coherence/correlation or time-dependent model; not implemented.

**No general source power budget follows solely from phasor addition.**
Mathematically superposing two same-mode, same-direction, same-phase fields of
input power `P` gives local `4*I`, and integration of that constructed output
would give `4P`. This is NOT an energy-conserving two-input interferometer
simulation: the other output ports and boundary conditions of a real combiner
must be modeled before accounting for energy transfer. D6 forbids using the
coherent field aggregate as a direct material-energy/damage authorization.

### Standing wave versus net energy flux

Two ideal, equally powered, oppositely travelling, same-linearly-polarized
Gaussian beams with co-located waists and relative reference phase zero give
on-axis:

```text
|F_total(z)|² = 4*I_one(z)*cos²(k*z - psi(z))
<S_z> = 0.
```

At the naive plane-wave node `z=lambda/4`, the Gaussian Gouy phases lead to a
small but nonzero residual `4*I_one(z)*sin²(psi(z))`. Simultaneously, magnetic
energy predominates there; the **total** cycle-averaged electromagnetic energy
density remains `2*I_one(z)/c` under the local paraxial approximation. A
standing wave's large `|F|²` at antinodes does NOT indicate net energy flow
from one side. This matters for optical lattice potential (sensitive to
`|E|²`) versus thermal/absorptive power budgets (requiring flux and coupling).

For non-collinear beams the Cartesian vector amplitudes are summed after
rotating each local Jones basis into the common laboratory frame; merely
adding separate local `(Jx,Jy)` values would be incorrect. The approximate
magnetic field uses the central beam direction; it ignores the small radial
energy flow and longitudinal field components of a finite-divergence Gaussian.
The actual local Poynting vector should not be treated as experimentally
calibrated in tight-focus/high-NA scenarios.

## 5. Program and correctness boundaries

| API | Meaning |
| --- | --- |
| `ComplexAmplitude` | finite complex arithmetic and optical phase |
| `JonesVector` | transverse complex field coefficients; not automatically normalized |
| `JonesMatrix` | uniform ideal linear polarizer and thin retarder, ordered composition |
| `JonesGaussianBeam` | passive branch with explicit waist phase, transverse frame and coherence group |
| `VectorFieldPhasor` | lab-Cartesian complex electric field in sqrt(W/m²) |
| `CoherentOptics` | same-carrier coherent phasor addition; distinct-group incoherent intensity; energy density and leading-order Poynting |

- `beamId` duplicate checks prevent accidental mathematical double counting of
  one listed branch. They are **not authentication or server asset ownership**.
- `phaseReferenceKey` and `phaseAtWaistRadians` are scientific inputs; merely
  using the same string cannot physically synchronize lasers.
- There is no absolute optical-phase propagation through a general D5 ABCD
  train yet. Thin lenses transform `q`, but their actual placement, optical
  path phase and transmission are not modeled by D6 interference. Do not infer
  that `GaussianPlane` can automatically be combined with a coherent
  `JonesGaussianBeam` after arbitrary optical elements.
- There is no beam clipping, thin-film Fresnel transmission/reflection,
  partial coherence, spectral width, pulse propagation, vectorial high-NA
  focusing, atomic susceptibility, external field, temperature or world damage.
- No NeoForge renderer, MC unit conversion, client-authorized data,
  instrument readout nor Owner-approved `ModelCatalog` entry is added.

## 6. Verification performed

- Source JUnit 5 tests: 9 `JonesMatrixTest` methods and 14
  `CoherentOpticsTest` methods; count is **23 new methods**.
- Offline *non-JUnit* assertion sweep: 194/194 methods including all older
  modules. This is a standalone shim and not a real JUnit Platform result.
- Dedicated standalone `CoherentSmoke`: 19/19 checks.
- A new **independent Decimal(70 digits)** Python oracle evaluates 39 scalar
  quantities from Malus/retarder, Gaussian/Gouy interference and Maxwell
  energy/flux relations; `verification/M0-R2.1-D6/verify_coherent_reference.py`.
- D5's 36 Decimal(80 digits) Gaussian/ABCD oracle comparisons are retained.
- Java `javac --release 21 -Xlint:all`: pure physics and experiment sources
  compile without warnings. JDK 21 is available here; formal **JDK25 JUnit,
  Gradle build, NeoForge JAR, real client/server run** are independent pending
  gates, not verified by this document.

## 7. Reference literature

- RP Photonics, *Gaussian Beams*, https://www.rp-photonics.com/gaussian_beams.html
- TU Delft, *Interactive Optics — Polarisation* (Jones matrices),
  https://interactivetextbooks.tudelft.nl/interactive-optics/content/Chap4_Polarisation/Polarization_2022_01Clean.html
- Matrix-Optics Reference Tables, *Ideal Jones Elements*,
  https://optics.engineering/knowledge_base/worked_exercises/introduction_matrix_methods_optics/reference_tables.html
- B. E. A. Saleh and M. C. Teich, *Fundamentals of Photonics*;
  A. E. Siegman, *Lasers* (textbook background).

**Director status:** optics reference may advance to independent formal
JDK25/JUnit verification; it remains SCIENTIFIC DRAFT / NOT OWNER_APPROVED and
has no permission to deposit energy into a Minecraft world.
