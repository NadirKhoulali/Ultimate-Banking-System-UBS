package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.austizz.ultimatebankingsystem.network.BalanceRequestPayload;
import net.austizz.ultimatebankingsystem.network.BalanceResponsePayload;
import net.austizz.ultimatebankingsystem.network.ChangePinPayload;
import net.austizz.ultimatebankingsystem.network.ChangePinResponsePayload;
import net.austizz.ultimatebankingsystem.network.SetPrimaryPayload;
import net.austizz.ultimatebankingsystem.network.SetPrimaryResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AccountSettingsLayer extends AbstractScreenLayer {
    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    private enum Tab {
        INFO,
        SECURITY
    }

    private Tab activeTab = Tab.INFO;

    private NineSliceTexturedButton infoTabButton;
    private NineSliceTexturedButton securityTabButton;
    private NineSliceTexturedButton copyButton;
    private NineSliceTexturedButton primaryToggleButton;

    private EditBox currentPinField;
    private EditBox newPinField;
    private EditBox confirmPinField;
    private NineSliceTexturedButton changePinButton;
    private NineSliceTexturedButton confirmPinYesButton;
    private NineSliceTexturedButton confirmPinCancelButton;

    private String accountId = "Loading...";
    private String accountType = "Loading...";
    private String bankName = "Loading...";
    private String createdDate = "Loading...";
    private String balance = "Loading...";
    private boolean isPrimary;

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
        int contentLeft = panelLeft + 8;
        int contentWidth = panelWidth - 16;

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            accountId = selected.accountId().toString();
            accountType = selected.accountType();
            bankName = selected.bankName();
            balance = selected.balance();
            isPrimary = selected.isPrimary();
        }

        int tabY = panelTop + 48;
        int tabWidth = (contentWidth - 6) / 2;
        infoTabButton = addWidget(new NineSliceTexturedButton(
                contentLeft, tabY,
                tabWidth, 18,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Info"),
                btn -> switchTab(Tab.INFO)
        ));
        securityTabButton = addWidget(new NineSliceTexturedButton(
                contentLeft + tabWidth + 6, tabY,
                tabWidth, 18,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Security"),
                btn -> switchTab(Tab.SECURITY)
        ));

        int accountIdLabelX = contentLeft;
        int accountIdLabelY = panelTop + 72;
        int copyButtonWidth = 36;
        int copyButtonHeight = 16;
        int copyButtonX = Math.min(
                accountIdLabelX + font.width("Account ID:") + 6,
                panelLeft + panelWidth - 8 - copyButtonWidth
        );
        int copyButtonY = accountIdLabelY - 4;
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
                panelTop + 150,
                contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                primaryLabel(),
                btn -> togglePrimary()
        ));

        currentPinField = new EditBox(font, contentLeft, panelTop + 76, contentWidth, 18, Component.literal(""));
        currentPinField.setMaxLength(32);
        currentPinField.setHint(Component.literal("Current PIN...").withStyle(ChatFormatting.DARK_GRAY));
        addWidget(currentPinField);

        newPinField = new EditBox(font, contentLeft, panelTop + 110, contentWidth, 18, Component.literal(""));
        newPinField.setMaxLength(32);
        newPinField.setHint(Component.literal("New PIN...").withStyle(ChatFormatting.DARK_GRAY));
        addWidget(newPinField);

        confirmPinField = new EditBox(font, contentLeft, panelTop + 144, contentWidth, 18, Component.literal(""));
        confirmPinField.setMaxLength(32);
        confirmPinField.setHint(Component.literal("Confirm new PIN...").withStyle(ChatFormatting.DARK_GRAY));
        addWidget(confirmPinField);

        changePinButton = addWidget(new NineSliceTexturedButton(
                contentLeft,
                panelTop + 168,
                contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Change PIN").withStyle(ChatFormatting.WHITE),
                btn -> requestPinChangeConfirmation()
        ));

        int confirmButtonWidth = (contentWidth - 6) / 2;
        confirmPinYesButton = addWidget(new NineSliceTexturedButton(
                contentLeft,
                panelTop + 168,
                confirmButtonWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Yes").withStyle(ChatFormatting.GREEN),
                btn -> sendPinChange()
        ));
        confirmPinCancelButton = addWidget(new NineSliceTexturedButton(
                contentLeft + confirmButtonWidth + 6,
                panelTop + 168,
                confirmButtonWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Cancel").withStyle(ChatFormatting.RED),
                btn -> cancelPinChangeConfirmation()
        ));

        addWidget(new NineSliceTexturedButton(
                panelLeft + 8,
                panelTop + panelHeight - 24,
                50, 20,
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
    }

    private void updateVisibility() {
        boolean infoVisible = activeTab == Tab.INFO;
        boolean securityVisible = activeTab == Tab.SECURITY;
        boolean pinEditVisible = securityVisible && !showPinConfirmation;

        copyButton.visible = infoVisible;
        primaryToggleButton.visible = infoVisible;

        currentPinField.visible = pinEditVisible;
        newPinField.visible = pinEditVisible;
        confirmPinField.visible = pinEditVisible;
        changePinButton.visible = pinEditVisible;
        confirmPinYesButton.visible = securityVisible && showPinConfirmation;
        confirmPinCancelButton.visible = securityVisible && showPinConfirmation;
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
        String currentPin = currentPinField.getValue().trim();
        String newPin = newPinField.getValue().trim();
        String confirmPin = confirmPinField.getValue().trim();

        if (currentPin.isEmpty()) {
            statusMessage = "Enter your current PIN.";
            statusSuccess = false;
            return;
        }
        if (newPin.isEmpty()) {
            statusMessage = "Enter a new PIN.";
            statusSuccess = false;
            return;
        }
        if (!newPin.equals(confirmPin)) {
            statusMessage = "New PIN and confirm PIN do not match.";
            statusSuccess = false;
            return;
        }

        pendingCurrentPin = currentPin;
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
                    isPrimary
            ));
        }
    }

    public void updatePrimaryResult(SetPrimaryResponsePayload payload) {
        if (payload.success()) {
            isPrimary = payload.newPrimaryState();
            primaryToggleButton.setMessage(primaryLabel());
            statusMessage = "Primary state updated.";
            statusSuccess = true;

            AccountSummary selected = ClientATMData.getSelectedAccount();
            if (selected != null) {
                ClientATMData.setSelectedAccount(new AccountSummary(
                        selected.accountId(),
                        selected.accountType(),
                        selected.bankName(),
                        selected.balance(),
                        isPrimary
                ));
            }
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
        } else {
            statusMessage = payload.errorMessage().isEmpty() ? "PIN change failed." : payload.errorMessage();
            statusSuccess = false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentWidth = panelWidth - 16;
        int sectionColor = 0xFF55FFFF;
        int valueColor = 0xFFFFFFFF;

        drawCenteredFittedString(graphics, "Account Settings",
                panelLeft + panelWidth / 2, panelTop + 27, contentWidth, 0xFFFFFFFF);

        if (activeTab == Tab.INFO) {
            graphics.drawString(font, "Account ID:", panelLeft + 8, panelTop + 72, sectionColor);
            drawFittedString(graphics, accountId, panelLeft + 8, panelTop + 84, contentWidth, valueColor);
            drawFittedString(graphics, "Type: " + accountType, panelLeft + 8, panelTop + 108, contentWidth, valueColor);
            drawFittedString(graphics, "Bank: " + bankName, panelLeft + 8, panelTop + 120, contentWidth, valueColor);
            drawFittedString(graphics, "Created: " + createdDate, panelLeft + 8, panelTop + 132, contentWidth, valueColor);
            graphics.drawString(font, "Primary Account", panelLeft + 8, panelTop + 172, sectionColor);
        } else {
            if (showPinConfirmation) {
                drawWrappedCentered(graphics, "Are you sure you want to change your PIN?",
                        panelLeft + panelWidth / 2, panelTop + 128, contentWidth, 0xFFFFFF55, 2);
            } else {
                graphics.drawString(font, "Current PIN:", panelLeft + 8, panelTop + 68, sectionColor);
                graphics.drawString(font, "New PIN:", panelLeft + 8, panelTop + 98, sectionColor);
                graphics.drawString(font, "Confirm PIN:", panelLeft + 8, panelTop + 132, sectionColor);
            }
        }

        if (!statusMessage.isEmpty()) {
            int statusColor = statusSuccess ? 0xFF55FF55 : 0xFFFF5555;
            drawWrappedCentered(graphics, statusMessage,
                    panelLeft + panelWidth / 2, panelTop + 194, contentWidth, statusColor, 2);
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

}
