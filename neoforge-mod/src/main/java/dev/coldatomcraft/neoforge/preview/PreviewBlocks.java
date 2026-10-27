package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.neoforge.ColdAtomCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The DEV-only blocks and creative items are deliberately separate from Physics Core. */
public final class PreviewBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ColdAtomCraft.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ColdAtomCraft.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ColdAtomCraft.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ColdAtomCraft.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ColdAtomCraft.MOD_ID);

    public static final DeferredBlock<DiagnosticLaserBlock> LASER = BLOCKS.registerBlock(
            "diagnostic_laser", DiagnosticLaserBlock::new,
            props -> props.strength(1.5f).noOcclusion()
                    .lightLevel(state -> state.getValue(DiagnosticLaserBlock.ENABLED) ? 5 : 0));
    public static final DeferredBlock<DiagnosticPowerMeterBlock> METER = BLOCKS.registerBlock(
            "diagnostic_power_meter", DiagnosticPowerMeterBlock::new,
            props -> props.strength(1.5f).noOcclusion());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserSettingsBlockEntity>>
            LASER_BLOCK_ENTITY = BLOCK_ENTITIES.register("diagnostic_laser_settings",
            () -> new BlockEntityType<>(LaserSettingsBlockEntity::new, LASER.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<LaserControlMenu>> LASER_MENU =
            MENUS.register("diagnostic_laser_control",
                    () -> new MenuType<>(LaserControlMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredItem<?> LASER_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_laser", LASER);
    public static final DeferredItem<?> METER_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_power_meter", METER);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "lab_preview", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.coldatomcraft.lab_preview"))
                    .icon(() -> new ItemStack(LASER_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(LASER_ITEM.get());
                        output.accept(METER_ITEM.get());
                    }).build());

    private PreviewBlocks() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
