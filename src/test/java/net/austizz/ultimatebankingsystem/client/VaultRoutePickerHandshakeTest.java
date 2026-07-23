package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.direction;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.EDIT_SESSION_ID;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.editorPayload;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.enumName;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.face;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.handshake;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.openOwnerPcPayload;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.pickerMode;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.position;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.property;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.state;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.waitStep;
import static net.austizz.ultimatebankingsystem.client.VaultRouteEditorBehaviorHarness.zeroPosition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultRoutePickerHandshakeTest {
    private static final UUID BANK_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TELLER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @AfterEach
    void clearState() throws Exception {
        state("clear");
    }

    @Test
    void beginRemovalCaptureReopenAndRestoreKeepTheDraftAlive() throws Exception {
        openNewRoute();

        Object launch = handshake("begin", pickerMode("START"), -1);

        assertTrue((boolean) property(launch, "removeOwnerPcScreen"));
        assertTrue((boolean) state("isEditorOpen"));
        assertTrue(((List<?>) state("steps")).isEmpty());
        assertEquals("START", enumName(state("pickerMode")));

        Object selected = position(1_000, 70, -1_000);
        Object capture = handshake(
                "capture", "minecraft:overworld", selected, face("UP"));

        assertTrue((boolean) property(capture, "selectionAccepted"));
        assertEquals(openOwnerPcPayload(), property(capture, "reopenPayload"));
        assertEquals("minecraft:overworld", state("dimension"));
        assertEquals(selected, state("start"));
        assertTrue((boolean) state("hasStart"));
        assertTrue((boolean) state("shouldRestoreEditor"));

        state("markEditorRestored");
        assertFalse((boolean) state("shouldRestoreEditor"));
        assertTrue((boolean) state("isEditorOpen"));
        assertEquals(EDIT_SESSION_ID, state("editSessionId"));
    }

    @Test
    void dimensionMismatchRetainsEntireDraftAndStillRequestsReopen() throws Exception {
        openExistingRoute(waitStep(40));
        Object identity = state("identity");
        Object start = state("start");
        Object finish = state("finish");
        Object steps = state("steps");

        Object launch = handshake("begin", pickerMode("WALK"), -1);
        assertTrue((boolean) property(launch, "removeOwnerPcScreen"));
        assertEquals(identity, state("identity"));
        assertEquals(start, state("start"));
        assertEquals(finish, state("finish"));
        assertEquals(steps, state("steps"));

        Object capture = handshake(
                "capture", "minecraft:the_nether", position(1, 65, 1), face("NORTH"));

        assertFalse((boolean) property(capture, "selectionAccepted"));
        assertEquals(openOwnerPcPayload(), property(capture, "reopenPayload"));
        assertEquals(identity, state("identity"));
        assertEquals("minecraft:overworld", state("dimension"));
        assertEquals(start, state("start"));
        assertEquals(finish, state("finish"));
        assertEquals(steps, state("steps"));
        assertEquals("NONE", enumName(state("pickerMode")));
        assertTrue((boolean) state("shouldRestoreEditor"));
        assertFalse((boolean) state("messageSuccess"));
    }

    private static void openNewRoute() throws Exception {
        beginRequest();
        assertTrue((boolean) state("acceptServerResponse", editorPayload(
                true,
                "New route",
                BANK_ID,
                "vault-a",
                TELLER_ID,
                "OUTBOUND",
                false,
                "",
                zeroPosition(),
                zeroPosition(),
                List.of())));
    }

    private static void openExistingRoute(Object step) throws Exception {
        beginRequest();
        assertTrue((boolean) state("acceptServerResponse", editorPayload(
                true,
                "Existing route",
                BANK_ID,
                "vault-a",
                TELLER_ID,
                "OUTBOUND",
                true,
                "minecraft:overworld",
                position(0, 64, 0),
                position(8, 64, 8),
                List.of(step))));
    }

    private static void beginRequest() throws Exception {
        state("openDetails", BANK_ID, TELLER_ID);
        state("requestEditor", "vault-a", direction("OUTBOUND"));
    }
}
