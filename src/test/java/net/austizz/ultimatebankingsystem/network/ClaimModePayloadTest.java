package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimModePayloadTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String NETWORK = "net.austizz.ultimatebankingsystem.network.";

    @Test
    void actionPayloadIsTrimmedAndRoundTripsTargetCoordinates() throws Exception {
        Class<?> payloadClass = load(NETWORK + "ClaimModeActionPayload");
        Object payload = payloadClass.getConstructor(
                        String.class, String.class, boolean.class,
                        int.class, int.class, int.class)
                .newInstance(" 12345678-1234-1234-1234-123456789012 ",
                        " set_pos1 ", true, -42, 71, 208);

        assertEquals("12345678-1234-1234-1234-123456789012", invoke(payload, "sessionId"));
        assertEquals("set_pos1", invoke(payload, "action"));
        assertEquals(payload, roundTrip(payloadClass, payload));
    }

    @Test
    void snapshotDefensivelyBoundsCollectionsAndCounters() throws Exception {
        Class<?> outlineClass = load(NETWORK + "ClaimOutlineSummary");
        Object outline = outlineClass.getConstructor(
                        String.class, String.class, String.class,
                        int.class, int.class, int.class, int.class, int.class, int.class)
                .newInstance("minecraft:overworld", "SHOP_PLOT", "Owner",
                        1, 60, 2, 8, 70, 9);
        List<Object> supplied = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            supplied.add(outline);
        }

        Class<?> snapshotClass = load(NETWORK + "ClaimModeSnapshotPayload");
        Constructor<?> constructor = snapshotClass.getConstructor(
                boolean.class, String.class, String.class, String.class, String.class,
                String.class, String.class, boolean.class, boolean.class,
                boolean.class, int.class, int.class, int.class,
                boolean.class, int.class, int.class, int.class,
                boolean.class, double.class, double.class, double.class, float.class,
                int.class, int.class, int.class, String.class, boolean.class, boolean.class, List.class);
        Object snapshot = constructor.newInstance(
                true, "session", "SHOP_PLOT", "Plot Claim", "Corner Store",
                "Owner", "minecraft:overworld", true, true,
                true, 1, 64, 2, true, 8, 70, 9,
                false, 0.0D, 0.0D, 0.0D, 0.0F,
                -2, -3, -40, "Ready", true, true, supplied);

        supplied.clear();
        assertEquals(0, invoke(snapshot, "pendingAdd"));
        assertEquals(0, invoke(snapshot, "pendingRemove"));
        assertEquals(0, invoke(snapshot, "remainingTicks"));
        assertEquals(true, invoke(snapshot, "appliedSuccessfully"));
        @SuppressWarnings("unchecked")
        List<Object> outlines = (List<Object>) invoke(snapshot, "outlines");
        assertEquals(96, outlines.size());
        assertFalse(outlines.isEmpty());
        assertEquals(snapshot, roundTrip(snapshotClass, snapshot));
    }

    @Test
    void inactiveSnapshotCannotExposeAWorkspace() throws Exception {
        Class<?> snapshotClass = load(NETWORK + "ClaimModeSnapshotPayload");
        Object inactive = snapshotClass.getMethod("inactive").invoke(null);

        assertEquals(false, invoke(inactive, "active"));
        assertEquals("", invoke(inactive, "sessionId"));
        assertTrue(((List<?>) invoke(inactive, "outlines")).isEmpty());
    }

    private static Object invoke(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
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

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }
}
