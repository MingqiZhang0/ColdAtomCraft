package dev.coldatomcraft.experiment.motion;

import java.util.Objects;
import java.util.UUID;

/**
 * A bounded, immutable apparatus identity stored in a block entity, NOT a
 * hardware attestation, digital signature, permission or source of motor power.
 *
 * <p>A clean older world without this key is assigned a fresh ID on migration;
 * a nonempty malformed ID is QUARANTINED, never silently rekeyed. Revision
 * counts controlled optical configuration changes, not physical accuracy.</p>
 */
public final class PersistentApparatusIdentity {
    public enum Origin { PLACED, RESTORED, LEGACY_MIGRATED, QUARANTINED }

    public record Token(UUID uuid, long revision) {
        public Token {
            Objects.requireNonNull(uuid, "apparatus UUID");
            if (revision < 0) throw new IllegalArgumentException("negative configuration revision");
        }
    }

    private final Token token;
    private final String savedId;
    private final long savedRevision;
    private final Origin origin;

    private PersistentApparatusIdentity(Token token, String savedId, long revision, Origin origin) {
        this.token = token;
        this.savedId = savedId;
        this.savedRevision = revision;
        this.origin = origin;
    }

    public static PersistentApparatusIdentity placed() {
        UUID id = UUID.randomUUID();
        return new PersistentApparatusIdentity(new Token(id, 0L), id.toString(), 0L, Origin.PLACED);
    }

    public static PersistentApparatusIdentity restored(String id, long revision) {
        if (id == null) throw new IllegalArgumentException("null saved identifier");
        // Missing key in a D14-D22E world: never claim prior device continuity.
        if (id.isEmpty() && revision == 0L) {
            UUID migrated = UUID.randomUUID();
            return new PersistentApparatusIdentity(new Token(migrated, 0L),
                    migrated.toString(), 0L, Origin.LEGACY_MIGRATED);
        }
        // UUID.fromString accepts some noncanonical strings; exact round-trip
        // prevents alternate text encodings and accidental malformed storage.
        if (id.length() == 36 && revision >= 0) {
            try {
                UUID uuid = UUID.fromString(id);
                if (uuid.toString().equals(id) && uuid.version() == 4) {
                    return new PersistentApparatusIdentity(new Token(uuid, revision),
                            id, revision, Origin.RESTORED);
                }
            } catch (IllegalArgumentException invalid) {
                // Preserve the corrupt bytes as a non-authoritative quarantine.
            }
        }
        return new PersistentApparatusIdentity(null, id, revision, Origin.QUARANTINED);
    }

    public boolean usable() { return token != null; }
    public Origin origin() { return origin; }
    public Token token() {
        if (token == null) throw new IllegalStateException("quarantined apparatus identity");
        return token;
    }
    public String storedId() { return savedId; }
    public long storedRevision() { return savedRevision; }

    /** Returns the next persistent revision or quarantines an exhausted record. */
    public PersistentApparatusIdentity changed() {
        if (token == null) return this;
        if (savedRevision == Long.MAX_VALUE) {
            return new PersistentApparatusIdentity(null, savedId, savedRevision, Origin.QUARANTINED);
        }
        long next = savedRevision + 1L;
        return new PersistentApparatusIdentity(new Token(token.uuid(), next),
                savedId, next, origin);
    }
}
