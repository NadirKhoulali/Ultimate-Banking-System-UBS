package net.austizz.ultimatebankingsystem.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * A 9-slice textured button (no stretching of corners; center/edges tile).
 *
 * Texture layout:
 * - One "frame" is {@code frameWidth x frameHeight} at (u, v).
 * - Hovered frame is expected directly BELOW the normal frame (v + frameHeight).
 * - Inside each frame, borders are: leftBorder, rightBorder, topBorder, bottomBorder.
 */
public class NineSliceTexturedButton extends AbstractButton {

    private final ResourceLocation texture;

    private final int u;
    private final int v;
    private final int frameWidth;
    private final int frameHeight;

    private final int textureWidth;
    private final int textureHeight;

    private final int leftBorder;
    private final int rightBorder;
    private final int topBorder;
    private final int bottomBorder;

    private final Consumer<NineSliceTexturedButton> onPress;

    public NineSliceTexturedButton(int x, int y,
                                   int width, int height,
                                   ResourceLocation texture,
                                   int u, int v,
                                   int frameWidth, int frameHeight,
                                   int textureWidth, int textureHeight,
                                   int leftBorder, int rightBorder, int topBorder, int bottomBorder,
                                   Component message,
                                   Consumer<NineSliceTexturedButton> onPress) {
        super(x, y, width, height, message);
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.leftBorder = leftBorder;
        this.rightBorder = rightBorder;
        this.topBorder = topBorder;
        this.bottomBorder = bottomBorder;
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
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int x2 = x0 + this.width - 1;
        int y2 = y0 + this.height - 1;
        if (x2 > x1 && y2 > y1) {
            int phaseOffset = Math.floorMod((x0 * 31) + (y0 * 17), 3200);
            float cycle = ((System.currentTimeMillis() + phaseOffset) % 3200L) / 3200.0F;
            float pulse = cycle <= 0.5F ? (cycle * 2.0F) : ((1.0F - cycle) * 2.0F);

            int baseDark = !this.active ? 0x80424B58 : 0xCC173F6A;
            int baseLight = !this.active ? 0xA06A7686 : 0xE25EB8EE;
            int topColor = lerpColor(baseDark, baseLight, pulse);
            int bottomColor = lerpColor(baseLight, baseDark, pulse);
            int fillHeight = Math.max(1, y2 - y1);

            for (int y = 0; y < fillHeight; y++) {
                float ratio = fillHeight <= 1 ? 0.0F : (float) y / (float) (fillHeight - 1);
                int rowColor = lerpColor(topColor, bottomColor, ratio);
                graphics.fill(x1, y1 + y, x2, y1 + y + 1, rowColor);
            }

            int borderColor;
            if (!this.active) {
                borderColor = 0x88545D69;
            } else if (this.isHoveredOrFocused()) {
                borderColor = 0xFF7BCBFF;
            } else {
                borderColor = 0xFF2A4768;
            }
            graphics.fill(x1, y1, x2, y1 + 1, borderColor);
            graphics.fill(x1, y2 - 1, x2, y2, borderColor);
            graphics.fill(x1, y1, x1 + 1, y2, borderColor);
            graphics.fill(x2 - 1, y1, x2, y2, borderColor);
        }

        // Optional label
        if (!getMessage().getString().isEmpty()) {
            String fittedText = fitToWidth(getMessage().getString(), Math.max(0, this.width - 8));
            int color = resolveTextColor(getMessage().getStyle().getColor(), this.active);
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    fittedText,
                    this.getX() + this.width / 2,
                    this.getY() + (this.height - 8) / 2,
                    color
            );
        }
    }

    private static String fitToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }

        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth > maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static int resolveTextColor(TextColor styledColor, boolean active) {
        if (!active) {
            return 0xFF7F7F7F;
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
