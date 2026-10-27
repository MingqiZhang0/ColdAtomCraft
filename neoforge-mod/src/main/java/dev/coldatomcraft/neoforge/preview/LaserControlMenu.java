package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.LaserTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla menu button packets and data slots. The client never directly writes
 * laser NBT. The server validates every requested parameter and player range.
 */
public final class LaserControlMenu extends AbstractContainerMenu {
    public static final int ENABLED = 0;
    public static final int POWER = 1;
    public static final int WAVELENGTH = 2;
    public static final int WAIST = 3;
    public static final int OFFSET = 4;
    public static final int FACING = 5;
    public static final int VALID = 6;
    public static final int DATA_COUNT = 7;

    public static final int BUTTON_TOGGLE = 0;
    public static final int BUTTON_ROTATE_LEFT = 1;
    public static final int BUTTON_ROTATE_RIGHT = 2;
    private static final int BUTTON_FIELD_BASE = 100_000;
    private static final int BUTTON_FIELD_STRIDE = 50_000;
    private static final int BUTTON_FIELD_OFFSET = 20_000;

    private final ContainerData data;
    private final LaserSettingsBlockEntity source; // null only for the mirrored client menu
    private final Level level;
    private final BlockPos pos;

    public LaserControlMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public LaserControlMenu(int id, Inventory inventory, LaserSettingsBlockEntity source) {
        super(PreviewBlocks.LASER_MENU.get(), id);
        this.source = source;
        this.level = source == null ? null : source.getLevel();
        this.pos = source == null ? null : source.getBlockPos().immutable();
        if (source == null) {
            SimpleContainerData clientData = new SimpleContainerData(DATA_COUNT);
            clientData.set(POWER, LaserTuning.DEFAULT.powerMilliwatts());
            clientData.set(WAVELENGTH, LaserTuning.DEFAULT.wavelengthNanometers());
            clientData.set(WAIST, LaserTuning.DEFAULT.waistRadiusMicrometers());
            clientData.set(OFFSET, LaserTuning.DEFAULT.waistOffsetMillimeters());
            clientData.set(VALID, 1);
            this.data = clientData;
        } else {
            this.data = new ContainerData() {
                @Override public int get(int slot) {
                    LaserTuning tuning = source.tuning();
                    BlockState block = level.getBlockState(pos);
                    return switch (slot) {
                        case ENABLED -> block.is(PreviewBlocks.LASER.get())
                                && block.getValue(DiagnosticLaserBlock.ENABLED) ? 1 : 0;
                        case POWER -> tuning.powerMilliwatts();
                        case WAVELENGTH -> tuning.wavelengthNanometers();
                        case WAIST -> tuning.waistRadiusMicrometers();
                        case OFFSET -> tuning.waistOffsetMillimeters();
                        case FACING -> block.is(PreviewBlocks.LASER.get())
                                ? block.getValue(DiagnosticLaserBlock.FACING).get2DDataValue() : 0;
                        case VALID -> source.configurationValid() ? 1 : 0;
                        default -> 0;
                    };
                }
                @Override public void set(int slot, int value) { /* server state is read-only here */ }
                @Override public int getCount() { return DATA_COUNT; }
            };
        }
        addDataSlots(data);
    }

    public int value(int index) { return data.get(index); }

    public static int setFieldButton(int field, int value) {
        if (field < 0 || field > 3) throw new IllegalArgumentException("field index");
        return BUTTON_FIELD_BASE + field * BUTTON_FIELD_STRIDE + BUTTON_FIELD_OFFSET + value;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (level == null || level.isClientSide() || !stillValid(player)) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.is(PreviewBlocks.LASER.get())) return false;
        if (buttonId == BUTTON_TOGGLE) {
            if (!source.configurationValid() && !state.getValue(DiagnosticLaserBlock.ENABLED)) return false;
            level.setBlockAndUpdate(pos, state.setValue(DiagnosticLaserBlock.ENABLED,
                    !state.getValue(DiagnosticLaserBlock.ENABLED)));
        } else if (buttonId == BUTTON_ROTATE_LEFT || buttonId == BUTTON_ROTATE_RIGHT) {
            Direction direction = state.getValue(DiagnosticLaserBlock.FACING);
            level.setBlockAndUpdate(pos, state.setValue(DiagnosticLaserBlock.FACING,
                    buttonId == BUTTON_ROTATE_LEFT ? direction.getCounterClockWise() : direction.getClockWise()));
        } else {
            int encoded = buttonId - BUTTON_FIELD_BASE;
            if (encoded < 0) return false;
            int field = encoded / BUTTON_FIELD_STRIDE;
            int candidate = encoded % BUTTON_FIELD_STRIDE - BUTTON_FIELD_OFFSET;
            if (field < 0 || field >= 4) return false;
            LaserTuning updated;
            try { updated = source.tuning().withField(field, candidate); }
            catch (IllegalArgumentException invalid) { return false; }
            source.update(updated);
        }
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return source == null || (level != null && pos != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) == source
                && level.getBlockState(pos).is(PreviewBlocks.LASER.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0);
    }

    @Override public ItemStack quickMoveStack(Player player, int slotIndex) { return ItemStack.EMPTY; }
}
