package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
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

        int contentLeft = panelLeft + 8;
        int contentWidth = panelWidth - 16;

        // Recipient UUID edit box
        recipientField = new EditBox(font, contentLeft, panelTop + 50, contentWidth, 16, Component.literal(""));
        recipientField.setMaxLength(36);
        recipientField.setHint(Component.literal("Recipient Account UUID...").withStyle(ChatFormatting.DARK_GRAY));
        addWidget(recipientField);

        // Amount edit box
        amountField = new EditBox(font, contentLeft, panelTop + 84, contentWidth, 16, Component.literal(""));
        amountField.setMaxLength(20);
        amountField.setHint(Component.literal("Enter amount...").withStyle(ChatFormatting.DARK_GRAY));
        addWidget(amountField);

        // Confirm button — shows confirmation dialog
        confirmBtn = addWidget(new NineSliceTexturedButton(
                contentLeft, panelTop + 108, contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Confirm Transfer").withStyle(ChatFormatting.WHITE),
                btn -> showConfirmationDialog()
        ));

        // Yes/Cancel confirmation buttons (hidden by default)
        int btnWidth = (contentWidth - 10) / 2;
        yesBtn = addWidget(new NineSliceTexturedButton(
                contentLeft, panelTop + 140, btnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Yes").withStyle(ChatFormatting.GREEN),
                btn -> sendTransfer()
        ));
        cancelBtn = addWidget(new NineSliceTexturedButton(
                contentLeft + btnWidth + 10, panelTop + 140, btnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Cancel").withStyle(ChatFormatting.RED),
                btn -> cancelConfirmation()
        ));

        // Back button
        addWidget(new NineSliceTexturedButton(
                panelLeft + 10,
                panelTop + bankScreen.getPanelHeight() - 30,
                50, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));

        // Apply initial visibility
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
            resultMessage = "Transfer successful! New balance: $" + payload.newBalance();
            resultSuccess = true;
        } else {
            resultMessage = payload.errorMessage().isEmpty() ? "Transfer failed." : payload.errorMessage();
            resultSuccess = false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentWidth = panelWidth - 20;

        drawCenteredFittedString(graphics, "Transfer Funds",
                panelLeft + panelWidth / 2, panelTop + 28, contentWidth, 0xFFFFFFFF);

        if (showConfirmation) {
            // Confirmation dialog text
            String confirmationText = "Transfer $" + pendingAmount + " to account ["
                    + pendingRecipient.substring(0, Math.min(8, pendingRecipient.length())) + "...]?";
            drawWrappedCentered(graphics, confirmationText,
                    panelLeft + panelWidth / 2, panelTop + 82, contentWidth, 0xFFFFFF55, 2);
            drawCenteredFittedString(graphics, "Are you sure?",
                    panelLeft + panelWidth / 2, panelTop + 112, contentWidth, 0xFFFFFFFF);
        } else {
            // Field labels
            graphics.drawString(font, "Recipient:", panelLeft + 8, panelTop + 42, 0xFF55FFFF);
            graphics.drawString(font, "Amount:", panelLeft + 8, panelTop + 76, 0xFF55FFFF);
        }

        // Result message
        if (!resultMessage.isEmpty()) {
            int color = resultSuccess ? 0xFF55FF55 : 0xFFFF5555;
            drawWrappedCentered(graphics, resultMessage,
                    panelLeft + panelWidth / 2, panelTop + 168, contentWidth, color, 2);
        }
    }
}
