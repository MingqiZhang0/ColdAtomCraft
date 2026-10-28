package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.GaussianCircularAperture;
import dev.coldatomcraft.physics.optics.IdealCircularDetectorFace;
import dev.coldatomcraft.physics.optics.JonesMatrix;
import dev.coldatomcraft.physics.optics.JonesVector;
import dev.coldatomcraft.physics.optics.RayFrame3;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * D22C physics-only reference for continuous transverse displacement and ideal
 * analyser rotation. It is NOT a Minecraft motor, hardware encoder, optical
 * network replacement, nor a source of authenticated measurements.
 *
 * <p>All geometry is in a declared laboratory-local SI frame. The detector is
 * flat and perpendicular to a single Gaussian beam, with a circular aperture.
 * This is valid for the ideal TEM00/normal-incidence model only. It does not
 * handle tilted aperture projection, finite element thickness, clipping along
 * the optical path, scattering, interference or device response.</p>
 */
public final class ContinuousBenchScanReference {
    /** Explicit optics: a missing analyser must not be silently assumed. */
    public enum Train { NO_ANALYSER, ANALYSER, HALF_WAVE_AND_ANALYSER }

    /**
     * Quantised *input representation* for a future encoder, not a claim of
     * calibrated nano-unit precision. Coordinates are relative to the Gaussian
     * axis at the detector plane, not to the Minecraft world origin.
     */
    public record Pose(long xNanometres, long yNanometres,
                       long analyserNanoradians, long waveplateNanoradians) {
        public Pose {
            bound(xNanometres, "x");
            bound(yNanometres, "y");
            bound(analyserNanoradians, "analyser angle");
            bound(waveplateNanoradians, "waveplate angle");
        }
        public static Pose transverse(long xNanometres, long yNanometres) {
            return new Pose(xNanometres, yNanometres, 0, 0);
        }
        private static void bound(long value, String field) {
            if (value < -StageScanPlan.MAX_ABS_UNITS
                    || value > StageScanPlan.MAX_ABS_UNITS) {
                throw new IllegalArgumentException(field + " outside D22 reference coordinate limits");
            }
        }
        public double xMetres() { return xNanometres * 1e-9; }
        public double yMetres() { return yNanometres * 1e-9; }
        public double analyserRadians() { return analyserNanoradians * 1e-9; }
        public double waveplateRadians() { return waveplateNanoradians * 1e-9; }
    }

    /** One deterministic *calculated* point. Not a server power-meter sample. */
    public record PredictedPoint(long calculationTick, Pose pose, Train train,
                                 double capturedWatts, double rejectedByAnalyserWatts,
                                 double missedByApertureWatts,
                                 double spatialFraction, double polarisationFraction) {
        public PredictedPoint {
            if (calculationTick < 0 || pose == null || train == null
                    || !finiteNonNegative(capturedWatts)
                    || !finiteNonNegative(rejectedByAnalyserWatts)
                    || !finiteNonNegative(missedByApertureWatts)
                    || !Double.isFinite(spatialFraction) || spatialFraction < 0 || spatialFraction > 1
                    || !Double.isFinite(polarisationFraction) || polarisationFraction < 0
                    || polarisationFraction > 1) {
                throw new IllegalArgumentException("invalid ideal predicted point");
            }
        }
        private static boolean finiteNonNegative(double x) {
            return Double.isFinite(x) && x >= 0;
        }
    }

    private final GaussianBeam beam;
    private final RayFrame3 frame;
    private final double detectorAxialMetres;
    private final double apertureRadiusMetres;
    private final JonesVector unitInputJones;

    public ContinuousBenchScanReference(GaussianBeam beam, RayFrame3 frame,
                                        double detectorAxialMetres,
                                        double apertureRadiusMetres,
                                        JonesVector unitInputJones) {
        this.beam = Objects.requireNonNull(beam, "beam");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.unitInputJones = Objects.requireNonNull(unitInputJones, "input Jones vector");
        if (!Double.isFinite(detectorAxialMetres) || detectorAxialMetres <= 0
                || !Double.isFinite(apertureRadiusMetres) || apertureRadiusMetres <= 0) {
            throw new IllegalArgumentException("positive finite axial distance and aperture required");
        }
        if (beam.directionLab().dot(frame.direction()) < 1 - 1e-12) {
            throw new IllegalArgumentException("Gaussian beam and local transverse frame disagree");
        }
        double norm = unitInputJones.squaredNorm();
        if (Math.abs(norm - 1.0) > 1e-10) {
            throw new IllegalArgumentException("input Jones state must have unit power norm");
        }
        this.detectorAxialMetres = detectorAxialMetres;
        this.apertureRadiusMetres = apertureRadiusMetres;
    }

    public GaussianBeam beam() { return beam; }
    public RayFrame3 frame() { return frame; }
    public double detectorAxialMetres() { return detectorAxialMetres; }
    public double apertureRadiusMetres() { return apertureRadiusMetres; }

    /**
     * Pure physics reference. The caller must not interpret a supplied pose as
     * encoder-authenticated until a trusted adapter verifies device identity,
     * revision, motor stopped and stable settling over real server ticks.
     */
    public PredictedPoint predict(long calculationTick, Pose pose, Train train) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(train, "train");
        if (calculationTick < 0) throw new IllegalArgumentException("negative calculation tick");

        // Explicit local origin avoids resolving nanometre offsets near huge MC coordinates.
        Vec3 center = beam.waistPositionLabMeters()
                .add(frame.direction().scale(detectorAxialMetres))
                .add(frame.xAxis().scale(pose.xMetres()))
                .add(frame.yAxis().scale(pose.yMetres()));
        var face = new IdealCircularDetectorFace(center,
                frame.direction().scale(-1), apertureRadiusMetres);
        GaussianCircularAperture.Capture footprint = face.capture(beam);
        double spatial = footprint.capturedFraction();

        JonesVector field = unitInputJones;
        if (train == Train.HALF_WAVE_AND_ANALYSER) {
            field = JonesMatrix.halfWave(pose.waveplateRadians()).apply(field);
        }
        if (train != Train.NO_ANALYSER) {
            field = JonesMatrix.idealLinearPolarizer(pose.analyserRadians()).apply(field);
        }
        double polarisation = field.squaredNorm();
        if (!Double.isFinite(polarisation) || polarisation < -1e-12
                || polarisation > 1 + 1e-12) {
            throw new ArithmeticException("ideal passive Jones train violates power bound");
        }
        polarisation = Math.max(0, Math.min(1, polarisation));

        // A uniform, ideal analyser acts on the full beam before collection.
        double transmitted = beam.powerWatts() * polarisation;
        double captured = transmitted * spatial;
        double rejected = beam.powerWatts() - transmitted;
        double missed = transmitted - captured;
        if (!Double.isFinite(captured) || !Double.isFinite(rejected)
                || !Double.isFinite(missed) || captured < 0 || rejected < 0 || missed < 0) {
            throw new ArithmeticException("unrepresentable passive power accounting");
        }
        return new PredictedPoint(calculationTick, pose, train,
                captured, rejected, missed, spatial, polarisation);
    }

    /** Bounded CSV reference; explicitly not a real-data or motor export. */
    public static String predictionCsv(List<PredictedPoint> points) {
        Objects.requireNonNull(points, "points");
        if (points.size() > StageScanPlan.MAX_POINTS) {
            throw new IllegalArgumentException("too many predicted points");
        }
        StringBuilder csv = new StringBuilder("provenance,calculation_tick,x_nm,y_nm,analyser_nrad,"
                + "waveplate_nrad,train,ideal_captured_watts,rejected_analyser_watts,"
                + "missed_aperture_watts,spatial_fraction,polarisation_fraction\n");
        long previous = -1;
        for (PredictedPoint point : points) {
            Objects.requireNonNull(point, "point");
            if (point.calculationTick() <= previous) {
                throw new IllegalArgumentException("prediction ticks must increase strictly");
            }
            previous = point.calculationTick();
            Pose pose = point.pose();
            csv.append("REFERENCE_ONLY,").append(point.calculationTick()).append(',')
                    .append(pose.xNanometres()).append(',').append(pose.yNanometres()).append(',')
                    .append(pose.analyserNanoradians()).append(',')
                    .append(pose.waveplateNanoradians()).append(',').append(point.train()).append(',')
                    .append(String.format(Locale.ROOT, "%.17g", point.capturedWatts())).append(',')
                    .append(String.format(Locale.ROOT, "%.17g", point.rejectedByAnalyserWatts())).append(',')
                    .append(String.format(Locale.ROOT, "%.17g", point.missedByApertureWatts())).append(',')
                    .append(String.format(Locale.ROOT, "%.17g", point.spatialFraction())).append(',')
                    .append(String.format(Locale.ROOT, "%.17g", point.polarisationFraction()))
                    .append('\n');
        }
        return csv.toString();
    }
}
