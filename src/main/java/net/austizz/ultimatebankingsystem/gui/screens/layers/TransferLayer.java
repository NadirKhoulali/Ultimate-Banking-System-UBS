package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.gui.widgets.AtmEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.austizz.ultimatebankingsystem.network.TransferRequestPayload;
import net.austizz.ultimatebankingsystem.network.TransferResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class TransferLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private EditBox recipientField;
    private EditBox amountField;
    private NineSliceTexturedButton confirmBtn;
    private NineSliceTexturedButton yesBtn;
    private NineSliceTexturedButton cancelBtn;

    private String resultMessage = "";
    private boolean resultSuccess = false;
    private boolean showConfirmation = false;
    private String pendingRecipient = "";
    private String pendingAmount = "";

    public TransferLayer(Minecraft minecraft) {
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
        int sectionTop = panelTop + 58;

        recipientField = new AtmEditBox(font, contentLeft, sectionTop + 22, contentWidth, 20, Component.literal(""));
        recipientField.setMaxLength(36);
        recipientField.setHint(Component.literal("Recipient Account UUID...").withStyle(ChatFormatting.WHITE));
        styleEditBox(recipientField);
        addWidget(recipientField);

        amountField = new AtmEditBox(font, contentLeft, sectionTop + 56, contentWidth, 20, Component.literal(""));
        amountField.setMaxLength(20);
        amountField.setHint(Component.literal("Enter amount...").withStyle(ChatFormatting.WHITE));
        styleEditBox(amountField);
        addWidget(amountField);

        confirmBtn = addWidget(new NineSliceTexturedButton(
                contentLeft, sectionTop + 86, contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Confirm Transfer").withStyle(ChatFormatting.WHITE),
                btn -> showConfirmationDialog()
        ));

        int btnWidth = (contentWidth - 8) / 2;
        int confirmY = panelTop + 150;
        yesBtn = addWidget(new NineSliceTexturedButton(
                contentLeft, confirmY, btnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Yes").withStyle(ChatFormatting.GREEN),
                btn -> sendTransfer()
        ));
        cancelBtn = addWidget(new NineSliceTexturedButton(
                contentLeft + btnWidth + 8, confirmY, btnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Cancel").withStyle(ChatFormatting.RED),
                btn -> cancelConfirmation()
        ));

        addWidget(new NineSliceTexturedButton(
                panelLeft + 14,
                panelTop + panelHeight - 36,
                56, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));

        updateVisibility();
    }

    private void updateVisibility() {
        recipientField.visible = !showConfirmation;
        amountField.visible = !showConfirmation;
        confirmBtn.visible = !showConfirmation;
        yesBtn.visible = showConfirmation;
        cancelBtn.visible = showConfirmation;
    }

    private void showConfirmationDialog() {
        String recipient = recipientField.getValue().trim();
        String amount = amountField.getValue().trim();

        if (recipient.isEmpty()) {
            resultMessage = "Please enter a recipient UUID.";
            resultSuccess = false;
            return;
        }
        if (amount.isEmpty()) {
            resultMessage = "Please enter an amount.";
            resultSuccess = false;
            return;
        }

        // Validate UUID format
        try {
            UUID.fromString(recipient);
        } catch (IllegalArgumentException e) {
            resultMessage = "Invalid recipient UUID format.";
            resultSuccess = false;
            return;
        }

        pendingRecipient = recipient;
        pendingAmount = amount;
        showConfirmation = true;
        resultMessage = "";
        updateVisibility();
    }

    private void cancelConfirmation() {
        showConfirmation = false;
        pendingRecipient = "";
        pendingAmount = "";
        updateVisibility();
    }

    private void sendTransfer() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            resultMessage = "No account selected.";
            resultSuccess = false;
            showConfirmation = false;
            updateVisibility();
            return;
        }

        UUID recipientId;
        try {
            recipientId = UUID.fromString(pendingRecipient);
        } catch (IllegalArgumentException e) {
            resultMessage = "Invalid recipient UUID.";
            resultSuccess = false;
            showConfirmation = false;
            updateVisibility();
            return;
        }

        resultMessage = "Processing...";
        resultSuccess = false;
        showConfirmation = false;
        updateVisibility();
        PacketDistributor.sendToServer(new TransferRequestPayload(selected.accountId(), recipientId, pendingAmount));
    }

    /**
     * Called by the client-side packet handler when the server responds.
     */
    public void updateResult(TransferResponsePayload payload) {
        if (payload.success()) {
            resultMessage = "Transfer successful! New balance: " + MoneyText.abbreviateWithDollar(payload.newBalance());
            resultSuccess = true;
        } else {
            resultMessage = payload.errorMessage().isEmpty()
                    ? "Transfer failed."
                    : MoneyText.abbreviateCurrencyTokens(payload.errorMessage());
            resultSuccess = false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int sectionTop = panelTop + 58;

        drawCenteredFittedString(graphics, "Transfer Funds",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        if (showConfirmation) {
            String confirmationText = "Transfer " + MoneyText.abbreviateWithDollar(pendingAmount) + " to account ["
                    + pendingRecipient.substring(0, Math.min(8, pendingRecipient.length())) + "...]?";
            drawWrappedCentered(graphics, confirmationText,
                    panelLeft + panelWidth / 2, sectionTop + 28, contentWidth - 10, 0xFFFFFF77, 2);
            drawCenteredFittedString(graphics, "Are you sure?",
                    panelLeft + panelWidth / 2, sectionTop + 72, contentWidth, COLOR_TITLE);
        } else {
            int recipientLabelY = sectionTop + Math.max(0, (recipientField.getY() - sectionTop - font.lineHeight) / 2);
            int recipientBottom = recipientField.getY() + recipientField.getHeight();
            int amountLabelY = recipientBottom + Math.max(0, (amountField.getY() - recipientBottom - font.lineHeight) / 2);
            graphics.drawString(font, "Recipient Account", contentLeft + 6, recipientLabelY, COLOR_LABEL);
            graphics.drawString(font, "Amount", contentLeft + 6, amountLabelY, COLOR_LABEL);
        }

        if (!resultMessage.isEmpty()) {
            int color = resultSuccess ? COLOR_SUCCESS : COLOR_ERROR;
            drawCenteredFittedString(graphics, resultMessage,
                    panelLeft + panelWidth / 2, panelTop + 44, contentWidth, color);
        }
    }
}
