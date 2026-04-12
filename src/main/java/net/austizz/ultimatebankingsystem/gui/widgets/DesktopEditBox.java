package net.austizz.ultimatebankingsystem.gui.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class DesktopEditBox extends EditBox {

    private static final int TEXT_LEFT_PADDING = 5;

    public DesktopEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.setBordered(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        int border;
        if (!this.active) {
            border = 0xFF55647A;
        } else if (this.isFocused()) {
            border = 0xFFD8EEFF;
        } else if (this.isHoveredOrFocused()) {
            border = 0xFF95C4E8;
        } else {
            border = 0xFF3A5B80;
        }

        graphics.fill(x1, y1, x2, y2, border);

        int fillTop = this.active ? 0xF0182F47 : 0xCC2B3745;
        int fillBottom = this.active ? 0xF0112238 : 0xCC212B36;
        int innerX1 = x1 + 1;
        int innerY1 = y1 + 1;
        int innerX2 = x2 - 1;
        int innerY2 = y2 - 1;
        int h = Math.max(1, innerY2 - innerY1);
        for (int y = 0; y < h; y++) {
            float t = h <= 1 ? 0.0F : (float) y / (float) (h - 1);
            graphics.fill(innerX1, innerY1 + y, innerX2, innerY1 + y + 1, lerpColor(fillTop, fillBottom, t));
        }
        graphics.fill(innerX1 + 1, innerY1 + 1, innerX2 - 1, innerY1 + 2, 0x55C7E2FF);

        int originalX = this.getX();
        int originalY = this.getY();
        int originalW = this.width;

        int textYOffset = Math.max(0, (this.height - 8) / 2);
        this.setX(originalX + TEXT_LEFT_PADDING);
        this.setY(originalY + textYOffset);
        this.width = Math.max(1, originalW - TEXT_LEFT_PADDING - 1);

        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        this.setX(originalX);
        this.setY(originalY);
        this.width = originalW;
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
}
