package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Locale;

/** DEV read-only inspector: empty-hand click on a CAC optical table. */
public final class DiagnosticOpticalTableBlock extends Block implements EntityBlock {
    // The tabletop model ends at y=13/16, not a full block high.
    private static final VoxelShape TABLE_SHAPE = box(0, 0, 0, 16, 13, 16);

    public DiagnosticOpticalTableBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level,
                                              BlockPos pos, CollisionContext context) {
        return TABLE_SHAPE;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagnosticApparatusIdentityBlockEntity(pos, state);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                           Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        // A normal side click often hits the table below a low-profile rail.
        // Reserve verbose diagnostics for an intentional top-face sneak-click.
        // The overlay replaces itself instead of filling chat every time.
        BlockPos mounted = pos.above();
        if (!player.isShiftKeyDown() || hit.getDirection() != Direction.UP) {
            if (server.hasChunkAt(mounted)
                    && server.getBlockState(mounted).getBlock() instanceof DiagnosticOpticBlock) {
                player.sendOverlayMessage(Component.literal(
                        "[CAC] Optic directly on table: put a stage between the table and optic."));
            } else {
                player.sendOverlayMessage(Component.literal(
                        "[CAC] Hit TABLE, not stage. Aim at rail; sneak-click tabletop to inspect."));
            }
            return InteractionResult.SUCCESS;
        }
        if (!server.hasChunkAt(mounted)) {
            player.sendSystemMessage(Component.literal("[CAC D22E] Mounted chunk unloaded; no geometry read."));
            return InteractionResult.SUCCESS;
        }
        try {
            var snapshot = LoadedLabGeometryWorld.captureNominal(server, pos, List.of(pos, mounted));
            if (LoadedLabGeometryWorld.verify(server, snapshot) != LoadedApparatusSnapshot.Check.VALID) {
                player.sendSystemMessage(Component.literal("[CAC D22E] Geometry changed; retry."));
                return InteractionResult.SUCCESS;
            }
            var device = snapshot.devices().get(1);
            var center = device.centerMetres();
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "[CAC D22E] %s: local centre (%.4f, %.4f, %.4f) preview m; %s=%s; %s; %s",
                    device.state().kind(), center.x(), center.y(), center.z(),
                    device.state().axisMeaning(), device.state().axis(),
                    device.state().binding(), snapshot.provenance())));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            player.sendSystemMessage(Component.literal("[CAC D22E] No supported loaded apparatus "
                    + "above table, or geometry rejected."));
        }
        return InteractionResult.SUCCESS;
    }
}
