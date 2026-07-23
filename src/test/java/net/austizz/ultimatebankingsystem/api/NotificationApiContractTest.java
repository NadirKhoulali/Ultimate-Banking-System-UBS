package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationApiContractTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void legacyAndVersionedNotificationMethodsCoexist() throws Exception {
        Class<?> api = load("api.UltimateBankingApi");
        Class<?> legacyTone = load("api.ApiAlertTone");
        Class<?> request = load("api.ApiNotificationRequest");
        assertDoesNotThrow(() -> api.getMethod(
                "sendUiAlert", UUID.class, String.class, String.class,
                legacyTone, int.class));
        assertDoesNotThrow(() -> api.getMethod("sendNotification", UUID.class, request));
        assertDoesNotThrow(() -> api.getMethod("dismissNotification", UUID.class, String.class));
        assertDoesNotThrow(() -> api.getMethod("clearNotificationChannel", UUID.class, String.class));
    }

    @Test
    void notificationEnumIdsAreStableAndUnknownValuesAreSafe() throws Exception {
        Class<?> type = load("api.ApiNotificationType");
        Class<?> priority = load("api.ApiNotificationPriority");
        Class<?> placement = load("api.ApiNotificationPlacement");
        assertEquals("TRANSACTION", type.getMethod("fromId", int.class).invoke(null, 4).toString());
        assertEquals("INFO", type.getMethod("fromId", int.class).invoke(null, 999).toString());
        assertEquals("NORMAL", priority.getMethod("fromId", int.class).invoke(null, 999).toString());
        assertEquals("AUTO", placement.getMethod("fromId", int.class).invoke(null, 999).toString());
    }

    private static Class<?> load(String suffix) throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem." + suffix, true, LOADER);
    }
}
