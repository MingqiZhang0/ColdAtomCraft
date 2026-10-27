package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEffectLedgerConcurrencyTest {

    private static final int TIMEOUT_SECONDS = 10;

    @Test
    void differentEventsAreClaimedConcurrentlyWithoutLoss() throws Exception {
        FakeWorldState world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        int events = 32;
        List<EnergyTransferCandidate> candidates = new ArrayList<>();
        List<WorldEffectGate.Precheck> prechecks = new ArrayList<>();
        for (int event = 0; event < events; event++) {
            var candidate = candidate(world, STEP, event);
            candidates.add(candidate);
            prechecks.add(precheck(world, candidate));
        }

        var start = new CountDownLatch(1);
        List<WorldEffectLedger.ClaimResult> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<WorldEffectLedger.ClaimResult>> futures = new ArrayList<>();
            for (int event = 0; event < events; event++) {
                int index = event;
                futures.add(executor.submit(() -> {
                    start.await();
                    return ledger.claim(candidates.get(index), prechecks.get(index));
                }));
            }
            start.countDown();
            for (var future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
        assertEquals(events, results.stream()
                .filter(result -> result == WorldEffectLedger.ClaimResult.CLAIMED).count());
        assertEquals(events, ledger.claimedTransferCount());
    }

    @Test
    void sameEventWithDistinctFreshPrechecksStillHasOneWinner() throws Exception {
        FakeWorldState world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        int workers = 16;
        List<EnergyTransferCandidate> candidates = new ArrayList<>();
        List<WorldEffectGate.Precheck> prechecks = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            var candidate = candidate(world, STEP, 0, 0.1 + index);
            candidates.add(candidate);
            prechecks.add(precheck(world, candidate));
        }

        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        List<WorldEffectLedger.ClaimResult> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<Future<WorldEffectLedger.ClaimResult>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                int slot = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return ledger.claim(candidates.get(slot), prechecks.get(slot));
                }));
            }
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, results.stream()
                .filter(result -> result == WorldEffectLedger.ClaimResult.CLAIMED).count());
        assertEquals(workers - 1, results.stream()
                .filter(result -> result == WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER).count());
    }

    @Test
    void overlappingBeginCalculationHasExactlyOneWinner() throws Exception {
        var ledger = new WorldEffectLedger();
        var first = new CalculationStep("run-1", 0, new SimulationInterval(0, 1_000));
        var second = new CalculationStep("run-1", 1, new SimulationInterval(500, 1_500));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        List<WorldEffectLedger.BeginResult> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<WorldEffectLedger.BeginResult>> futures = List.of(
                    executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return ledger.beginCalculation(first);
                    }),
                    executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return ledger.beginCalculation(second);
                    }));
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, results.stream()
                .filter(result -> result == WorldEffectLedger.BeginResult.STARTED).count());
        assertEquals(1, results.stream()
                .filter(result -> result == WorldEffectLedger.BeginResult.OVERLAPPING_INTERVAL).count());
    }

    @Test
    void concurrentDuplicateClaimAndMarkAppliedDoNotProduceTwoWinners() throws Exception {
        FakeWorldState world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        var candidate = candidate(world, STEP, 0);
        var precheck = precheck(world, candidate);
        ledger.beginCalculation(STEP);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(candidate, precheck));

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var appliedCount = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<WorldEffectLedger.ClaimResult> duplicate = executor.submit(() -> {
                ready.countDown();
                start.await();
                return ledger.claim(candidate, precheck);
            });
            Future<Boolean> applied = executor.submit(() -> {
                ready.countDown();
                start.await();
                boolean result = ledger.markApplied(candidate.eventKey());
                if (result) {
                    appliedCount.incrementAndGet();
                }
                return result;
            });
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                    duplicate.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(applied.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        assertEquals(1, appliedCount.get());
        assertEquals(WorldEffectLedger.SettlementState.APPLIED, ledger.stateOf(candidate.eventKey()));
    }
}
