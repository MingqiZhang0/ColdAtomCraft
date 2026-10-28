package dev.coldatomcraft.neoforge.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Manual, coarse grid DEV stage. Not a motor, no microposition, no FE or SU.
 * Operates only on the four D14 optic block types. Moving an optic by a
 * block destroys/recreates its identity BE (new device ID); this is NOT a
 * continuous translation or hardware-preserving move.
 */
public final class DiagnosticStageBlock extends Block {
    public enum Kind { LINEAR, ROTARY }
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Kind kind;

    // Visible D21 rail is only six model units high, not a full 16-unit cube.
    // Directional envelopes follow the JSON model after FACING rotation.
    private static final VoxelShape LINEAR_Z_SHAPE = box(1, 0, 0, 15, 6, 16);
    private static final VoxelShape LINEAR_X_SHAPE = box(0, 0, 1, 16, 6, 15);
    private static final VoxelShape ROTARY_SHAPE = box(1, 0, 1, 15, 7, 15);

    public DiagnosticStageBlock(Kind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = java.util.Objects.requireNonNull(kind,"stage kind");
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(FACING);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level,
                                              BlockPos pos, CollisionContext context) {
        if (kind == Kind.ROTARY) return ROTARY_SHAPE;
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? LINEAR_Z_SHAPE : LINEAR_X_SHAPE;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return interact(state,level,pos,player);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        return interact(state,level,pos,player);
    }

    private InteractionResult interact(BlockState stage, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            String feedback = !supported(level,pos) ?
                    "Place stage on a CAC optical table; add one D14 optic immediately above it." :
                    (kind == Kind.LINEAR ? translate(stage,level,pos,player.isShiftKeyDown()) :
                            rotate(level,pos,player.isShiftKeyDown()));
            player.sendSystemMessage(Component.literal("[CAC D21 DEV] "+feedback));
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean supported(Level level,BlockPos pos) {
        return level.hasChunkAt(pos.below()) &&
                level.getBlockState(pos.below()).is(PreviewBlocks.OPTICAL_TABLE.get());
    }

    private String translate(BlockState stage, Level level, BlockPos pos, boolean reverse) {
        BlockPos from=pos.above();
        if (!level.hasChunkAt(from)) return "Mounted optic chunk unloaded; no motion.";
        BlockState optic=level.getBlockState(from);
        if (!(optic.getBlock() instanceof DiagnosticOpticBlock) || !(level.getBlockEntity(from) instanceof DiagnosticApparatusIdentityBlockEntity identity
                && identity.identityUsable())) {
            return "Missing valid D22F optic identity above this rail; no motion.";
        }
        Direction step=stage.getValue(FACING);
        if (reverse) step=step.getOpposite();
        BlockPos otherRail=pos.relative(step);
        BlockPos target=otherRail.above();
        if (!level.hasChunkAt(otherRail) || !level.hasChunkAt(target)
                || !level.hasChunkAt(otherRail.below())) return "Destination chunk unloaded.";
        BlockState other=level.getBlockState(otherRail);
        if (!other.is(PreviewBlocks.LINEAR_STAGE.get()) ||
                other.getValue(FACING).getAxis() != step.getAxis() ||
                !level.getBlockState(otherRail.below()).is(PreviewBlocks.OPTICAL_TABLE.get()) ||
                !level.getBlockState(target).isAir()) return "Need empty target above aligned rail/table.";
        // Server-thread two-step DEV migration. If second step fails, undo the
        // target; do not duplicate an optic. Full transactional motion deferred.
        if (!level.setBlockAndUpdate(target,optic)) return "Could not place destination optic.";
        if (!level.removeBlock(from,false)) {
            level.removeBlock(target,false);
            return "Source removal failed; attempted destination rollback.";
        }
        return "Moved one Minecraft block "+step+" (NOT a precision slide).";
    }

    private String rotate(Level level, BlockPos pos, boolean reverse) {
        BlockPos mount=pos.above();
        if (!level.hasChunkAt(mount)) return "Mounted optic chunk unloaded.";
        BlockState optic=level.getBlockState(mount);
        if (!(optic.getBlock() instanceof DiagnosticOpticBlock) || !(level.getBlockEntity(mount) instanceof DiagnosticApparatusIdentityBlockEntity identity
                && identity.identityUsable())) {
            return "Missing valid D22F optic identity above rotation stage.";
        }
        OpticPortPreset previous=optic.getValue(DiagnosticOpticBlock.PORT);
        Direction incoming=OpticalBenchWorld.toMinecraft(previous.incoming());
        if (incoming.getAxis().isVertical()) {
            return "Vertical incoming beams need a separate axial rotation model.";
        }
        Direction next=reverse?incoming.getCounterClockWise():incoming.getClockWise();
        OpticPortPreset value=OpticPortPreset.of(OpticalBenchWorld.fromMinecraft(next),previous.selector());
        var before = identity.identityToken();
        BlockState changed = optic.setValue(DiagnosticOpticBlock.PORT, value);
        if (!level.setBlockAndUpdate(mount, changed)) return "Failed to rotate optic.";
        identity.confirmControlledChange(optic, changed, before);
        return "Rotated incoming port 90 degrees to "+next+"; preserved optical selector.";
    }
}
