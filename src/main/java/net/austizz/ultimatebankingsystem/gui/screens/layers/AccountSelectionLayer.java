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

public class AccountSelectionLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_SPACING = 3;

    private final List<AccountSummary> accounts = new ArrayList<>();
    private final List<NineSliceTexturedButton> rowButtons = new ArrayList<>();

    private int scrollIndex;

    public AccountSelectionLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int panelHeight = bankScreen.getPanelHeight();
        int listLeft = getListLeft();
        int listWidth = getListRight() - getListLeft();
        int rowY = getListTop() + 4;

        accounts.clear();
        accounts.addAll(ClientATMData.getAccounts());
        scrollIndex = clamp(scrollIndex, 0, getMaxScrollIndex());
        rowButtons.clear();

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            final int slot = i;
            NineSliceTexturedButton rowButton = addWidget(new NineSliceTexturedButton(
                    listLeft + 4, rowY,
                    listWidth - 8, ROW_HEIGHT,
                    ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                    4, 4, 4, 4,
                    Component.literal(""),
                    btn -> selectAccountAtSlot(slot)
            ));
            rowButtons.add(rowButton);
            rowY += ROW_HEIGHT + ROW_SPACING;
        }

        addWidget(new NineSliceTexturedButton(
                panelLeft + 8,
                panelTop + panelHeight - 24,
                52, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));

        addWidget(new NineSliceTexturedButton(
                panelLeft + panelWidth - 62,
                panelTop + panelHeight - 24,
                54, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Use").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));

        refreshRows();
    }

    private void selectAccountAtSlot(int slot) {
        int index = scrollIndex + slot;
        if (index < 0 || index >= accounts.size()) {
            return;
        }

        ClientATMData.setSelectedAccount(accounts.get(index));
        bankScreen.popLayer();
    }

    private void refreshRows() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        for (int i = 0; i < rowButtons.size(); i++) {
            NineSliceTexturedButton button = rowButtons.get(i);
            int index = scrollIndex + i;
            if (index >= 0 && index < accounts.size()) {
                AccountSummary account = accounts.get(index);
                boolean isSelected = selected != null && selected.accountId().equals(account.accountId());
                String label = (isSelected ? "> " : "  ") + account.accountType() + " @ " + account.bankName()
                        + "  $" + account.balance();
                int labelMaxWidth = Math.max(12, button.getWidth() - 8);
                button.setMessage(Component.literal(fitToWidth(label, labelMaxWidth))
                        .withStyle(isSelected ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
                button.active = true;
                button.visible = true;
            } else {
                button.setMessage(Component.literal(""));
                button.active = false;
                button.visible = false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (accounts.size() <= VISIBLE_ROWS) {
            return false;
        }
        if (!isInsideList(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }

        int delta = scrollY > 0 ? -1 : 1;
        int next = clamp(scrollIndex + delta, 0, getMaxScrollIndex());
        if (next != scrollIndex) {
            scrollIndex = next;
            refreshRows();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int listLeft = getListLeft();
        int listTop = getListTop();
        int listRight = getListRight();
        int listBottom = getListBottom();

        graphics.drawCenteredString(
                font,
                Component.literal("Select Account").withStyle(ChatFormatting.AQUA),
                panelLeft + panelWidth / 2,
                panelTop + 27,
                0xFFFFFFFF
        );

        drawCenteredFittedString(graphics,
                "Choose an account (mouse wheel to scroll)",
                panelLeft + panelWidth / 2,
                panelTop + 40,
                panelWidth - 20,
                0xFFBBBBBB);

        graphics.fill(listLeft - 1, listTop - 1, listRight + 1, listBottom + 1, 0xFF3A3A5E);
        graphics.fill(listLeft, listTop, listRight, listBottom, 0xFF1A1A2E);

        if (accounts.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.literal("No accounts available.").withStyle(ChatFormatting.GRAY),
                    panelLeft + panelWidth / 2,
                    panelTop + 100,
                    0xFF999999
            );
        } else {
            int from = scrollIndex + 1;
            int to = Math.min(scrollIndex + VISIBLE_ROWS, accounts.size());
            graphics.drawCenteredString(
                    font,
                    Component.literal("Showing " + from + "-" + to + " of " + accounts.size())
                            .withStyle(ChatFormatting.DARK_AQUA),
                    panelLeft + panelWidth / 2,
                    panelTop + 186,
                    0xFF55FFFF
            );

            AccountSummary selected = ClientATMData.getSelectedAccount();
            String selectedLabel = selected == null
                    ? "No account selected"
                    : "Current: " + selected.accountType() + " @ " + selected.bankName();
            drawCenteredFittedString(
                    graphics,
                    selectedLabel,
                    panelLeft + panelWidth / 2,
                    panelTop + 198,
                    panelWidth - 20,
                    0xFFFFFFFF
            );
        }
    }

    private int getListLeft() {
        return bankScreen.getPanelLeft() + 8;
    }

    private int getListTop() {
        return bankScreen.getPanelTop() + 50;
    }

    private int getListRight() {
        return bankScreen.getPanelLeft() + bankScreen.getPanelWidth() - 8;
    }

    private int getListBottom() {
        return bankScreen.getPanelTop() + 182;
    }

    private int getMaxScrollIndex() {
        return Math.max(0, accounts.size() - VISIBLE_ROWS);
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= getListLeft() && mouseX <= getListRight()
                && mouseY >= getListTop() && mouseY <= getListBottom();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
