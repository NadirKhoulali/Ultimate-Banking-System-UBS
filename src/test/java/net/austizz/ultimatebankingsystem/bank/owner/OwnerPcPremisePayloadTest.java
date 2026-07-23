package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremisePayloadTest {
    private static final String NETWORK_PACKAGE = "net.austizz.ultimatebankingsystem.network.";
    private static final String SAFE_SETUP_PACKAGE =
            "net.austizz.ultimatebankingsystem.bank.safebox.setup.";
    private static final UUID BANK_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000201");
    private static final UUID OPERATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000201");

    @Test
    void premiseActionCodecsCarryOperationId() throws Exception {
        Class<?> requestClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionPayload");
        Class<?> responseClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionResponsePayload");
        Class<?> actionClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionPayload$Action");
        Class<?> modeClass = load(SAFE_SETUP_PACKAGE + "SafePremiseMode");

        assertComponentNames(requestClass, "bankId", "operationId", "action", "premiseId", "mode");
        assertComponentNames(responseClass,
                "bankId", "operationId", "action", "premiseId", "success", "message");

        Constructor<?> requestConstructor = requestClass.getConstructor(
                UUID.class, UUID.class, actionClass, String.class, modeClass);
        Constructor<?> responseConstructor = responseClass.getConstructor(
                UUID.class, UUID.class, actionClass, String.class, boolean.class, String.class);
        Object action = enumValue(actionClass, "SET_MODE");
        Object mode = enumValue(modeClass, "STAFF_ONLY");
        Object request = requestConstructor.newInstance(
                BANK_ID, OPERATION_ID, action, "premise-main", mode);
        Object response = responseConstructor.newInstance(
                BANK_ID, OPERATION_ID, action, "premise-main", true, "Mode changed.");

        assertEquals(OPERATION_ID, invoke(roundTrip(requestClass, request), "operationId"));
        assertEquals(OPERATION_ID, invoke(roundTrip(responseClass, response), "operationId"));

        InvocationTargetException requestFailure = assertThrows(
                InvocationTargetException.class,
                () -> requestConstructor.newInstance(
                        BANK_ID, null, action, "premise-main", mode));
        assertInstanceOf(NullPointerException.class, requestFailure.getCause());
        InvocationTargetException responseFailure = assertThrows(
                InvocationTargetException.class,
                () -> responseConstructor.newInstance(
                        BANK_ID, null, action, "premise-main", true, "Mode changed."));
        assertInstanceOf(NullPointerException.class, responseFailure.getCause());

        Object generatedRequest = requestClass
                .getConstructor(UUID.class, actionClass, String.class, modeClass)
                .newInstance(BANK_ID, action, "premise-main", mode);
        assertNotNull(invoke(generatedRequest, "operationId"));
    }

    @Test
    void premiseContractsExposeTypedImmutableState() throws Exception {
        Class<?> payloadClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload");
        Class<?> modeClass = load(SAFE_SETUP_PACKAGE + "SafePremiseMode");
        Class<?> statusClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload$Status");
        Class<?> blockerClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload$DeleteBlocker");

        assertTrue(payloadClass.isRecord());
        assertEquals(List.of("NOT_READY", "READY"), enumNames(statusClass));
        assertEquals(List.of("NON_EMPTY", "MIGRATION_BACKED", "ASSIGNED", "ROUTED", "ACTIVE"),
                enumNames(blockerClass));
        assertComponentNames(payloadClass,
                "premiseId", "dimension",
                "minX", "minY", "minZ", "maxX", "maxY", "maxZ",
                "exitDimension", "exitX", "exitY", "exitZ", "exitYaw",
                "mode", "status", "safeAreaCount", "vaultCount", "readyVaultCount",
                "viewingRoomCount", "readyViewingRoomCount",
                "migrationBacked", "deleteBlockers");

        List<Object> stableBlockers = enumConstants(blockerClass);
        List<Object> suppliedBlockers = new ArrayList<>(stableBlockers);
        suppliedBlockers.add(enumValue(blockerClass, "ACTIVE"));
        Object premise = newPremise(payloadClass, modeClass, statusClass,
                enumValue(modeClass, "STAFF_ONLY"), enumValue(statusClass, "READY"),
                suppliedBlockers);

        assertEquals("premise-main", invoke(premise, "premiseId"));
        assertEquals("minecraft:overworld", invoke(premise, "dimension"));
        assertEquals(-12, invoke(premise, "minX"));
        assertEquals(60, invoke(premise, "minY"));
        assertEquals(-8, invoke(premise, "minZ"));
        assertEquals(19, invoke(premise, "maxX"));
        assertEquals(73, invoke(premise, "maxY"));
        assertEquals(27, invoke(premise, "maxZ"));
        assertEquals("minecraft:overworld", invoke(premise, "exitDimension"));
        assertEquals(-13, invoke(premise, "exitX"));
        assertEquals(61, invoke(premise, "exitY"));
        assertEquals(-8, invoke(premise, "exitZ"));
        assertEquals(92.5F, invoke(premise, "exitYaw"));
        assertEquals(enumValue(modeClass, "STAFF_ONLY"), invoke(premise, "mode"));
        assertEquals(enumValue(statusClass, "READY"), invoke(premise, "status"));
        assertEquals(2, invoke(premise, "safeAreaCount"));
        assertEquals(3, invoke(premise, "vaultCount"));
        assertEquals(1, invoke(premise, "readyVaultCount"));
        assertEquals(4, invoke(premise, "viewingRoomCount"));
        assertEquals(2, invoke(premise, "readyViewingRoomCount"));
        assertEquals(true, invoke(premise, "migrationBacked"));

        suppliedBlockers.clear();
        @SuppressWarnings("unchecked")
        List<Object> blockers = (List<Object>) invoke(premise, "deleteBlockers");
        assertEquals(stableBlockers, blockers);
        assertEquals(5, blockers.size());
        assertThrows(UnsupportedOperationException.class,
                () -> blockers.add(enumValue(blockerClass, "ACTIVE")));
        assertEquals(false, invoke(premise, "canDelete"));

        Object deletable = newPremise(payloadClass, modeClass, statusClass,
                enumValue(modeClass, "PUBLIC"), enumValue(statusClass, "NOT_READY"), List.of());
        assertEquals(List.of(), invoke(deletable, "deleteBlockers"));
        assertEquals(true, invoke(deletable, "canDelete"));
        assertNotNull(payloadClass.getField("STREAM_CODEC"));
    }

    @Test
    void invalidActionTargetsAreRejected() throws Exception {
        Class<?> payloadClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionPayload");
        Class<?> actionClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionPayload$Action");
        Class<?> modeClass = load(SAFE_SETUP_PACKAGE + "SafePremiseMode");
        Constructor<?> constructor = payloadClass.getConstructor(
                UUID.class, UUID.class, actionClass, String.class, modeClass);

        assertEquals(List.of("START_CLAIM", "SET_MODE", "START_EXIT_EDIT", "DELETE"),
                enumNames(actionClass));
        assertComponentNames(payloadClass, "bankId", "operationId", "action", "premiseId", "mode");

        assertConstructorRejects(constructor, BANK_ID, OPERATION_ID,
                enumValue(actionClass, "START_CLAIM"), "premise-main", null);
        assertConstructorRejects(constructor, BANK_ID, OPERATION_ID,
                enumValue(actionClass, "SET_MODE"), " ", enumValue(modeClass, "PUBLIC"));
        assertConstructorRejects(constructor, BANK_ID, OPERATION_ID,
                enumValue(actionClass, "SET_MODE"), "premise-main", null);
        assertConstructorRejects(constructor, BANK_ID, OPERATION_ID,
                enumValue(actionClass, "START_EXIT_EDIT"), "", null);
        assertConstructorRejects(constructor, BANK_ID, OPERATION_ID,
                enumValue(actionClass, "DELETE"), "", null);
        assertConstructorRejects(constructor, BANK_ID, OPERATION_ID,
                enumValue(actionClass, "DELETE"), "premise-main",
                enumValue(modeClass, "STAFF_ONLY"));
    }

    @Test
    void premiseCodecsRoundTripEveryEnumVariantAndTypedResponse() throws Exception {
        Class<?> premiseClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload");
        Class<?> modeClass = load(SAFE_SETUP_PACKAGE + "SafePremiseMode");
        Class<?> statusClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload$Status");
        Class<?> blockerClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload$DeleteBlocker");
        Class<?> actionPayloadClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionPayload");
        Class<?> actionClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionPayload$Action");
        Class<?> responseClass = load(NETWORK_PACKAGE + "OwnerPcPremiseActionResponsePayload");
        Constructor<?> actionConstructor = actionPayloadClass.getConstructor(
                UUID.class, UUID.class, actionClass, String.class, modeClass);
        Constructor<?> responseConstructor = responseClass.getConstructor(
                UUID.class, UUID.class, actionClass, String.class, boolean.class, String.class);

        for (Object mode : modeClass.getEnumConstants()) {
            for (Object status : statusClass.getEnumConstants()) {
                Object premise = newPremise(premiseClass, modeClass, statusClass,
                        mode, status, enumConstants(blockerClass));
                assertEquals(premise, roundTrip(premiseClass, premise));
            }
        }

        for (Object action : actionClass.getEnumConstants()) {
            String actionName = ((Enum<?>) action).name();
            String premiseId = actionName.equals("START_CLAIM") ? "" : "premise-main";
            Object mode = actionName.equals("SET_MODE") ? enumValue(modeClass, "STAFF_ONLY") : null;
            Object request = actionConstructor.newInstance(
                    BANK_ID, OPERATION_ID, action, premiseId, mode);
            assertEquals(request, roundTrip(actionPayloadClass, request));

            Object response = responseConstructor.newInstance(
                    BANK_ID, OPERATION_ID, action, premiseId, true,
                    "Premise action completed.");
            Object decoded = roundTrip(responseClass, response);
            assertEquals(response, decoded);
            assertEquals(BANK_ID, invoke(decoded, "bankId"));
            assertEquals(OPERATION_ID, invoke(decoded, "operationId"));
            assertEquals(action, invoke(decoded, "action"));
            assertEquals(premiseId, invoke(decoded, "premiseId"));
            assertEquals(true, invoke(decoded, "success"));
            assertEquals("Premise action completed.", invoke(decoded, "message"));
        }

        assertComponentNames(responseClass,
                "bankId", "operationId", "action", "premiseId", "success", "message");
        assertNotNull(actionPayloadClass.getField("STREAM_CODEC"));
        assertNotNull(responseClass.getField("STREAM_CODEC"));
    }

    @Test
    void bankDataAppendsDefensivelyCopiedPremises() throws Exception {
        Class<?> dataClass = load(NETWORK_PACKAGE + "OwnerPcBankDataPayload");
        Class<?> premiseClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload");
        Class<?> modeClass = load(SAFE_SETUP_PACKAGE + "SafePremiseMode");
        Class<?> statusClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload$Status");

        assertComponentNames(dataClass,
                "bankId", "bankName", "status", "ownerName", "ownershipModel", "color", "motto",
                "reserve", "deposits", "reserveRatio", "minReserve", "accountsCount",
                "dailyCap", "dailyUsed", "dailyRemaining", "singleLimit", "dailyPlayerLimit",
                "dailyBankLimit", "tellerLimit", "cardIssueFee", "cardReplacementFee",
                "federalFundsRate", "bankLevel", "bankLevelDerived", "bankLevelManual",
                "bankLevelProgressRatio", "bankLevelNextDepositTarget", "bankLevelNextAccountTarget",
                "bankLevelRoadmap", "ownerView", "roles", "shares", "cofounders", "employees",
                "loanProducts", "interbankOffers", "interbankLoans", "accountRoster",
                "certificateSchedule", "safeAreaCount", "safeRowCapacity", "safeClaimedRowUnits",
                "safeTotalBoxSlots", "safeAssignedBoxes", "safeFreeBoxes", "safeLockedBoxes",
                "safeEscrowCases", "safePolicyMode", "safePolicyAmount", "safeRentPeriodTicks",
                "safeOverdueTicks", "safeAreaSummaries", "safeBoxAssignments", "safeLockedQueue",
                "playerEmployees", "bankTellers", "vaultSetups", "safeSetupObjective", "premises",
                "viewingRoomCapacity", "viewingRooms", "safeAccessLogs", "safeAlarm",
                "vaultStorageClaims");

        RecordComponent[] components = dataClass.getRecordComponents();
        int premisesIndex = componentIndex(components, "premises");
        assertEquals("java.util.List<net.austizz.ultimatebankingsystem.network.OwnerPcPremisePayload>",
                components[premisesIndex].getGenericType().getTypeName());

        Object premise = newPremise(premiseClass, modeClass, statusClass,
                enumValue(modeClass, "PUBLIC"), enumValue(statusClass, "NOT_READY"), List.of());
        List<Object> suppliedPremises = new ArrayList<>(List.of(premise));
        Object[] arguments = defaultRecordArguments(components);
        arguments[premisesIndex] = suppliedPremises;
        Constructor<?> constructor = dataClass.getConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
        Object bankData = constructor.newInstance(arguments);

        suppliedPremises.clear();
        @SuppressWarnings("unchecked")
        List<Object> premises = (List<Object>) invoke(bankData, "premises");
        assertEquals(List.of(premise), premises);
        assertThrows(UnsupportedOperationException.class, () -> premises.add(premise));

        Object decoded = roundTrip(dataClass, bankData);
        assertEquals(List.of(premise), invoke(decoded, "premises"));
        assertNotNull(dataClass.getField("STREAM_CODEC"));
    }

    private static Object newPremise(Class<?> payloadClass,
                                     Class<?> modeClass,
                                     Class<?> statusClass,
                                     Object mode,
                                     Object status,
                                     List<?> blockers) throws Exception {
        Class<?> blockerClass = load(NETWORK_PACKAGE + "OwnerPcPremisePayload$DeleteBlocker");
        Constructor<?> constructor = payloadClass.getConstructor(
                String.class, String.class,
                int.class, int.class, int.class, int.class, int.class, int.class,
                String.class, int.class, int.class, int.class, float.class,
                modeClass, statusClass, int.class, int.class, int.class,
                int.class, int.class, boolean.class, List.class);
        assertEquals(blockerClass,
                payloadClass.getRecordComponents()[payloadClass.getRecordComponents().length - 1]
                        .getGenericType() instanceof java.lang.reflect.ParameterizedType parameterized
                        ? parameterized.getActualTypeArguments()[0]
                        : null);
        return constructor.newInstance(
                "premise-main", "minecraft:overworld",
                -12, 60, -8, 19, 73, 27,
                "minecraft:overworld", -13, 61, -8, 92.5F,
                mode, status, 2, 3, 1, 4, 2, true, blockers);
    }

    private static Object[] defaultRecordArguments(RecordComponent[] components) {
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            Class<?> type = components[index].getType();
            if (type == UUID.class) {
                arguments[index] = BANK_ID;
            } else if (type == String.class) {
                arguments[index] = "";
            } else if (type == boolean.class) {
                arguments[index] = false;
            } else if (type == int.class) {
                arguments[index] = 0;
            } else if (type == List.class) {
                arguments[index] = List.of();
            } else {
                arguments[index] = null;
            }
        }
        return arguments;
    }

    private static int componentIndex(RecordComponent[] components, String name) {
        for (int index = 0; index < components.length; index++) {
            if (components[index].getName().equals(name)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Missing record component: " + name);
    }

    private static void assertConstructorRejects(Constructor<?> constructor, Object... arguments) {
        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class, () -> constructor.newInstance(arguments));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    private static void assertComponentNames(Class<?> recordClass, String... expected) {
        assertTrue(recordClass.isRecord());
        assertEquals(List.of(expected), Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .toList());
    }

    private static Object roundTrip(Class<?> payloadClass, Object value) throws Exception {
        Object codec = payloadClass.getField("STREAM_CODEC").get(null);
        Object buffer = buffer();
        try {
            load("net.minecraft.network.codec.StreamCodec")
                    .getMethod("encode", Object.class, Object.class)
                    .invoke(codec, buffer, value);
            return load("net.minecraft.network.codec.StreamCodec")
                    .getMethod("decode", Object.class)
                    .invoke(codec, buffer);
        } finally {
            buffer.getClass().getMethod("release").invoke(buffer);
        }
    }

    private static Object buffer() throws Exception {
        Class<?> byteBufClass = load("io.netty.buffer.ByteBuf");
        Object source = load("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        Object registries = load("net.minecraft.core.RegistryAccess").getField("EMPTY").get(null);
        return load("net.minecraft.network.RegistryFriendlyByteBuf")
                .getConstructor(byteBufClass, load("net.minecraft.core.RegistryAccess"))
                .newInstance(source, registries);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static List<Object> enumConstants(Class<?> enumClass) {
        return List.of(enumClass.getEnumConstants());
    }

    private static List<String> enumNames(Class<?> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }

    private static Class<?> load(String className) throws Exception {
        return Class.forName(className, true, NeoForgeTestClassLoader.get());
    }
}
