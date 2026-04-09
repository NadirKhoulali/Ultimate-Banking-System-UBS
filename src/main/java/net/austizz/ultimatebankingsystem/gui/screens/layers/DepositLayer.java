package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.widgets.AtmEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.austizz.ultimatebankingsystem.network.DepositRequestPayload;
import net.austizz.ultimatebankingsystem.network.DepositResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class DepositLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private EditBox amountField;
    private String resultMessage = "";
    private boolean resultSuccess = false;

    public DepositLayer(Minecraft minecraft) {
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

        amountField = new AtmEditBox(font, contentLeft, sectionTop + 22, contentWidth, 20, Component.literal(""));
        amountField.setMaxLength(20);
        amountField.setHint(Component.literal("Enter amount...").withStyle(ChatFormatting.WHITE));
        styleEditBox(amountField);
        addWidget(amountField);

        int quickBtnY = sectionTop + 54;
        int quickBtnWidth = (contentWidth - 12) / 3;
        int spacing = 6;

        addWidget(new NineSliceTexturedButton(
                contentLeft, quickBtnY, quickBtnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("$50").withStyle(ChatFormatting.WHITE),
                btn -> amountField.setValue("50")
        ));
        addWidget(new NineSliceTexturedButton(
                contentLeft + quickBtnWidth + spacing, quickBtnY, quickBtnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("$100").withStyle(ChatFormatting.WHITE),
                btn -> amountField.setValue("100")
        ));
        addWidget(new NineSliceTexturedButton(
                contentLeft + (quickBtnWidth + spacing) * 2, quickBtnY, quickBtnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("$500").withStyle(ChatFormatting.WHITE),
                btn -> amountField.setValue("500")
        ));

        addWidget(new NineSliceTexturedButton(
                contentLeft, sectionTop + 84, contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Confirm Deposit").withStyle(ChatFormatting.WHITE),
                btn -> sendDeposit()
        ));

        addWidget(new NineSliceTexturedButton(
                panelLeft + 14,
                panelTop + panelHeight - 36,
                56, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));
    }

    private void sendDeposit() {
        String amount = amountField.getValue().trim();
        if (amount.isEmpty()) {
            resultMessage = "Please enter an amount.";
            resultSuccess = false;
            return;
        }

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            resultMessage = "No account selected.";
            resultSuccess = false;
            return;
        }

        resultMessage = "Processing...";
        resultSuccess = false;
        PacketDistributor.sendToServer(new DepositRequestPayload(selected.accountId(), amount));
    }

    /**
     * Called by the client-side packet handler when the server responds.
     */
    public void updateResult(DepositResponsePayload payload) {
        if (payload.success()) {
            resultMessage = "Deposit successful! New balance: $" + payload.newBalance();
            resultSuccess = true;
            amountField.setValue("");
        } else {
            resultMessage = payload.errorMessage().isEmpty() ? "Deposit failed." : payload.errorMessage();
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
        int sectionBottom = sectionTop + 112;

        drawCenteredFittedString(graphics, "Deposit Funds",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        graphics.drawString(font, "Amount to Deposit", contentLeft + 6, sectionTop + 6, COLOR_LABEL);

        if (!resultMessage.isEmpty()) {
            int color = resultSuccess ? COLOR_SUCCESS : COLOR_ERROR;
            drawCenteredFittedString(graphics, resultMessage,
                    panelLeft + panelWidth / 2, panelTop + 44, contentWidth, color);
        }
    }
}
