package dev.coldatomcraft.neoforge.client;

import dev.coldatomcraft.experiment.PreviewBeamColour;
import dev.coldatomcraft.experiment.PreviewBeamParticleBudget;
import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D;
import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D.Segment;
import dev.coldatomcraft.neoforge.ColdAtomCraft;
import dev.coldatomcraft.neoforge.preview.OpticalBenchWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Client-only diagnostic false-colour display of the D14 pure network trace.
 * The server alone owns meter readings; rendering never makes world effects.
 * Near-infrared light is deliberately shown false-red, not as physical scattering.
 */
@EventBusSubscriber(modid = ColdAtomCraft.MOD_ID, value = Dist.CLIENT)
public final class DiagnosticBeamParticles {
    private static final int REFRESH_EVERY_TICKS = 4;
    private static final int SCAN_RADIUS_HORIZONTAL = 16;
    private static final int SCAN_RADIUS_VERTICAL = 5;
    private static final int PARTICLES_PER_REFRESH =
            PreviewBeamParticleBudget.DEFAULT_PARTICLES_PER_REFRESH;
    private static long clientTickCounter;

    private DiagnosticBeamParticles() { }

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post ignored) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        if (++clientTickCounter % REFRESH_EVERY_TICKS != 0) return;

        var sources = OpticalBenchWorld.sourcesNear(level, minecraft.player.blockPosition(),
                SCAN_RADIUS_HORIZONTAL, SCAN_RADIUS_VERTICAL);
        if (sources.isEmpty()) return;
        var trace = OpticalBenchWorld.trace(level, sources);
        List<Segment> segments = trace.segments();
        if (segments.isEmpty()) return;
        double[] lengths = new double[segments.size()];
        for (int i = 0; i < lengths.length; i++) lengths[i] = segments.get(i).lengthMeters();
        int[] steps = PreviewBeamParticleBudget.allocateSteps(
                lengths, PARTICLES_PER_REFRESH,
                PreviewBeamParticleBudget.NOMINAL_SPACING_BLOCKS,
                clientTickCounter / REFRESH_EVERY_TICKS);
        for (int i = 0; i < segments.size(); i++) {
            if (steps[i] > 0) draw(level, segments.get(i), steps[i]);
        }
    }

    private static void draw(ClientLevel level, Segment segment, int steps) {
        var direction = segment.direction();
        var u = direction.transverseX();
        var v = direction.transverseY();
        var origin = segment.origin();
        double start = segment.startOffsetMeters();
        double length = segment.lengthMeters();
        int wavelength = (int) Math.round(segment.atStart().wavelengthMeters() * 1e9);
        DustParticleOptions core = new DustParticleOptions(
                PreviewBeamColour.rgb(wavelength),
                (float) Math.min(1.5, 0.45 + 0.35 * Math.sqrt(segment.atStart().watts() * 1000.0 / 20.0)));
        DustParticleOptions halo = new DustParticleOptions(PreviewBeamColour.haloRgb(wavelength), 0.58f);
        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            double axial = start + length * fraction;
            double px = origin.x() + 0.5 + direction.dx() * axial;
            double py = origin.y() + 0.5 + direction.dy() * axial;
            double pz = origin.z() + 0.5 + direction.dz() * axial;
            level.addParticle(core, px, py, pz, 0, 0, 0);
            if (i % 3 == 0) {
                // Visual radius is exaggerated and is NEVER used for measurement.
                double physicalRadius = segment.waveAt(fraction).radiusMeters();
                double displayRadius = Math.min(0.16, 0.03 + 9.0 * physicalRadius);
                double angle = i * 2.399963229728653 + clientTickCounter * 0.27;
                double random = (((i * 37L + clientTickCounter / REFRESH_EVERY_TICKS) % 97) + 0.5) / 97.0;
                double spread = displayRadius * Math.min(1.5, Math.sqrt(-0.5 * Math.log1p(-random)));
                double sideways = Math.cos(angle) * spread;
                double vertical = Math.sin(angle) * spread;
                level.addParticle(halo,
                        px + u.x() * sideways + v.x() * vertical,
                        py + u.y() * sideways + v.y() * vertical,
                        pz + u.z() * sideways + v.z() * vertical,
                        0, 0, 0);
            }
        }
    }
}
