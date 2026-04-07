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

    private final List<NineSliceTexturedButton> accountButtons = new ArrayList<>();
    private final List<NineSliceTexturedButton> operationButtons = new ArrayList<>();

    public MainMenuLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        accountButtons.clear();
        operationButtons.clear();

        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();

        // Content area: below header (20px) + border (1px) + padding (4px)
        int contentLeft = panelLeft + 8;
        int contentWidth = panelWidth - 16;

        List<AccountSummary> accounts = ClientATMData.getAccounts();
        int operationStartY;

        if (accounts.isEmpty()) {
            // No accounts message
            MultiLineTextWidget noAccMsg = new MultiLineTextWidget(
                    contentLeft, panelTop + 30,
                    Component.literal("No accounts found.\nUse /ubs commands to create an account.")
                            .withStyle(ChatFormatting.GRAY),
                    minecraft.font
            );
            noAccMsg.setMaxWidth(contentWidth);
            addWidget(noAccMsg);
            operationStartY = panelTop + 80;
        } else {
            // Auto-select if only one account
            if (accounts.size() == 1 && ClientATMData.getSelectedAccount() == null) {
                ClientATMData.setSelectedAccount(accounts.getFirst());
            }

            // Account selector buttons
            int accountY = panelTop + 38;
            for (AccountSummary account : accounts) {
                boolean selected = account.equals(ClientATMData.getSelectedAccount());
                String label = (selected ? "> " : "  ") + account.accountType() + " @ " + account.bankName();

                NineSliceTexturedButton accountBtn = new NineSliceTexturedButton(
                        contentLeft, accountY,
                        contentWidth, 16,
                        ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                        4, 4, 4, 4,
                        Component.literal(label).withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE),
                        btn -> selectAccount(account)
                );
                addWidget(accountBtn);
                accountButtons.add(accountBtn);
                accountY += 18;
            }
            operationStartY = Math.max(panelTop + 80, accountY + 6);
        }

        // 6 operation buttons — 2-column, 3-row grid
        boolean hasSelection = ClientATMData.getSelectedAccount() != null;
        int leftColX = panelLeft + 10;
        int rightColX = panelLeft + panelWidth - 120;
        int rowSpacing = 28;

        // Left column: Withdraw Cash, Transfer Funds, Transaction History
        addOpButton(leftColX, operationStartY, "Withdraw Cash", hasSelection,
                btn -> bankScreen.pushLayer(new WithdrawLayer(minecraft)));
        addOpButton(leftColX, operationStartY + rowSpacing, "Transfer Funds", hasSelection,
                btn -> bankScreen.pushLayer(new TransferLayer(minecraft)));
        addOpButton(leftColX, operationStartY + rowSpacing * 2, "Transaction History", hasSelection,
                btn -> bankScreen.pushLayer(new TransactionHistoryLayer(minecraft)));

        // Right column: Deposit, Balance Inquiry, Account Settings
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
                Component.literal(label).withStyle(ChatFormatting.WHITE),
                onPress
        );
        button.active = active;
        addWidget(button);
        operationButtons.add(button);
    }

    private void selectAccount(AccountSummary account) {
        ClientATMData.setSelectedAccount(account);

        // Update account button labels to reflect selection
        List<AccountSummary> accounts = ClientATMData.getAccounts();
        for (int i = 0; i < accountButtons.size() && i < accounts.size(); i++) {
            AccountSummary acc = accounts.get(i);
            boolean selected = acc.equals(account);
            String label = (selected ? "> " : "  ") + acc.accountType() + " @ " + acc.bankName();
            accountButtons.get(i).setMessage(
                    Component.literal(label).withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE)
            );
        }

        // Enable operation buttons
        for (NineSliceTexturedButton btn : operationButtons) {
            btn.active = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Draw "Select Account:" label above account selector buttons
        if (!ClientATMData.getAccounts().isEmpty()) {
            graphics.drawString(
                    minecraft.font,
                    "Select Account:",
                    bankScreen.getPanelLeft() + 8,
                    bankScreen.getPanelTop() + 27,
                    0xFF55FFFF
            );
        }
    }
}
