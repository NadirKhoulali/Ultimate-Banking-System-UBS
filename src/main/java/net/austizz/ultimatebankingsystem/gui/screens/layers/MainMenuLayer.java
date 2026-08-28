package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
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
import java.math.BigDecimal;

public class MainMenuLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "textures/gui/atm_buttons.png");

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
        int contentWidth = panelWidth - 28;
        List<AccountSummary> accounts = ClientATMData.getAccounts();

        if (accounts.size() == 1 && ClientATMData.getSelectedAccount() == null) {
            ClientATMData.setSelectedAccount(accounts.get(0));
        }

        AccountSummary selectedAccount = ClientATMData.getSelectedAccount();
        if (selectedAccount != null && !ClientATMData.isSelectedAccountAuthenticated()) {
            bankScreen.setRootLayer(new PinEntryLayer(minecraft));
            return;
        }

        int operationStartY = panelTop + (bankScreen.isCompactLayout() ? 94 : 104);
        boolean hasSelection = ClientATMData.getSelectedAccount() != null;
        int columns = 3;
        int columnGap = 7;
        int buttonWidth = (contentWidth - (columnGap * (columns - 1))) / columns;
        int buttonHeight = 22;
        int rowSpacing = bankScreen.isCompactLayout() ? 27 : 31;

        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 0,
                "Withdraw", hasSelection, btn -> bankScreen.pushLayer(new WithdrawLayer(minecraft)));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 1,
                "Deposit", hasSelection, btn -> bankScreen.pushLayer(new DepositLayer(minecraft)));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 2,
                "Fast Cash", hasSelection, btn -> bankScreen.pushLayer(new WithdrawLayer(minecraft, "100")));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 3,
                "Transfer", hasSelection, btn -> bankScreen.pushLayer(new TransferLayer(minecraft)));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 4,
                "Balance", hasSelection, btn -> bankScreen.pushLayer(new BalanceInquiryLayer(minecraft)));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 5,
                "Mini Statement", hasSelection, btn -> bankScreen.pushLayer(new TransactionHistoryLayer(minecraft)));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 6,
                "Pay Requests", hasSelection, btn -> bankScreen.pushLayer(new PayRequestsLayer(minecraft)));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 7,
                "Settings", hasSelection, btn -> bankScreen.pushLayer(new AccountSettingsLayer(minecraft)));
        addGridButton(contentLeft, operationStartY, buttonWidth, columnGap, rowSpacing, 8,
                "Switch Account", !accounts.isEmpty(), btn -> {
                    ClientATMData.setAuthenticatedAccountId(null);
                    bankScreen.pushLayer(new AccountSelectionLayer(minecraft));
                });

        if (accounts.isEmpty()) {
            operationButtons.forEach(btn -> btn.active = false);
        }
    }

    private void addGridButton(int startX,
                               int startY,
                               int width,
                               int columnGap,
                               int rowSpacing,
                               int index,
                               String label,
                               boolean active,
                               Consumer<NineSliceTexturedButton> onPress) {
        int column = index % 3;
        int row = index / 3;
        addOpButton(startX + column * (width + columnGap), startY + row * rowSpacing,
                width, 22, label, active, onPress);
    }

    private void addOpButton(int x, int y, int width, int height, String label, boolean active,
                             Consumer<NineSliceTexturedButton> onPress) {
        NineSliceTexturedButton button = new NineSliceTexturedButton(
                x, y, width, height,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                UbsTranslations.literal(label).withStyle(ChatFormatting.WHITE),
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

        int cardTop = panelTop + 35;
        int cardBottom = cardTop + (bankScreen.isCompactLayout() ? 48 : 56);
        drawSectionBox(graphics, contentLeft, cardTop, contentRight, cardBottom);

        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            drawCenteredFittedString(graphics, "No account selected",
                    panelLeft + panelWidth / 2, cardTop + 18, contentWidth - 12, COLOR_MUTED);
        } else {
            int innerLeft = contentLeft + 8;
            int innerRight = contentRight - 8;
            String bankLine = selected.bankName() + (selected.isPrimary() ? "  |  PRIMARY" : "");
            drawFittedString(graphics, bankLine, innerLeft, cardTop + 6, contentWidth - 16,
                    selected.isPrimary() ? COLOR_SUCCESS : COLOR_LABEL);
            String shortId = selected.accountId().toString().substring(0, 8).toUpperCase();
            drawFittedString(graphics, selected.accountType() + "  |  •••• " + shortId,
                    innerLeft, cardTop + 18, contentWidth - 16, COLOR_MUTED);

            BigDecimal balance = parseMoney(selected.balance());
            BigDecimal available = balance.min(parseMoney(selected.effectiveWithdrawalLimit()))
                    .min(parseMoney(selected.dailyWithdrawalRemaining())).max(BigDecimal.ZERO);
            graphics.drawString(minecraft.font, UbsClientTranslations.resolve("Balance"),
                    innerLeft, cardTop + 32, COLOR_LABEL);
            drawFittedString(graphics, net.austizz.ultimatebankingsystem.util.MoneyText.abbreviateWithDollar(balance),
                    innerLeft + 45, cardTop + 32, Math.max(60, contentWidth / 2 - 45), COLOR_VALUE);
            drawRightAlignedFittedString(graphics,
                    "ATM available " + net.austizz.ultimatebankingsystem.util.MoneyText.abbreviateWithDollar(available),
                    innerRight, cardTop + 32, Math.max(95, contentWidth / 2), COLOR_SUCCESS);
        }

        int serviceLabelY = panelTop + (bankScreen.isCompactLayout() ? 84 : 94);
        graphics.drawString(minecraft.font, UbsClientTranslations.resolve("ATM SERVICES"),
                contentLeft + 2, serviceLabelY, COLOR_LABEL);

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

    private static BigDecimal parseMoney(String raw) {
        try {
            return raw == null || raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }
}
