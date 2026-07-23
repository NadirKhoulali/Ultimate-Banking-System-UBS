package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class VaultRouteEditorBehaviorHarness {
    static final UUID EDIT_SESSION_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final long SESSION_EXPIRY = 61_000L;
    private static final String ROOT = "net.austizz.ultimatebankingsystem.";
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    private VaultRouteEditorBehaviorHarness() {
    }

    static Object state(String method, Object... args) throws Exception {
        return invokeStatic(load("client.VaultRouteEditorClientState"), method, args);
    }

    static Object handshake(String method, Object... args) throws Exception {
        return invokeStatic(load("client.VaultRoutePickerHandshake"), method, args);
    }

    static Object property(Object target, String property) throws Exception {
        return target.getClass().getMethod(property).invoke(target);
    }

    static String enumName(Object value) {
        return ((Enum<?>) value).name();
    }

    static Object pickerMode(String name) throws Exception {
        return enumValue("client.VaultRouteEditorClientState$PickerMode", name);
    }

    static Object direction(String name) throws Exception {
        return enumValue("bank.safebox.route.SafeTellerRouteDirection", name);
    }

    static Object position(int x, int y, int z) throws Exception {
        return construct("network.OwnerPcVaultRoutePosition",
                new Class<?>[]{int.class, int.class, int.class}, x, y, z);
    }

    static Object zeroPosition() throws Exception {
        return load("network.OwnerPcVaultRoutePosition").getField("ZERO").get(null);
    }

    static Object waitStep(int durationTicks) throws Exception {
        return construct("network.OwnerPcVaultRouteStepPayload$Wait",
                new Class<?>[]{int.class}, durationTicks);
    }

    static Object walkStep(Object target) throws Exception {
        Class<?> positionType = load("network.OwnerPcVaultRoutePosition");
        return construct("network.OwnerPcVaultRouteStepPayload$Walk",
                new Class<?>[]{positionType}, target);
    }

    static Object redstoneStep(Object target,
                               String face,
                               int strength,
                               int durationTicks) throws Exception {
        Class<?> positionType = load("network.OwnerPcVaultRoutePosition");
        Class<?> faceType = load("bank.safebox.route.SafeTellerRouteFace");
        return construct("network.OwnerPcVaultRouteStepPayload$Redstone",
                new Class<?>[]{positionType, faceType, int.class, int.class},
                target, enumValue(faceType, face), strength, durationTicks);
    }

    static Object face(String name) throws Exception {
        return enumValue("bank.safebox.route.SafeTellerRouteFace", name);
    }

    static Object editorPayload(boolean success,
                                String message,
                                UUID bankId,
                                String vaultId,
                                UUID tellerId,
                                String direction,
                                boolean hasRoute,
                                String dimension,
                                Object start,
                                Object finish,
                                List<?> steps) throws Exception {
        Class<?> directionType = load("bank.safebox.route.SafeTellerRouteDirection");
        Class<?> positionType = load("network.OwnerPcVaultRoutePosition");
        return construct("network.OwnerPcVaultRouteEditorPayload",
                new Class<?>[]{boolean.class, String.class, UUID.class, long.class,
                        UUID.class, String.class,
                        UUID.class, directionType, boolean.class, String.class,
                        positionType, positionType, List.class},
                success, message, EDIT_SESSION_ID, SESSION_EXPIRY,
                bankId, vaultId, tellerId,
                enumValue(directionType, direction), hasRoute, dimension, start, finish, steps);
    }

    static Object editorPayloadWithoutSession(boolean success,
                                              String message,
                                              UUID bankId,
                                              String vaultId,
                                              UUID tellerId,
                                              String direction,
                                              boolean hasRoute,
                                              String dimension,
                                              Object start,
                                              Object finish,
                                              List<?> steps) throws Exception {
        Class<?> directionType = load("bank.safebox.route.SafeTellerRouteDirection");
        Class<?> positionType = load("network.OwnerPcVaultRoutePosition");
        return construct("network.OwnerPcVaultRouteEditorPayload",
                new Class<?>[]{boolean.class, String.class, UUID.class, long.class,
                        UUID.class, String.class, UUID.class, directionType,
                        boolean.class, String.class, positionType, positionType, List.class},
                success, message, null, 0L, bankId, vaultId, tellerId,
                enumValue(directionType, direction), hasRoute, dimension, start, finish, steps);
    }

    static Object savePayload(UUID bankId,
                              String vaultId,
                              UUID tellerId,
                              String direction,
                              String dimension,
                              Object start,
                              Object finish,
                              List<?> steps) throws Exception {
        Class<?> directionType = load("bank.safebox.route.SafeTellerRouteDirection");
        Class<?> positionType = load("network.OwnerPcVaultRoutePosition");
        return construct("network.OwnerPcVaultRouteSavePayload",
                new Class<?>[]{UUID.class, UUID.class, String.class, UUID.class, directionType,
                        String.class, positionType, positionType, List.class},
                EDIT_SESSION_ID, bankId, vaultId, tellerId, enumValue(directionType, direction),
                dimension, start, finish, steps);
    }

    static Object openOwnerPcPayload() throws Exception {
        return construct("network.OpenBankOwnerPcPayload", new Class<?>[0]);
    }

    static int validatorConstant(String field) throws Exception {
        return load("bank.safebox.route.SafeTellerRouteValidator").getField(field).getInt(null);
    }

    private static Object construct(String suffix, Class<?>[] types, Object... args)
            throws Exception {
        Constructor<?> constructor = load(suffix).getConstructor(types);
        return constructor.newInstance(args);
    }

    private static Object enumValue(String suffix, String name) throws Exception {
        return enumValue(load(suffix), name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private static Object invokeStatic(Class<?> owner, String name, Object[] args)
            throws Exception {
        Method method = Arrays.stream(owner.getMethods())
                .filter(candidate -> Modifier.isStatic(candidate.getModifiers()))
                .filter(candidate -> candidate.getName().equals(name))
                .filter(candidate -> accepts(candidate.getParameterTypes(), args))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(owner.getName() + "." + name));
        return method.invoke(null, args);
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int index = 0; index < args.length; index++) {
            if (args[index] == null) {
                if (parameterTypes[index].isPrimitive()) {
                    return false;
                }
            } else if (!boxed(parameterTypes[index]).isInstance(args[index])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        return type;
    }

    private static Class<?> load(String suffix) throws ClassNotFoundException {
        return Class.forName(ROOT + suffix, true, LOADER);
    }
}
