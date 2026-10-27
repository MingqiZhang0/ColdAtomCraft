package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M0-R2.1-D1: retiring a stale calculation run must not make late results
 * claimable, and no run with a claimed (possibly applied) effect may be retired.
 * Tests use a synthetic owner-approved model fixture; no production physics.
 */
class WorldEffectLedgerRunAbandonmentTest {
    @Test
    void unknownAndInvalidRunCannotCreateAnAbandonedTombstone() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.AbandonResult.INVALID, ledger.abandonRun(null));
        assertEquals(WorldEffectLedger.AbandonResult.INVALID, ledger.abandonRun("  "));
        assertEquals(WorldEffectLedger.AbandonResult.UNKNOWN_RUN, ledger.abandonRun("future-run"));
        assertFalse(ledger.isRunAbandoned("future-run"));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("future-run", 0, new SimulationInterval(0, 1_000))));
    }

    @Test
    void abandonedRunCannotRestartStepOrClaimLateResult() {
        FakeWorldState world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        var oldCandidate = candidate(world, STEP, 0);
        var oldPrecheck = precheck(world, oldCandidate);
        assertTrue(oldPrecheck.allowed());

        assertEquals(WorldEffectLedger.AbandonResult.ABANDONED, ledger.abandonRun("run-1"));
        assertTrue(ledger.isRunAbandoned("run-1"));
        assertEquals(WorldEffectLedger.AbandonResult.ALREADY_ABANDONED, ledger.abandonRun("run-1"));
        assertEquals(WorldEffectLedger.BeginResult.RUN_ABANDONED, ledger.beginCalculation(STEP));
        assertEquals(WorldEffectLedger.BeginResult.RUN_ABANDONED, ledger.beginCalculation(
                new CalculationStep("run-1", 1, new SimulationInterval(1_000, 2_000))));
        assertEquals(WorldEffectLedger.ClaimResult.RUN_ABANDONED,
                ledger.claim(oldCandidate, oldPrecheck));
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED,
                ledger.stateOf(oldCandidate.eventKey()));
    }

    @Test
    void staleResultMayBeRecomputedOnlyInAReplacementRun() {
        FakeWorldState world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        var oldCandidate = candidate(world, STEP, 0);
        var oldPrecheck = precheck(world, oldCandidate);

        // World changed while the solver was running: old submission is stale.
        world.changeMirrorContent();
        var coordinator = new ServerEffectCommitCoordinator(
                ledger, ContractTestSupport.TEST_MODEL_CATALOG);
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                coordinator.submit(oldCandidate, oldPrecheck,
                        world.snapshot(), ContractTestSupport.ALL_SERVER_FACTS));
        assertEquals(WorldEffectLedger.AbandonResult.ABANDONED, ledger.abandonRun("run-1"));

        // No second calculation may reuse run-1. A trusted scheduler must issue
        // a new run ID; the same simulated time interval is valid across runs.
        // An otherwise fresh candidate must still be rejected by the final
        // coordinator once its server-owned calculation run was abandoned.
        var sameRunCandidate = candidate(world, STEP, 0);
        assertEquals(ServerEffectCommitCoordinator.Decision.RUN_ABANDONED,
                coordinator.submit(sameRunCandidate, precheck(world, sameRunCandidate),
                        world.snapshot(), ContractTestSupport.ALL_SERVER_FACTS));

        var newStep = new CalculationStep("run-2", 0, STEP.interval());
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(newStep));
        var recomputed = candidate(world, newStep, 0);
        assertEquals(ServerEffectCommitCoordinator.Decision.SETTLEMENT_CLAIMED,
                coordinator.submit(recomputed, precheck(world, recomputed),
                        world.snapshot(), ContractTestSupport.ALL_SERVER_FACTS));
        assertEquals(WorldEffectLedger.ClaimResult.RUN_ABANDONED,
                ledger.claim(oldCandidate, oldPrecheck));
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED,
                ledger.stateOf(oldCandidate.eventKey()));
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED,
                ledger.stateOf(recomputed.eventKey()));
    }

    @Test
    void claimedRunCannotBeAbandonedEvenAfterTerminalOutcome() {
        for (WorldEffectLedger.SettlementState terminal : new WorldEffectLedger.SettlementState[] {
                WorldEffectLedger.SettlementState.CLAIMED,
                WorldEffectLedger.SettlementState.APPLIED,
                WorldEffectLedger.SettlementState.FAILED }) {
            var world = new FakeWorldState();
            var ledger = new WorldEffectLedger();
            assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
            var transfer = candidate(world, STEP, 0);
            assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                    ledger.claim(transfer, precheck(world, transfer)));
            if (terminal == WorldEffectLedger.SettlementState.APPLIED) {
                assertTrue(ledger.markApplied(transfer.eventKey()));
            } else if (terminal == WorldEffectLedger.SettlementState.FAILED) {
                assertTrue(ledger.markFailed(transfer.eventKey(), "possible partial effect"));
            }
            assertEquals(WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS,
                    ledger.abandonRun("run-1"));
            assertFalse(ledger.isRunAbandoned("run-1"));
            assertEquals(terminal, ledger.stateOf(transfer.eventKey()));
        }
    }

    @Test
    void concurrentClaimAndAbandonmentCannotBothSucceed() throws Exception {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        var transfer = candidate(world, STEP, 0);
        var check = precheck(world, transfer);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<WorldEffectLedger.ClaimResult> claim = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("claim start timeout");
                return ledger.claim(transfer, check);
            };
            Callable<WorldEffectLedger.AbandonResult> abandon = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("abandon start timeout");
                return ledger.abandonRun("run-1");
            };
            var claimFuture = executor.submit(claim);
            var abandonFuture = executor.submit(abandon);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var claimResult = claimFuture.get(10, TimeUnit.SECONDS);
            var abandonResult = abandonFuture.get(10, TimeUnit.SECONDS);
            assertTrue(
                    (claimResult == WorldEffectLedger.ClaimResult.CLAIMED
                            && abandonResult == WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS)
                    || (claimResult == WorldEffectLedger.ClaimResult.RUN_ABANDONED
                            && abandonResult == WorldEffectLedger.AbandonResult.ABANDONED));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
