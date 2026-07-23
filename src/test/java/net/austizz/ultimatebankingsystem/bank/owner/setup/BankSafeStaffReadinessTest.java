package net.austizz.ultimatebankingsystem.bank.owner.setup;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankSafeStaffReadinessTest {
    private static final String ROOT_PACKAGE = "net.austizz.ultimatebankingsystem.";
    private static final String STAFFING_REASON = "SAFE_ACCESS_STAFF_MISSING";
    private static final String STAFFING_LABEL = "At least one current bank employee must have Safe Access.";
    private static final UUID BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void grantAndRevokeSafeAccessDriveVaultAndObjectiveReadinessWithoutLosingSetupData() throws Exception {
        Object vault = vault();
        Object area = area(vault);
        Object premise = premise(area);
        Object snapshot = construct(
                load("bank.safebox.setup.SafeDepositSetupSnapshot"),
                new Class<?>[]{int.class, List.class},
                1, List.of(premise));
        Object summary = construct(
                load("bank.safebox.setup.SafeVaultReadinessSummary"),
                new Class<?>[]{String.class, boolean.class, List.class},
                "vault-1", true, List.of());
        Object readiness = construct(
                load("bank.safebox.setup.SafeVaultReadinessResolver$RowReadiness"),
                new Class<?>[]{boolean.class,
                        load("bank.safebox.setup.SafePremiseSnapshot"),
                        load("bank.safebox.setup.SafeAreaSnapshot"),
                        load("bank.safebox.setup.SafeVaultSnapshot"),
                        load("bank.safebox.setup.SafeVaultReadinessSummary"),
                        List.class},
                true, premise, area, vault, summary, List.of());
        Object metadata = construct(minecraft("nbt.CompoundTag"), new Class<?>[]{});
        String encodedEmployee = EMPLOYEE_ID + "=STAFF:125.50";
        invoke(metadata, "putString", new Class<?>[]{String.class, String.class}, "employees", encodedEmployee);

        Object noGrant = setupProjection(snapshot, List.of(readiness), metadata);
        assertStaffingBlocked(noGrant);

        assertEquals(true, staffingMutation("grantSafeAccess", metadata));
        Object granted = setupProjection(snapshot, List.of(readiness), metadata);
        assertEquals(true, value(value(granted, "objective"), "ready"));
        assertEquals(1, value(value(granted, "objective"), "readyVaultCount"));
        assertEquals(List.of(), listValue(value(granted, "objective"), "missingSteps"));
        Object grantedVault = listValue(granted, "vaults").getFirst();
        assertEquals("READY", value(grantedVault, "status"));
        assertEquals(true, value(grantedVault, "ready"));
        assertEquals(List.of(), listValue(grantedVault, "missingReasons"));

        assertEquals(true, staffingMutation("revokeSafeAccess", metadata));
        Object revoked = setupProjection(snapshot, List.of(readiness), metadata);
        assertStaffingBlocked(revoked);

        assertEquals(encodedEmployee, invoke(metadata, "getString", new Class<?>[]{String.class}, "employees"));
        assertEquals("premise-1", value(premise, "id"));
        assertEquals("safe-area-1", value(area, "id"));
        assertEquals("vault-1", value(vault, "id"));
        assertEquals(true, value(summary, "ready"), "staffing projection must not mutate source readiness");
        assertEquals(1, listValue(snapshot, "premises").size());
    }

    private static void assertStaffingBlocked(Object result) throws Exception {
        Object objective = value(result, "objective");
        assertEquals(false, value(objective, "ready"));
        assertEquals(1, value(objective, "premiseCount"));
        assertEquals(1, value(objective, "vaultCount"));
        assertEquals(0, value(objective, "readyVaultCount"));
        assertEquals(List.of(STAFFING_LABEL), listValue(objective, "missingSteps"));

        Object vaultPayload = listValue(result, "vaults").getFirst();
        assertEquals("NOT_READY", value(vaultPayload, "status"));
        assertEquals(false, value(vaultPayload, "ready"));
        assertEquals(List.of(STAFFING_REASON), listValue(vaultPayload, "missingReasons"));
        assertEquals(List.of(STAFFING_LABEL), listValue(vaultPayload, "missingReasonLabels"));
    }

    private static Object setupProjection(Object snapshot, List<?> readiness, Object metadata) throws Exception {
        Class<?> builder = load("bank.owner.setup.BankSafeSetupPayloadBuilder");
        Method method = builder.getDeclaredMethod(
                "buildFromSnapshot",
                load("bank.safebox.setup.SafeDepositSetupSnapshot"),
                List.class,
                minecraft("nbt.CompoundTag"));
        method.setAccessible(true);
        return method.invoke(null, snapshot, readiness, metadata);
    }

    private static boolean staffingMutation(String methodName, Object metadata) throws Exception {
        Method method = load("bank.owner.staffing.BankStaffingService")
                .getMethod(methodName, minecraft("nbt.CompoundTag"), UUID.class);
        return (Boolean) method.invoke(null, metadata, EMPLOYEE_ID);
    }

    private static Object premise(Object area) throws Exception {
        Class<?> mode = load("bank.safebox.setup.SafePremiseMode");
        return construct(
                load("bank.safebox.setup.SafePremiseSnapshot"),
                new Class<?>[]{String.class, String.class,
                        load("bank.safebox.setup.SafeBlockBounds"),
                        load("bank.safebox.setup.SafeExitSnapshot"), mode, List.class},
                "premise-1",
                BANK_ID.toString(),
                bounds(0, 10),
                construct(
                        load("bank.safebox.setup.SafeExitSnapshot"),
                        new Class<?>[]{String.class, int.class, int.class, int.class, float.class},
                        "minecraft:overworld", -1, 64, 0, 90.0F),
                enumValue(mode, "PUBLIC"),
                List.of(area));
    }

    private static Object area(Object vault) throws Exception {
        return construct(
                load("bank.safebox.setup.SafeAreaSnapshot"),
                new Class<?>[]{String.class, String.class,
                        load("bank.safebox.setup.SafeBlockBounds"), List.class},
                "safe-area-1", "premise-1", bounds(1, 9), List.of(vault));
    }

    private static Object vault() throws Exception {
        Class<?> status = load("bank.safebox.setup.SafeVaultSetupStatus");
        Object route = construct(
                load("bank.safebox.setup.SafeTellerRouteHook"),
                new Class<?>[]{String.class, boolean.class, String.class, String.class},
                "teller-1", true, "outbound-route", "return-route");
        return construct(
                load("bank.safebox.setup.SafeVaultSnapshot"),
                new Class<?>[]{String.class, String.class, String.class, status,
                        OptionalInt.class, OptionalInt.class, OptionalInt.class, OptionalInt.class, List.class},
                "vault-1", "safe-area-1", "minecraft:overworld", enumValue(status, "READY"),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), List.of(route));
    }

    private static Object bounds(int min, int max) throws Exception {
        return construct(
                load("bank.safebox.setup.SafeBlockBounds"),
                new Class<?>[]{String.class, int.class, int.class, int.class, int.class, int.class, int.class},
                "minecraft:overworld", min, 60, min, max, 70, max);
    }

    private static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listValue(Object target, String accessor) throws Exception {
        return (List<Object>) value(target, accessor);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) throws Exception {
        Constructor<?> constructor = type.getConstructor(parameterTypes);
        return constructor.newInstance(args);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private static Class<?> load(String relativeName) throws Exception {
        return Class.forName(ROOT_PACKAGE + relativeName, true, NeoForgeTestClassLoader.get());
    }

    private static Class<?> minecraft(String relativeName) throws Exception {
        return Class.forName("net.minecraft." + relativeName, true, NeoForgeTestClassLoader.get());
    }
}
