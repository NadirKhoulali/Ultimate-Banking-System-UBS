package net.austizz.ultimatebankingsystem.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
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
        int frameV = this.isHoveredOrFocused() ? (v + frameHeight) : v;

        // Ensure we never try to draw negative/invalid sizes.
        int lw = Math.min(leftBorder, this.width);
        int rw = Math.min(rightBorder, Math.max(0, this.width - lw));
        int th = Math.min(topBorder, this.height);
        int bh = Math.min(bottomBorder, Math.max(0, this.height - th));

        int centerW = Math.max(0, this.width - lw - rw);
        int centerH = Math.max(0, this.height - th - bh);

        int srcCenterW = Math.max(0, frameWidth - leftBorder - rightBorder);
        int srcCenterH = Math.max(0, frameHeight - topBorder - bottomBorder);

        int x0 = this.getX();
        int y0 = this.getY();

        // 4 corners (no scaling)
        blit(graphics, x0, y0, u, frameV, lw, th); // top-left
        blit(graphics, x0 + this.width - rw, y0, u + frameWidth - rw, frameV, rw, th); // top-right
        blit(graphics, x0, y0 + this.height - bh, u, frameV + frameHeight - bh, lw, bh); // bottom-left
        blit(graphics, x0 + this.width - rw, y0 + this.height - bh,
                u + frameWidth - rw, frameV + frameHeight - bh, rw, bh); // bottom-right

        // Top & bottom edges: tile horizontally
        tile(graphics,
                x0 + lw, y0,
                u + leftBorder, frameV,
                centerW, th,
                srcCenterW, th);
        tile(graphics,
                x0 + lw, y0 + this.height - bh,
                u + leftBorder, frameV + frameHeight - bh,
                centerW, bh,
                srcCenterW, bh);

        // Left & right edges: tile vertically
        tile(graphics,
                x0, y0 + th,
                u, frameV + topBorder,
                lw, centerH,
                lw, srcCenterH);
        tile(graphics,
                x0 + this.width - rw, y0 + th,
                u + frameWidth - rw, frameV + topBorder,
                rw, centerH,
                rw, srcCenterH);

        // Center: tile both directions
        tile(graphics,
                x0 + lw, y0 + th,
                u + leftBorder, frameV + topBorder,
                centerW, centerH,
                srcCenterW, srcCenterH);

        // Optional label
        if (!getMessage().getString().isEmpty()) {
            int color = 0xFF000000;
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    this.getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + (this.height - 8) / 2,
                    color
            );
        }
    }

    private void blit(GuiGraphics graphics, int x, int y, int u, int v, int w, int h) {
        if (w <= 0 || h <= 0) return;
        graphics.blit(texture, x, y, u, v, w, h, textureWidth, textureHeight);
    }

    /**
     * Tiles a source region (srcW x srcH) to fill a destination region (dstW x dstH) without stretching.
     */
    private void tile(GuiGraphics graphics,
                      int dstX, int dstY,
                      int srcU, int srcV,
                      int dstW, int dstH,
                      int srcW, int srcH) {
        if (dstW <= 0 || dstH <= 0 || srcW <= 0 || srcH <= 0) return;

        for (int y = 0; y < dstH; y += srcH) {
            int h = Math.min(srcH, dstH - y);
            for (int x = 0; x < dstW; x += srcW) {
                int w = Math.min(srcW, dstW - x);
                graphics.blit(
                        texture,
                        dstX + x, dstY + y,
                        srcU, srcV,
                        w, h,
                        textureWidth, textureHeight
                );
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}

