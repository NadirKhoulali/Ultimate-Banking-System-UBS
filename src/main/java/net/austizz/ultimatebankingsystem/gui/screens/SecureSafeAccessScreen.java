package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.network.SecureSafeActionPayload;
import net.austizz.ultimatebankingsystem.network.SecureSafeOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class SecureSafeAccessScreen extends net.minecraft.client.gui.screens.Screen {
    private SecureSafeOpenPayload payload;
    private String sessionPin = "";
    private String loginPinValue = "";
    private String setupPinValue = "";
    private String setupConfirmPinValue = "";
    private boolean setupConfirmActive = false;
    private String localMessage = "";
    private boolean localMessageSuccess = true;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public SecureSafeAccessScreen(SecureSafeOpenPayload payload) {
        super(Component.literal("Safe Access"));
        this.payload = payload;
    }

    public boolean matches(SecureSafeOpenPayload next) {
        return next != null
                && next.dimensionId().equals(payload.dimensionId())
                && next.x() == payload.x()
                && next.y() == payload.y()
                && next.z() == payload.z();
    }

    public void refresh(SecureSafeOpenPayload next) {
        this.payload = next;
        if (next.authenticated()) {
            if (!loginPinValue.isBlank()) {
                sessionPin = loginPinValue;
            } else if (!setupPinValue.isBlank()) {
                sessionPin = setupPinValue;
            }
            loginPinValue = "";
            setupPinValue = "";
            setupConfirmPinValue = "";
            setupConfirmActive = false;
        }
        rebuildSafeWidgets();
    }

    @Override
    protected void init() {
        rebuildSafeWidgets();
    }

    private void rebuildSafeWidgets() {
        clearWidgets();
        panelWidth = Math.min(520, Math.max(360, this.width - 36));
        panelHeight = !payload.configured() ? 390 : payload.authenticated() ? 300 : 330;
        panelHeight = Math.min(panelHeight, Math.max(220, this.height - 36));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int contentLeft = panelLeft + 16;
        int contentWidth = panelWidth - 32;
        int rowY = panelTop + 78;
        if (!payload.configured()) {
            buildSetup(contentLeft, contentWidth, rowY);
        } else if (!payload.authenticated()) {
            buildLogin(contentLeft, contentWidth, rowY);
        } else {
            buildControls(contentLeft, contentWidth, rowY);
        }
    }

    private void buildSetup(int contentLeft, int contentWidth, int rowY) {
        int pinW = Math.min(210, contentWidth);
        int pinX = contentLeft + Math.max(0, (contentWidth - pinW) / 2);
        addPinTargetButton(pinX, rowY, pinW, "New PIN", false);
        rowY += 32;
        addPinTargetButton(pinX, rowY, pinW, "Confirm PIN", true);
        rowY += 34;
        addKeypad(pinX, rowY, pinW);
        rowY += 138;
        addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                Component.literal("Set safe PIN"), 0xFF5FE79D, btn -> {
            String pin = setupPinValue.trim();
            if (!pin.equals(setupConfirmPinValue.trim())) {
                localMessage = "PIN confirmation does not match.";
                localMessageSuccess = false;
                return;
            }
            sessionPin = pin;
            send("SETUP", pin, pin);
        }));
    }

    private void buildLogin(int contentLeft, int contentWidth, int rowY) {
        int pinW = Math.min(210, contentWidth);
        int pinX = contentLeft + Math.max(0, (contentWidth - pinW) / 2);
        addPinDisplay(pinX, rowY, pinW, "PIN", loginPinValue);
        addKeypad(pinX, rowY + 36, pinW);
        rowY += 174;
        addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                Component.literal("Unlock safe"), 0xFF62DA8E, btn -> {
            sessionPin = loginPinValue.trim();
            send("LOGIN", sessionPin, "");
        }));
    }

    private void buildControls(int contentLeft, int contentWidth, int rowY) {
        addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                Component.literal(payload.open() ? "Close safe door" : "Open safe door"),
                payload.open() ? 0xFFFFB55F : 0xFF5FE79D,
                btn -> send("TOGGLE_OPEN", sessionPin, "")));

        rowY += 36;
        if (payload.chestUpgradeInstalled()) {
            addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                    Component.literal("Open chest compartment"),
                    payload.open() ? 0xFF70CBFF : 0xFF7895B4,
                    btn -> send("OPEN_STORAGE", sessionPin, "")));
        } else {
            addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                    Component.literal(payload.open() ? "Install chest upgrade" : "Install chest upgrade (open door first)"),
                    payload.open() ? 0xFF70CBFF : 0xFF7895B4,
                    btn -> send("OPEN_UPGRADE_SLOT", sessionPin, "")));
        }

        rowY += 36;
        addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                Component.literal("Close panel"), 0xFF7895B4, btn -> onClose()));
    }

    private void addPinTargetButton(int x, int y, int width, String label, boolean confirmTarget) {
        boolean activeTarget = setupConfirmActive == confirmTarget;
        addRenderableWidget(new DesktopButton(x, y, width, 24,
                Component.literal(label + ": " + maskedPin(confirmTarget ? setupConfirmPinValue : setupPinValue)),
                activeTarget ? 0xFF70CBFF : 0xFF7895B4,
                btn -> {
                    setupConfirmActive = confirmTarget;
                    rebuildSafeWidgets();
                }));
    }

    private void addPinDisplay(int x, int y, int width, String label, String value) {
        DesktopButton display = new DesktopButton(x, y, width, 24,
                Component.literal(label + ": " + maskedPin(value)),
                0xFF70CBFF,
                btn -> {});
        display.active = false;
        addRenderableWidget(display);
    }

    private void addKeypad(int x, int y, int width) {
        int gap = 6;
        int buttonW = Math.max(34, (width - gap * 2) / 3);
        int buttonH = 26;
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "Clear", "0", "Del"};
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            int col = i % 3;
            int row = i / 3;
            int buttonX = x + col * (buttonW + gap);
            int buttonY = y + row * (buttonH + gap);
            int accent = key.length() == 1 ? 0xFF70CBFF : 0xFFFFB55F;
            addRenderableWidget(new PinPadButton(buttonX, buttonY, buttonW, buttonH,
                    Component.literal(key), accent, () -> handlePinKey(key)));
        }
    }

    private void handlePinKey(String key) {
        localMessage = "";
        if ("Clear".equals(key)) {
            setActivePinValue("");
            rebuildSafeWidgets();
            return;
        }
        if ("Del".equals(key)) {
            String current = activePinValue();
            setActivePinValue(current.isEmpty() ? "" : current.substring(0, current.length() - 1));
            rebuildSafeWidgets();
            return;
        }
        if (key.length() == 1 && Character.isDigit(key.charAt(0)) && activePinValue().length() < 12) {
            setActivePinValue(activePinValue() + key);
            rebuildSafeWidgets();
        }
    }

    private String activePinValue() {
        if (!payload.configured()) {
            return setupConfirmActive ? setupConfirmPinValue : setupPinValue;
        }
        return loginPinValue;
    }

    private void setActivePinValue(String value) {
        String clean = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (clean.length() > 12) {
            clean = clean.substring(0, 12);
        }
        if (!payload.configured()) {
            if (setupConfirmActive) {
                setupConfirmPinValue = clean;
            } else {
                setupPinValue = clean;
            }
        } else {
            loginPinValue = clean;
        }
    }

    private void send(String action, String pin, String newPin) {
        localMessage = "";
        PacketDistributor.sendToServer(new SecureSafeActionPayload(
                payload.dimensionId(),
                payload.x(),
                payload.y(),
                payload.z(),
                action,
                pin == null ? "" : pin.trim(),
                newPin == null ? "" : newPin.trim(),
                payload.maxAttempts()
        ));
    }

    private static String maskedPin(String value) {
        int length = value == null ? 0 : value.length();
        return length <= 0 ? "----" : "*".repeat(Math.min(12, length));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000712);
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, right + 2, bottom + 2, 0xFF2D4C74);
        graphics.fill(panelLeft, panelTop, right, bottom, 0xFF0D2137);
        graphics.fill(panelLeft + 1, panelTop + 1, right - 1, panelTop + 32, 0xFF183B5C);
        graphics.fill(panelLeft + 12, panelTop + 58, right - 12, bottom - 14, 0xFF10263F);

        graphics.drawString(font, UbsClientTranslations.resolve(payload.tall() ? "Standing Safe" : "Compact Safe"),
                panelLeft + 14, panelTop + 12, 0xFFF4F8FF, false);
        graphics.drawString(font, UbsClientTranslations.resolve(statusLine()),
                panelLeft + 16, panelTop + 42, 0xFFCFE8FF, false);
        graphics.drawString(font, UbsClientTranslations.resolve("Attempts left: " + payload.attemptsRemaining() + "/" + payload.maxAttempts()),
                panelLeft + 16, panelTop + 56, attemptsColor(), false);

        String message = payload.message().isBlank() ? localMessage : payload.message();
        boolean success = payload.message().isBlank() ? localMessageSuccess : payload.messageSuccess();
        if (!message.isBlank()) {
            graphics.drawString(font, UbsClientTranslations.resolve(message), panelLeft + 16,
                    bottom - 28, success ? 0xFF7DFFB0 : 0xFFFFA6A6, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom translucent background.
    }

    private String statusLine() {
        if (!payload.configured()) {
            return "First setup: choose a PIN for this safe.";
        }
        if (!payload.authenticated()) {
            return "Enter PIN to unlock this safe.";
        }
        if (payload.open()) {
            return payload.chestUpgradeInstalled()
                    ? "Door open: shelves and lower chest compartment are ready in-world."
                    : "Door open: place bars or cash on shelves, or install a safe chest upgrade.";
        }
        return "Unlocked: use the door toggle, then interact with storage inside the safe.";
    }

    private int attemptsColor() {
        return payload.attemptsRemaining() <= 1 ? 0xFFFFA86B : 0xFFA7D4FF;
    }

    private class PinPadButton extends AbstractButton {
        private final int accentColor;
        private final Runnable action;

        private PinPadButton(int x, int y, int width, int height, Component message, int accentColor, Runnable action) {
            super(x, y, width, height, message);
            this.accentColor = accentColor;
            this.action = action;
        }

        @Override
        public void onPress() {
            if (action != null) {
                action.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int border = isHoveredOrFocused() ? 0xFF70CBFF : 0xFF244B73;
            int fill = isHoveredOrFocused() ? 0xFF173B5A : 0xFF132C45;
            graphics.fill(x, y, x + width, y + height, border);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
            graphics.fill(x + 2, y + 2, x + width - 2, y + 3, accentColor);
            graphics.drawCenteredString(font, UbsClientTranslations.resolve(getMessage().getString()),
                    x + width / 2, y + Math.max(1, (height - 8) / 2), 0xFFF4F8FF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
