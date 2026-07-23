package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NotificationClientStateTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @AfterEach
    void resetState() throws Exception {
        invokeStatic(stateType(), "clear", new Class<?>[0]);
    }

    @Test
    void collapsesRapidDuplicatesWithoutLosingRepeatCount() throws Exception {
        long now = 10_000L;
        Object request = builtRequest("info", "Settings refreshed", "Bank Owner PC", "owner_pc");

        apply(show("first", request), now);
        apply(show("second", request), now + 100L);

        List<?> views = snapshot(now + 100L);
        assertEquals(1, views.size());
        assertEquals(2, value(views.getFirst(), "repeatCount"));
    }

    @Test
    void stableIdUpdatesProgressInPlaceWithoutReplayingSound() throws Exception {
        long now = 20_000L;
        Object started = progressRequest("sync", "Synchronizing accounts", 0.25F);
        apply(show("sync", started), now);
        invokeStatic(stateType(), "markSoundPlayed", new Class<?>[]{String.class, long.class}, "sync", 1L);

        Object updated = progressRequest("sync", "Synchronizing accounts", 0.75F);
        apply(show("sync", updated), now + 500L);

        Object view = snapshot(now + 500L).getFirst();
        assertEquals(0.75F, value(view, "progress"));
        assertEquals(2L, value(view, "revision"));
        assertFalse((Boolean) value(view, "soundPending"));
    }

    @Test
    void prioritizesCriticalNoticesAndRemovesDismissedEntriesAfterAnimation() throws Exception {
        long now = 30_000L;
        Object low = withPriority(request("info", "Copied ID", "", "actions"), "LOW");
        Object critical = withPriority(request("security", "Vault breach detected", "", "security"), "CRITICAL");
        critical = callBuilder(critical, "sticky", new Class<?>[]{boolean.class}, true);
        critical = callBuilder(critical, "build", new Class<?>[0]);
        low = callBuilder(low, "build", new Class<?>[0]);
        apply(show("low", low), now);
        apply(show("critical", critical), now + 1L);

        assertEquals("critical", value(snapshot(now + 1L).getFirst(), "id"));

        apply(invokeStatic(payloadType(), "dismiss", new Class<?>[]{String.class}, "critical"), now + 10L);
        assertEquals(2, snapshot(now + 10L).size());
        assertEquals(1, snapshot(now + 10L + exitAnimationMs()).size());
    }

    @Test
    void clearChannelOnlyDismissesMatchingNotices() throws Exception {
        long now = 40_000L;
        apply(show("banking", builtRequest("transaction", "Transfer complete", "", "banking")), now);
        apply(show("security", builtRequest("security", "Access updated", "", "security")), now);

        Object clear = invokeStatic(payloadType(), "clearChannel", new Class<?>[]{String.class}, "banking");
        apply(clear, now + 10L);
        List<?> afterExit = snapshot(now + 10L + exitAnimationMs());

        assertEquals(1, afterExit.size());
        assertEquals("SECURITY", value(afterExit.getFirst(), "type").toString());
    }

    private static Object request(String factory, String message, String title, String channel) throws Exception {
        Class<?> requestType = load("api.ApiNotificationRequest");
        Object builder = requestType.getMethod(factory, String.class).invoke(null, message);
        builder = callBuilder(builder, "title", new Class<?>[]{String.class}, title);
        return callBuilder(builder, "channel", new Class<?>[]{String.class}, channel);
    }

    private static Object builtRequest(String factory, String message, String title, String channel) throws Exception {
        return callBuilder(request(factory, message, title, channel), "build", new Class<?>[0]);
    }

    private static Object progressRequest(String id, String message, float progress) throws Exception {
        Class<?> requestType = load("api.ApiNotificationRequest");
        Object builder = requestType.getMethod("progress", String.class, String.class).invoke(null, id, message);
        builder = callBuilder(builder, "progress", new Class<?>[]{float.class}, progress);
        return callBuilder(builder, "build", new Class<?>[0]);
    }

    private static Object withPriority(Object builder, String name) throws Exception {
        Class<?> priorityType = load("api.ApiNotificationPriority");
        Object value = Enum.valueOf(priorityType.asSubclass(Enum.class), name);
        return callBuilder(builder, "priority", new Class<?>[]{priorityType}, value);
    }

    private static Object callBuilder(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(method, types).invoke(target, args);
    }

    private static Object show(String id, Object request) throws Exception {
        return invokeStatic(payloadType(), "show", new Class<?>[]{String.class, request.getClass()}, id, request);
    }

    private static void apply(Object payload, long now) throws Exception {
        Method method = stateType().getDeclaredMethod("apply", payloadType(), long.class);
        method.setAccessible(true);
        method.invoke(null, payload, now);
    }

    @SuppressWarnings("unchecked")
    private static List<?> snapshot(long now) throws Exception {
        Method method = stateType().getDeclaredMethod("snapshot", long.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(null, now);
    }

    private static int exitAnimationMs() throws Exception {
        return stateType().getField("EXIT_ANIMATION_MS").getInt(null);
    }

    private static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    private static Object invokeStatic(Class<?> owner, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method target = owner.getDeclaredMethod(method, types);
        target.setAccessible(true);
        return target.invoke(null, args);
    }

    private static Class<?> stateType() throws ClassNotFoundException {
        return load("client.NotificationClientState");
    }

    private static Class<?> payloadType() throws ClassNotFoundException {
        return load("network.UiNotificationPayload");
    }

    private static Class<?> load(String suffix) throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem." + suffix, true, LOADER);
    }
}
