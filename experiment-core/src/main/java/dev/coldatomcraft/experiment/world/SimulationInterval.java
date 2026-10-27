package dev.coldatomcraft.experiment.world;

/**
 * Half-open simulation interval {@code [startNanos, endNanos)}.
 *
 * <p>A candidate belongs to exactly one step interval. Intervals make it
 * possible to reject a duplicate calculation even when two solver runs produce
 * different object identities. Intervals are simulation-clock nanoseconds, not
 * wall-clock time; their origin is owned by the server experiment scheduler.</p>
 */
public record SimulationInterval(long startNanos, long endNanos) {
    public SimulationInterval {
        if (startNanos < 0 || endNanos <= startNanos) {
            throw new IllegalArgumentException("interval must satisfy 0 <= start < end");
        }
    }

    public long durationNanos() {
        return endNanos - startNanos;
    }

    public boolean contains(long timeNanos) {
        return timeNanos >= startNanos && timeNanos < endNanos;
    }

    /** True only for a positive-duration overlap; touching endpoints do not overlap. */
    public boolean overlaps(SimulationInterval other) {
        if (other == null) throw new IllegalArgumentException("other interval required");
        return startNanos < other.endNanos && other.startNanos < endNanos;
    }
}
