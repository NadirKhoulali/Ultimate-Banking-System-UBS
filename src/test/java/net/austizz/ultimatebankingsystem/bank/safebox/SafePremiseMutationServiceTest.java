package net.austizz.ultimatebankingsystem.bank.safebox;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePremiseMutationServiceTest {
    private static final String SETUP_PACKAGE =
            "net.austizz.ultimatebankingsystem.bank.safebox.setup.";
    private static final UUID BANK_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000401");
    private static final UUID OTHER_BANK_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000402");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000401");
    private static final String TARGET_ID = "30000000-0000-0000-0000-000000000401";
    private static final String SIBLING_ID = "30000000-0000-0000-0000-000000000402";
    private static final Box TARGET_BOX =
            new Box("minecraft:overworld", 0, 60, 0, 10, 70, 10);
    private static final Box SIBLING_BOX =
            new Box("minecraft:overworld", 30, 60, 30, 40, 70, 40);

    @Test
    void createCustomEmptyPublicPremiseWithExplicitEmptySafeAreasAndUniqueNonMigrationId()
            throws Exception {
        assertDomainContract();
        Map<String, Object> metadata = metadata();
        premises(metadata).add(emptyPremise(SIBLING_ID, BANK_ID, SIBLING_BOX));
        Map<String, Object> before = deepCopyMap(metadata);
        Box createdBox = new Box(" Minecraft:OverWorld ", 50, 62, 50, 58, 69, 58);

        Object result = create(metadata, BANK_ID, createdBox,
                new Exit("minecraft:overworld", 49, 64, 54, 91.5F));

        assertTrue(success(result));
        assertEquals(List.of(), blockerNames(result));
        Map<String, Object> updated = committedMetadata(result);
        assertNotNull(updated);
        assertNotSame(metadata, updated);
        assertNotSame(metadata.get("safeDepositPremises"), updated.get("safeDepositPremises"));
        assertInputUnchanged(before, metadata);
        assertEquals(1, updated.get("safeDepositSetupVersion"));
        assertEquals(2, premises(updated).size());

        Map<String, Object> created = premises(updated).get(1);
        String createdId = string(created, "id");
        assertNotEquals("", createdId);
        UUID.fromString(createdId);
        assertNotEquals(SIBLING_ID, createdId);
        assertNotEquals(migrationPremiseId(BANK_ID, createdBox), createdId,
                "custom ids must not impersonate deterministic migration-owned ids");
        assertEquals(BANK_ID.toString(), string(created, "bankId"));
        assertEquals("minecraft:overworld", string(created, "dimension"));
        assertBounds(created, createdBox);
        assertEquals(49, created.get("exitX"));
        assertEquals(64, created.get("exitY"));
        assertEquals(54, created.get("exitZ"));
        assertEquals(91.5F, created.get("exitYaw"));
        assertEquals("PUBLIC", created.get("mode"));
        assertTrue(created.containsKey("safeAreas"));
        assertTrue(assertInstanceOf(List.class, created.get("safeAreas")).isEmpty());
        assertEquals(before.get("sentinel"), updated.get("sentinel"));
        assertEquals(before.get("accountSafeBoxSlots"), updated.get("accountSafeBoxSlots"));
    }

    @Test
    void createRejectsOverlapDuplicateMalformedAndInvalidExitWithoutMutatingInput()
            throws Exception {
        Map<String, Object> sameBankOverlap = metadata();
        premises(sameBankOverlap).add(emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX));
        assertCreateRejected(sameBankOverlap, BANK_ID,
                new Box("minecraft:overworld", 8, 65, 8, 18, 75, 18),
                new Exit("minecraft:overworld", 19, 70, 12, 0.0F));

        Map<String, Object> crossBankOverlap = metadata();
        premises(crossBankOverlap).add(emptyPremise(TARGET_ID, OTHER_BANK_ID, TARGET_BOX));
        assertCreateRejected(crossBankOverlap, BANK_ID,
                new Box("minecraft:overworld", -2, 58, -2, 2, 62, 2),
                new Exit("minecraft:overworld", -3, 60, 0, 0.0F));

        Map<String, Object> duplicateIds = metadata();
        premises(duplicateIds).add(emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX));
        premises(duplicateIds).add(emptyPremise(TARGET_ID, OTHER_BANK_ID, SIBLING_BOX));
        assertCreateRejected(duplicateIds, BANK_ID,
                new Box("minecraft:overworld", 8, 65, 8, 18, 75, 18),
                new Exit("minecraft:overworld", 19, 70, 12, 0.0F));
        assertCreateSucceeds(duplicateIds, BANK_ID,
                new Box("minecraft:the_nether", 0, 40, 0, 5, 45, 5),
                new Exit("minecraft:the_nether", -1, 42, 2, 0.0F));

        Map<String, Object> malformedList = metadata();
        malformedList.put("safeDepositPremises", "not-a-list");
        assertCreateRejected(malformedList, BANK_ID, SIBLING_BOX,
                new Exit("minecraft:overworld", 29, 64, 35, 0.0F));

        Map<String, Object> malformedEntry = metadata();
        malformedEntry.put("safeDepositPremises", new ArrayList<>(List.of("not-a-map")));
        assertCreateRejected(malformedEntry, BANK_ID, SIBLING_BOX,
                new Exit("minecraft:overworld", 29, 64, 35, 0.0F));

        Map<String, Object> malformedChildren = metadata();
        Map<String, Object> invalidPremise = emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX);
        invalidPremise.put("safeAreas", "not-a-list");
        premises(malformedChildren).add(invalidPremise);
        assertCreateRejected(malformedChildren, BANK_ID,
                new Box("minecraft:overworld", 8, 65, 8, 18, 75, 18),
                new Exit("minecraft:overworld", 19, 70, 12, 0.0F));
        assertCreateSucceeds(malformedChildren, BANK_ID, SIBLING_BOX,
                new Exit("minecraft:overworld", 29, 64, 35, 0.0F));

        assertCreateRejected(metadata(), BANK_ID, TARGET_BOX,
                new Exit("minecraft:the_nether", -1, 64, 5, 0.0F));
        assertCreateRejected(metadata(), BANK_ID, TARGET_BOX,
                new Exit("minecraft:overworld", 5, 65, 5, 0.0F));
    }

    @Test
    void createDetailNamesTheBlockingPremise() throws Exception {
        Map<String, Object> migrated = metadata();
        String migrationId = migrationPremiseId(BANK_ID, TARGET_BOX);
        premises(migrated).add(emptyPremise(migrationId, BANK_ID, TARGET_BOX));

        Object overlapResult = create(migrated, BANK_ID,
                new Box("minecraft:overworld", -5, 55, -5, 20, 80, 20),
                new Exit("minecraft:overworld", 21, 64, 5, 0.0F));
        assertFalse(success(overlapResult));
        assertTrue(detail(overlapResult).contains("overlaps"),
                "overlap rejections must name the conflict: " + detail(overlapResult));
        assertTrue(detail(overlapResult).contains("auto-created from a legacy safe area"),
                "migration-owned blockers must be identified: " + detail(overlapResult));

        Map<String, Object> malformedSibling = metadata();
        Map<String, Object> broken = emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX);
        broken.put("mode", "NOT_A_MODE");
        premises(malformedSibling).add(broken);
        Object blockedResult = create(malformedSibling, BANK_ID,
                new Box("minecraft:overworld", 5, 62, 5, 15, 72, 15),
                new Exit("minecraft:overworld", 16, 64, 8, 0.0F));
        assertFalse(success(blockedResult));
        assertTrue(detail(blockedResult).contains("unreadable saved data"),
                "opaque blockers must be identified: " + detail(blockedResult));
        assertCreateSucceeds(malformedSibling, BANK_ID, SIBLING_BOX,
                new Exit("minecraft:overworld", 29, 64, 35, 0.0F));
    }

    @Test
    void mutationsAfterOpaqueEntriesHitTheCorrectRawEntry() throws Exception {
        Map<String, Object> metadata = metadata();
        Map<String, Object> broken = emptyPremise(SIBLING_ID, BANK_ID, SIBLING_BOX);
        broken.put("mode", "NOT_A_MODE");
        premises(metadata).add(broken);
        premises(metadata).add(emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX));

        Object modeResult = setMode(metadata, BANK_ID, TARGET_ID, "STAFF_ONLY");

        assertTrue(success(modeResult));
        Map<String, Object> updated = committedMetadata(modeResult);
        assertEquals("NOT_A_MODE", string(premises(updated).get(0), "mode"),
                "the opaque sibling must remain untouched");
        assertEquals("STAFF_ONLY", string(premises(updated).get(1), "mode"),
                "the valid premise after the opaque entry must be the one mutated");
    }

    @Test
    void setModeAndSetExitPreserveAllIdsBoundsChildrenRoutesAndSentinels()
            throws Exception {
        Map<String, Object> metadata = metadataWithTargetAndSibling();
        Map<String, Object> before = deepCopyMap(metadata);

        Object modeResult = setMode(metadata, BANK_ID, TARGET_ID, "STAFF_ONLY");
        assertTrue(success(modeResult));
        Map<String, Object> modeUpdated = committedMetadata(modeResult);
        Map<String, Object> expectedMode = deepCopyMap(before);
        premise(expectedMode, TARGET_ID).put("mode", "STAFF_ONLY");
        assertEquals(expectedMode, modeUpdated,
                "setMode may change only the selected premise mode");
        assertInputUnchanged(before, metadata);

        Exit replacement = new Exit("minecraft:overworld", -3, 63, 5, -42.25F);
        Object exitResult = setExit(metadata, BANK_ID, TARGET_ID, replacement);
        assertTrue(success(exitResult));
        Map<String, Object> exitUpdated = committedMetadata(exitResult);
        Map<String, Object> expectedExit = deepCopyMap(before);
        Map<String, Object> selected = premise(expectedExit, TARGET_ID);
        selected.put("exitX", replacement.x());
        selected.put("exitY", replacement.y());
        selected.put("exitZ", replacement.z());
        selected.put("exitYaw", replacement.yaw());
        assertEquals(expectedExit, exitUpdated,
                "setExit may change only the selected premise exit fields");
        assertInputUnchanged(before, metadata);

        assertSetExitRejected(metadata, new Exit("minecraft:the_nether", -3, 63, 5, 0.0F));
        assertSetExitRejected(metadata, new Exit("minecraft:overworld", 5, 65, 5, 0.0F));
    }

    @Test
    void deleteReportsEveryFreshBlockerAndPreservesInput() throws Exception {
        Map<String, Object> metadata = blockedMetadata();
        String migrationId = string(premises(metadata).get(0), "id");
        Map<String, Object> before = deepCopyMap(metadata);
        Set<String> activeVaultIds = new LinkedHashSet<>(List.of("vault-target", "vault-unrelated"));
        Set<String> activeBefore = new LinkedHashSet<>(activeVaultIds);

        assertEquals(List.of("NON_EMPTY", "MIGRATION_BACKED", "ASSIGNED"),
                policyNames(deletionBlockers(metadata, BANK_ID, migrationId, Set.of())));
        List<?> freshBlockers = deletionBlockers(metadata, BANK_ID, migrationId, activeVaultIds);
        assertEquals(List.of("NON_EMPTY", "MIGRATION_BACKED", "ASSIGNED", "ACTIVE"),
                policyNames(freshBlockers));
        assertThrows(UnsupportedOperationException.class,
                () -> addToRawList(freshBlockers, policy("ACTIVE")));

        Object result = delete(metadata, BANK_ID, migrationId, activeVaultIds);

        assertFalse(success(result));
        assertNull(committedMetadata(result), "blocked deletion must return no commit candidate");
        assertEquals(List.of("NON_EMPTY", "MIGRATION_BACKED", "ASSIGNED", "ACTIVE"),
                blockerNames(result));
        assertInputUnchanged(before, metadata);
        assertEquals(activeBefore, activeVaultIds);
        assertEquals(before.get("safeDepositAssignments"), metadata.get("safeDepositAssignments"));
        assertEquals(before.get("safeTellerRoutes"), metadata.get("safeTellerRoutes"));
        assertEquals(before.get("safeDepositEscrow"), metadata.get("safeDepositEscrow"));
        assertEquals(before.get("safeDepositRentAmount"), metadata.get("safeDepositRentAmount"));
        assertEquals(before.get("accountSafeBoxSlots"), metadata.get("accountSafeBoxSlots"));
    }

    @Test
    void emptyCustomPremiseDeletionRemovesOnlySelectedTag() throws Exception {
        Map<String, Object> metadata = metadataWithTargetAndSibling();
        Map<String, Object> before = deepCopyMap(metadata);
        Map<String, Object> expected = deepCopyMap(metadata);
        premises(expected).removeIf(candidate -> TARGET_ID.equals(string(candidate, "id")));

        Object result = delete(metadata, BANK_ID, TARGET_ID, Set.of("vault-sibling"));

        assertTrue(success(result));
        assertEquals(List.of(), blockerNames(result));
        Map<String, Object> updated = committedMetadata(result);
        assertEquals(expected, updated,
                "successful deletion must remove only the uniquely selected premise tag");
        assertEquals(1, premises(updated).size());
        assertEquals(SIBLING_ID, string(premises(updated).get(0), "id"));
        assertInputUnchanged(before, metadata);
        assertNotSame(metadata, updated);
        assertNotSame(metadata.get("safeDepositPremises"), updated.get("safeDepositPremises"));
    }

    @Test
    void duplicateMalformedAndUncertainMappingsFailClosed() throws Exception {
        Map<String, Object> duplicate = metadata();
        premises(duplicate).add(emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX));
        premises(duplicate).add(emptyPremise(TARGET_ID, OTHER_BANK_ID, SIBLING_BOX));

        Map<String, Object> malformedList = metadata();
        malformedList.put("safeDepositPremises", "not-a-list");

        Map<String, Object> malformedChildren = metadata();
        Map<String, Object> brokenTarget = emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX);
        brokenTarget.remove("safeAreas");
        premises(malformedChildren).add(brokenTarget);

        Map<String, Object> uncertainAssignment = metadataWithEmptyTarget();
        assignments(uncertainAssignment).add(map(
                "accountId", ACCOUNT_ID.toString(),
                "bankId", BANK_ID.toString(),
                "doorIndex", 1));

        Map<String, Object> uncertainRoute = metadataWithEmptyTarget();
        routes(uncertainRoute).add(map(
                "id", "route-without-vault",
                "bankId", BANK_ID.toString(),
                "tellerId", "teller-1"));

        Map<String, Object> ambiguousVault = metadata();
        premises(ambiguousVault).add(premiseWithVault(
                TARGET_ID, BANK_ID, TARGET_BOX, "safe-area-target", "vault-shared"));
        premises(ambiguousVault).add(premiseWithVault(
                SIBLING_ID, BANK_ID, SIBLING_BOX, "safe-area-sibling", "vault-shared"));
        routes(ambiguousVault).add(route("route-shared", BANK_ID, "vault-shared"));

        assertDeleteFailsClosed("duplicate premise ids", duplicate, TARGET_ID);
        assertDeleteFailsClosed("malformed premise list", malformedList, TARGET_ID);
        assertDeleteFailsClosed("missing child list", malformedChildren, TARGET_ID);
        assertDeleteFailsClosed("uncertain assignment mapping", uncertainAssignment, TARGET_ID);
        assertDeleteSucceeds("dormant route data does not block premise deletion", uncertainRoute, TARGET_ID);
        assertDeleteFailsClosed("ambiguous descendant vault id", ambiguousVault, TARGET_ID);
    }

    private static void assertDomainContract() throws Exception {
        Class<?> resultClass = type("SafePremiseMutationResult");
        Class<?> policyClass = type("SafePremiseDeletionPolicy");
        assertTrue(resultClass.isRecord(), "mutation results must be immutable value records");
        assertEquals(List.of("success", "metadata", "blockers", "detail"),
                Arrays.stream(resultClass.getRecordComponents()).map(component -> component.getName()).toList());
        assertTrue(policyClass.isEnum());
        assertEquals(List.of("NON_EMPTY", "MIGRATION_BACKED", "ASSIGNED", "ROUTED", "ACTIVE"),
                enumNames(policyClass));
    }

    private static void assertCreateRejected(Map<String, Object> metadata,
                                             UUID bankId,
                                             Box box,
                                             Exit exit) throws Exception {
        Map<String, Object> before = deepCopyMap(metadata);
        Object result = create(metadata, bankId, box, exit);
        assertFalse(success(result));
        assertNull(committedMetadata(result));
        assertEquals(List.of(), blockerNames(result));
        assertInputUnchanged(before, metadata);
    }

    private static void assertCreateSucceeds(Map<String, Object> metadata,
                                             UUID bankId,
                                             Box box,
                                             Exit exit) throws Exception {
        Map<String, Object> before = deepCopyMap(metadata);
        Object result = create(metadata, bankId, box, exit);
        assertTrue(success(result),
                "non-overlapping claims must succeed even when unrelated stored premises are malformed");
        assertNotNull(committedMetadata(result));
        assertInputUnchanged(before, metadata);
    }

    private static String detail(Object result) throws Exception {
        return (String) invoke(result, "detail");
    }

    private static void assertSetExitRejected(Map<String, Object> metadata, Exit exit)
            throws Exception {
        Map<String, Object> before = deepCopyMap(metadata);
        Object result = setExit(metadata, BANK_ID, TARGET_ID, exit);
        assertFalse(success(result));
        assertNull(committedMetadata(result));
        assertInputUnchanged(before, metadata);
    }

    private static void assertDeleteFailsClosed(String label,
                                                Map<String, Object> metadata,
                                                String premiseId) throws Exception {
        Map<String, Object> before = deepCopyMap(metadata);
        Set<String> active = new LinkedHashSet<>(List.of("vault-shared"));
        Object result = delete(metadata, BANK_ID, premiseId, active);
        assertFalse(success(result), label);
        assertNull(committedMetadata(result), label);
        assertInputUnchanged(before, metadata);
        assertEquals(Set.of("vault-shared"), active, label);
    }

    private static void assertDeleteSucceeds(String label,
                                             Map<String, Object> metadata,
                                             String premiseId) throws Exception {
        Map<String, Object> before = deepCopyMap(metadata);
        Object result = delete(metadata, BANK_ID, premiseId, Set.of());
        assertTrue(success(result), label);
        assertNotNull(committedMetadata(result), label);
        assertInputUnchanged(before, metadata);
    }

    private static Object create(Map<String, Object> metadata,
                                 UUID bankId,
                                 Box box,
                                 Exit exit) throws Exception {
        return invokeService("create",
                new Class<?>[]{Map.class, UUID.class, type("SafeBlockBounds"), type("SafeExitSnapshot")},
                metadata, bankId, bounds(box), exit(exit));
    }

    private static Object setMode(Map<String, Object> metadata,
                                  UUID bankId,
                                  String premiseId,
                                  String mode) throws Exception {
        return invokeService("setMode",
                new Class<?>[]{Map.class, UUID.class, String.class, type("SafePremiseMode")},
                metadata, bankId, premiseId, enumValue(type("SafePremiseMode"), mode));
    }

    private static Object setExit(Map<String, Object> metadata,
                                  UUID bankId,
                                  String premiseId,
                                  Exit exit) throws Exception {
        return invokeService("setExit",
                new Class<?>[]{Map.class, UUID.class, String.class, type("SafeExitSnapshot")},
                metadata, bankId, premiseId, exit(exit));
    }

    private static List<?> deletionBlockers(Map<String, Object> metadata,
                                            UUID bankId,
                                            String premiseId,
                                            Set<String> activeVaultIds) throws Exception {
        Object result = invokeService("deletionBlockers",
                new Class<?>[]{Map.class, UUID.class, String.class, Set.class},
                metadata, bankId, premiseId, activeVaultIds);
        return assertInstanceOf(List.class, result);
    }

    private static Object delete(Map<String, Object> metadata,
                                 UUID bankId,
                                 String premiseId,
                                 Set<String> activeVaultIds) throws Exception {
        return invokeService("delete",
                new Class<?>[]{Map.class, UUID.class, String.class, Set.class},
                metadata, bankId, premiseId, activeVaultIds);
    }

    private static Object invokeService(String methodName,
                                        Class<?>[] parameterTypes,
                                        Object... arguments) throws Exception {
        Method method = type("SafePremiseMutationService").getMethod(methodName, parameterTypes);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()), methodName + " must be a pure static domain operation");
        Object result = method.invoke(null, arguments);
        if (!"deletionBlockers".equals(methodName)) {
            assertInstanceOf(type("SafePremiseMutationResult"), result);
        }
        return result;
    }

    private static boolean success(Object result) throws Exception {
        return (Boolean) invoke(result, "success");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> committedMetadata(Object result) throws Exception {
        Object metadata = invoke(result, "metadata");
        return metadata == null ? null : (Map<String, Object>) assertInstanceOf(Map.class, metadata);
    }

    private static List<String> blockerNames(Object result) throws Exception {
        return policyNames(assertInstanceOf(List.class, invoke(result, "blockers")));
    }

    private static List<String> policyNames(List<?> policies) throws Exception {
        List<String> names = new ArrayList<>();
        for (Object policy : policies) {
            assertInstanceOf(type("SafePremiseDeletionPolicy"), policy);
            names.add(((Enum<?>) policy).name());
        }
        return names;
    }

    private static Object policy(String name) throws Exception {
        return enumValue(type("SafePremiseDeletionPolicy"), name);
    }

    private static Object bounds(Box box) throws Exception {
        return type("SafeBlockBounds")
                .getConstructor(String.class, int.class, int.class, int.class,
                        int.class, int.class, int.class)
                .newInstance(box.dimension(), box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ());
    }

    private static Object exit(Exit exit) throws Exception {
        return type("SafeExitSnapshot")
                .getConstructor(String.class, int.class, int.class, int.class, float.class)
                .newInstance(exit.dimension(), exit.x(), exit.y(), exit.z(), exit.yaw());
    }

    private static Map<String, Object> metadataWithEmptyTarget() {
        Map<String, Object> metadata = metadata();
        premises(metadata).add(emptyPremise(TARGET_ID, BANK_ID, TARGET_BOX));
        return metadata;
    }

    private static Map<String, Object> metadataWithTargetAndSibling() {
        Map<String, Object> metadata = metadataWithEmptyTarget();
        Map<String, Object> sibling = premiseWithVault(
                SIBLING_ID, BANK_ID, SIBLING_BOX, "safe-area-sibling", "vault-sibling");
        premises(metadata).add(sibling);
        assignments(metadata).add(assignment(BANK_ID, "minecraft:overworld", 35, 64, 35, 2));
        routes(metadata).add(route("route-sibling", BANK_ID, "vault-sibling"));
        return metadata;
    }

    private static Map<String, Object> blockedMetadata() {
        Map<String, Object> metadata = metadata();
        String migrationId = migrationPremiseId(BANK_ID, TARGET_BOX);
        Map<String, Object> target = premiseWithVault(
                migrationId, BANK_ID, TARGET_BOX, "safe-area-target", "vault-target");
        target.put("migrationBacked", false);
        target.put("deleteBlockers", new ArrayList<>());
        target.put("active", false);
        premises(metadata).add(target);
        premises(metadata).add(emptyPremise(SIBLING_ID, BANK_ID, SIBLING_BOX));
        assignments(metadata).add(assignment(BANK_ID, "minecraft:overworld", 5, 65, 5, 1));
        routes(metadata).add(route("route-target", BANK_ID, "vault-target"));
        return metadata;
    }

    private static Map<String, Object> metadata() {
        return map(
                "safeDepositSetupVersion", 1,
                "safeDepositPremises", new ArrayList<Map<String, Object>>(),
                "safeDepositAssignments", new ArrayList<Map<String, Object>>(),
                "safeTellerRoutes", new ArrayList<Map<String, Object>>(),
                "safeDepositEscrow", new ArrayList<>(List.of(map(
                        "accountId", ACCOUNT_ID.toString(),
                        "contents", "escrow-do-not-touch"))),
                "safeDepositRentAmount", "42.00",
                "accountSafeBoxSlots", new ArrayList<>(List.of(
                        map("slot", 4, "item", "minecraft:diamond", "count", 17))),
                "sentinel", map(
                        "uuid", UUID.fromString("40000000-0000-0000-0000-000000000401"),
                        "nested", new ArrayList<>(List.of("alpha", "beta")))
        );
    }

    private static Map<String, Object> emptyPremise(String id, UUID bankId, Box box) {
        Map<String, Object> premise = premiseBase(id, bankId, box);
        premise.put("safeAreas", new ArrayList<Map<String, Object>>());
        return premise;
    }

    private static Map<String, Object> premiseWithVault(String id,
                                                       UUID bankId,
                                                       Box box,
                                                       String safeAreaId,
                                                       String vaultId) {
        Map<String, Object> premise = premiseBase(id, bankId, box);
        Map<String, Object> vault = map(
                "id", vaultId,
                "safeAreaId", safeAreaId,
                "dimension", normalized(box.dimension()),
                "status", "READY",
                "vaultDoorX", box.minX() + 2,
                "vaultDoorY", box.minY() + 2,
                "vaultDoorZ", box.minZ() + 2,
                "doorIndex", 1,
                "routeHooks", new ArrayList<>(List.of(map(
                        "tellerId", "teller-1",
                        "bankBound", true,
                        "outboundRouteRef", "route-out",
                        "returnRouteRef", "route-back"))),
                "vaultSentinel", "preserve-vault"
        );
        Map<String, Object> safeArea = map(
                "id", safeAreaId,
                "premiseId", id,
                "dimension", normalized(box.dimension()),
                "minX", box.minX() + 1,
                "minY", box.minY() + 1,
                "minZ", box.minZ() + 1,
                "maxX", box.maxX() - 1,
                "maxY", box.maxY() - 1,
                "maxZ", box.maxZ() - 1,
                "vaults", new ArrayList<>(List.of(vault)),
                "areaSentinel", "preserve-area"
        );
        premise.put("safeAreas", new ArrayList<>(List.of(safeArea)));
        return premise;
    }

    private static Map<String, Object> premiseBase(String id, UUID bankId, Box box) {
        return map(
                "id", id,
                "bankId", bankId.toString(),
                "dimension", normalized(box.dimension()),
                "minX", box.minX(),
                "minY", box.minY(),
                "minZ", box.minZ(),
                "maxX", box.maxX(),
                "maxY", box.maxY(),
                "maxZ", box.maxZ(),
                "exitX", box.minX() - 1,
                "exitY", box.minY(),
                "exitZ", box.minZ(),
                "exitYaw", 180.0F,
                "mode", "PUBLIC",
                "premiseSentinel", map("keep", true)
        );
    }

    private static Map<String, Object> assignment(UUID bankId,
                                                  String dimension,
                                                  int x,
                                                  int y,
                                                  int z,
                                                  int doorIndex) {
        return map(
                "accountId", ACCOUNT_ID.toString(),
                "bankId", bankId.toString(),
                "dimension", dimension,
                "x", x,
                "y", y,
                "z", z,
                "doorIndex", doorIndex,
                "boxNumber", "SDB-" + doorIndex,
                "locked", false,
                "assignmentSentinel", "preserve-assignment"
        );
    }

    private static Map<String, Object> route(String id, UUID bankId, String vaultId) {
        return map(
                "id", id,
                "bankId", bankId.toString(),
                "vaultId", vaultId,
                "tellerId", "teller-1",
                "direction", "OUTBOUND",
                "dimension", "minecraft:overworld",
                "steps", new ArrayList<>(List.of(map("type", "WAIT", "ticks", 5))),
                "routeSentinel", "preserve-route"
        );
    }

    private static void assertBounds(Map<String, Object> premise, Box expected) {
        assertEquals(normalized(expected.dimension()), string(premise, "dimension"));
        assertEquals(expected.minX(), premise.get("minX"));
        assertEquals(expected.minY(), premise.get("minY"));
        assertEquals(expected.minZ(), premise.get("minZ"));
        assertEquals(expected.maxX(), premise.get("maxX"));
        assertEquals(expected.maxY(), premise.get("maxY"));
        assertEquals(expected.maxZ(), premise.get("maxZ"));
    }

    private static void assertInputUnchanged(Map<String, Object> expected,
                                             Map<String, Object> actual) {
        assertEquals(expected, actual);
        assertEquals(expected.toString(), actual.toString(),
                "input key/list ordering and nested values must remain exact");
    }

    private static String migrationPremiseId(UUID bankId, Box box) {
        String seed = "ubs-safe-deposit-setup-v1|premise|" + bankId + "|"
                + normalized(box.dimension()) + "|" + box.minX() + "|" + box.minY() + "|"
                + box.minZ() + "|" + box.maxX() + "|" + box.maxY() + "|" + box.maxZ();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normalized(String dimension) {
        return dimension == null ? "" : dimension.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static Class<?> type(String simpleName) throws ClassNotFoundException {
        return Class.forName(SETUP_PACKAGE + simpleName);
    }

    private static List<String> enumNames(Class<?> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addToRawList(List<?> values, Object value) {
        ((List) values).add(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> premises(Map<String, Object> metadata) {
        return (List<Map<String, Object>>) metadata.get("safeDepositPremises");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> assignments(Map<String, Object> metadata) {
        return (List<Map<String, Object>>) metadata.get("safeDepositAssignments");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> routes(Map<String, Object> metadata) {
        return (List<Map<String, Object>>) metadata.get("safeTellerRoutes");
    }

    private static Map<String, Object> premise(Map<String, Object> metadata, String premiseId) {
        return premises(metadata).stream()
                .filter(candidate -> premiseId.equals(string(candidate, "id")))
                .findFirst()
                .orElseThrow();
    }

    private static String string(Map<String, Object> map, String key) {
        return String.valueOf(map.getOrDefault(key, ""));
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put((String) values[index], values[index + 1]);
        }
        return map;
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, deepCopy(value)));
        return copy;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, nested) -> copy.put(String.valueOf(key), deepCopy(nested)));
            return copy;
        }
        if (value instanceof List<?> source) {
            List<Object> copy = new ArrayList<>();
            source.forEach(nested -> copy.add(deepCopy(nested)));
            return copy;
        }
        if (value instanceof Set<?> source) {
            Set<Object> copy = new LinkedHashSet<>();
            source.forEach(nested -> copy.add(deepCopy(nested)));
            return copy;
        }
        return value;
    }

    private record Box(String dimension,
                       int minX,
                       int minY,
                       int minZ,
                       int maxX,
                       int maxY,
                       int maxZ) {
    }

    private record Exit(String dimension, int x, int y, int z, float yaw) {
    }
}
