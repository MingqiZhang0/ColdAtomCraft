package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelCatalog;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelVerification;

import java.util.List;

/**
 * Shared test-only fixtures for the world-interaction contract.
 *
 * <p>The synthetic model below exists only in test source. It is not evidence of
 * an Owner-approved scientific model and must never be copied into a production
 * model catalog.</p>
 */
final class ContractTestSupport {
    static final String MODEL_ID = "THM-001";
    static final String MODEL_REVISION = "A";
    static final CalculationStep STEP =
            new CalculationStep("run-1", 0, new SimulationInterval(0, 1_000));
    static final ModelDefinition SYNTHETIC_APPROVED_MODEL = new ModelDefinition(
            MODEL_ID, MODEL_REVISION, "test-only:THM-001-A",
            ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
    static final ModelCatalog TEST_MODEL_CATALOG =
            ModelCatalog.fromTrustedServerBootstrap(List.of(SYNTHETIC_APPROVED_MODEL));
    static final ServerMutationFacts ALL_SERVER_FACTS =
            new ServerMutationFacts(true, true, true, true, true, true, 0);

    private ContractTestSupport() { }

    static EnergyTransferCandidate candidate(FakeWorldState world,
                                             CalculationStep step,
                                             int eventIndex) {
        return candidate(world, step, eventIndex, 0.1);
    }

    static EnergyTransferCandidate candidate(FakeWorldState world,
                                             CalculationStep step,
                                             int eventIndex,
                                             double absorbedEnergyJoules) {
        return new EnergyTransferCandidate(MODEL_ID, MODEL_REVISION,
                world.device(), world.target(), absorbedEnergyJoules,
                step, eventIndex, world.readSet());
    }

    static WorldEffectGate.Precheck precheck(FakeWorldState world,
                                              EnergyTransferCandidate candidate) {
        return WorldEffectGate.precheck(candidate, SYNTHETIC_APPROVED_MODEL,
                world.readSet(), world.device(), world.target());
    }

    static WorldEffectGate.Precheck allowedPrecheck(FakeWorldState world,
                                                     CalculationStep step,
                                                     int eventIndex) {
        EnergyTransferCandidate candidate = candidate(world, step, eventIndex);
        WorldEffectGate.Precheck precheck = precheck(world, candidate);
        if (!precheck.allowed()) {
            throw new AssertionError("fixture precheck unexpectedly rejected: " + precheck.reasons());
        }
        return precheck;
    }
}
