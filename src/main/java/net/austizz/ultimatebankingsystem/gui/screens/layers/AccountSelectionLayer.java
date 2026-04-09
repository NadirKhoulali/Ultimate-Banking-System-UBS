package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class AccountSelectionLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 22;
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

            String title = fitToWidth(account.accountType(), textWidth);
            String bank = fitToWidth(account.bankName(), textWidth);
            graphics.drawString(font, title, textX, y1 + 3, titleColor);
            graphics.drawString(font, bank, textX, y1 + 12, bankColor);
        }
    }

    private void drawAnimatedBlock(GuiGraphics graphics,
                                   int x1, int y1, int x2, int y2,
                                   int rowOffset, long now, boolean selected) {
        int height = Math.max(1, y2 - y1);
        float timePhase = (now % 6000L) / 6000.0F;
        float hueBase = wrapHue(0.56F + (rowOffset * 0.035F) + (timePhase * 0.22F));
        float lightHue = wrapHue(hueBase - 0.02F);
        float darkHue = wrapHue(hueBase + 0.03F);
        float lightBrightness = selected ? 0.92F : 0.78F;
        float darkBrightness = selected ? 0.56F : 0.40F;

        int topColor = withAlpha(0xD8, Color.HSBtoRGB(lightHue, 0.52F, lightBrightness));
        int bottomColor = withAlpha(0xD8, Color.HSBtoRGB(darkHue, 0.66F, darkBrightness));

        for (int y = 0; y < height; y++) {
            float ratio = height <= 1 ? 0.0F : (float) y / (float) (height - 1);
            int lineColor = lerpColor(topColor, bottomColor, ratio);
            graphics.fill(x1, y1 + y, x2, y1 + y + 1, lineColor);
        }
    }

    private static float wrapHue(float hue) {
        float wrapped = hue % 1.0F;
        return wrapped < 0.0F ? wrapped + 1.0F : wrapped;
    }

    private static int withAlpha(int alpha, int rgbColor) {
        return ((alpha & 0xFF) << 24) | (rgbColor & 0x00FFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * clamped);
        int r = (int) (r1 + (r2 - r1) * clamped);
        int g = (int) (g1 + (g2 - g1) * clamped);
        int b = (int) (b1 + (b2 - b1) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
