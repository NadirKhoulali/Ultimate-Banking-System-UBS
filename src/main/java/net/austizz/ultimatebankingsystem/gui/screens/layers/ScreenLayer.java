package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.BankScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;

/**
 * A "layer" that can be hosted by a parent Screen.
 *
 * Contract:
 * - {@link #init(int, int)} is called after the parent screen is laid out; create widgets there.
 * - Widgets must be returned via {@link #children()} and {@link #renderables()} so the parent can
 *   handle rendering + input dispatch.
 * - {@link #render(GuiGraphics, int, int, float)} is for custom drawing behind/around widgets.
 */
public interface ScreenLayer {

    void init(int screenWidth, int screenHeight);

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

    default void tick() {}

    default void removed() {}

    default void setBankScreen(BankScreen screen) {}

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    List<? extends GuiEventListener> children();

    List<? extends Renderable> renderables();
}
