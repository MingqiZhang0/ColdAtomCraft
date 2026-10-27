package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D;
import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared read-only adapter: client visualization and server meter solve the same network. */
public final class OpticalBenchWorld {
    private OpticalBenchWorld() { }

    public static Direction3 fromMinecraft(Direction direction) {
        return switch (Objects.requireNonNull(direction)) {
            case NORTH -> Direction3.NORTH;
            case SOUTH -> Direction3.SOUTH;
            case EAST -> Direction3.EAST;
            case WEST -> Direction3.WEST;
            case UP -> Direction3.UP;
            case DOWN -> Direction3.DOWN;
        };
    }
    public static Direction toMinecraft(Direction3 direction) {
        return switch (Objects.requireNonNull(direction)) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }
    public static Position toPosition(BlockPos pos) {
        return new Position(pos.getX(), pos.getY(), pos.getZ());
    }
    private static BlockPos toBlockPos(Position pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    /** No chunk force loads; unexpected devices and other mod blocks stop the beam. */
    public static Surface at(Level level, Position pos) {
        BlockPos worldPos = toBlockPos(pos);
        if (!level.hasChunkAt(worldPos)) return new Unloaded();
        BlockState state = level.getBlockState(worldPos);
        if (state.isAir()) return new Air();
        if (state.is(PreviewBlocks.METER.get())) {
            return new Detector(fromMinecraft(state.getValue(DiagnosticPowerMeterBlock.FACING)),
                    state.getValue(DiagnosticPowerMeterBlock.APERTURE));
        }
        if (state.getBlock() instanceof DiagnosticOpticBlock optic) {
            OpticPortPreset ports = state.getValue(DiagnosticOpticBlock.PORT);
            return switch (optic.kind()) {
                case MIRROR -> new Mirror(ports.incoming(), ports.reflected());
                case SPLITTER -> new Splitter(ports.incoming(), ports.reflected());
                case LENS -> new Lens(ports.incoming(), ports.focalLengthMeters());
                case HALF_WAVE_PLATE -> new HalfWavePlate(ports.incoming(), ports.waveplateAxisRadians());
            };
        }
        return new Obstacle();
    }

    /**
     * Bounded, visible-source search. The region is a DEV-only locality
     * contract; sources outside it are NOT guaranteed absent. Server-side
     * readings declare the same limited scan in the status text.
     */
    public static List<Source> sourcesNear(Level level, BlockPos center, int horizontal, int vertical) {
        if (horizontal < 0 || horizontal > 24 || vertical < 0 || vertical > 12) {
            throw new IllegalArgumentException("diagnostic source scan budget exceeded");
        }
        List<Source> sources = new ArrayList<>();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - horizontal; x <= center.getX() + horizontal; x++) {
            for (int z = center.getZ() - horizontal; z <= center.getZ() + horizontal; z++) {
                long dx = (long)x - center.getX(), dz = (long)z - center.getZ();
                if (dx*dx+dz*dz > (long)horizontal*horizontal) continue;
                for (int y = center.getY() - vertical; y <= center.getY() + vertical; y++) {
                    probe.set(x, y, z);
                    if (!level.hasChunkAt(probe)) continue;
                    BlockState state = level.getBlockState(probe);
                    if (state.is(PreviewBlocks.LASER.get())
                            && state.getValue(DiagnosticLaserBlock.ENABLED)
                            && level.getBlockEntity(probe) instanceof LaserSettingsBlockEntity source
                            && source.configurationValid()) {
                        sources.add(new Source(toPosition(probe),
                                fromMinecraft(state.getValue(DiagnosticLaserBlock.FACING)), source.tuning()));
                    }
                }
            }
        }
        return List.copyOf(sources);
    }

    public static Trace trace(Level level, List<Source> sources) {
        return PreviewOpticalNetwork3D.solve(sources, position -> at(level, position));
    }
}
