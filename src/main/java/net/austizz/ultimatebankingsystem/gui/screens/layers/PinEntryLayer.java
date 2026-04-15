package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.austizz.ultimatebankingsystem.network.ChangePinPayload;
import net.austizz.ultimatebankingsystem.network.ChangePinResponsePayload;
import net.austizz.ultimatebankingsystem.network.PinAuthRequestPayload;
import net.austizz.ultimatebankingsystem.network.PinAuthResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PinEntryLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = new ResourceLocation(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");
    private static final int PIN_LENGTH = 4;

    private enum PinMode {
        ENTER_PIN,
        SET_PIN,
        CONFIRM_PIN
    }

    private final List<NineSliceTexturedButton> keypadButtons = new ArrayList<>();

    private NineSliceTexturedButton switchAccountButton;
    private NineSliceTexturedButton unlockButton;

    private PinMode mode = PinMode.ENTER_PIN;
    private String pinInput = "";
    private String pendingNewPin = "";
    private String statusMessage = "";
    private int statusColor = COLOR_MUTED;
    private boolean awaitingServer;
    private boolean initialAccountDecisionDone;
    private final boolean confirmationOnly;
    private final Component confirmationTitle;
    private final Consumer<String> onConfirmedPin;

    public PinEntryLayer(Minecraft minecraft) {
        super(minecraft);
        this.confirmationOnly = false;
        this.confirmationTitle = Component.literal("ATM PIN Access");
        this.onConfirmedPin = null;
    }

    public PinEntryLayer(Minecraft minecraft, Component confirmationTitle, Consumer<String> onConfirmedPin) {
        super(minecraft);
        this.confirmationOnly = true;
        this.confirmationTitle = confirmationTitle == null ? Component.literal("PIN Confirmation") : confirmationTitle;
        this.onConfirmedPin = onConfirmedPin;
    }

    @Override
    protected void onInit() {
        keypadButtons.clear();

        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int panelHeight = bankScreen.getPanelHeight();

        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;

        int keypadTop = panelTop + 108;
        int colGap = 8;
        int rowGap = 6;
        int keyWidth = (contentWidth - (colGap * 2)) / 3;
        int keyHeight = 22;

        addDigitRow(contentLeft, keypadTop, keyWidth, keyHeight, colGap, "1", "2", "3");
        addDigitRow(contentLeft, keypadTop + (keyHeight + rowGap), keyWidth, keyHeight, colGap, "4", "5", "6");
        addDigitRow(contentLeft, keypadTop + ((keyHeight + rowGap) * 2), keyWidth, keyHeight, colGap, "7", "8", "9");

        int lastRowY = keypadTop + ((keyHeight + rowGap) * 3);
        addKeypadButton(contentLeft, lastRowY, keyWidth, keyHeight, "Clear", this::clearInput);
        addKeypadButton(contentLeft + keyWidth + colGap, lastRowY, keyWidth, keyHeight, "0", () -> appendDigit('0'));
        addKeypadButton(contentLeft + (keyWidth + colGap) * 2, lastRowY, keyWidth, keyHeight, "Del", this::backspace);

        int bottomY = panelTop + panelHeight - 36;
        int backWidth = 56;
        int unlockWidth = 84;
        int bottomGap = 6;
        int switchWidth = contentWidth - backWidth - unlockWidth - (bottomGap * 2);

        addWidget(new NineSliceTexturedButton(
                contentLeft,
                bottomY,
                backWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> {
                    if (confirmationOnly) {
                        bankScreen.popLayer();
                    } else {
                        bankScreen.onClose();
                    }
                }
        ));

        switchAccountButton = addWidget(new NineSliceTexturedButton(
                contentLeft + backWidth + bottomGap,
                bottomY,
                switchWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Switch Account").withStyle(ChatFormatting.WHITE),
                btn -> openAccountChooser()
        ));

        unlockButton = addWidget(new NineSliceTexturedButton(
                contentLeft + backWidth + bottomGap + switchWidth + bottomGap,
                bottomY,
                unlockWidth, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Unlock").withStyle(ChatFormatting.WHITE),
                btn -> submitCurrentInput()
        ));

        syncModeFromSelectedAccount();

        if (confirmationOnly) {
            setStatusInfo("Enter your 4-digit PIN to confirm.");
        } else if (!initialAccountDecisionDone) {
            initialAccountDecisionDone = true;
            maybeOpenInitialAccountChooser();
        }

        refreshButtons();
    }

    private void maybeOpenInitialAccountChooser() {
        List<AccountSummary> accounts = ClientATMData.getAccounts();
        if (accounts.isEmpty()) {
            setStatusError("No accounts available.");
            return;
        }

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            setStatusInfo("Select an account to continue.");
            bankScreen.pushLayer(new AccountSelectionLayer(minecraft, this::onAccountSelected));
            return;
        }

        onAccountSelected(selected);
    }

    private void onAccountSelected(AccountSummary selectedAccount) {
        pinInput = "";
        pendingNewPin = "";
        awaitingServer = false;

        if (selectedAccount == null) {
            mode = PinMode.ENTER_PIN;
            setStatusError("No account selected.");
            refreshButtons();
            return;
        }

        if (!selectedAccount.pinSet()) {
            mode = PinMode.SET_PIN;
            setStatusInfo("PIN not set. Create a new 4-digit PIN.");
        } else {
            mode = PinMode.ENTER_PIN;
            setStatusInfo("Enter your 4-digit PIN.");
        }

        refreshButtons();
    }

    private void syncModeFromSelectedAccount() {
        if (confirmationOnly) {
            mode = PinMode.ENTER_PIN;
            return;
        }

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            mode = PinMode.ENTER_PIN;
            return;
        }

        if (!selected.pinSet()) {
            if (mode == PinMode.ENTER_PIN) {
                mode = PinMode.SET_PIN;
            }
        } else if (mode != PinMode.ENTER_PIN && pendingNewPin.isEmpty()) {
            mode = PinMode.ENTER_PIN;
        }
    }

    private void addDigitRow(int x, int y, int keyWidth, int keyHeight, int gap, String left, String middle, String right) {
        addKeypadButton(x, y, keyWidth, keyHeight, left, () -> appendDigit(left.charAt(0)));
        addKeypadButton(x + keyWidth + gap, y, keyWidth, keyHeight, middle, () -> appendDigit(middle.charAt(0)));
        addKeypadButton(x + (keyWidth + gap) * 2, y, keyWidth, keyHeight, right, () -> appendDigit(right.charAt(0)));
    }

    private void addKeypadButton(int x, int y, int width, int height, String label, Runnable action) {
        NineSliceTexturedButton button = addWidget(new NineSliceTexturedButton(
                x, y,
                width, height,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal(label).withStyle(ChatFormatting.WHITE),
                btn -> action.run()
        ));
        keypadButtons.add(button);
    }

    private void openAccountChooser() {
        if (awaitingServer) {
            return;
        }
        if (confirmationOnly) {
            return;
        }
        if (ClientATMData.getAccounts().isEmpty()) {
            setStatusError("No accounts available.");
            refreshButtons();
            return;
        }

        pinInput = "";
        pendingNewPin = "";
        setStatusInfo("Choose an account.");
        refreshButtons();
        bankScreen.pushLayer(new AccountSelectionLayer(minecraft, this::onAccountSelected));
    }

    private void appendDigit(char digit) {
        if (awaitingServer) {
            return;
        }

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            setStatusError("Select an account first.");
            refreshButtons();
            return;
        }

        if (pinInput.length() >= PIN_LENGTH) {
            setStatusError("PIN can only be 4 digits.");
            refreshButtons();
            return;
        }

        pinInput += digit;
        setStatusInfo("");
        refreshButtons();
    }

    private void backspace() {
        if (awaitingServer || pinInput.isEmpty()) {
            return;
        }
        pinInput = pinInput.substring(0, pinInput.length() - 1);
        refreshButtons();
    }

    private void clearInput() {
        if (awaitingServer || pinInput.isEmpty()) {
            return;
        }
        pinInput = "";
        refreshButtons();
    }

    private void submitCurrentInput() {
        if (awaitingServer) {
            return;
        }

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            setStatusError("No account selected.");
            refreshButtons();
            return;
        }

        if (pinInput.length() != PIN_LENGTH) {
            setStatusError("PIN must be exactly 4 digits.");
            refreshButtons();
            return;
        }

        if (mode == PinMode.ENTER_PIN) {
            awaitingServer = true;
            setStatusInfo("Verifying PIN...");
            refreshButtons();
            PacketDistributor.sendToServer(new PinAuthRequestPayload(selected.accountId(), pinInput));
            return;
        }

        if (mode == PinMode.SET_PIN) {
            pendingNewPin = pinInput;
            pinInput = "";
            mode = PinMode.CONFIRM_PIN;
            setStatusInfo("Repeat the new 4-digit PIN.");
            refreshButtons();
            return;
        }

        if (!pinInput.equals(pendingNewPin)) {
            pendingNewPin = "";
            pinInput = "";
            mode = PinMode.SET_PIN;
            setStatusError("PINs do not match. Start again.");
            refreshButtons();
            return;
        }

        awaitingServer = true;
        setStatusInfo("Saving PIN...");
        refreshButtons();
        PacketDistributor.sendToServer(new ChangePinPayload(selected.accountId(), "", pendingNewPin));
    }

    public void updateAuthResult(PinAuthResponsePayload payload) {
        String enteredPin = pinInput;
        awaitingServer = false;
        pinInput = "";

        if (payload.success()) {
            if (confirmationOnly) {
                if (onConfirmedPin != null) {
                    onConfirmedPin.accept(enteredPin);
                }
                bankScreen.popLayer();
            } else {
                AccountSummary selected = ClientATMData.getSelectedAccount();
                if (selected != null) {
                    ClientATMData.setAuthenticatedAccountId(selected.accountId());
                }
                bankScreen.setRootLayer(new MainMenuLayer(minecraft));
            }
            return;
        }

        if (payload.pinSetupRequired()) {
            pendingNewPin = "";
            mode = PinMode.SET_PIN;
            setStatusInfo(payload.message().isEmpty()
                    ? "PIN not set. Create a 4-digit PIN."
                    : MoneyText.abbreviateCurrencyTokens(payload.message()));
        } else {
            setStatusError(payload.message().isEmpty()
                    ? "Incorrect PIN."
                    : MoneyText.abbreviateCurrencyTokens(payload.message()));
        }

        refreshButtons();
    }

    public void updatePinSetupResult(ChangePinResponsePayload payload) {
        awaitingServer = false;
        pinInput = "";

        if (payload.success()) {
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
                ClientATMData.setAuthenticatedAccountId(selected.accountId());
            }
            pendingNewPin = "";
            mode = PinMode.ENTER_PIN;
            bankScreen.setRootLayer(new MainMenuLayer(minecraft));
            return;
        }

        pendingNewPin = "";
        mode = PinMode.SET_PIN;
        setStatusError(payload.errorMessage().isEmpty() ? "Could not set PIN." : payload.errorMessage());
        refreshButtons();
    }

    private void refreshButtons() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        boolean canType = selected != null && !awaitingServer;

        for (NineSliceTexturedButton button : keypadButtons) {
            button.active = canType;
        }

        if (switchAccountButton != null) {
            switchAccountButton.active = !confirmationOnly && !awaitingServer && ClientATMData.getAccounts().size() > 1;
        }

        if (unlockButton != null) {
            String unlockLabel = switch (mode) {
                case ENTER_PIN -> "Unlock";
                case SET_PIN -> "Next";
                case CONFIRM_PIN -> "Set PIN";
            };
            unlockButton.setMessage(Component.literal(unlockLabel).withStyle(ChatFormatting.WHITE));
            unlockButton.active = canType && pinInput.length() == PIN_LENGTH;
        }
    }

    private void setStatusInfo(String message) {
        statusMessage = message == null ? "" : message;
        statusColor = COLOR_MUTED;
    }

    private void setStatusError(String message) {
        statusMessage = message == null ? "" : message;
        statusColor = COLOR_ERROR;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitCurrentInput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            appendDigit((char) ('0' + (keyCode - GLFW.GLFW_KEY_0)));
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            appendDigit((char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0)));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (Character.isDigit(codePoint)) {
            appendDigit(codePoint);
            return true;
        }
        if (!Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
            setStatusError("PIN must use digits only.");
            refreshButtons();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;

        String title = mode == PinMode.ENTER_PIN ? "ATM PIN Access" : "Set Account PIN";
        if (confirmationOnly) {
            title = confirmationTitle.getString();
        }
        drawCenteredFittedString(graphics, title,
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        if (!statusMessage.isEmpty()) {
            drawCenteredFittedString(graphics, statusMessage,
                    panelLeft + panelWidth / 2, panelTop + 44, contentWidth, statusColor);
        }

        AccountSummary selected = ClientATMData.getSelectedAccount();
        String accountLine = selected == null
                ? "No account selected"
                : fitToWidth(selected.accountType() + " @ " + selected.bankName(), contentWidth - 12);
        drawCenteredFittedString(graphics, accountLine,
                panelLeft + panelWidth / 2, panelTop + 58, contentWidth, COLOR_LABEL);

        int displayTop = panelTop + 72;
        int displayBottom = panelTop + 96;
        drawSectionBox(graphics, contentLeft, displayTop, contentLeft + contentWidth, displayBottom);

        String masked = pinInput.isEmpty() ? "----" : "*".repeat(pinInput.length());
        graphics.drawCenteredString(font, masked, panelLeft + panelWidth / 2, displayTop + 8, 0xFFFFFFFF);
    }
}
