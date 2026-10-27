package dev.coldatomcraft.experiment;

/**
 * Scientific source provenance, owner approval and engineering verification are
 * independent. This is an administrative record, not a cryptographic
 * authorization capability.
 *
 * <p>Instances belong only in a server-owned {@link ModelCatalog} populated from
 * the owner-controlled bootstrap. Network packets and saved player data must use
 * {@link ModelReference} instead. Java cannot sandbox malicious code already
 * running in the server JVM; this boundary prevents accidental trust of
 * client/save fields, not a hostile in-process plugin.</p>
 */
public record ModelDefinition(String modelId, String revision, String ownerReference,
                              ModelApproval approval, ModelVerification verification) {
    public ModelDefinition {
        if (isBlank(modelId) || isBlank(revision) || isBlank(ownerReference)
                || approval == null || verification == null) {
            throw new IllegalArgumentException("model ID, revision, provenance and states required");
        }
        if (approval == ModelApproval.OWNER_APPROVED &&
                (ownerReference.strip().equalsIgnoreCase("PENDING") || ownerReference.strip().equalsIgnoreCase("TODO"))) {
            throw new IllegalArgumentException("approved model must identify owner source");
        }
        if (approval == ModelApproval.DRAFT && verification != ModelVerification.NOT_IMPLEMENTED) {
            throw new IllegalArgumentException("draft model cannot be claimed as implemented/verified");
        }
    }

    public ModelDefinition(String modelId, String revision, String ownerReference, ModelApproval approval) {
        this(modelId, revision, ownerReference, approval, ModelVerification.NOT_IMPLEMENTED);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean ownerApproved() {
        return approval == ModelApproval.OWNER_APPROVED;
    }

    public boolean numericallyVerified() {
        return verification == ModelVerification.NUMERICALLY_VERIFIED
                || verification == ModelVerification.GAME_VERIFIED;
    }
}
