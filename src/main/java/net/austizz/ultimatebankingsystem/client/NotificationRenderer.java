package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.api.ApiNotificationPlacement;
import net.austizz.ultimatebankingsystem.api.ApiNotificationPriority;
import net.austizz.ultimatebankingsystem.api.ApiNotificationRequest;
import net.austizz.ultimatebankingsystem.api.ApiNotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Responsive renderer for UBS notification API v2. */
@OnlyIn(Dist.CLIENT)
public final class NotificationRenderer {
    private static final int STACK_GAP = 6;
    private static final int MAX_PER_STACK = 4;
    private static final int CARD_MIN_WIDTH = 232;
    private static final int CARD_MAX_WIDTH = 344;
    private static long lastSoundAt;

    private NotificationRenderer() {
    }

    public static void render(Minecraft minecraft, GuiGraphics graphics) {
        if (minecraft == null || minecraft.player == null || graphics == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<NotificationClientState.View> notifications = NotificationClientState.snapshot();
        if (notifications.isEmpty()) {
            return;
        }

        playPendingSound(minecraft, notifications, now);
        Map<ApiNotificationPlacement, List<NotificationClientState.View>> stacks =
                new EnumMap<>(ApiNotificationPlacement.class);
        for (NotificationClientState.View notification : notifications) {
            ApiNotificationPlacement placement = resolvePlacement(notification, minecraft.screen != null,
                    graphics.guiWidth());
            List<NotificationClientState.View> stack = stacks.computeIfAbsent(placement, ignored -> new ArrayList<>());
            if (stack.size() < MAX_PER_STACK) {
                stack.add(notification);
            }
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 1450.0D);
        renderTopRight(minecraft, graphics, stacks.get(ApiNotificationPlacement.TOP_RIGHT), now);
        renderTopCenter(minecraft, graphics, stacks.get(ApiNotificationPlacement.TOP_CENTER), now);
        renderBottomRight(minecraft, graphics, stacks.get(ApiNotificationPlacement.BOTTOM_RIGHT), now);
        graphics.pose().popPose();
    }

    /** Bottom edge reserved by visible top-right cards, used by adjacent HUD widgets. */
    public static int topRightStackBottom(Minecraft minecraft, int screenWidth, int screenHeight) {
        if (minecraft == null || minecraft.screen != null) {
            return 0;
        }
        List<NotificationClientState.View> notifications = NotificationClientState.snapshot();
        if (notifications.isEmpty()) {
            return 0;
        }
        int width = cardWidth(screenWidth, false);
        int y = 10;
        int rendered = 0;
        for (NotificationClientState.View notification : notifications) {
            if (resolvePlacement(notification, false, screenWidth) != ApiNotificationPlacement.TOP_RIGHT) {
                continue;
            }
            if (rendered >= MAX_PER_STACK) {
                break;
            }
            CardLayout layout = layout(minecraft, notification, width);
            if (y + layout.height() > screenHeight - 8) {
                break;
            }
            y += layout.height() + STACK_GAP;
            rendered++;
        }
        return rendered == 0 ? 0 : y - STACK_GAP;
    }

    private static void renderTopRight(Minecraft minecraft,
                                       GuiGraphics graphics,
                                       List<NotificationClientState.View> stack,
                                       long now) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int width = cardWidth(graphics.guiWidth(), false);
        int y = 10;
        for (NotificationClientState.View notification : stack) {
            CardLayout layout = layout(minecraft, notification, width);
            if (y + layout.height() > graphics.guiHeight() - 8) {
                break;
            }
            float visibility = smooth(notification.visibility(now));
            int x = graphics.guiWidth() - width - 10 + Math.round((1.0F - visibility) * 38.0F);
            renderCard(minecraft, graphics, notification, layout, x, y, visibility, now);
            y += layout.height() + STACK_GAP;
        }
    }

    private static void renderTopCenter(Minecraft minecraft,
                                        GuiGraphics graphics,
                                        List<NotificationClientState.View> stack,
                                        long now) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int width = cardWidth(graphics.guiWidth(), true);
        int y = ActionAlertClientState.isActive()
                ? 92
                : PhoneNotificationClientState.isActive() && !SmartphoneClientState.isInteractive() ? 78 : 10;
        for (NotificationClientState.View notification : stack) {
            CardLayout layout = layout(minecraft, notification, width);
            if (y + layout.height() > graphics.guiHeight() - 8) {
                break;
            }
            float visibility = smooth(notification.visibility(now));
            int x = (graphics.guiWidth() - width) / 2;
            int animatedY = y - Math.round((1.0F - visibility) * 28.0F);
            renderCard(minecraft, graphics, notification, layout, x, animatedY, visibility, now);
            y += layout.height() + STACK_GAP;
        }
    }

    private static void renderBottomRight(Minecraft minecraft,
                                          GuiGraphics graphics,
                                          List<NotificationClientState.View> stack,
                                          long now) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int width = cardWidth(graphics.guiWidth(), false);
        int y = graphics.guiHeight() - 30;
        for (NotificationClientState.View notification : stack) {
            CardLayout layout = layout(minecraft, notification, width);
            if (y - layout.height() < 8) {
                break;
            }
            y -= layout.height();
            float visibility = smooth(notification.visibility(now));
            int x = graphics.guiWidth() - width - 10 + Math.round((1.0F - visibility) * 38.0F);
            renderCard(minecraft, graphics, notification, layout, x, y, visibility, now);
            y -= STACK_GAP;
        }
    }

    private static CardLayout layout(Minecraft minecraft,
                                     NotificationClientState.View notification,
                                     int width) {
        int textWidth = Math.max(80, width - 54);
        List<FormattedCharSequence> messageLines = limitedLines(
                minecraft.font.split(Component.literal(notification.message()), textWidth), 3);
        List<FormattedCharSequence> detailLines = notification.detail().isBlank()
                ? List.of()
                : limitedLines(minecraft.font.split(Component.literal(notification.detail()), textWidth), 2);
        int height = 13 + minecraft.font.lineHeight;
        height += Math.max(1, messageLines.size()) * minecraft.font.lineHeight + 4;
        if (!detailLines.isEmpty()) {
            height += detailLines.size() * minecraft.font.lineHeight + 3;
        }
        if (notification.hasProgress()) {
            height += 10;
        }
        height += 8;
        return new CardLayout(width, Math.max(54, height), messageLines, detailLines);
    }

    private static void renderCard(Minecraft minecraft,
                                   GuiGraphics graphics,
                                   NotificationClientState.View notification,
                                   CardLayout layout,
                                   int x,
                                   int y,
                                   float visibility,
                                   long now) {
        if (visibility <= 0.01F) {
            return;
        }
        Palette palette = palette(notification.type());
        int width = layout.width();
        int height = layout.height();
        int accent = palette.accent();
        if (notification.priority() == ApiNotificationPriority.CRITICAL) {
            float pulse = 0.72F + (float) ((Math.sin(now / 115.0D) + 1.0D) * 0.14D);
            accent = scaleRgb(accent, pulse);
        }

        int shadow = alpha(0x70000000, visibility * 0.72F);
        int border = alpha(notification.priority().id() >= ApiNotificationPriority.HIGH.id()
                ? accent : 0xFF38516A, visibility);
        int panel = alpha(0xF20B1826, visibility);
        int panelSoft = alpha(0xF2102335, visibility);
        int iconPanel = alpha(0xD91B3144, visibility);
        int text = alpha(0xFFF4F8FC, visibility);
        int muted = alpha(0xFFA9BAC9, visibility);
        int subtle = alpha(0xFF7890A4, visibility);
        int accentVisible = alpha(accent, visibility);

        drawCutPanel(graphics, x + 2, y + 3, width, height, shadow, shadow);
        drawCutPanel(graphics, x, y, width, height, border, panel);
        graphics.fillGradient(x + 4, y + 1, x + width - 2, y + height - 1, panelSoft, panel);
        graphics.fill(x + 1, y + 3, x + 4, y + height - 3, accentVisible);

        int iconX = x + 11;
        int iconY = y + 18;
        graphics.fill(iconX - 4, iconY - 4, iconX + 24, iconY + 24, iconPanel);
        drawIcon(graphics, notification.type(), iconX, iconY, accentVisible);

        String typeLabel = label(notification.type());
        String source = notification.source().isBlank() ? typeLabel : notification.source();
        String meta = source + " | " + typeLabel;
        if (notification.priority() == ApiNotificationPriority.CRITICAL) {
            meta += " | CRITICAL";
        }
        int badgeReserve = notification.repeatCount() > 1
                ? minecraft.font.width("x" + notification.repeatCount()) + 20
                : notification.sticky() ? minecraft.font.width("PINNED") + 16 : 8;
        int headingWidth = Math.max(48, width - 53 - badgeReserve);
        graphics.drawString(minecraft.font, truncate(minecraft, meta, headingWidth),
                x + 45, y + 6, subtle, false);

        String title = notification.title().isBlank() ? defaultTitle(notification.type()) : notification.title();
        graphics.drawString(minecraft.font, truncate(minecraft, title, headingWidth),
                x + 45, y + 16, text, false);

        if (notification.repeatCount() > 1) {
            String count = "x" + notification.repeatCount();
            int badgeWidth = minecraft.font.width(count) + 8;
            int badgeX = x + width - badgeWidth - 6;
            graphics.fill(badgeX, y + 5, badgeX + badgeWidth, y + 16, alpha(accent, visibility * 0.35F));
            graphics.drawString(minecraft.font, count, badgeX + 4, y + 7, text, false);
        } else if (notification.sticky()) {
            String pinned = "PINNED";
            int badgeX = x + width - minecraft.font.width(pinned) - 8;
            graphics.drawString(minecraft.font, pinned, badgeX, y + 6, accentVisible, false);
        }

        int textY = y + 29;
        for (FormattedCharSequence line : layout.messageLines()) {
            graphics.drawString(minecraft.font, line, x + 45, textY, text, false);
            textY += minecraft.font.lineHeight;
        }
        if (!layout.detailLines().isEmpty()) {
            textY += 2;
            for (FormattedCharSequence line : layout.detailLines()) {
                graphics.drawString(minecraft.font, line, x + 45, textY, muted, false);
                textY += minecraft.font.lineHeight;
            }
        }

        if (notification.hasProgress()) {
            int barX = x + 45;
            int barY = y + height - 8;
            int barWidth = width - 55;
            graphics.fill(barX, barY, barX + barWidth, barY + 3, alpha(0xFF263B4E, visibility));
            if (notification.progress() == ApiNotificationRequest.INDETERMINATE_PROGRESS) {
                int sweepWidth = Math.max(20, barWidth / 4);
                int travel = Math.max(1, barWidth + sweepWidth);
                int sweepX = (int) ((now / 7L) % travel) - sweepWidth;
                int left = Math.max(0, sweepX);
                int right = Math.min(barWidth, sweepX + sweepWidth);
                if (right > left) {
                    graphics.fill(barX + left, barY, barX + right, barY + 3, accentVisible);
                }
            } else {
                int filled = Math.round(barWidth * notification.progress());
                if (filled > 0) {
                    graphics.fill(barX, barY, barX + filled, barY + 3, accentVisible);
                }
                String percent = Math.round(notification.progress() * 100.0F) + "%";
                graphics.drawString(minecraft.font, percent,
                        x + width - minecraft.font.width(percent) - 7,
                        barY - minecraft.font.lineHeight - 1, muted, false);
            }
        }
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

    private static void drawIcon(GuiGraphics graphics,
                                 ApiNotificationType type,
                                 int x,
                                 int y,
                                 int color) {
        int iconCutout = (color & 0xFF000000) | 0x00102131;
        switch (type) {
            case SUCCESS -> {
                graphics.fill(x + 3, y + 10, x + 6, y + 13, color);
                graphics.fill(x + 5, y + 12, x + 8, y + 15, color);
                graphics.fill(x + 7, y + 10, x + 10, y + 13, color);
                graphics.fill(x + 9, y + 8, x + 12, y + 11, color);
                graphics.fill(x + 11, y + 6, x + 14, y + 9, color);
            }
            case ERROR -> {
                for (int i = 0; i < 9; i += 2) {
                    graphics.fill(x + 4 + i, y + 4 + i, x + 7 + i, y + 7 + i, color);
                    graphics.fill(x + 12 - i, y + 4 + i, x + 15 - i, y + 7 + i, color);
                }
            }
            case WARNING -> {
                graphics.fill(x + 9, y + 3, x + 11, y + 12, color);
                graphics.fill(x + 9, y + 15, x + 11, y + 18, color);
                graphics.fill(x + 6, y + 18, x + 14, y + 19, color);
            }
            case INFO -> {
                graphics.fill(x + 9, y + 4, x + 12, y + 7, color);
                graphics.fill(x + 9, y + 9, x + 12, y + 17, color);
                graphics.fill(x + 7, y + 16, x + 14, y + 18, color);
            }
            case TRANSACTION -> {
                graphics.fill(x + 3, y + 6, x + 16, y + 8, color);
                graphics.fill(x + 13, y + 4, x + 16, y + 10, color);
                graphics.fill(x + 4, y + 13, x + 17, y + 15, color);
                graphics.fill(x + 4, y + 11, x + 7, y + 17, color);
            }
            case SECURITY -> {
                graphics.fill(x + 5, y + 4, x + 15, y + 6, color);
                graphics.fill(x + 4, y + 6, x + 16, y + 12, color);
                graphics.fill(x + 6, y + 12, x + 14, y + 16, color);
                graphics.fill(x + 9, y + 16, x + 11, y + 18, color);
            }
            case MESSAGE -> {
                graphics.fill(x + 3, y + 5, x + 17, y + 15, color);
                graphics.fill(x + 6, y + 15, x + 9, y + 18, color);
                graphics.fill(x + 5, y + 8, x + 15, y + 10, iconCutout);
                graphics.fill(x + 5, y + 11, x + 12, y + 13, iconCutout);
            }
            case PROGRESS -> {
                graphics.fill(x + 4, y + 4, x + 16, y + 6, color);
                graphics.fill(x + 4, y + 16, x + 16, y + 18, color);
                graphics.fill(x + 6, y + 6, x + 14, y + 9, color);
                graphics.fill(x + 8, y + 9, x + 12, y + 13, color);
                graphics.fill(x + 6, y + 13, x + 14, y + 16, color);
            }
            case SYSTEM -> {
                graphics.fill(x + 8, y + 3, x + 12, y + 18, color);
                graphics.fill(x + 3, y + 8, x + 18, y + 12, color);
                graphics.fill(x + 5, y + 5, x + 15, y + 15, color);
                graphics.fill(x + 8, y + 8, x + 12, y + 12, iconCutout);
            }
        }
    }

    private static void playPendingSound(Minecraft minecraft,
                                         List<NotificationClientState.View> notifications,
                                         long now) {
        for (NotificationClientState.View notification : notifications) {
            if (!notification.soundPending()) {
                continue;
            }
            NotificationClientState.markSoundPlayed(notification.id(), notification.revision());
            if (now - lastSoundAt < 140L) {
                continue;
            }
            lastSoundAt = now;
            float pitch = switch (notification.type()) {
                case SUCCESS, TRANSACTION -> 1.35F;
                case WARNING -> 0.92F;
                case ERROR, SECURITY -> 0.68F;
                case MESSAGE -> 1.18F;
                default -> 1.0F;
            };
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    notification.type() == ApiNotificationType.ERROR
                            || notification.type() == ApiNotificationType.SECURITY
                            ? SoundEvents.NOTE_BLOCK_BASS.value()
                            : SoundEvents.UI_BUTTON_CLICK.value(),
                    pitch,
                    notification.priority() == ApiNotificationPriority.CRITICAL ? 0.75F : 0.45F
            ));
            return;
        }
    }

    private static ApiNotificationPlacement resolvePlacement(NotificationClientState.View notification,
                                                              boolean screenOpen,
                                                              int screenWidth) {
        if (notification.placement() != ApiNotificationPlacement.AUTO) {
            return notification.placement();
        }
        if (notification.priority() == ApiNotificationPriority.CRITICAL || screenWidth < 320) {
            return ApiNotificationPlacement.TOP_CENTER;
        }
        return screenOpen ? ApiNotificationPlacement.BOTTOM_RIGHT : ApiNotificationPlacement.TOP_RIGHT;
    }

    private static int cardWidth(int screenWidth, boolean centered) {
        int preferred = centered ? Math.round(screenWidth * 0.56F) : Math.round(screenWidth * 0.38F);
        int available = Math.max(140, screenWidth - 20);
        return Math.min(available, Math.max(Math.min(CARD_MIN_WIDTH, available),
                Math.min(centered ? 390 : CARD_MAX_WIDTH, preferred)));
    }

    private static List<FormattedCharSequence> limitedLines(List<FormattedCharSequence> lines, int limit) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.size() <= limit ? List.copyOf(lines) : List.copyOf(lines.subList(0, limit));
    }

    private static String truncate(Minecraft minecraft, String value, int width) {
        String normalized = value == null ? "" : value;
        return minecraft.font.width(normalized) <= width
                ? normalized
                : minecraft.font.plainSubstrByWidth(normalized, Math.max(0, width - minecraft.font.width("..."))) + "...";
    }

    private static String label(ApiNotificationType type) {
        return switch (type) {
            case SUCCESS -> "SUCCESS";
            case ERROR -> "ERROR";
            case WARNING -> "WARNING";
            case INFO -> "INFORMATION";
            case TRANSACTION -> "TRANSACTION";
            case SECURITY -> "SECURITY";
            case MESSAGE -> "MESSAGE";
            case PROGRESS -> "IN PROGRESS";
            case SYSTEM -> "SYSTEM";
        };
    }

    private static String defaultTitle(ApiNotificationType type) {
        return switch (type) {
            case SUCCESS -> "Action complete";
            case ERROR -> "Action failed";
            case WARNING -> "Attention required";
            case INFO -> "Information";
            case TRANSACTION -> "Banking update";
            case SECURITY -> "Security notice";
            case MESSAGE -> "New message";
            case PROGRESS -> "Processing";
            case SYSTEM -> "System update";
        };
    }

    private static Palette palette(ApiNotificationType type) {
        return switch (type) {
            case SUCCESS -> new Palette(0xFF42D392);
            case ERROR -> new Palette(0xFFFF6B72);
            case WARNING -> new Palette(0xFFFFC857);
            case INFO -> new Palette(0xFF5AC8FA);
            case TRANSACTION -> new Palette(0xFFF0C75E);
            case SECURITY -> new Palette(0xFFFF668F);
            case MESSAGE -> new Palette(0xFF4D9FFF);
            case PROGRESS -> new Palette(0xFFB38CFF);
            case SYSTEM -> new Palette(0xFF8ED1C2);
        };
    }

    private static float smooth(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int alpha(int argb, float factor) {
        int base = (argb >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(base * factor)));
        return (argb & 0x00FFFFFF) | (scaled << 24);
    }

    private static int scaleRgb(int argb, float factor) {
        int red = Math.max(0, Math.min(255, Math.round(((argb >>> 16) & 0xFF) * factor)));
        int green = Math.max(0, Math.min(255, Math.round(((argb >>> 8) & 0xFF) * factor)));
        int blue = Math.max(0, Math.min(255, Math.round((argb & 0xFF) * factor)));
        return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private record Palette(int accent) {
    }

    private record CardLayout(
            int width,
            int height,
            List<FormattedCharSequence> messageLines,
            List<FormattedCharSequence> detailLines
    ) {
    }
}
