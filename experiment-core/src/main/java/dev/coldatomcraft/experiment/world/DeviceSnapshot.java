package dev.coldatomcraft.experiment.world;

/**
 * Immutable server-captured identity and state version of the source device.
 *
 * <p>This is a data carrier only. Construction by a caller does not prove that
 * the device exists in a Minecraft level; a server adapter must capture it from
 * the authoritative block entity on the server thread.</p>
 */
public record DeviceSnapshot(String dimension, int blockX, int blockY, int blockZ,
                             String deviceId, long stateRevision, boolean loaded) {
    public DeviceSnapshot {
        if (dimension == null || dimension.isBlank() || deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("device dimension and ID required");
        }
        if (stateRevision < 0) {
            throw new IllegalArgumentException("device state revision must be non-negative");
        }
    }

    public int chunkX() {
        return Math.floorDiv(blockX, 16);
    }

    public int chunkZ() {
        return Math.floorDiv(blockZ, 16);
    }
}
