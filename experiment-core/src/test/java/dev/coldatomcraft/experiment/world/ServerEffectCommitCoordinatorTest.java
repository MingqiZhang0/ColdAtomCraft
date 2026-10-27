package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelCatalog;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelVerification;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.ALL_SERVER_FACTS;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.MODEL_ID;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.MODEL_REVISION;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.SYNTHETIC_APPROVED_MODEL;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.TEST_MODEL_CATALOG;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEffectCommitCoordinatorTest {

    private record Fixture(FakeWorldState world,
                           EnergyTransferCandidate candidate,
                           WorldEffectGate.Precheck precheck,
                           WorldEffectLedger ledger,
                           ServerEffectCommitCoordinator coordinator) { }

    private static Fixture fixture(ModelCatalog catalog) {
        if (catalog == null) {
            catalog = TEST_MODEL_CATALOG;
        }
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        var precheck = precheck(world, candidate);
        var ledger = new WorldEffectLedger();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, ledger.beginCalculation(STEP));
        return new Fixture(world, candidate, precheck, ledger,
                new ServerEffectCommitCoordinator(ledger, catalog));
    }

    @Test
    void unchangedWorldAndCompleteServerFactsReachSettlementClaimOnly() {
        Fixture f = fixture(null);
        assertEquals(ServerEffectCommitCoordinator.Decision.SETTLEMENT_CLAIMED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
        assertEquals(WorldEffectLedger.SettlementState.CLAIMED,
                f.ledger().stateOf(f.candidate().eventKey()));
        assertEquals(1, f.ledger().claimedTransferCount());
    }

    @Test
    void changedIntermediateChunkRejectsStalePrecheck() {
        Fixture f = fixture(null);
        f.world().changeMirrorContent();
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED,
                f.ledger().stateOf(f.candidate().eventKey()));
    }

    @Test
    void unloadedTargetChunkRejectsSubmission() {
        Fixture f = fixture(null);
        f.world().unloadTarget();
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
        assertFalse(f.ledger().isTransferClaimed(f.candidate().eventKey()));
    }

    @Test
    void unloadedSourceDeviceChunkRejectsSubmission() {
        Fixture f = fixture(null);
        f.world().unloadDevice();
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
        assertFalse(f.ledger().isTransferClaimed(f.candidate().eventKey()));
    }

    @Test
    void changedDeviceRevisionRejectsSubmission() {
        Fixture f = fixture(null);
        f.world().changeDeviceState();
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void movedTargetRejectsSubmission() {
        Fixture f = fixture(null);
        f.world().moveTarget(1.0);
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void changedTargetIdentityRejectsSubmission() {
        Fixture f = fixture(null);
        f.world().changeTargetIdentity();
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void reloadedChunkEpochRejectsStaleResult() {
        Fixture f = fixture(null);
        f.world().reloadWorld();
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void revokedPermissionRejectsSubmission() {
        Fixture f = fixture(null);
        var facts = new ServerMutationFacts(true, true, true, true, true, false, 0);
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), facts));
    }

    @Test
    void nonServerThreadRejectsSubmission() {
        Fixture f = fixture(null);
        var facts = new ServerMutationFacts(false, true, true, true, true, true, 0);
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), facts));
    }

    @Test
    void staleResultCausesNoWorldMutationAndNoSettlement() {
        Fixture f = fixture(null);
        ServerWorldSnapshot before = f.world().snapshot();
        f.world().changeMirrorContent();
        ServerWorldSnapshot staleWorld = f.world().snapshot();

        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), staleWorld, ALL_SERVER_FACTS));

        assertEquals(staleWorld, f.world().snapshot());
        assertFalse(before.equals(f.world().snapshot()));
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED,
                f.ledger().stateOf(f.candidate().eventKey()));
        assertFalse(f.ledger().isTransferClaimed(f.candidate().eventKey()));
    }

    @Test
    void staleRejectionRequiresNewServerRunForActualSolverReexecution() {
        Fixture f = fixture(null);
        f.world().changeMirrorContent();
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                f.coordinator().submit(f.candidate(), f.precheck(), f.world().snapshot(), ALL_SERVER_FACTS));
        assertFalse(f.ledger().isTransferClaimed(f.candidate().eventKey()));
        assertEquals(WorldEffectLedger.AbandonResult.ABANDONED,
                f.ledger().abandonRun(STEP.calculationRunId()));

        // Re-running the physical solver requires an independent server-owned run ID.
        // A refreshed candidate under the retired ID is deliberately rejected.
        var lateOldRun = candidate(f.world(), STEP, 0);
        assertEquals(ServerEffectCommitCoordinator.Decision.RUN_ABANDONED,
                f.coordinator().submit(lateOldRun, precheck(f.world(), lateOldRun),
                        f.world().snapshot(), ALL_SERVER_FACTS));

        var replacementStep = new CalculationStep("run-2", 0, STEP.interval());
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                f.ledger().beginCalculation(replacementStep));
        var recalculated = candidate(f.world(), replacementStep, 0);
        assertEquals(ServerEffectCommitCoordinator.Decision.SETTLEMENT_CLAIMED,
                f.coordinator().submit(recalculated, precheck(f.world(), recalculated),
                        f.world().snapshot(), ALL_SERVER_FACTS));
        assertEquals(WorldEffectLedger.SettlementState.UNCLAIMED,
                f.ledger().stateOf(f.candidate().eventKey()));
    }

    @Test
    void oldPrecheckForDifferentCandidateIsRejectedBeforeWorldChecks() {
        Fixture f = fixture(null);
        var changed = candidate(f.world(), STEP, 0, 2.0);
        assertEquals(ServerEffectCommitCoordinator.Decision.PRECHECK_CANDIDATE_MISMATCH,
                f.coordinator().submit(changed, f.precheck(),
                        f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void emptyTrustedCatalogFailsClosed() {
        Fixture f = fixture(ModelCatalog.fromTrustedServerBootstrap(List.of()));
        assertEquals(ServerEffectCommitCoordinator.Decision.MODEL_NOT_TRUSTED,
                f.coordinator().submit(f.candidate(), f.precheck(),
                        f.world().snapshot(), ALL_SERVER_FACTS));
        assertFalse(f.ledger().isTransferClaimed(f.candidate().eventKey()));
    }

    @Test
    void draftModelInTrustedCatalogFailsClosed() {
        var draft = new ModelDefinition(MODEL_ID, MODEL_REVISION, "PENDING", ModelApproval.DRAFT);
        Fixture f = fixture(ModelCatalog.fromTrustedServerBootstrap(List.of(draft)));
        assertEquals(ServerEffectCommitCoordinator.Decision.MODEL_NOT_TRUSTED,
                f.coordinator().submit(f.candidate(), f.precheck(),
                        f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void approvedButUnverifiedModelFailsClosed() {
        var implemented = new ModelDefinition(MODEL_ID, MODEL_REVISION, "test-only:" + MODEL_ID,
                ModelApproval.OWNER_APPROVED, ModelVerification.IMPLEMENTED);
        Fixture f = fixture(ModelCatalog.fromTrustedServerBootstrap(List.of(implemented)));
        assertEquals(ServerEffectCommitCoordinator.Decision.MODEL_NOT_TRUSTED,
                f.coordinator().submit(f.candidate(), f.precheck(),
                        f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void unknownModelRevisionFailsClosed() {
        Fixture f = fixture(TEST_MODEL_CATALOG);
        var unknown = new EnergyTransferCandidate(MODEL_ID, "B",
                f.world().device(), f.world().target(), 0.1, STEP, 0, f.world().readSet());
        var unknownModel = new ModelDefinition(MODEL_ID, "B", "test-only:B",
                ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
        var unknownPrecheck = WorldEffectGate.precheck(unknown, unknownModel,
                f.world().readSet(), f.world().device(), f.world().target());

        assertEquals(ServerEffectCommitCoordinator.Decision.MODEL_NOT_TRUSTED,
                f.coordinator().submit(unknown, unknownPrecheck,
                        f.world().snapshot(), ALL_SERVER_FACTS));
    }

    @Test
    void coordinatorPublicApiNeverAcceptsAClientModelDefinition() {
        assertTrue(Arrays.stream(ServerEffectCommitCoordinator.class.getMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(type -> type == ModelDefinition.class));
    }

    @Test
    void coordinatorHasNoMutationApi() {
        assertTrue(Arrays.stream(ServerEffectCommitCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .noneMatch(name -> name.matches(
                        "apply|mutate|setBlock|hurt|damage|explode|destroy|consume|spawn")));
    }

    @Test
    void coordinatorDoesNotClaimBeforeEveryCheckPasses() {
        Fixture f = fixture(null);
        assertEquals(ServerEffectCommitCoordinator.Decision.PRECHECK_NOT_ALLOWED,
                f.coordinator().submit(f.candidate(),
                        WorldEffectGate.precheck(null, SYNTHETIC_APPROVED_MODEL,
                                f.world().readSet(), f.world().device(), f.world().target()),
                        f.world().snapshot(), ALL_SERVER_FACTS));
        assertEquals(0, f.ledger().claimedTransferCount());
    }
}
