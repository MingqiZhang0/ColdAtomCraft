package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewOpticalBenchTest {
    @Test void sourceParametersAreLabelledDiagnosticConstants() {
        assertEquals(0.020, PreviewOpticalBench.DEMO_POWER_WATTS);
        assertEquals(780e-9, PreviewOpticalBench.DEMO_WAVELENGTH_METERS);
        assertEquals(0.0005, PreviewOpticalBench.DEMO_WAIST_RADIUS_METERS);
    }

    @Test void aperturePresetsAreExactlyThreeIncreasingValues() {
        assertEquals(3, PreviewOpticalBench.aperturePresetCount());
        assertEquals(0.0005, PreviewOpticalBench.apertureRadiusMeters(0));
        assertEquals(0.0010, PreviewOpticalBench.apertureRadiusMeters(1));
        assertEquals(0.0020, PreviewOpticalBench.apertureRadiusMeters(2));
    }

    @Test void invalidPresetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PreviewOpticalBench.apertureRadiusMeters(-1));
        assertThrows(IllegalArgumentException.class, () -> PreviewOpticalBench.apertureRadiusMeters(3));
    }

    @Test void invalidSeparationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PreviewOpticalBench.sampleAligned(0, 0));
        assertThrows(IllegalArgumentException.class, () -> PreviewOpticalBench.sampleAligned(25, 0));
    }

    @Test void validReadingIsNoninvasiveAndInWatts() {
        var reading = PreviewOpticalBench.sampleAligned(3, 0);
        assertEquals("W", reading.observation().siUnit());
        assertEquals("ideal_collected_optical_power", reading.observation().observable());
        assertEquals(0, reading.observation().simulatedTimeNanos());
        assertTrue(reading.observation().value() > 0);
        assertTrue(reading.observation().value() < PreviewOpticalBench.DEMO_POWER_WATTS);
    }

    @Test void wideningApertureIncreasesPower() {
        double a = PreviewOpticalBench.sampleAligned(4, 0).observation().value();
        double b = PreviewOpticalBench.sampleAligned(4, 1).observation().value();
        double c = PreviewOpticalBench.sampleAligned(4, 2).observation().value();
        assertTrue(a < b);
        assertTrue(b < c);
        assertTrue(c <= PreviewOpticalBench.DEMO_POWER_WATTS);
    }

    @Test void movingDetectorAwayDecreasesFixedApertureReading() {
        double near = PreviewOpticalBench.sampleAligned(2, 0).observation().value();
        double far = PreviewOpticalBench.sampleAligned(12, 0).observation().value();
        assertTrue(near > far);
    }

    @Test void readingIsDeterministic() {
        assertEquals(PreviewOpticalBench.sampleAligned(4, 2),
                     PreviewOpticalBench.sampleAligned(4, 2));
    }

    @Test void capturedAndMissedPowersCloseBudget() {
        var reading = PreviewOpticalBench.sampleAligned(9, 1);
        var capture = reading.apertureBudget();
        assertEquals(PreviewOpticalBench.DEMO_POWER_WATTS,
                capture.capturedPowerWatts() + capture.uncapturedPowerWatts(), 1e-12);
    }

    @Test void noGameTickToPhysicalTimeConversionIsImplied() {
        assertEquals(0L, PreviewOpticalBench.sampleAligned(1, 0)
                .observation().simulatedTimeNanos());
    }
}
