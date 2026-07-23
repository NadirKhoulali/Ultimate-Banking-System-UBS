package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteMetadataFixture.metadata;
import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteTestSupport.*;

final class OwnerPcVaultRouteServiceHarness {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    record Facts(boolean bankExists,
                 boolean activePc,
                 boolean poweredOn,
                 boolean unlocked,
                 boolean owner,
                 boolean operator,
                 boolean tellerLoaded,
                 boolean tellerBound,
                 boolean sameBank,
                 boolean cashier,
                 boolean origin) {
        static Facts liveRequest() {
            return new Facts(true, true, true, true, true, false,
                    true, true, true, false, true);
        }

        static Facts validSave() {
            return new Facts(true, false, false, false, true, false,
                    true, true, true, false, false);
        }

        Facts withActivePc(boolean active, boolean hasOrigin) {
            return new Facts(bankExists, active, poweredOn, unlocked, owner, operator,
                    tellerLoaded, tellerBound, sameBank, cashier, hasOrigin);
        }

        Facts withAuthority(boolean isOwner, boolean isOperator) {
            return new Facts(bankExists, activePc, poweredOn, unlocked, isOwner, isOperator,
                    tellerLoaded, tellerBound, sameBank, cashier, origin);
        }

        Facts withBankExists(boolean exists) {
            return new Facts(exists, activePc, poweredOn, unlocked, owner, operator,
                    tellerLoaded, tellerBound, sameBank, cashier, origin);
        }

        Facts withTeller(boolean loaded, boolean bound, boolean same, boolean isCashier) {
            return new Facts(bankExists, activePc, poweredOn, unlocked, owner, operator,
                    loaded, bound, same, isCashier, origin);
        }
    }

    record World(String dimension,
                 boolean available,
                 int minY,
                 int maxY,
                 double minX,
                 double maxX,
                 double minZ,
                 double maxZ,
                 Predicate<Object> loaded,
                 Predicate<Object> rfidScanner) {
        World(String dimension,
              boolean available,
              int minY,
              int maxY,
              double minX,
              double maxX,
              double minZ,
              double maxZ,
              Predicate<Object> loaded) {
            this(dimension, available, minY, maxY, minX, maxX, minZ, maxZ,
                    loaded, ignored -> true);
        }

        static World valid() {
            return new World(DIMENSION, true, 0, 256,
                    -100, 100, -100, 100, ignored -> true);
        }
    }

    static final class Ports {
        Object metadata;
        Facts requestFacts;
        Facts saveFacts;
        World world;
        int commits;
        int worldCalls;
        int requestAuthorityCalls;
        int saveAuthorityCalls;
        UUID requestBankId;
        UUID requestTellerId;
        UUID saveBankId;
        UUID saveTellerId;

        Ports(Facts requestFacts, Facts saveFacts) throws Exception {
            this(metadata(), requestFacts, saveFacts, World.valid());
        }

        Ports(Object metadata, Facts requestFacts, Facts saveFacts, World world) {
            this.metadata = metadata;
            this.requestFacts = requestFacts;
            this.saveFacts = saveFacts;
            this.world = world;
        }

        Object proxy() throws Exception {
            Class<?> portType = ownerRoute("OwnerPcVaultRoutePorts");
            return Proxy.newProxyInstance(LOADER, new Class<?>[]{portType},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "requestAuthority" -> {
                            requestAuthorityCalls++;
                            requestBankId = (UUID) args[0];
                            requestTellerId = (UUID) args[1];
                            yield authority(requestFacts, args);
                        }
                        case "saveAuthority" -> {
                            saveAuthorityCalls++;
                            saveBankId = (UUID) args[0];
                            saveTellerId = (UUID) args[1];
                            yield authority(saveFacts, args);
                        }
                        case "world" -> worldView();
                        case "commit" -> {
                            commits++;
                            metadata = args[1];
                            yield null;
                        }
                        case "toString" -> "RouteTestPorts";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Object authority(Facts facts, Object[] args) throws Exception {
            UUID bankId = (UUID) args[0];
            UUID tellerId = (UUID) args[1];
            Object origin = facts.origin() ? origin() : null;
            return ownerRoute("OwnerPcVaultRoutePorts$Authority").getConstructors()[0]
                    .newInstance(metadata, facts.bankExists() && BANK_ID.equals(bankId),
                            facts.activePc(), facts.poweredOn(), facts.unlocked(),
                            facts.owner(), facts.operator(),
                            facts.tellerLoaded() && TELLER_ID.equals(tellerId),
                            facts.tellerBound(), facts.sameBank(), facts.cashier(), origin,
                            DIMENSION, 10.5D, 64.0D, 10.5D);
        }

        private Object worldView() throws Exception {
            worldCalls++;
            Object bounds = ownerRoute("OwnerPcVaultRouteWorldBounds").getConstructors()[0]
                    .newInstance(world.minY(), world.maxY(), world.minX(), world.maxX(),
                            world.minZ(), world.maxZ());
            return ownerRoute("OwnerPcVaultRoutePorts$WorldView").getConstructors()[0]
                    .newInstance(world.dimension(), world.available(), bounds,
                            world.loaded(), world.rfidScanner());
        }
    }

    static Object store(AtomicLong clock, Supplier<UUID> tokens, long ttlMillis)
            throws Exception {
        Constructor<?> constructor = ownerRoute("OwnerPcVaultRouteEditSessionStore")
                .getDeclaredConstructor(LongSupplier.class, Supplier.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance((LongSupplier) clock::get, tokens, ttlMillis);
    }

    static Object request(Ports ports, Object store, UUID playerId, Object payload)
            throws Exception {
        return service("request", "OwnerPcVaultRouteRequestPayload")
                .invoke(null, ports.proxy(), store, playerId, payload);
    }

    static Object save(Ports ports, Object store, UUID playerId, Object payload)
            throws Exception {
        return service("save", "OwnerPcVaultRouteSavePayload")
                .invoke(null, ports.proxy(), store, playerId, payload);
    }

    static void cancel(Object store, UUID playerId, Object payload) throws Exception {
        Method method = ownerRoute("OwnerPcVaultRouteService").getDeclaredMethod(
                "cancel", ownerRoute("OwnerPcVaultRouteEditSessionStore"), UUID.class,
                network("OwnerPcVaultRouteCancelPayload"));
        method.setAccessible(true);
        method.invoke(null, store, playerId, payload);
    }

    static Object saveFor(UUID token, UUID bankId, String vaultId, UUID tellerId,
                          String directionName) throws Exception {
        return network("OwnerPcVaultRouteSavePayload").getConstructors()[0].newInstance(
                token, bankId, vaultId, tellerId, direction(directionName), DIMENSION,
                position(10, 64, 10), position(20, 64, 20), List.of(walk(11, 64, 10)));
    }

    static boolean success(Object result) throws Exception {
        return (Boolean) value(value(result, "editor"), "success");
    }

    static UUID sessionId(Object result) throws Exception {
        return (UUID) value(value(result, "editor"), "editSessionId");
    }

    static int storeSize(Object store) throws Exception {
        return (Integer) invokeStore(store, "size", new Class<?>[0]);
    }

    static void invalidatePlayer(Object store, UUID playerId) throws Exception {
        invokeStore(store, "invalidatePlayer", new Class<?>[]{UUID.class}, playerId);
    }

    static void clear(Object store) throws Exception {
        invokeStore(store, "clear", new Class<?>[0]);
    }

    static Object sessionOrigin(Object store, UUID token, UUID playerId, Object save)
            throws Exception {
        Constructor<?> identityConstructor = ownerRoute("OwnerPcVaultRouteEditSession$Identity")
                .getDeclaredConstructors()[0];
        identityConstructor.setAccessible(true);
        Object identity = identityConstructor.newInstance(
                value(save, "bankId"), value(save, "vaultId"), value(save, "tellerId"),
                value(save, "direction"));
        Object check = invokeStore(store, "check",
                new Class<?>[]{UUID.class, UUID.class,
                        ownerRoute("OwnerPcVaultRouteEditSession$Identity")},
                token, playerId, identity);
        Object session = value(check, "session");
        return value(session, "origin");
    }

    private static Object origin() throws Exception {
        Constructor<?> constructor = ownerRoute("OwnerPcVaultRouteEditSession$Origin")
                .getDeclaredConstructor(String.class, String.class,
                        int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance("owner-pc-test", DIMENSION, 4, 64, 4);
    }

    private static Method service(String methodName, String payloadType) throws Exception {
        Method method = ownerRoute("OwnerPcVaultRouteService").getDeclaredMethod(
                methodName, ownerRoute("OwnerPcVaultRoutePorts"),
                ownerRoute("OwnerPcVaultRouteEditSessionStore"), UUID.class,
                network(payloadType));
        method.setAccessible(true);
        return method;
    }

    private static Object invokeStore(Object store, String methodName,
                                      Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = store.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(store, args);
    }
}
