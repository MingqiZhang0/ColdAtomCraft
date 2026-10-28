package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D;
import dev.coldatomcraft.experiment.PreviewBeamRoute;
import dev.coldatomcraft.experiment.PreviewOpticalBench;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** Authoritative, read-only D14 meter measurement using the same solver as beam display. */
final class DiagnosticBeamScan {
    private DiagnosticBeamScan() { }

    static Component measure(ServerLevel level, BlockPos receiverPos,
                             Direction receiverFront, int aperturePreset) {
        // Read the actual server world at click time. The diagnostic search is
        // deliberately finite, not a claim to collect all distant laser power.
        PreviewOpticalNetwork3D.MeterReading reading = sample(level, receiverPos);
        return switch (reading.status()) {
            case MEASURED -> Component.translatable("message.coldatomcraft.d14.reading",
                    String.format(Locale.ROOT, "%.5f", reading.watts() * 1000.0));
            case MULTIPLE_UNRESOLVED -> Component.translatable("message.coldatomcraft.d14.ambiguous",
                    reading.incidentBranches());
            case INCOMPLETE -> Component.translatable("message.coldatomcraft.d14.incomplete");
            case NO_BEAM -> {
                // D10's diagnostic distinctions remain available for horizontal
                // straight-path failures, without ever authorizing power.
                if (receiverFront.getAxis().isHorizontal()) {
                    yield straightPathDiagnostic(level, receiverPos, receiverFront, aperturePreset);
                }
                yield Component.translatable("message.coldatomcraft.d14.no_beam");
            }
        };
    }

    /** Authoritative observation for an external long-term data logger. */
    static PreviewOpticalNetwork3D.MeterReading sample(ServerLevel level, BlockPos receiverPos) {
        var sources = OpticalBenchWorld.sourcesNear(level, receiverPos, 24, 12);
        var optical = OpticalBenchWorld.trace(level, sources);
        return optical.readingAt(OpticalBenchWorld.toPosition(receiverPos));
    }

    private static Component straightPathDiagnostic(ServerLevel level, BlockPos receiverPos,
                                                    Direction receiverFront, int preset) {
        PreviewBeamRoute.Result result = PreviewBeamRoute.scan(preset, distance -> {
            BlockPos next = receiverPos.relative(receiverFront, distance);
            if (!level.hasChunkAt(next)) return PreviewBeamRoute.Cell.UNLOADED;
            BlockState state = level.getBlockState(next);
            if (state.is(PreviewBlocks.LASER.get())) {
                if (state.getValue(DiagnosticLaserBlock.FACING) != receiverFront.getOpposite()) {
                    return PreviewBeamRoute.Cell.EMITTER_WRONG_FACING;
                }
                return state.getValue(DiagnosticLaserBlock.ENABLED)
                        ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.EMITTER_OFF;
            }
            return state.isAir() ? PreviewBeamRoute.Cell.AIR : PreviewBeamRoute.Cell.OBSTACLE;
        }, distance -> {
            BlockPos next = receiverPos.relative(receiverFront, distance);
            return level.getBlockEntity(next) instanceof LaserSettingsBlockEntity source
                    && source.configurationValid() ? source.tuning() : null;
        });
        return switch (result.status()) {
            // If the pure D14 network could not find a source, do not report a
            // D10 fallback value as though D14's complete 3D network saw it.
            case MEASURED, NO_SOURCE -> Component.translatable("message.coldatomcraft.d14.no_beam");
            case BLOCKED -> Component.translatable("message.coldatomcraft.preview.blocked");
            case UNLOADED -> Component.translatable("message.coldatomcraft.preview.unloaded");
            case WRONG_FACING -> Component.translatable("message.coldatomcraft.preview.wrong_facing");
            case SHUTTER_CLOSED -> Component.translatable("message.coldatomcraft.preview.shutter_closed");
            case SOURCE_CONFIG_MISSING -> Component.translatable("message.coldatomcraft.preview.config_missing");
        };
    }
}
