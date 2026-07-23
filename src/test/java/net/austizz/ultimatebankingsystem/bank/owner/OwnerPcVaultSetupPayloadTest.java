package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnerPcVaultSetupPayloadTest {
    private static final String NETWORK_PACKAGE = "net.austizz.ultimatebankingsystem.network.";

    @Test
    void staffingPayloadIsTypedAndPreservesSafeAccess() throws Exception {
        UUID employeeId = UUID.fromString("10000000-0000-0000-0000-000000000101");
        Class<?> employeeClass = load(NETWORK_PACKAGE + "OwnerPcPlayerEmployeePayload");
        Constructor<?> employeeCtor = employeeClass.getConstructor(
                UUID.class, String.class, String.class, String.class, boolean.class, boolean.class);
        Object employee = employeeCtor.newInstance(employeeId, "Austizz", "DIRECTOR", "150.00", true, true);

        assertEquals(employeeId, invoke(employee, "playerId"));
        assertEquals("Austizz", invoke(employee, "name"));
        assertEquals("DIRECTOR", invoke(employee, "role"));
        assertEquals("150.00", invoke(employee, "salary"));
        assertEquals(true, invoke(employee, "online"));
        assertEquals(true, invoke(employee, "safeAccess"));
        assertNotNull(employeeClass.getField("STREAM_CODEC"));

        Class<?> tellerClass = load(NETWORK_PACKAGE + "OwnerPcBankTellerPayload");
        assertNotNull(tellerClass.getConstructor(
                UUID.class, String.class, int.class, String.class,
                int.class, int.class, int.class, boolean.class));
        assertNotNull(tellerClass.getField("STREAM_CODEC"));
    }

    @Test
    void reportsPerVaultReadinessAndImmutableMissingReasons() throws Exception {
        Class<?> vaultClass = load(NETWORK_PACKAGE + "OwnerPcVaultSetupPayload");
        Constructor<?> ctor = vaultClass.getConstructor(
                String.class, String.class, String.class, String.class, boolean.class,
                String.class, String.class, String.class, boolean.class, List.class,
                List.class, String.class, String.class, String.class);
        Object vault = ctor.newInstance(
                "premise-1",
                "vault-1",
                "PUBLIC",
                "minecraft:overworld 0,62,0 -> 6,67,6",
                true,
                "safe-area-1",
                "minecraft:overworld 1,63,1 -> 5,66,5",
                "NOT_READY",
                false,
                List.of("VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING"),
                List.of("Complete BANK_VAULT_DOOR multiblock is missing."),
                "MISSING",
                "MISSING",
                "MISSING"
        );

        assertEquals("vault-1", invoke(vault, "vaultId"));
        assertEquals(false, invoke(vault, "ready"));
        @SuppressWarnings("unchecked")
        List<String> missingReasons = (List<String>) invoke(vault, "missingReasons");
        assertEquals(List.of("VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING"),
                missingReasons);
        assertThrows(UnsupportedOperationException.class, () -> missingReasons.add("MUTATED"));
        assertNotNull(vaultClass.getField("STREAM_CODEC"));
    }

    @Test
    void setupObjectiveHasStableOrderedSteps() throws Exception {
        Class<?> objectiveClass = load(NETWORK_PACKAGE + "OwnerPcSetupObjectivePayload");
        Constructor<?> ctor = objectiveClass.getConstructor(
                boolean.class, int.class, int.class, int.class, List.class);
        Object objective = ctor.newInstance(
                false,
                1,
                2,
                0,
                List.of("Install a complete Bank Vault Door.", "Fill one deposit row.", "Configure teller routes.")
        );

        assertEquals(false, invoke(objective, "ready"));
        assertEquals(1, invoke(objective, "premiseCount"));
        assertEquals(2, invoke(objective, "vaultCount"));
        assertEquals(0, invoke(objective, "readyVaultCount"));
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) invoke(objective, "missingSteps");
        assertEquals(List.of(
                "Install a complete Bank Vault Door.",
                "Fill one deposit row.",
                "Configure teller routes."
        ), steps);
        assertThrows(UnsupportedOperationException.class, () -> steps.add("MUTATED"));
        assertNotNull(objectiveClass.getField("STREAM_CODEC"));
    }

    @Test
    void explicitEmptyPremiseStateIsDistinctFromMissingPremiseAndVault() throws Exception {
        Class<?> objectiveClass = load(NETWORK_PACKAGE + "OwnerPcSetupObjectivePayload");
        Constructor<?> ctor = objectiveClass.getConstructor(
                boolean.class, int.class, int.class, int.class, List.class);
        Object objective = ctor.newInstance(
                false,
                1,
                0,
                0,
                List.of("Claim a safe area in Bank Owner PC > Safe > Claim Safe Area."));

        assertEquals(false, invoke(objective, "ready"));
        assertEquals(1, invoke(objective, "premiseCount"));
        assertEquals(0, invoke(objective, "vaultCount"));
        assertEquals(0, invoke(objective, "readyVaultCount"));
        assertEquals(List.of("Claim a safe area in Bank Owner PC > Safe > Claim Safe Area."),
                invoke(objective, "missingSteps"));
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Class<?> load(String className) throws Exception {
        return Class.forName(className, true, NeoForgeTestClassLoader.get());
    }
}
