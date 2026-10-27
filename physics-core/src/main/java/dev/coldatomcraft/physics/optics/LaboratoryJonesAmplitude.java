package dev.coldatomcraft.physics.optics;

/** Complex Cartesian traveling-mode amplitude in sqrt(W), NOT V/m or sqrt(W/m^2). */
public record LaboratoryJonesAmplitude(ComplexAmplitude x,
                                        ComplexAmplitude y,
                                        ComplexAmplitude z) {
    public LaboratoryJonesAmplitude {
        if (x == null || y == null || z == null) {
            throw new IllegalArgumentException("all lab Jones components required");
        }
    }
    public double squaredNormWatts() {
        return x.magnitudeSquared() + y.magnitudeSquared() + z.magnitudeSquared();
    }
}
