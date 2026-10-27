package dev.coldatomcraft.experiment.world;

import java.util.Objects;

/**
 * Immutable server-captured identity, position and state version of a target.
 *
 * <p>For a block target the coordinates are the block's centre in world units;
 * for an entity they are the captured entity position. The revision can be a
 * block-entity revision or an entity state revision from the server adapter.
 * This type deliberately contains no Minecraft classes.</p>
 */
public record TargetSnapshot(String targetId, TargetKind kind, String dimension,
                             double x, double y, double z, long stateRevision, boolean loaded) {
    public TargetSnapshot {
        if (targetId == null || targetId.isBlank() || kind == null
                || dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("target ID, kind and dimension required");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("target position must be finite");
        }
        if (stateRevision < 0) {
            throw new IllegalArgumentException("target state revision must be non-negative");
        }
    }

    public int chunkX() {
        return Math.floorDiv((int) Math.floor(x), 16);
    }

    public int chunkZ() {
        return Math.floorDiv((int) Math.floor(z), 16);
    }
}
