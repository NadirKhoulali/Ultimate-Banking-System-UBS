package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortMarkerPayloadTest {
    private static final String PAYLOAD_CLASS =
            "net.austizz.ultimatebankingsystem.network.SafeBoxEscortMarkerPayload";
    private static final String MOD_PAYLOADS_CLASS =
            "net.austizz.ultimatebankingsystem.network.ModPayloads";
    private static final String HANDLERS_CLASS =
            "net.austizz.ultimatebankingsystem.network.ClientPayloadHandlers";
    private static final String STATE_CLASS =
            "net.austizz.ultimatebankingsystem.client.SafeBoxEscortMarkerClientState";
    private static final ClassLoader MARKER_LOADER = markerClassLoader();

    public static void main(String[] args) throws Exception {
        SafeBoxEscortMarkerPayloadTest test = new SafeBoxEscortMarkerPayloadTest();
        run("payload-codec", test::payloadRoundTripsEveryMarkerField);
        run("payload-handler-state", test::serverPayloadDispatchesThroughHandlerIntoState);
        run("inactive-clear", test::inactivePayloadClearsEveryMarkerField);
        System.out.println("SUMMARY marker payload scenarios passed=3 failed=0");
    }

    @AfterEach
    void clearClientMarker() throws Exception {
        SafeBoxEscortMarkerClientState.clear();
        invokeStatic(STATE_CLASS, "clear");
    }

    @Test
    void payloadRoundTripsEveryMarkerField() throws Exception {
        Object expected = payload(true, "minecraft:overworld", -42, 71, 308, 2, "SDB-0042");

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void serverPayloadDispatchesThroughHandlerIntoState() throws Exception {
        handle(payload(true, "OverWorld", -42, 71, 308, 2, "SDB-0042"));

        Object snapshot = childSnapshot();
        assertTrue((boolean) invoke(snapshot, "active"));
        assertEquals("minecraft:overworld", invoke(snapshot, "dimensionId"));
        assertEquals(-42, invoke(snapshot, "rowX"));
        assertEquals(71, invoke(snapshot, "rowY"));
        assertEquals(308, invoke(snapshot, "rowZ"));
        assertEquals(2, invoke(snapshot, "doorIndex"));
        assertEquals("SDB-0042", invoke(snapshot, "boxLabel"));
    }

    @Test
    void inactivePayloadClearsEveryMarkerField() throws Exception {
        handle(payload(true, "minecraft:the_nether", 7, 63, -19, 3, "SDB-0009"));
        handle(invokeStatic(PAYLOAD_CLASS, "inactive"));

        Object snapshot = childSnapshot();
        assertFalse((boolean) invoke(snapshot, "active"));
        assertEquals("", invoke(snapshot, "dimensionId"));
        assertEquals(0, invoke(snapshot, "rowX"));
        assertEquals(0, invoke(snapshot, "rowY"));
        assertEquals(0, invoke(snapshot, "rowZ"));
        assertEquals(0, invoke(snapshot, "doorIndex"));
        assertEquals("", invoke(snapshot, "boxLabel"));
    }

    private static Object payload(boolean active,
                                  String dimensionId,
                                  int rowX,
                                  int rowY,
                                  int rowZ,
                                  int doorIndex,
                                  String boxLabel) throws Exception {
        Constructor<?> constructor = load(PAYLOAD_CLASS).getConstructor(
                boolean.class, String.class, int.class, int.class, int.class, int.class, String.class);
        return constructor.newInstance(active, dimensionId, rowX, rowY, rowZ, doorIndex, boxLabel);
    }

    private static void handle(Object payload) throws Exception {
        Consumer<Runnable> immediateMainThread = Runnable::run;
        Consumer<Object> typedClientReceiver = marker -> {
            try {
                invokeStatic(HANDLERS_CLASS, "handleSafeBoxEscortMarker",
                        new Class<?>[]{load(PAYLOAD_CLASS)}, marker);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to dispatch marker to typed client handler", exception);
            }
        };
        invokeStatic(MOD_PAYLOADS_CLASS, "enqueueSafeBoxEscortMarker",
                new Class<?>[]{load(PAYLOAD_CLASS), Consumer.class, Consumer.class},
                payload, immediateMainThread, typedClientReceiver);
    }

    private static Object childSnapshot() throws Exception {
        return invokeStatic(STATE_CLASS, "snapshot");
    }

    private static Object roundTrip(Object payload) throws Exception {
        Object codec = load(PAYLOAD_CLASS).getField("STREAM_CODEC").get(null);
        Object buffer = buffer();
        try {
            Class<?> streamCodec = load("net.minecraft.network.codec.StreamCodec");
            streamCodec.getMethod("encode", Object.class, Object.class).invoke(codec, buffer, payload);
            return streamCodec.getMethod("decode", Object.class).invoke(codec, buffer);
        } finally {
            buffer.getClass().getMethod("release").invoke(buffer);
        }
    }

    private static Object buffer() throws Exception {
        Class<?> byteBuf = load("io.netty.buffer.ByteBuf");
        Object source = load("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        Class<?> registryAccess = load("net.minecraft.core.RegistryAccess");
        Object registries = registryAccess.getField("EMPTY").get(null);
        return load("net.minecraft.network.RegistryFriendlyByteBuf")
                .getConstructor(byteBuf, registryAccess)
                .newInstance(source, registries);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invokeStatic(String className, String methodName) throws Exception {
        return invokeStatic(className, methodName, new Class<?>[0]);
    }

    private static Object invokeStatic(String className,
                                       String methodName,
                                       Class<?>[] parameterTypes,
                                       Object... args) throws Exception {
        Method method = load(className).getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Class<?> load(String className) throws Exception {
        return Class.forName(className, true, MARKER_LOADER);
    }

    private static ClassLoader markerClassLoader() {
        String classesOverride = System.getProperty("ubs.markerClassesDir", "").trim();
        if (classesOverride.isEmpty()) {
            return NeoForgeTestClassLoader.get();
        }
        try {
            Path project = Path.of(System.getProperty("ubs.projectDir", ".")).toAbsolutePath();
            List<URL> urls = new ArrayList<>();
            urls.add(Path.of(classesOverride).toAbsolutePath().toUri().toURL());
            urls.add(project.resolve(Path.of("build", "classes", "java", "main")).toUri().toURL());
            Path classpathFile = project.resolve(Path.of("build", "moddev", "clientLegacyClasspath.txt"));
            for (String line : Files.readAllLines(classpathFile)) {
                if (!line.isBlank()) {
                    urls.add(Path.of(line).toUri().toURL());
                }
            }
            Path artifacts = project.resolve(Path.of("build", "moddev", "artifacts"));
            try (var files = Files.list(artifacts)) {
                for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).toList()) {
                    urls.add(jar.toUri().toURL());
                }
            }
            return new MarkerClassLoader(urls.toArray(URL[]::new),
                    SafeBoxEscortMarkerPayloadTest.class.getClassLoader());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to construct marker behavior classloader", exception);
        }
    }

    private static void run(String scenario, ThrowingRunnable action) throws Exception {
        action.run();
        SafeBoxEscortMarkerClientState.clear();
        invokeStatic(STATE_CLASS, "clear");
        System.out.println("PASS " + scenario);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class MarkerClassLoader extends URLClassLoader {
        private MarkerClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("net.minecraft.")
                    && !name.startsWith("net.neoforged.")
                    && !name.startsWith("net.austizz.ultimatebankingsystem.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
