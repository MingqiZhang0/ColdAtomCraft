package dev.coldatomcraft.experiment;

/**
 * The only model identifier that may cross a network or save boundary.
 *
 * <p>It carries no approval, verification, equations, or source reference. A
 * client can ask the server to resolve a reference; it cannot assert that the
 * referenced model is owner-approved. The server must resolve the reference
 * against its own {@link ModelCatalog}.</p>
 */
public record ModelReference(String modelId, String revision) {
    public ModelReference {
        if (modelId == null || modelId.isBlank() || revision == null || revision.isBlank()) {
            throw new IllegalArgumentException("model ID and revision required");
        }
    }
}
