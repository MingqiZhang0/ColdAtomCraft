package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.motion.PersistentApparatusIdentity;
import dev.coldatomcraft.experiment.motion.LaboratoryMountFrame;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** D22F deterministic reference simulation; not a live NeoForge server. */
public final class PersistentIdentitySmoke {
    private static long assertions;
    private static void check(boolean result, String why) {
        assertions++;
        if (!result) throw new AssertionError(why + " at assertion " + assertions);
    }
    private static void reject(Runnable task, String why) {
        boolean failed = false;
        try { task.run(); } catch (IllegalArgumentException | IllegalStateException e) { failed = true; }
        check(failed, why);
    }
    private static LaboratoryMountFrame.BlockAddress at(long x, long y, long z) {
        return new LaboratoryMountFrame.BlockAddress(x, y, z);
    }
    private static final class World implements Reader {
        final Object world = new Object();
        final Map<LaboratoryMountFrame.BlockAddress, Observed> blocks = new HashMap<>();
        String dimension = "minecraft:overworld";
        long tick = 33L;
        @Override public Object serverWorldIdentity() { return world; }
        @Override public String dimensionId() { return dimension; }
        @Override public long gameTick() { return tick; }
        @Override public boolean serverThread() { return true; }
        @Override public boolean isLoaded(LaboratoryMountFrame.BlockAddress pos) {
            return blocks.containsKey(pos);
        }
        @Override public Observed readLoaded(LaboratoryMountFrame.BlockAddress pos) {
            return blocks.get(pos);
        }
    }
    private static Observed meter(PersistentApparatusIdentity identity, Object be) {
        return new Observed(new State(Kind.METER, Axis.NORTH, 1, false, null,
                Binding.PERSISTENT_BLOCK_ENTITY), be, identity.token());
    }

    public static void main(String[] args) {
        Random rng = new Random(0x22F2026L);
        // Large independent randomized persistence/revision checks.
        for (int run = 0; run < 35_000; run++) {
            PersistentApparatusIdentity placed = PersistentApparatusIdentity.placed();
            check(placed.usable(), "fresh apparatus usable");
            check(placed.origin() == PersistentApparatusIdentity.Origin.PLACED, "fresh provenance");
            check(placed.token().uuid().version() == 4, "random v4 device identity");
            check(placed.token().revision() == 0, "fresh revision zero");
            long increment = rng.nextInt(6);
            PersistentApparatusIdentity current = placed;
            for (int k = 0; k < increment; k++) current = current.changed();
            var restored = PersistentApparatusIdentity.restored(current.storedId(), current.storedRevision());
            check(restored.token().equals(current.token()), "save load is stable");
            check(restored.origin() == PersistentApparatusIdentity.Origin.RESTORED, "load provenance");
            check(!PersistentApparatusIdentity.placed().token().uuid().equals(current.token().uuid()),
                    "new placement is a different physical apparatus");
            check(PersistentApparatusIdentity.restored("", 0).origin()
                    == PersistentApparatusIdentity.Origin.LEGACY_MIGRATED, "legacy rekey");
            var corrupt = PersistentApparatusIdentity.restored("corrupt", increment);
            check(!corrupt.usable(), "corrupt saved token quarantined");
            check(corrupt.changed() == corrupt, "quarantined record never rekeys on changes");
            check(corrupt.storedId().equals("corrupt"), "preserve malformed source for audit");
            check(!PersistentApparatusIdentity.restored("", 12).usable(), "missing ID with revision");
        }
        var overflow = PersistentApparatusIdentity.restored(UUID.randomUUID().toString(), Long.MAX_VALUE);
        check(overflow.usable() && !overflow.changed().usable(), "revision overflow fails closed");
        check(!PersistentApparatusIdentity.restored("AAAAAAAA-AAAA-4AAA-AAAA-AAAAAAAAAAAA", 0).usable(),
                "uppercase not silently normalized");
        check(!PersistentApparatusIdentity.restored("00000000-0000-0000-0000-000000000001", 0).usable(),
                "non-v4 UUID rejected");
        reject(() -> PersistentApparatusIdentity.restored(null, 0), "null saved key");

        var position = at(29_999_985, 93, -29_999_985);
        var frame = LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld", position);
        World w = new World();
        var first = PersistentApparatusIdentity.placed();
        Object be = new Object();
        w.blocks.put(position, meter(first, be));
        var snap = LoadedApparatusSnapshot.capture(w, frame, List.of(position));
        check(snap.verify(w) == Check.VALID, "bound token is current");
        check(snap.persistentReferences().getFirst().token().equals(first.token()),
                "long-lived reference carries saved token");
        w.blocks.put(position, meter(first.changed(), be));
        check(snap.verify(w) == Check.PERSISTENT_ID_CHANGED, "revision invalidates");
        w.blocks.put(position, meter(PersistentApparatusIdentity.placed(), be));
        check(snap.verify(w) == Check.PERSISTENT_ID_CHANGED, "replacement ID invalidates");
        w.blocks.put(position, meter(first, new Object()));
        check(snap.verify(w) == Check.BLOCK_ENTITY_REPLACED, "copied ID not same session BE");
        w.blocks.put(position, meter(first, be));
        var duplicate = at(position.x()+1, position.y(), position.z());
        w.blocks.put(duplicate, meter(first, new Object()));
        reject(() -> LoadedApparatusSnapshot.capture(w, frame, List.of(position, duplicate)),
                "duplicate UUID inside one rig refused");
        w.blocks.remove(duplicate);
        var freshCapture = LoadedApparatusSnapshot.capture(w, frame, List.of(position));
        check(freshCapture.persistentReferences().equals(snap.persistentReferences()),
                "same durable address/key remains after a new observation");
        w.tick++;
        check(snap.verify(w) == Check.TICK_CHANGED, "snapshot never used for later tick");
        System.out.println("D22F PersistentIdentitySmoke PASS " + assertions + "/" + assertions);
    }
}
