package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.gui.screens.OwnerPcPremiseDeleteLifecycle;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremiseDeleteLifecycleTest {
    private static final UUID BANK_ID = UUID.fromString("9d48ad1f-45e4-4ed5-a86b-9227b9ccf0d8");
    private static final UUID OTHER_BANK_ID = UUID.fromString("2c4eb670-2db4-4f9e-98ee-e820f26d9592");
    private static final UUID FIRST_OPERATION_ID = UUID.fromString("1a4284cb-d85e-43d2-b740-a0f2fb2a8fd2");
    private static final UUID SECOND_OPERATION_ID = UUID.fromString("82b52699-bd21-46b1-bbbb-c94dfac92130");
    private static final String PREMISE_ID = "premise-main";

    @Test
    void closedDeleteLifecycleHasNoModalAndCannotConfirmOrDismiss() {
        OwnerPcPremiseDeleteLifecycle.State closed = OwnerPcPremiseDeleteLifecycle.closed();

        assertFalse(closed.modalOpen());
        assertFalse(closed.pending());
        assertFalse(closed.canDismiss());
        assertFalse(closed.canConfirm());
    }

    @Test
    void openStatePreservesExactNonblankPremiseIdentityAndRejectsBlank() {
        String exactPremiseId = " " + PREMISE_ID + " ";

        assertEquals(exactPremiseId,
                OwnerPcPremiseDeleteLifecycle.confirming(BANK_ID, exactPremiseId).premiseId());
        assertThrows(IllegalArgumentException.class,
                () -> OwnerPcPremiseDeleteLifecycle.confirming(BANK_ID, null));
        assertThrows(IllegalArgumentException.class,
                () -> OwnerPcPremiseDeleteLifecycle.confirming(BANK_ID, "   "));
    }

    @Test
    void pendingStateBlocksEveryDismissalAndDuplicateConfirm() {
        OwnerPcPremiseDeleteLifecycle.State confirming =
                OwnerPcPremiseDeleteLifecycle.confirming(BANK_ID, PREMISE_ID);

        assertTrue(confirming.modalOpen());
        assertTrue(confirming.canDismiss(), "Confirmation must remain cancellable before a request is sent");
        assertTrue(confirming.canConfirm());

        OwnerPcPremiseDeleteLifecycle.Confirmation first = confirming.confirm(FIRST_OPERATION_ID);
        assertTrue(first.shouldSend());
        assertEquals(BANK_ID, first.request().bankId());
        assertEquals(PREMISE_ID, first.request().premiseId());
        assertEquals(FIRST_OPERATION_ID, first.request().operationId());
        assertTrue(first.state().pending());
        assertFalse(first.state().canDismiss(),
                "Pending ESC, cancel, and outside-click dismissal must all be blocked");
        assertFalse(first.state().canConfirm());

        OwnerPcPremiseDeleteLifecycle.Confirmation duplicate = first.state().confirm(SECOND_OPERATION_ID);
        assertFalse(duplicate.shouldSend(), "Duplicate confirm must not create another request");
        assertNull(duplicate.request());
        assertSame(first.state(), duplicate.state());
    }

    @Test
    void responseMustMatchActionBankPremiseAndOperationIdentity() {
        OwnerPcPremiseDeleteLifecycle.State pending = pending(FIRST_OPERATION_ID);

        assertIgnored(pending.handle(OwnerPcPremiseDeleteLifecycle.ResponseAction.OTHER,
                BANK_ID, PREMISE_ID, FIRST_OPERATION_ID, true), pending);
        assertIgnored(pending.handle(OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                OTHER_BANK_ID, PREMISE_ID, FIRST_OPERATION_ID, true), pending);
        assertIgnored(pending.handle(OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID, "premise-other", FIRST_OPERATION_ID, true), pending);
        assertIgnored(pending.handle(OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID, " " + PREMISE_ID + " ", FIRST_OPERATION_ID, true), pending);
        assertIgnored(pending.handle(OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID, PREMISE_ID, SECOND_OPERATION_ID, true), pending);
    }

    @Test
    void matchingFailureReturnsToRetryAndRejectsLateResponseFromOlderSamePremiseDelete() {
        OwnerPcPremiseDeleteLifecycle.State firstPending = pending(FIRST_OPERATION_ID);
        OwnerPcPremiseDeleteLifecycle.Response failed = firstPending.handle(
                OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID,
                PREMISE_ID,
                FIRST_OPERATION_ID,
                false);

        assertEquals(OwnerPcPremiseDeleteLifecycle.Outcome.RETRY, failed.outcome());
        assertTrue(failed.accepted());
        assertFalse(failed.shouldRefresh());
        assertTrue(failed.state().modalOpen());
        assertFalse(failed.state().pending());
        assertTrue(failed.state().canDismiss());
        assertTrue(failed.state().canConfirm());

        OwnerPcPremiseDeleteLifecycle.Confirmation retry = failed.state().confirm(SECOND_OPERATION_ID);
        assertTrue(retry.shouldSend());
        assertEquals(SECOND_OPERATION_ID, retry.request().operationId());
        assertIgnored(retry.state().handle(
                OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID,
                PREMISE_ID,
                FIRST_OPERATION_ID,
                true), retry.state());
    }

    @Test
    void matchingSuccessClosesModalAndSignalsRefresh() {
        OwnerPcPremiseDeleteLifecycle.Response succeeded = pending(FIRST_OPERATION_ID).handle(
                OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID,
                PREMISE_ID,
                FIRST_OPERATION_ID,
                true);

        assertEquals(OwnerPcPremiseDeleteLifecycle.Outcome.SUCCESS, succeeded.outcome());
        assertTrue(succeeded.accepted());
        assertTrue(succeeded.shouldRefresh());
        assertEquals(OwnerPcPremiseDeleteLifecycle.closed(), succeeded.state());
    }

    @Test
    void independentPendingStatesCorrelateResponsesSeparately() {
        OwnerPcPremiseDeleteLifecycle.State activeWindow = OwnerPcPremiseDeleteLifecycle
                .confirming(OTHER_BANK_ID, "premise-other")
                .confirm(SECOND_OPERATION_ID)
                .state();
        OwnerPcPremiseDeleteLifecycle.State inactiveWindow = pending(FIRST_OPERATION_ID);

        assertIgnored(activeWindow.handle(
                OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID,
                PREMISE_ID,
                FIRST_OPERATION_ID,
                true), activeWindow);
        OwnerPcPremiseDeleteLifecycle.Response inactiveResult = inactiveWindow.handle(
                OwnerPcPremiseDeleteLifecycle.ResponseAction.DELETE,
                BANK_ID,
                PREMISE_ID,
                FIRST_OPERATION_ID,
                true);
        assertEquals(OwnerPcPremiseDeleteLifecycle.Outcome.SUCCESS, inactiveResult.outcome());
        assertEquals(OwnerPcPremiseDeleteLifecycle.closed(), inactiveResult.state());
    }

    private static OwnerPcPremiseDeleteLifecycle.State pending(UUID operationId) {
        return OwnerPcPremiseDeleteLifecycle.confirming(BANK_ID, PREMISE_ID)
                .confirm(operationId)
                .state();
    }

    private static void assertIgnored(OwnerPcPremiseDeleteLifecycle.Response response,
                                      OwnerPcPremiseDeleteLifecycle.State expectedState) {
        assertEquals(OwnerPcPremiseDeleteLifecycle.Outcome.IGNORED, response.outcome());
        assertFalse(response.accepted());
        assertFalse(response.shouldRefresh());
        assertSame(expectedState, response.state());
    }
}
