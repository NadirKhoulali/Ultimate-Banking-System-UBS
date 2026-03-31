package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convenience base class for layers that own their own widget lists.
 */
public abstract class AbstractScreenLayer implements ScreenLayer {

    protected final Minecraft minecraft;
    protected final Font font;

    protected int screenWidth;
    protected int screenHeight;

    protected final List<GuiEventListener> children = new ArrayList<>();
    protected final List<Renderable> renderables = new ArrayList<>();

    protected AbstractScreenLayer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.font = minecraft.font;
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.children.clear();
        this.renderables.clear();
        onInit();
    }

    protected abstract void onInit();

    /**
     * Layers can draw their own background/frames/text here.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // default: nothing
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public List<? extends Renderable> renderables() {
        return Collections.unmodifiableList(renderables);
    }

    protected <T extends GuiEventListener & Renderable> T addWidget(T widget) {
        this.children.add(widget);
        this.renderables.add(widget);
        return widget;
    }
}

