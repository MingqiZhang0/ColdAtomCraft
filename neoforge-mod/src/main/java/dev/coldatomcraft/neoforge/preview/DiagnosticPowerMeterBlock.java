package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.PreviewOpticalBench;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

/**
 * DEV-only detector with a visible placeholder model and three discrete
 * aperture presets. All readings are recomputed from authoritative server
 * blockstates on right-click; none are cached or presented as real instrument
 * calibration. No BlockEntity is needed for these finite, persisted states.
 */
public final class DiagnosticPowerMeterBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty APERTURE = IntegerProperty.create("aperture", 0, 2);

    public DiagnosticPowerMeterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(APERTURE, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Its FRONT FACE points toward the placing player, i.e. toward the beam source.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, APERTURE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            if (player.isShiftKeyDown()) {
                int next = (state.getValue(APERTURE) + 1) % PreviewOpticalBench.aperturePresetCount();
                level.setBlockAndUpdate(pos, state.setValue(APERTURE, next));
                player.displayClientMessage(Component.translatable(
                        "message.coldatomcraft.preview.aperture",
                        String.format(Locale.ROOT, "%.1f",
                                PreviewOpticalBench.apertureRadiusMeters(next) * 1000.0)), true);
            } else {
                player.displayClientMessage(DiagnosticBeamScan.measure(
                        serverLevel, pos, state.getValue(FACING), state.getValue(APERTURE)), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
