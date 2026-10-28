package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Formal JUnit5 source: also compiled separately by the offline compatibility gate. */
final class PersistentApparatusIdentityTest {
    @Test void newPlacementHasDistinctRandomUuid() {
        var a = PersistentApparatusIdentity.placed();
        var b = PersistentApparatusIdentity.placed();
        assertTrue(a.usable());
        assertEquals(4, a.token().uuid().version());
        assertEquals(0L, a.token().revision());
        assertFalse(a.token().uuid().equals(b.token().uuid()));
    }
    @Test void saveRestoreRetainsExactIdentityAndRevision() {
        var placed = PersistentApparatusIdentity.placed().changed().changed();
        var loaded = PersistentApparatusIdentity.restored(placed.storedId(), placed.storedRevision());
        assertEquals(placed.token(), loaded.token());
        assertEquals(PersistentApparatusIdentity.Origin.RESTORED, loaded.origin());
    }
    @Test void oldWorldWithoutIdGetsNewUntrustedLegacyLineage() {
        var migrated = PersistentApparatusIdentity.restored("", 0L);
        assertTrue(migrated.usable());
        assertEquals(PersistentApparatusIdentity.Origin.LEGACY_MIGRATED, migrated.origin());
        assertEquals(0L, migrated.token().revision());
    }
    @Test void malformedIdentifierIsQuarantinedNotRegenerated() {
        var bad = PersistentApparatusIdentity.restored("broken-data", 12L);
        assertFalse(bad.usable());
        assertEquals("broken-data", bad.storedId());
        assertEquals(12L, bad.storedRevision());
        assertEquals(bad, bad.changed());
        assertThrows(IllegalStateException.class, bad::token);
    }
    @Test void uppercaseUuidIsRejectedRatherThanNormalized() {
        var valid = PersistentApparatusIdentity.placed().storedId();
        var bad = PersistentApparatusIdentity.restored(valid.toUpperCase(java.util.Locale.ROOT), 0);
        assertFalse(bad.usable());
    }
    @Test void nonV4UuidIsRejected() {
        assertFalse(PersistentApparatusIdentity.restored(
                "00000000-0000-1000-8000-000000000001", 0).usable());
    }
    @Test void missingIdWithNonzeroRevisionIsQuarantined() {
        assertFalse(PersistentApparatusIdentity.restored("", 1).usable());
    }
    @Test void negativeRevisionIsQuarantined() {
        assertFalse(PersistentApparatusIdentity.restored(UUID.randomUUID().toString(), -1).usable());
    }
    @Test void configurationEditPreservesIdAndIncrementsRevision() {
        var first = PersistentApparatusIdentity.placed();
        var edited = first.changed();
        assertEquals(first.token().uuid(), edited.token().uuid());
        assertEquals(1L, edited.token().revision());
        assertEquals(0L, first.token().revision());
    }
    @Test void revisionOverflowFailsClosed() {
        var value = PersistentApparatusIdentity.restored(PersistentApparatusIdentity.placed().storedId(),
                Long.MAX_VALUE);
        assertFalse(value.changed().usable());
    }
    @Test void tokenRejectsNegativeRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersistentApparatusIdentity.Token(UUID.randomUUID(), -1));
    }
    @Test void nullStoredIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PersistentApparatusIdentity.restored(null, 0));
    }
    @Test void newPersistedBindingNeedsBothEntityAndToken() {
        var state = new State(Kind.MIRROR, Axis.SOUTH, 2, false, null,
                Binding.PERSISTENT_BLOCK_ENTITY);
        assertThrows(IllegalArgumentException.class, () -> new Observed(state, new Object()));
        assertThrows(IllegalArgumentException.class,
                () -> new Observed(state, null, PersistentApparatusIdentity.placed().token()));
        assertTrue(new Observed(state, new Object(), PersistentApparatusIdentity.placed().token())
                .persistentToken() != null);
    }

    private static final class ReaderWorld implements Reader {
        final Object world = new Object();
        final Map<LaboratoryMountFrame.BlockAddress, Observed> cells = new HashMap<>();
        @Override public Object serverWorldIdentity() { return world; }
        @Override public String dimensionId() { return "minecraft:overworld"; }
        @Override public long gameTick() { return 50; }
        @Override public boolean serverThread() { return true; }
        @Override public boolean isLoaded(LaboratoryMountFrame.BlockAddress pos) { return cells.containsKey(pos); }
        @Override public Observed readLoaded(LaboratoryMountFrame.BlockAddress pos) { return cells.get(pos); }
    }
    private static Observed snapshotMeter(PersistentApparatusIdentity id, Object be) {
        return new Observed(new State(Kind.METER, Axis.NORTH, 1, false, null,
                Binding.PERSISTENT_BLOCK_ENTITY), be, id.token());
    }
    @Test void newUuidAtSamePositionRejectsOldSnapshot() {
        var p = new LaboratoryMountFrame.BlockAddress(10, 80, 2);
        var w = new ReaderWorld();
        var old = PersistentApparatusIdentity.placed();
        var be = new Object();
        w.cells.put(p, snapshotMeter(old, be));
        var snap = LoadedApparatusSnapshot.capture(w,
                LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld", p), List.of(p));
        assertEquals(Check.VALID, snap.verify(w));
        w.cells.put(p, snapshotMeter(PersistentApparatusIdentity.placed(), new Object()));
        assertEquals(Check.PERSISTENT_ID_CHANGED, snap.verify(w));
    }
    @Test void aRevisionChangeRejectsOldSnapshot() {
        var p = new LaboratoryMountFrame.BlockAddress(10, 80, 2);
        var w = new ReaderWorld();
        var identity = PersistentApparatusIdentity.placed();
        Object sameBe = new Object();
        w.cells.put(p, snapshotMeter(identity, sameBe));
        var snap = LoadedApparatusSnapshot.capture(w,
                LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld", p), List.of(p));
        w.cells.put(p, snapshotMeter(identity.changed(), sameBe));
        assertEquals(Check.PERSISTENT_ID_CHANGED, snap.verify(w));
    }
    @Test void aDifferentBeWithCopiedUuidRejectsOldSnapshot() {
        var p = new LaboratoryMountFrame.BlockAddress(10, 80, 2);
        var w = new ReaderWorld();
        var identity = PersistentApparatusIdentity.placed();
        w.cells.put(p, snapshotMeter(identity, new Object()));
        var snap = LoadedApparatusSnapshot.capture(w,
                LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld", p), List.of(p));
        w.cells.put(p, snapshotMeter(identity, new Object()));
        assertEquals(Check.BLOCK_ENTITY_REPLACED, snap.verify(w));
    }
    @Test void repeatedUuidWithinOneRigIsRejected() {
        var p = new LaboratoryMountFrame.BlockAddress(10, 80, 2);
        var q = new LaboratoryMountFrame.BlockAddress(11, 80, 2);
        var w = new ReaderWorld();
        var identity = PersistentApparatusIdentity.placed();
        w.cells.put(p, snapshotMeter(identity, new Object()));
        w.cells.put(q, snapshotMeter(identity, new Object()));
        var frame = LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld", p);
        assertThrows(IllegalStateException.class,
                () -> LoadedApparatusSnapshot.capture(w, frame, List.of(p,q)));
    }
    @Test void durableReferenceStaysStableAcrossFreshSnapshots() {
        var p = new LaboratoryMountFrame.BlockAddress(10, 80, 2);
        var w = new ReaderWorld();
        w.cells.put(p, snapshotMeter(PersistentApparatusIdentity.placed(), new Object()));
        var frame = LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld", p);
        var first = LoadedApparatusSnapshot.capture(w, frame, List.of(p));
        var second = LoadedApparatusSnapshot.capture(w, frame, List.of(p));
        assertEquals(first.persistentReferences(), second.persistentReferences());
        assertEquals(1, first.persistentReferences().size());
    }
}
