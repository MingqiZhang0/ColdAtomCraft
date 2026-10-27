# CAC-M0-R2.1-D8 director scientific and engineering audit

**Review date:** 2026-10-26  
**Input:** frozen D7 ZIP SHA-256 `1a44ccde9e59ce6fbaa6ec9fe812b0a85d76778cc405ebf1c27114f503183c09`.
**Input integrity:** original 287/287 SHA-256 entries verified.  
**Status:** D8 independent optical science reference checkpoint; **NOT**
Minecraft/NeoForge release, M0 CLOSED or Owner-approved production model.

## Engineering result

The D8 optics classes are deliberately separate from world state, damage,
heating, Material registry and NeoForge classes. The `physics-core` retains
Java21 compatibility for the offline reference, while the project itself
requires JDK25 for its actual Gradle/NeoForge build.

### Major scientific findings

1. Three-dimensional specular reflection and Snell refraction are performed
   from a local oriented surface normal, not Blender/world cardinal axes.
2. s/p polarization bases are right-handed and explicitly tied to each ray;
   Fresnel reflection p scalar signs are gauge dependent. E field continuity
   is verified in Cartesian lab coordinates.
3. Fresnel *electric field* transmission coefficients must be multiplied by
   sqrt(n2*cos(theta_t)/(n1*cos(theta_i))) when used with D7 `sqrt(W)`
   modal amplitudes. Without that flux factor energy accounting is wrong.
4. Total internal reflection produces complex unit-magnitude reflected
   coefficients and zero **outgoing traveling-power ray**. An evanescent
   transmitted field exists physically but is NOT represented in D8.
5. A normal flat mirror preserves the local Gaussian q at the reflection
   plane when its new forward propagation direction is used, i.e.
   `q_out=q_in`. Using `-conj(q_in)` would silently produce an unphysical
   phase-conjugated wavefront. Multiple off-axis radial checks verify that
   the mirror's transverse field differs only by its explicit Jones phase.
6. A fully reflecting coated mirror is not one Fresnel dielectric interface;
   D8 uses a distinct ideal phase-defined mirror with explicitly supplied
   s/p phases and no claims about real-coating data.
7. Geometric propagation detects a positive displacement that would round
   to zero at very large double world coordinates. This is not a general
   cure for large-distance interferometric phase precision; local lab
   coordinates and calibrated phases remain required.

### Executed evidence

| Verification | Status | Evidence |
| --- | --- | --- |
| D7 ZIP CRC and source SHA | PASS (287/287) | extraction verification |
| Engineering Java smoke | PASS (383/383) | `verification/M0-R2.1-D8/offline-test-sweep.log` |
| Java test-source method sweep | PASS (255/255) NON-JUNIT | same log |
| D8 standalone ray/Fresnel smoke | PASS (18/18) | `d8-science-full.log` |
| Independent D8 Decimal80 Fresnel cases | PASS (146/146) | `verify_fresnel_reference.py` / `d8-science-full.log` |
| Retained D5/D6/D7 independent comparisons | PASS (36+39+29) | `d8-science-full.log` |
| Python asset + verification-tool tests | PASS (30/30) | `offline-test-sweep.log` |
| Pure Java source strict JDK21 compilation | PASS | `java21-strict.log` |
| JDK25 preflight | **BLOCKED** (actual JDK21) | `jdk25-preflight/report.json` |
| Formal Java25 Gradle/JUnit and new JAR | **NOT RUN** | missing JDK25/repositories |
| D8 NeoForge client/server launch | **NOT RUN** | no D8-built JAR |
| Blender hardware calibration and real model | **NOT RUN** | no Owner assets |
| Laser-material heating / MC damage | **NOT IMPLEMENTED** | not authorized |

The offline reflection harness intentionally uses JUnit annotation/Assert
shims, not a real JUnit Platform. It must NEVER be reported as formal JUnit.

### Open risk boundaries

- A plane-wave central-ray Fresnel approximation is not an exact finite
  Gaussian angular-spectrum interface. Angular Fresnel variation,
  apertures, clipping and astigmatism require separate physical models.
- The `GeometricJonesRay` mode label is a declaration, not a calibrated
  proof of coherent alignment or server permissions.
- Arbitrary two-port beam splitters, real coated mirrors and fiber coupling
  are not modeled by these single-surface equations.
- No evanescent near fields, real complex metal refractive index, thin-film
  interference, laser cooling, atomic state dynamics or absorption.
- The ray-plus-Gaussian adapter must be used ONLY at a known common reference
  plane. Ray.advance and D7 freeSpace must not both add carrier phase.
- Geometry does not own a real world server scheduler and does not grant
  any safety or damage permission. See M0-R2.1-D3/D4 world contracts.
- Reproducible JDK25 Gradle/JUnit/JAR and separate installed runtime evidence
  remain hard gates before full M0 close.

### Director decision

**PASS: pure-Java D8 optics scientific reference and documented test checkpoint.**
**PENDING: formal JDK25/NeoForge build/runtime; Owner production science approval.**

Next useful work: acquire actual mirror/coating optical specifications and
Blender surface normals, implement an experimental optical power detector,
and test the end-to-end device in Minecraft only after Java25 Gradle build.
