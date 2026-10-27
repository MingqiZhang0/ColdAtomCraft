package dev.coldatomcraft.experiment.world;

/**
 * Half-open [startTick,endTick) interval in ONE trusted, server-wide
 * operation-scheduling clock, NOT in solver nanoseconds and NOT in wall time.
 *
 * A future NeoForge adapter must define the epoch, ensure a common clock
 * across dimension keys, and check the actual server application time against
 * the reservation. Constructing this record does not establish authenticity.
 * This in-memory type has no cross-restart or persistent clock semantics.
 */
public record ServerTickWindow(long startTick, long endTick) {
    public ServerTickWindow {
        if (startTick < 0 || endTick <= startTick) {
            throw new IllegalArgumentException("window must satisfy 0 <= startTick < endTick");
        }
    }

    public boolean contains(long serverTick) {
        return serverTick >= startTick && serverTick < endTick;
    }

    public boolean overlaps(ServerTickWindow other) {
        if (other == null) throw new IllegalArgumentException("window required");
        return startTick < other.endTick && other.startTick < endTick;
    }
}
