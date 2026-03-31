package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.screens.layers.MainMenuLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.ScreenLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class BankScreen extends Screen {

    private final List<ScreenLayer> layers = new ArrayList<>();

    public BankScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();

        // (Re)build layer stack and initialize layers.
        layers.clear();
        layers.add(new MainMenuLayer(Minecraft.getInstance()));
        for (ScreenLayer layer : layers) {
            layer.init(this.width, this.height);
        }

        // Collect layer widgets into the parent Screen so vanilla input dispatch works.
        for (ScreenLayer layer : layers) {
            for (var r : layer.renderables()) {
                // Only widgets (AbstractWidget etc.) can be added this way; our layers currently add widgets.
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                    this.addRenderableWidget(w);
                } else {
                    this.addRenderableOnly(r);
                }
            }
        }

        int boxWidth = 200;
        int boxHeight = 150;
        int left = (this.width - boxWidth) / 2;
        int top = (this.height - boxHeight) / 2;

        // Example button owned by the parent screen.
        Component close = Component.literal("Close ATM");
        this.addRenderableWidget(new PlainTextButton(
                10, 10, minecraft.font.width(close), minecraft.font.wordWrapHeight(close, minecraft.font.width(close)),
                close,
                button -> onClose(),
                Minecraft.getInstance().font
        ));
    }

    @Override
    public void tick() {
        super.tick();
        for (ScreenLayer layer : layers) {
            layer.tick();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void removed() {
        for (ScreenLayer layer : layers) {
            layer.removed();
        }
        super.removed();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        // Include layer children too (important if you later add non-widget GuiEventListeners).
        List<GuiEventListener> kids = new ArrayList<>(super.children());
        for (ScreenLayer layer : layers) {
            kids.addAll(layer.children());
        }
        return kids;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Draw the bank panel.
        int boxWidth = 400;
        int boxHeight = 200;
        int left = (this.width - boxWidth) / 2;
        int top = (this.height - boxHeight) / 2;
        graphics.fill(left, top, left + boxWidth, top + boxHeight, 0xFFFFFFFF);

        // Let layers draw any custom stuff on top of the panel (their widgets are handled by super.render).
        for (ScreenLayer layer : layers) {
            layer.render(graphics, mouseX, mouseY, partialTicks);
        }
    }
}

