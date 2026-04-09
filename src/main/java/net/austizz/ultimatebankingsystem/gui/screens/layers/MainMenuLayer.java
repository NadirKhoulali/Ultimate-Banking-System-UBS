package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MainMenuLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
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

        int contentLeft = panelLeft + 8;
        int contentWidth = panelWidth - 16;
        List<AccountSummary> accounts = ClientATMData.getAccounts();

        if (accounts.size() == 1 && ClientATMData.getSelectedAccount() == null) {
            ClientATMData.setSelectedAccount(accounts.getFirst());
        }

        if (!accounts.isEmpty()) {
            addWidget(new NineSliceTexturedButton(
                    contentLeft, panelTop + 48,
                    contentWidth, 18,
                    ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                    4, 4, 4, 4,
                    Component.literal("Select Account").withStyle(ChatFormatting.WHITE),
                    btn -> bankScreen.pushLayer(new AccountSelectionLayer(minecraft))
            ));
        } else {
            MultiLineTextWidget noAccMsg = new MultiLineTextWidget(
                    contentLeft, panelTop + 50,
                    Component.literal("No accounts found.\nUse /ubs commands to create an account.")
                            .withStyle(ChatFormatting.GRAY),
                    minecraft.font
            );
            noAccMsg.setMaxWidth(contentWidth);
            addWidget(noAccMsg);
        }

        int operationStartY = panelTop + 84;
        boolean hasSelection = ClientATMData.getSelectedAccount() != null;
        int leftColX = panelLeft + 10;
        int rightColX = panelLeft + panelWidth - 120;
        int rowSpacing = 28;

        addOpButton(leftColX, operationStartY, "Withdraw Cash", hasSelection,
                btn -> bankScreen.pushLayer(new WithdrawLayer(minecraft)));
        addOpButton(leftColX, operationStartY + rowSpacing, "Transfer Funds", hasSelection,
                btn -> bankScreen.pushLayer(new TransferLayer(minecraft)));
        addOpButton(leftColX, operationStartY + rowSpacing * 2, "Transaction History", hasSelection,
                btn -> bankScreen.pushLayer(new TransactionHistoryLayer(minecraft)));

        addOpButton(rightColX, operationStartY, "Deposit", hasSelection,
                btn -> bankScreen.pushLayer(new DepositLayer(minecraft)));
        addOpButton(rightColX, operationStartY + rowSpacing, "Balance Inquiry", hasSelection,
                btn -> bankScreen.pushLayer(new BalanceInquiryLayer(minecraft)));
        addOpButton(rightColX, operationStartY + rowSpacing * 2, "Account Settings", hasSelection,
                btn -> bankScreen.pushLayer(new AccountSettingsLayer(minecraft)));
    }

    private void addOpButton(int x, int y, String label, boolean active,
                             Consumer<NineSliceTexturedButton> onPress) {
        NineSliceTexturedButton button = new NineSliceTexturedButton(
                x, y, 110, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal(fitToWidth(label, 98)).withStyle(ChatFormatting.WHITE),
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
        int contentWidth = panelWidth - 16;

        graphics.drawString(minecraft.font, "Selected Account:", panelLeft + 8, panelTop + 30, 0xFF55FFFF);

        AccountSummary selected = ClientATMData.getSelectedAccount();
        String selectedLine = selected == null
                ? "None selected"
                : selected.accountType() + " @ " + selected.bankName();

        drawCenteredFittedString(
                graphics,
                selectedLine,
                panelLeft + panelWidth / 2,
                panelTop + 38,
                contentWidth,
                selected == null ? 0xFF999999 : 0xFFFFFF55
        );
    }
}
