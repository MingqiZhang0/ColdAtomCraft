package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelVerification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEffectLedgerTest {
    private static final SimulationInterval INTERVAL = new SimulationInterval(0, 1_000);
    private static final CalculationStep STEP = new CalculationStep("run-1", 0, INTERVAL);
    private static final ModelDefinition APPROVED = new ModelDefinition(
            "THM-001", "A", "owner:THM-001-A",
            ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);

    private static EnergyTransferCandidate candidate(FakeWorldState world, CalculationStep step, int eventIndex) {
        return new EnergyTransferCandidate("THM-001", "A", world.device(), world.target(),
                0.1, step, eventIndex, world.readSet());
    }

    private static WorldEffectGate.Precheck precheck(FakeWorldState world,
                                                      EnergyTransferCandidate candidate) {
        return WorldEffectGate.precheck(candidate, APPROVED, world.readSet(), world.device(), world.target());
    }

    @Test
    void rejectsRepeatedCalculationStepAndOverlappingIntervals() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        assertEquals(WorldEffectLedger.BeginResult.DUPLICATE_STEP, ledger.beginCalculation(
                new CalculationStep("run-1", 0, new SimulationInterval(0, 500))));
        assertEquals(WorldEffectLedger.BeginResult.OVERLAPPING_INTERVAL, ledger.beginCalculation(
                new CalculationStep("run-1", 1, new SimulationInterval(500, 1_500))));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-1", 1, new SimulationInterval(1_000, 2_000))));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-2", 0, INTERVAL)));
    }

    @Test
    void claimsEachTransferEventExactlyOnce() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var gate = precheck(world, candidate);
        assertTrue(gate.allowed());
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));

        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(candidate, gate));
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER, ledger.claim(candidate, gate));
        assertTrue(ledger.isTransferClaimed(candidate.eventKey()));
        assertEquals(1, ledger.claimedTransferCount());
    }

    @Test
    void duplicateCannotBecomeNewByChangingTargetOrPayload() {
        FakeWorldState world = new FakeWorldState();
        var first = candidate(world, STEP, 0);
        var firstGate = precheck(world, first);
        var ledger = new WorldEffectLedger();
        ledger.beginCalculation(STEP);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(first, firstGate));

        world.moveTarget(2.0);
        var changedPayload = new EnergyTransferCandidate("THM-001", "A", world.device(), world.target(),
                9_999.0, STEP, 0, world.readSet());
        var changedGate = precheck(world, changedPayload);
        assertTrue(changedGate.allowed());
        assertEquals(changedPayload.eventKey(), first.eventKey());
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                ledger.claim(changedPayload, changedGate));
    }

    @Test
    void distinctEventIndicesInOneStepAreClaimedSeparately() {
        FakeWorldState world = new FakeWorldState();
        var first = candidate(world, STEP, 0);
        var second = candidate(world, STEP, 1);
        var ledger = new WorldEffectLedger();
        ledger.beginCalculation(STEP);

        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(first, precheck(world, first)));
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(second, precheck(world, second)));
        assertEquals(2, ledger.claimedTransferCount());
    }

    @Test
    void refusesUnregisteredCalculationAndRejectedPrecheck() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.ClaimResult.UNREGISTERED_CALCULATION,
                ledger.claim(candidate, precheck(world, candidate)));

        ledger.beginCalculation(STEP);
        world.changeMirrorContent();
        var rejected = precheck(world, candidate);
        assertFalse(rejected.allowed());
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_REQUIRED,
                ledger.claim(candidate, rejected));
        assertFalse(ledger.isTransferClaimed(candidate.eventKey()));
    }

    @Test
    void appliedAndFailedOutcomesAreTerminalAndNotRetried() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var gate = precheck(world, candidate);
        var ledger = new WorldEffectLedger();
        ledger.beginCalculation(STEP);

        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(candidate, gate));
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED, ledger.stateOf(candidate.eventKey()));
        String failureReason;
        try {
            throw new IllegalStateException("injected application exception");
        } catch (RuntimeException failure) {
            failureReason = failure.toString();
        }
        assertTrue(ledger.markFailed(candidate.eventKey(), failureReason));
        assertEquals(WorldEffectLedger.SettlementState.FAILED, ledger.stateOf(candidate.eventKey()));
        assertEquals("java.lang.IllegalStateException: injected application exception",
                ledger.failureReason(candidate.eventKey()).orElseThrow());
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER, ledger.claim(candidate, gate));
        assertFalse(ledger.markApplied(candidate.eventKey()));
        assertFalse(ledger.markFailed(candidate.eventKey(), "second failure"));

        var applied = candidate(world, STEP, 1);
        var appliedGate = precheck(world, applied);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(applied, appliedGate));
        assertTrue(ledger.markApplied(applied.eventKey()));
        assertEquals(WorldEffectLedger.SettlementState.APPLIED, ledger.stateOf(applied.eventKey()));
        assertFalse(ledger.markApplied(applied.eventKey()));
        assertFalse(ledger.markFailed(applied.eventKey(), "late failure"));
    }

    @Test
    void unrecordedOutcomeRemainsClaimedAndCannotBeRetried() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var ledger = new WorldEffectLedger();
        ledger.beginCalculation(STEP);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                ledger.claim(candidate, precheck(world, candidate)));
        // Simulate a crash between claim and outcome recording: state stays CLAIMED.
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED, ledger.stateOf(candidate.eventKey()));
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                ledger.claim(candidate, precheck(world, candidate)));
    }

    @Test
    void newLedgerAfterRestartDoesNotGuaranteeCrossRestartIdempotency() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var firstProcess = new WorldEffectLedger();
        firstProcess.beginCalculation(STEP);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                firstProcess.claim(candidate, precheck(world, candidate)));

        // Documented M0-R2 limitation: a new process has no durable claim table.
        var restartedProcess = new WorldEffectLedger();
        restartedProcess.beginCalculation(STEP);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                restartedProcess.claim(candidate, precheck(world, candidate)));
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED,
                restartedProcess.stateOf(candidate.eventKey()));
    }

    @Test
    void concurrentClaimsProduceExactlyOneWinner() throws Exception {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var gate = precheck(world, candidate);
        var ledger = new WorldEffectLedger();
        ledger.beginCalculation(STEP);

        int workers = 16;
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        var results = new ArrayList<WorldEffectLedger.ClaimResult>();
        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<java.util.concurrent.Future<WorldEffectLedger.ClaimResult>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return ledger.claim(candidate, gate);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, results.stream().filter(result -> result == WorldEffectLedger.ClaimResult.CLAIMED).count());
        assertEquals(workers - 1, results.stream()
                .filter(result -> result == WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER).count());
    }
}
