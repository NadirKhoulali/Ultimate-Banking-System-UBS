package net.austizz.ultimatebankingsystem.api.placeholder;

import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

import java.util.UUID;

/** Adapter contract for a PAPI expansion or another placeholder host. */
public final class PapiPlaceholderBridge {
    private PapiPlaceholderBridge() {
    }

    public static String resolve(UUID playerId, String params) {
        if (params == null || params.isBlank()) {
            return null;
        }
        String key = params.trim();
        if (!key.toLowerCase(java.util.Locale.ROOT).startsWith("ubs_")) {
            key = "ubs_" + key;
        }
        return UltimateBankingApiProvider.get().resolvePlaceholder(playerId, "%" + key + "%");
    }

    public static String resolveText(UUID playerId, String text) {
        return UltimateBankingApiProvider.get().resolvePlaceholders(playerId, text);
    }
}
