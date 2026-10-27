# CAC-OPT-001-D9 — Ideal finite-aperture optical power observation

**Scientific status: DRAFT numerical reference, not Owner-approved instrument
calibration or NeoForge gameplay.** This document describes what the code
actually computes, not capabilities of a fabricated photodiode or Blender asset.

## 1. Why the D7/D8 power value is not a power-meter reading

`ParaxialModeField.powerWatts()` is the integral of an ideal matched TEM00 mode
over an infinite transverse reference plane, not the optical power incident on
an arbitrarily sized/misaligned receiver. D8's `GeometricJonesRay.powerWatts()`
does not carry a finite transverse profile at all. Taking either value as an
arbitrary finite detector reading would erase important alignment effects.

A real optical power meter has wavelength-dependent responsivity, beam-size
acceptance, linearity, damage thresholds, finite electronic bandwidth and
traceable calibration uncertainties. None of those empirical parameters are
known for Owner's future Blender instrument; they are NOT invented here.

## 2. Gaussian ideal receiver aperture

The incident monochromatic paraxial TEM00 irradiance is

    I(r) = 2 P / (pi w^2) * exp(-2 r^2 / w^2),     [W/m^2]

where `w` is the **1/e^2 intensity radius at the detector plane**, not
necessarily the initial waist `w0`. A centred circular entrance aperture of
radius `a` collects

    P_collected(d=0) = P * (1 - exp(-2 a^2 / w^2)).

For lateral displacement `d` of the aperture centre from the beam axis,

    F(a,d,w) = (2/(pi*w^2)) * integral_{|r-d|<=a} exp(-2*r^2/w^2) d^2 r
    P_collected = P * F.

The exact CDF can also be expressed as the noncentral-chi-square distribution
with 2 degrees of freedom, noncentrality `4(d/w)^2` and threshold `4(a/w)^2`.
An independent 80-digit Decimal Poisson/Gamma-mixture implementation in
`verification/M0-R2.1-D9/verify_detector_reference.py` checks the Java code.

The Java implementation uses the angular fraction of each irradiance ring,
combined with 96-point Gauss-Legendre quadrature and a `sin^2` endpoint
transformation. The centred case is analytic and uses `-expm1` for accuracy.
The radial tail beyond `9 w` carries less than `exp(-162)` of total power.
Precision is independently tested over 26 reference cases, including large
nearly tangent apertures; it is NOT a theorem guaranteeing arbitrary extreme
IEEE-754 input ranges.

**Illustrative standard-reference values** for `P=20 mW`, `w=0.5 mm`:

| aperture radius | lateral offset | collected power |
| --- | --- | --- |
| 0.5 mm | 0 | approx 17.2933 mW |
| 0.5 mm | 0.5 mm | approx 7.9300 mW |

`GaussianCircularAperture.Capture` records incident, collected and uncollected
optical power. The uncollected portion is an *optical bookkeeping deficit*;
it is NOT asserted to be absorbed heat or lost to an in-world block.

## 3. Geometry and incident-side convention

`IdealCircularDetectorFace` takes a lab-local face centre, a front-facing unit
normal and positive radius. The normal points **towards the incoming light**;
the supported normally incident configuration requires `n dot k = -1` up to
roundoff tolerance. Tilted faces and backside pickup are rejected rather than
silently evaluated using a wrong normal-incidence Gaussian disk formula.

Detector centre and beam waist position are specified in **laboratory-local
metres**, never Minecraft block coordinates. A coordinate ULP larger than the
relevant beam/aperture feature size is rejected; precise local-origin
conversion must be handled by a future instrument-placement adapter.

The Gaussian waist may geometrically lie before or after the reference plane;
no source emission port or intervening occluder is inferred. A real apparatus
adapter must additionally validate that a source is actually emitting toward
this face and that no block or optical component interrupts the path.

## 4. Instrument observation, interference and time

`IdealOpticalPowerMeter.sampleMode` accepts a D7 matched Gaussian output mode
and a supplied transverse detector offset at **that same reference plane**.
It takes the finite-aperture integral of the mode's total optical power,
including the effect of its complex Jones amplitude and coherent two-port
interference already computed by the optical path solver.

`sampleAtFace` accepts D5 `GaussianBeam` and a D9 oriented detector face.
It requires exact declared equality of the meter/face aperture radius; it
does not infer an optical-world conversion factor.

Both return a `Measurement` with observable
`ideal_collected_optical_power`, unit `W`, a nonnegative simulation timestamp,
and `standardUncertainty=0` **only as the ideal deterministic model
convention**. Zero here does NOT certify a real device uncertainty, nor does
this discrete reading model provide detector bandwidth, time averaging,
noise, photodiode current, voltage or saturation.

The observation is **noninvasive**: querying a modal state does not consume
its optical power. Do not represent a physical absorbing in-line photodiode
without explicitly modelling the optical termination, reflection/absorption,
and energy conservation. Likewise, counterpropagating field `|E|^2` is NOT a
power meter's absorbed optical power; this module only accepts single matched
travelling-mode ports or a normally incident single Gaussian beam.

## 5. Exclusions and hard follow-up gates

- No calibrated photodiode, spectral/electrical responsivity or NIST-traceable
  uncertainty estimate.
- No finite-angle vector diffraction, oblique collection, angled/curved
  detector, occultation, pixel response or clipping propagation downstream.
- No detector modelling for a D8 `GeometricJonesRay` lacking a finite Gaussian
  mode. D8 Fresnel geometry must be explicitly associated with a valid mode
  before aperture power can be calculated; the current project does not
  silently treat a ray as a finite beam.
- No multi-mode overlap integral, spatially mismatched coherent beams,
  finite-bandwidth detector signal or photodetection shot-noise model.
- No real Minecraft detector block, Blender asset, registered Owner-approved
  production model, world heating or damage.

## 6. Verification and status

- D8 frozen manifest independently checked before starting D9.
- New D9 JUnit5 test source covers formula limits, offset, numeric bounds,
  3D face geometry, backside/tilt rejection, coordinate precision, MZI two-port
  readings, noninvasive observation and invalid inputs.
- D9 standalone Java21 smoke checks the physics/experiment bridge.
- 26 independently computed Decimal80 noncentral-chi-square CDF reference cases.
- Full JDK25 Gradle/JUnit/JAR and NeoForge game integration remain separate,
  **not inferred** from Java21 offline checks.

**References:**

1. N. R. Barbeau, *Power deposited by a Gaussian beam on a decentered circular
   aperture*, Applied Optics 34, 6443–6445 (1995), DOI 10.1364/AO.34.006443.
2. *Low-cost Gaussian beam profiling with circular irises and apertures*,
   Applied Optics 58, 1048 (2019), DOI 10.1364/AO.58.001048.
3. NIST SP 250-75, *cw Laser Power and Energy Calibrations at NIST*,
   https://doi.org/10.6028/NIST.SP.250-75 (detector calibration and uncertainty
   are separate real instrument problems, NOT implemented in D9).
