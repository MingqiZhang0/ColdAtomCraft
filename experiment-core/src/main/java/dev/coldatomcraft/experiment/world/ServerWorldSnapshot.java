package dev.coldatomcraft.experiment.world;

/**
 * Server-side snapshot used for final revalidation immediately before a future
 * world mutation.
 *
 * <p>The fields must be captured from the authoritative server world at
 * submission time, on the server thread. This record is a data carrier: its
 * constructor cannot prove provenance. A future NeoForge adapter is responsible
 * for resolving the live block entity/entity and constructing this snapshot; no
 * such adapter exists in M0-R2.1.</p>
 */
public record ServerWorldSnapshot(WorldReadSet readSet,
                                  DeviceSnapshot sourceDevice,
                                  TargetSnapshot target) {
    public ServerWorldSnapshot {
        if (readSet == null || sourceDevice == null || target == null) {
            throw new IllegalArgumentException("complete server world snapshot required");
        }
    }
}
