package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SmartphoneOverlay {
    private static final int BASE_PHONE_WIDTH = 320;
    private static final float PHONE_UPSCALE = 1.6F;
    private static final float PHONE_HEIGHT_SCALE = 0.29F * PHONE_UPSCALE;
    private static final int MIN_PHONE_WIDTH = Math.round(236 * PHONE_UPSCALE);
    private static final int MAX_PHONE_WIDTH = Math.round(BASE_PHONE_WIDTH * PHONE_UPSCALE);
    private static final int MIN_PHONE_HEIGHT = Math.round(260 * PHONE_UPSCALE);
    private static final int MAX_PHONE_HEIGHT = Math.round(704 * PHONE_UPSCALE);
    private static final float PHONE_ASPECT_RATIO = 2.27F;
    private static final int PHONE_BLACK = 0xFF000000;
    private static final int PHONE_EDGE_BLACK = 0xFF050505;
    private static final int PHONE_GLASS_BLACK = 0xFF020204;
    private static final int PHONE_BUTTON_RING = 0xFF151515;
    private static final int PHONE_BUTTON_FACE = 0xFF030303;
    private static final int PHONE_DISPLAY_INSET = 9;
    private static final int PHONE_CONTENT_INSET = 25;
    private static final int BANK_DISPLAY_BLEED = PHONE_CONTENT_INSET - PHONE_DISPLAY_INSET;
    private static final int PHONE_APP_TOP_BLEED = 45;
    private static final int CHAT_COMPOSER_MAX_LINES = 4;
    private static final int CHAT_COMPOSER_WRAP_MAX_LINES = 64;
    private static final int CHAT_MESSAGE_MAX_LINES = 10;
    private static final int MAX_GRADIENT_FILL_BANDS = 96;
    private static final int BANK_BG = 0xFF161622;
    private static final int BANK_PANEL = 0xFF27273A;
    private static final int BANK_PANEL_SOFT = 0xFF1E1E2D;
    private static final int BANK_PANEL_DEEP = 0xFF151B33;
    private static final int BANK_LINE = 0xFF2B2A3E;
    private static final int BANK_TEXT = 0xFFF7F6FF;
    private static final int BANK_MUTED = 0xFF9693A5;
    private static final int BANK_BLUE = 0xFF006BFF;
    private static final int BANK_INPUT = 0xFF1E1E2D;
    private static final int BANK_INPUT_ACTIVE = 0xFF22274B;
    private static final int BANK_DANGER = 0xFFFF3F60;
    private static final int BANK_LIGHT_BG = 0xFFF6F7FB;
    private static final int BANK_LIGHT_PANEL = 0xFFFFFFFF;
    private static final int BANK_LIGHT_PANEL_SOFT = 0xFFEDEFF7;
    private static final int BANK_LIGHT_PANEL_DEEP = 0xFFE3E8F5;
    private static final int BANK_LIGHT_LINE = 0xFFD9DEEA;
    private static final int BANK_LIGHT_TEXT = 0xFF151926;
    private static final int BANK_LIGHT_MUTED = 0xFF687085;
    private static final int BANK_LIGHT_INPUT = 0xFFF1F4FA;
    private static final int BANK_LIGHT_INPUT_ACTIVE = 0xFFE8F0FF;
    private static final ResourceLocation CARD_CHIP_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/card_chip_dark.png");
    private static final int CARD_CHIP_TEXTURE_WIDTH = 116;
    private static final int CARD_CHIP_TEXTURE_HEIGHT = 100;
    private static final ResourceLocation BOTTOM_NAV_HOME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/bottom_nav_home_dark.png");
    private static final ResourceLocation BOTTOM_NAV_CARDS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/bottom_nav_cards_dark.png");
    private static final ResourceLocation BOTTOM_NAV_STATS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/bottom_nav_stats_dark.png");
    private static final ResourceLocation BOTTOM_NAV_SETTINGS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/bottom_nav_settings_dark.png");
    private static final int BOTTOM_NAV_TEXTURE_WIDTH = 750;
    private static final int BOTTOM_NAV_TEXTURE_HEIGHT = 172;
    private static final ResourceLocation ACTION_SEND_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/action_send_dark.png");
    private static final ResourceLocation ACTION_RECEIVE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/action_receive_dark.png");
    private static final ResourceLocation ACTION_LOAN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/action_loan_dark.png");
    private static final ResourceLocation ACTION_TOPUP_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/action_topup_dark.png");
    private static final ResourceLocation TX_TRANSFER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/tx_transfer_dark.png");
    private static final ResourceLocation TX_CART_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/tx_cart.png");
    private static final ResourceLocation HEADER_BACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/header_back_dark.png");
    private static final ResourceLocation HEADER_CLOSE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/header_close_dark.png");
    private static final ResourceLocation HEADER_HOME_SEARCH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/header_home_search_dark.png");
    private static final ResourceLocation HEADER_SEARCH_GLYPH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/header_search_glyph.png");
    private static final int HEADER_BUTTON_TEXTURE_SIZE = 84;
    private static final int HEADER_SEARCH_GLYPH_TEXTURE_SIZE = 40;
    private static final ResourceLocation PAY_ADD_CONTACT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/pay_add_contact_dark.png");
    private static final int PAY_CONTACT_TEXTURE_SIZE = 96;
    private static final ResourceLocation PROFILE_EDIT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_edit.png");
    private static final ResourceLocation PROFILE_ARROW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_arrow.png");
    private static final ResourceLocation PROFILE_USER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_user.png");
    private static final ResourceLocation PROFILE_CARDS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_cards.png");
    private static final ResourceLocation PROFILE_CARD_EDIT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_card_edit.png");
    private static final ResourceLocation PROFILE_EMAIL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_email.png");
    private static final ResourceLocation PROFILE_PHONE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_phone.png");
    private static final ResourceLocation PROFILE_BELL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_bell.png");
    private static final ResourceLocation PROFILE_MESSAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_message.png");
    private static final ResourceLocation PROFILE_PIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_pin.png");
    private static final ResourceLocation PROFILE_SETTINGS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/profile_settings.png");
    private static final ResourceLocation SETTINGS_LOGOUT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/settings_logout_dark.png");
    private static final ResourceLocation LANGUAGE_US_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/lang_us.png");
    private static final ResourceLocation LANGUAGE_AU_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/lang_au.png");
    private static final ResourceLocation LANGUAGE_FR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/lang_fr.png");
    private static final ResourceLocation LANGUAGE_ES_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/lang_es.png");
    private static final ResourceLocation LANGUAGE_AM_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/lang_am.png");
    private static final ResourceLocation LANGUAGE_VN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "textures/gui/phone/lang_vn.png");
    private static final int PROFILE_ICON_TEXTURE_SIZE = 88;
    private static final int PROFILE_ARROW_TEXTURE_SIZE = 96;
    private static final int LANGUAGE_FLAG_TEXTURE_SIZE = 96;
    private static SmartphoneClientState.Rect lastPhoneRect;

    private record ChatMessageLayout(SmartphoneClientState.PhoneMessage message,
                                     boolean outgoing,
                                     int maxBubbleW,
                                     int bubbleH,
                                     int rowH,
                                     List<String> lines) {
    }

    private static List<SmartphoneClientState.PhoneMessage> cachedChatLayoutMessages = List.of();
    private static int cachedChatLayoutWidth = -1;
    private static List<ChatMessageLayout> cachedChatLayouts = List.of();
    private static List<SmartphoneClientState.PhoneTransaction> cachedSortedTransactionSource = List.of();
    private static List<SmartphoneClientState.PhoneTransaction> cachedSortedTransactions = List.of();
    private static List<SmartphoneClientState.PhoneTransaction> cachedHistoryFilterSource = List.of();
    private static String cachedHistoryFilterQuery = "";
    private static List<SmartphoneClientState.PhoneTransaction> cachedHistoryFilterRows = List.of();
    private static List<SmartphoneClientState.PhoneTransaction> cachedMonthFilterSource = List.of();
    private static String cachedMonthFilterKey = "";
    private static List<SmartphoneClientState.PhoneTransaction> cachedMonthFilterRows = List.of();
    private static List<SmartphoneClientState.PhoneTransaction> cachedStatsMonthKeysSource = List.of();
    private static String cachedStatsMonthKeysSelected = "";
    private static List<String> cachedStatsMonthKeys = List.of();

    private SmartphoneOverlay() {
    }

    private static boolean bankLightMode() {
        return SmartphoneClientState.bankLightMode();
    }

    private static int bankBg() {
        return bankLightMode() ? BANK_LIGHT_BG : BANK_BG;
    }

    private static int bankPanel() {
        return bankLightMode() ? BANK_LIGHT_PANEL : BANK_PANEL;
    }

    private static int bankPanelSoft() {
        return bankLightMode() ? BANK_LIGHT_PANEL_SOFT : BANK_PANEL_SOFT;
    }

    private static int bankPanelDeep() {
        return bankLightMode() ? BANK_LIGHT_PANEL_DEEP : BANK_PANEL_DEEP;
    }

    private static int bankLine() {
        return bankLightMode() ? BANK_LIGHT_LINE : BANK_LINE;
    }

    private static int bankText() {
        return bankLightMode() ? BANK_LIGHT_TEXT : BANK_TEXT;
    }

    private static int bankMuted() {
        return bankLightMode() ? BANK_LIGHT_MUTED : BANK_MUTED;
    }

    private static int bankInput() {
        return bankLightMode() ? BANK_LIGHT_INPUT : BANK_INPUT;
    }

    private static int bankInputActive() {
        return bankLightMode() ? BANK_LIGHT_INPUT_ACTIVE : BANK_INPUT_ACTIVE;
    }

    public static SmartphoneClientState.Rect lastPhoneRect() {
        return lastPhoneRect;
    }

    public static void render(Minecraft mc, GuiGraphics graphics) {
        if (mc == null || !SmartphoneClientState.isVisible()) {
            lastPhoneRect = null;
            SmartphoneClientState.setHitboxes(List.of());
            return;
        }
        int guiW = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());
        int sw = Math.max(1, mc.getWindow().getScreenWidth());
        int sh = Math.max(1, mc.getWindow().getScreenHeight());
        float guiScale = Math.max(sw / (float) guiW, sh / (float) guiH);
        float drawScale = 1.0F / Math.max(1.0F, guiScale);
        float t = SmartphoneClientState.animation();
        float eased = 1.0F - (float) Math.pow(1.0F - t, 3.0D);
        int margin = Mth.clamp(sh / 54, 12, 24);
        int maxPhoneW = Math.max(160, sw - (margin * 2));
        int maxPhoneH = Math.max(180, sh - (margin * 2));
        int phoneW = Math.min(Mth.clamp((int) (sh * PHONE_HEIGHT_SCALE), MIN_PHONE_WIDTH, MAX_PHONE_WIDTH), maxPhoneW);
        int phoneH = Math.min(Math.round(phoneW * PHONE_ASPECT_RATIO), Math.min(MAX_PHONE_HEIGHT, maxPhoneH));
        if (phoneH < Math.round(phoneW * 1.72F)) {
            phoneW = Math.min(phoneW, Math.max(160, Math.round(phoneH / 1.72F)));
        }
        phoneW = Math.max(Math.min(MIN_PHONE_WIDTH, maxPhoneW), Math.min(phoneW, maxPhoneW));
        phoneH = Math.max(Math.min(MIN_PHONE_HEIGHT, maxPhoneH), Math.min(phoneH, maxPhoneH));
        int targetX = sw - phoneW - margin;
        int targetY = sh - phoneH - margin;
        int startX = sw + Math.max(28, phoneW / 4);
        int startY = sh + Math.max(28, phoneH / 6);
        int x = (int) Mth.lerp(eased, startX, targetX);
        int y = (int) Mth.lerp(eased, startY, targetY);

        SmartphoneClientState.Rect physicalPhoneRect = new SmartphoneClientState.Rect(x, y, phoneW, phoneH);
        lastPhoneRect = toGuiRect(physicalPhoneRect, drawScale);

        List<SmartphoneClientState.Hitbox> physicalHitboxes = new ArrayList<>();
        graphics.pose().pushPose();
        graphics.pose().scale(drawScale, drawScale, 1.0F);
        drawPhoneShell(mc, graphics, x, y, phoneW, phoneH, physicalHitboxes);
        graphics.pose().popPose();
        SmartphoneClientState.setHitboxes(toGuiHitboxes(physicalHitboxes, drawScale));
    }

    private static List<SmartphoneClientState.Hitbox> toGuiHitboxes(List<SmartphoneClientState.Hitbox> physicalHitboxes,
                                                                    float drawScale) {
        List<SmartphoneClientState.Hitbox> scaled = new ArrayList<>();
        if (physicalHitboxes == null) {
            return scaled;
        }
        for (SmartphoneClientState.Hitbox hitbox : physicalHitboxes) {
            if (hitbox == null || hitbox.rect() == null) {
                continue;
            }
            scaled.add(new SmartphoneClientState.Hitbox(
                    toGuiRect(hitbox.rect(), drawScale),
                    hitbox.action(),
                    hitbox.p1(),
                    hitbox.p2(),
                    hitbox.p3()
            ));
        }
        return scaled;
    }

    private static SmartphoneClientState.Rect toGuiRect(SmartphoneClientState.Rect physicalRect, float drawScale) {
        int x = (int) Math.floor(physicalRect.x() * drawScale);
        int y = (int) Math.floor(physicalRect.y() * drawScale);
        int right = (int) Math.ceil((physicalRect.x() + physicalRect.w()) * drawScale);
        int bottom = (int) Math.ceil((physicalRect.y() + physicalRect.h()) * drawScale);
        return new SmartphoneClientState.Rect(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private static void drawPhoneShell(Minecraft mc, GuiGraphics g, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        float uiScale = Mth.clamp(w / (float) BASE_PHONE_WIDTH, 1.0F, PHONE_UPSCALE);
        int logicalW = Math.max(1, Math.round(w / uiScale));
        int logicalH = Math.max(1, Math.round(h / uiScale));

        List<SmartphoneClientState.Hitbox> logicalHitboxes = new ArrayList<>();
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(uiScale, uiScale, 1.0F);
        drawPhoneShellLogical(mc, g, 0, 0, logicalW, logicalH, logicalHitboxes);
        g.pose().popPose();
        for (SmartphoneClientState.Hitbox hitbox : logicalHitboxes) {
            if (hitbox == null || hitbox.rect() == null) {
                continue;
            }
            SmartphoneClientState.Rect rect = hitbox.rect();
            int left = x + (int) Math.floor(rect.x() * uiScale);
            int top = y + (int) Math.floor(rect.y() * uiScale);
            int right = x + (int) Math.ceil((rect.x() + rect.w()) * uiScale);
            int bottom = y + (int) Math.ceil((rect.y() + rect.h()) * uiScale);
            hitboxes.add(new SmartphoneClientState.Hitbox(
                    new SmartphoneClientState.Rect(left, top, Math.max(1, right - left), Math.max(1, bottom - top)),
                    hitbox.action(),
                    hitbox.p1(),
                    hitbox.p2(),
                    hitbox.p3()
            ));
        }
    }

    private static void drawPhoneShellLogical(Minecraft mc, GuiGraphics g, int x, int y, int w, int h,
                                              List<SmartphoneClientState.Hitbox> hitboxes) {
        Font font = mc.font;
        int r = Mth.clamp(Math.round(w * 0.085F), 24, 34);
        int edgeR = Math.max(1, r - 4);
        int glassR = Math.max(1, r - 8);
        int displayR = Math.max(16, r - 10);
        int bottomBezelH = 54;
        fillRounded(g, x, y, w, h, r, PHONE_BLACK);
        fillRounded(g, x + 3, y + 3, w - 6, h - 6, edgeR, PHONE_EDGE_BLACK);
        fillRounded(g, x + 6, y + 6, w - 12, h - 12, glassR, PHONE_GLASS_BLACK);
        drawPhoneWallpaper(g, x + PHONE_DISPLAY_INSET, y + PHONE_DISPLAY_INSET,
                w - PHONE_DISPLAY_INSET * 2, h - PHONE_DISPLAY_INSET * 2 - bottomBezelH);
        fillRounded(g, x + PHONE_DISPLAY_INSET, y + h - bottomBezelH - PHONE_DISPLAY_INSET,
                w - PHONE_DISPLAY_INSET * 2, bottomBezelH, displayR, PHONE_BLACK);
        g.fill(x + 16, y + h - bottomBezelH - 9, x + w - 16, y + h - bottomBezelH - 8, 0xFF101010);
        drawClassicTopHardware(g, x, y, w);
        int homeCenterX = x + w / 2;
        int homeCenterY = y + h - bottomBezelH / 2 - 3;
        drawHardwareHomeButton(g, homeCenterX, homeCenterY);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(homeCenterX - 18, homeCenterY - 18, 36, 36),
                "HOME", "", "", ""));

        int sx = x + PHONE_CONTENT_INSET;
        int sy = y + 30;
        int sw = w - PHONE_CONTENT_INSET * 2;
        int displayX = x + PHONE_DISPLAY_INSET;
        int displayY = y + PHONE_DISPLAY_INSET;
        int displayW = w - PHONE_DISPLAY_INSET * 2;
        int displayH = Math.max(1, h - PHONE_DISPLAY_INSET * 2 - bottomBezelH);
        int screenBottom = displayY + displayH;
        boolean bankingSurface = SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANKING
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.STATISTICS
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.HISTORY
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.SEARCH
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.SEND_MONEY
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.REQUEST_MONEY
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_PAY_REQUESTS
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_SETTINGS
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_STAFF
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_DISSOLVE
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_WELCOME
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_PROFILE
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_EDIT_PROFILE
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_SIGN_IN
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_LANGUAGE
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_CHANGE_PASSWORD
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.BANK_TERMS
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.ALL_CARDS
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.ADD_CARD
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.ACCOUNT
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.TRANSACTION_DETAIL
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.TAP;
        boolean utilitySurface = SmartphoneClientState.activeApp() == SmartphoneClientState.App.HOME
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.CALCULATOR
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.SPOT_MARKET
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.PAINT
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.CONTACTS
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.NOTES
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.SETTINGS;
        if (!bankingSurface && !utilitySurface) {
            drawStatusBar(g, font, sx, y + 16, sw);
            g.drawString(font, "UBS Phone", sx, sy, 0xFFEAF8FF, false);
            String mode = SmartphoneClientState.mode() == SmartphoneClientState.Mode.PASSIVE ? "Press P" : "Live";
            g.drawString(font, mode, x + w - 18 - font.width(mode), sy, 0xFFEAF8FF, false);
        }

        int contentY = bankingSurface ? sy + 24 : (utilitySurface ? sy + 22 : sy + 18);
        int contentH = Math.max(60, screenBottom - contentY - 8);
        // The foreground bezel masks spillover; a top-level scissor here can clip all content under scaled phone poses.
        try {
            if (bankingSurface) {
                fillRounded(g, displayX, displayY, displayW, displayH, 13, bankBg());
            }
            if (bankingSurface && SmartphoneClientState.bankingLoadingActive()) {
                drawBankingLoading(g, font, sx, contentY, sw, contentH);
                return;
            }
            if (SmartphoneClientState.phoneLocked()) {
                drawLockScreen(g, font, displayX, displayY, displayW, displayH, hitboxes);
                return;
            }
            switch (SmartphoneClientState.activeApp()) {
                case HOME -> drawHome(g, font, displayX + 10, displayY + 43,
                        Math.max(1, displayW - 20), Math.max(60, screenBottom - (displayY + 43) - 8), hitboxes);
                case BANKING -> drawBanking(g, font, sx, contentY, sw, contentH, hitboxes);
                case STATISTICS -> drawStatistics(g, font, sx, contentY, sw, contentH, hitboxes);
                case HISTORY -> drawHistory(g, font, sx, contentY, sw, contentH, hitboxes);
                case SEARCH -> drawSearch(g, font, sx, contentY, sw, contentH, hitboxes);
                case SEND_MONEY -> drawSendMoney(g, font, sx, contentY, sw, contentH, hitboxes);
                case REQUEST_MONEY -> drawRequestMoney(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_PAY_REQUESTS -> drawBankPayRequests(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_SETTINGS -> drawBankSettings(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_STAFF -> drawBankStaff(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_DISSOLVE -> drawBankDissolve(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_WELCOME -> drawBankWelcome(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_PROFILE -> drawBankProfile(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_EDIT_PROFILE -> drawBankEditProfile(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_SIGN_IN -> drawBankSignIn(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_LANGUAGE -> drawBankLanguage(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_CHANGE_PASSWORD -> drawBankChangePassword(g, font, sx, contentY, sw, contentH, hitboxes);
                case BANK_TERMS -> drawBankTerms(g, font, sx, contentY, sw, contentH, hitboxes);
                case ALL_CARDS -> drawAllCards(g, font, sx, contentY, sw, contentH, hitboxes);
                case ADD_CARD -> drawAddCard(g, font, sx, contentY, sw, contentH, hitboxes);
                case ACCOUNT -> drawAccount(g, font, sx, contentY, sw, contentH, hitboxes);
                case TRANSACTION_DETAIL -> drawTransactionDetail(g, font, sx, contentY, sw, contentH, hitboxes);
                case TAP -> drawTap(g, font, sx, contentY, sw, contentH, hitboxes);
                case SPOT_MARKET -> drawSpotMarket(g, font, sx, contentY, sw, contentH, hitboxes);
                case CALCULATOR -> drawCalculator(g, font, sx, contentY, sw, contentH, hitboxes);
                case PAINT -> drawPaint(g, font, sx, contentY, sw, contentH, hitboxes);
                case CONTACTS -> drawContacts(g, font, sx, contentY, sw, contentH, hitboxes);
                case MESSENGER -> drawMessenger(g, font, sx, contentY, sw, contentH, hitboxes);
                case NOTES -> drawNotes(g, font, sx, contentY, sw, contentH, hitboxes);
                case SETTINGS -> drawSettings(g, font, sx, contentY, sw, contentH, hitboxes);
            }

        } finally {
            drawPhoneNotification(g, font, displayX, displayY, displayW);
            maskRoundedRectCorners(g, displayX, displayY, displayW, displayH, displayR, PHONE_GLASS_BLACK);
            drawPhoneForegroundBezel(g, x, y, w, h, bottomBezelH, displayX, displayY, displayW, displayH, displayR);
        }
    }

    private static void drawPhoneForegroundBezel(GuiGraphics g, int x, int y, int w, int h, int bottomBezelH,
                                                 int displayX, int displayY, int displayW, int displayH,
                                                 int displayR) {
        g.fill(x, y, x + w, displayY, PHONE_BLACK);
        g.fill(x, displayY, displayX, displayY + displayH, PHONE_BLACK);
        g.fill(displayX + displayW, displayY, x + w, displayY + displayH, PHONE_BLACK);
        g.fill(x, displayY + displayH, x + w, y + h, PHONE_BLACK);
        maskRoundedRectCorners(g, displayX, displayY, displayW, displayH, displayR, PHONE_GLASS_BLACK);
        drawClassicTopHardware(g, x, y, w);
        g.fill(x + 16, y + h - bottomBezelH - 9, x + w - 16, y + h - bottomBezelH - 8, 0xFF101010);
        drawHardwareHomeButton(g, x + w / 2, y + h - bottomBezelH / 2 - 3);
    }

    private static int activeCaretColor(int color) {
        return ((System.currentTimeMillis() / 450L) & 1L) == 0L
                ? color
                : (0x66000000 | (color & 0x00FFFFFF));
    }

    private static void drawPhoneNotification(GuiGraphics g, Font font, int displayX, int displayY, int displayW) {
        String status = SmartphoneClientState.statusNotificationMessage();
        float progress = SmartphoneClientState.statusNotificationProgress();
        if (status == null || status.isBlank() || progress <= 0.01F) {
            return;
        }
        int margin = Math.max(12, Math.round(displayW * 0.04F));
        int bannerW = Math.max(1, displayW - margin * 2);
        int bannerH = 44;
        int hiddenY = displayY - bannerH - 10;
        int targetY = displayY + 30;
        int bannerY = Math.round(hiddenY + (targetY - hiddenY) * progress);
        int alpha = Mth.clamp(Math.round(242.0F * Math.min(1.0F, progress * 1.35F)), 0, 242);
        int bg = withAlpha(0xFFF2F4F8, alpha);
        int shadow = withAlpha(0xFF000000, Math.round(54.0F * progress));
        fillRounded(g, displayX + margin, bannerY + 2, bannerW, bannerH, 16, shadow);
        fillRounded(g, displayX + margin, bannerY, bannerW, bannerH, 16, bg);
        drawRoundedBorder(g, displayX + margin, bannerY, bannerW, bannerH, 16,
                withAlpha(0xFFFFFFFF, Math.round(130.0F * progress)), bg);

        int iconSize = 26;
        int iconX = displayX + margin + 10;
        int iconY = bannerY + 9;
        fillRounded(g, iconX, iconY, iconSize, iconSize, 8, SmartphoneClientState.accentColor());
        drawPhoneText(g, "$", iconX + 9, iconY + 6, 0xFFFFFFFF, 8.5F);

        int textX = iconX + iconSize + 9;
        int textW = Math.max(1, bannerW - (textX - (displayX + margin)) - 10);
        drawPhoneText(g, SmartphoneClientState.statusNotificationTitle(), textX, bannerY + 8, 0xFF1B1C22, 8.5F);
        drawPhoneText(g, trim(font, status, textW), textX, bannerY + 23, 0xFF4D5260, 7.8F);
    }

    private static void drawPhoneWallpaper(GuiGraphics g, int x, int y, int w, int h) {
        int top = SmartphoneClientState.wallpaperTop();
        int bottom = SmartphoneClientState.wallpaperBottom();
        fillVertical(g, x, y, w, h, top, bottom);
        String wallpaper = SmartphoneClientState.wallpaperName().toLowerCase(Locale.ROOT);
        int warm = switch (wallpaper) {
            case "forest" -> 0x6636D08A;
            case "dusk" -> 0x77FF9C5C;
            case "mono" -> 0x66FFCA5C;
            default -> 0x6652D7FF;
        };
        int cool = switch (wallpaper) {
            case "forest" -> 0x552A7E5F;
            case "dusk" -> 0x667C54FF;
            case "mono" -> 0x553B4657;
            default -> 0x66006BFF;
        };
        int bright = switch (wallpaper) {
            case "forest" -> 0x5534FFB5;
            case "dusk" -> 0x55FFD36A;
            case "mono" -> 0x44FFD36A;
            default -> 0x55FF7AD9;
        };
        fillRounded(g, x + Math.round(w * 0.03F), y + h / 12,
                Math.max(1, Math.round(w * 0.94F)), Math.max(34, h / 4), Math.max(22, w / 5), warm);
        fillRounded(g, x + Math.round(w * 0.22F), y + h / 4,
                Math.max(1, Math.round(w * 0.72F)), Math.max(42, h / 3), Math.max(24, w / 4), cool);
        fillRounded(g, x + Math.round(w * 0.08F), y + h / 2,
                Math.max(1, Math.round(w * 0.84F)), Math.max(48, h / 3), Math.max(28, w / 4), bright);
        g.fill(x, y, x + w, y + Math.max(1, h / 5), 0x22000000);
        g.fill(x, y + h - Math.max(1, h / 4), x + w, y + h, 0x18000000);
    }

    private static void drawLockScreen(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        drawStatusBar(g, font, x + 12, y + 14, Math.max(1, w - 24), 0xFFF7FBFF, 0xFF173052);
        boolean setup = !SmartphoneClientState.phonePasscodeSet();
        boolean panelOpen = SmartphoneClientState.lockPanelOpen();
        float panelT = SmartphoneClientState.lockPanelAnimation();
        int centerX = x + w / 2;
        int clockY = y + Math.max(58, Math.round(h * 0.13F));
        if (!panelOpen || panelT < 0.92F) {
            String localTime = localClockText();
            String serverTime = serverClockText();
            drawPhoneText(g, localTime, centerX - phoneTextWidth(localTime, 42.0F) / 2,
                    clockY, 0xFFF7FBFF, 42.0F);
            drawScaledCentered(g, font, "Local " + localTime + "  |  Server " + serverTime,
                    centerX, clockY + 49, 0xCFF7FBFF, 0.72F);
            drawScaledCentered(g, font, setup ? "Set a phone passcode" : "Phone locked",
                    centerX, clockY + 67, 0xDDF7FBFF, 0.92F);
        }

        int hintY = y + h - 38;
        if (!panelOpen) {
            drawScaledCentered(g, font, setup ? "Swipe up to begin" : "Swipe up to unlock",
                    centerX, hintY - 18, 0xEAF7FBFF, 0.82F);
        }
        drawLockHomeIndicator(g, centerX, hintY, panelOpen ? 0x66FFFFFF : 0xDDFFFFFF);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y + Math.max(1, h / 2), w, h / 2),
                "LOCK_SWIPE", "", "", ""));

        if (panelT <= 0.01F) {
            return;
        }
        int sheetMin = Math.min(260, Math.max(150, h - 72));
        int sheetMax = Math.max(sheetMin, h - 42);
        int sheetH = Mth.clamp(Math.round(h * 0.62F), sheetMin, sheetMax);
        int panelY = y + h - Math.round(sheetH * panelT);
        fillRounded(g, x + 8, panelY, w - 16, sheetH + 22, 24, 0xF00F1221);
        drawRoundedBorder(g, x + 8, panelY, w - 16, sheetH + 22, 24, 0x22FFFFFF, 0xF00F1221);
        drawScaledCentered(g, font, setup
                        ? (SmartphoneClientState.phonePasscodeConfirmStep() ? "Verify Passcode" : "Create Passcode")
                        : "Enter Passcode",
                centerX, panelY + 25, 0xFFF7FBFF, 1.08F);
        String error = SmartphoneClientState.phonePasscodeError();
        String subtitle = error.isBlank()
                ? (setup ? "Use 4 digits for this phone." : "Enter your 4 digit passcode.")
                : error;
        drawScaledCentered(g, font, trim(font, subtitle, w - 42), centerX, panelY + 47,
                error.isBlank() ? 0xBCE8EDF7 : 0xFFFF8A8A, 0.72F);

        String draft = SmartphoneClientState.phonePasscodeDraft();
        int dotGap = 20;
        int dotsW = dotGap * 3 + 48;
        int dotsX = centerX - dotsW / 2;
        int dotsY = panelY + 82;
        for (int i = 0; i < 4; i++) {
            boolean filled = i < draft.length();
            fillCircle(g, dotsX + i * dotGap + 6, dotsY, filled ? 6 : 5,
                    filled ? 0xFFF7FBFF : 0x55F7FBFF);
            if (!filled) {
                fillCircle(g, dotsX + i * dotGap + 6, dotsY, 3, 0xF00F1221);
            }
        }

        int keypadTop = panelY + 112;
        int availableKeyH = Math.max(90, y + h - 24 - keypadTop);
        int gapX = Mth.clamp(Math.round(w * 0.065F), 12, 22);
        int gapY = Mth.clamp(Math.round(availableKeyH * 0.035F), 7, 13);
        int key = Math.min((w - gapX * 4) / 3, (availableKeyH - gapY * 3) / 4);
        key = Mth.clamp(key, 34, 54);
        int startX = centerX - (key * 3 + gapX * 2) / 2;
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "Cancel", "0", "Del"};
        for (int i = 0; i < labels.length; i++) {
            int row = i / 3;
            int col = i % 3;
            int keyX = startX + col * (key + gapX);
            int keyY = keypadTop + row * (key + gapY);
            String label = labels[i];
            String action = switch (label) {
                case "Cancel" -> "LOCK_CANCEL";
                case "Del" -> "PHONE_PASSCODE_DELETE";
                default -> "PHONE_PASSCODE_DIGIT";
            };
            String value = label.length() == 1 && Character.isDigit(label.charAt(0)) ? label : "";
            drawLockKey(g, font, keyX, keyY, key, label, action, value, hitboxes);
        }
    }

    private static void drawLockHomeIndicator(GuiGraphics g, int centerX, int y, int color) {
        fillRounded(g, centerX - 42, y, 84, 4, 2, color);
    }

    private static void drawLockKey(GuiGraphics g, Font font, int x, int y, int size, String label,
                                    String action, String value, List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean command = label.length() > 1;
        if (!command) {
            fillCircle(g, x + size / 2, y + size / 2, size / 2, 0x33FFFFFF);
            drawScaledCentered(g, font, label, x + size / 2, y + Math.round(size * 0.30F), 0xFFF7FBFF, 1.24F);
        } else {
            drawScaledCentered(g, font, label, x + size / 2, y + Math.round(size * 0.36F), 0xEAF7FBFF, 0.78F);
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, size, size),
                action, value, "", ""));
    }

    private static void drawHome(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 List<SmartphoneClientState.Hitbox> hitboxes) {
        drawStatusBar(g, font, x, y - 31, w);
        List<SmartphoneClientState.PhoneApp> apps = SmartphoneClientState.apps();
        boolean compact = h < 300;
        boolean micro = h < 150;

        int sideInset = Mth.clamp(Math.round(w * 0.024F), micro ? 2 : 4,
                micro ? 6 : (compact ? 9 : 12));
        int safeX = x + sideInset;
        int safeW = Math.max(1, w - sideInset * 2);
        int minColumnW = micro ? 24 : (compact ? 34 : 46);
        int cols = Mth.clamp(Math.max(1, safeW / minColumnW), 1, 4);
        int gridAvailableW = Math.max(cols * minColumnW, safeW);
        int columnW = Math.max(minColumnW, gridAvailableW / cols);
        int gridW = Math.min(safeW, columnW * cols);
        int startX = safeX + (safeW - gridW) / 2;
        int icon = Mth.clamp(Math.round(w * (micro ? 0.105F : 0.135F)), micro ? 16 : (compact ? 28 : 38),
                micro ? 28 : (compact ? 40 : 48));
        int labelGap = micro ? 2 : (compact ? 4 : 6);
        int labelLines = micro ? 1 : 2;
        int labelLineH = micro ? 8 : (compact ? 10 : 12);
        int labelH = labelLineH * labelLines;
        int rowH = Math.max(icon + labelGap + labelH + (micro ? 2 : (compact ? 6 : 11)),
                Math.round(h * (compact ? 0.088F : 0.098F)));
        int topPad = Mth.clamp(Math.round(h * (micro ? 0.040F : (compact ? 0.095F : 0.105F))),
                micro ? 6 : (compact ? 28 : 42), micro ? 18 : (compact ? 42 : 58));
        int dockCount = Math.min(Math.min(4, cols), apps.size());
        int dockH = Mth.clamp(icon + (micro ? 8 : (compact ? 14 : 22)), micro ? 26 : (compact ? 46 : 58),
                micro ? 38 : (compact ? 56 : 68));
        int dockY = y + h - dockH - (micro ? 3 : (compact ? 6 : 10));
        int dotY = dockY - (micro ? 5 : (compact ? 9 : 14));
        int gridBottom = dotY - (micro ? 4 : (compact ? 10 : 16));
        int gridApps = Math.max(0, apps.size() - dockCount);
        int gridH = Math.max(0, gridBottom - (y + topPad));
        int minGridRow = icon + labelGap + labelH;
        if (compact && gridApps > 0 && gridH < minGridRow) {
            dockCount = 0;
            dockH = 0;
            dockY = y + h;
            dotY = y + h - 8;
            gridBottom = y + h - 8;
            topPad = Mth.clamp(Math.round(h * (micro ? 0.025F : 0.055F)), micro ? 4 : 18, micro ? 12 : 30);
            gridApps = apps.size();
            gridH = Math.max(0, gridBottom - (y + topPad));
        }
        if (compact && gridApps > 0 && gridH > 0 && gridH < minGridRow) {
            icon = Math.max(8, Math.min(icon, gridH - labelGap - labelH));
            rowH = Math.max(1, gridH);
            minGridRow = Math.max(1, Math.min(gridH, icon + labelGap + labelH));
        }
        int visibleRows = gridH >= minGridRow ? Math.max(1, gridH / rowH) : 0;
        int pageSize = Math.max(1, visibleRows * cols);
        int totalPages = Math.max(1, (gridApps + pageSize - 1) / pageSize);
        SmartphoneClientState.syncHomeGridScroll(totalPages, 1);
        int page = SmartphoneClientState.homeGridScrollOffset();
        int startIndex = dockCount + page * pageSize;
        int itemsOnPage = Math.min(pageSize, Math.max(0, apps.size() - startIndex));
        int rowsOnPage = itemsOnPage <= 0 ? 0 : (itemsOnPage + cols - 1) / cols;
        int usedRows = Math.max(0, Math.min(visibleRows, rowsOnPage));
        int visualGridTop = y + topPad;
        if (usedRows > 0) {
            int freeY = Math.max(0, gridH - usedRows * rowH);
            visualGridTop += Math.min(micro ? 4 : (compact ? 10 : 18), freeY / 2);
        }
        for (int i = 0; i < pageSize && startIndex + i < apps.size(); i++) {
            SmartphoneClientState.PhoneApp app = apps.get(startIndex + i);
            int columnX = startX + (i % cols) * columnW;
            int cx = columnX + (columnW - icon) / 2;
            int cy = visualGridTop + (i / cols) * rowH;
            int labelAllowance = labelGap + labelH + (micro ? 1 : 3);
            if (cy + icon + labelAllowance > gridBottom) {
                break;
            }
            int color = appColor(app.id());
            drawHomeAppIcon(g, app.id(), cx, cy, icon, color);
            int labelX = columnX + columnW / 2;
            int labelY = cy + icon + labelGap;
            float labelScale = compact ? 0.66F : 0.72F;
            drawHomeAppLabel(g, font, app.label(), labelX, labelY, columnW - 4, labelScale, labelLines);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(columnX, cy - 2, columnW,
                    icon + labelAllowance + 4), "APP", app.id(), "", ""));
        }
        if (visibleRows > 0 && gridApps > 0) {
            drawHomePageDots(g, x + w / 2, dotY, totalPages, page, hitboxes);
        }
        if (dockCount > 0) {
            drawHomeDock(g, safeX, dockY, safeW, dockH, apps, dockCount, icon, compact, hitboxes);
        }
    }

    private static void drawHomeAppLabel(GuiGraphics g, Font font, String label, int centerX, int y,
                                         int maxW, float scale, int maxLines) {
        String safe = label == null ? "" : label.trim();
        if (safe.isBlank() || maxW <= 0 || maxLines <= 0) {
            return;
        }
        float textSize = Math.max(5.0F, font.lineHeight * scale);
        List<String> wrapped = new ArrayList<>(wrapPhoneText(font, safe, maxW, textSize, maxLines + 1));
        if (wrapped.size() > maxLines) {
            wrapped = new ArrayList<>(wrapped.subList(0, maxLines));
            int last = wrapped.size() - 1;
            wrapped.set(last, ellipsizePhoneText(wrapped.get(last), maxW, textSize));
        } else if (wrapped.size() == 1) {
            wrapped.set(0, trimPhoneText(wrapped.get(0), maxW, textSize));
        }
        int lineStep = Math.max(7, Math.round(font.lineHeight * scale + 2.0F));
        for (int i = 0; i < wrapped.size(); i++) {
            String line = wrapped.get(i);
            int lineY = y + i * lineStep;
            drawScaledCentered(g, font, line, centerX + 1, lineY + 1, 0x88000000, scale);
            drawScaledCentered(g, font, line, centerX, lineY, 0xFFEAF8FF, scale);
        }
    }

    private static String ellipsizePhoneText(String raw, int width, float size) {
        String value = raw == null ? "" : raw.trim();
        String ellipsis = "...";
        while (!value.isEmpty() && phoneTextWidth(value + ellipsis, size) > width) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value.isBlank() ? ellipsis : value + ellipsis;
    }

    private static void drawHomeDock(GuiGraphics g, int x, int y, int w, int h,
                                     List<SmartphoneClientState.PhoneApp> apps, int count, int icon,
                                     boolean compact,
                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        if (count <= 0) {
            return;
        }
        int inset = compact ? 5 : 7;
        fillRounded(g, x + inset, y, w - inset * 2, h, Math.max(18, h / 3), 0x44FFFFFF);
        drawRoundedBorder(g, x + inset, y, w - inset * 2, h, Math.max(18, h / 3), 0x33FFFFFF, 0x22FFFFFF);
        int dockInnerW = Math.max(1, w - inset * 2);
        int slotW = Math.max(32, dockInnerW / Math.max(1, count));
        int dockStartX = x + inset + Math.max(0, (dockInnerW - slotW * count) / 2);
        int iconY = y + Math.max(5, (h - icon) / 2);
        for (int i = 0; i < count; i++) {
            SmartphoneClientState.PhoneApp app = apps.get(i);
            int slotX = dockStartX + i * slotW;
            int iconX = slotX + (slotW - icon) / 2;
            drawHomeAppIcon(g, app.id(), iconX, iconY, icon, appColor(app.id()));
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(slotX, y, slotW, h),
                    "APP", app.id(), "", ""));
        }
    }

    private static void drawHomePageDots(GuiGraphics g, int centerX, int y, int totalRows, int activeRow,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        if (totalRows <= 1) {
            return;
        }
        int visibleDots = Math.min(totalRows, 5);
        int gap = 10;
        int startX = centerX - ((visibleDots - 1) * gap) / 2;
        int active = Mth.clamp(activeRow, 0, Math.max(0, totalRows - 1));
        int first = Mth.clamp(active - visibleDots / 2, 0, Math.max(0, totalRows - visibleDots));
        for (int i = 0; i < visibleDots; i++) {
            int row = first + i;
            int dotX = startX + i * gap;
            fillCircle(g, dotX, y, row == active ? 3 : 2,
                    row == active ? 0xCCFFFFFF : 0x66FFFFFF);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(dotX - 5, y - 7, 10, 14),
                    "HOME_PAGE", String.valueOf(row), "", ""));
        }
    }

    private static void drawHomeAppIcon(GuiGraphics g, String appId, int x, int y, int size, int color) {
        int maxRadius = Math.max(1, size / 2);
        int minRadius = Math.min(maxRadius, Math.max(2, size / 5));
        int radius = Mth.clamp(Math.round(size * 0.22F), minRadius, Math.min(12, maxRadius));
        fillRoundedVertical(g, x, y, size, size, radius,
                lerpColor(color, 0xFFFFFFFF, 0.10F),
                lerpColor(color, 0xFF000000, 0.14F));
        if (size > radius * 2 + 2) {
            g.fill(x + radius, y + 2, x + size - radius, y + 3, 0x33FFFFFF);
        }
        drawHomeAppSymbol(g, appId, x, y, size, color);
    }

    private static void drawHomeAppSymbol(GuiGraphics g, String appId, int x, int y, int size, int tileColor) {
        String id = appId == null ? "" : appId.toLowerCase(Locale.ROOT);
        if (id.startsWith("account:")) {
            drawAccountShortcutSymbol(g, x, y, size, tileColor);
            return;
        }

        int cx = x + size / 2;
        int cy = y + size / 2;
        int white = 0xFFF8FBFF;
        int soft = 0xCCF8FBFF;
        int dark = 0x66000000;
        int one = Math.max(1, Math.round(size / 42.0F));
        int two = Math.max(2, Math.round(size / 20.0F));
        int three = Math.max(2, Math.round(size / 16.0F));
        int four = Math.max(3, Math.round(size / 12.0F));
        int five = Math.max(4, Math.round(size / 9.0F));
        int six = Math.max(5, Math.round(size / 7.0F));
        int eight = Math.max(7, Math.round(size / 5.2F));
        int nine = Math.max(8, Math.round(size / 4.7F));
        switch (id) {
            case "banking" -> drawBankingHomeSymbol(g, cx, cy, size, tileColor);
            case "market" -> {
                fillCircle(g, cx - Math.round(size * 0.18F), cy + Math.round(size * 0.16F),
                        Math.max(5, Math.round(size * 0.14F)), 0xFFFFD66B);
                fillCircle(g, cx + Math.round(size * 0.18F), cy + Math.round(size * 0.10F),
                        Math.max(5, Math.round(size * 0.14F)), 0xFFFFB13D);
                int chartLeft = cx - Math.round(size * 0.30F);
                int chartBottom = cy + Math.round(size * 0.18F);
                drawSoftLine(g, chartLeft, chartBottom, chartLeft + Math.round(size * 0.17F),
                        chartBottom - Math.round(size * 0.18F), white);
                drawSoftLine(g, chartLeft + Math.round(size * 0.17F), chartBottom - Math.round(size * 0.18F),
                        chartLeft + Math.round(size * 0.34F), chartBottom - Math.round(size * 0.08F), white);
                drawSoftLine(g, chartLeft + Math.round(size * 0.34F), chartBottom - Math.round(size * 0.08F),
                        chartLeft + Math.round(size * 0.60F), chartBottom - Math.round(size * 0.31F), white);
                g.fill(cx - one, cy - Math.round(size * 0.30F), cx + one + 1, cy + Math.round(size * 0.28F), soft);
                g.fill(cx - Math.round(size * 0.31F), cy - one, cx + Math.round(size * 0.31F), cy + one + 1, soft);
            }
            case "tap" -> {
                int phoneW = Math.round(size * 0.31F);
                int phoneH = Math.round(size * 0.49F);
                drawRoundedBorder(g, cx - phoneW / 2 - five, cy - phoneH / 2, phoneW, phoneH,
                        Math.max(4, two), white, tileColor);
                g.fill(cx - phoneW / 2 - two, cy + phoneH / 2 - four, cx - phoneW / 2 + four, cy + phoneH / 2 - three, soft);
                drawSoftLine(g, cx + three, cy - eight, cx + eight, cy - four, white);
                drawSoftLine(g, cx + eight, cy - four, cx + eight, cy + four, white);
                drawSoftLine(g, cx + three, cy + eight, cx + eight, cy + four, white);
                drawSoftLine(g, cx + eight + three, cy - six, cx + eight + six, cy - two, soft);
                drawSoftLine(g, cx + eight + six, cy - two, cx + eight + six, cy + two, soft);
                drawSoftLine(g, cx + eight + three, cy + six, cx + eight + six, cy + two, soft);
            }
            case "calculator" -> {
                int calcW = Math.round(size * 0.48F);
                int calcH = Math.round(size * 0.58F);
                fillRounded(g, cx - calcW / 2, cy - calcH / 2, calcW, calcH, four, white);
                fillRounded(g, cx - calcW / 2 + three, cy - calcH / 2 + three,
                        calcW - six, Math.max(5, Math.round(size * 0.16F)), two, 0xFF2B3444);
                int dot = Math.max(2, Math.round(size * 0.055F));
                int startX = cx - calcW / 2 + four;
                int startY = cy - calcH / 2 + Math.round(size * 0.25F);
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        fillCircle(g, startX + col * Math.max(6, Math.round(size * 0.14F)),
                                startY + row * Math.max(6, Math.round(size * 0.13F)), dot, 0xFF2B3444);
                    }
                }
            }
            case "paint" -> {
                fillCircle(g, cx - one, cy, Math.round(size * 0.25F), white);
                fillCircle(g, cx - Math.round(size * 0.12F), cy - Math.round(size * 0.10F), two, tileColor);
                fillCircle(g, cx + Math.round(size * 0.05F), cy - Math.round(size * 0.13F), two, tileColor);
                fillCircle(g, cx - Math.round(size * 0.07F), cy + Math.round(size * 0.09F), two, tileColor);
                drawSoftLine(g, cx + six, cy + six, cx + Math.round(size * 0.30F), cy + Math.round(size * 0.29F), white);
                drawSoftLine(g, cx + five, cy + eight, cx + Math.round(size * 0.20F), cy + Math.round(size * 0.30F), soft);
                fillRounded(g, cx + three, cy + two, Math.max(8, Math.round(size * 0.20F)),
                        Math.max(5, Math.round(size * 0.10F)), three, 0xFFFFD264);
            }
            case "contacts" -> {
                fillCircle(g, cx - six, cy - five, five, white);
                fillCircle(g, cx + six, cy - five, five, soft);
                fillRounded(g, cx - Math.round(size * 0.30F), cy + two, Math.round(size * 0.28F),
                        Math.round(size * 0.18F), six, white);
                fillRounded(g, cx + two, cy + two, Math.round(size * 0.28F),
                        Math.round(size * 0.18F), six, soft);
                fillCircle(g, cx, cy - two, six, white);
                fillRounded(g, cx - eight, cy + five, eight * 2, Math.round(size * 0.20F), eight, white);
            }
            case "messenger" -> {
                int bw = Math.round(size * 0.57F);
                int bh = Math.round(size * 0.42F);
                fillRounded(g, cx - bw / 2, cy - bh / 2, bw, bh, eight, white);
                g.fill(cx - six, cy + bh / 2 - one, cx - two, cy + bh / 2 + five, white);
                drawSoftLine(g, cx - eight, cy - two, cx - two, cy - two, tileColor);
                drawSoftLine(g, cx + two, cy - two, cx + eight, cy - two, tileColor);
                drawSoftLine(g, cx - five, cy + five, cx + five, cy + five, tileColor);
            }
            case "notes" -> {
                int pageW = Math.round(size * 0.48F);
                int pageH = Math.round(size * 0.58F);
                fillRounded(g, cx - pageW / 2, cy - pageH / 2, pageW, pageH, three, white);
                g.fill(cx - pageW / 2, cy - pageH / 2 + five, cx + pageW / 2, cy - pageH / 2 + six, 0xFFFFB13D);
                for (int line = 0; line < 4; line++) {
                    int ly = cy - pageH / 2 + Math.round(size * 0.20F) + line * four;
                    g.fill(cx - pageW / 2 + five, ly, cx + pageW / 2 - four, ly + one, 0xFFB99331);
                }
            }
            case "settings" -> {
                drawCircleRing(g, cx, cy, eight, three, white, tileColor);
                fillCircle(g, cx, cy, three, white);
                g.fill(cx - one, cy - eight - five, cx + one + 1, cy - eight + one, white);
                g.fill(cx - one, cy + eight - one, cx + one + 1, cy + eight + five, white);
                g.fill(cx - eight - five, cy - one, cx - eight + one, cy + one + 1, white);
                g.fill(cx + eight - one, cy - one, cx + eight + five, cy + one + 1, white);
                drawSoftLine(g, cx - eight, cy - eight, cx - five, cy - five, soft);
                drawSoftLine(g, cx + eight, cy - eight, cx + five, cy - five, soft);
                drawSoftLine(g, cx - eight, cy + eight, cx - five, cy + five, soft);
                drawSoftLine(g, cx + eight, cy + eight, cx + five, cy + five, soft);
            }
            default -> {
                fillCircle(g, cx - five, cy - five, three, white);
                fillCircle(g, cx + five, cy - five, three, white);
                fillCircle(g, cx - five, cy + five, three, white);
                fillCircle(g, cx + five, cy + five, three, white);
            }
        }
        g.fill(x + size - two - one, y + two, x + size - two, y + size - two, dark);
    }

    private static void drawBankingHomeSymbol(GuiGraphics g, int cx, int cy, int size, int tileColor) {
        int ring = Math.max(7, Math.round(size * 0.17F));
        int thickness = Math.max(2, Math.round(size * 0.055F));
        int offset = Math.max(6, Math.round(size * 0.14F));
        drawCircleRing(g, cx - offset, cy, ring, thickness, 0xFFF8FBFF, tileColor);
        drawCircleRing(g, cx + offset, cy, ring, thickness, 0xFFB9FFF2, tileColor);
        drawSoftLine(g, cx - offset + ring - thickness, cy, cx + offset - ring + thickness, cy, 0xFFF8FBFF);
        fillCircle(g, cx, cy, Math.max(2, Math.round(size * 0.055F)), 0xFFF8FBFF);
        g.fill(cx - Math.round(size * 0.25F), cy + Math.round(size * 0.25F),
                cx + Math.round(size * 0.25F), cy + Math.round(size * 0.25F) + Math.max(2, thickness), 0xCCF8FBFF);
    }

    private static void drawAccountShortcutSymbol(GuiGraphics g, int x, int y, int size, int tileColor) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int cardW = Math.round(size * 0.58F);
        int cardH = Math.round(size * 0.40F);
        fillRounded(g, cx - cardW / 2, cy - cardH / 2, cardW, cardH, Math.max(4, Math.round(size * 0.10F)), 0xFFF8FBFF);
        g.fill(cx - cardW / 2 + 4, cy - cardH / 2 + 5, cx + cardW / 2 - 4, cy - cardH / 2 + 7, tileColor);
        fillRounded(g, cx - cardW / 2 + 5, cy - 1, Math.max(8, Math.round(size * 0.18F)),
                Math.max(6, Math.round(size * 0.13F)), Math.max(2, Math.round(size * 0.04F)), 0xFFFFC44A);
        g.fill(cx - 1, cy + Math.round(size * 0.12F), cx + cardW / 2 - 5, cy + Math.round(size * 0.12F) + 1, 0xFF1D3B83);
    }

    private static void drawSpotMarket(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        int bg = 0xFF10131D;
        int panel = 0xFF1B2030;
        int panelDeep = 0xFF151927;
        int line = 0xFF2D3548;
        int text = 0xFFF8FAFF;
        int muted = 0xFFA4AAB9;
        int gold = 0xFFFFC857;
        int green = 0xFF39D98A;
        drawPhoneAppSurface(g, x, y, w, h, bg);
        fillVertical(g, x - BANK_DISPLAY_BLEED, y - PHONE_APP_TOP_BLEED,
                w + BANK_DISPLAY_BLEED * 2, Math.max(1, h / 2), 0xFF161A29, bg);
        drawStatusBar(g, font, x, y - 40, w, text, bg);

        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int headerButton = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, headerButton);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, headerButton, headerButton),
                "APP", "home", "", ""));
        int refreshSize = headerButton;
        int refreshX = x + w - refreshSize;
        fillCircle(g, refreshX + refreshSize / 2, y + refreshSize / 2, refreshSize / 2, 0xFF202638);
        drawCircleRing(g, refreshX + refreshSize / 2, y + refreshSize / 2, Math.max(8, refreshSize / 4),
                Math.max(2, refreshSize / 12), gold, 0xFF202638);
        drawScaledCentered(g, font, "R", refreshX + refreshSize / 2, y + Math.max(9, refreshSize / 2 - 5),
                gold, 0.82F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(refreshX, y, refreshSize, refreshSize),
                "MARKET_REFRESH", "", "", ""));

        drawScaledCentered(g, font, "Spot Market", x + w / 2, y + Math.round(9 * sy), text, 1.10F);
        drawScaledCentered(g, font, "Central Bank Global Desk", x + w / 2, y + Math.round(31 * sy), muted, 0.74F);

        List<SmartphoneClientState.MarketQuote> quotes = SmartphoneClientState.marketQuotes();
        SmartphoneClientState.MarketQuote hero = quotes.stream()
                .filter(SmartphoneClientState.MarketQuote::seeded)
                .findFirst()
                .orElse(quotes.isEmpty() ? null : quotes.get(0));

        int heroY = y + Math.round(70 * sy);
        int heroH = Math.max(116, Math.round(142 * sy));
        fillRoundedVertical(g, x, heroY, w, heroH, Math.max(18, Math.round(22 * sx)),
                0xFF262A3D, 0xFF171B2B);
        drawRoundedBorder(g, x, heroY, w, heroH, Math.max(18, Math.round(22 * sx)), 0x663F4965, 0xFF171B2B);
        g.fill(x + Math.round(18 * sx), heroY + Math.round(18 * sy),
                x + w - Math.round(18 * sx), heroY + Math.round(19 * sy), 0x33FFFFFF);
        int coinX = x + Math.round(28 * sx);
        int coinY = heroY + Math.round(40 * sy);
        fillCircle(g, coinX, coinY, Math.max(17, Math.round(22 * sx)), 0x44FFD36B);
        fillCircle(g, coinX, coinY, Math.max(12, Math.round(16 * sx)), gold);
        drawScaledCentered(g, font, "$", coinX, coinY - Math.round(7 * sy), 0xFF5B3A00, 1.0F);
        String title = hero == null ? "No commodities" : hero.displayName();
        String spot = hero == null ? "Unpriced" : hero.spotLabel();
        String change = hero == null ? "Seed required" : hero.changeLabel();
        drawScaledString(g, font, trim(font, title, w - Math.round(96 * sx)),
                x + Math.round(62 * sx), heroY + Math.round(26 * sy), text, 0.96F);
        drawScaledString(g, font, trim(font, spot, w - Math.round(84 * sx)),
                x + Math.round(62 * sx), heroY + Math.round(52 * sy), gold, 1.72F);
        drawScaledString(g, font, "Spot per " + (hero == null ? "unit" : hero.unitName()),
                x + Math.round(64 * sx), heroY + Math.round(84 * sy), muted, 0.78F);
        int chipW = Math.max(58, Math.round(76 * sx));
        fillRounded(g, x + w - chipW - Math.round(16 * sx), heroY + Math.round(28 * sy),
                chipW, Math.max(25, Math.round(30 * sy)), Math.max(12, Math.round(14 * sx)),
                change.startsWith("-") ? 0x33FF4D6D : 0x3339D98A);
        drawScaledCentered(g, font, change, x + w - chipW / 2 - Math.round(16 * sx),
                heroY + Math.round(37 * sy), change.startsWith("-") ? 0xFFFF6B84 : green, 0.82F);

        int statY = heroY + heroH - Math.max(44, Math.round(50 * sy));
        int statGap = Math.max(8, Math.round(10 * sx));
        int statW = (w - statGap) / 2;
        drawMarketStat(g, font, x, statY, statW, Math.max(38, Math.round(42 * sy)),
                "Bid", hero == null ? "-" : hero.bidLabel(), panelDeep, green);
        drawMarketStat(g, font, x + statW + statGap, statY, statW, Math.max(38, Math.round(42 * sy)),
                "Ask", hero == null ? "-" : hero.askLabel(), panelDeep, gold);

        int quoteHeaderY = heroY + heroH + Math.max(22, Math.round(28 * sy));
        drawScaledString(g, font, "Quotes", x + 2, quoteHeaderY, text, 1.12F);
        String updated = hero == null ? "No feed" : marketUpdatedLabel(hero.updatedAtMillis());
        drawScaledString(g, font, trim(font, updated, Math.round(w * 0.40F)),
                x + w - phoneTextWidth(trim(font, updated, Math.round(w * 0.40F)), 7.0F),
                quoteHeaderY + 4, muted, 0.78F);

        int listY = quoteHeaderY + Math.max(23, Math.round(28 * sy));
        int cardH = Math.max(84, Math.round(94 * sy));
        int gap = Math.max(8, Math.round(10 * sy));
        int visibleRows = Math.max(1, (y + h - listY) / Math.max(1, cardH + gap));
        SmartphoneClientState.syncMarketScroll(quotes.size(), visibleRows);
        int offset = SmartphoneClientState.marketScrollOffset();
        if (quotes.isEmpty()) {
            drawMarketEmpty(g, font, x, listY, w, Math.max(120, y + h - listY), panel, line, muted, gold);
            return;
        }
        for (int i = 0; i < visibleRows && offset + i < quotes.size(); i++) {
            int rowY = listY + i * (cardH + gap);
            if (rowY + cardH > y + h) {
                break;
            }
            drawMarketQuoteCard(g, font, x, rowY, w, cardH, quotes.get(offset + i), panel, line, text, muted, gold, green);
        }
    }

    private static void drawMarketStat(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       String label, String value, int bg, int accent) {
        fillRounded(g, x, y, w, h, Math.max(10, h / 4), bg);
        g.fill(x + 10, y + h - 2, x + w - 10, y + h - 1, withAlpha(accent, 110));
        drawScaledString(g, font, label, x + 12, y + 8, 0xFFA4AAB9, 0.72F);
        drawScaledString(g, font, trim(font, value, w - 24), x + 12, y + 22, 0xFFF8FAFF, 0.90F);
    }

    private static void drawMarketQuoteCard(GuiGraphics g, Font font, int x, int y, int w, int h,
                                            SmartphoneClientState.MarketQuote quote, int panel, int line,
                                            int text, int muted, int gold, int green) {
        fillRounded(g, x, y, w, h, Math.max(14, h / 6), panel);
        drawRoundedBorder(g, x, y, w, h, Math.max(14, h / 6), line, panel);
        int accent = quote.seeded() ? gold : 0xFF6C7486;
        g.fill(x, y + 12, x + 3, y + h - 12, accent);
        fillCircle(g, x + 24, y + 26, 15, withAlpha(accent, quote.seeded() ? 230 : 120));
        drawScaledCentered(g, font, quote.seeded() ? "$" : "?", x + 24, y + 19, 0xFF151927, 0.92F);
        drawScaledString(g, font, trim(font, quote.displayName(), w - 118), x + 48, y + 13, text, 0.96F);
        drawScaledString(g, font, quote.id(), x + 48, y + 31, muted, 0.64F);
        drawScaledString(g, font, trim(font, quote.spotLabel(), Math.round(w * 0.32F)),
                x + w - Math.round(w * 0.32F), y + 13, quote.seeded() ? gold : muted, 1.02F);
        String lowHigh = "L " + quote.lowLabel() + "  H " + quote.highLabel();
        drawScaledString(g, font, trim(font, lowHigh, w - 60), x + 48, y + 52, muted, 0.70F);
        String spread = "Bid " + quote.bidLabel() + " / Ask " + quote.askLabel();
        drawScaledString(g, font, trim(font, spread, w - 60), x + 48, y + 67, quote.seeded() ? green : muted, 0.68F);
    }

    private static void drawMarketEmpty(GuiGraphics g, Font font, int x, int y, int w, int h,
                                        int panel, int line, int muted, int gold) {
        fillRounded(g, x, y, w, Math.min(h, 148), 18, panel);
        drawRoundedBorder(g, x, y, w, Math.min(h, 148), 18, line, panel);
        fillCircle(g, x + w / 2, y + 42, 18, withAlpha(gold, 120));
        drawScaledCentered(g, font, "$", x + w / 2, y + 34, gold, 1.1F);
        drawScaledCentered(g, font, "No market quotes yet", x + w / 2, y + 72, 0xFFF8FAFF, 1.0F);
        drawScaledCentered(g, font, "Seed gold from Central Bank commands.", x + w / 2, y + 94, muted, 0.72F);
    }

    private static String marketUpdatedLabel(long updatedAtMillis) {
        if (updatedAtMillis <= 0L) {
            return "Awaiting seed";
        }
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - updatedAtMillis) / 1000L);
        if (ageSeconds < 60L) {
            return "Updated " + ageSeconds + "s ago";
        }
        long minutes = ageSeconds / 60L;
        if (minutes < 60L) {
            return "Updated " + minutes + "m ago";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return "Updated " + hours + "h ago";
        }
        return "Updated " + (hours / 24L) + "d ago";
    }

    private static void drawBanking(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        if (SmartphoneClientState.bankingLoadingActive()) {
            drawBankingLoading(g, font, x, y, w, h);
            return;
        }
        if (!SmartphoneClientState.hasBankAccounts()) {
            drawBankWelcome(g, font, x, y, w, h, hitboxes);
            return;
        }

        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        List<SmartphoneClientState.PhoneAccount> accounts = SmartphoneClientState.accounts();

        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int avatar = Math.max(40, Math.round(50 * sx));
        drawFigmaAvatar(g, font, x, y, avatar);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, avatar, avatar),
                "APP", "bank-profile", "", ""));
        drawScaledString(g, font, "Welcome back,", x + Math.round(66 * sx), y + Math.round(4 * sy),
                bankMuted(), 1.0F);
        drawScaledString(g, font, trim(font, SmartphoneClientState.ownerName(), Math.round(w * 0.56F)),
                x + Math.round(66 * sx), y + Math.round(26 * sy), bankText(), 1.18F);

        int searchSize = Math.max(34, Math.round(42 * sx));
        int searchX = x + w - searchSize;
        int searchY = y + Math.round(4 * sy);
        drawFigmaHeaderButton(g, HEADER_HOME_SEARCH_TEXTURE, searchX, searchY, searchSize);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(searchX, searchY, searchSize, searchSize),
                "APP", "search", "", ""));

        int cardH = Math.max(142, Math.round(w * 199.0F / 335.0F));
        int cardY = y + Math.round(82 * sy);
        drawFigmaCard(g, font, x, cardY, w, cardH, selected, true);
        if (selected != null) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, cardY, w, cardH),
                    "ACCOUNT", selected.id().toString(), "", ""));
            drawBankingAccountMeta(g, font, x, cardY + cardH + Math.round(7 * sy), w, selected, hitboxes);
        }

        int accountMetaSpacing = Math.round(22 * sy);
        int actionsY = y + Math.round(319 * sy) + accountMetaSpacing;
        drawFigmaHomeAction(g, font, x + Math.round(0 * sx), actionsY, Math.round(54 * sx),
                "Sent", "send", BANK_BLUE, hitboxes, "APP", "send-money");
        drawFigmaHomeAction(g, font, x + Math.round(94 * sx), actionsY, Math.round(54 * sx),
                "Receive", "receive", 0xFFFF7A45, hitboxes, "APP", "request-money");
        drawFigmaHomeAction(g, font, x + Math.round(188 * sx), actionsY, Math.round(54 * sx),
                "Pay Requests", "stats", 0xFF2CCB8E, hitboxes, "APP", "bank-pay-requests");
        drawFigmaHomeAction(g, font, x + Math.round(281 * sx), actionsY, Math.round(54 * sx),
                "Tap to Pay", "tap", 0xFFFFB13D, hitboxes, "APP", "tap");

        int headingY = y + Math.round(415 * sy) + accountMetaSpacing;
        drawScaledString(g, font, "Transaction", x, headingY, bankText(), 1.28F);
        String seeAll = "See All";
        int seeAllW = phoneTextWidth(seeAll, 9.0F);
        drawScaledString(g, font, seeAll, x + w - seeAllW, headingY + 2, BANK_BLUE, 1.0F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - seeAllW - 6, headingY - 4, seeAllW + 12, 20),
                "APP", "history", "", ""));

        int navY = bankDisplayNavY(y, h, w);
        int rowY = y + Math.round(454 * sy) + accountMetaSpacing;
        List<SmartphoneClientState.PhoneTransaction> txs = SmartphoneClientState.transactionsForSelectedAccount();
        int rowStep = Math.max(52, Math.round(64 * sy));
        int maxRows = Math.max(2, (navY - rowY - 8) / rowStep);
        drawTransactionRows(g, font, x, rowY, w, rowStep, maxRows, txs, 0, hitboxes);
        drawBankDisplayBottomNav(g, font, x, navY, w, "home", hitboxes);
    }

    private static void drawBankingAccountMeta(GuiGraphics g, Font font, int x, int y, int w,
                                               SmartphoneClientState.PhoneAccount account,
                                               List<SmartphoneClientState.Hitbox> hitboxes) {
        float sx = w / 335.0F;
        float sy = w / 335.0F;
        int copyW = Math.max(62, Math.round(70 * sx));
        int copyH = Math.max(17, Math.round(18 * sy));
        int copyX = x + w - copyW;
        int copyY = y;

        String bankName = account.bankName() == null || account.bankName().isBlank() ? "Bank" : account.bankName();
        int primaryW = account.primary() ? Math.max(52, Math.round(57 * sx)) : 0;
        int bankMaxW = Math.max(44, copyX - x - primaryW - Math.round(12 * sx));
        String bankLabel = trim(font, bankName, bankMaxW);
        drawScaledString(g, font, bankLabel, x, y + Math.round(1 * sy), bankText(), 0.88F);

        if (account.primary()) {
            int badgeX = x + Math.min(bankMaxW, phoneTextWidth(bankLabel, 8.0F)) + Math.round(6 * sx);
            fillRounded(g, badgeX, y, primaryW, copyH, Math.max(6, Math.round(8 * sx)), 0x3320C985);
            drawScaledCentered(g, font, "PRIMARY", badgeX + primaryW / 2, y + Math.round(4 * sy), 0xFF20C985, 0.68F);
        }

        fillRounded(g, copyX, copyY, copyW, copyH, Math.max(6, Math.round(8 * sx)), bankPanelSoft());
        drawScaledCentered(g, font, "Copy ID", copyX + copyW / 2, copyY + Math.round(4 * sy), bankText(), 0.74F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(copyX, copyY, copyW, copyH),
                "COPY_SELECTED_ACCOUNT_ID", "", "", ""));

        String idLabel = "ID " + shortUuid(account.id());
        drawScaledString(g, font, idLabel, x, y + Math.round(18 * sy), bankMuted(), 0.72F);
    }

    private static void drawBankWelcome(GuiGraphics g, Font font, int x, int y, int w, int h,
                                        List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "home", "", ""));

        int titleY = y + Math.round(94 * sy);
        drawScaledString(g, font, "Sign Up", x, titleY, bankText(), 1.9F);

        int fieldY = y + Math.round(166 * sy);
        drawFigmaSignupStaticField(g, font, x, fieldY, w, sy, "Full Name", "user", SmartphoneClientState.ownerName());
        fieldY += Math.round(84 * sy);
        drawFigmaSignupStaticField(g, font, x, fieldY, w, sy, "Bank Name", "cards", SmartphoneClientState.defaultBankName());
        fieldY += Math.round(84 * sy);
        drawFigmaSignupStaticField(g, font, x, fieldY, w, sy, "Account Type", "email", "Checking Account");
        fieldY += Math.round(84 * sy);
        drawFigmaSignupPinField(g, font, x, fieldY, w, sy, hitboxes);

        int buttonH = Math.max(46, Math.round(56 * sy));
        int buttonY = Math.min(y + Math.round(548 * sy), y + h - buttonH - Math.round(62 * sy));
        mobileButton(g, font, x, buttonY, w, buttonH, "Sign Up", BANK_BLUE,
                hitboxes, "OPEN_DEFAULT_ACCOUNT", "", "", "");

        int copyY = Math.min(y + Math.round(628 * sy), y + h - Math.round(35 * sy));
        drawScaledCentered(g, font, "Already have an account.", x + w / 2 - Math.round(22 * sx), copyY, bankMuted(), 0.86F);
        drawScaledString(g, font, "Sign In", x + w / 2 + Math.round(48 * sx), copyY, BANK_BLUE, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w / 2 + Math.round(42 * sx), copyY - 6,
                Math.round(70 * sx), 20), "APP", "bank-sign-in", "", ""));
    }

    private static void drawFigmaSignupStaticField(GuiGraphics g, Font font, int x, int y, int w, float sy,
                                                   String label, String icon, String value) {
        float sx = w / 335.0F;
        drawScaledString(g, font, label, x, y, bankMuted(), 0.92F);
        int rowY = y + Math.round(31 * sy);
        drawFigmaProfileIcon(g, icon, x + Math.round(11 * sx), rowY + Math.round(11 * sy), bankMuted());
        drawScaledString(g, font, trim(font, value == null ? "" : value, w - Math.round(58 * sx)),
                x + Math.round(38 * sx), rowY + Math.round(3 * sy), bankText(), 0.95F);
        g.fill(x, rowY + Math.round(32 * sy), x + w, rowY + Math.round(33 * sy), bankLine());
    }

    private static void drawFigmaSignupPinField(GuiGraphics g, Font font, int x, int y, int w, float sy,
                                                List<SmartphoneClientState.Hitbox> hitboxes) {
        float sx = w / 335.0F;
        drawScaledString(g, font, "Password", x, y, bankMuted(), 0.92F);
        int rowY = y + Math.round(31 * sy);
        drawDarkLockGlyph(g, x + Math.round(11 * sx), rowY + Math.round(11 * sy), bankMuted(), bankBg());
        String pin = SmartphoneClientState.signupPin();
        boolean pinActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.SIGNUP_PIN;
        String dots = passwordDots(pin);
        drawScaledString(g, font, dots.isBlank() ? (pinActive ? "" : "4 digit PIN") : dots,
                x + Math.round(38 * sx), rowY + Math.round(3 * sy), dots.isBlank() ? bankMuted() : bankText(), 0.95F);
        if (pinActive) {
            int caretX = x + Math.round(38 * sx) + (dots.isBlank() ? 0 : Math.min(phoneTextWidth(dots, 8.55F), w - Math.round(58 * sx))) + 2;
            g.fill(caretX, rowY + Math.round(2 * sy), caretX + 1, rowY + Math.round(14 * sy),
                    activeCaretColor(bankText()));
        }
        drawEyeGlyph(g, x + w - Math.round(13 * sx), rowY + Math.round(11 * sy), bankMuted(), bankBg());
        g.fill(x, rowY + Math.round(32 * sy), x + w, rowY + Math.round(33 * sy), bankLine());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y + Math.round(22 * sy),
                w, Math.round(48 * sy)), "INPUT", SmartphoneClientState.InputTarget.SIGNUP_PIN.name(), "", ""));
    }

    private static void drawFigmaAvatar(GuiGraphics g, Font font, int x, int y, int size) {
        fillCircle(g, x + size / 2, y + size / 2, size / 2, bankPanelSoft());
        ResourceLocation skin = resolveLocalSkin();
        int inset = Math.max(3, Math.round(size * 0.08F));
        if (skin != null) {
            PlayerFaceRenderer.draw(g, skin, x + inset, y + inset, Math.max(1, size - inset * 2));
            maskOutsideCircle(g, x, y, size, x + size / 2, y + size / 2, size / 2, bankBg());
        } else {
            fillCircle(g, x + size / 2, y + size / 2, Math.max(1, size / 2 - inset), BANK_BLUE);
            drawScaledCentered(g, font, initials(SmartphoneClientState.ownerName()), x + size / 2,
                    y + Math.round(size * 0.38F), bankText(), Mth.clamp(size / 38.0F, 0.72F, 1.18F));
        }
    }

    private static ResourceLocation resolveLocalSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return null;
        }
        return resolveSkin(mc.player.getUUID());
    }

    private static ResourceLocation resolveSkin(UUID playerId) {
        Minecraft mc = Minecraft.getInstance();
        if (playerId == null || mc.getConnection() == null) {
            return null;
        }
        var info = mc.getConnection().getPlayerInfo(playerId);
        return info == null ? null : info.getSkin().texture();
    }

    private static void drawFigmaHeaderButton(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        g.blit(texture, x, y, size, size, 0.0F, 0.0F,
                HEADER_BUTTON_TEXTURE_SIZE, HEADER_BUTTON_TEXTURE_SIZE,
                HEADER_BUTTON_TEXTURE_SIZE, HEADER_BUTTON_TEXTURE_SIZE);
    }

    private static void drawFigmaSearchGlyph(GuiGraphics g, int x, int y, int size) {
        g.blit(HEADER_SEARCH_GLYPH_TEXTURE, x, y, size, size, 0.0F, 0.0F,
                HEADER_SEARCH_GLYPH_TEXTURE_SIZE, HEADER_SEARCH_GLYPH_TEXTURE_SIZE,
                HEADER_SEARCH_GLYPH_TEXTURE_SIZE, HEADER_SEARCH_GLYPH_TEXTURE_SIZE);
    }

    private static void drawFigmaHomeAction(GuiGraphics g, Font font, int x, int y, int size,
        String label, String icon, int color,
                                            List<SmartphoneClientState.Hitbox> hitboxes,
                                            String action, String p1) {
        int safeSize = Math.max(38, size);
        fillCircle(g, x + safeSize / 2, y + safeSize / 2, safeSize / 2, bankPanelSoft());
        drawFigmaActionIcon(g, icon, x + safeSize / 2, y + safeSize / 2, safeSize, color);
        drawScaledCentered(g, font, label, x + safeSize / 2, y + safeSize + 7, bankMuted(), 0.92F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x - 4, y - 4, safeSize + 8, safeSize + 28),
                action, p1, "", ""));
    }

    private static void drawFigmaActionIcon(GuiGraphics g, String icon, int centerX, int centerY, int color) {
        drawFigmaActionIcon(g, icon, centerX, centerY, 54, color);
    }

    private static void drawFigmaActionIcon(GuiGraphics g, String icon, int centerX, int centerY, int circleSize, int color) {
        String id = icon == null ? "" : icon.toLowerCase(Locale.ROOT);
        ResourceLocation texture = actionIconTexture(id);
        if (texture == null) {
            fillCircle(g, centerX, centerY, Math.max(7, Math.round(circleSize * 0.15F)), color);
            return;
        }
        int iconW;
        int iconH;
        if ("tap".equals(id)) {
            iconW = Math.max(18, Math.round(circleSize * 0.43F));
            iconH = Math.max(19, Math.round(circleSize * 0.45F));
        } else if ("stats".equals(id)) {
            int ring = Math.max(10, Math.round(circleSize * 0.22F));
            drawCircleRing(g, centerX, centerY, ring, Math.max(1, Math.round(circleSize * 0.03F)), color, bankPanelSoft());
            iconW = Math.max(6, Math.round(circleSize * 0.14F));
            iconH = Math.max(11, Math.round(circleSize * 0.25F));
        } else {
            iconW = Math.max(11, Math.round(circleSize * 0.24F));
            iconH = Math.max(15, Math.round(circleSize * 0.30F));
        }
        g.blit(texture, centerX - iconW / 2, centerY - iconH / 2, iconW, iconH, 0.0F, 0.0F,
                actionIconTextureWidth(id), actionIconTextureHeight(id),
                actionIconTextureWidth(id), actionIconTextureHeight(id));
    }

    private static ResourceLocation actionIconTexture(String id) {
        return switch (id) {
            case "send" -> ACTION_SEND_TEXTURE;
            case "receive" -> ACTION_RECEIVE_TEXTURE;
            case "stats" -> ACTION_LOAN_TEXTURE;
            case "tap" -> ACTION_TOPUP_TEXTURE;
            default -> null;
        };
    }

    private static int actionIconTextureWidth(String id) {
        return switch (id) {
            case "send", "receive" -> 30;
            case "stats" -> 16;
            case "tap" -> 48;
            default -> 1;
        };
    }

    private static int actionIconTextureHeight(String id) {
        return switch (id) {
            case "send", "receive" -> 36;
            case "stats" -> 28;
            case "tap" -> 50;
            default -> 1;
        };
    }

    private static void drawFigmaBottomNav(GuiGraphics g, Font font, int x, int y, int w, String active,
                                           List<SmartphoneClientState.Hitbox> hitboxes) {
        int navH = bankBottomNavHeight(w);
        g.blit(bottomNavTexture(active), x, y, w, navH, 0.0F, 0.0F,
                BOTTOM_NAV_TEXTURE_WIDTH, BOTTOM_NAV_TEXTURE_HEIGHT,
                BOTTOM_NAV_TEXTURE_WIDTH, BOTTOM_NAV_TEXTURE_HEIGHT);
        int col = Math.max(1, w / 4);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, col, navH),
                "APP", "banking", "", ""));
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + col, y, col, navH),
                "APP", "my-cards", "", ""));
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + col * 2, y, col, navH),
                "APP", "statistics", "", ""));
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + col * 3, y, w - col * 3, navH),
                "APP", "bank-settings", "", ""));
    }

    private static void drawBankDisplayBottomNav(GuiGraphics g, Font font, int contentX, int y, int contentW,
                                                 String active, List<SmartphoneClientState.Hitbox> hitboxes) {
        drawFigmaBottomNav(g, font, contentX - BANK_DISPLAY_BLEED, y,
                contentW + BANK_DISPLAY_BLEED * 2, active, hitboxes);
    }

    private static int bankDisplayNavY(int contentY, int contentH, int contentW) {
        int displayW = contentW + BANK_DISPLAY_BLEED * 2;
        int navH = bankBottomNavHeight(displayW);
        int bottomAligned = contentY + contentH - navH;
        int figmaTop = contentY + Math.round(673.0F * (contentW / 335.0F));
        if (bottomAligned <= contentY) {
            return contentY;
        }
        return Mth.clamp(Math.min(figmaTop, bottomAligned), contentY, bottomAligned);
    }

    private static int bankBottomNavHeight(int displayW) {
        float scale = Mth.clamp(displayW / 375.0F, 0.65F, 1.15F);
        return Math.max(60, Math.round(86 * scale));
    }

    private static void drawBankBottomTab(GuiGraphics g, Font font, int x, int y, int w, float scale,
                                          String id, String label, String active) {
        boolean selected = id.equals(active);
        int color = selected ? BANK_BLUE : bankMuted();
        int centerX = x + w / 2;
        int iconY = y + Math.round(10 * scale);
        drawFigmaBottomIcon(g, id, centerX, iconY, color, scale);
        drawScaledCentered(g, font, label, centerX, y + Math.round(30 * scale), color, Mth.clamp(scale * 0.83F, 0.62F, 0.92F));
    }

    private static ResourceLocation bottomNavTexture(String active) {
        return switch (active == null ? "" : active) {
            case "cards" -> BOTTOM_NAV_CARDS_TEXTURE;
            case "stats" -> BOTTOM_NAV_STATS_TEXTURE;
            case "settings" -> BOTTOM_NAV_SETTINGS_TEXTURE;
            default -> BOTTOM_NAV_HOME_TEXTURE;
        };
    }

    private static void figmaBottomTabHitbox(int x, int y, int w, float scale,
                                            List<SmartphoneClientState.Hitbox> hitboxes,
                                            String action, String p1) {
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - Math.round(8 * scale), w, Math.round(58 * scale)),
                action, p1, "", ""));
    }

    private static void drawFigmaBottomIcon(GuiGraphics g, String id, int centerX, int centerY, int color, float scale) {
        int one = Math.max(1, Math.round(scale));
        int two = Math.max(2, Math.round(2 * scale));
        int three = Math.max(2, Math.round(3 * scale));
        int four = Math.max(3, Math.round(4 * scale));
        int five = Math.max(4, Math.round(5 * scale));
        int six = Math.max(5, Math.round(6 * scale));
        int seven = Math.max(6, Math.round(7 * scale));
        int eight = Math.max(7, Math.round(8 * scale));
        int nine = Math.max(7, Math.round(9 * scale));
        int ten = Math.max(8, Math.round(10 * scale));
        switch (id) {
            case "home" -> {
                drawSoftLine(g, centerX - ten, centerY - one, centerX, centerY - nine, color);
                drawSoftLine(g, centerX, centerY - nine, centerX + ten, centerY - one, color);
                drawSoftLine(g, centerX - seven, centerY, centerX - seven, centerY + nine, color);
                drawSoftLine(g, centerX + seven, centerY, centerX + seven, centerY + nine, color);
                drawSoftLine(g, centerX - seven, centerY + nine, centerX + seven, centerY + nine, color);
                drawSoftLine(g, centerX - two, centerY + four, centerX - two, centerY + nine, color);
                drawSoftLine(g, centerX + two, centerY + four, centerX + two, centerY + nine, color);
            }
            case "cards" -> {
                drawRoundedBorder(g, centerX - ten, centerY - seven, ten * 2, Math.max(13, Math.round(14 * scale)),
                        three, color, bankPanel());
                g.fill(centerX - eight, centerY - three, centerX + eight, centerY - one, color);
                g.fill(centerX - seven, centerY + four, centerX - one, centerY + five, color);
            }
            case "stats" -> {
                drawCircleRing(g, centerX - one, centerY, nine, two, color, bankPanel());
                fillCircle(g, centerX - one, centerY, Math.max(4, Math.round(5 * scale)), bankPanel());
                drawSoftLine(g, centerX - one, centerY, centerX - one, centerY - nine, color);
                drawSoftLine(g, centerX - one, centerY, centerX + eight, centerY + four, color);
            }
            case "settings" -> {
                drawCircleRing(g, centerX, centerY, eight, two, color, bankPanel());
                fillCircle(g, centerX, centerY, three, color);
                g.fill(centerX - one, centerY - ten, centerX + one, centerY - eight, color);
                g.fill(centerX - one, centerY + eight, centerX + one, centerY + ten, color);
                g.fill(centerX - ten, centerY - one, centerX - eight, centerY + one, color);
                g.fill(centerX + eight, centerY - one, centerX + ten, centerY + one, color);
            }
            default -> fillCircle(g, centerX, centerY, seven, color);
        }
    }

    private static void drawStatistics(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        List<SmartphoneClientState.PhoneTransaction> txs = SmartphoneClientState.transactionsForSelectedAccount();
        String selectedMonth = normalizeStatsMonthKey(txs);
        List<SmartphoneClientState.PhoneTransaction> monthTxs = filterByMonth(txs, selectedMonth);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Statistics", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, bankPanelSoft());
        drawBellGlyph(g, x + w - circle / 2, y + circle / 2, bankText(), bankPanelSoft());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "bank-pay-requests", "", ""));

        drawScaledCentered(g, font, "Current Balance", x + w / 2, y + Math.round(73 * sy), bankMuted(), 1.38F);
        drawScaledCentered(g, font, selected == null ? totalBalanceLabel(SmartphoneClientState.accounts()) : selected.shortBalance(),
                x + w / 2, y + Math.round(105 * sy), bankText(), 1.85F);

        int chartY = y + Math.round(162 * sy);
        int chartH = Math.max(132, Math.round(204 * sy));
        drawFigmaStatisticsChart(g, x, chartY, w, chartH, monthTxs);

        int monthY = y + Math.round(386 * sy);
        List<String> months = statsMonthKeys(txs);
        int visibleMonths = Math.max(1, months.size());
        int selectedPillW = Math.max(42, Math.round(47 * sx));
        int monthSpan = Math.max(1, w - selectedPillW);
        for (int i = 0; i < months.size(); i++) {
            String key = months.get(i);
            String label = monthLabel(key);
            int monthX = x + (visibleMonths <= 1 ? 0 : Math.round(monthSpan * (i / (float) (visibleMonths - 1))));
            if (key.equals(selectedMonth)) {
                int pillW = selectedPillW;
                fillRounded(g, monthX, monthY - Math.round(6 * sy), pillW, Math.max(24, Math.round(28 * sy)),
                        Math.max(7, Math.round(8 * sx)), BANK_BLUE);
                drawScaledCentered(g, font, label, monthX + pillW / 2, monthY, 0xFFFFFFFF, 0.98F);
                hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(monthX, monthY - 8, pillW, 30),
                        "STATS_MONTH", key, "", ""));
            } else {
                drawScaledString(g, font, label, monthX, monthY, bankMuted(), 0.98F);
                hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(monthX - 6, monthY - 8, 50, 30),
                        "STATS_MONTH", key, "", ""));
            }
        }

        int headingY = y + Math.round(437 * sy);
        drawScaledString(g, font, "Transaction", x, headingY, bankText(), 1.28F);
        String seeAll = "See All";
        int seeAllW = phoneTextWidth(seeAll, 9.0F);
        drawScaledString(g, font, seeAll, x + w - seeAllW, headingY + 2, BANK_BLUE, 1.0F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - seeAllW - 6, headingY - 4, seeAllW + 12, 20),
                "APP", "history", "", ""));

        int navY = bankDisplayNavY(y, h, w);
        int rowY = y + Math.round(476 * sy);
        int rowStep = Math.max(52, Math.round(64 * sy));
        int maxRows = Math.max(1, (navY - rowY - 8) / rowStep);
        SmartphoneClientState.setHistoryScrollWindow(monthTxs.size(), maxRows);
        drawTransactionRows(g, font, x, rowY, w, rowStep, maxRows, monthTxs,
                SmartphoneClientState.historyScrollOffset(), hitboxes);
        drawBankDisplayBottomNav(g, font, x, navY, w, "stats", hitboxes);
    }

    private static void drawBellGlyph(GuiGraphics g, int centerX, int centerY, int color) {
        drawBellGlyph(g, centerX, centerY, color, 0xFFF6F6F7);
    }

    private static void drawBellGlyph(GuiGraphics g, int centerX, int centerY, int color, int bg) {
        fillCircle(g, centerX, centerY + 1, 7, color);
        fillCircle(g, centerX, centerY + 2, 5, bg);
        g.fill(centerX - 6, centerY + 5, centerX + 7, centerY + 7, color);
        g.fill(centerX - 4, centerY - 6, centerX + 5, centerY + 4, color);
        fillCircle(g, centerX, centerY + 9, 2, color);
        g.fill(centerX - 1, centerY - 9, centerX + 2, centerY - 6, color);
    }

    private static void drawEyeGlyph(GuiGraphics g, int centerX, int centerY, int color, int bg) {
        fillRounded(g, centerX - 9, centerY - 5, 18, 10, 5, color);
        fillRounded(g, centerX - 7, centerY - 3, 14, 6, 3, bg);
        fillCircle(g, centerX, centerY, 4, color);
        fillCircle(g, centerX, centerY, 2, bg);
        g.fill(centerX + 6, centerY - 7, centerX + 8, centerY - 5, color);
        g.fill(centerX + 8, centerY - 9, centerX + 10, centerY - 7, color);
    }

    private static void drawDarkMailGlyph(GuiGraphics g, int centerX, int centerY, int color) {
        g.fill(centerX - 9, centerY - 6, centerX + 10, centerY - 4, color);
        g.fill(centerX - 9, centerY + 5, centerX + 10, centerY + 7, color);
        g.fill(centerX - 9, centerY - 6, centerX - 7, centerY + 7, color);
        g.fill(centerX + 8, centerY - 6, centerX + 10, centerY + 7, color);
        g.fill(centerX - 6, centerY - 2, centerX - 2, centerY, color);
        g.fill(centerX + 2, centerY - 2, centerX + 6, centerY, color);
        g.fill(centerX - 2, centerY, centerX + 3, centerY + 2, color);
    }

    private static void drawDarkLockGlyph(GuiGraphics g, int centerX, int centerY, int color, int bg) {
        fillRounded(g, centerX - 7, centerY - 9, 14, 12, 6, color);
        fillRounded(g, centerX - 5, centerY - 7, 10, 10, 5, bg);
        fillRounded(g, centerX - 9, centerY - 1, 18, 13, 3, color);
        fillCircle(g, centerX, centerY + 4, 2, bg);
        g.fill(centerX - 1, centerY + 4, centerX + 2, centerY + 9, bg);
    }

    private static void drawFigmaStatisticsChart(GuiGraphics g, int x, int y, int w, int h,
                                                 List<SmartphoneClientState.PhoneTransaction> txs) {
        int[] gridOffsets = {18, 78, 137, 197, 258, 318};
        for (int offset : gridOffsets) {
            int gx = x + Math.round(offset * (w / 335.0F));
            g.fill(gx, y, gx + 1, y + h, bankLine());
        }

        SmartphoneChartData.ChartSeries series = SmartphoneChartData.buildStatisticsChartSeries(txs, x, y, w, h);
        if (!series.hasData()) {
            int baseline = series.baselineY();
            drawSoftLine(g, x, baseline, x + w, baseline, 0x662748FF);
            return;
        }

        int[] px = series.xs();
        int[] py = series.ys();
        int baseline = series.baselineY();
        int prevX = px[0];
        int prevY = py[0];
        for (int i = 0; i < px.length - 1; i++) {
            float p0 = py[Math.max(0, i - 1)];
            float p1 = py[i];
            float p2 = py[i + 1];
            float p3 = py[Math.min(px.length - 1, i + 2)];
            int startX = px[i];
            int endX = px[i + 1];
            int samples = Math.max(4, Math.round((endX - startX) / 7.0F));
            for (int step = 1; step <= samples; step++) {
                float t = step / (float) samples;
                int sx = Math.round(Mth.lerp(t, startX, endX));
                int sy = Mth.clamp(Math.round(catmullRom(p0, p1, p2, p3, t)), y, y + h);
                int fillTop = Math.min(sy + 5, baseline);
                int fillBottom = Math.max(sy + 5, baseline);
                g.fill(sx, fillTop, sx + Math.max(1, Math.round(w / 170.0F)), fillBottom, 0x12006BFF);
                drawSoftLine(g, prevX, prevY, sx, sy, BANK_BLUE);
                prevX = sx;
                prevY = sy;
            }
        }
        fillCircle(g, series.markerX(), series.markerY(), 11, BANK_BLUE);
        fillCircle(g, series.markerX(), series.markerY(), 6, bankText());
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * ((2.0F * p1)
                + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }

    private static void drawHistory(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Transaction History", x + w / 2, y + Math.round(10 * sy),
                bankText(), 1.22F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, bankPanelSoft());
        drawFigmaRefreshGlyph(g, x + w - circle / 2, y + circle / 2, bankText(), bankPanelSoft());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "history", "", ""));

        int headingY = y + Math.round(72 * sy);
        drawScaledString(g, font, "Today", x, headingY, bankText(), 1.34F);
        String seeAll = "See All";
        int seeW = phoneTextWidth(seeAll, 9.0F);
        drawScaledString(g, font, seeAll, x + w - seeW, headingY + 2, BANK_BLUE, 1.0F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - seeW - 6, headingY - 4, seeW + 12, 20),
                "APP", "search", "", ""));

        List<SmartphoneClientState.PhoneTransaction> txs = SmartphoneClientState.transactionsForSelectedAccount();
        int navY = bankDisplayNavY(y, h, w);
        int rowY = y + Math.round(121 * sy);
        int rowStep = Math.max(52, Math.round(64 * sy));
        int maxRows = Math.max(3, (navY - rowY - 8) / rowStep);
        SmartphoneClientState.setHistoryScrollWindow(txs.size(), maxRows);
        drawTransactionRows(g, font, x, rowY, w, rowStep, maxRows, txs,
                SmartphoneClientState.historyScrollOffset(), hitboxes);
        drawBankDisplayBottomNav(g, font, x, navY, w, "", hitboxes);
    }

    private static void drawFigmaRefreshGlyph(GuiGraphics g, int centerX, int centerY, int color) {
        drawFigmaRefreshGlyph(g, centerX, centerY, color, 0xFFF6F6F7);
    }

    private static void drawFigmaRefreshGlyph(GuiGraphics g, int centerX, int centerY, int color, int bg) {
        fillCircle(g, centerX, centerY, 8, color);
        fillCircle(g, centerX, centerY, 6, bg);
        g.fill(centerX + 4, centerY - 8, centerX + 9, centerY - 6, color);
        g.fill(centerX + 7, centerY - 8, centerX + 9, centerY - 3, color);
        g.fill(centerX - 9, centerY + 5, centerX - 4, centerY + 7, color);
        g.fill(centerX - 9, centerY + 2, centerX - 7, centerY + 7, color);
    }

    private static void drawSearch(GuiGraphics g, Font font, int x, int y, int w, int h,
                                   List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;

        int circle = Math.max(32, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Search", x + w / 2, y + Math.round(10 * sy), bankText(), 1.35F);
        drawFigmaHeaderButton(g, HEADER_CLOSE_TEXTURE, x + w - circle, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "banking", "", ""));

        int searchY = y + Math.round(74 * sy);
        int searchH = Math.max(38, Math.round(44 * sy));
        fillRounded(g, x, searchY, w, searchH, Math.max(9, Math.round(10 * sx)), bankInput());
        int searchGlyph = Math.max(18, Math.round(20 * sx));
        drawSearchGlyph(g, x + Math.round(18 * sx) + searchGlyph / 2, searchY + searchH / 2, bankMuted());
        String query = SmartphoneClientState.historySearch();
        boolean searchActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.HISTORY_SEARCH;
        String searchText = query == null || query.isBlank() ? (searchActive ? "" : "Search") : trim(font, query, w - Math.round(96 * sx));
        drawScaledString(g, font, searchText, x + Math.round(46 * sx), searchY + (searchH - 8) / 2,
                query == null || query.isBlank() ? bankMuted() : bankText(), 1.05F);
        if (searchActive) {
            int caretX = x + Math.round(46 * sx)
                    + (query == null || query.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, query, w - Math.round(96 * sx)), 9.45F), w - Math.round(96 * sx))) + 2;
            g.fill(caretX, searchY + (searchH - 12) / 2, caretX + 1, searchY + (searchH + 12) / 2,
                    activeCaretColor(bankText()));
        }
        drawFigmaCloseGlyph(g, x + w - Math.round(28 * sx), searchY + searchH / 2, bankMuted(), sx * 0.82F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, searchY, w, searchH),
                "INPUT", SmartphoneClientState.InputTarget.HISTORY_SEARCH.name(), "", ""));
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - Math.round(48 * sx), searchY, Math.round(48 * sx), searchH),
                "SEARCH_CLEAR", "", "", ""));

        List<SmartphoneClientState.PhoneTransaction> txs = filterHistory(
                SmartphoneClientState.transactionsForSelectedAccount(),
                query);
        int rowY = y + Math.round(148 * sy);
        int rowStep = Math.max(52, Math.round(64 * sy));
        int maxRows = Math.max(3, (y + h - rowY - 8) / rowStep);
        SmartphoneClientState.setHistoryScrollWindow(txs.size(), maxRows);
        drawTransactionRows(g, font, x, rowY, w, rowStep, maxRows, txs,
                SmartphoneClientState.historyScrollOffset(), hitboxes);
    }

    private static void drawFigmaSearchRow(GuiGraphics g, Font font, int x, int y, int w,
                                           String title, String subtitle, String amount, String icon) {
        drawFigmaSearchRow(g, font, x, y, w, title, subtitle, amount, icon, true);
    }

    private static void drawTransactionRows(GuiGraphics g, Font font, int x, int rowY, int w,
                                            int rowStep, int maxRows,
                                            List<SmartphoneClientState.PhoneTransaction> txs,
                                            int offset,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        List<SmartphoneClientState.PhoneTransaction> rows = sortedTransactions(txs);
        if (rows.isEmpty()) {
            drawEmptyState(g, font, x, rowY + 28, w, "No transactions yet", "This account has no recorded history.");
            return;
        }
        int visibleRows = Math.max(1, maxRows);
        int start = Mth.clamp(offset, 0, Math.max(0, rows.size() - visibleRows));
        int shown = 0;
        for (int i = start; i < rows.size() && shown < visibleRows; i++, shown++) {
            SmartphoneClientState.PhoneTransaction tx = rows.get(i);
            drawFigmaSearchRow(g, font, x, rowY, w,
                    transactionTitle(tx),
                    transactionSubtitle(tx),
                    tx.amount(),
                    iconForTransaction(tx, i));
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, rowY - 4, w, Math.max(44, rowStep - 6)),
                    "TX_DETAIL", tx.txId().toString(), "", ""));
            rowY += rowStep;
        }
    }

    private static void drawEmptyState(GuiGraphics g, Font font, int x, int y, int w, String title, String detail) {
        fillRounded(g, x, y, w, 86, 15, bankPanelSoft());
        drawScaledCentered(g, font, title, x + w / 2, y + 24, bankText(), 1.05F);
        drawScaledCentered(g, font, detail, x + w / 2, y + 46, bankMuted(), 0.85F);
    }

    private static void drawFigmaSearchRow(GuiGraphics g, Font font, int x, int y, int w,
                                           String title, String subtitle, String amount, String icon,
                                           boolean positiveAmountBlue) {
        int iconSize = Math.max(36, Math.round(w * 42.0F / 335.0F));
        fillCircle(g, x + iconSize / 2, y + iconSize / 2, iconSize / 2, bankPanelSoft());
        drawFigmaSearchIcon(g, font, icon, x + iconSize / 2, y + iconSize / 2);
        String rawValue = amount == null || amount.isBlank() ? "$0" : amount;
        String value = figmaAmountLabel(rawValue);
        float amountScale = 1.35F;
        int amountW = phoneTextWidth(value, font.lineHeight * amountScale);
        int amountColor = !rawValue.contains("-") && positiveAmountBlue ? BANK_BLUE : bankText();
        int textX = x + Math.round(w * 58.0F / 335.0F);
        int textMax = Math.max(48, w - (textX - x) - amountW - Math.round(w * 18.0F / 335.0F));
        drawScaledString(g, font, trim(font, title == null || title.isBlank() ? "Transaction" : title,
                Math.round(textMax / 1.32F)), textX, y + 4, bankText(), 1.32F);
        drawScaledString(g, font, trim(font, subtitle == null || subtitle.isBlank() ? "Transaction" : subtitle,
                textMax), textX, y + 25, bankMuted(), 1.0F);
        drawScaledString(g, font, value, x + w - amountW, y + 10, amountColor, amountScale);
    }

    private static String figmaAmountLabel(String amount) {
        String value = amount == null || amount.isBlank() ? "$0" : amount.trim();
        if (value.startsWith("-$")) {
            return "- $" + value.substring(2);
        }
        if (value.startsWith("- $")) {
            return value;
        }
        if (value.startsWith("+$")) {
            return "$" + value.substring(2);
        }
        if (value.startsWith("+ $")) {
            return "$" + value.substring(3);
        }
        if (value.startsWith("+")) {
            return value.substring(1).trim();
        }
        return value;
    }

    private static void drawFigmaCloseGlyph(GuiGraphics g, int centerX, int centerY, int color, float sx) {
        int arm = Math.max(6, Math.round(8 * sx));
        int thickness = Math.max(2, Math.round(2 * sx));
        drawSoftLine(g, centerX - arm, centerY - arm, centerX + arm, centerY + arm, color);
        drawSoftLine(g, centerX - arm, centerY + arm, centerX + arm, centerY - arm, color);
        if (thickness > 2) {
            drawSoftLine(g, centerX - arm + 1, centerY - arm, centerX + arm + 1, centerY + arm, color);
            drawSoftLine(g, centerX - arm + 1, centerY + arm, centerX + arm + 1, centerY - arm, color);
        }
    }

    private static void drawFigmaChevronRight(GuiGraphics g, int centerX, int centerY, int size, int color) {
        drawSoftLine(g, centerX - size / 2, centerY - size, centerX + size / 2, centerY, color);
        drawSoftLine(g, centerX + size / 2, centerY, centerX - size / 2, centerY + size, color);
    }

    private static void drawFigmaChevronLeft(GuiGraphics g, int centerX, int centerY, int size, int color) {
        drawSoftLine(g, centerX + size / 2, centerY - size, centerX - size / 2, centerY, color);
        drawSoftLine(g, centerX - size / 2, centerY, centerX + size / 2, centerY + size, color);
    }

    private static void drawFigmaCheckGlyph(GuiGraphics g, int centerX, int centerY, int color) {
        drawSoftLine(g, centerX - 5, centerY, centerX - 1, centerY + 4, color);
        drawSoftLine(g, centerX - 1, centerY + 4, centerX + 6, centerY - 5, color);
    }

    private static void drawFigmaPlusGlyph(GuiGraphics g, int centerX, int centerY, int size, int color) {
        drawSoftLine(g, centerX - size, centerY, centerX + size, centerY, color);
        drawSoftLine(g, centerX, centerY - size, centerX, centerY + size, color);
    }

    private static void drawFigmaSearchIcon(GuiGraphics g, Font font, String icon, int centerX, int centerY) {
        String id = icon == null ? "" : icon.toLowerCase(Locale.ROOT);
        ResourceLocation texture = transactionIconTexture(id);
        if (texture == null) {
            fillCircle(g, centerX, centerY, 7, BANK_BLUE);
            return;
        }
        int drawW = 17;
        int drawH = 17;
        g.blit(texture, centerX - drawW / 2, centerY - drawH / 2, drawW, drawH, 0.0F, 0.0F,
                transactionIconTextureWidth(id), transactionIconTextureHeight(id),
                transactionIconTextureWidth(id), transactionIconTextureHeight(id));
    }

    private static ResourceLocation transactionIconTexture(String id) {
        return switch (id) {
            case "transfer" -> TX_TRANSFER_TEXTURE;
            case "cart" -> TX_CART_TEXTURE;
            default -> null;
        };
    }

    private static int transactionIconTextureWidth(String id) {
        return switch (id) {
            case "transfer", "cart" -> 34;
            default -> 1;
        };
    }

    private static int transactionIconTextureHeight(String id) {
        return switch (id) {
            case "transfer", "cart" -> 34;
            default -> 1;
        };
    }

    private static void drawSearchGlyph(GuiGraphics g, int centerX, int centerY, int color) {
        fillCircle(g, centerX - 2, centerY - 2, 5, color);
        fillCircle(g, centerX - 2, centerY - 2, 3, bankPanelSoft());
        g.fill(centerX + 3, centerY + 3, centerX + 8, centerY + 5, color);
    }

    private static String transactionTitle(SmartphoneClientState.PhoneTransaction tx) {
        String description = tx == null || tx.description() == null ? "" : tx.description().trim();
        if ("PHONE_TRANSFER".equalsIgnoreCase(description)) {
            return "Phone Transfer";
        }
        if (description.isBlank()) {
            return "Money Transfer";
        }
        int split = description.indexOf(':');
        if (split > 0) {
            return description.substring(0, split).trim();
        }
        return description;
    }

    private static String transactionSubtitle(SmartphoneClientState.PhoneTransaction tx) {
        if (tx == null) {
            return "Transaction";
        }
        if (tx.counterparty() != null && !tx.counterparty().isBlank()) {
            return (tx.direction() != null && tx.direction().equalsIgnoreCase("OUT") ? "To " : "From ") + tx.counterparty();
        }
        String description = tx.description() == null ? "" : tx.description().trim();
        int split = description.indexOf(':');
        if (split > 0 && split + 1 < description.length()) {
            String detail = description.substring(split + 1).trim();
            if (!detail.isBlank()) {
                return detail;
            }
        }
        return tx.time() == null || tx.time().isBlank() ? "Transaction" : tx.time();
    }

    private static String iconForTransaction(SmartphoneClientState.PhoneTransaction tx, int index) {
        String desc = tx == null || tx.description() == null ? "" : tx.description().toLowerCase(Locale.ROOT);
        if (desc.contains("grocery")
                || desc.contains("shop")
                || desc.contains("cart")
                || desc.contains("checkout")
                || desc.contains("retail")
                || desc.contains("purchase")) {
            return "cart";
        }
        return "transfer";
    }

    private static void drawTransactionDetail(GuiGraphics g, Font font, int x, int y, int w, int h,
                                              List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "history", "", ""));
        drawScaledCentered(g, font, "Transaction", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        SmartphoneClientState.PhoneTransaction tx = SmartphoneClientState.selectedTransaction();
        if (tx == null) {
            drawEmptyState(g, font, x, y + Math.round(116 * sy), w,
                    "Transaction not found", "Open a transaction from history.");
            return;
        }

        int heroY = y + Math.round(82 * sy);
        String rawAmount = tx.amount() == null || tx.amount().isBlank() ? "$0" : tx.amount();
        String heroAmount = figmaAmountLabel(rawAmount);
        fillCircle(g, x + w / 2, heroY + Math.round(28 * sy), Math.max(26, Math.round(32 * sx)),
                rawAmount.contains("-") ? 0x33FF3F60 : 0x3320C985);
        drawFigmaSearchIcon(g, font, iconForTransaction(tx, 0), x + w / 2, heroY + Math.round(28 * sy));
        drawScaledCentered(g, font, heroAmount, x + w / 2, heroY + Math.round(76 * sy),
                rawAmount.contains("-") ? bankText() : BANK_BLUE, 1.78F);
        drawScaledCentered(g, font, transactionTitle(tx), x + w / 2, heroY + Math.round(108 * sy),
                bankMuted(), 0.96F);

        int detailY = y + Math.round(246 * sy);
        int availableDetailH = Math.max(180, y + h - detailY - 8);
        int detailH = Math.min(availableDetailH, Math.max(360, Math.round(430 * sy)));
        fillRounded(g, x, detailY, w, detailH, Math.max(16, Math.round(18 * sx)), bankPanelSoft());
        drawRoundedBorder(g, x, detailY, w, detailH, Math.max(16, Math.round(18 * sx)), bankLine(), bankPanelSoft());
        int rowY = detailY + Math.round(26 * sy);
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Status", "Completed");
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Amount", rawAmount);
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Direction", tx.direction() == null || tx.direction().isBlank() ? "Transaction" : tx.direction());
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Description", tx.description() == null || tx.description().isBlank() ? "Transaction" : tx.description());
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Counterparty", tx.counterparty() == null || tx.counterparty().isBlank() ? "Account transfer" : tx.counterparty());
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Time", tx.time() == null || tx.time().isBlank() ? "Unknown" : tx.time());
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Account", shortUuid(tx.accountId()));
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "From", shortUuid(tx.senderAccountId()));
        rowY = drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "To", shortUuid(tx.receiverAccountId()));
        drawDetailLine(g, font, x + Math.round(18 * sx), rowY, w - Math.round(36 * sx),
                "Transaction ID", shortUuid(tx.txId()));
    }

    private static int drawDetailLine(GuiGraphics g, Font font, int x, int y, int w, String label, String value) {
        drawScaledString(g, font, label, x, y, bankMuted(), 0.88F);
        drawScaledString(g, font, trim(font, value == null ? "" : value, w), x, y + 16, bankText(), 0.94F);
        g.fill(x, y + 34, x + w, y + 35, bankLine());
        return y + 43;
    }

    private static void drawSendMoney(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      List<SmartphoneClientState.Hitbox> hitboxes) {
        drawMoneyFlow(g, font, x, y, w, h, hitboxes, "Send Money", "Player or Account ID",
                SmartphoneClientState.transferTo(), SmartphoneClientState.InputTarget.TRANSFER_TO,
                SmartphoneClientState.transferAmount(), SmartphoneClientState.InputTarget.TRANSFER_AMOUNT,
                "Send Money", "TRANSFER", 0xFF1972FF, false);
    }

    private static void drawRequestMoney(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        drawMoneyFlow(g, font, x, y, w, h, hitboxes, "Request Money", "Online player name",
                SmartphoneClientState.requestTarget(), SmartphoneClientState.InputTarget.REQUEST_TARGET,
                SmartphoneClientState.requestAmount(), SmartphoneClientState.InputTarget.REQUEST_AMOUNT,
                "Request Money", "REQUEST_MONEY", 0xFF20C985, true);
    }

    private static void drawBankPayRequests(GuiGraphics g, Font font, int x, int y, int w, int h,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-profile", "", ""));
        drawScaledCentered(g, font, "Pay Requests", x + w / 2, y + Math.round(11 * sy), bankText(), 1.3F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, bankPanelSoft());
        drawFigmaRefreshGlyph(g, x + w - circle / 2, y + circle / 2, bankText(), bankPanelSoft());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "PAY_REQUESTS_REFRESH", "", "", ""));

        int summaryY = y + Math.round(74 * sy);
        int summaryH = Math.max(92, Math.round(108 * sy));
        fillRounded(g, x, summaryY, w, summaryH, Math.max(14, Math.round(18 * sx)), bankPanelSoft());
        fillCircle(g, x + Math.round(34 * sx), summaryY + Math.round(34 * sy), Math.max(19, Math.round(22 * sx)), bankInput());
        drawFigmaActionIcon(g, "receive", x + Math.round(34 * sx), summaryY + Math.round(34 * sy), BANK_BLUE);
        drawScaledString(g, font, SmartphoneClientState.payRequests().size() + " Pending",
                x + Math.round(66 * sx), summaryY + Math.round(18 * sy), bankText(), 1.12F);
        drawScaledString(g, font, trim(font, "Primary: " + SmartphoneClientState.payRequestsPrimaryLabel(), w - Math.round(86 * sx)),
                x + Math.round(66 * sx), summaryY + Math.round(40 * sy), bankMuted(), 0.9F);
        drawScaledString(g, font, "Approve or decline requests securely.",
                x + Math.round(66 * sx), summaryY + Math.round(61 * sy), bankMuted(), 0.86F);

        int rowY = summaryY + summaryH + Math.round(30 * sy);
        drawScaledString(g, font, "Pending Requests", x, rowY, bankText(), 1.16F);
        rowY += Math.round(28 * sy);
        int shown = 0;
        for (var request : SmartphoneClientState.payRequests()) {
            if (shown++ >= 4 || rowY > y + h - Math.round(160 * sy)) {
                break;
            }
            drawPayRequestRow(g, font, x, rowY, w, request, hitboxes);
            rowY += Math.max(86, Math.round(96 * sy));
        }
        if (shown == 0) {
            int emptyH = Math.max(126, Math.round(148 * sy));
            fillRounded(g, x, rowY, w, emptyH, Math.max(14, Math.round(18 * sx)), bankPanelSoft());
            fillCircle(g, x + w / 2, rowY + Math.round(34 * sy), Math.max(19, Math.round(22 * sx)), bankInput());
            drawFigmaActionIcon(g, "receive", x + w / 2, rowY + Math.round(34 * sy), 0xFF20C985);
            drawScaledCentered(g, font, "No pending pay requests", x + w / 2, rowY + Math.round(68 * sy), bankText(), 1.02F);
            drawScaledCentered(g, font, "Refresh or create a new request.", x + w / 2, rowY + Math.round(88 * sy), bankMuted(), 0.9F);
            mobileButton(g, font, x + Math.round(20 * sx), rowY + emptyH - Math.round(44 * sy),
                    w - Math.round(40 * sx), Math.max(30, Math.round(36 * sy)), "Create Request", 0xFF20C985,
                    hitboxes, "APP", "request-money", "", "");
        }
        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "settings", hitboxes);
    }

    private static void drawMoneyFlow(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      List<SmartphoneClientState.Hitbox> hitboxes,
                                      String title,
                                      String targetLabel,
                                      String targetValue,
                                      SmartphoneClientState.InputTarget targetInput,
                                      String amountValue,
                                      SmartphoneClientState.InputTarget amountInput,
                                      String buttonLabel,
                                      String buttonAction,
                                      int accent,
                                      boolean requestFlow) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, title, x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        drawMoneyFlowBackdrop(g, x, y, w, sx, sy);

        int cardH = Math.max(142, Math.round(w * 199.0F / 335.0F));
        int cardY = y + Math.round(74 * sy);
        if (account != null) {
            drawFigmaCard(g, font, x, cardY, w, cardH, account, true);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, cardY, w, cardH),
                    "ACCOUNT", account.id().toString(), "", ""));
        } else {
            drawFigmaCard(g, font, x, cardY, w, cardH, null, true);
        }

        int peopleY = y + Math.round(303 * sy);
        int peopleH = Math.max(104, Math.round(129 * sy));
        fillRounded(g, x, peopleY, w, peopleH, Math.max(12, Math.round(14 * sx)), bankPanelSoft());
        drawRoundedBorder(g, x, peopleY, w, peopleH, Math.max(12, Math.round(14 * sx)), bankLine(), bankPanelSoft());
        drawScaledString(g, font, requestFlow ? "Request from" : "Send to",
                x + Math.round(12 * sx), peopleY + Math.round(15 * sy), bankText(), 1.0F);
        drawPaymentContacts(g, font, x + Math.round(12 * sx), peopleY + Math.round(46 * sy),
                w - Math.round(24 * sx), requestFlow, hitboxes);
        boolean targetActive = SmartphoneClientState.inputTarget() == targetInput;
        String targetShown = targetValue == null || targetValue.isBlank() ? (targetActive ? "" : targetLabel) : targetValue;
        int targetLineY = peopleY + peopleH - Math.round(18 * sy);
        drawScaledString(g, font, trim(font, targetShown, w - Math.round(32 * sx)),
                x + Math.round(16 * sx), targetLineY, targetValue == null || targetValue.isBlank() ? bankMuted() : bankText(), 0.82F);
        if (targetActive) {
            int caretX = x + Math.round(16 * sx) + (targetValue == null || targetValue.isBlank()
                    ? 0 : Math.min(phoneTextWidth(trim(font, targetValue, w - Math.round(32 * sx)), 7.4F), w - Math.round(32 * sx)));
            g.fill(caretX + 2, targetLineY - 1, caretX + 3, targetLineY + 10, activeCaretColor(bankText()));
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, targetLineY - 8, w, 26),
                "INPUT", targetInput.name(), "", ""));

        int amountY = y + Math.round(462 * sy);
        int amountH = Math.max(94, Math.round(116 * sy));
        fillRounded(g, x, amountY, w, amountH, Math.max(12, Math.round(14 * sx)), bankPanelSoft());
        drawRoundedBorder(g, x, amountY, w, amountH, Math.max(12, Math.round(14 * sx)), bankLine(), bankPanelSoft());
        drawScaledString(g, font, requestFlow ? "Enter Request Amount" : "Enter Your Amount",
                x + Math.round(16 * sx), amountY + Math.round(29 * sy), bankMuted(), 0.88F);
        String balanceLabel = account == null ? "" : "Balance " + account.shortBalance();
        if (!balanceLabel.isBlank()) {
            int balanceMax = Math.round(128 * sx);
            String balanceShown = trim(font, balanceLabel, balanceMax);
            int balanceW = Math.min(phoneTextWidth(balanceShown, 7.92F), balanceMax);
            drawScaledString(g, font, balanceShown, x + w - balanceW,
                    amountY + Math.round(29 * sy), bankMuted(), 0.88F);
        }
        drawScaledString(g, font, "USD", x + Math.round(16 * sx),
                amountY + Math.round(61 * sy), 0xFF9BB2D4, 1.65F);
        boolean amountActive = SmartphoneClientState.inputTarget() == amountInput;
        String amountShown = amountValue == null || amountValue.isBlank() ? (amountActive ? "" : "0.00") : amountValue.replace("$", "");
        drawScaledString(g, font, trim(font, amountShown, Math.round(w * 0.44F)),
                x + Math.round(81 * sx), amountY + Math.round(61 * sy), bankText(), 1.65F);
        if (amountActive) {
            int maxTextW = Math.round(w * 0.44F);
            int caretX = x + Math.round(81 * sx)
                    + (amountValue == null || amountValue.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, amountShown, maxTextW), 14.85F), maxTextW)) + 2;
            g.fill(caretX, amountY + Math.round(60 * sy), caretX + 1, amountY + Math.round(78 * sy),
                    activeCaretColor(bankText()));
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, amountY, w, amountH),
                "INPUT", amountInput.name(), "", ""));

        int buttonH = Math.max(46, Math.round(56 * sy));
        int buttonY = Math.min(y + Math.round(652 * sy), y + h - buttonH - Math.round(10 * sy));
        figmaPrimaryButton(g, font, x, buttonY, w, buttonH, buttonLabel, accent,
                hitboxes, buttonAction, "", "", "");
        if (SmartphoneClientState.paymentTargetModalOpen()) {
            drawPaymentTargetModal(g, font, x, y, w, h, hitboxes);
        }
    }

    private static void drawPaymentTargetModal(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               List<SmartphoneClientState.Hitbox> hitboxes) {
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        g.fill(x - 9, y - 44, x + w + 9, y + h + 8, 0x99000000);
        int modalW = w - Math.round(36 * sx);
        int modalH = Math.max(196, Math.round(224 * sy));
        int modalX = x + (w - modalW) / 2;
        int modalY = y + Math.round(238 * sy);
        fillRounded(g, modalX, modalY, modalW, modalH, Math.max(18, Math.round(20 * sx)), bankPanelSoft());
        drawRoundedBorder(g, modalX, modalY, modalW, modalH, Math.max(18, Math.round(20 * sx)), bankLine(), bankPanelSoft());
        drawScaledString(g, font, "Add recipient", modalX + Math.round(18 * sx), modalY + Math.round(20 * sy),
                bankText(), 1.18F);
        drawScaledString(g, font, SmartphoneClientState.paymentTargetModalRequest()
                        ? "Use an online player name with a primary account."
                        : "Use an online player name or a bank account ID.",
                modalX + Math.round(18 * sx), modalY + Math.round(47 * sy), bankMuted(), 0.82F);
        SmartphoneClientState.InputTarget target = SmartphoneClientState.paymentTargetModalRequest()
                ? SmartphoneClientState.InputTarget.REQUEST_TARGET
                : SmartphoneClientState.InputTarget.TRANSFER_TO;
        drawBankInputRow(g, font, modalX + Math.round(18 * sx), modalY + Math.round(76 * sy),
                modalW - Math.round(36 * sx), "Recipient", SmartphoneClientState.paymentTargetModalValue(),
                SmartphoneClientState.paymentTargetModalRequest() ? "Online player name" : "Player or Account ID",
                target, hitboxes);

        int buttonY = modalY + modalH - Math.round(58 * sy);
        int gap = Math.max(10, Math.round(12 * sx));
        int cancelW = Math.round((modalW - Math.round(36 * sx) - gap) * 0.42F);
        int confirmW = modalW - Math.round(36 * sx) - gap - cancelW;
        mobileButton(g, font, modalX + Math.round(18 * sx), buttonY, cancelW, Math.max(36, Math.round(42 * sy)),
                "Cancel", bankPanel(), hitboxes, "CLOSE_PAYMENT_TARGET_MODAL", "", "", "");
        mobileButton(g, font, modalX + Math.round(18 * sx) + cancelW + gap, buttonY, confirmW,
                Math.max(36, Math.round(42 * sy)), "Add", BANK_BLUE,
                hitboxes, "CONFIRM_PAYMENT_TARGET_MODAL", "", "", "");
    }

    private static void drawMoneyFlowBackdrop(GuiGraphics g, int x, int y, int w, float sx, float sy) {
        int lineA = y + Math.round(282 * sy);
        int lineB = y + Math.round(414 * sy);
        g.fill(x + Math.round(18 * sx), lineA, x + w - Math.round(22 * sx), lineA + 1, 0x16006BFF);
        g.fill(x + Math.round(46 * sx), lineB, x + w - Math.round(46 * sx), lineB + 1, 0x1018D2FF);
    }

    private static void drawPaymentContacts(GuiGraphics g, Font font, int x, int y, int w, boolean requestFlow,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        int allRecipientCount = SmartphoneClientState.paymentRecipientCount();
        List<SmartphoneClientState.PaymentRecipient> recipients = SmartphoneClientState.paymentRecipients();
        int avatar = Math.max(40, Math.round(48 * (w / 311.0F)));
        float step = (w - avatar) / 4.0F;
        int addCx = x + avatar / 2;
        int addCy = y + avatar / 2;
        float iconScale = avatar / 48.0F;
        g.blit(PAY_ADD_CONTACT_TEXTURE, x, y, avatar, avatar, 0.0F, 0.0F,
                PAY_CONTACT_TEXTURE_SIZE, PAY_CONTACT_TEXTURE_SIZE,
                PAY_CONTACT_TEXTURE_SIZE, PAY_CONTACT_TEXTURE_SIZE);
        drawScaledCentered(g, font, "Add", addCx, y + avatar + 7, bankText(), 0.78F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x - 4, y - 4, avatar + 8, avatar + 28),
                "OPEN_PAYMENT_TARGET_MODAL", requestFlow ? "request" : "send", "", ""));

        int visible = Math.min(4, recipients.size());
        for (int i = 0; i < visible; i++) {
            SmartphoneClientState.PaymentRecipient recipient = recipients.get(i);
            int left = x + Math.round((i + 1) * step);
            int cx = left + avatar / 2;
            int cy = y + avatar / 2;
            String label = recipient.name();
            drawPaymentRecipientAvatar(g, font, recipient, cx, cy, avatar);
            if (recipient.online()) {
                fillCircle(g, cx + Math.round(14 * iconScale), cy + Math.round(14 * iconScale), 4, 0xFF20C985);
            }
            drawScaledCentered(g, font, trim(font, label, Math.max(42, Math.round(step - 4))), cx, y + avatar + 7, bankText(), 0.78F);
            hitboxes.add(new SmartphoneClientState.Hitbox(
                    new SmartphoneClientState.Rect(left - 4, y - 4, avatar + 8, avatar + 28),
                    requestFlow ? "REQUEST_CONTACT" : "TRANSFER_CONTACT",
                    recipient.targetValue(),
                    "",
                    ""));
        }
        if (SmartphoneClientState.paymentContactOffset() > 0) {
            int arrowX = x + avatar + Math.max(2, Math.round(3 * iconScale));
            int arrowY = y + avatar / 2;
            fillCircle(g, arrowX, arrowY, Math.max(9, Math.round(10 * iconScale)), bankPanel());
            drawFigmaChevronLeft(g, arrowX, arrowY, Math.max(4, Math.round(5 * iconScale)), bankText());
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(arrowX - 13, arrowY - 13, 26, 26),
                    "PAYMENT_CONTACTS_PREV", "", "", ""));
        }
        if (SmartphoneClientState.paymentContactOffset() + visible < allRecipientCount) {
            int arrowX = x + w - Math.max(8, Math.round(10 * iconScale));
            int arrowY = y + avatar / 2;
            fillCircle(g, arrowX, arrowY, Math.max(9, Math.round(10 * iconScale)), bankPanel());
            drawFigmaChevronRight(g, arrowX, arrowY, Math.max(4, Math.round(5 * iconScale)), bankText());
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(arrowX - 13, arrowY - 13, 26, 26),
                    "PAYMENT_CONTACTS_NEXT", "", "", ""));
        }
        if (allRecipientCount <= 0) {
            int emptyX = x + avatar + Math.max(12, Math.round(14 * iconScale));
            drawScaledString(g, font, "No players with primary accounts online", emptyX,
                    y + Math.round(18 * iconScale), bankMuted(), 0.78F);
        }
    }

    private static void drawPaymentRecipientAvatar(GuiGraphics g, Font font,
                                                   SmartphoneClientState.PaymentRecipient recipient,
                                                   int cx, int cy, int avatar) {
        String type = recipient == null || recipient.type() == null ? "" : recipient.type().toLowerCase(Locale.ROOT);
        fillCircle(g, cx, cy, avatar / 2, bankInput());
        if ("player".equals(type) && recipient != null && recipient.online()) {
            ResourceLocation skin = resolveSkin(recipient.id());
            if (skin != null) {
                int inset = Math.max(3, Math.round(avatar * 0.09F));
                int left = cx - avatar / 2;
                int top = cy - avatar / 2;
                PlayerFaceRenderer.draw(g, skin, left + inset, top + inset, Math.max(1, avatar - inset * 2));
                maskOutsideCircle(g, left, top, avatar, cx, cy, avatar / 2, bankInput());
                return;
            }
        }
        int color = recipient != null && recipient.recent() ? 0xFFFFBE55 : recipient != null && recipient.online() ? 0xFF20C985 : BANK_BLUE;
        fillCircle(g, cx, cy, Math.max(avatar / 2 - 4, 1), color);
        if ("account".equals(type)) {
            drawCentered(g, font, "$", cx, cy - 4, 0xFFFFFFFF);
        } else {
            drawCentered(g, font, initials(recipient == null ? "" : recipient.name()), cx, cy - 4, 0xFFFFFFFF);
        }
    }

    private static void drawPayRequestRow(GuiGraphics g, Font font, int x, int y, int w,
                                          net.austizz.ultimatebankingsystem.network.PayRequestEntry request,
                                          List<SmartphoneClientState.Hitbox> hitboxes) {
        fillRounded(g, x, y, w, 82, 18, bankPanelSoft());
        drawRoundedBorder(g, x, y, w, 82, 18, bankLine(), bankPanelSoft());
        fillCircle(g, x + 27, y + 28, 18, bankInput());
        fillCircle(g, x + 27, y + 28, 14, BANK_BLUE);
        drawCentered(g, font, initials(request.requesterName()), x + 27, y + 24, 0xFFFFFFFF);
        drawScaledString(g, font, trim(font, request.requesterName(), w - 128), x + 54, y + 11, bankText(), 0.94F);
        drawScaledString(g, font, trim(font, "Requested " + request.createdAt(), w - 128), x + 54, y + 28,
                bankMuted(), 0.78F);
        String amount = request.amount();
        String shownAmount = trim(font, amount, 66);
        drawPhoneText(g, shownAmount, x + w - phoneTextWidth(shownAmount, 9.6F) - 14, y + 13, 0xFF20C985, 9.6F);

        int buttonY = y + 52;
        int half = (w - 12) / 2;
        fillRounded(g, x + 8, buttonY, half, 18, 9, 0xFF20C985);
        drawCentered(g, font, "Accept", x + 8 + half / 2, buttonY + 5, 0xFFFFFFFF);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + 8, buttonY, half, 18),
                "PAY_REQUEST_ACCEPT", request.requestId().toString(), "", ""));
        fillRounded(g, x + 8 + half + 8, buttonY, w - half - 24, 18, 9, 0xFFFF6A6A);
        drawCentered(g, font, "Decline", x + 8 + half + 8 + (w - half - 24) / 2, buttonY + 5, 0xFFFFFFFF);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + 8 + half + 8, buttonY, w - half - 24, 18),
                "PAY_REQUEST_DECLINE", request.requestId().toString(), "", ""));
    }

    private static void drawBankSettings(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Settings", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);
        drawFigmaHeaderButton(g, SETTINGS_LOGOUT_TEXTURE, x + w - circle, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "bank-sign-in", "", ""));

        int rowY = y + Math.round(73 * sy);
        drawScaledString(g, font, "General", x, rowY, bankMuted(), 1.0F);
        rowY = y + Math.round(120 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Language", "",
                "APP", "bank-language", hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "My Profile", "",
                "APP", "bank-profile", hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Contact Us", "",
                "APP", "messenger", hitboxes);
        rowY += Math.round(56 * sy);
        boolean lightMode = SmartphoneClientState.bankLightMode();
        drawFigmaSettingsToggleRow(g, font, x, rowY, w, "Light Mode", lightMode,
                hitboxes, "BANK_THEME", lightMode ? "dark" : "light");

        rowY = y + Math.round(353 * sy);
        drawScaledString(g, font, "Security", x, rowY, bankMuted(), 1.0F);
        rowY = y + Math.round(400 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Change Password", "",
                "APP", "bank-change-password", hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Privacy Policy", "",
                "APP", "bank-terms", hitboxes);

        rowY += Math.round(56 * sy);
        drawWrapped(g, font,
                "Choose what data you share with UBS mobile banking.",
                x, rowY + Math.round(2 * sy), w - Math.round(20 * sx), bankMuted(), 2);
        rowY += Math.round(58 * sy);
        drawFigmaSettingsToggleRow(g, font, x, rowY, w, "Biometric", true);

        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "settings", hitboxes);
    }

    private static void drawBankStaff(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Bank Staff", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        SmartphoneClientState.OwnedBank bank = SmartphoneClientState.ownedBank();
        if (bank == null) {
            drawEmptyState(g, font, x, y + Math.round(116 * sy), w,
                    "No owned bank", "Only bank owners can manage staff here.");
            return;
        }

        int summaryY = y + Math.round(72 * sy);
        int summaryH = Math.max(90, Math.round(104 * sy));
        fillRounded(g, x, summaryY, w, summaryH, Math.max(16, Math.round(18 * sx)), bankPanelSoft());
        drawRoundedBorder(g, x, summaryY, w, summaryH, Math.max(16, Math.round(18 * sx)), bankLine(), bankPanelSoft());
        fillCircle(g, x + Math.round(34 * sx), summaryY + Math.round(34 * sy), Math.max(20, Math.round(23 * sx)), bankInput());
        drawFigmaBottomIcon(g, "settings", x + Math.round(34 * sx), summaryY + Math.round(34 * sy), BANK_BLUE, 1.0F);
        drawScaledString(g, font, trim(font, bank.name(), w - Math.round(86 * sx)),
                x + Math.round(68 * sx), summaryY + Math.round(18 * sy), bankText(), 1.08F);
        drawScaledString(g, font, bank.status() + " | " + bank.employeeCount() + " staff",
                x + Math.round(68 * sx), summaryY + Math.round(43 * sy), bankMuted(), 0.88F);
        drawScaledString(g, font, trim(font, "Hire or fire staff for this bank.", w - Math.round(86 * sx)),
                x + Math.round(68 * sx), summaryY + Math.round(64 * sy), bankMuted(), 0.82F);

        int fieldY = y + Math.round(204 * sy);
        drawBankInputRow(g, font, x, fieldY, w, "Player", SmartphoneClientState.staffPlayer(),
                "Online name or UUID", SmartphoneClientState.InputTarget.STAFF_PLAYER, hitboxes);
        fieldY += Math.round(74 * sy);
        drawBankInputRow(g, font, x, fieldY, w, "Role", SmartphoneClientState.staffRole(),
                "TELLER, DIRECTOR, AUDITOR, STAFF", SmartphoneClientState.InputTarget.STAFF_ROLE, hitboxes);
        fieldY += Math.round(74 * sy);
        drawBankInputRow(g, font, x, fieldY, w, "Salary", SmartphoneClientState.staffSalary(),
                "0", SmartphoneClientState.InputTarget.STAFF_SALARY, hitboxes);

        int buttonY = fieldY + Math.round(76 * sy);
        int buttonH = Math.max(36, Math.round(42 * sy));
        int gap = Math.max(10, Math.round(12 * sx));
        int half = (w - gap) / 2;
        mobileButton(g, font, x, buttonY, half, buttonH, "Hire", 0xFF20C985,
                hitboxes, "BANK_HIRE", "", "", "");
        mobileButton(g, font, x + half + gap, buttonY, w - half - gap, buttonH, "Fire", 0xFFFF6A6A,
                hitboxes, "BANK_FIRE", "", "", "");

        List<SmartphoneClientState.BankStaffMember> staff = SmartphoneClientState.bankStaffMembers();
        int rosterY = buttonY + buttonH + Math.round(18 * sy);
        drawScaledString(g, font, "Current Staff", x, rosterY, bankText(), 1.05F);
        int rowY = rosterY + Math.round(25 * sy);
        int rowH = Math.max(42, Math.round(48 * sy));
        int maxRows = Math.min(staff.size(), 2);
        if (staff.isEmpty()) {
            fillRounded(g, x, rowY, w, rowH, Math.max(12, Math.round(14 * sx)), bankPanelSoft());
            drawRoundedBorder(g, x, rowY, w, rowH, Math.max(12, Math.round(14 * sx)), bankLine(), bankPanelSoft());
            drawScaledString(g, font, "No active staff yet", x + Math.round(14 * sx), rowY + Math.round(10 * sy),
                    bankText(), 0.95F);
            drawScaledString(g, font, "Hire someone above to fill this roster.", x + Math.round(14 * sx),
                    rowY + Math.round(27 * sy), bankMuted(), 0.78F);
            rowY += rowH + Math.round(8 * sy);
        } else {
            for (int i = 0; i < maxRows; i++) {
                drawBankStaffRow(g, font, x, rowY, w, rowH, staff.get(i), hitboxes);
                rowY += rowH + Math.round(8 * sy);
            }
            if (staff.size() > maxRows) {
                drawScaledString(g, font, "+" + (staff.size() - maxRows) + " more staff member(s)",
                        x + Math.round(14 * sx), rowY, bankMuted(), 0.78F);
                rowY += Math.round(18 * sy);
            }
        }

        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "settings", hitboxes);
    }

    private static void drawBankDissolve(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Dissolve Bank", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        SmartphoneClientState.OwnedBank bank = SmartphoneClientState.ownedBank();
        if (bank == null) {
            drawEmptyState(g, font, x, y + Math.round(116 * sy), w,
                    "No owned bank", "Only bank owners can dissolve a bank here.");
            return;
        }

        int summaryY = y + Math.round(86 * sy);
        int summaryH = Math.max(142, Math.round(168 * sy));
        fillRounded(g, x, summaryY, w, summaryH, Math.max(18, Math.round(20 * sx)), bankPanelSoft());
        drawRoundedBorder(g, x, summaryY, w, summaryH, Math.max(18, Math.round(20 * sx)), bankLine(), bankPanelSoft());
        fillCircle(g, x + Math.round(40 * sx), summaryY + Math.round(40 * sy), Math.max(24, Math.round(28 * sx)), bankInput());
        drawFigmaBottomIcon(g, "settings", x + Math.round(40 * sx), summaryY + Math.round(40 * sy), BANK_DANGER, 1.0F);
        drawScaledString(g, font, trim(font, bank.name(), w - Math.round(92 * sx)),
                x + Math.round(82 * sx), summaryY + Math.round(21 * sy), bankText(), 1.12F);
        drawScaledString(g, font, bank.status() + " | " + bank.employeeCount() + " staff",
                x + Math.round(82 * sx), summaryY + Math.round(47 * sy), bankMuted(), 0.88F);
        drawWrapped(g, font,
                "This moves positive account balances into Central Bank checking accounts, clears employees, and removes the owned bank.",
                x + Math.round(18 * sx), summaryY + Math.round(86 * sy), w - Math.round(36 * sx), bankMuted(), 4);

        int fieldY = summaryY + summaryH + Math.round(42 * sy);
        drawBankInputRow(g, font, x, fieldY, w, "Confirmation",
                SmartphoneClientState.disbandConfirm(), "Type DISSOLVE", SmartphoneClientState.InputTarget.DISBAND_CONFIRM, hitboxes);

        int buttonH = Math.max(42, Math.round(50 * sy));
        int buttonY = Math.min(y + Math.round(528 * sy), y + h - Math.max(72, Math.round(86 * sy)) - buttonH - 8);
        mobileButton(g, font, x, buttonY, w, buttonH, "Dissolve Bank", 0xFFED1B3A,
                hitboxes, "DISBAND_BANK", "", "", "");

        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "settings", hitboxes);
    }

    private static void drawBankStaffRow(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         SmartphoneClientState.BankStaffMember member,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        fillRounded(g, x, y, w, h, 12, bankPanelSoft());
        drawRoundedBorder(g, x, y, w, h, 12, bankLine(), bankPanelSoft());
        int avatar = Math.max(28, Math.min(36, h - 10));
        int cx = x + 10 + avatar / 2;
        int cy = y + h / 2;
        fillCircle(g, cx, cy, avatar / 2, bankInput());
        fillCircle(g, cx, cy, Math.max(avatar / 2 - 4, 1), BANK_BLUE);
        drawCentered(g, font, initials(member == null ? "" : member.name()), cx, cy - 4, bankText());
        int textX = x + 18 + avatar;
        int actionW = Math.max(46, Math.round(w * 0.18F));
        String name = member == null ? "Staff" : member.name();
        String role = member == null ? "STAFF" : member.role();
        String salary = member == null ? "$0" : member.salary();
        drawScaledString(g, font, trim(font, name, w - actionW * 2 - avatar - 32), textX, y + 7, bankText(), 0.92F);
        drawScaledString(g, font, trim(font, role + " | " + salary, w - actionW * 2 - avatar - 32),
                textX, y + 23, bankMuted(), 0.76F);
        int fireX = x + w - actionW - 8;
        mobileButton(g, font, fireX, y + (h - 22) / 2, actionW, 22, "Fire", 0xFFFF6A6A,
                hitboxes, "BANK_FIRE_MEMBER", member == null ? "" : member.id().toString(), "", "");
        int useX = fireX - actionW - 7;
        mobileButton(g, font, useX, y + (h - 22) / 2, actionW, 22, "Select", bankPanel(),
                hitboxes, "BANK_STAFF_SELECT", member == null ? "" : member.id().toString(), "", "");
    }

    private static void drawFigmaSettingsRow(GuiGraphics g, Font font, int x, int y, int w,
                                             String title, String value, String action, String p1,
                                             List<SmartphoneClientState.Hitbox> hitboxes) {
        drawScaledString(g, font, title, x, y + 4, bankText(), 1.02F);
        if (value != null && !value.isBlank()) {
            int valueW = phoneTextWidth(value, font.lineHeight * 0.98F);
            drawScaledString(g, font, value, x + w - valueW - 40, y + 4, bankMuted(), 0.98F);
        }
        drawFigmaArrowRight(g, x + w - 12, y + 12, Math.max(18, Math.round(24 * (w / 335.0F))), bankMuted());
        g.fill(x, y + 34, x + w, y + 35, bankLine());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - 8, w, 48),
                action, p1, "", ""));
    }

    private static void drawFigmaSettingsToggleRow(GuiGraphics g, Font font, int x, int y, int w,
                                                   String title, boolean enabled) {
        drawScaledString(g, font, title, x, y + 4, bankText(), 1.02F);
        drawFigmaToggle(g, x + w - 24, y + 13, enabled);
        g.fill(x, y + 34, x + w, y + 35, bankLine());
    }

    private static void drawFigmaSettingsToggleRow(GuiGraphics g, Font font, int x, int y, int w,
                                                   String title, boolean enabled,
                                                   List<SmartphoneClientState.Hitbox> hitboxes,
                                                   String action, String p1) {
        drawFigmaSettingsToggleRow(g, font, x, y, w, title, enabled);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - 8, w, 48),
                action, p1, "", ""));
    }

    private static void drawBankInputRow(GuiGraphics g, Font font, int x, int y, int w,
                                         String label, String value, String placeholder,
                                         SmartphoneClientState.InputTarget target,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean active = SmartphoneClientState.inputTarget() == target;
        drawScaledString(g, font, label, x, y, bankMuted(), 0.92F);
        int rowY = y + 24;
        int rowH = 42;
        fillRounded(g, x, rowY, w, rowH, 12, active ? bankInputActive() : bankInput());
        drawRoundedBorder(g, x, rowY, w, rowH, 12, active ? BANK_BLUE : bankLine(),
                active ? bankInputActive() : bankInput());
        String actual = value == null ? "" : value;
        String shown = actual.isBlank() ? (active ? "" : placeholder) : actual;
        drawScaledString(g, font, trim(font, shown, w - 26), x + 13, rowY + 14,
                actual.isBlank() ? bankMuted() : bankText(), 0.98F);
        if (active) {
            int caretX = x + 13 + (actual.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, actual, w - 26), 8.82F), w - 26)) + 2;
            g.fill(caretX, rowY + 13, caretX + 1, rowY + 28, activeCaretColor(bankText()));
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, rowY, w, rowH),
                "INPUT", target.name(), "", ""));
    }

    private static void drawLogoutGlyph(GuiGraphics g, int centerX, int centerY, int color) {
        g.fill(centerX - 8, centerY - 9, centerX - 5, centerY + 9, color);
        g.fill(centerX - 8, centerY - 9, centerX + 2, centerY - 7, color);
        g.fill(centerX - 8, centerY + 7, centerX + 2, centerY + 9, color);
        g.fill(centerX + 1, centerY - 1, centerX + 9, centerY + 2, color);
        g.fill(centerX + 5, centerY - 5, centerX + 10, centerY + 2, color);
        g.fill(centerX + 5, centerY + 2, centerX + 10, centerY + 7, color);
    }

    private static void drawFigmaToggle(GuiGraphics g, int centerX, int centerY, boolean enabled) {
        int bg = enabled ? BANK_BLUE : bankMuted();
        fillRounded(g, centerX - 18, centerY - 10, 36, 20, 10, bg);
        fillCircle(g, centerX + (enabled ? 8 : -8), centerY, 8, 0xFFFFFFFF);
    }

    private static void drawBankProfile(GuiGraphics g, Font font, int x, int y, int w, int h,
                                        List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Profile", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, bankPanelSoft());
        drawFigmaProfileIcon(g, "edit", x + w - circle / 2, y + circle / 2, bankText());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "bank-edit-profile", "", ""));

        int avatar = Math.max(56, Math.round(70 * sx));
        int profileY = y + Math.round(74 * sy);
        drawFigmaAvatar(g, font, x, profileY, avatar);
        drawScaledString(g, font, trim(font, SmartphoneClientState.ownerName(), Math.round(w * 0.58F)),
                x + Math.round(92 * sx), profileY + Math.round(14 * sy), bankText(), 1.24F);
        String role = selected == null || selected.role() == null || selected.role().isBlank()
                ? "Account owner" : selected.role();
        drawScaledString(g, font, trim(font, role, Math.round(w * 0.55F)),
                x + Math.round(92 * sx), profileY + Math.round(43 * sy), bankMuted(), 0.95F);

        int rowY = y + Math.round(176 * sy);
        drawFigmaProfileRow(g, font, x, rowY, w, "user", "Personal Information", "APP", "bank-edit-profile", false, hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaProfileRow(g, font, x, rowY, w, "cards", "Payment Preferences", "APP", "tap", false, hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaProfileRow(g, font, x, rowY, w, "card-edit", "Banks and Cards", "APP", "all-cards", false, hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaProfileRow(g, font, x, rowY, w, "bell", "Notifications", "APP", "bank-pay-requests", false, hitboxes);
        rowY += Math.round(54 * sy);
        drawFigmaProfileRow(g, font, x, rowY, w, "message", "Message Center", "APP", "messenger", false, hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaProfileRow(g, font, x, rowY, w, "pin", "Contacts", "APP", "contacts", false, hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaProfileRow(g, font, x, rowY, w, "settings", "Settings", "APP", "bank-settings", false, hitboxes);
        SmartphoneClientState.OwnedBank ownedBank = SmartphoneClientState.ownedBank();
        if (ownedBank != null) {
            rowY += Math.round(56 * sy);
            drawFigmaProfileRow(g, font, x, rowY, w, "user", "Bank Staff", "APP", "bank-staff", false, hitboxes);
            rowY += Math.round(56 * sy);
            drawFigmaProfileRow(g, font, x, rowY, w, "settings", "Dissolve Bank", "APP", "bank-dissolve", false, hitboxes);
        }
    }

    private static void drawFigmaProfileRow(GuiGraphics g, Font font, int x, int y, int w,
                                            String icon, String label, String action, String p1, boolean badge,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        drawFigmaProfileIcon(g, icon, x + 11, y + 11, bankMuted());
        drawScaledString(g, font, label, x + Math.round(w * 38.0F / 335.0F), y + 4, bankText(), 1.03F);
        if (badge) {
            fillCircle(g, x + w - 14, y + 11, 9, 0xFFED1B3A);
            drawCentered(g, font, "2", x + w - 14, y + 7, 0xFFFFFFFF);
        } else {
            drawFigmaArrowRight(g, x + w - 12, y + 12, Math.max(18, Math.round(24 * (w / 335.0F))));
        }
        g.fill(x, y + 34, x + w, y + 35, bankLine());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - 8, w, 48),
                action, p1, "", ""));
    }

    private static void drawFigmaArrowRight(GuiGraphics g, int centerX, int centerY, int size) {
        drawFigmaArrowRight(g, centerX, centerY, size, bankMuted());
    }

    private static void drawFigmaArrowRight(GuiGraphics g, int centerX, int centerY, int size, int color) {
        drawFigmaChevronRight(g, centerX, centerY, Math.max(5, size / 4), color);
    }

    private static void drawFigmaProfileIcon(GuiGraphics g, String icon, int centerX, int centerY, int color) {
        String id = icon == null ? "" : icon.toLowerCase(Locale.ROOT);
        ResourceLocation texture = profileIconTexture(id);
        if (texture != null) {
            int size = "edit".equals(id) ? 22 : 22;
            g.blit(texture, centerX - size / 2, centerY - size / 2, size, size,
                    0.0F, 0.0F, PROFILE_ICON_TEXTURE_SIZE, PROFILE_ICON_TEXTURE_SIZE,
                    PROFILE_ICON_TEXTURE_SIZE, PROFILE_ICON_TEXTURE_SIZE);
            return;
        }
        switch (id) {
            case "user", "edit" -> {
                fillCircle(g, centerX, centerY - 3, 5, color);
                fillCircle(g, centerX, centerY - 3, 3, 0xFFFFFFFF);
                fillRounded(g, centerX - 8, centerY + 4, 16, 8, 5, color);
                fillRounded(g, centerX - 6, centerY + 6, 12, 4, 3, 0xFFFFFFFF);
                if ("edit".equals(id)) {
                    g.fill(centerX + 5, centerY + 2, centerX + 10, centerY + 4, color);
                    g.fill(centerX + 8, centerY - 1, centerX + 10, centerY + 6, color);
                }
            }
            case "cards", "card-edit" -> {
                fillRounded(g, centerX - 9, centerY - 6, 18, 13, 3, color);
                g.fill(centerX - 7, centerY - 2, centerX + 7, centerY, 0xFFFFFFFF);
                if ("card-edit".equals(id)) {
                    g.fill(centerX + 3, centerY + 4, centerX + 9, centerY + 6, color);
                }
            }
            case "bell" -> drawBellGlyph(g, centerX, centerY, color);
            case "message" -> {
                fillRounded(g, centerX - 9, centerY - 7, 18, 13, 5, color);
                g.fill(centerX - 4, centerY + 5, centerX, centerY + 10, color);
                g.fill(centerX - 5, centerY - 1, centerX - 3, centerY + 1, 0xFFFFFFFF);
                g.fill(centerX, centerY - 1, centerX + 2, centerY + 1, 0xFFFFFFFF);
                g.fill(centerX + 5, centerY - 1, centerX + 7, centerY + 1, 0xFFFFFFFF);
            }
            case "email" -> {
                fillRounded(g, centerX - 9, centerY - 6, 18, 13, 3, color);
                g.fill(centerX - 7, centerY - 3, centerX + 8, centerY - 1, 0xFFFFFFFF);
                g.fill(centerX - 5, centerY + 1, centerX + 6, centerY + 3, 0xFFFFFFFF);
            }
            case "phone" -> {
                g.fill(centerX - 7, centerY - 8, centerX - 3, centerY + 3, color);
                g.fill(centerX - 3, centerY + 3, centerX + 7, centerY + 7, color);
                g.fill(centerX + 5, centerY + 2, centerX + 9, centerY + 8, color);
            }
            case "lock" -> {
                fillRounded(g, centerX - 8, centerY - 1, 16, 12, 3, color);
                fillCircle(g, centerX, centerY - 4, 7, color);
                fillCircle(g, centerX, centerY - 4, 4, 0xFFFFFFFF);
                g.fill(centerX - 7, centerY - 2, centerX + 8, centerY + 2, color);
            }
            case "pin" -> {
                fillCircle(g, centerX, centerY - 2, 8, color);
                fillCircle(g, centerX, centerY - 2, 4, 0xFFFFFFFF);
                g.fill(centerX - 2, centerY + 5, centerX + 3, centerY + 12, color);
            }
            case "settings" -> drawFigmaBottomIcon(g, "settings", centerX, centerY, color, 1.0F);
            default -> fillCircle(g, centerX, centerY, 8, color);
        }
    }

    private static ResourceLocation profileIconTexture(String id) {
        return switch (id) {
            case "edit" -> PROFILE_EDIT_TEXTURE;
            case "user" -> PROFILE_USER_TEXTURE;
            case "cards" -> PROFILE_CARDS_TEXTURE;
            case "card-edit" -> PROFILE_CARD_EDIT_TEXTURE;
            case "email" -> PROFILE_EMAIL_TEXTURE;
            case "phone" -> PROFILE_PHONE_TEXTURE;
            case "bell" -> PROFILE_BELL_TEXTURE;
            case "message" -> PROFILE_MESSAGE_TEXTURE;
            case "pin" -> PROFILE_PIN_TEXTURE;
            case "settings" -> PROFILE_SETTINGS_TEXTURE;
            default -> null;
        };
    }

    private static void drawBankEditProfile(GuiGraphics g, Font font, int x, int y, int w, int h,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-profile", "", ""));
        drawScaledCentered(g, font, "Edit Profile", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        int avatar = Math.max(72, Math.round(90 * sx));
        int avatarX = x + w / 2 - avatar / 2;
        int avatarY = y + Math.round(74 * sy);
        drawFigmaAvatar(g, font, avatarX, avatarY, avatar);
        drawScaledCentered(g, font, trim(font, SmartphoneClientState.profileNameDraft(), Math.round(w * 0.64F)),
                x + w / 2, y + Math.round(185 * sy), bankText(), 1.28F);
        String role = selected == null || selected.role() == null || selected.role().isBlank()
                ? "Account owner" : selected.role();
        drawScaledCentered(g, font, trim(font, role, Math.round(w * 0.54F)),
                x + w / 2, y + Math.round(214 * sy), bankMuted(), 0.95F);

        int fieldY = y + Math.round(258 * sy);
        drawFigmaProfileField(g, font, x, fieldY, w, "user", "Full Name",
                SmartphoneClientState.profileNameDraft(), SmartphoneClientState.InputTarget.PROFILE_NAME, hitboxes);
        fieldY += Math.round(84 * sy);
        drawFigmaProfileField(g, font, x, fieldY, w, "cards", "Selected Account",
                selected == null ? "No account" : selected.appTitle(), SmartphoneClientState.InputTarget.NONE, hitboxes);
        fieldY += Math.round(84 * sy);
        drawFigmaProfileField(g, font, x, fieldY, w, "pin", "PIN Status",
                selected == null ? "No account" : selected.pinSet() ? "Configured" : "Not set", SmartphoneClientState.InputTarget.NONE, hitboxes);
        fieldY += Math.round(84 * sy);
        drawFigmaProfileField(g, font, x, fieldY, w, "settings", "Access",
                selected == null ? "None" : selected.accessType(), SmartphoneClientState.InputTarget.NONE, hitboxes);
    }

    private static void drawFigmaProfileField(GuiGraphics g, Font font, int x, int y, int w,
                                              String icon, String label, String value,
                                              SmartphoneClientState.InputTarget inputTarget,
                                              List<SmartphoneClientState.Hitbox> hitboxes) {
        drawScaledString(g, font, label, x, y, bankMuted(), 1.0F);
        int rowY = y + 31;
        drawFigmaProfileIcon(g, icon, x + 11, rowY + 11, bankMuted());
        String shown = value == null ? "" : value;
        boolean active = SmartphoneClientState.inputTarget() == inputTarget && inputTarget != SmartphoneClientState.InputTarget.NONE;
        String display = shown.isBlank() && inputTarget != SmartphoneClientState.InputTarget.NONE && !active ? "Type here" : shown;
        drawScaledString(g, font, trim(font, display, w - 58),
                x + Math.round(w * 38.0F / 335.0F), rowY + 3,
                shown.isBlank() ? bankMuted() : bankText(), 1.02F);
        if (active) {
            int caretX = x + Math.round(w * 38.0F / 335.0F)
                    + (shown.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, shown, w - 58), 9.18F), w - 58)) + 2;
            g.fill(caretX, rowY + 2, caretX + 1, rowY + 13, activeCaretColor(bankText()));
        }
        g.fill(x, rowY + 32, x + w, rowY + 33, bankLine());
        if (inputTarget != SmartphoneClientState.InputTarget.NONE) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, rowY - 7, w, 46),
                    "INPUT", inputTarget.name(), "", ""));
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, rowY + 33, w, 18),
                    "SAVE_BANK_PROFILE", "", "", ""));
        }
    }

    private static void drawFigmaDateValue(GuiGraphics g, Font font, int x, int y, int w, String value) {
        drawScaledCentered(g, font, value, x + w / 2, y, bankText(), 1.0F);
        g.fill(x, y + 25, x + w, y + 26, bankLine());
    }

    private static void drawBankSignIn(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, 0xFFFFFFFF, bankBg());
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.signInAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        fillCircle(g, x + circle / 2, y + circle / 2, circle / 2, bankPanelSoft());
        drawFigmaChevronLeft(g, x + circle / 2, y + circle / 2, Math.max(7, Math.round(8 * sx)), 0xFFFFFFFF);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));

        int titleY = y + Math.round(95 * sy);
        drawScaledString(g, font, "Sign In", x, titleY, 0xFFFFFFFF, 1.9F);

        int emailY = y + Math.round(167 * sy);
        drawScaledString(g, font, "Email Address", x, emailY, bankMuted(), 0.92F);
        int emailRowY = emailY + Math.round(31 * sy);
        drawDarkMailGlyph(g, x + Math.round(11 * sx), emailRowY + Math.round(11 * sy), bankMuted());
        String accountLabel = account == null ? "No account selected" : account.appTitle();
        drawScaledString(g, font, trim(font, accountLabel, w - Math.round(58 * sx)),
                x + Math.round(38 * sx), emailRowY + Math.round(3 * sy), 0xFFFFFFFF, 0.95F);
        g.fill(x, emailRowY + Math.round(32 * sy), x + w, emailRowY + Math.round(33 * sy), bankLine());

        int passwordY = y + Math.round(251 * sy);
        drawScaledString(g, font, "Password", x, passwordY, bankMuted(), 0.92F);
        int passRowY = passwordY + Math.round(31 * sy);
        drawDarkLockGlyph(g, x + Math.round(11 * sx), passRowY + Math.round(11 * sy), bankMuted(), bankBg());
        String pinDots = passwordDots(SmartphoneClientState.bankPin());
        boolean pinInputActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.BANK_PIN;
        drawScaledString(g, font, pinDots.isBlank() ? (pinInputActive ? "" : "Account PIN") : pinDots,
                x + Math.round(38 * sx), passRowY + Math.round(3 * sy), pinDots.isBlank() ? bankMuted() : 0xFFFFFFFF, 0.95F);
        if (pinInputActive) {
            int caretX = x + Math.round(38 * sx) + (pinDots.isBlank() ? 0 : Math.min(phoneTextWidth(pinDots, 8.55F), w - Math.round(58 * sx))) + 2;
            g.fill(caretX, passRowY + Math.round(2 * sy), caretX + 1, passRowY + Math.round(14 * sy),
                    activeCaretColor(0xFFFFFFFF));
        }
        drawEyeGlyph(g, x + w - Math.round(13 * sx), passRowY + Math.round(11 * sy), bankMuted(), bankBg());
        g.fill(x, passRowY + Math.round(32 * sy), x + w, passRowY + Math.round(33 * sy), bankLine());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, passwordY - Math.round(8 * sy),
                w, Math.round(58 * sy)), "INPUT", SmartphoneClientState.InputTarget.BANK_PIN.name(), "", ""));

        int buttonY = y + Math.round(354 * sy);
        int buttonH = Math.max(46, Math.round(56 * sy));
        figmaPrimaryButton(g, font, x, buttonY, w, buttonH, "Sign In", BANK_BLUE,
                hitboxes, "BANK_SIGN_IN", "", "", "");

        int copyY = y + Math.round(438 * sy);
        drawScaledCentered(g, font, "I'm a new user.", x + w / 2 - Math.round(28 * sx), copyY, bankMuted(), 0.86F);
        drawScaledString(g, font, "Sign Up", x + w / 2 + Math.round(16 * sx), copyY, BANK_BLUE, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w / 2 + Math.round(10 * sx), copyY - 6,
                Math.round(70 * sx), 20), "APP", "bank-welcome", "", ""));
    }

    private static void drawBankLanguage(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Language", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        int searchY = y + Math.round(74 * sy);
        int searchH = Math.max(38, Math.round(44 * sy));
        fillRounded(g, x, searchY, w, searchH, Math.max(9, Math.round(10 * sx)), bankInput());
        int searchGlyph = Math.max(18, Math.round(20 * sx));
        drawSearchGlyph(g, x + Math.round(12 * sx) + searchGlyph / 2, searchY + searchH / 2, bankMuted());
        drawScaledString(g, font, "Search Language", x + Math.round(40 * sx),
                searchY + (searchH - 9) / 2, bankMuted(), 1.0F);

        int rowY = y + Math.round(150 * sy);
        drawFigmaLanguageRow(g, font, x, rowY, w, "English", "us", hitboxes);
        rowY += Math.round(80 * sy);
        drawFigmaLanguageRow(g, font, x, rowY, w, "Dutch", "nl", hitboxes);
        rowY += Math.round(80 * sy);
        drawFigmaLanguageRow(g, font, x, rowY, w, "French", "fr", hitboxes);
        rowY += Math.round(80 * sy);
        drawFigmaLanguageRow(g, font, x, rowY, w, "German", "de", hitboxes);
        rowY += Math.round(80 * sy);
        drawFigmaLanguageRow(g, font, x, rowY, w, "American English", "am", hitboxes);
    }

    private static void drawFigmaLanguageRow(GuiGraphics g, Font font, int x, int y, int w, String language, String flag,
                                             List<SmartphoneClientState.Hitbox> hitboxes) {
        int flagR = Math.max(20, Math.round(w * 24.0F / 335.0F));
        drawFlagCircle(g, x + flagR, y + flagR, flagR, flag);
        drawScaledString(g, font, language, x + Math.round(w * 64.0F / 335.0F), y + Math.round(w * 15.0F / 335.0F),
                bankText(), 1.15F);
        if (language.equalsIgnoreCase(SmartphoneClientState.bankLanguage())) {
            fillCircle(g, x + w - 10, y + flagR, 10, BANK_BLUE);
            drawFigmaCheckGlyph(g, x + w - 10, y + flagR, 0xFFFFFFFF);
        }
        g.fill(x, y + flagR * 2 + 10, x + w, y + flagR * 2 + 11, bankLine());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - 8, w, flagR * 2 + 20),
                "BANK_LANGUAGE", language, "", ""));
    }

    private static void drawFlagCircle(GuiGraphics g, int centerX, int centerY, int radius, String id) {
        ResourceLocation texture = languageFlagTexture(id);
        if (texture != null) {
            int size = radius * 2;
            g.blit(texture, centerX - radius, centerY - radius, size, size,
                    0.0F, 0.0F, LANGUAGE_FLAG_TEXTURE_SIZE, LANGUAGE_FLAG_TEXTURE_SIZE,
                    LANGUAGE_FLAG_TEXTURE_SIZE, LANGUAGE_FLAG_TEXTURE_SIZE);
            return;
        }
        switch (id) {
            case "us", "am" -> {
                fillCircle(g, centerX, centerY, radius, 0xFFFFFFFF);
                int stripe = Math.max(2, radius / 6);
                for (int yy = centerY - radius; yy < centerY + radius; yy += stripe * 2) {
                    fillCircleHorizontalBand(g, centerX, centerY, radius, yy, yy + stripe, 0xFFFF2F45);
                }
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX - radius, centerY - radius, centerX - Math.max(1, radius / 8), centerY - Math.max(1, radius / 5),
                        0xFF224A9A);
                int dot = Math.max(1, radius / 12);
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 4; col++) {
                        int px = centerX - radius + Math.round((5 + col * 8) * radius / 24.0F);
                        int py = centerY - radius + Math.round((5 + row * 6) * radius / 24.0F);
                        fillCircle(g, px, py, dot, 0xFFFFFFFF);
                    }
                }
            }
            case "fr" -> {
                fillCircle(g, centerX, centerY, radius, 0xFFFFFFFF);
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX - radius, centerY - radius, centerX - radius / 3, centerY + radius,
                        0xFF365CBF);
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX + radius / 3, centerY - radius, centerX + radius, centerY + radius,
                        0xFFFF334E);
            }
            case "es" -> {
                fillCircle(g, centerX, centerY, radius, 0xFFFF2E2E);
                fillCircleHorizontalBand(g, centerX, centerY, radius,
                        centerY - radius / 2, centerY + radius / 2, 0xFFFFD33D);
                fillCircle(g, centerX - radius / 3, centerY, Math.max(2, radius / 7), 0xFFFF2E2E);
            }
            case "vn" -> {
                fillCircle(g, centerX, centerY, radius, 0xFFFF2E2E);
                drawFlagStar(g, centerX, centerY, Math.max(8, radius / 2), 0xFFFFE05C);
            }
            case "au" -> {
                fillCircle(g, centerX, centerY, radius, 0xFF001B75);
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX - radius, centerY - radius, centerX, centerY, 0xFF1E4FA3);
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX - radius, centerY - radius / 5, centerX, centerY + radius / 5, 0xFFFFFFFF);
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX - radius / 2, centerY - radius, centerX - radius / 4, centerY, 0xFFFFFFFF);
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX - radius, centerY - radius / 9, centerX, centerY + radius / 9, 0xFFFF2F45);
                fillCircleClippedRect(g, centerX, centerY, radius,
                        centerX - radius / 2 + radius / 18, centerY - radius, centerX - radius / 4 - radius / 18, centerY, 0xFFFF2F45);
                drawFlagStar(g, centerX + radius / 3, centerY + radius / 4, Math.max(4, radius / 4), 0xFFFFFFFF);
            }
            default -> fillCircle(g, centerX, centerY, radius, 0xFFFFFFFF);
        }
    }

    private static ResourceLocation languageFlagTexture(String id) {
        return switch (id == null ? "" : id) {
            case "us" -> LANGUAGE_US_TEXTURE;
            case "au" -> LANGUAGE_AU_TEXTURE;
            case "fr" -> LANGUAGE_FR_TEXTURE;
            case "es" -> LANGUAGE_ES_TEXTURE;
            case "am" -> LANGUAGE_AM_TEXTURE;
            case "vn" -> LANGUAGE_VN_TEXTURE;
            default -> null;
        };
    }

    private static void fillCircleHorizontalBand(GuiGraphics g, int centerX, int centerY, int radius,
                                                 int top, int bottom, int color) {
        int y0 = Math.max(centerY - radius, top);
        int y1 = Math.min(centerY + radius, bottom);
        for (int py = y0; py < y1; py++) {
            double dy = py + 0.5D - centerY;
            int span = (int) Math.floor(Math.sqrt(Math.max(0.0D, radius * radius - dy * dy)));
            g.fill(centerX - span, py, centerX + span + 1, py + 1, color);
        }
    }

    private static void fillCircleClippedRect(GuiGraphics g, int centerX, int centerY, int radius,
                                              int left, int top, int right, int bottom, int color) {
        int y0 = Math.max(centerY - radius, top);
        int y1 = Math.min(centerY + radius, bottom);
        for (int py = y0; py < y1; py++) {
            double dy = py + 0.5D - centerY;
            int span = (int) Math.floor(Math.sqrt(Math.max(0.0D, radius * radius - dy * dy)));
            int rowLeft = Math.max(centerX - span, left);
            int rowRight = Math.min(centerX + span + 1, right);
            if (rowLeft < rowRight) {
                g.fill(rowLeft, py, rowRight, py + 1, color);
            }
        }
    }

    private static void drawFlagStar(GuiGraphics g, int centerX, int centerY, int radius, int color) {
        g.fill(centerX - 1, centerY - radius, centerX + 2, centerY + radius + 1, color);
        g.fill(centerX - radius, centerY - 1, centerX + radius + 1, centerY + 2, color);
        for (int i = 0; i <= radius; i++) {
            int half = Math.max(1, radius / 5);
            g.fill(centerX - i, centerY - i / 2 - half / 2, centerX - i + half, centerY - i / 2 + half / 2 + 1, color);
            g.fill(centerX + i - half, centerY - i / 2 - half / 2, centerX + i, centerY - i / 2 + half / 2 + 1, color);
            g.fill(centerX - i, centerY + i / 2 - half / 2, centerX - i + half, centerY + i / 2 + half / 2 + 1, color);
            g.fill(centerX + i - half, centerY + i / 2 - half / 2, centerX + i, centerY + i / 2 + half / 2 + 1, color);
        }
    }

    private static void drawBankChangePassword(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.selectedAccount();
        boolean setup = account != null && !account.pinSet();
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", setup ? "home" : "bank-settings", "", ""));
        drawScaledCentered(g, font, setup ? "Set Password" : "Change Password",
                x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        int fieldY = y + Math.round((setup ? 126 : 73) * sy);
        if (!setup) {
            drawFigmaPasswordField(g, font, x, fieldY, w, "Current Password", SmartphoneClientState.currentPin(),
                    SmartphoneClientState.InputTarget.CURRENT_PIN, hitboxes, false, sy);
            fieldY += Math.round(84 * sy);
        } else {
            drawScaledCentered(g, font, trim(font, "Set a 4 digit password for " + account.appTitle() + ".", w - Math.round(24 * sx)),
                    x + w / 2, y + Math.round(80 * sy), bankMuted(), 0.9F);
        }
        drawFigmaPasswordField(g, font, x, fieldY, w, "New Password", SmartphoneClientState.newPin(),
                SmartphoneClientState.InputTarget.NEW_PIN, hitboxes, true, sy);
        fieldY += Math.round(84 * sy);
        drawFigmaPasswordField(g, font, x, fieldY, w, "Confirm New Password", SmartphoneClientState.confirmPin(),
                SmartphoneClientState.InputTarget.CONFIRM_PIN, hitboxes, false, sy);
        drawScaledString(g, font, "Both passwords must match", x, fieldY + Math.round(72 * sy), bankMuted(), 0.88F);

        int buttonY = y + Math.round((setup ? 354 : 366) * sy);
        int buttonH = Math.max(46, Math.round(56 * sy));
        figmaPrimaryButton(g, font, x, buttonY, w, buttonH, setup ? "Set Password" : "Change Password", BANK_BLUE,
                hitboxes, "CHANGE_BANK_PIN", "", "", "");
    }

    private static void drawFigmaPasswordField(GuiGraphics g, Font font, int x, int y, int w,
                                               String label, String value, SmartphoneClientState.InputTarget target,
                                               List<SmartphoneClientState.Hitbox> hitboxes, boolean eye, float sy) {
        float sx = w / 335.0F;
        drawScaledString(g, font, label, x, y, bankMuted(), 1.0F);
        int rowY = y + Math.round(31 * sy);
        int iconCenter = Math.round(11 * sx);
        drawFigmaProfileIcon(g, "lock", x + iconCenter, rowY + iconCenter, bankMuted());
        String dots = passwordDots(value);
        boolean active = SmartphoneClientState.inputTarget() == target;
        drawScaledString(g, font, dots.isBlank() ? (active ? "" : "4 digit password") : dots, x + Math.round(38 * sx),
                rowY + Math.round(3 * sy), dots.isBlank() ? bankMuted() : bankText(), 1.1F);
        if (active) {
            int caretX = x + Math.round(38 * sx) + (dots.isBlank() ? 0 : Math.min(phoneTextWidth(dots, 9.9F), w - Math.round(58 * sx))) + 2;
            g.fill(caretX, rowY + Math.round(2 * sy), caretX + 1, rowY + Math.round(14 * sy),
                    activeCaretColor(bankText()));
        }
        if (eye) {
            int eyeX = x + w - Math.round(11 * sx);
            int eyeY = rowY + iconCenter;
            fillCircle(g, eyeX, eyeY, Math.max(6, Math.round(8 * sx)), bankMuted());
            fillCircle(g, eyeX, eyeY, Math.max(3, Math.round(4 * sx)), bankBg());
            fillCircle(g, eyeX, eyeY, Math.max(1, Math.round(2 * sx)), bankMuted());
        }
        int lineY = rowY + Math.round(32 * sy);
        g.fill(x, lineY, x + w, lineY + 1, bankLine());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, rowY - Math.round(7 * sy), w, Math.round(46 * sy)),
                "INPUT", target.name(), "", ""));
    }

    private static String passwordDots(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int count = Mth.clamp(value.length(), 1, 12);
        return "*".repeat(count);
    }

    private static void drawBankTerms(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Terms & Condition", x + w / 2, y + Math.round(11 * sy), bankText(), 1.25F);

        int textY = y + Math.round(72 * sy);
        String[] paragraphs = {
                "L15.1 Thank you for using UBS Mobile Banking and enrolling as a member.",
                "15.2 Your privacy and account security are important to us. This notice explains our policy with regards to the information you share with us. This privacy policy relates to the information we collect online from the Application, received through server actions, payment requests, card changes, and account updates, and retain and use for the purpose of providing you services. If you do not agree to the terms in this Policy, we kindly ask you not to use these portals and/or sign the contract document.",
                "15.3 In order to use the services of this Application, You are required to register yourself by verifying the authorised device. This Privacy Policy applies to your information that we collect and receive on and through UBS Mobile Banking; it does not apply to practices of businesses that we do not own or control or people we do not employ.",
                "15.4 By using this Application, you agree to the terms of this Privacy Policy."
        };
        drawFigmaTermsParagraphs(g, font, paragraphs, x + Math.round(4 * sx), textY, w - Math.round(8 * sx), sy);
    }

    private static void drawFigmaTermsParagraphs(GuiGraphics g, Font font, String[] paragraphs,
                                                 int x, int y, int w, float sy) {
        float scale = 1.08F;
        int lineHeight = Math.max(18, Math.round(24 * sy));
        int paragraphGap = Math.max(16, Math.round(24 * sy));
        int cursor = y;
        for (String paragraph : paragraphs) {
            List<String> lines = wrap(font, paragraph, Math.round(w / scale), 18);
            for (String line : lines) {
                drawScaledString(g, font, line, x, cursor, bankText(), scale);
                cursor += lineHeight;
            }
            cursor += paragraphGap;
        }
    }

    private static void drawAllCards(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        List<SmartphoneClientState.PhoneAccount> accounts = SmartphoneClientState.accounts();

        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int backSize = Math.max(32, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, backSize);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, backSize, backSize),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "All Cards", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        SmartphoneClientState.PhoneAccount first = selected;
        if (first == null && !accounts.isEmpty()) {
            first = accounts.get(0);
        }
        SmartphoneClientState.PhoneAccount primaryCard = first;
        SmartphoneClientState.PhoneAccount second = accounts.stream()
                .filter(account -> primaryCard == null || !account.id().equals(primaryCard.id()))
                .findFirst()
                .orElse(primaryCard);

        int cardH = Math.max(142, Math.round(w * 199.0F / 335.0F));
        int topCardY = y + Math.round(74 * sy);
        int secondCardY = y + Math.round(297 * sy);
        drawFigmaCard(g, font, x, topCardY, w, cardH, first, true);
        if (first != null) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, topCardY, w, cardH),
                    "ACCOUNT", first.id().toString(), "", ""));
        }
        drawFigmaCard(g, font, x, secondCardY, w, cardH, second, false);
        if (second != null) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, secondCardY, w, cardH),
                    "ACCOUNT", second.id().toString(), "", ""));
        }

        int addButtonH = Math.max(44, Math.round(56 * sy));
        int addButtonY = Math.min(y + h - addButtonH - 8, y + Math.round(642 * sy));
        fillRounded(g, x, addButtonY, w, addButtonH, Math.max(12, Math.round(16 * sx)), BANK_BLUE);
        drawScaledCentered(g, font, "Add Card", x + w / 2 - Math.round(14 * sx),
                addButtonY + (addButtonH - Math.round(8 * 1.25F)) / 2, 0xFFFFFFFF, 1.25F);
        drawScaledString(g, font, "+", x + w / 2 + Math.round(42 * sx),
                addButtonY + (addButtonH - Math.round(8 * 1.45F)) / 2 - 1, 0xFFFFFFFF, 1.45F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, addButtonY, w, addButtonH),
                "APP", "add-card", "", ""));
    }

    private static void drawAddCard(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        List<SmartphoneClientState.PhoneAccount> accounts = SmartphoneClientState.accounts();
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;

        int backSize = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, backSize);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, backSize, backSize),
                "APP", "all-cards", "", ""));
        drawScaledCentered(g, font, "Add New Card", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);

        SmartphoneClientState.PhoneAccount preview = selected;
        if (preview == null && !accounts.isEmpty()) {
            preview = accounts.get(0);
        }

        int cardH = Math.max(142, Math.round(w * 199.0F / 335.0F));
        int previewY = y + Math.round(74 * sy);
        drawFigmaCard(g, font, x, previewY, w, cardH, preview, true);
        if (preview != null) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, previewY, w, cardH),
                    "SET_TAP_ACCOUNT_FOR", preview.id().toString(), "", ""));
        }

        int fieldY = y + Math.round(302 * sy);
        drawFigmaProfileField(g, font, x, fieldY, w, "user", "Cardholder Name",
                SmartphoneClientState.ownerName(), SmartphoneClientState.InputTarget.NONE, hitboxes);
        fieldY += Math.round(84 * sy);
        drawScaledString(g, font, "Expiry Date", x, fieldY, bankMuted(), 1.0F);
        drawScaledString(g, font, "4-digit CVV", x + Math.round(215 * sx), fieldY, bankMuted(), 1.0F);
        int splitY = fieldY + Math.round(30 * sy);
        drawFigmaCardDetailValue(g, font, x, splitY, Math.round(120 * sx), virtualExpiry(preview));
        drawFigmaCardDetailValue(g, font, x + Math.round(215 * sx), splitY, Math.round(120 * sx), cvvFor(preview));
        fieldY += Math.round(76 * sy);
        drawFigmaProfileField(g, font, x, fieldY, w, "cards", "Card Number",
                normalizeCardNumber(preview == null ? "" : preview.virtualCard()), SmartphoneClientState.InputTarget.NONE, hitboxes);
        drawFigmaMiniMastercard(g, x + w - Math.round(25 * sx), fieldY + Math.round(42 * sy), sx);
    }

    private static void drawFigmaMiniMastercard(GuiGraphics g, int centerX, int centerY, float scale) {
        int radius = Math.max(6, Math.round(9 * scale));
        int offset = Math.max(7, Math.round(8 * scale));
        fillCircle(g, centerX - offset / 2, centerY, radius, 0xFFFF1F28);
        fillCircle(g, centerX + offset / 2, centerY, radius, 0xFFFFA31A);
    }

    private static void drawFigmaCardDetailValue(GuiGraphics g, Font font, int x, int y, int w, String value) {
        drawScaledString(g, font, value, x, y, bankText(), 1.0F);
        g.fill(x, y + 25, x + w, y + 26, bankLine());
    }

    private static String totalBalanceLabel(List<SmartphoneClientState.PhoneAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return "$0";
        }
        double total = 0.0D;
        for (SmartphoneClientState.PhoneAccount account : accounts) {
            if (account == null) {
                continue;
            }
            total += parseMoney(account.rawBalance());
        }
        if (Math.abs(total - Math.rint(total)) < 0.005D) {
            return "$" + Math.round(total);
        }
        return String.format(Locale.ROOT, "$%.2f", total);
    }

    private static void drawFigmaCard(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      SmartphoneClientState.PhoneAccount account, boolean mastercard) {
        int radius = Math.max(18, Math.round(w * 25.0F / 335.0F));
        fillRounded(g, x, y, w, h, radius, mastercard ? 0xFF31324A : 0xFF383350);
        fillRoundedVertical(g, x + 1, y + 1, w - 2, h - 2, radius - 1,
                mastercard ? 0xFF292A42 : 0xFF332E4C,
                mastercard ? 0xFF202138 : 0xFF25213B);
        if (mastercard) {
            drawFigmaCardPattern(g, x, y, w, h);
        }
        drawFigmaChip(g, x + Math.round(w * 20.0F / 335.0F), y + Math.round(h * 22.0F / 199.0F),
                Math.max(26, Math.round(w * (mastercard ? 29.0F : 35.0F) / 335.0F)),
                Math.max(20, Math.round(h * 25.0F / 199.0F)));
        if (mastercard) {
            drawContactlessMark(g, x + Math.round(w * 0.88F), y + Math.round(h * 0.11F),
                    Math.max(13, Math.round(h * 0.11F)), 0x883C4E92);
        }

        String cardNo = account == null ? "" : normalizeCardNumber(account.virtualCard());
        String holder = account == null ? "No account"
                : trim(font, SmartphoneClientState.ownerName(), Math.round(w * 0.42F));
        drawFigmaCardNumber(g, font, cardNo, x, y + Math.round(h * 73.0F / 199.0F), w);
        drawScaledString(g, font, holder, x + Math.round(w * 20.0F / 335.0F),
                y + Math.round(h * 114.0F / 199.0F), 0xFFFFFFFF, 1.18F);
        int labelY = y + Math.round(h * 146.0F / 199.0F);
        drawScaledString(g, font, "Expiry Date", x + Math.round(w * 20.0F / 335.0F), labelY, 0xFFC2C2C7, 0.82F);
        drawScaledString(g, font, account == null ? "--/--" : virtualExpiry(account), x + Math.round(w * 20.0F / 335.0F), labelY + Math.round(h * 15.0F / 199.0F),
                0xFFFFFFFF, 1.08F);
        if (mastercard) {
            int cvvX = x + Math.round(w * 103.0F / 335.0F);
            drawScaledString(g, font, "CVV", cvvX, labelY, 0xFFC2C2C7, 0.82F);
            drawScaledString(g, font, cvvFor(account), cvvX, labelY + Math.round(h * 15.0F / 199.0F), 0xFFFFFFFF, 1.08F);
            drawFigmaCardBrand(g, font, x, y, w, h);
        } else {
            drawScaledString(g, font, "VISA", x + w - Math.round(w * 0.19F),
                    y + h - Math.round(h * 0.16F), 0xFFFFFFFF, 1.55F);
        }
    }

    private static void drawFigmaCardBrand(GuiGraphics g, Font font, int x, int y, int w, int h) {
        int brandCenterX = x + w - Math.round(w * 0.20F);
        int brandCenterY = y + h - Math.round(h * 0.26F);
        int radius = Math.max(7, Math.round(h * 0.052F));
        int offset = Math.max(8, Math.round(w * 0.04F));
        fillCircle(g, brandCenterX, brandCenterY, radius, 0xFFFF1F28);
        fillCircle(g, brandCenterX + offset, brandCenterY, radius, 0xFFFFA31A);

        String label = "Mastercard";
        int rightPad = Math.max(16, Math.round(w * 16.0F / 335.0F));
        int maxLabelW = Math.max(42, Math.round(w * 92.0F / 335.0F));
        float labelScale = Mth.clamp(maxLabelW / (float) Math.max(1, phoneTextWidth(label, 9.0F)), 0.70F, 1.0F);
        int labelW = phoneTextWidth(label, font.lineHeight * labelScale);
        drawScaledString(g, font, label, x + w - rightPad - labelW,
                y + h - Math.round(h * 29.0F / 199.0F), 0xFFFFFFFF, labelScale);
    }

    private static void drawFigmaCardNumber(GuiGraphics g, Font font, String cardNo, int x, int y, int w) {
        String digits = cardNo == null ? "" : cardNo.replaceAll("[^0-9]", "");
        if (digits.length() < 16) {
            drawScaledCentered(g, font, "No card linked", x + w / 2, y, 0xFFFFFFFF, 1.25F);
            return;
        }
        int[] offsets = {20, 99, 172, 245};
        float scale = Mth.clamp(w / 156.0F, 1.55F, 2.15F);
        for (int i = 0; i < 4; i++) {
            String group = digits.substring(i * 4, i * 4 + 4);
            drawScaledString(g, font, group, x + Math.round(w * offsets[i] / 335.0F), y, 0xFFFFFFFF, scale);
        }
    }

    private static void drawFigmaCardPattern(GuiGraphics g, int x, int y, int w, int h) {
        int pad = Math.max(12, Math.round(w * 0.045F));
        int right = x + w - pad;
        int top = y + Math.max(10, Math.round(h * 0.08F));
        int bottom = y + h - Math.max(12, Math.round(h * 0.10F));
        int thin = Math.max(1, Math.round(h * 0.008F));

        g.fill(x + pad, top, right, top + thin, 0x18FFFFFF);
        g.fill(x + pad, bottom, right, bottom + thin, 0x10000000);

        drawCardDiagonalBand(g, x + Math.round(w * 0.12F), y + Math.round(h * 0.33F),
                Math.max(30, Math.round(w * 0.26F)), thin, 4, Math.max(12, Math.round(w * 0.055F)),
                Math.max(6, Math.round(h * 0.032F)), 0x16FFFFFF);
        drawCardDiagonalBand(g, x + Math.round(w * 0.48F), y + Math.round(h * 0.16F),
                Math.max(42, Math.round(w * 0.33F)), thin, 5, Math.max(13, Math.round(w * 0.052F)),
                Math.max(7, Math.round(h * 0.034F)), 0x12006BFF);
        g.fill(x + Math.round(w * 0.68F), y + Math.round(h * 0.26F),
                x + Math.round(w * 0.88F), y + Math.round(h * 0.26F) + thin, 0x20FFFFFF);
        g.fill(x + Math.round(w * 0.74F), y + Math.round(h * 0.31F),
                x + Math.round(w * 0.95F), y + Math.round(h * 0.31F) + thin, 0x1400D1FF);
    }

    private static void drawCardDiagonalBand(GuiGraphics g, int x, int y, int segmentW, int segmentH,
                                             int segments, int stepX, int stepY, int color) {
        for (int i = 0; i < segments; i++) {
            int sx = x + i * stepX;
            int sy = y + i * stepY;
            g.fill(sx, sy, sx + segmentW, sy + segmentH, color);
        }
    }

    private static void drawFigmaChip(GuiGraphics g, int x, int y, int w, int h) {
        g.blit(CARD_CHIP_TEXTURE, x, y, w, h, 0.0F, 0.0F,
                CARD_CHIP_TEXTURE_WIDTH, CARD_CHIP_TEXTURE_HEIGHT,
                CARD_CHIP_TEXTURE_WIDTH, CARD_CHIP_TEXTURE_HEIGHT);
    }

    private static void drawContactlessMark(GuiGraphics g, int x, int y, int h, int color) {
        int centerX = x;
        int centerY = y + h / 2;
        drawContactlessArc(g, centerX, centerY, Math.max(4, Math.round(h * 0.30F)),
                Math.max(6, Math.round(h * 0.48F)), color);
        drawContactlessArc(g, centerX, centerY, Math.max(7, Math.round(h * 0.48F)),
                Math.max(8, Math.round(h * 0.68F)), color);
        drawContactlessArc(g, centerX, centerY, Math.max(10, Math.round(h * 0.66F)),
                Math.max(11, Math.round(h * 0.88F)), color);
    }

    private static void drawContactlessArc(GuiGraphics g, int centerX, int centerY, int rx, int ry, int color) {
        int prevX = centerX + Math.round((float) (Math.cos(Math.toRadians(-58.0D)) * rx));
        int prevY = centerY + Math.round((float) (Math.sin(Math.toRadians(-58.0D)) * ry));
        for (int step = 1; step <= 9; step++) {
            double angle = Math.toRadians(-58.0D + step * (116.0D / 9.0D));
            int px = centerX + Math.round((float) (Math.cos(angle) * rx));
            int py = centerY + Math.round((float) (Math.sin(angle) * ry));
            drawSoftLine(g, prevX, prevY, px, py, color);
            prevX = px;
            prevY = py;
        }
    }

    private static String normalizeCardNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() < 16) {
            return "";
        }
        return digits.substring(0, 4) + " " + digits.substring(4, 8) + " "
                + digits.substring(8, 12) + " " + digits.substring(12, 16);
    }

    private static String cvvFor(SmartphoneClientState.PhoneAccount account) {
        if (account == null || account.id() == null) {
            return "----";
        }
        int code = Math.abs(account.id().hashCode()) % 9000 + 1000;
        return String.valueOf(code);
    }

    private static String virtualExpiry(SmartphoneClientState.PhoneAccount account) {
        if (account == null || account.id() == null) {
            return "--/--";
        }
        int month = Math.abs(account.id().hashCode()) % 12 + 1;
        int year = 28 + Math.abs(account.id().hashCode() / 31) % 6;
        return String.format(Locale.ROOT, "%02d/%02d", month, year);
    }

    private static void drawBankingLoading(GuiGraphics g, Font font, int x, int y, int w, int h) {
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        long tick = System.currentTimeMillis();
        int phase = (int) ((tick / 260L) % 3L);
        drawBankSurface(g, x, y, w, h, bankBg());
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());

        if (SmartphoneClientState.bankingBankPickLoadingActive()) {
            drawBankPickSplash(g, font, x, y, w, h, sx, sy, SmartphoneClientState.bankingLoadingProgress(), tick);
            return;
        }

        int centerX = x + w / 2;
        drawBankLoadingIllustration(g, font, centerX, y + Math.round(234 * sy), w, sx, sy, phase);

        int dotY = y + Math.round(472 * sy);
        int dotGap = Math.max(12, Math.round(15 * sx));
        int dotR = Math.max(3, Math.round(4 * sx));
        for (int i = 0; i < 3; i++) {
            int color = phase == i ? BANK_BLUE : 0xFF3A3A4D;
            fillCircle(g, centerX - dotGap + i * dotGap, dotY, dotR, color);
        }

        int titleY = y + Math.round(527 * sy);
        drawScaledCentered(g, font, "Banking for Everything is", centerX, titleY, bankText(), 1.65F);
        drawScaledCentered(g, font, "Easy and Convenient", centerX, titleY + Math.round(33 * sy), bankText(), 1.65F);
        drawScaledCentered(g, font, "Secure payments, transfers, and account access",
                centerX, titleY + Math.round(79 * sy), bankMuted(), 0.88F);
        drawScaledCentered(g, font, "without leaving your phone.",
                centerX, titleY + Math.round(103 * sy), bankMuted(), 0.88F);

        int buttonH = Math.max(46, Math.round(56 * sy));
        int buttonY = Math.min(y + Math.round(684 * sy), y + h - buttonH - Math.round(10 * sy));
        drawFigmaPrimaryButtonVisual(g, font, x, buttonY, w, buttonH,
                phase == 2 ? "Loading..." : "Loading", BANK_BLUE);
        int shimmerW = Math.max(30, Math.round(42 * sx));
        int shimmerRange = Math.max(1, w - shimmerW - Math.round(24 * sx));
        int shimmerX = x + Math.round(12 * sx) + (int) ((tick / 8L) % shimmerRange);
        g.fill(shimmerX, buttonY + 1, shimmerX + shimmerW, buttonY + buttonH - 1, 0x22FFFFFF);
    }

    private static void drawBankPickSplash(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           float sx, float sy, float progress, long tick) {
        int centerX = x + w / 2;
        int centerY = y + Math.round(h * 0.43F);
        int logoSize = Math.max(48, Math.round(68 * sx));
        float pulse = 0.88F + ((tick % 700L) / 700.0F) * 0.18F;
        drawBankPickLogo(g, centerX, centerY - Math.round(28 * sy), Math.round(logoSize * pulse));
        drawScaledCentered(g, font, "BANKPICK", centerX, centerY + Math.round(31 * sy), bankText(), 1.48F);
        drawScaledCentered(g, font, "UBS mobile banking", centerX, centerY + Math.round(58 * sy), bankMuted(), 0.88F);

        int barW = Math.max(96, Math.round(w * 0.46F));
        int barH = Math.max(4, Math.round(5 * sy));
        int barX = centerX - barW / 2;
        int barY = y + h - Math.round(84 * sy);
        fillRounded(g, barX, barY, barW, barH, Math.max(2, barH / 2), bankPanelSoft());
        int filled = Mth.clamp(Math.round(barW * progress), 0, barW);
        if (filled > 0) {
            fillRounded(g, barX, barY, filled, barH, Math.max(2, barH / 2), BANK_BLUE);
        }
    }

    private static void drawBankPickLogo(GuiGraphics g, int centerX, int centerY, int size) {
        int ringRadius = Math.max(10, size / 5);
        int ringThickness = Math.max(3, size / 18);
        int offset = Math.max(8, size / 7);
        drawCircleRing(g, centerX - offset, centerY, ringRadius, ringThickness, 0xFF27D6FF, bankBg());
        drawCircleRing(g, centerX + offset, centerY, ringRadius, ringThickness, BANK_BLUE, bankBg());
        drawSoftLine(g, centerX - offset + ringRadius, centerY, centerX + offset - ringRadius, centerY, bankText());
        fillCircle(g, centerX, centerY, Math.max(4, size / 16), bankText());
    }

    private static void drawBankLoadingIllustration(GuiGraphics g, Font font, int centerX, int centerY, int w,
                                                    float sx, float sy, int phase) {
        int coin = Math.max(24, Math.round(30 * sx));
        drawCoinStack(g, centerX - Math.round(108 * sx), centerY + Math.round(72 * sy), coin, 5, sx, phase == 0);
        drawCoinStack(g, centerX + Math.round(92 * sx), centerY + Math.round(82 * sy), coin, 4, sx, phase == 1);

        int cardW = Math.max(122, Math.round(156 * sx));
        int cardH = Math.max(68, Math.round(84 * sy));
        int cardX = centerX - cardW / 2 + Math.round(18 * sx);
        int cardY = centerY - Math.round(34 * sy);
        fillRounded(g, cardX, cardY, cardW, cardH, Math.max(16, Math.round(19 * sx)), 0xFF243268);
        drawFigmaCardPattern(g, cardX + Math.round(7 * sx), cardY + Math.round(6 * sy),
                cardW - Math.round(14 * sx), cardH - Math.round(12 * sy));
        fillRounded(g, cardX + Math.round(12 * sx), cardY + Math.round(13 * sy),
                Math.round(34 * sx), Math.round(24 * sy), Math.round(7 * sx), 0xFFFFC44A);
        drawCircleRing(g, cardX + cardW - Math.round(34 * sx), cardY + Math.round(35 * sy),
                Math.max(14, Math.round(17 * sx)), Math.max(2, Math.round(3 * sx)), 0xFFFFC44A, 0xFF243268);
        drawScaledCentered(g, font, "$", cardX + cardW - Math.round(34 * sx),
                cardY + Math.round(27 * sy), bankText(), 1.0F);

        int bodyX = centerX - Math.round(50 * sx);
        int bodyY = centerY - Math.round(98 * sy);
        fillCircle(g, bodyX, bodyY, Math.max(16, Math.round(18 * sx)), 0xFFFFC999);
        fillCircle(g, bodyX + Math.round(13 * sx), bodyY - Math.round(11 * sy),
                Math.max(11, Math.round(13 * sx)), 0xFF22243B);
        fillRounded(g, bodyX - Math.round(21 * sx), bodyY + Math.round(22 * sy),
                Math.round(42 * sx), Math.round(74 * sy), Math.round(16 * sx), BANK_BLUE);
        fillRounded(g, bodyX - Math.round(10 * sx), bodyY + Math.round(88 * sy),
                Math.round(12 * sx), Math.round(70 * sy), Math.round(6 * sx), 0xFF111222);
        fillRounded(g, bodyX + Math.round(10 * sx), bodyY + Math.round(88 * sy),
                Math.round(12 * sx), Math.round(70 * sy), Math.round(6 * sx), 0xFF111222);
        drawSoftLine(g, bodyX + Math.round(18 * sx), bodyY + Math.round(43 * sy),
                cardX + Math.round(28 * sx), cardY + Math.round(6 * sy), BANK_BLUE);

        int megaphoneX = bodyX + Math.round(38 * sx);
        int megaphoneY = bodyY - Math.round(25 * sy);
        fillRounded(g, megaphoneX, megaphoneY, Math.round(28 * sx), Math.round(18 * sy),
                Math.round(6 * sx), 0xFFFFB13D);
        drawSoftLine(g, megaphoneX + Math.round(25 * sx), megaphoneY + Math.round(2 * sy),
                megaphoneX + Math.round(48 * sx), megaphoneY - Math.round(14 * sy), 0xFFFFB13D);
        drawSoftLine(g, megaphoneX + Math.round(26 * sx), megaphoneY + Math.round(16 * sy),
                megaphoneX + Math.round(50 * sx), megaphoneY + Math.round(31 * sy), 0xFFFFB13D);

        fillCircle(g, centerX + Math.round(110 * sx), centerY + Math.round(150 * sy),
                Math.max(32, Math.round(42 * sx)), 0xFF006BFF);
        fillCircle(g, centerX + Math.round(133 * sx), centerY + Math.round(132 * sy),
                Math.max(19, Math.round(24 * sx)), 0xFF21A0FF);
    }

    private static void drawCoinStack(GuiGraphics g, int x, int y, int coinW, int count, float sx, boolean active) {
        int coinH = Math.max(7, Math.round(8 * sx));
        int color = active ? 0xFFFFD873 : 0xFFFFC44A;
        for (int i = 0; i < count; i++) {
            int cy = y - i * Math.max(5, Math.round(6 * sx));
            fillRounded(g, x - coinW / 2, cy, coinW, coinH, coinH / 2, color);
            g.fill(x - coinW / 2 + 3, cy + coinH - 2, x + coinW / 2 - 3, cy + coinH, 0xFFFFA92D);
        }
    }

    private static void drawSoftLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(Mth.lerp(t, x1, x2));
            int y = Math.round(Mth.lerp(t, y1, y2));
            g.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private static void drawCircleRing(GuiGraphics g, int centerX, int centerY, int radius, int thickness, int color, int innerColor) {
        fillCircle(g, centerX, centerY, radius, color);
        fillCircle(g, centerX, centerY, Math.max(0, radius - Math.max(1, thickness)), innerColor);
    }

    private static List<SmartphoneClientState.PhoneTransaction> filterHistory(
            List<SmartphoneClientState.PhoneTransaction> txs,
            String query) {
        List<SmartphoneClientState.PhoneTransaction> source = txs == null ? List.of() : txs;
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return source;
        }
        if (source == cachedHistoryFilterSource && needle.equals(cachedHistoryFilterQuery)) {
            return cachedHistoryFilterRows;
        }
        List<SmartphoneClientState.PhoneTransaction> filtered = new ArrayList<>();
        for (SmartphoneClientState.PhoneTransaction tx : source) {
            String haystack = ((tx.description() == null ? "" : tx.description()) + " "
                    + (tx.amount() == null ? "" : tx.amount()) + " "
                    + (tx.time() == null ? "" : tx.time())).toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                filtered.add(tx);
            }
        }
        cachedHistoryFilterSource = source;
        cachedHistoryFilterQuery = needle;
        cachedHistoryFilterRows = filtered;
        return cachedHistoryFilterRows;
    }

    private static String normalizeStatsMonthKey(List<SmartphoneClientState.PhoneTransaction> txs) {
        List<String> months = statsMonthKeys(txs);
        String selected = SmartphoneClientState.statsMonthKey();
        if (selected != null && months.contains(selected)) {
            return selected;
        }
        String current = YearMonth.now().toString();
        if (months.contains(current)) {
            return current;
        }
        if (txs != null && !txs.isEmpty()) {
            String latest = txs.stream()
                    .max(Comparator.comparingLong(SmartphoneClientState.PhoneTransaction::timestampMillis))
                    .map(SmartphoneOverlay::transactionMonthKey)
                    .orElse("");
            if (months.contains(latest)) {
                return latest;
            }
        }
        return months.isEmpty() ? transactionMonthKey(null) : months.get(months.size() - 1);
    }

    private static List<String> statsMonthKeys(List<SmartphoneClientState.PhoneTransaction> txs) {
        List<SmartphoneClientState.PhoneTransaction> source = txs == null ? List.of() : txs;
        String selected = SmartphoneClientState.statsMonthKey();
        String selectedKey = selected == null ? "" : selected;
        if (source == cachedStatsMonthKeysSource && selectedKey.equals(cachedStatsMonthKeysSelected)) {
            return cachedStatsMonthKeys;
        }
        List<String> months = new ArrayList<>();
        for (String key : lastSixMonthKeys()) {
            if (!months.contains(key)) {
                months.add(key);
            }
        }
        for (SmartphoneClientState.PhoneTransaction tx : source) {
            String key = transactionMonthKey(tx);
            if (!months.contains(key)) {
                months.add(key);
            }
        }
        if (months.isEmpty()) {
            cachedStatsMonthKeysSource = source;
            cachedStatsMonthKeysSelected = selectedKey;
            cachedStatsMonthKeys = lastSixMonthKeys();
            return cachedStatsMonthKeys;
        }
        months.sort(String::compareTo);
        if (months.size() <= 6) {
            cachedStatsMonthKeysSource = source;
            cachedStatsMonthKeysSelected = selectedKey;
            cachedStatsMonthKeys = months;
            return cachedStatsMonthKeys;
        }
        int anchor = selected != null && months.contains(selected)
                ? months.indexOf(selected)
                : months.size() - 1;
        int from = Mth.clamp(anchor - 5, 0, Math.max(0, months.size() - 6));
        cachedStatsMonthKeysSource = source;
        cachedStatsMonthKeysSelected = selectedKey;
        cachedStatsMonthKeys = new ArrayList<>(months.subList(from, Math.min(months.size(), from + 6)));
        return cachedStatsMonthKeys;
    }

    private static List<String> lastSixMonthKeys() {
        List<String> months = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            months.add(current.minusMonths(i).toString());
        }
        return months;
    }

    private static String monthLabel(String key) {
        try {
            return YearMonth.parse(key).getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT);
        } catch (RuntimeException ignored) {
            return "Now";
        }
    }

    private static List<SmartphoneClientState.PhoneTransaction> filterByMonth(
            List<SmartphoneClientState.PhoneTransaction> txs,
            String monthKey) {
        List<SmartphoneClientState.PhoneTransaction> source = txs == null ? List.of() : txs;
        if (source.isEmpty()) {
            return List.of();
        }
        String key = monthKey == null || monthKey.isBlank() ? normalizeStatsMonthKey(source) : monthKey;
        if (source == cachedMonthFilterSource && key.equals(cachedMonthFilterKey)) {
            return cachedMonthFilterRows;
        }
        cachedMonthFilterSource = source;
        cachedMonthFilterKey = key;
        cachedMonthFilterRows = source.stream()
                .filter(tx -> key.equals(transactionMonthKey(tx)))
                .toList();
        return cachedMonthFilterRows;
    }

    private static List<SmartphoneClientState.PhoneTransaction> sortedTransactions(
            List<SmartphoneClientState.PhoneTransaction> txs) {
        List<SmartphoneClientState.PhoneTransaction> source = txs == null ? List.of() : txs;
        if (source == cachedSortedTransactionSource) {
            return cachedSortedTransactions;
        }
        cachedSortedTransactionSource = source;
        cachedSortedTransactions = source.stream()
                .sorted(Comparator.comparingLong(SmartphoneClientState.PhoneTransaction::timestampMillis).reversed())
                .toList();
        return cachedSortedTransactions;
    }

    private static String transactionMonthKey(SmartphoneClientState.PhoneTransaction tx) {
        LocalDate date = transactionDate(tx);
        return YearMonth.from(date).toString();
    }

    private static LocalDate transactionDate(SmartphoneClientState.PhoneTransaction tx) {
        long millis = tx == null ? 0L : tx.timestampMillis();
        if (millis <= 0L) {
            return LocalDate.now();
        }
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static double parseMoney(String value) {
        String raw = value == null ? "" : value.replace("$", "").replace(",", "").replace("+", "").trim();
        if (raw.isBlank()) {
            return 0.0D;
        }
        double multiplier = 1.0D;
        char last = raw.charAt(raw.length() - 1);
        if (last == 'K' || last == 'k') {
            multiplier = 1_000.0D;
            raw = raw.substring(0, raw.length() - 1).trim();
        } else if (last == 'M' || last == 'm') {
            multiplier = 1_000_000.0D;
            raw = raw.substring(0, raw.length() - 1).trim();
        } else if (last == 'B' || last == 'b') {
            multiplier = 1_000_000_000.0D;
            raw = raw.substring(0, raw.length() - 1).trim();
        }
        try {
            return Double.parseDouble(raw) * multiplier;
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static double sumSignedTransactions(List<SmartphoneClientState.PhoneTransaction> txs, boolean outgoing) {
        double total = 0.0D;
        if (txs == null) {
            return total;
        }
        for (SmartphoneClientState.PhoneTransaction tx : txs) {
            if (tx == null || tx.amount() == null) {
                continue;
            }
            boolean negative = tx.amount().contains("-");
            if (negative == outgoing) {
                total += Math.abs(parseMoney(tx.amount()));
            }
        }
        return total;
    }

    private static String moneyLabel(double value) {
        double safe = Math.max(0.0D, value);
        if (safe >= 1_000_000_000.0D) {
            return "$" + trimDecimal(safe / 1_000_000_000.0D) + "B";
        }
        if (safe >= 1_000_000.0D) {
            return "$" + trimDecimal(safe / 1_000_000.0D) + "M";
        }
        if (safe >= 1_000.0D) {
            return "$" + trimDecimal(safe / 1_000.0D) + "K";
        }
        if (Math.abs(safe - Math.rint(safe)) < 0.005D) {
            return "$" + Math.round(safe);
        }
        return "$" + String.format(Locale.ROOT, "%.2f", safe);
    }

    private static String trimDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05D) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void drawAccount(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, bankBg());
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.selectedAccount();
        if (account == null && !SmartphoneClientState.accounts().isEmpty()) {
            account = SmartphoneClientState.accounts().get(0);
        }
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "My Cards", x + w / 2, y + Math.round(11 * sy), bankText(), 1.35F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, bankPanelSoft());
        drawFigmaPlusGlyph(g, x + w - circle / 2, y + circle / 2, Math.max(8, Math.round(9 * sx)), bankText());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "add-card", "", ""));

        int cardH = Math.max(142, Math.round(w * 199.0F / 335.0F));
        int cardY = y + Math.round(74 * sy);
        if (account == null) {
            drawFigmaCard(g, font, x, cardY, w, cardH, null, true);
        } else {
            drawFigmaCard(g, font, x, cardY, w, cardH, account, true);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, cardY, w, cardH),
                    "ACCOUNT", account.id().toString(), "", ""));
        }

        int rowY = y + Math.round(303 * sy);
        List<SmartphoneClientState.PhoneTransaction> txs = SmartphoneClientState.transactionsForSelectedAccount();
        drawTransactionRows(g, font, x, rowY, w, Math.max(52, Math.round(64 * sy)), 3, txs, 0, hitboxes);

        List<SmartphoneClientState.PhoneTransaction> monthTxs = filterByMonth(txs, normalizeStatsMonthKey(txs));
        double monthSpent = sumSignedTransactions(monthTxs, true);
        double monthIncome = sumSignedTransactions(monthTxs, false);
        double monthActivity = monthSpent + monthIncome;
        int limitTitleY = y + Math.round(502 * sy);
        drawScaledString(g, font, "Monthly spending limit", x, limitTitleY, bankText(), 1.28F);
        int limitY = y + Math.round(541 * sy);
        int limitH = Math.max(96, Math.round(113 * sy));
        fillRounded(g, x, limitY, w, limitH, Math.max(15, Math.round(18 * sx)), bankPanelSoft());
        String amountText = monthActivity <= 0.0D
                ? "Amount: " + (account == null ? "$0" : account.shortBalance())
                : "Amount: " + moneyLabel(monthSpent);
        drawScaledString(g, font, trim(font, amountText, w - Math.round(48 * sx)),
                x + Math.round(24 * sx), limitY + Math.round(23 * sy), bankText(), 0.98F);
        int barX = x + Math.round(24 * sx);
        int barY = limitY + Math.round(54 * sy);
        int barW = w - Math.round(48 * sx);
        fillRounded(g, barX, barY, barW, Math.max(6, Math.round(7 * sy)), 4, 0xFFFFFFFF);
        int progress = monthActivity <= 0.0D ? 0 : Mth.clamp(Math.round(barW * (float) (monthSpent / monthActivity)), 0, barW);
        if (progress > 0) {
            fillRounded(g, barX, barY, progress, Math.max(6, Math.round(7 * sy)), 4, BANK_BLUE);
            fillCircle(g, barX + progress, barY + Math.max(3, Math.round(3 * sy)), Math.max(7, Math.round(8 * sx)), BANK_BLUE);
            fillCircle(g, barX + progress, barY + Math.max(3, Math.round(3 * sy)), Math.max(4, Math.round(4 * sx)), 0xFFFFFFFF);
        }
        drawScaledString(g, font, "$0", barX, limitY + Math.round(76 * sy), bankMuted(), 0.9F);
        if (progress > 0) {
            drawScaledCentered(g, font, moneyLabel(monthSpent), barX + progress, limitY + Math.round(76 * sy), bankText(), 0.9F);
        }
        drawScaledString(g, font, moneyLabel(monthActivity), x + w - Math.round(68 * sx), limitY + Math.round(76 * sy), bankMuted(), 0.9F);

        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "cards", hitboxes);
    }

    private static void drawTap(GuiGraphics g, Font font, int x, int y, int w, int h,
                                List<SmartphoneClientState.Hitbox> hitboxes) {
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.accounts().stream()
                .filter(SmartphoneClientState.PhoneAccount::tapSelected)
                .findFirst()
                .orElse(SmartphoneClientState.selectedAccount());
        drawBankSurface(g, x, y, w, h, bankBg());
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, bankText(), bankBg());
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Tap to Pay", x + w / 2, y + Math.round(11 * sy), bankText(), 1.3F);

        int navY = bankDisplayNavY(y, h, w);
        int cardY = y + Math.round(74 * sy);
        int cardH = Math.max(142, Math.round(w * 199.0F / 335.0F));
        if (account == null) {
            drawFigmaCard(g, font, x, cardY, w, cardH, null, true);
            int emptyY = cardY + cardH + Math.round(32 * sy);
            fillRounded(g, x, emptyY, w, Math.max(124, Math.round(146 * sy)), Math.max(14, Math.round(18 * sx)), bankPanelSoft());
            fillCircle(g, x + w / 2, emptyY + Math.round(36 * sy), Math.max(19, Math.round(22 * sx)), bankInput());
            drawFigmaActionIcon(g, "tap", x + w / 2, emptyY + Math.round(36 * sy), BANK_BLUE);
            drawScaledCentered(g, font, "No card selected", x + w / 2, emptyY + Math.round(72 * sy), bankText(), 1.04F);
            drawScaledCentered(g, font, "Choose a card before paying.", x + w / 2, emptyY + Math.round(92 * sy), bankMuted(), 0.9F);
            mobileButton(g, font, x + Math.round(20 * sx), emptyY + Math.round(110 * sy),
                    w - Math.round(40 * sx), Math.max(34, Math.round(40 * sy)), "Select Card", BANK_BLUE,
                    hitboxes, "APP", "all-cards", "", "");
            drawBankDisplayBottomNav(g, font, x, navY, w, "cards", hitboxes);
            return;
        }

        drawFigmaCard(g, font, x, cardY, w, cardH, account, true);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, cardY, w, cardH),
                "ACCOUNT", account.id().toString(), "", ""));

        int statusY = cardY + cardH + Math.round(28 * sy);
        fillRounded(g, x, statusY, w, Math.max(78, Math.round(92 * sy)), Math.max(14, Math.round(18 * sx)), bankPanelSoft());
        fillCircle(g, x + Math.round(31 * sx), statusY + Math.round(31 * sy), Math.max(17, Math.round(20 * sx)), bankInput());
        drawFigmaActionIcon(g, "tap", x + Math.round(31 * sx), statusY + Math.round(31 * sy), BANK_BLUE);
        drawScaledString(g, font, account.frozen() ? "Card Frozen" : "Ready to pay",
                x + Math.round(62 * sx), statusY + Math.round(17 * sy), bankText(), 1.0F);
        drawScaledString(g, font, account.tapSelected() ? "Default contactless card" : "Available for this session",
                x + Math.round(62 * sx), statusY + Math.round(39 * sy),
                account.frozen() ? BANK_DANGER : bankMuted(), 0.86F);

        int detailsY = statusY + Math.round(120 * sy);
        drawFigmaSettingsRow(g, font, x, detailsY, w, "Selected Card",
                trim(font, account.appTitle(), Math.round(w * 0.45F)), "APP", "all-cards", hitboxes);
        detailsY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, detailsY, w, "Contactless Status",
                account.tapSelected() ? "Active" : "Set active", "SET_TAP_ACCOUNT", "", hitboxes);

        int infoY = detailsY + Math.round(68 * sy);
        if (infoY + Math.round(80 * sy) < navY - Math.round(44 * sy)) {
            fillRounded(g, x, infoY, w, Math.max(66, Math.round(78 * sy)), Math.max(14, Math.round(18 * sx)), bankPanelSoft());
            fillCircle(g, x + Math.round(27 * sx), infoY + Math.round(29 * sy), Math.max(14, Math.round(16 * sx)), bankInput());
            drawFigmaActionIcon(g, "tap", x + Math.round(27 * sx), infoY + Math.round(29 * sy), BANK_BLUE);
            drawScaledString(g, font, "Payment points", x + Math.round(52 * sx), infoY + Math.round(13 * sy), bankText(), 0.98F);
            drawScaledString(g, font, trim(font, "Use this phone near supported terminals.", w - Math.round(66 * sx)),
                    x + Math.round(52 * sx), infoY + Math.round(35 * sy), bankMuted(), 0.86F);
        }
        mobileButton(g, font, x, navY - Math.round(52 * sy), w, Math.max(34, Math.round(40 * sy)), "Change Payment Card", BANK_BLUE,
                hitboxes, "APP", "all-cards", "", "");
        drawBankDisplayBottomNav(g, font, x, navY, w, "cards", hitboxes);
    }

    private static void drawCalculator(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        drawPhoneAppSurface(g, x, y, w, h, 0xFF050506);
        drawStatusBar(g, font, x, y - 31, w, 0xFFF7F7F7, 0xFF050506);

        int sidePad = Mth.clamp(Math.round(w * 0.025F), 4, 8);
        boolean tightCalculator = h < 260;
        boolean microCalculator = h < 160;
        int gap = Mth.clamp(Math.round(w * 0.026F), microCalculator ? 2 : 4, 7);
        int minDisplay = microCalculator ? 16 : (tightCalculator ? 34 : 74);
        int minKey = microCalculator ? 10 : (tightCalculator ? 20 : 28);
        int verticalPad = Math.max(microCalculator ? 2 : (tightCalculator ? 4 : 10),
                Math.round(h * (tightCalculator ? 0.018F : 0.035F)));
        int keyByWidth = Math.max(1, (w - sidePad * 2 - gap * 3) / 4);
        int keyByHeight = Math.max(1, (h - minDisplay - gap * 4 - verticalPad) / 5);
        int key = Mth.clamp(Math.min(keyByWidth, keyByHeight), Math.min(minKey, Math.max(1, Math.min(keyByWidth, keyByHeight))), 54);
        int gridH = key * 5 + gap * 4;
        int gridW = key * 4 + gap * 3;
        int startX = x + sidePad + Math.max(0, (w - sidePad * 2 - gridW) / 2);
        int availableDisplayH = Math.max(1, h - gridH - verticalPad);
        if (availableDisplayH < minDisplay && gap > 1) {
            gap = 1;
            keyByWidth = Math.max(1, (w - sidePad * 2 - gap * 3) / 4);
            keyByHeight = Math.max(1, (h - minDisplay - gap * 4 - verticalPad) / 5);
            key = Mth.clamp(Math.min(keyByWidth, keyByHeight), Math.min(minKey, Math.max(1, Math.min(keyByWidth, keyByHeight))), 54);
            gridH = key * 5 + gap * 4;
            gridW = key * 4 + gap * 3;
            startX = x + sidePad + Math.max(0, (w - sidePad * 2 - gridW) / 2);
            availableDisplayH = Math.max(1, h - gridH - verticalPad);
        }
        int displayMin = Math.min(minDisplay, availableDisplayH);
        int displayH = Mth.clamp(Math.round(h * (tightCalculator ? 0.22F : 0.31F)), displayMin, availableDisplayH);
        int displayBottom = y + displayH;
        String expression = SmartphoneClientState.calculatorInput();
        String shownExpression = expression == null || expression.isBlank() ? "0" : expression;
        String display = SmartphoneClientState.calculatorResultCurrent()
                ? SmartphoneClientState.calculatorResult()
                : shownExpression;
        String displayText = calculatorDisplayText(display);
        int displayWidth = Math.max(28, gridW);
        float displaySize = fitPhoneTextSize(displayText, displayWidth, tightCalculator ? 22.0F : 31.0F, 11.0F);
        String fittedDisplay = trimPhoneTextFromStart(displayText, displayWidth, displaySize);
        int displayTextW = phoneTextWidth(fittedDisplay, displaySize);
        int displayY = Math.max(y + 12, displayBottom - Math.round(displaySize * 1.45F));
        String expressionPreview = SmartphoneClientState.calculatorExpressionPreview();
        if (!expressionPreview.isBlank() && availableDisplayH >= (tightCalculator ? 36 : 54)) {
            float previewSize = fitPhoneTextSize(expressionPreview, displayWidth, tightCalculator ? 9.5F : 12.0F, 7.0F);
            String fittedPreview = trimPhoneTextFromStart(expressionPreview, displayWidth, previewSize);
            int previewW = phoneTextWidth(fittedPreview, previewSize);
            int previewY = Math.max(y + 8, displayY - Math.round(previewSize * 1.55F));
            drawPhoneText(g, fittedPreview, startX + gridW - previewW - 2, previewY, 0xFF8E8E93, previewSize);
        }
        drawPhoneText(g, fittedDisplay, startX + gridW - displayTextW - 2, displayY, 0xFFFFFFFF, displaySize);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, displayH),
                "INPUT", SmartphoneClientState.InputTarget.CALCULATOR.name(), "", ""));

        int preferredGap = microCalculator ? 1 : (tightCalculator ? 4 : 8);
        int bottomAlignedY = y + h - gridH - verticalPad;
        int startY = bottomAlignedY >= displayBottom + preferredGap
                ? bottomAlignedY
                : Math.max(y, y + h - gridH);
        String clearLabel = SmartphoneClientState.calculatorAtResetState() ? "AC" : "C";
        drawCalcKey(g, font, startX, startY, key, clearLabel, 0xFFA5A5A5, 0xFF050506, hitboxes, "CALC_CLEAR", "");
        drawCalcKey(g, font, startX + key + gap, startY, key, "+/-", 0xFFA5A5A5, 0xFF050506, hitboxes, "CALC_TOGGLE_SIGN", "");
        drawCalcKey(g, font, startX + (key + gap) * 2, startY, key, "%", 0xFFA5A5A5, 0xFF050506, hitboxes, "CALC_PERCENT", "");
        drawCalcOperatorKey(g, font, startX + (key + gap) * 3, startY, key, "/", hitboxes);

        String[][] rows = {
                {"7", "8", "9", "*"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"}
        };
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length; col++) {
                String value = rows[row][col];
                int keyX = startX + (key + gap) * col;
                int keyY = startY + (key + gap) * (row + 1);
                if (isCalcOperatorValue(value)) {
                    drawCalcOperatorKey(g, font, keyX, keyY, key, value, hitboxes);
                } else {
                    drawCalcKey(g, font, keyX, keyY, key, value, 0xFF333336, 0xFFFFFFFF, hitboxes, "CALC_KEY", value);
                }
            }
        }
        int zeroY = startY + (key + gap) * 4;
        int zeroW = key * 2 + gap;
        drawCalcKey(g, font, startX, zeroY, zeroW, key, "0", 0xFF333336, 0xFFFFFFFF, hitboxes, "CALC_KEY", "0");
        drawCalcKey(g, font, startX + zeroW + gap, zeroY, key, ".", 0xFF333336, 0xFFFFFFFF, hitboxes, "CALC_KEY", ".");
        drawCalcKey(g, font, startX + zeroW + gap + key + gap, zeroY, key, "=", 0xFFFF9F0A, 0xFFFFFFFF, hitboxes, "CALC_EVAL", "");
    }

    private static void drawCalcOperatorKey(GuiGraphics g, Font font, int x, int y, int size, String value,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean active = SmartphoneClientState.calculatorAwaitingOperand()
                && value.equals(SmartphoneClientState.calculatorPendingOperator());
        int fill = active ? 0xFFFFFFFF : 0xFFFF9F0A;
        int text = active ? 0xFFFF9F0A : 0xFFFFFFFF;
        drawCalcKey(g, font, x, y, size, calcOperatorLabel(value), fill, text, hitboxes, "CALC_KEY", value);
    }

    private static boolean isCalcOperatorValue(String value) {
        return "+".equals(value) || "-".equals(value) || "*".equals(value) || "/".equals(value);
    }

    private static String calcOperatorLabel(String value) {
        return switch (value) {
            case "/" -> "\u00F7";
            case "*" -> "\u00D7";
            default -> value;
        };
    }

    private static String calculatorDisplayText(String value) {
        String safe = value == null || value.isBlank() ? "0" : value;
        return safe.replace("*", "\u00D7").replace("/", "\u00F7");
    }

    private static void drawCalcKey(GuiGraphics g, Font font, int x, int y, int size, String label,
                                    int color, int textColor, List<SmartphoneClientState.Hitbox> hitboxes,
                                    String action, String value) {
        fillCircle(g, x + size / 2, y + size / 2, size / 2, color);
        float scale = calcKeyTextScale(label, size);
        drawScaledCentered(g, font, label, x + size / 2, y + Math.round(size * 0.35F), textColor, scale);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, size, size),
                action, value, "", ""));
    }

    private static void drawCalcKey(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                                    int color, int textColor, List<SmartphoneClientState.Hitbox> hitboxes,
                                    String action, String value) {
        fillRounded(g, x, y, w, h, h / 2, color);
        float scale = calcKeyTextScale(label, h);
        int textX = "0".equals(label) ? x + Math.round(h * 0.45F) : x + w / 2;
        if ("0".equals(label)) {
            drawScaledString(g, font, label, textX, y + Math.round(h * 0.35F), textColor, scale);
        } else {
            drawScaledCentered(g, font, label, textX, y + Math.round(h * 0.35F), textColor, scale);
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                action, value, "", ""));
    }

    private static float calcKeyTextScale(String label, int keySize) {
        float base = label != null && label.length() > 1 ? 0.88F : 1.45F;
        return base * Mth.clamp(keySize / 42.0F, 0.58F, 1.0F);
    }

    private static void drawPaint(GuiGraphics g, Font font, int x, int y, int w, int h,
                                  List<SmartphoneClientState.Hitbox> hitboxes) {
        drawPhoneAppSurface(g, x, y, w, h, 0xFFF7F8FB);
        drawStatusBar(g, font, x, y - 31, w, 0xFF111318, 0xFFF7F8FB);
        boolean compactPaint = h < 300;
        boolean tightPaint = h < 230;
        boolean microPaint = h < 150;
        drawScaledString(g, font, "Paint", x, y, 0xFF111318, microPaint ? 0.92F : (tightPaint ? 1.22F : 1.54F));
        if (!tightPaint) {
            drawScaledString(g, font, "Pixel sketch pad", x, y + 26, 0xFF8E8E93, 0.78F);
        }

        int selected = SmartphoneClientState.paintIndex();
        String pixels = SmartphoneClientState.paintingDraft();
        int filled = paintedPixelCount(pixels);
        drawScaledString(g, font, "Slot " + (selected + 1), x + w - 58,
                y + (tightPaint ? 3 : 6), SmartphoneClientState.accentColor(), microPaint ? 0.56F : (tightPaint ? 0.68F : 0.78F));

        int buttonH = microPaint
                ? Mth.clamp(Math.round(h * 0.18F), 12, 20)
                : Mth.clamp(Math.round(h * (tightPaint ? 0.095F : 0.08F)),
                tightPaint ? 22 : 32, tightPaint ? 30 : 42);
        int slotsH = microPaint
                ? Mth.clamp(Math.round(h * 0.22F), 14, 28)
                : Mth.clamp(Math.round(h * (tightPaint ? 0.16F : 0.15F)),
                tightPaint ? 30 : 44, tightPaint ? 42 : 62);
        int bottomPad = microPaint ? 3 : 8;
        int slotsGap = microPaint ? 3 : (tightPaint ? 8 : 14);
        int paletteGap = microPaint ? 2 : (tightPaint ? 6 : 10);
        int buttonY = y + h - buttonH - bottomPad;
        int slotsY = buttonY - slotsH - slotsGap;
        int paletteH = microPaint ? Mth.clamp(Math.round(h * 0.16F), 8, 18) : (tightPaint ? 20 : 26);
        int paletteY = slotsY - paletteH - paletteGap;
        int boardTop = y + (microPaint ? 14 : (tightPaint ? 28 : (compactPaint ? 48 : 56)));
        int boardAreaH = Math.max(0, paletteY - boardTop - (microPaint ? 3 : (tightPaint ? 6 : 12)));
        int board = Math.min(w, boardAreaH);
        if (board >= 48) {
            int boardX = x + (w - board) / 2;
            fillRounded(g, boardX, boardTop, board, board, 24, 0xFFFFFFFF);
            drawRoundedBorder(g, boardX, boardTop, board, board, 24, 0xFFE1E1E6, 0xFFFFFFFF);
            int cellGap = Math.max(1, board / 80);
            int cell = Math.max(2, (board - 28 - cellGap * 8) / 9);
            int grid = cell * 9 + cellGap * 8;
            int gridX = boardX + (board - grid) / 2;
            int gridY = boardTop + (board - grid) / 2;
            for (int i = 0; i < 81; i++) {
                int px = gridX + (i % 9) * (cell + cellGap);
                int py = gridY + (i / 9) * (cell + cellGap);
                boolean painted = pixels != null && i < pixels.length() && pixels.charAt(i) != '.';
                int c = painted ? paintPixelColor(pixels.charAt(i)) : 0xFFE9ECF2;
                fillRounded(g, px, py, cell, cell, Math.max(2, cell / 4), c);
                hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(px, py, cell, cell),
                        "PAINT_CELL", String.valueOf(i), "", ""));
            }
            drawScaledCentered(g, font, filled + "/81 pixels", boardX + board / 2, boardTop + board - 18,
                    0xFF8E8E93, 0.72F);
        }

        drawPaintPalette(g, font, x, paletteY, w, paletteH, hitboxes);

        int slotCount = 6;
        int minSlotW = 12;
        int maxSlotGap = Math.max(1, (w - minSlotW * slotCount) / Math.max(1, slotCount - 1));
        int gap = Math.min(Mth.clamp(w / 58, 3, 7), maxSlotGap);
        int slotW = Math.max(1, (w - gap * (slotCount - 1)) / slotCount);
        for (int i = 0; i < slotCount; i++) {
            int sx = x + i * (slotW + gap);
            boolean active = i == selected;
            int slotRadius = Mth.clamp(Math.min(slotW, slotsH) / 3, 1, 14);
            fillRounded(g, sx, slotsY, slotW, slotsH, slotRadius, active ? 0xFFEAF3FF : 0xFFFFFFFF);
            drawRoundedBorder(g, sx, slotsY, slotW, slotsH, slotRadius,
                    active ? SmartphoneClientState.accentColor() : 0xFFE1E1E6,
                    active ? 0xFFEAF3FF : 0xFFFFFFFF);
            String slotPixels = i < SmartphoneClientState.paintings().size()
                    ? SmartphoneClientState.paintings().get(i)
                    : "";
            int preview = Math.max(1, Math.min(Math.max(1, slotW - 8), Math.max(1, slotsH - 18)));
            int previewX = sx + (slotW - preview) / 2;
            int previewY = slotsY + Math.max(4, Math.min(7, (slotsH - preview - 10) / 2));
            drawPaintPreview(g, previewX, previewY, preview, slotPixels);
            if (slotsH >= 22) {
                drawScaledCentered(g, font, String.valueOf(i + 1), sx + slotW / 2, slotsY + slotsH - 14,
                        active ? SmartphoneClientState.accentColor() : 0xFF8E8E93, microPaint ? 0.54F : 0.66F);
            }
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(sx, slotsY, slotW, slotsH),
                    "PAINT_SELECT", String.valueOf(i), "", ""));
        }
        fillRounded(g, x, buttonY, w, buttonH, Mth.clamp(buttonH / 2, 4, 17), SmartphoneClientState.accentColor());
        drawScaledCentered(g, font, filled > 0 ? "Clear Canvas" : "Canvas Empty",
                x + w / 2, buttonY + Math.max(3, Math.round(buttonH * 0.33F)),
                0xFFFFFFFF, microPaint ? 0.58F : 0.92F);
        if (filled > 0) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, buttonY, w, buttonH),
                    "PAINT_CLEAR", "", "", ""));
        }
    }

    private static void drawPaintPalette(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        char[] tools = {'1', '2', '3', '4', '5', '6', '.'};
        int minSwatch = 8;
        int maxGap = Math.max(1, (w - minSwatch * tools.length) / Math.max(1, tools.length - 1));
        int gap = Math.min(Mth.clamp(w / 62, 3, 7), maxGap);
        int swatch = Math.max(1, Math.min(h, (w - gap * (tools.length - 1)) / tools.length));
        int totalW = swatch * tools.length + gap * (tools.length - 1);
        int startX = x + (w - totalW) / 2;
        int y0 = y + (h - swatch) / 2;
        char selected = SmartphoneClientState.paintColor();
        for (int i = 0; i < tools.length; i++) {
            char tool = tools[i];
            int sx = startX + i * (swatch + gap);
            boolean active = selected == tool;
            int fill = tool == '.' ? 0xFFFFFFFF : paintPixelColor(tool);
            fillCircle(g, sx + swatch / 2, y0 + swatch / 2, swatch / 2, active ? SmartphoneClientState.accentColor() : 0xFFE1E1E6);
            fillCircle(g, sx + swatch / 2, y0 + swatch / 2, Math.max(2, swatch / 2 - 3), fill);
            if (tool == '.') {
                drawScaledCentered(g, font, "x", sx + swatch / 2, y0 + Math.max(4, swatch / 2 - 5),
                        0xFF8E8E93, 0.62F);
            }
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(sx, y0, swatch, swatch),
                    "PAINT_COLOR", String.valueOf(tool), "", ""));
        }
    }

    private static void drawPaintPreview(GuiGraphics g, int x, int y, int size, String pixels) {
        int gap = Math.max(1, size / 28);
        int cell = Math.max(1, (size - gap * 2) / 3);
        for (int i = 0; i < 9; i++) {
            int px = x + (i % 3) * (cell + gap);
            int py = y + (i / 3) * (cell + gap);
            int color = pixels != null && i < pixels.length() && pixels.charAt(i) != '.'
                    ? paintPixelColor(pixels.charAt(i))
                    : 0xFFE9ECF2;
            fillRounded(g, px, py, cell, cell, Math.max(1, cell / 4), color);
        }
    }

    private static int paintPixelColor(char value) {
        return switch (Character.toLowerCase(value)) {
            case '1', '6', 'b' -> 0xFF52D7FF;
            case '2', '7', 'c' -> 0xFF23D18B;
            case '3', '8', 'd' -> 0xFFFF6A9C;
            case '4', '9', 'e' -> 0xFFFFCA5C;
            case '5', 'a', 'f' -> 0xFF9B7CFF;
            default -> 0xFF2B3444;
        };
    }

    private static int paintedPixelCount(String pixels) {
        if (pixels == null || pixels.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < Math.min(81, pixels.length()); i++) {
            if (pixels.charAt(i) != '.') {
                count++;
            }
        }
        return count;
    }

    private static void drawContacts(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        drawPhoneAppSurface(g, x, y, w, h, 0xFFF6F7FA);
        drawStatusBar(g, font, x, y - 31, w, 0xFF111318, 0xFFF6F7FA);
        boolean messengerMode = SmartphoneClientState.contactsMessengerMode()
                || SmartphoneClientState.activeApp() == SmartphoneClientState.App.MESSENGER;
        if (!messengerMode && SmartphoneClientState.contactDetailOpen()) {
            SmartphoneClientState.PhoneContact selected = SmartphoneClientState.selectedContact();
            if (selected != null) {
                drawContactDetail(g, font, x, y, w, h, selected, hitboxes);
                return;
            }
        }
        List<SmartphoneClientState.PhoneContact> visibleContacts = SmartphoneClientState.visibleContacts();
        drawScaledString(g, font, messengerMode ? "Messenger" : "Contacts", x, y, 0xFF111318, 1.42F);
        String countLabel = SmartphoneClientState.contactSearch().isBlank()
                ? SmartphoneClientState.contacts().size() + " contacts"
                : visibleContacts.size() + " results";
        drawScaledString(g, font, countLabel, x, y + 23, 0xFF8E8E93, 0.74F);

        int searchY = y + 45;
        int searchH = 36;
        boolean searchActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.CONTACT_SEARCH;
        boolean searchHasText = !SmartphoneClientState.contactSearch().isBlank();
        boolean showCancel = searchActive || searchHasText;
        int cancelW = showCancel ? Math.min(50, Math.max(42, w / 6)) : 0;
        int searchW = showCancel ? Math.max(90, w - cancelW - 8) : w;
        fillRounded(g, x, searchY, searchW, searchH, 18, 0xFFFFFFFF);
        drawRoundedBorder(g, x, searchY, searchW, searchH, 18,
                searchActive ? 0xFF0078FF : 0xFFE1E1E6, 0xFFFFFFFF);
        int iconX = x + 18;
        int iconY = searchY + searchH / 2;
        fillCircle(g, iconX, iconY - 1, 5, 0xFF8E8E93);
        fillCircle(g, iconX, iconY - 1, 3, 0xFFFFFFFF);
        g.fill(iconX + 4, iconY + 3, iconX + 10, iconY + 5, 0xFF8E8E93);
        drawFieldText(g, font, x + 34, searchY + 13, searchW - 72,
                SmartphoneClientState.contactSearch(), searchActive, "Search player",
                0xFF111318, 0xFF8E8E93, 0xFF111318);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, searchY, searchW, searchH),
                "INPUT", SmartphoneClientState.InputTarget.CONTACT_SEARCH.name(), "", ""));
        if (searchHasText) {
            drawFigmaCloseGlyph(g, x + searchW - 20, iconY, 0xFF8E8E93, 0.62F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + searchW - 38, searchY, 38, searchH),
                    "CONTACT_SEARCH_CLEAR", "", "", ""));
        }
        if (showCancel) {
            drawScaledCentered(g, font, "Cancel", x + searchW + 4 + cancelW / 2, searchY + 12,
                    0xFF0078FF, 0.76F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + searchW + 2, searchY, cancelW + 6, searchH),
                    "CONTACT_SEARCH_CANCEL", "", "", ""));
        }

        boolean compactList = h < 330;
        int rowH = compactList ? Mth.clamp(Math.round(h * 0.13F), 34, 42) : 50;
        int listY = searchY + searchH + 18;
        int listH = Math.max(0, y + h - listY - 8);
        if (listH > 0 && listH < rowH) {
            rowH = listH;
        }
        int visibleRows = listH >= 24 && rowH > 0 ? Math.max(1, listH / rowH) : 0;
        SmartphoneClientState.syncContactListScroll(visibleContacts.size(), Math.max(1, visibleRows));
        int offset = SmartphoneClientState.contactListScrollOffset();
        boolean showSectionIndex = visibleRows > 0 && !showCancel && visibleContacts.size() > visibleRows;
        int listW = showSectionIndex ? w - 16 : w;
        if (!visibleContacts.isEmpty() && listH > 0) {
            int shownRows = Math.min(visibleRows, visibleContacts.size() - offset);
            fillRounded(g, x, listY, listW, Math.min(listH, Math.max(rowH, shownRows * rowH)), 18, 0xFFFFFFFF);
        }
        int cy = listY;
        for (int i = offset; i < visibleContacts.size() && i < offset + visibleRows; i++) {
            SmartphoneClientState.PhoneContact contact = visibleContacts.get(i);
            boolean sectionStart = i == 0
                    || contactSection(contact).charAt(0) != contactSection(visibleContacts.get(i - 1)).charAt(0);
            drawContactListRow(g, font, x, cy, listW, rowH, contact, sectionStart, hitboxes);
            cy += rowH;
        }
        if (visibleContacts.isEmpty() && listH >= 40) {
            if (SmartphoneClientState.contactSearch().isBlank()) {
                drawContactEmptyState(g, font, x, listY, w, listH, "No Players", "Server directory is empty.");
            } else {
                drawContactEmptyState(g, font, x, listY, w, listH, "No Results", "No matching players.");
            }
        }
        if (showSectionIndex) {
            drawContactSectionIndex(g, font, x + w - 11, listY, listH,
                    contactSectionLabels(visibleContacts), hitboxes);
        } else if (visibleRows > 0) {
            drawMessageDraftScrollbar(g, x, listY, w, listH, offset, visibleRows, visibleContacts.size());
        }
    }

    private static void drawContactEmptyState(GuiGraphics g, Font font, int x, int y, int w, int h,
                                              String title, String subtitle) {
        int centerX = x + w / 2;
        int centerY = y + Math.max(24, h / 2);
        if (h >= 72) {
            int iconY = centerY - 24;
            fillCircle(g, centerX - 8, iconY - 5, 7, 0xFFBFC4CC);
            fillCircle(g, centerX + 8, iconY - 5, 7, 0xFFC9CDD4);
            fillRounded(g, centerX - 23, iconY + 5, 20, 14, 7, 0xFFBFC4CC);
            fillRounded(g, centerX + 3, iconY + 5, 20, 14, 7, 0xFFC9CDD4);
            fillCircle(g, centerX, iconY - 2, 8, 0xFF8E8E93);
            fillRounded(g, centerX - 14, iconY + 10, 28, 16, 8, 0xFF8E8E93);
        }
        int titleY = h >= 72 ? centerY + 4 : centerY - 8;
        drawScaledCentered(g, font, title, centerX, titleY, 0xFF111318, 0.98F);
        if (h >= 58) {
            drawScaledCentered(g, font, subtitle, centerX, titleY + 17, 0xFF8E8E93, 0.68F);
        }
    }

    private static void drawContactDetail(GuiGraphics g, Font font, int x, int y, int w, int h,
                                          SmartphoneClientState.PhoneContact contact,
                                          List<SmartphoneClientState.Hitbox> hitboxes) {
        int backSize = 30;
        drawFigmaChevronLeft(g, x + 7, y + 16, 6, 0xFF0078FF);
        drawScaledString(g, font, "Contacts", x + 16, y + 10, 0xFF0078FF, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, Math.min(76, w / 3), backSize),
                "CONTACTS_BACK", "", "", ""));

        drawScaledCentered(g, font, "Contact", x + w / 2, y + 7, 0xFF111318, 1.02F);
        boolean compactContact = h < 330;
        boolean tightContact = h < 260;
        int bottom = y + h - 6;
        int avatar = compactContact
                ? Mth.clamp(Math.min(Math.round(w * 0.20F), Math.round(h * 0.15F)),
                tightContact ? 30 : 36, tightContact ? 42 : 58)
                : Mth.clamp(Math.min(Math.round(w * 0.25F), Math.round(h * 0.18F)), 52, 82);
        int avatarX = x + w / 2 - avatar / 2;
        int avatarY = y + (compactContact ? (tightContact ? 32 : 38) : Mth.clamp(Math.round(h * 0.10F), 42, 56));
        drawContactAvatar(g, font, contact, avatarX, avatarY, avatar);
        int nameY = avatarY + avatar + (tightContact ? 8 : 16);
        drawScaledCentered(g, font, trim(font, contact.name(), w - 22), x + w / 2, nameY,
                0xFF111318, tightContact ? 0.84F : (compactContact ? 1.02F : 1.24F));

        int actionsY = nameY + (tightContact ? 12 : (compactContact ? 20 : Math.max(24, Math.round(h * 0.062F))));
        int availableForActionAndCard = Math.max(0, bottom - actionsY);
        int reservedCardH = tightContact && availableForActionAndCard < 56 ? 0 : (tightContact ? 24 : 32);
        int actionGridH = drawContactActionGrid(g, font, x, actionsY, w,
                Math.max(0, availableForActionAndCard - reservedCardH), contact, compactContact, tightContact, hitboxes);

        int cardY = actionsY + actionGridH + (tightContact ? 6 : (compactContact ? 10 : 18));
        int availableBelowActions = bottom - cardY;
        if (availableBelowActions >= (tightContact ? 24 : 32)) {
            List<ContactInfoData> rows = List.of(
                    new ContactInfoData("Server status", contact.online() ? "Online" : "Offline",
                            contact.online() ? 0xFF20C985 : 0xFF8E8E93),
                    new ContactInfoData("Preferences", contactPreferenceSummary(contact), contactPreferenceDetailColor(contact)),
                    new ContactInfoData("Player ID", shortUuid(contact.id()), 0xFF8E8E93),
                    new ContactInfoData("Unread", String.valueOf(Math.max(0, contact.unread())), 0xFF111318)
            );
            int minRowH = tightContact ? 24 : 32;
            int maxRowH = tightContact ? 34 : 46;
            int preferredRows = Math.min(rows.size(), Math.max(1, availableBelowActions / minRowH));
            int rowH = Mth.clamp(availableBelowActions / preferredRows, minRowH, maxRowH);
            int visibleRows = Math.max(1, Math.min(rows.size(), availableBelowActions / Math.max(1, rowH)));
            SmartphoneClientState.syncContactDetailScroll(rows.size(), visibleRows);
            int offset = SmartphoneClientState.contactDetailScrollOffset();
            int drawnRows = Math.min(rows.size() - offset, visibleRows);
            fillRounded(g, x, cardY, w, rowH * drawnRows, 16, 0xFFFFFFFF);
            for (int i = 0; i < drawnRows; i++) {
                int rowIndex = offset + i;
                ContactInfoData row = rows.get(rowIndex);
                drawContactInfoRow(g, font, x, cardY + rowH * i, w, rowH, row.title(), row.value(), row.color(),
                        rowIndex < rows.size() - 1);
            }
            if (rows.size() > visibleRows) {
                drawMessageDraftScrollbar(g, x, cardY, w, availableBelowActions, offset, visibleRows, rows.size());
            }
        }
    }

    private static void drawContactAction(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int color,
                                          List<SmartphoneClientState.Hitbox> hitboxes, String action, String p1) {
        fillRounded(g, x, y, w, h, Mth.clamp(h / 2, 6, 15), 0xFFFFFFFF);
        int glyphY = y + Math.round(h * 0.34F);
        if (h >= 32) {
            fillCircle(g, x + w / 2, glyphY, Mth.clamp(Math.round(h * 0.24F), 7, 10), color);
            drawContactActionGlyph(g, x + w / 2, glyphY, color, action);
        }
        String safeLabel = trim(font, label, Math.max(8, w - 6));
        float labelScale = h < 32 ? 0.62F : (safeLabel.length() > 8 ? 0.58F : 0.68F);
        int labelY = h < 32 ? y + Math.max(8, h / 2 - 4) : y + Math.round(h * 0.66F);
        drawScaledCentered(g, font, safeLabel, x + w / 2, labelY, color, labelScale);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                action, p1, "", ""));
    }

    private static void drawContactAvatar(GuiGraphics g, Font font, SmartphoneClientState.PhoneContact contact,
                                          int x, int y, int size) {
        int bg = contact != null && contact.online() ? 0xFF0078FF : 0xFFBFC4CC;
        fillCircle(g, x + size / 2, y + size / 2, size / 2, bg);
        ResourceLocation skin = contact == null ? null : resolveSkin(contact.id());
        int inset = Math.max(2, Math.round(size * 0.08F));
        if (skin != null && size - inset * 2 >= 8) {
            PlayerFaceRenderer.draw(g, skin, x + inset, y + inset, Math.max(1, size - inset * 2));
            maskOutsideCircle(g, x, y, size, x + size / 2, y + size / 2, size / 2, 0xFFF6F7FA);
            if (contact != null && !contact.online()) {
                g.fill(x, y, x + size, y + size, 0x33000000);
                maskOutsideCircle(g, x, y, size, x + size / 2, y + size / 2, size / 2, 0xFFF6F7FA);
            }
            return;
        }
        String initials = contact == null ? "?" : initials(contact.name());
        drawScaledCentered(g, font, initials, x + size / 2, y + Math.round(size * 0.34F),
                0xFFFFFFFF, Mth.clamp(size / 52.0F, 0.82F, 1.38F));
    }

    private static int drawContactActionGrid(GuiGraphics g, Font font, int x, int y, int w, int availableH,
                                             SmartphoneClientState.PhoneContact contact,
                                             boolean compactContact, boolean tightContact,
                                             List<SmartphoneClientState.Hitbox> hitboxes) {
        if (availableH < 18) {
            return 0;
        }
        ContactActionData[] actions = {
                new ContactActionData("Message", "CONTACT_MESSAGE", 0xFF0078FF, contact.id().toString()),
                new ContactActionData(contact.favorite() ? "Unfavorite" : "Favorite",
                        contact.favorite() ? "UNFAVORITE" : "FAVORITE", 0xFFFFB800, contact.id().toString()),
                new ContactActionData(contact.muted() ? "Unmute" : "Mute",
                        contact.muted() ? "UNMUTE" : "MUTE", 0xFF8E8E93, contact.id().toString()),
                new ContactActionData(contact.blocked() ? "Unblock" : "Block",
                        contact.blocked() ? "UNBLOCK" : "BLOCK", 0xFFFF3B30, contact.id().toString()),
                new ContactActionData("Report", "REPORT", 0xFFFF3B30, contact.id().toString())
        };
        int gap = tightContact ? 4 : (compactContact ? 6 : 8);
        int minActionH = tightContact ? 24 : 34;
        int preferredActionH = compactContact
                ? Mth.clamp(Math.round(availableH * 0.48F), minActionH, tightContact ? 34 : 42)
                : Mth.clamp(Math.round(availableH * 0.42F), 38, 50);
        int cols = w >= 285 ? actions.length : (w >= 210 ? 3 : 2);
        int rows = Math.max(1, (actions.length + cols - 1) / cols);
        if (availableH < minActionH * rows + gap * (rows - 1)) {
            cols = Math.min(actions.length, Math.max(1, w / 44));
            rows = Math.max(1, (actions.length + cols - 1) / cols);
        }
        int maxFittingActionH = Math.max(0, (availableH - gap * (rows - 1)) / rows);
        int actionH = Math.min(preferredActionH, Math.max(18, maxFittingActionH));
        actionH = Math.min(actionH, maxFittingActionH);
        if (actionH < 18) {
            return 0;
        }
        int actionW = Math.max(1, (w - gap * (cols - 1)) / cols);
        int shown = Math.min(actions.length, rows * cols);
        for (int i = 0; i < shown; i++) {
            int col = i % cols;
            int row = i / cols;
            ContactActionData data = actions[i];
            drawContactAction(g, font, x + col * (actionW + gap), y + row * (actionH + gap),
                    actionW, actionH, data.label(), data.color(), hitboxes, data.action(), data.p1());
        }
        return rows * actionH + gap * (rows - 1);
    }

    private record ContactActionData(String label, String action, int color, String p1) {
    }

    private record ContactInfoData(String title, String value, int color) {
    }

    private static void drawContactActionGlyph(GuiGraphics g, int cx, int cy, int color, String action) {
        int white = 0xFFFFFFFF;
        String safeAction = action == null ? "" : action;
        if ("CONTACT_MESSAGE".equals(safeAction)) {
            fillRounded(g, cx - 6, cy - 4, 12, 8, 4, white);
            g.fill(cx - 2, cy + 3, cx + 3, cy + 6, white);
            return;
        }
        if ("FAVORITE".equals(safeAction) || "UNFAVORITE".equals(safeAction)) {
            drawSoftLine(g, cx, cy - 6, cx + 2, cy - 1, white);
            drawSoftLine(g, cx + 2, cy - 1, cx + 7, cy - 1, white);
            drawSoftLine(g, cx + 7, cy - 1, cx + 3, cy + 2, white);
            drawSoftLine(g, cx + 3, cy + 2, cx + 5, cy + 7, white);
            drawSoftLine(g, cx + 5, cy + 7, cx, cy + 4, white);
            drawSoftLine(g, cx, cy + 4, cx - 5, cy + 7, white);
            drawSoftLine(g, cx - 5, cy + 7, cx - 3, cy + 2, white);
            drawSoftLine(g, cx - 3, cy + 2, cx - 7, cy - 1, white);
            drawSoftLine(g, cx - 7, cy - 1, cx - 2, cy - 1, white);
            drawSoftLine(g, cx - 2, cy - 1, cx, cy - 6, white);
            return;
        }
        if ("BLOCK".equals(safeAction) || "UNBLOCK".equals(safeAction)) {
            drawCircleRing(g, cx, cy, 7, 2, white, color);
            drawSoftLine(g, cx - 5, cy + 5, cx + 5, cy - 5, white);
            return;
        }
        if ("REPORT".equals(safeAction)) {
            fillRounded(g, cx - 6, cy - 7, 12, 14, 3, white);
            g.fill(cx - 1, cy - 4, cx + 2, cy + 2, color);
            g.fill(cx - 1, cy + 4, cx + 2, cy + 6, color);
            return;
        }
        int muted = 0xCCFFFFFF;
        g.fill(cx - 7, cy - 4, cx - 3, cy + 4, white);
        drawSoftLine(g, cx - 3, cy - 4, cx + 3, cy - 8, white);
        drawSoftLine(g, cx - 3, cy + 4, cx + 3, cy + 8, white);
        drawSoftLine(g, cx + 5, cy - 5, cx + 8, cy - 2, muted);
        drawSoftLine(g, cx + 8, cy - 2, cx + 8, cy + 2, muted);
        drawSoftLine(g, cx + 5, cy + 5, cx + 8, cy + 2, muted);
    }

    private static void drawContactInfoRow(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           String label, String value, int valueColor, boolean divider) {
        boolean compact = h < 34;
        float labelScale = compact ? 0.72F : 0.86F;
        float valueScale = compact ? 0.70F : 0.82F;
        int inset = Math.min(14, Math.max(4, w / 12));
        int textY = compact ? y + Math.max(5, h / 2 - 5) : y + 14;
        int availableW = Math.max(12, w - inset * 2);
        int labelW = compact ? Math.max(12, availableW / 2) : Math.max(24, Math.min(96, availableW / 2));
        int valueAreaW = Math.max(12, availableW - labelW - 6);
        if (!compact && w < 150 && h >= 42) {
            drawScaledString(g, font, trim(font, label, availableW), x + inset, y + 8,
                    0xFF111318, 0.78F);
            drawScaledString(g, font, trim(font, value, availableW), x + inset, y + 25,
                    valueColor, 0.72F);
        } else {
            drawScaledString(g, font, trim(font, label, labelW), x + inset, textY,
                    0xFF111318, labelScale);
            String fittedValue = trim(font, value, valueAreaW);
            int valueW = phoneTextWidth(fittedValue, valueScale * 9.75F);
            int valueX = x + w - inset - Math.min(valueW, valueAreaW);
            valueX = Math.max(x + inset + labelW + 4, Math.min(valueX, x + w - inset - 1));
            drawScaledString(g, font, fittedValue, valueX, compact ? textY : y + 15,
                    valueColor, valueScale);
        }
        if (divider) {
            g.fill(x + inset, y + h - 1, x + w, y + h, 0xFFE5E5EA);
        }
    }

    private static void drawMessenger(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      List<SmartphoneClientState.Hitbox> hitboxes) {
        SmartphoneClientState.PhoneContact contact = SmartphoneClientState.selectedContact();
        drawPhoneAppSurface(g, x, y, w, h, 0xFFFCFCFE);
        drawStatusBar(g, font, x, y - 31, w, 0xFF111318, 0xFFFCFCFE);
        if (contact == null) {
            drawContacts(g, font, x, y, w, h, hitboxes);
            return;
        }

        int headerH = 82;
        drawMessengerHeader(g, font, x, y, w, contact, hitboxes);
        int controlsY = y + headerH;
        drawMessengerControls(g, font, x, controlsY, w, contact, hitboxes);

        int composerH = messageComposerHeight(font, w);
        int composerY = y + h - composerH;
        int threadTop = controlsY + 32;
        int threadBottom = composerY - 10;
        List<SmartphoneClientState.PhoneMessage> messages = SmartphoneClientState.messagesForSelectedContact();
        boolean typing = SmartphoneClientState.selectedContactTyping();
        if (messages.isEmpty() && !typing) {
            SmartphoneClientState.syncMessageThreadScroll(0, Math.max(1, threadBottom - threadTop));
            drawScaledCentered(g, font, "No messages yet", x + w / 2, threadTop + 48, 0xFF8E8E93, 0.92F);
            drawScaledCentered(g, font, "Start the conversation below.", x + w / 2, threadTop + 67, 0xFFB0B0B6, 0.78F);
        } else {
            List<ChatMessageLayout> layouts = buildChatMessageLayouts(font, messages, w);
            int typingRowH = typing ? 46 : 0;
            int contentH = typingRowH;
            for (ChatMessageLayout layout : layouts) {
                contentH += layout.rowH();
            }
            int viewportH = Math.max(1, threadBottom - threadTop);
            SmartphoneClientState.syncMessageThreadScroll(contentH, viewportH);
            int cy = threadBottom + SmartphoneClientState.messageThreadScrollOffset();
            if (typing) {
                cy -= typingRowH;
                if (cy + typingRowH >= threadTop && cy <= threadBottom) {
                    drawTypingBubble(g, x, cy, w);
                }
            }
            for (ChatMessageLayout layout : layouts) {
                cy -= layout.rowH();
                if (cy + layout.rowH() < threadTop) {
                    break;
                }
                if (cy >= threadTop && cy + layout.rowH() <= threadBottom) {
                    if (layout.message().moneyAction()) {
                        drawPayRequestBubble(g, font, x, cy, w, layout.maxBubbleW(), layout.bubbleH(),
                                layout.message(), layout.outgoing(), hitboxes);
                    } else {
                        drawMessageBubble(g, font, x, cy, w, layout.maxBubbleW(), layout.bubbleH(),
                                layout.lines(), layout.message().time(), layout.outgoing());
                    }
                }
            }
            drawMessageThreadScrollbar(g, x, threadTop, w, viewportH,
                    SmartphoneClientState.messageThreadScrollOffset(), contentH);
        }
        drawMessageComposer(g, font, x, composerY, w, composerH, hitboxes);
    }

    private static List<ChatMessageLayout> buildChatMessageLayouts(Font font,
                                                                   List<SmartphoneClientState.PhoneMessage> messages,
                                                                   int w) {
        if (messages == cachedChatLayoutMessages && w == cachedChatLayoutWidth) {
            return cachedChatLayouts;
        }
        List<ChatMessageLayout> layouts = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            SmartphoneClientState.PhoneMessage message = messages.get(i);
            boolean outgoing = SmartphoneClientState.isOutgoingMessage(message);
            int maxBubbleW = message.moneyAction()
                    ? Math.max(168, Math.round(w * 0.86F))
                    : Math.max(94, Math.round(w * 0.74F));
            List<String> lines = message.moneyAction()
                    ? List.of()
                    : wrapPhoneText(font, message.body(), maxBubbleW - 22, 10.5F, CHAT_MESSAGE_MAX_LINES);
            int bubbleH = message.moneyAction()
                    ? payRequestBubbleHeight(message, outgoing)
                    : Math.max(27, 13 + lines.size() * 13);
            int rowH = bubbleH + 18;
            layouts.add(new ChatMessageLayout(message, outgoing, maxBubbleW, bubbleH, rowH, lines));
        }
        cachedChatLayoutMessages = messages;
        cachedChatLayoutWidth = w;
        cachedChatLayouts = layouts;
        return layouts;
    }

    private static void drawTypingBubble(GuiGraphics g, int x, int y, int w) {
        int bubbleW = 56;
        int bubbleH = 31;
        int bubbleX = x + 4;
        int bubbleY = y + 8;
        int color = 0xFFE9E9EB;
        fillRounded(g, bubbleX, bubbleY, bubbleW, bubbleH, 15, color);
        int tailY = bubbleY + bubbleH - 9;
        g.fill(bubbleX - 1, tailY, bubbleX + 8, tailY + 8, color);
        fillCircle(g, bubbleX - 1, tailY + 7, 4, color);

        long now = System.currentTimeMillis();
        float base = (now % 1260L) / 1260.0F;
        for (int i = 0; i < 3; i++) {
            float phase = (base - i * 0.18F + 1.0F) % 1.0F;
            float pulse = (Mth.sin(phase * (float) (Math.PI * 2.0D)) + 1.0F) * 0.5F;
            int alpha = Mth.clamp(Math.round(118.0F + pulse * 110.0F), 118, 228);
            int radius = 3 + Math.round(pulse);
            int dotX = bubbleX + 19 + i * 9;
            int dotY = bubbleY + 16 - Math.round(pulse * 2.0F);
            fillCircle(g, dotX, dotY, radius, withAlpha(0xFF8E8E93, alpha));
        }
    }

    private static void drawContactListRow(GuiGraphics g, Font font, int x, int y, int w, int rowH,
                                           SmartphoneClientState.PhoneContact contact, boolean sectionStart,
                                           List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean compact = rowH < 44;
        int avatar = compact ? 18 : 26;
        int avatarX = x + 14;
        int avatarY = y + (rowH - avatar) / 2 + (sectionStart && !compact ? 6 : 0);
        drawContactListAvatar(g, font, contact, avatarX, avatarY, avatar);
        int textX = avatarX + avatar + 10;
        int trailingSpace = contact.unread() > 0 ? (compact ? 26 : 32) : 18;
        int nameY = compact ? y + Math.max(12, rowH / 2 - 4) : (sectionStart ? y + 22 : y + 18);
        if (sectionStart) {
            drawScaledString(g, font, contactSection(contact), textX, y + (compact ? 3 : 5),
                    0xFF8E8E93, compact ? 0.54F : 0.62F);
        }
        drawScaledString(g, font, trim(font, contact.name(), w - (textX - x) - trailingSpace),
                textX, nameY, 0xFF111318, compact ? 0.84F : (sectionStart ? 0.92F : 0.98F));
        if (!compact) {
            String state = contactListSubtitle(contact);
            drawScaledString(g, font, trim(font, state, w - (textX - x) - trailingSpace),
                    textX, y + rowH - 15, contactListSubtitleColor(contact), 0.62F);
        }
        if (contact.unread() > 0) {
            String unread = String.valueOf(Math.min(99, contact.unread()));
            int badge = compact ? 14 : 18;
            fillCircle(g, x + w - badge / 2, y + rowH / 2, badge / 2, 0xFF0078FF);
            drawScaledCentered(g, font, unread, x + w - badge / 2, y + rowH / 2 - (compact ? 4 : 5),
                    0xFFFFFFFF, compact ? 0.56F : 0.66F);
        }
        g.fill(x + 14, y + rowH - 1, x + w, y + rowH, 0xFFE6E6EA);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, rowH),
                "CONTACT", contact.id().toString(), "", ""));
    }

    private static void drawContactListAvatar(GuiGraphics g, Font font, SmartphoneClientState.PhoneContact contact,
                                              int x, int y, int size) {
        int center = size / 2;
        int fill = contact != null && contact.online() ? 0xFF0078FF : 0xFFD1D5DB;
        fillCircle(g, x + center, y + center, center, fill);
        ResourceLocation skin = contact == null ? null : resolveSkin(contact.id());
        int inset = Math.max(1, Math.round(size * 0.10F));
        if (skin != null && size - inset * 2 >= 8) {
            PlayerFaceRenderer.draw(g, skin, x + inset, y + inset, Math.max(1, size - inset * 2));
            maskOutsideCircle(g, x, y, size, x + center, y + center, center, 0xFFFFFFFF);
        } else {
            drawScaledCentered(g, font, contact == null ? "?" : initials(contact.name()),
                    x + center, y + Math.round(size * 0.32F), 0xFFFFFFFF,
                    Mth.clamp(size / 34.0F, 0.52F, 0.82F));
        }
        int dot = Math.max(3, Math.round(size * 0.18F));
        fillCircle(g, x + size - dot, y + size - dot, dot, 0xFFFFFFFF);
        fillCircle(g, x + size - dot, y + size - dot, Math.max(1, dot - 1),
                contact != null && contact.online() ? 0xFF20C985 : 0xFF9CA3AF);
    }

    private static String contactSection(SmartphoneClientState.PhoneContact contact) {
        String name = contact == null || contact.name() == null ? "" : contact.name().trim();
        if (name.isBlank()) {
            return "#";
        }
        char first = Character.toUpperCase(name.charAt(0));
        return Character.isLetterOrDigit(first) ? String.valueOf(first) : "#";
    }

    private static String contactListSubtitle(SmartphoneClientState.PhoneContact contact) {
        StringBuilder builder = new StringBuilder(contact != null && contact.online() ? "Online" : "Offline");
        appendContactFlag(builder, contact != null && contact.favorite(), "Favorite");
        appendContactFlag(builder, contact != null && contact.muted(), "Muted");
        appendContactFlag(builder, contact != null && contact.blocked(), "Blocked");
        return builder.toString();
    }

    private static String contactPreferenceSummary(SmartphoneClientState.PhoneContact contact) {
        StringBuilder builder = new StringBuilder();
        appendContactFlag(builder, contact != null && contact.favorite(), "Favorite");
        appendContactFlag(builder, contact != null && contact.muted(), "Muted");
        appendContactFlag(builder, contact != null && contact.blocked(), "Blocked");
        return builder.isEmpty() ? "None" : builder.toString();
    }

    private static void appendContactFlag(StringBuilder builder, boolean enabled, String label) {
        if (!enabled) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" · ");
        }
        builder.append(label);
    }

    private static int contactPreferenceDetailColor(SmartphoneClientState.PhoneContact contact) {
        if (contact != null && contact.blocked()) {
            return 0xFFFF3B30;
        }
        if (contact != null && contact.favorite()) {
            return 0xFFFFB800;
        }
        return 0xFF8E8E93;
    }

    private static int contactListSubtitleColor(SmartphoneClientState.PhoneContact contact) {
        if (contact != null && contact.blocked()) {
            return 0xFFFF3B30;
        }
        return contact != null && contact.online() ? 0xFF20C985 : 0xFF8E8E93;
    }

    private static List<String> contactSectionLabels(List<SmartphoneClientState.PhoneContact> contacts) {
        Set<String> labels = new LinkedHashSet<>();
        for (SmartphoneClientState.PhoneContact contact : contacts) {
            labels.add(contactSection(contact));
        }
        return new ArrayList<>(labels);
    }

    private static void drawContactSectionIndex(GuiGraphics g, Font font, int x, int y, int h,
                                                List<String> sections,
                                                List<SmartphoneClientState.Hitbox> hitboxes) {
        if (sections == null || sections.isEmpty() || h <= 0) {
            return;
        }
        int maxLabels = Math.max(1, (Math.max(0, h - 7) / 6) + 1);
        List<String> visibleSections = sections;
        if (sections.size() > maxLabels) {
            visibleSections = new ArrayList<>();
            int last = sections.size() - 1;
            for (int i = 0; i < maxLabels; i++) {
                int sectionIndex = maxLabels <= 1 ? 0 : Math.round(i * (last / (float) (maxLabels - 1)));
                String section = sections.get(Mth.clamp(sectionIndex, 0, last));
                if (visibleSections.isEmpty() || !visibleSections.get(visibleSections.size() - 1).equals(section)) {
                    visibleSections.add(section);
                }
            }
        }
        int count = visibleSections.size();
        int step = count <= 1 ? 0 : Math.max(6, Math.min(12, Math.max(1, (h - 7) / Math.max(1, count - 1))));
        float scale = step <= 6 ? 0.50F : 0.56F;
        int totalH = count <= 1 ? 7 : (count - 1) * step + 7;
        int startY = y + Math.max(0, (h - totalH) / 2);
        for (int i = 0; i < count; i++) {
            String label = visibleSections.get(i);
            int labelY = startY + i * step;
            drawScaledCentered(g, font, label, x + 4, labelY, 0xFF0078FF, scale);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x - 6, labelY - 2, 20, Math.max(6, step)),
                    "CONTACT_JUMP", label, "", ""));
        }
    }

    private static void drawMessengerHeader(GuiGraphics g, Font font, int x, int y, int w,
                                            SmartphoneClientState.PhoneContact contact,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        int backSize = 28;
        fillCircle(g, x + backSize / 2, y + 17, backSize / 2, 0xFFF0F2F6);
        drawScaledCentered(g, font, "<", x + backSize / 2, y + 10, 0xFF0078FF, 1.05F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y + 3, backSize, backSize),
                "APP", "messenger", "", ""));

        int avatar = 42;
        int avatarX = x + w / 2 - avatar / 2;
        fillCircle(g, avatarX + avatar / 2, y + 3 + avatar / 2, avatar / 2,
                contact.online() ? 0xFF0078FF : 0xFFBFC4CC);
        drawScaledCentered(g, font, initials(contact.name()), avatarX + avatar / 2, y + 15, 0xFFFFFFFF, 0.9F);
        drawScaledCentered(g, font, trim(font, contact.name(), Math.round(w * 0.58F)),
                x + w / 2, y + 50, 0xFF111318, 0.9F);
        drawScaledCentered(g, font, contact.online() ? "online" : "offline",
                x + w / 2, y + 64, contact.online() ? 0xFF20C985 : 0xFF8E8E93, 0.66F);
        g.fill(x - 10, y + 80, x + w + 10, y + 81, 0xFFE7E7EA);
    }

    private static void drawMessengerControls(GuiGraphics g, Font font, int x, int y, int w,
                                              SmartphoneClientState.PhoneContact contact,
                                              List<SmartphoneClientState.Hitbox> hitboxes) {
        int gap = 5;
        int chipW = Math.max(42, (w - gap * 3) / 4);
        drawChatChip(g, font, x, y, chipW, 20, contact.favorite() ? "Unfav" : "Fav",
                0xFFFFB800, hitboxes, contact.favorite() ? "UNFAVORITE" : "FAVORITE");
        drawChatChip(g, font, x + chipW + gap, y, chipW, 20, contact.muted() ? "Unmute" : "Mute",
                0xFF8E8E93, hitboxes, contact.muted() ? "UNMUTE" : "MUTE");
        drawChatChip(g, font, x + (chipW + gap) * 2, y, chipW, 20, contact.blocked() ? "Unblock" : "Block",
                0xFFFF3B30, hitboxes, contact.blocked() ? "UNBLOCK" : "BLOCK");
        drawChatChip(g, font, x + (chipW + gap) * 3, y, w - (chipW + gap) * 3, 20, "Report",
                0xFFFF3B30, hitboxes, "REPORT");
    }

    private static void drawChatChip(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int color,
                                     List<SmartphoneClientState.Hitbox> hitboxes, String action) {
        fillRounded(g, x, y, w, h, h / 2, 0xFFF0F2F6);
        drawScaledCentered(g, font, trim(font, label, w - 8), x + w / 2, y + 5, color, 0.66F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                action, "", "", ""));
    }

    private static void drawMessageBubble(GuiGraphics g, Font font, int x, int y, int w, int maxBubbleW, int bubbleH,
                                          List<String> lines, String time, boolean outgoing) {
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, phoneTextWidth(line, 10.5F));
        }
        int bubbleW = Mth.clamp(textW + 24, 48, maxBubbleW);
        int bubbleX = outgoing ? x + w - bubbleW - 4 : x + 4;
        int bubbleY = y + 10;
        int color = outgoing ? 0xFF0078FF : 0xFFE9E9EB;
        int textColor = outgoing ? 0xFFFFFFFF : 0xFF111318;
        int tailX = outgoing ? bubbleX + bubbleW - 5 : bubbleX + 5;

        fillRounded(g, bubbleX, bubbleY, bubbleW, bubbleH, 14, color);
        int tailY = bubbleY + bubbleH - 9;
        if (outgoing) {
            g.fill(bubbleX + bubbleW - 8, tailY, bubbleX + bubbleW + 1, tailY + 8, color);
            fillCircle(g, bubbleX + bubbleW + 1, tailY + 7, 4, color);
        } else {
            g.fill(bubbleX - 1, tailY, bubbleX + 8, tailY + 8, color);
            fillCircle(g, bubbleX - 1, tailY + 7, 4, color);
        }

        int ty = bubbleY + 7;
        for (String line : lines) {
            drawScaledString(g, font, line, bubbleX + 12, ty, textColor, 0.88F);
            ty += 13;
        }
        String timeLabel = time == null || time.isBlank() ? "" : time;
        if (!timeLabel.isBlank()) {
            int timeColor = outgoing ? 0xFF8E8E93 : 0xFFB0B0B6;
            int timeW = phoneTextWidth(timeLabel, 6.8F);
            int timeX = outgoing ? bubbleX + bubbleW - timeW - 3 : bubbleX + 3;
            drawScaledString(g, font, timeLabel, timeX, bubbleY + bubbleH + 3, timeColor, 0.62F);
        }
    }

    private static int payRequestBubbleHeight(SmartphoneClientState.PhoneMessage message, boolean outgoing) {
        return payRequestPending(message) && !outgoing ? 118 : 92;
    }

    private static void drawPayRequestBubble(GuiGraphics g, Font font, int x, int y, int w, int maxBubbleW, int bubbleH,
                                             SmartphoneClientState.PhoneMessage message,
                                             boolean outgoing,
                                             List<SmartphoneClientState.Hitbox> hitboxes) {
        int bubbleW = Mth.clamp(maxBubbleW, 168, w - 8);
        int bubbleX = outgoing ? x + w - bubbleW - 4 : x + 4;
        int bubbleY = y + 10;
        boolean gift = message.gift();
        int color = outgoing ? 0xFFF4F6FA : 0xFFF1F1F5;
        int border = 0xFFDADAE2;
        drawRoundedBorder(g, bubbleX, bubbleY, bubbleW, bubbleH, 18, border, color);

        int iconColor = gift ? 0xFFFFB800 : 0xFF20C985;
        fillCircle(g, bubbleX + 22, bubbleY + 24, 15, iconColor);
        drawScaledCentered(g, font, gift ? "*" : "$", bubbleX + 22, bubbleY + 17, 0xFFFFFFFF, 0.86F);
        drawScaledString(g, font, gift ? "Gift" : "Pay Request", bubbleX + 45, bubbleY + 11,
                0xFF111318, 0.92F);
        drawScaledString(g, font, payRequestStatusLabel(message), bubbleX + 45, bubbleY + 28,
                payRequestStatusColor(message), 0.72F);

        String amount = payRequestAmountLabel(message.payRequestAmount());
        drawScaledString(g, font, trim(font, amount, bubbleW - 28), bubbleX + 14, bubbleY + 51,
                0xFF111318, 1.42F);
        String detail = gift
                ? (outgoing ? "Gifted to " + selectedContactName() : "Gift from " + message.senderName())
                : (outgoing ? "Requested from " + selectedContactName() : "Requested by " + message.senderName());
        drawScaledString(g, font, trim(font, detail, bubbleW - 28), bubbleX + 14, bubbleY + 75,
                0xFF7B7B86, 0.72F);

        if (payRequestPending(message) && !outgoing) {
            int buttonY = bubbleY + bubbleH - 29;
            int gap = 6;
            int buttonW = (bubbleW - 28 - gap) / 2;
            fillRounded(g, bubbleX + 14, buttonY, buttonW, 22, 11, 0xFF0078FF);
            drawScaledCentered(g, font, gift ? "Accept" : "Pay", bubbleX + 14 + buttonW / 2, buttonY + 7,
                    0xFFFFFFFF, 0.72F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(
                    bubbleX + 14, buttonY, buttonW, 22),
                    gift ? "MESSAGE_GIFT_ACCEPT" : "MESSAGE_PAY_REQUEST_ACCEPT",
                    message.payRequestId().toString(), "", ""));
            fillRounded(g, bubbleX + 14 + buttonW + gap, buttonY, buttonW, 22, 11, 0xFFE9E9EF);
            drawScaledCentered(g, font, "Decline", bubbleX + 14 + buttonW + gap + buttonW / 2, buttonY + 7,
                    0xFFFF3B30, 0.72F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(
                    bubbleX + 14 + buttonW + gap, buttonY, buttonW, 22),
                    gift ? "MESSAGE_GIFT_DECLINE" : "MESSAGE_PAY_REQUEST_DECLINE",
                    message.payRequestId().toString(), "", ""));
        }

        String timeLabel = message.time() == null || message.time().isBlank() ? "" : message.time();
        if (!timeLabel.isBlank()) {
            int timeW = phoneTextWidth(timeLabel, 6.8F);
            int timeX = outgoing ? bubbleX + bubbleW - timeW - 3 : bubbleX + 3;
            drawScaledString(g, font, timeLabel, timeX, bubbleY + bubbleH + 3, 0xFFB0B0B6, 0.62F);
        }
    }

    private static boolean payRequestPending(SmartphoneClientState.PhoneMessage message) {
        return message != null && "PENDING".equalsIgnoreCase(message.payRequestStatus());
    }

    private static String payRequestStatusLabel(SmartphoneClientState.PhoneMessage message) {
        String status = message == null || message.payRequestStatus() == null ? "" : message.payRequestStatus();
        boolean gift = message != null && message.gift();
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACCEPTED" -> gift ? "Accepted" : "Paid";
            case "DECLINED" -> "Declined";
            case "EXPIRED" -> "Expired";
            default -> "Pending";
        };
    }

    private static int payRequestStatusColor(SmartphoneClientState.PhoneMessage message) {
        String status = message == null || message.payRequestStatus() == null ? "" : message.payRequestStatus();
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACCEPTED" -> 0xFF20C985;
            case "DECLINED", "EXPIRED" -> 0xFFFF3B30;
            default -> 0xFFFF9F0A;
        };
    }

    private static String payRequestAmountLabel(String amount) {
        String safe = amount == null || amount.isBlank() ? "$0" : amount.trim();
        return safe.startsWith("$") ? safe : "$" + safe;
    }

    private static String selectedContactName() {
        SmartphoneClientState.PhoneContact contact = SmartphoneClientState.selectedContact();
        return contact == null ? "contact" : contact.name();
    }

    private static void drawMessageComposer(GuiGraphics g, Font font, int x, int y, int w, int h,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        g.fill(x - 10, y - 7, x + w + 10, y + h + 8, 0xFFF6F7FA);
        g.fill(x - 10, y - 8, x + w + 10, y - 7, 0xFFE1E1E6);

        int sendSize = 34;
        int attachSize = 34;
        int gap = 6;
        int fieldW = w - sendSize - attachSize - gap * 2;
        List<String> draftLines = wrapPhoneText(font, SmartphoneClientState.messageDraft(), fieldW - 24, 10.5F, CHAT_COMPOSER_WRAP_MAX_LINES);
        int visibleLines = SmartphoneClientState.messageDraft().isBlank()
                ? 1
                : Math.min(CHAT_COMPOSER_MAX_LINES, Math.max(1, draftLines.size()));
        SmartphoneClientState.syncMessageDraftScroll(draftLines.size(), visibleLines);
        int draftStart = Mth.clamp(SmartphoneClientState.messageDraftScrollOffset(), 0,
                Math.max(0, draftLines.size() - visibleLines));
        List<String> visibleDraftLines = draftLines.subList(draftStart, Math.min(draftLines.size(), draftStart + visibleLines));
        int fieldH = Math.max(34, 20 + visibleLines * 13);
        int fieldY = y + 6;
        boolean active = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.MESSAGE;
        int attachX = x + fieldW + gap;
        int sendX = attachX + attachSize + gap;
        int buttonY = fieldY + fieldH - sendSize;
        fillRounded(g, x, fieldY, fieldW, fieldH, fieldH / 2, 0xFFFFFFFF);
        drawRoundedBorder(g, x, fieldY, fieldW, fieldH, fieldH / 2, active ? 0xFF0078FF : 0xFFDADAE0, 0xFFFFFFFF);
        drawMultilineFieldText(g, font, x + 14, fieldY + 12, fieldW - 24, visibleDraftLines,
                SmartphoneClientState.messageDraft(), active, "Message",
                0xFF111318, 0xFF8E8E93, 0xFF111318,
                draftStart + visibleDraftLines.size() >= draftLines.size());
        drawMessageDraftScrollbar(g, x, fieldY, fieldW, fieldH, draftStart, visibleLines, draftLines.size());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, fieldY, fieldW, fieldH),
                "INPUT", SmartphoneClientState.InputTarget.MESSAGE.name(), "", ""));

        boolean trayOpen = SmartphoneClientState.messengerTrayOpen();
        fillCircle(g, attachX + attachSize / 2, buttonY + attachSize / 2, attachSize / 2,
                trayOpen ? 0xFFE9E9EF : 0xFFFFFFFF);
        drawRoundedBorder(g, attachX, buttonY, attachSize, attachSize, attachSize / 2,
                trayOpen ? 0xFFE9E9EF : 0xFFDADAE0, trayOpen ? 0xFFE9E9EF : 0xFFFFFFFF);
        if (trayOpen) {
            drawFigmaCloseGlyph(g, attachX + attachSize / 2, buttonY + attachSize / 2, 0xFF0078FF, 0.68F);
        } else {
            drawFigmaPlusGlyph(g, attachX + attachSize / 2, buttonY + attachSize / 2, 8, 0xFF0078FF);
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(attachX, buttonY, attachSize, attachSize),
                "MESSAGE_ATTACH_TOGGLE", "", "", ""));

        fillCircle(g, sendX + sendSize / 2, buttonY + sendSize / 2, sendSize / 2, 0xFF0078FF);
        drawScaledCentered(g, font, ">", sendX + sendSize / 2 + 1, buttonY + 10, 0xFFFFFFFF, 1.0F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(sendX, buttonY, sendSize, sendSize),
                "SEND_MESSAGE", "", "", ""));

        if (trayOpen) {
            int trayY = fieldY + fieldH + 10;
            int trayH = Math.max(88, y + h - trayY - 4);
            if (SmartphoneClientState.messengerPayRequestPanel() || SmartphoneClientState.messengerGiftPanel()) {
                drawMessengerPayRequestPanel(g, font, x, trayY, w, trayH, hitboxes);
            } else {
                drawMessengerAttachTray(g, font, x, trayY, w, trayH, hitboxes);
            }
        }
    }

    private static int messageComposerHeight(Font font, int w) {
        int sendSize = 34;
        int attachSize = 34;
        int gap = 6;
        int fieldW = w - sendSize - attachSize - gap * 2;
        List<String> draftLines = wrapPhoneText(font, SmartphoneClientState.messageDraft(), fieldW - 24, 10.5F, CHAT_COMPOSER_WRAP_MAX_LINES);
        int visibleLines = SmartphoneClientState.messageDraft().isBlank()
                ? 1
                : Math.min(CHAT_COMPOSER_MAX_LINES, Math.max(1, draftLines.size()));
        int fieldH = Math.max(34, 20 + visibleLines * 13);
        int tray = SmartphoneClientState.messengerTrayOpen() ? 132 : 0;
        return fieldH + 14 + tray;
    }

    private static void drawMessengerAttachTray(GuiGraphics g, Font font, int x, int y, int w, int h,
                                                List<SmartphoneClientState.Hitbox> hitboxes) {
        fillRounded(g, x, y, w, h, 20, 0xFFFFFFFF);
        drawRoundedBorder(g, x, y, w, h, 20, 0xFFE1E1E6, 0xFFFFFFFF);
        drawScaledString(g, font, "Apps", x + 16, y + 16, 0xFF111318, 1.02F);
        int tile = Math.min(62, Math.max(52, h - 54));
        int tileX = x + 16;
        int tileY = y + 42;
        drawMessengerAppTile(g, font, tileX, tileY, tile, 0xFFEAF8F1, 0xFF20C985,
                "$", "Pay", "Request");
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(tileX, tileY, tile, tile),
                "MESSAGE_PAY_REQUEST_TILE", "", "", ""));

        int giftX = tileX + tile + 14;
        drawMessengerAppTile(g, font, giftX, tileY, tile, 0xFFFFF4D8, 0xFFFFB800,
                "*", "Gift", "Money");
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(giftX, tileY, tile, tile),
                "MESSAGE_GIFT_TILE", "", "", ""));
    }

    private static void drawMessengerAppTile(GuiGraphics g, Font font, int x, int y, int size,
                                             int bg, int accent, String icon, String top, String bottom) {
        fillRounded(g, x, y, size, size, Math.max(14, size / 4), bg);
        fillCircle(g, x + size / 2, y + Math.round(size * 0.34F), Math.max(12, size / 4), accent);
        drawScaledCentered(g, font, icon, x + size / 2, y + Math.round(size * 0.25F), 0xFFFFFFFF, 0.86F);
        drawScaledCentered(g, font, top, x + size / 2, y + Math.round(size * 0.60F), 0xFF111318, 0.70F);
        drawScaledCentered(g, font, bottom, x + size / 2, y + Math.round(size * 0.75F), 0xFF111318, 0.70F);
    }

    private static void drawMessengerPayRequestPanel(GuiGraphics g, Font font, int x, int y, int w, int h,
                                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        fillRounded(g, x, y, w, h, 20, 0xFFFFFFFF);
        drawRoundedBorder(g, x, y, w, h, 20, 0xFFE1E1E6, 0xFFFFFFFF);
        boolean gift = SmartphoneClientState.messengerGiftPanel();
        drawScaledString(g, font, gift ? "Gift Money" : "Pay Request", x + 14, y + 12, 0xFF111318, 0.98F);
        drawScaledString(g, font, (gift ? "Gift to " : "Request from ") + selectedContactName(),
                x + 14, y + 29, 0xFF8E8E93, 0.72F);

        int rowY = y + 48;
        int accountW = Math.max(116, Math.round(w * 0.56F));
        fillRounded(g, x + 12, rowY, accountW, 28, 14, 0xFFF2F2F7);
        drawScaledString(g, font, trim(font, SmartphoneClientState.messengerRequestAccountLabel(), accountW - 60),
                x + 24, rowY + 9, 0xFF111318, 0.72F);
        drawScaledString(g, font, "Choose", x + accountW - 34, rowY + 9, 0xFF0078FF, 0.64F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + 12, rowY, accountW, 28),
                "MESSAGE_REQUEST_ACCOUNT_PICKER", "", "", ""));

        int amountX = x + accountW + 20;
        int amountW = Math.max(72, w - accountW - 32);
        boolean amountActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.MESSENGER_REQUEST_AMOUNT;
        fillRounded(g, amountX, rowY, amountW, 28, 14, 0xFFF2F2F7);
        drawRoundedBorder(g, amountX, rowY, amountW, 28, 14,
                amountActive ? 0xFF0078FF : 0xFFF2F2F7, 0xFFF2F2F7);
        drawFieldText(g, font, amountX + 10, rowY + 9, amountW - 20,
                SmartphoneClientState.messengerRequestAmount(), amountActive, "$0",
                0xFF111318, 0xFF8E8E93, 0xFF111318);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(amountX, rowY, amountW, 28),
                "INPUT", SmartphoneClientState.InputTarget.MESSENGER_REQUEST_AMOUNT.name(), "", ""));

        int buttonY = y + h - 38;
        mobileButton(g, font, x + 12, buttonY, w - 24, 30, gift ? "Gift" : "Request", 0xFF0078FF,
                hitboxes, "MESSAGE_REQUEST_SEND", "", "", "");

        if (SmartphoneClientState.messengerAccountPickerOpen()) {
            drawMessengerAccountPicker(g, font, x + 10, y + 38, w - 20, Math.max(84, h - 48), hitboxes);
        }
    }

    private static void drawMessengerAccountPicker(GuiGraphics g, Font font, int x, int y, int w, int h,
                                                   List<SmartphoneClientState.Hitbox> hitboxes) {
        fillRounded(g, x, y, w, h, 18, 0xFFFFFFFF);
        drawRoundedBorder(g, x, y, w, h, 18, 0xFFDADAE0, 0xFFFFFFFF);
        drawScaledString(g, font, "Choose account", x + 12, y + 10, 0xFF111318, 0.84F);
        int rowY = y + 32;
        int rowH = 30;
        int visibleRows = Math.max(1, (h - 38) / rowH);
        List<SmartphoneClientState.PhoneAccount> accounts = SmartphoneClientState.accounts();
        SmartphoneClientState.syncMessengerAccountPickerScroll(accounts.size(), visibleRows);
        int start = Mth.clamp(SmartphoneClientState.messengerAccountPickerOffset(), 0,
                Math.max(0, accounts.size() - visibleRows));
        int shown = 0;
        for (int i = start; i < accounts.size() && shown < visibleRows; i++, shown++) {
            SmartphoneClientState.PhoneAccount account = accounts.get(i);
            int py = rowY + shown * rowH;
            fillRounded(g, x + 8, py, w - 16, 25, 12, account.primary() ? 0xFFEAF3FF : 0xFFF2F2F7);
            drawScaledString(g, font, trim(font, account.bankName() + " " + account.accountType(), w - 84),
                    x + 18, py + 6, 0xFF111318, 0.68F);
            drawScaledString(g, font, account.shortBalance(), x + w - 70, py + 6, 0xFF0078FF, 0.66F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + 8, py, w - 16, 25),
                    "MESSAGE_REQUEST_ACCOUNT_SELECT", account.id().toString(), "", ""));
        }
        if (accounts.isEmpty()) {
            drawScaledCentered(g, font, "No accounts available", x + w / 2, y + h / 2 - 4,
                    0xFF8E8E93, 0.76F);
        }
    }

    private static void drawMessageDraftScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                                  int startLine, int visibleLines, int totalLines) {
        if (totalLines <= visibleLines) {
            return;
        }
        int trackX = x + w - 7;
        int trackY = y + 8;
        int trackH = Math.max(12, h - 16);
        g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0xFFE2E2E8);
        int thumbH = Math.max(8, Math.round(trackH * (visibleLines / (float) totalLines)));
        int maxStart = Math.max(1, totalLines - visibleLines);
        int thumbY = trackY + Math.round((trackH - thumbH) * (startLine / (float) maxStart));
        g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xFFB6B6C2);
    }

    private static void drawMessageThreadScrollbar(GuiGraphics g, int x, int y, int w, int viewportH,
                                                   int offsetFromBottom, int contentH) {
        if (contentH <= viewportH) {
            return;
        }
        int trackX = x + w + 5;
        int trackY = y + 2;
        int trackH = Math.max(18, viewportH - 4);
        g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0xFFE5E5EA);
        int thumbH = Math.max(14, Math.round(trackH * (viewportH / (float) contentH)));
        int maxOffset = Math.max(1, contentH - viewportH);
        float normalizedFromTop = 1.0F - Mth.clamp(offsetFromBottom / (float) maxOffset, 0.0F, 1.0F);
        int thumbY = trackY + Math.round((trackH - thumbH) * normalizedFromTop);
        g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xFFB6B6C2);
    }

    private static List<String> wrapPhoneText(Font font, String text, int width, float size, int maxLines) {
        List<String> lines = new ArrayList<>();
        String safe = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        if (safe.isBlank()) {
            return List.of("");
        }
        String[] paragraphs = safe.split("\n", -1);
        for (String paragraph : paragraphs) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                lines.add("");
                if (lines.size() >= maxLines) {
                    return lines;
                }
                continue;
            }
            String[] words = normalized.split("\\s+");
            String line = "";
            for (String word : words) {
                String remaining = word;
                while (!remaining.isBlank()) {
                    String candidate = line.isBlank() ? remaining : line + " " + remaining;
                    if (phoneTextWidth(candidate, size) <= width) {
                        line = candidate;
                        remaining = "";
                    } else {
                        if (!line.isBlank()) {
                            lines.add(line);
                            line = "";
                            if (lines.size() >= maxLines) {
                                return lines;
                            }
                            continue;
                        }
                        int split = fitTextPrefix(remaining, width, size);
                        if (split <= 0 || split >= remaining.length()) {
                            line = remaining;
                            remaining = "";
                        } else {
                            lines.add(remaining.substring(0, split));
                            remaining = remaining.substring(split);
                            if (lines.size() >= maxLines) {
                                return lines;
                            }
                        }
                    }
                }
            }
            if (!line.isBlank() && lines.size() < maxLines) {
                lines.add(line);
            }
            if (lines.size() >= maxLines) {
                return lines;
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static int fitTextPrefix(String text, int width, float size) {
        String safe = text == null ? "" : text;
        int best = 0;
        for (int i = 1; i <= safe.length(); i++) {
            if (phoneTextWidth(safe.substring(0, i), size) > width) {
                break;
            }
            best = i;
        }
        return Math.max(1, best);
    }

    private static List<String> wrapNoteText(String text, int width, float size, int maxLines) {
        List<String> lines = new ArrayList<>();
        String safe = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        if (safe.isEmpty()) {
            return List.of("");
        }
        String[] rawLines = safe.split("\n", -1);
        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                lines.add("");
                if (lines.size() >= maxLines) {
                    return lines;
                }
                continue;
            }
            String remaining = rawLine;
            while (!remaining.isEmpty()) {
                int split = Math.max(1, Math.min(fitTextPrefix(remaining, width, size), remaining.length()));
                if (split < remaining.length()) {
                    int whitespaceSplit = lastWhitespaceBefore(remaining, split);
                    if (whitespaceSplit > 0) {
                        split = whitespaceSplit + 1;
                    }
                }
                lines.add(remaining.substring(0, split));
                remaining = remaining.substring(split);
                if (lines.size() >= maxLines) {
                    return lines;
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static int lastWhitespaceBefore(String value, int endExclusive) {
        int limit = Math.min(value == null ? 0 : value.length(), Math.max(0, endExclusive));
        for (int i = limit - 1; i >= 0; i--) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void drawNotes(GuiGraphics g, Font font, int x, int y, int w, int h,
                                  List<SmartphoneClientState.Hitbox> hitboxes) {
        drawPhoneAppSurface(g, x, y, w, h, 0xFFF7F4EA);
        drawStatusBar(g, font, x, y - 31, w, 0xFF161616, 0xFFF7F4EA);
        List<String> notes = SmartphoneClientState.notes();
        int savedCount = (int) notes.stream().filter(note -> note != null && !note.isBlank()).count();
        if (!SmartphoneClientState.noteEditorOpen()) {
            drawNotesList(g, font, x, y, w, h, savedCount, hitboxes);
            return;
        }
        drawNotesEditor(g, font, x, y, w, h, notes, savedCount, hitboxes);
    }

    private static void drawNotesList(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      int savedCount, List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean compactNotes = h < 310;
        drawScaledString(g, font, "Notes", x, y, 0xFF1C1C1E, compactNotes ? 1.34F : 1.62F);
        List<SmartphoneClientState.PhoneNote> visibleNotes = SmartphoneClientState.visibleNotes();
        String countLabel = SmartphoneClientState.noteSearch().isBlank()
                ? savedCount + " saved"
                : visibleNotes.size() + " of " + savedCount;
        drawScaledString(g, font, countLabel, x, y + (compactNotes ? 21 : 26),
                0xFF8E8E93, compactNotes ? 0.68F : 0.78F);
        boolean canCreate = savedCount < SmartphoneClientState.maxPhoneNotes();

        int searchY = y + (compactNotes ? 38 : 50);
        int searchH = compactNotes ? 30 : 34;
        boolean searchActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.NOTE_SEARCH;
        boolean searchHasText = !SmartphoneClientState.noteSearch().isBlank();
        boolean showCancel = searchActive || searchHasText;
        int cancelW = showCancel ? Math.min(50, Math.max(42, w / 6)) : 0;
        int searchW = showCancel ? Math.max(90, w - cancelW - 8) : w;
        fillRounded(g, x, searchY, searchW, searchH, 17, 0xFFFFFFFF);
        drawRoundedBorder(g, x, searchY, searchW, searchH, 17,
                searchActive ? 0xFFFFB13D : 0xFFE5E1D6, 0xFFFFFFFF);
        int iconX = x + 17;
        int iconY = searchY + searchH / 2;
        fillCircle(g, iconX, iconY - 1, 5, 0xFFB0A99A);
        fillCircle(g, iconX, iconY - 1, 3, 0xFFFFFFFF);
        g.fill(iconX + 4, iconY + 3, iconX + 10, iconY + 5, 0xFFB0A99A);
        drawFieldText(g, font, x + 34, searchY + 12, searchW - 72,
                SmartphoneClientState.noteSearch(), searchActive, "Search notes",
                0xFF1C1C1E, 0xFFB0A99A, 0xFF1C1C1E);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, searchY, searchW, searchH),
                "INPUT", SmartphoneClientState.InputTarget.NOTE_SEARCH.name(), "", ""));
        if (searchHasText) {
            drawFigmaCloseGlyph(g, x + searchW - 20, iconY, 0xFFB0A99A, 0.62F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + searchW - 38, searchY, 38, searchH),
                    "NOTE_SEARCH_CLEAR", "", "", ""));
        }
        if (showCancel) {
            drawScaledCentered(g, font, "Cancel", x + searchW + 4 + cancelW / 2, searchY + 12,
                    0xFFFFA800, 0.76F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + searchW + 2, searchY, cancelW + 6, searchH),
                    "NOTE_SEARCH_CANCEL", "", "", ""));
        }

        int listY = searchY + searchH + (compactNotes ? 8 : 12);
        int toolbarH = Mth.clamp(Math.round(h * (compactNotes ? 0.085F : 0.10F)),
                compactNotes ? 28 : 34, compactNotes ? 36 : 44);
        int toolbarY = y + h - toolbarH;
        int viewportH = Math.max(0, toolbarY - listY - 8);
        int rowH = compactNotes ? Mth.clamp(Math.round(h * 0.15F), 34, 46) : Mth.clamp(viewportH, 28, 58);
        if (viewportH > 0 && viewportH < rowH) {
            rowH = viewportH;
        }
        int visibleRows = viewportH >= 24 && rowH > 0 ? Math.max(1, viewportH / rowH) : 0;
        SmartphoneClientState.syncNoteListScroll(visibleNotes.size(), Math.max(1, visibleRows));
        int offset = SmartphoneClientState.noteListScrollOffset();
        int drawn = 0;
        if (viewportH > 0) {
            fillRounded(g, x, listY, w, Math.min(viewportH, Math.max(rowH, visibleNotes.size() * rowH)), 18, 0xFFFFFFFF);
        }
        for (int i = offset; i < visibleNotes.size() && drawn < visibleRows; i++) {
            SmartphoneClientState.PhoneNote note = visibleNotes.get(i);
            int rowY = listY + drawn * rowH;
            drawNoteListRow(g, font, x, rowY, w, rowH, note.index(), note.body(),
                    drawn < visibleRows - 1 && i < visibleNotes.size() - 1, hitboxes);
            drawn++;
        }
        if (savedCount == 0 && viewportH >= 28) {
            drawNoteEmptyState(g, font, x, listY, w, viewportH, "No Notes", "Notebook is empty.");
        } else if (visibleNotes.isEmpty() && viewportH >= 28) {
            drawNoteEmptyState(g, font, x, listY, w, viewportH, "No Results", "No matching notes.");
        }
        if (visibleRows > 0) {
            drawMessageDraftScrollbar(g, x, listY, w, viewportH, offset, visibleRows, visibleNotes.size());
        }
        drawNotesToolbar(g, font, x, toolbarY, w, toolbarH, savedCount, canCreate, hitboxes);
    }

    private static void drawNotesToolbar(GuiGraphics g, Font font, int x, int y, int w, int h, int savedCount,
                                         boolean canCreate, List<SmartphoneClientState.Hitbox> hitboxes) {
        g.fill(x, y, x + w, y + 1, 0xFFE5E1D6);
        int composeSize = Mth.clamp(Math.min(h - 8, w / 5), 16, 34);
        int composeX = x + w - composeSize - 4;
        int composeY = y + (h - composeSize) / 2;
        String countText = savedCount + "/" + SmartphoneClientState.maxPhoneNotes();
        int countMaxW = Math.max(0, composeX - x - 8);
        if (countMaxW >= 28) {
            drawScaledCentered(g, font, trim(font, countText, countMaxW),
                    x + countMaxW / 2, y + Math.max(7, h / 2 - 4), 0xFF8E8E93,
                    h < 30 ? 0.58F : 0.70F);
        }
        drawNoteComposeGlyph(g, composeX + composeSize / 2, composeY + composeSize / 2, composeSize,
                canCreate ? 0xFFFFA800 : 0xFFB0A99A);
        if (canCreate) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(composeX, composeY, composeSize, composeSize),
                    "NOTE_NEW", "", "", ""));
        }
    }

    private static void drawNoteEmptyState(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           String title, String subtitle) {
        int centerX = x + w / 2;
        int centerY = y + Math.max(22, h / 2);
        if (h >= 70) {
            int iconY = centerY - 24;
            fillRounded(g, centerX - 13, iconY - 14, 26, 28, 4, 0xFFFFF3C7);
            g.fill(centerX - 13, iconY - 9, centerX + 13, iconY - 7, 0xFFFFC84D);
            g.fill(centerX - 8, iconY - 1, centerX + 8, iconY, 0xFFD8B24C);
            g.fill(centerX - 8, iconY + 6, centerX + 5, iconY + 7, 0xFFD8B24C);
        }
        int titleY = h >= 70 ? centerY + 2 : centerY - 8;
        drawScaledCentered(g, font, title, centerX, titleY, 0xFF1C1C1E, 0.98F);
        if (h >= 58) {
            drawScaledCentered(g, font, subtitle, centerX, titleY + 17, 0xFF8E8E93, 0.68F);
        }
    }

    private static void drawNoteListRow(GuiGraphics g, Font font, int x, int y, int w, int h, int index,
                                        String note, boolean divider,
                                        List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean compact = h < 44;
        String query = SmartphoneClientState.noteSearch();
        String matchedSnippet = query == null || query.isBlank() ? "" : noteSearchSnippet(note, query);
        String title = firstNoteLine(note);
        if (compact && !matchedSnippet.isBlank() && !title.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))) {
            title = matchedSnippet;
        }
        drawScaledString(g, font, trim(font, title, w - 38), x + 14,
                y + (compact ? Math.max(6, h / 2 - 5) : 10), 0xFF1C1C1E, compact ? 0.84F : 0.96F);
        String snippet = noteSnippet(note);
        if (!matchedSnippet.isBlank()) {
            snippet = matchedSnippet;
        }
        if (!compact && !snippet.isBlank()) {
            drawScaledString(g, font, trim(font, snippet, w - 38), x + 14, y + 30, 0xFF8E8E93, 0.74F);
        }
        if (h >= 28) {
            drawFigmaChevronRight(g, x + w - 18, y + h / 2, 5, 0xFFC7C7CC);
        }
        if (divider) {
            g.fill(x + 14, y + h - 1, x + w, y + h, 0xFFE5E1D6);
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                "NOTE_SELECT", String.valueOf(index), "", ""));
    }

    private static void drawNotesEditor(GuiGraphics g, Font font, int x, int y, int w, int h, List<String> notes,
                                        int savedCount, List<SmartphoneClientState.Hitbox> hitboxes) {
        int selected = SmartphoneClientState.noteIndex();
        int backSize = 30;
        drawFigmaChevronLeft(g, x + 7, y + 16, 6, 0xFFFFA800);
        drawScaledString(g, font, "Notes", x + 16, y + 10, 0xFFFFA800, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, Math.min(62, w / 3), backSize),
                "NOTE_LIST", "", "", ""));
        drawScaledCentered(g, font, "Note", x + w / 2, y + 8, 0xFF1C1C1E, 1.02F);
        int doneW = 48;
        drawScaledString(g, font, "Done", x + w - doneW + 6, y + 10, 0xFFFFA800, 0.84F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - doneW, y, doneW, backSize),
                "SAVE_NOTE", "", "", ""));

        boolean compactEditor = h < 260;
        int editorY = y + (compactEditor ? 38 : 46);
        int toolbarH = SmartphoneClientState.selectedNoteHasContent()
                ? Mth.clamp(Math.round(h * (compactEditor ? 0.085F : 0.10F)),
                compactEditor ? 28 : 34, compactEditor ? 36 : 44)
                : 0;
        int toolbarY = y + h - toolbarH;
        int editorBottom = toolbarH > 0 ? toolbarY - 4 : y + h - 4;
        int editorH = Math.max(0, editorBottom - editorY);
        if (toolbarH > 0 && editorH < 28) {
            toolbarH = 0;
            toolbarY = y + h;
            editorBottom = y + h - 4;
            editorH = Math.max(0, editorBottom - editorY);
        }
        String draft = SmartphoneClientState.noteDraft();
        boolean active = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.NOTE;
        int fieldY = editorY + 4;
        int fieldH = Math.max(0, editorY + editorH - fieldY - 4);
        if (editorH > 0 && fieldH > 0) {
            int visibleLines = Math.max(1, fieldH / 13);
            int textInset = 4;
            int textW = Math.max(24, w - textInset * 2);
            List<String> allLines = wrapNoteText(draft, textW, 9.0F, 1024);
            SmartphoneClientState.syncNoteDraftScroll(allLines.size(), visibleLines);
            int noteStart = Mth.clamp(SmartphoneClientState.noteDraftScrollOffset(), 0,
                    Math.max(0, allLines.size() - visibleLines));
            List<String> visibleNoteLines = allLines.subList(noteStart, Math.min(allLines.size(), noteStart + visibleLines));
            drawMultilineFieldText(g, font, x + textInset, fieldY, textW, visibleNoteLines, draft, active,
                    "Write a note...", 0xFF1C1C1E, 0xFFB0A99A, 0xFF1C1C1E, true);
            drawMessageDraftScrollbar(g, x, fieldY - 4, w, fieldH + 6, noteStart, visibleLines, allLines.size());
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, editorY, w, editorH),
                    "INPUT", SmartphoneClientState.InputTarget.NOTE.name(), "", ""));
        }
        if (toolbarH > 0) {
            drawNotesEditorToolbar(g, font, x, toolbarY, w, toolbarH, hitboxes);
        }
    }

    private static void drawNotesEditorToolbar(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               List<SmartphoneClientState.Hitbox> hitboxes) {
        g.fill(x, y, x + w, y + 1, 0xFFE5E1D6);
        int deleteSize = Mth.clamp(Math.min(h - 8, w / 5), 16, 34);
        int deleteX = x + w - deleteSize - 4;
        int deleteY = y + (h - deleteSize) / 2;
        fillCircle(g, deleteX + deleteSize / 2, deleteY + deleteSize / 2, deleteSize / 2, 0xFFFFE2E0);
        drawNoteDeleteGlyph(g, deleteX + deleteSize / 2, deleteY + deleteSize / 2, deleteSize, 0xFFFF3B30);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(deleteX, deleteY, deleteSize, deleteSize),
                "DELETE_NOTE", "", "", ""));
    }

    private static String noteSnippet(String value) {
        String safe = value == null ? "" : value.trim().replace('\n', ' ');
        String title = firstNoteLine(value);
        if (safe.startsWith(title)) {
            safe = safe.substring(Math.min(safe.length(), title.length())).trim();
        }
        return safe;
    }

    private static String noteSearchSnippet(String value, String query) {
        String safe = value == null ? "" : value.trim().replace('\n', ' ');
        String needle = query == null ? "" : query.trim();
        if (safe.isBlank() || needle.isBlank()) {
            return "";
        }
        String lower = safe.toLowerCase(Locale.ROOT);
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        int match = lower.indexOf(lowerNeedle);
        if (match < 0) {
            return "";
        }
        int start = Math.max(0, match - 18);
        int end = Math.min(safe.length(), match + lowerNeedle.length() + 28);
        while (start > 0 && start < safe.length() && safe.charAt(start) != ' ') {
            start--;
        }
        while (end < safe.length() && end > 0 && safe.charAt(end - 1) != ' ') {
            end++;
        }
        String snippet = safe.substring(Mth.clamp(start, 0, safe.length()), Mth.clamp(end, 0, safe.length())).trim();
        if (snippet.isBlank()) {
            return "";
        }
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < safe.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private static void drawNoteDeleteGlyph(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = Mth.clamp(size / 28.0F, 0.50F, 1.0F);
        int lidW = Math.max(5, Math.round(11 * scale));
        int halfLid = lidW / 2;
        int topY = cy - Math.round(4 * scale);
        g.fill(cx - halfLid, topY, cx + halfLid + 1, topY + Math.max(1, Math.round(2 * scale)), color);
        g.fill(cx - Math.round(2 * scale), cy - Math.round(7 * scale),
                cx + Math.round(3 * scale), cy - Math.round(5 * scale), color);
        drawSoftLine(g, cx - Math.round(4 * scale), cy - Math.round(1 * scale),
                cx - Math.round(3 * scale), cy + Math.round(8 * scale), color);
        drawSoftLine(g, cx + Math.round(4 * scale), cy - Math.round(1 * scale),
                cx + Math.round(3 * scale), cy + Math.round(8 * scale), color);
        g.fill(cx - Math.round(3 * scale), cy + Math.round(8 * scale),
                cx + Math.round(4 * scale), cy + Math.round(10 * scale), color);
        g.fill(cx - Math.max(1, Math.round(1 * scale)), cy + Math.round(1 * scale),
                cx, cy + Math.round(7 * scale), color);
        g.fill(cx + Math.round(2 * scale), cy + Math.round(1 * scale),
                cx + Math.round(3 * scale), cy + Math.round(7 * scale), color);
    }

    private static void drawNoteComposeGlyph(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = Mth.clamp(size / 28.0F, 0.50F, 1.0F);
        int cardW = Math.max(8, Math.round(16 * scale));
        int cardH = Math.max(8, Math.round(16 * scale));
        drawRoundedBorder(g, cx - cardW / 2, cy - Math.round(7 * scale), cardW, cardH,
                Math.max(2, Math.round(3 * scale)), color, 0xFFF7F4EA);
        drawSoftLine(g, cx - Math.round(2 * scale), cy + Math.round(4 * scale),
                cx + Math.round(8 * scale), cy - Math.round(6 * scale), color);
        drawSoftLine(g, cx + Math.round(6 * scale), cy - Math.round(8 * scale),
                cx + Math.round(10 * scale), cy - Math.round(4 * scale), color);
        drawSoftLine(g, cx - Math.round(3 * scale), cy + Math.round(5 * scale),
                cx - Math.round(5 * scale), cy + Math.round(8 * scale), color);
        g.fill(cx - Math.round(6 * scale), cy + Math.round(8 * scale),
                cx - Math.round(3 * scale), cy + Math.round(10 * scale), color);
    }

    private static void drawSettings(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        drawPhoneAppSurface(g, x, y, w, h, 0xFFF2F2F7);
        drawStatusBar(g, font, x, y - 31, w, 0xFF111318, 0xFFF2F2F7);
        if (SmartphoneClientState.settingsAppearanceOpen()) {
            drawSettingsAppearance(g, font, x, y, w, h, hitboxes);
            return;
        }
        if (SmartphoneClientState.settingsInstalledAppsOpen()) {
            drawSettingsInstalledApps(g, font, x, y, w, h, hitboxes);
            return;
        }
        if (SmartphoneClientState.settingsPasscodeOpen()) {
            drawSettingsPasscode(g, font, x, y, w, h, hitboxes);
            return;
        }
        drawSettingsMain(g, font, x, y, w, h, hitboxes);
    }

    private static void drawSettingsMain(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        int bottom = y + h - 8;
        boolean compactSettings = h < 320;
        boolean tightSettings = h < 240;
        drawScaledString(g, font, "Settings", x, y, 0xFF111318, tightSettings ? 1.34F : 1.56F);

        int searchY = y + (tightSettings ? 30 : 34);
        int searchH = tightSettings ? 30 : 34;
        drawSettingsSearch(g, font, x, searchY, w, searchH, hitboxes);

        boolean searching = !SmartphoneClientState.settingsSearch().isBlank();
        List<SettingsRowData> rows = visibleSettingsRows();
        int groupY;
        if (searching) {
            groupY = searchY + searchH + Mth.clamp(Math.round(h * 0.055F), tightSettings ? 16 : 20, 28);
            drawScaledString(g, font, "Results", x + 2, groupY - 18, 0xFF8E8E93, 0.74F);
        } else {
            int profileY = searchY + searchH + (compactSettings ? 8 : Mth.clamp(Math.round(h * 0.035F), 12, 18));
            int profileH = compactSettings ? Mth.clamp(Math.round(h * 0.13F), 38, 46)
                    : Mth.clamp(Math.round(h * 0.13F), 52, 66);
            int candidateGroupY = profileY + profileH
                    + (compactSettings ? (tightSettings ? 16 : 20) : Mth.clamp(Math.round(h * 0.075F), 28, 38));
            long actionableRows = rows.stream().filter(row -> row.action() != null && !row.action().isBlank()).count();
            int requiredRows = Math.max(1, Math.min(rows.size(),
                    compactSettings ? Math.max(1, Math.min(2, (int) actionableRows)) : rows.size()));
            boolean showProfile = !tightSettings && (!compactSettings || bottom - candidateGroupY >= 28 * requiredRows);
            if (showProfile) {
                fillRounded(g, x, profileY, w, profileH, 17, 0xFFFFFFFF);
                int avatar = compactSettings ? 17 : 23;
                fillCircle(g, x + 34, profileY + profileH / 2, avatar, SmartphoneClientState.accentColor());
                drawScaledCentered(g, font, initials(SmartphoneClientState.ownerName()), x + 34,
                        profileY + profileH / 2 - (compactSettings ? 6 : 8), 0xFFFFFFFF, compactSettings ? 0.74F : 0.92F);
                drawScaledString(g, font, trim(font, SmartphoneClientState.ownerName(), w - 88), x + 68,
                        profileY + (compactSettings ? 9 : 15), 0xFF111318, compactSettings ? 0.88F : 1.0F);
                if (!compactSettings) {
                    drawScaledString(g, font, "Phone owner", x + 68, profileY + 34, 0xFF8E8E93, 0.78F);
                }
                groupY = candidateGroupY;
            } else {
                groupY = searchY + searchH + (tightSettings ? 18 : 24);
            }
            drawScaledString(g, font, "Phone", x + 2, groupY - 18, 0xFF8E8E93, 0.74F);
        }
        int infoH = 64;
        int availableGroupH = Math.max(0, bottom - groupY);
        int desiredRows = Math.max(1, rows.size());
        int rowWithInfo = (bottom - groupY - infoH - 24) / desiredRows;
        boolean showInfo = rowWithInfo >= 40;
        int minRowH = tightSettings ? 18 : 24;
        int rowH = Math.min(52, Math.max(minRowH,
                showInfo ? rowWithInfo : availableGroupH / desiredRows));
        if (availableGroupH < rowH * desiredRows && availableGroupH >= 16 * desiredRows) {
            rowH = Math.max(16, availableGroupH / desiredRows);
        } else if (availableGroupH < rowH && availableGroupH >= 16) {
            rowH = availableGroupH;
        }
        int visibleRows = availableGroupH >= rowH && rowH > 0
                ? Math.max(1, Math.min(rows.size(), availableGroupH / rowH))
                : 0;
        SmartphoneClientState.syncSettingsMainScroll(rows.size(), Math.max(1, visibleRows));
        int offset = SmartphoneClientState.settingsMainScrollOffset();
        if (availableGroupH >= rowH && !rows.isEmpty()) {
            int groupRows = Math.min(rows.size() - offset, visibleRows);
            fillRounded(g, x, groupY, w, rowH * groupRows, 16, 0xFFFFFFFF);
            for (int i = 0; i < groupRows; i++) {
                int rowIndex = offset + i;
                SettingsRowData row = rows.get(rowIndex);
                drawSettingsRow(g, font, x, groupY + rowH * i, w, rowH, row.title(), row.subtitle(),
                        row.iconColor(), i < groupRows - 1, row.action(), "", "", hitboxes);
            }
            if (rows.size() > visibleRows) {
                drawMessageDraftScrollbar(g, x, groupY, w, availableGroupH, offset, visibleRows, rows.size());
            }
        } else if (availableGroupH >= 38 && rows.isEmpty()) {
            drawSettingsEmptyState(g, font, x, groupY, w, availableGroupH, "No Results", "No matching settings.");
        }

        if (showInfo && !searching && offset == 0 && availableGroupH >= rowH * rows.size() + infoH + 18) {
            int infoY = groupY + rowH * rows.size() + 18;
            fillRounded(g, x, infoY, w, infoH, 16, 0xFFFFFFFF);
            drawScaledString(g, font, SmartphoneClientState.phoneAccessLabel(), x + 14, infoY + 12,
                    0xFF111318, 0.96F);
            drawScaledString(g, font, trim(font, SmartphoneClientState.phoneAccessDescription(), w - 28),
                    x + 14, infoY + 32, 0xFF8E8E93, 0.72F);
        }
    }

    private record SettingsRowData(String title, String subtitle, int iconColor, String action) {
    }

    private static List<SettingsRowData> visibleSettingsRows() {
        List<SettingsRowData> rows = List.of(
                new SettingsRowData("Appearance", settingThemeLabel(), SmartphoneClientState.accentColor(),
                        "SETTINGS_APPEARANCE"),
                new SettingsRowData("Installed apps", SmartphoneClientState.apps().size() + " apps", 0xFF0078FF,
                        "SETTINGS_APPS"),
                new SettingsRowData("Change Passcode",
                        SmartphoneClientState.phonePasscodeSet() ? "4 digit passcode" : "Not set",
                        0xFFFF9F0A,
                        "SETTINGS_PASSCODE")
        );
        String query = SmartphoneClientState.settingsSearch().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> row.title().toLowerCase(Locale.ROOT).contains(query)
                        || row.subtitle().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private static void drawSettingsSearch(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean active = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.SETTINGS_SEARCH;
        boolean searchHasText = !SmartphoneClientState.settingsSearch().isBlank();
        boolean showCancel = active || searchHasText;
        int cancelW = showCancel ? Math.min(50, Math.max(42, w / 6)) : 0;
        int searchW = showCancel ? Math.max(90, w - cancelW - 8) : w;
        fillRounded(g, x, y, searchW, h, h / 2, 0xFFFFFFFF);
        drawRoundedBorder(g, x, y, searchW, h, h / 2, active ? 0xFF0078FF : 0xFFE1E1E6, 0xFFFFFFFF);
        int iconX = x + 17;
        int iconY = y + h / 2;
        fillCircle(g, iconX, iconY - 1, 5, 0xFF8E8E93);
        fillCircle(g, iconX, iconY - 1, 3, 0xFFFFFFFF);
        g.fill(iconX + 4, iconY + 3, iconX + 10, iconY + 5, 0xFF8E8E93);
        drawFieldText(g, font, x + 34, y + 12, searchW - 72,
                SmartphoneClientState.settingsSearch(), active, "Search",
                0xFF111318, 0xFF8E8E93, 0xFF111318);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, searchW, h),
                "INPUT", SmartphoneClientState.InputTarget.SETTINGS_SEARCH.name(), "", ""));
        if (searchHasText) {
            drawFigmaCloseGlyph(g, x + searchW - 20, iconY, 0xFF8E8E93, 0.62F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + searchW - 38, y, 38, h),
                    "SETTINGS_SEARCH_CLEAR", "", "", ""));
        }
        if (showCancel) {
            drawScaledCentered(g, font, "Cancel", x + searchW + 4 + cancelW / 2, y + 12,
                    0xFF0078FF, 0.76F);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + searchW + 2, y, cancelW + 6, h),
                    "SETTINGS_SEARCH_CANCEL", "", "", ""));
        }
    }

    private static void drawSettingsPasscode(GuiGraphics g, Font font, int x, int y, int w, int h,
                                             List<SmartphoneClientState.Hitbox> hitboxes) {
        int backSize = 30;
        drawFigmaChevronLeft(g, x + 7, y + 16, 6, 0xFF0078FF);
        drawScaledString(g, font, "Settings", x + 16, y + 10, 0xFF0078FF, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, Math.min(82, w / 3), backSize),
                "SETTINGS_MAIN", "", "", ""));
        drawScaledCentered(g, font, "Change Passcode", x + w / 2, y + 8, 0xFF111318, 1.02F);

        boolean compact = h < 310;
        int infoY = y + (compact ? 40 : 52);
        int infoH = compact ? 46 : 58;
        fillRounded(g, x, infoY, w, infoH, 16, 0xFFFFFFFF);
        int lockSize = compact ? 22 : 28;
        fillRounded(g, x + 14, infoY + (infoH - lockSize) / 2, lockSize, lockSize, 7, 0xFFFF9F0A);
        drawSettingsRowIcon(g, "Change Passcode", x + 14, infoY + (infoH - lockSize) / 2, lockSize, 0xFFFFFFFF);
        drawScaledString(g, font, "Phone Passcode", x + 52, infoY + (compact ? 9 : 13), 0xFF111318,
                compact ? 0.86F : 0.98F);
        if (!compact) {
            drawScaledString(g, font, trim(font, "Change the passcode used on the lock screen.", w - 66),
                    x + 52, infoY + 33, 0xFF8E8E93, 0.72F);
        }

        int formY = infoY + infoH + (compact ? 18 : 26);
        int available = Math.max(0, y + h - formY - 8);
        int buttonH = Mth.clamp(Math.round(h * 0.085F), 30, 42);
        int buttonY = y + h - buttonH - 2;
        int rowH = Mth.clamp((buttonY - formY - 14) / 3, compact ? 34 : 42, compact ? 42 : 52);
        int groupH = Math.max(rowH, rowH * 3);
        if (available >= rowH * 3 + buttonH + 12) {
            fillRounded(g, x, formY, w, groupH, 16, 0xFFFFFFFF);
            drawSettingsPasscodeField(g, font, x, formY, w, rowH, "Current Passcode",
                    SmartphoneClientState.settingsPasscodeCurrent(),
                    SmartphoneClientState.InputTarget.PHONE_PASSCODE_CURRENT, true, hitboxes);
            drawSettingsPasscodeField(g, font, x, formY + rowH, w, rowH, "New Passcode",
                    SmartphoneClientState.settingsPasscodeNew(),
                    SmartphoneClientState.InputTarget.PHONE_PASSCODE_NEW, true, hitboxes);
            drawSettingsPasscodeField(g, font, x, formY + rowH * 2, w, rowH, "Verify Passcode",
                    SmartphoneClientState.settingsPasscodeConfirm(),
                    SmartphoneClientState.InputTarget.PHONE_PASSCODE_CONFIRM, false, hitboxes);
            mobileButton(g, font, x, buttonY, w, buttonH, "Change Passcode", 0xFF0078FF,
                    hitboxes, "SAVE_PHONE_PASSCODE", "", "", "");
        } else {
            drawSettingsEmptyState(g, font, x, formY, w, available, "Screen Too Small", "Use keyboard input to continue.");
            int miniButtonY = Math.max(formY + available - buttonH, formY);
            mobileButton(g, font, x, miniButtonY, w, buttonH, "Save", 0xFF0078FF,
                    hitboxes, "SAVE_PHONE_PASSCODE", "", "", "");
        }
    }

    private static void drawSettingsPasscodeField(GuiGraphics g, Font font, int x, int y, int w, int h,
                                                  String label, String value,
                                                  SmartphoneClientState.InputTarget target, boolean divider,
                                                  List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean active = SmartphoneClientState.inputTarget() == target;
        int labelW = Math.max(72, Math.round(w * 0.48F));
        drawScaledString(g, font, trim(font, label, labelW), x + 14, y + Math.max(7, h / 2 - 5),
                0xFF111318, h < 40 ? 0.76F : 0.86F);
        String dots = passwordDots(value);
        String display = dots.isBlank() ? (active ? "" : "4 digits") : dots;
        int valueX = x + labelW + 12;
        int valueW = Math.max(24, w - labelW - 40);
        drawFieldText(g, font, valueX, y + Math.max(7, h / 2 - 5), valueW,
                display, active, "4 digits", 0xFF111318, 0xFF8E8E93, 0xFF0078FF);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                "INPUT", target.name(), "", ""));
        if (divider) {
            g.fill(x + 14, y + h - 1, x + w, y + h, 0xFFE5E5EA);
        }
    }

    private static void drawSettingsInstalledApps(GuiGraphics g, Font font, int x, int y, int w, int h,
                                                  List<SmartphoneClientState.Hitbox> hitboxes) {
        SmartphoneClientState.PhoneApp selectedApp = SmartphoneClientState.settingsSelectedApp();
        if (selectedApp != null) {
            drawSettingsAppDetail(g, font, x, y, w, h, selectedApp, hitboxes);
            return;
        }
        int backSize = 30;
        drawFigmaChevronLeft(g, x + 7, y + 16, 6, 0xFF0078FF);
        drawScaledString(g, font, "Settings", x + 16, y + 10, 0xFF0078FF, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, Math.min(82, w / 3), backSize),
                "SETTINGS_MAIN", "", "", ""));
        drawScaledCentered(g, font, "Installed Apps", x + w / 2, y + 8, 0xFF111318, 1.02F);

        List<SmartphoneClientState.PhoneApp> apps = SmartphoneClientState.apps();
        boolean compactApps = h < 300;
        boolean tightApps = h < 240;
        int headerY = y + (tightApps ? 34 : 44);
        drawScaledString(g, font, apps.size() + " available", x + 2, headerY, 0xFF8E8E93, 0.74F);
        int listY = headerY + (tightApps ? 14 : 20);
        int listH = Math.max(0, y + h - listY - 8);
        int minRowH = tightApps ? 28 : (compactApps ? 34 : 42);
        int rowH = Mth.clamp(Math.round(h * (tightApps ? 0.14F : 0.12F)), minRowH, tightApps ? 38 : 58);
        if (listH < rowH && listH >= 24) {
            rowH = listH;
        }
        int visibleRows = listH >= 24 ? Math.max(1, listH / Math.max(1, rowH)) : 0;
        SmartphoneClientState.syncSettingsInstalledAppsScroll(apps.size(), Math.max(1, visibleRows));
        int offset = SmartphoneClientState.settingsInstalledAppsScrollOffset();
        int groupH = Math.min(listH, Math.max(rowH, Math.min(apps.size(), Math.max(1, visibleRows)) * rowH));
        if (listH > 0) {
            fillRounded(g, x, listY, w, groupH, 16, 0xFFFFFFFF);
        }
        int drawn = 0;
        for (int i = offset; i < apps.size() && drawn < visibleRows; i++) {
            SmartphoneClientState.PhoneApp app = apps.get(i);
            int rowY = listY + drawn * rowH;
            drawInstalledAppRow(g, font, x, rowY, w, rowH, app,
                    drawn < visibleRows - 1 && i < apps.size() - 1, hitboxes);
            drawn++;
        }
        if (apps.isEmpty() && listH >= 42) {
            drawSettingsEmptyState(g, font, x, listY, w, listH, "No Apps", "App library is empty.");
        }
        if (visibleRows > 0) {
            drawMessageDraftScrollbar(g, x, listY, w, listH, offset, visibleRows, apps.size());
        }
    }

    private static void drawInstalledAppRow(GuiGraphics g, Font font, int x, int y, int w, int h,
                                            SmartphoneClientState.PhoneApp app, boolean divider,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        int icon = Mth.clamp(Math.round(h * 0.62F), h < 34 ? 18 : 28, 38);
        int iconX = x + 14;
        int iconY = y + (h - icon) / 2;
        drawHomeAppIcon(g, app.id(), iconX, iconY, icon, appColor(app.id()));
        int titleY = y + Math.max(6, h / 2 - 6);
        drawScaledString(g, font, trim(font, app.label(), w - 94), x + 62, titleY,
                0xFF111318, h < 34 ? 0.82F : 0.94F);
        drawFigmaChevronRight(g, x + w - 20, y + h / 2, 5, 0xFFC7C7CC);
        if (divider) {
            g.fill(x + 62, y + h - 1, x + w, y + h, 0xFFE5E5EA);
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                "SETTINGS_APP_DETAIL", app.id(), "", ""));
    }

    private static void drawSettingsAppDetail(GuiGraphics g, Font font, int x, int y, int w, int h,
                                              SmartphoneClientState.PhoneApp app,
                                              List<SmartphoneClientState.Hitbox> hitboxes) {
        int backSize = 30;
        drawFigmaChevronLeft(g, x + 7, y + 16, 6, 0xFF0078FF);
        drawScaledString(g, font, "Apps", x + 16, y + 10, 0xFF0078FF, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, Math.min(62, w / 3), backSize),
                "SETTINGS_APPS", "", "", ""));
        drawScaledCentered(g, font, trim(font, app.label(), Math.round(w * 0.58F)),
                x + w / 2, y + 8, 0xFF111318, 1.02F);

        boolean compactDetail = h < 330;
        boolean tightDetail = h < 250;
        int bottom = y + h - 8;
        int openH = compactDetail ? Mth.clamp(Math.round(h * (tightDetail ? 0.12F : 0.10F)),
                tightDetail ? 28 : 32, tightDetail ? 34 : 38)
                : Mth.clamp(Math.round(h * 0.075F), 36, 46);
        int openY = bottom - openH;
        int icon = compactDetail
                ? Mth.clamp(Math.round(w * 0.16F), tightDetail ? 28 : 38, tightDetail ? 38 : 52)
                : Mth.clamp(Math.round(w * 0.22F), 56, 76);
        int iconX = x + w / 2 - icon / 2;
        int iconY = y + (compactDetail ? (tightDetail ? 34 : 38) : Mth.clamp(Math.round(h * 0.10F), 50, 66));
        boolean showIcon = iconY + icon + (tightDetail ? 8 : 16) < openY - (tightDetail ? 30 : 44);
        if (showIcon) {
            drawHomeAppIcon(g, app.id(), iconX, iconY, icon, appColor(app.id()));
            drawScaledCentered(g, font, trim(font, app.label(), w - 36),
                    x + w / 2, iconY + icon + (compactDetail ? 8 : 16), 0xFF111318,
                    tightDetail ? 0.82F : (compactDetail ? 0.92F : 1.18F));
        }

        int cardY = showIcon
                ? iconY + icon + (compactDetail ? (tightDetail ? 20 : 26) : 44)
                : y + (tightDetail ? 42 : 54);
        int groupY = cardY;
        int detailGap = compactDetail ? 8 : 16;
        int availableDetailH = Math.max(0, openY - detailGap - groupY);
        int groupRows = Math.max(1, Math.min(2, availableDetailH / (tightDetail ? 24 : 38)));
        int rowH = Mth.clamp(availableDetailH / Math.max(1, groupRows), tightDetail ? 22 : 34, tightDetail ? 34 : 46);
        if (availableDetailH >= (tightDetail ? 22 : 34) && groupY + rowH <= openY - detailGap) {
            fillRounded(g, x, groupY, w, rowH * groupRows, 16, 0xFFFFFFFF);
            String appName = trim(font, app.label(), Math.max(48, w - 128));
            if (groupRows == 1) {
                drawContactInfoRow(g, font, x, groupY, w, rowH, "App",
                        trim(font, app.label() + " | " + app.id(), Math.max(48, w - 108)), 0xFF111318, false);
            } else {
                drawContactInfoRow(g, font, x, groupY, w, rowH, "Name",
                        appName, 0xFF111318, true);
            }
            if (groupRows > 1) {
                drawContactInfoRow(g, font, x, groupY + rowH, w, rowH, "Identifier",
                        trim(font, app.id(), Math.max(48, w - 128)), 0xFF8E8E93, false);
            }
        }

        mobileButton(g, font, x, openY, w, openH, "Open App", 0xFF0078FF,
                hitboxes, "SETTINGS_APP_OPEN", app.id(), "", "");
    }

    private static void drawSettingsAppearance(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               List<SmartphoneClientState.Hitbox> hitboxes) {
        int backSize = 30;
        drawFigmaChevronLeft(g, x + 7, y + 16, 6, 0xFF0078FF);
        drawScaledString(g, font, "Settings", x + 16, y + 10, 0xFF0078FF, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, Math.min(82, w / 3), backSize),
                "SETTINGS_MAIN", "", "", ""));
        drawScaledCentered(g, font, "Appearance", x + w / 2, y + 8, 0xFF111318, 1.02F);

        int bottom = y + h - 8;
        boolean compactAppearance = h < 330;
        boolean tightAppearance = h < 280;
        int minThemeRowH = tightAppearance ? 22 : (compactAppearance ? 26 : 34);
        int minThemeGroupH = minThemeRowH * 4;
        int previewY = y + (compactAppearance ? (tightAppearance ? 34 : 38) : Mth.clamp(Math.round(h * 0.10F), 42, 48));
        int availableAfterHeader = Math.max(0, bottom - previewY);
        int desiredPreviewH = compactAppearance
                ? Mth.clamp(Math.round(h * 0.11F), tightAppearance ? 18 : 28, tightAppearance ? 28 : 40)
                : Mth.clamp(Math.round(h * 0.18F), 70, 104);
        int groupGap = compactAppearance ? (tightAppearance ? 12 : 18) : Mth.clamp(Math.round(h * 0.07F), 22, 36);
        boolean showPreview = availableAfterHeader >= minThemeGroupH + groupGap + Math.max(16, desiredPreviewH);
        int previewH = showPreview ? Math.min(desiredPreviewH, Math.max(16, availableAfterHeader - minThemeGroupH - groupGap)) : 0;
        if (showPreview) {
            fillRoundedVertical(g, x, previewY, w, previewH, 20,
                    SmartphoneClientState.wallpaperTop(), SmartphoneClientState.wallpaperBottom());
            int previewDot = compactAppearance ? 11 : 18;
            fillCircle(g, x + Math.round(w * 0.18F), previewY + previewH / 2, previewDot, SmartphoneClientState.accentColor());
            drawScaledString(g, font, settingThemeLabel(), x + Math.round(w * 0.32F),
                    previewY + (compactAppearance ? Math.max(6, previewH / 2 - 4) : Math.round(previewH * 0.34F)),
                    0xFFFFFFFF, compactAppearance ? 0.82F : 1.0F);
            if (!compactAppearance) {
                drawScaledString(g, font, "Current phone colors", x + Math.round(w * 0.32F),
                        previewY + Math.round(previewH * 0.55F), 0xCCFFFFFF, 0.74F);
            }
        }

        int groupY = showPreview ? previewY + previewH + groupGap : y + (tightAppearance ? 52 : 58);
        drawScaledString(g, font, "Themes", x + 2, groupY - 18, 0xFF8E8E93, 0.74F);
        int availableThemeH = Math.max(0, bottom - groupY);
        ThemeOption[] themes = {
                new ThemeOption("Aurora", "Cyan accent", "cyan", "aurora", 0xFF52D7FF),
                new ThemeOption("Forest", "Green accent", "green", "forest", 0xFF23D18B),
                new ThemeOption("Dusk", "Rose accent", "rose", "dusk", 0xFFFF6A9C),
                new ThemeOption("Midnight", "Gold accent", "gold", "mono", 0xFFFFCA5C)
        };
        int rowH = Mth.clamp(availableThemeH / Math.max(1, themes.length), minThemeRowH, 52);
        int visibleRows = availableThemeH >= minThemeRowH ? Math.max(1, Math.min(themes.length, availableThemeH / rowH)) : 0;
        SmartphoneClientState.syncSettingsAppearanceScroll(themes.length, Math.max(1, visibleRows));
        int offset = SmartphoneClientState.settingsAppearanceScrollOffset();
        if (visibleRows > 0) {
            int drawnRows = Math.min(themes.length - offset, visibleRows);
            fillRounded(g, x, groupY, w, rowH * drawnRows, 16, 0xFFFFFFFF);
            for (int i = 0; i < drawnRows; i++) {
                int rowIndex = offset + i;
                ThemeOption theme = themes[rowIndex];
                drawThemeRow(g, font, x, groupY + rowH * i, w, rowH, theme.title(), theme.subtitle(),
                        theme.accent(), theme.wallpaper(), theme.color(), rowIndex < themes.length - 1, hitboxes);
            }
            if (themes.length > visibleRows) {
                drawMessageDraftScrollbar(g, x, groupY, w, availableThemeH, offset, visibleRows, themes.length);
            }
        }
    }

    private record ThemeOption(String title, String subtitle, String accent, String wallpaper, int color) {
    }

    private static void drawSettingsRow(GuiGraphics g, Font font, int x, int y, int w, int h,
                                        String title, String subtitle, int iconColor, boolean divider,
                                        String action, String p1, String p2,
        List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean compact = h < 40;
        int iconMax = Math.max(8, Math.min(30, h - 4));
        int iconMin = Math.min(iconMax, h < 34 ? 12 : 22);
        int iconSize = Mth.clamp(Math.round(h * 0.58F), iconMin, iconMax);
        int iconX = x + 12;
        int iconY = y + (h - iconSize) / 2;
        fillRounded(g, iconX, iconY, iconSize, iconSize, Mth.clamp(iconSize / 4, 5, 8), iconColor);
        drawSettingsRowIcon(g, title, iconX, iconY, iconSize, 0xFFFFFFFF);
        if (compact && h >= 34) {
            int arrowSpace = action != null && !action.isBlank() ? 42 : 14;
            int textLeft = x + 52;
            int textRight = x + w - arrowSpace;
            int textW = Math.max(12, textRight - textLeft);
            boolean showSubtitleInline = textW >= 104 && subtitle != null && !subtitle.isBlank();
            if (showSubtitleInline) {
                int subtitleMaxW = Math.max(28, Math.min(textW / 2, textW - 46));
                String safeSubtitle = trim(font, subtitle, subtitleMaxW);
                int subtitleW = Math.min(subtitleMaxW, phoneTextWidth(safeSubtitle, 6.6F));
                int subtitleX = Mth.clamp(textRight - subtitleW, textLeft + 46, Math.max(textLeft, textRight - 1));
                int titleW = Math.max(24, subtitleX - textLeft - 6);
                drawScaledString(g, font, trim(font, title, titleW), textLeft, y + Math.max(5, h / 2 - 5),
                        0xFF111318, 0.82F);
                drawScaledString(g, font, safeSubtitle, subtitleX, y + Math.max(6, h / 2 - 4),
                        0xFF8E8E93, 0.66F);
            } else {
                drawScaledString(g, font, trim(font, title, textW), textLeft, y + Math.max(5, h / 2 - 5),
                        0xFF111318, 0.82F);
            }
        } else {
            int textW = Math.max(12, w - 94);
            drawScaledString(g, font, trim(font, title, textW), x + 52, y + 12,
                    0xFF111318, 0.94F);
            drawScaledString(g, font, trim(font, subtitle, textW), x + 52, y + 30, 0xFF8E8E93, 0.72F);
        }
        if (action != null && !action.isBlank()) {
            drawFigmaChevronRight(g, x + w - 22, y + h / 2, 5, 0xFFC7C7CC);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                    action, p1, p2, ""));
        }
        if (divider) {
            g.fill(x + 52, y + h - 1, x + w, y + h, 0xFFE5E5EA);
        }
    }

    private static void drawSettingsEmptyState(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               String title, String subtitle) {
        int centerX = x + w / 2;
        int centerY = y + Math.max(22, h / 2);
        if (h >= 70) {
            int iconY = centerY - 24;
            fillRounded(g, centerX - 18, iconY - 18, 36, 36, 10, 0xFFE5E5EA);
            fillCircle(g, centerX, iconY, 8, 0xFF8E8E93);
            g.fill(centerX - 2, iconY - 15, centerX + 2, iconY - 10, 0xFF8E8E93);
            g.fill(centerX - 2, iconY + 10, centerX + 2, iconY + 15, 0xFF8E8E93);
            g.fill(centerX - 15, iconY - 2, centerX - 10, iconY + 2, 0xFF8E8E93);
            g.fill(centerX + 10, iconY - 2, centerX + 15, iconY + 2, 0xFF8E8E93);
        }
        int titleY = h >= 70 ? centerY + 2 : centerY - 8;
        drawScaledCentered(g, font, title, centerX, titleY, 0xFF111318, 0.98F);
        if (h >= 58) {
            drawScaledCentered(g, font, subtitle, centerX, titleY + 17, 0xFF8E8E93, 0.68F);
        }
    }

    private static void drawSettingsRowIcon(GuiGraphics g, String title, int x, int y, int size, int color) {
        String key = title == null ? "" : title.toLowerCase(Locale.ROOT);
        int cx = x + size / 2;
        int cy = y + size / 2;
        int unit = Math.max(2, size / 7);
        if (key.contains("appearance")) {
            fillCircle(g, cx, cy, Math.max(4, size / 5), color);
            g.fill(cx - 1, y + 4, cx + 1, y + 8, color);
            g.fill(cx - 1, y + size - 8, cx + 1, y + size - 4, color);
            g.fill(x + 4, cy - 1, x + 8, cy + 1, color);
            g.fill(x + size - 8, cy - 1, x + size - 4, cy + 1, color);
            return;
        }
        if (key.contains("installed")) {
            int cell = Math.max(4, size / 4);
            int gap = Math.max(2, size / 9);
            int startX = cx - cell - gap / 2;
            int startY = cy - cell - gap / 2;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    fillRounded(g, startX + col * (cell + gap), startY + row * (cell + gap),
                            cell, cell, Math.max(2, cell / 4), color);
                }
            }
            return;
        }
        int bodyW = Math.max(10, size / 2);
        int bodyH = Math.max(8, size / 3);
        fillRounded(g, cx - bodyW / 2, cy - bodyH / 2 + unit, bodyW, bodyH, Math.max(2, unit), color);
        drawCircleRing(g, cx, cy - unit, Math.max(5, size / 5), Math.max(2, unit / 2), color, color);
        g.fill(cx - Math.max(2, unit / 2), cy + unit, cx + Math.max(2, unit / 2), cy + unit * 2 + 1, 0xAA000000);
    }

    private static String settingThemeLabel() {
        return switch (SmartphoneClientState.wallpaperName().toLowerCase(Locale.ROOT)) {
            case "forest" -> "Forest";
            case "dusk" -> "Dusk";
            case "mono" -> "Midnight";
            default -> "Aurora";
        };
    }

    private static String firstNoteLine(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            return "New note";
        }
        int newline = safe.indexOf('\n');
        return newline >= 0 ? safe.substring(0, newline).trim() : safe;
    }

    private static void drawThemeRow(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     String title, String subtitle, String accent, String wallpaper, int color,
                                     boolean divider, List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean selected = accent.equalsIgnoreCase(SmartphoneClientState.accentName())
                && wallpaper.equalsIgnoreCase(SmartphoneClientState.wallpaperName());
        boolean compact = h < 40;
        int iconSize = Mth.clamp(Math.round(h * 0.58F), h < 34 ? 18 : 22, 30);
        int iconX = x + 12;
        int iconY = y + (h - iconSize) / 2;
        fillRounded(g, iconX, iconY, iconSize, iconSize, Mth.clamp(iconSize / 4, 5, 8), color);
        fillCircle(g, iconX + iconSize / 2, iconY + iconSize / 2, Math.max(4, iconSize / 5), 0xEEFFFFFF);
        if (compact && h >= 34) {
            int textLeft = x + 52;
            int textRight = x + w - 50;
            int textW = Math.max(12, textRight - textLeft);
            boolean showSubtitleInline = textW >= 104 && subtitle != null && !subtitle.isBlank();
            if (showSubtitleInline) {
                int subtitleMaxW = Math.max(28, Math.min(textW / 2, textW - 46));
                String safeSubtitle = trim(font, subtitle, subtitleMaxW);
                int subtitleW = Math.min(subtitleMaxW, phoneTextWidth(safeSubtitle, 6.6F));
                int subtitleX = Mth.clamp(textRight - subtitleW, textLeft + 46, Math.max(textLeft, textRight - 1));
                int titleW = Math.max(24, subtitleX - textLeft - 6);
                drawScaledString(g, font, trim(font, title, titleW), textLeft, y + Math.max(5, h / 2 - 5),
                        0xFF111318, 0.82F);
                drawScaledString(g, font, safeSubtitle, subtitleX, y + Math.max(6, h / 2 - 4),
                        0xFF8E8E93, 0.66F);
            } else {
                drawScaledString(g, font, trim(font, title, textW), textLeft, y + Math.max(5, h / 2 - 5),
                        0xFF111318, 0.82F);
            }
        } else {
            int textW = Math.max(12, w - 94);
            drawScaledString(g, font, trim(font, title, textW), x + 52, y + 12,
                    0xFF111318, 0.94F);
            drawScaledString(g, font, trim(font, subtitle, textW), x + 52, y + 30, 0xFF8E8E93, 0.72F);
        }
        if (selected) {
            fillCircle(g, x + w - 27, y + h / 2, Mth.clamp(Math.round(h * 0.22F), 7, 11), 0xFF0078FF);
            drawFigmaCheckGlyph(g, x + w - 27, y + h / 2, 0xFFFFFFFF);
        }
        if (divider) {
            g.fill(x + 52, y + h - 1, x + w, y + h, 0xFFE5E5EA);
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h),
                "THEME", accent, wallpaper, ""));
    }

    private static void drawInput(GuiGraphics g, Font font, int x, int y, int w, String label, String value,
                                  SmartphoneClientState.InputTarget target, List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean active = SmartphoneClientState.inputTarget() == target;
        g.drawString(font, label, x, y, 0xCCFFFFFF, false);
        g.fill(x, y + 11, x + w, y + 31, active ? 0xDD102D44 : 0xAA081624);
        g.fill(x, y + 11, x + 3, y + 31, active ? SmartphoneClientState.accentColor() : 0x6688CCFF);
        drawFieldText(g, font, x + 7, y + 17, w - 10, value, active, "...");
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y + 11, w, 20), "INPUT", target.name(), "", ""));
    }

    private static void drawBankSurface(GuiGraphics g, int x, int y, int w, int h, int color) {
        int topBleed = 45;
        g.fill(x - BANK_DISPLAY_BLEED, y - topBleed, x + w + BANK_DISPLAY_BLEED, y + h + 8, color);
    }

    private static void drawPhoneAppSurface(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x - BANK_DISPLAY_BLEED, y - PHONE_APP_TOP_BLEED, x + w + BANK_DISPLAY_BLEED, y + h + 8, color);
    }

    private static void drawBankBackdropAccents(GuiGraphics g, int x, int y, int w, int h) {
        int left = x + Math.round(w * 0.08F);
        int right = x + w - Math.round(w * 0.08F);
        int top = y + Math.round(h * 0.18F);
        int mid = y + Math.round(h * 0.54F);
        g.fill(left, top, right, top + 1, 0x10006BFF);
        g.fill(x + Math.round(w * 0.22F), mid, right, mid + 1, 0x0C18D2FF);
    }

    private static void drawFieldText(GuiGraphics g, Font font, int x, int y, int maxW,
                                      String value, boolean active, String placeholder) {
        drawFieldText(g, font, x, y, maxW, value, active, placeholder,
                0xFFFFFFFF, 0x88FFFFFF, 0xFFFFFFFF);
    }

    private static void drawFieldText(GuiGraphics g, Font font, int x, int y, int maxW,
                                      String value, boolean active, String placeholder,
                                      int textColor, int placeholderColor, int caretColor) {
        String actual = value == null ? "" : value;
        String shown = actual.isBlank() ? (active ? "" : (placeholder == null ? "" : placeholder)) : actual;
        int color = actual.isBlank() ? placeholderColor : textColor;
        g.drawString(font, trim(font, shown, maxW - 8), x, y, color, false);
        if (active) {
            int caretX = x + (actual.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, actual, maxW - 8), 9.0F), maxW - 8)) + 2;
            g.fill(caretX, y - 1, caretX + 1, y + 10, activeCaretColor(caretColor));
        }
    }

    private static void drawMultilineFieldText(GuiGraphics g, Font font, int x, int y, int maxW,
                                               List<String> lines, String value, boolean active, String placeholder,
                                               int textColor, int placeholderColor, int caretColor,
                                               boolean caretVisible) {
        String actual = value == null ? "" : value;
        if (actual.isBlank()) {
            String shown = active ? "" : (placeholder == null ? "" : placeholder);
            g.drawString(font, shown, x, y, placeholderColor, false);
            if (active) {
                g.fill(x + 2, y - 1, x + 3, y + 10, activeCaretColor(caretColor));
            }
            return;
        }
        List<String> safeLines = lines == null || lines.isEmpty() ? List.of(actual) : lines;
        int ty = y;
        for (String line : safeLines) {
            g.drawString(font, trim(font, line, maxW), x, ty, textColor, false);
            ty += 13;
        }
        if (active && caretVisible) {
            String last = safeLines.get(safeLines.size() - 1);
            int caretX = x + Math.min(phoneTextWidth(last, 9.0F), maxW) + 2;
            int caretY = y + (safeLines.size() - 1) * 13;
            g.fill(caretX, caretY - 1, caretX + 1, caretY + 10, activeCaretColor(caretColor));
        }
    }

    private static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int color,
                               List<SmartphoneClientState.Hitbox> hitboxes, String action, String p1, String p2, String p3) {
        fillRounded(g, x, y, w, h, 5, 0xDD07111F);
        g.fill(x, y, x + 3, y + h, color);
        g.drawString(font, trim(font, label, w - 8), x + 6, y + (h - 8) / 2, 0xFFFFFFFF, false);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h), action, p1, p2, p3));
    }

    private static void mobileButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int color,
                                     List<SmartphoneClientState.Hitbox> hitboxes, String action, String p1, String p2, String p3) {
        figmaPrimaryButton(g, font, x, y, w, h, label, color, hitboxes, action, p1, p2, p3);
    }

    private static void figmaPrimaryButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int color,
                                           List<SmartphoneClientState.Hitbox> hitboxes,
                                           String action, String p1, String p2, String p3) {
        drawFigmaPrimaryButtonVisual(g, font, x, y, w, h, label, color);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, w, h), action, p1, p2, p3));
    }

    private static void drawFigmaPrimaryButtonVisual(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int color) {
        int radius = Mth.clamp(Math.round(h * 0.30F), 10, 18);
        fillRounded(g, x, y, w, h, radius, color);
        float scale = Mth.clamp(h / 52.0F, 0.72F, 1.15F);
        drawScaledCentered(g, font, trim(font, label, Math.round((w - 24) / scale)),
                x + w / 2, y + Math.round((h - font.lineHeight * scale) / 2.0F), 0xFFFFFFFF, scale);
    }

    private static void card(GuiGraphics g, int x, int y, int w, int h) {
        fillRounded(g, x, y, w, h, 7, 0xB50B1626);
        g.fill(x, y, x + w, y + 1, 0x6652D7FF);
    }

    private static void drawHardwareHomeButton(GuiGraphics g, int centerX, int centerY) {
        fillCircle(g, centerX, centerY, 20, PHONE_BLACK);
        fillCircle(g, centerX, centerY, 18, PHONE_BUTTON_RING);
        fillCircle(g, centerX, centerY, 15, PHONE_BUTTON_FACE);
        g.fill(centerX - 9, centerY - 11, centerX + 9, centerY - 9, 0xFF0B0B0B);
        g.fill(centerX - 7, centerY + 10, centerX + 7, centerY + 11, 0xFF0A0A0A);
    }

    private static void drawClassicTopHardware(GuiGraphics g, int x, int y, int w) {
        int centerX = x + w / 2;
        int speakerW = Math.max(32, Math.round(w * 0.14F));
        int speakerX = centerX - speakerW / 2;
        int speakerY = y + 14;
        fillRounded(g, speakerX - 2, speakerY - 2, speakerW + 4, 6, 3, PHONE_BLACK);
        fillRounded(g, speakerX, speakerY, speakerW, 2, 1, 0xFF15171A);
        g.fill(speakerX + 4, speakerY, speakerX + speakerW - 4, speakerY + 1, 0xFF2A2D33);
        int cameraX = centerX + speakerW / 2 + 14;
        fillCircle(g, cameraX, speakerY + 1, 5, PHONE_BLACK);
        fillCircle(g, cameraX, speakerY + 1, 3, 0xFF0A1020);
        fillCircle(g, cameraX - 1, speakerY, 1, 0xFF263E66);
    }

    private static void drawStatusBar(GuiGraphics g, Font font, int x, int y, int w) {
        drawStatusBar(g, font, x, y, w, 0xFFEAF8FF, 0xFF060B1C);
    }

    private static void drawStatusBar(GuiGraphics g, Font font, int x, int y, int w, int color, int batteryInner) {
        int statusInset = Math.max(3, Math.round(w * 5.0F / 335.0F));
        int statusX = x + statusInset;
        drawPhoneText(g, localClockText(), statusX, y - 1, color, 9.4F);
        int right = x + w + statusInset;
        int signalX = right - 54;
        for (int i = 0; i < 4; i++) {
            int barH = 3 + i * 2;
            g.fill(signalX + i * 4, y + 8 - barH, signalX + i * 4 + 2, y + 8, color);
        }
        int wifiX = right - 35;
        g.fill(wifiX, y + 4, wifiX + 13, y + 5, color);
        g.fill(wifiX + 2, y + 6, wifiX + 11, y + 7, color);
        g.fill(wifiX + 5, y + 8, wifiX + 8, y + 9, color);
        int batteryX = right - 16;
        g.fill(batteryX, y + 3, batteryX + 12, y + 9, color);
        g.fill(batteryX + 12, y + 5, batteryX + 14, y + 7, color);
        g.fill(batteryX + 2, y + 5, batteryX + 10, y + 7, batteryInner);
    }

    private static String localClockText() {
        return formatClockTime(SmartphoneClientState.localClockMillis(), ZoneId.systemDefault());
    }

    private static String serverClockText() {
        return formatClockTime(SmartphoneClientState.serverClockMillis(),
                parseClockZone(SmartphoneClientState.serverClockZone()));
    }

    private static String formatClockTime(long millis, ZoneId zone) {
        ZoneId resolved = zone == null ? ZoneId.systemDefault() : zone;
        return DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
                .withZone(resolved)
                .format(Instant.ofEpochMilli(millis));
    }

    private static ZoneId parseClockZone(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return ZoneId.of(raw);
            } catch (RuntimeException ignored) {
            }
        }
        return ZoneId.systemDefault();
    }

    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        drawRoundedBorder(g, x, y, w, h, r, color, 0xFFFFFFFF);
    }

    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color, int innerColor) {
        fillRounded(g, x, y, w, h, r, color);
        fillRounded(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), innerColor);
    }

    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + radius, y, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + w, y + h - radius, color);
        for (int i = 0; i < radius; i++) {
            double dy = radius - i - 0.5D;
            double extent = Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
            double boundary = radius - extent;
            int inset = (int) Math.ceil(boundary);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
            int edge = inset - 1;
            if (edge >= 0) {
                int edgeColor = withAlpha(color, Mth.clamp(Math.round((1.0F - (float) (boundary - Math.floor(boundary))) * 132.0F), 26, 132));
                g.fill(x + edge, y + i, x + edge + 1, y + i + 1, edgeColor);
                g.fill(x + w - edge - 1, y + i, x + w - edge, y + i + 1, edgeColor);
                g.fill(x + edge, y + h - i - 1, x + edge + 1, y + h - i, edgeColor);
                g.fill(x + w - edge - 1, y + h - i - 1, x + w - edge, y + h - i, edgeColor);
            }
        }
    }

    private static void fillRoundedVertical(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            float t = h <= 1 ? 0.0F : i / (float) (h - 1);
            int color = lerpColor(top, bottom, t);
            int inset = 0;
            int edge = -1;
            double boundary = 0.0D;
            if (radius > 0 && i < radius) {
                double dy = radius - i - 0.5D;
                double extent = Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
                boundary = radius - extent;
                inset = (int) Math.ceil(boundary);
                edge = inset - 1;
            } else if (radius > 0 && i >= h - radius) {
                double dy = radius - (h - i - 1) - 0.5D;
                double extent = Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
                boundary = radius - extent;
                inset = (int) Math.ceil(boundary);
                edge = inset - 1;
            }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            if (edge >= 0) {
                int edgeColor = withAlpha(color, Mth.clamp(Math.round((1.0F - (float) (boundary - Math.floor(boundary))) * 132.0F), 26, 132));
                g.fill(x + edge, y + i, x + edge + 1, y + i + 1, edgeColor);
                g.fill(x + w - edge - 1, y + i, x + w - edge, y + i + 1, edgeColor);
            }
        }
    }

    private static void fillCircle(GuiGraphics g, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            double exact = Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
            int dx = (int) Math.floor(exact);
            g.fill(centerX - dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
            int edgeColor = withAlpha(color, Mth.clamp(Math.round((float) (exact - dx) * 124.0F), 20, 124));
            g.fill(centerX - dx - 1, centerY + dy, centerX - dx, centerY + dy + 1, edgeColor);
            g.fill(centerX + dx + 1, centerY + dy, centerX + dx + 2, centerY + dy + 1, edgeColor);
        }
    }

    private static void maskOutsideCircle(GuiGraphics g, int x, int y, int size, int centerX, int centerY,
                                          int radius, int color) {
        int r2 = radius * radius;
        for (int row = 0; row < size; row++) {
            int py = y + row;
            int dy = py - centerY;
            int span = (int) Math.floor(Math.sqrt(Math.max(0.0D, r2 - dy * dy)));
            int left = Math.max(x, centerX - span);
            int right = Math.min(x + size, centerX + span + 1);
            if (left > x) {
                g.fill(x, py, left, py + 1, color);
            }
            if (right < x + size) {
                g.fill(right, py, x + size, py + 1, color);
            }
        }
    }

    private static void maskRoundedRectCorners(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
        int r2 = r * r;
        for (int py = 0; py < r; py++) {
            int dy = r - py;
            int outside = 0;
            while (outside < r) {
                int dx = r - outside;
                if (dx * dx + dy * dy <= r2) {
                    break;
                }
                outside++;
            }
            if (outside > 0) {
                g.fill(x, y + py, x + outside, y + py + 1, color);
                g.fill(x + w - outside, y + py, x + w, y + py + 1, color);
                g.fill(x, y + h - py - 1, x + outside, y + h - py, color);
                g.fill(x + w - outside, y + h - py - 1, x + w, y + h - py, color);
            }
        }
    }

    private static void fillVertical(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (top == bottom) {
            g.fill(x, y, x + w, y + h, top);
            return;
        }
        int steps = Math.max(1, Math.min(h, MAX_GRADIENT_FILL_BANDS));
        for (int i = 0; i < steps; i++) {
            int y0 = y + (i * h) / steps;
            int y1 = y + ((i + 1) * h) / steps;
            if (y1 <= y0) {
                continue;
            }
            float t = steps <= 1 ? 0.0F : i / (float) (steps - 1);
            int c = lerpColor(top, bottom, t);
            g.fill(x, y0, x + w, y1, c);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;
        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;
        int r = (int) Mth.lerp(t, ar, br);
        int g = (int) Mth.lerp(t, ag, bg);
        int blue = (int) Mth.lerp(t, ab, bb);
        return 0xFF000000 | (r << 16) | (g << 8) | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static void drawCentered(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        drawPhoneText(g, text, centerX - phoneTextWidth(text, 8.8F) / 2, y - 1, color, 8.8F);
    }

    private static void drawScaledString(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float safeScale = Mth.clamp(scale, 0.50F, 2.35F);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(safeScale, safeScale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void drawScaledCentered(GuiGraphics g, Font font, String text, int centerX, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float safeScale = Mth.clamp(scale, 0.50F, 2.35F);
        int scaledWidth = Math.round(font.width(text) * safeScale);
        drawScaledString(g, font, text, centerX - scaledWidth / 2, y, color, safeScale);
    }

    private static void drawPhoneText(GuiGraphics g, String raw, int x, int y, int color, float size) {
        String text = raw == null ? "" : raw;
        if (text.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float scale = Mth.clamp(size / Math.max(1.0F, font.lineHeight), 0.50F, 2.35F);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static int phoneTextWidth(String raw, float size) {
        String text = raw == null ? "" : raw;
        if (text.isEmpty()) {
            return 0;
        }
        Font font = Minecraft.getInstance().font;
        float scale = Mth.clamp(size / Math.max(1.0F, font.lineHeight), 0.50F, 2.35F);
        return Math.round(font.width(text) * scale);
    }

    private static float fitPhoneTextSize(String raw, int width, float preferred, float minimum) {
        String text = raw == null ? "" : raw;
        float size = preferred;
        while (size > minimum && phoneTextWidth(text, size) > width) {
            size -= 0.5F;
        }
        return Math.max(minimum, size);
    }

    private static String trimPhoneText(String raw, int width, float size) {
        String value = raw == null ? "" : raw;
        if (phoneTextWidth(value, size) <= width) {
            return value;
        }
        String ellipsis = "...";
        while (!value.isEmpty() && phoneTextWidth(value + ellipsis, size) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private static String trimPhoneTextFromStart(String raw, int width, float size) {
        String value = raw == null ? "" : raw;
        if (phoneTextWidth(value, size) <= width) {
            return value;
        }
        String ellipsis = "...";
        while (!value.isEmpty() && phoneTextWidth(ellipsis + value, size) > width) {
            value = value.substring(1);
        }
        return ellipsis + value;
    }

    private static void drawWrapped(GuiGraphics g, Font font, String text, int x, int y, int w, int color, int maxLines) {
        List<String> lines = wrap(font, text == null ? "" : text, w, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + i * 11, color, false);
        }
    }

    private static List<String> wrap(Font font, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        String line = "";
        for (String word : words) {
            String candidate = line.isBlank() ? word : line + " " + word;
            if (font.width(candidate) <= width) {
                line = candidate;
            } else {
                if (!line.isBlank()) {
                    lines.add(line);
                }
                line = word;
            }
            if (lines.size() >= maxLines) {
                break;
            }
        }
        if (!line.isBlank() && lines.size() < maxLines) {
            lines.add(line);
        }
        return lines;
    }

    private static String trim(Font font, String text, int width) {
        String value = text == null ? "" : text;
        if (phoneTextWidth(value, 9.0F) <= width) {
            return value;
        }
        String ellipsis = "...";
        while (!value.isEmpty() && phoneTextWidth(value + ellipsis, 9.0F) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private static String shortUuid(java.util.UUID id) {
        if (id == null) {
            return "-";
        }
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String initials(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) {
            return "U";
        }
        String[] parts = value.split("\\s+");
        String first = parts[0].isEmpty() ? "U" : parts[0].substring(0, 1);
        if (parts.length == 1 || parts[parts.length - 1].isEmpty()) {
            return first.toUpperCase();
        }
        return (first + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private static int appColor(String appId) {
        if (appId != null && appId.startsWith("account:")) {
            return 0xFF1D3B83;
        }
        return switch (appId) {
            case "banking" -> 0xFF0F9F6E;
            case "tap" -> 0xFF2D7DFF;
            case "market" -> 0xFFB88722;
            case "calculator" -> 0xFF303846;
            case "paint" -> 0xFFE86BA5;
            case "contacts" -> 0xFF2FBF71;
            case "messenger" -> 0xFF20A7FF;
            case "notes" -> 0xFFF5C64F;
            case "settings" -> 0xFF8A95A8;
            default -> SmartphoneClientState.accentColor();
        };
    }

}
