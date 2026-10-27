package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoherentOpticsTest {
    private static final double LAMBDA = 780e-9;
    private static final double WAIST = 500e-6;
    private static final double POWER = 0.02;
    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final Vec3 NEG_Z = new Vec3(0, 0, -1);

    private static JonesGaussianBeam beam(String id, String group, Vec3 dir,
                                          JonesVector jones, double phase) {
        return new JonesGaussianBeam(id, group,
                new GaussianBeam(POWER, LAMBDA, WAIST, ORIGIN, dir), X, jones, phase);
    }

    private static void rel(double expected, double actual, double fractionalTolerance) {
        assertEquals(expected, actual,
                Math.max(1e-13, Math.abs(expected) * fractionalTolerance));
    }

    @Test void oneBeamElectricEquivalentMatchesGaussianAndPoyntingDirection() {
        JonesGaussianBeam b = beam("beam-1", "common", Z, JonesVector.horizontal(), 0);
        Vec3[] points = {ORIGIN, new Vec3(0, 0, 0.12), new Vec3(0.0002, 0, 0.15)};
        for (Vec3 point : points) {
            double expected = b.beam().intensityAtLabPointWattsPerSquareMeter(point);
            var sum = CoherentOptics.coherentAtLabPoint(point, List.of(b));
            rel(expected, sum.electricIntensityEquivalentWattsPerSquareMeter(), 1e-12);
            Vec3 s = sum.timeAveragePoyntingWattsPerSquareMeter();
            rel(expected, s.z(), 1e-12);
            rel(0, s.x(), 0);
            rel(0, s.y(), 0);
        }
    }

    @Test void equalCopropagatingFieldsCreateFourTimesOrZeroIntensity() {
        var b1 = beam("arm-1", "laser-1", Z, JonesVector.horizontal(), 0);
        var constructive = beam("arm-2", "laser-1", Z, JonesVector.horizontal(), 0);
        var destructive = beam("arm-3", "laser-1", Z, JonesVector.horizontal(), Math.PI);
        double i = b1.beam().onAxisIntensityWattsPerSquareMeter(0);
        rel(4*i, CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(b1, constructive))
                .electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
        rel(0, CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(b1, destructive))
                .electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
    }

    @Test void quadraturePhaseAndOrthogonalPolarizationRemoveInterferenceCrossTerm() {
        var h = beam("h", "shared", Z, JonesVector.horizontal(), 0);
        double i = h.beam().onAxisIntensityWattsPerSquareMeter(0);
        var quadrature = beam("q", "shared", Z, JonesVector.horizontal(), Math.PI/2);
        rel(2*i, CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(h, quadrature))
                .electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
        var v = beam("v", "shared", Z, JonesVector.vertical(), 0);
        rel(2*i, CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(h, v))
                .electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
    }

    @Test void counterpropagatingStandingWaveHasFringesButZeroNetPowerFlux() {
        var forward = beam("forward", "lattice", Z, JonesVector.horizontal(), 0);
        var backward = beam("backward", "lattice", NEG_Z, JonesVector.horizontal(), 0);
        double i = forward.beam().onAxisIntensityWattsPerSquareMeter(0);
        var antinode = CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(forward, backward));
        rel(4*i, antinode.electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
        rel(0, antinode.timeAveragePoyntingWattsPerSquareMeter().magnitude(), 0);
        var node = CoherentOptics.coherentAtLabPoint(new Vec3(0, 0, LAMBDA/4),
                List.of(forward, backward));
        // A Gaussian is NOT a plane wave: oppositely travelling Gouy phases
        // shift the node slightly. At z=lambda/4 the exact Gaussian result is
        // 4 I(z) sin^2(atan(z/zR)), tiny but not mathematically zero.
        double z = LAMBDA / 4;
        double gouy = Math.atan(z / forward.beam().rayleighRangeMeters());
        double expectedNode = 4 * forward.beam().onAxisIntensityWattsPerSquareMeter(z)
                * Math.sin(gouy) * Math.sin(gouy);
        rel(expectedNode, node.electricIntensityEquivalentWattsPerSquareMeter(), 2e-8);
        rel(0, node.timeAveragePoyntingWattsPerSquareMeter().magnitude(), 1e-10);
    }

    @Test void nonCollinearBeamsUseCommonCartesianElectricFieldNotScalarJonesCoordinates() {
        var alongZ = beam("z", "crossing", Z, JonesVector.vertical(), 0);
        // For k along X, choose local transverse x along Y: Jones horizontal
        // is therefore the same global Y orientation as beam along Z's Jones vertical.
        var alongX = new JonesGaussianBeam("x", "crossing",
                new GaussianBeam(POWER, LAMBDA, WAIST, ORIGIN, X),
                new Vec3(0, 1, 0), JonesVector.horizontal(), 0);
        double i = alongZ.beam().onAxisIntensityWattsPerSquareMeter(0);
        var result = CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(alongZ, alongX));
        rel(4*i, result.electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
        Vec3 flux = result.timeAveragePoyntingWattsPerSquareMeter();
        rel(2*i, flux.x(), 1e-14);
        rel(2*i, flux.z(), 1e-14);
        rel(0, flux.y(), 0);
    }

    @Test void passThroughPolarizerReducesOpticalPowerAndPeakIntensity() {
        var original = beam("in", "laser", Z, JonesVector.horizontal(), 0);
        JonesVector outputJones = JonesMatrix.idealLinearPolarizer(Math.PI/4)
                .apply(JonesVector.horizontal());
        var transmitted = beam("out", "laser", Z, outputJones, 0);
        rel(POWER/2, transmitted.transmittedPowerWatts(), 1e-14);
        rel(original.sampleAtLabPoint(ORIGIN).electricIntensityEquivalentWattsPerSquareMeter()/2,
                transmitted.sampleAtLabPoint(ORIGIN).electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
    }

    @Test void calibratedHalfWavePlatePathPhaseCanReverseInterferenceWithoutChangingPower() {
        // In the convention where the selected eigenaxis has zero common
        // path phase, a half-wave at theta=pi/2 multiplies horizontal by -1.
        // This is NOT a prediction about a real plate's unknown absolute path
        // phase: see the explicit 3-argument idealRetarder overload.
        var unchanged = beam("plain", "arm-phase", Z, JonesVector.horizontal(), 0);
        var throughPlate = beam("plate", "arm-phase", Z,
                JonesMatrix.halfWave(Math.PI/2).apply(JonesVector.horizontal()), 0);
        double i=unchanged.beam().onAxisIntensityWattsPerSquareMeter(0);
        rel(POWER, throughPlate.transmittedPowerWatts(), 1e-14);
        rel(0, CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(unchanged, throughPlate))
                .electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
        rel(i, throughPlate.sampleAtLabPoint(ORIGIN)
                .electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
        var compensated = beam("plate-compensated", "arm-phase", Z,
                JonesMatrix.idealRetarder(Math.PI/2, Math.PI, Math.PI)
                        .apply(JonesVector.horizontal()), 0);
        rel(4*i, CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(unchanged, compensated))
                .electricIntensityEquivalentWattsPerSquareMeter(), 1e-14);
    }

    @Test void incoherentSumUsesIntensitiesAndAllowsDifferentFrequencies() {
        var a = beam("a", "laser-A", Z, JonesVector.horizontal(), 0);
        var b = new JonesGaussianBeam("b", "laser-B",
                new GaussianBeam(POWER, 1064e-9, WAIST, ORIGIN, Z), X,
                JonesVector.horizontal(), Math.PI);
        double i = a.beam().onAxisIntensityWattsPerSquareMeter(0);
        rel(2*i, CoherentOptics.incoherentElectricIntensityEquivalentAtLabPoint(
                ORIGIN, List.of(a, b)), 1e-14);
        assertThrows(IllegalArgumentException.class,
                () -> CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(a, b)));
    }

    @Test void coherentInputMustSharePhaseReferenceAndExactCarrier() {
        var a = beam("a", "laser-A", Z, JonesVector.horizontal(), 0);
        var b = beam("b", "laser-B", Z, JonesVector.horizontal(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(a, b)));
        var c = new JonesGaussianBeam("c", "laser-A",
                new GaussianBeam(POWER, 780.000001e-9, WAIST, ORIGIN, Z),
                X, JonesVector.horizontal(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(a, c)));
        assertThrows(IllegalArgumentException.class,
                () -> CoherentOptics.incoherentElectricIntensityEquivalentAtLabPoint(
                        ORIGIN, List.of(a, beam("d", "laser-A", Z, JonesVector.horizontal(), 0))));
    }

    @Test void duplicateBeamIdentityCannotBeCountedTwice() {
        var b = beam("same-id", "laser", Z, JonesVector.horizontal(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(b, b)));
        assertThrows(IllegalArgumentException.class,
                () -> CoherentOptics.incoherentElectricIntensityEquivalentAtLabPoint(ORIGIN, List.of(b, b)));
        assertThrows(IllegalArgumentException.class,
                () -> CoherentOptics.coherentAtLabPoint(ORIGIN, List.of()));
    }

    @Test void energyDensityOfSingleTravelingBeamHasEqualElectricAndMagneticParts() {
        var b = beam("travel", "energy", Z, JonesVector.horizontal(), 0);
        var result = CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(b));
        double i = b.beam().onAxisIntensityWattsPerSquareMeter(0);
        rel(i / (2 * CoherentOptics.VACUUM_LIGHT_SPEED_METERS_PER_SECOND),
                result.electricEnergyDensityJoulesPerCubicMeter(), 1e-14);
        rel(i / (2 * CoherentOptics.VACUUM_LIGHT_SPEED_METERS_PER_SECOND),
                result.magneticEnergyDensityJoulesPerCubicMeter(), 1e-14);
        rel(i / CoherentOptics.VACUUM_LIGHT_SPEED_METERS_PER_SECOND,
                result.totalEnergyDensityJoulesPerCubicMeter(), 1e-14);
    }

    @Test void standingWaveRedistributesElectricAndMagneticEnergyWithoutNetFlux() {
        var forward = beam("fw-energy", "energy", Z, JonesVector.horizontal(), 0);
        var backward = beam("bw-energy", "energy", NEG_Z, JonesVector.horizontal(), 0);
        double i = forward.beam().onAxisIntensityWattsPerSquareMeter(0);
        double c = CoherentOptics.VACUUM_LIGHT_SPEED_METERS_PER_SECOND;
        var peak = CoherentOptics.coherentAtLabPoint(ORIGIN, List.of(forward, backward));
        rel(2*i/c, peak.electricEnergyDensityJoulesPerCubicMeter(), 1e-14);
        rel(0, peak.magneticEnergyDensityJoulesPerCubicMeter(), 0);
        double z=LAMBDA/4;
        var trough = CoherentOptics.coherentAtLabPoint(new Vec3(0, 0, z),
                List.of(forward, backward));
        double iz=forward.beam().onAxisIntensityWattsPerSquareMeter(z);
        rel(2*iz/c, trough.totalEnergyDensityJoulesPerCubicMeter(), 1e-14);
        assertTrue(trough.electricEnergyDensityJoulesPerCubicMeter()
                < trough.magneticEnergyDensityJoulesPerCubicMeter());
        rel(0, trough.timeAveragePoyntingWattsPerSquareMeter().magnitude(), 1e-14);
    }

    @Test void rejectsInvalidGeometryActiveGainAndMissingPhaseIdentity() {
        GaussianBeam scalar = new GaussianBeam(POWER, LAMBDA, WAIST, ORIGIN, Z);
        assertThrows(IllegalArgumentException.class,
                () -> new JonesGaussianBeam("x", "y", scalar, Z, JonesVector.horizontal(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new JonesGaussianBeam("x", "y", scalar, X,
                        JonesVector.horizontal().scale(2), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new JonesGaussianBeam("x", " ", scalar, X, JonesVector.horizontal(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new JonesGaussianBeam("x", "y", scalar, X, JonesVector.horizontal(), Double.NaN));
    }

    @Test void intensityAtOffAxisPointMatchesGaussianRadialLaw() {
        var b = beam("spot", "reference", Z, JonesVector.linearRadians(Math.PI/7), 0.75);
        Vec3 point = new Vec3(0.00015, -0.00021, 0.7);
        rel(b.beam().intensityAtLabPointWattsPerSquareMeter(point),
                b.sampleAtLabPoint(point).electricIntensityEquivalentWattsPerSquareMeter(), 2e-13);
    }
}
