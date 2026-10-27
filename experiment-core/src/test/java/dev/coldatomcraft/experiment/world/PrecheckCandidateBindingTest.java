package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecheckCandidateBindingTest {

    private static WorldEffectLedger ledgerWithStep() {
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        return ledger;
    }

    @Test
    void originalCandidateWithOriginalPrecheckIsClaimedOnce() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var precheck = precheck(world, candidate);
        assertTrue(precheck.allowed());
        assertEquals(candidate, precheck.candidate().orElseThrow());

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(candidate, precheck));
        assertTrue(ledger.isTransferClaimed(candidate.eventKey()));
    }

    @Test
    void changedAbsorbedEnergyCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        var changed = candidate(world, STEP, 0, 9_999.0);

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changed, originalPrecheck));
        assertFalse(ledger.isTransferClaimed(original.eventKey()));
    }

    @Test
    void changedTargetPositionCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        world.moveTarget(1.0);
        var changed = candidate(world, STEP, 0);

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changed, originalPrecheck));
        assertFalse(ledger.isTransferClaimed(original.eventKey()));
    }

    @Test
    void changedTargetIdentityCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        world.changeTargetIdentity();
        var changed = candidate(world, STEP, 0);

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changed, originalPrecheck));
    }

    @Test
    void changedTargetRevisionOrKindCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        var ledger = ledgerWithStep();

        world.bumpTargetRevision();
        var changedRevision = candidate(world, STEP, 0);
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changedRevision, originalPrecheck));

        world.changeTargetKind();
        var changedKind = candidate(world, STEP, 0);
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changedKind, originalPrecheck));
    }

    @Test
    void changedDeviceStateRevisionIdentityOrPositionCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        var ledger = ledgerWithStep();

        world.changeDeviceState();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(candidate(world, STEP, 0), originalPrecheck));

        world.changeDeviceIdentity();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(candidate(world, STEP, 0), originalPrecheck));

        world.moveDeviceWithinChunk();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(candidate(world, STEP, 0), originalPrecheck));
    }

    @Test
    void changedReadSetContentRevisionCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        world.changeMirrorContent();
        var changed = candidate(world, STEP, 0);

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changed, originalPrecheck));
    }

    @Test
    void changedReadSetLoadEpochCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        world.reloadWorld();
        var changed = candidate(world, STEP, 0);

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changed, originalPrecheck));
    }

    @Test
    void changedCalculationStepCannotReusePrecheckEvenWithSameEventKey() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        var changedStep = new CalculationStep("run-1", 0, new SimulationInterval(200, 300));
        var changed = candidate(world, changedStep, 0);

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(changed, originalPrecheck));
    }

    @Test
    void changedModelOrEventIndexCannotReusePrecheck() {
        FakeWorldState world = new FakeWorldState();
        var original = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, original);
        var ledger = ledgerWithStep();

        var differentModel = new EnergyTransferCandidate("THM-002", "A",
                world.device(), world.target(), 0.1, STEP, 0, world.readSet());
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(differentModel, originalPrecheck));

        var differentEventIndex = candidate(world, STEP, 1);
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                ledger.claim(differentEventIndex, originalPrecheck));
    }

    @Test
    void freshPrecheckForChangedPayloadCannotBypassSettledEventKey() {
        FakeWorldState world = new FakeWorldState();
        var first = candidate(world, STEP, 0);
        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(first, precheck(world, first)));

        var changed = candidate(world, STEP, 0, 2.0);
        var freshPrecheck = precheck(world, changed);
        assertTrue(freshPrecheck.allowed());
        assertEquals(first.eventKey(), changed.eventKey());
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                ledger.claim(changed, freshPrecheck));
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED, ledger.stateOf(first.eventKey()));
    }

    @Test
    void distinctEventIndicesAreClaimedSeparately() {
        FakeWorldState world = new FakeWorldState();
        var ledger = ledgerWithStep();
        var first = candidate(world, STEP, 0);
        var second = candidate(world, STEP, 1);

        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                ledger.claim(first, precheck(world, first)));
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                ledger.claim(second, precheck(world, second)));
        assertEquals(2, ledger.claimedTransferCount());
    }

    @Test
    void rejectedPrecheckDoesNotConsumeUnclaimedEvent() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var originalPrecheck = precheck(world, candidate);
        world.changeMirrorContent();
        var stalePrecheck = precheck(world, candidate);
        assertFalse(stalePrecheck.allowed());

        var ledger = ledgerWithStep();
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_REQUIRED,
                ledger.claim(candidate, stalePrecheck));
        assertFalse(ledger.isTransferClaimed(candidate.eventKey()));

        // The prior solver run must be abandoned before rerunning physics.
        assertEquals(WorldEffectLedger.AbandonResult.ABANDONED,
                ledger.abandonRun(STEP.calculationRunId()));
        FakeWorldState recalculatedWorld = new FakeWorldState();
        var staleRunCandidate = candidate(recalculatedWorld, STEP, 0);
        assertEquals(WorldEffectLedger.ClaimResult.RUN_ABANDONED,
                ledger.claim(staleRunCandidate, precheck(recalculatedWorld, staleRunCandidate)));
        var newStep = new CalculationStep("run-2", 0, STEP.interval());
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(newStep));
        var recalculated = candidate(recalculatedWorld, newStep, 0);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED,
                ledger.claim(recalculated, precheck(recalculatedWorld, recalculated)));
    }
}
