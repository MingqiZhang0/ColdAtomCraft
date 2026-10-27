package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelVerification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEffectGateTest {
    private static final CalculationStep STEP =
            new CalculationStep("run-1", 0, new SimulationInterval(0, 1_000_000));
    private static final ModelDefinition APPROVED = new ModelDefinition(
            "THM-001", "A", "owner:THM-001-A",
            ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);

    private static EnergyTransferCandidate candidate(FakeWorldState world) {
        return new EnergyTransferCandidate("THM-001", "A", world.device(), world.target(),
                0.1, STEP, 0, world.readSet());
    }

    private static WorldEffectGate.Precheck precheck(FakeWorldState world,
                                                      EnergyTransferCandidate candidate,
                                                      ModelDefinition model) {
        return WorldEffectGate.precheck(candidate, model, world.readSet(), world.device(), world.target());
    }

    @Test
    void acceptsCurrentCompleteSnapshotAsPrecheckOnly() {
        FakeWorldState world = new FakeWorldState();
        var precheck = precheck(world, candidate(world), APPROVED);
        assertTrue(precheck.allowed(), () -> "unexpected rejection: " + precheck.reasons());
        assertEquals("THM-001", precheck.candidateKey().modelId());
        assertEquals(0, precheck.reasons().size());
    }

    @Test
    void rejectsStaleChunkContentAndReloadEpoch() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world);
        world.changeTargetChunkContent();
        assertTrue(precheck(world, candidate, APPROVED).reasons()
                .contains(WorldEffectGate.RejectionReason.READ_SET_MISMATCH));

        world = new FakeWorldState();
        candidate = candidate(world);
        world.reloadWorld();
        assertTrue(precheck(world, candidate, APPROVED).reasons()
                .contains(WorldEffectGate.RejectionReason.READ_SET_MISMATCH));
    }

    @Test
    void rejectsChangedIntermediateChunkInMultiChunkPath() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world);
        world.changeMirrorContent();
        var result = precheck(world, candidate, APPROVED);
        assertFalse(result.allowed());
        assertTrue(result.reasons().contains(WorldEffectGate.RejectionReason.READ_SET_MISMATCH));
    }

    @Test
    void rejectsModelProblemsIndependently() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world);

        var approvedButUnverified = new ModelDefinition("THM-001", "A", "owner:A",
                ModelApproval.OWNER_APPROVED, ModelVerification.IMPLEMENTED);
        assertTrue(precheck(world, candidate, approvedButUnverified).reasons()
                .contains(WorldEffectGate.RejectionReason.MODEL_NOT_NUMERICALLY_VERIFIED));

        var wrongRevision = new ModelDefinition("THM-001", "B", "owner:B",
                ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
        assertTrue(precheck(world, candidate, wrongRevision).reasons()
                .contains(WorldEffectGate.RejectionReason.MODEL_MISMATCH));

        var wrongId = new ModelDefinition("OPT-999", "A", "owner:other",
                ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
        assertTrue(precheck(world, candidate, wrongId).reasons()
                .contains(WorldEffectGate.RejectionReason.MODEL_MISMATCH));

        var draft = new ModelDefinition("THM-001", "A", "PENDING", ModelApproval.DRAFT);
        var draftResult = precheck(world, candidate, draft);
        assertTrue(draftResult.reasons().contains(WorldEffectGate.RejectionReason.MODEL_NOT_OWNER_APPROVED));
        assertTrue(draftResult.reasons().contains(WorldEffectGate.RejectionReason.MODEL_NOT_NUMERICALLY_VERIFIED));
    }

    @Test
    void rejectsChangedDeviceStateAndMovedTarget() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world);
        world.changeDeviceState();
        assertTrue(precheck(world, candidate, APPROVED).reasons()
                .contains(WorldEffectGate.RejectionReason.DEVICE_SNAPSHOT_MISMATCH));

        world = new FakeWorldState();
        candidate = candidate(world);
        world.moveTarget(1.0);
        assertTrue(precheck(world, candidate, APPROVED).reasons()
                .contains(WorldEffectGate.RejectionReason.TARGET_SNAPSHOT_MISMATCH));
    }

    @Test
    void rejectsUnloadedDeviceOrTarget() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world);
        var readSet = candidate.readSet();
        world.unloadDevice();
        var gate = WorldEffectGate.precheck(candidate, APPROVED, readSet, world.device(), world.target());
        assertTrue(gate.reasons().contains(WorldEffectGate.RejectionReason.DEVICE_UNLOADED));

        world = new FakeWorldState();
        candidate = candidate(world);
        readSet = candidate.readSet();
        world.unloadTarget();
        gate = WorldEffectGate.precheck(candidate, APPROVED, readSet, world.device(), world.target());
        assertTrue(gate.reasons().contains(WorldEffectGate.RejectionReason.TARGET_UNLOADED));
    }

    @Test
    void rejectsMissingInputsAndInvalidCandidates() {
        var result = WorldEffectGate.precheck(null, null, null, null, null);
        assertFalse(result.allowed());
        assertTrue(result.reasons().contains(WorldEffectGate.RejectionReason.MISSING_INPUT));

        FakeWorldState world = new FakeWorldState();
        assertThrows(IllegalArgumentException.class, () -> new EnergyTransferCandidate(
                "THM-001", "A", world.device(), world.target(), Double.NaN, STEP, 0, world.readSet()));
        assertThrows(IllegalArgumentException.class, () -> new EnergyTransferCandidate(
                "THM-001", "A", world.device(), world.target(), -1, STEP, 0, world.readSet()));
        assertThrows(IllegalArgumentException.class, () -> new EnergyTransferCandidate(
                "THM-001", "A", world.device(), world.target(), 1, STEP, -1, world.readSet()));
    }

    @Test
    void rejectsReadSetWithoutDeviceOrTargetChunk() {
        FakeWorldState world = new FakeWorldState();
        var onlyMirror = WorldReadSet.of(new WorldRevision(
                FakeWorldState.DIMENSION, 2, 2, 1, 12, true));
        assertThrows(IllegalArgumentException.class, () -> new EnergyTransferCandidate(
                "THM-001", "A", world.device(), world.target(), 1, STEP, 0, onlyMirror));
    }
}
