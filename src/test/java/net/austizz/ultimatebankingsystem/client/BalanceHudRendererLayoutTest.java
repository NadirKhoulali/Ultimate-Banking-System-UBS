package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalanceHudRendererLayoutTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void anchorsCardsInsideEachConfiguredCorner() throws Exception {
        assertLayout("TOP_LEFT", 8, 8);
        assertLayout("TOP_RIGHT", 728, 8);
        assertLayout("MIDDLE_LEFT", 8, 251);
        assertLayout("MIDDLE_RIGHT", 728, 251);
        assertLayout("BOTTOM_LEFT", 8, 494);
        assertLayout("BOTTOM_RIGHT", 728, 494);
    }

    @Test
    void parserAcceptsCommandAndLegacyConfigSpellings() throws Exception {
        Class<?> positionType = positionType();
        Method parse = positionType.getDeclaredMethod("parse", String.class);

        assertEquals("MIDDLE_RIGHT", parse.invoke(null, "middle-right").toString());
        assertEquals("BOTTOM_LEFT", parse.invoke(null, "BOTTOM_LEFT").toString());
        assertEquals(null, parse.invoke(null, "not-a-position"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertLayout(String anchorName, int expectedX, int expectedY) throws Exception {
        Class<?> renderer = rendererType();
        Class<?> positionType = positionType();
        Object position = Enum.valueOf(positionType.asSubclass(Enum.class), anchorName);
        Method layout = renderer.getDeclaredMethod("layout",
                int.class, int.class, int.class, int.class, positionType);
        layout.setAccessible(true);
        Object result = layout.invoke(null, 960, 540, 224, 38, position);

        assertEquals(expectedX, value(result, "x"));
        assertEquals(expectedY, value(result, "y"));
    }

    private static Object value(Object target, String accessor) throws Exception {
        Method method = target.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Class<?> positionType() throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem.hud.HudPosition", true, LOADER);
    }

    private static Class<?> rendererType() throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem.client.BalanceHudRenderer", true, LOADER);
    }
}
