package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSafeBoxEscortPortsTest {
    private static final String PORTS =
            "net.austizz.ultimatebankingsystem.bank.safebox.escort.MinecraftSafeBoxEscortPorts";
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void navigationAdapterPropagatesCleanupRefusalAndAcceptance() throws Exception {
        Class<?> cleanupType = load(PORTS + "$NavigationCleanup");
        AtomicBoolean accepted = new AtomicBoolean();
        Object cleanup = Proxy.newProxyInstance(LOADER, new Class<?>[]{cleanupType},
                (proxy, method, arguments) -> accepted.get());
        Object navigation = navigation(cleanupType, cleanup);
        Method cancel = navigation.getClass().getDeclaredMethod("cancel", UUID.class);
        Method forget = navigation.getClass().getDeclaredMethod("forget", UUID.class);
        cancel.setAccessible(true);
        forget.setAccessible(true);

        assertFalse((boolean) cancel.invoke(navigation, UUID.randomUUID()));
        assertFalse((boolean) forget.invoke(navigation, UUID.randomUUID()));
        accepted.set(true);
        assertTrue((boolean) cancel.invoke(navigation, UUID.randomUUID()));
        assertTrue((boolean) forget.invoke(navigation, UUID.randomUUID()));
    }

    private static Object navigation(Class<?> cleanupType, Object cleanup) throws Exception {
        Class<?> navigationType = load(PORTS + "$Navigation");
        Constructor<?> constructor = navigationType.getDeclaredConstructor(
                Class.forName("net.minecraft.server.MinecraftServer", false, LOADER), cleanupType);
        constructor.setAccessible(true);
        return constructor.newInstance(null, cleanup);
    }

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }
}
