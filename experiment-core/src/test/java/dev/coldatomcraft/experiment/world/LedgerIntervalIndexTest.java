package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerIntervalIndexTest {

    @Test
    void randomizedRegistrationMatchesBruteForceOracle() {
        var random = new Random(0xCAC_2026L);
        var ledger = new WorldEffectLedger();
        Map<String, Map<Long, SimulationInterval>> acceptedByRun = new HashMap<>();
        int accepted = 0;
        int rejectedOverlap = 0;
        int rejectedDuplicate = 0;

        for (int attempt = 0; attempt < 3_000; attempt++) {
            String runId = "run-" + random.nextInt(4);
            long stepIndex = random.nextInt(500);
            long start = random.nextInt(40_000);
            long end = start + 1L + random.nextInt(2_000);
            var step = new CalculationStep(runId, stepIndex, new SimulationInterval(start, end));

            Map<Long, SimulationInterval> acceptedSteps =
                    acceptedByRun.computeIfAbsent(runId, ignored -> new HashMap<>());
            WorldEffectLedger.BeginResult expected;
            if (acceptedSteps.containsKey(stepIndex)) {
                expected = WorldEffectLedger.BeginResult.DUPLICATE_STEP;
            } else if (acceptedSteps.values().stream().anyMatch(interval -> interval.overlaps(step.interval()))) {
                expected = WorldEffectLedger.BeginResult.OVERLAPPING_INTERVAL;
            } else {
                expected = WorldEffectLedger.BeginResult.STARTED;
                acceptedSteps.put(stepIndex, step.interval());
                accepted++;
            }

            int attemptNumber = attempt;
            assertEquals(expected, ledger.beginCalculation(step),
                    () -> "mismatch at attempt " + attemptNumber + " for " + step);
            if (expected == WorldEffectLedger.BeginResult.OVERLAPPING_INTERVAL) {
                rejectedOverlap++;
            } else if (expected == WorldEffectLedger.BeginResult.DUPLICATE_STEP) {
                rejectedDuplicate++;
            }
        }

        assertTrue(accepted > 100, "oracle scenario should accept a representative set");
        assertTrue(rejectedOverlap > 100, "oracle scenario should exercise overlap rejection");
        assertTrue(rejectedDuplicate > 10, "oracle scenario should exercise duplicate rejection");
    }

    @Test
    void thousandAdjacentIntervalsCanBeInsertedInShuffledOrder() {
        int count = 1_000;
        List<CalculationStep> steps = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long start = index * 1_000L;
            steps.add(new CalculationStep("run-large", index, new SimulationInterval(start, start + 1_000L)));
        }
        Collections.shuffle(steps, new Random(7L));

        var ledger = new WorldEffectLedger();
        for (CalculationStep step : steps) {
            assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(step));
        }
        assertTrue(ledger.isCalculationStarted("run-large", 0));
        assertTrue(ledger.isCalculationStarted("run-large", count - 1));
        assertEquals(WorldEffectLedger.BeginResult.OVERLAPPING_INTERVAL, ledger.beginCalculation(
                new CalculationStep("run-large", count, new SimulationInterval(500, 1_500))));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-large", count + 1, new SimulationInterval(1_000_000, 1_000_001))));
    }

    @Test
    void rejectedIntervalIsNotInsertedIntoIndex() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-1", 0, new SimulationInterval(0, 100))));
        assertEquals(WorldEffectLedger.BeginResult.OVERLAPPING_INTERVAL, ledger.beginCalculation(
                new CalculationStep("run-1", 1, new SimulationInterval(50, 200))));
        assertFalse(ledger.isCalculationStarted("run-1", 1));

        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-1", 2, new SimulationInterval(100, 150))));
        assertTrue(ledger.isCalculationStarted("run-1", 2));
    }
}
