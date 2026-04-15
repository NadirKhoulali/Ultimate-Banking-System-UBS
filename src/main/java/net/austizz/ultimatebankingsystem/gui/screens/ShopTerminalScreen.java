package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.ShopTerminalAccountSummary;
import net.austizz.ultimatebankingsystem.network.ShopTerminalOpenPayload;
import net.austizz.ultimatebankingsystem.network.ShopTerminalSavePayload;
import net.austizz.ultimatebankingsystem.network.ShopTerminalSaveResponsePayload;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ShopTerminalScreen extends Screen {
    private final ShopTerminalOpenPayload openPayload;
    private final List<ShopTerminalAccountSummary> accounts;
    private final UUID merchantAccountIdFromServer;

    private DesktopEditBox shopNameInput;
    private DesktopEditBox priceInput;
    private DesktopEditBox successPulseTicksInput;
    private DesktopEditBox failurePulseTicksInput;
    private DesktopEditBox idlePulseStrengthInput;

    private DesktopButton successPulseToggleButton;
    private DesktopButton failurePulseToggleButton;
    private DesktopButton idlePulseToggleButton;
    private DesktopButton accountPrevButton;
    private DesktopButton accountNextButton;
    private DesktopButton accountSelectButton;
    private DesktopButton saveButton;
    private DesktopButton closeButton;

    private boolean pulseOnSuccess;
    private boolean pulseOnFailure;
    private boolean pulseOnIdle;
    private int selectedAccountIndex;
    private long totalSalesDollars;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    private boolean feedbackSuccess = true;
    private String feedbackMessage = "";
    private long feedbackUntilMillis = 0L;

    public ShopTerminalScreen(ShopTerminalOpenPayload payload) {
        super(Component.literal("Payment Terminal"));
        this.openPayload = payload;
        this.accounts = payload.accounts() == null ? List.of() : new ArrayList<>(payload.accounts());
        this.merchantAccountIdFromServer = payload.parseMerchantAccountId();
        this.pulseOnSuccess = payload.pulseOnSuccess();
        this.pulseOnFailure = payload.pulseOnFailure();
        this.pulseOnIdle = payload.pulseOnIdle();
        this.totalSalesDollars = Math.max(0L, payload.totalSalesDollars());
        this.selectedAccountIndex = resolveInitialSelectedAccountIndex();
    }

    @Override
    protected void init() {
        clearWidgets();

        panelWidth = Math.min(560, Math.max(420, this.width - 36));
        panelHeight = Math.min(370, Math.max(310, this.height - 36));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int contentLeft = panelLeft + 14;
        int contentRight = panelLeft + panelWidth - 14;
        int contentWidth = contentRight - contentLeft;

        int rowY = panelTop + 64;
        int rowGap = 30;
        int inputHeight = 22;

        shopNameInput = new DesktopEditBox(font, contentLeft, rowY, contentWidth, inputHeight, Component.literal("Shop name"));
        shopNameInput.setValue(openPayload.shopName());
        shopNameInput.setMaxLength(42);
        addRenderableWidget(shopNameInput);

        rowY += rowGap;
        int priceWidth = Math.max(120, contentWidth / 3);
        priceInput = new DesktopEditBox(font, contentLeft, rowY, priceWidth, inputHeight, Component.literal("Price"));
        priceInput.setValue(String.valueOf(Math.max(1L, openPayload.priceDollars())));
        priceInput.setMaxLength(16);
        addRenderableWidget(priceInput);

        int ownerX = contentLeft + priceWidth + 12;
        int ownerW = Math.max(80, contentRight - ownerX);
        addRenderableWidget(new DesktopButton(ownerX, rowY, ownerW, inputHeight,
                Component.literal("Owner: " + sanitizeOwnerName(openPayload.ownerName())),
                0xFF70CBFF,
                btn -> {}) {{
            active = false;
        }});

        rowY += rowGap;
        int selectorButtonWidth = Math.max(94, Math.min(120, contentWidth / 6));
        int accountMidPadding = 8;
        int accountDisplayX = contentLeft + selectorButtonWidth + accountMidPadding;
        int accountDisplayWidth = contentWidth - selectorButtonWidth * 2 - accountMidPadding * 2;

        accountPrevButton = addRenderableWidget(new DesktopButton(contentLeft, rowY, selectorButtonWidth, inputHeight,
                Component.literal("Prev Account"), 0xFF76B7FF, btn -> stepAccount(-1)));
        accountNextButton = addRenderableWidget(new DesktopButton(contentRight - selectorButtonWidth, rowY, selectorButtonWidth, inputHeight,
                Component.literal("Next Account"), 0xFF76B7FF, btn -> stepAccount(1)));
        accountSelectButton = addRenderableWidget(new DesktopButton(accountDisplayX, rowY, accountDisplayWidth, inputHeight,
                selectedAccountCaption(), 0xFF8FD8FF, btn -> stepAccount(1)) {
            @Override
            public void onPress() {
                super.onPress();
                this.setMessage(selectedAccountCaption());
            }
        });

        rowY += rowGap;
        int thirdWidth = (contentWidth - 12) / 3;
        successPulseToggleButton = addRenderableWidget(new DesktopButton(
                contentLeft, rowY, thirdWidth, inputHeight,
                pulseOnSuccess ? Component.literal("Success Pulse: ON") : Component.literal("Success Pulse: OFF"),
                pulseOnSuccess ? 0xFF5AD98E : 0xFFD87E7E,
                btn -> {
                    pulseOnSuccess = !pulseOnSuccess;
                    refreshPulseButtons();
                }
        ));
        failurePulseToggleButton = addRenderableWidget(new DesktopButton(
                contentLeft + thirdWidth + 6, rowY, thirdWidth, inputHeight,
                pulseOnFailure ? Component.literal("Failure Pulse: ON") : Component.literal("Failure Pulse: OFF"),
                pulseOnFailure ? 0xFF5AD98E : 0xFFD87E7E,
                btn -> {
                    pulseOnFailure = !pulseOnFailure;
                    refreshPulseButtons();
                }
        ));
        idlePulseToggleButton = addRenderableWidget(new DesktopButton(
                contentLeft + (thirdWidth + 6) * 2, rowY, thirdWidth, inputHeight,
                pulseOnIdle ? Component.literal("Idle Pulse: ON") : Component.literal("Idle Pulse: OFF"),
                pulseOnIdle ? 0xFF5AD98E : 0xFFD87E7E,
                btn -> {
                    pulseOnIdle = !pulseOnIdle;
                    refreshPulseButtons();
                }
        ));

        rowY += rowGap;
        successPulseTicksInput = new DesktopEditBox(font, contentLeft, rowY, thirdWidth, inputHeight, Component.literal("Success strength (1-15)"));
        successPulseTicksInput.setValue(String.valueOf(Math.max(1, openPayload.successPulseTicks())));
        successPulseTicksInput.setMaxLength(2);
        addRenderableWidget(successPulseTicksInput);

        failurePulseTicksInput = new DesktopEditBox(font, contentLeft + thirdWidth + 6, rowY, thirdWidth, inputHeight, Component.literal("Failure strength (1-15)"));
        failurePulseTicksInput.setValue(String.valueOf(Math.max(1, openPayload.failurePulseTicks())));
        failurePulseTicksInput.setMaxLength(2);
        addRenderableWidget(failurePulseTicksInput);

        DesktopEditBox idlePulseStrengthInput = new DesktopEditBox(
                font,
                contentLeft + (thirdWidth + 6) * 2,
                rowY,
                thirdWidth,
                inputHeight,
                Component.literal("Idle strength (1-15)")
        );
        idlePulseStrengthInput.setValue(String.valueOf(Math.max(1, openPayload.idlePulseStrength())));
        idlePulseStrengthInput.setMaxLength(2);
        addRenderableWidget(idlePulseStrengthInput);
        this.idlePulseStrengthInput = idlePulseStrengthInput;

        rowY += rowGap + 4;
        int actionWidth = (contentWidth - 8) / 2;
        saveButton = addRenderableWidget(new DesktopButton(contentLeft, rowY, actionWidth, 24,
                Component.literal("Save Terminal"), 0xFF62DA8E, btn -> submitSave()));
        closeButton = addRenderableWidget(new DesktopButton(contentLeft + actionWidth + 8, rowY, actionWidth, 24,
                Component.literal("Close"), 0xFF8ABAF1, btn -> onClose()));

        refreshPulseButtons();
        updateAccountButtonsActive();
    }

    @Override
    public void tick() {
        super.tick();
        if (System.currentTimeMillis() > feedbackUntilMillis) {
            feedbackMessage = "";
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
        graphics.fill(0, 0, this.width, this.height, 0xFF0A1B30);

        int frameRight = panelLeft + panelWidth;
        int frameBottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, frameRight + 2, frameBottom + 2, 0xFF2D4C74);
        graphics.fill(panelLeft, panelTop, frameRight, frameBottom, 0xFF1D3558);
        graphics.fill(panelLeft + 1, panelTop + 1, frameRight - 1, panelTop + 28, 0xFF7DA6D7);
        graphics.fill(panelLeft + 10, panelTop + 56, frameRight - 10, frameBottom - 12, 0xFF10263F);

        graphics.drawString(font, "Payment Terminal Configuration", panelLeft + 12, panelTop + 10, 0xFFF6FCFF, false);
        graphics.drawString(font, "Shift-right-click to reopen this panel from the terminal block.",
                panelLeft + 14, panelTop + 33, 0xFFA7D4FF, false);

        int infoY = panelTop + panelHeight - 26;
        String sales = "Sales: $" + MoneyText.abbreviate(BigDecimal.valueOf(totalSalesDollars));
        graphics.drawString(font, sales, panelLeft + 14, infoY, 0xFFB8E4FF, false);

        ShopTerminalAccountSummary selected = getSelectedAccount();
        String accountLine = selected == null
                ? "Merchant account: Not selected"
                : "Merchant account: " + shortAccountLine(selected);
        graphics.drawString(font, accountLine, panelLeft + 14, infoY - 12, 0xFFCFE8FF, false);

        if (!feedbackMessage.isBlank()) {
            int color = feedbackSuccess ? 0xFF7DFFB0 : 0xFFFFA6A6;
            graphics.drawString(font, feedbackMessage, panelLeft + 14, panelTop + panelHeight - 56, color, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // No-op to hard-disable vanilla menu blur/background behavior.
    }

    public void handleSaveResponse(ShopTerminalSaveResponsePayload payload) {
        this.feedbackSuccess = payload.success();
        this.feedbackMessage = payload.message() == null ? "" : payload.message();
        this.feedbackUntilMillis = System.currentTimeMillis() + 4500L;
        if (!payload.success()) {
            return;
        }
        this.shopNameInput.setValue(payload.shopName());
        this.priceInput.setValue(String.valueOf(payload.priceDollars()));
        this.successPulseTicksInput.setValue(String.valueOf(payload.successPulseTicks()));
        this.failurePulseTicksInput.setValue(String.valueOf(payload.failurePulseTicks()));
        this.idlePulseStrengthInput.setValue(String.valueOf(payload.idlePulseStrength()));
        this.pulseOnSuccess = payload.pulseOnSuccess();
        this.pulseOnFailure = payload.pulseOnFailure();
        this.pulseOnIdle = payload.pulseOnIdle();
        this.totalSalesDollars = Math.max(0L, payload.totalSalesDollars());
        refreshPulseButtons();

        UUID updatedMerchant = parseUuid(payload.merchantAccountId());
        if (updatedMerchant != null) {
            for (int i = 0; i < accounts.size(); i++) {
                if (updatedMerchant.equals(accounts.get(i).accountId())) {
                    selectedAccountIndex = i;
                    break;
                }
            }
        }
        if (accountSelectButton != null) {
            accountSelectButton.setMessage(selectedAccountCaption());
        }
        updateAccountButtonsActive();
    }

    private void submitSave() {
        UUID selectedAccount = getSelectedAccountId();
        String merchantId = selectedAccount == null ? "" : selectedAccount.toString();
        PacketDistributor.sendToServer(new ShopTerminalSavePayload(
                openPayload.dimensionId(),
                openPayload.x(),
                openPayload.y(),
                openPayload.z(),
                shopNameInput.getValue(),
                priceInput.getValue(),
                merchantId,
                pulseOnSuccess,
                pulseOnFailure,
                pulseOnIdle,
                successPulseTicksInput.getValue(),
                failurePulseTicksInput.getValue(),
                idlePulseStrengthInput.getValue()
        ));
    }

    private void stepAccount(int direction) {
        if (accounts.isEmpty()) {
            selectedAccountIndex = -1;
            if (accountSelectButton != null) {
                accountSelectButton.setMessage(selectedAccountCaption());
            }
            return;
        }
        if (selectedAccountIndex < 0 || selectedAccountIndex >= accounts.size()) {
            selectedAccountIndex = 0;
        } else {
            int next = selectedAccountIndex + direction;
            if (next < 0) {
                next = accounts.size() - 1;
            } else if (next >= accounts.size()) {
                next = 0;
            }
            selectedAccountIndex = next;
        }
        if (accountSelectButton != null) {
            accountSelectButton.setMessage(selectedAccountCaption());
        }
    }

    private void refreshPulseButtons() {
        if (successPulseToggleButton != null) {
            successPulseToggleButton.setMessage(pulseOnSuccess
                    ? Component.literal("Success Pulse: ON")
                    : Component.literal("Success Pulse: OFF"));
            successPulseToggleButton.setAccentColor(pulseOnSuccess ? 0xFF5AD98E : 0xFFD87E7E);
        }
        if (failurePulseToggleButton != null) {
            failurePulseToggleButton.setMessage(pulseOnFailure
                    ? Component.literal("Failure Pulse: ON")
                    : Component.literal("Failure Pulse: OFF"));
            failurePulseToggleButton.setAccentColor(pulseOnFailure ? 0xFF5AD98E : 0xFFD87E7E);
        }
        if (idlePulseToggleButton != null) {
            idlePulseToggleButton.setMessage(pulseOnIdle
                    ? Component.literal("Idle Pulse: ON")
                    : Component.literal("Idle Pulse: OFF"));
            idlePulseToggleButton.setAccentColor(pulseOnIdle ? 0xFF5AD98E : 0xFFD87E7E);
        }
    }

    private void updateAccountButtonsActive() {
        boolean hasAccounts = !accounts.isEmpty();
        if (accountPrevButton != null) {
            accountPrevButton.active = hasAccounts;
        }
        if (accountNextButton != null) {
            accountNextButton.active = hasAccounts;
        }
        if (accountSelectButton != null) {
            accountSelectButton.active = hasAccounts;
        }
    }

    private UUID getSelectedAccountId() {
        ShopTerminalAccountSummary selected = getSelectedAccount();
        return selected == null ? null : selected.accountId();
    }

    private ShopTerminalAccountSummary getSelectedAccount() {
        if (accounts.isEmpty() || selectedAccountIndex < 0 || selectedAccountIndex >= accounts.size()) {
            return null;
        }
        return accounts.get(selectedAccountIndex);
    }

    private Component selectedAccountCaption() {
        ShopTerminalAccountSummary selected = getSelectedAccount();
        if (selected == null) {
            return Component.literal("No Account Available");
        }
        String prefix = selected.primary() ? "[Primary] " : "";
        return Component.literal(prefix + selected.accountType() + " | $" + MoneyText.abbreviate(BigDecimal.valueOf(parseLongSafe(selected.balance()))));
    }

    private int resolveInitialSelectedAccountIndex() {
        if (accounts.isEmpty()) {
            return -1;
        }
        if (merchantAccountIdFromServer != null) {
            for (int i = 0; i < accounts.size(); i++) {
                if (merchantAccountIdFromServer.equals(accounts.get(i).accountId())) {
                    return i;
                }
            }
        }
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).primary()) {
                return i;
            }
        }
        return 0;
    }

    private static long parseLongSafe(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim()).longValue();
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String shortAccountLine(ShopTerminalAccountSummary selected) {
        String id = selected.accountId().toString();
        String shortId = id.substring(0, Math.min(8, id.length())).toLowerCase(Locale.ROOT);
        return selected.accountType() + " (" + shortId + ") - $" + MoneyText.abbreviate(BigDecimal.valueOf(parseLongSafe(selected.balance())));
    }

    private static String sanitizeOwnerName(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value.trim();
    }
}
