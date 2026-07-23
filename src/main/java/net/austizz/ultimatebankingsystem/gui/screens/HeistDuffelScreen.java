package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.austizz.ultimatebankingsystem.menu.HeistDuffelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class HeistDuffelScreen extends AbstractContainerScreen<HeistDuffelMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    public HeistDuffelScreen(HeistDuffelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 168;
        inventoryLabelY = 74;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int containerHeight = 71;
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, containerHeight);
        graphics.blit(TEXTURE, leftPos, topPos + containerHeight, 0, 126, imageWidth, 96);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
