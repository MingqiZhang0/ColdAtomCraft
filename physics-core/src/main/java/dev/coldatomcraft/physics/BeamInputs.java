package dev.coldatomcraft.physics;

/**
 * Validated instrument settings only. NOT a laser propagation/scattering model.
 * All lengths are SI meters in a laboratory-local frame: never mix absolute
 * Minecraft block coordinates with microscopic physics coordinates.
 * These fields may change after approval of OPT-001 by the scientific owner.
 */
public record BeamInputs(
        double powerWatts,
        double vacuumWavelengthMeters,
        double waistRadiusMeters,
        Vec3 outputPositionLabMeters,
        Vec3 outputDirectionLab) {
    public BeamInputs {
        Vec3.requireFinite(powerWatts, "powerWatts");
        Vec3.requireFinite(vacuumWavelengthMeters, "vacuumWavelengthMeters");
        Vec3.requireFinite(waistRadiusMeters, "waistRadiusMeters");
        if (powerWatts < 0 || vacuumWavelengthMeters <= 0 || waistRadiusMeters <= 0) {
            throw new IllegalArgumentException("power >= 0 and wavelength/waist > 0 required");
        }
        if (outputPositionLabMeters == null || outputDirectionLab == null) {
            throw new IllegalArgumentException("output position/direction must be provided");
        }
        outputDirectionLab = outputDirectionLab.normalized();
    }
}
