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

    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 27;
    private static final int ROW_SPACING = 2;

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
        int rowY = getListTop() + 2;

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
                panelTop + panelHeight - 36,
                56, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));

        addWidget(new NineSliceTexturedButton(
                panelLeft + panelWidth - 62,
                panelTop + panelHeight - 36,
                54, 22,
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
        for (int i = 0; i < rowButtons.size(); i++) {
            NineSliceTexturedButton button = rowButtons.get(i);
            int index = scrollIndex + i;
            if (index >= 0 && index < accounts.size()) {
                button.setMessage(Component.literal(""));
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
                panelTop + 31,
                0xFFFFFFFF
        );

        drawCenteredFittedString(graphics,
                "Choose an account (mouse wheel to scroll)",
                panelLeft + panelWidth / 2,
                panelTop + 44,
                panelWidth - 20,
                COLOR_MUTED);

        drawSectionBox(graphics, listLeft, listTop, listRight, listBottom);

        if (accounts.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.literal("No accounts available.").withStyle(ChatFormatting.GRAY),
                    panelLeft + panelWidth / 2,
                    listTop + (listBottom - listTop) / 2 - 4,
                    COLOR_MUTED
            );
        } else {
            int from = scrollIndex + 1;
            int to = Math.min(scrollIndex + VISIBLE_ROWS, accounts.size());
            graphics.drawCenteredString(
                    font,
                    Component.literal("Showing " + from + "-" + to + " of " + accounts.size())
                            .withStyle(ChatFormatting.DARK_AQUA),
                    panelLeft + panelWidth / 2,
                    listBottom + 7,
                    0xFF55FFFF
            );
            renderAccountBlocks(graphics);
        }
    }

    private void renderAccountBlocks(GuiGraphics graphics) {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        long now = System.currentTimeMillis();

        for (int i = 0; i < rowButtons.size(); i++) {
            NineSliceTexturedButton button = rowButtons.get(i);
            if (!button.visible) {
                continue;
            }

            int accountIndex = scrollIndex + i;
            if (accountIndex < 0 || accountIndex >= accounts.size()) {
                continue;
            }

            AccountSummary account = accounts.get(accountIndex);
            boolean isSelected = selected != null && selected.accountId().equals(account.accountId());
            boolean isHovered = button.isHoveredOrFocused();

            int x1 = button.getX() + 1;
            int y1 = button.getY() + 1;
            int x2 = button.getX() + button.getWidth() - 1;
            int y2 = button.getY() + button.getHeight() - 1;

            drawAnimatedBlock(graphics, x1, y1, x2, y2, i, now, isSelected);
            int borderColor = isSelected ? 0xFFFFE066 : (isHovered ? 0xFF7BCBFF : 0xFF2A4768);
            graphics.fill(x1, y1, x2, y1 + 1, borderColor);
            graphics.fill(x1, y2 - 1, x2, y2, borderColor);
            graphics.fill(x1, y1, x1 + 1, y2, borderColor);
            graphics.fill(x2 - 1, y1, x2, y2, borderColor);

            int textX = x1 + 6;
            int textWidth = Math.max(20, (x2 - x1) - 12);
            int titleColor = isSelected ? 0xFFFFFF99 : 0xFFFFFFFF;
            int bankColor = isSelected ? 0xFFAAE6FF : 0xFF8AC7E8;
            int textBlockHeight = (font.lineHeight * 2) + 1;
            int blockHeight = Math.max(1, y2 - y1);
            int line1Y = y1 + Math.max(1, (blockHeight - textBlockHeight) / 2);
            int line2Y = line1Y + font.lineHeight + 1;

            String title = fitToWidth(account.accountType(), textWidth);
            String bank = fitToWidth(account.bankName(), textWidth);
            graphics.drawString(font, title, textX, line1Y, titleColor);
            graphics.drawString(font, bank, textX, line2Y, bankColor);
        }
    }

    private void drawAnimatedBlock(GuiGraphics graphics,
                                   int x1, int y1, int x2, int y2,
                                   int rowOffset, long now, boolean selected) {
        int height = Math.max(1, y2 - y1);
        float cycle = ((now + (rowOffset * 420L)) % 3200L) / 3200.0F;
        float pulse = cycle <= 0.5F ? (cycle * 2.0F) : ((1.0F - cycle) * 2.0F);
        int baseDark = selected ? 0xE41A4F86 : 0xCC173F6A;
        int baseLight = selected ? 0xF06FCFFF : 0xE25EB8EE;
        int topColor = lerpColor(baseDark, baseLight, pulse);
        int bottomColor = lerpColor(baseLight, baseDark, pulse);

        for (int y = 0; y < height; y++) {
            float ratio = height <= 1 ? 0.0F : (float) y / (float) (height - 1);
            int lineColor = lerpColor(topColor, bottomColor, ratio);
            graphics.fill(x1, y1 + y, x2, y1 + y + 1, lineColor);
        }
    }

    private int getListLeft() {
        return bankScreen.getPanelLeft() + 8;
    }

    private int getListTop() {
        return bankScreen.getPanelTop() + 56;
    }

    private int getListRight() {
        return bankScreen.getPanelLeft() + bankScreen.getPanelWidth() - 8;
    }

    private int getListBottom() {
        return bankScreen.getPanelTop() + bankScreen.getPanelHeight() - 52;
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
