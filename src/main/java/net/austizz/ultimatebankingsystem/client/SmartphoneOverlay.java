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
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private static final int PHONE_APP_TOP_BLEED = 39;
    private static final int CHAT_COMPOSER_MAX_LINES = 4;
    private static final int CHAT_COMPOSER_WRAP_MAX_LINES = 64;
    private static final int CHAT_MESSAGE_MAX_LINES = 10;
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

    private SmartphoneOverlay() {
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
        int r = 18;
        int bottomBezelH = 54;
        fillRounded(g, x, y, w, h, r, PHONE_BLACK);
        fillRounded(g, x + 3, y + 3, w - 6, h - 6, 16, PHONE_EDGE_BLACK);
        fillRounded(g, x + 6, y + 6, w - 12, h - 12, 13, PHONE_GLASS_BLACK);
        fillVertical(g, x + PHONE_DISPLAY_INSET, y + PHONE_DISPLAY_INSET,
                w - PHONE_DISPLAY_INSET * 2, h - PHONE_DISPLAY_INSET * 2 - bottomBezelH,
                SmartphoneClientState.wallpaperTop(), SmartphoneClientState.wallpaperBottom());
        fillRounded(g, x + PHONE_DISPLAY_INSET, y + h - bottomBezelH - PHONE_DISPLAY_INSET,
                w - PHONE_DISPLAY_INSET * 2, bottomBezelH, 12, PHONE_BLACK);
        g.fill(x + 16, y + h - bottomBezelH - 9, x + w - 16, y + h - bottomBezelH - 8, 0xFF101010);
        fillRounded(g, x + w / 2 - 35, y + 11, 70, 13, 7, PHONE_BLACK);
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
        if (!bankingSurface) {
            drawStatusBar(g, font, sx, y + 16, sw);
            g.drawString(font, "UBS Phone", sx, sy, 0xFFEAF8FF, false);
            String mode = SmartphoneClientState.mode() == SmartphoneClientState.Mode.PASSIVE ? "Press P" : "Live";
            g.drawString(font, mode, x + w - 18 - font.width(mode), sy, 0xFFEAF8FF, false);
        }

        int contentY = bankingSurface ? sy + 24 : sy + 18;
        int contentH = Math.max(60, screenBottom - contentY - 8);
        try {
            if (bankingSurface) {
                fillRounded(g, displayX, displayY, displayW, displayH, 13, BANK_BG);
            }
            if (bankingSurface && SmartphoneClientState.bankingLoadingActive()) {
                drawBankingLoading(g, font, sx, contentY, sw, contentH);
                drawBankStatusToast(g, font, sx, displayY, displayH, sw);
                return;
            }
            switch (SmartphoneClientState.activeApp()) {
                case HOME -> drawHome(g, font, sx, contentY, sw, contentH, hitboxes);
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
                case CALCULATOR -> drawCalculator(g, font, sx, contentY, sw, contentH, hitboxes);
                case PAINT -> drawPaint(g, font, sx, contentY, sw, contentH, hitboxes);
                case CONTACTS -> drawContacts(g, font, sx, contentY, sw, contentH, hitboxes);
                case MESSENGER -> drawMessenger(g, font, sx, contentY, sw, contentH, hitboxes);
                case NOTES -> drawNotes(g, font, sx, contentY, sw, contentH, hitboxes);
                case SETTINGS -> drawSettings(g, font, sx, contentY, sw, contentH, hitboxes);
                case JOURNEYMAP, AUCTION, REAL_ESTATE -> drawBridge(g, font, sx, contentY, sw, contentH);
            }

            drawBankStatusToast(g, font, sx, displayY, displayH, sw);
        } finally {
            maskRoundedRectCorners(g, displayX, displayY, displayW, displayH, 13, PHONE_GLASS_BLACK);
            drawPhoneForegroundBezel(g, x, y, w, h, bottomBezelH, displayX, displayY, displayW, displayH);
        }
    }

    private static void drawPhoneForegroundBezel(GuiGraphics g, int x, int y, int w, int h, int bottomBezelH,
                                                 int displayX, int displayY, int displayW, int displayH) {
        g.fill(x, y, x + w, displayY, PHONE_BLACK);
        g.fill(x, displayY, displayX, displayY + displayH, PHONE_BLACK);
        g.fill(displayX + displayW, displayY, x + w, displayY + displayH, PHONE_BLACK);
        g.fill(x, displayY + displayH, x + w, y + h, PHONE_BLACK);
        maskRoundedRectCorners(g, displayX, displayY, displayW, displayH, 13, PHONE_GLASS_BLACK);
        fillRounded(g, x + w / 2 - 35, y + 11, 70, 13, 7, PHONE_BLACK);
        g.fill(x + 16, y + h - bottomBezelH - 9, x + w - 16, y + h - bottomBezelH - 8, 0xFF101010);
        drawHardwareHomeButton(g, x + w / 2, y + h - bottomBezelH / 2 - 3);
    }

    private static int activeCaretColor(int color) {
        return ((System.currentTimeMillis() / 450L) & 1L) == 0L
                ? color
                : (0x66000000 | (color & 0x00FFFFFF));
    }

    private static void drawBankStatusToast(GuiGraphics g, Font font, int x, int phoneY, int phoneH, int w) {
        String status = SmartphoneClientState.statusMessage();
        if (status == null || status.isBlank()) {
            return;
        }
        g.fill(x, phoneY + phoneH - 64, x + w, phoneY + phoneH - 46, 0xCC06101D);
        drawPhoneText(g, trim(font, status, w - 8), x + 4, phoneY + phoneH - 60,
                SmartphoneClientState.accentColor(), 8.5F);
    }

    private static void drawHome(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 List<SmartphoneClientState.Hitbox> hitboxes) {
        int cols = 4;
        int cell = Math.max(44, (w - 12) / cols);
        int icon = Math.min(40, cell - 8);
        int i = 0;
        for (SmartphoneClientState.PhoneApp app : SmartphoneClientState.apps()) {
            int cx = x + (i % cols) * cell + 4;
            int cy = y + 8 + (i / cols) * 58;
            int color = appColor(app.id());
            fillRounded(g, cx, cy, icon, icon, 9, color);
            g.drawString(font, iconGlyph(app.id()), cx + icon / 2 - 3, cy + 13, 0xFFFFFFFF, false);
            drawCentered(g, font, trim(font, app.label(), cell - 2), cx + icon / 2, cy + icon + 5, 0xFFEAF8FF);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(cx, cy, icon, icon + 18), "APP", app.id(), "", ""));
            i++;
        }
    }

    private static void drawBanking(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
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
                BANK_MUTED, 1.0F);
        drawScaledString(g, font, trim(font, SmartphoneClientState.ownerName(), Math.round(w * 0.56F)),
                x + Math.round(66 * sx), y + Math.round(26 * sy), BANK_TEXT, 1.18F);

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

        int actionsY = y + Math.round(319 * sy);
        drawFigmaHomeAction(g, font, x + Math.round(0 * sx), actionsY, Math.round(54 * sx),
                "Sent", "send", BANK_BLUE, hitboxes, "APP", "send-money");
        drawFigmaHomeAction(g, font, x + Math.round(94 * sx), actionsY, Math.round(54 * sx),
                "Receive", "receive", 0xFFFF7A45, hitboxes, "APP", "request-money");
        drawFigmaHomeAction(g, font, x + Math.round(188 * sx), actionsY, Math.round(54 * sx),
                "Pay Requests", "stats", 0xFF2CCB8E, hitboxes, "APP", "bank-pay-requests");
        drawFigmaHomeAction(g, font, x + Math.round(281 * sx), actionsY, Math.round(54 * sx),
                "Tap to Pay", "tap", 0xFFFFB13D, hitboxes, "APP", "tap");

        int headingY = y + Math.round(415 * sy);
        drawScaledString(g, font, "Transaction", x, headingY, BANK_TEXT, 1.28F);
        String seeAll = "See All";
        int seeAllW = phoneTextWidth(seeAll, 9.0F);
        drawScaledString(g, font, seeAll, x + w - seeAllW, headingY + 2, BANK_BLUE, 1.0F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - seeAllW - 6, headingY - 4, seeAllW + 12, 20),
                "APP", "history", "", ""));

        int navY = bankDisplayNavY(y, h, w);
        int rowY = y + Math.round(454 * sy);
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
        drawScaledString(g, font, bankLabel, x, y + Math.round(1 * sy), BANK_TEXT, 0.88F);

        if (account.primary()) {
            int badgeX = x + Math.min(bankMaxW, phoneTextWidth(bankLabel, 8.0F)) + Math.round(6 * sx);
            fillRounded(g, badgeX, y, primaryW, copyH, Math.max(6, Math.round(8 * sx)), 0x3320C985);
            drawScaledCentered(g, font, "PRIMARY", badgeX + primaryW / 2, y + Math.round(4 * sy), 0xFF20C985, 0.68F);
        }

        fillRounded(g, copyX, copyY, copyW, copyH, Math.max(6, Math.round(8 * sx)), BANK_PANEL_SOFT);
        drawScaledCentered(g, font, "Copy ID", copyX + copyW / 2, copyY + Math.round(4 * sy), BANK_TEXT, 0.74F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(copyX, copyY, copyW, copyH),
                "COPY_SELECTED_ACCOUNT_ID", "", "", ""));

        String idLabel = "ID " + shortUuid(account.id());
        drawScaledString(g, font, idLabel, x, y + Math.round(18 * sy), BANK_MUTED, 0.72F);
    }

    private static void drawBankWelcome(GuiGraphics g, Font font, int x, int y, int w, int h,
                                        List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "home", "", ""));

        int titleY = y + Math.round(94 * sy);
        drawScaledString(g, font, "Sign Up", x, titleY, BANK_TEXT, 1.9F);

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
        drawScaledCentered(g, font, "Already have an account.", x + w / 2 - Math.round(22 * sx), copyY, BANK_MUTED, 0.86F);
        drawScaledString(g, font, "Sign In", x + w / 2 + Math.round(48 * sx), copyY, BANK_BLUE, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w / 2 + Math.round(42 * sx), copyY - 6,
                Math.round(70 * sx), 20), "APP", "bank-sign-in", "", ""));
    }

    private static void drawFigmaSignupStaticField(GuiGraphics g, Font font, int x, int y, int w, float sy,
                                                   String label, String icon, String value) {
        float sx = w / 335.0F;
        drawScaledString(g, font, label, x, y, BANK_MUTED, 0.92F);
        int rowY = y + Math.round(31 * sy);
        drawFigmaProfileIcon(g, icon, x + Math.round(11 * sx), rowY + Math.round(11 * sy), BANK_MUTED);
        drawScaledString(g, font, trim(font, value == null ? "" : value, w - Math.round(58 * sx)),
                x + Math.round(38 * sx), rowY + Math.round(3 * sy), BANK_TEXT, 0.95F);
        g.fill(x, rowY + Math.round(32 * sy), x + w, rowY + Math.round(33 * sy), BANK_LINE);
    }

    private static void drawFigmaSignupPinField(GuiGraphics g, Font font, int x, int y, int w, float sy,
                                                List<SmartphoneClientState.Hitbox> hitboxes) {
        float sx = w / 335.0F;
        drawScaledString(g, font, "Password", x, y, BANK_MUTED, 0.92F);
        int rowY = y + Math.round(31 * sy);
        drawDarkLockGlyph(g, x + Math.round(11 * sx), rowY + Math.round(11 * sy), BANK_MUTED, BANK_BG);
        String pin = SmartphoneClientState.signupPin();
        boolean pinActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.SIGNUP_PIN;
        String dots = passwordDots(pin);
        drawScaledString(g, font, dots.isBlank() ? (pinActive ? "" : "4 digit PIN") : dots,
                x + Math.round(38 * sx), rowY + Math.round(3 * sy), dots.isBlank() ? BANK_MUTED : BANK_TEXT, 0.95F);
        if (pinActive) {
            int caretX = x + Math.round(38 * sx) + (dots.isBlank() ? 0 : Math.min(phoneTextWidth(dots, 8.55F), w - Math.round(58 * sx))) + 2;
            g.fill(caretX, rowY + Math.round(2 * sy), caretX + 1, rowY + Math.round(14 * sy),
                    activeCaretColor(BANK_TEXT));
        }
        drawEyeGlyph(g, x + w - Math.round(13 * sx), rowY + Math.round(11 * sy), BANK_MUTED, BANK_BG);
        g.fill(x, rowY + Math.round(32 * sy), x + w, rowY + Math.round(33 * sy), BANK_LINE);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y + Math.round(22 * sy),
                w, Math.round(48 * sy)), "INPUT", SmartphoneClientState.InputTarget.SIGNUP_PIN.name(), "", ""));
    }

    private static void drawFigmaAvatar(GuiGraphics g, Font font, int x, int y, int size) {
        fillCircle(g, x + size / 2, y + size / 2, size / 2, BANK_PANEL_SOFT);
        ResourceLocation skin = resolveLocalSkin();
        int inset = Math.max(3, Math.round(size * 0.08F));
        if (skin != null) {
            PlayerFaceRenderer.draw(g, skin, x + inset, y + inset, Math.max(1, size - inset * 2));
            maskOutsideCircle(g, x, y, size, x + size / 2, y + size / 2, size / 2, BANK_BG);
        } else {
            fillCircle(g, x + size / 2, y + size / 2, Math.max(1, size / 2 - inset), BANK_BLUE);
            drawScaledCentered(g, font, initials(SmartphoneClientState.ownerName()), x + size / 2,
                    y + Math.round(size * 0.38F), BANK_TEXT, Mth.clamp(size / 38.0F, 0.72F, 1.18F));
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
        fillCircle(g, x + safeSize / 2, y + safeSize / 2, safeSize / 2, BANK_PANEL_SOFT);
        drawFigmaActionIcon(g, icon, x + safeSize / 2, y + safeSize / 2, safeSize, color);
        drawScaledCentered(g, font, label, x + safeSize / 2, y + safeSize + 7, BANK_MUTED, 0.92F);
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
            drawCircleRing(g, centerX, centerY, ring, Math.max(1, Math.round(circleSize * 0.03F)), color, BANK_PANEL_SOFT);
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
        int color = selected ? BANK_BLUE : BANK_MUTED;
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
                        three, color, BANK_PANEL);
                g.fill(centerX - eight, centerY - three, centerX + eight, centerY - one, color);
                g.fill(centerX - seven, centerY + four, centerX - one, centerY + five, color);
            }
            case "stats" -> {
                drawCircleRing(g, centerX - one, centerY, nine, two, color, BANK_PANEL);
                fillCircle(g, centerX - one, centerY, Math.max(4, Math.round(5 * scale)), BANK_PANEL);
                drawSoftLine(g, centerX - one, centerY, centerX - one, centerY - nine, color);
                drawSoftLine(g, centerX - one, centerY, centerX + eight, centerY + four, color);
            }
            case "settings" -> {
                drawCircleRing(g, centerX, centerY, eight, two, color, BANK_PANEL);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
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
        drawScaledCentered(g, font, "Statistics", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, BANK_PANEL_SOFT);
        drawBellGlyph(g, x + w - circle / 2, y + circle / 2, BANK_TEXT, BANK_PANEL_SOFT);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "bank-pay-requests", "", ""));

        drawScaledCentered(g, font, "Current Balance", x + w / 2, y + Math.round(73 * sy), BANK_MUTED, 1.38F);
        drawScaledCentered(g, font, selected == null ? totalBalanceLabel(SmartphoneClientState.accounts()) : selected.shortBalance(),
                x + w / 2, y + Math.round(105 * sy), BANK_TEXT, 1.85F);

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
                drawScaledString(g, font, label, monthX, monthY, BANK_MUTED, 0.98F);
                hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(monthX - 6, monthY - 8, 50, 30),
                        "STATS_MONTH", key, "", ""));
            }
        }

        int headingY = y + Math.round(437 * sy);
        drawScaledString(g, font, "Transaction", x, headingY, BANK_TEXT, 1.28F);
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
            g.fill(gx, y, gx + 1, y + h, BANK_LINE);
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
        fillCircle(g, series.markerX(), series.markerY(), 6, BANK_TEXT);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Transaction History", x + w / 2, y + Math.round(10 * sy),
                BANK_TEXT, 1.22F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, BANK_PANEL_SOFT);
        drawFigmaRefreshGlyph(g, x + w - circle / 2, y + circle / 2, BANK_TEXT, BANK_PANEL_SOFT);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "history", "", ""));

        int headingY = y + Math.round(72 * sy);
        drawScaledString(g, font, "Today", x, headingY, BANK_TEXT, 1.34F);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;

        int circle = Math.max(32, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Search", x + w / 2, y + Math.round(10 * sy), BANK_TEXT, 1.35F);
        drawFigmaHeaderButton(g, HEADER_CLOSE_TEXTURE, x + w - circle, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "banking", "", ""));

        int searchY = y + Math.round(74 * sy);
        int searchH = Math.max(38, Math.round(44 * sy));
        fillRounded(g, x, searchY, w, searchH, Math.max(9, Math.round(10 * sx)), BANK_INPUT);
        int searchGlyph = Math.max(18, Math.round(20 * sx));
        drawSearchGlyph(g, x + Math.round(18 * sx) + searchGlyph / 2, searchY + searchH / 2, BANK_MUTED);
        String query = SmartphoneClientState.historySearch();
        boolean searchActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.HISTORY_SEARCH;
        String searchText = query == null || query.isBlank() ? (searchActive ? "" : "Search") : trim(font, query, w - Math.round(96 * sx));
        drawScaledString(g, font, searchText, x + Math.round(46 * sx), searchY + (searchH - 8) / 2,
                query == null || query.isBlank() ? BANK_MUTED : BANK_TEXT, 1.05F);
        if (searchActive) {
            int caretX = x + Math.round(46 * sx)
                    + (query == null || query.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, query, w - Math.round(96 * sx)), 9.45F), w - Math.round(96 * sx))) + 2;
            g.fill(caretX, searchY + (searchH - 12) / 2, caretX + 1, searchY + (searchH + 12) / 2,
                    activeCaretColor(BANK_TEXT));
        }
        drawFigmaCloseGlyph(g, x + w - Math.round(28 * sx), searchY + searchH / 2, BANK_MUTED, sx * 0.82F);
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
        List<SmartphoneClientState.PhoneTransaction> rows = txs == null ? List.of() : txs.stream()
                .sorted(Comparator.comparingLong(SmartphoneClientState.PhoneTransaction::timestampMillis).reversed())
                .toList();
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
        fillRounded(g, x, y, w, 86, 15, BANK_PANEL_SOFT);
        drawScaledCentered(g, font, title, x + w / 2, y + 24, BANK_TEXT, 1.05F);
        drawScaledCentered(g, font, detail, x + w / 2, y + 46, BANK_MUTED, 0.85F);
    }

    private static void drawFigmaSearchRow(GuiGraphics g, Font font, int x, int y, int w,
                                           String title, String subtitle, String amount, String icon,
                                           boolean positiveAmountBlue) {
        int iconSize = Math.max(36, Math.round(w * 42.0F / 335.0F));
        fillCircle(g, x + iconSize / 2, y + iconSize / 2, iconSize / 2, BANK_PANEL_SOFT);
        drawFigmaSearchIcon(g, font, icon, x + iconSize / 2, y + iconSize / 2);
        String rawValue = amount == null || amount.isBlank() ? "$0" : amount;
        String value = figmaAmountLabel(rawValue);
        float amountScale = 1.35F;
        int amountW = phoneTextWidth(value, font.lineHeight * amountScale);
        int amountColor = !rawValue.contains("-") && positiveAmountBlue ? BANK_BLUE : BANK_TEXT;
        int textX = x + Math.round(w * 58.0F / 335.0F);
        int textMax = Math.max(48, w - (textX - x) - amountW - Math.round(w * 18.0F / 335.0F));
        drawScaledString(g, font, trim(font, title == null || title.isBlank() ? "Transaction" : title,
                Math.round(textMax / 1.32F)), textX, y + 4, BANK_TEXT, 1.32F);
        drawScaledString(g, font, trim(font, subtitle == null || subtitle.isBlank() ? "Transaction" : subtitle,
                textMax), textX, y + 25, BANK_MUTED, 1.0F);
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
        fillCircle(g, centerX - 2, centerY - 2, 3, BANK_PANEL_SOFT);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "history", "", ""));
        drawScaledCentered(g, font, "Transaction", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

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
                rawAmount.contains("-") ? BANK_TEXT : BANK_BLUE, 1.78F);
        drawScaledCentered(g, font, transactionTitle(tx), x + w / 2, heroY + Math.round(108 * sy),
                BANK_MUTED, 0.96F);

        int detailY = y + Math.round(246 * sy);
        int availableDetailH = Math.max(180, y + h - detailY - 8);
        int detailH = Math.min(availableDetailH, Math.max(360, Math.round(430 * sy)));
        fillRounded(g, x, detailY, w, detailH, Math.max(16, Math.round(18 * sx)), BANK_PANEL_SOFT);
        drawRoundedBorder(g, x, detailY, w, detailH, Math.max(16, Math.round(18 * sx)), BANK_LINE, BANK_PANEL_SOFT);
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
        drawScaledString(g, font, label, x, y, BANK_MUTED, 0.88F);
        drawScaledString(g, font, trim(font, value == null ? "" : value, w), x, y + 16, BANK_TEXT, 0.94F);
        g.fill(x, y + 34, x + w, y + 35, BANK_LINE);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-profile", "", ""));
        drawScaledCentered(g, font, "Pay Requests", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.3F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, BANK_PANEL_SOFT);
        drawFigmaRefreshGlyph(g, x + w - circle / 2, y + circle / 2, BANK_TEXT, BANK_PANEL_SOFT);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "PAY_REQUESTS_REFRESH", "", "", ""));

        int summaryY = y + Math.round(74 * sy);
        int summaryH = Math.max(92, Math.round(108 * sy));
        fillRounded(g, x, summaryY, w, summaryH, Math.max(14, Math.round(18 * sx)), BANK_PANEL_SOFT);
        fillCircle(g, x + Math.round(34 * sx), summaryY + Math.round(34 * sy), Math.max(19, Math.round(22 * sx)), BANK_INPUT);
        drawFigmaActionIcon(g, "receive", x + Math.round(34 * sx), summaryY + Math.round(34 * sy), BANK_BLUE);
        drawScaledString(g, font, SmartphoneClientState.payRequests().size() + " Pending",
                x + Math.round(66 * sx), summaryY + Math.round(18 * sy), BANK_TEXT, 1.12F);
        drawScaledString(g, font, trim(font, "Primary: " + SmartphoneClientState.payRequestsPrimaryLabel(), w - Math.round(86 * sx)),
                x + Math.round(66 * sx), summaryY + Math.round(40 * sy), BANK_MUTED, 0.9F);
        drawScaledString(g, font, "Approve or decline requests securely.",
                x + Math.round(66 * sx), summaryY + Math.round(61 * sy), BANK_MUTED, 0.86F);

        int rowY = summaryY + summaryH + Math.round(30 * sy);
        drawScaledString(g, font, "Pending Requests", x, rowY, BANK_TEXT, 1.16F);
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
            fillRounded(g, x, rowY, w, emptyH, Math.max(14, Math.round(18 * sx)), BANK_PANEL_SOFT);
            fillCircle(g, x + w / 2, rowY + Math.round(34 * sy), Math.max(19, Math.round(22 * sx)), BANK_INPUT);
            drawFigmaActionIcon(g, "receive", x + w / 2, rowY + Math.round(34 * sy), 0xFF20C985);
            drawScaledCentered(g, font, "No pending pay requests", x + w / 2, rowY + Math.round(68 * sy), BANK_TEXT, 1.02F);
            drawScaledCentered(g, font, "Refresh or create a new request.", x + w / 2, rowY + Math.round(88 * sy), BANK_MUTED, 0.9F);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, title, x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

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
        fillRounded(g, x, peopleY, w, peopleH, Math.max(12, Math.round(14 * sx)), BANK_PANEL_SOFT);
        drawRoundedBorder(g, x, peopleY, w, peopleH, Math.max(12, Math.round(14 * sx)), BANK_LINE, BANK_PANEL_SOFT);
        drawScaledString(g, font, requestFlow ? "Request from" : "Send to",
                x + Math.round(12 * sx), peopleY + Math.round(15 * sy), BANK_TEXT, 1.0F);
        drawPaymentContacts(g, font, x + Math.round(12 * sx), peopleY + Math.round(46 * sy),
                w - Math.round(24 * sx), requestFlow, hitboxes);
        boolean targetActive = SmartphoneClientState.inputTarget() == targetInput;
        String targetShown = targetValue == null || targetValue.isBlank() ? (targetActive ? "" : targetLabel) : targetValue;
        int targetLineY = peopleY + peopleH - Math.round(18 * sy);
        drawScaledString(g, font, trim(font, targetShown, w - Math.round(32 * sx)),
                x + Math.round(16 * sx), targetLineY, targetValue == null || targetValue.isBlank() ? BANK_MUTED : BANK_TEXT, 0.82F);
        if (targetActive) {
            int caretX = x + Math.round(16 * sx) + (targetValue == null || targetValue.isBlank()
                    ? 0 : Math.min(phoneTextWidth(trim(font, targetValue, w - Math.round(32 * sx)), 7.4F), w - Math.round(32 * sx)));
            g.fill(caretX + 2, targetLineY - 1, caretX + 3, targetLineY + 10, activeCaretColor(BANK_TEXT));
        }
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, targetLineY - 8, w, 26),
                "INPUT", targetInput.name(), "", ""));

        int amountY = y + Math.round(462 * sy);
        int amountH = Math.max(94, Math.round(116 * sy));
        fillRounded(g, x, amountY, w, amountH, Math.max(12, Math.round(14 * sx)), BANK_PANEL_SOFT);
        drawRoundedBorder(g, x, amountY, w, amountH, Math.max(12, Math.round(14 * sx)), BANK_LINE, BANK_PANEL_SOFT);
        drawScaledString(g, font, requestFlow ? "Enter Request Amount" : "Enter Your Amount",
                x + Math.round(16 * sx), amountY + Math.round(29 * sy), BANK_MUTED, 0.88F);
        String balanceLabel = account == null ? "" : "Balance " + account.shortBalance();
        if (!balanceLabel.isBlank()) {
            int balanceMax = Math.round(128 * sx);
            String balanceShown = trim(font, balanceLabel, balanceMax);
            int balanceW = Math.min(phoneTextWidth(balanceShown, 7.92F), balanceMax);
            drawScaledString(g, font, balanceShown, x + w - balanceW,
                    amountY + Math.round(29 * sy), BANK_MUTED, 0.88F);
        }
        drawScaledString(g, font, "USD", x + Math.round(16 * sx),
                amountY + Math.round(61 * sy), 0xFF9BB2D4, 1.65F);
        boolean amountActive = SmartphoneClientState.inputTarget() == amountInput;
        String amountShown = amountValue == null || amountValue.isBlank() ? (amountActive ? "" : "0.00") : amountValue.replace("$", "");
        drawScaledString(g, font, trim(font, amountShown, Math.round(w * 0.44F)),
                x + Math.round(81 * sx), amountY + Math.round(61 * sy), BANK_TEXT, 1.65F);
        if (amountActive) {
            int maxTextW = Math.round(w * 0.44F);
            int caretX = x + Math.round(81 * sx)
                    + (amountValue == null || amountValue.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, amountShown, maxTextW), 14.85F), maxTextW)) + 2;
            g.fill(caretX, amountY + Math.round(60 * sy), caretX + 1, amountY + Math.round(78 * sy),
                    activeCaretColor(BANK_TEXT));
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
        fillRounded(g, modalX, modalY, modalW, modalH, Math.max(18, Math.round(20 * sx)), BANK_PANEL_SOFT);
        drawRoundedBorder(g, modalX, modalY, modalW, modalH, Math.max(18, Math.round(20 * sx)), BANK_LINE, BANK_PANEL_SOFT);
        drawScaledString(g, font, "Add recipient", modalX + Math.round(18 * sx), modalY + Math.round(20 * sy),
                BANK_TEXT, 1.18F);
        drawScaledString(g, font, SmartphoneClientState.paymentTargetModalRequest()
                        ? "Use an online player name with a primary account."
                        : "Use an online player name or a bank account ID.",
                modalX + Math.round(18 * sx), modalY + Math.round(47 * sy), BANK_MUTED, 0.82F);
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
                "Cancel", BANK_PANEL, hitboxes, "CLOSE_PAYMENT_TARGET_MODAL", "", "", "");
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
        drawScaledCentered(g, font, "Add", addCx, y + avatar + 7, BANK_TEXT, 0.78F);
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
            drawScaledCentered(g, font, trim(font, label, Math.max(42, Math.round(step - 4))), cx, y + avatar + 7, BANK_TEXT, 0.78F);
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
            fillCircle(g, arrowX, arrowY, Math.max(9, Math.round(10 * iconScale)), BANK_PANEL);
            drawFigmaChevronLeft(g, arrowX, arrowY, Math.max(4, Math.round(5 * iconScale)), BANK_TEXT);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(arrowX - 13, arrowY - 13, 26, 26),
                    "PAYMENT_CONTACTS_PREV", "", "", ""));
        }
        if (SmartphoneClientState.paymentContactOffset() + visible < allRecipientCount) {
            int arrowX = x + w - Math.max(8, Math.round(10 * iconScale));
            int arrowY = y + avatar / 2;
            fillCircle(g, arrowX, arrowY, Math.max(9, Math.round(10 * iconScale)), BANK_PANEL);
            drawFigmaChevronRight(g, arrowX, arrowY, Math.max(4, Math.round(5 * iconScale)), BANK_TEXT);
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(arrowX - 13, arrowY - 13, 26, 26),
                    "PAYMENT_CONTACTS_NEXT", "", "", ""));
        }
        if (allRecipientCount <= 0) {
            int emptyX = x + avatar + Math.max(12, Math.round(14 * iconScale));
            drawScaledString(g, font, "No players with primary accounts online", emptyX,
                    y + Math.round(18 * iconScale), BANK_MUTED, 0.78F);
        }
    }

    private static void drawPaymentRecipientAvatar(GuiGraphics g, Font font,
                                                   SmartphoneClientState.PaymentRecipient recipient,
                                                   int cx, int cy, int avatar) {
        String type = recipient == null || recipient.type() == null ? "" : recipient.type().toLowerCase(Locale.ROOT);
        fillCircle(g, cx, cy, avatar / 2, BANK_INPUT);
        if ("player".equals(type) && recipient != null && recipient.online()) {
            ResourceLocation skin = resolveSkin(recipient.id());
            if (skin != null) {
                int inset = Math.max(3, Math.round(avatar * 0.09F));
                int left = cx - avatar / 2;
                int top = cy - avatar / 2;
                PlayerFaceRenderer.draw(g, skin, left + inset, top + inset, Math.max(1, avatar - inset * 2));
                maskOutsideCircle(g, left, top, avatar, cx, cy, avatar / 2, BANK_INPUT);
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
        fillRounded(g, x, y, w, 82, 18, BANK_PANEL_SOFT);
        drawRoundedBorder(g, x, y, w, 82, 18, BANK_LINE, BANK_PANEL_SOFT);
        fillCircle(g, x + 27, y + 28, 18, BANK_INPUT);
        fillCircle(g, x + 27, y + 28, 14, BANK_BLUE);
        drawCentered(g, font, initials(request.requesterName()), x + 27, y + 24, 0xFFFFFFFF);
        drawScaledString(g, font, trim(font, request.requesterName(), w - 128), x + 54, y + 11, BANK_TEXT, 0.94F);
        drawScaledString(g, font, trim(font, "Requested " + request.createdAt(), w - 128), x + 54, y + 28,
                BANK_MUTED, 0.78F);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Settings", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);
        drawFigmaHeaderButton(g, SETTINGS_LOGOUT_TEXTURE, x + w - circle, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "bank-sign-in", "", ""));

        int rowY = y + Math.round(73 * sy);
        drawScaledString(g, font, "General", x, rowY, BANK_MUTED, 1.0F);
        rowY = y + Math.round(120 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Language", "",
                "APP", "bank-language", hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "My Profile", "",
                "APP", "bank-profile", hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Contact Us", "",
                "APP", "messenger", hitboxes);

        rowY = y + Math.round(297 * sy);
        drawScaledString(g, font, "Security", x, rowY, BANK_MUTED, 1.0F);
        rowY = y + Math.round(344 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Change Password", "",
                "APP", "bank-change-password", hitboxes);
        rowY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, rowY, w, "Privacy Policy", "",
                "APP", "bank-terms", hitboxes);

        rowY += Math.round(56 * sy);
        drawWrapped(g, font,
                "Choose what data you share with UBS mobile banking.",
                x, rowY + Math.round(2 * sy), w - Math.round(20 * sx), BANK_MUTED, 2);
        rowY += Math.round(58 * sy);
        drawFigmaSettingsToggleRow(g, font, x, rowY, w, "Biometric", true);

        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "settings", hitboxes);
    }

    private static void drawBankStaff(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Bank Staff", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

        SmartphoneClientState.OwnedBank bank = SmartphoneClientState.ownedBank();
        if (bank == null) {
            drawEmptyState(g, font, x, y + Math.round(116 * sy), w,
                    "No owned bank", "Only bank owners can manage staff here.");
            return;
        }

        int summaryY = y + Math.round(72 * sy);
        int summaryH = Math.max(90, Math.round(104 * sy));
        fillRounded(g, x, summaryY, w, summaryH, Math.max(16, Math.round(18 * sx)), BANK_PANEL_SOFT);
        drawRoundedBorder(g, x, summaryY, w, summaryH, Math.max(16, Math.round(18 * sx)), BANK_LINE, BANK_PANEL_SOFT);
        fillCircle(g, x + Math.round(34 * sx), summaryY + Math.round(34 * sy), Math.max(20, Math.round(23 * sx)), BANK_INPUT);
        drawFigmaBottomIcon(g, "settings", x + Math.round(34 * sx), summaryY + Math.round(34 * sy), BANK_BLUE, 1.0F);
        drawScaledString(g, font, trim(font, bank.name(), w - Math.round(86 * sx)),
                x + Math.round(68 * sx), summaryY + Math.round(18 * sy), BANK_TEXT, 1.08F);
        drawScaledString(g, font, bank.status() + " | " + bank.employeeCount() + " staff",
                x + Math.round(68 * sx), summaryY + Math.round(43 * sy), BANK_MUTED, 0.88F);
        drawScaledString(g, font, trim(font, "Hire or fire staff for this bank.", w - Math.round(86 * sx)),
                x + Math.round(68 * sx), summaryY + Math.round(64 * sy), BANK_MUTED, 0.82F);

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
        drawScaledString(g, font, "Current Staff", x, rosterY, BANK_TEXT, 1.05F);
        int rowY = rosterY + Math.round(25 * sy);
        int rowH = Math.max(42, Math.round(48 * sy));
        int maxRows = Math.min(staff.size(), 2);
        if (staff.isEmpty()) {
            fillRounded(g, x, rowY, w, rowH, Math.max(12, Math.round(14 * sx)), BANK_PANEL_SOFT);
            drawRoundedBorder(g, x, rowY, w, rowH, Math.max(12, Math.round(14 * sx)), BANK_LINE, BANK_PANEL_SOFT);
            drawScaledString(g, font, "No active staff yet", x + Math.round(14 * sx), rowY + Math.round(10 * sy),
                    BANK_TEXT, 0.95F);
            drawScaledString(g, font, "Hire someone above to fill this roster.", x + Math.round(14 * sx),
                    rowY + Math.round(27 * sy), BANK_MUTED, 0.78F);
            rowY += rowH + Math.round(8 * sy);
        } else {
            for (int i = 0; i < maxRows; i++) {
                drawBankStaffRow(g, font, x, rowY, w, rowH, staff.get(i), hitboxes);
                rowY += rowH + Math.round(8 * sy);
            }
            if (staff.size() > maxRows) {
                drawScaledString(g, font, "+" + (staff.size() - maxRows) + " more staff member(s)",
                        x + Math.round(14 * sx), rowY, BANK_MUTED, 0.78F);
                rowY += Math.round(18 * sy);
            }
        }

        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "settings", hitboxes);
    }

    private static void drawBankDissolve(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Dissolve Bank", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

        SmartphoneClientState.OwnedBank bank = SmartphoneClientState.ownedBank();
        if (bank == null) {
            drawEmptyState(g, font, x, y + Math.round(116 * sy), w,
                    "No owned bank", "Only bank owners can dissolve a bank here.");
            return;
        }

        int summaryY = y + Math.round(86 * sy);
        int summaryH = Math.max(142, Math.round(168 * sy));
        fillRounded(g, x, summaryY, w, summaryH, Math.max(18, Math.round(20 * sx)), BANK_PANEL_SOFT);
        drawRoundedBorder(g, x, summaryY, w, summaryH, Math.max(18, Math.round(20 * sx)), BANK_LINE, BANK_PANEL_SOFT);
        fillCircle(g, x + Math.round(40 * sx), summaryY + Math.round(40 * sy), Math.max(24, Math.round(28 * sx)), BANK_INPUT);
        drawFigmaBottomIcon(g, "settings", x + Math.round(40 * sx), summaryY + Math.round(40 * sy), BANK_DANGER, 1.0F);
        drawScaledString(g, font, trim(font, bank.name(), w - Math.round(92 * sx)),
                x + Math.round(82 * sx), summaryY + Math.round(21 * sy), BANK_TEXT, 1.12F);
        drawScaledString(g, font, bank.status() + " | " + bank.employeeCount() + " staff",
                x + Math.round(82 * sx), summaryY + Math.round(47 * sy), BANK_MUTED, 0.88F);
        drawWrapped(g, font,
                "This moves positive account balances into Central Bank checking accounts, clears employees, and removes the owned bank.",
                x + Math.round(18 * sx), summaryY + Math.round(86 * sy), w - Math.round(36 * sx), BANK_MUTED, 4);

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
        fillRounded(g, x, y, w, h, 12, BANK_PANEL_SOFT);
        drawRoundedBorder(g, x, y, w, h, 12, BANK_LINE, BANK_PANEL_SOFT);
        int avatar = Math.max(28, Math.min(36, h - 10));
        int cx = x + 10 + avatar / 2;
        int cy = y + h / 2;
        fillCircle(g, cx, cy, avatar / 2, BANK_INPUT);
        fillCircle(g, cx, cy, Math.max(avatar / 2 - 4, 1), BANK_BLUE);
        drawCentered(g, font, initials(member == null ? "" : member.name()), cx, cy - 4, BANK_TEXT);
        int textX = x + 18 + avatar;
        int actionW = Math.max(46, Math.round(w * 0.18F));
        String name = member == null ? "Staff" : member.name();
        String role = member == null ? "STAFF" : member.role();
        String salary = member == null ? "$0" : member.salary();
        drawScaledString(g, font, trim(font, name, w - actionW * 2 - avatar - 32), textX, y + 7, BANK_TEXT, 0.92F);
        drawScaledString(g, font, trim(font, role + " | " + salary, w - actionW * 2 - avatar - 32),
                textX, y + 23, BANK_MUTED, 0.76F);
        int fireX = x + w - actionW - 8;
        mobileButton(g, font, fireX, y + (h - 22) / 2, actionW, 22, "Fire", 0xFFFF6A6A,
                hitboxes, "BANK_FIRE_MEMBER", member == null ? "" : member.id().toString(), "", "");
        int useX = fireX - actionW - 7;
        mobileButton(g, font, useX, y + (h - 22) / 2, actionW, 22, "Select", BANK_PANEL,
                hitboxes, "BANK_STAFF_SELECT", member == null ? "" : member.id().toString(), "", "");
    }

    private static void drawFigmaSettingsRow(GuiGraphics g, Font font, int x, int y, int w,
                                             String title, String value, String action, String p1,
                                             List<SmartphoneClientState.Hitbox> hitboxes) {
        drawScaledString(g, font, title, x, y + 4, BANK_TEXT, 1.02F);
        if (value != null && !value.isBlank()) {
            int valueW = phoneTextWidth(value, font.lineHeight * 0.98F);
            drawScaledString(g, font, value, x + w - valueW - 40, y + 4, BANK_MUTED, 0.98F);
        }
        drawFigmaArrowRight(g, x + w - 12, y + 12, Math.max(18, Math.round(24 * (w / 335.0F))), BANK_MUTED);
        g.fill(x, y + 34, x + w, y + 35, BANK_LINE);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - 8, w, 48),
                action, p1, "", ""));
    }

    private static void drawFigmaSettingsToggleRow(GuiGraphics g, Font font, int x, int y, int w,
                                                   String title, boolean enabled) {
        drawScaledString(g, font, title, x, y + 4, BANK_TEXT, 1.02F);
        drawFigmaToggle(g, x + w - 24, y + 13, enabled);
        g.fill(x, y + 34, x + w, y + 35, BANK_LINE);
    }

    private static void drawBankInputRow(GuiGraphics g, Font font, int x, int y, int w,
                                         String label, String value, String placeholder,
                                         SmartphoneClientState.InputTarget target,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        boolean active = SmartphoneClientState.inputTarget() == target;
        drawScaledString(g, font, label, x, y, BANK_MUTED, 0.92F);
        int rowY = y + 24;
        int rowH = 42;
        fillRounded(g, x, rowY, w, rowH, 12, active ? BANK_INPUT_ACTIVE : BANK_INPUT);
        drawRoundedBorder(g, x, rowY, w, rowH, 12, active ? BANK_BLUE : BANK_LINE,
                active ? BANK_INPUT_ACTIVE : BANK_INPUT);
        String actual = value == null ? "" : value;
        String shown = actual.isBlank() ? (active ? "" : placeholder) : actual;
        drawScaledString(g, font, trim(font, shown, w - 26), x + 13, rowY + 14,
                actual.isBlank() ? BANK_MUTED : BANK_TEXT, 0.98F);
        if (active) {
            int caretX = x + 13 + (actual.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, actual, w - 26), 8.82F), w - 26)) + 2;
            g.fill(caretX, rowY + 13, caretX + 1, rowY + 28, activeCaretColor(BANK_TEXT));
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
        int bg = enabled ? BANK_BLUE : BANK_MUTED;
        fillRounded(g, centerX - 18, centerY - 10, 36, 20, 10, bg);
        fillCircle(g, centerX + (enabled ? 8 : -8), centerY, 8, 0xFFFFFFFF);
    }

    private static void drawBankProfile(GuiGraphics g, Font font, int x, int y, int w, int h,
                                        List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Profile", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, BANK_PANEL_SOFT);
        drawFigmaProfileIcon(g, "edit", x + w - circle / 2, y + circle / 2, BANK_TEXT);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w - circle, y, circle, circle),
                "APP", "bank-edit-profile", "", ""));

        int avatar = Math.max(56, Math.round(70 * sx));
        int profileY = y + Math.round(74 * sy);
        drawFigmaAvatar(g, font, x, profileY, avatar);
        drawScaledString(g, font, trim(font, SmartphoneClientState.ownerName(), Math.round(w * 0.58F)),
                x + Math.round(92 * sx), profileY + Math.round(14 * sy), BANK_TEXT, 1.24F);
        String role = selected == null || selected.role() == null || selected.role().isBlank()
                ? "Account owner" : selected.role();
        drawScaledString(g, font, trim(font, role, Math.round(w * 0.55F)),
                x + Math.round(92 * sx), profileY + Math.round(43 * sy), BANK_MUTED, 0.95F);

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
        drawFigmaProfileIcon(g, icon, x + 11, y + 11, BANK_MUTED);
        drawScaledString(g, font, label, x + Math.round(w * 38.0F / 335.0F), y + 4, BANK_TEXT, 1.03F);
        if (badge) {
            fillCircle(g, x + w - 14, y + 11, 9, 0xFFED1B3A);
            drawCentered(g, font, "2", x + w - 14, y + 7, 0xFFFFFFFF);
        } else {
            drawFigmaArrowRight(g, x + w - 12, y + 12, Math.max(18, Math.round(24 * (w / 335.0F))));
        }
        g.fill(x, y + 34, x + w, y + 35, BANK_LINE);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - 8, w, 48),
                action, p1, "", ""));
    }

    private static void drawFigmaArrowRight(GuiGraphics g, int centerX, int centerY, int size) {
        drawFigmaArrowRight(g, centerX, centerY, size, BANK_MUTED);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-profile", "", ""));
        drawScaledCentered(g, font, "Edit Profile", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

        int avatar = Math.max(72, Math.round(90 * sx));
        int avatarX = x + w / 2 - avatar / 2;
        int avatarY = y + Math.round(74 * sy);
        drawFigmaAvatar(g, font, avatarX, avatarY, avatar);
        drawScaledCentered(g, font, trim(font, SmartphoneClientState.profileNameDraft(), Math.round(w * 0.64F)),
                x + w / 2, y + Math.round(185 * sy), BANK_TEXT, 1.28F);
        String role = selected == null || selected.role() == null || selected.role().isBlank()
                ? "Account owner" : selected.role();
        drawScaledCentered(g, font, trim(font, role, Math.round(w * 0.54F)),
                x + w / 2, y + Math.round(214 * sy), BANK_MUTED, 0.95F);

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
        drawScaledString(g, font, label, x, y, BANK_MUTED, 1.0F);
        int rowY = y + 31;
        drawFigmaProfileIcon(g, icon, x + 11, rowY + 11, BANK_MUTED);
        String shown = value == null ? "" : value;
        boolean active = SmartphoneClientState.inputTarget() == inputTarget && inputTarget != SmartphoneClientState.InputTarget.NONE;
        String display = shown.isBlank() && inputTarget != SmartphoneClientState.InputTarget.NONE && !active ? "Type here" : shown;
        drawScaledString(g, font, trim(font, display, w - 58),
                x + Math.round(w * 38.0F / 335.0F), rowY + 3,
                shown.isBlank() ? BANK_MUTED : BANK_TEXT, 1.02F);
        if (active) {
            int caretX = x + Math.round(w * 38.0F / 335.0F)
                    + (shown.isBlank() ? 0 : Math.min(phoneTextWidth(trim(font, shown, w - 58), 9.18F), w - 58)) + 2;
            g.fill(caretX, rowY + 2, caretX + 1, rowY + 13, activeCaretColor(BANK_TEXT));
        }
        g.fill(x, rowY + 32, x + w, rowY + 33, BANK_LINE);
        if (inputTarget != SmartphoneClientState.InputTarget.NONE) {
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, rowY - 7, w, 46),
                    "INPUT", inputTarget.name(), "", ""));
            hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, rowY + 33, w, 18),
                    "SAVE_BANK_PROFILE", "", "", ""));
        }
    }

    private static void drawFigmaDateValue(GuiGraphics g, Font font, int x, int y, int w, String value) {
        drawScaledCentered(g, font, value, x + w / 2, y, BANK_TEXT, 1.0F);
        g.fill(x, y + 25, x + w, y + 26, BANK_LINE);
    }

    private static void drawBankSignIn(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, 0xFFFFFFFF, BANK_BG);
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.signInAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        fillCircle(g, x + circle / 2, y + circle / 2, circle / 2, BANK_PANEL_SOFT);
        drawFigmaChevronLeft(g, x + circle / 2, y + circle / 2, Math.max(7, Math.round(8 * sx)), 0xFFFFFFFF);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));

        int titleY = y + Math.round(95 * sy);
        drawScaledString(g, font, "Sign In", x, titleY, 0xFFFFFFFF, 1.9F);

        int emailY = y + Math.round(167 * sy);
        drawScaledString(g, font, "Email Address", x, emailY, BANK_MUTED, 0.92F);
        int emailRowY = emailY + Math.round(31 * sy);
        drawDarkMailGlyph(g, x + Math.round(11 * sx), emailRowY + Math.round(11 * sy), BANK_MUTED);
        String accountLabel = account == null ? "No account selected" : account.appTitle();
        drawScaledString(g, font, trim(font, accountLabel, w - Math.round(58 * sx)),
                x + Math.round(38 * sx), emailRowY + Math.round(3 * sy), 0xFFFFFFFF, 0.95F);
        g.fill(x, emailRowY + Math.round(32 * sy), x + w, emailRowY + Math.round(33 * sy), BANK_LINE);

        int passwordY = y + Math.round(251 * sy);
        drawScaledString(g, font, "Password", x, passwordY, BANK_MUTED, 0.92F);
        int passRowY = passwordY + Math.round(31 * sy);
        drawDarkLockGlyph(g, x + Math.round(11 * sx), passRowY + Math.round(11 * sy), BANK_MUTED, BANK_BG);
        String pinDots = passwordDots(SmartphoneClientState.bankPin());
        boolean pinInputActive = SmartphoneClientState.inputTarget() == SmartphoneClientState.InputTarget.BANK_PIN;
        drawScaledString(g, font, pinDots.isBlank() ? (pinInputActive ? "" : "Account PIN") : pinDots,
                x + Math.round(38 * sx), passRowY + Math.round(3 * sy), pinDots.isBlank() ? BANK_MUTED : 0xFFFFFFFF, 0.95F);
        if (pinInputActive) {
            int caretX = x + Math.round(38 * sx) + (pinDots.isBlank() ? 0 : Math.min(phoneTextWidth(pinDots, 8.55F), w - Math.round(58 * sx))) + 2;
            g.fill(caretX, passRowY + Math.round(2 * sy), caretX + 1, passRowY + Math.round(14 * sy),
                    activeCaretColor(0xFFFFFFFF));
        }
        drawEyeGlyph(g, x + w - Math.round(13 * sx), passRowY + Math.round(11 * sy), BANK_MUTED, BANK_BG);
        g.fill(x, passRowY + Math.round(32 * sy), x + w, passRowY + Math.round(33 * sy), BANK_LINE);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, passwordY - Math.round(8 * sy),
                w, Math.round(58 * sy)), "INPUT", SmartphoneClientState.InputTarget.BANK_PIN.name(), "", ""));

        int buttonY = y + Math.round(354 * sy);
        int buttonH = Math.max(46, Math.round(56 * sy));
        figmaPrimaryButton(g, font, x, buttonY, w, buttonH, "Sign In", BANK_BLUE,
                hitboxes, "BANK_SIGN_IN", "", "", "");

        int copyY = y + Math.round(438 * sy);
        drawScaledCentered(g, font, "I'm a new user.", x + w / 2 - Math.round(28 * sx), copyY, BANK_MUTED, 0.86F);
        drawScaledString(g, font, "Sign Up", x + w / 2 + Math.round(16 * sx), copyY, BANK_BLUE, 0.86F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x + w / 2 + Math.round(10 * sx), copyY - 6,
                Math.round(70 * sx), 20), "APP", "bank-welcome", "", ""));
    }

    private static void drawBankLanguage(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Language", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

        int searchY = y + Math.round(74 * sy);
        int searchH = Math.max(38, Math.round(44 * sy));
        fillRounded(g, x, searchY, w, searchH, Math.max(9, Math.round(10 * sx)), BANK_INPUT);
        int searchGlyph = Math.max(18, Math.round(20 * sx));
        drawSearchGlyph(g, x + Math.round(12 * sx) + searchGlyph / 2, searchY + searchH / 2, BANK_MUTED);
        drawScaledString(g, font, "Search Language", x + Math.round(40 * sx),
                searchY + (searchH - 9) / 2, BANK_MUTED, 1.0F);

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
                BANK_TEXT, 1.15F);
        if (language.equalsIgnoreCase(SmartphoneClientState.bankLanguage())) {
            fillCircle(g, x + w - 10, y + flagR, 10, BANK_BLUE);
            drawFigmaCheckGlyph(g, x + w - 10, y + flagR, 0xFFFFFFFF);
        }
        g.fill(x, y + flagR * 2 + 10, x + w, y + flagR * 2 + 11, BANK_LINE);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.selectedAccount();
        boolean setup = account != null && !account.pinSet();
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", setup ? "home" : "bank-settings", "", ""));
        drawScaledCentered(g, font, setup ? "Set Password" : "Change Password",
                x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

        int fieldY = y + Math.round((setup ? 126 : 73) * sy);
        if (!setup) {
            drawFigmaPasswordField(g, font, x, fieldY, w, "Current Password", SmartphoneClientState.currentPin(),
                    SmartphoneClientState.InputTarget.CURRENT_PIN, hitboxes, false, sy);
            fieldY += Math.round(84 * sy);
        } else {
            drawScaledCentered(g, font, trim(font, "Set a 4 digit password for " + account.appTitle() + ".", w - Math.round(24 * sx)),
                    x + w / 2, y + Math.round(80 * sy), BANK_MUTED, 0.9F);
        }
        drawFigmaPasswordField(g, font, x, fieldY, w, "New Password", SmartphoneClientState.newPin(),
                SmartphoneClientState.InputTarget.NEW_PIN, hitboxes, true, sy);
        fieldY += Math.round(84 * sy);
        drawFigmaPasswordField(g, font, x, fieldY, w, "Confirm New Password", SmartphoneClientState.confirmPin(),
                SmartphoneClientState.InputTarget.CONFIRM_PIN, hitboxes, false, sy);
        drawScaledString(g, font, "Both passwords must match", x, fieldY + Math.round(72 * sy), BANK_MUTED, 0.88F);

        int buttonY = y + Math.round((setup ? 354 : 366) * sy);
        int buttonH = Math.max(46, Math.round(56 * sy));
        figmaPrimaryButton(g, font, x, buttonY, w, buttonH, setup ? "Set Password" : "Change Password", BANK_BLUE,
                hitboxes, "CHANGE_BANK_PIN", "", "", "");
    }

    private static void drawFigmaPasswordField(GuiGraphics g, Font font, int x, int y, int w,
                                               String label, String value, SmartphoneClientState.InputTarget target,
                                               List<SmartphoneClientState.Hitbox> hitboxes, boolean eye, float sy) {
        float sx = w / 335.0F;
        drawScaledString(g, font, label, x, y, BANK_MUTED, 1.0F);
        int rowY = y + Math.round(31 * sy);
        int iconCenter = Math.round(11 * sx);
        drawFigmaProfileIcon(g, "lock", x + iconCenter, rowY + iconCenter, BANK_MUTED);
        String dots = passwordDots(value);
        boolean active = SmartphoneClientState.inputTarget() == target;
        drawScaledString(g, font, dots.isBlank() ? (active ? "" : "4 digit password") : dots, x + Math.round(38 * sx),
                rowY + Math.round(3 * sy), dots.isBlank() ? BANK_MUTED : BANK_TEXT, 1.1F);
        if (active) {
            int caretX = x + Math.round(38 * sx) + (dots.isBlank() ? 0 : Math.min(phoneTextWidth(dots, 9.9F), w - Math.round(58 * sx))) + 2;
            g.fill(caretX, rowY + Math.round(2 * sy), caretX + 1, rowY + Math.round(14 * sy),
                    activeCaretColor(BANK_TEXT));
        }
        if (eye) {
            int eyeX = x + w - Math.round(11 * sx);
            int eyeY = rowY + iconCenter;
            fillCircle(g, eyeX, eyeY, Math.max(6, Math.round(8 * sx)), BANK_MUTED);
            fillCircle(g, eyeX, eyeY, Math.max(3, Math.round(4 * sx)), BANK_BG);
            fillCircle(g, eyeX, eyeY, Math.max(1, Math.round(2 * sx)), BANK_MUTED);
        }
        int lineY = rowY + Math.round(32 * sy);
        g.fill(x, lineY, x + w, lineY + 1, BANK_LINE);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "bank-settings", "", ""));
        drawScaledCentered(g, font, "Terms & Condition", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.25F);

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
                drawScaledString(g, font, line, x, cursor, BANK_TEXT, scale);
                cursor += lineHeight;
            }
            cursor += paragraphGap;
        }
    }

    private static void drawAllCards(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        List<SmartphoneClientState.PhoneAccount> accounts = SmartphoneClientState.accounts();

        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int backSize = Math.max(32, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, backSize);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, backSize, backSize),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "All Cards", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        List<SmartphoneClientState.PhoneAccount> accounts = SmartphoneClientState.accounts();
        SmartphoneClientState.PhoneAccount selected = SmartphoneClientState.selectedAccount();
        float sx = w / 335.0F;
        float sy = h / 758.0F;

        int backSize = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, backSize);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, backSize, backSize),
                "APP", "all-cards", "", ""));
        drawScaledCentered(g, font, "Add New Card", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);

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
        drawScaledString(g, font, "Expiry Date", x, fieldY, BANK_MUTED, 1.0F);
        drawScaledString(g, font, "4-digit CVV", x + Math.round(215 * sx), fieldY, BANK_MUTED, 1.0F);
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
        drawScaledString(g, font, value, x, y, BANK_TEXT, 1.0F);
        g.fill(x, y + 25, x + w, y + 26, BANK_LINE);
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);

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
        drawScaledCentered(g, font, "Banking for Everything is", centerX, titleY, BANK_TEXT, 1.65F);
        drawScaledCentered(g, font, "Easy and Convenient", centerX, titleY + Math.round(33 * sy), BANK_TEXT, 1.65F);
        drawScaledCentered(g, font, "Secure payments, transfers, and account access",
                centerX, titleY + Math.round(79 * sy), BANK_MUTED, 0.88F);
        drawScaledCentered(g, font, "without leaving your phone.",
                centerX, titleY + Math.round(103 * sy), BANK_MUTED, 0.88F);

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
        drawScaledCentered(g, font, "BANKPICK", centerX, centerY + Math.round(31 * sy), BANK_TEXT, 1.48F);
        drawScaledCentered(g, font, "UBS mobile banking", centerX, centerY + Math.round(58 * sy), BANK_MUTED, 0.88F);

        int barW = Math.max(96, Math.round(w * 0.46F));
        int barH = Math.max(4, Math.round(5 * sy));
        int barX = centerX - barW / 2;
        int barY = y + h - Math.round(84 * sy);
        fillRounded(g, barX, barY, barW, barH, Math.max(2, barH / 2), BANK_PANEL_SOFT);
        int filled = Mth.clamp(Math.round(barW * progress), 0, barW);
        if (filled > 0) {
            fillRounded(g, barX, barY, filled, barH, Math.max(2, barH / 2), BANK_BLUE);
        }
    }

    private static void drawBankPickLogo(GuiGraphics g, int centerX, int centerY, int size) {
        int ringRadius = Math.max(10, size / 5);
        int ringThickness = Math.max(3, size / 18);
        int offset = Math.max(8, size / 7);
        drawCircleRing(g, centerX - offset, centerY, ringRadius, ringThickness, 0xFF27D6FF, BANK_BG);
        drawCircleRing(g, centerX + offset, centerY, ringRadius, ringThickness, BANK_BLUE, BANK_BG);
        drawSoftLine(g, centerX - offset + ringRadius, centerY, centerX + offset - ringRadius, centerY, BANK_TEXT);
        fillCircle(g, centerX, centerY, Math.max(4, size / 16), BANK_TEXT);
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
                cardY + Math.round(27 * sy), BANK_TEXT, 1.0F);

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
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return txs;
        }
        List<SmartphoneClientState.PhoneTransaction> filtered = new ArrayList<>();
        for (SmartphoneClientState.PhoneTransaction tx : txs) {
            String haystack = ((tx.description() == null ? "" : tx.description()) + " "
                    + (tx.amount() == null ? "" : tx.amount()) + " "
                    + (tx.time() == null ? "" : tx.time())).toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                filtered.add(tx);
            }
        }
        return filtered;
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
        List<String> months = new ArrayList<>();
        for (String key : lastSixMonthKeys()) {
            if (!months.contains(key)) {
                months.add(key);
            }
        }
        if (txs != null) {
            for (SmartphoneClientState.PhoneTransaction tx : txs) {
                String key = transactionMonthKey(tx);
                if (!months.contains(key)) {
                    months.add(key);
                }
            }
        }
        if (months.isEmpty()) {
            return lastSixMonthKeys();
        }
        months.sort(String::compareTo);
        if (months.size() <= 6) {
            return months;
        }
        String selected = SmartphoneClientState.statsMonthKey();
        int anchor = selected != null && months.contains(selected)
                ? months.indexOf(selected)
                : months.size() - 1;
        int from = Mth.clamp(anchor - 5, 0, Math.max(0, months.size() - 6));
        return new ArrayList<>(months.subList(from, Math.min(months.size(), from + 6)));
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
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }
        String key = monthKey == null || monthKey.isBlank() ? normalizeStatsMonthKey(txs) : monthKey;
        return txs.stream()
                .filter(tx -> key.equals(transactionMonthKey(tx)))
                .toList();
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
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
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
        drawScaledCentered(g, font, "My Cards", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.35F);
        fillCircle(g, x + w - circle / 2, y + circle / 2, circle / 2, BANK_PANEL_SOFT);
        drawFigmaPlusGlyph(g, x + w - circle / 2, y + circle / 2, Math.max(8, Math.round(9 * sx)), BANK_TEXT);
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
        drawScaledString(g, font, "Monthly spending limit", x, limitTitleY, BANK_TEXT, 1.28F);
        int limitY = y + Math.round(541 * sy);
        int limitH = Math.max(96, Math.round(113 * sy));
        fillRounded(g, x, limitY, w, limitH, Math.max(15, Math.round(18 * sx)), BANK_PANEL_SOFT);
        String amountText = monthActivity <= 0.0D
                ? "Amount: " + (account == null ? "$0" : account.shortBalance())
                : "Amount: " + moneyLabel(monthSpent);
        drawScaledString(g, font, trim(font, amountText, w - Math.round(48 * sx)),
                x + Math.round(24 * sx), limitY + Math.round(23 * sy), BANK_TEXT, 0.98F);
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
        drawScaledString(g, font, "$0", barX, limitY + Math.round(76 * sy), BANK_MUTED, 0.9F);
        if (progress > 0) {
            drawScaledCentered(g, font, moneyLabel(monthSpent), barX + progress, limitY + Math.round(76 * sy), BANK_TEXT, 0.9F);
        }
        drawScaledString(g, font, moneyLabel(monthActivity), x + w - Math.round(68 * sx), limitY + Math.round(76 * sy), BANK_MUTED, 0.9F);

        int navY = bankDisplayNavY(y, h, w);
        drawBankDisplayBottomNav(g, font, x, navY, w, "cards", hitboxes);
    }

    private static void drawTap(GuiGraphics g, Font font, int x, int y, int w, int h,
                                List<SmartphoneClientState.Hitbox> hitboxes) {
        SmartphoneClientState.PhoneAccount account = SmartphoneClientState.accounts().stream()
                .filter(SmartphoneClientState.PhoneAccount::tapSelected)
                .findFirst()
                .orElse(SmartphoneClientState.selectedAccount());
        drawBankSurface(g, x, y, w, h, BANK_BG);
        drawBankBackdropAccents(g, x, y, w, h);
        drawStatusBar(g, font, x, y - 40, w, BANK_TEXT, BANK_BG);
        float sx = w / 335.0F;
        float sy = h / 758.0F;
        int circle = Math.max(34, Math.round(42 * sx));
        drawFigmaHeaderButton(g, HEADER_BACK_TEXTURE, x, y, circle);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y, circle, circle),
                "APP", "banking", "", ""));
        drawScaledCentered(g, font, "Tap to Pay", x + w / 2, y + Math.round(11 * sy), BANK_TEXT, 1.3F);

        int navY = bankDisplayNavY(y, h, w);
        int cardY = y + Math.round(74 * sy);
        int cardH = Math.max(142, Math.round(w * 199.0F / 335.0F));
        if (account == null) {
            drawFigmaCard(g, font, x, cardY, w, cardH, null, true);
            int emptyY = cardY + cardH + Math.round(32 * sy);
            fillRounded(g, x, emptyY, w, Math.max(124, Math.round(146 * sy)), Math.max(14, Math.round(18 * sx)), BANK_PANEL_SOFT);
            fillCircle(g, x + w / 2, emptyY + Math.round(36 * sy), Math.max(19, Math.round(22 * sx)), BANK_INPUT);
            drawFigmaActionIcon(g, "tap", x + w / 2, emptyY + Math.round(36 * sy), BANK_BLUE);
            drawScaledCentered(g, font, "No card selected", x + w / 2, emptyY + Math.round(72 * sy), BANK_TEXT, 1.04F);
            drawScaledCentered(g, font, "Choose a card before paying.", x + w / 2, emptyY + Math.round(92 * sy), BANK_MUTED, 0.9F);
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
        fillRounded(g, x, statusY, w, Math.max(78, Math.round(92 * sy)), Math.max(14, Math.round(18 * sx)), BANK_PANEL_SOFT);
        fillCircle(g, x + Math.round(31 * sx), statusY + Math.round(31 * sy), Math.max(17, Math.round(20 * sx)), BANK_INPUT);
        drawFigmaActionIcon(g, "tap", x + Math.round(31 * sx), statusY + Math.round(31 * sy), BANK_BLUE);
        drawScaledString(g, font, account.frozen() ? "Card Frozen" : "Ready to pay",
                x + Math.round(62 * sx), statusY + Math.round(17 * sy), BANK_TEXT, 1.0F);
        drawScaledString(g, font, account.tapSelected() ? "Default contactless card" : "Available for this session",
                x + Math.round(62 * sx), statusY + Math.round(39 * sy),
                account.frozen() ? BANK_DANGER : BANK_MUTED, 0.86F);

        int detailsY = statusY + Math.round(120 * sy);
        drawFigmaSettingsRow(g, font, x, detailsY, w, "Selected Card",
                trim(font, account.appTitle(), Math.round(w * 0.45F)), "APP", "all-cards", hitboxes);
        detailsY += Math.round(56 * sy);
        drawFigmaSettingsRow(g, font, x, detailsY, w, "Contactless Status",
                account.tapSelected() ? "Active" : "Set active", "SET_TAP_ACCOUNT", "", hitboxes);

        int infoY = detailsY + Math.round(68 * sy);
        if (infoY + Math.round(80 * sy) < navY - Math.round(44 * sy)) {
            fillRounded(g, x, infoY, w, Math.max(66, Math.round(78 * sy)), Math.max(14, Math.round(18 * sx)), BANK_PANEL_SOFT);
            fillCircle(g, x + Math.round(27 * sx), infoY + Math.round(29 * sy), Math.max(14, Math.round(16 * sx)), BANK_INPUT);
            drawFigmaActionIcon(g, "tap", x + Math.round(27 * sx), infoY + Math.round(29 * sy), BANK_BLUE);
            drawScaledString(g, font, "Payment points", x + Math.round(52 * sx), infoY + Math.round(13 * sy), BANK_TEXT, 0.98F);
            drawScaledString(g, font, trim(font, "Use this phone near supported terminals.", w - Math.round(66 * sx)),
                    x + Math.round(52 * sx), infoY + Math.round(35 * sy), BANK_MUTED, 0.86F);
        }
        mobileButton(g, font, x, navY - Math.round(52 * sy), w, Math.max(34, Math.round(40 * sy)), "Change Payment Card", BANK_BLUE,
                hitboxes, "APP", "all-cards", "", "");
        drawBankDisplayBottomNav(g, font, x, navY, w, "cards", hitboxes);
    }

    private static void drawCalculator(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       List<SmartphoneClientState.Hitbox> hitboxes) {
        g.drawString(font, "Calculator", x, y, 0xFFFFFFFF, false);
        drawInput(g, font, x, y + 20, w, "Expression", SmartphoneClientState.calculatorInput(),
                SmartphoneClientState.InputTarget.CALCULATOR, hitboxes);
        card(g, x, y + 48, w, 36);
        g.drawString(font, trim(font, SmartphoneClientState.calculatorResult(), w - 16), x + 8, y + 62, 0xFFFFFFFF, false);
        button(g, font, x, y + 92, 58, 22, "=", SmartphoneClientState.accentColor(), hitboxes, "CALC_EVAL", "", "", "");
        button(g, font, x + 66, y + 92, 58, 22, "Clear", 0xFFFF6A6A, hitboxes, "CALC_CLEAR", "", "", "");
    }

    private static void drawPaint(GuiGraphics g, Font font, int x, int y, int w, int h,
                                  List<SmartphoneClientState.Hitbox> hitboxes) {
        g.drawString(font, "Paint", x, y, 0xFFFFFFFF, false);
        int board = Math.min(w, h - 72);
        card(g, x, y + 20, board, board);
        String pixels = SmartphoneClientState.paintingDraft();
        for (int i = 0; i < 81; i++) {
            int px = x + 8 + (i % 9) * Math.max(1, (board - 16) / 9);
            int py = y + 28 + (i / 9) * Math.max(1, (board - 16) / 9);
            int c = i < pixels.length() ? 0xFF52D7FF + (pixels.charAt(i) % 5) * 0x000A0700 : 0x55384C66;
            g.fill(px, py, px + Math.max(2, (board - 18) / 9), py + Math.max(2, (board - 18) / 9), c);
        }
        int by = y + board + 28;
        for (int i = 0; i < 6; i++) {
            button(g, font, x + i * 30, by, 24, 18, String.valueOf(i + 1), 0xAAFFFFFF, hitboxes, "PAINT_SELECT", String.valueOf(i), "", "");
        }
        button(g, font, x, by + 24, 86, 20, "Add Pixel", SmartphoneClientState.accentColor(), hitboxes, "PAINT_SAVE", "", "", "");
    }

    private static void drawContacts(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        drawPhoneAppSurface(g, x, y, w, h, 0xFFF6F7FA);
        drawStatusBar(g, font, x, y - 31, w, 0xFF111318, 0xFFF6F7FA);
        drawScaledString(g, font, "Contacts", x, y, 0xFF111318, 1.42F);
        drawScaledString(g, font, "Server players", x, y + 22, 0xFF8E8E93, 0.82F);

        int cy = y + 52;
        for (SmartphoneClientState.PhoneContact contact : SmartphoneClientState.contacts()) {
            if (cy > y + h - 48) {
                break;
            }
            drawContactListRow(g, font, x, cy, w, contact, hitboxes);
            cy += 48;
        }
    }

    private static void drawMessenger(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      List<SmartphoneClientState.Hitbox> hitboxes) {
        SmartphoneClientState.PhoneContact contact = SmartphoneClientState.selectedContact();
        drawPhoneAppSurface(g, x, y, w, h, 0xFFFCFCFE);
        drawStatusBar(g, font, x, y - 31, w, 0xFF111318, 0xFFFCFCFE);
        if (contact == null) {
            drawScaledCentered(g, font, "Messenger", x + w / 2, y + 8, 0xFF111318, 1.15F);
            drawScaledCentered(g, font, "Pick a contact first.", x + w / 2, y + h / 2, 0xFF8E8E93, 0.9F);
            mobileButton(g, font, x + Math.round(w * 0.18F), y + h / 2 + 24, Math.round(w * 0.64F), 36,
                    "Open Contacts", 0xFF0078FF, hitboxes, "APP", "contacts", "", "");
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
        if (messages.isEmpty()) {
            SmartphoneClientState.syncMessageThreadScroll(0, Math.max(1, threadBottom - threadTop));
            drawScaledCentered(g, font, "No messages yet", x + w / 2, threadTop + 48, 0xFF8E8E93, 0.92F);
            drawScaledCentered(g, font, "Start the conversation below.", x + w / 2, threadTop + 67, 0xFFB0B0B6, 0.78F);
        } else {
            List<ChatMessageLayout> layouts = buildChatMessageLayouts(font, messages, w);
            int contentH = 0;
            for (ChatMessageLayout layout : layouts) {
                contentH += layout.rowH();
            }
            int viewportH = Math.max(1, threadBottom - threadTop);
            SmartphoneClientState.syncMessageThreadScroll(contentH, viewportH);
            int cy = threadBottom + SmartphoneClientState.messageThreadScrollOffset();
            for (ChatMessageLayout layout : layouts) {
                cy -= layout.rowH();
                if (cy + layout.rowH() < threadTop) {
                    break;
                }
                if (cy >= threadTop && cy + layout.rowH() <= threadBottom) {
                    drawMessageBubble(g, font, x, cy, w, layout.maxBubbleW(), layout.bubbleH(),
                            layout.lines(), layout.message().time(), layout.outgoing());
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
        List<ChatMessageLayout> layouts = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            SmartphoneClientState.PhoneMessage message = messages.get(i);
            boolean outgoing = SmartphoneClientState.isOutgoingMessage(message);
            int maxBubbleW = Math.max(94, Math.round(w * 0.74F));
            List<String> lines = wrapPhoneText(font, message.body(), maxBubbleW - 22, 10.5F, CHAT_MESSAGE_MAX_LINES);
            int bubbleH = Math.max(27, 13 + lines.size() * 13);
            int rowH = bubbleH + 18;
            layouts.add(new ChatMessageLayout(message, outgoing, maxBubbleW, bubbleH, rowH, lines));
        }
        return layouts;
    }

    private static void drawContactListRow(GuiGraphics g, Font font, int x, int y, int w,
                                           SmartphoneClientState.PhoneContact contact,
                                           List<SmartphoneClientState.Hitbox> hitboxes) {
        int avatar = 34;
        fillCircle(g, x + avatar / 2, y + avatar / 2, avatar / 2, contact.online() ? 0xFF0078FF : 0xFFBFC4CC);
        drawScaledCentered(g, font, initials(contact.name()), x + avatar / 2, y + 10, 0xFFFFFFFF, 0.86F);
        drawScaledString(g, font, trim(font, (contact.favorite() ? "* " : "") + contact.name(), w - 86),
                x + 44, y + 3, 0xFF111318, 0.96F);
        drawScaledString(g, font, contact.online() ? "Online" : "Offline", x + 44, y + 20,
                contact.online() ? 0xFF20C985 : 0xFF8E8E93, 0.74F);
        if (contact.unread() > 0) {
            String unread = String.valueOf(Math.min(99, contact.unread()));
            int badge = 18;
            fillCircle(g, x + w - badge / 2, y + 17, badge / 2, 0xFF0078FF);
            drawScaledCentered(g, font, unread, x + w - badge / 2, y + 12, 0xFFFFFFFF, 0.66F);
        }
        g.fill(x + 44, y + 43, x + w, y + 44, 0xFFE6E6EA);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y - 4, w, 44),
                "CONTACT", contact.id().toString(), "", ""));
    }

    private static void drawMessengerHeader(GuiGraphics g, Font font, int x, int y, int w,
                                            SmartphoneClientState.PhoneContact contact,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        int backSize = 28;
        fillCircle(g, x + backSize / 2, y + 17, backSize / 2, 0xFFF0F2F6);
        drawScaledCentered(g, font, "<", x + backSize / 2, y + 10, 0xFF0078FF, 1.05F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, y + 3, backSize, backSize),
                "APP", "contacts", "", ""));

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

    private static void drawMessageComposer(GuiGraphics g, Font font, int x, int y, int w, int h,
                                            List<SmartphoneClientState.Hitbox> hitboxes) {
        g.fill(x - 10, y - 7, x + w + 10, y + h + 8, 0xFFF6F7FA);
        g.fill(x - 10, y - 8, x + w + 10, y - 7, 0xFFE1E1E6);

        int sendSize = 34;
        int fieldW = w - sendSize - 8;
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
        fillRounded(g, x, fieldY, fieldW, fieldH, fieldH / 2, 0xFFFFFFFF);
        drawRoundedBorder(g, x, fieldY, fieldW, fieldH, fieldH / 2, active ? 0xFF0078FF : 0xFFDADAE0, 0xFFFFFFFF);
        drawMultilineFieldText(g, font, x + 14, fieldY + 12, fieldW - 24, visibleDraftLines,
                SmartphoneClientState.messageDraft(), active, "Message",
                0xFF111318, 0xFF8E8E93, 0xFF111318,
                draftStart + visibleDraftLines.size() >= draftLines.size());
        drawMessageDraftScrollbar(g, x, fieldY, fieldW, fieldH, draftStart, visibleLines, draftLines.size());
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(x, fieldY, fieldW, fieldH),
                "INPUT", SmartphoneClientState.InputTarget.MESSAGE.name(), "", ""));

        int sendX = x + fieldW + 8;
        int sendY = fieldY + fieldH - sendSize;
        fillCircle(g, sendX + sendSize / 2, sendY + sendSize / 2, sendSize / 2, 0xFF0078FF);
        drawScaledCentered(g, font, ">", sendX + sendSize / 2 + 1, sendY + 10, 0xFFFFFFFF, 1.0F);
        hitboxes.add(new SmartphoneClientState.Hitbox(new SmartphoneClientState.Rect(sendX, sendY, sendSize, sendSize),
                "SEND_MESSAGE", "", "", ""));
    }

    private static int messageComposerHeight(Font font, int w) {
        int sendSize = 34;
        int fieldW = w - sendSize - 8;
        List<String> draftLines = wrapPhoneText(font, SmartphoneClientState.messageDraft(), fieldW - 24, 10.5F, CHAT_COMPOSER_WRAP_MAX_LINES);
        int visibleLines = SmartphoneClientState.messageDraft().isBlank()
                ? 1
                : Math.min(CHAT_COMPOSER_MAX_LINES, Math.max(1, draftLines.size()));
        int fieldH = Math.max(34, 20 + visibleLines * 13);
        return fieldH + 14;
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
        String safe = text == null ? "" : text.trim();
        if (safe.isBlank()) {
            return List.of("");
        }
        String[] words = safe.split("\\s+");
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

    private static void drawNotes(GuiGraphics g, Font font, int x, int y, int w, int h,
                                  List<SmartphoneClientState.Hitbox> hitboxes) {
        g.drawString(font, "Notes", x, y, 0xFFFFFFFF, false);
        int by = y + 20;
        for (int i = 0; i < 12; i++) {
            button(g, font, x + (i % 6) * 28, by + (i / 6) * 22, 22, 18, String.valueOf(i + 1), 0xAAFFFFFF, hitboxes, "NOTE_SELECT", String.valueOf(i), "", "");
        }
        drawInput(g, font, x, y + 72, w, "Note", SmartphoneClientState.noteDraft(), SmartphoneClientState.InputTarget.NOTE, hitboxes);
        card(g, x, y + 100, w, Math.max(80, h - 150));
        drawWrapped(g, font, SmartphoneClientState.noteDraft(), x + 8, y + 108, w - 16, 0xFFFFFFFF, 8);
        button(g, font, x, y + h - 36, 52, 20, "Save", SmartphoneClientState.accentColor(), hitboxes, "SAVE_NOTE", "", "", "");
        button(g, font, x + 60, y + h - 36, 58, 20, "Delete", 0xFFFF6A6A, hitboxes, "DELETE_NOTE", "", "", "");
    }

    private static void drawSettings(GuiGraphics g, Font font, int x, int y, int w, int h,
                                     List<SmartphoneClientState.Hitbox> hitboxes) {
        g.drawString(font, "Settings", x, y, 0xFFFFFFFF, false);
        g.drawString(font, "Accent", x, y + 22, 0xCCFFFFFF, false);
        button(g, font, x, y + 38, 52, 20, "Cyan", 0xFF52D7FF, hitboxes, "THEME", "cyan", "aurora", "");
        button(g, font, x + 58, y + 38, 56, 20, "Green", 0xFF23D18B, hitboxes, "THEME", "green", "forest", "");
        button(g, font, x + 120, y + 38, 52, 20, "Rose", 0xFFFF6A9C, hitboxes, "THEME", "rose", "dusk", "");
        button(g, font, x, y + 66, 52, 20, "Gold", 0xFFFFCA5C, hitboxes, "THEME", "gold", "mono", "");
        g.drawString(font, "Phone is owner-locked unless the server config enables open access.", x, y + 100, 0xCCFFFFFF, false);
    }

    private static void drawBridge(GuiGraphics g, Font font, int x, int y, int w, int h) {
        g.drawString(font, "External App", x, y, 0xFFFFFFFF, false);
        card(g, x, y + 24, w, 84);
        drawWrapped(g, font, "This app is visible because its mod is loaded. Opening the native UI is reserved for that mod's own keybind/API.",
                x + 8, y + 34, w - 16, 0xFFFFFFFF, 5);
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

    private static void drawStatusBar(GuiGraphics g, Font font, int x, int y, int w) {
        drawStatusBar(g, font, x, y, w, 0xFFEAF8FF, 0xFF060B1C);
    }

    private static void drawStatusBar(GuiGraphics g, Font font, int x, int y, int w, int color, int batteryInner) {
        int statusInset = Math.max(3, Math.round(w * 5.0F / 335.0F));
        int statusX = x + statusInset;
        drawPhoneText(g, "9:41", statusX, y - 1, color, 9.4F);
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
            for (int px = 0; px < r; px++) {
                int dx = r - px;
                int dy = r - py;
                if (dx * dx + dy * dy <= r2) {
                    continue;
                }
                g.fill(x + px, y + py, x + px + 1, y + py + 1, color);
                g.fill(x + w - px - 1, y + py, x + w - px, y + py + 1, color);
                g.fill(x + px, y + h - py - 1, x + px + 1, y + h - py, color);
                g.fill(x + w - px - 1, y + h - py - 1, x + w - px, y + h - py, color);
            }
        }
    }

    private static void fillVertical(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        int steps = Math.max(1, h);
        for (int i = 0; i < steps; i++) {
            float t = i / (float) steps;
            int c = lerpColor(top, bottom, t);
            g.fill(x, y + i, x + w, y + i + 1, c);
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
            case "calculator" -> 0xFF303846;
            case "paint" -> 0xFFE86BA5;
            case "contacts" -> 0xFF2FBF71;
            case "messenger" -> 0xFF20A7FF;
            case "notes" -> 0xFFF5C64F;
            case "settings" -> 0xFF8A95A8;
            case "journeymap" -> 0xFF5AB56B;
            case "auction" -> 0xFFB681FF;
            case "realestate" -> 0xFFDC8D40;
            default -> SmartphoneClientState.accentColor();
        };
    }

    private static String iconGlyph(String appId) {
        if (appId != null && appId.startsWith("account:")) {
            return "$";
        }
        return switch (appId) {
            case "banking" -> "$";
            case "tap" -> "N";
            case "calculator" -> "=";
            case "paint" -> "P";
            case "contacts" -> "@";
            case "messenger" -> "M";
            case "notes" -> "T";
            case "settings" -> "*";
            case "journeymap" -> "^";
            case "auction" -> "A";
            case "realestate" -> "R";
            default -> "?";
        };
    }
}
