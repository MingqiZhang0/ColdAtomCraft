# Cold Atom Craft — M0-R2.1-D5 Independent Code Director Audit

**Date:** 2026-10-26  
**Input archive:** `ColdAtomCraft-M0-R2.1-D4-DirectorCheckpoint.zip`  
**Work performed:** independent local source extension, not a DeepSeek result.  
**Disposition:** D5 OPT-001 **numerical draft reference** passes offline validation;
M0 formal JDK25/Gradle/NeoForge and independent installation **not yet passed**.

## 1. Verified frozen input

- Extracted the D4 ZIP into a new `ColdAtomCraft-M0-R2.1-D5` directory;
  preserved the D4 archive and prior history.
- Recomputed the **221/221** embedded D4 SHA-256 manifest entries; all matched.
- This is intentionally still `mod_version=0.0.1-dev`; D5 is not a published
  mod release and does not introduce a source of production physical authority.

## 2. Rationale for the scope change

D4 is rich in independent server scheduling/contracts but has no real optics.
Current Linux runtime provides only **JDK 21** and cannot resolve the official
Gradle/NeoForge/Adoptium download hosts. Rather than add an untestable
`ServerLevel` adapter or another purely abstract scheduler, D5 implements the
Owner-selected standard laboratory Gaussian optics at a separate *numerical
reference* level. It does not merge that reference with world energy effects.

## 3. Source implementation

`physics-core/src/main/java/dev/coldatomcraft/physics/optics/`:

- `GaussianBeam`: scalar CW Gaussian TEM00 profile in vacuum with a precisely
  located physical waist, normalized 3D propagation axis, Rayleigh range,
  divergence parameter, transverse irradiance, encircled power, curvature,
  Gouy phase and q at signed axial coordinates; SI units throughout.
- `GaussianQ`: complex q with strictly positive imaginary part; q-derived
  beam radius and new waist radius.
- `ParaxialAbcd`: dimensionally documented determinant-1 free-space/ideal
  thin-lens ray transfer; correct composition order and q transport.
- `GaussianPlane`: optical power + q at a transverse plane, propagating
  unchanged power through the *lossless* optical reference elements and
  yielding observable plane irradiance and encircled power.

**Important scientific boundary:** The physical waist location is not inferred
from the Minecraft emitter output block/port. No polarization, frequency noise,
complex coherent interference, absorption, atom scattering, real-life optical
coatings, near-field diffraction, or damage is implied. No Owner-approved
`ModelCatalog` entry was created. The existing equipment and world interfaces
were deliberately not modified.

## 4. Independently reproduced numerical cancellation defect

The first draft of q transport computed its imaginary part from a difference
of near-equal floating-point products after Smith-style complex division.
Verification-only case with `q_in = 1e16 + i` and a thin lens `f=1 m`:

```
naive Im(q_out)=-0.0
D5 robust Im(q_out)=9.999999999999999E-33
PASS: catastrophic cancellation reproduced; determinant formulation avoids it
```

The physical/mathematical identity for real ABCD coefficients is:

`Im(q_out) = (AD - BC)*Im(q_in) / |C*q_in + D|^2`.

The production implementation now uses this positive-definite invariant for
its imaginary part rather than subtracting nearly equal terms; its real part
uses a scaled complex division. A dedicated JUnit-source regression also checks
that ideal thin-lens transmission preserves beam radius at its plane.
**This extreme case is a numerical stability test, not evidence that a
highly nonparaxial beam would physically obey Gaussian optics.**

## 5. Executed evidence

| Check | Observed result | Evidence |
| --- | --- | --- |
| D4 frozen manifest | PASS 221/221 | Input archive rehash |
| Pure Java legacy engineering smoke | PASS **383/383** | `verification/M0-R2.1-D5/d5-offline-final.log` |
| OPT-001 analytic smoke | PASS **21/21** | same log |
| JUnit5 **source** methods executed by NON-JUNIT reflection shim | PASS **171/171** | same log; **not** JUnit Platform |
| Python unit tests, including archive/JUnit parser tools | PASS **28/28** | same log |
| Independent 80-digit `decimal` oracle | PASS **36/36 scalar checks** (6 Gaussian × 4, 3 ABCD × 2, 1 optical plane × 6) | same log; `verify_optics_reference.py` |
| Cancellation before/after reproduction | PASS | `abcd-imaginary-cancellation.log` |
| JDK25 preflight | **BLOCKED**, Java 21.0.12.1 | `jdk25-preflight/report.json` |
| D5 real JDK25/Gradle/JUnit | **NOT RUN** | JDK25 and dependency access missing |
| D5 NeoForge JAR build | **NOT RUN** | same |
| D5 packaged-JAR client/server loads | **NOT RUN** | no D5 distributable JAR |
| D5 in-game laser, Blender render and material damage | **NOT IMPLEMENTED** | separate scientific/hardware gates |

`run_d5_offline.sh` invokes all test methods under the historical *test shim*.
The official Windows/CI `tools/verify_m0.py` is updated to require **171 actual
JUnit XML results** and to run the independent optics reference oracle first;
this has not been executed under JDK25 in the current container. Neither the
legacy R2 game screenshots nor R2 JAR can validate any D5 behavior.

## 6. Correctness and risk review

**Supported mathematical statements:** free-space TEM00 normalization,
Rayleigh-range and waist relations; signed propagation; ideal same-medium
lossless Gaussian ABCD transformation; q-derived output radius; scalar
irradiance/encircled power on an ideal reference plane. Independent numerical
checks cover multiple experimental parameter sets and an extreme q condition.

**Not covered:** finite-aperture diffraction or clipping (the reported
`powerWithinRadius` is a *measurement integral*, not a propagated truncated
beam), realistic lens thickness/transmission, beam quality M², aberrations,
polarization, relative optical phase and interference, material energy uptake,
atom-field interaction, Minecraft simulation time or experimental instrument
noise. The paraxial divergence ratio is exposed, not given an arbitrary Owner
acceptance threshold. No model should be treated as production-certified until
these applicability choices are resolved.

**Engineering trust boundary:** Physics Core remains independent from NeoForge,
Game code and user Blender assets. `ModelCatalog` and the authoritative server
commit gate are unchanged. There is no new path to authorized world mutation.

## 7. Next requested Gate

1. Obtain a real JDK25 + Gradle/NeoForge-capable environment.
2. Run `python tools/verify_m0.py` from **this D5 source**, checking actual
   JUnit5 XML, packaging of every physics-core/experiment-core class and D5 JAR.
3. Independently install the **same built JAR** in the pinned 26.3 / NeoForge
   26.3.0.99 client and dedicated server; record exact hashes, logs and results.
4. Ask Owner for actual first-laser waist/output-port geometry and target
   device optical parameters before implementing source-to-world beam rendering.
5. Begin OPT-002 surface physics, polarization/interference and later thermal
   coupling only with separate reviewed physical contracts. Do not map joules
   directly to Minecraft health without an approved calibration model.

**Director decision:** D5 numerics APPROVED AS OFFLINE DRAFT REFERENCE ONLY;
**M0 NOT CLOSED**, actual game integration and formal build Gate remain open.
