package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.ComplexAmplitude;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.JonesVector;
import dev.coldatomcraft.physics.optics.RayFrame3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D22C physics-only tests, not evidence of game motor/encoder integration. */
class ContinuousBenchScanReferenceTest {
    private static final double POWER = 0.02;
    private static ContinuousBenchScanReference bench(JonesVector field) {
        var beam = new GaussianBeam(POWER, 780e-9, 0.0005,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1));
        return new ContinuousBenchScanReference(beam,
                RayFrame3.fromDirectionAndXAxis(new Vec3(0, 0, 1), new Vec3(1, 0, 0)),
                0.5, 0.00075, field);
    }
    private static long angle(double radians) { return Math.round(radians * 1e9); }

    @Test void centredGaussianMatchesExactApertureFormula() {
        var system = bench(JonesVector.horizontal());
        var result = system.predict(11, ContinuousBenchScanReference.Pose.transverse(0, 0),
                ContinuousBenchScanReference.Train.NO_ANALYSER);
        double w = system.beam().radiusMeters(system.detectorAxialMetres());
        double f = -Math.expm1(-2 * Math.pow(system.apertureRadiusMetres() / w, 2));
        assertEquals(f, result.spatialFraction(), 1e-12);
        assertEquals(POWER * f, result.capturedWatts(), 1e-14);
        assertEquals(0, result.rejectedByAnalyserWatts(), 1e-14);
    }
    @Test void lateralOffsetsAreRadiallySymmetric() {
        var system = bench(JonesVector.horizontal());
        var p = system.predict(0, ContinuousBenchScanReference.Pose.transverse(300_000, 0),
                ContinuousBenchScanReference.Train.NO_ANALYSER);
        var q = system.predict(1, ContinuousBenchScanReference.Pose.transverse(0, -300_000),
                ContinuousBenchScanReference.Train.NO_ANALYSER);
        assertEquals(p.capturedWatts(), q.capturedWatts(), 1e-13);
    }
    @Test void transverseDisplacementReducesApertureCollection() {
        var system = bench(JonesVector.horizontal());
        double centred = system.predict(0, ContinuousBenchScanReference.Pose.transverse(0, 0),
                ContinuousBenchScanReference.Train.NO_ANALYSER).capturedWatts();
        double offset = system.predict(1, ContinuousBenchScanReference.Pose.transverse(600_000, 0),
                ContinuousBenchScanReference.Train.NO_ANALYSER).capturedWatts();
        assertTrue(offset < centred);
        assertTrue(offset > 0);
    }
    @Test void idealAnalyserSatisfiesMalusLaw() {
        var system = bench(JonesVector.horizontal());
        for (int i = 0; i <= 16; i++) {
            double angle = i * Math.PI / 16;
            var r = system.predict(i, new ContinuousBenchScanReference.Pose(0, 0,
                    angle(angle), 0), ContinuousBenchScanReference.Train.ANALYSER);
            assertEquals(Math.pow(Math.cos(angle), 2), r.polarisationFraction(), 1.3e-9);
        }
    }
    @Test void withoutAnalyserHalfWaveRetardanceIsNotInserted() {
        var system = bench(JonesVector.horizontal());
        var result = system.predict(0, new ContinuousBenchScanReference.Pose(0, 0,
                angle(Math.PI / 2), angle(Math.PI / 4)),
                ContinuousBenchScanReference.Train.NO_ANALYSER);
        assertEquals(1.0, result.polarisationFraction(), 1e-12);
    }
    @Test void halfWavePlateRequiresAnalyserToProduceAngularPowerScan() {
        var system = bench(JonesVector.horizontal());
        var r = system.predict(0, new ContinuousBenchScanReference.Pose(0, 0, 0,
                angle(Math.PI / 8)), ContinuousBenchScanReference.Train.HALF_WAVE_AND_ANALYSER);
        assertEquals(0.5, r.polarisationFraction(), 2e-9);
    }
    @Test void circularInputGivesHalfIntensityInAnyLinearAnalyser() {
        double u = 1 / Math.sqrt(2);
        var circular = new JonesVector(new ComplexAmplitude(u, 0), new ComplexAmplitude(0, u));
        var system = bench(circular);
        for (int i = 0; i <= 8; i++) {
            var p = system.predict(i, new ContinuousBenchScanReference.Pose(0, 0,
                    angle(i * Math.PI / 8), 0), ContinuousBenchScanReference.Train.ANALYSER);
            assertEquals(0.5, p.polarisationFraction(), 1e-12);
        }
    }
    @Test void passiveEnergyBudgetIsConserved() {
        var system = bench(JonesVector.linearRadians(Math.PI / 5));
        for (int i = 0; i < 600; i++) {
            var p = system.predict(i, new ContinuousBenchScanReference.Pose(
                    (i % 31 - 15L) * 45_000L, (i % 17 - 8L) * 23_000L,
                    angle(i * 0.01), angle(i * 0.021)),
                    ContinuousBenchScanReference.Train.HALF_WAVE_AND_ANALYSER);
            assertEquals(POWER, p.capturedWatts() + p.rejectedByAnalyserWatts()
                    + p.missedByApertureWatts(), 2e-14);
            assertTrue(p.capturedWatts() <= POWER);
        }
    }
    @Test void invalidReferenceGeometryAndJonesInputAreRejected() {
        var good = bench(JonesVector.horizontal());
        assertThrows(IllegalArgumentException.class, () -> good.predict(-1,
                ContinuousBenchScanReference.Pose.transverse(0, 0),
                ContinuousBenchScanReference.Train.NO_ANALYSER));
        assertThrows(IllegalArgumentException.class, () -> new ContinuousBenchScanReference.Pose(
                StageScanPlan.MAX_ABS_UNITS+1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> bench(JonesVector.horizontal().scale(0.5)));
        assertThrows(IllegalArgumentException.class, () -> new ContinuousBenchScanReference(
                good.beam(), RayFrame3.fromDirectionAndXAxis(new Vec3(0, 0, -1),
                        new Vec3(1, 0, 0)), 1, 0.001, JonesVector.horizontal()));
    }
    @Test void largeWorldCoordinatesCannotSilentlyEraseMicrometreOffsets() {
        var beam = new GaussianBeam(POWER, 780e-9, 0.0005,
                new Vec3(1e15, 0, 0), new Vec3(0, 0, 1));
        var system = new ContinuousBenchScanReference(beam,
                RayFrame3.fromDirectionAndXAxis(new Vec3(0, 0, 1), new Vec3(1, 0, 0)),
                1, 0.00075, JonesVector.horizontal());
        assertThrows(IllegalArgumentException.class, () -> system.predict(0,
                ContinuousBenchScanReference.Pose.transverse(200_000, 0),
                ContinuousBenchScanReference.Train.NO_ANALYSER));
    }
    @Test void csvMarksPredictionsAndPreservesInputTicks() {
        var s = bench(JonesVector.horizontal());
        var a = s.predict(1L << 45, ContinuousBenchScanReference.Pose.transverse(-10, 20),
                ContinuousBenchScanReference.Train.NO_ANALYSER);
        var b = s.predict((1L << 45) + 1, ContinuousBenchScanReference.Pose.transverse(0, 30),
                ContinuousBenchScanReference.Train.ANALYSER);
        String csv = ContinuousBenchScanReference.predictionCsv(List.of(a, b));
        assertTrue(csv.contains("REFERENCE_ONLY,35184372088832,-10,20"));
        assertEquals(3, csv.lines().count());
        assertThrows(IllegalArgumentException.class,
                () -> ContinuousBenchScanReference.predictionCsv(List.of(b, a)));
    }
}
