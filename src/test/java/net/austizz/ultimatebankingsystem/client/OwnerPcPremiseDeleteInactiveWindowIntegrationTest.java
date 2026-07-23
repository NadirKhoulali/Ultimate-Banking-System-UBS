package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.enumFieldName;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.fieldValue;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.invokeNoArgs;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.load;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.loadedPendingModalScreen;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.pendingDelete;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.setEnumField;
import static net.austizz.ultimatebankingsystem.client.OwnerPcPremisesPanelScreenFixture.setField;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class OwnerPcPremiseDeleteInactiveWindowIntegrationTest {
    private static final UUID STORED_BANK_ID = UUID.fromString("9d48ad1f-45e4-4ed5-a86b-9227b9ccf0d8");
    private static final UUID ACTIVE_BANK_ID = UUID.fromString("2c4eb670-2db4-4f9e-98ee-e820f26d9592");
    private static final UUID STORED_OPERATION_ID = UUID.fromString("1a4284cb-d85e-43d2-b740-a0f2fb2a8fd2");
    private static final UUID ACTIVE_OPERATION_ID = UUID.fromString("82b52699-bd21-46b1-bbbb-c94dfac92130");
    private static final String STORED_PREMISE_ID = "premise-main";

    @Test
    void exactDeleteUpdatesOnlyMatchingSavedInactiveWindow() throws Exception {
        OwnerPcPremisesPanelScreenFixture.LoadedModalScreen fixture = loadedPendingModalScreen();
        Object screen = fixture.screen();
        Object storedPending = fieldValue(screen, "premiseDeleteState");
        setField(screen, "activeBankId", STORED_BANK_ID);
        invokeNoArgs(screen, "saveActiveBankWindowState");

        invoke(screen, "loadBankWindowState", new Class<?>[]{UUID.class}, ACTIVE_BANK_ID);
        Object activePending = pendingDelete(ACTIVE_BANK_ID, "premise-active", ACTIVE_OPERATION_ID);
        setField(screen, "premiseDeleteState", activePending);
        setEnumField(screen, "bankActionModal", "PREMISE_DELETE_CONFIRM");

        @SuppressWarnings("unchecked")
        Map<UUID, Object> windows = (Map<UUID, Object>) fieldValue(screen, "bankWindows");
        Object storedWindow = windows.get(STORED_BANK_ID);
        assertSame(storedPending, fieldValue(storedWindow, "premiseDeleteState"));

        handle(screen, response(STORED_BANK_ID, ACTIVE_OPERATION_ID, STORED_PREMISE_ID));
        assertSame(storedPending, fieldValue(storedWindow, "premiseDeleteState"));
        assertSame(activePending, fieldValue(screen, "premiseDeleteState"));

        handle(screen, response(STORED_BANK_ID, STORED_OPERATION_ID, "premise-other"));
        assertSame(storedPending, fieldValue(storedWindow, "premiseDeleteState"));
        assertSame(activePending, fieldValue(screen, "premiseDeleteState"));

        handle(screen, response(STORED_BANK_ID, STORED_OPERATION_ID, STORED_PREMISE_ID));
        Object storedResult = fieldValue(storedWindow, "premiseDeleteState");
        assertFalse((boolean) storedResult.getClass().getMethod("modalOpen").invoke(storedResult));
        assertEquals("NONE", enumFieldName(storedWindow, "bankActionModal"));
        assertEquals(ACTIVE_BANK_ID, fieldValue(screen, "activeBankId"));
        assertSame(activePending, fieldValue(screen, "premiseDeleteState"));
        assertEquals("PREMISE_DELETE_CONFIRM", enumFieldName(screen, "bankActionModal"));

        invokeNoArgs(screen, "saveActiveBankWindowState");
        invoke(screen, "loadBankWindowState", new Class<?>[]{UUID.class}, STORED_BANK_ID);
        assertSame(storedResult, fieldValue(screen, "premiseDeleteState"));
        assertEquals("NONE", enumFieldName(screen, "bankActionModal"));
    }

    private static Object response(UUID bankId, UUID operationId, String premiseId) throws Exception {
        Class<?> actionType = load("network.OwnerPcPremiseActionPayload$Action");
        Object delete = enumConstant(actionType, "DELETE");
        Class<?> payloadType = load("network.OwnerPcPremiseActionResponsePayload");
        return payloadType.getConstructor(UUID.class, UUID.class, actionType,
                        String.class, boolean.class, String.class)
                .newInstance(bankId, operationId, delete, premiseId, true, "ok");
    }

    private static void handle(Object screen, Object payload) throws Exception {
        invoke(screen, "handlePremiseActionResponse", new Class<?>[]{payload.getClass()}, payload);
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown enum constant " + name);
    }

    private static Object invoke(Object target,
                                 String methodName,
                                 Class<?>[] parameterTypes,
                                 Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
