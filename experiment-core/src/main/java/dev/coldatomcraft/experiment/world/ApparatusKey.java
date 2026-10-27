package dev.coldatomcraft.experiment.world;

/**
 * Physical location of an apparatus, independent of its model/device ID.
 * A block replacement at the same position MUST NOT bypass a reservation.
 * The dimension key and block coordinates must be obtained from trusted
 * ServerLevel state by the future NeoForge adapter, not a client packet.
 */
public record ApparatusKey(String dimension, int blockX, int blockY, int blockZ) {
    public ApparatusKey {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("apparatus dimension required");
        }
    }

    public static ApparatusKey of(DeviceSnapshot device) {
        if (device == null) throw new IllegalArgumentException("device required");
        return new ApparatusKey(device.dimension(), device.blockX(),
                device.blockY(), device.blockZ());
    }
}
