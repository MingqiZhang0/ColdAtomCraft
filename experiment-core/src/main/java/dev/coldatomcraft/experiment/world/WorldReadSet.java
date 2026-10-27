package dev.coldatomcraft.experiment.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Immutable, canonical version stamp of ALL chunks used by a world calculation.
 *
 * <p>An optical path may cross multiple chunks: checking only the target chunk
 * would allow a changed intermediate mirror or shutter to invalidate the result.
 * Every entry must be from one dimension, must be loaded, and must be unique.</p>
 *
 * <p>This is a snapshot data carrier only. It does not subscribe to NeoForge
 * events and does not prove that all relevant world changes are tracked.</p>
 */
public record WorldReadSet(List<WorldRevision> chunks) {
    private static final Comparator<WorldRevision> ORDER = Comparator
            .comparing(WorldRevision::dimension)
            .thenComparingInt(WorldRevision::chunkX)
            .thenComparingInt(WorldRevision::chunkZ);

    public WorldReadSet {
        if (chunks == null || chunks.isEmpty() || chunks.stream().anyMatch(rev -> rev == null)) {
            throw new IllegalArgumentException("nonempty, nonnull world read set required");
        }
        var ordered = new ArrayList<>(chunks);
        ordered.sort(ORDER);
        var keys = new HashSet<String>();
        String dimension = ordered.getFirst().dimension();
        for (WorldRevision rev : ordered) {
            if (!rev.loaded()) {
                throw new IllegalArgumentException("read set may contain only loaded chunks");
            }
            if (!dimension.equals(rev.dimension())) {
                throw new IllegalArgumentException("read set must use one dimension");
            }
            String key = rev.dimension() + "\0" + rev.chunkX() + ":" + rev.chunkZ();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("duplicate chunk revision in snapshot");
            }
        }
        chunks = List.copyOf(ordered);
    }

    public static WorldReadSet of(WorldRevision singleChunk) {
        if (singleChunk == null) throw new IllegalArgumentException("chunk snapshot required");
        return new WorldReadSet(List.of(singleChunk));
    }

    /** Returns true only for an exact dimension/chunk match in this loaded snapshot. */
    public boolean containsChunk(String dimension, int chunkX, int chunkZ) {
        if (dimension == null) return false;
        return chunks.stream().anyMatch(rev -> rev.dimension().equals(dimension)
                && rev.chunkX() == chunkX && rev.chunkZ() == chunkZ);
    }
}
