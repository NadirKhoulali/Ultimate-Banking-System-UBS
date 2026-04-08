package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.BankScreen;
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
    protected BankScreen bankScreen;

    protected int screenWidth;
    protected int screenHeight;

    protected final List<GuiEventListener> children = new ArrayList<>();
    protected final List<Renderable> renderables = new ArrayList<>();

    protected AbstractScreenLayer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.font = minecraft.font;
    }

    @Override
    public void setBankScreen(BankScreen screen) {
        this.bankScreen = screen;
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

    protected String fitToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
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

    protected List<String> wrapToWidth(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0 || maxLines <= 0) {
            return lines;
        }

        boolean truncated = false;
        String[] paragraphs = text.split("\\R", -1);
        for (int p = 0; p < paragraphs.length && lines.size() < maxLines; p++) {
            String paragraph = paragraphs[p];
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }

            String[] words = paragraph.split("\\s+");
            String currentLine = "";
            for (int i = 0; i < words.length && lines.size() < maxLines; i++) {
                String word = words[i];
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (font.width(candidate) <= maxWidth) {
                    currentLine = candidate;
                    continue;
                }

                if (!currentLine.isEmpty()) {
                    lines.add(currentLine);
                    currentLine = "";
                    if (lines.size() >= maxLines) {
                        if (i < words.length - 1 || p < paragraphs.length - 1) {
                            truncated = true;
                        }
                        break;
                    }
                }

                if (font.width(word) <= maxWidth) {
                    currentLine = word;
                } else {
                    lines.add(fitToWidth(word, maxWidth));
                    if (lines.size() >= maxLines && (i < words.length - 1 || p < paragraphs.length - 1)) {
                        truncated = true;
                        break;
                    }
                }
            }

            if (!currentLine.isEmpty() && lines.size() < maxLines) {
                lines.add(currentLine);
            } else if (!currentLine.isEmpty()) {
                truncated = true;
            }
        }

        if (truncated && !lines.isEmpty()) {
            int lastIndex = lines.size() - 1;
            lines.set(lastIndex, fitToWidth(lines.get(lastIndex) + "...", maxWidth));
        }

        return lines;
    }

    protected void drawFittedString(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, fitToWidth(text, maxWidth), x, y, color);
    }

    protected void drawCenteredFittedString(GuiGraphics graphics, String text, int centerX, int y, int maxWidth, int color) {
        graphics.drawCenteredString(font, fitToWidth(text, maxWidth), centerX, y, color);
    }

    protected void drawWrappedCentered(GuiGraphics graphics, String text, int centerX, int y, int maxWidth, int color, int maxLines) {
        List<String> lines = wrapToWidth(text, maxWidth, maxLines);
        int lineY = y;
        int lineSpacing = font.lineHeight + 1;
        for (String line : lines) {
            graphics.drawCenteredString(font, line, centerX, lineY, color);
            lineY += lineSpacing;
        }
    }
}

