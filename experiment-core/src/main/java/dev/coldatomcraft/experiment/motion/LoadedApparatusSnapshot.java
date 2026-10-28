package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.LaserTuning;
import dev.coldatomcraft.physics.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * D22E read-only, SAME-TICK view of already-loaded Minecraft apparatus.
 * <p>The Reader contract is implemented by the SERVER adapter. A snapshot is
 * neither a persistent instrument ID nor an experimental observation. A
 * stateless D14 block cannot prove that it was not removed and replaced by an
 * identical state; consequently even VALID is NEVER permission to move or ON.
 * The conversion uses a DECLARED laboratory frame, not calibrated metrology.
 */
public final class LoadedApparatusSnapshot {
    public static final int MAX_DEVICES = 24;

    public enum Kind {
        LASER, METER, MIRROR, SPLITTER, LENS, HALF_WAVE_PLATE,
        OPTICAL_TABLE, MANUAL_LINEAR_STAGE, MANUAL_ROTARY_STAGE, AUTO_PRESET_STAGE
    }
    public enum Axis { EAST, WEST, UP, DOWN, SOUTH, NORTH, NONE }
    public enum Binding { BLOCKSTATE_ONLY, BLOCK_ENTITY_INSTANCE, PERSISTENT_BLOCK_ENTITY }
    public enum Check {
        VALID, OFF_THREAD, SERVER_CHANGED, DIMENSION_CHANGED, TICK_CHANGED,
        CHUNK_UNLOADED, BLOCKSTATE_CHANGED, BLOCK_ENTITY_REPLACED, PERSISTENT_ID_CHANGED
    }

    /** Describes settings as read from a server BlockState + optional BE. */
    public record State(Kind kind, Axis axis, int setting,
                        boolean enabled, LaserTuning laserSettings, Binding binding) {
        public State {
            Objects.requireNonNull(kind, "apparatus kind");
            Objects.requireNonNull(axis, "apparatus axis");
            Objects.requireNonNull(binding, "binding evidence");
            boolean requiresEntity = kind == Kind.LASER || kind == Kind.AUTO_PRESET_STAGE;
            if (requiresEntity != (binding == Binding.BLOCK_ENTITY_INSTANCE)) {
                // D22E laser + auto stage keep their session-only evidence.
                // Optics, meters and tables may now carry D22F persistent BEs.
                if (requiresEntity || binding != Binding.PERSISTENT_BLOCK_ENTITY
                        || !(kind == Kind.METER || kind == Kind.OPTICAL_TABLE
                                || kind == Kind.MIRROR || kind == Kind.SPLITTER
                                || kind == Kind.LENS || kind == Kind.HALF_WAVE_PLATE)) {
                    throw new IllegalArgumentException("incorrect apparatus identity evidence");
                }
            }
            if ((kind == Kind.LASER) != (laserSettings != null)) {
                throw new IllegalArgumentException("laser parameters required only for laser");
            }
            if (kind != Kind.LASER && enabled) {
                throw new IllegalArgumentException("non-laser apparatus cannot emit");
            }
            boolean hasAxis = kind != Kind.OPTICAL_TABLE && kind != Kind.AUTO_PRESET_STAGE;
            if ((axis != Axis.NONE) != hasAxis) {
                throw new IllegalArgumentException("wrong apparatus axis presence");
            }
            int expectedMax = switch (kind) {
                case MIRROR, SPLITTER, LENS, HALF_WAVE_PLATE -> 3;
                case METER -> 2;
                default -> -1;
            };
            if (expectedMax == -1 ? setting != -1 : setting < 0 || setting > expectedMax) {
                throw new IllegalArgumentException("invalid persisted optical selector");
            }
        }

        public boolean sessionInstanceBound() {
            return binding != Binding.BLOCKSTATE_ONLY;
        }
        /** The direction means emission for laser, FRONT for meter, incoming TRAVEL for optics. */
        public String axisMeaning() {
            return switch (kind) {
                case LASER -> "EMISSION_TRAVEL";
                case METER -> "DETECTOR_FRONT_NORMAL";
                case MIRROR, SPLITTER, LENS, HALF_WAVE_PLATE -> "INCIDENT_TRAVEL";
                case MANUAL_LINEAR_STAGE, MANUAL_ROTARY_STAGE -> "MOUNT_AXIS";
                default -> "NONE";
            };
        }
    }

    /** The object identity is the JVM BE reference; the token is saved in NBT. */
    public record Observed(State state, Object blockEntityIdentity,
                           PersistentApparatusIdentity.Token persistentToken) {
        public Observed(State state, Object entity) { this(state, entity, null); }
        public Observed {
            Objects.requireNonNull(state, "actual state");
            if ((blockEntityIdentity != null) != state.sessionInstanceBound()) {
                throw new IllegalArgumentException("actual block entity identity mismatch");
            }
            if ((persistentToken != null) != (state.binding() == Binding.PERSISTENT_BLOCK_ENTITY)) {
                throw new IllegalArgumentException("persistent device token/evidence mismatch");
            }
        }
    }

    /** A long-lived reference is meaningful only after fresh SERVER read/verification. */
    public record DurableReference(String dimensionId, LaboratoryMountFrame.BlockAddress block,
                                   Kind kind, PersistentApparatusIdentity.Token token) {
        public DurableReference {
            Objects.requireNonNull(dimensionId, "dimension");
            Objects.requireNonNull(block, "position");
            Objects.requireNonNull(kind, "apparatus kind");
            Objects.requireNonNull(token, "identity token");
        }
    }

    /** Implemented exclusively by an authoritative server world adapter. */
    public interface Reader {
        Object serverWorldIdentity();
        String dimensionId();
        long gameTick();
        boolean serverThread();
        boolean isLoaded(LaboratoryMountFrame.BlockAddress position);
        Observed readLoaded(LaboratoryMountFrame.BlockAddress position);
    }

    public record LocalDevice(LaboratoryMountFrame.BlockAddress block,
                              Vec3 centerMetres, Vec3 indicatedAxisLab,
                              State state) {
        public LocalDevice {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(centerMetres, "SI position");
            Objects.requireNonNull(indicatedAxisLab, "axis");
            Objects.requireNonNull(state, "state");
        }
        public String provenance() { return "READ_ONLY_SERVER_SNAPSHOT"; }
        public boolean authenticatedHardwarePose() { return false; }
    }

    private record Bound(LaboratoryMountFrame.BlockAddress block, Observed observed,
                         LocalDevice device) { }

    private final Object worldIdentity;
    private final String dimensionId;
    private final long tick;
    private final LaboratoryMountFrame frame;
    private final List<Bound> bound;
    private final List<LocalDevice> devices;

    private LoadedApparatusSnapshot(Object worldIdentity, String dimensionId, long tick,
                                    LaboratoryMountFrame frame, List<Bound> bound) {
        this.worldIdentity = worldIdentity;
        this.dimensionId = dimensionId;
        this.tick = tick;
        this.frame = frame;
        this.bound = List.copyOf(bound);
        this.devices = this.bound.stream().map(Bound::device).toList();
    }

    /** Atomic-with-respect-to-one-server-thread capture; no chunks are loaded. */
    public static LoadedApparatusSnapshot capture(Reader reader, LaboratoryMountFrame frame,
                List<LaboratoryMountFrame.BlockAddress> positions) {
        Objects.requireNonNull(reader, "world");
        Objects.requireNonNull(frame, "lab frame");
        Objects.requireNonNull(positions, "requested positions");
        if (!reader.serverThread()) throw new IllegalStateException("not on server world thread");
        Object world = Objects.requireNonNull(reader.serverWorldIdentity(), "world identity");
        String dimension = Objects.requireNonNull(reader.dimensionId(), "dimension");
        long tick = reader.gameTick();
        if (!dimension.equals(frame.dimensionId()) || tick < 0) {
            throw new IllegalStateException("invalid dimension or world tick");
        }
        if (positions.isEmpty() || positions.size() > MAX_DEVICES) {
            throw new IllegalArgumentException("apparatus list must be bounded and nonempty");
        }
        List<LaboratoryMountFrame.BlockAddress> requests = List.copyOf(positions);
        Set<LaboratoryMountFrame.BlockAddress> unique = new HashSet<>();
        Set<java.util.UUID> persistentIds = new HashSet<>();
        List<Bound> found = new ArrayList<>(requests.size());
        for (var pos : requests) {
            Objects.requireNonNull(pos, "block address");
            if (!unique.add(pos)) throw new IllegalArgumentException("duplicate apparatus position");
            if (!reader.isLoaded(pos)) throw new IllegalStateException("unloaded apparatus chunk");
            Observed observed = Objects.requireNonNull(reader.readLoaded(pos), "loaded apparatus");
            if (observed.persistentToken() != null &&
                    !persistentIds.add(observed.persistentToken().uuid())) {
                throw new IllegalStateException("duplicate D22F identity inside one rig");
            }
            Vec3 center = frame.blockCenter(pos, LaboratoryMountFrame.NanometreOffset.zero());
            Vec3 axis = direction(frame, observed.state().axis());
            found.add(new Bound(pos, observed,
                    new LocalDevice(pos, center, axis, observed.state())));
        }
        // Explicitly prohibit a reader that crosses a tick during capture.
        if (!reader.serverThread() || reader.serverWorldIdentity() != world
                || !dimension.equals(reader.dimensionId()) || reader.gameTick() != tick) {
            throw new IllegalStateException("world changed during apparatus capture");
        }
        return new LoadedApparatusSnapshot(world, dimension, tick, frame, found);
    }

    public List<LocalDevice> devices() { return devices; }
    public List<DurableReference> persistentReferences() {
        return bound.stream().filter(x -> x.observed().persistentToken() != null)
                .map(x -> new DurableReference(dimensionId, x.block(),
                        x.observed().state().kind(), x.observed().persistentToken())).toList();
    }

    public long gameTick() { return tick; }
    public String dimensionId() { return dimensionId; }
    public LaboratoryMountFrame laboratory() { return frame; }
    public String provenance() { return "READ_ONLY_SERVER_SNAPSHOT"; }

    /**
     * A VALID check means the SAME server world/tick and checked state/BE
     * instances still match. It does not certify persistent stateless blocks,
     * real encoder positions, calibration, stability, or operator permissions.
     */
    public Check verify(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        if (!reader.serverThread()) return Check.OFF_THREAD;
        if (reader.serverWorldIdentity() != worldIdentity) return Check.SERVER_CHANGED;
        if (!dimensionId.equals(reader.dimensionId())) return Check.DIMENSION_CHANGED;
        if (reader.gameTick() != tick) return Check.TICK_CHANGED;
        for (Bound item : bound) {
            if (!reader.isLoaded(item.block())) return Check.CHUNK_UNLOADED;
            Observed current;
            try { current = reader.readLoaded(item.block()); }
            catch (IllegalArgumentException | IllegalStateException invalid) {
                return Check.BLOCKSTATE_CHANGED;
            }
            if (current == null || !current.state().equals(item.observed().state())) {
                return Check.BLOCKSTATE_CHANGED;
            }
            if (!Objects.equals(current.persistentToken(), item.observed().persistentToken())) {
                return Check.PERSISTENT_ID_CHANGED;
            }
            if (current.blockEntityIdentity() != item.observed().blockEntityIdentity()) {
                return Check.BLOCK_ENTITY_REPLACED;
            }
        }
        // A re-entrant or unusual caller must not turn a world/tick change
        // during validation into a successful verification result.
        if (!reader.serverThread()) return Check.OFF_THREAD;
        if (reader.serverWorldIdentity() != worldIdentity) return Check.SERVER_CHANGED;
        if (!dimensionId.equals(reader.dimensionId())) return Check.DIMENSION_CHANGED;
        if (reader.gameTick() != tick) return Check.TICK_CHANGED;
        return Check.VALID;
    }

    private static Vec3 direction(LaboratoryMountFrame frame, Axis axis) {
        var basis = frame.axes();
        return switch (axis) {
            case EAST -> basis.xAxis();
            case WEST -> basis.xAxis().scale(-1);
            case UP -> basis.yAxis();
            case DOWN -> basis.yAxis().scale(-1);
            case SOUTH -> basis.direction();
            case NORTH -> basis.direction().scale(-1);
            case NONE -> new Vec3(0, 0, 0);
        };
    }
}
