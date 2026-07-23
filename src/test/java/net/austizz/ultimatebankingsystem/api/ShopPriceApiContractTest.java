package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPriceApiContractTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void exposesAllFourPhysicalShelfScopesAndConvenienceMethods() throws Exception {
        Class<?> scope = load("api.ApiShopPriceScope");
        List<String> values = Arrays.stream(scope.getEnumConstants()).map(Object::toString).toList();
        assertEquals(List.of("REGULAR", "INCLUDE_ALL", "ALL_SHELVES_EXCLUDE_CREATIVE", "CREATIVE_ONLY"), values);

        Class<?> api = load("api.UltimateBankingApi");
        assertNotNull(method(api, "getItemShopPriceStatistics", 2));
        assertNotNull(method(api, "getRegularShopPriceStatistics", 1));
        assertNotNull(method(api, "getAllShelfPriceStatistics", 1));
        assertNotNull(method(api, "getNonCreativeShelfPriceStatistics", 1));
        assertNotNull(method(api, "getCreativeShelfPriceStatistics", 1));
    }

    @Test
    void medianAndAverageUseEveryShelfSlotSample() throws Exception {
        Class<?> scopeClass = load("api.ApiShopPriceScope");
        Object regular = Enum.valueOf(scopeClass.asSubclass(Enum.class), "REGULAR");
        Class<?> service = load("shop.ShopMarketPriceService");
        Method summarize = service.getDeclaredMethod("summarize", String.class, scopeClass, List.class);
        summarize.setAccessible(true);
        Object result = summarize.invoke(null, "minecraft:diamond", regular, List.of(100L, 500L, 200L, 300L));
        assertTrue((Boolean) result.getClass().getMethod("available").invoke(result));
        assertEquals(4, result.getClass().getMethod("sampleCount").invoke(result));
        assertEquals(250L, result.getClass().getMethod("medianPriceCents").invoke(result));
        assertEquals(275L, result.getClass().getMethod("averagePriceCents").invoke(result));
        assertEquals(100L, result.getClass().getMethod("minimumPriceCents").invoke(result));
        assertEquals(500L, result.getClass().getMethod("maximumPriceCents").invoke(result));
    }

    private static Method method(Class<?> type, String name, int parameterCount) {
        return Arrays.stream(type.getMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .filter(candidate -> candidate.getParameterCount() == parameterCount)
                .findFirst().orElse(null);
    }

    private static Class<?> load(String suffix) throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem." + suffix, true, LOADER);
    }
}
