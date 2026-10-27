package dev.coldatomcraft.experiment;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModelGateTest {
    @Test void ownerApprovalAndEngineeringVerificationAreIndependent() {
        ModelDefinition ownerApproved = new ModelDefinition("OPT-001", "A", "owner:OPT-001-A",
                ModelApproval.OWNER_APPROVED);
        assertTrue(ownerApproved.ownerApproved());
        assertFalse(ownerApproved.numericallyVerified());
        assertThrows(IllegalStateException.class, () -> ModelGate.requireNumericallyVerified(ownerApproved));
        ModelDefinition verified = new ModelDefinition("OPT-001", "A", "owner:OPT-001-A",
                ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
        assertDoesNotThrow(() -> ModelGate.requireNumericallyVerified(verified));
    }

    @Test void serverCatalogResolutionProducesOnlyTheRecordedApprovalState() {
        var verified = new ModelDefinition("OPT-001", "A", "owner:OPT-001-A",
                ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
        var catalog = ModelCatalog.fromTrustedServerBootstrap(List.of(verified));
        var reference = new ModelReference("OPT-001", "A");
        assertEquals(verified, ModelGate.resolveWorldEffectModel(catalog, reference));
        assertThrows(IllegalStateException.class, () -> ModelGate.resolveWorldEffectModel(
                catalog, new ModelReference("OPT-001", "B")));
    }

    @Test void draftCannotClaimVerifiedAndApprovalNeedsTraceableReference() {
        assertThrows(IllegalArgumentException.class, () -> new ModelDefinition("OPT-001", "A", "PENDING",
                ModelApproval.OWNER_APPROVED));
        assertThrows(IllegalArgumentException.class, () -> new ModelDefinition("OPT-001", "A", "PENDING",
                ModelApproval.DRAFT, ModelVerification.GAME_VERIFIED));
        assertThrows(IllegalStateException.class, () -> ModelGate.requireOwnerApproved(
                new ModelDefinition("OPT-001", "A", "PENDING", ModelApproval.DRAFT)));
    }
}
