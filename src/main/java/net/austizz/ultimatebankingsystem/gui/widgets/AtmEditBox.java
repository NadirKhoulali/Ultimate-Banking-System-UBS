package net.austizz.ultimatebankingsystem.gui.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class AtmEditBox extends EditBox {
    private static final int TEXT_LEFT_PADDING = 4;

    public AtmEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.setBordered(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        boolean editable = this.active;
        boolean focused = this.isFocused();
        boolean hovered = this.isHoveredOrFocused();

        int phaseOffset = Math.floorMod((x1 * 19) + (y1 * 13), 3000);
        float cycle = ((System.currentTimeMillis() + phaseOffset) % 3000L) / 3000.0F;
        float pulse = cycle <= 0.5F ? (cycle * 2.0F) : ((1.0F - cycle) * 2.0F);

        int darkColor = editable ? 0xE0123556 : 0xCC2A3645;
        int lightColor = editable ? 0xE03C80B5 : 0xCC5A6B7F;
        int topColor = lerpColor(darkColor, lightColor, pulse * 0.75F);
        int bottomColor = lerpColor(lightColor, darkColor, pulse * 0.75F);

        int fillX1 = x1 + 1;
        int fillY1 = y1 + 1;
        int fillX2 = x2 - 1;
        int fillY2 = y2 - 1;
        int height = Math.max(1, fillY2 - fillY1);
        for (int y = 0; y < height; y++) {
            float ratio = height <= 1 ? 0.0F : (float) y / (float) (height - 1);
            int rowColor = lerpColor(topColor, bottomColor, ratio);
            graphics.fill(fillX1, fillY1 + y, fillX2, fillY1 + y + 1, rowColor);
        }

        int borderColor;
        if (!editable) {
            borderColor = 0xFF4F5F72;
        } else if (focused) {
            borderColor = 0xFFD9F4FF;
        } else if (hovered) {
            borderColor = 0xFF8CCEEE;
        } else {
            borderColor = 0xFF2A4768;
        }
        graphics.fill(x1, y1, x2, y1 + 1, borderColor);
        graphics.fill(x1, y2 - 1, x2, y2, borderColor);
        graphics.fill(x1, y1, x1 + 1, y2, borderColor);
        graphics.fill(x2 - 1, y1, x2, y2, borderColor);

        int shineColor = focused ? 0x88E4F7FF : 0x5579A9D2;
        graphics.fill(fillX1, fillY1, fillX2, fillY1 + 1, shineColor);

        // EditBox with bordered=false renders text from widget Y, so offset it to the visual midline.
        int originalX = this.getX();
        int originalY = this.getY();
        int originalWidth = this.width;
        int textYOffset = Math.max(0, (this.height - 8) / 2);
        int paddedWidth = Math.max(1, originalWidth - TEXT_LEFT_PADDING);

        this.setX(originalX + TEXT_LEFT_PADDING);
        this.width = paddedWidth;
        this.setY(originalY + textYOffset);
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);
        this.setX(originalX);
        this.width = originalWidth;
        this.setY(originalY);
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
