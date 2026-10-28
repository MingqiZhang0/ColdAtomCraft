package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.motion.PersistentApparatusIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Minimal SERVER-authoritative identity for D14 optics/meter and D21 table.
 * IDs are saved in the block entity, not carried in dropped items. Breaking
 * and replacing an apparatus therefore creates a new UUID; merely adjusting
 * an optic preset preserves its UUID and advances its revision.
 *
 * <p>IDs can be duplicated via privileged NBT/structure cloning and are not
 * cryptographic authority. No laser ON/motor authorization is granted here.</p>
 */
public final class DiagnosticApparatusIdentityBlockEntity extends BlockEntity {
    private static final String ID_KEY = "d22f_apparatus_uuid";
    private static final String REVISION_KEY = "d22f_apparatus_revision";
    private PersistentApparatusIdentity identity = PersistentApparatusIdentity.placed();

    public DiagnosticApparatusIdentityBlockEntity(BlockPos pos, BlockState state) {
        super(PreviewBlocks.APPARATUS_IDENTITY_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean identityUsable() { return identity.usable(); }
    public PersistentApparatusIdentity.Token identityToken() { return identity.token(); }
    public PersistentApparatusIdentity.Origin identityOrigin() { return identity.origin(); }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(ID_KEY, identity.storedId());
        output.putLong(REVISION_KEY, identity.storedRevision());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String storedId = input.getStringOr(ID_KEY, "");
        // If an ID exists but its revision is missing or malformed, reject
        // rather than silently resetting its saved configuration history.
        long revision = input.getLong(REVISION_KEY).orElse(storedId.isEmpty() ? 0L : -1L);
        identity = PersistentApparatusIdentity.restored(storedId, revision);
    }

    @Override public void onLoad() {
        super.onLoad();
        // A BE newly attached to an old D14 world must be saved with its new
        // ID. A malformed saved ID stays quarantined, not silently replaced.
        if (level instanceof ServerLevel && (identity.origin()
                == PersistentApparatusIdentity.Origin.LEGACY_MIGRATED
                || identity.origin() == PersistentApparatusIdentity.Origin.PLACED)) {
            setChanged();
        }
    }

    /**
     * Mark an observed CAC-controlled update if vanilla did not invoke
     * setBlockState. False is a fail-closed identity result, NEVER permission
     * for a scanning controller to continue or for a laser to turn on.
     */
    public boolean confirmControlledChange(BlockState before, BlockState after,
                                           PersistentApparatusIdentity.Token previous) {
        if (!(level instanceof ServerLevel server) || !identity.usable()
                || previous == null || before.getBlock() != after.getBlock()
                || server.getBlockEntity(worldPosition) != this
                || !server.getBlockState(worldPosition).equals(after)) return false;
        if (before.equals(after)) return identity.token().equals(previous);
        if (identity.token().equals(previous)) {
            identity = identity.changed();
            setChanged();
            return identity.usable();
        }
        return identity.token().uuid().equals(previous.uuid())
                && previous.revision() < Long.MAX_VALUE
                && identity.token().revision() == previous.revision() + 1L;
    }

    @SuppressWarnings("deprecation")
    @Override public void setBlockState(BlockState nextState) {
        BlockState prior = getBlockState();
        super.setBlockState(nextState);
        if (level instanceof ServerLevel && prior != nextState
                && prior.getBlock() == nextState.getBlock()) {
            identity = identity.changed();
            setChanged();
        }
    }
}
