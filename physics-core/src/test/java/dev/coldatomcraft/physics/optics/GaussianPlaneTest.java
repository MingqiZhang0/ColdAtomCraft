package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GaussianPlaneTest {
    private static GaussianBeam beam() {
        return new GaussianBeam(0.02, 780e-9, 0.0005,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1));
    }

    @Test void freeSpacePlaneEqualsWaistCentredBeamForPowerAndRadius() {
        var original = beam();
        var waist = GaussianPlane.atAxialDistance(original, 0);
        double distance = original.rayleighRangeMeters();
        var after = waist.through(ParaxialAbcd.freeSpace(distance));
        assertEquals(original.radiusMeters(distance), after.radiusMeters(),
                1e-14 * original.radiusMeters(distance));
        assertEquals(original.intensityWattsPerSquareMeter(0.0002, distance),
                after.intensityWattsPerSquareMeter(0.0002),
                1e-13 * original.intensityWattsPerSquareMeter(0.0002, distance));
        assertEquals(original.powerWatts(), after.powerWatts(), 0);
    }

    @Test void idealLensDoesNotChangePowerOrSpotAtItsOwnPlane() {
        var original = GaussianPlane.atAxialDistance(beam(), 0.03);
        var after = original.through(ParaxialAbcd.thinLens(0.2));
        assertEquals(original.powerWatts(), after.powerWatts(), 0);
        assertEquals(original.radiusMeters(), after.radiusMeters(),
                original.radiusMeters() * 1e-14);
        assertEquals(original.intensityWattsPerSquareMeter(0.0001),
                after.intensityWattsPerSquareMeter(0.0001),
                original.intensityWattsPerSquareMeter(0.0001) * 1e-13);
    }

    @Test void focusChangesSpotSizeButRetainsIdealTransmittedPower() {
        var start = GaussianPlane.atAxialDistance(beam(), 0.0);
        var lens = ParaxialAbcd.freeSpace(0.1)
                .then(ParaxialAbcd.thinLens(0.2))
                .then(ParaxialAbcd.freeSpace(0.4));
        var out = start.through(lens);
        assertTrue(out.radiusMeters() > 0);
        assertTrue(Math.abs(out.radiusMeters() - start.radiusMeters()) > 1e-6);
        assertEquals(start.powerWatts(), out.powerWatts(), 0);
        assertEquals(start.powerWatts() * (1-Math.exp(-2)),
                out.powerWithinRadiusWatts(out.radiusMeters()),
                start.powerWatts() * 1e-14);
    }

    @Test void planeRejectsInvalidParametersAndApertures() {
        GaussianQ q = new GaussianQ(0, 1);
        assertThrows(IllegalArgumentException.class, () -> new GaussianPlane(-1, 780e-9, q));
        assertThrows(IllegalArgumentException.class, () -> new GaussianPlane(1, -1, q));
        assertThrows(IllegalArgumentException.class, () -> new GaussianPlane(1, 780e-9, null));
        assertThrows(IllegalArgumentException.class,
                () -> new GaussianPlane(1, 780e-9, q).powerWithinRadiusWatts(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new GaussianPlane(1, 780e-9, q).intensityWattsPerSquareMeter(Double.NaN));
    }
}
