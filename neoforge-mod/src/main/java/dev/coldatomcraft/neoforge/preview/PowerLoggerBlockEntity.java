package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.measurement.PowerTraceComparison;
import dev.coldatomcraft.experiment.measurement.PowerTraceArchive;
import dev.coldatomcraft.experiment.measurement.PowerTraceHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;

/**
 * DEV-only sampled optical observation. Writes append-only CSV on the server,
 * retaining a bounded preview in the world save for SVG export. No electricity
 * is consumed and no physical instrument calibration is claimed.
 */
public final class PowerLoggerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SAMPLE_PERIOD_GAME_TICKS = 100;
    private static final String ID_KEY = "d21_recorder_id";
    private static final String SAMPLES_KEY = "d21_recent_samples";
    private static final String ERROR_KEY = "d21_io_or_save_error";

    private String recorderId = UUID.randomUUID().toString();
    private PowerTrace recent = new PowerTrace();
    private boolean recording;
    private boolean storageError;

    public PowerLoggerBlockEntity(BlockPos pos, BlockState state) {
        super(PreviewBlocks.LOGGER_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean recording() { return recording; }
    public boolean storageError() { return storageError; }
    public List<PowerTrace.Sample> recentSamples() { return recent.samples(); }
    public long recentLastTick() { return recent.lastTick(); }

    /** Historical pages are resolved only from this BlockEntity's trusted ID. */
    PowerTraceHistory.Page archiveNewest(ServerLevel server) throws IOException {
        return PowerTraceHistory.newest(root(server), safeId(server));
    }
    PowerTraceHistory.Page archiveOlder(ServerLevel server, long end, long snapshotEnd)
            throws IOException {
        return PowerTraceHistory.older(root(server), safeId(server), end, snapshotEnd);
    }
    PowerTraceHistory.Page archiveNewer(ServerLevel server, long start, long snapshotEnd)
            throws IOException {
        return PowerTraceHistory.newer(root(server), safeId(server), start, snapshotEnd);
    }

    /** Do not continue collecting data into an archive that fails integrity checks. */
    void archiveReadFailed() {
        recording = false;
        storageError = true;
        setChanged();
    }
    public int recentSize() { return recent.size(); }

    @Override public Component getDisplayName() {
        return Component.translatable("gui.coldatomcraft.logger.title");
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PowerLoggerMenu(id, inventory, this);
    }

    public String toggle(ServerLevel level) {
        if (recording) {
            recording = false;
            setChanged();
            return "[CAC D21] Recorder PAUSED. Sneak-right-click to export CSV/SVG.";
        }
        // A replaced world clock would otherwise intermingle unrelated histories.
        if (storageError || recent.lastTick() >= level.getGameTime()) {
            return "[CAC D21] Recorder locked: invalid saved state, disk error or non-monotonic clock.";
        }
        recording = true;
        setChanged();
        return "[CAC D21] Recording ON (1 sample / 100 game ticks). "+
                "Exactly one adjacent power meter is required; invalid readings are saved as gaps.";
    }

    /** A bounded, server-only export of the page actually displayed in history mode. */
    String exportHistoryPage(ServerLevel level, List<PowerTrace.Sample> samples) {
        try {
            PowerTraceArchive.exportHistoryPage(root(level),safeId(level),samples);
            return "[CAC D21C] Archived page CSV/SVG exported in the SERVER world measurements folder.";
        } catch (IOException | IllegalArgumentException exc) {
            return "[CAC D21C] ARCHIVE EXPORT FAILED: " + exc.getClass().getSimpleName();
        }
    }

    /** Compare exactly the bounded samples selected by the server menu. */
    String exportComparisonPage(ServerLevel level, PowerTraceComparison.Frame frame) {
        try {
            PowerTraceArchive.exportComparisonPage(root(level),safeId(level),frame);
            return "[CAC D21D] Dual-channel page exported on SERVER (.compare-page.csv/.svg).";
        } catch (IOException | IllegalArgumentException exception) {
            return "[CAC D21D] EXPORT FAILED: " + exception.getClass().getSimpleName();
        }
    }

    public String export(ServerLevel level) {
        Path root = root(level);
        String name = safeId(level);
        try {
            Path image = PowerTraceArchive.exportSvg(root,name,recent);
            Path csv = PowerTraceArchive.csv(root,name);
            String csvStatus = Files.exists(csv) ? csv.toString() : "no CSV yet";
            return "[CAC D21] Saved SVG graph: " + image + " | data: " + csvStatus
                    + " | recent points: " + recent.size();
        } catch (IOException | IllegalArgumentException exc) {
            storageError = true;
            recording = false;
            setChanged();
            return "[CAC D21] EXPORT FAILED; recording paused: " + exc.getClass().getSimpleName();
        }
    }

    /** Never tick or read client-owned optical state. */
    void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !recording) return;
        long tick = server.getGameTime();
        long phase = Math.floorMod(pos.asLong(), SAMPLE_PERIOD_GAME_TICKS);
        if (Math.floorMod(tick + phase, SAMPLE_PERIOD_GAME_TICKS) != 0) return;
        if (recent.lastTick() >= tick) {
            recording = false;
            storageError = true;
            setChanged();
            return;
        }
        PowerTrace.Sample sample = capture(server, pos, tick);
        try {
            PowerTraceArchive.append(root(server), safeId(server), sample);
        } catch (IOException | IllegalArgumentException exc) {
            // No false claim of successful long-term collection after a disk error.
            recording = false;
            storageError = true;
            setChanged();
            return;
        }
        recent.append(sample);
        setChanged();
    }

    private static PowerTrace.Sample capture(ServerLevel level, BlockPos loggerPos, long tick) {
        BlockPos meterPos = null;
        boolean unknownNeighbor = false;
        for (Direction direction : Direction.values()) {
            BlockPos candidate = loggerPos.relative(direction);
            if (!level.hasChunkAt(candidate)) {
                unknownNeighbor = true;
                continue;
            }
            if (!level.getBlockState(candidate).is(PreviewBlocks.METER.get())) continue;
            if (meterPos != null) {
                return new PowerTrace.Sample(tick, PowerTrace.Status.MULTIPLE_METERS, 0);
            }
            meterPos = candidate;
        }
        if (unknownNeighbor) {
            return new PowerTrace.Sample(tick, PowerTrace.Status.ADJACENT_UNLOADED, 0);
        }
        if (meterPos == null) return new PowerTrace.Sample(tick, PowerTrace.Status.NO_METER, 0);
        var optical = DiagnosticBeamScan.sample(level, meterPos);
        return switch (optical.status()) {
            case MEASURED -> new PowerTrace.Sample(tick, PowerTrace.Status.MEASURED, optical.watts());
            case NO_BEAM -> new PowerTrace.Sample(tick, PowerTrace.Status.NO_BEAM, 0);
            case MULTIPLE_UNRESOLVED -> new PowerTrace.Sample(tick, PowerTrace.Status.MULTIPLE_UNRESOLVED, 0);
            case INCOMPLETE -> new PowerTrace.Sample(tick, PowerTrace.Status.INCOMPLETE, 0);
        };
    }

    private Path root(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT);
    }
    private String safeId(ServerLevel level) {
        int dimensionHash = level.dimension().identifier().toString().hashCode();
        return recorderId + "_" + Integer.toHexString(dimensionHash)
                + "_" + worldPosition.getX() + "_" + worldPosition.getY() + "_" + worldPosition.getZ();
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(ID_KEY, recorderId);
        output.putString(SAMPLES_KEY, recent.encode());
        output.putInt(ERROR_KEY, storageError ? 1 : 0);
        // Deliberately omit the ON flag: restart/unload never auto-resumes.
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        recording = false;
        storageError = input.getIntOr(ERROR_KEY,0) != 0;
        String id = input.getStringOr(ID_KEY, "");
        if (id.matches("[0-9a-f-]{36}")) recorderId = id;
        else if (!id.isEmpty()) storageError = true;
        try {
            recent = PowerTrace.decode(input.getStringOr(SAMPLES_KEY, ""));
        } catch (IllegalArgumentException exc) {
            recent = new PowerTrace();
            storageError = true;
        }
    }
}
