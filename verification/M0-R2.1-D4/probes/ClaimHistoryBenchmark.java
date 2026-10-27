package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelVerification;
import dev.coldatomcraft.experiment.world.*;
import java.util.List;

/**
 * Comparative pure-Java microbenchmark. Not a correctness test, not JUnit,
 * not a Minecraft TPS measurement, and not an Owner-approved physics model.
 */
public final class ClaimHistoryBenchmark {
    private static final ModelDefinition TEST_MODEL = new ModelDefinition(
            "BENCH-ONLY", "1", "test-only:benchmark", ModelApproval.OWNER_APPROVED,
            ModelVerification.NUMERICALLY_VERIFIED);

    public static void main(String[] args) {
        final int claims = args.length > 0 ? Integer.parseInt(args[0]) : 16_000;
        final int abandons = args.length > 1 ? Integer.parseInt(args[1]) : 4_000;
        var ledger = new WorldEffectLedger();
        var chunk = new WorldRevision("minecraft:overworld", 0, 0, 0, 0, true);
        var reads = WorldReadSet.of(chunk);
        var device = new DeviceSnapshot("minecraft:overworld", 0, 64, 0, "benchmark-device", 1, true);
        var target = new TargetSnapshot("benchmark-target", TargetKind.BLOCK,
                "minecraft:overworld", 0.5, 64.5, 0.5, 0, true);

        for (int i = 0; i < claims; i++) {
            var step = new CalculationStep("claimed-history", i,
                    new SimulationInterval(2L * i, 2L * i + 1));
            if (ledger.beginCalculation(step) != WorldEffectLedger.BeginResult.STARTED) {
                throw new AssertionError("claim setup step " + i);
            }
            var candidate = new EnergyTransferCandidate("BENCH-ONLY", "1", device, target,
                    0.001, step, 0, reads);
            var precheck = WorldEffectGate.precheck(candidate, TEST_MODEL, reads, device, target);
            if (ledger.claim(candidate, precheck) != WorldEffectLedger.ClaimResult.CLAIMED) {
                throw new AssertionError("claim setup event " + i);
            }
        }
        long start = System.nanoTime();
        for (int i = 0; i < abandons; i++) {
            String run = "stale-run-" + i;
            var step = new CalculationStep(run, 0, new SimulationInterval(0, 1));
            if (ledger.beginCalculation(step) != WorldEffectLedger.BeginResult.STARTED) {
                throw new AssertionError("abandon setup failed " + i);
            }
            if (ledger.abandonRun(run) != WorldEffectLedger.AbandonResult.ABANDONED) {
                throw new AssertionError("abandon failed " + i);
            }
        }
        long nanos = System.nanoTime() - start;
        if (ledger.abandonRun("claimed-history")
                != WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS) {
            throw new AssertionError("claimed run must be un-abandonable");
        }
        System.out.printf("claimed=%d abandoned=%d elapsed_ms=%.3f; NOT GAME TPS%n",
                claims, abandons, nanos / 1_000_000.0);
    }
}
