package dev.coldatomcraft.experiment;

import java.util.Objects;

/**
 * Pure-Java allocation of client-only particle samples across nearby laser beams.
 *
 * <p>A renderer refresh has a fixed particle budget, not a fixed three-laser
 * limit. Normal laboratory layouts show every eligible beam, with sample
 * density reduced only when the total budget would be exceeded. In the
 * pathological case of more beams than the minimum two-core/one-halo budget,
 * successive refreshes rotate the selected sources to avoid permanent
 * starvation. Nothing in this class participates in optical measurement.
 */
public final class PreviewBeamParticleBudget {
    public static final int DEFAULT_PARTICLES_PER_REFRESH = 1200;
    public static final double NOMINAL_SPACING_BLOCKS = 0.20;

    private PreviewBeamParticleBudget() { }

    /**
     * Returns the number of axial *steps* for each beam; zero means this
     * source is time-sliced out for this refresh. A positive step count
     * yields (steps + 1) core dust particles and (steps / 3 + 1) halo
     * particles, matching the client's draw loop exactly.
     */
    public static int[] allocateSteps(double[] lengthsBlocks, int budget,
                                      double desiredSpacingBlocks, long refreshIndex) {
        Objects.requireNonNull(lengthsBlocks, "lengthsBlocks");
        if (budget < 3 || !Double.isFinite(desiredSpacingBlocks) || desiredSpacingBlocks <= 0
                || refreshIndex < 0) {
            throw new IllegalArgumentException("invalid preview particle budget");
        }
        int n = lengthsBlocks.length;
        for (double length : lengthsBlocks) {
            if (!Double.isFinite(length) || length <= 0 || length > 25.0) {
                throw new IllegalArgumentException("invalid preview beam length");
            }
        }
        int[] result = new int[n];
        if (n == 0) return result;

        int visible = Math.min(n, budget / 3);
        // A consecutive rotating window is deterministic and guarantees that
        // even extremely crowded scenes eventually display every source.
        int start = (int) (((refreshIndex % n) * visible) % n);
        int[] selected = new int[visible];
        double longest = 0;
        for (int i = 0; i < visible; i++) {
            int index = (start + i) % n;
            selected[i] = index;
            longest = Math.max(longest, lengthsBlocks[index]);
        }

        // First attempt nominal 0.20-block spacing. Keep this detail at normal
        // emitter counts so adding a fourth beam changes nothing visually.
        fill(result, lengthsBlocks, selected, desiredSpacingBlocks);
        if (particleCost(result) <= budget) return result;

        // At longest length each selected source uses its safe minimum of
        // two core particles and one halo particle (three total).
        double low = desiredSpacingBlocks;
        double high = longest;
        for (int i = 0; i < 55; i++) {
            double middle = low + (high - low) / 2.0;
            fill(result, lengthsBlocks, selected, middle);
            if (particleCost(result) <= budget) high = middle;
            else low = middle;
        }
        fill(result, lengthsBlocks, selected, high);
        // Guard against floating-point threshold rounding (fail bounded).
        if (particleCost(result) > budget) {
            for (int index : selected) result[index] = 1;
        }
        return result;
    }

    public static int particleCost(int[] steps) {
        Objects.requireNonNull(steps, "steps");
        int count = 0;
        for (int step : steps) {
            if (step < 0) throw new IllegalArgumentException("negative sample steps");
            if (step > 0) {
                long next = (long) count + step + 1 + step / 3 + 1;
                if (next > Integer.MAX_VALUE) throw new IllegalArgumentException("particle cost overflow");
                count = (int) next;
            }
        }
        return count;
    }

    private static void fill(int[] steps, double[] lengths, int[] selected, double spacing) {
        java.util.Arrays.fill(steps, 0);
        for (int index : selected) {
            steps[index] = Math.max(1, (int) Math.ceil(lengths[index] / spacing));
        }
    }
}
