package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
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

        int contentLeft = panelLeft + 10;
        int contentWidth = panelWidth - 20;

        // Preset amount buttons — row of 5
        int btnWidth = 44;
        int btnSpacing = 4;
        int totalBtnWidth = PRESET_AMOUNTS.length * btnWidth + (PRESET_AMOUNTS.length - 1) * btnSpacing;
        int startX = panelLeft + (panelWidth - totalBtnWidth) / 2;
        int presetY = panelTop + 48;

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

        // Custom amount EditBox
        int fieldY = presetY + 32;
        int fieldWidth = contentWidth - 80;
        amountField = new EditBox(font, contentLeft, fieldY, fieldWidth, 20, Component.literal(""));
        amountField.setHint(Component.literal("Custom amount...").withStyle(ChatFormatting.DARK_GRAY));
        amountField.setMaxLength(15);
        addWidget(amountField);

        // Confirm button next to EditBox
        addWidget(new NineSliceTexturedButton(
            contentLeft + fieldWidth + 4, fieldY,
            72, 20,
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

        // Back button at bottom
        addWidget(new NineSliceTexturedButton(
            panelLeft + 10,
            panelTop + panelHeight - 30,
            50, 20,
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
        int contentWidth = panelWidth - 20;

        drawCenteredFittedString(graphics, "Withdraw Cash",
                panelLeft + panelWidth / 2, panelTop + 27, contentWidth, 0xFFFFFFFF);

        // "Select amount:" label above preset buttons
        graphics.drawString(font, "Select amount:", panelLeft + 10, panelTop + 38, 0xFF55FFFF);

        // Result message
        if (resultMessage != null) {
            int resultY = panelTop + 130;
            int color = resultSuccess ? 0xFF55FF55 : 0xFFFF5555;
            drawWrappedCentered(graphics, resultMessage, panelLeft + panelWidth / 2, resultY, contentWidth, color, 2);
        }
    }
}
