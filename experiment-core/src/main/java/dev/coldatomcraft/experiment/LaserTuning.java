package dev.coldatomcraft.experiment;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;

/**
 * Bounded, SI-converted, DEV-only laser settings. The parameters belong to a
 * single source and are never treated as a certified manufacturer's model.
 *
 * <p>The output port is at local axial z=0. A positive waist offset means the
 * beam waist is located downstream of the output port. One block is one metre
 * ONLY in the D10-D12 diagnostic optical bench fixture.</p>
 */
public record LaserTuning(int powerMilliwatts, int wavelengthNanometers,
                          int waistRadiusMicrometers, int waistOffsetMillimeters) {
    public static final int MIN_POWER_MW = 1, MAX_POWER_MW = 200;
    public static final int MIN_WAVELENGTH_NM = 400, MAX_WAVELENGTH_NM = 1100;
    public static final int MIN_WAIST_UM = 100, MAX_WAIST_UM = 3000;
    public static final int MIN_WAIST_OFFSET_MM = -2000, MAX_WAIST_OFFSET_MM = 2000;
    public static final LaserTuning DEFAULT = new LaserTuning(20, 780, 500, 0);

    public LaserTuning {
        requireRange(powerMilliwatts, MIN_POWER_MW, MAX_POWER_MW, "powerMilliwatts");
        requireRange(wavelengthNanometers, MIN_WAVELENGTH_NM, MAX_WAVELENGTH_NM, "wavelengthNanometers");
        requireRange(waistRadiusMicrometers, MIN_WAIST_UM, MAX_WAIST_UM, "waistRadiusMicrometers");
        requireRange(waistOffsetMillimeters, MIN_WAIST_OFFSET_MM, MAX_WAIST_OFFSET_MM, "waistOffsetMillimeters");
    }

    private static void requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) throw new IllegalArgumentException(label + " outside approved preview control range");
    }

    public double powerWatts() { return powerMilliwatts * 0.001; }
    public double wavelengthMeters() { return wavelengthNanometers * 1e-9; }
    public double waistRadiusMeters() { return waistRadiusMicrometers * 1e-6; }
    public double waistOffsetMeters() { return waistOffsetMillimeters * 0.001; }

    public GaussianBeam laboratoryBeam() {
        return new GaussianBeam(powerWatts(), wavelengthMeters(), waistRadiusMeters(),
                new Vec3(0, 0, waistOffsetMeters()), new Vec3(0, 0, 1));
    }

    /** Input z is measured from the source output PORT, not from the beam waist. */
    public double beamRadiusAtPortDistanceMeters(double portDistanceMeters) {
        if (!Double.isFinite(portDistanceMeters) || portDistanceMeters < 0) {
            throw new IllegalArgumentException("port distance must be nonnegative and finite");
        }
        return laboratoryBeam().radiusMeters(portDistanceMeters - waistOffsetMeters());
    }

    /** Return the exact setting replacement; reject out-of-range values. */
    public LaserTuning withField(int field, int value) {
        return switch (field) {
            case 0 -> new LaserTuning(value, wavelengthNanometers, waistRadiusMicrometers, waistOffsetMillimeters);
            case 1 -> new LaserTuning(powerMilliwatts, value, waistRadiusMicrometers, waistOffsetMillimeters);
            case 2 -> new LaserTuning(powerMilliwatts, wavelengthNanometers, value, waistOffsetMillimeters);
            case 3 -> new LaserTuning(powerMilliwatts, wavelengthNanometers, waistRadiusMicrometers, value);
            default -> throw new IllegalArgumentException("unknown laser field");
        };
    }

    /** Corrupt or incompatible stored data is reset to a known safe test default. */
    public static LaserTuning fromStored(int power, int wavelength, int radius, int offset) {
        try { return new LaserTuning(power, wavelength, radius, offset); }
        catch (IllegalArgumentException invalid) { return DEFAULT; }
    }
}
