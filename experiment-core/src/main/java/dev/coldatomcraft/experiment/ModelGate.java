package dev.coldatomcraft.experiment;

/**
 * Fail-closed boundary for model-driven actions. Only invoke on the server.
 *
 * <p>A {@link ModelDefinition} is an administrative record, not a capability.
 * Network and save data must use {@link ModelReference} and be resolved through
 * the server-owned {@link ModelCatalog}; callers must never deserialize a model
 * definition or an approval flag from client-controlled data.</p>
 */
public final class ModelGate {
    private ModelGate() { }

    /** Resolves a reference against the trusted catalog and requires owner approval. */
    public static ModelDefinition resolveOwnerApproved(ModelCatalog catalog, ModelReference reference) {
        if (catalog == null) {
            throw new IllegalStateException("server-owned model catalog required");
        }
        ModelDefinition definition = catalog.requireResolved(reference);
        requireOwnerApproved(definition);
        return definition;
    }

    /** Resolves and requires both owner approval and engineering numerical verification. */
    public static ModelDefinition resolveWorldEffectModel(ModelCatalog catalog, ModelReference reference) {
        ModelDefinition definition = resolveOwnerApproved(catalog, reference);
        if (!definition.numericallyVerified()) {
            throw new IllegalStateException("numerical verification evidence required");
        }
        return definition;
    }

    public static void requireOwnerApproved(ModelDefinition definition) {
        if (definition == null || !definition.ownerApproved()) {
            throw new IllegalStateException("owner-approved scientific model required");
        }
    }

    public static void requireNumericallyVerified(ModelDefinition definition) {
        requireOwnerApproved(definition);
        if (!definition.numericallyVerified()) {
            throw new IllegalStateException("numerical verification evidence required");
        }
    }
}
