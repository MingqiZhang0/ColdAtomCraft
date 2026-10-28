package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

import java.util.Objects;

/**
 * D22D continuous, LOCAL-SI detector-plane geometry for one ideal TEM00 beam.
 * This is a reference power-flux calculation, NOT an authenticated measurement
 * or a replacement for the D14 multi-branch Minecraft optical network.
 *
 * <p>A disk's normal faces the approaching light. Its centre and the beam
 * waist must belong to the same local laboratory frame; do not inject large
 * world/block coordinates directly into these double-precision coordinates.</p>
 *
 * <p>At normal incidence the existing exact/controlled-offset circular
 * Gaussian aperture is reused. Oblique flux is a restricted paraxial model:
 * integral over the physical detector disk of I_perp(r,z) * (-n dot k) dA.
 * It neglects the small radial Poynting component, aperture diffraction,
 * scattering, coating physics and nonparaxial fields. We deliberately reject
 * large shear, shallow incidence and unresolved aperture geometry instead of
 * returning an unjustified number.</p>
 */
public final class FinitePlaneGaussianFlux {
    private static final double MIN_FORWARD_COSINE = 0.5;
    private static final double MAX_PARAXIAL_DIVERGENCE = 0.05;
    private static final double MAX_AXIAL_SHEAR_OVER_ZR = 0.10;
    private static final double MAX_APERTURE_OVER_SPOT = 3.5;
    private static final int COARSE_RADIAL = 64;
    private static final int COARSE_ANGULAR = 128;
    private static final int FINE_RADIAL = 128;
    private static final int FINE_ANGULAR = 256;

    private FinitePlaneGaussianFlux() { }

    /** Right-handed disk frame: the frame direction is the FRONT face normal. */
    public record Disk(Vec3 center, RayFrame3 facing, double radiusMetres) {
        public Disk {
            Objects.requireNonNull(center, "disk centre");
            Objects.requireNonNull(facing, "disk frame");
            if (!Double.isFinite(radiusMetres) || radiusMetres <= 0) {
                throw new IllegalArgumentException("positive finite disk radius required");
            }
        }

        public static Disk fromNormal(Vec3 center, Vec3 frontNormal,
                                      Vec3 xAxisHint, double radiusMetres) {
            return new Disk(center, RayFrame3.fromDirectionAndXAxis(frontNormal, xAxisHint),
                    radiusMetres);
        }
    }

    public enum CrossingStatus { FORWARD, PARALLEL, BACK_FACE, BEHIND_START }

    /** Pure ray/plane geometry; a miss of the AXIS does not imply zero TEM00 flux. */
    public record AxisCrossing(CrossingStatus status, Vec3 position,
                               double distanceFromStartMetres, double diskOffsetMetres,
                               boolean insideDisk) {
        public AxisCrossing {
            Objects.requireNonNull(status, "crossing status");
            if (status == CrossingStatus.FORWARD) {
                if (position == null || !Double.isFinite(distanceFromStartMetres)
                        || distanceFromStartMetres <= 0 || !Double.isFinite(diskOffsetMetres)
                        || diskOffsetMetres < 0) {
                    throw new IllegalArgumentException("bad forward crossing");
                }
            } else if (position != null || insideDisk) {
                throw new IllegalArgumentException("nonforward ray has no crossing point");
            }
        }
    }

    public static AxisCrossing axisCrossing(Vec3 rayStart, Vec3 rayDirection, Disk disk) {
        Objects.requireNonNull(rayStart, "ray start");
        Objects.requireNonNull(rayDirection, "ray direction");
        Objects.requireNonNull(disk, "disk");
        Vec3 k = rayDirection.normalized();
        double denom = k.dot(disk.facing().direction());
        if (Math.abs(denom) <= 1e-12) {
            return new AxisCrossing(CrossingStatus.PARALLEL, null, 0, 0, false);
        }
        if (denom > 0) {
            return new AxisCrossing(CrossingStatus.BACK_FACE, null, 0, 0, false);
        }
        double along = disk.center().subtract(rayStart).dot(disk.facing().direction()) / denom;
        if (!Double.isFinite(along)) throw new ArithmeticException("ray crossing overflow");
        if (along <= 0) {
            return new AxisCrossing(CrossingStatus.BEHIND_START, null, 0, 0, false);
        }
        Vec3 hit = rayStart.add(k.scale(along));
        Vec3 difference = hit.subtract(disk.center());
        double u = difference.dot(disk.facing().xAxis());
        double v = difference.dot(disk.facing().yAxis());
        double offset = Math.hypot(u, v);
        return new AxisCrossing(CrossingStatus.FORWARD, hit, along, offset,
                offset <= disk.radiusMetres());
    }

    public enum Method { NORMAL_ANALYTIC, OBLIQUE_PARAXIAL_QUADRATURE }

    /** A prediction only. captured + uncaptured = incident, with NO detector noise. */
    public record Prediction(double incidentWatts, double capturedWatts,
                             double uncapturedWatts, double forwardCosine,
                             double axisOffsetMetres, boolean axisIntersectsDisk,
                             double estimatedQuadratureErrorWatts, Method method) {
        public Prediction {
            Objects.requireNonNull(method, "calculation method");
            if (!finiteNonNegative(incidentWatts) || !finiteNonNegative(capturedWatts)
                    || !finiteNonNegative(uncapturedWatts)
                    || !Double.isFinite(forwardCosine) || forwardCosine <= 0
                    || forwardCosine > 1 + 1e-12 || !finiteNonNegative(axisOffsetMetres)
                    || !finiteNonNegative(estimatedQuadratureErrorWatts)
                    || Math.abs((capturedWatts + uncapturedWatts) - incidentWatts)
                    > 4 * Math.ulp(incidentWatts)) {
                throw new IllegalArgumentException("invalid predicted optical power budget");
            }
        }
        private static boolean finiteNonNegative(double value) {
            return Double.isFinite(value) && value >= 0;
        }
        public double spatialFraction() {
            return incidentWatts == 0 ? 0 : capturedWatts / incidentWatts;
        }
        public String provenance() { return "REFERENCE_ONLY"; }
    }

    /**
     * Predict the IDEAL incident power at a finite disk. The beam waist is
     * not the physical laser output port; source visibility is not verified.
     */
    public static Prediction predict(GaussianBeam beam, Disk disk) {
        Objects.requireNonNull(beam, "beam");
        Objects.requireNonNull(disk, "disk");
        double cosine = -disk.facing().direction().dot(beam.directionLab());
        if (!(cosine >= MIN_FORWARD_COSINE)) {
            throw new IllegalArgumentException("backside or unvalidated grazing incidence");
        }
        Vec3 delta = disk.center().subtract(beam.waistPositionLabMeters());
        double z = delta.dot(beam.directionLab());
        Vec3 transverse = delta.subtract(beam.directionLab().scale(z));
        double offset = transverse.magnitude();
        double w = beam.radiusMeters(z);
        // Large global coordinates lose micrometre/nanometre displacement.
        // A local frame is mandatory; do not conceal precision loss.
        if (coordinateUlp(beam.waistPositionLabMeters(), disk.center())
                > Math.min(w, disk.radiusMetres()) * 1e-5) {
            throw new IllegalArgumentException("lab origin too large for detector resolution");
        }
        double captured;
        double error;
        Method method;
        if (Math.abs(cosine - 1) <= 1e-12) {
            var normal = GaussianCircularAperture.capture(beam.powerWatts(), w,
                    disk.radiusMetres(), offset);
            captured = normal.capturedPowerWatts();
            error = 0;
            method = Method.NORMAL_ANALYTIC;
        } else {
            if (beam.divergenceParameter() > MAX_PARAXIAL_DIVERGENCE
                    || disk.radiusMetres() > MAX_APERTURE_OVER_SPOT * w
                    || disk.radiusMetres() * Math.sqrt(Math.max(0, 1 - cosine * cosine))
                    > MAX_AXIAL_SHEAR_OVER_ZR * beam.rayleighRangeMeters()) {
                throw new IllegalArgumentException("geometry exceeds verified oblique TEM00 regime");
            }
            double coarse = integrate(beam, disk, cosine, COARSE_RADIAL, COARSE_ANGULAR);
            captured = integrate(beam, disk, cosine, FINE_RADIAL, FINE_ANGULAR);
            error = Math.abs(captured - coarse);
            if (error > 3e-5 * beam.powerWatts()) {
                throw new ArithmeticException("oblique aperture integration failed convergence budget");
            }
            // Some oblique Gaussian approximations are not globally conservative.
            // Reject any unphysical integral; do not silently normalise it.
            if (captured > beam.powerWatts() + 1e-10 * beam.powerWatts()) {
                throw new ArithmeticException("oblique paraxial flux exceeds source power");
            }
            if (captured > beam.powerWatts()) captured = beam.powerWatts();
            method = Method.OBLIQUE_PARAXIAL_QUADRATURE;
        }
        // The analytic Gaussian extends on both sides of its waist; a waist
        // behind the receiver is NOT by itself evidence of an absent source.
        Vec3 axisAtPlane = beam.waistPositionLabMeters()
                .add(beam.directionLab().scale(z + transverse.dot(disk.facing().direction())
                        / disk.facing().direction().dot(beam.directionLab())));
        double planeOffset = axisAtPlane.subtract(disk.center()).magnitude();
        if (!Double.isFinite(planeOffset)) {
            throw new ArithmeticException("invalid optical axis intersection");
        }
        return new Prediction(beam.powerWatts(), captured,
                beam.powerWatts() - captured, cosine, planeOffset,
                planeOffset <= disk.radiusMetres(), error, method);
    }

    private static double coordinateUlp(Vec3 first, Vec3 second) {
        return Math.max(Math.max(Math.ulp(first.x()), Math.ulp(first.y())),
                Math.max(Math.max(Math.ulp(first.z()), Math.ulp(second.x())),
                        Math.max(Math.ulp(second.y()), Math.ulp(second.z()))));
    }

    /** Equal-area polar midpoint cells; physical projected flux, not pixel brightness. */
    private static double integrate(GaussianBeam beam, Disk disk, double cosine,
                                    int radialSteps, int angularSteps) {
        Vec3 fromWaist = disk.center().subtract(beam.waistPositionLabMeters());
        Vec3 k = beam.directionLab();
        double baseZ = fromWaist.dot(k);
        Vec3 baseT = fromWaist.subtract(k.scale(baseZ));
        Vec3 e1 = disk.facing().xAxis();
        Vec3 e2 = disk.facing().yAxis();
        double z1 = e1.dot(k), z2 = e2.dot(k);
        Vec3 transverse1 = e1.subtract(k.scale(z1));
        Vec3 transverse2 = e2.subtract(k.scale(z2));
        double cellArea = Math.PI * (disk.radiusMetres() / radialSteps)
                * (disk.radiusMetres() / angularSteps);
        // Above equals pi*a^2/(nr*na) without a*a overflow in numerator.
        if (!Double.isFinite(cellArea) || !(cellArea > 0)) {
            throw new ArithmeticException("unrepresentable integration area");
        }
        double total = 0;
        for (int i = 0; i < radialSteps; i++) {
            double radius = disk.radiusMetres() * Math.sqrt((i + 0.5) / radialSteps);
            for (int j = 0; j < angularSteps; j++) {
                double angle = (2 * Math.PI / angularSteps) * (j + 0.5);
                double u = radius * Math.cos(angle), v = radius * Math.sin(angle);
                double localZ = baseZ + u * z1 + v * z2;
                Vec3 radial = baseT.add(transverse1.scale(u)).add(transverse2.scale(v));
                double flux = beam.intensityWattsPerSquareMeter(radial.magnitude(), localZ)
                        * cosine * cellArea;
                if (!Double.isFinite(flux) || flux < 0) {
                    throw new ArithmeticException("unrepresentable local Gaussian irradiance");
                }
                total += flux;
            }
        }
        if (!Double.isFinite(total)) throw new ArithmeticException("quadrature overflow");
        return total;
    }
}
