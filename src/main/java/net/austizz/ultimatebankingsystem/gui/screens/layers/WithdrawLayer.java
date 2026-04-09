package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.widgets.AtmEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.WithdrawRequestPayload;
import net.austizz.ultimatebankingsystem.network.WithdrawResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class WithdrawLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private static final String[] PRESET_AMOUNTS = {"20", "50", "100", "200", "500"};

    private EditBox amountField;
    private String resultMessage = null;
    private boolean resultSuccess = false;

    public WithdrawLayer(Minecraft minecraft) {
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

        int quickTop = panelTop + 58;
        int customTop = panelTop + 122;
        int btnSpacing = 4;
        int btnWidth = (contentWidth - ((PRESET_AMOUNTS.length - 1) * btnSpacing)) / PRESET_AMOUNTS.length;
        btnWidth = Math.max(40, btnWidth);
        int totalBtnWidth = PRESET_AMOUNTS.length * btnWidth + (PRESET_AMOUNTS.length - 1) * btnSpacing;
        int startX = panelLeft + (panelWidth - totalBtnWidth) / 2;
        int presetY = quickTop + 20;

        for (int i = 0; i < PRESET_AMOUNTS.length; i++) {
            String amt = PRESET_AMOUNTS[i];
            int x = startX + i * (btnWidth + btnSpacing);
            addWidget(new NineSliceTexturedButton(
                x, presetY,
                btnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("$" + amt).withStyle(ChatFormatting.WHITE),
                btn -> sendWithdraw(amt)
            ));
        }

        int fieldY = customTop + 20;
        int confirmWidth = 84;
        int fieldWidth = contentWidth - (confirmWidth + 8);
        amountField = new AtmEditBox(font, contentLeft, fieldY, fieldWidth, 20, Component.literal(""));
        amountField.setHint(Component.literal("Custom amount...").withStyle(ChatFormatting.WHITE));
        amountField.setMaxLength(15);
        styleEditBox(amountField);
        addWidget(amountField);

        addWidget(new NineSliceTexturedButton(
            contentLeft + fieldWidth + 8, fieldY,
            confirmWidth, 20,
            ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
            4, 4, 4, 4,
            Component.literal("Confirm").withStyle(ChatFormatting.WHITE),
            btn -> {
                String value = amountField.getValue().trim();
                if (!value.isEmpty()) {
                    sendWithdraw(value);
                }
            }
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
    }

    private void sendWithdraw(String amount) {
        var selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            resultMessage = null;
            PacketDistributor.sendToServer(new WithdrawRequestPayload(selected.accountId(), amount));
        }
    }

    public void updateResult(WithdrawResponsePayload payload) {
        if (payload.success()) {
            resultMessage = "Withdrawal successful! New balance: $" + payload.newBalance();
            resultSuccess = true;
            // Update the cached account balance
            var selected = ClientATMData.getSelectedAccount();
            if (selected != null) {
                ClientATMData.setSelectedAccount(new net.austizz.ultimatebankingsystem.network.AccountSummary(
                    selected.accountId(),
                    selected.accountType(),
                    selected.bankName(),
                    payload.newBalance(),
                    selected.isPrimary()
                ));
            }
        } else {
            resultMessage = payload.errorMessage();
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
        int quickTop = panelTop + 58;
        int customTop = panelTop + 122;

        drawCenteredFittedString(graphics, "Withdraw Cash",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        graphics.drawString(font, "Quick Withdraw", contentLeft + 6, quickTop + 6, COLOR_LABEL);
        graphics.drawString(font, "Custom Amount", contentLeft + 6, customTop + 6, COLOR_LABEL);

        if (resultMessage != null) {
            int resultY = panelTop + 44;
            int color = resultSuccess ? COLOR_SUCCESS : COLOR_ERROR;
            drawCenteredFittedString(graphics, resultMessage, panelLeft + panelWidth / 2, resultY, contentWidth, color);
        }
    }
}
