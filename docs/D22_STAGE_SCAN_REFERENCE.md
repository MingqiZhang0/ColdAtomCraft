# CAC D22A — Feedback-verified motor scan reference

**Status:** engineering-only pure-Java research checkpoint. **NOT integrated with Minecraft motor blocks**, physical encoder devices, the Create power chain, or the D21 data recorder GUI. Does not introduce a new in-game stage, does not animate or translate an existing optic, and does not assert scientific calibration or owner model approval. **No automatic laser ON** is enabled.

## Why D21 manual stages must not be promoted directly into a precision motor

D21's linear/rotary previews change **Minecraft block coordinates by 1 m** or a D14 optic port by **90°**. Neither is a physical translation-stage encoder nor a continuous 3D optical surface. A scan of two block positions is not a micropositioning experiment. D22A establishes a device-independent control/data protocol for future motor and encoder hardware while leaving D21 game behavior unchanged.

## SI control coordinates, encoder feedback and the clock

- A `NANOMETRE` axis stores an integer `u_x` representing `x = u_x * 1e-9 m`.
- A `NANORADIAN` axis stores an integer `u_theta` representing `theta = u_theta * 1e-9 rad`.
- These are **representation units, not stated physical hardware resolution, accuracy or repeatability**. The Owner will specify a device's real mechanical range, calibration curve, backlash, angular zero, limit switches, velocity and uncertainty.
- `StageScanPlan.Axis` declares minimum, maximum and allowable deviation from target. The scan plan has 1–128 positions, with 1–64 measurements at each position. Every point has a bounded move timeout, a consecutive encoder-stability dwell, a bounded read-response timeout and an acquisition interval.
- Minecraft block coords and dimension are outside the pure core. A future motor binding must map a unique device frame to a laboratory SI frame and authenticate feedback **at the time of the request**.
- Tick counts are *logical server ticks*, not precision SI elapsed seconds. D22A rejects skipped/repeated ticks rather than fabricating elapsed time. Microsecond motion/pulse control requires a distinct calibrated physical-time engine.

## State machine and server contract

```text
NEW -> [MOVE to commanded coordinate] -> MOVING
MOVING -> encoder in target tolerance AND motor stopped -> SETTLING
SETTLING -> N consecutive ticks within tolerance, motor stopped -> SAMPLING
SAMPLING -> issue READ -> accept a later, authenticated server reading
SAMPLING -> after K raw readings, MOVE to next target or COMPLETE
ANY ACTIVE -> bad identity/revision/load/interlock/clock/encoder/timeout -> ABORTED -> STOP
```

The model **never** operates a real motor, knows whether an actual stage is powered, samples a real detector or confirms a shutdown. `interlockApproved` is an externally asserted condition whose **provenance must be verified by the server-side adapter**; it does not constitute D15/D20 energy escrow. No powered operation is authorized by this draft. It does not write blocks or NBT.

After `STOP`, this instance stays `ABORTED`; the future server manager must hold exclusive leases until a separate, trusted shutdown confirmation. A returned `FINISHED` is completion of the request protocol, **not** confirmation that the motor has stopped or that a lease may safely be released.

Current encoder checks require `motionStopped` plus in-range position held within tolerance for the declared dwell. This is not a verified acceleration or vibration model. The future feedback bridge must validate velocity and calibrated uncertainty, and reject stale or spoofed feedback; a client-provided boolean is not adequate.

## Data and scientific integrity

Each acquisition stores `(point index, target integer SI coordinate, observed encoder coordinate, authoritative server game tick, power status, watts or missing)`. `StageScanDataset.csv` preserves **all** raw measurements. `StageScanDataset.svg` displays mean optical power vs **measured encoder position**, not requested position, and disconnects the curve through any point containing an invalid optical observation. A true measured zero watts remains zero. No invalid status becomes a zero-watt observation.

The mean is displayed only when every power observation at that target is valid; no interpolation, forced summing of beams, or Gaussian correction is introduced. For release-grade instruments, add calibration/uncertainty bands and explicit aperture/spot alignment. This is a *diagnostic reference*, not a measurement of a verified physical stage.

## Verified limits and explicit missing features

- Java 21 offline smoke compiles with `-Xlint:all -Werror`; portable to the JDK 25 source build target.
- A seeded actuator/encoder/measurement simulator exercises more than 500,000 checks under changing targets, backlash-like positional delays, multiple samples per point, invalid detector statuses and both axis types. It is **not** a physical servo model.
- A separate JUnit 5 test source covers dropped ticks, changed identity/revision, interlock denial, timeout, drift, unsolicited reads, renderer gaps and finite numerical overflow. JUnit 5 on JDK 25 remains pending GitHub Actions.
- **NOT implemented:** NeoForge block entity motor and UI, Create drive coupling, energy debit and release, optical model recalculation at continuous positions, persisted scan sessions, 3D multi-axis coupling, live acquisition, client data download, physical uncertainty, real trajectory dynamics.

## D22B implementation order (requires Owner model approval where indicated)

1. Bring D21A–D21D through Java 25 CI and a client/dedicated-server game test. Avoid adding another unverified menu first.
2. Add a server-only motor ownership/binding adapter; leases, physical interlock and authoritative feedback must be mediated by one manager. Use D19/D20 services only after their official CI and real energy accounting are proven.
3. Add a mounted instrument coordinate map and sub-block geometry. Continuous optics must use actual SI positions/angles, not simply animate a block and leave D14's grid-only path unchanged.
4. Bind D22 `READ` requests to the existing D21 recorder, preserving recorder ID, detector identity, sampling tick, measurement status and source control revision. Stop on reload/unload; never auto-resume a powered scan from saved NBT.
5. Add an in-game motion panel with limit ranges, command vs measured position, settle time, scan start/stop and live `P(x)`/`P(theta)` plots. Only the server may authorize new sessions; dimension/distance/permissions checked on each action.
6. Validate in a real 26.3 NeoForge client + dedicated server: encoder loss, world reload, motor extraction, competing players, power loss, restore, bounded scans, file corruption, TPS and sampled-data integrity.
