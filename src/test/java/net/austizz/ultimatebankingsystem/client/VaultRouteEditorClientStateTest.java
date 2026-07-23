package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.direction;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.EDIT_SESSION_ID;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.editorPayload;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.editorPayloadWithoutSession;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.enumName;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.face;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.pickerMode;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.position;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.property;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.redstoneStep;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.savePayload;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.state;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.validatorConstant;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.waitStep;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.walkStep;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.zeroPosition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultRouteEditorClientStateTest {
    private static final UUID BANK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TELLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String VAULT_ID = "vault-alpha";

    @AfterEach
    void clearState() throws Exception {
        state("clear");
    }

    @Test
    void retainsIdentityAndDraftAcrossScreenRestore() throws Exception {
        Object wait = waitStep(20);
        openOutboundRoute(List.of(wait));
        state("beginPicker", pickerMode("FINISH"), -1);
        Object selected = position(9, 65, 9);
        assertTrue((boolean) state("captureSelection",
                "minecraft:overworld", selected, face("UP")));

        assertTrue((boolean) state("shouldRestoreEditor"));
        Object identity = state("identity");
        assertEquals(BANK_ID, property(identity, "bankId"));
        assertEquals(TELLER_ID, property(identity, "tellerId"));
        assertEquals(VAULT_ID, property(identity, "vaultId"));
        assertEquals(EDIT_SESSION_ID, state("editSessionId"));
        assertEquals(selected, state("finish"));
        assertEquals(List.of(wait), state("steps"));

        state("markEditorRestored");
        assertFalse((boolean) state("shouldRestoreEditor"));
        assertTrue((boolean) state("isEditorOpen"));
    }

    @Test
    void pickerModeCapturesRedstoneBlockAndClickedFace() throws Exception {
        openOutboundRoute(List.of());
        state("beginRedstonePicker", -1, 12, 45);

        assertEquals("REDSTONE", enumName(state("pickerMode")));
        Object selected = position(4, 70, -3);
        assertTrue((boolean) state("captureSelection",
                "minecraft:overworld", selected, face("NORTH")));

        assertEquals("NONE", enumName(state("pickerMode")));
        assertEquals(List.of(redstoneStep(selected, "NORTH", 12, 45)), state("steps"));
    }

    @Test
    void orderedInsertEditDeleteAndReorderAreStable() throws Exception {
        openOutboundRoute(List.of());
        Object walkTarget = position(2, 64, 2);
        state("insertWait", 0, 20);
        state("insertWalk", 1, walkTarget);
        state("insertRedstone", 1, position(3, 64, 3), face("EAST"), 10, 30);
        state("updateWait", 0, 35);
        state("moveStep", 2, -1);
        state("deleteStep", 2);

        assertEquals(List.of(waitStep(35), walkStep(walkTarget)), state("steps"));
    }

    @Test
    void waitAndRedstoneInputsAreBoundedBeforeTheyReachPayloads() throws Exception {
        openOutboundRoute(List.of());
        Object target = position(3, 64, 3);
        state("insertWait", 0, Integer.MAX_VALUE);
        state("insertRedstone", 1, target, face("DOWN"), -4, Integer.MAX_VALUE);

        int maxWait = validatorConstant("MAX_WAIT_TICKS");
        int maxRedstone = validatorConstant("MAX_REDSTONE_DURATION_TICKS");
        assertEquals(List.of(
                waitStep(maxWait),
                redstoneStep(target, "DOWN", 1, maxRedstone)
        ), state("steps"));
    }

    @Test
    void newRouteResponseStartsWithUnboundEmptyDraft() throws Exception {
        openOutboundRoute(List.of());

        assertEquals("", state("dimension"));
        assertFalse((boolean) state("hasStart"));
        assertFalse((boolean) state("hasFinish"));
        assertEquals(zeroPosition(), state("start"));
        assertEquals(zeroPosition(), state("finish"));
        assertTrue(((List<?>) state("steps")).isEmpty());
    }

    @Test
    void staleServerResponseIsRejectedWithoutReplacingDraft() throws Exception {
        openOutboundRoute(List.of(waitStep(20)));
        state("requestEditor", "vault-beta", direction("RETURN"));

        boolean accepted = (boolean) state("acceptServerResponse", existingRoutePayload(
                true, BANK_ID, VAULT_ID, TELLER_ID, "OUTBOUND", List.of(waitStep(99))));

        assertFalse(accepted);
        assertEquals("vault-beta", property(state("identity"), "vaultId"));
        assertTrue(((List<?>) state("steps")).isEmpty());
    }

    @Test
    void failedMatchingServerResponsePreservesCurrentDraft() throws Exception {
        openOutboundRoute(List.of(waitStep(20)));
        state("updateWait", 0, 40);
        state("expectServerResponse");

        assertTrue((boolean) state("acceptServerResponse", editorPayload(
                false, "Route rejected by server", BANK_ID, VAULT_ID, TELLER_ID,
                "OUTBOUND", false, "", zeroPosition(), zeroPosition(), List.of())));
        assertEquals(List.of(waitStep(40)), state("steps"));
        assertEquals(EDIT_SESSION_ID, state("editSessionId"));
        assertEquals("Route rejected by server", state("message"));
        assertFalse((boolean) state("messageSuccess"));
    }

    @Test
    void savePayloadExactlyMatchesRetainedDraft() throws Exception {
        Object start = position(1, 64, 1);
        Object finish = position(8, 64, 8);
        List<?> steps = List.of(
                walkStep(position(2, 64, 2)),
                waitStep(40),
                redstoneStep(position(3, 64, 3), "WEST", 7, 60));
        openOutboundRoute(steps);

        Object expected = savePayload(
                BANK_ID, VAULT_ID, TELLER_ID, "OUTBOUND", "minecraft:overworld",
                start, finish, steps);

        assertEquals(expected, state("toSavePayload"));
    }

    @Test
    void cancelDiscardsOnlyRouteDraftAndKeepsTellerDetails() throws Exception {
        openOutboundRoute(List.of(waitStep(20)));

        assertEquals(EDIT_SESSION_ID, state("cancelEditor"));

        assertFalse((boolean) state("isEditorOpen"));
        assertTrue((boolean) state("isDetailsOpen"));
        assertEquals(BANK_ID, state("selectedBankId"));
        assertEquals(TELLER_ID, state("selectedTellerId"));
        assertNull(state("identity"));
        assertNull(state("editSessionId"));
        assertTrue(((List<?>) state("steps")).isEmpty());
    }

    @Test
    void successfulSaveResponseConsumesClientSessionAndClosesOnlyEditor() throws Exception {
        openOutboundRoute(List.of(waitStep(20)));
        state("expectServerResponse");

        assertTrue((boolean) state("acceptServerResponse", editorPayloadWithoutSession(
                true, "Route saved.", BANK_ID, VAULT_ID, TELLER_ID, "OUTBOUND",
                true, "minecraft:overworld", position(1, 64, 1),
                position(8, 64, 8), List.of(waitStep(20)))));

        assertFalse((boolean) state("isEditorOpen"));
        assertTrue((boolean) state("isDetailsOpen"));
        assertNull(state("identity"));
        assertNull(state("editSessionId"));
        assertEquals("Route saved.", state("message"));
    }

    private static void openOutboundRoute(List<?> steps) throws Exception {
        state("openDetails", BANK_ID, TELLER_ID);
        state("requestEditor", VAULT_ID, direction("OUTBOUND"));
        Object payload = steps.isEmpty()
                ? editorPayload(true, "New route", BANK_ID, VAULT_ID, TELLER_ID,
                        "OUTBOUND", false, "", zeroPosition(), zeroPosition(), List.of())
                : existingRoutePayload(
                        true, BANK_ID, VAULT_ID, TELLER_ID, "OUTBOUND", steps);
        assertTrue((boolean) state("acceptServerResponse", payload));
    }

    private static Object existingRoutePayload(boolean success,
                                               UUID bankId,
                                               String vaultId,
                                               UUID tellerId,
                                               String directionName,
                                               List<?> steps) throws Exception {
        return editorPayload(
                success,
                success ? "Route ready" : "Route failed",
                bankId,
                vaultId,
                tellerId,
                directionName,
                true,
                "minecraft:overworld",
                position(1, 64, 1),
                position(8, 64, 8),
                steps);
    }
}
