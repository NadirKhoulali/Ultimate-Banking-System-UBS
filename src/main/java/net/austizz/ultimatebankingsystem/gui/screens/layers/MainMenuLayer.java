package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MainMenuLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = new ResourceLocation(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private final List<NineSliceTexturedButton> operationButtons = new ArrayList<>();

    public MainMenuLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        operationButtons.clear();

        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();

        int contentLeft = panelLeft + 14;
        int contentRight = panelLeft + panelWidth - 14;
        int contentWidth = contentRight - contentLeft;
        List<AccountSummary> accounts = ClientATMData.getAccounts();

        if (accounts.size() == 1 && ClientATMData.getSelectedAccount() == null) {
            ClientATMData.setSelectedAccount(accounts.get(0));
        }

        AccountSummary selectedAccount = ClientATMData.getSelectedAccount();
        if (selectedAccount != null && !ClientATMData.isSelectedAccountAuthenticated()) {
            bankScreen.setRootLayer(new PinEntryLayer(minecraft));
            return;
        }

        int selectBtnY = panelTop + 76;
        if (!accounts.isEmpty()) {
            addWidget(new NineSliceTexturedButton(
                    contentLeft, selectBtnY,
                    contentWidth, 22,
                    ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                    4, 4, 4, 4,
                    Component.literal("Select Account").withStyle(ChatFormatting.WHITE),
                    btn -> bankScreen.pushLayer(new AccountSelectionLayer(minecraft))
            ));
        }

        int operationStartY = panelTop + 108;
        boolean hasSelection = ClientATMData.getSelectedAccount() != null;
        int columnGap = 10;
        int buttonWidth = (contentWidth - columnGap) / 2;
        int buttonHeight = 22;
        int leftColX = contentLeft;
        int rightColX = leftColX + buttonWidth + columnGap;
        int rowSpacing = 32;

        addOpButton(leftColX, operationStartY, buttonWidth, buttonHeight, "Withdraw Cash", hasSelection,
                btn -> bankScreen.pushLayer(new WithdrawLayer(minecraft)));
        addOpButton(leftColX, operationStartY + rowSpacing, buttonWidth, buttonHeight, "Transfer Funds", hasSelection,
                btn -> bankScreen.pushLayer(new TransferLayer(minecraft)));
        addOpButton(leftColX, operationStartY + rowSpacing * 2, buttonWidth, buttonHeight, "Transaction History", hasSelection,
                btn -> bankScreen.pushLayer(new TransactionHistoryLayer(minecraft)));

        addOpButton(rightColX, operationStartY, buttonWidth, buttonHeight, "Deposit Cash", hasSelection,
                btn -> bankScreen.pushLayer(new DepositLayer(minecraft)));
        addOpButton(rightColX, operationStartY + rowSpacing, buttonWidth, buttonHeight, "Balance Inquiry", hasSelection,
                btn -> bankScreen.pushLayer(new BalanceInquiryLayer(minecraft)));
        addOpButton(rightColX, operationStartY + rowSpacing * 2, buttonWidth, buttonHeight, "Account Settings", hasSelection,
                btn -> bankScreen.pushLayer(new AccountSettingsLayer(minecraft)));

        int payRequestY = operationStartY + rowSpacing * 3;
        addOpButton(contentLeft, payRequestY, contentWidth, 20, "Pay Requests", hasSelection,
                btn -> bankScreen.pushLayer(new PayRequestsLayer(minecraft)));

        if (accounts.isEmpty()) {
            operationButtons.forEach(btn -> btn.active = false);
        }
    }

    private void addOpButton(int x, int y, int width, int height, String label, boolean active,
                             Consumer<NineSliceTexturedButton> onPress) {
        NineSliceTexturedButton button = new NineSliceTexturedButton(
                x, y, width, height,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal(fitToWidth(label, Math.max(16, width - 12))).withStyle(ChatFormatting.WHITE),
                onPress
        );
        button.active = active;
        addWidget(button);
        operationButtons.add(button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentLeft = panelLeft + 14;
        int contentRight = panelLeft + panelWidth - 14;
        int contentWidth = contentRight - contentLeft;

        int cardTop = panelTop + 36;
        int cardBottom = cardTop + 32;
        drawSectionBox(graphics, contentLeft, cardTop, contentRight, cardBottom);

        graphics.drawString(minecraft.font, "Selected Account", contentLeft + 8, cardTop + 6, COLOR_LABEL);

        AccountSummary selected = ClientATMData.getSelectedAccount();
        String selectedLine = selected == null
                ? "No account selected"
                : selected.accountType() + " @ " + selected.bankName();

        drawCenteredFittedString(
                graphics,
                selectedLine,
                panelLeft + panelWidth / 2,
                cardTop + 18,
                contentWidth - 12,
                selected == null ? COLOR_MUTED : 0xFFFFFFAA
        );

        if (ClientATMData.getAccounts().isEmpty()) {
            drawCenteredFittedString(
                    graphics,
                    "No accounts found. Use /ubs commands to create one.",
                    panelLeft + panelWidth / 2,
                    panelTop + 94,
                    contentWidth,
                    COLOR_MUTED
            );
        }
    }
}
