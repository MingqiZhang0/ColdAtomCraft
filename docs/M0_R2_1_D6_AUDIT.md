# CAC-M0-R2.1-D6 — Independent Code Director Audit

**Status:** D6 numerical optics draft COMPLETE as an offline checkpoint;
**M0 FORMAL GATE NOT PASSED**. No playable equipment, no Owner-approved
production physics and no Minecraft world mutation are added.

## 1. Starting state and scope

- Input `ColdAtomCraft-M0-R2.1-D5-DirectorCheckpoint.zip` passed archive CRC
  and **242/242** embedded SHA-256 entries, then was copied into a separate
  D6 working directory without altering the archived D5 input.
- The existing D5 mathematical reference implemented scalar CW vacuum TEM00
  Gaussian beams and lossless ABCD q transport, **not** complex field phase,
  Jones polarization or interference.
- D6 extends *physics-core only* with ideal vector-field observables. The
  experiment/world ledger, server permission gates, Minecraft mod entrypoint
  and ModelCatalog's Owner approval authority are unchanged.
- Example optical wavelength `780 nm` in tests is illustrative; no claim that
  it exactly equals a selected Rb-87 hyperfine cooling transition.

## 2. Added engineering components

| File | Role |
| --- | --- |
| `ComplexAmplitude.java` | Finite complex arithmetic; carrier-phase unit phasor |
| `JonesVector.java` | Fully polarized complex two-axis field; normalization intentionally explicit |
| `JonesMatrix.java` | Ideal linear polarizer; ideal quarter/half-wave retarder; ordered multiplication |
| `VectorFieldPhasor.java` | Cartesian complex field, sqrt(W/m²) scale, normalized magnetic cross product |
| `JonesGaussianBeam.java` | CW Gaussian + orthogonal local transverse Jones basis + explicit relative waist phase |
| `CoherentOptics.java` | Coherent field summation, separately defined incoherent averaging, electric/magnetic energy density and leading-order flux |

`beamId` prevents accidentally listing one branch twice. A declared phase
reference and **exact common vacuum wavelength** are required for coherent
addition; the fields must share a physically meaningful phase origin. Neither
string is an authorization credential or proof of experimental coherence.

## 3. Scientific correctness findings

### Jones optics

- Ideal linear polarizer obeys `P_out = P_in*cos²(theta)` for horizontal
  incident light; crossed polarizers extinguish light; insertion of an
  intermediate 45-degree polarizer produces `P_in/4`, not `P_in/8`.
- The ideal retarder has `J = u u^T + exp(i delta) v v^T` in a documented
  `exp(-i omega t)` convention. A quarter-wave plate produces a ±90-degree
  relative phase, while a half-wave plate rotates linear polarization by
  twice the physical orientation. Uniform lossless retarders preserve norm.
- The ideal plate's **global optical phase is not determined by relative
  retardance alone**. The default zero-eigenaxis-phase convention is documented,
  and D6 exposes an explicit uniform path phase for calibrated two-arm
  comparison. A test proves this phase can interchange destructive and
  constructive interference without altering either arm's power.

### Standing-wave Gouy correction

The initial draft test incorrectly assumed `I_total=0` at `lambda/4` for two
opposite, equal Gaussian beams. That plane-wave claim ignores the relative
Gouy phase. The correct ideal Gaussian on-axis result is:

```text
I_e(z) = 4*I_single(z)*cos²(kz - atan(z/zR)),
I_e(lambda/4) = 4*I_single(lambda/4)*sin²(atan(lambda/(4zR))) > 0.
```

The code was not modified to force zero; the **test was corrected** to the
actual Gaussian formula, and an independent high-precision oracle validates
this small but measurable-in-principle correction.

### Electric, magnetic, total energy, and net energy flow

Under the normalized field convention `F=sqrt(epsilon0*c/2)*E_peak`:

```text
I_elec_equiv = |F_total|² = 2c*<u_E>
<u_E> = |F_total|²/(2c)
<u_B> = |sum(k_hat x F)|²/(2c)
<u_total> = <u_E> + <u_B>
<S> = Re(F_total x conj(sum(k_hat x F)))  [W/m²]
```

For two equal counterpropagating modes, the on-axis time-averaged flux
vanishes. At a field antinode, electric energy dominates; at a nearby
plane-wave node, magnetic energy dominates. The combined cycle-averaged
energy density stays `2*I_single(z)/c` to leading paraxial order. This is
**not** equivalent to a net power deposition into blocks.

For non-collinear beam directions, each Jones vector is rotated into the
common Cartesian laboratory frame before summation. The magnetic proxy uses
the central propagation direction of each paraxial beam, so transverse
Poynting components due to wavefront curvature and tight-focus longitudinal
fields are not included.

## 4. Actually executed verification

| Check | Actual result | Evidence |
| --- | --- | --- |
| D5 ZIP CRC and SHA manifest | **PASS 242/242** | input verification |
| Original Java engineering smoke | **PASS 383/383** | `verification/M0-R2.1-D6/d6-offline-final.log` |
| All Java JUnit **source methods via NON-JUNIT shim** | **PASS 194/194** | same log; **NOT formal JUnit** |
| Legacy Gaussian/ABCD smoke | **PASS 21/21** | same log |
| New complex/Jones smoke | **PASS 19/19** | same log |
| D5 independent Decimal 80-digit oracle | **PASS 36/36 scalars** | same log + retained D5 script |
| D6 independent Decimal 70-digit oracle | **PASS 39/39 scalars** | same log + `verify_coherent_reference.py` |
| Python engineering tests | **PASS 30/30** | same log |
| Java21 main-source `javac --release 21 -Xlint:all` | **PASS** with no warnings | `java21-lint.log` |
| JDK25 preflight for actual formal build | **BLOCKED: JDK21 only**, exit 2 | `verification/M0-R2.1-D6/jdk25-preflight/report.json` |
| Full D6 Gradle/JUnit/NeoForge JAR build | **NOT RUN** | no JDK25/dependency access |
| D6 independent packed-JAR client/server load | **NOT RUN** | no D6 JAR generated |
| Owner Blender export and actual render | **NOT RUN** | no supplied Blender asset |
| World effect energy deposit / material heating | **NOT IMPLEMENTED** | physical/permission gates remain separate |

The new `verify_m0.py` additionally rejects a formal JUnit run if any actual
`*Test.java` suite lacks its matching Gradle JUnit XML, even if the total
number of tests is high. This prevents older suites from masking a skipped
Jones/interference test class. The mandatory count is 194, corresponding to
all current JUnit source methods; **actual Gradle XML must still be produced**.

## 5. Limitations, unresolved risks and next engineering Gate

1. The input phase references are **user-provided mathematical values**.
   Their shared key does not establish physically phase-locked hardware.
2. Phase precision is sensitive to position precision at an optical
   wavelength scale. A future apparatus adapter needs local path-length and
   timing references and must not compute phase naively from huge MC global
   coordinates.
3. The `JonesGaussianBeam` model is unbounded along the formal Gaussian axis
   and does not enforce physical shutter, port, aperture or occlusion geometry.
4. A coherent two-arm overlap is **not** a complete energy-conserving optical
   beam-combiner transfer matrix. Interferometer complementary ports, optical
   losses and source/boundary bookkeeping must be implemented before any
   world energy deposition or detector energy accounting.
5. `GaussianPlane` transforms q but not absolute path phase through a lens
   train. It must not be presented as an already phase-calibrated MZ
   interferometer or a complete optical lattice beamline.
6. Partial coherence, source linewidth/phase noise, different carrier
   frequency beats, nonparaxial high-NA vector fields, physical interfaces,
   vacuum/material transport and atomic effects are explicitly excluded.
7. The M0 world's authoritative scheduler, snapshot/revision adapter and
   crash-recovery mechanism remain separate incomplete production work.
8. This host provides OpenJDK 21.0.12.1 but cannot resolve official Gradle,
   NeoForge and Adoptium download hosts. No D6 JDK25/NeoForge build is
   claimed; the previous R2 launch log does not validate D6.

**Next hard Gate:** on an equipped Windows/JDK25 machine run
`python tools/verify_m0.py`, retaining actual JUnit XML and final packaged
JAR hash; then install that exact D6 JAR separately in client and dedicated
server, after honoring all EULA/permissions requirements. M0 final signoff
belongs to Owner after code-director review.

**Next scientific Gate:** Owner supplies output-port-to-waist transform,
polarization axes, source coherence/linewidth assumptions and hardware phase
reference. Until then the OPT-001 CW/Jones calculations remain verified
**reference equations**, not a certified experimental apparatus.
