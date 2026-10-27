package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A run's settlement history remains fail-closed even when many unrelated
 * runs are created, completed or abandoned. This tests behavior rather than
 * implementation details of the internal O(1) claim-history lookup.
 * Test candidates use only synthetic model fixtures, never production physics.
 */
class WorldEffectLedgerClaimIndexTest {
    @Test
    void mixedTerminalOutcomesAllPreventRunAbandonment() {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        for (int i = 0; i < 3; i++) {
            String run = "claimed-" + i;
            var step = new CalculationStep(run, 0, new SimulationInterval(0, 1));
            assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(step));
            var event = candidate(world, step, 0);
            assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                    ledger.claim(event, precheck(world, event)));
            if (i == 1) {
                assertTrue(ledger.markApplied(event.eventKey()));
            } else if (i == 2) {
                assertTrue(ledger.markFailed(event.eventKey(), "partial mutation may have happened"));
            }
            assertEquals(WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS,
                    ledger.abandonRun(run));
        }
        assertEquals(3, ledger.claimedTransferCount());
    }

    @Test
    void unclaimedRunsMayRetireDespiteLargeUnrelatedClaimHistory() {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        for (int i = 0; i < 600; i++) {
            var step = new CalculationStep("long-lived-run", i,
                    new SimulationInterval(i * 2L, i * 2L + 1));
            assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(step));
            var event = candidate(world, step, 0);
            assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                    ledger.claim(event, precheck(world, event)));
        }
        for (int i = 0; i < 300; i++) {
            String run = "stale-no-claims-" + i;
            var step = new CalculationStep(run, 0, new SimulationInterval(0, 1));
            assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(step));
            assertEquals(WorldEffectLedger.AbandonResult.ABANDONED, ledger.abandonRun(run));
            assertTrue(ledger.isRunAbandoned(run));
            assertEquals(WorldEffectLedger.BeginResult.RUN_ABANDONED,
                    ledger.beginCalculation(new CalculationStep(run, 1,
                            new SimulationInterval(1, 2))));
        }
        assertEquals(WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS,
                ledger.abandonRun("long-lived-run"));
        assertEquals(600, ledger.claimedTransferCount());
    }

    @Test
    void invalidClaimsDoNotBlockAnUnclaimedRunFromRetiring() {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        var step = new CalculationStep("rejected-run", 0, new SimulationInterval(0, 1));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(step));
        var event = candidate(world, step, 0);
        var mismatched = candidate(world, step, 0, 23.0);
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(mismatched, precheck(world, event)));
        assertEquals(WorldEffectLedger.AbandonResult.ABANDONED,
                ledger.abandonRun("rejected-run"));
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED,
                ledger.stateOf(event.eventKey()));
    }

    @Test
    void duplicateEventDoesNotGrowClaimHistoryOrChangeTerminalState() {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        var step = new CalculationStep("same-run", 0, new SimulationInterval(0, 1));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(step));
        var event = candidate(world, step, 0);
        var check = precheck(world, event);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(event, check));
        assertTrue(ledger.markApplied(event.eventKey()));
        for (int i = 0; i < 20; i++) {
            assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                    ledger.claim(event, check));
        }
        assertEquals(1, ledger.claimedTransferCount());
        assertEquals(WorldEffectLedger.SettlementState.APPLIED,
                ledger.stateOf(event.eventKey()));
        assertEquals(WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS,
                ledger.abandonRun("same-run"));
    }
}
