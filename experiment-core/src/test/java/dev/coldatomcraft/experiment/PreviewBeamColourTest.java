package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewBeamColourTest {
    @Test void visibleWavelengthsGetDistinctDiagnosticColours() {
        assertNotEquals(PreviewBeamColour.rgb(450), PreviewBeamColour.rgb(532));
        assertNotEquals(PreviewBeamColour.rgb(532), PreviewBeamColour.rgb(650));
        assertEquals(0x2FE65D, PreviewBeamColour.rgb(532));
    }

    @Test void infraredUsesConsistentExplicitFalseColour() {
        assertFalse(PreviewBeamColour.infraredFalseColour(700));
        assertTrue(PreviewBeamColour.infraredFalseColour(780));
        assertEquals(0xFF4538, PreviewBeamColour.rgb(780));
        assertEquals(PreviewBeamColour.rgb(780), PreviewBeamColour.rgb(1064));
    }

    @Test void invalidWavelengthRejectedAndHaloIsPale() {
        assertThrows(IllegalArgumentException.class, () -> PreviewBeamColour.rgb(399));
        assertThrows(IllegalArgumentException.class, () -> PreviewBeamColour.rgb(1101));
        int halo = PreviewBeamColour.haloRgb(780);
        int core = PreviewBeamColour.rgb(780);
        assertTrue((halo & 255) > (core & 255));
    }
}
