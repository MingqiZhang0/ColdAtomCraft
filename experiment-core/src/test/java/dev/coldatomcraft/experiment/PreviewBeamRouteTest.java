package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PreviewBeamRouteTest {
    private static PreviewBeamRoute.Result scan(int preset, int sourceDistance,
                                                 PreviewBeamRoute.Cell sourceCell) {
        return PreviewBeamRoute.scan(preset,
                d -> d == sourceDistance ? sourceCell : PreviewBeamRoute.Cell.AIR);
    }

    @Test void alignedPoweredSourceGivesMeasuredPowerAndCorrectDistance() {
        var result = scan(0, 5, PreviewBeamRoute.Cell.EMITTER_READY);
        assertEquals(PreviewBeamRoute.Status.MEASURED, result.status());
        assertEquals(5, result.separationBlocks());
        assertEquals(PreviewOpticalBench.sampleAligned(5, 0).observation().value(),
                result.powerWatts());
    }

    @Test void noSourceInFiniteRangeReportsNoSource() {
        var result = PreviewBeamRoute.scan(0, d -> PreviewBeamRoute.Cell.AIR);
        assertEquals(PreviewBeamRoute.Status.NO_SOURCE, result.status());
        assertEquals(0, result.powerWatts());
        assertEquals(0, result.separationBlocks());
    }

    @Test void noReadBeyondTwentyFourBlocks() {
        AtomicInteger called = new AtomicInteger();
        var result = PreviewBeamRoute.scan(0, d -> {
            called.incrementAndGet();
            assertTrue(d <= 24);
            return PreviewBeamRoute.Cell.AIR;
        });
        assertEquals(24, called.get());
        assertEquals(PreviewBeamRoute.Status.NO_SOURCE, result.status());
    }

    @Test void obstructionBeforeSourceFailsClosed() {
        var result = PreviewBeamRoute.scan(1, d -> switch (d) {
            case 2 -> PreviewBeamRoute.Cell.OBSTACLE;
            case 4 -> PreviewBeamRoute.Cell.EMITTER_READY;
            default -> PreviewBeamRoute.Cell.AIR;
        });
        assertEquals(PreviewBeamRoute.Status.BLOCKED, result.status());
        assertEquals(0, result.powerWatts());
        assertEquals(2, result.separationBlocks());
    }

    @Test void unloadedChunkFailsClosedBeforeSource() {
        var result = PreviewBeamRoute.scan(0, d -> switch (d) {
            case 2 -> PreviewBeamRoute.Cell.UNLOADED;
            case 3 -> PreviewBeamRoute.Cell.EMITTER_READY;
            default -> PreviewBeamRoute.Cell.AIR;
        });
        assertEquals(PreviewBeamRoute.Status.UNLOADED, result.status());
    }

    @Test void sourceOffIsNotZeroPowerMeasurement() {
        var result = scan(0, 2, PreviewBeamRoute.Cell.EMITTER_OFF);
        assertEquals(PreviewBeamRoute.Status.SHUTTER_CLOSED, result.status());
        assertEquals(0, result.powerWatts());
    }

    @Test void incorrectlyFacingSourceIsRejected() {
        var result = scan(0, 2, PreviewBeamRoute.Cell.EMITTER_WRONG_FACING);
        assertEquals(PreviewBeamRoute.Status.WRONG_FACING, result.status());
    }

    @Test void nearestSourceWinsWithoutDoubleCounting() {
        var result = PreviewBeamRoute.scan(0, d -> (d == 3 || d == 8)
                ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR);
        assertEquals(3, result.separationBlocks());
        assertEquals(PreviewOpticalBench.sampleAligned(3, 0).observation().value(),
                result.powerWatts());
    }

    @Test void invalidInputFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamRoute.scan(3, d -> PreviewBeamRoute.Cell.AIR));
        assertThrows(NullPointerException.class,
                () -> PreviewBeamRoute.scan(0, null));
        assertThrows(NullPointerException.class,
                () -> PreviewBeamRoute.scan(0, d -> null));
    }

    @Test void declaredStatusBudgetCannotBeFaked() {
        assertThrows(IllegalArgumentException.class, () -> new PreviewBeamRoute.Result(
                PreviewBeamRoute.Status.BLOCKED, 0.02, 2));
        assertThrows(IllegalArgumentException.class, () -> new PreviewBeamRoute.Result(
                PreviewBeamRoute.Status.NO_SOURCE, 0, 2));
    }
}
