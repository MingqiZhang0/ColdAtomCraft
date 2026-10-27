package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PreviewBeamParticleBudgetTest {
    private static int[] plan(int count, int budget, long frame) {
        double[] lengths = new double[count];
        Arrays.fill(lengths, 24.0);
        return PreviewBeamParticleBudget.allocateSteps(lengths, budget, 0.2, frame);
    }

    @Test void fourthBeamIsVisible() {
        int[] steps = plan(4, 1200, 0);
        assertEquals(4, steps.length);
        for (int step : steps) assertEquals(120, step);
        assertEquals(648, PreviewBeamParticleBudget.particleCost(steps));
    }

    @Test void crowdedBenchKeepsAllSourcesAndBudget() {
        for (int count : new int[]{1, 3, 4, 8, 16, 32, 64, 120, 400}) {
            int[] steps = plan(count, 1200, 0);
            assertEquals(count, Arrays.stream(steps).filter(x -> x > 0).count());
            assertTrue(PreviewBeamParticleBudget.particleCost(steps) <= 1200);
        }
    }

    @Test void hugeBenchRotatesSourcesFairly() {
        boolean[] seen = new boolean[601];
        for (int frame = 0; frame < 5; frame++) {
            int[] steps = plan(601, 1200, frame);
            assertEquals(400, Arrays.stream(steps).filter(x -> x > 0).count());
            assertTrue(PreviewBeamParticleBudget.particleCost(steps) <= 1200);
            for (int i = 0; i < steps.length; i++) if (steps[i] > 0) seen[i] = true;
        }
        for (boolean hit : seen) assertTrue(hit, "no source permanently starved");
    }

    @Test void localBudgetHasNoEffectOnPhysicalParameters() {
        assertEquals(24.0, PreviewBeamParticleBudget.allocateSteps(
                new double[]{24.0}, 1200, 0.2, 0)[0] * .2, 1e-10);
    }

    @Test void checksInputs() {
        assertThrows(NullPointerException.class,
                () -> PreviewBeamParticleBudget.allocateSteps(null, 1200, .2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamParticleBudget.allocateSteps(new double[]{0}, 1200, .2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamParticleBudget.allocateSteps(new double[]{Double.NaN}, 1200, .2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamParticleBudget.allocateSteps(new double[]{3}, 2, .2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamParticleBudget.allocateSteps(new double[]{3}, 1200, .2, -1));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBeamParticleBudget.particleCost(new int[]{-1}));
    }
}
