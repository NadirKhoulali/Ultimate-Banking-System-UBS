package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

final class VaultRouteTestSupport {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String ROUTE_PACKAGE =
            "net.austizz.ultimatebankingsystem.bank.safebox.route.";

    private VaultRouteTestSupport() {
    }

    static Object route(String teller, String direction, List<?> steps) throws Exception {
        return routeFor("bank-1", "vault-1", teller, direction, "minecraft:overworld", steps);
    }

    static Object routeFor(String bank, String vault, String teller, String direction,
                           String dimension, List<?> steps) throws Exception {
        return type("SafeTellerRoute").getMethod("create", String.class, String.class,
                        String.class, type("SafeTellerRouteDirection"), String.class,
                        type("SafeTellerRoutePosition"), type("SafeTellerRoutePosition"), List.class)
                .invoke(null, bank, vault, teller, enumValue("SafeTellerRouteDirection", direction),
                        dimension, position(1, 64, 9), position(4, 64, 10), steps);
    }

    static Object rawRoute(String id, String bank, String vault, String teller,
                           String direction, String dimension, List<?> steps) throws Exception {
        Constructor<?> constructor = type("SafeTellerRoute").getConstructor(String.class,
                String.class, String.class, String.class, type("SafeTellerRouteDirection"),
                String.class, type("SafeTellerRoutePosition"),
                type("SafeTellerRoutePosition"), List.class);
        return constructor.newInstance(id, bank, vault, teller,
                enumValue("SafeTellerRouteDirection", direction), dimension,
                position(1, 64, 9), position(4, 64, 10), steps);
    }

    static Object position(int x, int y, int z) throws Exception {
        return type("SafeTellerRoutePosition").getConstructor(int.class, int.class, int.class)
                .newInstance(x, y, z);
    }

    static Object walk(int x, int y, int z) throws Exception {
        return type("SafeTellerRouteStep$Walk").getConstructor(type("SafeTellerRoutePosition"))
                .newInstance(position(x, y, z));
    }

    static Object waitStep(int ticks) throws Exception {
        return type("SafeTellerRouteStep$Wait").getConstructor(int.class).newInstance(ticks);
    }

    static Object redstone(int x, int y, int z, String face, int strength, int ticks)
            throws Exception {
        return type("SafeTellerRouteStep$Redstone").getConstructor(
                        type("SafeTellerRoutePosition"), type("SafeTellerRouteFace"),
                        int.class, int.class)
                .newInstance(position(x, y, z), enumValue("SafeTellerRouteFace", face),
                        strength, ticks);
    }

    static Object rfid(int x, int y, int z) throws Exception {
        return type("SafeTellerRouteStep$Rfid").getConstructor(type("SafeTellerRoutePosition"))
                .newInstance(position(x, y, z));
    }

    static Object save(Object metadata, Object route) throws Exception {
        return type("SafeTellerRouteNbtStore").getMethod("saveAndBind",
                        VaultRouteNbtTestSupport.compoundClass(), type("SafeTellerRoute"))
                .invoke(null, metadata, route);
    }

    static List<?> readAll(Object metadata) throws Exception {
        return (List<?>) type("SafeTellerRouteNbtStore")
                .getMethod("readAll", VaultRouteNbtTestSupport.compoundClass())
                .invoke(null, metadata);
    }

    static Optional<?> resolve(Object metadata, String id) throws Exception {
        return (Optional<?>) type("SafeTellerRouteNbtStore")
                .getMethod("resolve", VaultRouteNbtTestSupport.compoundClass(), String.class)
                .invoke(null, metadata, id);
    }

    static List<?> listFor(Object metadata, String vault, String teller) throws Exception {
        return (List<?>) type("SafeTellerRouteNbtStore").getMethod("listForVaultTeller",
                        VaultRouteNbtTestSupport.compoundClass(), String.class, String.class)
                .invoke(null, metadata, vault, teller);
    }

    static List<?> listForBank(Object metadata, String bank, String vault, String teller)
            throws Exception {
        return (List<?>) type("SafeTellerRouteNbtStore").getMethod("listForVaultTeller",
                        VaultRouteNbtTestSupport.compoundClass(), String.class, String.class, String.class)
                .invoke(null, metadata, bank, vault, teller);
    }

    static Object validate(Object route) throws Exception {
        return type("SafeTellerRouteValidator").getMethod("validate", type("SafeTellerRoute"))
                .invoke(null, route);
    }

    @SuppressWarnings("unchecked")
    static List<Object> steps(Object route) throws Exception {
        return (List<Object>) value(route, "steps");
    }

    static Object value(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    static String id(Object route) throws Exception {
        return (String) value(route, "id");
    }

    static boolean success(Object result) throws Exception {
        return (Boolean) value(result, "success");
    }

    static String status(Object result) throws Exception {
        return value(result, "status").toString();
    }

    private static Class<?> type(String name) throws Exception {
        return Class.forName(ROUTE_PACKAGE + name, true, LOADER);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(String type, String value) throws Exception {
        return value == null ? null : Enum.valueOf((Class) type(type), value);
    }
}
