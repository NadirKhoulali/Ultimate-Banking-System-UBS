package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.PayRequestActionPayload;
import net.austizz.ultimatebankingsystem.network.PayRequestActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.PayRequestEntry;
import net.austizz.ultimatebankingsystem.network.PayRequestInboxRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class PayRequestsLayer extends AbstractScreenLayer {
    private static final ResourceLocation ATM_BUTTONS = new ResourceLocation(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");
    private static final int ENTRY_HEIGHT = 30;
    private static final int SCROLL_STEP = 18;

    private final List<PayRequestEntry> entries = new ArrayList<>();
    private boolean loaded;
    private int selectedIndex = -1;
    private int scrollOffset;
    private String statusMessage = "";
    private boolean statusSuccess;
    private String primaryAccountLabel = "None";

    private NineSliceTexturedButton acceptButton;
    private NineSliceTexturedButton declineButton;
    private NineSliceTexturedButton chooseButton;
    private NineSliceTexturedButton createRequestButton;

    public PayRequestsLayer(Minecraft minecraft) {
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

        int actionsTop = getActionsTop();
        int createButtonY = getCreateButtonY();
        int gap = 6;
        int actionWidth = (contentWidth - (gap * 2)) / 3;

        int createWidth = Math.min(176, contentWidth);
        int createX = panelLeft + (panelWidth - createWidth) / 2;
        createRequestButton = addWidget(new NineSliceTexturedButton(
                createX,
                createButtonY,
                createWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Create Pay Request").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.pushLayer(new CreatePayRequestLayer(minecraft))
        ));

        acceptButton = addWidget(new NineSliceTexturedButton(
                contentLeft,
                actionsTop,
                actionWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Accept").withStyle(ChatFormatting.GREEN),
                btn -> acceptWithPrimary()
        ));

        declineButton = addWidget(new NineSliceTexturedButton(
                contentLeft + actionWidth + gap,
                actionsTop,
                actionWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Decline").withStyle(ChatFormatting.RED),
                btn -> declineSelected()
        ));

        chooseButton = addWidget(new NineSliceTexturedButton(
                contentLeft + (actionWidth + gap) * 2,
                actionsTop,
                actionWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Choose Account").withStyle(ChatFormatting.AQUA),
                btn -> chooseAccountForSelected()
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

        addWidget(new NineSliceTexturedButton(
                panelLeft + panelWidth - 78,
                panelTop + panelHeight - 36,
                64, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Refresh").withStyle(ChatFormatting.WHITE),
                btn -> requestInbox()
        ));

        loaded = false;
        selectedIndex = -1;
        scrollOffset = 0;
        statusMessage = "";
        statusSuccess = false;
        primaryAccountLabel = "None";
        entries.clear();
        updateButtons();
        requestInbox();
    }

    private void requestInbox() {
        var selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            loaded = true;
            statusMessage = "No account selected.";
            statusSuccess = false;
            updateButtons();
            return;
        }
        PacketDistributor.sendToServer(new PayRequestInboxRequestPayload(selected.accountId()));
    }

    public void updateInbox(List<PayRequestEntry> requests, String primaryLabel) {
        entries.clear();
        if (requests != null) {
            entries.addAll(requests);
        }
        loaded = true;
        primaryAccountLabel = (primaryLabel == null || primaryLabel.isBlank()) ? "None" : primaryLabel;
        if (entries.isEmpty()) {
            selectedIndex = -1;
            scrollOffset = 0;
        } else if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            selectedIndex = 0;
        }
        clampScroll();
        updateButtons();
    }

    public void updateActionResult(PayRequestActionResponsePayload payload) {
        statusMessage = MoneyText.abbreviateCurrencyTokens(payload.message());
        statusSuccess = payload.success();
        requestInbox();
    }

    private void acceptWithPrimary() {
        PayRequestEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        sendAction("accept_primary", "");
    }

    private void declineSelected() {
        PayRequestEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        sendAction("decline", "");
    }

    private void chooseAccountForSelected() {
        PayRequestEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        bankScreen.pushLayer(new AccountSelectionLayer(minecraft, selectedAccount -> {
            if (selectedAccount == null) {
                return;
            }
            sendAction("accept_account", selectedAccount.accountId().toString());
        }, false));
    }

    private void sendAction(String action, String senderAccountId) {
        PayRequestEntry entry = getSelectedEntry();
        var selected = ClientATMData.getSelectedAccount();
        if (entry == null || selected == null) {
            statusMessage = "No request selected.";
            statusSuccess = false;
            return;
        }
        PacketDistributor.sendToServer(new PayRequestActionPayload(
                selected.accountId(),
                entry.requestId(),
                action,
                senderAccountId == null ? "" : senderAccountId
        ));
    }

    private PayRequestEntry getSelectedEntry() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return null;
        }
        return entries.get(selectedIndex);
    }

    private void updateButtons() {
        boolean active = loaded && selectedIndex >= 0 && selectedIndex < entries.size();
        if (acceptButton != null) {
            acceptButton.active = active;
        }
        if (declineButton != null) {
            declineButton.active = active;
        }
        if (chooseButton != null) {
            chooseButton.active = active;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (!loaded || entries.isEmpty()) {
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

        int delta = (int) Math.round(scrollDelta * SCROLL_STEP);
        if (delta == 0) {
            delta = scrollDelta > 0 ? SCROLL_STEP : -SCROLL_STEP;
        }
        scrollOffset = clamp(scrollOffset - delta, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || !loaded || entries.isEmpty()) {
            return false;
        }

        int listLeft = getListLeft();
        int listTop = getListTop();
        int listRight = getListRight();
        int listBottom = getListBottom();
        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int localY = (int) mouseY - listTop + scrollOffset;
        int idx = localY / ENTRY_HEIGHT;
        if (idx >= 0 && idx < entries.size()) {
            selectedIndex = idx;
            updateButtons();
            return true;
        }
        return false;
    }

    private int getListLeft() {
        return bankScreen.getPanelLeft() + 14;
    }

    private int getListTop() {
        return bankScreen.getPanelTop() + 74;
    }

    private int getListRight() {
        return bankScreen.getPanelLeft() + bankScreen.getPanelWidth() - 14;
    }

    private int getListBottom() {
        return getCreateButtonY() - 8;
    }

    private int getActionsTop() {
        return bankScreen.getPanelTop() + bankScreen.getPanelHeight() - 62;
    }

    private int getCreateButtonY() {
        return getActionsTop() - 26;
    }

    private int getListHeight() {
        return getListBottom() - getListTop();
    }

    private int getMaxScroll() {
        return Math.max(0, entries.size() * ENTRY_HEIGHT - getListHeight());
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
        int contentLeft = panelLeft + 14;

        drawCenteredFittedString(graphics, "Pay Requests",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);
        drawFittedString(graphics, "Current Primary: " + primaryAccountLabel,
                contentLeft + 2, panelTop + 46, contentWidth - 4, COLOR_LABEL);

        if (!statusMessage.isEmpty()) {
            int statusColor = statusSuccess ? COLOR_SUCCESS : COLOR_ERROR;
            drawCenteredFittedString(graphics, statusMessage,
                    panelLeft + panelWidth / 2, panelTop + 58, contentWidth, statusColor);
        }

        int listLeft = getListLeft();
        int listTop = getListTop();
        int listRight = getListRight();
        int listBottom = getListBottom();

        drawSectionBox(graphics, listLeft, listTop, listRight, listBottom);

        if (!loaded) {
            drawCenteredFittedString(graphics, "Loading pay requests...",
                    panelLeft + panelWidth / 2, listTop + 32, contentWidth, COLOR_MUTED);
            return;
        }

        if (entries.isEmpty()) {
            drawCenteredFittedString(graphics, "No pending pay requests.",
                    panelLeft + panelWidth / 2, listTop + 32, contentWidth, COLOR_MUTED);
            return;
        }

        clampScroll();
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);

        int y = listTop - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            PayRequestEntry entry = entries.get(i);
            int entryTop = y;
            int entryBottom = y + ENTRY_HEIGHT - 2;

            if (entryBottom >= listTop && entryTop <= listBottom) {
                boolean isSelected = i == selectedIndex;
                int fillColor = isSelected ? 0x664B7DB6 : ((((y / ENTRY_HEIGHT) & 1) == 0) ? 0x33406992 : 0x33345278);
                graphics.fill(listLeft + 2, entryTop + 1, listRight - 2, entryBottom, fillColor);
                if (isSelected) {
                    graphics.fill(listLeft + 2, entryTop + 1, listRight - 2, entryTop + 2, 0xFF9BD0FF);
                }

                String amountText = MoneyText.abbreviateWithDollar(entry.amount());
                int amountX = listRight - 12 - font.width(amountText);
                graphics.drawString(font, amountText, amountX, entryTop + 4, 0xFFFFE27A);

                int textWidth = Math.max(12, amountX - (listLeft + 8) - 8);
                drawFittedString(graphics, "From: " + entry.requesterName(), listLeft + 8, entryTop + 4, textWidth, COLOR_VALUE);
                drawFittedString(graphics, "Requested: " + entry.createdAt(), listLeft + 8, entryTop + 16, textWidth, COLOR_MUTED);
            }

            y += ENTRY_HEIGHT;
        }

        graphics.disableScissor();
    }
}
