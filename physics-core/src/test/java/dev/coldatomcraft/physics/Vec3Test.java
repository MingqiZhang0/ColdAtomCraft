package dev.coldatomcraft.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vec3Test {
    @Test void normalizedVectorHasUnitLength() {
        Vec3 unit = new Vec3(3, 4, 0).normalized();
        assertEquals(1.0, unit.magnitude(), 1e-14);
        assertEquals(0.6, unit.x(), 1e-14);
        assertEquals(0.8, unit.y(), 1e-14);
    }

    @Test void normalizationHandlesExtremelySmallAndLargeInputs() {
        assertEquals(new Vec3(1, 0, 0), new Vec3(Double.MIN_VALUE, 0, 0).normalized());
        assertEquals(new Vec3(1, 0, 0), new Vec3(Double.MAX_VALUE, 0, 0).normalized());
    }

    @Test void crossProductIsRightHanded() {
        assertEquals(new Vec3(0, 0, 1), new Vec3(1, 0, 0).cross(new Vec3(0, 1, 0)));
    }

    @Test void nonFiniteValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Vec3(Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Vec3(0, 0, 0).normalized());
    }
}
