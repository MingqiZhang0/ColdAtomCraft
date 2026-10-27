package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GaussianBeamTest {
    private static final double WAVELENGTH = 780e-9;
    private static final double WAIST = 0.5e-3;
    private static final double POWER = 0.02;

    private static GaussianBeam reference() {
        return new GaussianBeam(POWER, WAVELENGTH, WAIST,
                new Vec3(1, 2, 3), new Vec3(0, 0, -5));
    }

    private static void closeRelative(double actual, double expected, double rel) {
        assertEquals(expected, actual, Math.max(1e-300, Math.abs(expected) * rel));
    }

    @Test void rayleighRangeAndRadiusAreStandardAnalyticLimits() {
        var beam = reference();
        double zr = Math.PI * WAIST * WAIST / WAVELENGTH;
        closeRelative(beam.rayleighRangeMeters(), zr, 1e-14);
        closeRelative(beam.radiusMeters(0), WAIST, 1e-14);
        closeRelative(beam.radiusMeters(zr), Math.sqrt(2) * WAIST, 1e-14);
        closeRelative(beam.radiusMeters(-zr), Math.sqrt(2) * WAIST, 1e-14);
        closeRelative(beam.divergenceParameter(), WAVELENGTH / (Math.PI * WAIST), 1e-14);
        closeRelative(beam.radiusMeters(1000 * zr),
                Math.hypot(WAIST, 1000 * WAIST), 1e-14);
    }

    @Test void irradianceIsCorrectAtWaistOneOverERadiusAndSourceOff() {
        var b = reference();
        double i0 = 2 * POWER / (Math.PI * WAIST * WAIST);
        closeRelative(b.onAxisIntensityWattsPerSquareMeter(0), i0, 1e-14);
        closeRelative(b.intensityWattsPerSquareMeter(WAIST, 0),
                i0 * Math.exp(-2), 1e-14);
        assertEquals(0.0, new GaussianBeam(0, WAVELENGTH, WAIST,
                new Vec3(0, 0, 0), new Vec3(0, 1, 0))
                .intensityWattsPerSquareMeter(0, 0));
    }

    @Test void radialIntegralNumericallyConservesPower() {
        var beam = reference();
        for (double z : new double[] {0, beam.rayleighRangeMeters(),
                -4 * beam.rayleighRangeMeters()}) {
            double w = beam.radiusMeters(z);
            int steps = 20_000;
            double maxR = 5 * w;
            double dr = maxR / steps;
            double sum = 0;
            for (int n = 0; n < steps; n++) {
                double r = (n + 0.5) * dr;
                sum += 2 * Math.PI * r * beam.intensityWattsPerSquareMeter(r, z) * dr;
            }
            closeRelative(sum, POWER, 1e-7);
        }
    }

    @Test void enclosedPowerAndNarrowApertureStable() {
        var beam = reference();
        double w = beam.radiusMeters(0);
        closeRelative(beam.powerWithinRadiusWatts(w, 0),
                POWER * (1 - Math.exp(-2)), 1e-14);
        double tiny = 1e-9 * w;
        closeRelative(beam.powerWithinRadiusWatts(tiny, 0), POWER * 2e-18, 1e-14);
        assertEquals(0, beam.powerWithinRadiusWatts(0, 0), 0);
        closeRelative(beam.powerWithinRadiusWatts(100 * w, 0), POWER, 1e-14);
    }

    @Test void laboratoryAxesMustNotBeConfusedWithMinecraftBlocks() {
        var beam = reference();
        double zr = beam.rayleighRangeMeters();
        var local = new Vec3(1.3, 2.4, 3 - zr);
        closeRelative(beam.axialCoordinateMeters(local), zr, 1e-14);
        closeRelative(beam.intensityAtLabPointWattsPerSquareMeter(local),
                beam.intensityWattsPerSquareMeter(0.5, zr), 1e-14);
        var diagonal = new GaussianBeam(POWER, WAVELENGTH, WAIST,
                new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        var zAxis = diagonal.directionLab();
        var transverse = new Vec3(1, -1, 0).normalized();
        var point = zAxis.scale(zr).add(transverse.scale(0.25e-3));
        closeRelative(diagonal.intensityAtLabPointWattsPerSquareMeter(point),
                diagonal.intensityWattsPerSquareMeter(0.25e-3, zr), 1e-12);
        assertEquals(1.0, diagonal.directionLab().magnitude(), 1e-14);
    }

    @Test void qGouyAndCurvatureRespectWaistAndNegativeZ() {
        var beam = reference();
        double zr = beam.rayleighRangeMeters();
        assertEquals(new GaussianQ(0, zr), beam.complexParameterAt(0));
        assertEquals(Math.PI / 4, beam.gouyPhaseRadians(zr), 1e-14);
        assertEquals(-Math.PI / 4, beam.gouyPhaseRadians(-zr), 1e-14);
        assertEquals(Double.POSITIVE_INFINITY, beam.curvatureRadiusMeters(0));
        closeRelative(beam.curvatureRadiusMeters(zr), 2 * zr, 1e-14);
        closeRelative(beam.curvatureRadiusMeters(-zr), -2 * zr, 1e-14);
        closeRelative(beam.complexParameterAt(zr).radiusMeters(WAVELENGTH),
                beam.radiusMeters(zr), 1e-14);
    }

    @Test void illegalOrNonRepresentablePhysicalInputsFailExplicitly() {
        var origin = new Vec3(0, 0, 0);
        var axis = new Vec3(1, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new GaussianBeam(-1, WAVELENGTH, WAIST, origin, axis));
        assertThrows(IllegalArgumentException.class,
                () -> new GaussianBeam(1, 0, WAIST, origin, axis));
        assertThrows(IllegalArgumentException.class,
                () -> new GaussianBeam(1, WAVELENGTH, 0, origin, axis));
        assertThrows(IllegalArgumentException.class,
                () -> new GaussianBeam(1, WAVELENGTH, WAIST, origin, new Vec3(0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new GaussianBeam(Double.MAX_VALUE, WAVELENGTH, 1e-200, origin, axis));
        assertThrows(IllegalArgumentException.class,
                () -> reference().intensityWattsPerSquareMeter(-1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> reference().powerWithinRadiusWatts(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> reference().radiusMeters(Double.POSITIVE_INFINITY));
    }
}
