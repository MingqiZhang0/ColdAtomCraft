package dev.coldatomcraft.neoforge.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * DEV-only placeholder emitter. No laser packets, particles, heating or world
 * mutation beyond its own synced blockstate. Its diagnostic source settings
 * are implemented by the separate pure-Java PreviewOpticalBench fixture.
 */
public final class DiagnosticLaserBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    public DiagnosticLaserBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(ENABLED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Laser output points toward the placing player (same convention as the power meter).
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserSettingsBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        return openControlPanel(level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                         BlockPos pos, Player player, InteractionHand hand,
                                         BlockHitResult hitResult) {
        return openControlPanel(level, pos, player);
    }

    private static InteractionResult openControlPanel(Level level, BlockPos pos, Player player) {
        // Right click opens the menu regardless of held item. Never mutates the
        // world on the client. Sneaking is no longer a rotation shortcut.
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LaserSettingsBlockEntity source) {
            serverPlayer.openMenu(source);
        }
        return InteractionResult.SUCCESS;
    }
}
