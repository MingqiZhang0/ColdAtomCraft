package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.motion.ContinuousBenchScanReference;
import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.ComplexAmplitude;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.JonesVector;
import dev.coldatomcraft.physics.optics.RayFrame3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Independent deterministic D22C optical physics reference, not game motor execution. */
public final class ContinuousOpticsSmoke {
    private static long checks;
    private ContinuousOpticsSmoke() { }
    private static void check(boolean pass, String description) {
        checks++;
        if (!pass) throw new AssertionError(description);
    }
    private static void close(double a, double b, double tolerance, String description) {
        check(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a-b) <= tolerance,
                description + " actual=" + a + " expected=" + b);
    }
    /** Independent 2D Simpson integration of a displaced Gaussian over a disk. */
    private static double referenceDiskFraction(double w, double a, double d) {
        int nr=160, nf=192;
        double total=0;
        for (int i=0;i<=nr;i++) {
            double radius=a*i/nr;
            double radWeight=(i==0||i==nr)?1:(i%2==0?2:4);
            double angular=0;
            for (int j=0;j<=nf;j++) {
                double angle=2*Math.PI*j/nf;
                double squared=radius*radius+d*d-2*radius*d*Math.cos(angle);
                int angleWeight=(j==0||j==nf)?1:(j%2==0?2:4);
                angular+=angleWeight*Math.exp(-2*squared/(w*w));
            }
            total+=radWeight*radius*angular*(2*Math.PI/nf)/3;
        }
        return total*(a/nr)/3*(2/(Math.PI*w*w));
    }
    public static void main(String[] args) {
        double power = 0.025;
        var beam = new GaussianBeam(power, 780e-9, 0.0005,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1));
        var frame = RayFrame3.fromDirectionAndXAxis(new Vec3(0, 0, 1),
                new Vec3(1, 0, 0));
        var bench = new ContinuousBenchScanReference(beam, frame, 1.2, 0.0006,
                JonesVector.horizontal());
        double radiusAtReceiver=beam.radiusMeters(1.2);
        // Independent numerical 2D integration verifies the *geometry* of
        // the new local-frame aperture mapping, not only the same production function.
        for(int i=0;i<=16;i++) {
            long offset=i*65_000L;
            double d=offset*1e-9;
            var p=bench.predict(i,
                    ContinuousBenchScanReference.Pose.transverse(offset,0),
                    ContinuousBenchScanReference.Train.NO_ANALYSER);
            close(p.spatialFraction(),referenceDiskFraction(radiusAtReceiver,0.0006,d),
                    3e-9,"independent 2D disk quadrature");
        }
        Random random = new Random(0xD22C2026L);
        var samples = new ArrayList<ContinuousBenchScanReference.PredictedPoint>();
        for (int i = 0; i < 8_000; i++) {
            long x = random.nextInt(1_200_001) - 600_000L;
            long y = random.nextInt(1_200_001) - 600_000L;
            long radians = Math.round((random.nextDouble()*2*Math.PI-Math.PI)*1e9);
            var point = new ContinuousBenchScanReference.Pose(x, y, radians,
                    Math.round(random.nextDouble()*Math.PI*1e9));
            var mode = ContinuousBenchScanReference.Train.values()[i % 3];
            var result = bench.predict(i, point, mode);
            close(result.capturedWatts() + result.rejectedByAnalyserWatts()
                    + result.missedByApertureWatts(), power, 1e-13, "passive power balance");
            check(result.capturedWatts() >= 0 && result.capturedWatts() <= power,
                    "captured power within input");
            check(result.spatialFraction() >= 0 && result.spatialFraction() <= 1,
                    "aperture capture fraction");
            check(result.polarisationFraction() >= 0 && result.polarisationFraction() <= 1,
                    "ideal Jones transmission");
            var reflected = bench.predict(i, new ContinuousBenchScanReference.Pose(-x, -y,
                    radians, point.waveplateNanoradians()), mode);
            close(result.spatialFraction(), reflected.spatialFraction(), 2e-12,
                    "transverse central symmetry");
            check(result.pose().xNanometres() == x && result.pose().yNanometres() == y,
                    "integer SI provenance preserved");
            if (i < 128) samples.add(result);
        }
        // Independent analytic Malus formula; do not use the production Jones result.
        double alignedCapture = bench.predict(0,
                ContinuousBenchScanReference.Pose.transverse(0, 0),
                ContinuousBenchScanReference.Train.NO_ANALYSER).capturedWatts();
        for (int i = 0; i <= 1024; i++) {
            double angle = i * Math.PI / 1024;
            long nrad = Math.round(angle * 1e9);
            var pose = new ContinuousBenchScanReference.Pose(0, 0, nrad, 0);
            var result = bench.predict(i, pose, ContinuousBenchScanReference.Train.ANALYSER);
            close(result.capturedWatts(), alignedCapture*Math.pow(Math.cos(angle), 2),
                    7e-11, "Malus theta curve");
        }
        double u = 1.0 / Math.sqrt(2);
        var circular = new JonesVector(new ComplexAmplitude(u, 0), new ComplexAmplitude(0, u));
        var circularBench = new ContinuousBenchScanReference(beam, frame, 1.2, 0.0006, circular);
        for (int i = 0; i < 100; i++) {
            long angle = Math.round(i * 0.016 * 1e9);
            var measured = circularBench.predict(i,
                    new ContinuousBenchScanReference.Pose(0, 0, angle, 0),
                    ContinuousBenchScanReference.Train.ANALYSER);
            close(measured.polarisationFraction(), 0.5, 1e-12,
                    "ideal circular state passes half of any linear analyser");
        }
        check(ContinuousBenchScanReference.predictionCsv(samples).lines().count() == 129,
                "bounded CSV exported");
        check(ContinuousBenchScanReference.predictionCsv(samples).contains("REFERENCE_ONLY"),
                "synthetic provenance explicit");
        System.out.println("D22C ContinuousOpticsSmoke PASS " + checks + "/" + checks);
    }
}
