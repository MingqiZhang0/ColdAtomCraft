package dev.coldatomcraft.physics.optics;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable single-branch optical-mode history. Splitting/recombination is
 * deliberately performed by IdealTwoPortBeamSplitter, outside this linear
 * path chain, so neither output port can be silently discarded.
 *
 * <p>Removed optical power is a tracked-mode deficit only. It is NOT an
 * absorption, local heating or Minecraft damage assignment.</p>
 */
public record OpticalPathTrace(ParaxialModeField current, List<Step> steps) {
    public record Step(String operation, ParaxialModeField input,
                       ParaxialModeField output, double powerRemovedFromModeWatts) {
        public Step {
            if (operation == null || operation.isBlank() || input == null || output == null
                    || !Double.isFinite(powerRemovedFromModeWatts)
                    || powerRemovedFromModeWatts < 0) {
                throw new IllegalArgumentException("valid path step required");
            }
        }
    }

    public OpticalPathTrace {
        if (current == null || steps == null || steps.stream().anyMatch(s -> s == null)) {
            throw new IllegalArgumentException("optical state and steps required");
        }
        steps = List.copyOf(steps);
    }

    public static OpticalPathTrace start(ParaxialModeField initial) {
        return new OpticalPathTrace(initial, List.of());
    }

    private OpticalPathTrace append(String kind, ParaxialModeField next) {
        double before = current.powerWatts();
        double after = next.powerWatts();
        double removed = Math.max(0.0, before - after);
        var history = new ArrayList<>(steps);
        history.add(new Step(kind, current, next, removed));
        return new OpticalPathTrace(next, history);
    }

    public OpticalPathTrace freeSpace(double distanceMeters) {
        return append("FREE_SPACE", current.freeSpace(distanceMeters));
    }

    public OpticalPathTrace thinLens(double focalLengthMeters, double onAxisPhaseRadians) {
        return append("THIN_LENS", current.thinLens(focalLengthMeters, onAxisPhaseRadians));
    }

    public OpticalPathTrace phaseShift(double phaseRadians) {
        return append("CALIBRATED_PHASE", current.withUniformPhaseRadians(phaseRadians));
    }

    public OpticalPathTrace idealMirrorPhaseOnly(double coatingAndPathPhaseRadians) {
        return append("MIRROR_PHASE_PROXY",
                current.withIdealMirrorReferencePhaseRadians(coatingAndPathPhaseRadians));
    }

    public OpticalPathTrace passiveJones(JonesMatrix jones) {
        return append("PASSIVE_JONES", current.throughPassiveJones(jones));
    }

    public double totalRemovedFromTrackedModeWatts() {
        double total = 0;
        for (Step step : steps) total += step.powerRemovedFromModeWatts();
        return total;
    }
}
