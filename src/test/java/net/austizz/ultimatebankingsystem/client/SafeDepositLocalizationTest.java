package net.austizz.ultimatebankingsystem.client;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeDepositLocalizationTest {
    private static final List<String> LOCALES = List.of("en_us", "de_de", "fr_fr", "nl_nl");
    private static final List<String> EXPECTED_KEYS = List.of(
            "Premises",
            "Claim Premise",
            "Bank location registry, access modes, exits, and deletion state.",
            "No premises are registered.",
            "Deletion blockers",
            "None - deletion eligible",
            "Access mode and actions",
            "PUBLIC",
            "STAFF_ONLY",
            "Update Exit",
            "Delete Premise",
            "Safety Deposit Setup",
            "Teller rental and guided safe-box access are enabled.",
            "Claim a bank premise and safe area to create the first vault setup.",
            "Safe Access",
            "Grant Safe Access",
            "Revoke Safe Access",
            "Configure Vault Path",
            "Bank Teller Detail",
            "Vault Route Editor",
            "Routes not configured",
            "Route unavailable",
            "Set by first coordinate selection",
            "Start",
            "Finish",
            "Add ordered step",
            "Route Steps",
            "No route steps. Add walk, wait, or directional redstone actions.",
            "Walk",
            "Wait",
            "Redstone",
            "Not selected",
            "Request to Open Safe Box",
            "No assignment",
            "Request Guided Safe Box Access",
            "The selected account does not have an assigned safety deposit box.",
            "Ask this teller to guide you to the selected account's assigned safety deposit box.",
            "The assigned safety deposit box is temporarily unavailable.",
            "The bank owner must finish the safety deposit setup before this tab can be used.",
            "Rent a safety deposit box or request guided access to the selected account's assigned box.",
            "Staffing",
            "Premise ",
            "Ready | ",
            "Setup required | ",
            "Next: ",
            "Vault ",
            "Request ",
            "Assign a free ",
            "No free ",
            "Ordered steps: ",
            "Target ",
            "ubs.value.account_type.checking",
            "ubs.value.account_type.savings",
            "ubs.value.account_type.money_market",
            "ubs.value.account_type.certificate"
    );
    private static final List<String> REQUIRED_DYNAMIC_PREFIX_KEYS = List.of(
            "Premise ",
            "Ready | ",
            "Setup required | ",
            "Next: ",
            "Vault ",
            "Request ",
            "Assign a free ",
            "No free ",
            "Ordered steps: ",
            "Target "
    );

    @Test
    void everySupportedLocaleHasUniqueKeysAndCoversEveryExpectedEnglishKey() throws Exception {
        Map<String, String> expectedTranslations = readLocale("en_us");
        assertFalse(expectedTranslations.isEmpty(), "en_us must define translation keys");
        Set<String> missingEnglishKeys = new TreeSet<>(EXPECTED_KEYS);
        missingEnglishKeys.removeAll(expectedTranslations.keySet());
        assertTrue(missingEnglishKeys.isEmpty(),
                () -> "en_us is missing expected localization keys: " + missingEnglishKeys);

        for (String locale : LOCALES) {
            Map<String, String> translations = readLocale(locale);
            Set<String> missingKeys = new TreeSet<>(EXPECTED_KEYS);
            missingKeys.removeAll(translations.keySet());
            assertTrue(missingKeys.isEmpty(),
                    () -> locale + " is missing " + missingKeys.size() + " expected en_us keys: " + missingKeys);

            for (String key : EXPECTED_KEYS) {
                assertFalse(translations.get(key).isBlank(),
                        () -> locale + " has a blank translation for expected en_us key: " + key);
            }
        }
    }

    @Test
    void everySupportedLocaleContainsRequiredDynamicPrefixKeys() throws Exception {
        for (String locale : LOCALES) {
            Map<String, String> translations = readLocale(locale);
            for (String key : REQUIRED_DYNAMIC_PREFIX_KEYS) {
                assertTrue(translations.containsKey(key),
                        () -> locale + " is missing required dynamic-prefix key: " + key);
                assertFalse(translations.get(key).isBlank(),
                        () -> locale + " has a blank dynamic-prefix translation for: " + key);
            }
        }
    }

    private static Map<String, String> readLocale(String locale) throws Exception {
        String path = "/assets/ultimatebankingsystem/lang/" + locale + ".json";
        try (InputStream stream = SafeDepositLocalizationTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, () -> "Missing locale resource: " + path);
            try (JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.setLenient(false);
                assertEquals(JsonToken.BEGIN_OBJECT, reader.peek(), () -> locale + " root must be an object");
                reader.beginObject();

                Map<String, String> translations = new LinkedHashMap<>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    assertFalse(translations.containsKey(key),
                            () -> locale + " contains duplicate translation key: " + key);
                    assertEquals(JsonToken.STRING, reader.peek(),
                            () -> locale + " translation must be a string: " + key);
                    translations.put(key, reader.nextString());
                }

                reader.endObject();
                assertEquals(JsonToken.END_DOCUMENT, reader.peek(),
                        () -> locale + " contains trailing JSON content");
                return translations;
            }
        }
    }
}
