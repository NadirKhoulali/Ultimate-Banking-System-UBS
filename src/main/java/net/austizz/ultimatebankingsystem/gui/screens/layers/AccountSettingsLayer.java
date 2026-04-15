package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.gui.widgets.AtmEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.austizz.ultimatebankingsystem.network.BalanceRequestPayload;
import net.austizz.ultimatebankingsystem.network.BalanceResponsePayload;
import net.austizz.ultimatebankingsystem.network.ChangePinPayload;
import net.austizz.ultimatebankingsystem.network.ChangePinResponsePayload;
import net.austizz.ultimatebankingsystem.network.SetPrimaryPayload;
import net.austizz.ultimatebankingsystem.network.SetPrimaryResponsePayload;
import net.austizz.ultimatebankingsystem.network.SetTemporaryWithdrawalLimitPayload;
import net.austizz.ultimatebankingsystem.network.SetTemporaryWithdrawalLimitResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;

public class AccountSettingsLayer extends AbstractScreenLayer {
    private static final ResourceLocation ATM_BUTTONS = new ResourceLocation(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    private enum Tab {
        INFO,
        SECURITY,
        LIMITS
    }

    private Tab activeTab = Tab.INFO;

    private NineSliceTexturedButton infoTabButton;
    private NineSliceTexturedButton securityTabButton;
    private NineSliceTexturedButton limitsTabButton;
    private NineSliceTexturedButton copyButton;
    private NineSliceTexturedButton primaryToggleButton;

    private EditBox currentPinField;
    private EditBox newPinField;
    private EditBox confirmPinField;
    private NineSliceTexturedButton changePinButton;
    private NineSliceTexturedButton confirmPinYesButton;
    private NineSliceTexturedButton confirmPinCancelButton;

    private EditBox temporaryLimitField;
    private NineSliceTexturedButton applyLimitButton;
    private String pendingTemporaryLimit = "";

    private String accountId = "Loading...";
    private String accountType = "Loading...";
    private String bankName = "Loading...";
    private String createdDate = "Loading...";
    private String balance = "Loading...";
    private boolean isPrimary;
    private String defaultWithdrawalLimit = "0";
    private String effectiveWithdrawalLimit = "0";
    private String temporaryWithdrawalLimit = "";
    private long temporaryLimitExpiresAtGameTime = -1L;
    private String dailyWithdrawalLimit = "0";
    private String dailyWithdrawnToday = "0";
    private String dailyWithdrawalRemaining = "0";
    private long dailyResetEpochMillis = -1L;

    private boolean showPinConfirmation;
    private String pendingCurrentPin = "";
    private String pendingNewPin = "";
    private String statusMessage = "";
    private boolean statusSuccess;

    public AccountSettingsLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int panelHeight = bankScreen.getPanelHeight();
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            accountId = selected.accountId().toString();
            accountType = selected.accountType();
            bankName = selected.bankName();
            balance = selected.balance();
            isPrimary = selected.isPrimary();
            defaultWithdrawalLimit = selected.defaultWithdrawalLimit();
            effectiveWithdrawalLimit = selected.effectiveWithdrawalLimit();
            temporaryWithdrawalLimit = selected.temporaryWithdrawalLimit();
            temporaryLimitExpiresAtGameTime = selected.temporaryLimitExpiresAtGameTime();
            dailyWithdrawalLimit = selected.dailyWithdrawalLimit();
            dailyWithdrawnToday = selected.dailyWithdrawnToday();
            dailyWithdrawalRemaining = selected.dailyWithdrawalRemaining();
            dailyResetEpochMillis = selected.dailyResetEpochMillis();
        }

        int tabY = panelTop + 58;
        int tabWidth = (contentWidth - 16) / 3;
        infoTabButton = addWidget(new NineSliceTexturedButton(
                contentLeft, tabY,
                tabWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Info"),
                btn -> switchTab(Tab.INFO)
        ));
        securityTabButton = addWidget(new NineSliceTexturedButton(
                contentLeft + tabWidth + 8, tabY,
                tabWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Security"),
                btn -> switchTab(Tab.SECURITY)
        ));
        limitsTabButton = addWidget(new NineSliceTexturedButton(
                contentLeft + (tabWidth + 8) * 2, tabY,
                tabWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Limits"),
                btn -> switchTab(Tab.LIMITS)
        ));

        int infoTop = panelTop + 88;
        int accountIdLabelX = contentLeft;
        int accountIdLabelY = infoTop + 8;
        int copyButtonWidth = 40;
        int copyButtonHeight = 18;
        int copyButtonX = Math.min(
                accountIdLabelX + font.width("Account ID:") + 10,
                panelLeft + panelWidth - 14 - copyButtonWidth
        );
        int copyButtonY = accountIdLabelY - 6;
        copyButton = addWidget(new NineSliceTexturedButton(
                copyButtonX,
                copyButtonY,
                copyButtonWidth, copyButtonHeight,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Copy").withStyle(ChatFormatting.WHITE),
                btn -> copyAccountId()
        ));

        primaryToggleButton = addWidget(new NineSliceTexturedButton(
                contentLeft,
                panelTop + 176,
                contentWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                primaryLabel(),
                btn -> togglePrimary()
        ));

        currentPinField = new AtmEditBox(font, contentLeft, panelTop + 108, contentWidth, 20, Component.literal(""));
        currentPinField.setMaxLength(4);
        currentPinField.setHint(Component.literal("Current 4-digit PIN...").withStyle(ChatFormatting.WHITE));
        styleEditBox(currentPinField);
        addWidget(currentPinField);

        newPinField = new AtmEditBox(font, contentLeft, panelTop + 142, contentWidth, 20, Component.literal(""));
        newPinField.setMaxLength(4);
        newPinField.setHint(Component.literal("New 4-digit PIN...").withStyle(ChatFormatting.WHITE));
        styleEditBox(newPinField);
        addWidget(newPinField);

        confirmPinField = new AtmEditBox(font, contentLeft, panelTop + 176, contentWidth, 20, Component.literal(""));
        confirmPinField.setMaxLength(4);
        confirmPinField.setHint(Component.literal("Repeat new 4-digit PIN...").withStyle(ChatFormatting.WHITE));
        styleEditBox(confirmPinField);
        addWidget(confirmPinField);

        int confirmButtonWidth = (contentWidth - 8) / 2;
        int pinActionY = panelTop + 204;
        int changePinButtonWidth = confirmButtonWidth;
        int changePinButtonX = contentLeft + (contentWidth - changePinButtonWidth) / 2;
        changePinButton = addWidget(new NineSliceTexturedButton(
                changePinButtonX,
                pinActionY,
                changePinButtonWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Change PIN").withStyle(ChatFormatting.WHITE),
                btn -> requestPinChangeConfirmation()
        ));

        confirmPinYesButton = addWidget(new NineSliceTexturedButton(
                contentLeft,
                pinActionY,
                confirmButtonWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Yes").withStyle(ChatFormatting.GREEN),
                btn -> sendPinChange()
        ));
        confirmPinCancelButton = addWidget(new NineSliceTexturedButton(
                contentLeft + confirmButtonWidth + 8,
                pinActionY,
                confirmButtonWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Cancel").withStyle(ChatFormatting.RED),
                btn -> cancelPinChangeConfirmation()
        ));

        temporaryLimitField = new AtmEditBox(font, contentLeft, panelTop + 150, contentWidth, 20, Component.literal(""));
        temporaryLimitField.setMaxLength(12);
        temporaryLimitField.setHint(Component.literal("Custom limit (whole dollars)...").withStyle(ChatFormatting.WHITE));
        styleEditBox(temporaryLimitField);
        addWidget(temporaryLimitField);

        applyLimitButton = addWidget(new NineSliceTexturedButton(
                contentLeft,
                panelTop + 188,
                contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Apply Temporary Limit").withStyle(ChatFormatting.WHITE),
                btn -> applyTemporaryLimit()
        ));

        addWidget(new NineSliceTexturedButton(
                panelLeft + 14,
                panelTop + panelHeight - 36,
                56, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));

        updateTabButtons();
        updateVisibility();

        if (selected != null) {
            PacketDistributor.sendToServer(new BalanceRequestPayload(selected.accountId()));
        }
    }

    private void switchTab(Tab nextTab) {
        if (activeTab == nextTab) {
            return;
        }

        activeTab = nextTab;
        if (activeTab != Tab.SECURITY) {
            showPinConfirmation = false;
            pendingCurrentPin = "";
            pendingNewPin = "";
        }
        updateTabButtons();
        updateVisibility();
    }

    private void updateTabButtons() {
        infoTabButton.setMessage(Component.literal(activeTab == Tab.INFO ? "Info *" : "Info"));
        securityTabButton.setMessage(Component.literal(activeTab == Tab.SECURITY ? "Security *" : "Security"));
        limitsTabButton.setMessage(Component.literal(activeTab == Tab.LIMITS ? "Limits *" : "Limits"));
    }

    private void updateVisibility() {
        boolean infoVisible = activeTab == Tab.INFO;
        boolean securityVisible = activeTab == Tab.SECURITY;
        boolean limitsVisible = activeTab == Tab.LIMITS;
        boolean pinEditVisible = securityVisible && !showPinConfirmation;

        copyButton.visible = infoVisible;
        primaryToggleButton.visible = infoVisible;

        currentPinField.visible = pinEditVisible;
        newPinField.visible = pinEditVisible;
        confirmPinField.visible = pinEditVisible;
        changePinButton.visible = pinEditVisible;
        confirmPinYesButton.visible = securityVisible && showPinConfirmation;
        confirmPinCancelButton.visible = securityVisible && showPinConfirmation;

        temporaryLimitField.visible = limitsVisible;
        applyLimitButton.visible = limitsVisible;
    }

    private void applyTemporaryLimit() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            statusMessage = "No account selected.";
            statusSuccess = false;
            return;
        }

        String rawLimit = temporaryLimitField.getValue().trim();

        if (rawLimit.isEmpty()) {
            statusMessage = "Enter a custom limit amount.";
            statusSuccess = false;
            return;
        }
        if (!rawLimit.matches("\\d+")) {
            statusMessage = "Custom limit must be a whole dollar amount.";
            statusSuccess = false;
            return;
        }

        pendingTemporaryLimit = rawLimit;
        statusMessage = "Confirm with your PIN to apply the limit.";
        statusSuccess = false;
        bankScreen.pushLayer(new PinEntryLayer(
                minecraft,
                Component.literal("Confirm Temporary Limit"),
                confirmedPin -> sendTemporaryLimitAfterPin(confirmedPin)
        ));
    }

    private void sendTemporaryLimitAfterPin(String confirmedPin) {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            statusMessage = "No account selected.";
            statusSuccess = false;
            return;
        }
        if (pendingTemporaryLimit.isBlank()) {
            statusMessage = "No temporary limit pending.";
            statusSuccess = false;
            return;
        }

        statusMessage = "Applying temporary limit...";
        statusSuccess = false;
        PacketDistributor.sendToServer(new SetTemporaryWithdrawalLimitPayload(selected.accountId(), pendingTemporaryLimit, confirmedPin));
    }

    private void copyAccountId() {
        if (accountId == null || accountId.isBlank() || "Loading...".equals(accountId)) {
            statusMessage = "Account ID not loaded yet.";
            statusSuccess = false;
            return;
        }

        minecraft.keyboardHandler.setClipboard(accountId);
        statusMessage = "Account ID copied to clipboard.";
        statusSuccess = true;
    }

    private void togglePrimary() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            statusMessage = "No account selected.";
            statusSuccess = false;
            return;
        }

        statusMessage = "Updating primary state...";
        statusSuccess = false;
        PacketDistributor.sendToServer(new SetPrimaryPayload(selected.accountId(), !isPrimary));
    }

    private void requestPinChangeConfirmation() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        boolean requiresCurrentPin = selected != null && selected.pinSet();

        String currentPin = currentPinField.getValue().trim();
        String newPin = newPinField.getValue().trim();
        String confirmPin = confirmPinField.getValue().trim();

        if (requiresCurrentPin) {
            if (currentPin.isEmpty()) {
                statusMessage = "Enter your current PIN.";
                statusSuccess = false;
                return;
            }
            if (!currentPin.matches("\\d{4}")) {
                statusMessage = "Current PIN must be exactly 4 digits.";
                statusSuccess = false;
                return;
            }
        }
        if (newPin.isEmpty()) {
            statusMessage = "Enter a new PIN.";
            statusSuccess = false;
            return;
        }
        if (!newPin.matches("\\d{4}")) {
            statusMessage = "New PIN must be exactly 4 digits.";
            statusSuccess = false;
            return;
        }
        if (!confirmPin.matches("\\d{4}")) {
            statusMessage = "Confirm PIN must be exactly 4 digits.";
            statusSuccess = false;
            return;
        }
        if (!newPin.equals(confirmPin)) {
            statusMessage = "New PIN and confirm PIN do not match.";
            statusSuccess = false;
            return;
        }

        pendingCurrentPin = requiresCurrentPin ? currentPin : "";
        pendingNewPin = newPin;
        showPinConfirmation = true;
        statusMessage = "";
        updateVisibility();
    }

    private void sendPinChange() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            statusMessage = "No account selected.";
            statusSuccess = false;
            showPinConfirmation = false;
            updateVisibility();
            return;
        }

        showPinConfirmation = false;
        updateVisibility();
        statusMessage = "Processing PIN change...";
        statusSuccess = false;
        PacketDistributor.sendToServer(new ChangePinPayload(selected.accountId(), pendingCurrentPin, pendingNewPin));
    }

    private void cancelPinChangeConfirmation() {
        showPinConfirmation = false;
        pendingCurrentPin = "";
        pendingNewPin = "";
        statusMessage = "PIN change cancelled.";
        statusSuccess = false;
        updateVisibility();
    }

    private Component primaryLabel() {
        return Component.literal("Primary: " + (isPrimary ? "YES" : "NO"))
                .withStyle(isPrimary ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    public void updateAccountInfo(BalanceResponsePayload payload) {
        accountType = payload.accountType();
        bankName = payload.bankName();
        accountId = payload.accountId();
        balance = payload.balance();
        createdDate = formatCreatedDate(payload.createdDate());

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            ClientATMData.setSelectedAccount(new AccountSummary(
                    selected.accountId(),
                    accountType,
                    bankName,
                    balance,
                    isPrimary,
                    selected.pinSet(),
                    selected.defaultWithdrawalLimit(),
                    selected.effectiveWithdrawalLimit(),
                    selected.temporaryWithdrawalLimit(),
                    selected.temporaryLimitExpiresAtGameTime(),
                    selected.dailyWithdrawalLimit(),
                    selected.dailyWithdrawnToday(),
                    selected.dailyWithdrawalRemaining(),
                    selected.dailyResetEpochMillis()
            ));
        }
    }

    public void updatePrimaryResult(SetPrimaryResponsePayload payload) {
        if (payload.success()) {
            AccountSummary selected = ClientATMData.getSelectedAccount();
            if (selected != null) {
                ClientATMData.applyPrimaryState(selected.accountId(), payload.newPrimaryState());
                AccountSummary refreshed = ClientATMData.getSelectedAccount();
                isPrimary = refreshed != null ? refreshed.isPrimary() : payload.newPrimaryState();
            } else {
                isPrimary = payload.newPrimaryState();
            }
            primaryToggleButton.setMessage(primaryLabel());
            statusMessage = "Primary state updated.";
            statusSuccess = true;
        } else {
            statusMessage = "Could not update primary state.";
            statusSuccess = false;
        }
    }

    public void updatePinResult(ChangePinResponsePayload payload) {
        if (payload.success()) {
            statusMessage = "PIN changed successfully.";
            statusSuccess = true;
            currentPinField.setValue("");
            newPinField.setValue("");
            confirmPinField.setValue("");
            pendingCurrentPin = "";
            pendingNewPin = "";

            AccountSummary selected = ClientATMData.getSelectedAccount();
            if (selected != null) {
                ClientATMData.setSelectedAccount(new AccountSummary(
                        selected.accountId(),
                        selected.accountType(),
                        selected.bankName(),
                        selected.balance(),
                        selected.isPrimary(),
                        true,
                        selected.defaultWithdrawalLimit(),
                        selected.effectiveWithdrawalLimit(),
                        selected.temporaryWithdrawalLimit(),
                        selected.temporaryLimitExpiresAtGameTime(),
                        selected.dailyWithdrawalLimit(),
                        selected.dailyWithdrawnToday(),
                        selected.dailyWithdrawalRemaining(),
                        selected.dailyResetEpochMillis()
                ));
            }
        } else {
            statusMessage = payload.errorMessage().isEmpty()
                    ? "PIN change failed."
                    : MoneyText.abbreviateCurrencyTokens(payload.errorMessage());
            statusSuccess = false;
        }
    }

    public void updateWithdrawalLimitResult(SetTemporaryWithdrawalLimitResponsePayload payload) {
        if (!payload.success()) {
            statusMessage = payload.errorMessage().isEmpty()
                    ? "Could not apply temporary limit."
                    : MoneyText.abbreviateCurrencyTokens(payload.errorMessage());
            statusSuccess = false;
            return;
        }

        defaultWithdrawalLimit = payload.defaultLimit();
        effectiveWithdrawalLimit = payload.effectiveLimit();
        temporaryWithdrawalLimit = payload.temporaryLimit();
        temporaryLimitExpiresAtGameTime = payload.temporaryLimitExpiresAtGameTime();
        pendingTemporaryLimit = "";

        temporaryLimitField.setValue(temporaryWithdrawalLimit);
        statusMessage = "Temporary withdrawal limit applied for one Minecraft day.";
        statusSuccess = true;

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            ClientATMData.setSelectedAccount(new AccountSummary(
                    selected.accountId(),
                    selected.accountType(),
                    selected.bankName(),
                    selected.balance(),
                    selected.isPrimary(),
                    selected.pinSet(),
                    defaultWithdrawalLimit,
                    effectiveWithdrawalLimit,
                    temporaryWithdrawalLimit,
                    temporaryLimitExpiresAtGameTime,
                    selected.dailyWithdrawalLimit(),
                    selected.dailyWithdrawnToday(),
                    selected.dailyWithdrawalRemaining(),
                    selected.dailyResetEpochMillis()
            ));
        }
    }

    @Override
    public void tick() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            return;
        }

        boolean changed = !defaultWithdrawalLimit.equals(selected.defaultWithdrawalLimit())
                || !effectiveWithdrawalLimit.equals(selected.effectiveWithdrawalLimit())
                || !temporaryWithdrawalLimit.equals(selected.temporaryWithdrawalLimit())
                || temporaryLimitExpiresAtGameTime != selected.temporaryLimitExpiresAtGameTime()
                || !dailyWithdrawalLimit.equals(selected.dailyWithdrawalLimit())
                || !dailyWithdrawnToday.equals(selected.dailyWithdrawnToday())
                || !dailyWithdrawalRemaining.equals(selected.dailyWithdrawalRemaining())
                || dailyResetEpochMillis != selected.dailyResetEpochMillis();

        if (!changed) {
            return;
        }

        defaultWithdrawalLimit = selected.defaultWithdrawalLimit();
        effectiveWithdrawalLimit = selected.effectiveWithdrawalLimit();
        temporaryWithdrawalLimit = selected.temporaryWithdrawalLimit();
        temporaryLimitExpiresAtGameTime = selected.temporaryLimitExpiresAtGameTime();
        dailyWithdrawalLimit = selected.dailyWithdrawalLimit();
        dailyWithdrawnToday = selected.dailyWithdrawnToday();
        dailyWithdrawalRemaining = selected.dailyWithdrawalRemaining();
        dailyResetEpochMillis = selected.dailyResetEpochMillis();

        if (activeTab == Tab.LIMITS && temporaryLimitField != null && temporaryLimitField.getValue().isBlank()) {
            temporaryLimitField.setValue(temporaryWithdrawalLimit);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int sectionTop = panelTop + 88;

        drawCenteredFittedString(graphics, "Account Settings",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        if (activeTab == Tab.INFO) {
            graphics.drawString(font, "Account ID:", contentLeft + 8, sectionTop + 8, COLOR_LABEL);
            drawFittedString(graphics, accountId, contentLeft + 8, sectionTop + 20, contentWidth - 16, COLOR_VALUE);
            drawFittedString(graphics, "Type: " + accountType, contentLeft + 8, sectionTop + 36, contentWidth - 16, COLOR_VALUE);
            drawFittedString(graphics, "Bank: " + bankName, contentLeft + 8, sectionTop + 48, contentWidth - 16, COLOR_VALUE);
            drawFittedString(graphics, "Created: " + createdDate, contentLeft + 8, sectionTop + 60, contentWidth - 16, COLOR_VALUE);
            graphics.drawString(font, "Primary Account", contentLeft + 2, panelTop + 168, COLOR_LABEL);
        } else if (activeTab == Tab.SECURITY) {
            if (showPinConfirmation) {
                drawWrappedCentered(graphics, "Are you sure you want to change your PIN?",
                        panelLeft + panelWidth / 2, panelTop + 124, contentWidth, 0xFFFFFF77, 2);
            } else {
                int currentBottom = currentPinField.getY() + currentPinField.getHeight();
                int newTop = newPinField.getY();
                int newLabelY = currentBottom + Math.max(0, (newTop - currentBottom - font.lineHeight) / 2);

                int newBottom = newPinField.getY() + newPinField.getHeight();
                int confirmTop = confirmPinField.getY();
                int confirmLabelY = newBottom + Math.max(0, (confirmTop - newBottom - font.lineHeight) / 2);

                graphics.drawString(font, "Current PIN", contentLeft + 6, panelTop + 94, COLOR_LABEL);
                graphics.drawString(font, "New PIN", contentLeft + 6, newLabelY, COLOR_LABEL);
                graphics.drawString(font, "Confirm PIN", contentLeft + 6, confirmLabelY, COLOR_LABEL);
            }
        } else {
            graphics.drawString(font, "Per-withdrawal limit: " + MoneyText.abbreviateWithDollar(defaultWithdrawalLimit), contentLeft + 6, sectionTop + 8, COLOR_LABEL);
            graphics.drawString(font, "Active withdrawal limit: " + MoneyText.abbreviateWithDollar(effectiveWithdrawalLimit), contentLeft + 152, sectionTop + 8, COLOR_VALUE);
            graphics.drawString(font, "Daily withdrawal limit: " + MoneyText.abbreviateWithDollar(dailyWithdrawalLimit), contentLeft + 6, sectionTop + 22, COLOR_LABEL);
            graphics.drawString(font, "Used today: " + MoneyText.abbreviateWithDollar(dailyWithdrawnToday), contentLeft + 152, sectionTop + 22, COLOR_VALUE);
            graphics.drawString(font, "Remaining today: " + MoneyText.abbreviateWithDollar(dailyWithdrawalRemaining), contentLeft + 6, sectionTop + 36, COLOR_VALUE);
            drawFittedString(graphics, "Resets: " + formatResetTime(dailyResetEpochMillis), contentLeft + 152, sectionTop + 36, contentWidth - 156, COLOR_MUTED);

            graphics.drawString(font, "Custom limit", contentLeft + 6, panelTop + 136, COLOR_LABEL);
            drawFittedString(graphics, "Apply will open PIN keypad confirmation.", contentLeft + 6, panelTop + 174, contentWidth - 12, COLOR_MUTED);
        }

        if (!statusMessage.isEmpty()) {
            int statusColor = statusSuccess ? COLOR_SUCCESS : COLOR_ERROR;
            drawCenteredFittedString(graphics, statusMessage,
                    panelLeft + panelWidth / 2, panelTop + 44, contentWidth, statusColor);
        }
    }

    private static String formatCreatedDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "Unknown";
        }
        try {
            return DATE_FORMATTER.format(LocalDateTime.parse(rawDate));
        } catch (DateTimeParseException ignored) {
            return rawDate.replace('T', ' ');
        }
    }

    private static String formatResetTime(long epochMillis) {
        if (epochMillis <= 0L) {
            return "Unknown";
        }
        try {
            return DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(java.time.Instant.ofEpochMilli(epochMillis));
        } catch (Exception ignored) {
            return "Unknown";
        }
    }

}
