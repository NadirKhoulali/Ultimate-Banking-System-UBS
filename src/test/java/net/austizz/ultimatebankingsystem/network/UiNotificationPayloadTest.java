package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.api.ApiNotificationRequest;
import net.austizz.ultimatebankingsystem.api.ApiNotificationType;
import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiNotificationPayloadTest {
    @Test
    void requestNormalizesDefaultsAndProgress() {
        ApiNotificationRequest request = ApiNotificationRequest.builder(null, "  Ready  ")
                .channel("  ")
                .durationMs(100)
                .progress(4.0F)
                .build();

        assertEquals("Ready", request.message());
        assertEquals("general", request.channel());
        assertEquals(ApiNotificationType.INFO, request.type());
        assertEquals(1500, request.durationMs());
        assertEquals(1.0F, request.progress());
        assertTrue(request.hasProgress());
    }

    @Test
    void payloadBoundsAllUntrustedTextAndDuration() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> payloadType = load(loader, "network.UiNotificationPayload");
        Class<?> operationType = load(loader, "network.UiNotificationPayload$Operation");
        Class<?> notificationType = load(loader, "api.ApiNotificationType");
        Class<?> priorityType = load(loader, "api.ApiNotificationPriority");
        Class<?> placementType = load(loader, "api.ApiNotificationPlacement");
        Constructor<?> constructor = payloadType.getConstructor(
                operationType, String.class, String.class, String.class, String.class,
                String.class, String.class, notificationType, priorityType, placementType,
                int.class, float.class, boolean.class, boolean.class, boolean.class);
        String oversized = "x".repeat(900);
        Object payload = constructor.newInstance(
                enumValue(operationType, "SHOW"), oversized, oversized, oversized, oversized,
                oversized, oversized, null, null, null, 99_000, Float.NaN,
                false, true, true);

        assertEquals(72, ((String) value(payload, "id")).length());
        assertEquals(512, ((String) value(payload, "message")).length());
        assertEquals(256, ((String) value(payload, "detail")).length());
        assertEquals(30_000, value(payload, "durationMs"));
        assertEquals(-1.0F, value(payload, "progress"));
        assertFalse(ApiNotificationRequest.info("Done").build().hasProgress());
    }

    private static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf(type.asSubclass(Enum.class), name);
    }

    private static Class<?> load(ClassLoader loader, String suffix) throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem." + suffix, true, loader);
    }
}
