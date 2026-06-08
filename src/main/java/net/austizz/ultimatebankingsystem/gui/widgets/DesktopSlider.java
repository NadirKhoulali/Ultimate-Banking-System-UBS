package net.austizz.ultimatebankingsystem.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Small themed integer slider used by the PC apps.
 * It keeps the same visual language as DesktopButton/DesktopEditBox while allowing drag/click selection.
 */
public class DesktopSlider extends AbstractWidget {

    private final int minValue;
    private final int maxValue;
    private final Consumer<Integer> onValueChanged;
    private int value;
    private int accentColor = 0xFF69B8FF;
    private String label = "";

    public DesktopSlider(int x,
                         int y,
                         int width,
                         int height,
                         int minValue,
                         int maxValue,
                         int initialValue,
                         Consumer<Integer> onValueChanged) {
        super(x, y, width, height, Component.empty());
        this.minValue = Math.min(minValue, maxValue);
        this.maxValue = Math.max(minValue, maxValue);
        this.value = clamp(initialValue);
        this.onValueChanged = onValueChanged;
    }

    public DesktopSlider setAccentColor(int color) {
        this.accentColor = color;
        return this;
    }

    public DesktopSlider setLabel(String label) {
        this.label = label == null ? "" : label.trim();
        return this;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int nextValue) {
        setValueInternal(nextValue, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        setFromMouse(mouseX, true);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        setFromMouse(mouseX, true);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        boolean hovered = this.isHoveredOrFocused();
        int border = this.active
                ? (hovered ? 0xFFCDE9FF : 0xFF355A83)
                : 0xFF526075;
        graphics.fill(x1, y1, x2, y2, border);

        int innerX1 = x1 + 1;
        int innerY1 = y1 + 1;
        int innerX2 = x2 - 1;
        int innerY2 = y2 - 1;
        int fillTop = this.active ? 0xF0182F47 : 0xCC2B3745;
        int fillBottom = this.active ? 0xF0112238 : 0xCC212B36;
        int h = Math.max(1, innerY2 - innerY1);
        for (int y = 0; y < h; y++) {
            float t = h <= 1 ? 0.0F : (float) y / (float) (h - 1);
            graphics.fill(innerX1, innerY1 + y, innerX2, innerY1 + y + 1, lerpColor(fillTop, fillBottom, t));
        }
        graphics.fill(innerX1 + 1, innerY1 + 1, innerX2 - 1, innerY1 + 2, 0x55C7E2FF);

        int trackX1 = innerX1 + 8;
        int trackX2 = innerX2 - 56;
        if (trackX2 <= trackX1 + 8) {
            trackX2 = innerX2 - 8;
        }
        int trackY1 = innerY1 + Math.max(4, (innerY2 - innerY1) / 2 - 2);
        int trackY2 = trackY1 + 4;
        graphics.fill(trackX1, trackY1, trackX2, trackY2, 0xFF33495F);
        graphics.fill(trackX1, trackY1, knobCenterX(trackX1, trackX2), trackY2, this.active ? accentColor : 0xFF7D8EA1);

        int knobW = 8;
        int knobH = Math.max(10, this.height - 6);
        int knobX = knobCenterX(trackX1, trackX2) - (knobW / 2);
        int knobY = y1 + Math.max(2, (this.height - knobH) / 2);
        int knobBorder = hovered ? 0xFFE7F4FF : 0xFF7CA2C4;
        int knobFill = this.active ? 0xFF2A5B87 : 0xFF4C5E71;
        graphics.fill(knobX, knobY, knobX + knobW, knobY + knobH, knobBorder);
        graphics.fill(knobX + 1, knobY + 1, knobX + knobW - 1, knobY + knobH - 1, knobFill);

        Font font = Minecraft.getInstance().font;
        String text = (label.isBlank() ? "" : (label + ": ")) + value;
        int textX = Math.min(x2 - 52, trackX2 + 8);
        int textY = y1 + Math.max(1, (this.height - 8) / 2);
        graphics.drawString(font, text, textX, textY, this.active ? 0xFFEAF5FF : 0xFF98A8BA, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                Component.literal((label.isBlank() ? "Slider" : label) + " " + value));
    }

    private void setFromMouse(double mouseX, boolean notify) {
        int innerX1 = this.getX() + 1;
        int innerX2 = this.getX() + this.width - 1;
        int trackX1 = innerX1 + 8;
        int trackX2 = innerX2 - 56;
        if (trackX2 <= trackX1 + 8) {
            trackX2 = innerX2 - 8;
        }
        double clamped = Math.max(trackX1, Math.min(trackX2, mouseX));
        double span = Math.max(1.0D, (double) (trackX2 - trackX1));
        double t = (clamped - trackX1) / span;
        int next = minValue + (int) Math.round(t * (maxValue - minValue));
        setValueInternal(next, notify);
    }

    private int knobCenterX(int trackX1, int trackX2) {
        if (maxValue <= minValue) {
            return trackX1;
        }
        float t = (float) (value - minValue) / (float) (maxValue - minValue);
        t = Math.max(0.0F, Math.min(1.0F, t));
        return trackX1 + Math.round((trackX2 - trackX1) * t);
    }

    private void setValueInternal(int nextValue, boolean notify) {
        int clamped = clamp(nextValue);
        if (clamped == value) {
            return;
        }
        value = clamped;
        if (notify && onValueChanged != null) {
            onValueChanged.accept(value);
        }
    }

    private int clamp(int raw) {
        return Math.max(minValue, Math.min(maxValue, raw));
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
