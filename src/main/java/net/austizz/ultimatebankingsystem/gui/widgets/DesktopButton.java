package net.austizz.ultimatebankingsystem.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.function.Consumer;

public class DesktopButton extends AbstractButton {

    private final Consumer<DesktopButton> onPress;
    private final int accentColor;
    private int labelOffsetX;
    private int labelOffsetY;
    private int iconOffsetX;
    private int iconOffsetY;

    public DesktopButton(int x,
                         int y,
                         int width,
                         int height,
                         Component message,
                         int accentColor,
                         Consumer<DesktopButton> onPress) {
        super(x, y, width, height, message);
        this.accentColor = accentColor;
        this.onPress = onPress;
    }

    public DesktopButton(int x,
                         int y,
                         int width,
                         int height,
                         Component message,
                         Consumer<DesktopButton> onPress) {
        this(x, y, width, height, message, 0xFF69B8FF, onPress);
    }

    public DesktopButton setLabelOffset(int x, int y) {
        this.labelOffsetX = x;
        this.labelOffsetY = y;
        return this;
    }

    public DesktopButton setIconOffset(int x, int y) {
        this.iconOffsetX = x;
        this.iconOffsetY = y;
        return this;
    }

    @Override
    public void onPress() {
        if (this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        boolean hovered = this.isHoveredOrFocused();
        int border = this.active
                ? (hovered ? 0xFFCDE9FF : 0xFF355A83)
                : 0xFF526075;

        int top = this.active
                ? (hovered ? 0xEE2B5A8D : 0xE6264E7A)
                : 0xCC37404D;
        int bottom = this.active
                ? (hovered ? 0xEE1D4062 : 0xE61A3856)
                : 0xCC2D3540;

        graphics.fill(x1, y1, x2, y2, border);

        int innerX1 = x1 + 1;
        int innerY1 = y1 + 1;
        int innerX2 = x2 - 1;
        int innerY2 = y2 - 1;
        int innerH = Math.max(1, innerY2 - innerY1);
        for (int y = 0; y < innerH; y++) {
            float t = innerH <= 1 ? 0.0F : (float) y / (float) (innerH - 1);
            graphics.fill(innerX1, innerY1 + y, innerX2, innerY1 + y + 1, lerpColor(top, bottom, t));
        }

        int accent = this.active ? accentColor : 0xFF8798AA;
        graphics.fill(innerX1 + 1, innerY1 + 1, innerX1 + 4, innerY2 - 1, accent);

        int iconSeed = Math.abs(this.getMessage().getString().hashCode());
        int iconX = innerX1 + 8 + iconOffsetX;
        int iconY = innerY1 + Math.max(1, (innerY2 - innerY1 - 8) / 2) + iconOffsetY;
        int iconColor = this.active ? 0xFFEAF5FF : 0xFFB4C0CD;
        if ((iconSeed & 1) == 0) {
            graphics.fill(iconX, iconY, iconX + 8, iconY + 2, iconColor);
            graphics.fill(iconX, iconY + 3, iconX + 6, iconY + 5, iconColor);
            graphics.fill(iconX, iconY + 6, iconX + 4, iconY + 8, iconColor);
        } else {
            graphics.fill(iconX, iconY, iconX + 2, iconY + 8, iconColor);
            graphics.fill(iconX + 3, iconY + 2, iconX + 5, iconY + 8, iconColor);
            graphics.fill(iconX + 6, iconY + 4, iconX + 8, iconY + 8, iconColor);
        }

        Font font = Minecraft.getInstance().font;
        int labelStart = innerX1 + 19 + labelOffsetX;
        String label = fitToWidth(font, this.getMessage().getString(), Math.max(0, x2 - labelStart - 4));
        int textColor = resolveTextColor(this.getMessage().getStyle().getColor(), this.active);
        graphics.drawString(
                font,
                label,
                labelStart,
                y1 + Math.max(1, (this.height - 8) / 2) + labelOffsetY,
                textColor,
                false
        );

        if (hovered && this.active) {
            graphics.fill(innerX1 + 1, innerY1 + 1, innerX2 - 1, innerY1 + 2, 0x66FFFFFF);
        }
    }

    private static String fitToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static int resolveTextColor(TextColor styledColor, boolean active) {
        if (!active) {
            return 0xFF98A8BA;
        }
        if (styledColor != null) {
            return 0xFF000000 | styledColor.getValue();
        }
        return 0xFFFFFFFF;
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

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
