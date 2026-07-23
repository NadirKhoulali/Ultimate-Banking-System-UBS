package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

final class SafeBoxEscortFreshAuthorizationFixture {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String BASE =
            "net.austizz.ultimatebankingsystem.bank.safebox.escort.";
    private static final String ROUTE_BASE =
            "net.austizz.ultimatebankingsystem.bank.safebox.route.";

    final UUID owner = id(1);
    final UUID teller = id(2);
    final UUID bank;
    final String vaultId;
    final UUID account = id(4);
    final Object row;
    final Object door;
    final Object outbound;
    final Object returning;
    private final Object context;

    SafeBoxEscortFreshAuthorizationFixture() throws Exception {
        this(id(3), "vault-a");
    }

    SafeBoxEscortFreshAuthorizationFixture(UUID bank, String vaultId) throws Exception {
        this.bank = bank;
        this.vaultId = vaultId;
        row = position(12, 64, 14);
        door = position(18, 64, 18);
        outbound = route("OUTBOUND", null);
        returning = route("RETURN", null);
        Object target = construct(BASE + "SafeBoxEscortTarget",
                new Class<?>[]{UUID.class, String.class, UUID.class, String.class,
                        type(BASE + "EscortBlockPosition"), int.class, UUID.class},
                bank, vaultId, account, "minecraft:overworld", row, 3, teller);
        Object premise = area(0, 50, 0, 40, 80, 40);
        Object safe = area(10, 60, 10, 20, 70, 20);
        Object exit = construct(BASE + "SafeBoxEscortRuntimeContext$Exit",
                new Class<?>[]{String.class, int.class, int.class, int.class, float.class},
                "minecraft:overworld", -2, 64, -2, 90.0F);
        context = construct(BASE + "SafeBoxEscortRuntimeContext",
                new Class<?>[]{UUID.class, UUID.class, type(BASE + "SafeBoxEscortTarget"),
                        type(BASE + "SafeBoxArea"), type(BASE + "SafeBoxArea"),
                        type(BASE + "SafeBoxEscortRuntimeContext$Exit"),
                        type(BASE + "EscortBlockPosition"), type(ROUTE_BASE + "SafeTellerRoute"),
                        type(ROUTE_BASE + "SafeTellerRoute"), String.class},
                id(5), owner, target, premise, safe, exit, door, outbound, returning, "Box A-1");
    }

    boolean matches(Object snapshot) throws Exception {
        Class<?> resolver = type(BASE + "SafeBoxEscortContextResolver");
        Method method = resolver.getDeclaredMethod("matchesFreshSnapshot",
                type(BASE + "SafeBoxEscortRuntimeContext"),
                type(BASE + "SafeBoxEscortAuthorizationSnapshot"));
        method.setAccessible(true);
        return (boolean) method.invoke(null, context, snapshot);
    }

    Object context() {
        return context;
    }

    Object target() throws Exception {
        return context.getClass().getMethod("target").invoke(context);
    }

    boolean liveReadinessAuthorized(Object metadata, Object readiness) throws Exception {
        Class<?> liveAuthorization = type(BASE + "SafeBoxEscortLiveAuthorization");
        Method method = liveAuthorization.getDeclaredMethod(
                "verifyReadiness",
                type("net.minecraft.nbt.CompoundTag"),
                type("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver$RowReadiness"),
                type(BASE + "SafeBoxEscortRuntimeContext"));
        method.setAccessible(true);
        return (boolean) method.invoke(null, metadata, readiness, context);
    }

    Object snapshot(UUID ownerId, UUID bankId, Object tellerValue,
                    Object assignmentValue, Object vaultValue, Object routesValue) throws Exception {
        return construct(BASE + "SafeBoxEscortAuthorizationSnapshot",
                new Class<?>[]{UUID.class, UUID.class,
                        type(BASE + "SafeBoxEscortAuthorizationSnapshot$Teller"),
                        type(BASE + "SafeBoxEscortAuthorizationSnapshot$Assignment"),
                        type(BASE + "SafeBoxEscortAuthorizationSnapshot$Vault"),
                        type(BASE + "SafeBoxEscortAuthorizationSnapshot$Routes")},
                ownerId, bankId, tellerValue, assignmentValue, vaultValue, routesValue);
    }

    Object teller() throws Exception {
        return teller(teller, bank, true, false);
    }

    Object teller(UUID id, UUID bankId, boolean alive, boolean cashier) throws Exception {
        return construct(BASE + "SafeBoxEscortAuthorizationSnapshot$Teller",
                new Class<?>[]{UUID.class, UUID.class, boolean.class, boolean.class},
                id, bankId, alive, cashier);
    }

    Object assignment() throws Exception {
        return assignment(bank, account, "minecraft:overworld", row, 3, "Box A-1", false);
    }

    Object assignment(UUID bankId, UUID accountId, String dimension, Object rowPosition,
                      int doorIndex, String label, boolean locked) throws Exception {
        return construct(BASE + "SafeBoxEscortAuthorizationSnapshot$Assignment",
                new Class<?>[]{UUID.class, UUID.class, String.class,
                        type(BASE + "EscortBlockPosition"), int.class, String.class, boolean.class},
                bankId, accountId, dimension, rowPosition, doorIndex, label, locked);
    }

    Object vault() throws Exception {
        return vault(true, true, vaultId, door);
    }

    Object vault(boolean mapped, boolean ready, String id, Object doorMaster) throws Exception {
        return construct(BASE + "SafeBoxEscortAuthorizationSnapshot$Vault",
                new Class<?>[]{boolean.class, boolean.class, String.class,
                        type(BASE + "EscortBlockPosition")},
                mapped, ready, id, doorMaster);
    }

    Object routes() throws Exception {
        return routes(routeId(outbound), routeId(returning), outbound, returning);
    }

    Object routes(String outboundRef, String returnRef,
                  Object outboundRoute, Object returnRoute) throws Exception {
        return construct(BASE + "SafeBoxEscortAuthorizationSnapshot$Routes",
                new Class<?>[]{String.class, String.class, type(ROUTE_BASE + "SafeTellerRoute"),
                        type(ROUTE_BASE + "SafeTellerRoute")},
                outboundRef, returnRef, outboundRoute, returnRoute);
    }

    Object position(int x, int y, int z) throws Exception {
        return construct(BASE + "EscortBlockPosition",
                new Class<?>[]{int.class, int.class, int.class}, x, y, z);
    }

    Object route(String directionName, List<?> explicitSteps) throws Exception {
        Class<?> positionType = type(ROUTE_BASE + "SafeTellerRoutePosition");
        Object start = construct(ROUTE_BASE + "SafeTellerRoutePosition",
                new Class<?>[]{int.class, int.class, int.class}, 1, 64, 1);
        Object finish = construct(ROUTE_BASE + "SafeTellerRoutePosition",
                new Class<?>[]{int.class, int.class, int.class}, 18, 64, 18);
        Object direction = enumValue(ROUTE_BASE + "SafeTellerRouteDirection", directionName);
        List<?> steps = explicitSteps == null
                ? List.of(construct(ROUTE_BASE + "SafeTellerRouteStep$Walk",
                new Class<?>[]{positionType}, finish)) : explicitSteps;
        Method create = type(ROUTE_BASE + "SafeTellerRoute").getMethod("create",
                String.class, String.class, String.class, direction.getClass(), String.class,
                positionType, positionType, List.class);
        return create.invoke(null, bank.toString(), vaultId, teller.toString(), direction,
                "minecraft:overworld", start, finish, steps);
    }

    String routeId(Object route) throws Exception {
        return (String) route.getClass().getMethod("id").invoke(route);
    }

    private Object area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) throws Exception {
        return construct(BASE + "SafeBoxArea",
                new Class<?>[]{String.class, int.class, int.class, int.class,
                        int.class, int.class, int.class},
                "minecraft:overworld", minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Object construct(String name, Class<?>[] parameters, Object... arguments) throws Exception {
        Constructor<?> constructor = type(name).getDeclaredConstructor(parameters);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(String name, String value) throws Exception {
        return Enum.valueOf((Class<? extends Enum>) type(name), value);
    }

    private static Class<?> type(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
