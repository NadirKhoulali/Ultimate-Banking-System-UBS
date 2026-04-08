package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.screens.layers.MainMenuLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.ScreenLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

public class BankScreen extends Screen {

    private final Deque<ScreenLayer> layerStack = new ArrayDeque<>();

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 220;

    public BankScreen(Component title) {
        super(title);
    }

    public int getPanelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    public int getPanelTop() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    public int getPanelWidth() {
        return PANEL_WIDTH;
    }

    public int getPanelHeight() {
        return PANEL_HEIGHT;
    }

    public ScreenLayer getTopLayer() {
        return layerStack.peek();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        if (layerStack.isEmpty()) {
            pushLayer(new MainMenuLayer(Minecraft.getInstance()));
        } else {
            // Screen resize — re-init the top layer and re-register its widgets.
            ScreenLayer top = layerStack.peek();
            top.init(this.width, this.height);
            registerLayerWidgets(top);
        }
    }

    public void pushLayer(ScreenLayer layer) {
        this.clearWidgets();
        layerStack.push(layer);
        layer.setBankScreen(this);
        layer.init(this.width, this.height);
        registerLayerWidgets(layer);
    }

    public void popLayer() {
        if (layerStack.size() <= 1) {
            this.onClose();
            return;
        }
        ScreenLayer removed = layerStack.pop();
        removed.removed();
        this.clearWidgets();
        ScreenLayer top = layerStack.peek();
        top.init(this.width, this.height);
        registerLayerWidgets(top);
    }

    private void registerLayerWidgets(ScreenLayer layer) {
        for (var r : layer.renderables()) {
            if (r instanceof AbstractWidget w) {
                this.addRenderableWidget(w);
            } else {
                this.addRenderableOnly(r);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && layerStack.size() > 1) { // 256 = GLFW_KEY_ESCAPE
            popLayer();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ScreenLayer top = layerStack.peek();
        if (top != null && top.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        ScreenLayer top = layerStack.peek();
        if (top != null) {
            top.tick();
        }
    }

    @Override
    public void onClose() {
        ClientATMData.clear();
        super.onClose();
    }

    @Override
    public void removed() {
        for (ScreenLayer layer : layerStack) {
            layer.removed();
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Draw semi-transparent dark overlay behind the panel (dims the game world)
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int left = getPanelLeft();
        int top = getPanelTop();
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        // Outer border (2px, lighter gray-blue)
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF3A3A5E);

        // Main panel background (dark charcoal)
        graphics.fill(left, top, right, bottom, 0xFF1A1A2E);

        // Header bar (bank-blue, 20px tall)
        int headerHeight = 20;
        graphics.fill(left, top, right, top + headerHeight, 0xFF0D47A1);

        // Header bottom border (thin highlight line)
        graphics.fill(left, top + headerHeight, right, top + headerHeight + 1, 0xFF1565C0);

        // Content area (slightly lighter, below header)
        int contentTop = top + headerHeight + 1;
        graphics.fill(left + 4, contentTop + 4, right - 4, bottom - 4, 0xFF252540);

        // Header title text: "ATM Machine" centered in header bar
        graphics.drawCenteredString(
            this.font,
            Component.literal("ATM Machine"),
            left + PANEL_WIDTH / 2,
            top + (headerHeight - 8) / 2,
            0xFFFFFFFF
        );

        // Render widgets (buttons, text fields, etc.)
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Let the top layer draw any custom overlay
        ScreenLayer topLayer = layerStack.peek();
        if (topLayer != null) {
            topLayer.render(graphics, mouseX, mouseY, partialTicks);
        }
    }
}
