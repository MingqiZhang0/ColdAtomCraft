package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.motion.LaboratoryMountFrame;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * D22E SERVER-ONLY, read-only bridge between actual loaded NeoForge apparatus
 * and the local-SI reference. No ray tracing, motor output, or power permission.
 * Do not cache snapshots across ticks, send BE object identities to clients, or
 * claim that persistent UUIDs are authenticated hardware certificates.
 */
public final class LoadedLabGeometryWorld {
    private LoadedLabGeometryWorld() { }

    /** The 1 metre per block scale is a declared D14 DEV convention only. */
    public static LoadedApparatusSnapshot captureNominal(ServerLevel server, BlockPos anchor,
                                                          List<BlockPos> apparatus) {
        Objects.requireNonNull(server, "server world");
        Objects.requireNonNull(anchor, "laboratory anchor");
        var frame = LaboratoryMountFrame.nominalMinecraftMetres(
                server.dimension().identifier().toString(), address(anchor));
        return capture(server, frame, apparatus);
    }

    /** An explicitly declared / owner-calibrated frame can be supplied later. */
    public static LoadedApparatusSnapshot capture(ServerLevel server, LaboratoryMountFrame frame,
                                                  List<BlockPos> apparatus) {
        Objects.requireNonNull(apparatus, "apparatus positions");
        return LoadedApparatusSnapshot.capture(new ServerReader(server), frame,
                apparatus.stream().map(LoadedLabGeometryWorld::address).toList());
    }

    public static LoadedApparatusSnapshot.Check verify(ServerLevel server,
                                                       LoadedApparatusSnapshot snapshot) {
        return Objects.requireNonNull(snapshot, "snapshot").verify(new ServerReader(server));
    }

    private static LaboratoryMountFrame.BlockAddress address(BlockPos pos) {
        Objects.requireNonNull(pos, "world position");
        return new LaboratoryMountFrame.BlockAddress(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Axis axis(Direction direction) {
        return Axis.valueOf(direction.name());
    }

    private static State observeState(BlockPos pos, BlockState block,
                                      BlockEntity entity) {
        if (block.is(PreviewBlocks.LASER.get())) {
            if (!(entity instanceof LaserSettingsBlockEntity laser) || !laser.configurationValid()) {
                throw new IllegalStateException("missing or invalid laser settings entity");
            }
            return new State(Kind.LASER, axis(block.getValue(DiagnosticLaserBlock.FACING)),
                    -1, block.getValue(DiagnosticLaserBlock.ENABLED), laser.tuning(),
                    Binding.BLOCK_ENTITY_INSTANCE);
        }
        if (block.is(PreviewBlocks.AUTO_PRESET_STAGE.get())) {
            if (!(entity instanceof AutoPresetStageBlockEntity)) {
                throw new IllegalStateException("missing scan stage block entity");
            }
            return new State(Kind.AUTO_PRESET_STAGE, Axis.NONE, -1, false, null,
                    Binding.BLOCK_ENTITY_INSTANCE);
        }
        // D22F adds a persisted UUID/revision to optics, meter and table.
        // A missing/malformed identity fails closed for automatic scanning.
        if (block.is(PreviewBlocks.METER.get())) {
            return new State(Kind.METER, axis(block.getValue(DiagnosticPowerMeterBlock.FACING)),
                    block.getValue(DiagnosticPowerMeterBlock.APERTURE), false, null,
                    Binding.PERSISTENT_BLOCK_ENTITY);
        }
        if (block.getBlock() instanceof DiagnosticOpticBlock optic) {
            OpticPortPreset ports = block.getValue(DiagnosticOpticBlock.PORT);
            Kind kind = switch (optic.kind()) {
                case MIRROR -> Kind.MIRROR;
                case SPLITTER -> Kind.SPLITTER;
                case LENS -> Kind.LENS;
                case HALF_WAVE_PLATE -> Kind.HALF_WAVE_PLATE;
            };
            // PORT is incoming LIGHT TRAVEL, not outward-facing model normal.
            return new State(kind, Axis.valueOf(ports.incoming().name()), ports.selector(),
                    false, null, Binding.PERSISTENT_BLOCK_ENTITY);
        }
        if (block.is(PreviewBlocks.OPTICAL_TABLE.get())) {
            return new State(Kind.OPTICAL_TABLE, Axis.NONE, -1, false, null,
                    Binding.PERSISTENT_BLOCK_ENTITY);
        }
        if (block.is(PreviewBlocks.LINEAR_STAGE.get())) {
            if (entity != null) throw new IllegalStateException("unexpected manual stage BE");
            return new State(Kind.MANUAL_LINEAR_STAGE,
                    axis(block.getValue(DiagnosticStageBlock.FACING)), -1, false, null,
                    Binding.BLOCKSTATE_ONLY);
        }
        if (block.is(PreviewBlocks.ROTARY_STAGE.get())) {
            if (entity != null) throw new IllegalStateException("unexpected manual stage BE");
            return new State(Kind.MANUAL_ROTARY_STAGE,
                    axis(block.getValue(DiagnosticStageBlock.FACING)), -1, false, null,
                    Binding.BLOCKSTATE_ONLY);
        }
        throw new IllegalStateException("not a supported CAC apparatus at " + pos);
    }

    private static final class ServerReader implements LoadedApparatusSnapshot.Reader {
        private final ServerLevel level;
        ServerReader(ServerLevel level) { this.level = Objects.requireNonNull(level, "server world"); }
        @Override public Object serverWorldIdentity() { return level; }
        @Override public String dimensionId() { return level.dimension().identifier().toString(); }
        @Override public long gameTick() { return level.getGameTime(); }
        @Override public boolean serverThread() { return level.getServer().isSameThread(); }
        @Override public boolean isLoaded(LaboratoryMountFrame.BlockAddress address) {
            return level.hasChunkAt(blockPos(address));
        }
        @Override public Observed readLoaded(LaboratoryMountFrame.BlockAddress position) {
            BlockPos pos = blockPos(position);
            if (!serverThread() || !level.hasChunkAt(pos)) {
                throw new IllegalStateException("read would leave loaded server world");
            }
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && be.isRemoved()) {
                throw new IllegalStateException("removed apparatus block entity");
            }
            State description = observeState(pos, state, be);
            if (description.binding() == Binding.PERSISTENT_BLOCK_ENTITY) {
                if (!(be instanceof DiagnosticApparatusIdentityBlockEntity identity)
                        || !identity.identityUsable()) {
                    throw new IllegalStateException("missing or quarantined D22F apparatus identity");
                }
                return new Observed(description, be, identity.identityToken());
            }
            return new Observed(description,
                    description.sessionInstanceBound() ? be : null);
        }
        private static BlockPos blockPos(LaboratoryMountFrame.BlockAddress pos) {
            // Reader positions originate from BlockPos and are never parsed
            // from a client string. Reject accidentally out-of-range casts.
            return new BlockPos(Math.toIntExact(pos.x()), Math.toIntExact(pos.y()),
                    Math.toIntExact(pos.z()));
        }
    }
}
