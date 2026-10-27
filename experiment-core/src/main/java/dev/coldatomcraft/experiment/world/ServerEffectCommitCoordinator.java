package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelCatalog;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelReference;

/**
 * Pure-Java server submission contract for the first three stages of a world
 * effect:
 *
 * <ol>
 *   <li>accept a candidate prepared by the calculation layer;</li>
 *   <li>revalidate the precheck, model, read set, device, target, server thread
 *       and permission facts against a fresh server snapshot;</li>
 *   <li>reserve settlement eligibility in the ledger.</li>
 * </ol>
 *
 * <p>It deliberately does not implement stage four, the world mutation. A
 * successful {@link Decision#SETTLEMENT_CLAIMED} only means the event was
 * reserved once; it does <strong>not</strong> mean the world was changed or that
 * the effect was applied. The real NeoForge adapter is
 * <strong>NOT IMPLEMENTED</strong>.</p>
 *
 * <p>The coordinator has no Minecraft dependency. Callers must not pass a
 * {@link ServerWorldSnapshot} assembled from client data; the future adapter
 * owns that trust boundary.</p>
 */
public final class ServerEffectCommitCoordinator {
    public enum Decision {
        /** Ledger claim succeeded. The caller may attempt one effect on the server thread. */
        SETTLEMENT_CLAIMED,
        INVALID,
        PRECHECK_NOT_ALLOWED,
        PRECHECK_CANDIDATE_MISMATCH,
        MODEL_NOT_TRUSTED,
        SERVER_REVALIDATION_FAILED,
        RUN_ABANDONED,
        LEDGER_REJECTED
    }

    private final WorldEffectLedger ledger;
    private final ModelCatalog modelCatalog;

    public ServerEffectCommitCoordinator(WorldEffectLedger ledger,
                                         ModelCatalog modelCatalog) {
        if (ledger == null || modelCatalog == null) {
            throw new IllegalArgumentException("ledger and trusted model catalog required");
        }
        this.ledger = ledger;
        this.modelCatalog = modelCatalog;
    }

    /**
     * Runs the pure server revalidation and, only if every check passes, claims
     * the event. No effect is executed by this method.
     */
    public Decision submit(EnergyTransferCandidate candidate,
                           WorldEffectGate.Precheck precheck,
                           ServerWorldSnapshot current,
                           ServerMutationFacts facts) {
        if (candidate == null || precheck == null || current == null || facts == null) {
            return Decision.INVALID;
        }
        if (!precheck.allowed()) {
            return Decision.PRECHECK_NOT_ALLOWED;
        }
        EnergyTransferCandidate boundCandidate = precheck.candidate().orElse(null);
        if (boundCandidate == null || !boundCandidate.equals(candidate)) {
            return Decision.PRECHECK_CANDIDATE_MISMATCH;
        }

        ModelReference reference = new ModelReference(candidate.modelId(), candidate.modelRevision());
        ModelDefinition model = modelCatalog.resolve(reference).orElse(null);
        if (model == null
                || !model.modelId().equals(candidate.modelId())
                || !model.revision().equals(candidate.modelRevision())
                || !model.ownerApproved()
                || !model.numericallyVerified()) {
            return Decision.MODEL_NOT_TRUSTED;
        }

        ServerEffectGate.Authorization authorization =
                ServerEffectGate.authorize(candidate, precheck, model, current, facts);
        if (!authorization.allowed()) {
            if (authorization.reasons().contains(ServerEffectGate.RejectionReason.PRECHECK_NOT_ALLOWED)) {
                return Decision.PRECHECK_NOT_ALLOWED;
            }
            if (authorization.reasons().contains(
                    ServerEffectGate.RejectionReason.PRECHECK_CANDIDATE_MISMATCH)) {
                return Decision.PRECHECK_CANDIDATE_MISMATCH;
            }
            return Decision.SERVER_REVALIDATION_FAILED;
        }

        WorldEffectLedger.ClaimResult claim = ledger.claim(candidate, precheck);
        if (claim == WorldEffectLedger.ClaimResult.CLAIMED) {
            return Decision.SETTLEMENT_CLAIMED;
        }
        if (claim == WorldEffectLedger.ClaimResult.RUN_ABANDONED) {
            return Decision.RUN_ABANDONED;
        }
        return Decision.LEDGER_REJECTED;
    }
}
