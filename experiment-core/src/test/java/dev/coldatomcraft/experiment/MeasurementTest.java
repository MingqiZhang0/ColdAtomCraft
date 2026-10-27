package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeasurementTest {
    @Test void allowsZeroNoiseAsAnExplicitIdealizedInstrument() {
        Measurement result = new Measurement("meter-1", "power", "W", 0.2, 0, 100);
        assertEquals("W", result.siUnit());
    }
    @Test void rejectsNegativeUncertainty() {
        assertThrows(IllegalArgumentException.class,
                () -> new Measurement("meter-1", "power", "W", 0.2, -0.01, 100));
    }
    @Test void modelRequiresOwnerReference() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelDefinition("OPT-001", "A", "", ModelApproval.DRAFT));
        assertFalse(new ModelDefinition("OPT-001", "A", "PENDING", ModelApproval.DRAFT).ownerApproved());
    }
}
