package dev.coldatomcraft.experiment;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;

import java.util.Objects;
import java.util.function.IntFunction;

/**
 * D11 DEV-only visualisation contract for a straight, air-only beam.
 *
 * <p>All physics lengths are laboratory-local metres (the D10 preview
 * explicitly assumes 1 block = 1 metre). This class is read-only. It does
 * not authorize Minecraft block/entity effects or substitute for the
 * authoritative receiver-side measurement scan.
 */
public final class PreviewBeamDisplay {
    public enum Cell { AIR, RECEIVER, OBSTACLE, UNLOADED }
    public enum Stop { REACH_LIMIT, RECEIVER, OBSTACLE, UNLOADED }

    /** Distances are measured from the laser block's centre along its facing. */
    public record Trace(Stop stop, int cellDistance, double endFromSourceCenterBlocks) {
        public Trace {
            Objects.requireNonNull(stop, "stop");
            if (cellDistance < 1 || cellDistance > PreviewOpticalBench.MAX_SEPARATION_BLOCKS
                    || !Double.isFinite(endFromSourceCenterBlocks)
                    || endFromSourceCenterBlocks < 0
                    || endFromSourceCenterBlocks > PreviewOpticalBench.MAX_SEPARATION_BLOCKS + 0.5) {
                throw new IllegalArgumentException("invalid preview visual trace");
            }
        }
    }

    private static final GaussianBeam BEAM = new GaussianBeam(
            PreviewOpticalBench.DEMO_POWER_WATTS,
            PreviewOpticalBench.DEMO_WAVELENGTH_METERS,
            PreviewOpticalBench.DEMO_WAIST_RADIUS_METERS,
            new Vec3(0, 0, 0), new Vec3(0, 0, 1));

    private PreviewBeamDisplay() { }

    /**
     * Traverses source->forward. The cell adapter must not force chunk loads.
     * The first non-air cell ends the visual beam, including incorrectly
     * oriented detectors (which the adapter should classify as OBSTACLE).
     * An unloaded cell is fail-closed; no geometry is shown inside it.
     */
    public static Trace trace(IntFunction<Cell> cellAtDistance) {
        Objects.requireNonNull(cellAtDistance, "cellAtDistance");
        for (int distance = 1; distance <= PreviewOpticalBench.MAX_SEPARATION_BLOCKS; distance++) {
            Cell cell = Objects.requireNonNull(cellAtDistance.apply(distance), "unknown beam cell");
            switch (cell) {
                case AIR -> { continue; }
                case RECEIVER -> {
                    // The receiver's optical face is 7/16 block from its centre.
                    return new Trace(Stop.RECEIVER, distance,
                            distance - PreviewOpticalBench.DEMO_PORT_OFFSET_METERS);
                }
                case OBSTACLE -> {
                    // Air/solid interface at the near edge of the first obstacle block.
                    return new Trace(Stop.OBSTACLE, distance, distance - 0.5);
                }
                case UNLOADED -> {
                    return new Trace(Stop.UNLOADED, distance, distance - 0.5);
                }
            }
        }
        // All 24 checked cells were empty; stay within the last checked cell.
        return new Trace(Stop.REACH_LIMIT, PreviewOpticalBench.MAX_SEPARATION_BLOCKS,
                PreviewOpticalBench.MAX_SEPARATION_BLOCKS + 0.4375);
    }

    /** The real, unscaled 1/e^2 intensity radius, in metres. */
    public static double physicalRadiusMeters(double distanceFromLaserPortMeters) {
        if (!Double.isFinite(distanceFromLaserPortMeters) || distanceFromLaserPortMeters < 0) {
            throw new IllegalArgumentException("optical path distance must be finite and nonnegative");
        }
        return BEAM.radiusMeters(distanceFromLaserPortMeters);
    }

    public static double physicalRadiusMeters(double distanceFromLaserPortMeters, LaserTuning tuning) {
        return Objects.requireNonNull(tuning, "tuning")
                .beamRadiusAtPortDistanceMeters(distanceFromLaserPortMeters);
    }

    /**
     * Only for false-colour display, NEVER for measurements or interactions.
     * Real sub-centimetre beam diameters are too small to see at block scale.
     */
    public static double displayedRadiusBlocks(double distanceFromLaserPortMeters) {
        return Math.min(0.16, 0.03 + 9.0 * physicalRadiusMeters(distanceFromLaserPortMeters));
    }

    public static double displayedRadiusBlocks(double distanceFromLaserPortMeters, LaserTuning tuning) {
        return Math.min(0.16, 0.03 + 9.0 * physicalRadiusMeters(distanceFromLaserPortMeters, tuning));
    }
}
