package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class OwnerPcVaultRouteClientDispatchTest {
    private static final UUID BANK_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID TELLER_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void routeEditorResponseHasTypedDispatchTargetAndStateBehavior() throws Exception {
        Class<?> payloadType = loadNetwork("OwnerPcVaultRouteEditorPayload");
        Class<?> screenType = load("gui.screens.OwnerPcClientScreen");
        Class<?> handlers = loadNetwork("ClientPayloadHandlers");
        Class<?> state = load("gui.screens.ClientOwnerPcData");
        Object payload = editor(payloadType, false, "Route denied.");
        Method dispatch = handlers.getDeclaredMethod(
                "handleOwnerPcVaultRouteEditor", payloadType, screenType);
        dispatch.setAccessible(true);
        Method stateGetter = state.getDeclaredMethod("getVaultRouteEditor");
        AtomicInteger refreshes = new AtomicInteger();
        Object openScreen = Proxy.newProxyInstance(LOADER, new Class<?>[]{screenType},
                (proxy, method, args) -> {
                    if (method.getName().equals("refreshFromNetwork")) {
                        refreshes.incrementAndGet();
                    }
                    return null;
                });

        invoke(state, "clear");
        dispatch.invoke(null, payload, openScreen);

        assertSame(payload, stateGetter.invoke(null));
        assertEquals("Route denied.", invoke(state, "getToastMessage"));
        assertFalse((Boolean) invoke(state, "isToastSuccess"));
        assertEquals(1, refreshes.get());
    }

    private static Object editor(Class<?> payloadType, boolean success, String message)
            throws Exception {
        Class<?> directionType = load("bank.safebox.route.SafeTellerRouteDirection");
        Class<?> positionType = loadNetwork("OwnerPcVaultRoutePosition");
        Object outbound = Enum.valueOf(directionType.asSubclass(Enum.class), "OUTBOUND");
        Object zero = positionType.getField("ZERO").get(null);
        return payloadType.getConstructor(boolean.class, String.class, UUID.class, long.class,
                        UUID.class, String.class, UUID.class, directionType, boolean.class,
                        String.class, positionType, positionType, List.class)
                .newInstance(success, message, null, 0L, BANK_ID, "vault-main", TELLER_ID,
                        outbound, false, "minecraft:overworld", zero, zero, List.of());
    }

    private static Class<?> loadNetwork(String simpleName) throws ClassNotFoundException {
        return load("network." + simpleName);
    }

    private static Class<?> load(String suffix) throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem." + suffix, true, LOADER);
    }

    private static Object invoke(Class<?> owner, String method) throws Exception {
        return invoke(owner, method, new Class<?>[0]);
    }

    private static Object invoke(Class<?> owner, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method target = owner.getDeclaredMethod(method, types);
        target.setAccessible(true);
        return target.invoke(null, args);
    }
}
