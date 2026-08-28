package net.austizz.ultimatebankingsystem.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarryOnCompatibilityTest {
    private static final String DISPLAY_PROXY =
            "ultimatebankingsystem:safety_deposit_box_display_proxy";

    @Test
    void displayProxyIsBlacklistedWithoutReplacingOtherModsEntries() throws Exception {
        Path projectDir = Path.of(System.getProperty("ubs.projectDir"));
        Path blacklist = projectDir.resolve(
                "src/main/resources/data/carryon/tags/entity_types/entity_blacklist.json");

        assertTrue(Files.isRegularFile(blacklist), "Carry On entity blacklist is missing");
        JsonObject root = JsonParser.parseString(Files.readString(blacklist)).getAsJsonObject();
        assertFalse(root.get("replace").getAsBoolean(), "UBS must preserve other blacklist entries");
        JsonArray values = root.getAsJsonArray("values");
        assertTrue(values.asList().stream().anyMatch(value -> DISPLAY_PROXY.equals(value.getAsString())),
                "safety-deposit display proxy must not be carryable");
    }
}
