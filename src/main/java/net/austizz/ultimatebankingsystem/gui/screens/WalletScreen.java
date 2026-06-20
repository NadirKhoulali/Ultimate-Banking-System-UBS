package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.WalletData;
import net.austizz.ultimatebankingsystem.menu.WalletMenu;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;

public class WalletScreen extends AbstractContainerScreen<WalletMenu> {
    private static final int PANEL = 0xFF10283F;
    private static final int PANEL_SOFT = 0xFF14324F;
    private static final int PANEL_DARK = 0xFF0A1B2C;
    private static final int BORDER = 0xFF315B85;
    private static final int BORDER_HOT = 0xFF72C8FF;
    private static final int TEXT = 0xFFEAF6FF;
    private static final int MUTED = 0xFF9EBBD4;
    private static final int GREEN = 0xFF73E89B;
    private static final int GOLD = 0xFFF2C86E;
    private static final int BLUE = 0xFF72C8FF;
    private static final int PURPLE = 0xFFC19AFF;
    private static final ResourceLocation[] CASH_PLACEHOLDERS = {
            placeholderTexture("hundred_dollar_bill"),
            placeholderTexture("fifty_dollar_bill"),
            placeholderTexture("twenty_dollar_bill"),
            placeholderTexture("ten_dollar_bill"),
            placeholderTexture("five_dollar_bill"),
            placeholderTexture("two_dollar_bill"),
            placeholderTexture("one_dollar_bill"),
            placeholderTexture("half_dollar_coin"),
            placeholderTexture("quarter_coin"),
            placeholderTexture("dime_coin"),
            placeholderTexture("nickel_coin"),
            placeholderTexture("penny_coin")
    };
    private static final ResourceLocation CARD_PLACEHOLDER = placeholderTexture("credit_card");

    private WalletButton modeButton;
    private WalletButton fallbackButton;
    private int selectedCashIndex = 0;

    private static ResourceLocation placeholderTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                UltimateBankingSystem.MODID,
                "textures/gui/wallet_placeholders/" + name + ".png"
        );
    }

    public WalletScreen(WalletMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WalletMenu.IMAGE_WIDTH;
        this.imageHeight = WalletMenu.IMAGE_HEIGHT;
        this.inventoryLabelX = WalletMenu.PLAYER_INV_X;
        this.inventoryLabelY = WalletMenu.PLAYER_INV_Y - 11;
        this.titleLabelX = 0;
        this.titleLabelY = 0;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        this.modeButton = addRenderableWidget(new WalletButton(
                this.leftPos + 236,
                this.topPos + 10,
                74,
                18,
                modeLabel(),
                BLUE,
                button -> clickButton(WalletMenu.BUTTON_TOGGLE_MODE)
        ));
        this.fallbackButton = addRenderableWidget(new WalletButton(
                this.leftPos + 316,
                this.topPos + 10,
                76,
                18,
                fallbackLabel(),
                PURPLE,
                button -> clickButton(WalletMenu.BUTTON_TOGGLE_CARD_FALLBACK)
        ));

        addRenderableWidget(new WalletButton(this.leftPos + 208, this.topPos + 152, 20, 16,
                Component.literal("<"), BLUE, button -> selectPreviousCash()));
        addRenderableWidget(new WalletButton(this.leftPos + 232, this.topPos + 152, 20, 16,
                Component.literal(">"), BLUE, button -> selectNextCash()));

        addCashActionButton(0, "Store 1", 208, 175, 56, GREEN);
        addCashActionButton(1, "Store 64", 268, 175, 58, GREEN);
        addCashActionButton(2, "Store All", 330, 175, 58, GREEN);
        addCashActionButton(3, "Take 1", 208, 193, 56, GOLD);
        addCashActionButton(4, "Take 64", 268, 193, 58, GOLD);
        addCashActionButton(5, "Take All", 330, 193, 58, GOLD);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        refreshToggleLabels();
        int left = this.leftPos;
        int top = this.topPos;

        drawFrame(graphics, left, top);
        drawHeader(graphics, left, top);
        drawCashPanel(graphics, left, top);
        drawCardPanel(graphics, left, top);
        drawActionPanel(graphics, left, top);
        drawInventoryBand(graphics, left, top);

        drawSlotBackings(graphics);
        drawWalletSlotHints(graphics);
        drawSelectedCashOutline(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String cash = "$" + MoneyText.abbreviate(BigDecimal.valueOf(this.menu.getTotalCashCents(), 2));
        drawString(graphics, "Wallet", 14, 12, TEXT);
        drawString(graphics, "Owner", 15, 30, MUTED);
        drawString(graphics, fit(this.menu.getOwnerName(), 84), 48, 30, TEXT);
        drawString(graphics, "Cash Total", 118, 10, MUTED);
        drawString(graphics, cash, 118, 22, GREEN);

        drawString(graphics, "Cash Drawer", 18, 54, TEXT);
        drawString(graphics, "Bills", 27, 69, MUTED);
        drawString(graphics, "Coins", 109, 69, MUTED);

        drawString(graphics, "Card Rail", 206, 54, TEXT);
        drawString(graphics, this.menu.getCardCount() + "/" + WalletMenu.CARD_SLOT_COUNT, 352, 54, BLUE);
        drawString(graphics, "Active slot first", 208, 70, MUTED);

        drawString(graphics, "Selected Denomination", 208, 130, TEXT);
        graphics.drawString(this.font, fit(selectedCashLabel(), 118), 264, 156, GOLD, false);
        drawString(graphics, "Inventory", this.inventoryLabelX, this.inventoryLabelY, MUTED);

        drawCashRows(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderWalletTooltip(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < WalletMenu.CASH_SLOT_COUNT; i++) {
            int x = this.leftPos + cashSlotX(i);
            int y = this.topPos + cashSlotY(i);
            if (mouseX >= x - 3 && mouseX < x - 3 + cashRowWidth(i) && mouseY >= y - 2 && mouseY < y + 19) {
                this.selectedCashIndex = i;
                break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Component modeLabel() {
        return Component.literal(this.menu.getMode() == WalletData.PaymentMode.CARD ? "Card mode" : "Cash mode");
    }

    private Component fallbackLabel() {
        return Component.literal(this.menu.isCardFallbackEnabled() ? "Fallback on" : "Fallback off");
    }

    private void refreshToggleLabels() {
        if (this.modeButton != null) {
            this.modeButton.setMessage(modeLabel());
        }
        if (this.fallbackButton != null) {
            this.fallbackButton.setMessage(fallbackLabel());
        }
    }

    private void addCashActionButton(int action, String label, int x, int y, int width, int accentColor) {
        addRenderableWidget(new WalletButton(
                this.leftPos + x,
                this.topPos + y,
                width,
                15,
                Component.literal(label),
                accentColor,
                button -> clickCashAction(action)
        ));
    }

    private void clickCashAction(int action) {
        int id = WalletMenu.BUTTON_CASH_BASE
                + this.selectedCashIndex * WalletMenu.CASH_BUTTONS_PER_DENOM
                + action;
        clickButton(id);
    }

    private void clickButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void selectPreviousCash() {
        this.selectedCashIndex--;
        if (this.selectedCashIndex < 0) {
            this.selectedCashIndex = WalletMenu.CASH_SLOT_COUNT - 1;
        }
    }

    private void selectNextCash() {
        this.selectedCashIndex++;
        if (this.selectedCashIndex >= WalletMenu.CASH_SLOT_COUNT) {
            this.selectedCashIndex = 0;
        }
    }

    private String selectedCashLabel() {
        int cents = DollarBills.cashDenominationCentsForIndex(this.selectedCashIndex);
        String coinHint = this.selectedCashIndex >= 7 ? " (" + cents + "c)" : "";
        return "$" + DollarBills.formatCents(cents) + coinHint + " " + compactCount(this.menu.getCashCount(this.selectedCashIndex));
    }

    private void drawFrame(GuiGraphics graphics, int left, int top) {
        graphics.fill(left - 2, top - 2, left + this.imageWidth + 2, top + this.imageHeight + 2, 0xFF071523);
        graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, 0xFF0B2035);
        graphics.fill(left + 1, top + 1, left + this.imageWidth - 1, top + this.imageHeight - 1, 0xFF0E2740);
        drawBorder(graphics, left, top, this.imageWidth, this.imageHeight, BORDER);
    }

    private void drawHeader(GuiGraphics graphics, int left, int top) {
        graphics.fill(left + 2, top + 2, left + this.imageWidth - 2, top + 42, 0xFF143A5B);
        graphics.fill(left + 2, top + 41, left + this.imageWidth - 2, top + 42, 0xFF6FA6D7);
        drawMiniCard(graphics, left + 112, top + 7, 112, 26, 0xFF0F2B43, GREEN);
    }

    private void drawCashPanel(GuiGraphics graphics, int left, int top) {
        drawPanel(graphics, left + 10, top + 48, 180, 162, "Cash Drawer", GREEN);
        graphics.fill(left + 94, top + 68, left + 95, top + 203, 0xFF284A67);
        for (int i = 0; i < WalletMenu.CASH_SLOT_COUNT; i++) {
            int rowX = left + cashSlotX(i) - 4;
            int rowY = top + cashSlotY(i) - 2;
            int color = i == selectedCashIndex ? 0x4424D879 : 0x22000000;
            graphics.fill(rowX, rowY, rowX + cashRowWidth(i), rowY + 20, color);
        }
    }

    private void drawCardPanel(GuiGraphics graphics, int left, int top) {
        drawPanel(graphics, left + 198, top + 48, 196, 74, "Card Rail", BLUE);
        graphics.fill(left + 208, top + 80, left + 384, top + 112, 0xFF0C2034);
        drawBorder(graphics, left + 208, top + 80, 176, 32, 0xFF284A67);
    }

    private void drawActionPanel(GuiGraphics graphics, int left, int top) {
        drawPanel(graphics, left + 198, top + 126, 196, 84, "Selected Denomination", GOLD);
        graphics.fill(left + 258, top + 151, left + 388, top + 169, 0xFF0B1C2D);
        drawBorder(graphics, left + 258, top + 151, 130, 18, 0xFF385B78);
    }

    private void drawInventoryBand(GuiGraphics graphics, int left, int top) {
        graphics.fill(left + 10, top + 214, left + this.imageWidth - 10, top + this.imageHeight - 10, PANEL_DARK);
        drawBorder(graphics, left + 10, top + 214, this.imageWidth - 20, this.imageHeight - 224, 0xFF254863);
        graphics.fill(left + WalletMenu.PLAYER_INV_X - 10, top + WalletMenu.PLAYER_INV_Y - 16,
                left + WalletMenu.PLAYER_INV_X + 172, top + WalletMenu.PLAYER_INV_Y + 78, 0xFF081827);
        drawBorder(graphics,
                left + WalletMenu.PLAYER_INV_X - 10,
                top + WalletMenu.PLAYER_INV_Y - 16,
                182,
                94,
                0xFF1D425E);
        graphics.fill(left + WalletMenu.PLAYER_INV_X - 8, top + WalletMenu.PLAYER_INV_Y + 54,
                left + WalletMenu.PLAYER_INV_X + 170, top + WalletMenu.PLAYER_INV_Y + 55, 0xFF315B85);
        drawInventorySlotBackings(graphics);
    }

    private void drawCashRows(GuiGraphics graphics) {
        for (int i = 0; i < WalletMenu.CASH_SLOT_COUNT; i++) {
            int row = i < 7 ? i : i - 7;
            int labelX = i < 7 ? 47 : 129;
            int countX = i < 7 ? 90 : 184;
            int y = WalletMenu.CASH_Y + row * 18 + 5;
            int cents = DollarBills.cashDenominationCentsForIndex(i);
            int color = i == selectedCashIndex ? 0xFFFFFFFF : TEXT;
            graphics.drawString(this.font, cashRowDenominationLabel(i, cents), labelX, y, color, false);
            drawRightAlignedRaw(graphics, compactCount(this.menu.getCashCount(i)), countX, y,
                    i == selectedCashIndex ? GOLD : MUTED);
        }
    }

    private void drawSlotBackings(GuiGraphics graphics) {
        for (int i = 0; i < WalletMenu.CASH_SLOT_COUNT; i++) {
            drawSlotBacking(graphics, this.leftPos + cashSlotX(i), this.topPos + cashSlotY(i), i == selectedCashIndex);
        }
        for (int i = 0; i < WalletMenu.CARD_SLOT_COUNT; i++) {
            drawSlotBacking(graphics, this.leftPos + WalletMenu.CARD_X + i * 23, this.topPos + WalletMenu.CARD_Y, false);
        }
    }

    private void drawInventorySlotBackings(GuiGraphics graphics) {
        int left = this.leftPos + WalletMenu.PLAYER_INV_X;
        int top = this.topPos + WalletMenu.PLAYER_INV_Y;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawInventorySlotBacking(graphics, left + col * 18, top + row * 18);
            }
        }
        int hotbarY = top + 58;
        for (int col = 0; col < 9; col++) {
            drawInventorySlotBacking(graphics, left + col * 18, hotbarY);
        }
    }

    private void drawWalletSlotHints(GuiGraphics graphics) {
        for (int i = 0; i < WalletMenu.CASH_SLOT_COUNT; i++) {
            Item item = WalletData.cashItemForIndex(i);
            if (item == null) {
                continue;
            }
            boolean populated = this.menu.getCashCount(i) > 0;
            int x = this.leftPos + cashSlotX(i) + 1;
            int y = this.topPos + cashSlotY(i) + 1;
            if (populated) {
                graphics.renderItem(new ItemStack(item), x, y);
            } else if (i < CASH_PLACEHOLDERS.length) {
                graphics.blit(CASH_PLACEHOLDERS[i], x, y, 0, 0, 16, 16, 16, 16);
            }
        }
        for (int i = 0; i < WalletMenu.CARD_SLOT_COUNT; i++) {
            if (this.menu.hasCard(i)) {
                continue;
            }
            int x = this.leftPos + WalletMenu.CARD_X + i * 23 + 1;
            int y = this.topPos + WalletMenu.CARD_Y + 1;
            graphics.blit(CARD_PLACEHOLDER, x, y, 0, 0, 16, 16, 16, 16);
        }
    }

    private void drawSelectedCashOutline(GuiGraphics graphics) {
        int x = this.leftPos + cashSlotX(this.selectedCashIndex);
        int y = this.topPos + cashSlotY(this.selectedCashIndex);
        drawBorder(graphics, x - 2, y - 2, 21, 21, GOLD);
    }

    private int cashSlotX(int cashIndex) {
        return cashIndex < 7 ? WalletMenu.BILLS_X : WalletMenu.COINS_X;
    }

    private int cashSlotY(int cashIndex) {
        return WalletMenu.CASH_Y + (cashIndex < 7 ? cashIndex : cashIndex - 7) * 18;
    }

    private int cashRowWidth(int cashIndex) {
        return cashIndex < 7 ? 76 : 84;
    }

    private String cashRowDenominationLabel(int cashIndex, int cents) {
        if (cashIndex >= 7) {
            return cents + "c";
        }
        return "$" + DollarBills.formatCents(cents);
    }

    private void drawSlotBacking(GuiGraphics graphics, int x, int y, boolean selected) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, selected ? 0xFFF2C86E : 0xFF050D16);
        graphics.fill(x, y, x + 17, y + 17, selected ? 0xFF25485F : 0xFF213B55);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, 0xFF11243A);
    }

    private void drawInventorySlotBacking(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xFF06101B);
        graphics.fill(x, y, x + 17, y + 17, 0xFF1C354E);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, 0xFF0E2236);
        graphics.fill(x + 1, y + 1, x + 16, y + 2, 0xFF2F5C80);
        graphics.fill(x + 1, y + 15, x + 16, y + 16, 0xFF071421);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, String title, int accent) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_SOFT);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 18, 0xFF173D60);
        graphics.fill(x + 2, y + 2, x + 5, y + height - 2, accent);
        drawBorder(graphics, x, y, width, height, BORDER);
    }

    private void drawMiniCard(GuiGraphics graphics, int x, int y, int width, int height, int fill, int accent) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + 3, y + height, accent);
        drawBorder(graphics, x, y, width, height, 0xFF315B85);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void renderWalletTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < WalletMenu.CASH_SLOT_COUNT; i++) {
            int x = this.leftPos + cashSlotX(i) - 4;
            int y = this.topPos + cashSlotY(i) - 2;
            if (mouseX >= x && mouseX < x + cashRowWidth(i) && mouseY >= y && mouseY < y + 20) {
                int cents = DollarBills.cashDenominationCentsForIndex(i);
                String coinHint = i >= 7 ? " (" + cents + "c)" : "";
                graphics.renderTooltip(this.font, Component.literal(
                        tr("Select") + " $" + DollarBills.formatCents(cents) + coinHint
                                + " | " + tr("Stored") + ": " + this.menu.getCashCount(i)
                ), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, tr(text), x, y, color, false);
    }

    private void drawRightAligned(GuiGraphics graphics, String text, int rightX, int y, int color) {
        String translated = tr(text);
        graphics.drawString(this.font, translated, rightX - this.font.width(translated), y, color, false);
    }

    private void drawRightAlignedRaw(GuiGraphics graphics, String text, int rightX, int y, int color) {
        graphics.drawString(this.font, text, rightX - this.font.width(text), y, color, false);
    }

    private String tr(String text) {
        return UbsClientTranslations.resolve(text);
    }

    private String fit(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && this.font.width(text.substring(0, end)) + this.font.width(ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private String compactCount(int count) {
        long value = Math.max(0, count);
        if (value < 1_000L) {
            return "x" + value;
        }
        if (value < 1_000_000L) {
            return "x" + compactNumber(value, 1_000L, "K");
        }
        if (value < 1_000_000_000L) {
            return "x" + compactNumber(value, 1_000_000L, "M");
        }
        return "x" + compactNumber(value, 1_000_000_000L, "B");
    }

    private String compactNumber(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long tenth = (value % divisor) / Math.max(1L, divisor / 10L);
        if (tenth <= 0) {
            return whole + suffix;
        }
        return whole + "." + tenth + suffix;
    }

    private static final class WalletButton extends AbstractButton {
        private final java.util.function.Consumer<WalletButton> onPress;
        private final int accentColor;

        private WalletButton(int x,
                             int y,
                             int width,
                             int height,
                             Component message,
                             int accentColor,
                             java.util.function.Consumer<WalletButton> onPress) {
            super(x, y, width, height, message);
            this.accentColor = accentColor;
            this.onPress = onPress;
        }

        @Override
        public void onPress() {
            if (onPress != null) {
                onPress.accept(this);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getX();
            int y = getY();
            int border = this.active ? (isHoveredOrFocused() ? BORDER_HOT : BORDER) : 0xFF526075;
            int top = this.active ? (isHoveredOrFocused() ? 0xFF285B86 : 0xFF1F4B73) : 0xFF353D48;
            int bottom = this.active ? 0xFF153553 : 0xFF2B323B;
            graphics.fill(x, y, x + width, y + height, border);
            for (int row = 0; row < Math.max(1, height - 2); row++) {
                float t = height <= 2 ? 0.0F : (float) row / (float) (height - 2);
                graphics.fill(x + 1, y + 1 + row, x + width - 1, y + 2 + row, lerp(top, bottom, t));
            }
            graphics.fill(x + 2, y + 2, x + 4, y + height - 2, this.active ? accentColor : 0xFF7F8B98);

            Font font = Minecraft.getInstance().font;
            String label = fitToWidth(font, UbsClientTranslations.resolve(getMessage().getString()), width - 8);
            int color = this.active ? 0xFFFFFFFF : 0xFF9DAAB8;
            graphics.drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2, color);
        }

        private static String fitToWidth(Font font, String text, int maxWidth) {
            if (text == null || text.isEmpty() || maxWidth <= 0) {
                return "";
            }
            if (font.width(text) <= maxWidth) {
                return text;
            }
            String ellipsis = "...";
            int end = text.length();
            while (end > 0 && font.width(text.substring(0, end)) + font.width(ellipsis) > maxWidth) {
                end--;
            }
            return text.substring(0, Math.max(0, end)) + ellipsis;
        }

        private static int lerp(int from, int to, float t) {
            float clamped = Math.max(0.0F, Math.min(1.0F, t));
            int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * clamped);
            int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * clamped);
            int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
