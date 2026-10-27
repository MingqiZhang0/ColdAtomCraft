package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.PreviewBeamRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** The thin NeoForge-to-pure-core adapter for the DEV-only straight path. */
final class DiagnosticBeamScan {
    private DiagnosticBeamScan() { }

    static Component measure(ServerLevel level, BlockPos receiverPos,
                             Direction receiverFront, int aperturePreset) {
        if (receiverFront.getAxis().isVertical()) {
            throw new IllegalArgumentException("receiver front must be horizontal");
        }
        PreviewBeamRoute.Result result = PreviewBeamRoute.scan(aperturePreset, distance -> {
            BlockPos next = receiverPos.relative(receiverFront, distance);
            if (!level.hasChunkAt(next)) {
                return PreviewBeamRoute.Cell.UNLOADED;
            }
            BlockState state = level.getBlockState(next);
            if (state.is(PreviewBlocks.LASER.get())) {
                if (state.getValue(DiagnosticLaserBlock.FACING) != receiverFront.getOpposite()) {
                    return PreviewBeamRoute.Cell.EMITTER_WRONG_FACING;
                }
                return state.getValue(DiagnosticLaserBlock.ENABLED)
                        ? PreviewBeamRoute.Cell.EMITTER_READY
                        : PreviewBeamRoute.Cell.EMITTER_OFF;
            }
            return state.isAir() ? PreviewBeamRoute.Cell.AIR : PreviewBeamRoute.Cell.OBSTACLE;
        }, distance -> {
            BlockPos next = receiverPos.relative(receiverFront, distance);
            return level.getBlockEntity(next) instanceof LaserSettingsBlockEntity source
                    ? source.tuning() : null;
        });
        return switch (result.status()) {
            case MEASURED -> Component.translatable("message.coldatomcraft.preview.reading",
                    String.format(Locale.ROOT, "%.5f", result.powerWatts() * 1000.0),
                    result.separationBlocks());
            case NO_SOURCE -> Component.translatable("message.coldatomcraft.preview.no_source");
            case BLOCKED -> Component.translatable("message.coldatomcraft.preview.blocked");
            case UNLOADED -> Component.translatable("message.coldatomcraft.preview.unloaded");
            case WRONG_FACING -> Component.translatable("message.coldatomcraft.preview.wrong_facing");
            case SHUTTER_CLOSED -> Component.translatable("message.coldatomcraft.preview.shutter_closed");
            case SOURCE_CONFIG_MISSING -> Component.translatable("message.coldatomcraft.preview.config_missing");
        };
    }
}
