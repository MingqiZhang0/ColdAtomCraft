package dev.coldatomcraft.neoforge.client;

import dev.coldatomcraft.experiment.LaserTuning;
import dev.coldatomcraft.neoforge.ColdAtomCraft;
import dev.coldatomcraft.neoforge.preview.LaserControlMenu;
import dev.coldatomcraft.neoforge.preview.PreviewBlocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.Locale;

/**
 * D12 experimental laser control panel. Buttons and sliders use Minecraft's
 * authenticated container-button channel, NOT client-side block mutation.
 */
public final class LaserControlScreen extends AbstractContainerScreen<LaserControlMenu> {
    private static final int PANEL_WIDTH = 334;
    private static final int PANEL_HEIGHT = 252;
    private final ParameterSlider[] sliders = new ParameterSlider[4];
    private Button powerButton;
    private String lastPowerText = "";

    public LaserControlScreen(LaserControlMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @EventBusSubscriber(modid = ColdAtomCraft.MOD_ID, value = Dist.CLIENT)
    public static final class Register {
        private Register() { }
        @SubscribeEvent
        public static void menus(RegisterMenuScreensEvent event) {
            event.register(PreviewBlocks.LASER_MENU.get(), LaserControlScreen::new);
        }
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos;
        int top = this.topPos;
        powerButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                send(LaserControlMenu.BUTTON_TOGGLE)).bounds(left + 14, top + 33, 142, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.laser.left"), button ->
                send(LaserControlMenu.BUTTON_ROTATE_LEFT)).bounds(left + 170, top + 33, 68, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.laser.right"), button ->
                send(LaserControlMenu.BUTTON_ROTATE_RIGHT)).bounds(left + 244, top + 33, 76, 22).build());
        for (int field = 0; field < 4; field++) {
            int dataSlot = field + 1;
            sliders[field] = addRenderableWidget(new ParameterSlider(left + 14, top + 84 + field * 33,
                    PANEL_WIDTH - 28, 20, field, menu.value(dataSlot)));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.coldatomcraft.laser.done"),
                button -> onClose()).bounds(left + 232, top + 221, 88, 20).build());
        refreshText();
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshText();
        for (int i = 0; i < sliders.length; i++) {
            if (sliders[i] != null) sliders[i].syncFromServer(menu.value(i + 1));
        }
    }

    private void refreshText() {
        if (powerButton == null) return;
        String power = menu.value(LaserControlMenu.ENABLED) == 1
                ? "gui.coldatomcraft.laser.power_on" : "gui.coldatomcraft.laser.power_off";
        if (!power.equals(lastPowerText)) {
            powerButton.setMessage(Component.translatable(power));
            lastPowerText = power;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xEB111925);
        graphics.fill(x + 3, y + 3, x + PANEL_WIDTH - 3, y + 25, 0xFF263B53);
        graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6895B3);
        graphics.text(font, title.getString(), x + 12, y + 10, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("gui.coldatomcraft.laser.direction").getString()
                + ": " + directionText(menu.value(LaserControlMenu.FACING)),
                x + 14, y + 64, 0xFFB4D3E5);
        if (menu.value(LaserControlMenu.VALID) == 0) {
            graphics.text(font, Component.translatable("gui.coldatomcraft.laser.invalid").getString(),
                    x + 170, y + 64, 0xFFFF6666);
        }
        graphics.text(font, Component.translatable("gui.coldatomcraft.laser.disclaimer").getString(),
                x + 14, y + 209, 0xFFFFC88A);
        graphics.text(font, Component.translatable("gui.coldatomcraft.laser.units").getString(),
                x + 14, y + 231, 0xFF94A9C0);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // All labels are laid out explicitly in extractBackground (no inventory slots).
    }

    private static String directionText(int direction) {
        return switch (direction) {
            case 0 -> "S"; case 1 -> "W"; case 2 -> "N"; case 3 -> "E";
            default -> "?";
        };
    }

    private final class ParameterSlider extends AbstractSliderButton {
        private final int field;
        private final int minimum;
        private final int maximum;
        private int lastSent = Integer.MIN_VALUE;
        private int lastObservedServer = Integer.MIN_VALUE;

        ParameterSlider(int x, int y, int width, int height, int field, int initial) {
            super(x, y, width, height, Component.empty(), 0.5);
            this.field = field;
            this.minimum = switch (field) {
                case 0 -> LaserTuning.MIN_POWER_MW;
                case 1 -> LaserTuning.MIN_WAVELENGTH_NM;
                case 2 -> LaserTuning.MIN_WAIST_UM;
                case 3 -> LaserTuning.MIN_WAIST_OFFSET_MM;
                default -> throw new IllegalArgumentException("unknown slider");
            };
            this.maximum = switch (field) {
                case 0 -> LaserTuning.MAX_POWER_MW;
                case 1 -> LaserTuning.MAX_WAVELENGTH_NM;
                case 2 -> LaserTuning.MAX_WAIST_UM;
                case 3 -> LaserTuning.MAX_WAIST_OFFSET_MM;
                default -> throw new IllegalArgumentException("unknown slider");
            };
            this.value = (double)(Math.max(minimum, Math.min(maximum, initial)) - minimum) / (maximum - minimum);
            this.lastObservedServer = initial;
            updateMessage();
        }

        int quantizedValue() {
            int raw = minimum + (int) Math.round(value * (maximum - minimum));
            // Field-specific UI resolution; never modifies server validation bounds.
            int increment = field == 2 ? 10 : field == 3 ? 50 : 1;
            return Math.max(minimum, Math.min(maximum,
                    minimum + (int) Math.round((double)(raw - minimum) / increment) * increment));
        }

        @Override protected void updateMessage() {
            int current = quantizedValue();
            String label = Component.translatable(switch (field) {
                case 0 -> "gui.coldatomcraft.laser.power";
                case 1 -> "gui.coldatomcraft.laser.wavelength";
                case 2 -> "gui.coldatomcraft.laser.waist";
                default -> "gui.coldatomcraft.laser.offset";
            }).getString();
            String valueText = switch (field) {
                case 0 -> current + " mW";
                case 1 -> current + " nm";
                case 2 -> String.format(Locale.ROOT, "%.2f mm", current / 1000.0);
                default -> String.format(Locale.ROOT, "%+.2f m", current / 1000.0);
            };
            setMessage(Component.literal(label + ": " + valueText));
        }

        @Override protected void applyValue() {
            int current = quantizedValue();
            if (current != lastSent) {
                lastSent = current;
                send(LaserControlMenu.setFieldButton(field, current));
            }
        }

        void syncFromServer(int serverValue) {
            if (serverValue != lastObservedServer && !dragging) {
                lastObservedServer = serverValue;
                value = (double) (Math.max(minimum, Math.min(maximum, serverValue)) - minimum)
                        / (maximum - minimum);
                updateMessage();
            }
        }
    }
}
