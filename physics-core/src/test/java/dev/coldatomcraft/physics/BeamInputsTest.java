package dev.coldatomcraft.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeamInputsTest {
    @Test void preservesUnitsAndNormalizesDirection() {
        BeamInputs settings = new BeamInputs(0.02, 780e-9, 1e-3,
                new Vec3(0, 0, 0), new Vec3(0, 0, -2));
        assertEquals(780e-9, settings.vacuumWavelengthMeters());
        assertEquals(-1, settings.outputDirectionLab().z(), 1e-14);
    }
    @Test void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeamInputs(-1, 780e-9, 1e-3,
                        new Vec3(0, 0, 0), new Vec3(1, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new BeamInputs(1, 0, 1e-3,
                        new Vec3(0, 0, 0), new Vec3(1, 0, 0)));
    }
}
