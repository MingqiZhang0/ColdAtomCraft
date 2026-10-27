package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PreviewBeamDisplayTest {
    @Test void traceStopsAtFirstObstruction() {
        AtomicInteger queries = new AtomicInteger();
        var trace = PreviewBeamDisplay.trace(distance -> {
            queries.incrementAndGet();
            return distance == 4 ? PreviewBeamDisplay.Cell.OBSTACLE : PreviewBeamDisplay.Cell.AIR;
        });
        assertEquals(PreviewBeamDisplay.Stop.OBSTACLE, trace.stop());
        assertEquals(4, queries.get());
        assertEquals(3.5, trace.endFromSourceCenterBlocks(), 1e-12);
    }

    @Test void alignedReceiverEndsAtOpticalFaceRatherThanCentre() {
        var trace = PreviewBeamDisplay.trace(distance -> distance == 5
                ? PreviewBeamDisplay.Cell.RECEIVER : PreviewBeamDisplay.Cell.AIR);
        assertEquals(PreviewBeamDisplay.Stop.RECEIVER, trace.stop());
        assertEquals(5.0 - PreviewOpticalBench.DEMO_PORT_OFFSET_METERS,
                trace.endFromSourceCenterBlocks(), 1e-12);
    }

    @Test void unloadedChunksFailClosed() {
        var trace = PreviewBeamDisplay.trace(distance -> distance == 2
                ? PreviewBeamDisplay.Cell.UNLOADED : PreviewBeamDisplay.Cell.AIR);
        assertEquals(PreviewBeamDisplay.Stop.UNLOADED, trace.stop());
        assertEquals(1.5, trace.endFromSourceCenterBlocks(), 1e-12);
    }

    @Test void emptyPathIsCappedAtCheckedAirOnly() {
        var trace = PreviewBeamDisplay.trace(distance -> PreviewBeamDisplay.Cell.AIR);
        assertEquals(PreviewBeamDisplay.Stop.REACH_LIMIT, trace.stop());
        assertEquals(24, trace.cellDistance());
        assertEquals(24.4375, trace.endFromSourceCenterBlocks(), 1e-12);
    }

    @Test void radiusUsesTheExistingGaussianTheory() {
        assertEquals(0.0005, PreviewBeamDisplay.physicalRadiusMeters(0), 1e-15);
        double zr = Math.PI * 0.0005 * 0.0005 / 780e-9;
        assertEquals(Math.sqrt(2.0) * 0.0005,
                PreviewBeamDisplay.physicalRadiusMeters(zr), 1e-14);
        assertTrue(PreviewBeamDisplay.physicalRadiusMeters(24) >
                PreviewBeamDisplay.physicalRadiusMeters(2));
    }

    @Test void presentationRadiusIsNeverUsedAsPhysicalRadius() {
        for (int distance = 0; distance <= 24; distance++) {
            double real = PreviewBeamDisplay.physicalRadiusMeters(distance);
            double visual = PreviewBeamDisplay.displayedRadiusBlocks(distance);
            assertTrue(real > 0 && visual > real);
            assertTrue(visual <= 0.16);
        }
    }

    @Test void badDistancesAreRejectedWithoutMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamDisplay.physicalRadiusMeters(-1));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamDisplay.physicalRadiusMeters(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamDisplay.physicalRadiusMeters(Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> PreviewBeamDisplay.trace(null));
        assertThrows(NullPointerException.class,
                () -> PreviewBeamDisplay.trace(distance -> null));
    }
}
