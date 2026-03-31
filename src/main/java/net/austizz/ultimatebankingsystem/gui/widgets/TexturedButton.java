package net.austizz.ultimatebankingsystem.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Simple button that renders a custom texture region.
 *
 * The texture is treated as a sprite sheet; you provide the (u, v) of the normal state, and the
 * hovered state is expected to be directly below it (v + height).
 */
public class TexturedButton extends AbstractButton {

    private final ResourceLocation texture;
    private final int u;
    private final int v;
    private final int textureWidth;
    private final int textureHeight;
    private final Consumer<TexturedButton> onPress;

    public TexturedButton(int x, int y,
                          int width, int height,
                          ResourceLocation texture,
                          int u, int v,
                          int textureWidth, int textureHeight,
                          Component message,
                          Consumer<TexturedButton> onPress) {
        super(x, y, width, height, message);
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
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
        int vOffset = this.isHoveredOrFocused() ? this.height : 0;

        graphics.blit(
                texture,
                this.getX(), this.getY(),
                u, v + vOffset,
                this.width, this.height,
                textureWidth, textureHeight
        );

        // Optional: draw text centered on top.
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

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Minimal narration support so the class isn't abstract.
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
