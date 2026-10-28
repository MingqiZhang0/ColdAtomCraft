package dev.coldatomcraft.neoforge.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Real server-ticked four-selector D14 optical bench diagnostic actuator. */
public final class AutoPresetStageBlock extends Block implements EntityBlock {
    public AutoPresetStageBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoPresetStageBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != PreviewBlocks.AUTO_PRESET_STAGE_BLOCK_ENTITY.get()) return null;
        return (world, pos, ignored, entity) -> {
            if (entity instanceof AutoPresetStageBlockEntity stage) stage.serverTick(world);
        };
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, player);
    }
    private InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && player.distanceToSqr(
                pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) <= 64.0
                && level.getBlockEntity(pos) instanceof AutoPresetStageBlockEntity stage) {
            String result;
            try {
                result = player.isShiftKeyDown() ? stage.export(server) : stage.startOrStop(server);
            } catch (java.io.IOException | IllegalArgumentException exception) {
                result = "[CAC D22B] Stage export failed: " + exception.getClass().getSimpleName();
            }
            player.sendSystemMessage(Component.literal(result));
            player.sendSystemMessage(Component.literal("[CAC D22B] " + stage.status()));
        }
        return InteractionResult.SUCCESS;
    }
}
