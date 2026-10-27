package dev.coldatomcraft.experiment.world;

/**
 * Identifies the immutable world/chunk snapshot used to compute an effect.
 *
 * <p>{@code loadEpoch} distinguishes a reload from the previous occupancy of the
 * same chunk; {@code contentRevision} identifies observed content changes. The
 * server adapter is responsible for incrementing both. These fields are only as
 * strong as that adapter: <strong>no NeoForge world-change tracker is implemented
 * in M0-R2</strong>, so equality alone is not proof that the world is unchanged.</p>
 *
 * <p>{@code loaded} is captured with the rest of the snapshot. A read set may
 * contain only loaded chunks. An unloaded chunk cannot be sampled and must cause
 * a calculation result to be treated as stale.</p>
 */
public record WorldRevision(String dimension, int chunkX, int chunkZ,
                            long loadEpoch, long contentRevision, boolean loaded) {
    public WorldRevision {
        if (dimension == null || dimension.isBlank() || loadEpoch < 0 || contentRevision < 0) {
            throw new IllegalArgumentException("invalid world revision identity");
        }
    }
}
