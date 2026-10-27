package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculationIntervalBindingTest {

    private static WorldEffectLedger ledgerWith(CalculationStep step) {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(step));
        return ledger;
    }

    private static WorldEffectLedger.ClaimResult claimAt(FakeWorldState world,
                                                         WorldEffectLedger ledger,
                                                         CalculationStep step) {
        var candidate = candidate(world, step, 0);
        var precheck = precheck(world, candidate);
        assertTrue(precheck.allowed());
        return ledger.claim(candidate, precheck);
    }

    @Test
    void exactRegisteredIntervalIsClaimed() {
        FakeWorldState world = new FakeWorldState();
        var ledger = ledgerWith(STEP);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, claimAt(world, ledger, STEP));
    }

    @Test
    void differentStartIsRejected() {
        FakeWorldState world = new FakeWorldState();
        var ledger = ledgerWith(STEP);
        var changed = new CalculationStep("run-1", 0, new SimulationInterval(100, 1_000));
        assertEquals(WorldEffectLedger.ClaimResult.STEP_INTERVAL_MISMATCH,
                claimAt(world, ledger, changed));
    }

    @Test
    void differentEndIsRejected() {
        FakeWorldState world = new FakeWorldState();
        var ledger = ledgerWith(STEP);
        var changed = new CalculationStep("run-1", 0, new SimulationInterval(0, 1_001));
        assertEquals(WorldEffectLedger.ClaimResult.STEP_INTERVAL_MISMATCH,
                claimAt(world, ledger, changed));
    }

    @Test
    void shiftedIntervalWithSameStepIdentityIsRejected() {
        FakeWorldState world = new FakeWorldState();
        var ledger = ledgerWith(STEP);
        var changed = new CalculationStep("run-1", 0, new SimulationInterval(200, 300));
        assertEquals(WorldEffectLedger.ClaimResult.STEP_INTERVAL_MISMATCH,
                claimAt(world, ledger, changed));
    }

    @Test
    void mismatchDoesNotConsumeEventOrChangeRegisteredInterval() {
        FakeWorldState world = new FakeWorldState();
        var ledger = ledgerWith(STEP);
        var wrong = new CalculationStep("run-1", 0, new SimulationInterval(200, 300));
        assertEquals(WorldEffectLedger.ClaimResult.STEP_INTERVAL_MISMATCH,
                claimAt(world, ledger, wrong));

        var correct = candidate(world, STEP, 0);
        assertFalse(ledger.isTransferClaimed(correct.eventKey()));
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                ledger.claim(correct, precheck(world, correct)));
    }

    @Test
    void settledEventRemainsDuplicateEvenWhenLaterIntervalDiffers() {
        FakeWorldState world = new FakeWorldState();
        var ledger = ledgerWith(STEP);
        var first = candidate(world, STEP, 0);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                ledger.claim(first, precheck(world, first)));

        var changedStep = new CalculationStep("run-1", 0, new SimulationInterval(200, 300));
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                claimAt(world, ledger, changedStep));
    }

    @Test
    void duplicateStepIsRejectedBeforeIntervalComparison() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        assertEquals(WorldEffectLedger.BeginResult.DUPLICATE_STEP, ledger.beginCalculation(
                new CalculationStep("run-1", 0, new SimulationInterval(500, 600))));
    }

    @Test
    void positiveLengthOverlapInSameRunIsRejected() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        assertEquals(WorldEffectLedger.BeginResult.OVERLAPPING_INTERVAL, ledger.beginCalculation(
                new CalculationStep("run-1", 1, new SimulationInterval(500, 1_500))));
    }

    @Test
    void touchingHalfOpenEndpointsDoNotOverlap() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-1", 0, new SimulationInterval(0, 1_000))));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-1", 1, new SimulationInterval(1_000, 2_000))));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-1", 2, new SimulationInterval(2_000, 3_000))));
    }

    @Test
    void differentRunMayUseSameInterval() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(
                new CalculationStep("run-2", 0, new SimulationInterval(0, 1_000))));
        assertTrue(ledger.isCalculationStarted("run-1", 0));
        assertTrue(ledger.isCalculationStarted("run-2", 0));
    }

    @Test
    void unregisteredCalculationIsRejectedBeforeEventReservation() {
        FakeWorldState world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        var candidate = candidate(world, STEP, 0);
        assertEquals(WorldEffectLedger.ClaimResult.UNREGISTERED_CALCULATION,
                ledger.claim(candidate, precheck(world, candidate)));
        assertFalse(ledger.isTransferClaimed(candidate.eventKey()));
    }
}
