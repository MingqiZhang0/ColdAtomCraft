package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fail-closed <strong>precheck</strong> for a world-effect candidate.
 *
 * <p>This class deliberately has no apply/mutate method. Even an allowed
 * precheck is not permission to modify a Minecraft level. The server adapter
 * must still resolve the target and source device from authoritative server
 * state, verify permissions, and check that the chunks are still loaded
 * immediately before mutation (see {@link ServerEffectGate}).</p>
 *
 * <p>The revision data is not a complete world-change tracker. Until a real
 * NeoForge adapter maintains load epochs and content revisions for every
 * dependency, callers must not treat a matching read set as proof of world
 * validity.</p>
 */
public final class WorldEffectGate {
    private WorldEffectGate() { }

    public enum RejectionReason {
        MISSING_INPUT,
        MODEL_NOT_OWNER_APPROVED,
        MODEL_NOT_NUMERICALLY_VERIFIED,
        MODEL_MISMATCH,
        READ_SET_MISMATCH,
        DEVICE_SNAPSHOT_MISMATCH,
        TARGET_SNAPSHOT_MISMATCH,
        DEVICE_UNLOADED,
        TARGET_UNLOADED
    }

    public static Precheck precheck(EnergyTransferCandidate candidate,
                                    ModelDefinition model,
                                    WorldReadSet currentReadSet,
                                    DeviceSnapshot currentDevice,
                                    TargetSnapshot currentTarget) {
        var reasons = new ArrayList<RejectionReason>();
        if (candidate == null || model == null || currentReadSet == null
                || currentDevice == null || currentTarget == null) {
            reasons.add(RejectionReason.MISSING_INPUT);
            return new Precheck(candidate, reasons);
        }
        if (!model.ownerApproved()) {
            reasons.add(RejectionReason.MODEL_NOT_OWNER_APPROVED);
        }
        if (!model.numericallyVerified()) {
            reasons.add(RejectionReason.MODEL_NOT_NUMERICALLY_VERIFIED);
        }
        if (!model.modelId().equals(candidate.modelId())
                || !model.revision().equals(candidate.modelRevision())) {
            reasons.add(RejectionReason.MODEL_MISMATCH);
        }
        if (!currentReadSet.equals(candidate.readSet())) {
            reasons.add(RejectionReason.READ_SET_MISMATCH);
        }
        if (!currentDevice.loaded()) {
            reasons.add(RejectionReason.DEVICE_UNLOADED);
        }
        if (!currentTarget.loaded()) {
            reasons.add(RejectionReason.TARGET_UNLOADED);
        }
        if (!currentDevice.equals(candidate.sourceDevice())) {
            reasons.add(RejectionReason.DEVICE_SNAPSHOT_MISMATCH);
        }
        if (!currentTarget.equals(candidate.target())) {
            reasons.add(RejectionReason.TARGET_SNAPSHOT_MISMATCH);
        }
        return new Precheck(candidate, reasons);
    }

    /**
     * Result of the pure precheck bound to the complete immutable candidate that
     * was checked. Construction is private so callers cannot fabricate an allowed
     * result; obtain one from {@link #precheck}.
     *
     * <p>Binding the whole candidate, rather than only its stable event key, is
     * essential: the event key deliberately excludes energy, target snapshots,
     * read set and the full calculation interval. A caller that edits any of
     * those fields cannot reuse this result.</p>
     */
    public static final class Precheck {
        private final EnergyTransferCandidate candidate;
        private final TransferEventKey candidateKey;
        private final List<RejectionReason> reasons;

        private Precheck(EnergyTransferCandidate candidate, List<RejectionReason> reasons) {
            this.candidate = candidate;
            this.candidateKey = candidate == null ? null : candidate.eventKey();
            this.reasons = List.copyOf(reasons);
        }

        public boolean allowed() {
            return reasons.isEmpty();
        }

        public TransferEventKey candidateKey() {
            return candidateKey;
        }

        /**
         * The complete candidate this check was performed on. Empty only when no
         * candidate was supplied. All nested value types are immutable records and
         * {@link WorldReadSet} defensively copies its list.
         */
        public Optional<EnergyTransferCandidate> candidate() {
            return Optional.ofNullable(candidate);
        }

        public List<RejectionReason> reasons() {
            return reasons;
        }

        public void requireAllowed() {
            if (!allowed()) {
                throw new IllegalStateException("world-effect precheck rejected: " + reasons);
            }
        }
    }
}
