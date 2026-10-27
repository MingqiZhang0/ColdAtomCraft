package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldReadSetTest {
    private static final WorldRevision A = new WorldRevision("minecraft:overworld", 1, 2, 5, 10, true);
    private static final WorldRevision B = new WorldRevision("minecraft:overworld", 0, 9, 2, 3, true);

    @Test
    void canonicalizesOrderAndSupportsChunkLookup() {
        var forward = new WorldReadSet(List.of(A, B));
        var reverse = new WorldReadSet(List.of(B, A));
        assertEquals(forward, reverse);
        assertTrue(forward.containsChunk("minecraft:overworld", 1, 2));
        assertFalse(forward.containsChunk("minecraft:the_nether", 1, 2));
        assertFalse(forward.containsChunk("minecraft:overworld", 2, 2));
    }

    @Test
    void rejectsDuplicateMixedDimensionAndUnloadedChunks() {
        assertThrows(IllegalArgumentException.class, () -> new WorldReadSet(List.of(A, A)));
        assertThrows(IllegalArgumentException.class, () -> new WorldReadSet(List.of(
                A, new WorldRevision("minecraft:the_nether", 1, 2, 5, 10, true))));
        assertThrows(IllegalArgumentException.class, () -> new WorldReadSet(List.of(
                A, new WorldRevision("minecraft:overworld", 4, 4, 1, 1, false))));
    }

    @Test
    void revisionIdentityIncludesLoadEpochContentAndLoadedState() {
        assertFalse(A.equals(new WorldRevision("minecraft:overworld", 1, 2, 6, 10, true)));
        assertFalse(A.equals(new WorldRevision("minecraft:overworld", 1, 2, 5, 11, true)));
        assertFalse(new WorldRevision("minecraft:overworld", 1, 2, 5, 10, false)
                .equals(A));
    }

    @Test
    void simulationIntervalIsHalfOpenAndDetectsOverlap() {
        var interval = new SimulationInterval(100, 200);
        assertTrue(interval.contains(100));
        assertTrue(interval.contains(199));
        assertFalse(interval.contains(200));
        assertFalse(interval.overlaps(new SimulationInterval(200, 300)));
        assertFalse(interval.overlaps(new SimulationInterval(0, 100)));
        assertTrue(interval.overlaps(new SimulationInterval(199, 201)));
        assertEquals(100, interval.durationNanos());
        assertThrows(IllegalArgumentException.class, () -> new SimulationInterval(2, 2));
        assertThrows(IllegalArgumentException.class, () -> new SimulationInterval(-1, 2));
    }
}
