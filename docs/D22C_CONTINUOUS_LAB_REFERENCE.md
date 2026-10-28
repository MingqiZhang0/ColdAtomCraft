# D22C: continuous laboratory-coordinate optics -- reference, not actuator integration

Status: pure Java mathematical reference and verification only. **Not yet JDK25/NeoForge built, hardware-calibrated, approved as scientific truth, or playable in Minecraft.**

## Why this exists

D22B's four selectors are discrete *game BlockState choices*, not a fine-angle motor. D22A's requested position is not trustworthy actual position until an external server-authoritative encoder has been authenticated, checked against an apparatus identity and revision, and observed stable. D22C begins the continuous optical model **without** pretending that an arbitrary double or nanometre integer constitutes that hardware proof.

The reference composes existing CAC classes:

- `GaussianBeam` and `IdealCircularDetectorFace` in a laboratory-local `RayFrame3`; the circular detector is perpendicular to the beam and front-illuminated.
- `GaussianCircularAperture` to compute collected power, including a transverse x/y beam-to-aperture displacement.
- `JonesMatrix.idealLinearPolarizer` for an **explicitly installed** ideal polarisation analyser. An optional half-wave plate uses `JonesMatrix.halfWave` **before** the analyser.
- `StageScanPlan.MAX_ABS_UNITS` for bounded nm/nrad *data representation*; `StageScanController` remains the only planned way to control a future real motor adapter.

No new Gaussian optics, Jones convention or instrument calibration is invented by this milestone.

## Mathematical contract

For a single forward-going TEM00 scalar Gaussian (vacuum, paraxial), spot size at an axial distance z is

    w(z) = sqrt(w0^2 + (lambda*z/(pi*w0))^2).

The detector centre is at **waist + z*k + dx*ex + dy*ey**, with ex,ey,k a right-handed orthonormal frame; dx and dy are **reported relative to the beam axis** in nm -> m. This intentionally uses a local coordinate frame rather than large global Minecraft positions.

The circular aperture's fraction C(w(z),a,sqrt(dx^2+dy^2)) is the existing CAC numerical Gaussian integral. With unit-power Jones input E:

    T = ||A(theta)*H(alpha)*E||^2 (H omitted unless explicitly selected,
                                  A omitted for NO_ANALYSER).

Power accounting is **passive and explicit**:

    P_captured = P_laser*T*C
    P_rejected_by_ideal_analyser = P_laser*(1-T)
    P_missed_by_aperture = P_laser*T*(1-C)
    P_captured + P_rejected + P_missed = P_laser.

For linear horizontal input and an analyser without waveplate: T = cos^2(theta). With a half-wave plate at alpha before a horizontal analyser: T = cos^2(2*alpha). **A half-wave plate by itself does not reduce total power in this ideal model.**

Boundaries: the Gaussian aperture remains normal to the beam; oblique finite apertures, clipping by the waveplate body, scattered light, coherent multipath interference, real analyser extinction and reflectivity, motor inertia, backlash, thermal noise, uncertainty and actual time are NOT represented. A large absolute waist origin that destroys offset precision is explicitly rejected rather than generating a fictitious centred reading.

## Integration gate / scientific meaning

`ContinuousBenchScanReference.predict` accepts a `Pose` as *untrusted reference input* and produces a `PredictedPoint`, never a `PowerTrace.Sample` or `StageScanDataset.Reading`. It never turns on a laser, creates a block entity, changes a BlockState, asserts an encoder was read or reserves power. `predictionCsv` marks every row `REFERENCE_ONLY` to prevent confusion with authoritative server measurements.

A future D22D server adapter must prove each one of:

1. an immutable mounted device identity, config revision and world/bench frame;
2. motor physical position readback, calibrated uncertainty, limits and stopped state;
3. stable consecutive server observations with no chunk unload or missed ticks;
4. aperture, analyser angle and beam parameters bound to those verified states;
5. optical reading obtained from authoritative server-world optical state, not from this reference prediction;
6. separate hardware/energy authority before any automatic ON or laser power change.

Do NOT substitute the reference prediction for item 5. In particular, this reference is not a full continuous-geometry replacement for the existing D14 multi-branch ray-tracing solver.

## Verification

Use JDK21 or newer for the standalone reference; CI additionally runs JDK25 formal JUnit tests and the NeoForge mod build after a feature branch is pushed:

    python tools/check_d22c_continuous_optics.py
    python tools/check_d22_stage_scan.py
    python -m unittest discover -s verification -p 'test_*.py' -v
    python tools/check_source_manifest.py --mode checkout --update
    git add MANIFEST_SHA256.txt
    python tools/check_source_manifest.py --mode checkout

This delivery environment has only JDK21 and no network access to download Gradle/NeoForge: the formal build gate remains **PENDING**, not passed.
