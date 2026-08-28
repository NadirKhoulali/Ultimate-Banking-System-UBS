package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
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

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private EditBox amountField;
    private String resultMessage = "";
    private boolean resultSuccess = false;
    private static final String[] QUICK_AMOUNTS = {"20", "50", "100", "500"};

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
        int sectionTop = panelTop + (bankScreen.isCompactLayout() ? 80 : 88);

        amountField = new AtmEditBox(font, contentLeft, sectionTop + 22, contentWidth, 20, UbsTranslations.literal(""));
        amountField.setMaxLength(20);
        amountField.setHint(UbsTranslations.literal("Enter amount...").withStyle(ChatFormatting.WHITE));
        styleEditBox(amountField);
        addWidget(amountField);

        int quickBtnY = sectionTop + 50;
        int spacing = 5;
        int quickBtnWidth = (contentWidth - spacing * (QUICK_AMOUNTS.length - 1)) / QUICK_AMOUNTS.length;
        for (int i = 0; i < QUICK_AMOUNTS.length; i++) {
            String quickAmount = QUICK_AMOUNTS[i];
            addWidget(new NineSliceTexturedButton(
                    contentLeft + i * (quickBtnWidth + spacing), quickBtnY, quickBtnWidth, 20,
                    ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                    UbsTranslations.literal("$" + quickAmount).withStyle(ChatFormatting.WHITE),
                    btn -> amountField.setValue(quickAmount)
            ));
        }

        int actionGap = 7;
        int actionWidth = (contentWidth - actionGap) / 2;
        addWidget(new NineSliceTexturedButton(
                contentLeft, sectionTop + 79, actionWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                UbsTranslations.literal("Confirm Deposit").withStyle(ChatFormatting.WHITE),
                btn -> sendDeposit()
        ));
        addWidget(new NineSliceTexturedButton(
                contentLeft + actionWidth + actionGap, sectionTop + 79, actionWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                UbsTranslations.literal("Deposit All Cash").withStyle(ChatFormatting.WHITE),
                btn -> sendDeposit("ALL")
        ));

        addWidget(new NineSliceTexturedButton(
                panelLeft + 14,
                panelTop + panelHeight - 36,
                56, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40, 4, 4, 4, 4,
                UbsTranslations.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));
    }

    private void sendDeposit() {
        sendDeposit(amountField.getValue().trim());
    }

    private void sendDeposit(String amount) {
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
            resultMessage = "Deposit successful! New balance: " + MoneyText.abbreviateWithDollar(payload.newBalance());
            resultSuccess = true;
            amountField.setValue("");
            AccountSummary selected = ClientATMData.getSelectedAccount();
            if (selected != null) {
                ClientATMData.setSelectedAccount(new AccountSummary(
                        selected.accountId(), selected.accountType(), selected.bankName(), payload.newBalance(),
                        selected.isPrimary(), selected.pinSet(), selected.defaultWithdrawalLimit(),
                        selected.effectiveWithdrawalLimit(), selected.temporaryWithdrawalLimit(),
                        selected.temporaryLimitExpiresAtGameTime(), selected.dailyWithdrawalLimit(),
                        selected.dailyWithdrawnToday(), selected.dailyWithdrawalRemaining(),
                        selected.dailyResetEpochMillis()));
            }
        } else {
            resultMessage = payload.errorMessage().isEmpty()
                    ? "Deposit failed."
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
        int sectionTop = panelTop + (bankScreen.isCompactLayout() ? 80 : 88);

        drawCenteredFittedString(graphics, "Deposit Funds",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        AccountSummary selected = ClientATMData.getSelectedAccount();
        int cardTop = panelTop + 45;
        int cardBottom = sectionTop - 8;
        drawSectionBox(graphics, contentLeft, cardTop, contentLeft + contentWidth, cardBottom);
        if (!resultMessage.isEmpty()) {
            drawCenteredFittedString(graphics, resultMessage,
                    panelLeft + panelWidth / 2, cardTop + 11, contentWidth - 14,
                    resultSuccess ? COLOR_SUCCESS : COLOR_ERROR);
        } else if (selected != null) {
            drawFittedString(graphics, selected.bankName() + "  |  " + selected.accountType(),
                    contentLeft + 7, cardTop + 6, contentWidth - 14, COLOR_LABEL);
            drawRightAlignedFittedString(graphics, MoneyText.abbreviateWithDollar(selected.balance()),
                    contentLeft + contentWidth - 7, cardTop + 18, contentWidth / 2, COLOR_VALUE);
            drawFittedString(graphics, "Destination account", contentLeft + 7, cardTop + 18,
                    contentWidth / 2, COLOR_MUTED);
        }

        graphics.drawString(font, UbsClientTranslations.resolve("CASH DEPOSIT AMOUNT"), contentLeft + 2, sectionTop + 6, COLOR_LABEL);

        drawRightAlignedFittedString(graphics, "Uses held wallet first, otherwise inventory",
                panelLeft + panelWidth - 14, panelTop + bankScreen.getPanelHeight() - 33,
                contentWidth - 66, COLOR_MUTED);
    }
}
