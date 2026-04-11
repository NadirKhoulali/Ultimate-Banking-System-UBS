package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.BankScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convenience base class for layers that own their own widget lists.
 */
public abstract class AbstractScreenLayer implements ScreenLayer {
    protected static final int COLOR_TITLE = 0xFFFFFFFF;
    protected static final int COLOR_LABEL = 0xFF7ED0FF;
    protected static final int COLOR_VALUE = 0xFFFFFFFF;
    protected static final int COLOR_MUTED = 0xFF9AB2CF;
    protected static final int COLOR_SUCCESS = 0xFF63E07C;
    protected static final int COLOR_ERROR = 0xFFFF6B6B;

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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
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

    protected void drawSectionBox(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF304E76);
        for (int y = top; y < bottom; y++) {
            float ratio = (float) (y - top) / (float) Math.max(1, (bottom - top) - 1);
            int rowColor = lerpColor(0xAA183253, 0xAA12263F, ratio);
            graphics.fill(left, y, right, y + 1, rowColor);
        }
    }

    protected static int lerpColor(int from, int to, float t) {
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

    protected void styleEditBox(EditBox box) {
        box.setBordered(false);
        box.setTextColor(0xFFFFFFFF);
        box.setTextColorUneditable(0xFFE0E0E0);
    }
}
