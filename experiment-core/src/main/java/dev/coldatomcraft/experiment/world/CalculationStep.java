package dev.coldatomcraft.experiment.world;

/**
 * Identity of one server-side physical calculation step.
 *
 * <p>{@code calculationRunId} is allocated once per solver run by the server,
 * outside any client packet or saved player data. {@code stepIndex} is the
 * solver's monotonically increasing step number. The interval is the simulated
 * time slice consumed by that step. No optical/thermal model is defined here.</p>
 */
public record CalculationStep(String calculationRunId, long stepIndex, SimulationInterval interval) {
    public CalculationStep {
        if (calculationRunId == null || calculationRunId.isBlank()) {
            throw new IllegalArgumentException("calculation run ID required");
        }
        if (stepIndex < 0) {
            throw new IllegalArgumentException("step index must be non-negative");
        }
        if (interval == null) {
            throw new IllegalArgumentException("simulation interval required");
        }
    }
}
