package dev.coldatomcraft.experiment.world;

import java.util.Set;

/**
 * Immutable request to reserve ALL apparatus positions over the same
 * server-wide scheduling window. The scheduler uses an atomic all-or-none
 * allocation: an experiment cannot reserve only a fraction of its apparatus.
 *
 * This record is NOT a capability/security proof. Only a future trusted
 * server adapter may construct requests from live world state; clients must
 * never be allowed to choose reservation windows or apparatus keys as authority.
 */
public record ExperimentReservation(Set<ApparatusKey> apparatus,
                                    ServerTickWindow window) {
    public ExperimentReservation {
        if (apparatus == null || apparatus.isEmpty() || apparatus.stream().anyMatch(item -> item == null)
                || window == null) {
            throw new IllegalArgumentException("nonempty apparatus and server window required");
        }
        apparatus = Set.copyOf(apparatus);
    }

    public static ExperimentReservation single(ApparatusKey apparatus, ServerTickWindow window) {
        if (apparatus == null) throw new IllegalArgumentException("apparatus required");
        return new ExperimentReservation(Set.of(apparatus), window);
    }

    public boolean includes(DeviceSnapshot device) {
        return device != null && apparatus.contains(ApparatusKey.of(device));
    }
}
