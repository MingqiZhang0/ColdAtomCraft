package dev.coldatomcraft.experiment;

/**
 * An immutable simulated instrument observation. Value and uncertainty are in
 * the same declared SI unit. An observation is never automatically ground truth.
 */
public record Measurement(String deviceId, String observable, String siUnit,
                          double value, double standardUncertainty, long simulatedTimeNanos) {
    public Measurement {
        if (deviceId == null || deviceId.isBlank() || observable == null || observable.isBlank()
                || siUnit == null || siUnit.isBlank()) {
            throw new IllegalArgumentException("device, observable and unit required");
        }
        if (!Double.isFinite(value) || !Double.isFinite(standardUncertainty)
                || standardUncertainty < 0 || simulatedTimeNanos < 0) {
            throw new IllegalArgumentException("invalid measurement values");
        }
    }
}
