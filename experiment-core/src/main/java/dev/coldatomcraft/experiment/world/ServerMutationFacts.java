package dev.coldatomcraft.experiment.world;

/**
 * Server-owned facts required immediately before a world mutation.
 *
 * <p>Every value must be obtained from the authoritative server state on the
 * server thread at mutation time. In particular, none of these facts may be
 * deserialized from a client packet, a player save, or a candidate object. This
 * currentServerTick uses the same trusted server-wide scheduling clock as
 * ServerTickWindow, NOT solver simulation nanoseconds. The value is supplied
 * by the future trusted server adapter; this record cannot prove its source.
 * This record is an integration contract; the NeoForge adapter that captures the
 * facts is <strong>not implemented in M0-R2.1</strong>.</p>
 */
public record ServerMutationFacts(
        boolean executedOnServerThread,
        boolean sourceDeviceResolved,
        boolean targetIdentityResolved,
        boolean targetPositionCurrent,
        boolean targetChunkLoaded,
        boolean permissionGranted,
        long currentServerTick) {
    public ServerMutationFacts {
        if (currentServerTick < 0) {
            throw new IllegalArgumentException("trusted current server tick must be nonnegative");
        }
    }
}
