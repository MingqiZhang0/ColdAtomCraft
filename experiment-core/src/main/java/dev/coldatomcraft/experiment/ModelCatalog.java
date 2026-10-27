package dev.coldatomcraft.experiment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable server-owned model catalog.
 *
 * <p>Only the server bootstrap may construct this catalog from authoritative
 * owner acceptance records. Network packets, player saves and arbitrary runtime
 * requests must carry a {@link ModelReference}, never a {@link ModelDefinition}.
 * Unknown references fail closed.</p>
 *
 * <p>This is an architectural boundary, not a cryptographic signature system or
 * a sandbox against malicious code already running in the server JVM. No
 * approved scientific model is shipped in M0-R2.</p>
 */
public final class ModelCatalog {
    private final Map<ModelReference, ModelDefinition> definitions;

    private ModelCatalog(Map<ModelReference, ModelDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    /**
     * Builds the catalog at server startup from owner-controlled, server-side
     * configuration. The collection is copied; later caller mutation is ignored.
     */
    public static ModelCatalog fromTrustedServerBootstrap(Collection<ModelDefinition> source) {
        if (source == null) {
            throw new IllegalArgumentException("model definitions required");
        }
        var byReference = new LinkedHashMap<ModelReference, ModelDefinition>();
        for (ModelDefinition definition : source) {
            if (definition == null) {
                throw new IllegalArgumentException("null model definition");
            }
            var reference = new ModelReference(definition.modelId(), definition.revision());
            if (byReference.putIfAbsent(reference, definition) != null) {
                throw new IllegalArgumentException("duplicate model definition: " + reference);
            }
        }
        return new ModelCatalog(byReference);
    }

    public Optional<ModelDefinition> resolve(ModelReference reference) {
        if (reference == null) return Optional.empty();
        return Optional.ofNullable(definitions.get(reference));
    }

    public ModelDefinition requireResolved(ModelReference reference) {
        return resolve(reference).orElseThrow(() ->
                new IllegalStateException("model not present in server-owned catalog: " + reference));
    }

    public Set<ModelReference> references() {
        return definitions.keySet();
    }
}
