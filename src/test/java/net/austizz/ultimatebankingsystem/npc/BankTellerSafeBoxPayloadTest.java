package net.austizz.ultimatebankingsystem.npc;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BankTellerSafeBoxPayloadTest {
    private static final String STATE_CLASS =
            "net.austizz.ultimatebankingsystem.network.BankTellerSafeBoxState";
    private static final UUID REQUESTED_TELLER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TELLER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID BANK_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CHECKING_ACCOUNT = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SAVINGS_ACCOUNT = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Test
    void requestUsesSelectedAccountsExactAssignmentAndRequestedTeller() throws Exception {
        Class<?> stateClass = load(STATE_CLASS);
        Class<?> assignmentClass = load(STATE_CLASS + "$AccountAssignment");
        Object checking = assignment(assignmentClass, CHECKING_ACCOUNT, "SDB-0007", "vault-east", 1, true, List.of());
        Object savings = assignment(assignmentClass, SAVINGS_ACCOUNT, "SDB-0009", "vault-west", 3, true, List.of());
        Object state = state(stateClass, REQUESTED_TELLER, true, List.of(), List.of(checking, savings));

        Object result = invoke(state, "validateOpenRequest",
                new Class<?>[]{UUID.class, UUID.class}, REQUESTED_TELLER, SAVINGS_ACCOUNT);
        assertEquals(true, invoke(result, "success"));
        assertEquals("NONE", String.valueOf(invoke(result, "failure")));
        assertEquals(false, invoke(result, "queueRequested"));
        Object selected = invoke(result, "assignment");
        assertEquals(SAVINGS_ACCOUNT, invoke(selected, "accountId"));
        assertEquals("vault-west", invoke(selected, "vaultId"));
        assertEquals(3, invoke(selected, "doorIndex"));

        Object forged = invoke(state, "validateOpenRequest",
                new Class<?>[]{UUID.class, UUID.class}, OTHER_TELLER, SAVINGS_ACCOUNT);
        assertEquals("INVALID_TELLER", String.valueOf(invoke(forged, "failure")));
        assertNull(invoke(forged, "assignment"));
    }

    @Test
    void unreadyBankDisablesOpenRequestWithReasons() throws Exception {
        Class<?> stateClass = load(STATE_CLASS);
        Class<?> assignmentClass = load(STATE_CLASS + "$AccountAssignment");
        Object checking = assignment(assignmentClass, CHECKING_ACCOUNT, "SDB-0012", "vault-north", 0, false,
                List.of("Complete BANK_VAULT_DOOR multiblock is missing."));
        Object state = state(stateClass, REQUESTED_TELLER, false,
                List.of("No ready safe-deposit vault is configured.", "Teller route is unavailable."),
                List.of(checking));

        assertEquals(false, invoke(state, "bankHasReadyVault"));
        assertEquals(List.of("No ready safe-deposit vault is configured.", "Teller route is unavailable."),
                invoke(state, "missingReasons"));
        Object result = invoke(state, "validateOpenRequest",
                new Class<?>[]{UUID.class, UUID.class}, REQUESTED_TELLER, CHECKING_ACCOUNT);
        assertEquals("BANK_NOT_READY", String.valueOf(invoke(result, "failure")));
        assertEquals(false, invoke(result, "queueRequested"));
    }

    @Test
    void rentalAndOpenRequestRemainDistinct() throws Exception {
        Class<?> stateClass = load(STATE_CLASS);
        String rental = String.valueOf(stateClass.getField("REQUEST_RENTAL_SAFE_BOX_ACTION").get(null));
        String open = String.valueOf(stateClass.getField("REQUEST_OPEN_SAFE_BOX_ACTION").get(null));
        assertEquals("REQUEST_SAFE_BOX", rental);
        assertEquals("REQUEST_OPEN_SAFE_BOX", open);
        assertFalse(rental.equals(open));
        assertNotNull(stateClass.getField("STREAM_CODEC"));
    }

    private static Object state(Class<?> stateClass,
                                UUID tellerId,
                                boolean ready,
                                List<String> reasons,
                                List<Object> assignments) throws Exception {
        Constructor<?> ctor = stateClass.getConstructor(UUID.class, UUID.class, boolean.class, List.class, List.class);
        return ctor.newInstance(tellerId, BANK_ID, ready, reasons, assignments);
    }

    private static Object assignment(Class<?> assignmentClass,
                                     UUID accountId,
                                     String boxNumber,
                                     String vaultId,
                                     int doorIndex,
                                     boolean ready,
                                     List<String> reasons) throws Exception {
        Constructor<?> ctor = assignmentClass.getConstructor(
                UUID.class, String.class, String.class, int.class, int.class, int.class,
                int.class, String.class, boolean.class, boolean.class, List.class);
        return ctor.newInstance(accountId, boxNumber, "minecraft:overworld", 12, 64, -5,
                doorIndex, vaultId, ready, false, reasons);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private static Object invoke(Object target,
                                 String methodName,
                                 Class<?>[] parameterTypes,
                                 Object... args) throws Exception {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    private static Class<?> load(String className) throws Exception {
        return Class.forName(className, true, NeoForgeTestClassLoader.get());
    }
}
