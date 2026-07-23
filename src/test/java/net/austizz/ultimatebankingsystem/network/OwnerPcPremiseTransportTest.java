package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremiseTransportTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String NETWORK = "net.austizz.ultimatebankingsystem.network.";
    private static final UUID BANK_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000702");
    private static final UUID OPERATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000702");

    @Test
    void actionCodecCarriesIntentAndIdentifiersOnly() throws Exception {
        Class<?> payloadType = load(NETWORK + "OwnerPcPremiseActionPayload");
        Class<?> responseType = load(NETWORK + "OwnerPcPremiseActionResponsePayload");
        Class<?> actionType = load(NETWORK + "OwnerPcPremiseActionPayload$Action");
        Class<?> modeType = load(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode");
        List<String> components = Arrays.stream(payloadType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("bankId", "operationId", "action", "premiseId", "mode"),
                components);
        assertFalse(components.stream().anyMatch(name -> name.matches(
                "(?i).*(dimension|bounds|coordinate|yaw).*|[xyz]")));

        Object action = enumValue(actionType, "SET_MODE");
        Object mode = enumValue(modeType, "STAFF_ONLY");
        Object request = payloadType.getConstructor(
                        UUID.class, UUID.class, actionType, String.class, modeType)
                .newInstance(BANK_ID, OPERATION_ID, action, "premise-main", mode);
        Object response = responseType.getConstructor(
                        UUID.class, UUID.class, actionType,
                        String.class, boolean.class, String.class)
                .newInstance(BANK_ID, OPERATION_ID, action,
                        "premise-main", true, "Mode changed.");
        Object decodedRequest = roundTrip(payloadType, request);
        Object decodedResponse = roundTrip(responseType, response);
        assertEquals(request, decodedRequest);
        assertEquals(response, decodedResponse);
        assertEquals(OPERATION_ID,
                decodedRequest.getClass().getMethod("operationId").invoke(decodedRequest));
        assertEquals(OPERATION_ID,
                decodedResponse.getClass().getMethod("operationId").invoke(decodedResponse));
    }

    @Test
    void clientEntrypointStoresTypedResponseAndVerbatimAlert() throws Exception {
        Object response = deniedResponse();
        Class<?> entrypoint = load(NETWORK + "ClientPayloadHandlers");
        Class<?> state = load(
                "net.austizz.ultimatebankingsystem.gui.screens.ClientOwnerPcData");
        Class<?> alerts = load(
                "net.austizz.ultimatebankingsystem.client.NotificationClientState");
        Method dispatch = entrypoint.getDeclaredMethod(
                "handleOwnerPcPremiseActionResponse", response.getClass());
        dispatch.setAccessible(true);

        state.getMethod("clear").invoke(null);
        alerts.getMethod("clear").invoke(null);
        try {
            dispatch.invoke(null, response);

            assertSame(response, state.getMethod("consumePremiseActionResponse").invoke(null));
            List<?> notifications = (List<?>) alerts.getMethod("snapshot").invoke(null);
            Object notification = notifications.getFirst();
            assertEquals("Exact premise denial.", notification.getClass().getMethod("message").invoke(notification));
            assertEquals("ERROR", notification.getClass().getMethod("type").invoke(notification).toString());
            assertFalse((boolean) state.getMethod("isToastSuccess").invoke(null));
        } finally {
            state.getMethod("clear").invoke(null);
            alerts.getMethod("clear").invoke(null);
        }
    }

    @Test
    void directHelperRefreshesOpenScreen() throws Exception {
        Object response = deniedResponse();
        Class<?> handler = load(NETWORK + "OwnerPcPremiseActionResponseClientHandler");
        Class<?> state = load(
                "net.austizz.ultimatebankingsystem.gui.screens.ClientOwnerPcData");
        Class<?> alerts = load(
                "net.austizz.ultimatebankingsystem.client.NotificationClientState");
        Class<?> screenType = load(
                "net.austizz.ultimatebankingsystem.gui.screens.OwnerPcClientScreen");
        Method dispatch = handler.getDeclaredMethod("handle", response.getClass(), screenType);
        dispatch.setAccessible(true);
        AtomicInteger refreshes = new AtomicInteger();
        Object openScreen = Proxy.newProxyInstance(LOADER, new Class<?>[]{screenType},
                (proxy, method, args) -> {
                    if (method.getName().equals("refreshFromNetwork")) {
                        refreshes.incrementAndGet();
                    }
                    return null;
                });

        state.getMethod("clear").invoke(null);
        alerts.getMethod("clear").invoke(null);
        try {
            dispatch.invoke(null, response, openScreen);

            assertEquals(1, refreshes.get());
        } finally {
            state.getMethod("clear").invoke(null);
            alerts.getMethod("clear").invoke(null);
        }
    }

    @Test
    void activeEscortVaultQueryIsReadOnly() throws Exception {
        Class<?> runtimeType = load(
                "net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime");
        Class<?> navigationType = load(
                "net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimePorts$Navigation");
        Class<?> effectsType = load(
                "net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimePorts$Effects");
        Object navigation = defaultProxy(navigationType);
        Object effects = defaultProxy(effectsType);
        Object runtime = runtimeType.getConstructor(navigationType, effectsType)
                .newInstance(navigation, effects);
        Object active = runtimeType.getMethod("activeVaultIds").invoke(runtime);
        assertTrue(active instanceof Set<?>);
        assertEquals(Set.of(), active);
        @SuppressWarnings("unchecked")
        Set<String> activeIds = (Set<String>) active;
        assertThrows(UnsupportedOperationException.class, () -> activeIds.add("vault-spoof"));
    }

    private static Object deniedResponse() throws Exception {
        Class<?> responseType = load(NETWORK + "OwnerPcPremiseActionResponsePayload");
        Class<?> actionType = load(NETWORK + "OwnerPcPremiseActionPayload$Action");
        return responseType.getConstructor(
                        UUID.class, UUID.class, actionType,
                        String.class, boolean.class, String.class)
                .newInstance(BANK_ID, OPERATION_ID, enumValue(actionType, "DELETE"),
                        "premise-main", false, "Exact premise denial.");
    }

    private static Object defaultProxy(Class<?> type) {
        return Proxy.newProxyInstance(LOADER, new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class || returnType == short.class
                    || returnType == int.class || returnType == long.class) {
                return 0;
            }
            if (returnType == float.class || returnType == double.class) {
                return 0.0D;
            }
            if (returnType == void.class) {
                return null;
            }
            if (returnType.isEnum()) {
                return returnType.getEnumConstants()[0];
            }
            return null;
        });
    }

    private static Object roundTrip(Class<?> payloadType, Object payload) throws Exception {
        Object codec = payloadType.getField("STREAM_CODEC").get(null);
        Object buffer = buffer();
        try {
            load("net.minecraft.network.codec.StreamCodec")
                    .getMethod("encode", Object.class, Object.class)
                    .invoke(codec, buffer, payload);
            return load("net.minecraft.network.codec.StreamCodec")
                    .getMethod("decode", Object.class)
                    .invoke(codec, buffer);
        } finally {
            buffer.getClass().getMethod("release").invoke(buffer);
        }
    }

    private static Object buffer() throws Exception {
        Class<?> byteBuf = load("io.netty.buffer.ByteBuf");
        Object source = load("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        Object registries = load("net.minecraft.core.RegistryAccess").getField("EMPTY").get(null);
        return load("net.minecraft.network.RegistryFriendlyByteBuf")
                .getConstructor(byteBuf, load("net.minecraft.core.RegistryAccess"))
                .newInstance(source, registries);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }

}
