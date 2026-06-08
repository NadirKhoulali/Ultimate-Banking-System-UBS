package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.austizz.ultimatebankingsystem.menu.CardboardBoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CardboardBoxScreen extends AbstractContainerScreen<CardboardBoxMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final int ROWS = CardboardBoxMenu.BOX_ROWS;

    public CardboardBoxScreen(CardboardBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int left = this.leftPos;
        int top = this.topPos;
        int containerHeight = ROWS * 18 + 17;
        graphics.blit(CONTAINER_TEXTURE, left, top, 0, 0, this.imageWidth, containerHeight);
        graphics.blit(CONTAINER_TEXTURE, left, top + containerHeight, 0, 126, this.imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
