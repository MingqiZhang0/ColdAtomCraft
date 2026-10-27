package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.LaserTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Authoritative per-apparatus preview parameters, saved into the world. */
public final class LaserSettingsBlockEntity extends BlockEntity implements MenuProvider {
    private static final String POWER = "preview_power_mw";
    private static final String WAVELENGTH = "preview_wavelength_nm";
    private static final String WAIST = "preview_waist_um";
    private static final String OFFSET = "preview_waist_offset_mm";

    private LaserTuning tuning = LaserTuning.DEFAULT;

    public LaserSettingsBlockEntity(BlockPos pos, BlockState state) {
        super(PreviewBlocks.LASER_BLOCK_ENTITY.get(), pos, state);
    }

    public LaserTuning tuning() { return tuning; }

    /** Called by the validated server menu only. */
    public void update(LaserTuning value) {
        if (value == null) throw new IllegalArgumentException("tuning required");
        if (value.equals(tuning)) return;
        tuning = value;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tuning = LaserTuning.fromStored(
                input.getIntOr(POWER, LaserTuning.DEFAULT.powerMilliwatts()),
                input.getIntOr(WAVELENGTH, LaserTuning.DEFAULT.wavelengthNanometers()),
                input.getIntOr(WAIST, LaserTuning.DEFAULT.waistRadiusMicrometers()),
                input.getIntOr(OFFSET, LaserTuning.DEFAULT.waistOffsetMillimeters()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(POWER, tuning.powerMilliwatts());
        output.putInt(WAVELENGTH, tuning.wavelengthNanometers());
        output.putInt(WAIST, tuning.waistRadiusMicrometers());
        output.putInt(OFFSET, tuning.waistOffsetMillimeters());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.coldatomcraft.laser.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LaserControlMenu(id, inventory, this);
    }
}
