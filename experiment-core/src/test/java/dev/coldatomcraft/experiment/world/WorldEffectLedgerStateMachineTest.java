package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEffectLedgerStateMachineTest {

    private record Fixture(FakeWorldState world,
                           EnergyTransferCandidate candidate,
                           WorldEffectGate.Precheck precheck,
                           WorldEffectLedger ledger) { }

    private static Fixture fixture() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var precheck = precheck(world, candidate);
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        return new Fixture(world, candidate, precheck, ledger);
    }

    @Test
    void failureBeforeClaimLeavesEventUnclaimedAndUnapplied() {
        Fixture f = fixture();
        assertThrows(IllegalStateException.class, () -> {
            throw new IllegalStateException("injected before claim");
        });

        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED, f.ledger().stateOf(f.candidate().eventKey()));
        assertFalse(f.ledger().isTransferClaimed(f.candidate().eventKey()));
        assertTrue(f.ledger().failureReason(f.candidate().eventKey()).isEmpty());
    }

    @Test
    void failureAfterClaimBeforeMutationDoesNotBecomeApplied() {
        Fixture f = fixture();
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, f.ledger().claim(f.candidate(), f.precheck()));
        assertThrows(IllegalStateException.class, () -> {
            throw new IllegalStateException("injected after claim, before apply");
        });

        assertEquals(WorldEffectLedger.SettlementState.CLAIMED, f.ledger().stateOf(f.candidate().eventKey()));
        assertTrue(f.ledger().failureReason(f.candidate().eventKey()).isEmpty());
    }

    @Test
    void failureDuringApplicationIsRecordedAndTerminal() {
        Fixture f = fixture();
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, f.ledger().claim(f.candidate(), f.precheck()));
        String reason;
        try {
            throw new IllegalStateException("injected partial application");
        } catch (RuntimeException failure) {
            reason = failure.toString();
        }
        assertTrue(f.ledger().markFailed(f.candidate().eventKey(), reason));

        assertEquals(WorldEffectLedger.SettlementState.FAILED, f.ledger().stateOf(f.candidate().eventKey()));
        assertEquals(reason, f.ledger().failureReason(f.candidate().eventKey()).orElseThrow());
        assertFalse(f.ledger().markApplied(f.candidate().eventKey()));
        assertFalse(f.ledger().markFailed(f.candidate().eventKey(), "second failure record"));
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                f.ledger().claim(f.candidate(), f.precheck()));
    }

    @Test
    void crashAfterWorldOperationBeforeMarkAppliedRemainsUncertainButNotRetried() {
        Fixture f = fixture();
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, f.ledger().claim(f.candidate(), f.precheck()));
        // Simulate the process disappearing before markApplied: the ledger can
        // only retain CLAIMED. It has no proof that the world operation happened.
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED, f.ledger().stateOf(f.candidate().eventKey()));
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                f.ledger().claim(f.candidate(), f.precheck()));
        assertTrue(f.ledger().failureReason(f.candidate().eventKey()).isEmpty());
    }

    @Test
    void repeatedMarkAppliedAndLateFailureAreRejected() {
        Fixture f = fixture();
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, f.ledger().claim(f.candidate(), f.precheck()));
        assertTrue(f.ledger().markApplied(f.candidate().eventKey()));
        assertEquals(WorldEffectLedger.SettlementState.APPLIED, f.ledger().stateOf(f.candidate().eventKey()));
        assertFalse(f.ledger().markApplied(f.candidate().eventKey()));
        assertFalse(f.ledger().markFailed(f.candidate().eventKey(), "late failure"));
        assertEquals(WorldEffectLedger.SettlementState.APPLIED, f.ledger().stateOf(f.candidate().eventKey()));
        assertTrue(f.ledger().failureReason(f.candidate().eventKey()).isEmpty());
    }

    @Test
    void failedEventCannotBecomeAppliedOrBeFailedTwice() {
        Fixture f = fixture();
        f.ledger().claim(f.candidate(), f.precheck());
        assertTrue(f.ledger().markFailed(f.candidate().eventKey(), "executor rejected operation"));
        assertFalse(f.ledger().markApplied(f.candidate().eventKey()));
        assertFalse(f.ledger().markFailed(f.candidate().eventKey(), "second failure"));
        assertEquals(WorldEffectLedger.SettlementState.FAILED, f.ledger().stateOf(f.candidate().eventKey()));
    }

    @Test
    void blankFailureReasonIsRejectedWithoutChangingClaimedState() {
        Fixture f = fixture();
        f.ledger().claim(f.candidate(), f.precheck());
        assertThrows(IllegalArgumentException.class,
                () -> f.ledger().markFailed(f.candidate().eventKey(), "  "));
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED, f.ledger().stateOf(f.candidate().eventKey()));
        assertTrue(f.ledger().failureReason(f.candidate().eventKey()).isEmpty());
    }

    @Test
    void outcomeMethodsRejectUnknownOrNullEvents() {
        Fixture f = fixture();
        assertFalse(f.ledger().markApplied(null));
        assertFalse(f.ledger().markFailed(null, "reason"));
        assertFalse(f.ledger().markApplied(f.candidate().eventKey()));
        assertFalse(f.ledger().markFailed(f.candidate().eventKey(), "not claimed"));
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED, f.ledger().stateOf(null));
        assertTrue(f.ledger().failureReason(null).isEmpty());
    }

    @Test
    void invalidClaimInputsDoNotReserveEvent() {
        Fixture f = fixture();
        assertEquals(WorldEffectLedger.ClaimResult.INVALID, f.ledger().claim(null, f.precheck()));
        assertEquals(WorldEffectLedger.ClaimResult.INVALID, f.ledger().claim(f.candidate(), null));
        assertEquals(WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                f.ledger().claim(candidate(f.world(), STEP, 0, 2.0), f.precheck()));
        assertFalse(f.ledger().isTransferClaimed(f.candidate().eventKey()));
    }
}
