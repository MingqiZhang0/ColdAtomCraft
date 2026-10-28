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

    public static final DeferredBlock<DiagnosticOpticBlock> MIRROR = BLOCKS.registerBlock(
            "diagnostic_mirror", props -> new DiagnosticOpticBlock(DiagnosticOpticBlock.Kind.MIRROR, props),
            props -> props.strength(1.5f).noOcclusion());
    public static final DeferredBlock<DiagnosticOpticBlock> SPLITTER = BLOCKS.registerBlock(
            "diagnostic_splitter", props -> new DiagnosticOpticBlock(DiagnosticOpticBlock.Kind.SPLITTER, props),
            props -> props.strength(1.5f).noOcclusion());
    public static final DeferredBlock<DiagnosticOpticBlock> LENS = BLOCKS.registerBlock(
            "diagnostic_lens", props -> new DiagnosticOpticBlock(DiagnosticOpticBlock.Kind.LENS, props),
            props -> props.strength(1.5f).noOcclusion());
    public static final DeferredBlock<DiagnosticOpticBlock> HALF_WAVE = BLOCKS.registerBlock(
            "diagnostic_half_wave_plate", props -> new DiagnosticOpticBlock(DiagnosticOpticBlock.Kind.HALF_WAVE_PLATE, props),
            props -> props.strength(1.5f).noOcclusion());

    // D21 laboratory instrumentation and manual precision-stage stand-ins.
    public static final DeferredBlock<DiagnosticPowerLoggerBlock> LOGGER = BLOCKS.registerBlock(
            "diagnostic_power_logger", DiagnosticPowerLoggerBlock::new,
            props -> props.strength(1.5f).noOcclusion());
    public static final DeferredBlock<DiagnosticOpticalTableBlock> OPTICAL_TABLE = BLOCKS.registerBlock(
            "diagnostic_optical_table", DiagnosticOpticalTableBlock::new,
            props -> props.strength(2.5f));
    public static final DeferredBlock<DiagnosticStageBlock> LINEAR_STAGE = BLOCKS.registerBlock(
            "diagnostic_linear_stage", props -> new DiagnosticStageBlock(DiagnosticStageBlock.Kind.LINEAR, props),
            props -> props.strength(1.5f).noOcclusion());
    public static final DeferredBlock<DiagnosticStageBlock> ROTARY_STAGE = BLOCKS.registerBlock(
            "diagnostic_rotary_stage", props -> new DiagnosticStageBlock(DiagnosticStageBlock.Kind.ROTARY, props),
            props -> props.strength(1.5f).noOcclusion());

    // D22B coarse real-world optical preset scan (NOT a precision motor).
    public static final DeferredBlock<AutoPresetStageBlock> AUTO_PRESET_STAGE = BLOCKS.registerBlock(
            "diagnostic_auto_preset_stage", AutoPresetStageBlock::new,
            props -> props.strength(1.5f).noOcclusion());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserSettingsBlockEntity>>
            LASER_BLOCK_ENTITY = BLOCK_ENTITIES.register("diagnostic_laser_settings",
            () -> new BlockEntityType<>(LaserSettingsBlockEntity::new, LASER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerLoggerBlockEntity>>
            LOGGER_BLOCK_ENTITY = BLOCK_ENTITIES.register("diagnostic_power_logger_data",
            () -> new BlockEntityType<>(PowerLoggerBlockEntity::new, LOGGER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoPresetStageBlockEntity>>
            AUTO_PRESET_STAGE_BLOCK_ENTITY = BLOCK_ENTITIES.register("diagnostic_auto_preset_stage_data",
            () -> new BlockEntityType<>(AutoPresetStageBlockEntity::new, AUTO_PRESET_STAGE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiagnosticApparatusIdentityBlockEntity>>
            APPARATUS_IDENTITY_BLOCK_ENTITY = BLOCK_ENTITIES.register("diagnostic_apparatus_identity",
            () -> new BlockEntityType<>(DiagnosticApparatusIdentityBlockEntity::new,
                    METER.get(), MIRROR.get(), SPLITTER.get(), LENS.get(), HALF_WAVE.get(),
                    OPTICAL_TABLE.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<LaserControlMenu>> LASER_MENU =
            MENUS.register("diagnostic_laser_control",
                    () -> new MenuType<>(LaserControlMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<PowerLoggerMenu>> LOGGER_MENU =
            MENUS.register("diagnostic_power_logger_menu",
                    () -> new MenuType<>(PowerLoggerMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredItem<?> LASER_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_laser", LASER);
    public static final DeferredItem<?> METER_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_power_meter", METER);
    public static final DeferredItem<?> MIRROR_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_mirror", MIRROR);
    public static final DeferredItem<?> SPLITTER_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_splitter", SPLITTER);
    public static final DeferredItem<?> LENS_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_lens", LENS);
    public static final DeferredItem<?> HALF_WAVE_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_half_wave_plate", HALF_WAVE);

    public static final DeferredItem<?> LOGGER_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_power_logger", LOGGER);
    public static final DeferredItem<?> TABLE_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_optical_table", OPTICAL_TABLE);
    public static final DeferredItem<?> LINEAR_STAGE_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_linear_stage", LINEAR_STAGE);
    public static final DeferredItem<?> ROTARY_STAGE_ITEM = ITEMS.registerSimpleBlockItem("diagnostic_rotary_stage", ROTARY_STAGE);
    public static final DeferredItem<?> AUTO_PRESET_STAGE_ITEM = ITEMS.registerSimpleBlockItem(
            "diagnostic_auto_preset_stage", AUTO_PRESET_STAGE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "lab_preview", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.coldatomcraft.lab_preview"))
                    .icon(() -> new ItemStack(LASER_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(LASER_ITEM.get());
                        output.accept(METER_ITEM.get());
                        output.accept(MIRROR_ITEM.get());
                        output.accept(SPLITTER_ITEM.get());
                        output.accept(LENS_ITEM.get());
                        output.accept(HALF_WAVE_ITEM.get());
                        output.accept(LOGGER_ITEM.get());
                        output.accept(TABLE_ITEM.get());
                        output.accept(LINEAR_STAGE_ITEM.get());
                        output.accept(ROTARY_STAGE_ITEM.get());
                        output.accept(AUTO_PRESET_STAGE_ITEM.get());
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
