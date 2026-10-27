# CAC-M0-R2.1-D9 — Code director scientific and engineering audit

**Date:** 2026-10-26

**Scope:** Pure Java reference for ideal finite-aperture optical power
observations, based on SHA-verified frozen D8. Does NOT authorize a calibrated
hardware model, Minecraft damage or M0 release.

## Baseline and immutable artifacts

- D8 input: `ColdAtomCraft-M0-R2.1-D8-DirectorCheckpoint.zip`.
- D8 input SHA-256: `63fc3421bb23cf4227528175f1c9f8ee80d5310758bc0584e1460e1ab2369268`.
- D8 preflight: **313/313** SHA-256 entries matched and ZIP CRC clean.
- Version pin unchanged: Minecraft 26.3, NeoForge 26.3.0.99, JDK25 required,
  Gradle 9.2.1, ModDevGradle 2.0.148, official `disableRecompilation` mode.

## D9 production code

1. `GaussianCircularAperture`: bounded power budget; centred analytic Gaussian
   disk integral plus 96-point transformed Gauss-Legendre integral for lateral
   offset, independently checked against a noncentral chi-square CDF.
2. `IdealCircularDetectorFace`: explicit lab-local 3D centre, front-face
   normal, aperture radius; rejects reverse/oblique incidence and clearly
   unresolvable absolute-coordinate input.
3. `IdealOpticalPowerMeter`: stable instrument identity, ideal collected W,
   simulation timestamp, explicit zero *model* standard uncertainty and
   captured-versus-uncaptured budget. It does not mutate the optical field or
   Minecraft and makes no claim of hardware calibration.

The integrator's 9*w cutoff omits less than exp(-162) of normalized tail
power; general IEEE754 error certification is out of scope. The D8 ray-only
state lacks a finite beam profile; a matched D7 Gaussian mode or a D5 Gaussian
source is required for aperture sampling.

## Independent local evidence

| Scope | Status | Evidence |
|---|---|---|
| D8 frozen integrity | PASS 313/313 | SHA inspection before development |
| Pure Java engineering smoke | PASS 383/383 | `verification/M0-R2.1-D9/d9-all-smokes-final.log` |
| D5 Gaussian smoke | PASS 21 | same |
| D6 coherent smoke | PASS 19 | same |
| D7 path smoke | PASS 19 | same |
| D8 geometric Fresnel smoke | PASS 18 | same |
| D9 ideal detector smoke | PASS 17 | same |
| Entire Java JUnit *SOURCE* executed in independent offline shim | PASS 287/287, **NOT FORMAL JUNIT** | `d9-java-offline-final.log` |
| Python engineering tests | PASS 30/30 | `d9-python-tools-final.log` |
| D5 Decimal80 oracle | PASS 36 | `d5-oracle-retained.log` |
| D6 Decimal70 oracle | PASS 39 | `d6-oracle-retained.log` |
| D7 Decimal80 oracle | PASS 29 | `d7-oracle-retained.log` |
| D8 Decimal80 Fresnel oracle | PASS 146 | `d8-oracle-retained.log` |
| D9 independent Decimal80 aperture oracle | PASS 26/26 | `d9-decimal80-final.log` |
| D9 optional SciPy seeded noncentral-chi-square sweep | PASS 1,500 pairs, worst abs error ~1.09e-13 | `d9-optional-scipy-final.log` |
| Java21 `--release 21 -Xlint:all -Werror` | PASS | `d9-all-smokes-final.log` (javac command in run script) |
| JDK25 formal preflight | BLOCKED, exit 2 (only OpenJDK21) | `jdk25-preflight/report.json` |
| Actual JDK25 Gradle/JUnit and distributable D9 JAR | NOT RUN | No JDK25 / dependency network here |
| D9 NeoForge client/server launch and Blender device | NOT RUN / NOT IMPLEMENTED | No D9 compiled mod JAR or owner equipment |

One chained D5–D9 offline script attempt was interrupted by this runtime
without a completed result; it is **not** counted as PASS. The individual
smokes and reference-oracle scripts were then independently rerun and passed,
with logs archived above.

## Numerical results and performance

For ideal 20 mW Gaussian at a detector where `w=0.5 mm`, with an aperture
radius of `a=0.5 mm`:

- Coaxial `d=0`: F=1-exp(-2) ~= 0.864664716763, P_collected ~= 17.2932943353 mW.
- Decentred `d=0.5 mm`: F ~= 0.396499039388, P_collected ~= 7.92998078776 mW.

A limited Java21 exploratory microbenchmark (100k samples with varying
offsets, warm-up) observed roughly **59 ns/sample** for the centred analytic
path and **4.95 microseconds/sample** for the offset quadrature path on this
host. These numbers are not JMH-calibrated and do NOT imply any Minecraft TPS
or production throughput. Avoid per-particle/per-tick unbounded sampling.

## Remaining engineering/science gates

- No Owner-specified sensor wavelength response, quantum efficiency,
  electrical transfer curve, calibration standard, bandwidth, uncertainty,
  thermal properties, saturation or damage threshold.
- No detector block registration, real face material, optical interception,
  real world occlusion, Blender port transform or calibrated MC-to-metres scale.
- `Measurement.standardUncertainty=0` is an ideal deterministic observation
  convention; never treat it as measured zero uncertainty.
- No physically absorbing in-line power meter; the ideal power probe is
  noninvasive and must not be inserted as a power sink in a simulation without
  a separate approved power/energy transfer model.
- No multi-mode interference integral, unmatched spatial modes, oblique
  collection, finite pixelated detector or time-dependent optical response.
- M0 formal JDK25 Gradle/JUnit/JAR verification and **D9** game runtime must
  be executed on a prepared real JDK25 build host. Prior R2 startup logs are
  not evidence for this version.
- `ModelCatalog` receives **no production OWNER_APPROVED model**. The Owner
  retains scientific and final milestone signoff authority.

## Director decision

**D9 numerical reference: REVIEW READY (offline checks PASS).**
**M0 formal gate: OPEN / JDK25 BLOCKED.**
**D9 production game detector: NOT IMPLEMENTED.**

Next engineering action on a prepared host: run `python tools/verify_m0.py`,
archive formal JUnit XML, inspect D9 JAR, then independently load it in a
Minecraft 26.3 + NeoForge 26.3.0.99 instance. After that, attach a user-owned
Blender detector mesh to a real world optical input anchor and validate
geometry/scale; do not fabricate calibration values.
