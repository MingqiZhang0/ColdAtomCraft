package dev.coldatomcraft.neoforge.client;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.measurement.PowerTraceComparison;
import dev.coldatomcraft.experiment.measurement.PowerTracePage;
import dev.coldatomcraft.neoforge.ColdAtomCraft;
import dev.coldatomcraft.neoforge.preview.PowerLoggerMenu;
import dev.coldatomcraft.neoforge.preview.PreviewBlocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;
import java.util.Locale;

/**
 * D21B in-game scientific trace preview. Draws only server-supplied menu
 * snapshots. Never reads server files or runs a client-owned optical solver.
 * The history axis is *game ticks*, not a calibrated elapsed-time clock.
 */
public final class PowerLoggerScreen extends AbstractContainerScreen<PowerLoggerMenu> {
    private static final int PANEL_WIDTH = 448;
    private static final int PANEL_HEIGHT = 288;
    private static final int PLOT_LEFT = 50;
    private static final int PLOT_RIGHT = 424;
    private static final int PLOT_TOP = 83;
    private static final int PLOT_BOTTOM = 213;
    private static final int LINE = 0xFF70E1C3;
    private static final int SECONDARY_LINE = 0xFFFFBB70;
    private static final int AXIS = 0xFF8298AF;

    private Button recordingButton;
    private Button rangeButton;
    private Button historyButton;
    private Button compareButton;
    private Button nextChannelButton;

    public PowerLoggerScreen(PowerLoggerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @EventBusSubscriber(modid = ColdAtomCraft.MOD_ID, value = Dist.CLIENT)
    public static final class Register {
        private Register() { }
        @SubscribeEvent
        public static void menus(RegisterMenuScreensEvent event) {
            event.register(PreviewBlocks.LOGGER_MENU.get(), PowerLoggerScreen::new);
        }
    }

    @Override protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        recordingButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> send(PowerLoggerMenu.BUTTON_TOGGLE))
                .bounds(x + 13, y + 33, 104, 21).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.logger.refresh"),
                button -> send(PowerLoggerMenu.BUTTON_REFRESH))
                .bounds(x + 123, y + 33, 72, 21).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.logger.export"),
                button -> send(PowerLoggerMenu.BUTTON_EXPORT))
                .bounds(x + 201, y + 33, 113, 21).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.logger.close"),
                button -> onClose())
                .bounds(x + 320, y + 33, 112, 21).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.logger.older"),
                button -> send(PowerLoggerMenu.BUTTON_OLDER))
                .bounds(x + 13, y + 251, 83, 21).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.logger.newer"),
                button -> send(PowerLoggerMenu.BUTTON_NEWER))
                .bounds(x + 102, y + 251, 83, 21).build());
        rangeButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> send(PowerLoggerMenu.BUTTON_RANGE))
                .bounds(x + 191, y + 251, 140, 21).build());
        historyButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> send(PowerLoggerMenu.BUTTON_HISTORY))
                .bounds(x + 337, y + 251, 95, 21).build());
        compareButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> send(PowerLoggerMenu.BUTTON_COMPARE))
                .bounds(x + 221, y + 217, 105, 18).build());
        nextChannelButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.coldatomcraft.logger.compare_next"),
                button -> send(PowerLoggerMenu.BUTTON_NEXT_CHANNEL))
                .bounds(x + 332, y + 217, 100, 18).build());
        refreshControls();
    }

    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    @Override protected void containerTick() {
        super.containerTick();
        refreshControls();
    }

    private void refreshControls() {
        if (recordingButton != null) {
            recordingButton.setMessage(Component.translatable(menu.value(PowerLoggerMenu.RECORDING) == 1
                    ? "gui.coldatomcraft.logger.pause" : "gui.coldatomcraft.logger.record"));
        }
        if (rangeButton != null) {
            String rangeKey = switch (menu.value(PowerLoggerMenu.RANGE)) {
                case PowerTracePage.RANGE_ONE_MINUTE -> "gui.coldatomcraft.logger.one_minute";
                case PowerTracePage.RANGE_FIVE_MINUTES -> "gui.coldatomcraft.logger.five_minutes";
                case PowerTracePage.RANGE_FIFTEEN_MINUTES -> "gui.coldatomcraft.logger.fifteen_minutes";
                default -> "gui.coldatomcraft.logger.all_recent";
            };
            rangeButton.setMessage(Component.translatable(rangeKey));
            rangeButton.active = menu.value(PowerLoggerMenu.VIEW_HISTORY) != 1;
        }
        if (historyButton != null) {
            historyButton.setMessage(Component.translatable(menu.value(PowerLoggerMenu.VIEW_HISTORY) == 1
                    ? "gui.coldatomcraft.logger.show_recent" : "gui.coldatomcraft.logger.show_history"));
        }
        if (compareButton != null) {
            compareButton.setMessage(Component.translatable(menu.value(PowerLoggerMenu.COMPARE_ACTIVE) == 1
                    ? "gui.coldatomcraft.logger.compare_on" : "gui.coldatomcraft.logger.compare_off"));
            compareButton.active = menu.value(PowerLoggerMenu.VIEW_HISTORY) != 1;
        }
        if (nextChannelButton != null) {
            nextChannelButton.active = menu.value(PowerLoggerMenu.COMPARE_ACTIVE) == 1
                    && menu.value(PowerLoggerMenu.VIEW_HISTORY) != 1;
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xEE101926);
        graphics.fill(x + 2, y + 2, x + PANEL_WIDTH - 2, y + 27, 0xFF294158);
        graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF82A2BA);
        graphics.text(font, title.getString(), x + 12, y + 9, 0xFFF4F8FC);

        boolean ready = menu.pageReady();
        String state = menu.value(PowerLoggerMenu.STORAGE_ERROR) != 0 ?
                Component.translatable("gui.coldatomcraft.logger.error").getString() :
                Component.translatable(menu.value(PowerLoggerMenu.RECORDING) == 1
                        ? "gui.coldatomcraft.logger.state_recording"
                        : "gui.coldatomcraft.logger.state_paused").getString();
        graphics.text(font, state, x + 14, y + 61, 0xFFACCADD);
        int page = Math.max(0, menu.value(PowerLoggerMenu.CURRENT_PAGE));
        int total = Math.max(1, menu.value(PowerLoggerMenu.TOTAL_PAGES));
        boolean archive = menu.value(PowerLoggerMenu.VIEW_HISTORY) == 1;
        String pageLabel = archive
                ? Component.translatable("gui.coldatomcraft.logger.archive_page").getString()
                    + " " + (page + 1) + (menu.value(PowerLoggerMenu.HAS_OLDER) == 1 ? "+" : "")
                : Component.translatable("gui.coldatomcraft.logger.page").getString()
                    + " " + (page + 1) + "/" + total;
        graphics.text(font, pageLabel, x + 326, y + 61, 0xFFB9C6D9);

        int plotLeft = x + PLOT_LEFT, plotRight = x + PLOT_RIGHT;
        int plotTop = y + PLOT_TOP, plotBottom = y + PLOT_BOTTOM;
        graphics.fill(plotLeft, plotTop, plotRight, plotBottom, 0xFF162632);
        for (int line = 0; line <= 4; line++) {
            int yy = plotBottom - line * (PLOT_BOTTOM - PLOT_TOP) / 4;
            graphics.fill(plotLeft, yy, plotRight, yy + 1, 0xFF344858);
        }
        graphics.outline(plotLeft, plotTop, plotRight-plotLeft, plotBottom-plotTop, AXIS);
        if (!ready) {
            graphics.text(font, Component.translatable("gui.coldatomcraft.logger.syncing").getString(),
                    plotLeft + 42, plotTop + 60, 0xFFF0D19F);
            return;
        }
        if (menu.value(PowerLoggerMenu.HISTORY_ERROR) == 1) {
            graphics.text(font, Component.translatable("gui.coldatomcraft.logger.history_error").getString(),
                    plotLeft + 10, plotTop + 60, 0xFFE8A1A1);
            return;
        }
        List<PowerTrace.Sample> samples = menu.pageSamples();
        boolean dual = menu.value(PowerLoggerMenu.COMPARE_ACTIVE) == 1;
        if (dual) {
            drawComparison(graphics, samples, menu.comparisonSamples(), mouseX, mouseY,
                    plotLeft, plotTop, plotRight, plotBottom);
        } else {
            drawSamples(graphics, samples, mouseX, mouseY, plotLeft, plotTop, plotRight, plotBottom);
        }
        graphics.text(font,
                Component.translatable(dual ? "gui.coldatomcraft.logger.compare_note"
                        : archive ? "gui.coldatomcraft.logger.archive_note"
                        : "gui.coldatomcraft.logger.preview_note").getString(),
                x + 14, y + 239, 0xFF9EB0C1);
    }

    private void drawSamples(GuiGraphicsExtractor graphics, List<PowerTrace.Sample> samples,
                             int mouseX, int mouseY, int left, int top, int right, int bottom) {
        if (samples.isEmpty()) {
            graphics.text(font, Component.translatable("gui.coldatomcraft.logger.no_points").getString(),
                    left + 26, top + 61, 0xFF9EB0C1);
            return;
        }
        long firstTick = samples.getFirst().gameTick();
        long lastTick = samples.getLast().gameTick();
        double maxWatts = 0;
        for (PowerTrace.Sample sample : samples) {
            if (sample.valid()) maxWatts = Math.max(maxWatts, sample.watts());
        }
        if (maxWatts <= 0) maxWatts = 0.001; // Valid measured zero stays on the zero baseline.
        double yMax = maxWatts * 1.08;
        graphics.text(font, "mW", left - 32, top + 2, 0xFFABC6D8);
        graphics.text(font, formatMilliwatts(yMax), left - 44, top + 13, 0xFFABC6D8);
        graphics.text(font, "0", left - 14, bottom - 8, 0xFFABC6D8);

        PowerTrace.Sample previous = null;
        int px = 0, py = 0;
        int nearest = -1;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            PowerTrace.Sample sample = samples.get(i);
            int cx = xAt(sample.gameTick(), firstTick, lastTick, left, right);
            int distance = Math.abs(cx - mouseX);
            if (distance < nearestDistance) { nearestDistance = distance; nearest = i; }
            if (!sample.valid()) {
                graphics.fill(cx - 2, bottom - 4, cx + 3, bottom, 0xFFD69A5E);
                previous = null;
                continue;
            }
            int cy = bottom - (int) Math.round(sample.watts() / yMax * (bottom - top - 2));
            if (previous != null && sample.gameTick() - previous.gameTick()
                    <= PowerTrace.MAX_CONNECTED_TICK_GAP) {
                drawLine(graphics,px,py,cx,cy,LINE);
            }
            graphics.fill(cx - 2,cy - 2,cx + 3,cy + 3,LINE);
            previous = sample;
            px = cx; py = cy;
        }
        graphics.text(font, Long.toString(firstTick), left, bottom + 4, 0xFFABC6D8);
        String maxTick = Long.toString(lastTick);
        graphics.text(font, maxTick, right - maxTick.length() * 7, bottom + 4, 0xFFABC6D8);

        if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom
                && nearest >= 0 && nearestDistance <= 12) {
            PowerTrace.Sample selected = samples.get(nearest);
            String detail = "tick " + selected.gameTick() + " : "
                    + (selected.valid() ? formatMilliwatts(selected.watts()) + " mW"
                            : selected.status().name() + " (gap)");
            graphics.fill(left + 8,top + 8,right - 8,top + 27,0xE9263D53);
            graphics.text(font, detail, left + 15, top + 14, 0xFFF4F8FC);
        }
    }

    /**
     * Both traces share the same absolute server Tick axis. No attempt is made
     * to pair timestamps, interpolate across gaps or add optical powers.
     */
    private void drawComparison(GuiGraphicsExtractor graphics, List<PowerTrace.Sample> a,
                                List<PowerTrace.Sample> b, int mouseX, int mouseY,
                                int left, int top, int right, int bottom) {
        PowerTraceComparison.Frame frame = PowerTraceComparison.frame(a,b);
        if (a.isEmpty() && b.isEmpty()) {
            graphics.text(font, Component.translatable("gui.coldatomcraft.logger.no_points").getString(),
                    left + 26, top + 61, 0xFF9EB0C1);
            return;
        }
        graphics.text(font, "mW", left - 32, top + 2, 0xFFABC6D8);
        graphics.text(font, formatMilliwatts(frame.maxWatts()), left - 44, top + 13, 0xFFABC6D8);
        graphics.text(font, "0", left - 14, bottom - 8, 0xFFABC6D8);
        graphics.text(font, "A", right - 149, top + 4, LINE);
        String secondaryLabel = "B (" + menu.value(PowerLoggerMenu.SECONDARY_DX) + ","
                + menu.value(PowerLoggerMenu.SECONDARY_DY) + ","
                + menu.value(PowerLoggerMenu.SECONDARY_DZ) + ")";
        graphics.text(font, secondaryLabel, right - 130, top + 4, SECONDARY_LINE);
        plotChannel(graphics,frame.a(),frame,LINE,left,top,right,bottom);
        plotChannel(graphics,frame.b(),frame,SECONDARY_LINE,left,top,right,bottom);
        graphics.text(font,Long.toString(frame.firstTick()),left,bottom+4,0xFFABC6D8);
        String last = Long.toString(frame.lastTick());
        graphics.text(font,last,right-last.length()*7,bottom+4,0xFFABC6D8);
        PowerTrace.Sample near = null;
        String label = "";
        int nearest = Integer.MAX_VALUE;
        if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
            for (int channel=0;channel<2;channel++) {
                List<PowerTrace.Sample> data = channel == 0 ? frame.a() : frame.b();
                for (PowerTrace.Sample sample : data) {
                    int px = xAt(sample.gameTick(),frame.firstTick(),frame.lastTick(),left,right);
                    int py = sample.valid() ? bottom-(int)Math.round(
                            sample.watts()/frame.maxWatts()*(bottom-top-2)) : bottom-3;
                    int dist = Math.abs(mouseX-px)+Math.abs(mouseY-py);
                    if (dist < nearest) {
                        nearest=dist; near=sample; label=channel==0?"A":"B";
                    }
                }
            }
        }
        if (near != null && nearest <= 18) {
            String detail = label + " tick " + near.gameTick() + " : "
                    + (near.valid() ? formatMilliwatts(near.watts())+" mW"
                            : near.status().name()+" (gap)");
            graphics.fill(left+8,top+21,right-8,top+41,0xE9263D53);
            graphics.text(font,detail,left+15,top+27,0xFFF4F8FC);
        }
    }

    private static void plotChannel(GuiGraphicsExtractor graphics, List<PowerTrace.Sample> samples,
                                    PowerTraceComparison.Frame frame, int color,
                                    int left, int top, int right, int bottom) {
        PowerTrace.Sample previous = null;
        int px = 0, py = 0;
        for (PowerTrace.Sample sample : samples) {
            int cx = xAt(sample.gameTick(),frame.firstTick(),frame.lastTick(),left,right);
            if (!sample.valid()) {
                // A missing observation is a visible marker, not 0 mW.
                graphics.fill(cx-2,bottom-4,cx+3,bottom,0xFFD69A5E);
                previous = null;
                continue;
            }
            int cy = bottom-(int)Math.round(sample.watts()/frame.maxWatts()*(bottom-top-2));
            if (previous != null && sample.gameTick()-previous.gameTick()
                    <= PowerTrace.MAX_CONNECTED_TICK_GAP) drawLine(graphics,px,py,cx,cy,color);
            graphics.fill(cx-2,cy-2,cx+3,cy+3,color);
            previous=sample;
            px=cx; py=cy;
        }
    }

    private static String formatMilliwatts(double watts) {
        if (watts > Double.MAX_VALUE / 1000.0) {
            return java.math.BigDecimal.valueOf(watts).movePointRight(3)
                    .round(new java.math.MathContext(5)).toEngineeringString();
        }
        return String.format(Locale.ROOT, "%.5g", watts * 1000.0);
    }

    private static int xAt(long tick, long first, long last, int left, int right) {
        if (first == last) return (left + right) / 2;
        return left + (int)Math.round((right - left - 2) * ((tick - first) / (double)(last - first)));
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
        for (int i=0;i<=steps;i++) {
            double part = steps==0 ? 0 : i/(double)steps;
            int x=x0+(int)Math.round((x1-x0)*part);
            int y=y0+(int)Math.round((y1-y0)*part);
            graphics.fill(x,y,x+1,y+1,color);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // No inventory slots: the full instrument interface is drawn above.
    }
}
