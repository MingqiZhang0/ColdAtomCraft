# CAC-OPT-001-D8 — 3D planar reflection, Jones bases and Fresnel reference

**Status:** Independent scientific DRAFT (physics-core), **not** a calibrated
laboratory mirror, production Owner approval, implemented NeoForge block,
measurement apparatus, material absorption or Minecraft world modification.

## Scope and relationship to D5–D7

- D5: scalar vacuum TEM00 Gaussian beam, complex q and ABCD.
- D6: coherent field phasors and Jones polarization.
- D7: complex modal amplitudes in **sqrt(W)**, q and phase, ideal two-port
  coupler and coherent matched-mode optical paths.
- **D8:** a *separate geometric-ray* reference for planar surfaces in 3D,
  a local s/p basis and analytic Fresnel coefficients. Adds a limited adapter
  from a D7 Gaussian **reference plane**, but DOES NOT silently equate a ray
  with a full angular-spectrum treatment of a finite-width Gaussian.

The geometric reference uses laboratory-local metres. A positive ray advance
that disappears at double precision is rejected rather than silently
retaining the old position. This is only a partial numerical guard; use a
local laboratory origin for all interferometric trajectories. No Minecraft global
coordinates, blocks, chunk revisions, Blender file or actual calibration is
inferred. The owner supplies true apparatus and coating parameters later.

## 1. Direction and transverse polarization gauge

`RayFrame3` stores a right-handed orthonormal basis (u, v, k), with
`u cross v = k`, where k is the light propagation direction. The Jones
amplitude is `a_u * u + a_v * v`, with |a_u|²+|a_v|² measured in watts.
All three basis vectors are dimensionless; positions are in metres.

An `OpticalPlane3` has unit normal **N pointing from medium 1 to medium 2**.
The ray MUST enter from medium 1, with `k_i dot N > 1e-12`, and intersect the
plane in the forward direction. For oblique incidence define

    s = normalize(N cross k_i)
    p_j = k_j cross s        for j = incident, reflected, transmitted.

The s/p bases are individually right-handed: `s cross p_j = k_j`.
At exact normal incidence the incidence plane is undefined; the *declared
incoming transverse u axis* is the s gauge. This avoids silently choosing a
world-axis polarization and keeps physical Cartesian fields invariant.
The continuous limit near normal incidence remains a coordinate-gauge issue;
only the Cartesian field and energy, not individual s/p scalar phases, are
basis-independent observables.

The reflected direction is

    k_r = k_i - 2 (k_i dot N) N.

For real positive refractive indices n1, n2, Snell's transmitted direction
when it exists is

    eta = n1/n2
    cos_i = k_i dot N
    cos_t = sqrt(1 - eta²(1-cos_i²))
    k_t = eta (k_i - cos_i N) + cos_t N.

## 2. Single UNCOATED dielectric boundary (NOT a mirror coating)

For an isotropic, non-magnetic, lossless dielectric interface,
`exp(-i omega t)` and the s/p gauge above, the **electric field** amplitude
Fresnel coefficients are

    r_s = (n1 cos_i - n2 cos_t)/(n1 cos_i + n2 cos_t)
    r_p = (n2 cos_i - n1 cos_t)/(n2 cos_i + n1 cos_t)
    t_s = 2 n1 cos_i/(n1 cos_i + n2 cos_t)
    t_p = 2 n1 cos_i/(n2 cos_i + n1 cos_t).

The signs of p reflection depend on the REFLECTED transverse p-axis
convention. Our `p_j = k_j cross s` makes `r_p=-r_s` at normal incidence.
This is NOT a polarization paradox: since `p_r = -p_i` at normal incidence,
the actual Cartesian reflected electric fields have the same physical sign.
The tangential electric fields obey the boundary equation
`E_i,tangential+E_r,tangential=E_t,tangential` (tested).

**Do not confuse electric amplitudes with power-normalized D7 modes.**
Fresnel t_s,t_p alone MUST NOT directly multiply D7's sqrt(W) modal amplitudes.
For n1,n2 real, the modal transmission coefficient additionally carries

    sqrt_flux = sqrt(n2 cos_t/(n1 cos_i))
    a_t,s/p = (sqrt_flux * t_s/p) * a_i,s/p.

The reflected modal amplitude is `a_r,s/p = r_s/p * a_i,s/p`.
Thus `R_j=|r_j|²` and `T_j=sqrt_flux²*|t_j|²`, and for each j,
`R_j+T_j=1`. Both reflected and transmitted ports are preserved.
**One interface's transmitted mode is not the whole multi-bounce field
of a finite plate or cavity.** Further internal reflections need explicit
branch tracing and coherent recombination.

At Brewster incidence from n1 to n2, `tan(theta_B)=n2/n1` and p reflectance
is zero for ideal lossless dielectrics. This is not a coating property.

### Total internal reflection

When `n1 > n2` and `(n1/n2)^2 sin²(theta_i) >= 1`, D8 returns a complex
reflection of unit modulus and **no propagating transmitted-power ray**:

    alpha = sqrt((n1/n2)² sin²(theta_i)-1)
    r_s = (n1 cos_i - i n2 alpha)/(n1 cos_i + i n2 alpha)
    r_p = (n2 cos_i - i n1 alpha)/(n2 cos_i + i n1 alpha).

This does NOT mean the evanescent electromagnetic field in medium 2 is zero.
The current ray API does not calculate its penetration depth, near-field
energy, or frustrated TIR in another nearby interface.

## 3. Ideal calibrated phase-only planar mirror

`IdealCalibratedPlaneMirror` is a separate abstract **100% reflective**
component; it is intentionally not constructed from a single n1/n2 boundary.
Its s/p complex reflection coefficients are `exp(i phi_s)` and
`exp(i phi_p)`, explicitly specified by the caller. The phases are not
inferred from an unknown real multilayer coating. A later laboratory
material/optical database must define wavelength, incidence-angle and
polarization-dependent `r_s,r_p` and any scattering/absorption.

The normal and incident-ray frame select the same output s/p orientation as
the dielectric interface. A reflected ray's optical power is conserved under
this idealization; no light-to-heat mapping is allowed.

### Crucial Gaussian q convention at a FLAT mirror

At an ideal planar mirror, with q defined using the **new outgoing forward
propagation axis** and with the same incident/reference surface plane:

    q_out = q_in     (NOT -conjugate(q_in)).

Why: the transverse complex electric field at the mirror plane must be the
incident field times the (spatially uniform) calibrated reflection coefficient.
The Gaussian radial curvature phase is continuous, not complex-conjugated.
A diverging Gaussian incident on a FLAT mirror remains diverging along the
new outgoing direction. A phase-conjugating mirror, which returns the beam
toward its original waist, is a distinct optical device.

`reflectGaussianModeAtPlane()` accepts the D7 mode only if:
- the modal state and geometric ray are ALREADY at the mirror plane;
- the phase reference, vacuum wavelength and complete Jones amplitudes match;
- the caller gives a DISTINCT outgoing mode-frame key.

It does not propagate a Gaussian from the laser to the mirror; do that with
D7's `freeSpace` (including Gouy phase), then construct the geometric ray
AT that reference plane. Do not call both geometric ray.advance() and modal
freeSpace() and combine both carrier phase advances: that double counts phase.
The monochromatic ray phase advance is only a geometric, local plane-wave
reference for paths without a q state.

For a narrow paraxial Gaussian, a spatially constant local reflection
coefficient is a central-ray approximation; finite aperture, angle-varying
coatings, clipping, astigmatism, distortions and high-NA vector diffraction
are NOT modeled.

## 4. Executable verification and science gates

- 31 new JUnit5-source tests across 3 new test classes (D7 baseline 224;
  D8 source methods 255). Executed with an offline reflection shim under
  Java21, **not** official JUnit Platform.
- Full engineering smoke: 383/383.
- D8 `GeometryOpticsSmoke`: 18 checks.
- Independent stdlib Decimal80 oracle: 146 numerical comparisons across 9
  Fresnel cases and Brewster reference, including supercritical phase.
- D5/D6/D7 independent oracles remain: 36+39+29 scalar comparisons.
- Additional boundary and covariant 3D-Jones tests cover optical power,
  right-handed bases, tangent E continuity, Snell/specular geometry,
  Brewster zero and TIR without invented traveling transmission.
- A reproducible JDK25 preflight reports BLOCKED on this JDK21-only host.
  A full Gradle/JUnit/NeoForge build and actual game startup MUST be tested
  on a separate prepared build host; historical R2 logs do not validate D8.

**Scientific limitations:** No complex refractive-index metals, multilayer
coatings, finite Gaussian angular-spectrum propagation through refracting
surfaces, astigmatic beams, mode-overlap integrals, optical detector response,
evanscent near-field numerical grids, laser-material absorption, world blocks,
entity damage, atomic scattering or Owner-approved production models.

**Authoritative background:**
- RP Photonics, Fresnel Equations: https://www.rp-photonics.com/fresnel_equations.html
- COMSOL Wave/Ray Optics Fresnel explanation and reflection p-gauge note:
  https://doc.comsol.com/6.3/doc/com.comsol.help.roptics/roptics_ug_optics.6.64.html
- COMSOL Fresnel analytic verification (Brewster and R+T=1):
  https://doc.comsol.com/6.3/doc/com.comsol.help.models.rf.fresnel_equations/fresnel_equations.html
- Edmund Optics, Gaussian q and ABCD:
  https://www.edmundoptics.com/knowledge-center/application-notes/lasers/gaussian-beam-propagation/
