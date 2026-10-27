package dev.coldatomcraft.experiment;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.IdealCircularDetectorFace;

/**
 * DEV-ONLY fixture connecting the existing D9 ideal power measurement to a
 * collinear block-sized bench. These are NOT Owner-approved equipment settings.
 * No heat, absorption, noise, calibrated responsivity, or world effect occurs.
 *
 * <p>One Minecraft block is provisionally mapped to one metre of apparatus
 * separation. The beam waist is provisionally at the laser output port, and
 * both face anchors are 7/16 m from their block centres. Coordinates are
 * LAB-LOCAL, so large Minecraft world coordinates cannot erase micrometre
 * offsets. This class does not interpret game ticks as simulated nanoseconds.
 */
public final class PreviewOpticalBench {
    public static final int MAX_SEPARATION_BLOCKS = 24;
    public static final double DEMO_POWER_WATTS = 0.020;
    public static final double DEMO_WAVELENGTH_METERS = 780e-9;
    public static final double DEMO_WAIST_RADIUS_METERS = 0.0005;
    public static final double DEMO_PORT_OFFSET_METERS = 7.0 / 16.0;

    private static final double[] APERTURE_RADII_METERS = {0.0005, 0.001, 0.002};

    private PreviewOpticalBench() { }

    public static int aperturePresetCount() {
        return APERTURE_RADII_METERS.length;
    }

    public static double apertureRadiusMeters(int index) {
        if (index < 0 || index >= APERTURE_RADII_METERS.length) {
            throw new IllegalArgumentException("unknown preview aperture preset: " + index);
        }
        return APERTURE_RADII_METERS[index];
    }

    /**
     * A noninvasive, snapshot-only observation at a perpendicular, front-facing
     * ideal receiver. Does not imply a real detector, optical blockage or an
     * instrument GUI. The caller MUST check source power state, facing, chunk
     * load state and all intermediate blocks on the authoritative server.
     */
    public static IdealOpticalPowerMeter.Reading sampleAligned(
            int separationBlocks, int aperturePresetIndex) {
        return sampleAligned(separationBlocks, aperturePresetIndex, LaserTuning.DEFAULT);
    }

    /** Resolve this exact source's saved settings, never the global demo constants. */
    public static IdealOpticalPowerMeter.Reading sampleAligned(
            int separationBlocks, int aperturePresetIndex, LaserTuning tuning) {
        if (tuning == null) throw new IllegalArgumentException("source tuning required");
        if (separationBlocks < 1 || separationBlocks > MAX_SEPARATION_BLOCKS) {
            throw new IllegalArgumentException("preview source/detector separation must be 1..24 blocks");
        }
        double faceSeparationMeters = separationBlocks - 2.0 * DEMO_PORT_OFFSET_METERS;
        if (!(faceSeparationMeters > 0)) {
            throw new IllegalArgumentException("nonpositive optical port separation");
        }
        double aperture = apertureRadiusMeters(aperturePresetIndex);
        GaussianBeam beam = tuning.laboratoryBeam();
        IdealCircularDetectorFace face = new IdealCircularDetectorFace(
                new Vec3(0, 0, faceSeparationMeters), new Vec3(0, 0, -1), aperture);
        return new IdealOpticalPowerMeter("coldatomcraft:preview_power_meter", aperture)
                .sampleAtFace(beam, face, 0L);
    }
}
