## CAC-D10-PREVIEW — provisional equipment geometry and interaction protocol (2026-10-26)

- Owner authorized a **temporary visual model** in place of the future Blender model, specifically to test interaction.
- Use two minimal, distinct Minecraft-native cuboid blocks as a **DEV-only** fixture. Final Blender asset creation remains Owner-owned.
- Preserve separation of experimental optics and Minecraft interface: no scientific approval or physical world damage is inferred.
- The fixed diagnostic source parameters and one metre/block translation exist only to test a measurable interaction loop; `ModelCatalog` remains unchanged.
- Only loaded, entirely-air, collinear routes are supported and sampled on the server. Never treat the readout as power/heat transferred to a block.
- The early source-only D10 code is not claimed to have passed formal JDK25 / NeoForge runtime gates.

## CAC-D9-01 — finite-aperture ideal power observation (2026-10-26)

- Owner explicitly chose standard real experimental optics. Engineering
  selects the standard TEM00 disk integral as the **DRAFT numerical reference**
  for the first ideal power readout, NOT as approval of any real detector model.
- Detector normal points toward incoming light; only beam-normal incidence is
  accepted; lab-local SI metres and explicit offset are mandatory.
- Misalignment and finite aperture affect the reading; missing power is never
  silently assigned to heating. The reading is a noninvasive observer.
- Instrument responsivity, noise, spectral/temporal bandwidth and saturation
  remain unspecified until Owner or actual device data is available.
- D8 ray-only spatial power is not accepted as a Gaussian finite-aperture mode.
- JDK25/NeoForge launch verification remains a separate hard gate.

## CAC D8 optical-science engineering decision (2026-10-26)

- An uncoated dielectric Fresnel boundary and a real coated laboratory mirror
  are **not equivalent**; model them as separate, explicitly scoped components.
- Fresnel electric transmission coefficients require a sqrt(power flux ratio)
  when applied to D7 `sqrt(W)` traveling-mode amplitudes.
- The reflected p basis is `k_reflected cross s`, not an unspecified global
  Cartesian axis. Reflectance signs depend on this basis gauge.
- At an ordinary flat mirror `q_out=q_in` using the outgoing forward axis;
  do not silently implement a phase-conjugating mirror.
- Any geometric-to-modal projection is a central-ray paraxial approximation,
  not a measured mode overlap or proof of experimental optical alignment.
- No material absorption, damage, MC-world modification or Owner model approval.

## CAC-D7: matched-mode interferometer reference (2026-10-26)

**Engineering-only draft reference:** introduce carrier- and Gouy-phase-tracked
modal amplitudes (sqrt(W)), plus a symmetric lossless 2-port scattering
matrix. Both outputs are mandatory for energy accounting. Recombination is
allowed only for explicitly equal wavelength, Gaussian q, coherence reference
and matching modal basis declaration; geometric validation is deferred.
Mirror and plate global phases are explicit inputs, never assumed from
missing coating data. ULP diagnostic exposes the precision limitation of
using global MC world coordinates for interferometry. No production
ModelCatalog/Owner approval or damage capability is added. Real JDK25 runtime
remains an independent gate.

## D3 engineering decision — 2026-10-26

- Mandatory per-session **apparatus/operation tick-window reservation** in the pure Java
  scheduler, with no public unrestricted session-creation bypass. The server-wide tick
  window and solver nanoseconds are independent clocks. Competing reservations in one
  scheduler instance fail atomically; known claimed histories retain an in-memory tombstone.
- Cancellation and reuse of a reserved apparatus/window are permitted only before ANY claim.
  The old attempt cannot submit after cancellation. A failure or APPLIED outcome never
  grants automatic replay of the identical reserved physical interval.
- Two schedulers and forged/non-authoritative scheduling records remain an explicit hazard;
  no actual NeoForge integration, persistent release protocol, or gameplay/world effect is
  authorized. Gating this type in pure Java does **not** mean world delivery is safe.
- Owner retains physics, Blender and release approvals. D3 is NOT a final M0 closure.

---

# Director decision record: CAC-M0-R2.1-D2 (2026-10-26)

**D2-01:** Require uniqueness of each `(runId,stepIndex,eventIndex)` event slot independent of model ID/revision, preserving model provenance in the original TransferEventKey. Motivated by an actual D1 two-claim reproduction.

**D2-02:** For a single logical experiment in memory, use a private-ledger `ExperimentRunScheduler` to issue attempt tokens and serialize claim/abandon/retry. Replace stale unclaimed attempts only; never replay an experiment session after a Claim.

**D2-03 (explicit limitation):** Two different experiment sessions can still refer to the same physical apparatus/time. Do not allow destructive effects until a true server-side physical-operation scheduler and world-state adapter enforce exclusion.

**D2-04:** No durable exactly-once claim, no automatic safe eviction, no Owner science model or Blender handoff implied. Java25/NeoForge D2 build and release Gate remain open.

---

# Decision log

## CAC-001 - 2026-10-25 - Project authorization

- Decision: establish Cold Atom Craft as a research-oriented Minecraft NeoForge mod.
- Owner: scientific direction, equations/approximations, and Blender 3D assets.
- Engineering: source code, numerical verification, mod integration, tests and optimization.
- Status: approved in conversation.

## CAC-002 - 2026-10-25 - Target runtime

- Decision: target latest agreed stable Minecraft `26.3`, use NeoForge and Java 25.
- Initial dependency pin: NeoForge `26.3.0.84`; not claimed as the latest loader build.
- Reason: use a verifiable released dependency; changes require validation.
- Status: superseded by CAC-006 (`26.3.0.99`) after the M0-R2 build audit.

## CAC-003 - 2026-10-25 - Scientific modeling gate

- Decision: generic typed data is permitted before physics approval; no specific laser-heating, injury, optical scattering or atomic model can be activated without accepted equations, units, domains and comparison tests.
- Status: active.

## CAC-004 - 2026-10-25 - Licensing

- Decision: no open-source license has been selected by the owner. Distribution rights must not be presumed.
- Status: pending owner choice before public release.

## CAC-005 - 2026-10-25 - Engineering M0-R1 structure

- Technical design: separate owner authorization from engineering verification, with independent evidence flags.
- Technical design: use laboratory-local SI meters for scientific inputs, not absolute Minecraft block coordinates.
- Technical design: Blender +Z/-Y device convention and explicit 4-way transforms; **provisional pending first owner asset validation**.
- Technical design: physical solver may only propose world effects after owner approval and numerical verification; server must also validate every chunk consulted and current targets/permissions.
- Technical design: GLB is an interchange file, not a native NeoForge-renderable block model.
- No authority to select OPT-001/OPT-002/THM-001/BIO-001 equations was exercised.
- Status: engineering revision implemented and standalone-tested; loader runtime remains pending.

## CAC-006 - 2026-10-26 - M0-R2 reproducible runtime baseline

- Decision: pin NeoForge `26.3.0.99` (latest 26.3 release observed on 2026-10-26) for M0-R2.
- Decision: use the official ModDevGradle `enable { disableRecompilation = true }` pipeline for builds and runs on this host.
- Reason: the official MDK control project also builds with this combination. The local NeoFormRuntime 2.0.31 decompile/recompile pipeline fails while writing `net.minecraft.world.waypoints.package-info` to its output JAR; that is an environment/tooling issue, not a project-source compile error. `disableRecompilation` still compiles all project Java with javac 25.
- Version evidence: Gradle `9.2.1`; ModDevGradle `2.0.148`; JDK `25.0.3+9-LTS`; Minecraft `26.3`; NeoForge `26.3.0.99`.
- M0-R1's provisional `26.3.0.84` pin and the intermediate `.98` test pin are superseded. See `docs/M0_R2_AUDIT.md` for the exact failed attempts and logs.
- Status: accepted engineering baseline; owner acceptance of M0 is still separate.

## CAC-007 - 2026-10-26 - World interaction contract v0.2

- Decision: separate calculation-step, simulation-interval and transfer-event identities; require an in-memory exactly-once claim before a future mutation.
- Decision: precheck is not permission. Final mutation requires server-thread target/position/load/permission facts and is not implemented in M0-R2.
- Decision: world revision data is not a complete NeoForge change tracker. Candidate stale-state rejection is implemented only as pure data-contract logic.
- Decision: model references, not model definitions or approval flags, may cross network/save boundaries.
- Decision: cross-restart exactly-once and durable failure recovery are **not** claimed; they remain future requirements.
- Status: interfaces and tests implemented; no laser damage, heating or world mutation added.

## CAC-008 - 2026-10-26 - GLB interchange axes

- Decision: retain Blender `+Z` up / `-Y` forward and add explicit glTF `+Y` up / `+Z` forward metadata for integrated assets.
- Decision: `DevicePlacement` exposes both Blender-space and glTF-space conversion methods; they are tested to agree after the standard Blender exporter conversion.
- Status: manifest/coordinate logic verified. No owner Blender model was received; actual Blender export and Minecraft renderer integration remain NOT RUN.

## CAC-009 - 2026-10-26 - Director interim stale-run lifecycle (M0-R2.1-D1)

- Engineering decision: an invalidated solver run may be explicitly **abandoned only before any event has been claimed**. Retirement is in-memory and retains run/step tombstones; late claims must fail closed.
- Engineering decision: actual solver recomputation after stale inputs requires a new server-owned calculationRunId, not a repeated `beginCalculation` for the same `(runId, stepIndex)`.
- Engineering safety limit: a run with CLAIMED/APPLIED/FAILED events cannot be abandoned, even if the failure may have been only partial; the scheduler must halt and reconcile rather than automatically re-run.
- Authority: code-director interface refinement, **not** Owner approval of any physical model, physical constants, world-effect damage law or final M0 closure.
- Unresolved: future scheduler must enforce logical-experiment exclusivity across different run IDs and decide a durable recovery strategy before enabling real world modification.
- Status: implemented and offline-tested in D1; JDK 25/NeoForge integration pending.

## CAC-012 - 2026-10-26 - D4 claim-history index and release evidence boundary

- Engineering decision: after the first successful Claim, a run remains permanently
  marked as having settlement history for the lifetime of the in-memory Ledger;
  the terminal state cannot clear this flag. Abandonment must be O(1) in the
  total number of previously claimed events. The claim index is additive to,
  not a replacement for, the actual settlement ledger or tombstones.
- Engineering decision: a build is only certified after formal JDK25 Gradle and
  JUnit execution plus class-by-class JAR inventory verification. The portable
  validator may run under other JDKs, but D4's local JDK21 tests are NOT formal
  JDK25 evidence.
- Risk: a JAR content check does not demonstrate client/server runtime loading;
  no release or Owner M0 closure is authorized by these checks.
- No physics-model approval, atom/laser damage law, Blender model or server
  world-effect authority granted by this decision.


## CAC-OPT-001-D5 (2026-10-26) — Scientific reference, not production approval

Owner chose standard real experimental laser optics. D5 engineers an independent scalar CW vacuum Gaussian TEM00 / ideal thin-lens ABCD reference without imposing a new device/waist location, paraxial threshold or world-damage mapping. Gaussian formulas are conventional physical theory; their D5 implementation is not an `OWNER_APPROVED` production `ModelCatalog` entry. Future emission geometry/thresholds and actual game integration require scientific approval and real-world adapter verification. JDK25 formal M0 Gate remains open.


## CAC-D6 — Scientific reference decisions (not Owner physics signoff)

- Use the standard time phasor `exp(-i omega t)`, vacuum scalar-paraxial
  Gaussian amplitude with signed Gouy phase, and an explicit Cartesian Jones
  basis. Coherence requires both an exact common carrier wavelength and a
  declared common phase origin; mere key equality does not prove phase locking.
- Store electric phasors in `sqrt(W/m²)` normalized units: a travelling wave
  has `|F|²=I`, but in a standing wave `|F|²=2c*u_E` must not be mistaken for
  the net Poynting flux. Compute E/B energy densities and flux separately.
- Restrict Jones optical elements to ideal uniform polarizers/retarders;
  general ABCD propagation does NOT yet carry the relative optical-path phase
  necessary for a calibrated interferometer.
- All D6 work remains a local numerical reference, not a newly authorized
  physical model or world modification capability.

- Real waveplates have a common propagation phase in addition to their
  relative retardance. D6's two-argument factory sets the common phase zero
  by gauge choice only; real arm-to-arm interference needs explicit
  `withUniformPhaseRadians` or an approved calibrated optical path model.

## CAC-D21C-ARCHIVE (2026-10-27) — historical data without client file access

- Retain the D21A server-generated recorder identity and append-only CSV as the
  authoritative long-term observation archive; do not reinterpret invalid
  statuses as zero optical watts or game ticks as SI seconds.
- Add bounded 32-point, 16-KiB-row-data server-side disk page reads through an
  authenticated D21B menu, with frozen size-based snapshot navigation.
- Only pages selected by the validated server may be exported as separate
  `.history-page.csv/.svg` files. No user-supplied paths, no automatic client
  file download, no claim of per-user ownership permissions.
- Archive parse/consistency failures fail closed (no false graph; pause logger).
- Pure Java checks and NeoForge-shaped mocks are not formal JDK25/game tests;
  D21C release acceptance remains pending.

## CAC-D22A (engineering draft, 2026-10-28) — Feedback-verified stage scan reference

- Introduce a pure-Java motor scan state machine and data model without claiming a working Minecraft motor or a continuous 3D optics solver.
- Position and angle commands are integer nano-SI representation quantities; their resolution does **not** specify actual hardware accuracy.
- Enforce current, immutable device identity/revision, loaded state, server-side interlock, stopped motor and consecutive encoder stability prior to accepted power samples.
- Rejected/invalid optical samples remain distinct from measured zero watts. A failed scan stays terminal pending a future trusted motor STOP acknowledgement.
- No automatic laser ON, energy debit, Create integration, physical model approval or MC world mutation is authorized by this draft.

## D22E (2026-10-28) — Read-only loaded-world geometry preview

- The existing optical table provides an empty-hand local geometry inspector.
- Server `LoadedApparatusSnapshot` is bounded to one actual world and one Tick;
  no chunk is force-loaded, no motor is commanded, and no laser is enabled.
- `BLOCK_ENTITY_INSTANCE` is only a same-session identity check;
  `BLOCKSTATE_ONLY` cannot detect identical stateless replacement.
- The nominal 1 m/block scale is an explicit DEV convention, not calibration.
- `READ_ONLY_SERVER_SNAPSHOT` metadata and `REFERENCE_ONLY` predictions are
  not `MEASURED` power samples or authorization for energy/device control.
- JDK25 NeoForge build and in-game tests remain required release gates.
