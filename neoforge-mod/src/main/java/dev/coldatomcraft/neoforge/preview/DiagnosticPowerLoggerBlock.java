package dev.coldatomcraft.neoforge.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/** Separate recorder: original meter right-click and aperture remain unchanged. */
public final class DiagnosticPowerLoggerBlock extends Block implements EntityBlock {
    public DiagnosticPowerLoggerBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerLoggerBlockEntity(pos,state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != PreviewBlocks.LOGGER_BLOCK_ENTITY.get()) return null;
        return (world, pos, ignored, blockEntity) -> {
            if (blockEntity instanceof PowerLoggerBlockEntity logger) logger.serverTick(world, pos);
        };
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level,pos,player);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level,pos,player);
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof PowerLoggerBlockEntity logger) {
            // Sneak-right-click preserves a quick record/pause interaction;
            // ordinary right-click opens the in-game history workstation.
            if (player.isShiftKeyDown()) {
                player.sendSystemMessage(Component.literal(logger.toggle(server)));
            } else {
                serverPlayer.openMenu(logger);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
