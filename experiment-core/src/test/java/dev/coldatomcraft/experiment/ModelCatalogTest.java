package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCatalogTest {
    private static final ModelDefinition DRAFT = new ModelDefinition(
            "OPT-001", "A", "PENDING", ModelApproval.DRAFT);
    private static final ModelDefinition APPROVED_IMPLEMENTED = new ModelDefinition(
            "OPT-001", "A", "owner:OPT-001-A",
            ModelApproval.OWNER_APPROVED, ModelVerification.IMPLEMENTED);
    private static final ModelDefinition APPROVED_VERIFIED = new ModelDefinition(
            "OPT-001", "A", "owner:OPT-001-A",
            ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);

    @Test
    void referenceCarriesNoApprovalOrVerificationAuthority() {
        List<String> components = Arrays.stream(ModelReference.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("modelId", "revision"), components);
    }

    @Test
    void publicResolutionApiNeverAcceptsAModelDefinition() {
        assertTrue(Arrays.stream(ModelGate.class.getMethods())
                .filter(method -> method.getName().startsWith("resolve"))
                .allMatch(method -> method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == ModelCatalog.class
                        && method.getParameterTypes()[1] == ModelReference.class));
    }

    @Test
    void resolvesOnlyExactServerCatalogEntries() {
        var catalog = ModelCatalog.fromTrustedServerBootstrap(List.of(APPROVED_VERIFIED));
        assertTrue(catalog.resolve(new ModelReference("OPT-001", "A")).isPresent());
        assertTrue(catalog.resolve(new ModelReference("OPT-001", "B")).isEmpty());
        assertTrue(catalog.resolve(new ModelReference("OPT-999", "A")).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> catalog.requireResolved(new ModelReference("OPT-001", "B")));
    }

    @Test
    void unknownClientReferenceCannotForgeAnApprovedModel() {
        var emptyCatalog = ModelCatalog.fromTrustedServerBootstrap(List.of());
        assertThrows(IllegalStateException.class, () -> ModelGate.resolveOwnerApproved(
                emptyCatalog, new ModelReference("OPT-001", "A")));

        // A save/packet can reference an existing draft, but resolution preserves
        // the server's DRAFT state and the approval gate still fails closed.
        var draftCatalog = ModelCatalog.fromTrustedServerBootstrap(List.of(DRAFT));
        assertThrows(IllegalStateException.class, () -> ModelGate.resolveOwnerApproved(
                draftCatalog, new ModelReference("OPT-001", "A")));
    }

    @Test
    void catalogCopiesInputAndRejectsDuplicateReferences() {
        var source = new ArrayList<ModelDefinition>();
        source.add(APPROVED_VERIFIED);
        var catalog = ModelCatalog.fromTrustedServerBootstrap(source);
        source.clear();
        assertTrue(catalog.resolve(new ModelReference("OPT-001", "A")).isPresent());

        assertThrows(UnsupportedOperationException.class,
                () -> catalog.references().add(new ModelReference("X", "Y")));
        assertThrows(IllegalArgumentException.class, () -> ModelCatalog.fromTrustedServerBootstrap(
                List.of(APPROVED_IMPLEMENTED, APPROVED_VERIFIED)));
    }

    @Test
    void ownerApprovalAndNumericalVerificationRemainIndependent() {
        var catalog = ModelCatalog.fromTrustedServerBootstrap(List.of(APPROVED_IMPLEMENTED));
        var reference = new ModelReference("OPT-001", "A");
        assertEquals(APPROVED_IMPLEMENTED, ModelGate.resolveOwnerApproved(catalog, reference));
        assertFalse(ModelGate.resolveOwnerApproved(catalog, reference).numericallyVerified());
        assertThrows(IllegalStateException.class,
                () -> ModelGate.resolveWorldEffectModel(catalog, reference));
    }
}
