import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelVerification;
import dev.coldatomcraft.experiment.world.CalculationStep;
import dev.coldatomcraft.experiment.world.DeviceSnapshot;
import dev.coldatomcraft.experiment.world.EnergyTransferCandidate;
import dev.coldatomcraft.experiment.world.ServerWorldSnapshot;
import dev.coldatomcraft.experiment.world.SimulationInterval;
import dev.coldatomcraft.experiment.world.TargetKind;
import dev.coldatomcraft.experiment.world.TargetSnapshot;
import dev.coldatomcraft.experiment.world.WorldEffectGate;
import dev.coldatomcraft.experiment.world.WorldEffectLedger;
import dev.coldatomcraft.experiment.world.WorldReadSet;
import dev.coldatomcraft.experiment.world.WorldRevision;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Standalone, dependency-free Ledger performance probe.
 *
 * <p>The synthetic model is benchmark-only. It does not represent an Owner
 * approval of any physical law and is not part of the production catalog.</p>
 */
public final class LedgerBenchmark {
    private static final String MODEL_ID = "BM-900";
    private static final String MODEL_REVISION = "BENCH";
    private static final String RUN_ID = "benchmark-run";
    private static final int[] SIZES = {1_000, 10_000, 50_000};
    private static final int REPETITIONS = 3;
    private static final ModelDefinition BENCHMARK_MODEL = new ModelDefinition(
            MODEL_ID, MODEL_REVISION, "benchmark-only:not-scientific-approval",
            ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
    private static final DeviceSnapshot DEVICE = new DeviceSnapshot(
            "minecraft:overworld", 0, 64, 0, "benchmark:device", 1, true);
    private static final TargetSnapshot TARGET = new TargetSnapshot(
            "benchmark:target", TargetKind.ENTITY, "minecraft:overworld",
            20.5, 65, 0.5, 1, true);
    private static final WorldReadSet READ_SET = new WorldReadSet(List.of(
            new WorldRevision("minecraft:overworld", 0, 0, 1, 1, true),
            new WorldRevision("minecraft:overworld", 1, 0, 1, 1, true),
            new WorldRevision("minecraft:overworld", 2, 2, 1, 1, true)));

    private LedgerBenchmark() { }

    public static void main(String[] args) {
        System.out.println("=== Cold Atom Craft LedgerBenchmark ===");
        System.out.println("jdk=" + System.getProperty("java.version"));
        System.out.println("vm=" + System.getProperty("java.vm.name"));
        System.out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("processors=" + Runtime.getRuntime().availableProcessors());
        System.out.println("maxHeapMiB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.println("jvmArgs=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println("sizes=" + Arrays.toString(SIZES) + " repetitions=" + REPETITIONS);
        System.out.println("note=timings are wall-clock observations, not a formal complexity proof");

        warmUp();
        for (int size : SIZES) {
            runBeginBenchmark(size);
        }
        runClaimBenchmark(20_000);
        measureMemory(50_000);
        System.out.println("=== END ===");
    }

    private static void warmUp() {
        for (int round = 0; round < 3; round++) {
            buildLedger(1_000);
        }
        System.out.println("warmup=3x1000 beginCalculation");
    }

    private static void runBeginBenchmark(int size) {
        long[] nanos = new long[REPETITIONS];
        for (int rep = 0; rep < REPETITIONS; rep++) {
            WorldEffectLedger ledger = new WorldEffectLedger();
            long started = System.nanoTime();
            for (int i = 0; i < size; i++) {
                WorldEffectLedger.BeginResult result = ledger.beginCalculation(
                        new CalculationStep(RUN_ID, i, intervalFor(i)));
                if (result != WorldEffectLedger.BeginResult.STARTED) {
                    throw new IllegalStateException("unexpected begin result " + result + " at " + i);
                }
            }
            nanos[rep] = System.nanoTime() - started;
        }
        report("begin", size, nanos);
    }

    private static WorldEffectLedger buildLedger(int size) {
        WorldEffectLedger ledger = new WorldEffectLedger();
        for (int i = 0; i < size; i++) {
            WorldEffectLedger.BeginResult result = ledger.beginCalculation(
                    new CalculationStep(RUN_ID, i, intervalFor(i)));
            if (result != WorldEffectLedger.BeginResult.STARTED) {
                throw new IllegalStateException("unexpected begin result " + result + " at " + i);
            }
        }
        return ledger;
    }

    private static SimulationInterval intervalFor(int index) {
        long start = index * 1_000L;
        return new SimulationInterval(start, start + 999L);
    }

    private static void runClaimBenchmark(int events) {
        List<EnergyTransferCandidate> candidates = new ArrayList<>(events);
        List<WorldEffectGate.Precheck> prechecks = new ArrayList<>(events);
        List<CalculationStep> steps = new ArrayList<>(events);
        for (int i = 0; i < events; i++) {
            CalculationStep step = new CalculationStep("claim-run", i, intervalFor(i));
            EnergyTransferCandidate candidate = new EnergyTransferCandidate(
                    MODEL_ID, MODEL_REVISION, DEVICE, TARGET, 0.1, step, 0, READ_SET);
            WorldEffectGate.Precheck precheck = WorldEffectGate.precheck(
                    candidate, BENCHMARK_MODEL, READ_SET, DEVICE, TARGET);
            if (!precheck.allowed()) {
                throw new IllegalStateException("benchmark precheck rejected: " + precheck.reasons());
            }
            steps.add(step);
            candidates.add(candidate);
            prechecks.add(precheck);
        }

        WorldEffectLedger ledger = new WorldEffectLedger();
        long started = System.nanoTime();
        for (CalculationStep step : steps) {
            if (ledger.beginCalculation(step) != WorldEffectLedger.BeginResult.STARTED) {
                throw new IllegalStateException("unexpected claim benchmark begin result");
            }
        }
        long beginNanos = System.nanoTime() - started;

        started = System.nanoTime();
        for (int i = 0; i < events; i++) {
            if (ledger.claim(candidates.get(i), prechecks.get(i)) != WorldEffectLedger.ClaimResult.CLAIMED) {
                throw new IllegalStateException("unexpected claim result at " + i);
            }
            if (!ledger.markApplied(candidates.get(i).eventKey())) {
                throw new IllegalStateException("markApplied rejected at " + i);
            }
        }
        long claimAndApplyNanos = System.nanoTime() - started;

        System.out.printf(Locale.ROOT,
                "claims events=%d beginMs=%.3f claimPlusMarkAppliedMs=%.3f perClaimUs=%.3f%n",
                events, beginNanos / 1_000_000.0, claimAndApplyNanos / 1_000_000.0,
                claimAndApplyNanos / 1_000.0 / events);
    }

    private static void measureMemory(int size) {
        forceGc();
        long before = usedHeapBytes();
        WorldEffectLedger ledger = buildLedger(size);
        forceGc();
        long after = usedHeapBytes();
        System.out.printf(Locale.ROOT,
                "memory beginSteps=%d approxRetainedMiB=%.3f approxBytesPerStep=%.1f ledgerRef=%s%n",
                size, (after - before) / (1024.0 * 1024.0),
                (double) (after - before) / size, Integer.toHexString(System.identityHashCode(ledger)));
    }

    private static long usedHeapBytes() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static void forceGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("benchmark interrupted", interrupted);
            }
        }
    }

    private static void report(String operation, int size, long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double[] millis = new double[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            millis[i] = sorted[i] / 1_000_000.0;
        }
        double median = percentile(millis, 0.50);
        double p95 = percentile(millis, 0.95);
        System.out.printf(Locale.ROOT,
                "%s size=%d timesMs=%s medianMs=%.3f p95Ms=%.3f perOpUs=%.3f%n",
                operation, size, Arrays.toString(millis), median, p95,
                sorted[sorted.length / 2] / 1_000.0 / size);
    }

    private static double percentile(double[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
