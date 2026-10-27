package dev.coldatomcraft.experiment.world;

/**
 * Stable identity of one energy-transfer event produced by a calculation step.
 *
 * <p>The event index is assigned by the solver and must be unique among all
 * events of that step. Target identity, energy, position and read set are
 * intentionally absent: a duplicate event cannot become new by changing its
 * target or payload between submissions. The ledger also reserves a secondary
 * (runId, stepIndex, eventIndex) slot, so switching the model ID or revision
 * cannot produce two claims for the same event slot.</p>
 */
public record TransferEventKey(String modelId, String modelRevision,
                               String calculationRunId, long stepIndex, int eventIndex) {
    public TransferEventKey {
        if (modelId == null || modelId.isBlank()
                || modelRevision == null || modelRevision.isBlank()
                || calculationRunId == null || calculationRunId.isBlank()) {
            throw new IllegalArgumentException("model, revision and calculation run required");
        }
        if (stepIndex < 0 || eventIndex < 0) {
            throw new IllegalArgumentException("step and event indices must be non-negative");
        }
    }
}
