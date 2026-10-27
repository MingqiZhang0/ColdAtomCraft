package dev.coldatomcraft.experiment;

import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Explicit, fail-closed DEV fixture for one straight, air-only optical path.
 * The authoritative game adapter supplies each cell without loading chunks.
 * No blocks are changed, and no optical material coefficients are invented.
 * Scanner behavior remains independently testable without Minecraft.
 */
public final class PreviewBeamRoute {
    public enum Cell {
        AIR, EMITTER_READY, EMITTER_OFF, EMITTER_WRONG_FACING, OBSTACLE, UNLOADED
    }
    public enum Status {
        MEASURED, NO_SOURCE, BLOCKED, UNLOADED, WRONG_FACING, SHUTTER_CLOSED
    }

    public record Result(Status status, double powerWatts, int separationBlocks) {
        public Result {
            Objects.requireNonNull(status, "status");
            if (!Double.isFinite(powerWatts) || powerWatts < 0
                    || separationBlocks < 0 || separationBlocks > PreviewOpticalBench.MAX_SEPARATION_BLOCKS) {
                throw new IllegalArgumentException("invalid preview scan result");
            }
            if (status != Status.MEASURED && powerWatts != 0.0) {
                throw new IllegalArgumentException("non-measurement cannot report power");
            }
            if ((status == Status.NO_SOURCE) != (separationBlocks == 0)) {
                throw new IllegalArgumentException("NO_SOURCE must have distance 0; other outcomes need distance");
            }
        }
    }

    private PreviewBeamRoute() { }

    /**
     * Cells are read from receiver outward towards its front face, distances
     * 1..24. The first non-air cell terminates the scan; a source behind an
     * obstruction is deliberately not discovered or used. No forced load.
     */
    public static Result scan(int aperturePreset, IntFunction<Cell> cellAtDistance) {
        PreviewOpticalBench.apertureRadiusMeters(aperturePreset);
        Objects.requireNonNull(cellAtDistance, "cellAtDistance");
        for (int d = 1; d <= PreviewOpticalBench.MAX_SEPARATION_BLOCKS; d++) {
            Cell cell = Objects.requireNonNull(cellAtDistance.apply(d), "unknown cell state");
            switch (cell) {
                case AIR -> { continue; }
                case EMITTER_READY -> {
                    var reading = PreviewOpticalBench.sampleAligned(d, aperturePreset);
                    return new Result(Status.MEASURED, reading.observation().value(), d);
                }
                case EMITTER_OFF -> { return new Result(Status.SHUTTER_CLOSED, 0, d); }
                case EMITTER_WRONG_FACING -> { return new Result(Status.WRONG_FACING, 0, d); }
                case OBSTACLE -> { return new Result(Status.BLOCKED, 0, d); }
                case UNLOADED -> { return new Result(Status.UNLOADED, 0, d); }
            }
        }
        return new Result(Status.NO_SOURCE, 0, 0);
    }
}
