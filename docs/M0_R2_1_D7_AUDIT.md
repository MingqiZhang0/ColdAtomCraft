# CAC-M0-R2.1-D7 — director engineering and scientific audit

**Date:** 2026-10-26 (user-local project dating)

**Input:** D6 frozen checkpoint, ZIP SHA-256
`ee32c94ac0093cf170e7e32edddfc64196b8e86be03c4e2234803bfb2526a122`;
**267/267** manifest entries verified before changes.

**Current status:** PHYSICS-CORE OPT-001 DRAFT REFERENCE / offline tests PASS;
M0 D7 JDK25 Gradle/JUnit/JAR build **BLOCKED**, D7 actual NeoForge
client/server runtime **NOT RUN**, official scientific Owner approval **PENDING**.
No production `ModelCatalog` approvals were added.

## Problem and approach

D5 supplied paraxial Gaussian `q` and optical power, and D6 introduced
complex phasors and Jones polarization. However, transporting `q` through
ABCD alone does not determine the carrier and accumulated Gouy phase; and
naively summing two arm electric fields without the unused output port can
misrepresent an interferometer's energy budget.

D7 introduces a separate **reference-plane, matched-mode** optical layer:

1. `ParaxialModeField` records `q`, λ and complex Jones modal amplitudes in
   `sqrt(W)` with explicit coherence and modal-frame labels. Its free-space
   transfer adds modulo-wavelength carrier and Gouy phase, its ideal thin
   lens updates `q` and requires an explicitly calibrated on-axis phase, and
   passive Jones elements preserve or reduce modal power.
2. `IdealTwoPortBeamSplitter` implements the lossless reciprocal symmetric
   unitary 2x2 scattering convention with *both* outputs retained.
3. `OpticalPathTrace` provides an immutable sequential path history and
   a tracked-mode loss tally. Loss from the selected mode is **not** heat.

A declared phase/mode label is not evidence of real alignment. The two-port
model strictly rejects mismatched `q`, λ, phase references and mode-frame
labels. A physically misaligned or multimode path is explicitly outside D7.

## Physical numerical benchmarks

Test inputs for the principal reference: λ=780 nm, waist w0=0.5 mm and
P=20 mW, with Rayleigh range ≈1.006920722304421 m.

- Free propagation is power preserving; q grows by signed forward path L.
- A single lossless lens leaves on-plane spot and power unchanged, modifies
  wavefront curvature and does not silently add a common interferometric phase.
- A polarizer at 45 degrees removes half the horizontal input's *tracked-mode*
  power, without assigning that deficit to actual material absorption.
- At a balanced Mach-Zehnder, equal arms give a dark and a bright output.
  With additional arm phase Δ=π/3, powers are **5 mW and 15 mW**,
  respectively, summing to the 20 mW input; Δ=π switches ports.
- Nonbalanced and random-complex-Jones two-port inputs preserve total power.
- `inputPathUlpPhaseRadians` flags loss of sub-wavelength phase resolution
  when lengths are represented by coarse floating-point world coordinates.

## Verification evidence

| Gate | Result | Evidence |
| --- | --- | --- |
| D6 ZIP and original file manifest | **PASS 267/267** | before source extraction |
| Existing pure Java engineering smoke | PASS 383/383 | D7 `offline-final.log` |
| Full source-method sweep with offline assertion shim | **PASS 224/224** | NOT real JUnit; D7 `offline-final.log` |
| D5 Gaussian smoke | PASS 21/21 | D7 `offline-final.log` |
| D6 coherent smoke | PASS 19/19 | D7 `offline-final.log` |
| D7 optical path smoke | PASS 19/19 | D7 `offline-final.log` |
| Independent D5 Decimal80 oracle | PASS 36 scalar values | D7 `offline-final.log` |
| Independent D6 Decimal70 oracle | PASS 39 scalar values | D7 `offline-final.log` |
| Independent D7 Decimal80 oracle | **PASS 29 scalar values** | `verify_optical_path_reference.py` and D7 `offline-final.log` |
| Python engineering/asset tests | PASS 30/30 | D7 `offline-final.log` |
| JDK21 `javac --release 21 -Xlint:all` production sources | PASS, no warnings | `java21-lint.log` |
| JDK25/Gradle/JUnit build and packaged D7 JAR | **BLOCKED / NOT RUN** | JDK25 preflight `report.json`; host has JDK21 |
| D7 NeoForge 26.3 actual client/server or separate installed JAR | NOT RUN | no D7 JAR exists |
| Blender real model export and hardware calibration | NOT RUN | no Owner asset or setup supplied |
| World laser damage, atomic cooling or heat deposition | NOT IMPLEMENTED | separate Owner scientific gates |

Prior R2 development world/server logs are retained solely as historical
baseline evidence; they do **not** validate D7 modified source.

## Major limitations and future gates

- `ParaxialModeField` carries a **local** optical reference plane, not
  physical block coordinates, component intersection or a real optical port.
- A phase-only mirror annotation does NOT implement reflection geometry,
  Fresnel s/p phase, coating loss or polarization-basis transport. A full
  reflection implementation is a separate science/asset integration gate.
- Real beam splitter phase and port signs depend on coatings and port labeling;
  the D7 unitary convention is a chosen idealized gauge, not measured hardware.
- Identical q and labels are necessary here for the single Gaussian modal
  representation; they do not physically guarantee mode matching. Arbitrary
  spatial mode overlap and multiple modes are not implemented.
- Explicit optical phase is vulnerable to path length input precision; no
  Minecraft global-coordinate or network timestamp is an acceptable source
  of sub-wavelength phase calibration by default.
- All D7 data is read-only physics reference: no device block or world effect.
- Runtime verification and real JUnit XML from JDK25 remain required before
  accepting this as a verified NeoForge M0/M1 artifact.

## Director decision

Approve as a **pure Java scientific-reference checkpoint**, not as a formal
M0 release or a production physical model. The next useful implementation
would be real mirror/port geometry and a measurement model, *after* completing
JDK25/NeoForge validation and obtaining Owner hardware/asset constraints.

The complete test/manifest/diff evidence accompanies the source ZIP.
