package net.austizz.ultimatebankingsystem.i18n;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Lightweight UBS localization helper.
 *
 * <p>We intentionally use the original English text as the translation key.
 * When a key is missing in the active locale, Minecraft falls back to
 * rendering the key itself, which keeps behavior stable while allowing
 * language packs to override text.</p>
 */
public final class UbsTranslations {
    private UbsTranslations() {
    }

    public static MutableComponent literal(String text) {
        String key = text == null ? "" : text;
        return Component.translatable(key);
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key == null ? "" : key, args);
    }
}
