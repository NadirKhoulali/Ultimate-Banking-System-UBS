package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

final class OwnerPcVaultRouteTestSupport {
    static final UUID BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID TELLER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    static final UUID PLAYER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    static final UUID SESSION_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    static final String VAULT_ID = "vault-main";
    static final String DIMENSION = "minecraft:overworld";
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String NETWORK = "net.austizz.ultimatebankingsystem.network.";
    private static final String OWNER_ROUTE =
            "net.austizz.ultimatebankingsystem.bank.owner.route.";
    private static final String SAFE_ROUTE =
            "net.austizz.ultimatebankingsystem.bank.safebox.route.";

    private OwnerPcVaultRouteTestSupport() {
    }

    static Object request() throws Exception {
        return construct(network("OwnerPcVaultRouteRequestPayload"),
                BANK_ID, VAULT_ID, TELLER_ID, direction("OUTBOUND"));
    }

    static Object validSave() throws Exception {
        return validSave(SESSION_ID);
    }

    static Object validSave(UUID editSessionId) throws Exception {
        return save(editSessionId, "OUTBOUND", DIMENSION,
                position(10, 64, 10), position(20, 64, 20),
                List.of(walk(11, 64, 10), waitStep(20),
                        redstone(20, 64, 20, "NORTH", 9, 40)));
    }

    static Object save(String direction, String dimension, Object start, Object finish,
                       List<?> steps) throws Exception {
        return save(SESSION_ID, direction, dimension, start, finish, steps);
    }

    static Object save(UUID editSessionId, String direction, String dimension,
                       Object start, Object finish, List<?> steps) throws Exception {
        return construct(network("OwnerPcVaultRouteSavePayload"),
                editSessionId, BANK_ID, VAULT_ID, TELLER_ID, direction(direction), dimension,
                start, finish, steps);
    }

    static Object editor(Object save) throws Exception {
        return construct(network("OwnerPcVaultRouteEditorPayload"),
                true, "Route ready.", SESSION_ID, 61_000L,
                BANK_ID, VAULT_ID, TELLER_ID,
                direction("OUTBOUND"), true, DIMENSION,
                value(save, "start"), value(save, "finish"), value(save, "steps"));
    }

    static Object cancel(UUID editSessionId) throws Exception {
        return construct(network("OwnerPcVaultRouteCancelPayload"), editSessionId);
    }

    static Object position(int x, int y, int z) throws Exception {
        return construct(network("OwnerPcVaultRoutePosition"), x, y, z);
    }

    static Object walk(int x, int y, int z) throws Exception {
        return construct(network("OwnerPcVaultRouteStepPayload$Walk"), position(x, y, z));
    }

    static Object waitStep(int ticks) throws Exception {
        return construct(network("OwnerPcVaultRouteStepPayload$Wait"), ticks);
    }

    static Object redstone(int x, int y, int z, String face, int strength, int ticks)
            throws Exception {
        return construct(network("OwnerPcVaultRouteStepPayload$Redstone"),
                position(x, y, z), enumValue(safeRoute("SafeTellerRouteFace"), face),
                strength, ticks);
    }

    static Object rfid(int x, int y, int z) throws Exception {
        return construct(network("OwnerPcVaultRouteStepPayload$Rfid"), position(x, y, z));
    }

    static Object safeBounds() throws Exception {
        return construct(type("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds"),
                DIMENSION, 19, 63, 19, 24, 67, 24);
    }

    static Object premiseBounds() throws Exception {
        return construct(type("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds"),
                DIMENSION, 0, 60, 0, 30, 80, 30);
    }

    static Object access(boolean active, boolean powered, boolean unlocked, boolean owner,
                         boolean operator, boolean loaded, boolean bound, boolean sameBank,
                         boolean cashier, boolean vaultFound) throws Exception {
        return construct(ownerRoute("OwnerPcVaultRoutePolicy$AccessFacts"),
                active, powered, unlocked, owner, operator, loaded, bound, sameBank,
                cashier, vaultFound);
    }

    static Object authorize(Object facts) throws Exception {
        return ownerRoute("OwnerPcVaultRoutePolicy").getMethod(
                "authorize", ownerRoute("OwnerPcVaultRoutePolicy$AccessFacts"))
                .invoke(null, facts);
    }

    static Object authorizeSave(Object facts) throws Exception {
        return ownerRoute("OwnerPcVaultRoutePolicy").getMethod(
                "authorizeSave", ownerRoute("OwnerPcVaultRoutePolicy$AccessFacts"))
                .invoke(null, facts);
    }

    static Object validate(Object save, Object bounds, String vaultDimension,
                           Predicate<Object> loaded) throws Exception {
        return validate(save, premiseBounds(), bounds, vaultDimension, loaded, ignored -> true);
    }

    static Object validate(Object save,
                           Object premise,
                           Object bounds,
                           String vaultDimension,
                           Predicate<Object> loaded,
                           Predicate<Object> rfidScanner) throws Exception {
        return ownerRoute("OwnerPcVaultRoutePolicy").getMethod(
                "validateDraft", network("OwnerPcVaultRouteSavePayload"),
                type("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds"),
                type("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds"),
                String.class, double.class, double.class, double.class,
                Predicate.class, Predicate.class)
                .invoke(null, save, premise, bounds, vaultDimension,
                        10.5D, 64.0D, 10.5D, loaded, rfidScanner);
    }

    static Object map(Object save) throws Exception {
        return ownerRoute("OwnerPcVaultRouteMapper").getMethod(
                "toDomain", network("OwnerPcVaultRouteSavePayload")).invoke(null, save);
    }

    static Object roundTrip(String payloadClass, Object value) throws Exception {
        Class<?> payload = network(payloadClass);
        Object codec = payload.getField("STREAM_CODEC").get(null);
        Object buffer = buffer();
        try {
            type("net.minecraft.network.codec.StreamCodec")
                    .getMethod("encode", Object.class, Object.class)
                    .invoke(codec, buffer, value);
            return type("net.minecraft.network.codec.StreamCodec")
                    .getMethod("decode", Object.class).invoke(codec, buffer);
        } finally {
            release(buffer);
        }
    }

    static Object buffer() throws Exception {
        Class<?> byteBuf = type("io.netty.buffer.ByteBuf");
        Object source = type("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        Object registries = type("net.minecraft.core.RegistryAccess").getField("EMPTY").get(null);
        return type("net.minecraft.network.RegistryFriendlyByteBuf")
                .getConstructor(byteBuf, type("net.minecraft.core.RegistryAccess"))
                .newInstance(source, registries);
    }

    static void write(Object buffer, String method, Class<?>[] types, Object... values)
            throws Exception {
        buffer.getClass().getMethod(method, types).invoke(buffer, values);
    }

    static Object decode(String payloadClass, Object buffer) throws Exception {
        Object codec = network(payloadClass).getField("STREAM_CODEC").get(null);
        try {
            return type("net.minecraft.network.codec.StreamCodec")
                    .getMethod("decode", Object.class).invoke(codec, buffer);
        } catch (InvocationTargetException exception) {
            throw new IllegalArgumentException(exception.getCause());
        }
    }

    static void encode(String payloadClass, Object buffer, Object value) throws Exception {
        Object codec = network(payloadClass).getField("STREAM_CODEC").get(null);
        type("net.minecraft.network.codec.StreamCodec")
                .getMethod("encode", Object.class, Object.class)
                .invoke(codec, buffer, value);
    }

    static void release(Object buffer) throws Exception {
        buffer.getClass().getMethod("release").invoke(buffer);
    }

    static Object value(Object target, String method) throws Exception {
        Method accessor = target.getClass().getMethod(method);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }

    static Class<?> network(String name) throws Exception {
        return type(NETWORK + name);
    }

    static Class<?> ownerRoute(String name) throws Exception {
        return type(OWNER_ROUTE + name);
    }

    static Class<?> safeRoute(String name) throws Exception {
        return type(SAFE_ROUTE + name);
    }

    static Object direction(String name) throws Exception {
        return enumValue(safeRoute("SafeTellerRouteDirection"), name);
    }

    private static Object construct(Class<?> type, Object... args) throws Exception {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == args.length) {
                return constructor.newInstance(args);
            }
        }
        throw new NoSuchMethodException(type.getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return name == null ? null : Enum.valueOf((Class) type, name);
    }

    private static Class<?> type(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }
}
