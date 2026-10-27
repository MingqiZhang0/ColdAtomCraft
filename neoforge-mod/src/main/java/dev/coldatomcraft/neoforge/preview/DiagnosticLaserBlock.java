package dev.coldatomcraft.neoforge.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
public final class DiagnosticLaserBlock extends Block {
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
        // Laser output points in the same horizontal direction the player is facing.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockState updated;
            if (player.isShiftKeyDown()) {
                Direction rotated = state.getValue(FACING).getClockWise();
                updated = state.setValue(FACING, rotated);
                player.sendOverlayMessage(Component.translatable(
                        "message.coldatomcraft.preview.laser_rotated", rotated.getName()));
            } else {
                boolean enabled = !state.getValue(ENABLED);
                updated = state.setValue(ENABLED, enabled);
                player.sendOverlayMessage(Component.translatable(
                        enabled ? "message.coldatomcraft.preview.laser_on"
                                : "message.coldatomcraft.preview.laser_off"));
            }
            level.setBlockAndUpdate(pos, updated);
        }
        return InteractionResult.SUCCESS;
    }
}
