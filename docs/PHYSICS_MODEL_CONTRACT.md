## D9 addition — ideal observation is not an approved physical sensor

The D9 optical power meter integrates the standard TEM00 irradiance over a
finite coaxial or laterally decentered circular aperture, using laboratory-local
metres and SI watts. It is a DRAFT deterministic observation model. The
existing `Measurement(... standardUncertainty=0 ...)` means an idealized
model with no experimental/electronic uncertainty, **not** a calibrated or
NIST-traceable instrument. Real responsivity, bandwidth, detector material,
heat and damage require separate Owner-approved models. No ModelCatalog
approval status is changed; an observation never grants world-mutation rights.

## D8 reference amendment: optical surface geometry (DRAFT ONLY)

The real-optics request authorizes studying standard experimental models,
not inventing coating/material constants or granting production model approval.
`PlanarFresnelInterface` assumes two real positive nondispersive dielectric
indices, a single flat nonmagnetic boundary and a central-plane-wave ray;
`IdealCalibratedPlaneMirror` assumes exactly lossless reflection with externally
specified s/p phases. Neither yields absorption, game damage or a proven
finite Gaussian interface solution. All outputs stay in pure physics-core.

## OPT-001-D7 scientific reference (not Owner approval)

A new matched-mode CW TEM00 two-port interferometer reference tracks q,
complex sqrt(W) Jones modal amplitudes and path phase in vacuum. It is a
provisional standard-optics numerical reference only. The user has chosen
real experimental optical equations, but has not approved specific hardware
phase calibrations, splitter coatings or production damage thresholds.
`TrackedModePowerLoss` is NOT equivalent to absorbed heat. No new production
ModelCatalog entry is OWNER_APPROVED.

# Scientific Model Intake Contract

Every physics model submitted for implementation should supply the following, where relevant:

1. **Identifier and revision**: e.g., `OPT-001 rev A`.
2. **Question and domain**: which physical system, species, fields, operating regime and assumptions are represented?
3. **State variables**: symbols, SI units, valid ranges and initialization.
4. **Equations**: governing differential, integral or discrete equations; conventions; boundary and initial conditions.
5. **Interactions**: energy, momentum, particles or information exchanged with other subsystems and the Minecraft world.
6. **Observable quantities**: measurements and measurement noise, clearly separate from simulation truth.
7. **Limiting cases**: analytically known behavior, benchmarks or authoritative literature.
8. **Numerical treatment**: if owner does not prescribe a solver, engineering proposes options for approval, including error and performance tradeoffs.
9. **Failure modes**: nonphysical inputs, instability, invalid domains, unloaded chunks, and persistence behavior.
10. **Owner decision:** `DRAFT` or `OWNER_APPROVED`, with an explicit owner-source reference (no `PENDING` placeholder for approval).
11. **Independent engineering verification:** `NOT_IMPLEMENTED`, `IMPLEMENTED`, `NUMERICALLY_VERIFIED` or `GAME_VERIFIED`, with reproducible test evidence. Engineering verification cannot grant owner approval.

The game must never label an owner-unapproved physical approximation as an experimentally validated law. `ModelGate` keeps owner approval separate from numeric/game verification. The future server adapter must never construct trusted model-approval records from client-authored data.

## Initial model backlog (not yet approved)

- `OPT-001`: Owner chose standard experimental laser optics; D5 has a **draft numerical reference** for CW scalar vacuum TEM00 Gaussian + ABCD. Detailed emission geometry/validity and production authorization remain pending, and no model is `OWNER_APPROVED` in the server catalog.
- `OPT-002`: optical surface scattering/reflection/transmission (pending).
- `THM-001`: localized absorption, diffusion and material changes (pending).
- `BIO-001`: game entity exposure-to-damage mapping and protection rules (pending).

The owner's Blender device mesh is **not** a substitute for a physics definition, nor is a chosen formula a substitute for a game interaction specification.


## D6 clarification — phasor/Jones reference remains unapproved

D6 extends the draft `OPT-001` scalar CW vacuum TEM00 reference with fully
polarized complex fields, ideal uniform Jones elements and ideal coherent
superposition. **No production `OWNER_APPROVED` model has been registered.**
Before M1 apparatus integration, Owner must specify phase reference, physical
waist/port placement, emitter polarization basis, frequency controls and
what experimental approximation regime to accept. D6 does not infer actual
materials, absorption, damage, optical port budgets, linewidth or coherence
from simple IDs. Full equations/limits: `OPT_001_COMPLEX_FIELD_REFERENCE.md`.
