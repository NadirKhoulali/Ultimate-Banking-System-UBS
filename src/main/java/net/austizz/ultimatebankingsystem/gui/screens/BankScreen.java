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

    private static final int PANEL_WIDTH = 304;
    private static final int PANEL_HEIGHT = 252;
    private static final int HEADER_HEIGHT = 24;
    private static final int INNER_MARGIN = 6;

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
        this.renderTransparentBackground(graphics);

        int left = getPanelLeft();
        int top = getPanelTop();
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        // Outer ATM shell
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, 0xE60A1222);
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xE61C2C43);

        // Main panel body gradient
        for (int y = top; y < bottom; y++) {
            float ratio = (float) (y - top) / (float) Math.max(1, PANEL_HEIGHT - 1);
            int rowColor = lerpColor(0xE616273F, 0xE60E182A, ratio);
            graphics.fill(left, y, right, y + 1, rowColor);
        }
        graphics.fill(left, top, right, top + 1, 0xE65F7EA8);
        graphics.fill(left, bottom - 1, right, bottom, 0xE6091121);

        // Header panel
        for (int y = 0; y < HEADER_HEIGHT; y++) {
            float ratio = (float) y / (float) Math.max(1, HEADER_HEIGHT - 1);
            int rowColor = lerpColor(0xE62D78CF, 0xE6104889, ratio);
            graphics.fill(left + 1, top + 1 + y, right - 1, top + 2 + y, rowColor);
        }
        graphics.fill(left + 1, top + 1 + HEADER_HEIGHT, right - 1, top + 2 + HEADER_HEIGHT, 0xE677B8F5);
        graphics.fill(left + 1, top + 2, right - 1, top + 3, 0x66BEE8FF);

        // Content panel
        int contentTop = top + HEADER_HEIGHT + 4;
        int contentBottom = bottom - INNER_MARGIN;
        int contentLeft = left + INNER_MARGIN;
        int contentRight = right - INNER_MARGIN;
        graphics.fill(contentLeft - 1, contentTop - 1, contentRight + 1, contentBottom + 1, 0xD62C4A72);
        for (int y = contentTop; y < contentBottom; y++) {
            float ratio = (float) (y - contentTop) / (float) Math.max(1, contentBottom - contentTop - 1);
            int rowColor = lerpColor(0xC6223A59, 0xC61B2C45, ratio);
            graphics.fill(contentLeft, y, contentRight, y + 1, rowColor);
        }

        // Footer status stripe
        int statusTop = bottom - 13;
        graphics.fill(contentLeft, statusTop, contentRight, statusTop + 1, 0xCC3F628D);
        graphics.fill(contentLeft, statusTop + 1, contentRight, contentBottom, 0xAA11233A);

        graphics.drawCenteredString(
            this.font,
            Component.literal("ATM MACHINE"),
            left + PANEL_WIDTH / 2,
            top + 8,
            0xFFFFFFFF
        );
        graphics.drawString(this.font, "SECURE TERMINAL", contentLeft + 4, statusTop + 3, 0xFF87BDE9);

        super.render(graphics, mouseX, mouseY, partialTicks);

        ScreenLayer topLayer = layerStack.peek();
        if (topLayer != null) {
            topLayer.render(graphics, mouseX, mouseY, partialTicks);
        }
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
