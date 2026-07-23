package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupMigration;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeTellerRouteHook;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.austizz.ultimatebankingsystem.bank.safebox.VaultRouteNbtTestSupport.*;
import static net.austizz.ultimatebankingsystem.bank.safebox.VaultRouteTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultRouteValidationTest {
    @Test
    void partialHookSurvivesTypedSnapshotParsingButIsNotReady() {
        Map<String, Object> hookMap = map("tellerId", "teller-1", "bankBound", true,
                "outboundRouteRef", "route-out");
        SafeVaultSnapshot vault = SafeDepositSetupMigration.snapshot(mapMetadata(hookMap))
                .premises().get(0).safeAreas().get(0).vaults().get(0);
        assertEquals(1, vault.routeHooks().size());
        SafeTellerRouteHook hook = vault.routeHooks().get(0);
        assertEquals("route-out", hook.outboundRouteRef());
        assertEquals("", hook.returnRouteRef());
        assertFalse(hook.ready());
    }

    @Test
    void malformedRoutesAreFilteredWithoutCoercion() throws Exception {
        Object metadata = metadata();
        Object good = route("teller-good", "OUTBOUND", List.of(walk(2, 64, 10)));
        Object unknown = route("teller-unknown", "OUTBOUND", List.of(waitStep(1)));
        assertSaved(save(metadata, good));
        assertSaved(save(metadata, unknown));
        putString(stepTag(metadata, id(unknown), 0), "type", "TELEPORT");
        assertEquals(List.of(id(good)), readAll(metadata).stream()
                .map(VaultRouteValidationTest::idUnchecked).toList());
        assertTrue(resolve(metadata, id(unknown)).isEmpty());
        assertFilteredAfter(route("numeric-start", "OUTBOUND", List.of(waitStep(1))),
                (data, routeId) -> putLong(routeTag(data, routeId), "startX", 1L));
        assertFilteredAfter(route("numeric-walk", "OUTBOUND", List.of(walk(2, 64, 10))),
                (data, routeId) -> putDouble(stepTag(data, routeId, 0), "x", 2.0D));
        assertFilteredAfter(route("numeric-wait", "OUTBOUND", List.of(waitStep(20))),
                (data, routeId) -> putShort(stepTag(data, routeId, 0), "durationTicks", (short) 20));
        assertFilteredAfter(route("numeric-power", "OUTBOUND",
                        List.of(redstone(3, 65, 11, "UP", 9, 40))),
                (data, routeId) -> putShort(stepTag(data, routeId, 0), "strength", (short) 9));
        assertFilteredAfter(route("numeric-duration", "OUTBOUND",
                        List.of(redstone(3, 65, 11, "UP", 9, 40))),
                (data, routeId) -> putLong(stepTag(data, routeId, 0), "durationTicks", 40L));
    }

    @Test
    void explicitValidationBoundariesRejectInvalidInputAtomically() throws Exception {
        assertValid(route("steps-max", "OUTBOUND", Collections.nCopies(256, waitStep(1))));
        assertValid(route("wait-max", "OUTBOUND", List.of(waitStep(1200))));
        assertValid(route("redstone-max", "OUTBOUND",
                List.of(redstone(1, 2, 3, "DOWN", 15, 1200))));
        List<Object> invalid = List.of(
                rawRoute("", "bank-1", "vault-1", "bad-id", "OUTBOUND",
                        "minecraft:overworld", List.of(waitStep(1))),
                routeFor("", "vault-1", "bad-bank", "OUTBOUND",
                        "minecraft:overworld", List.of(waitStep(1))),
                routeFor("bank-1", "", "bad-vault", "OUTBOUND",
                        "minecraft:overworld", List.of(waitStep(1))),
                routeFor("bank-1", "vault-1", "", "OUTBOUND",
                        "minecraft:overworld", List.of(waitStep(1))),
                routeFor("bank-1", "vault-1", "bad-dimension", "OUTBOUND", "",
                        List.of(waitStep(1))),
                route("steps-over", "OUTBOUND", Collections.nCopies(257, waitStep(1))),
                route("wait-zero", "OUTBOUND", List.of(waitStep(0))),
                route("wait-over", "OUTBOUND", List.of(waitStep(1201))),
                route("power-low", "OUTBOUND", List.of(redstone(1, 2, 3, "NORTH", 0, 1))),
                route("power-high", "OUTBOUND", List.of(redstone(1, 2, 3, "NORTH", 16, 1))),
                route("face", "OUTBOUND", List.of(redstone(1, 2, 3, null, 1, 1))),
                route("redstone-zero", "OUTBOUND", List.of(redstone(1, 2, 3, "UP", 1, 0))),
                route("redstone-over", "OUTBOUND", List.of(redstone(1, 2, 3, "UP", 1, 1201)))
        );
        for (Object route : invalid) {
            assertFalse((Boolean) value(validate(route), "valid"));
            assertFailureAtomic("INVALID_ROUTE", metadata(), route);
        }
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

    private static void assertValid(Object route) throws Exception {
        assertTrue((Boolean) value(validate(route), "valid"));
    }

    private static void assertFilteredAfter(Object route, NumericMutation mutation) throws Exception {
        Object metadata = metadata();
        assertSaved(save(metadata, route));
        mutation.apply(metadata, id(route));
        assertTrue(resolve(metadata, id(route)).isEmpty());
    }

    private static Map<String, Object> mapMetadata(Map<String, Object> hook) {
        Map<String, Object> vault = map("id", "vault-1", "safeAreaId", "safe-area-1",
                "dimension", "minecraft:overworld", "status", "ROUTES_PENDING",
                "routeHooks", List.of(hook));
        Map<String, Object> area = map("id", "safe-area-1", "premiseId", "premise-1",
                "dimension", "minecraft:overworld", "minX", 1, "minY", 63, "minZ", 9,
                "maxX", 5, "maxY", 66, "maxZ", 12, "vaults", List.of(vault));
        Map<String, Object> premise = map("id", "premise-1", "bankId", "bank-1",
                "dimension", "minecraft:overworld", "minX", 1, "minY", 63, "minZ", 9,
                "maxX", 5, "maxY", 66, "maxZ", 12, "exitX", 0, "exitY", 63,
                "exitZ", 9, "exitYaw", 180.0F, "mode", "PUBLIC", "safeAreas", List.of(area));
        return map("safeDepositSetupVersion", 1, "safeDepositPremises", List.of(premise));
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], pairs[index + 1]);
        }
        return map;
    }

    private static String idUnchecked(Object route) {
        try {
            return id(route);
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

    private interface NumericMutation {
        void apply(Object metadata, String routeId) throws Exception;
    }
}
