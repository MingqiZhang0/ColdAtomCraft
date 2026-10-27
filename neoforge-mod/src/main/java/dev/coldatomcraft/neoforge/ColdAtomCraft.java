package dev.coldatomcraft.neoforge;

import dev.coldatomcraft.neoforge.preview.PreviewBlocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/**
 * Mod entrypoint. The registered DEV devices use placeholder cuboid assets and
 * a fixed, clearly-labelled optical calibration fixture. They do not approve
 * any research model or perform Minecraft world energy transfer.
 */
@Mod(ColdAtomCraft.MOD_ID)
public final class ColdAtomCraft {
    public static final String MOD_ID = "coldatomcraft";

    public ColdAtomCraft(IEventBus modEventBus, ModContainer container) {
        PreviewBlocks.register(modEventBus);
    }
}
