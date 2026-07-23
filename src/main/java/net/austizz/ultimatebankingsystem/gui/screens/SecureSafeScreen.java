package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.menu.SecureSafeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SecureSafeScreen extends AbstractContainerScreen<SecureSafeMenu> {
    public SecureSafeScreen(SecureSafeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = menu.getInventoryStartY() + 94;
        this.inventoryLabelY = menu.getInventoryStartY() - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 7;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left - 3, top - 3, left + imageWidth + 3, top + imageHeight + 3, 0xFF28486B);
        graphics.fill(left - 1, top - 1, left + imageWidth + 1, top + imageHeight + 1, 0xFF0B1B2B);
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFF10263F);
        graphics.fill(left + 1, top + 1, left + imageWidth - 1, top + 18, 0xFF183B5C);

        if (menu.hasChestUpgrade()) {
            graphics.fill(left + 7, top + 25, left + imageWidth - 7, top + 56, 0xFF172E49);
            drawSlotFrames(graphics, 0, menu.getSafeSlotCount(), 0xFFFFB55F);
        } else {
            graphics.fill(left + 7, top + 25, left + imageWidth - 7, top + 56, 0xFF172E49);
            drawSlotFrames(graphics, 0, menu.getSafeSlotCount(), 0xFF70CBFF);
        }

        drawPlayerSlotFrames(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF4F8FF, false);
        if (menu.hasChestUpgrade()) {
            graphics.drawString(this.font, "Chest compartment", 8, 21, 0xFFFFD58A, false);
        } else {
            graphics.drawString(this.font, "Upgrade slot", 8, 21, 0xFF70CBFF, false);
            graphics.drawString(this.font, "Insert item", 102, 37, 0xFFBBD4ED, false);
        }
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFDCE8F7, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawSlotFrames(GuiGraphics graphics, int from, int to, int color) {
        for (int i = from; i < to && i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, color);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF0F2238);
        }
    }

    private void drawPlayerSlotFrames(GuiGraphics graphics) {
        for (int i = menu.getSafeSlotCount(); i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xFF2D4C74);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF0D2137);
        }
    }
}
