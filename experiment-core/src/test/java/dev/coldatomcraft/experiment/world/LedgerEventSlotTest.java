package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelVerification;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.*;

/** D2 regressions: provenance variations must not create new event slots. */
class LedgerEventSlotTest {
    private static EnergyTransferCandidate withModel(EnergyTransferCandidate source,
                                                       String id, String revision) {
        return new EnergyTransferCandidate(id, revision, source.sourceDevice(), source.target(),
                source.absorbedEnergyJoules(), source.step(), source.eventIndex(), source.readSet());
    }

    private static WorldEffectGate.Precheck check(FakeWorldState world, EnergyTransferCandidate transfer) {
        var model = new ModelDefinition(transfer.modelId(), transfer.modelRevision(),
                "synthetic-test-model", ModelApproval.OWNER_APPROVED,
                ModelVerification.NUMERICALLY_VERIFIED);
        return WorldEffectGate.precheck(transfer, model, world.readSet(), world.device(), world.target());
    }

    @Test
    void changedModelRevisionCannotReclaimSameEventIndex() {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        var first = candidate(world, STEP, 0);
        var revised = withModel(first, first.modelId(), "REV-B");
        assertTrue(precheck(world, first).allowed());
        assertTrue(check(world, revised).allowed());
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(first, precheck(world, first)));
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                ledger.claim(revised, check(world, revised)));
        assertEquals(1, ledger.claimedTransferCount());
        assertEquals(first.eventKey(), ledger.claimedEventForSlot(STEP.calculationRunId(),
                STEP.stepIndex(), 0).orElseThrow());
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED, ledger.stateOf(revised.eventKey()));
    }

    @Test
    void changedModelIdentityCannotReclaimEvenAfterTerminalOutcome() {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        ledger.beginCalculation(STEP);
        var first = candidate(world, STEP, 0);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(first, precheck(world, first)));
        assertTrue(ledger.markApplied(first.eventKey()));
        var different = withModel(first, "DIFFERENT-TEST-MODEL", "Q");
        assertEquals(WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                ledger.claim(different, check(world, different)));
        assertEquals(WorldEffectLedger.SettlementState.APPLIED, ledger.stateOf(first.eventKey()));
    }

    @Test
    void differentEventIndicesAndDifferentStepsRemainLegal() {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        var later = new CalculationStep(STEP.calculationRunId(), 1, new SimulationInterval(1000, 2000));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(later));
        var a = candidate(world, STEP, 0);
        var b = candidate(world, STEP, 1);
        var c = candidate(world, later, 0);
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(a, precheck(world,a)));
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(b, precheck(world,b)));
        assertEquals(WorldEffectLedger.ClaimResult.CLAIMED, ledger.claim(c, precheck(world,c)));
        assertEquals(3, ledger.claimedTransferCount());
    }

    @Test
    void modelRevisionRaceClaimsAtMostOnce() throws Exception {
        var world = new FakeWorldState();
        var ledger = new WorldEffectLedger();
        ledger.beginCalculation(STEP);
        var first = candidate(world, STEP, 0);
        var revised = withModel(first, first.modelId(), "REV-B");
        var firstCheck = precheck(world, first);
        var secondCheck = check(world, revised);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                return ledger.claim(first, firstCheck);
            });
            var b = pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                return ledger.claim(revised, secondCheck);
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            var ar = a.get(10, TimeUnit.SECONDS);
            var br = b.get(10, TimeUnit.SECONDS);
            assertTrue((ar == WorldEffectLedger.ClaimResult.CLAIMED &&
                    br == WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER)
                    || (br == WorldEffectLedger.ClaimResult.CLAIMED &&
                    ar == WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER));
            assertEquals(1, ledger.claimedTransferCount());
        } finally {
            go.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
