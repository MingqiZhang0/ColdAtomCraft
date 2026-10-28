package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Four DEV-only ideal components. No Blender asset or experimental coating is
 * implied. All 24 port/preset states are stored in the ordinary blockstate.
 *
 * <p>Right click: cycle the four reflected directions, lens focal presets or
 * half-wave plate axis presets. Sneak-right-click: cycle the input port among
 * the six world directions. Inputs are one-directional by design; all other
 * ports are treated as opaque until a reciprocal multiport model exists.</p>
 */
public final class DiagnosticOpticBlock extends Block implements EntityBlock {
    public enum Kind { MIRROR, SPLITTER, LENS, HALF_WAVE_PLATE }
    public static final EnumProperty<OpticPortPreset> PORT =
            EnumProperty.create("port", OpticPortPreset.class);

    private final Kind kind;

    public DiagnosticOpticBlock(Kind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = java.util.Objects.requireNonNull(kind, "optic kind");
        registerDefaultState(stateDefinition.any().setValue(PORT, OpticPortPreset.SOUTH_0));
    }
    public Kind kind() { return kind; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagnosticApparatusIdentityBlockEntity(pos, state);
    }


    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagnosticApparatusIdentityBlockEntity(pos, state);
    }


    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // PORT is the INCOMING LIGHT TRAVEL DIRECTION, not the visible front.
        // The player looks along the incident beam; the model's front faces back
        // toward the player (opposite to incoming travel) via the blockstate model.
        var direction = OpticalBenchWorld.fromMinecraft(context.getHorizontalDirection());
        return defaultBlockState().setValue(PORT, OpticPortPreset.of(direction, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PORT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return rotate(state, level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        return rotate(state, level, pos, player);
    }

    private InteractionResult rotate(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            OpticPortPreset current = state.getValue(PORT);
            OpticPortPreset next = player.isShiftKeyDown()
                    ? current.nextIncoming() : current.nextSelector();
            BlockState changed = state.setValue(PORT, next);
            var identity = level.getBlockEntity(pos) instanceof DiagnosticApparatusIdentityBlockEntity be
                    && be.identityUsable() ? be : null;
            var previous = identity == null ? null : identity.identityToken();
            if (level.setBlockAndUpdate(pos, changed) && identity != null) {
                identity.confirmControlledChange(state, changed, previous);
            }
            String description = switch (kind) {
                case MIRROR, SPLITTER -> next.reflected().name();
                case LENS -> String.format(java.util.Locale.ROOT, "f = %.2f m", next.focalLengthMeters());
                case HALF_WAVE_PLATE -> String.format(java.util.Locale.ROOT,
                        "axis = %.1f deg", Math.toDegrees(next.waveplateAxisRadians()));
            };
            player.sendOverlayMessage(Component.translatable("message.coldatomcraft.d14.optic_ports",
                    next.incoming().name(), description));
        }
        return InteractionResult.SUCCESS;
    }
}
