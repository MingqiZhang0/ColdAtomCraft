package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LaserTuningTest {
    @Test void defaultsPreserveD10LabFixture() {
        var t = LaserTuning.DEFAULT;
        assertEquals(.020, t.powerWatts(), 1e-15);
        assertEquals(780e-9, t.wavelengthMeters(), 1e-18);
        assertEquals(.0005, t.waistRadiusMeters(), 1e-16);
        assertEquals(0, t.waistOffsetMeters(), 1e-16);
        assertEquals(PreviewOpticalBench.sampleAligned(4, 0),
                PreviewOpticalBench.sampleAligned(4, 0, t));
    }

    @Test void strictRangeLimitsAndIndependentFields() {
        var t = LaserTuning.DEFAULT;
        assertThrows(IllegalArgumentException.class, () -> t.withField(0, 0));
        assertThrows(IllegalArgumentException.class, () -> t.withField(1, 1200));
        assertThrows(IllegalArgumentException.class, () -> t.withField(2, 30));
        assertThrows(IllegalArgumentException.class, () -> t.withField(3, 2001));
        assertThrows(IllegalArgumentException.class, () -> t.withField(4, 50));
        assertEquals(new LaserTuning(50, 780, 500, 0), t.withField(0, 50));
        assertEquals(new LaserTuning(20, 650, 500, 0), t.withField(1, 650));
        assertEquals(new LaserTuning(20, 780, 1200, 0), t.withField(2, 1200));
        assertEquals(new LaserTuning(20, 780, 500, -2000), t.withField(3, -2000));
    }

    @Test void corruptSavedParametersResetToKnownDefault() {
        assertEquals(LaserTuning.DEFAULT, LaserTuning.fromStored(0, 780, 500, 0));
        assertEquals(LaserTuning.DEFAULT, LaserTuning.fromStored(20, 0, 500, 0));
        assertEquals(LaserTuning.DEFAULT, LaserTuning.fromStored(20, 780, 0, 0));
        assertEquals(LaserTuning.DEFAULT, LaserTuning.fromStored(20, 780, 500, 99_999));
        assertEquals(new LaserTuning(50, 650, 1200, 500), LaserTuning.fromStored(50, 650, 1200, 500));
    }

    @Test void powerMeterRespondsLinearlyToSourcePower() {
        var low = LaserTuning.DEFAULT.withField(0, 10);
        var high = low.withField(0, 150);
        double a = PreviewOpticalBench.sampleAligned(5, 0, low).observation().value();
        double b = PreviewOpticalBench.sampleAligned(5, 0, high).observation().value();
        assertEquals(15.0 * a, b, Math.max(1e-15, b * 1e-10));
    }

    @Test void wavelengthAndWaistAffectDistantCollection() {
        var baseline = LaserTuning.DEFAULT;
        var longerWave = baseline.withField(1, 1100);
        var widerWaist = baseline.withField(2, 1500);
        double standard = PreviewOpticalBench.sampleAligned(15, 0, baseline).observation().value();
        double wideDivergence = PreviewOpticalBench.sampleAligned(15, 0, longerWave).observation().value();
        double lowDivergence = PreviewOpticalBench.sampleAligned(15, 0, widerWaist).observation().value();
        assertTrue(wideDivergence < standard);
        assertTrue(lowDivergence > standard);
    }

    @Test void waistPositionActuallyShiftsBeamShape() {
        var t = LaserTuning.DEFAULT.withField(3, 1500);
        assertEquals(t.waistRadiusMeters(), t.beamRadiusAtPortDistanceMeters(1.5), 1e-15);
        assertTrue(t.beamRadiusAtPortDistanceMeters(0) > t.beamRadiusAtPortDistanceMeters(1.5));
        assertTrue(t.beamRadiusAtPortDistanceMeters(3.5) > t.beamRadiusAtPortDistanceMeters(1.5));
    }

    @Test void serverRouteReadsExactMatchedEmitterSettings() {
        var source = LaserTuning.DEFAULT.withField(0, 80).withField(1, 650);
        var result = PreviewBeamRoute.scan(0,
                d -> d == 3 ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR,
                d -> source);
        assertEquals(PreviewBeamRoute.Status.MEASURED, result.status());
        assertEquals(PreviewOpticalBench.sampleAligned(3, 0, source).observation().value(),
                result.powerWatts(), 1e-15);
        assertTrue(result.powerWatts() > PreviewBeamRoute.scan(0,
                d -> d == 3 ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR).powerWatts());
    }

    @Test void missingSourceSettingsFailClosed() {
        var result = PreviewBeamRoute.scan(0,
                d -> d == 4 ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR,
                d -> null);
        assertEquals(PreviewBeamRoute.Status.SOURCE_CONFIG_MISSING, result.status());
        assertEquals(0, result.powerWatts());
        assertEquals(4, result.separationBlocks());
    }

    @Test void displayAndMeterShareSameGaussianRadius() {
        var t = new LaserTuning(30, 1064, 750, 500);
        double dist = 7.75;
        assertEquals(t.beamRadiusAtPortDistanceMeters(dist),
                PreviewBeamDisplay.physicalRadiusMeters(dist, t), 1e-15);
        assertTrue(PreviewBeamDisplay.displayedRadiusBlocks(dist, t) > 0);
        assertTrue(PreviewBeamDisplay.displayedRadiusBlocks(dist, t) <= 0.16);
    }
}
