package net.austizz.ultimatebankingsystem.bank.owner.route;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteServiceHarness.saveFor;
import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnerPcVaultRouteTransportTest {
    @Test
    void typedCodecsRoundTripEveryFieldAndOrderedSteps() throws Exception {
        Object request = request();
        assertEquals(request, roundTrip("OwnerPcVaultRouteRequestPayload", request));
        Object save = validSave();
        Object decodedSave = roundTrip("OwnerPcVaultRouteSavePayload", save);
        assertEquals(save, decodedSave);
        Object editor = editor(save);
        assertEquals(editor, roundTrip("OwnerPcVaultRouteEditorPayload", editor));
        Object rfid = rfid(14, 64, 14);
        assertEquals(rfid, roundTrip("OwnerPcVaultRouteStepPayload", rfid));
        assertEquals(cancel(SESSION_ID),
                roundTrip("OwnerPcVaultRouteCancelPayload", cancel(SESSION_ID)));
    }

    @Test
    void sessionPayloadsRejectNullAndZeroTokensIncludingDuringDecode() throws Exception {
        UUID zero = new UUID(0L, 0L);
        assertConstructorRejects(() -> validSave(null));
        assertConstructorRejects(() -> validSave(zero));
        assertConstructorRejects(() -> cancel(null));
        assertConstructorRejects(() -> cancel(zero));
        assertConstructorRejects(() -> editorWithSession(zero, 61_000L));
        assertConstructorRejects(() -> editorWithSession(null, 61_000L));

        Object encoded = buffer();
        try {
            encode("OwnerPcVaultRouteSavePayload", encoded, validSave());
            write(encoded, "setLong", new Class<?>[]{int.class, long.class}, 0, 0L);
            write(encoded, "setLong", new Class<?>[]{int.class, long.class}, 8, 0L);
            assertThrows(IllegalArgumentException.class,
                    () -> decode("OwnerPcVaultRouteSavePayload", encoded));
        } finally {
            release(encoded);
        }

        Object encodedCancel = buffer();
        try {
            write(encodedCancel, "writeUUID", new Class<?>[]{UUID.class}, zero);
            assertThrows(IllegalArgumentException.class,
                    () -> decode("OwnerPcVaultRouteCancelPayload", encodedCancel));
        } finally {
            release(encodedCancel);
        }
    }

    @Test
    void payloadsDefensivelyCopyAndRejectMaliciousBoundsAndTags() throws Exception {
        List<Object> mutable = new ArrayList<>(list(validSave(), "steps"));
        Object save = save("OUTBOUND", DIMENSION, position(10, 64, 10),
                position(20, 64, 20), mutable);
        mutable.clear();
        assertEquals(3, list(save, "steps").size());
        assertThrows(UnsupportedOperationException.class,
                () -> list(save, "steps").add(waitStep(1)));
        assertDoesNotThrow(() -> save("OUTBOUND", DIMENSION, position(10, 64, 10),
                position(20, 64, 20), Collections.nCopies(256, waitStep(1))));
        assertConstructorRejects(() -> save("OUTBOUND", DIMENSION, position(10, 64, 10),
                position(20, 64, 20), Collections.nCopies(257, waitStep(1))));
        assertConstructorRejects(() -> constructRequest("v".repeat(129), "OUTBOUND"));
        assertConstructorRejects(() -> constructRequest(VAULT_ID, null));

        Object invalidDirection = buffer();
        try {
            write(invalidDirection, "writeUUID", new Class<?>[]{java.util.UUID.class}, BANK_ID);
            write(invalidDirection, "writeUtf", new Class<?>[]{String.class, int.class}, VAULT_ID, 128);
            write(invalidDirection, "writeUUID", new Class<?>[]{java.util.UUID.class}, TELLER_ID);
            write(invalidDirection, "writeUtf", new Class<?>[]{String.class, int.class}, "SIDEWAYS", 16);
            assertThrows(IllegalArgumentException.class,
                    () -> decode("OwnerPcVaultRouteRequestPayload", invalidDirection));
        } finally {
            release(invalidDirection);
        }
        Object invalidType = buffer();
        try {
            write(invalidType, "writeByte", new Class<?>[]{int.class}, 99);
            assertThrows(IllegalArgumentException.class,
                    () -> decode("OwnerPcVaultRouteStepPayload", invalidType));
        } finally {
            release(invalidType);
        }
    }

    @Test
    void accessPolicyDeniesLockedPoweredOffUnauthorizedAndInvalidTellers() throws Exception {
        assertDenied(access(false, true, true, true, false, true, true, true, false, true));
        assertDenied(access(true, false, true, true, false, true, true, true, false, true));
        assertDenied(access(true, true, false, true, false, true, true, true, false, true));
        assertDenied(access(true, true, true, false, false, true, true, true, false, true));
        assertAllowedResult(authorize(
                access(true, true, true, false, true, true, true, true, false, true)));
        assertDenied(access(true, true, true, true, false, false, false, false, false, true));
        assertDenied(access(true, true, true, true, false, true, false, false, false, true));
        assertDenied(access(true, true, true, true, false, true, true, false, false, true));
        assertDenied(access(true, true, true, true, false, true, true, true, true, true));
        assertDenied(access(true, true, true, true, false, true, true, true, false, false));
        assertAllowedResult(authorizeSave(
                access(false, false, false, true, false,
                        true, true, true, false, true)));
    }

    @Test
    void draftPolicyRejectsDimensionUnloadedPointsAndWrongEndpoints() throws Exception {
        Object save = validSave();
        assertDeniedResult(validate(save("OUTBOUND", "minecraft:the_nether",
                value(save, "start"), value(save, "finish"), list(save, "steps")),
                safeBounds(), DIMENSION, ignored -> true));
        Object start = position(10, 64, 10);
        assertDeniedResult(validate(save, safeBounds(), DIMENSION, point -> !point.equals(start)));
        Object finishAndRedstoneTarget = position(20, 64, 20);
        assertDeniedResult(validate(save, safeBounds(), DIMENSION,
                point -> !point.equals(finishAndRedstoneTarget)));
        Object walkTarget = position(11, 64, 10);
        assertDeniedResult(validate(save, safeBounds(), DIMENSION, point -> !point.equals(walkTarget)));
        Object relay = position(20, 64, 19);
        assertDeniedResult(validate(save, safeBounds(), DIMENSION, point -> !point.equals(relay)));
        assertDeniedResult(validate(save("OUTBOUND", DIMENSION, position(0, 64, 0),
                value(save, "finish"), list(save, "steps")), safeBounds(), DIMENSION,
                ignored -> true));
        assertDeniedResult(validate(save("RETURN", DIMENSION, position(20, 64, 20),
                position(0, 64, 0), list(save, "steps")), safeBounds(), DIMENSION,
                ignored -> true));
        assertAllowedResult(validate(save, safeBounds(), DIMENSION, ignored -> true));
        assertAllowedResult(validate(save("RETURN", DIMENSION, position(20, 64, 20),
                position(10, 64, 10), list(save, "steps")), safeBounds(), DIMENSION,
                ignored -> true));
    }

    @Test
    void draftPolicyRejectsOutsideWalksAndInvalidRfidTargets() throws Exception {
        Object start = position(10, 64, 10);
        Object finish = position(20, 64, 20);
        Object outsideWalk = save("OUTBOUND", DIMENSION, start, finish,
                List.of(walk(31, 64, 10)));
        Object outsideResult = validate(outsideWalk, premiseBounds(), safeBounds(), DIMENSION,
                ignored -> true, ignored -> true);
        assertDeniedMessage(outsideResult, "cannot walk outside");

        Object outsideRfid = save("OUTBOUND", DIMENSION, start, finish,
                List.of(rfid(31, 64, 10)));
        Object outsideRfidResult = validate(outsideRfid, premiseBounds(), safeBounds(), DIMENSION,
                ignored -> true, ignored -> true);
        assertDeniedMessage(outsideRfidResult, "only be linked inside");

        Object insideRfid = save("OUTBOUND", DIMENSION, start, finish,
                List.of(rfid(14, 64, 14)));
        Object wrongBlockResult = validate(insideRfid, premiseBounds(), safeBounds(), DIMENSION,
                ignored -> true, ignored -> false);
        assertDeniedMessage(wrongBlockResult, "not an RFID scanner");
        assertAllowedResult(validate(insideRfid, premiseBounds(), safeBounds(), DIMENSION,
                ignored -> true, ignored -> true));
    }

    @Test
    void routeIdIsStableDistinctAndPreservedThroughCodecMapping() throws Exception {
        Object save = validSave();
        Object mapped = map(save);
        String routeId = (String) value(mapped, "id");
        Object sameIdentity = saveFor(new UUID(8L, 8L),
                BANK_ID, VAULT_ID, TELLER_ID, "OUTBOUND");
        Object changedIdentity = saveFor(SESSION_ID,
                BANK_ID, VAULT_ID, new UUID(7L, 7L), "OUTBOUND");

        assertFalse(routeId.isBlank());
        assertEquals(routeId, value(map(roundTrip("OwnerPcVaultRouteSavePayload", save)), "id"));
        assertEquals(routeId, value(map(sameIdentity), "id"));
        assertNotEquals(routeId, value(map(changedIdentity), "id"));
        assertEquals(3, list(mapped, "steps").size());
        assertEquals(DIMENSION, value(mapped, "dimension"));
    }

    private static Object constructRequest(String vaultId, String direction) throws Exception {
        Object enumValue = direction == null ? null : Enum.valueOf(
                safeRoute("SafeTellerRouteDirection").asSubclass(Enum.class), direction);
        return network("OwnerPcVaultRouteRequestPayload").getConstructors()[0]
                .newInstance(BANK_ID, vaultId, TELLER_ID, enumValue);
    }

    private static Object editorWithSession(UUID token, long expiry) throws Exception {
        Object zero = position(0, 0, 0);
        return network("OwnerPcVaultRouteEditorPayload").getConstructors()[0].newInstance(
                true, "Route ready.", token, expiry, BANK_ID, VAULT_ID, TELLER_ID,
                direction("OUTBOUND"), false, "", zero, zero, List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object target, String method) throws Exception {
        return (List<Object>) value(target, method);
    }

    private static void assertConstructorRejects(ThrowingCall call) {
        InvocationTargetException error = assertThrows(InvocationTargetException.class, call::run);
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private static void assertDenied(Object facts) throws Exception {
        assertDeniedResult(authorize(facts));
    }

    private static void assertDeniedResult(Object result) throws Exception {
        assertFalse((Boolean) value(result, "allowed"));
    }

    private static void assertAllowedResult(Object result) throws Exception {
        assertTrue((Boolean) value(result, "allowed"));
    }

    private static void assertDeniedMessage(Object result, String expected) throws Exception {
        assertDeniedResult(result);
        assertTrue(((String) value(result, "message")).contains(expected));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
