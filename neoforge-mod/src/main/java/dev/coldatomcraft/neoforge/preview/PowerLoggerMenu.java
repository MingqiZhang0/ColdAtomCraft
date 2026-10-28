package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.measurement.PowerTraceComparison;
import dev.coldatomcraft.experiment.measurement.PowerTraceHistory;
import dev.coldatomcraft.experiment.measurement.PowerTraceMenuCodec;
import dev.coldatomcraft.experiment.measurement.PowerTracePage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * D21B read-only historical graph transport. All values come from the server
 * BlockEntity, authenticated via the vanilla container/menu button channel.
 * Each 64-bit sample field is split into signed 16-bit menu data words.
 * No packet can request a raw server path or arbitrary recorder identity.
 */
public final class PowerLoggerMenu extends AbstractContainerMenu {
    public static final int BUTTON_OLDER = 0;
    public static final int BUTTON_NEWER = 1;
    public static final int BUTTON_REFRESH = 2;
    public static final int BUTTON_TOGGLE = 3;
    public static final int BUTTON_EXPORT = 4;
    public static final int BUTTON_RANGE = 5;
    public static final int BUTTON_HISTORY = 6;
    public static final int BUTTON_COMPARE = 7;
    public static final int BUTTON_NEXT_CHANNEL = 8;

    public static final int RECORDING = 0;
    public static final int STORAGE_ERROR = 1;
    public static final int CURRENT_PAGE = 2;
    public static final int TOTAL_PAGES = 3;
    public static final int VISIBLE_COUNT = 4;
    public static final int FILTERED_COUNT = 5;
    public static final int RANGE = 6;
    public static final int VIEW_HISTORY = 7;
    public static final int HAS_OLDER = 8;
    public static final int HISTORY_ERROR = 9;
    public static final int COMPARE_ACTIVE = 10;
    public static final int SECONDARY_VISIBLE_COUNT = 11;
    public static final int SECONDARY_DX = 12;
    public static final int SECONDARY_DY = 13;
    public static final int SECONDARY_DZ = 14;
    public static final int DATA_START = 15;
    public static final int SECONDARY_START = DATA_START
            + PowerTracePage.PAGE_SIZE * PowerTraceMenuCodec.WORDS_PER_SAMPLE;
    public static final int CHECK_LOW = SECONDARY_START
            + PowerTracePage.PAGE_SIZE * PowerTraceMenuCodec.WORDS_PER_SAMPLE;
    public static final int CHECK_HIGH = CHECK_LOW + 1;
    public static final int DATA_COUNT = CHECK_HIGH + 1;

    private final ContainerData data;
    private final PowerLoggerBlockEntity logger; // null only for the client mirror
    private final Level level;
    private final BlockPos pos;
    private final Player viewer;
    private PowerLoggerBlockEntity secondary;
    private BlockPos secondaryPos;
    private List<PowerTrace.Sample> secondaryFiltered = List.of();
    private List<PowerTrace.Sample> secondaryPage = List.of();
    private long secondaryLastTick = -1;
    private int secondarySize;
    private boolean comparing;
    private List<PowerTrace.Sample> filtered = List.of();
    private int range = PowerTracePage.RANGE_ALL;
    private int page;
    private long snapshotLastTick = -1;
    private int snapshotSize;
    private long nextExportTick = Long.MIN_VALUE;
    private long nextRefreshTick = Long.MIN_VALUE;
    private long nextHistoryTick = Long.MIN_VALUE;
    private boolean historical;
    private boolean historyError;
    private PowerTraceHistory.Page historyPage = PowerTraceHistory.Page.empty();

    public PowerLoggerMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public PowerLoggerMenu(int id, Inventory inventory, PowerLoggerBlockEntity logger) {
        super(PreviewBlocks.LOGGER_MENU.get(), id);
        this.logger = logger;
        this.level = logger == null ? null : logger.getLevel();
        this.pos = logger == null ? null : logger.getBlockPos().immutable();
        this.viewer = inventory.player;
        this.data = new SimpleContainerData(DATA_COUNT);
        if (logger != null) refreshSnapshot();
        addDataSlots(data);
    }

    /** The copied page is changed only by validated server menu button actions. */
    private void refreshSnapshot() {
        filtered = PowerTracePage.filter(logger.recentSamples(), range);
        snapshotLastTick = logger.recentLastTick();
        snapshotSize = logger.recentSize();
        page = 0;
        if (comparing) {
            if (secondaryBound()) snapshotSecondary();
            else clearComparison();
        }
        publishPage();
    }

    private void snapshotSecondary() {
        secondaryFiltered = PowerTracePage.filter(secondary.recentSamples(), range);
        secondaryLastTick = secondary.recentLastTick();
        secondarySize = secondary.recentSize();
    }

    private void clearComparison() {
        comparing = false;
        secondary = null;
        secondaryPos = null;
        secondaryFiltered = List.of();
        secondaryPage = List.of();
        secondaryLastTick = -1;
        secondarySize = 0;
    }

    /** Only same-dimension loaded recorders; both must be within player reach. */
    private boolean secondaryBound() {
        if (!(level instanceof ServerLevel server) || secondary == null || secondaryPos == null
                || viewer.level() != level || !server.hasChunkAt(secondaryPos)
                || server.getBlockEntity(secondaryPos) != secondary
                || !server.getBlockState(secondaryPos).is(PreviewBlocks.LOGGER.get())) return false;
        long dx = (long) secondaryPos.getX() - pos.getX();
        long dy = (long) secondaryPos.getY() - pos.getY();
        long dz = (long) secondaryPos.getZ() - pos.getZ();
        return dx*dx + dy*dy + dz*dz <= 64
                && viewer.distanceToSqr(secondaryPos.getX()+0.5,
                        secondaryPos.getY()+0.5, secondaryPos.getZ()+0.5) <= 64.0;
    }

    /** Bounded discovery on button press only: 13*5*13 candidate cells. */
    private List<PowerLoggerBlockEntity> nearbyRecorders(ServerLevel server) {
        ArrayList<PowerLoggerBlockEntity> choices = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (dx*dx + dy*dy + dz*dz > 64) continue;
                    BlockPos target = pos.offset(dx, dy, dz);
                    if (!server.hasChunkAt(target)
                            || !server.getBlockState(target).is(PreviewBlocks.LOGGER.get())
                            || !(server.getBlockEntity(target) instanceof PowerLoggerBlockEntity found)
                            || viewer.distanceToSqr(target.getX()+0.5,
                                    target.getY()+0.5,target.getZ()+0.5) > 64.0) continue;
                    choices.add(found);
                }
            }
        }
        choices.sort(Comparator
                .comparingLong((PowerLoggerBlockEntity e) -> {
                    BlockPos b = e.getBlockPos();
                    long dx = (long)b.getX() - pos.getX();
                    long dy = (long)b.getY() - pos.getY();
                    long dz = (long)b.getZ() - pos.getZ();
                    return dx*dx + dy*dy + dz*dz;
                })
                .thenComparingInt(e -> e.getBlockPos().getX())
                .thenComparingInt(e -> e.getBlockPos().getY())
                .thenComparingInt(e -> e.getBlockPos().getZ()));
        return List.copyOf(choices);
    }

    private void publishPage() {
        List<PowerTrace.Sample> visible = historical ? historyPage.samples()
                : PowerTracePage.page(filtered, page);
        if (comparing && !historical) {
            try {
                secondaryPage = PowerTraceComparison.secondaryWindow(visible, secondaryFiltered);
            } catch (IllegalArgumentException invalid) {
                // No implicit downsampling; fail closed if an unusual source
                // generated >32 readings in one primary page time interval.
                clearComparison();
            }
        } else secondaryPage = List.of();
        data.set(COMPARE_ACTIVE, comparing ? 1 : 0);
        data.set(SECONDARY_VISIBLE_COUNT, secondaryPage.size());
        data.set(SECONDARY_DX, comparing ? secondaryPos.getX()-pos.getX() : 0);
        data.set(SECONDARY_DY, comparing ? secondaryPos.getY()-pos.getY() : 0);
        data.set(SECONDARY_DZ, comparing ? secondaryPos.getZ()-pos.getZ() : 0);
        data.set(CURRENT_PAGE, page);
        // Archive page count is not scanned: a '+' denotes more older pages.
        data.set(TOTAL_PAGES, historical ? page + 1 + (historyPage.hasOlder() ? 1 : 0)
                : PowerTracePage.pages(filtered));
        data.set(VISIBLE_COUNT, visible.size());
        data.set(FILTERED_COUNT, historical ? visible.size() : filtered.size());
        data.set(RANGE, range);
        data.set(VIEW_HISTORY, historical ? 1 : 0);
        data.set(HAS_OLDER, historical ? (historyPage.hasOlder() ? 1 : 0)
                : (page + 1 < PowerTracePage.pages(filtered) ? 1 : 0));
        data.set(HISTORY_ERROR, historyError ? 1 : 0);
        for (int i = 0; i < PowerTracePage.PAGE_SIZE; i++) {
            int[] words = i < visible.size()
                    ? PowerTraceMenuCodec.encode(visible.get(i)) : PowerTraceMenuCodec.empty();
            for (int j = 0; j < words.length; j++) {
                data.set(DATA_START + i * PowerTraceMenuCodec.WORDS_PER_SAMPLE + j, words[j]);
            }
        }
        for (int i = 0; i < PowerTracePage.PAGE_SIZE; i++) {
            int[] words = i < secondaryPage.size()
                    ? PowerTraceMenuCodec.encode(secondaryPage.get(i)) : PowerTraceMenuCodec.empty();
            for (int j = 0; j < words.length; j++) {
                data.set(SECONDARY_START + i * PowerTraceMenuCodec.WORDS_PER_SAMPLE + j, words[j]);
            }
        }
        updateStatusAndChecksum();
    }

    private void updateStatusAndChecksum() {
        data.set(RECORDING, logger.recording() ? 1 : 0);
        data.set(STORAGE_ERROR, logger.storageError() ? 1 : 0);
        int crc = PowerTraceMenuCodec.checksum(data::get, CHECK_LOW);
        data.set(CHECK_LOW, crc & 0xffff);
        data.set(CHECK_HIGH, (crc >>> 16) & 0xffff);
    }

    @Override public void broadcastChanges() {
        if (logger != null) {
            // Never continue transmitting another recorder's data after it is
            // moved, replaced, unloaded, or out of the viewer's reach.
            if (comparing && !secondaryBound()) {
                clearComparison();
                publishPage();
            } else if (!historical && page == 0 && (snapshotLastTick != logger.recentLastTick()
                    || snapshotSize != logger.recentSize()
                    || (comparing && (secondaryLastTick != secondary.recentLastTick()
                        || secondarySize != secondary.recentSize())))) refreshSnapshot();
            else updateStatusAndChecksum();
        }
        super.broadcastChanges();
    }

    /** False until all signed-short menu slots have a consistent snapshot. */
    public boolean pageReady() {
        long supplied = (data.get(CHECK_LOW) & 0xffffL)
                | ((data.get(CHECK_HIGH) & 0xffffL) << 16);
        return supplied == Integer.toUnsignedLong(PowerTraceMenuCodec.checksum(data::get, CHECK_LOW));
    }

    public int value(int index) { return data.get(index); }

    public List<PowerTrace.Sample> pageSamples() {
        if (!pageReady()) return List.of();
        int count = data.get(VISIBLE_COUNT);
        if (count < 0 || count > PowerTracePage.PAGE_SIZE) return List.of();
        ArrayList<PowerTrace.Sample> samples = new ArrayList<>(count);
        try {
            for (int i = 0; i < count; i++) {
                PowerTrace.Sample sample = PowerTraceMenuCodec.decode(data::get,
                        DATA_START + i * PowerTraceMenuCodec.WORDS_PER_SAMPLE);
                if (sample == null || (!samples.isEmpty()
                        && sample.gameTick() <= samples.getLast().gameTick())) return List.of();
                samples.add(sample);
            }
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
        return List.copyOf(samples);
    }

    /** Second bounded 32-sample page is protected by the same page CRC. */
    public List<PowerTrace.Sample> comparisonSamples() {
        if (!pageReady() || data.get(COMPARE_ACTIVE) != 1) return List.of();
        int count = data.get(SECONDARY_VISIBLE_COUNT);
        if (count < 0 || count > PowerTracePage.PAGE_SIZE) return List.of();
        ArrayList<PowerTrace.Sample> samples = new ArrayList<>(count);
        try {
            for (int i=0; i<count; i++) {
                PowerTrace.Sample sample = PowerTraceMenuCodec.decode(data::get,
                        SECONDARY_START + i * PowerTraceMenuCodec.WORDS_PER_SAMPLE);
                if (sample == null || (!samples.isEmpty()
                        && sample.gameTick() <= samples.getLast().gameTick())) return List.of();
                samples.add(sample);
            }
            // Both are server pages of the same absolute Tick interval.
            List<PowerTrace.Sample> primary = pageSamples();
            if (!primary.isEmpty()) {
                long start = primary.getFirst().gameTick();
                long end = primary.getLast().gameTick();
                for (PowerTrace.Sample sample : samples) {
                    if (sample.gameTick() < start || sample.gameTick() > end) return List.of();
                }
            } else if (!samples.isEmpty()) return List.of();
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
        return List.copyOf(samples);
    }

    private void archiveFault(ServerPlayer player) {
        historical = true;
        historyError = true;
        historyPage = PowerTraceHistory.Page.empty();
        page = 0;
        // An unreadable/altered archive is not a safe continuation target.
        logger.archiveReadFailed();
        publishPage();
        player.sendSystemMessage(Component.literal(
                "[CAC D21C] Historical archive unavailable; recorder paused. "
                + "Check the server log/storage and restore from backup."));
    }

    private boolean fetchHistory(ServerLevel server, ServerPlayer player, int movement) {
        try {
            PowerTraceHistory.Page next;
            if (movement == 0) {
                next = logger.archiveNewest(server);
                page = 0;
            } else if (movement > 0) {
                if (!historyPage.hasOlder() || page >= 1_000_000) return false;
                next = logger.archiveOlder(server, historyPage.startOffset(), historyPage.snapshotEnd());
                page++;
            } else {
                if (!historyPage.hasNewer() || page <= 0) return false;
                next = logger.archiveNewer(server, historyPage.endOffset(), historyPage.snapshotEnd());
                page--;
            }
            historyPage = next;
            historical = true;
            historyError = false;
            publishPage();
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            archiveFault(player);
            return true;
        }
    }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)
                || !stillValid(player)) return false;
        if (comparing && !secondaryBound()) {
            clearComparison();
            publishPage();
        }
        long tick = server.getGameTime();
        switch (buttonId) {
            case BUTTON_OLDER -> {
                if (historical) {
                    if (tick < nextHistoryTick) return false;
                    nextHistoryTick = tick > Long.MAX_VALUE - 5 ? Long.MAX_VALUE : tick + 5;
                    if (!fetchHistory(server, serverPlayer, +1)) return false;
                } else {
                    if (page + 1 >= PowerTracePage.pages(filtered)) return false;
                    page++;
                    publishPage();
                }
            }
            case BUTTON_NEWER -> {
                if (historical) {
                    if (tick < nextHistoryTick) return false;
                    nextHistoryTick = tick > Long.MAX_VALUE - 5 ? Long.MAX_VALUE : tick + 5;
                    if (!fetchHistory(server, serverPlayer, -1)) return false;
                } else {
                    if (page == 0) return false;
                    page--;
                    publishPage();
                }
            }
            case BUTTON_REFRESH -> {
                if (tick < nextRefreshTick) return false;
                nextRefreshTick = tick > Long.MAX_VALUE - 20 ? Long.MAX_VALUE : tick + 20;
                if (historical) fetchHistory(server, serverPlayer, 0);
                else refreshSnapshot();
            }
            case BUTTON_TOGGLE -> {
                player.sendSystemMessage(Component.literal(logger.toggle(server)));
                updateStatusAndChecksum();
            }
            case BUTTON_RANGE -> {
                if (historical) return false;
                range = (range + 1) % 4;
                refreshSnapshot();
            }
            case BUTTON_HISTORY -> {
                if (tick < nextHistoryTick) return false;
                // D21C disk paging stays strictly one channel; never conflate
                // two different archive snapshots or their byte offsets.
                if (!historical && comparing) clearComparison();
                nextHistoryTick = tick > Long.MAX_VALUE - 5 ? Long.MAX_VALUE : tick + 5;
                if (historical) {
                    historical = false;
                    historyError = false;
                    refreshSnapshot();
                } else {
                    fetchHistory(server, serverPlayer, 0);
                }
            }
            case BUTTON_COMPARE -> {
                if (historical || tick < nextHistoryTick) return false;
                nextHistoryTick = tick > Long.MAX_VALUE-5 ? Long.MAX_VALUE : tick+5;
                if (comparing) {
                    clearComparison();
                    publishPage();
                } else {
                    List<PowerLoggerBlockEntity> choices = nearbyRecorders(server);
                    if (choices.isEmpty()) {
                        player.sendSystemMessage(Component.literal(
                                "[CAC D21D] No second loaded recorder in the local scan window and player reach."));
                        return false;
                    }
                    secondary = choices.getFirst();
                    secondaryPos = secondary.getBlockPos().immutable();
                    comparing = true;
                    refreshSnapshot();
                }
            }
            case BUTTON_NEXT_CHANNEL -> {
                if (!comparing || historical || tick < nextHistoryTick || !secondaryBound()) return false;
                nextHistoryTick = tick > Long.MAX_VALUE-5 ? Long.MAX_VALUE : tick+5;
                List<PowerLoggerBlockEntity> choices = nearbyRecorders(server);
                int selected = -1;
                for (int i = 0; i < choices.size(); i++) {
                    if (choices.get(i) == secondary) selected = i;
                }
                if (selected < 0) {
                    clearComparison();
                    publishPage();
                    return false;
                }
                secondary = choices.get((selected + 1) % choices.size());
                secondaryPos = secondary.getBlockPos().immutable();
                refreshSnapshot();
            }
            case BUTTON_EXPORT -> {
                if (tick < nextExportTick) return false;
                nextExportTick = tick > Long.MAX_VALUE - 20 ? Long.MAX_VALUE : tick + 20;
                if (historical && (historyError || historyPage.samples().isEmpty())) return false;
                if (comparing && !secondaryBound()) {
                    clearComparison();
                    publishPage();
                    return false;
                }
                String result = historical
                        ? logger.exportHistoryPage(server, historyPage.samples())
                        : comparing
                            ? logger.exportComparisonPage(server, PowerTraceComparison.frame(
                                    PowerTracePage.page(filtered,page),secondaryPage))
                            : logger.export(server);
                // Never disclose the absolute server filesystem path to clients.
                player.sendSystemMessage(Component.literal(result.contains("EXPORT FAILED")
                        ? result : historical
                            ? "[CAC D21C] Visible 32-point historical page exported on SERVER "
                                + "as .history-page.csv/.svg; full archive CSV remains unchanged."
                            : comparing
                                ? "[CAC D21D] Dual-channel page .compare-page.csv/.svg saved on SERVER; "
                                    + "both original archives unchanged."
                                : "[CAC D21B] CSV/SVG on SERVER: world/coldatomcraft/measurements/; "
                                    + "this preview has no automatic client download."));
                updateStatusAndChecksum();
            }
            default -> { return false; }
        }
        broadcastChanges();
        return true;
    }

    @Override public boolean stillValid(Player player) {
        return logger == null || (level != null && pos != null && player.level() == level
                && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) == logger
                && level.getBlockState(pos).is(PreviewBlocks.LOGGER.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0);
    }

    @Override public ItemStack quickMoveStack(Player player, int slotIndex) { return ItemStack.EMPTY; }
}
