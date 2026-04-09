package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.TransactionSummary;
import net.austizz.ultimatebankingsystem.network.TxHistoryRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TransactionHistoryLayer extends AbstractScreenLayer {
    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");
    private static final int MAX_ENTRIES = 50;
    private static final int ENTRY_HEIGHT = 32;
    private static final int SCROLL_STEP = 16;

    private final List<TransactionSummary> summaries = new ArrayList<>();
    private boolean loaded;
    private int scrollOffset;

    public TransactionHistoryLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        addWidget(new NineSliceTexturedButton(
            bankScreen.getPanelLeft() + 14,
            bankScreen.getPanelTop() + bankScreen.getPanelHeight() - 36,
            56, 22,
            ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
            4, 4, 4, 4,
            Component.literal("Back").withStyle(ChatFormatting.WHITE),
            btn -> bankScreen.popLayer()
        ));

        loaded = false;
        scrollOffset = 0;
        summaries.clear();

        var selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            PacketDistributor.sendToServer(new TxHistoryRequestPayload(selected.accountId(), MAX_ENTRIES));
        } else {
            loaded = true;
        }
    }

    public void updateEntries(List<TransactionSummary> entries) {
        summaries.clear();
        summaries.addAll(entries);
        loaded = true;
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!loaded || summaries.isEmpty()) {
            return false;
        }

        int listLeft = getListLeft();
        int listTop = getListTop();
        int listRight = getListRight();
        int listBottom = getListBottom();

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }

        int delta = (int) Math.round(scrollY * SCROLL_STEP);
        if (delta == 0) {
            delta = scrollY > 0 ? SCROLL_STEP : -SCROLL_STEP;
        }

        scrollOffset = clamp(scrollOffset - delta, 0, maxScroll);
        return true;
    }

    private int getListLeft() {
        return bankScreen.getPanelLeft() + 14;
    }

    private int getListTop() {
        return bankScreen.getPanelTop() + 58;
    }

    private int getListRight() {
        return bankScreen.getPanelLeft() + bankScreen.getPanelWidth() - 14;
    }

    private int getListBottom() {
        return bankScreen.getPanelTop() + bankScreen.getPanelHeight() - 52;
    }

    private int getListHeight() {
        return getListBottom() - getListTop();
    }

    private int getMaxScroll() {
        return Math.max(0, summaries.size() * ENTRY_HEIGHT - getListHeight());
    }

    private void clampScroll() {
        scrollOffset = clamp(scrollOffset, 0, getMaxScroll());
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentWidth = panelWidth - 28;

        drawCenteredFittedString(graphics, "Transaction History",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);
        drawCenteredFittedString(graphics, "Scroll inside the list",
                panelLeft + panelWidth / 2, panelTop + 44, contentWidth, COLOR_MUTED);

        int listLeft = getListLeft();
        int listTop = getListTop();
        int listRight = getListRight();
        int listBottom = getListBottom();

        drawSectionBox(graphics, listLeft, listTop, listRight, listBottom);

        if (!loaded) {
            drawCenteredFittedString(graphics, "Loading...",
                    panelLeft + panelWidth / 2, listTop + 40, contentWidth, COLOR_MUTED);
            return;
        }

        if (summaries.isEmpty()) {
            drawCenteredFittedString(graphics, "No transactions yet.",
                    panelLeft + panelWidth / 2, listTop + 40, contentWidth, COLOR_MUTED);
            return;
        }

        clampScroll();
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);

        int y = listTop - scrollOffset;
        for (TransactionSummary summary : summaries) {
            int entryTop = y;
            int entryBottom = y + ENTRY_HEIGHT - 2;

            if (entryBottom >= listTop && entryTop <= listBottom) {
                int fillColor = ((y / ENTRY_HEIGHT) & 1) == 0 ? 0x33406992 : 0x33345278;
                graphics.fill(listLeft + 2, entryTop + 1, listRight - 2, entryBottom, fillColor);

                String amountPrefix = summary.isIncoming() ? "+" : "-";
                String amountText = fitToWidth(amountPrefix + "$" + summary.amount(), 72);
                int amountColor = summary.isIncoming() ? 0xFF55FF55 : 0xFFFF5555;
                int amountX = listRight - 6 - font.width(amountText);
                int textLeft = listLeft + 6;
                int textMaxWidth = Math.max(10, amountX - 8 - textLeft);
                graphics.drawString(font, amountText, amountX, entryTop + 3, amountColor);

                drawFittedString(graphics, summary.date(), textLeft, entryTop + 3, textMaxWidth, COLOR_MUTED);
                drawFittedString(graphics, summary.description(), textLeft, entryTop + 13, textMaxWidth, COLOR_VALUE);
                drawFittedString(graphics, "Acct: " + summary.counterpartyId(), textLeft, entryTop + 22, textMaxWidth, 0xFF9AD9FF);
            }

            y += ENTRY_HEIGHT;
        }

        graphics.disableScissor();
    }
}
