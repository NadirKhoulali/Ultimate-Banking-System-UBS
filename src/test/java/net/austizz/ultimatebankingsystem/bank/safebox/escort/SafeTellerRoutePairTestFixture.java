package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class SafeTellerRoutePairTestFixture {
    static final UUID BANK = new UUID(0L, 1L);
    static final UUID TELLER = new UUID(0L, 2L);
    static final String VAULT = "vault-a";
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String ROUTE_BASE =
            "net.austizz.ultimatebankingsystem.bank.safebox.route.";

    private SafeTellerRoutePairTestFixture() {
    }

    static Fixture metadata() throws Exception {
        Object outbound = route(new RouteIdentity(BANK.toString(), VAULT, TELLER.toString(), "OUTBOUND"));
        Object returning = route(new RouteIdentity(BANK.toString(), VAULT, TELLER.toString(), "RETURN"));
        String outboundId = string(outbound, "id");
        String returningId = string(returning, "id");
        Object hook = compound();
        putString(hook, "tellerId", TELLER.toString());
        putBoolean(hook, "bankBound", true);
        putString(hook, "outboundRouteRef", outboundId);
        putString(hook, "returnRouteRef", returningId);
        List<Object> hooks = list(hook);
        Object vault = compound();
        putString(vault, "id", VAULT);
        putString(vault, "safeAreaId", "safe-area-a");
        putString(vault, "dimension", "minecraft:overworld");
        putString(vault, "status", "READY");
        putInt(vault, "vaultDoorX", 8);
        putInt(vault, "vaultDoorY", 64);
        putInt(vault, "vaultDoorZ", 8);
        put(vault, "routeHooks", hooks);
        Object area = compound();
        putString(area, "id", "safe-area-a");
        putString(area, "premiseId", "premise-a");
        putBounds(area, new Bounds(1, 60, 1, 9, 70, 9));
        put(area, "vaults", list(vault));
        Object premise = compound();
        putString(premise, "id", "premise-a");
        putString(premise, "bankId", BANK.toString());
        putString(premise, "mode", "PUBLIC");
        putBounds(premise, new Bounds(0, 59, 0, 10, 71, 10));
        putInt(premise, "exitX", -1);
        putInt(premise, "exitY", 64);
        putInt(premise, "exitZ", 0);
        putFloat(premise, "exitYaw", 0.0F);
        put(premise, "safeAreas", list(area));
        Object root = compound();
        putInt(root, "safeDepositSetupVersion", 1);
        put(root, "safeDepositPremises", list(premise));
        List<Object> routes = list(outbound, returning);
        put(root, "safeTellerRoutes", routes);
        putString(root, "employees", BANK + "=VAULT:0");
        Object access = compound();
        putUuid(access, "playerId", BANK);
        putBoolean(access, "safeAccess", true);
        put(root, "bankEmployeeSafeAccess", list(access));
        return new Fixture(root, hook, hooks, routes, outbound, returning, outboundId, returningId);
    }

    static boolean setupAuthority(Object metadata) throws Exception {
        Class<?> requestType = load(ROUTE_BASE + "SafeTellerRoutePairResolver$VaultRequest");
        Object request = construct(requestType,
                new Class<?>[]{compoundClass(), UUID.class, String.class}, metadata, BANK, VAULT);
        return (Boolean) load(ROUTE_BASE + "SafeTellerRoutePairResolver")
                .getMethod("hasAnyExactPair", requestType).invoke(null, request);
    }

    static Optional<?> resolve(Object metadata) throws Exception {
        return resolveRequest(tellerRequest(metadata));
    }

    static Object tellerRequest(Object metadata) throws Exception {
        Class<?> vaultType = load(ROUTE_BASE + "SafeTellerRoutePairResolver$VaultRequest");
        Object vault = construct(vaultType,
                new Class<?>[]{compoundClass(), UUID.class, String.class}, metadata, BANK, VAULT);
        Class<?> requestType = load(ROUTE_BASE + "SafeTellerRoutePairResolver$TellerRequest");
        return construct(requestType,
                new Class<?>[]{vaultType, UUID.class}, vault, TELLER);
    }

    static Optional<?> resolveRequest(Object request) throws Exception {
        Class<?> requestType = load(ROUTE_BASE + "SafeTellerRoutePairResolver$TellerRequest");
        Class<?> routesClass = load(
                "net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortMetadataRoutes");
        Method method = routesClass.getDeclaredMethod("exactPair", requestType);
        method.setAccessible(true);
        return (Optional<?>) method.invoke(null, request);
    }

    static void replaceOutbound(Fixture fixture, RouteIdentity identity) throws Exception {
        Object replacement = route(identity);
        fixture.routes().set(0, replacement);
        putString(fixture.hook(), "outboundRouteRef", string(replacement, "id"));
    }

    static void replaceReturn(Fixture fixture, RouteIdentity identity) throws Exception {
        Object replacement = route(identity);
        fixture.routes().set(1, replacement);
        putString(fixture.hook(), "returnRouteRef", string(replacement, "id"));
    }

    static Object copy(Object compound) throws Exception {
        return compoundClass().getMethod("copy").invoke(compound);
    }

    static Object invoke(Object target, String method) throws Exception {
        Method accessor = target.getClass().getDeclaredMethod(method);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }

    static void putString(Object compound, String key, String value) throws Exception {
        compoundClass().getMethod("putString", String.class, String.class).invoke(compound, key, value);
    }

    static void putBoolean(Object compound, String key, boolean value) throws Exception {
        compoundClass().getMethod("putBoolean", String.class, boolean.class).invoke(compound, key, value);
    }

    static void putInt(Object compound, String key, int value) throws Exception {
        compoundClass().getMethod("putInt", String.class, int.class).invoke(compound, key, value);
    }

    private static void putFloat(Object compound, String key, float value) throws Exception {
        compoundClass().getMethod("putFloat", String.class, float.class).invoke(compound, key, value);
    }

    private static void putUuid(Object compound, String key, UUID value) throws Exception {
        compoundClass().getMethod("putUUID", String.class, UUID.class).invoke(compound, key, value);
    }

    static void remove(Object compound, String key) throws Exception {
        compoundClass().getMethod("remove", String.class).invoke(compound, key);
    }

    private static Object route(RouteIdentity identity) throws Exception {
        Object route = compound();
        putString(route, "id", stableId(identity));
        putString(route, "bankId", identity.bank());
        putString(route, "vaultId", identity.vault());
        putString(route, "tellerId", identity.teller());
        putString(route, "direction", identity.direction());
        putString(route, "dimension", "minecraft:overworld");
        putInt(route, "startX", 1);
        putInt(route, "startY", 64);
        putInt(route, "startZ", 1);
        putInt(route, "finishX", 2);
        putInt(route, "finishY", 64);
        putInt(route, "finishZ", 2);
        Object wait = compound();
        putString(wait, "type", "WAIT");
        putInt(wait, "durationTicks", 1);
        put(route, "steps", list(wait));
        return route;
    }

    private static void putBounds(Object tag, Bounds bounds) throws Exception {
        putString(tag, "dimension", "minecraft:overworld");
        putInt(tag, "minX", bounds.minX());
        putInt(tag, "minY", bounds.minY());
        putInt(tag, "minZ", bounds.minZ());
        putInt(tag, "maxX", bounds.maxX());
        putInt(tag, "maxY", bounds.maxY());
        putInt(tag, "maxZ", bounds.maxZ());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String stableId(RouteIdentity identity) throws Exception {
        Class<? extends Enum> directionClass = (Class<? extends Enum>) load(
                ROUTE_BASE + "SafeTellerRouteDirection");
        Object value = Enum.valueOf(directionClass, identity.direction());
        return (String) load(ROUTE_BASE + "SafeTellerRoute")
                .getMethod("stableId", String.class, String.class, String.class, directionClass)
                .invoke(null, identity.bank(), identity.vault(), identity.teller(), value);
    }

    private static Object compound() throws Exception {
        return compoundClass().getConstructor().newInstance();
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object... values) throws Exception {
        List<Object> result = (List<Object>) load("net.minecraft.nbt.ListTag").getConstructor().newInstance();
        for (Object value : values) {
            result.add(value);
        }
        return result;
    }

    private static void put(Object compound, String key, Object tag) throws Exception {
        compoundClass().getMethod("put", String.class, load("net.minecraft.nbt.Tag"))
                .invoke(compound, key, tag);
    }

    private static String string(Object compound, String key) throws Exception {
        return (String) compoundClass().getMethod("getString", String.class).invoke(compound, key);
    }

    private static Object construct(Class<?> type, Class<?>[] parameters, Object... arguments) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(parameters);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Class<?> compoundClass() throws Exception {
        return load("net.minecraft.nbt.CompoundTag");
    }

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }

    record Fixture(Object metadata, Object hook, List<Object> hooks, List<Object> routes,
                   Object outbound, Object returning, String outboundId, String returningId) {
    }

    record RouteIdentity(String bank, String vault, String teller, String direction) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

}
