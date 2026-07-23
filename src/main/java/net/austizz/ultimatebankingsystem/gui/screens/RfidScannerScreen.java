package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.client.RfidTargetSelectionClientState;
import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.RfidScannerActionPayload;
import net.austizz.ultimatebankingsystem.network.RfidScannerOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

public class RfidScannerScreen extends Screen {
    private RfidScannerOpenPayload payload;
    private String sessionPin = "";
    private String localMessage = "";
    private boolean localMessageSuccess = true;
    private int tab = 0;
    private int cardScroll = 0;
    private int targetScroll = 0;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int bodyTop;

    private DesktopEditBox setupPinInput;
    private DesktopEditBox setupConfirmInput;
    private DesktopEditBox loginPinInput;
    private DesktopEditBox accessLevelInput;
    private DesktopEditBox linkTypeInput;
    private DesktopEditBox linkNameInput;
    private DesktopEditBox idleSignalInput;
    private DesktopEditBox successSignalInput;
    private DesktopEditBox failSignalInput;
    private DesktopEditBox successDurationInput;
    private DesktopEditBox failDurationInput;
    private DesktopEditBox failThresholdInput;
    private DesktopEditBox issuePlayerInput;
    private DesktopEditBox issueLevelInput;

    public RfidScannerScreen(RfidScannerOpenPayload payload) {
        this(payload, "");
    }

    public RfidScannerScreen(RfidScannerOpenPayload payload, String retainedPin) {
        super(Component.literal("RFID Scanner"));
        this.payload = payload;
        if (payload.authenticated()) {
            this.sessionPin = retainedPin == null ? "" : retainedPin.trim();
        }
    }

    public boolean matches(RfidScannerOpenPayload next) {
        return next != null
                && next.dimensionId().equals(payload.dimensionId())
                && next.x() == payload.x()
                && next.y() == payload.y()
                && next.z() == payload.z();
    }

    public void refresh(RfidScannerOpenPayload next) {
        this.payload = next;
        if (next.authenticated()) {
            if (loginPinInput != null && !loginPinInput.getValue().isBlank()) {
                sessionPin = loginPinInput.getValue().trim();
            } else if (setupPinInput != null && !setupPinInput.getValue().isBlank()) {
                sessionPin = setupPinInput.getValue().trim();
            }
        }
        rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        clearInputReferences();
        panelWidth = Math.min(760, Math.max(360, width - 32));
        panelHeight = Math.min(500, Math.max(250, height - 32));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int contentLeft = panelLeft + 18;
        int contentWidth = panelWidth - 36;

        if (!payload.configured()) {
            bodyTop = panelTop + 72;
            buildSetup(contentLeft, bodyTop, contentWidth);
            return;
        }
        if (!payload.authenticated()) {
            bodyTop = panelTop + 72;
            buildLogin(contentLeft, bodyTop, contentWidth);
            return;
        }
        bodyTop = panelTop + 100;
        buildTabs(contentLeft, panelTop + 70, contentWidth);
        switch (tab) {
            case 1 -> buildSignals(contentLeft, bodyTop, contentWidth);
            case 2 -> buildCards(contentLeft, bodyTop, contentWidth);
            case 3 -> buildTargets(contentLeft, bodyTop, contentWidth);
            default -> buildGeneral(contentLeft, bodyTop, contentWidth);
        }
    }

    private void clearInputReferences() {
        setupPinInput = null;
        setupConfirmInput = null;
        loginPinInput = null;
        accessLevelInput = null;
        linkTypeInput = null;
        linkNameInput = null;
        idleSignalInput = null;
        successSignalInput = null;
        failSignalInput = null;
        successDurationInput = null;
        failDurationInput = null;
        failThresholdInput = null;
        issuePlayerInput = null;
        issueLevelInput = null;
    }

    private void buildSetup(int x, int y, int contentWidth) {
        int inputWidth = Math.min(260, contentWidth);
        int inputX = x + Math.max(0, (contentWidth - inputWidth) / 2);
        setupPinInput = addInput(inputX, y + 18, inputWidth, 22, "4-12 digits");
        setupPinInput.setMaxLength(12);
        setupConfirmInput = addInput(inputX, y + 58, inputWidth, 22, "Confirm PIN");
        setupConfirmInput.setMaxLength(12);
        accessLevelInput = addInput(inputX, y + 98, inputWidth, 22, "Access level");
        accessLevelInput.setValue(String.valueOf(payload.requiredAccessLevel()));
        addRenderableWidget(new DesktopButton(inputX, y + 138, inputWidth, 24,
                Component.literal("Initialize RFID Scanner"), 0xFF5FE79D, btn -> {
            String pin = setupPinInput.getValue().trim();
            if (!pin.equals(setupConfirmInput.getValue().trim())) {
                localMessage = "PIN confirmation does not match.";
                localMessageSuccess = false;
                return;
            }
            sessionPin = pin;
            send("SETUP", pin, pin, "", parseInt(accessLevelInput, 1), 0, 0, 0, true);
        }));
    }

    private void buildLogin(int x, int y, int contentWidth) {
        int inputWidth = Math.min(260, contentWidth);
        int inputX = x + Math.max(0, (contentWidth - inputWidth) / 2);
        loginPinInput = addInput(inputX, y + 18, inputWidth, 22, "PIN");
        loginPinInput.setMaxLength(12);
        addRenderableWidget(new DesktopButton(inputX, y + 58, inputWidth, 24,
                Component.literal("Unlock RFID Settings"), 0xFF62DA8E, btn -> {
            sessionPin = loginPinInput.getValue().trim();
            send("LOGIN", sessionPin, "", "", 0, 0, 0, 0, true);
        }));
    }

    private void buildTabs(int x, int y, int contentWidth) {
        String[] labels = {"General", "Signals", "Cards", "Targets"};
        int buttonWidth = Math.max(70, Math.min(112, (contentWidth - 18) / labels.length));
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            int color = tab == index ? 0xFF70CBFF : 0xFF244B73;
            addRenderableWidget(new DesktopButton(x + i * (buttonWidth + 6), y, buttonWidth, 22,
                    Component.literal(labels[i]), color, btn -> {
                tab = index;
                rebuild();
            }));
        }
    }

    private void buildGeneral(int x, int y, int contentWidth) {
        int col = Math.max(150, (contentWidth - 16) / 2);
        accessLevelInput = addInput(x, y + 14, col, 22, "0-100");
        accessLevelInput.setValue(String.valueOf(payload.requiredAccessLevel()));
        linkTypeInput = addInput(x + col + 16, y + 14, col, 22, "NONE/BANK/SHOP");
        linkTypeInput.setValue(payload.linkType().isBlank() ? "NONE" : payload.linkType());
        linkNameInput = addInput(x, y + 50, contentWidth, 22, "Linked bank or shop name");
        linkNameInput.setValue(payload.linkName());
        addRenderableWidget(new DesktopButton(x, y + 86, 140, 24,
                Component.literal(payload.enabled() ? "Enabled" : "Disabled"),
                payload.enabled() ? 0xFF5FE79D : 0xFFFF6B7E,
                btn -> send("SAVE_GENERAL", sessionPin, linkTypeInput.getValue(), linkNameInput.getValue(),
                        parseInt(accessLevelInput, payload.requiredAccessLevel()), 0, 0, 0, !payload.enabled())));
        addRenderableWidget(new DesktopButton(x + 150, y + 86, 150, 24,
                Component.literal("Save General"), 0xFF70CBFF,
                btn -> send("SAVE_GENERAL", sessionPin, linkTypeInput.getValue(), linkNameInput.getValue(),
                        parseInt(accessLevelInput, payload.requiredAccessLevel()), 0, 0, 0, payload.enabled())));
        addRenderableWidget(new DesktopButton(x, y + 120, 122, 22,
                Component.literal("Mode Normal"), modeColor("NORMAL"),
                btn -> send("SET_MODE", sessionPin, "NORMAL", "", 0, 0, 0, 0, true)));
        addRenderableWidget(new DesktopButton(x + 132, y + 120, 122, 22,
                Component.literal("Force Open"), modeColor("OPEN"),
                btn -> send("SET_MODE", sessionPin, "OPEN", "", 0, 0, 0, 0, true)));
        addRenderableWidget(new DesktopButton(x + 264, y + 120, 122, 22,
                Component.literal("Force Closed"), modeColor("CLOSED"),
                btn -> send("SET_MODE", sessionPin, "CLOSED", "", 0, 0, 0, 0, true)));
    }

    private void buildSignals(int x, int y, int contentWidth) {
        int third = Math.max(86, (contentWidth - 16) / 3);
        idleSignalInput = addInput(x, y + 14, third, 22, "0-15");
        idleSignalInput.setValue(String.valueOf(payload.idleSignal()));
        successSignalInput = addInput(x + third + 8, y + 14, third, 22, "0-15");
        successSignalInput.setValue(String.valueOf(payload.successSignal()));
        failSignalInput = addInput(x + (third + 8) * 2, y + 14, third, 22, "0-15");
        failSignalInput.setValue(String.valueOf(payload.failSignal()));
        successDurationInput = addInput(x, y + 54, third, 22, "ticks");
        successDurationInput.setValue(String.valueOf(payload.successDurationTicks()));
        failDurationInput = addInput(x + third + 8, y + 54, third, 22, "ticks");
        failDurationInput.setValue(String.valueOf(payload.failDurationTicks()));
        failThresholdInput = addInput(x + (third + 8) * 2, y + 54, third, 22, "tries");
        failThresholdInput.setValue(String.valueOf(payload.failThreshold()));
        addRenderableWidget(new DesktopButton(x, y + 92, contentWidth, 24,
                Component.literal("Save Signal Policy"), 0xFF70CBFF,
                btn -> send("SAVE_SIGNALS", sessionPin,
                        String.valueOf(parseInt(failDurationInput, payload.failDurationTicks())),
                        String.valueOf(parseInt(failThresholdInput, payload.failThreshold())),
                        parseInt(idleSignalInput, payload.idleSignal()),
                        parseInt(successSignalInput, payload.successSignal()),
                        parseInt(failSignalInput, payload.failSignal()),
                        parseInt(successDurationInput, payload.successDurationTicks()),
                        true)));
    }

    private void buildCards(int x, int y, int contentWidth) {
        int levelWidth = 86;
        issuePlayerInput = addInput(x, y + 14, contentWidth - levelWidth - 108, 22, "Online player");
        issueLevelInput = addInput(x + contentWidth - levelWidth - 98, y + 14, levelWidth, 22, "Level");
        issueLevelInput.setValue(String.valueOf(payload.requiredAccessLevel()));
        addRenderableWidget(new DesktopButton(x + contentWidth - 90, y + 14, 90, 22,
                Component.literal("Give Card"), 0xFF5FE79D,
                btn -> send("ISSUE_CARD", sessionPin, issuePlayerInput.getValue(), "",
                        parseInt(issueLevelInput, payload.requiredAccessLevel()), 0, 0, 0, true)));
        int rowY = y + 52;
        List<RfidScannerOpenPayload.CardSummary> cards = payload.cards();
        int visible = Math.max(1, Math.min(8, (panelTop + panelHeight - 28 - rowY) / 28));
        cardScroll = Math.max(0, Math.min(cardScroll, Math.max(0, cards.size() - visible)));
        for (int i = 0; i < visible && cardScroll + i < cards.size(); i++) {
            int index = cardScroll + i;
            RfidScannerOpenPayload.CardSummary card = cards.get(index);
            int yy = rowY + i * 28;
            addRenderableWidget(new DesktopButton(x + contentWidth - 78, yy, 78, 22,
                    Component.literal("Remove"), 0xFFFF6B7E,
                    btn -> send("REMOVE_CARD", sessionPin, card.cardId(), "", 0, 0, 0, 0, true)));
        }
    }

    private void buildTargets(int x, int y, int contentWidth) {
        addRenderableWidget(new DesktopButton(x, y + 8, 160, 24,
                Component.literal("Select Success Target"), 0xFF5FE79D, btn -> startSelection("SUCCESS")));
        addRenderableWidget(new DesktopButton(x + 170, y + 8, 150, 24,
                Component.literal("Select Fail Target"), 0xFFFF6B7E, btn -> startSelection("FAIL")));
        List<TargetRow> rows = targetRows();
        int rowY = y + 46;
        int visible = Math.max(1, Math.min(9, (panelTop + panelHeight - 28 - rowY) / 26));
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, rows.size() - visible)));
        for (int i = 0; i < visible && targetScroll + i < rows.size(); i++) {
            TargetRow row = rows.get(targetScroll + i);
            int yy = rowY + i * 26;
            addRenderableWidget(new DesktopButton(x + contentWidth - 72, yy, 72, 20,
                    Component.literal("Remove"), 0xFFFF6B7E,
                    btn -> send("REMOVE_TARGET", sessionPin, row.type(), "", row.index(), 0, 0, 0, true)));
        }
    }

    private void startSelection(String type) {
        RfidTargetSelectionClientState.start(payload, type, sessionPin);
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private List<TargetRow> targetRows() {
        java.util.ArrayList<TargetRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < payload.successTargets().size(); i++) {
            rows.add(new TargetRow("SUCCESS", i, payload.successTargets().get(i)));
        }
        for (int i = 0; i < payload.failTargets().size(); i++) {
            rows.add(new TargetRow("FAIL", i, payload.failTargets().get(i)));
        }
        return rows;
    }

    private DesktopEditBox addInput(int x, int y, int w, int h, String hint) {
        DesktopEditBox input = new DesktopEditBox(font, x, y, Math.max(42, w), h, Component.literal(hint));
        input.setHint(Component.literal(hint));
        return addRenderableWidget(input);
    }

    private void send(String action, String pin, String text1, String text2,
                      int int1, int int2, int int3, int int4, boolean bool1) {
        localMessage = "";
        PacketDistributor.sendToServer(new RfidScannerActionPayload(
                payload.dimensionId(),
                payload.x(),
                payload.y(),
                payload.z(),
                action,
                pin == null ? "" : pin.trim(),
                text1 == null ? "" : text1.trim(),
                text2 == null ? "" : text2.trim(),
                int1,
                int2,
                int3,
                int4,
                bool1
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

    private int modeColor(String mode) {
        return payload.forceMode().equalsIgnoreCase(mode) ? 0xFF70CBFF : 0xFF244B73;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollDelta) {
        if (payload.authenticated() && tab == 2 && !payload.cards().isEmpty()) {
            cardScroll = Math.max(0, cardScroll - (scrollDelta > 0 ? 1 : -1));
            rebuild();
            return true;
        }
        if (payload.authenticated() && tab == 3 && !targetRows().isEmpty()) {
            targetScroll = Math.max(0, targetScroll - (scrollDelta > 0 ? 1 : -1));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollDelta);
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
    public void onClose() {
        RfidTargetSelectionClientState.clearSession();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom translucent background.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0000712);
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, right + 2, bottom + 2, 0xFF2D4C74);
        graphics.fill(panelLeft, panelTop, right, bottom, 0xFF0D2137);
        graphics.fill(panelLeft + 1, panelTop + 1, right - 1, panelTop + 34, 0xFF183B5C);
        graphics.fill(panelLeft + 14, panelTop + 64, right - 14, bottom - 16, 0xFF10263F);

        graphics.drawString(font, UbsClientTranslations.resolve("RFID Scanner"), panelLeft + 14, panelTop + 12, 0xFFF4F8FF, false);
        graphics.drawString(font, UbsClientTranslations.resolve(statusLine()), panelLeft + 16, panelTop + 46, 0xFFCFE8FF, false);
        graphics.drawString(font, UbsClientTranslations.resolve("Reader " + shortId(payload.readerId())),
                right - 130, panelTop + 12, 0xFF9FB8D2, false);

        drawLabels(graphics);
        drawLists(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        String message = !payload.message().isBlank() ? payload.message() : localMessage;
        boolean success = !payload.message().isBlank() ? payload.messageSuccess() : localMessageSuccess;
        if (!message.isBlank()) {
            graphics.drawString(font, UbsClientTranslations.resolve(message), panelLeft + 16, bottom - 12,
                    success ? 0xFF7DFFB0 : 0xFFFFA6A6, false);
        }
    }

    private void drawLabels(GuiGraphics graphics) {
        if (!payload.configured()) {
            drawInputLabel(graphics, setupPinInput, "New PIN");
            drawInputLabel(graphics, setupConfirmInput, "Confirm PIN");
            drawInputLabel(graphics, accessLevelInput, "Access level");
            return;
        }
        if (!payload.authenticated()) {
            drawInputLabel(graphics, loginPinInput, "PIN");
            return;
        }
        switch (tab) {
            case 1 -> {
                drawInputLabel(graphics, idleSignalInput, "Idle signal");
                drawInputLabel(graphics, successSignalInput, "Success signal");
                drawInputLabel(graphics, failSignalInput, "Fail signal");
                drawInputLabel(graphics, successDurationInput, "Success ticks");
                drawInputLabel(graphics, failDurationInput, "Fail ticks");
                drawInputLabel(graphics, failThresholdInput, "Fail tries");
            }
            case 2 -> {
                drawInputLabel(graphics, issuePlayerInput, "Online player");
                drawInputLabel(graphics, issueLevelInput, "Level");
            }
            case 3 -> {
            }
            default -> {
                drawInputLabel(graphics, accessLevelInput, "Access level");
                drawInputLabel(graphics, linkTypeInput, "Link type");
                drawInputLabel(graphics, linkNameInput, "Link name");
            }
        }
    }

    private void drawInputLabel(GuiGraphics graphics, DesktopEditBox input, String label) {
        if (input == null || !input.visible) {
            return;
        }
        graphics.drawString(font, UbsClientTranslations.resolve(label), input.getX(), input.getY() - 10, 0xFF9FB8D2, false);
    }

    private void drawLists(GuiGraphics graphics) {
        if (!payload.authenticated()) {
            return;
        }
        int x = panelLeft + 18;
        int contentWidth = panelWidth - 36;
        if (tab == 2) {
            int y = bodyTop + 52;
            List<RfidScannerOpenPayload.CardSummary> cards = payload.cards();
            if (cards.isEmpty()) {
                graphics.drawString(font, UbsClientTranslations.resolve("No cards are connected to this reader."),
                        x, y, 0xFF9FB8D2, false);
                return;
            }
            int visible = Math.max(1, Math.min(8, (panelTop + panelHeight - 28 - y) / 28));
            for (int i = 0; i < visible && cardScroll + i < cards.size(); i++) {
                RfidScannerOpenPayload.CardSummary card = cards.get(cardScroll + i);
                int yy = y + i * 28;
                graphics.fill(x, yy, x + contentWidth - 86, yy + 22, 0xFF132C45);
                graphics.drawString(font, UbsClientTranslations.resolve(card.holderName() + " | level " + card.level()),
                        x + 8, yy + 4, 0xFFF4F8FF, false);
                graphics.drawString(font, UbsClientTranslations.resolve(shortId(card.cardId()) + " | " + card.label()),
                        x + 8, yy + 14, 0xFF9FB8D2, false);
            }
        } else if (tab == 3) {
            int y = bodyTop + 46;
            List<TargetRow> rows = targetRows();
            if (rows.isEmpty()) {
                graphics.drawString(font, UbsClientTranslations.resolve("No redstone targets connected."),
                        x, y, 0xFF9FB8D2, false);
                return;
            }
            int visible = Math.max(1, Math.min(9, (panelTop + panelHeight - 28 - y) / 26));
            for (int i = 0; i < visible && targetScroll + i < rows.size(); i++) {
                TargetRow row = rows.get(targetScroll + i);
                int yy = y + i * 26;
                int accent = "FAIL".equals(row.type()) ? 0xFFFF6B7E : 0xFF5FE79D;
                graphics.fill(x, yy, x + contentWidth - 80, yy + 20, 0xFF132C45);
                graphics.fill(x, yy, x + 3, yy + 20, accent);
                graphics.drawString(font, UbsClientTranslations.resolve(row.type() + " | " + row.target().label()),
                        x + 8, yy + 4, 0xFFF4F8FF, false);
                graphics.drawString(font, UbsClientTranslations.resolve(row.target().dimensionId() + " " + row.target().relaySide()),
                        x + 8, yy + 14, 0xFF9FB8D2, false);
            }
        }
    }

    private String statusLine() {
        if (!payload.configured()) {
            return "First setup: set an admin PIN and default access level.";
        }
        if (!payload.authenticated()) {
            return "Shift-right-click settings: enter PIN to manage this reader.";
        }
        return payload.enabled()
                ? "Unlocked | " + payload.linkType() + " " + payload.linkName() + " | status " + payload.status().toLowerCase(Locale.ROOT)
                : "Unlocked | reader disabled";
    }

    private static String shortId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "--------";
        }
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private record TargetRow(String type, int index, RfidScannerOpenPayload.TargetSummary target) {
    }
}
