package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.hud.HudPosition;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Compact, account-aware renderer for the player's monitored balance. */
@OnlyIn(Dist.CLIENT)
public final class BalanceHudRenderer {
    private static final int MARGIN = 8;
    private static final int NORMAL_HEIGHT = 38;
    private static final int COMPACT_HEIGHT = 27;
    private static final int MIN_WIDTH = 148;
    private static final int MAX_WIDTH = 224;
    private static final int COMPACT_MIN_WIDTH = 102;
    private static final int COMPACT_MAX_WIDTH = 164;
    private static final int PANEL_TOP = 0xF2112639;
    private static final int PANEL_BOTTOM = 0xF0081522;
    private static final int PANEL_BORDER = 0xFF35516A;
    private static final int PANEL_SHADOW = 0x68000000;
    private static final int ICON_PANEL = 0xE01A3043;
    private static final int MUTED = 0xFF9EB4C7;
    private static final int CREDIT = 0xFF42D392;
    private static final int DEBIT = 0xFFFF737A;

    private BalanceHudRenderer() {
    }

    public static void render(Minecraft minecraft, GuiGraphics graphics) {
        if (minecraft == null || graphics == null || !HudClientState.isEnabled()) {
            return;
        }
        String rawBalance = HudClientState.getBalanceText();
        if (rawBalance.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        Font font = minecraft.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        boolean compact = screenWidth < 360 || screenHeight < 210;
        String amount = safe(Config.CURRENCY_SYMBOL.get())
                + MoneyText.abbreviate(HudClientState.getAnimatedBalanceText(now));
        String context = accountContext();
        int changeReserve = HudClientState.getChangeStrength(now) > 0.01F ? 14 : 0;
        int amountWidth = font.width(amount);
        int contextWidth = compact ? 0 : font.width(context);
        int desiredWidth = compact
                ? amountWidth + 42 + changeReserve
                : Math.max(amountWidth + 54 + changeReserve, contextWidth + 48);
        int maxAvailableWidth = screenWidth - MARGIN * 2;
        if (maxAvailableWidth < 48) {
            return;
        }
        int cardWidth = compact
                ? clamp(desiredWidth, Math.min(COMPACT_MIN_WIDTH, maxAvailableWidth),
                        Math.min(COMPACT_MAX_WIDTH, maxAvailableWidth))
                : clamp(desiredWidth, Math.min(MIN_WIDTH, maxAvailableWidth),
                        Math.min(MAX_WIDTH, maxAvailableWidth));
        int cardHeight = compact ? COMPACT_HEIGHT : NORMAL_HEIGHT;
        if (cardWidth <= 0 || cardHeight > screenHeight) {
            return;
        }

        String selectedPosition = HudClientState.getPosition();
        HudPosition position = HudPosition.parseOrDefault(
                selectedPosition.isBlank() ? Config.HUD_CORNER.get() : selectedPosition,
                HudPosition.TOP_RIGHT);
        Layout layout = layout(screenWidth, screenHeight, cardWidth, cardHeight, position);
        if (position == HudPosition.TOP_RIGHT && minecraft.screen == null) {
            int reservedBottom = NotificationRenderer.topRightStackBottom(
                    minecraft, screenWidth, screenHeight);
            int candidateY = reservedBottom + 6;
            if (reservedBottom > 0 && candidateY + cardHeight <= screenHeight - MARGIN) {
                layout = new Layout(layout.x(), candidateY, layout.width(), layout.height(), layout.position());
            }
        }

        float visibility = HudClientState.getAppearanceProgress(now);
        int slide = Math.round((1.0F - visibility) * 18.0F);
        int x = layout.x() + (position.isRight() ? slide : -slide);
        int y = layout.y();
        HudClientState.ChangeDirection direction = HudClientState.getChangeDirection();
        float changeStrength = HudClientState.getChangeStrength(now);
        int configuredAccent = 0xFF000000 | (Config.HUD_TEXT_COLOR.get() & 0x00FFFFFF);
        int changeColor = direction == HudClientState.ChangeDirection.DECREASE ? DEBIT : CREDIT;
        int liveAccent = direction == HudClientState.ChangeDirection.NONE
                ? configuredAccent
                : blend(configuredAccent, changeColor, changeStrength);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 900.0D);
        renderCard(graphics, font, x, y, cardWidth, cardHeight, amount, context,
                compact, liveAccent, direction, changeStrength, visibility);
        graphics.pose().popPose();
    }

    static Layout layout(int screenWidth,
                         int screenHeight,
                         int cardWidth,
                         int cardHeight,
                         HudPosition position) {
        int x = position.isRight()
                ? Math.max(0, screenWidth - cardWidth - MARGIN)
                : Math.min(MARGIN, Math.max(0, screenWidth - cardWidth));
        int y = position.isMiddle()
                ? Math.max(0, (screenHeight - cardHeight) / 2)
                : position.isBottom()
                        ? Math.max(0, screenHeight - cardHeight - MARGIN)
                        : Math.min(MARGIN, Math.max(0, screenHeight - cardHeight));
        return new Layout(x, y, cardWidth, cardHeight, position);
    }

    private static void renderCard(GuiGraphics graphics,
                                   Font font,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   String amount,
                                   String context,
                                   boolean compact,
                                   int accent,
                                   HudClientState.ChangeDirection direction,
                                   float changeStrength,
                                   float visibility) {
        int shadow = alpha(PANEL_SHADOW, visibility);
        int border = alpha(blend(PANEL_BORDER, accent, changeStrength * 0.58F), visibility);
        int panelTop = alpha(PANEL_TOP, visibility);
        int panelBottom = alpha(PANEL_BOTTOM, visibility);
        int iconPanel = alpha(ICON_PANEL, visibility);
        int accentVisible = alpha(accent, visibility);
        int muted = alpha(MUTED, visibility);

        drawCutPanel(graphics, x + 2, y + 3, width, height, shadow, shadow);
        drawCutPanel(graphics, x, y, width, height, border, panelBottom);
        graphics.fillGradient(x + 3, y + 1, x + width - 2, y + height - 1, panelTop, panelBottom);
        graphics.fill(x + 1, y + 3, x + 4, y + height - 3, accentVisible);

        int iconSize = compact ? 19 : 22;
        int iconX = x + 8;
        int iconY = y + (height - iconSize) / 2;
        graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, iconPanel);
        drawBankIcon(graphics, iconX + (compact ? 2 : 3), iconY + (compact ? 3 : 4), accentVisible);

        int textX = iconX + iconSize + 7;
        int indicatorReserve = changeStrength > 0.01F ? 13 : 0;
        int textRight = x + width - 7 - indicatorReserve;
        int textWidth = Math.max(8, textRight - textX);
        if (compact) {
            graphics.drawString(font, truncate(font, amount, textWidth), textX,
                    y + (height - font.lineHeight) / 2 + 1, accentVisible, false);
        } else {
            graphics.drawString(font, truncate(font, context, textWidth), textX, y + 6, muted, false);
            graphics.drawString(font, truncate(font, amount, textWidth), textX, y + 20,
                    accentVisible, false);
        }

        if (changeStrength > 0.01F && direction != HudClientState.ChangeDirection.NONE) {
            int indicatorX = x + width - 12;
            int indicatorY = y + (height - 11) / 2;
            int changeColor = alpha(direction == HudClientState.ChangeDirection.INCREASE ? CREDIT : DEBIT,
                    visibility * Math.max(0.35F, changeStrength));
            drawChangeIndicator(graphics, indicatorX, indicatorY,
                    direction == HudClientState.ChangeDirection.INCREASE, changeColor);
        }
    }

    private static void drawBankIcon(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 5, y, x + 9, y + 2, color);
        graphics.fill(x + 3, y + 2, x + 11, y + 4, color);
        graphics.fill(x + 1, y + 4, x + 13, y + 6, color);
        graphics.fill(x + 2, y + 7, x + 4, y + 13, color);
        graphics.fill(x + 6, y + 7, x + 8, y + 13, color);
        graphics.fill(x + 10, y + 7, x + 12, y + 13, color);
        graphics.fill(x, y + 13, x + 14, y + 15, color);
    }

    private static void drawChangeIndicator(GuiGraphics graphics,
                                            int x,
                                            int y,
                                            boolean increase,
                                            int color) {
        if (increase) {
            graphics.fill(x + 3, y, x + 5, y + 8, color);
            graphics.fill(x + 1, y + 2, x + 7, y + 4, color);
        } else {
            graphics.fill(x + 1, y + 5, x + 7, y + 7, color);
        }
    }

    private static String accountContext() {
        String type = shortAccountType(HudClientState.getAccountType());
        String bank = HudClientState.getBankName();
        String prefix = HudClientState.isPrimaryAccount() ? "PRIMARY" : "MONITORED";
        if (type.isBlank() && bank.isBlank()) {
            return "ACCOUNT BALANCE";
        }
        if (type.isBlank()) {
            return prefix + " | " + bank;
        }
        if (bank.isBlank()) {
            return prefix + " | " + type;
        }
        return prefix + " | " + type + " @ " + bank;
    }

    private static String shortAccountType(String value) {
        String normalized = safe(value).trim();
        return normalized.endsWith(" Account")
                ? normalized.substring(0, normalized.length() - " Account".length())
                : normalized;
    }

    private static String truncate(Font font, String value, int maxWidth) {
        String normalized = safe(value);
        if (font.width(normalized) <= maxWidth) {
            return normalized;
        }
        String ellipsis = "...";
        int available = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(normalized, available) + ellipsis;
    }

    private static void drawCutPanel(GuiGraphics graphics,
                                     int x,
                                     int y,
                                     int width,
                                     int height,
                                     int border,
                                     int fill) {
        graphics.fill(x + 2, y, x + width - 2, y + height, border);
        graphics.fill(x, y + 2, x + width, y + height - 2, border);
        graphics.fill(x + 2, y + 1, x + width - 2, y + height - 1, fill);
        graphics.fill(x + 1, y + 2, x + width - 1, y + height - 2, fill);
    }

    private static int alpha(int argb, float factor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = clamp(Math.round(baseAlpha * factor), 0, 255);
        return (argb & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    private static int blend(int first, int second, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int alpha = mixChannel(first >>> 24, second >>> 24, clamped);
        int red = mixChannel(first >>> 16, second >>> 16, clamped);
        int green = mixChannel(first >>> 8, second >>> 8, clamped);
        int blue = mixChannel(first, second, clamped);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mixChannel(int first, int second, float amount) {
        int from = first & 0xFF;
        int to = second & 0xFF;
        return clamp(Math.round(from + (to - from) * amount), 0, 255);
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (maximum < minimum) {
            return Math.max(0, maximum);
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    record Layout(int x, int y, int width, int height, HudPosition position) {
    }
}
