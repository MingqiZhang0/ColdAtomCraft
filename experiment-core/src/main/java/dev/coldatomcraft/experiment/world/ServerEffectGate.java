package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Last pure-Java fail-closed check before a future server adapter mutates the
 * world. It does not mutate anything and does not replace the adapter's own
 * checks against live {@code ServerLevel}, {@code Entity} and permission state.
 *
 * <p>The check requires both the complete candidate bound by the original
 * precheck and a fresh {@link ServerWorldSnapshot}. It therefore cannot be
 * satisfied by a stale precheck after an intermediate chunk changed.</p>
 *
 * <p>No production code performs a world effect yet. This contract exists so
 * that a precheck cannot silently grow into an authorization to apply one.</p>
 */
public final class ServerEffectGate {
    private ServerEffectGate() { }

    public enum RejectionReason {
        MISSING_INPUT,
        PRECHECK_NOT_ALLOWED,
        PRECHECK_CANDIDATE_MISMATCH,
        NOT_SERVER_THREAD,
        SOURCE_DEVICE_UNRESOLVED,
        TARGET_IDENTITY_UNRESOLVED,
        TARGET_POSITION_STALE,
        TARGET_CHUNK_UNLOADED,
        PERMISSION_DENIED,
        CURRENT_WORLD_NOT_REVALIDATED
    }

    /**
     * Revalidates a candidate against a fresh server snapshot and server facts.
     * The returned authorization is not a world mutation and expires immediately
     * for contract purposes; a future adapter must execute on the same server
     * thread without an intervening uncontrolled await.
     */
    public static Authorization authorize(EnergyTransferCandidate candidate,
                                          WorldEffectGate.Precheck precheck,
                                          ModelDefinition trustedModel,
                                          ServerWorldSnapshot current,
                                          ServerMutationFacts facts) {
        var reasons = new ArrayList<RejectionReason>();
        var worldReasons = new ArrayList<WorldEffectGate.RejectionReason>();
        if (candidate == null || precheck == null || trustedModel == null
                || current == null || facts == null) {
            reasons.add(RejectionReason.MISSING_INPUT);
            return new Authorization(reasons, worldReasons);
        }
        if (!precheck.allowed()) {
            reasons.add(RejectionReason.PRECHECK_NOT_ALLOWED);
        }
        EnergyTransferCandidate boundCandidate = precheck.candidate().orElse(null);
        if (boundCandidate == null || !boundCandidate.equals(candidate)) {
            reasons.add(RejectionReason.PRECHECK_CANDIDATE_MISMATCH);
        }
        if (!facts.executedOnServerThread()) {
            reasons.add(RejectionReason.NOT_SERVER_THREAD);
        }
        if (!facts.sourceDeviceResolved()) {
            reasons.add(RejectionReason.SOURCE_DEVICE_UNRESOLVED);
        }
        if (!facts.targetIdentityResolved()) {
            reasons.add(RejectionReason.TARGET_IDENTITY_UNRESOLVED);
        }
        if (!facts.targetPositionCurrent()) {
            reasons.add(RejectionReason.TARGET_POSITION_STALE);
        }
        if (!facts.targetChunkLoaded()) {
            reasons.add(RejectionReason.TARGET_CHUNK_UNLOADED);
        }
        if (!facts.permissionGranted()) {
            reasons.add(RejectionReason.PERMISSION_DENIED);
        }

        WorldEffectGate.Precheck currentWorld = WorldEffectGate.precheck(candidate, trustedModel,
                current.readSet(), current.sourceDevice(), current.target());
        if (!currentWorld.allowed()) {
            reasons.add(RejectionReason.CURRENT_WORLD_NOT_REVALIDATED);
            worldReasons.addAll(currentWorld.reasons());
        }
        return new Authorization(reasons, worldReasons);
    }

    /** Authorization result with no apply method; the server adapter remains responsible. */
    public static final class Authorization {
        private final List<RejectionReason> reasons;
        private final List<WorldEffectGate.RejectionReason> worldReasons;

        private Authorization(List<RejectionReason> reasons,
                              List<WorldEffectGate.RejectionReason> worldReasons) {
            this.reasons = List.copyOf(reasons);
            this.worldReasons = List.copyOf(worldReasons);
        }

        public boolean allowed() {
            return reasons.isEmpty();
        }

        public List<RejectionReason> reasons() {
            return reasons;
        }

        /**
         * Detailed stale-world reasons when {@link RejectionReason#CURRENT_WORLD_NOT_REVALIDATED}
         * is present. These explain which read-set/device/target comparison failed.
         */
        public List<WorldEffectGate.RejectionReason> worldReasons() {
            return worldReasons;
        }
    }
}
