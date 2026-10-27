package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.PreviewBeamParticleBudget;
import java.util.Arrays;

/** JDK21+ standalone executable for beam allocation, not a Minecraft runtime test. */
public final class MultiBeamBudgetSmoke {
    private static int checks;
    private MultiBeamBudgetSmoke() { }

    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static int[] plan(int count, long frame) {
        double[] lengths = new double[count];
        Arrays.fill(lengths, 24.0);
        return PreviewBeamParticleBudget.allocateSteps(lengths,
                PreviewBeamParticleBudget.DEFAULT_PARTICLES_PER_REFRESH,
                PreviewBeamParticleBudget.NOMINAL_SPACING_BLOCKS, frame);
    }

    public static void main(String[] args) {
        check(plan(0, 0).length == 0, "empty world");
        for (int count : new int[]{1, 2, 3, 4, 8, 16, 32, 64, 120, 256, 400}) {
            int[] samples = plan(count, 0);
            check(samples.length == count, "all sources represented");
            check(PreviewBeamParticleBudget.particleCost(samples) <= 1200, "bounded particle count");
            for (int sample : samples) check(sample > 0, "all laboratory beams visible");
        }
        int[] four = plan(4, 0);
        for (int steps : four) check(steps == 120, "four beams retain full density");
        check(PreviewBeamParticleBudget.particleCost(four) == 648, "exact core plus halo cost");
        boolean[] seen = new boolean[601];
        for (int frame = 0; frame < 5; frame++) {
            int[] framePlan = plan(601, frame);
            check(Arrays.stream(framePlan).filter(steps -> steps > 0).count() == 400,
                    "time slicing only when true min particle count exceeded");
            check(PreviewBeamParticleBudget.particleCost(framePlan) <= 1200,
                    "time-sliced budget bounded");
            for (int i = 0; i < framePlan.length; i++) if (framePlan[i] > 0) seen[i] = true;
        }
        for (boolean ever : seen) check(ever, "every source receives a draw slot");
        for (int count = 1; count <= 150; count++) {
            double[] lengths = new double[count];
            for (int i = 0; i < count; i++) lengths[i] = 0.0625 + (i % 24);
            int[] plan = PreviewBeamParticleBudget.allocateSteps(lengths, 1200, 0.2, count);
            check(PreviewBeamParticleBudget.particleCost(plan) <= 1200, "mixed beam lengths bounded");
            for (int step : plan) check(step > 0, "mixed beam lengths get samples");
        }
        System.out.println("D13 MULTI-BEAM PARTICLE BUDGET PASS " + checks + "/" + checks);
    }
}
