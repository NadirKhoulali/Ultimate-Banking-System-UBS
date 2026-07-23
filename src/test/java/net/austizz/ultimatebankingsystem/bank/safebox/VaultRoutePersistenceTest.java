package net.austizz.ultimatebankingsystem.bank.safebox;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.safebox.VaultRouteNbtTestSupport.*;
import static net.austizz.ultimatebankingsystem.bank.safebox.VaultRouteTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultRoutePersistenceTest {
    @Test
    void emptySameBankSiblingDoesNotChangeExactPopulatedVaultRouteBinding() throws Exception {
        Object metadata = metadata();
        List<Object> premises = list(metadata, "safeDepositPremises");
        Object emptySibling = premises.get(1);
        putString(emptySibling, "bankId", "bank-1");
        Object outbound = route("teller-1", "OUTBOUND", List.of(waitStep(20)));
        Object returning = route("teller-1", "RETURN", List.of(waitStep(20)));

        assertSaved(save(metadata, outbound));
        assertSaved(save(metadata, returning));

        assertEquals(List.of(id(outbound), id(returning)),
                listForBank(metadata, "bank-1", "vault-1", "teller-1").stream()
                        .map(VaultRoutePersistenceTest::idUnchecked).toList());
        assertEquals(id(outbound), string(hook(metadata, "teller-1"), "outboundRouteRef"));
        assertEquals(id(returning), string(hook(metadata, "teller-1"), "returnRouteRef"));
        assertTrue(list(emptySibling, "safeAreas").isEmpty(),
                "the empty same-bank sibling must remain unrelated to the exact vault binding");
    }

    @Test
    void malformedOppositeLegRefRejectsSaveWithoutMutation() throws Exception {
        Object metadata = metadata();
        putInt(hook(metadata, "teller-1"), "returnRouteRef", 7);
        assertFailureAtomic("ROUTE_HOOKS_MALFORMED", metadata,
                route("teller-1", "OUTBOUND", List.of(waitStep(20))));
    }

    @Test
    void persistenceFailuresAreTypedAndAtomic() throws Exception {
        Object metadata = metadata();
        list(targetArea(metadata), "vaults").add(copyTag(targetVault(metadata)));
        assertFailureAtomic("VAULT_AMBIGUOUS", metadata, validRoute());
        metadata = metadata();
        list(targetVault(metadata), "routeHooks").add(copyTag(hook(metadata, "teller-1")));
        assertFailureAtomic("ROUTE_HOOK_AMBIGUOUS", metadata, validRoute());
        metadata = metadata();
        putString(metadata, "safeTellerRoutes", "not-a-list");
        assertFailureAtomic("ROUTE_STORAGE_MALFORMED", metadata, validRoute());
        metadata = metadata();
        putString(metadata, "safeDepositPremises", "not-a-list");
        assertFailureAtomic("PREMISES_MALFORMED", metadata, validRoute());
        metadata = metadata();
        putString(targetVault(metadata), "routeHooks", "not-a-list");
        assertFailureAtomic("ROUTE_HOOKS_MALFORMED", metadata, validRoute());
    }

    @Test
    void malformedMatchingHookFieldsAreRejectedAtomically() throws Exception {
        Object metadata = metadata();
        putString(hook(metadata, "teller-1"), "bankBound", "true");
        assertFailureAtomic("ROUTE_HOOKS_MALFORMED", metadata, validRoute());
        metadata = metadata();
        putInt(hook(metadata, "teller-1"), "outboundRouteRef", 1);
        assertFailureAtomic("ROUTE_HOOKS_MALFORMED", metadata, validRoute());
        metadata = metadata();
        Object malformedTeller = hook(metadata, "teller-1");
        putInt(malformedTeller, "tellerId", 1);
        assertFailureAtomic("ROUTE_HOOKS_MALFORMED", metadata, validRoute());
    }

    @Test
    void orderedRoundTripPreservesEveryFieldAndCustomTags() throws Exception {
        Object metadata = metadata();
        String unrelated = unrelated(metadata);
        Object outbound = route("teller-1", "OUTBOUND", List.of(
                walk(2, 64, 10), waitStep(20), redstone(3, 65, 11, "NORTH", 9, 40),
                rfid(4, 65, 11)));
        Object returning = route("teller-1", "RETURN", List.of(walk(1, 64, 9)));
        assertSaved(save(metadata, outbound));
        Object routeCustom = nested("route");
        Object walkCustom = nested("walk");
        Object waitCustom = nested("wait");
        Object redstoneCustom = nested("redstone");
        Object rfidCustom = nested("rfid");
        put(routeTag(metadata, id(outbound)), "customRoute", routeCustom);
        put(stepTag(metadata, id(outbound), 0), "customStep", walkCustom);
        put(stepTag(metadata, id(outbound), 1), "customStep", waitCustom);
        put(stepTag(metadata, id(outbound), 2), "customStep", redstoneCustom);
        put(stepTag(metadata, id(outbound), 3), "customStep", rfidCustom);
        Object hookCustom = copyTag(getTag(hook(metadata, "teller-1"), "customHook"));
        assertSaved(save(metadata, outbound));
        assertEquals("legacy-return", string(hook(metadata, "teller-1"), "returnRouteRef"));
        assertSaved(save(metadata, returning));
        Object restored = resolve(metadata, id(outbound)).orElseThrow();
        List<Object> restoredSteps = steps(restored);
        assertEquals(List.of("Walk", "Wait", "Redstone", "Rfid"), restoredSteps.stream()
                .map(step -> step.getClass().getSimpleName()).toList());
        assertPosition(value(restored, "start"), 1, 64, 9);
        assertPosition(value(restored, "finish"), 4, 64, 10);
        assertPosition(value(restoredSteps.get(0), "target"), 2, 64, 10);
        assertEquals(20, value(restoredSteps.get(1), "durationTicks"));
        assertPosition(value(restoredSteps.get(2), "target"), 3, 65, 11);
        assertEquals("NORTH", value(restoredSteps.get(2), "face").toString());
        assertEquals(9, value(restoredSteps.get(2), "strength"));
        assertEquals(40, value(restoredSteps.get(2), "durationTicks"));
        assertPosition(value(restoredSteps.get(3), "scanner"), 4, 65, 11);
        assertEquals(routeCustom, getTag(routeTag(metadata, id(outbound)), "customRoute"));
        assertEquals(walkCustom, getTag(stepTag(metadata, id(outbound), 0), "customStep"));
        assertEquals(waitCustom, getTag(stepTag(metadata, id(outbound), 1), "customStep"));
        assertEquals(redstoneCustom, getTag(stepTag(metadata, id(outbound), 2), "customStep"));
        assertEquals(rfidCustom, getTag(stepTag(metadata, id(outbound), 3), "customStep"));
        assertEquals(hookCustom, getTag(hook(metadata, "teller-1"), "customHook"));
        assertEquals(id(outbound), string(hook(metadata, "teller-1"), "outboundRouteRef"));
        assertEquals(id(returning), string(hook(metadata, "teller-1"), "returnRouteRef"));
        assertEquals(List.of(2, 2, 0), List.of(readAll(metadata).size(),
                listFor(metadata, "vault-1", "teller-1").size(),
                listForBank(metadata, "bank-other", "vault-1", "teller-1").size()));
        assertThrows(UnsupportedOperationException.class, () -> restoredSteps.clear());
        assertEquals(unrelated, unrelated(metadata));
    }

    @Test
    void deterministicReplacementDoesNotDuplicateOrEraseReturnRoute() throws Exception {
        Object metadata = metadata();
        Object first = route("teller-2", "OUTBOUND", List.of(waitStep(10)));
        Object returning = route("teller-2", "RETURN", List.of(waitStep(30)));
        Object replacement = route("teller-2", "OUTBOUND", List.of(waitStep(80)));
        assertSaved(save(metadata, first));
        assertSaved(save(metadata, returning));
        assertSaved(save(metadata, replacement));
        assertEquals(id(first), id(replacement));
        assertEquals(2, readAll(metadata).size());
        assertEquals(80, value(steps(resolve(metadata, id(first)).orElseThrow()).get(0),
                "durationTicks"));
        assertEquals(id(returning), string(hook(metadata, "teller-2"), "returnRouteRef"));
        assertEquals(1, list(targetVault(metadata), "routeHooks").stream()
                .filter(candidate -> idUnchecked(first).equals(stringUnchecked(candidate, "outboundRouteRef")))
                .count());
    }

    private static Object validRoute() throws Exception {
        return route("teller-1", "OUTBOUND", List.of(waitStep(20)));
    }

    private static void assertSaved(Object result) throws Exception {
        assertTrue(success(result), () -> "save failed: " + statusUnchecked(result));
    }

    private static void assertFailureAtomic(String expected, Object metadata, Object route)
            throws Exception {
        Object before = copyTag(metadata);
        Object result = save(metadata, route);
        assertEquals(expected, status(result));
        assertFalse(success(result));
        assertEquals(before, metadata);
    }

    private static void assertPosition(Object position, int x, int y, int z) throws Exception {
        assertEquals(List.of(x, y, z),
                List.of(value(position, "x"), value(position, "y"), value(position, "z")));
    }

    private static String idUnchecked(Object route) {
        try {
            return id(route);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static String stringUnchecked(Object tag, String key) {
        try {
            return string(tag, key);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static String statusUnchecked(Object result) {
        try {
            return status(result);
        } catch (Exception exception) {
            return exception.toString();
        }
    }
}
