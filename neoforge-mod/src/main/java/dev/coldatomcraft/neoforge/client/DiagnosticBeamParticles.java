package dev.coldatomcraft.neoforge.client;

import dev.coldatomcraft.experiment.PreviewBeamDisplay;
import dev.coldatomcraft.experiment.LaserTuning;
import dev.coldatomcraft.experiment.PreviewBeamColour;
import dev.coldatomcraft.experiment.PreviewOpticalBench;
import dev.coldatomcraft.neoforge.ColdAtomCraft;
import dev.coldatomcraft.neoforge.preview.DiagnosticLaserBlock;
import dev.coldatomcraft.neoforge.preview.LaserSettingsBlockEntity;
import dev.coldatomcraft.neoforge.preview.DiagnosticPowerMeterBlock;
import dev.coldatomcraft.neoforge.preview.PreviewBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * CLIENT-ONLY D11 false-colour tracer for 780 nm infrared bench light.
 *
 * <p>No ray is created on the server and no world effects are applied.
 * Blockstate originates on the server; this class only reads its client mirror.
 * It renders a bounded dust-particle preview using the D10/D11 Gaussian
 * geometry, not a claim that near-infrared light is visible in clear air.
 */
@EventBusSubscriber(modid = ColdAtomCraft.MOD_ID, value = Dist.CLIENT)
public final class DiagnosticBeamParticles {
    // Colours below 700 nm are approximate visual cues, never measured radiometry.
    // 780 nm and all other IR preview wavelengths use an explicitly false red.

    private static final int REFRESH_EVERY_TICKS = 4;
    private static final int SCAN_RADIUS_HORIZONTAL = 16;
    private static final int SCAN_RADIUS_VERTICAL = 5;
    private static final int MAX_VISIBLE_LASERS = 3;
    private static final double SAMPLE_SPACING_BLOCKS = 0.20;
    private static long clientTickCounter;

    private DiagnosticBeamParticles() { }

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post ignored) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        if (++clientTickCounter % REFRESH_EVERY_TICKS != 0) return;

        BlockPos center = minecraft.player.blockPosition();
        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();
        int active = 0;
        // A bounded nearby search avoids a global laser registry or chunk loads.
        for (int x = center.getX() - SCAN_RADIUS_HORIZONTAL;
             x <= center.getX() + SCAN_RADIUS_HORIZONTAL; x++) {
            for (int z = center.getZ() - SCAN_RADIUS_HORIZONTAL;
                 z <= center.getZ() + SCAN_RADIUS_HORIZONTAL; z++) {
                if ((x - center.getX()) * (x - center.getX())
                        + (z - center.getZ()) * (z - center.getZ())
                        > SCAN_RADIUS_HORIZONTAL * SCAN_RADIUS_HORIZONTAL) continue;
                testPos.set(x, center.getY(), z);
                if (!level.hasChunkAt(testPos)) continue;
                for (int y = center.getY() - SCAN_RADIUS_VERTICAL;
                     y <= center.getY() + SCAN_RADIUS_VERTICAL; y++) {
                    testPos.setY(y);
                    BlockState state = level.getBlockState(testPos);
                    if (state.is(PreviewBlocks.LASER.get())
                            && state.getValue(DiagnosticLaserBlock.ENABLED)) {
                        // Fail closed until synchronized per-device settings arrive.
                        BlockPos emitter = testPos.immutable();
                        if (!(level.getBlockEntity(emitter) instanceof LaserSettingsBlockEntity source)) continue;
                        LaserTuning tuning = source.tuning();
                        Direction facing = state.getValue(DiagnosticLaserBlock.FACING);
                        PreviewBeamDisplay.Trace trace = PreviewBeamDisplay.trace(distance -> {
                            BlockPos next = emitter.relative(facing, distance);
                            if (!level.hasChunkAt(next)) return PreviewBeamDisplay.Cell.UNLOADED;
                            BlockState ahead = level.getBlockState(next);
                            if (ahead.is(PreviewBlocks.METER.get())
                                    && ahead.getValue(DiagnosticPowerMeterBlock.FACING)
                                        == facing.getOpposite()) {
                                return PreviewBeamDisplay.Cell.RECEIVER;
                            }
                            return ahead.isAir() ? PreviewBeamDisplay.Cell.AIR
                                    : PreviewBeamDisplay.Cell.OBSTACLE;
                        });
                        drawFalseColourBeam(level, emitter, facing, trace, tuning);
                        if (++active >= MAX_VISIBLE_LASERS) return;
                    }
                }
            }
        }
    }

    private static void drawFalseColourBeam(ClientLevel level, BlockPos emitter,
                                            Direction facing, PreviewBeamDisplay.Trace trace, LaserTuning tuning) {
        double start = PreviewOpticalBench.DEMO_PORT_OFFSET_METERS;
        double end = trace.endFromSourceCenterBlocks();
        if (end <= start) return;
        int steps = Math.max(1, (int) Math.ceil((end - start) / SAMPLE_SPACING_BLOCKS));
        // False-colour display scale; not an optical absorbance or scattering model.
        DustParticleOptions core = new DustParticleOptions(
                PreviewBeamColour.rgb(tuning.wavelengthNanometers()),
                (float) Math.min(1.5, 0.45 + 0.35 * Math.sqrt(tuning.powerMilliwatts() / 20.0)));
        DustParticleOptions halo = new DustParticleOptions(
                PreviewBeamColour.haloRgb(tuning.wavelengthNanometers()), 0.58f);
        // Sampling follows the complete path, including its final segment.
        for (int i = 0; i <= steps; i++) {
            double axial = start + (end - start) * i / steps;
            double px = emitter.getX() + 0.5 + facing.getStepX() * axial;
            double py = emitter.getY() + 0.5;
            double pz = emitter.getZ() + 0.5 + facing.getStepZ() * axial;
            level.addParticle(core, px, py, pz, 0, 0, 0);
            // A sparse radial falloff suggests a Gaussian beam, not a hard cylinder.
            if (i % 3 == 0) {
                double zMeters = axial - PreviewOpticalBench.DEMO_PORT_OFFSET_METERS;
                double displayRadius = PreviewBeamDisplay.displayedRadiusBlocks(zMeters, tuning);
                double angle = i * 2.399963229728653 + clientTickCounter * 0.27;
                // 2D TEM00 intensity has radial CDF 1-exp(-2*r^2/w^2).
                // A deterministic quasi-random inverse-CDF sample keeps
                // the visualization repeatable without using world RNG.
                double u = (((i * 37L + clientTickCounter / REFRESH_EVERY_TICKS) % 97) + 0.5) / 97.0;
                double radiusFraction = Math.sqrt(-0.5 * Math.log1p(-u));
                double offset = displayRadius * Math.min(1.5, radiusFraction);
                double sideways = Math.cos(angle) * offset;
                double vertical = Math.sin(angle) * offset;
                level.addParticle(halo,
                        px - facing.getStepZ() * sideways,
                        py + vertical,
                        pz + facing.getStepX() * sideways,
                        0, 0, 0);
            }
        }
    }
}
