package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudClientStateTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @AfterEach
    void resetState() throws Exception {
        invokeStatic(stateType(), "reset", new Class<?>[0]);
    }

    @Test
    void carriesAccountContextAndAnimatesBalanceIncreases() throws Exception {
        apply("100.00", true, "Central Bank", "Checking Account", true, "middle-right", 1_000L);

        assertEquals("Central Bank", invokeStatic(stateType(), "getBankName", new Class<?>[0]));
        assertEquals("Checking Account", invokeStatic(stateType(), "getAccountType", new Class<?>[0]));
        assertEquals(true, invokeStatic(stateType(), "isPrimaryAccount", new Class<?>[0]));
        assertEquals("middle-right", invokeStatic(stateType(), "getPosition", new Class<?>[0]));
        assertEquals(0.0F, appearance(1_000L));
        assertEquals(1.0F, appearance(1_260L));

        apply("250.00", true, "Central Bank", "Checking Account", true, "middle-right", 2_000L);

        assertEquals("INCREASE", invokeStatic(stateType(), "getChangeDirection", new Class<?>[0]).toString());
        assertEquals(new BigDecimal("100.00"), animatedBalance(2_000L));
        BigDecimal midpoint = animatedBalance(2_230L);
        assertTrue(midpoint.compareTo(new BigDecimal("100.00")) > 0);
        assertTrue(midpoint.compareTo(new BigDecimal("250.00")) < 0);
        assertEquals(new BigDecimal("250.00"), animatedBalance(2_460L));
        assertEquals(1.0F, changeStrength(2_000L));
        assertEquals(0.0F, changeStrength(3_350L));
    }

    @Test
    void detectsDebitsWithoutRestartingAnimationForRepeatedSnapshots() throws Exception {
        apply("500", true, "Community Bank", "Saving Account", false, "bottom-left", 4_000L);
        apply("425", true, "Community Bank", "Saving Account", false, "bottom-left", 5_000L);

        assertEquals("DECREASE", invokeStatic(stateType(), "getChangeDirection", new Class<?>[0]).toString());
        apply("425", true, "Community Bank", "Saving Account", false, "bottom-left", 5_900L);

        assertEquals(0.0F, changeStrength(6_350L));
        assertEquals(false, invokeStatic(stateType(), "isPrimaryAccount", new Class<?>[0]));
    }

    private static void apply(String balance,
                              boolean enabled,
                              String bank,
                              String accountType,
                              boolean primary,
                              String position,
                              long now) throws Exception {
        invokeStatic(stateType(), "apply",
                new Class<?>[]{String.class, boolean.class, String.class, String.class, boolean.class,
                        String.class, long.class},
                balance, enabled, bank, accountType, primary, position, now);
    }

    private static BigDecimal animatedBalance(long now) throws Exception {
        return new BigDecimal((String) invokeStatic(stateType(), "getAnimatedBalanceText",
                new Class<?>[]{long.class}, now));
    }

    private static float changeStrength(long now) throws Exception {
        return (Float) invokeStatic(stateType(), "getChangeStrength", new Class<?>[]{long.class}, now);
    }

    private static float appearance(long now) throws Exception {
        return (Float) invokeStatic(stateType(), "getAppearanceProgress", new Class<?>[]{long.class}, now);
    }

    private static Object invokeStatic(Class<?> owner, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Class<?> stateType() throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem.client.HudClientState", true, LOADER);
    }
}
