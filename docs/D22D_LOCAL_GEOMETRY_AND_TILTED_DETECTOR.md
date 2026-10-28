# CAC D22D - Local-SI optical mounts and tilted finite detector (reference only)

**Status: offline numerical reference, not motor integration; Java 25 / NeoForge build PENDING.**

## 1. Problem and scope

D22C computes the Gaussian power through a circular aperture whose plane is normal to the optical axis, and the angular response of an IDEAL Jones polarisation analyser. D22D adds (i) an explicitly anchored, rotated local coordinate frame that handles large Minecraft block coordinates without losing small offsets, (ii) geometric intersection of a directed ray with a finite oriented detector plane, (iii) a bounded oblique-incidence Gaussian power-flux approximation and (iv) a typed read-only bridge from REPORTED motor pose to optical prediction.

This is NOT the D14 game's multi-branch solver, a continuous ray-tracing replacement, a true encoder, actual photodiode readout, real electrical power delivery, calibrated SI scale, or an authoritative server measurement. No Minecraft block state or laser is changed, and no motor is commanded. The D22A/D22B systems remain unchanged. **The owner must approve the physical model and calibration before production use.**

## 2. Exact distinction between world positions and SI coordinates

Define a world-block anchor with integer coordinates `b0=(bx0,by0,bz0)`, a declared metres-per-block scale `L`, and a right-handed orthonormal 3D basis `(ex,ey,ez)`. For another block `b` and a small encoder-REPORTED or DECLARED offset `d_nm`, its laboratory coordinate is:

    r_lab = ex * (L*(bx-bx0) + d_x_nm*1e-9)
          + ey * (L*(by-by0) + d_y_nm*1e-9)
          + ez * (L*(bz-bz0) + d_z_nm*1e-9).

Integer subtraction occurs *before* conversion to binary64. Bounds reject distant blocks, long overflow, unrepresentable nanometre offsets, and wrong dimension metadata. The convenience factory assumes `L = 1 m/block` only for the D14 PREVIEW. It is not a calibrated measurement of Minecraft or Create machinery. Never form `double(worldBlock)*L - double(originBlock)*L` for distant blocks: this loses local displacement. A block centre at the anchor has r=0.

A `ReportedStagePose` retains dimension key, device identity, configuration revision, reported game tick, block mount position, reported integer displacement, motor-stopped flag, and loaded flag. `StageSurfacePrediction` checks that those fields match what the caller expected. **This does NOT authenticate the upstream report**: only a future trusted server adapter can prove identity and apply D22A consecutive-tick settling and external motor-stop interlocks. Model output is `ReferenceObservation`, never `PowerTrace.Sample`.

## 3. Three-dimensional ray--disc geometry

Let the ray have start `o` and unit propagation `k`, and the physical detector disc centre `c`, radius `a` and unit FRONT normal `n` pointing into incoming light. Intersect the infinite ray line with the plane:

    t = n . (c - o) / (n . k),       r_hit = o + t*k.

`PARALLEL` means `n.k` is near zero; `BACK_FACE` means `n.k>0`; `BEHIND_START` means `t<=0`. Otherwise we return `FORWARD`, the crossing point, and its actual in-plane offset from the detector centre. Only when the offset <= a does the **ray axis** cross the finite disc.

A physically extended TEM00 beam has nonzero Gaussian tails. Therefore **axis outside disc DOES NOT imply zero optical power**. Actual beam visibility and occlusion require source-port and world-intersection logic that D22D does not claim.

## 4. Spatial Gaussian flux through an oblique finite disc

The existing `GaussianBeam` has (P,lambda,w0,r_waist,k), with `w(z) = hypot(w0,lambda*z/(pi*w0))`. We evaluate its existing paraxial irradiance at local position `r`, with z=(r-r_waist).k and transverse radius rho=|(r-r_waist)-z*k|:

    I_perp(r) = 2P/(pi*w(z)^2) * exp(-2*rho^2/w(z)^2).

For a front-facing disc, let cos(alpha)=-n.k>0. A restricted geometric irradiance projection gives

    P_collected ~ cos(alpha) * integral_D I_perp(r) dA.

At *normal* incidence this reduces to CAC's existing GaussianCircularAperture captured fraction and uses its analytic/numerical implementation (no duplicate approximation). For *oblique* incidence, the local z and transverse rho vary across the disc. D22D uses equal-area polar midpoint quadrature, compares a 64x128 estimate with a 128x256 estimate, and rejects failure to meet the chosen convergence budget. An independent test uses **different radial/azimuthal Simpson quadrature** on the physical disc. In other words, the verification is not simply production code tested against itself.

Model validity is intentionally restricted:

- FRONT-facing cos(alpha)>=0.5 (incidence <= 60 degrees);
- paraxial theta=lambda/(pi*w0)<=0.05;
- aperture a <= 3.5*w(z_center) to resolve beam power with the fixed integration grid;
- longitudinal shear a*sin(alpha) <= 0.1*z_R, where z_R=pi*w0^2/lambda;
- finite, representable SI positions and positive radii;
- measured integral cannot exceed incident P (conservation fail closed).

The projected-flux formula neglects radial Poynting flow and vector diffraction, and its coarse/fine difference is a convergence INDICATOR, not a rigorous theorem of error bounds. It is NOT a model of grazing-incidence pickup, Fresnel losses, optical coatings or detector responsivity. Unsupported inputs throw; they do not manufacture experimental points. This is a forward-going scalar Gaussian optical reference, not the exact Maxwell solution. A Gaussian waist can physically lie downstream of the detector plane; it is NOT necessarily an emitter port, so the optical intensity calculation does not reject an upstream-of-waist detector based on the waist coordinate alone.

Conservation is reported as `P_captured + P_uncaptured = P_incident` and `P_captured <= P_incident`. The `uncaptured` residual is NOT assigned to absorption; it includes all ideal light missing the finite active aperture.

**Numerical note:** for highly skewed beams, large apertures, sharp spatial variations or multi-branch interference, the simplified irradiance projection is not reliable. The later full geometry solver must model the actual vector Poynting flux / beam transform and account for surface intersections of optics and occluders. Nothing here silently changes D14 world tracing.

## 5. Scanning and provenance

A D22D prediction can be generated from D22A-style *reported* integer positions, but is always labelled `REFERENCE_ONLY`. The typed result preserves reported time, device ID, revision and the three reported coordinates. It is never written as D21 `PowerTrace.Sample`, never mistaken for D22B `MEASURED`, and never grants electrical energy, device occupancy or permission to turn a laser on.

Eventually the future trusted NeoForge adapter must:

1. establish the exact loaded Level, dimension, block entity identity and configuration revision, plus a calibrated bench frame;
2. obtain encoder feedback from a real stage, motor stopped and stable for consecutive actual server ticks;
3. bind the tracked beam's actual source port and solver revision to that stage/receiver state;
4. perform a genuine server-optical readout and record its status and measurement provenance;
5. distinguish preview model predictions, simulated power, and instrument readout in all CSV/GUI output;
6. use real energy and authorisation gates before enabling a powered motor or an automatic laser.

The D22C normal-incidence single-branch reference remains unchanged. The D22B game stage continues to scan existing four discrete D14 optical presets, not arbitrary real angles.

## 6. Verification

```sh
python tools/check_d22d_geometry.py
python tools/check_d22d_geometry.py --demo-csv D22D-reference.csv
python tools/check_d22c_continuous_optics.py
python tools/check_d22b_preset_scan.py
python -m unittest discover -s verification -p 'test_*.py' -v
python tools/check_source_manifest.py --mode checkout --update
git add MANIFEST_SHA256.txt
python tools/check_source_manifest.py --mode checkout
```

The strict `javac --release 21 -Xlint:all -Werror` local smoke does NOT substitute for JDK 25, Gradle JUnit 5, NeoForge 26.3 or game in-world testing. Formal compilation remains pending until a GitHub feature-branch CI run. The actual test result and Git patch provenance are recorded separately in the delivery notes.

## 7. Follow-up milestones

D22E: read-only authenticated (server-world) mounted-apparatus metadata and stable beam-source/receiver IDs; no automatic laser ON. D22F: fully continuous world-ray intersections and optical-branch tracing, with real moving meshes and aperture/filter semantics approved by the owner. Then integrate motor feedback, interlocks and actual instrument samples with the D21 measurement workstation.
