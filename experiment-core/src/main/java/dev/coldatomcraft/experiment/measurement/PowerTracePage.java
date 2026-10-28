package dev.coldatomcraft.experiment.measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded pages of a D21A preview ring, not a full-history query of the CSV archive.
 * All timestamps remain server game ticks. Page zero is the newest page.
 */
public final class PowerTracePage {
    public static final int PAGE_SIZE = 32;
    public static final int RANGE_ONE_MINUTE = 0;
    public static final int RANGE_FIVE_MINUTES = 1;
    public static final int RANGE_FIFTEEN_MINUTES = 2;
    public static final int RANGE_ALL = 3;

    private PowerTracePage() { }

    public static long rangeTicks(int range) {
        return switch (range) {
            case RANGE_ONE_MINUTE -> 1_200L;
            case RANGE_FIVE_MINUTES -> 6_000L;
            case RANGE_FIFTEEN_MINUTES -> 18_000L;
            case RANGE_ALL -> Long.MAX_VALUE;
            default -> throw new IllegalArgumentException("unrecognized trace range");
        };
    }

    public static List<PowerTrace.Sample> filter(List<PowerTrace.Sample> history, int range) {
        Objects.requireNonNull(history, "history");
        long limit = rangeTicks(range);
        if (history.size() > PowerTrace.PREVIEW_CAPACITY) {
            throw new IllegalArgumentException("unbounded preview history");
        }
        if (history.isEmpty()) return List.of();
        long latest = history.getLast().gameTick();
        long previous = -1;
        ArrayList<PowerTrace.Sample> window = new ArrayList<>();
        for (PowerTrace.Sample sample : history) {
            Objects.requireNonNull(sample, "sample");
            if (sample.gameTick() <= previous || sample.gameTick() > latest) {
                throw new IllegalArgumentException("non-monotonic preview input");
            }
            previous = sample.gameTick();
            // Subtraction is safe because each tick is in [0,latest].
            if (latest - sample.gameTick() <= limit) window.add(sample);
        }
        return List.copyOf(window);
    }

    public static int pages(List<PowerTrace.Sample> filtered) {
        Objects.requireNonNull(filtered, "filtered");
        return Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static List<PowerTrace.Sample> page(List<PowerTrace.Sample> filtered, int page) {
        Objects.requireNonNull(filtered, "filtered");
        if (filtered.size() > PowerTrace.PREVIEW_CAPACITY || page < 0 || page >= pages(filtered)) {
            throw new IllegalArgumentException("invalid trace page");
        }
        int end = filtered.size() - page * PAGE_SIZE;
        if (end <= 0) return List.of();
        int start = Math.max(0, end - PAGE_SIZE);
        return List.copyOf(filtered.subList(start, end));
    }
}
