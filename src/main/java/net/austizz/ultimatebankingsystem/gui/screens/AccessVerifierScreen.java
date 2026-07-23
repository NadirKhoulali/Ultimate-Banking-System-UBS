package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.AccessVerifierActionPayload;
import net.austizz.ultimatebankingsystem.network.AccessVerifierOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AccessVerifierScreen extends Screen {
    private AccessVerifierOpenPayload payload;
    private String sessionPin = "";
    private String localMessage = "";
    private boolean localMessageSuccess = true;
    private String loginPinValue = "";
    private String setupPinValue = "";
    private String setupConfirmPinValue = "";
    private boolean setupConfirmActive = false;

    private DesktopEditBox successSignalInput;
    private DesktopEditBox failSignalInput;
    private DesktopEditBox attemptsInput;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public AccessVerifierScreen(AccessVerifierOpenPayload payload) {
        super(Component.literal("Access Verifier"));
        this.payload = payload;
    }

    public boolean matches(AccessVerifierOpenPayload next) {
        return next != null
                && next.dimensionId().equals(payload.dimensionId())
                && next.x() == payload.x()
                && next.y() == payload.y()
                && next.z() == payload.z();
    }

    public void refresh(AccessVerifierOpenPayload next) {
        this.payload = next;
        if (next.authenticated()) {
            if (!loginPinValue.isBlank()) {
                this.sessionPin = loginPinValue;
            } else if (!setupPinValue.isBlank()) {
                this.sessionPin = setupPinValue;
            }
            loginPinValue = "";
            setupPinValue = "";
            setupConfirmPinValue = "";
            setupConfirmActive = false;
        }
        rebuildVerifierWidgets();
    }

    @Override
    protected void init() {
        rebuildVerifierWidgets();
    }

    private void rebuildVerifierWidgets() {
        clearWidgets();
        panelWidth = Math.min(520, Math.max(360, this.width - 36));
        panelHeight = !payload.configured() ? 430 : payload.authenticated() ? 330 : 360;
        panelHeight = Math.min(panelHeight, Math.max(220, this.height - 36));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int contentLeft = panelLeft + 16;
        int contentWidth = panelWidth - 32;
        int rowY = panelTop + 78;
        int inputHeight = 22;

        if (!payload.configured()) {
            buildSetup(contentLeft, contentWidth, rowY, inputHeight);
        } else if (!payload.authenticated()) {
            buildLogin(contentLeft, contentWidth, rowY, inputHeight);
        } else {
            buildControls(contentLeft, contentWidth, rowY, inputHeight);
        }
    }

    private void buildSetup(int contentLeft, int contentWidth, int rowY, int inputHeight) {
        int pinW = Math.min(210, contentWidth);
        int pinX = contentLeft + Math.max(0, (contentWidth - pinW) / 2);
        rowY += 2;
        addPinTargetButton(pinX, rowY, pinW, "New PIN", false);
        rowY += 32;
        addPinTargetButton(pinX, rowY, pinW, "Confirm PIN", true);
        rowY += 34;
        addKeypad(pinX, rowY, pinW);
        rowY += 138;
        addSettingsInputs(contentLeft, contentWidth, rowY, inputHeight);
        rowY += 42;
        addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                Component.literal("Set PIN"), 0xFF5FE79D, btn -> {
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

    private void buildLogin(int contentLeft, int contentWidth, int rowY, int inputHeight) {
        int pinW = Math.min(210, contentWidth);
        int pinX = contentLeft + Math.max(0, (contentWidth - pinW) / 2);
        addPinDisplay(pinX, rowY, pinW, "PIN", loginPinValue);
        addKeypad(pinX, rowY + 36, pinW);
        rowY += 174;
        addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                Component.literal("Unlock verifier"), 0xFF62DA8E, btn -> {
            sessionPin = loginPinValue.trim();
            send("LOGIN", sessionPin, "");
        }));
    }

    private void buildControls(int contentLeft, int contentWidth, int rowY, int inputHeight) {
        int halfWidth = (contentWidth - 8) / 2;
        String successLabel = payload.successCircuitActive() ? "Close redstone circuit" : "Open redstone circuit";
        addRenderableWidget(new DesktopButton(contentLeft, rowY, halfWidth, 24,
                Component.literal(successLabel), payload.successCircuitActive() ? 0xFFFFB55F : 0xFF5FE79D,
                btn -> send("TOGGLE_SUCCESS", sessionPin, "")));

        DesktopButton stopFail = new DesktopButton(contentLeft + halfWidth + 8, rowY, halfWidth, 24,
                Component.literal("Stop fail circuit"), 0xFFFF6B7E, btn -> send("STOP_FAIL", sessionPin, ""));
        stopFail.active = payload.failCircuitActive();
        addRenderableWidget(stopFail);

        rowY += 40;
        addSettingsInputs(contentLeft, contentWidth, rowY, inputHeight);
        rowY += 34;
        addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                Component.literal("Save signal settings"), 0xFF70CBFF, btn -> send("SAVE_SETTINGS", sessionPin, "")));
    }

    private void addSettingsInputs(int contentLeft, int contentWidth, int rowY, int inputHeight) {
        int third = (contentWidth - 12) / 3;
        int inputY = rowY + 12;
        successSignalInput = addInput(contentLeft, inputY, third, inputHeight, "1-15");
        successSignalInput.setValue(String.valueOf(payload.successSignal()));
        successSignalInput.setMaxLength(2);

        failSignalInput = addInput(contentLeft + third + 6, inputY, third, inputHeight, "1-15");
        failSignalInput.setValue(String.valueOf(payload.failSignal()));
        failSignalInput.setMaxLength(2);

        attemptsInput = addInput(contentLeft + (third + 6) * 2, inputY, third, inputHeight, "Attempts");
        attemptsInput.setValue(String.valueOf(payload.maxAttempts()));
        attemptsInput.setMaxLength(2);
    }

    private void addPinTargetButton(int x, int y, int width, String label, boolean confirmTarget) {
        boolean activeTarget = setupConfirmActive == confirmTarget;
        addRenderableWidget(new DesktopButton(x, y, width, 24,
                Component.literal(label + ": " + maskedPin(confirmTarget ? setupConfirmPinValue : setupPinValue)),
                activeTarget ? 0xFF70CBFF : 0xFF7895B4,
                btn -> {
                    setupConfirmActive = confirmTarget;
                    rebuildVerifierWidgets();
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
            rebuildVerifierWidgets();
            return;
        }
        if ("Del".equals(key)) {
            String current = activePinValue();
            setActivePinValue(current.isEmpty() ? "" : current.substring(0, current.length() - 1));
            rebuildVerifierWidgets();
            return;
        }
        if (key.length() == 1 && Character.isDigit(key.charAt(0)) && activePinValue().length() < 12) {
            setActivePinValue(activePinValue() + key);
            rebuildVerifierWidgets();
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

    private static String maskedPin(String value) {
        int length = value == null ? 0 : value.length();
        if (length <= 0) {
            return "----";
        }
        return "*".repeat(Math.min(12, length));
    }

    private DesktopEditBox addInput(int x, int y, int width, int height, String hint) {
        DesktopEditBox input = new DesktopEditBox(font, x, y, width, height, Component.literal(hint));
        input.setHint(Component.literal(hint));
        return addRenderableWidget(input);
    }

    private void send(String action, String pin, String newPin) {
        localMessage = "";
        PacketDistributor.sendToServer(new AccessVerifierActionPayload(
                payload.dimensionId(),
                payload.x(),
                payload.y(),
                payload.z(),
                action,
                pin == null ? "" : pin.trim(),
                newPin == null ? "" : newPin.trim(),
                parseInt(successSignalInput, payload.successSignal()),
                parseInt(failSignalInput, payload.failSignal()),
                parseInt(attemptsInput, payload.maxAttempts())
        ));
    }

    private static int parseInt(DesktopEditBox input, int fallback) {
        if (input == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(input.getValue().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

        graphics.drawString(font, UbsClientTranslations.resolve("Access Verifier"), panelLeft + 14, panelTop + 12, 0xFFF4F8FF, false);
        graphics.drawString(font, UbsClientTranslations.resolve(statusLine()), panelLeft + 16, panelTop + 42, 0xFFCFE8FF, false);
        graphics.drawString(font, UbsClientTranslations.resolve("Attempts left: " + payload.attemptsRemaining() + "/" + payload.maxAttempts()),
                panelLeft + 16, panelTop + 56, attemptsColor(), false);

        if (payload.failCircuitActive()) {
            graphics.drawString(font, UbsClientTranslations.resolve("Fail circuit active: signal " + payload.failSignal()),
                    right - 190, panelTop + 42, 0xFFFFA86B, false);
        }
        if (payload.successCircuitActive()) {
            graphics.drawString(font, UbsClientTranslations.resolve("Success circuit active: signal " + payload.successSignal()),
                    right - 214, panelTop + 56, 0xFF7DFFB0, false);
        }
        String message = payload.message().isBlank() ? localMessage : payload.message();
        boolean messageSuccess = payload.message().isBlank() ? localMessageSuccess : payload.messageSuccess();
        if (!message.isBlank()) {
            graphics.drawString(font, UbsClientTranslations.resolve(message), panelLeft + 16,
                    bottom - 28, messageSuccess ? 0xFF7DFFB0 : 0xFFFFA6A6, false);
        }

        drawInputLabels(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawInputLabels(GuiGraphics graphics) {
        drawInputLabel(graphics, successSignalInput, "Success signal");
        drawInputLabel(graphics, failSignalInput, "Fail signal");
        drawInputLabel(graphics, attemptsInput, "Wrong tries");
    }

    private void drawInputLabel(GuiGraphics graphics, DesktopEditBox input, String label) {
        if (input == null || !input.visible) {
            return;
        }
        graphics.drawString(font, UbsClientTranslations.resolve(label), input.getX(), input.getY() - 10, 0xFF9FB8D2, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom translucent background.
    }

    private String statusLine() {
        if (!payload.configured()) {
            return "First setup: choose a PIN and signal policy.";
        }
        if (!payload.authenticated()) {
            return "Enter PIN to manage redstone circuits.";
        }
        return "Unlocked: toggle circuits or adjust signal policy.";
    }

    private int attemptsColor() {
        if (payload.attemptsRemaining() <= 1) {
            return 0xFFFFA86B;
        }
        return 0xFFA7D4FF;
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
