package dev.coldatomcraft.experiment.world;

/**
 * Data-only candidate from a numerically verified, owner-approved physical solver.
 * NOT a game damage/heating law.
 *
 * <p>The candidate carries a complete calculation step and event identity so a
 * duplicate submission can be rejected deterministically. It also carries the
 * device and target snapshots and every chunk read by the calculation. Passing
 * {@link WorldEffectGate} is a <strong>precheck only</strong>; it is not a permit
 * to mutate a Minecraft level.</p>
 */
public record EnergyTransferCandidate(
        String modelId,
        String modelRevision,
        DeviceSnapshot sourceDevice,
        TargetSnapshot target,
        double absorbedEnergyJoules,
        CalculationStep step,
        int eventIndex,
        WorldReadSet readSet) {

    public EnergyTransferCandidate {
        if (modelId == null || modelId.isBlank()
                || modelRevision == null || modelRevision.isBlank()
                || sourceDevice == null || target == null
                || step == null || readSet == null
                || !Double.isFinite(absorbedEnergyJoules) || absorbedEnergyJoules < 0
                || eventIndex < 0) {
            throw new IllegalArgumentException("invalid energy-transfer candidate");
        }
        if (!sourceDevice.loaded() || !target.loaded()) {
            throw new IllegalArgumentException("candidate device and target must be loaded at capture time");
        }
        if (!sourceDevice.dimension().equals(target.dimension())) {
            throw new IllegalArgumentException("device and target must be in the same dimension");
        }
        if (!readSet.containsChunk(sourceDevice.dimension(), sourceDevice.chunkX(), sourceDevice.chunkZ())
                || !readSet.containsChunk(target.dimension(), target.chunkX(), target.chunkZ())) {
            throw new IllegalArgumentException("read set must contain the device and target chunks");
        }
    }

    /** Stable identity of this transfer event, excluding payload and target. */
    public TransferEventKey eventKey() {
        return new TransferEventKey(modelId, modelRevision, step.calculationRunId(),
                step.stepIndex(), eventIndex);
    }

    public String targetId() {
        return target.targetId();
    }

    public TargetKind targetKind() {
        return target.kind();
    }
}
