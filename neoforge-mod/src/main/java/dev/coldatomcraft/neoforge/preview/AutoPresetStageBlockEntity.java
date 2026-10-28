package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.measurement.PowerTraceArchive;
import dev.coldatomcraft.experiment.motion.DiscretePresetReport;
import dev.coldatomcraft.experiment.motion.DiscretePresetSweep;
import dev.coldatomcraft.experiment.motion.PersistentApparatusIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/**
 * D22B server-authoritative coarse *preset* scanner. No micromotion, encoder,
 * electrical power, automatic laser enable, or real motor is claimed.
 * The D14 solver observes the actual mounted BlockState on a later server tick.
 */
public final class AutoPresetStageBlockEntity extends BlockEntity {
    private static final String ID_KEY = "d22b_stage_id";
    private static final String DATA_KEY = "d22b_completed_samples";
    private String stageId = UUID.randomUUID().toString();
    private List<DiscretePresetSweep.Point> lastCompleted = List.of();
    private DiscretePresetSweep scan; // NEVER saved: chunk unload or restart disarms it
    private BlockPos meterPos;
    private BlockState initialMeter;
    private BlockState initialOptic;
    private DiagnosticApparatusIdentityBlockEntity tableIdentity;
    private DiagnosticApparatusIdentityBlockEntity opticIdentity;
    private DiagnosticApparatusIdentityBlockEntity meterIdentity;
    private PersistentApparatusIdentity.Token tableToken;
    private PersistentApparatusIdentity.Token opticToken;
    private PersistentApparatusIdentity.Token meterToken;

    private int commandedPreset;
    private String lastOutcome = "No scan completed";

    public AutoPresetStageBlockEntity(BlockPos pos, BlockState state) {
        super(PreviewBlocks.AUTO_PRESET_STAGE_BLOCK_ENTITY.get(), pos, state);
    }
    public boolean scanning() { return scan != null; }
    public List<DiscretePresetSweep.Point> lastCompleted() { return lastCompleted; }
    public String status() { return lastOutcome + (scanning() ? " (RUNNING)" : ""); }

    /** Validated from the clicked server block, not a client-supplied position. */
    String startOrStop(ServerLevel server) {
        if (scan != null) {
            scan.cancel();
            finish(server, "Operator stopped scan");
            return "[CAC D22B] Scan stopped; automatic laser ON is never used.";
        }
        if (!server.hasChunkAt(worldPosition.below())
                || !server.getBlockState(worldPosition.below()).is(PreviewBlocks.OPTICAL_TABLE.get())) {
            return "[CAC D22B] Place the stage on a CAC optical table.";
        }
        BlockPos mounted = worldPosition.above();
        if (!server.hasChunkAt(mounted)) return "[CAC D22B] Mounted optic chunk unloaded.";
        BlockState optic = server.getBlockState(mounted);
        if (!(optic.getBlock() instanceof DiagnosticOpticBlock)
                || !(server.getBlockEntity(mounted) instanceof DiagnosticApparatusIdentityBlockEntity
                        mountedIdentity) || !mountedIdentity.identityUsable()) {
            return "[CAC D22F] Mounted optic needs a valid persistent identity.";
        }
        var meter = nearestMeter(server);
        if (meter == null) return "[CAC D22B] Need a unique nearest loaded D14 meter within 8 blocks.";
        // D22E: acquire a same-tick, server-only geometry snapshot before
        // admitting a scan. This is a read-side integrity check, NOT motor
        // encoder proof or permission to automatically energize a laser.
        try {
            var geometry = LoadedLabGeometryWorld.captureNominal(server, worldPosition,
                    List.of(worldPosition.below(), worldPosition, mounted, meter));
            if (LoadedLabGeometryWorld.verify(server, geometry)
                    != dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.Check.VALID
                    || server.getBlockEntity(worldPosition) != this) {
                return "[CAC D22E] Scan rig changed during geometry inspection.";
            }
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return "[CAC D22E] Scan rig geometry unavailable; scan not started.";
        }
        if (!(server.getBlockEntity(worldPosition.below()) instanceof DiagnosticApparatusIdentityBlockEntity table)
                || !(server.getBlockEntity(meter) instanceof DiagnosticApparatusIdentityBlockEntity sensor)
                || !table.identityUsable() || !sensor.identityUsable()) {
            return "[CAC D22F] Table or meter has missing/quarantined apparatus identity.";
        }
        tableIdentity = table;
        opticIdentity = mountedIdentity;
        meterIdentity = sensor;
        tableToken = table.identityToken();
        opticToken = mountedIdentity.identityToken();
        meterToken = sensor.identityToken();
        initialOptic = optic;
        meterPos = meter;
        initialMeter = server.getBlockState(meter);
        commandedPreset = optic.getValue(DiagnosticOpticBlock.PORT).selector();
        scan = new DiscretePresetSweep();
        lastOutcome = "Scan requested; 4 D14 selector states, not calibrated angles";
        setChanged();
        return "[CAC D22B] Started four-preset server scan; nearby meter at " + meter
                + ". Laser stays under manual control.";
    }

    /** One server tick per callback, all world accesses guarded by loaded chunks. */
    void serverTick(Level level) {
        if (!(level instanceof ServerLevel server) || scan == null) return;
        BlockPos mounted = worldPosition.above();
        boolean valid = server.hasChunkAt(worldPosition.below())
                && server.getBlockState(worldPosition.below()).is(PreviewBlocks.OPTICAL_TABLE.get())
                && server.hasChunkAt(mounted) && server.hasChunkAt(meterPos);
        BlockState state = valid ? server.getBlockState(mounted) : null;
        if (valid) {
            valid = state.getBlock() == initialOptic.getBlock()
                    && state.getValue(DiagnosticOpticBlock.PORT).incoming()
                            == initialOptic.getValue(DiagnosticOpticBlock.PORT).incoming()
                    && identitiesStillMatch(server, mounted)
                    && server.getBlockState(meterPos).equals(initialMeter);
        }
        if (valid) {
            // Reacquire each server tick. Never cache a BE reference or local
            // SI position beyond the exact snapshot tick; a block may unload.
            try {
                var geometry = LoadedLabGeometryWorld.captureNominal(server, worldPosition,
                        List.of(worldPosition.below(), worldPosition, mounted, meterPos));
                valid = LoadedLabGeometryWorld.verify(server, geometry)
                        == dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.Check.VALID
                        && server.getBlockEntity(worldPosition) == this;
            } catch (IllegalArgumentException | IllegalStateException invalidGeometry) {
                valid = false;
            }
        }
        int actual = valid ? state.getValue(DiagnosticOpticBlock.PORT).selector() : -1;
        PowerTrace.Sample observation = null;
        if (valid && scan.phase() == DiscretePresetSweep.Phase.AWAIT_SAMPLE
                && actual == scan.requestedPreset()) {
            try {
                observation = readPower(server, meterPos, server.getGameTime());
            } catch (RuntimeException exception) {
                // The solver can reject a complex/invalid network. It must not
                // silently contribute a measured zero or advance the scan.
                scan.cancel();
                finish(server, "Server optical solver rejected measurement: "
                        + exception.getClass().getSimpleName());
                return;
            }
        }
        var decision = scan.advance(server.getGameTime(), actual, valid, observation);
        switch (decision.action()) {
            case SET_PRESET -> {
                // Use the actual D14 property; future optical solves see this same state.
                var target = state.setValue(DiagnosticOpticBlock.PORT,
                        OpticPortPreset.of(state.getValue(DiagnosticOpticBlock.PORT).incoming(),
                                decision.preset()));
                if (!server.setBlockAndUpdate(mounted, target)) {
                    scan.cancel();
                    finish(server, "Mounted optic state update rejected");
                } else {
                    // Refresh only OUR OWN commanded revision. Player mutations
                    // or device replacement are never silently adopted.
                    if (!opticIdentity.confirmControlledChange(state, target, opticToken)) {
                        scan.cancel();
                        finish(server, "Persistent optic identity changed during set-preset");
                        return;
                    }
                    opticToken = opticIdentity.identityToken();
                    commandedPreset = decision.preset();
                }
            }
            case FINISHED -> {
                lastCompleted = DiscretePresetReport.validated(scan.readings());
                setChanged();
                finish(server, "Four-preset scan completed");
                // Auto-export results on the server; also available by sneak-right-click.
                try { export(server); }
                catch (IOException | IllegalArgumentException exc) {
                    lastOutcome += "; CSV/SVG export failed (" + exc.getClass().getSimpleName() + ")";
                }
            }
            case STOP -> finish(server, "Scan aborted: " + decision.reason());
            case WAIT, READ -> { /* Observation can only be delivered on next tick. */ }
        }
    }

    private void finish(ServerLevel server, String message) {
        // Restore original selector only when our own last commanded state is still present.
        // Never overwrite a player's conflicting edit or operate an unloaded chunk.
        BlockPos mounted = worldPosition.above();
        boolean restored = false;
        if (initialOptic != null && server.hasChunkAt(mounted)) {
            BlockState state = server.getBlockState(mounted);
            if (state.getBlock() == initialOptic.getBlock()
                    && state.getValue(DiagnosticOpticBlock.PORT).incoming()
                          == initialOptic.getValue(DiagnosticOpticBlock.PORT).incoming()
                    && state.getValue(DiagnosticOpticBlock.PORT).selector() == commandedPreset
                    && identitiesStillMatch(server, mounted)) {
                var before = opticToken;
                restored = server.setBlockAndUpdate(mounted, initialOptic);
                if (restored) opticIdentity.confirmControlledChange(state, initialOptic, before);
            }
        }
        scan = null;
        tableIdentity = null;
        opticIdentity = null;
        meterIdentity = null;
        tableToken = null;
        opticToken = null;
        meterToken = null;
        initialOptic = null;
        initialMeter = null;
        meterPos = null;
        lastOutcome = message + (restored ? "; original optic preset restored"
                : "; WARNING: verify/reset mounted optic manually");
        setChanged();
    }

    /** No world chunk loads; a placed-new identical optic is still rejected. */
    private boolean identitiesStillMatch(ServerLevel server, BlockPos mounted) {
        return tableIdentity != null && opticIdentity != null && meterIdentity != null
                && server.hasChunkAt(worldPosition.below())
                && server.hasChunkAt(mounted) && server.hasChunkAt(meterPos)
                && server.getBlockEntity(worldPosition.below()) == tableIdentity
                && server.getBlockEntity(mounted) == opticIdentity
                && server.getBlockEntity(meterPos) == meterIdentity
                && tableIdentity.identityUsable() && opticIdentity.identityUsable()
                && meterIdentity.identityUsable()
                && tableToken.equals(tableIdentity.identityToken())
                && opticToken.equals(opticIdentity.identityToken())
                && meterToken.equals(meterIdentity.identityToken());
    }

    private BlockPos nearestMeter(ServerLevel server) {
        BlockPos best = null;
        int nearest = Integer.MAX_VALUE;
        boolean tied = false;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    int distance = dx*dx + dz*dz + dy*dy;
                    if (distance > 64 || distance == 0) continue;
                    BlockPos pos = worldPosition.offset(dx, dy, dz);
                    if (!server.hasChunkAt(pos) || !server.getBlockState(pos).is(PreviewBlocks.METER.get())) continue;
                    if (distance < nearest) { nearest = distance; best = pos; tied = false; }
                    else if (distance == nearest) tied = true;
                }
            }
        }
        return tied ? null : best;
    }

    private static PowerTrace.Sample readPower(ServerLevel server, BlockPos meter, long tick) {
        var reading = DiagnosticBeamScan.sample(server, meter);
        return switch (reading.status()) {
            case MEASURED -> new PowerTrace.Sample(tick, PowerTrace.Status.MEASURED, reading.watts());
            case NO_BEAM -> new PowerTrace.Sample(tick, PowerTrace.Status.NO_BEAM, 0.0);
            case MULTIPLE_UNRESOLVED -> new PowerTrace.Sample(tick, PowerTrace.Status.MULTIPLE_UNRESOLVED, 0.0);
            case INCOMPLETE -> new PowerTrace.Sample(tick, PowerTrace.Status.INCOMPLETE, 0.0);
        };
    }

    String export(ServerLevel server) throws IOException {
        if (scan != null) return "[CAC D22B] Stop the running scan before exporting.";
        if (lastCompleted.size() != DiscretePresetSweep.PRESETS) return "[CAC D22B] No complete 4-point scan to export.";
        String name = safeId(server);
        Path root = server.getServer().getWorldPath(LevelResource.ROOT);
        Path data = PowerTraceArchive.csv(root, name);
        Path image = PowerTraceArchive.svg(root, name);
        Files.createDirectories(data.getParent());
        for (Path file : List.of(data, image)) {
            if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())
                    || Files.isSymbolicLink(file.getParent().getParent())
                    || (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("unsafe stage scan export path");
            }
        }
        Files.writeString(data, DiscretePresetReport.csv(lastCompleted), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(image, DiscretePresetReport.svg(lastCompleted), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return "[CAC D22B] Saved server CSV/SVG four-preset diagnostic scan (not P(theta)) to "
                + data.getParent();
    }

    private String safeId(ServerLevel level) {
        int dimensionHash = level.dimension().identifier().toString().hashCode();
        return stageId + "_" + Integer.toHexString(dimensionHash)
                + "_" + worldPosition.getX() + "_" + worldPosition.getY() + "_" + worldPosition.getZ();
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(ID_KEY, stageId);
        output.putString(DATA_KEY, DiscretePresetReport.csv(lastCompleted));
        // The controller is NEVER persisted: save/load must not resume movement.
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        scan = null;
        tableIdentity = null;
        opticIdentity = null;
        meterIdentity = null;
        tableToken = null;
        opticToken = null;
        meterToken = null;
        initialOptic = null;
        initialMeter = null;
        meterPos = null;
        String savedId = input.getStringOr(ID_KEY, "");
        if (savedId.matches("[0-9a-f-]{36}")) stageId = savedId;
        else if (!savedId.isEmpty()) {
            lastCompleted = List.of();
            lastOutcome = "Invalid saved scanner identity; dataset refused";
            return;
        }
        try {
            lastCompleted = DiscretePresetReport.decode(input.getStringOr(DATA_KEY, ""));
        } catch (IllegalArgumentException exc) {
            lastCompleted = List.of();
            lastOutcome = "Invalid previous scan dataset discarded; no scan resumed";
        }
    }
}
