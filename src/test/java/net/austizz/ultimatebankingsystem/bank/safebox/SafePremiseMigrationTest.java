package net.austizz.ultimatebankingsystem.bank.safebox;

// SIZE_OK: inherited 1392-line migration corpus; -58 task lines retain its indivisible NBT builders and loader.

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupMigration;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeTellerRouteHook;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultSnapshot;
import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePremiseMigrationTest {
    private static final UUID BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final int TAG_BYTE = 1;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    @Test
    void multipleLegacyAssignmentsInOneAreaBecomeOnePlaceholderVaultWithoutTouchingLegacyTags() throws Exception {
        Map<String, Object> metadata = legacyMetadata(List.of(
                assignment(ACCOUNT_ID, BANK_ID, "minecraft:overworld", 3, 64, 10, 2),
                assignment(SECOND_ACCOUNT_ID, BANK_ID, "minecraft:overworld", 4, 64, 11, 1)
        ));
        String legacyAreasBefore = metadata.get("safeDepositAreas").toString();
        String legacyAssignmentsBefore = metadata.get("safeDepositAssignments").toString();
        String legacyEscrowBefore = metadata.get("safeDepositEscrow").toString();
        String legacyRentBefore = (String) metadata.get("safeDepositRentAmount");
        String legacyLocationBefore = string(first(list(metadata, "safeDepositAssignments")), "legacyLocation");

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID), "first migration should add setup records");

        List<Map<String, Object>> premises = list(metadata, "safeDepositPremises");
        assertEquals(1, premises.size(), "only the valid legacy area should become a premise");
        Map<String, Object> premise = premises.get(0);
        assertEquals(1, intValue(metadata, "safeDepositSetupVersion"));
        assertEquals(BANK_ID.toString(), string(premise, "bankId"));
        assertEquals("minecraft:overworld", string(premise, "dimension"));
        assertEquals("PUBLIC", string(premise, "mode"));
        assertEquals(1, intValue(premise, "minX"));
        assertEquals(5, intValue(premise, "maxX"));
        assertEquals(63, intValue(premise, "minY"));
        assertEquals(66, intValue(premise, "maxY"));
        assertEquals(9, intValue(premise, "minZ"));
        assertEquals(12, intValue(premise, "maxZ"));
        assertFalse(pointInside(premise, intValue(premise, "exitX"), intValue(premise, "exitY"), intValue(premise, "exitZ")),
                "migrated premise exit must be outside inclusive bounds");
        assertTrue(Float.isFinite(floatValue(premise, "exitYaw")), "exit yaw must be persisted");

        Map<String, Object> safeArea = singleSafeArea(premise);
        assertEquals(string(premise, "id"), string(safeArea, "premiseId"));
        Map<String, Object> vault = singleVault(safeArea);
        assertEquals(string(safeArea, "id"), string(vault, "safeAreaId"));
        assertEquals("SETUP_PENDING", string(vault, "status"));
        assertEquals("minecraft:overworld", string(vault, "dimension"));
        assertFalse(vault.containsKey("vaultDoorX"), "migrated placeholder vault must not invent a door anchor");
        assertFalse(vault.containsKey("doorIndex"), "migrated placeholder vault must not bind to one legacy assignment door");
        assertTrue(vault.get("routeHooks") instanceof List<?>, "route hook list should exist even before teller routing");
        assertNotEquals("", string(vault, "id"));

        String migratedPremises = premises.toString();
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID), "second migration should be idempotent");
        assertEquals(migratedPremises, metadata.get("safeDepositPremises").toString());
        assertEquals(legacyAreasBefore, metadata.get("safeDepositAreas").toString());
        assertEquals(legacyAssignmentsBefore, metadata.get("safeDepositAssignments").toString());
        assertEquals(legacyEscrowBefore, metadata.get("safeDepositEscrow").toString());
        assertEquals(legacyRentBefore, metadata.get("safeDepositRentAmount"));
        assertEquals(legacyLocationBefore, string(first(list(metadata, "safeDepositAssignments")), "legacyLocation"));
    }

    @Test
    void legacyAreaWithoutAssignmentsStillGetsOneSetupPendingPlaceholderVault() throws Exception {
        Map<String, Object> metadata = legacyMetadata(List.of());

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID));

        Map<String, Object> premise = first(list(metadata, "safeDepositPremises"));
        Map<String, Object> vault = singleVault(singleSafeArea(premise));
        assertEquals("SETUP_PENDING", string(vault, "status"));
        assertFalse(vault.containsKey("vaultDoorX"), "empty-area placeholder vault must not fake a door anchor");
    }

    @Test
    void malformedAreasAssignmentsAndPremisesAreIgnoredAndRepeatedMigrationIsStable() throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        List<Map<String, Object>> areas = new ArrayList<>();
        areas.add(area(" Minecraft:OverWorld ", 5, 66, 12, 1, 63, 9));
        areas.add(map("dimension", "minecraft:overworld", "minX", "bad", "minY", 1, "minZ", 1, "maxX", 2, "maxY", 2, "maxZ", 2));
        areas.add(map("dimension", "", "minX", 0, "maxX", -1));
        metadata.put("safeDepositAreas", areas);
        metadata.put("safeDepositAssignments", new ArrayList<>(List.of(
                map("dimension", "minecraft:overworld"),
                assignment(UUID.randomUUID(), BANK_ID, "minecraft:overworld", 3, 64, 10, 99)
        )));
        metadata.put("safeDepositPremises", new ArrayList<>(List.of(
                map("id", "", "safeAreas", "not-a-list")
        )));

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID));
        String migrated = metadata.get("safeDepositPremises").toString();

        assertEquals(1, list(metadata, "safeDepositPremises").size(), "only valid legacy area should be migrated");
        assertEquals(1, list(singleSafeArea(first(list(metadata, "safeDepositPremises"))), "vaults").size());
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID));
        assertEquals(migrated, metadata.get("safeDepositPremises").toString());
    }

    @Test
    void nbtAdapterBoundaryPreservesLegacyTagsAndUuidRelatedFieldsWhileWritingSetupPremises() throws Exception {
        Map<String, Object> metadata = legacyMetadata(List.of(assignment(ACCOUNT_ID, BANK_ID, "minecraft:overworld", 3, 64, 10, 2)));
        metadata.put("safeDepositEscrow", new ArrayList<>(List.of(map(
                "accountId", ACCOUNT_ID,
                "bankId", BANK_ID,
                "contents", map("slot", 3, "itemId", "minecraft:diamond")
        ))));
        String legacyAreasBefore = metadata.get("safeDepositAreas").toString();
        String legacyAssignmentsBefore = metadata.get("safeDepositAssignments").toString();
        String legacyEscrowBefore = metadata.get("safeDepositEscrow").toString();

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID));

        assertEquals(legacyAreasBefore, metadata.get("safeDepositAreas").toString());
        assertEquals(legacyAssignmentsBefore, metadata.get("safeDepositAssignments").toString());
        assertEquals(legacyEscrowBefore, metadata.get("safeDepositEscrow").toString());
        Map<String, Object> escrow = first(list(metadata, "safeDepositEscrow"));
        assertEquals(ACCOUNT_ID, escrow.get("accountId"));
        assertEquals(BANK_ID, escrow.get("bankId"));
        assertEquals(1, list(metadata, "safeDepositPremises").size());
    }

    @Test
    void nbtCodecMigratesRealCompoundTagsWithoutTouchingLegacyListsRentEscrowOrUuidFields() throws Exception {
        Object metadata = legacyNbtMetadata();
        Object legacyAreasBefore = copyNbt(getNbt(metadata, "safeDepositAreas"));
        Object legacyAssignmentsBefore = copyNbt(getNbt(metadata, "safeDepositAssignments"));
        Object legacyEscrowBefore = copyNbt(getNbt(metadata, "safeDepositEscrow"));
        Object legacyRentBefore = copyNbt(getNbt(metadata, "safeDepositRentAmount"));
        Object legacyBoxContentsBefore = copyNbt(getNbt(metadata, "safeDepositBoxContents"));

        assertTrue(migrateNbt(metadata));

        assertEquals(legacyAreasBefore, getNbt(metadata, "safeDepositAreas"));
        assertEquals(legacyAssignmentsBefore, getNbt(metadata, "safeDepositAssignments"));
        assertEquals(legacyEscrowBefore, getNbt(metadata, "safeDepositEscrow"));
        assertEquals(legacyRentBefore, getNbt(metadata, "safeDepositRentAmount"));
        assertEquals(legacyBoxContentsBefore, getNbt(metadata, "safeDepositBoxContents"));
        Object assignment = getCompound(getList(metadata, "safeDepositAssignments", TAG_COMPOUND), 0);
        assertTrue(contains(assignment, "accountId", TAG_INT_ARRAY));
        assertTrue(contains(assignment, "bankId", TAG_INT_ARRAY));
        assertTrue(hasUuid(assignment, "accountId"));
        assertTrue(hasUuid(assignment, "bankId"));
        assertEquals(ACCOUNT_ID, getUuid(assignment, "accountId"));
        assertEquals(BANK_ID, getUuid(assignment, "bankId"));
        assertEquals("minecraft:overworld:3,64,10#2", getString(assignment, "legacyLocation"));
        Object escrow = getCompound(getList(metadata, "safeDepositEscrow", TAG_COMPOUND), 0);
        assertTrue(contains(escrow, "accountId", TAG_INT_ARRAY));
        assertTrue(hasUuid(escrow, "accountId"));
        assertEquals(ACCOUNT_ID, getUuid(escrow, "accountId"));

        assertEquals(1, getInt(metadata, "safeDepositSetupVersion"));
        Object premises = getList(metadata, "safeDepositPremises", TAG_COMPOUND);
        assertEquals(1, size(premises));
        Object premise = getCompound(premises, 0);
        assertEquals(BANK_ID.toString(), getString(premise, "bankId"));
        Object vault = getCompound(getList(getCompound(getList(premise, "safeAreas", TAG_COMPOUND), 0),
                "vaults", TAG_COMPOUND), 0);
        assertEquals("SETUP_PENDING", getString(vault, "status"));
        assertTrue(contains(vault, "routeHooks", TAG_LIST));

        Object migratedBeforeSecondRun = copyNbt(metadata);
        assertFalse(migrateNbt(metadata), "second NBT migration should be idempotent and report unchanged");
        assertEquals(migratedBeforeSecondRun, metadata, "second NBT migration must leave the tag equal to its copy");
    }

    @Test
    void nbtCodecChangedFlagMatchesCurrentTagMutationSemantics() throws Exception {
        Object valid = currentValidSetupNbt("PUBLIC", "SETUP_PENDING");
        putString(valid, "unrelatedCurrentField", "preserve");
        Object validBefore = copyNbt(valid);

        assertFalse(migrateNbt(valid),
                "valid current setup should not be rewritten or reported as changed");
        assertEquals(validBefore, valid, "changed=false must leave the current tag structure equivalent");

        Object invalidMode = currentValidSetupNbt("NOT_A_REAL_MODE", "SETUP_PENDING");
        Object invalidBefore = copyNbt(invalidMode);

        assertTrue(migrateNbt(invalidMode),
                "removing malformed persisted setup data must be reported as changed");
        assertNotEquals(invalidBefore, invalidMode);
        assertEquals(0, size(getList(invalidMode, "safeDepositPremises", TAG_COMPOUND)));

        Object invalidStatus = currentValidSetupNbt("PUBLIC", "DONE_ENOUGH");
        Object invalidStatusBefore = copyNbt(invalidStatus);

        assertTrue(migrateNbt(invalidStatus),
                "removing malformed persisted vault data must be reported as changed");
        assertNotEquals(invalidStatusBefore, invalidStatus);
        assertEquals(0, size(getList(invalidStatus, "safeDepositPremises", TAG_COMPOUND)));
    }

    @Test
    void emptyPremiseRoundTripsWithoutSetupVersionBump() throws Exception {
        Map<String, Object> metadata = currentEmptyPremiseMap();
        String mapBefore = metadata.toString();

        SafeDepositSetupSnapshot mapSnapshot = SafeDepositSetupMigration.snapshot(metadata);
        assertEquals(1, mapSnapshot.version());
        assertEquals(1, mapSnapshot.premises().size());
        assertTrue(mapSnapshot.premises().get(0).safeAreas().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> mapSnapshot.premises().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> mapSnapshot.premises().get(0).safeAreas().clear());
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "a current explicit empty premise must not be rewritten");
        assertEquals(mapBefore, metadata.toString());
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "a second map migration must remain idempotent");
        assertEquals(mapBefore, metadata.toString());

        Object nbt = currentEmptyPremiseNbt();
        Object nbtBefore = copyNbt(nbt);
        Object nbtSnapshot = snapshotNbt(nbt);
        assertEquals(1, value(nbtSnapshot, "version"));
        List<Object> nbtPremises = listValue(nbtSnapshot, "premises");
        assertEquals(1, nbtPremises.size());
        assertTrue(listValue(nbtPremises.get(0), "safeAreas").isEmpty());
        assertThrows(UnsupportedOperationException.class, nbtPremises::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> listValue(nbtPremises.get(0), "safeAreas").clear());
        assertFalse(migrateNbt(nbt), "a current explicit empty NBT premise must not be rewritten");
        assertEquals(nbtBefore, nbt);
        assertFalse(migrateNbt(nbt), "a second NBT migration must remain idempotent");
        assertEquals(nbtBefore, nbt);
        assertEquals(1, getInt(nbt, "safeDepositSetupVersion"));
        Object storedPremise = getCompound(getList(nbt, "safeDepositPremises", TAG_COMPOUND), 0);
        assertEquals(0, size(getList(storedPremise, "safeAreas", TAG_COMPOUND)));
    }

    @Test
    void missingOrMalformedSafeAreaCollectionDoesNotBecomeAnEmptyPremise() throws Exception {
        Map<String, Object> missing = currentEmptyPremiseMap();
        first(list(missing, "safeDepositPremises")).remove("safeAreas");
        Map<String, Object> wrongType = currentEmptyPremiseMap();
        first(list(wrongType, "safeDepositPremises")).put("safeAreas", "not-a-list");
        Map<String, Object> allInvalid = currentEmptyPremiseMap();
        first(list(allInvalid, "safeDepositPremises")).put("safeAreas",
                new ArrayList<>(List.of(map("id", "", "premiseId", "premise-1"))));

        assertTrue(SafeDepositSetupMigration.snapshot(missing).premises().isEmpty());
        assertTrue(SafeDepositSetupMigration.snapshot(wrongType).premises().isEmpty());
        assertTrue(SafeDepositSetupMigration.snapshot(allInvalid).premises().isEmpty());

        Object missingNbt = currentEmptyPremiseNbt();
        removeNbt(firstPremiseNbt(missingNbt), "safeAreas");
        Object wrongTypeNbt = currentEmptyPremiseNbt();
        putString(firstPremiseNbt(wrongTypeNbt), "safeAreas", "not-a-list");
        Object allInvalidNbt = currentEmptyPremiseNbt();
        Object invalidChildren = newListTag();
        Object invalidChild = newCompoundTag();
        putString(invalidChild, "id", "");
        putString(invalidChild, "premiseId", "premise-1");
        addToList(invalidChildren, invalidChild);
        putTag(firstPremiseNbt(allInvalidNbt), "safeAreas", invalidChildren);

        assertTrue(listValue(snapshotNbt(missingNbt), "premises").isEmpty());
        assertTrue(listValue(snapshotNbt(wrongTypeNbt), "premises").isEmpty());
        assertTrue(listValue(snapshotNbt(allInvalidNbt), "premises").isEmpty());
    }

    @Test
    void safeAreaMustBeContainedByItsParentPremise() throws Exception {
        Map<String, Object> metadata = currentValidSetupMap("PUBLIC", "SETUP_PENDING");
        Map<String, Object> safeArea = singleSafeArea(first(list(metadata, "safeDepositPremises")));
        safeArea.put("maxX", 6);

        assertTrue(SafeDepositSetupMigration.snapshot(metadata).premises().isEmpty(),
                "a child extending outside its parent premise must reject the premise");

        Object nbt = currentValidSetupNbt("PUBLIC", "SETUP_PENDING");
        Object premise = firstPremiseNbt(nbt);
        Object child = getCompound(getList(premise, "safeAreas", TAG_COMPOUND), 0);
        putInt(child, "maxX", 6);

        assertTrue(listValue(snapshotNbt(nbt), "premises").isEmpty(),
                "the real-NBT adapter must enforce the same parent containment rule");
    }

    @Test
    void exactRemovalLeavesExplicitEmptyCustomParent() {
        String premiseId = "custom-premise-exact-removal";
        Map<String, Object> premiseBounds = area("minecraft:overworld", 0, 60, 0, 20, 80, 20);
        Map<String, Object> removedBounds = area("minecraft:overworld", 2, 64, 2, 8, 70, 8);
        Map<String, Object> premise = customPremiseMap(premiseId, premiseBounds);
        premise.put("safeAreas", new ArrayList<>(List.of(
                generatedNestedSafeAreaMap(premiseId, removedBounds)
        )));
        Map<String, Object> premiseConfigurationBefore = premiseConfiguration(premise);
        Map<String, Object> metadata = map(
                "safeDepositSetupVersion", 1,
                "safeDepositAreas", new ArrayList<Map<String, Object>>(),
                "safeDepositPremises", new ArrayList<>(List.of(premise))
        );

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "removing the exact legacy bounds must remove its generated nested child");
        List<Map<String, Object>> premises = list(metadata, "safeDepositPremises");
        assertEquals(1, premises.size(), "the custom parent must remain explicit after its last safe area is removed");
        Map<String, Object> retainedParent = premises.get(0);
        assertEquals(premiseConfigurationBefore, premiseConfiguration(retainedParent));
        assertTrue(list(retainedParent, "safeAreas").isEmpty(),
                "exact removal must leave the custom parent with an explicit empty child list");

        String afterFirstMigration = metadata.toString();
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "reconciling the explicit empty parent a second time must be idempotent");
        assertEquals(afterFirstMigration, metadata.toString());
    }

    @Test
    void subdivisionKeepsFragmentsNestedUnderSameCustomParent() {
        String premiseId = "custom-premise-subdivision-parent";
        Map<String, Object> premiseBounds = area("minecraft:overworld", 0, 60, 0, 30, 80, 30);
        Map<String, Object> originalBounds = area("minecraft:overworld", 2, 64, 2, 10, 70, 10);
        Map<String, Object> firstFragment = area("minecraft:overworld", 2, 64, 2, 5, 70, 10);
        Map<String, Object> secondFragment = area("minecraft:overworld", 6, 64, 2, 10, 70, 10);
        Map<String, Object> siblingBounds = area("minecraft:overworld", 16, 64, 16, 22, 70, 22);

        Map<String, Object> originalChild = generatedNestedSafeAreaMap(premiseId, originalBounds);
        Map<String, Object> siblingChild = generatedNestedSafeAreaMap(premiseId, siblingBounds);
        String siblingSafeAreaId = "custom-sibling-safe-area";
        String siblingVaultId = "custom-sibling-vault";
        siblingChild.put("id", siblingSafeAreaId);
        Map<String, Object> siblingVault = singleVault(siblingChild);
        siblingVault.put("id", siblingVaultId);
        siblingVault.put("safeAreaId", siblingSafeAreaId);
        configureNestedVaultForPreservation(siblingChild);
        String siblingBefore = siblingChild.toString();

        Map<String, Object> premise = customPremiseMap(premiseId, premiseBounds);
        premise.put("safeAreas", new ArrayList<>(List.of(originalChild, siblingChild)));
        Map<String, Object> premiseConfigurationBefore = premiseConfiguration(premise);
        Map<String, Object> metadata = map(
                "safeDepositSetupVersion", 1,
                "safeDepositAreas", new ArrayList<>(List.of(firstFragment, secondFragment, siblingBounds)),
                "safeDepositPremises", new ArrayList<>(List.of(premise))
        );

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "subdivision must replace the generated nested child with nested fragments");
        List<Map<String, Object>> premises = list(metadata, "safeDepositPremises");
        assertEquals(1, premises.size(), "subdivision must not create fragment-sized migration premises");
        Map<String, Object> retainedParent = premises.get(0);
        assertEquals(premiseConfigurationBefore, premiseConfiguration(retainedParent),
                "custom parent id, bounds, exit, and mode must survive subdivision");

        List<Map<String, Object>> safeAreas = list(retainedParent, "safeAreas");
        assertEquals(3, safeAreas.size(), "two generated fragments and the configured sibling must remain nested");
        assertNull(safeAreaById(safeAreas, generatedSafeAreaId(premiseId, originalBounds)),
                "the unsubdivided generated child must be removed");
        assertGeneratedNestedSafeArea(
                safeAreaById(safeAreas, generatedSafeAreaId(premiseId, firstFragment)), premiseId, firstFragment);
        assertGeneratedNestedSafeArea(
                safeAreaById(safeAreas, generatedSafeAreaId(premiseId, secondFragment)), premiseId, secondFragment);
        Map<String, Object> siblingAfter = safeAreaById(safeAreas, siblingSafeAreaId);
        assertNotEquals(null, siblingAfter, "the configured sibling safe area must remain nested");
        assertEquals(siblingBefore, siblingAfter.toString(),
                "configured sibling safe-area/vault ids, door data, and route hooks must remain byte-equivalent");
        assertEquals(siblingVaultId, string(singleVault(siblingAfter), "id"));
        assertNull(premiseById(premises, migrationPremiseId(firstFragment)));
        assertNull(premiseById(premises, migrationPremiseId(secondFragment)));
        assertNull(premiseById(premises, migrationPremiseId(siblingBounds)));

        String afterFirstMigration = metadata.toString();
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "repeated subdivision reconciliation must be idempotent");
        assertEquals(afterFirstMigration, metadata.toString());
    }

    @Test
    void secondMigrationIsIdempotentAndDoesNotAddSameSizedMigrationPremise() {
        String premiseId = "custom-premise-backed-child";
        Map<String, Object> premiseBounds = area("minecraft:overworld", 0, 60, 0, 20, 80, 20);
        Map<String, Object> nestedBounds = area("minecraft:overworld", 4, 64, 4, 8, 70, 8);
        Map<String, Object> nestedChild = generatedNestedSafeAreaMap(premiseId, nestedBounds);
        String nestedChildBefore = nestedChild.toString();
        Map<String, Object> premise = customPremiseMap(premiseId, premiseBounds);
        premise.put("safeAreas", new ArrayList<>(List.of(nestedChild)));
        Map<String, Object> premiseConfigurationBefore = premiseConfiguration(premise);
        Map<String, Object> metadata = map(
                "safeDepositAreas", new ArrayList<>(List.of(nestedBounds)),
                "safeDepositPremises", new ArrayList<>(List.of(premise))
        );

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "the first migration must only establish the setup version");
        List<Map<String, Object>> premises = list(metadata, "safeDepositPremises");
        assertEquals(1, premises.size(),
                "a legacy area already represented by one nested child must not create a same-sized premise");
        assertNull(premiseById(premises, migrationPremiseId(nestedBounds)));
        Map<String, Object> retainedParent = premiseById(premises, premiseId);
        assertNotEquals(null, retainedParent);
        assertEquals(premiseConfigurationBefore, premiseConfiguration(retainedParent));
        assertEquals(nestedChildBefore, singleSafeArea(retainedParent).toString());

        String afterFirstMigration = metadata.toString();
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "the second migration must report unchanged");
        assertEquals(afterFirstMigration, metadata.toString(),
                "the second migration must not append or rewrite setup records");
        assertEquals(1, list(metadata, "safeDepositPremises").size());
    }

    @Test
    void mapMigrationRemovesOnlyUnbackedMigrationOwnedPremisesWhenLegacyAreasAreRemoved() throws Exception {
        Map<String, Object> metadata = legacyMetadata(List.of(
                assignment(ACCOUNT_ID, BANK_ID, "minecraft:overworld", 3, 64, 10, 2)
        ));
        metadata.put("safeDepositBoxContents", new ArrayList<>(List.of(map("slot", 4, "itemId", "minecraft:diamond"))));
        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID));
        String migratedPremiseId = string(first(list(metadata, "safeDepositPremises")), "id");
        Map<String, Object> customPremise = customPremiseMap("custom-premise-remove",
                area("minecraft:the_nether", 100, 40, 100, 104, 44, 104));
        list(metadata, "safeDepositPremises").add(customPremise);
        String customBefore = customPremise.toString();
        String assignmentsBefore = metadata.get("safeDepositAssignments").toString();
        String escrowBefore = metadata.get("safeDepositEscrow").toString();
        String rentBefore = String.valueOf(metadata.get("safeDepositRentAmount"));
        String contentsBefore = metadata.get("safeDepositBoxContents").toString();

        metadata.put("safeDepositAreas", new ArrayList<Map<String, Object>>());

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "synchronizing removed legacy areas must delete stale migration-owned premises");
        List<Map<String, Object>> premises = list(metadata, "safeDepositPremises");
        assertEquals(1, premises.size(), "only the custom premise should survive after all legacy areas are removed");
        assertNull(premiseById(premises, migratedPremiseId), "removed legacy bounds must not leave a ghost premise");
        assertEquals(customBefore, premises.get(0).toString(), "custom premise data must be structurally preserved");
        assertEquals(assignmentsBefore, metadata.get("safeDepositAssignments").toString());
        assertEquals(escrowBefore, metadata.get("safeDepositEscrow").toString());
        assertEquals(rentBefore, String.valueOf(metadata.get("safeDepositRentAmount")));
        assertEquals(contentsBefore, metadata.get("safeDepositBoxContents").toString());
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "repeated synchronization after removal should be idempotent");
    }

    @Test
    void mapMigrationReconcilesSubdividedLegacyAreasWithoutDeletingCustomPremises() throws Exception {
        Map<String, Object> originalArea = area(" Minecraft:OverWorld ", 1, 63, 9, 5, 66, 12);
        Map<String, Object> replacedArea = area("minecraft:overworld", 20, 63, 9, 24, 66, 12);
        Map<String, Object> firstReplacement = area("minecraft:overworld", 20, 63, 9, 21, 66, 12);
        Map<String, Object> secondReplacement = area("minecraft:overworld", 22, 63, 9, 24, 66, 12);
        Map<String, Object> metadata = legacyMetadata(List.of());
        metadata.put("safeDepositAreas", new ArrayList<>(List.of(originalArea, replacedArea)));
        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID));
        String originalPremiseId = migrationPremiseId(originalArea);
        String replacedPremiseId = migrationPremiseId(replacedArea);
        Map<String, Object> originalPremise = premiseById(list(metadata, "safeDepositPremises"), originalPremiseId);
        configurePremiseForPreservation(originalPremise);
        Map<String, Object> customPremise = customPremiseMap("custom-premise-subdivide",
                area("minecraft:the_end", -10, 50, -10, -6, 54, -6));
        list(metadata, "safeDepositPremises").add(customPremise);
        String originalBefore = originalPremise.toString();
        String customBefore = customPremise.toString();

        metadata.put("safeDepositAreas", new ArrayList<>(List.of(originalArea, firstReplacement, secondReplacement)));

        assertTrue(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "subdividing a legacy area must replace only its old migration-owned premise");
        List<Map<String, Object>> premises = list(metadata, "safeDepositPremises");
        assertEquals(4, premises.size(), "matching original, two replacements, and custom premise should remain");
        assertEquals(originalBefore, premiseById(premises, originalPremiseId).toString(),
                "configured data on a still-backed migration-owned premise must be preserved");
        assertNull(premiseById(premises, replacedPremiseId), "old subdivided bounds must not remain as a ghost premise");
        assertEquals(customBefore, premiseById(premises, "custom-premise-subdivide").toString());
        assertMigrationOwnedPendingPremise(premises, firstReplacement);
        assertMigrationOwnedPendingPremise(premises, secondReplacement);
        assertFalse(SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID),
                "repeated subdivision synchronization should report unchanged");
    }

    @Test
    void nbtCodecReconcilesRemovedLegacyAreasWithoutTouchingLegacyListsRentEscrowOrUuidFields() throws Exception {
        Object metadata = legacyNbtMetadata();
        assertTrue(migrateNbt(metadata));
        Object premises = getList(metadata, "safeDepositPremises", TAG_COMPOUND);
        String migratedPremiseId = getString(getCompound(premises, 0), "id");
        Object customPremise = customPremiseNbt("custom-premise-nbt-remove",
                "minecraft:the_nether", 100, 40, 100, 104, 44, 104);
        addToList(premises, customPremise);
        Object customBefore = copyNbt(customPremise);
        Object assignmentsBefore = copyNbt(getNbt(metadata, "safeDepositAssignments"));
        Object escrowBefore = copyNbt(getNbt(metadata, "safeDepositEscrow"));
        Object rentBefore = copyNbt(getNbt(metadata, "safeDepositRentAmount"));
        Object boxContentsBefore = copyNbt(getNbt(metadata, "safeDepositBoxContents"));

        putTag(metadata, "safeDepositAreas", newListTag());

        assertTrue(migrateNbt(metadata), "NBT sync must report changed when it removes a ghost typed premise");
        Object afterPremises = getList(metadata, "safeDepositPremises", TAG_COMPOUND);
        assertEquals(1, size(afterPremises));
        assertNull(nbtPremiseById(afterPremises, migratedPremiseId));
        assertEquals(customBefore, getCompound(afterPremises, 0));
        assertEquals(assignmentsBefore, getNbt(metadata, "safeDepositAssignments"));
        assertEquals(escrowBefore, getNbt(metadata, "safeDepositEscrow"));
        assertEquals(rentBefore, getNbt(metadata, "safeDepositRentAmount"));
        assertEquals(boxContentsBefore, getNbt(metadata, "safeDepositBoxContents"));
        Object assignment = getCompound(getList(metadata, "safeDepositAssignments", TAG_COMPOUND), 0);
        assertTrue(hasUuid(assignment, "accountId"));
        assertTrue(hasUuid(assignment, "bankId"));
        assertFalse(migrateNbt(metadata), "second NBT removal sync should be idempotent");
    }

    @Test
    void migrationRewritePreservesOpaqueNestedNbtTypes() throws Exception {
        Object metadata = legacyNbtMetadata();
        assertTrue(migrateNbt(metadata), "fixture setup must create a migration-owned premise");

        Object premises = getList(metadata, "safeDepositPremises", TAG_COMPOUND);
        String customPremiseId = "custom-premise-opaque-nbt";
        Object customPremise = customPremiseNbt(customPremiseId,
                "minecraft:the_nether", 100, 40, 100, 104, 44, 104);
        Object opaqueNested = newCompoundTag();
        putByte(opaqueNested, "nonBooleanByte", (byte) 7);

        Object primitiveList = newListTag();
        Object primitiveValues = newCompoundTag();
        putInt(primitiveValues, "first", 13);
        putInt(primitiveValues, "second", -21);
        addToList(primitiveList, getNbt(primitiveValues, "first"));
        addToList(primitiveList, getNbt(primitiveValues, "second"));
        putTag(opaqueNested, "primitiveList", primitiveList);
        putByteArray(opaqueNested, "byteArray", new byte[]{0, 7, -1});
        putIntArray(opaqueNested, "intArray", new int[]{Integer.MIN_VALUE, 17, Integer.MAX_VALUE});
        putLongArray(opaqueNested, "longArray", new long[]{Long.MIN_VALUE, 23L, Long.MAX_VALUE});

        Object compoundSentinel = newCompoundTag();
        putString(compoundSentinel, "marker", "opaque-nested-sentinel");
        putInt(compoundSentinel, "revision", 4);
        putTag(opaqueNested, "compoundSentinel", compoundSentinel);
        putTag(customPremise, "opaqueNested", opaqueNested);
        addToList(premises, customPremise);

        Object expectedByte = copyNbt(getNbt(opaqueNested, "nonBooleanByte"));
        Object expectedPrimitiveList = copyNbt(getNbt(opaqueNested, "primitiveList"));
        Object expectedByteArray = copyNbt(getNbt(opaqueNested, "byteArray"));
        Object expectedIntArray = copyNbt(getNbt(opaqueNested, "intArray"));
        Object expectedLongArray = copyNbt(getNbt(opaqueNested, "longArray"));
        Object expectedCompoundSentinel = copyNbt(getNbt(opaqueNested, "compoundSentinel"));

        putTag(metadata, "safeDepositAreas", newListTag());

        assertTrue(migrateNbt(metadata),
                "removing a separate migration-owned premise must force a real codec rewrite");
        Object rewrittenPremise = nbtPremiseById(
                getList(metadata, "safeDepositPremises", TAG_COMPOUND), customPremiseId);
        assertNotEquals(null, rewrittenPremise, "the otherwise-valid custom premise must survive reconciliation");
        assertTrue(contains(rewrittenPremise, "opaqueNested", TAG_COMPOUND),
                "the unknown nested compound must survive the premise rewrite");
        Object rewrittenOpaque = getNbt(rewrittenPremise, "opaqueNested");

        assertAll(
                () -> assertTrue(contains(rewrittenOpaque, "nonBooleanByte", TAG_BYTE),
                        "the non-boolean byte must remain a ByteTag"),
                () -> assertEquals(expectedByte, getNbt(rewrittenOpaque, "nonBooleanByte"),
                        "the non-boolean ByteTag value must survive exactly"),
                () -> assertTrue(contains(rewrittenOpaque, "primitiveList", TAG_LIST),
                        "the primitive values must remain a ListTag"),
                () -> assertEquals(expectedPrimitiveList, getNbt(rewrittenOpaque, "primitiveList"),
                        "the primitive ListTag element type and values must survive exactly"),
                () -> assertTrue(contains(rewrittenOpaque, "byteArray", TAG_BYTE_ARRAY),
                        "the byte array must remain a ByteArrayTag"),
                () -> assertEquals(expectedByteArray, getNbt(rewrittenOpaque, "byteArray"),
                        "the ByteArrayTag values must survive exactly"),
                () -> assertTrue(contains(rewrittenOpaque, "intArray", TAG_INT_ARRAY),
                        "the int array must remain an IntArrayTag"),
                () -> assertEquals(expectedIntArray, getNbt(rewrittenOpaque, "intArray"),
                        "the IntArrayTag values must survive exactly"),
                () -> assertTrue(contains(rewrittenOpaque, "longArray", TAG_LONG_ARRAY),
                        "the long array must remain a LongArrayTag"),
                () -> assertEquals(expectedLongArray, getNbt(rewrittenOpaque, "longArray"),
                        "the LongArrayTag values must survive exactly"),
                () -> assertTrue(contains(rewrittenOpaque, "compoundSentinel", TAG_COMPOUND),
                        "the ordinary nested sentinel must remain a CompoundTag"),
                () -> assertEquals(expectedCompoundSentinel, getNbt(rewrittenOpaque, "compoundSentinel"),
                        "the ordinary nested CompoundTag must survive exactly")
        );
    }

    @Test
    void nbtCodecReconcilesSubdividedLegacyAreasAndPreservesCustomPremises() throws Exception {
        Object metadata = legacyNbtMetadata();
        assertTrue(migrateNbt(metadata));
        Object premises = getList(metadata, "safeDepositPremises", TAG_COMPOUND);
        String originalPremiseId = getString(getCompound(premises, 0), "id");
        Object customPremise = customPremiseNbt("custom-premise-nbt-subdivide",
                "minecraft:the_end", -10, 50, -10, -6, 54, -6);
        addToList(premises, customPremise);
        Object customBefore = copyNbt(customPremise);
        Object replacementAreas = newListTag();
        addToList(replacementAreas, areaNbt("minecraft:overworld", 1, 63, 9, 2, 66, 12));
        addToList(replacementAreas, areaNbt("minecraft:overworld", 3, 63, 9, 5, 66, 12));

        putTag(metadata, "safeDepositAreas", replacementAreas);

        assertTrue(migrateNbt(metadata), "NBT sync must replace the original typed premise with subdivision premises");
        Object afterPremises = getList(metadata, "safeDepositPremises", TAG_COMPOUND);
        assertEquals(3, size(afterPremises), "two migration-owned subdivision premises and one custom premise should remain");
        assertNull(nbtPremiseById(afterPremises, originalPremiseId));
        assertEquals(customBefore, nbtPremiseById(afterPremises, "custom-premise-nbt-subdivide"));
        assertNotEquals(null, nbtPremiseById(afterPremises, migrationPremiseId(
                area("minecraft:overworld", 1, 63, 9, 2, 66, 12))));
        assertNotEquals(null, nbtPremiseById(afterPremises, migrationPremiseId(
                area("minecraft:overworld", 3, 63, 9, 5, 66, 12))));
        assertFalse(migrateNbt(metadata), "second NBT subdivision sync should be idempotent");
    }

    @Test
    void snapshotApiExposesImmutableTypedReadModelsInsteadOfMapContract() throws Exception {
        Map<String, Object> metadata = legacyMetadata(List.of());
        SafeDepositSetupMigration.migrateLegacy(metadata, BANK_ID);

        SafeDepositSetupSnapshot snapshot = SafeDepositSetupMigration.snapshot(metadata);
        List<SafePremiseSnapshot> premises = snapshot.premises();

        assertEquals(1, premises.size());
        assertThrows(UnsupportedOperationException.class, premises::clear);
        SafePremiseSnapshot premise = premises.get(0);
        assertFalse(((Object) premise) instanceof Map<?, ?>,
                "production read model must not expose raw maps as the public contract");
        assertNotEquals("", premise.id());
        List<SafeAreaSnapshot> safeAreas = premise.safeAreas();
        assertThrows(UnsupportedOperationException.class, safeAreas::clear);
        SafeVaultSnapshot vault = safeAreas.get(0).vaults().get(0);
        assertFalse(((Object) vault) instanceof Map<?, ?>);
        assertEquals("SETUP_PENDING", String.valueOf(vault.status()));
    }

    @Test
    void staffOnlyPremiseModeSurvivesTypedSnapshotParsing() throws Exception {
        SafeDepositSetupSnapshot snapshot = SafeDepositSetupMigration.snapshot(currentValidSetupMap("STAFF_ONLY", "READY"));

        assertEquals(1, snapshot.premises().size());
        assertEquals(SafePremiseMode.STAFF_ONLY, snapshot.premises().get(0).mode());
    }

    @Test
    void malformedPersistedPremiseModeAndVaultStatusAreRejectedInsteadOfDefaulted() throws Exception {
        assertNull(SafePremiseMode.parse("visitors_maybe"));

        SafeDepositSetupSnapshot malformedMode = SafeDepositSetupMigration.snapshot(
                currentValidSetupMap("visitors_maybe", "READY"));
        assertTrue(malformedMode.premises().isEmpty(),
                "malformed stored premise mode must reject the persisted premise instead of defaulting to PUBLIC");

        SafeDepositSetupSnapshot malformedVaultStatus = SafeDepositSetupMigration.snapshot(
                currentValidSetupMap("PUBLIC", "DONE_ENOUGH"));
        assertTrue(malformedVaultStatus.premises().isEmpty(),
                "malformed stored vault status must reject the persisted vault instead of defaulting to SETUP_PENDING");
    }

    @Test
    void typedSnapshotExposesImmutableRouteHooksAndSkipsMalformedHooks() throws Exception {
        Map<String, Object> metadata = currentValidSetupMap("PUBLIC", "ROUTES_PENDING");
        Map<String, Object> vaultMap = singleVault(singleSafeArea(first(list(metadata, "safeDepositPremises"))));
        vaultMap.put("routeHooks", List.of(
                map("tellerId", "teller-1", "bankBound", true,
                        "outboundRouteRef", "route-out", "returnRouteRef", "route-back"),
                map("tellerId", "", "bankBound", true, "outboundRouteRef", "route-out")
        ));

        SafeVaultSnapshot vault = firstVault(SafeDepositSetupMigration.snapshot(metadata));
        List<SafeTellerRouteHook> routeHooks = vault.routeHooks();
        assertEquals(1, routeHooks.size());
        SafeTellerRouteHook hook = routeHooks.get(0);
        assertEquals("teller-1", hook.tellerId());
        assertTrue(hook.bankBound());
        assertEquals("route-out", hook.outboundRouteRef());
        assertEquals("route-back", hook.returnRouteRef());
        assertTrue(hook.ready());
        assertThrows(UnsupportedOperationException.class, routeHooks::clear);
    }

    @Test
    void derivesReadinessFromCurrentStructureAndWorldHooksInsteadOfPersistedReadyFlag() throws Exception {
        Object metadata = currentValidSetupNbt("PUBLIC", "SETUP_PENDING");
        Object vault = firstVaultNbt(metadata);
        putByte(vault, "ready", (byte) 1);

        Object snapshot = snapshotNbt(metadata);
        Object rowPos = blockPos(3, 64, 10);
        Object missing = resolveForRow(rowRequest(
                readinessContext(metadata,
                        loadedWorldFacts(Map.of(getString(vault, "id"), false), List.of())),
                snapshot, rowLocation(rowPos)));
        Object missingSummary = value(missing, "summary");
        assertFalse((Boolean) value(missingSummary, "ready"),
                "persisted ready=true must not override derived missing reasons");
        assertEquals(List.of("VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING"),
                listValue(missingSummary, "missingReasons").stream().map(Object::toString).toList());

        installExactRoutePair(metadata, vault);
        Object readySnapshot = snapshotNbt(metadata);
        Object ready = resolveForRow(rowRequest(
                readinessContext(metadata, loadedWorldFacts(
                        Map.of(getString(vault, "id"), true),
                        List.of(loadedRow("minecraft:overworld", rowPos, row("MEDIUM", "EMPTY", "MEDIUM", "EMPTY"))),
                        Set.of(getString(firstPremiseNbt(metadata), "id"))
                )), readySnapshot, rowLocation(rowPos)));
        Object readySummary = value(ready, "summary");
        assertTrue((Boolean) value(readySummary, "ready"));
        assertTrue(listValue(readySummary, "missingReasons").isEmpty());
    }

    private static Map<String, Object> legacyMetadata(List<Map<String, Object>> assignments) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        List<Map<String, Object>> areas = new ArrayList<>();
        areas.add(area(" Minecraft:OverWorld ", 5, 66, 12, 1, 63, 9));
        areas.add(map("dimension", "", "minX", 0, "maxX", -1));
        metadata.put("safeDepositAreas", areas);
        metadata.put("safeDepositAssignments", new ArrayList<>(assignments));
        metadata.put("safeDepositEscrow", new ArrayList<>(List.of(map(
                "accountId", ACCOUNT_ID.toString(),
                "contents", "do-not-touch"
        ))));
        metadata.put("safeDepositRentAmount", "42.00");
        return metadata;
    }

    private static Map<String, Object> area(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return map(
                "dimension", dimension,
                "minX", minX,
                "minY", minY,
                "minZ", minZ,
                "maxX", maxX,
                "maxY", maxY,
                "maxZ", maxZ
        );
    }

    private static Map<String, Object> assignment(UUID accountId, UUID bankId, String dimension, int x, int y, int z, int doorIndex) {
        return map(
                "accountId", accountId.toString(),
                "bankId", bankId.toString(),
                "dimension", dimension,
                "x", x,
                "y", y,
                "z", z,
                "doorIndex", doorIndex,
                "boxNumber", "SDB-" + doorIndex,
                "locked", false,
                "legacyLocation", dimension + ":" + x + "," + y + "," + z + "#" + doorIndex
        );
    }

    private static Map<String, Object> currentValidSetupMap(String mode, String status) {
        Map<String, Object> vault = map(
                "id", "vault-1",
                "safeAreaId", "safe-area-1",
                "dimension", "minecraft:overworld",
                "status", status,
                "routeHooks", new ArrayList<Map<String, Object>>()
        );
        Map<String, Object> safeArea = map(
                "id", "safe-area-1",
                "premiseId", "premise-1",
                "dimension", "minecraft:overworld",
                "minX", 1,
                "minY", 63,
                "minZ", 9,
                "maxX", 5,
                "maxY", 66,
                "maxZ", 12,
                "vaults", new ArrayList<>(List.of(vault))
        );
        Map<String, Object> premise = map(
                "id", "premise-1",
                "bankId", BANK_ID.toString(),
                "dimension", "minecraft:overworld",
                "minX", 1,
                "minY", 63,
                "minZ", 9,
                "maxX", 5,
                "maxY", 66,
                "maxZ", 12,
                "exitX", 0,
                "exitY", 63,
                "exitZ", 9,
                "exitYaw", 180.0F,
                "mode", mode,
                "safeAreas", new ArrayList<>(List.of(safeArea))
        );
        return map(
                "safeDepositSetupVersion", 1,
                "safeDepositPremises", new ArrayList<>(List.of(premise))
        );
    }

    private static Map<String, Object> currentEmptyPremiseMap() {
        Map<String, Object> metadata = currentValidSetupMap("PUBLIC", "SETUP_PENDING");
        first(list(metadata, "safeDepositPremises")).put("safeAreas", new ArrayList<Map<String, Object>>());
        return metadata;
    }

    private static Object legacyNbtMetadata() throws Exception {
        Object metadata = newCompoundTag();
        Object areas = newListTag();
        Object area = newCompoundTag();
        putString(area, "dimension", " Minecraft:OverWorld ");
        putInt(area, "minX", 1);
        putInt(area, "minY", 63);
        putInt(area, "minZ", 9);
        putInt(area, "maxX", 5);
        putInt(area, "maxY", 66);
        putInt(area, "maxZ", 12);
        addToList(areas, area);
        putTag(metadata, "safeDepositAreas", areas);

        Object assignments = newListTag();
        Object assignment = newCompoundTag();
        putUuid(assignment, "accountId", ACCOUNT_ID);
        putUuid(assignment, "bankId", BANK_ID);
        putString(assignment, "dimension", "minecraft:overworld");
        putInt(assignment, "x", 3);
        putInt(assignment, "y", 64);
        putInt(assignment, "z", 10);
        putInt(assignment, "doorIndex", 2);
        putString(assignment, "legacyLocation", "minecraft:overworld:3,64,10#2");
        addToList(assignments, assignment);
        putTag(metadata, "safeDepositAssignments", assignments);

        Object escrow = newListTag();
        Object escrowEntry = newCompoundTag();
        putUuid(escrowEntry, "accountId", ACCOUNT_ID);
        putUuid(escrowEntry, "bankId", BANK_ID);
        Object contents = newCompoundTag();
        putString(contents, "itemId", "minecraft:diamond");
        putTag(escrowEntry, "contents", contents);
        addToList(escrow, escrowEntry);
        putTag(metadata, "safeDepositEscrow", escrow);
        Object boxContents = newListTag();
        Object boxEntry = newCompoundTag();
        putInt(boxEntry, "slot", 4);
        putTag(boxEntry, "stack", copyNbt(contents));
        addToList(boxContents, boxEntry);
        putTag(metadata, "safeDepositBoxContents", boxContents);
        putString(metadata, "safeDepositRentAmount", "42.00");
        return metadata;
    }

    private static Object currentValidSetupNbt(String mode, String status) throws Exception {
        Object metadata = newCompoundTag();
        putInt(metadata, "safeDepositSetupVersion", 1);
        Object premises = newListTag();
        Object premise = newCompoundTag();
        putString(premise, "id", "premise-1");
        putString(premise, "bankId", BANK_ID.toString());
        putBounds(premise, "minecraft:overworld", 1, 63, 9, 5, 66, 12);
        putInt(premise, "exitX", 0);
        putInt(premise, "exitY", 63);
        putInt(premise, "exitZ", 9);
        putFloat(premise, "exitYaw", 180.0F);
        putString(premise, "mode", mode);

        Object safeAreas = newListTag();
        Object safeArea = newCompoundTag();
        putString(safeArea, "id", "safe-area-1");
        putString(safeArea, "premiseId", "premise-1");
        putBounds(safeArea, "minecraft:overworld", 1, 63, 9, 5, 66, 12);

        Object vaults = newListTag();
        Object vault = newCompoundTag();
        putString(vault, "id", "vault-1");
        putString(vault, "safeAreaId", "safe-area-1");
        putString(vault, "dimension", "minecraft:overworld");
        putString(vault, "status", status);
        putTag(vault, "routeHooks", newListTag());
        addToList(vaults, vault);
        putTag(safeArea, "vaults", vaults);
        addToList(safeAreas, safeArea);
        putTag(premise, "safeAreas", safeAreas);
        addToList(premises, premise);
        putTag(metadata, "safeDepositPremises", premises);
        return metadata;
    }

    private static Object currentEmptyPremiseNbt() throws Exception {
        Object metadata = currentValidSetupNbt("PUBLIC", "SETUP_PENDING");
        putTag(firstPremiseNbt(metadata), "safeAreas", newListTag());
        return metadata;
    }

    private static Object firstPremiseNbt(Object metadata) throws Exception {
        return getCompound(getList(metadata, "safeDepositPremises", TAG_COMPOUND), 0);
    }

    private static Object firstVaultNbt(Object metadata) throws Exception {
        Object premise = firstPremiseNbt(metadata);
        Object safeArea = getCompound(getList(premise, "safeAreas", TAG_COMPOUND), 0);
        return getCompound(getList(safeArea, "vaults", TAG_COMPOUND), 0);
    }

    private static void installExactRoutePair(Object metadata, Object vault) throws Exception {
        String bankId = BANK_ID.toString();
        String vaultId = getString(vault, "id");
        String tellerId = "30000000-0000-0000-0000-000000000003";
        String outboundId = stableRouteId(bankId, vaultId, tellerId, "OUTBOUND");
        String returnId = stableRouteId(bankId, vaultId, tellerId, "RETURN");

        Object hook = newCompoundTag();
        putString(hook, "tellerId", tellerId);
        putByte(hook, "bankBound", (byte) 1);
        putString(hook, "outboundRouteRef", outboundId);
        putString(hook, "returnRouteRef", returnId);
        Object hooks = newListTag();
        addToList(hooks, hook);
        putTag(vault, "routeHooks", hooks);

        Object routes = newListTag();
        addToList(routes, routeNbt(outboundId, bankId, vaultId, tellerId, "OUTBOUND"));
        addToList(routes, routeNbt(returnId, bankId, vaultId, tellerId, "RETURN"));
        putTag(metadata, "safeTellerRoutes", routes);
    }

    private static Object routeNbt(String id, String bankId, String vaultId,
                                   String tellerId, String direction) throws Exception {
        Object route = newCompoundTag();
        putString(route, "id", id);
        putString(route, "bankId", bankId);
        putString(route, "vaultId", vaultId);
        putString(route, "tellerId", tellerId);
        putString(route, "direction", direction);
        putString(route, "dimension", "minecraft:overworld");
        putInt(route, "startX", 1);
        putInt(route, "startY", 64);
        putInt(route, "startZ", 1);
        putInt(route, "finishX", 2);
        putInt(route, "finishY", 64);
        putInt(route, "finishZ", 2);
        Object wait = newCompoundTag();
        putString(wait, "type", "WAIT");
        putInt(wait, "durationTicks", 1);
        Object steps = newListTag();
        addToList(steps, wait);
        putTag(route, "steps", steps);
        return route;
    }

    private static String stableRouteId(String bankId, String vaultId, String tellerId, String direction) {
        String key = bankId + '\u001f' + vaultId + '\u001f' + tellerId + '\u001f' + direction;
        return "safe-teller-route-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBounds(Object tag,
                                  String dimension,
                                  int minX,
                                  int minY,
                                  int minZ,
                                  int maxX,
                                  int maxY,
                                  int maxZ) throws Exception {
        putString(tag, "dimension", dimension);
        putInt(tag, "minX", minX);
        putInt(tag, "minY", minY);
        putInt(tag, "minZ", minZ);
        putInt(tag, "maxX", maxX);
        putInt(tag, "maxY", maxY);
        putInt(tag, "maxZ", maxZ);
    }

    private static SafeVaultSnapshot firstVault(SafeDepositSetupSnapshot snapshot) {
        return snapshot.premises().get(0).safeAreas().get(0).vaults().get(0);
    }

    private static Object harnessSnapshot(Map<String, Object> metadata) throws Exception {
        return setupClass("SafeDepositSetupMigration").getMethod("snapshot", Map.class).invoke(null, metadata);
    }

    private static Object resolveForRow(Object request) throws Exception {
        return resolverClass().getMethod("resolveForRow", request.getClass()).invoke(null, request);
    }

    private static Object readinessContext(Object metadata, Object facts) throws Exception {
        return setupClass("SafeVaultReadinessResolver$EvaluationContext")
                .getConstructor(compoundTagClass(), loadedWorldFactsClass())
                .newInstance(metadata, facts);
    }

    private static Object rowLocation(Object rowPos) throws Exception {
        return setupClass("SafeVaultReadinessResolver$RowLocation")
                .getConstructor(String.class, blockPosClass())
                .newInstance("minecraft:overworld", rowPos);
    }

    private static Object rowRequest(Object context, Object snapshot, Object location) throws Exception {
        return setupClass("SafeVaultReadinessResolver$RowRequest")
                .getConstructor(context.getClass(), setupClass("SafeDepositSetupSnapshot"), location.getClass())
                .newInstance(context, snapshot, location);
    }

    private static Object loadedWorldFacts(Map<String, Boolean> completeVaultDoors, List<Object> loadedRows)
            throws Exception {
        return loadedWorldFactsClass()
                .getConstructor(Map.class, List.class)
                .newInstance(completeVaultDoors, loadedRows);
    }

    private static Object loadedWorldFacts(Map<String, Boolean> completeVaultDoors,
                                           List<Object> loadedRows,
                                           Set<String> readyViewingRoomPremiseIds) throws Exception {
        return loadedWorldFactsClass()
                .getConstructor(Map.class, Map.class, List.class, Set.class)
                .newInstance(completeVaultDoors, Map.of(), loadedRows, readyViewingRoomPremiseIds);
    }

    private static Object loadedRow(String dimension, Object pos, Object row) throws Exception {
        return loadedRowSnapshotClass()
                .getConstructor(String.class, blockPosClass(), moduleTypeArrayClass())
                .newInstance(dimension, pos, row);
    }

    private static Object row(String first, String second, String third, String fourth) throws Exception {
        Object row = java.lang.reflect.Array.newInstance(moduleTypeClass(), 4);
        java.lang.reflect.Array.set(row, 0, moduleType(first));
        java.lang.reflect.Array.set(row, 1, moduleType(second));
        java.lang.reflect.Array.set(row, 2, moduleType(third));
        java.lang.reflect.Array.set(row, 3, moduleType(fourth));
        return row;
    }

    private static Object moduleType(String name) throws Exception {
        return Enum.valueOf(moduleTypeClass().asSubclass(Enum.class), name);
    }

    private static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listValue(Object target, String accessor) throws Exception {
        return (List<Object>) value(target, accessor);
    }

    private static boolean migrateNbt(Object metadata) throws Exception {
        Class<?> codec = Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec",
                true, nbtClassLoader());
        return (Boolean) codec.getMethod("migrateLegacy", compoundTagClass(), UUID.class).invoke(null, metadata, BANK_ID);
    }

    private static Object snapshotNbt(Object metadata) throws Exception {
        Class<?> codec = Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec",
                true, nbtClassLoader());
        return codec.getMethod("snapshot", compoundTagClass()).invoke(null, metadata);
    }

    private static Object newCompoundTag() throws Exception {
        return compoundTagClass().getConstructor().newInstance();
    }

    private static Object newListTag() throws Exception {
        return listTagClass().getConstructor().newInstance();
    }

    private static Class<?> compoundTagClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.nbt.CompoundTag", true, nbtClassLoader());
    }

    private static Class<?> listTagClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.nbt.ListTag", true, nbtClassLoader());
    }

    private static Class<?> tagClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.nbt.Tag", true, nbtClassLoader());
    }

    private static Class<?> blockPosClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.core.BlockPos", true, nbtClassLoader());
    }

    private static Object blockPos(int x, int y, int z) throws Exception {
        return blockPosClass().getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
    }

    private static Class<?> resolverClass() throws ClassNotFoundException {
        return setupClass("SafeVaultReadinessResolver");
    }

    private static Class<?> loadedWorldFactsClass() throws ClassNotFoundException {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver$LoadedWorldFacts",
                true, nbtClassLoader());
    }

    private static Class<?> loadedRowSnapshotClass() throws ClassNotFoundException {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver$LoadedRowSnapshot",
                true, nbtClassLoader());
    }

    private static Class<?> moduleTypeClass() throws ClassNotFoundException {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity$ModuleType",
                true, nbtClassLoader());
    }

    private static Class<?> moduleTypeArrayClass() throws ClassNotFoundException {
        return java.lang.reflect.Array.newInstance(moduleTypeClass(), 0).getClass();
    }

    private static Class<?> setupClass(String simpleName) throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.setup." + simpleName, true,
                nbtClassLoader());
    }

    private static ClassLoader nbtClassLoader() {
        return NeoForgeTestClassLoader.get();
    }

    private static void putTag(Object tag, String key, Object value) throws Exception {
        compoundTagClass().getMethod("put", String.class, tagClass()).invoke(tag, key, value);
    }

    private static void putString(Object tag, String key, String value) throws Exception {
        compoundTagClass().getMethod("putString", String.class, String.class).invoke(tag, key, value);
    }

    private static void putByte(Object tag, String key, byte value) throws Exception {
        compoundTagClass().getMethod("putByte", String.class, byte.class).invoke(tag, key, value);
    }

    private static void putByteArray(Object tag, String key, byte[] value) throws Exception {
        compoundTagClass().getMethod("putByteArray", String.class, byte[].class).invoke(tag, key, value);
    }

    private static void putInt(Object tag, String key, int value) throws Exception {
        compoundTagClass().getMethod("putInt", String.class, int.class).invoke(tag, key, value);
    }

    private static void putIntArray(Object tag, String key, int[] value) throws Exception {
        compoundTagClass().getMethod("putIntArray", String.class, int[].class).invoke(tag, key, value);
    }

    private static void putLongArray(Object tag, String key, long[] value) throws Exception {
        compoundTagClass().getMethod("putLongArray", String.class, long[].class).invoke(tag, key, value);
    }

    private static void putFloat(Object tag, String key, float value) throws Exception {
        compoundTagClass().getMethod("putFloat", String.class, float.class).invoke(tag, key, value);
    }

    private static void putUuid(Object tag, String key, UUID value) throws Exception {
        compoundTagClass().getMethod("putUUID", String.class, UUID.class).invoke(tag, key, value);
    }

    private static void removeNbt(Object tag, String key) throws Exception {
        compoundTagClass().getMethod("remove", String.class).invoke(tag, key);
    }

    @SuppressWarnings("unchecked")
    private static void addToList(Object list, Object value) {
        ((List<Object>) list).add(value);
    }

    private static Object getNbt(Object tag, String key) throws Exception {
        return compoundTagClass().getMethod("get", String.class).invoke(tag, key);
    }

    private static Object copyNbt(Object tag) throws Exception {
        return tagClass().getMethod("copy").invoke(tag);
    }

    private static Object getList(Object tag, String key, int elementType) throws Exception {
        return compoundTagClass().getMethod("getList", String.class, int.class).invoke(tag, key, elementType);
    }

    private static Object getCompound(Object list, int index) throws Exception {
        return listTagClass().getMethod("getCompound", int.class).invoke(list, index);
    }

    private static int size(Object list) throws Exception {
        return (Integer) listTagClass().getMethod("size").invoke(list);
    }

    private static String getString(Object tag, String key) throws Exception {
        return String.valueOf(compoundTagClass().getMethod("getString", String.class).invoke(tag, key));
    }

    private static int getInt(Object tag, String key) throws Exception {
        return (Integer) compoundTagClass().getMethod("getInt", String.class).invoke(tag, key);
    }

    private static boolean contains(Object tag, String key, int elementType) throws Exception {
        return (Boolean) compoundTagClass().getMethod("contains", String.class, int.class).invoke(tag, key, elementType);
    }

    private static boolean hasUuid(Object tag, String key) throws Exception {
        return (Boolean) compoundTagClass().getMethod("hasUUID", String.class).invoke(tag, key);
    }

    private static UUID getUuid(Object tag, String key) throws Exception {
        return (UUID) compoundTagClass().getMethod("getUUID", String.class).invoke(tag, key);
    }

    private static Map<String, Object> customPremiseMap(String premiseId, Map<String, Object> bounds) {
        Map<String, Object> vault = map(
                "id", premiseId + "-vault",
                "safeAreaId", premiseId + "-safe-area",
                "dimension", string(bounds, "dimension"),
                "status", "READY",
                "vaultDoorX", intValue(bounds, "minX"),
                "vaultDoorY", intValue(bounds, "minY"),
                "vaultDoorZ", intValue(bounds, "minZ"),
                "doorIndex", 0,
                "routeHooks", new ArrayList<>(List.of(map("tellerId", "custom-teller", "bankBound", true,
                        "outboundRouteRef", "out", "returnRouteRef", "back")))
        );
        Map<String, Object> safeArea = map(
                "id", premiseId + "-safe-area",
                "premiseId", premiseId,
                "dimension", string(bounds, "dimension"),
                "minX", intValue(bounds, "minX"),
                "minY", intValue(bounds, "minY"),
                "minZ", intValue(bounds, "minZ"),
                "maxX", intValue(bounds, "maxX"),
                "maxY", intValue(bounds, "maxY"),
                "maxZ", intValue(bounds, "maxZ"),
                "vaults", new ArrayList<>(List.of(vault))
        );
        return map(
                "id", premiseId,
                "bankId", BANK_ID.toString(),
                "dimension", string(bounds, "dimension"),
                "minX", intValue(bounds, "minX"),
                "minY", intValue(bounds, "minY"),
                "minZ", intValue(bounds, "minZ"),
                "maxX", intValue(bounds, "maxX"),
                "maxY", intValue(bounds, "maxY"),
                "maxZ", intValue(bounds, "maxZ"),
                "exitX", intValue(bounds, "minX") - 1,
                "exitY", intValue(bounds, "minY"),
                "exitZ", intValue(bounds, "minZ"),
                "exitYaw", 90.0F,
                "mode", "STAFF_ONLY",
                "safeAreas", new ArrayList<>(List.of(safeArea))
        );
    }

    private static Map<String, Object> generatedNestedSafeAreaMap(String premiseId,
                                                                  Map<String, Object> bounds) {
        String safeAreaId = generatedSafeAreaId(premiseId, bounds);
        Map<String, Object> vault = map(
                "id", generatedVaultId(safeAreaId),
                "safeAreaId", safeAreaId,
                "dimension", normalizedDimension(string(bounds, "dimension")),
                "status", "SETUP_PENDING",
                "routeStatus", "UNWIRED",
                "routeHooks", new ArrayList<Map<String, Object>>()
        );
        return map(
                "id", safeAreaId,
                "premiseId", premiseId,
                "dimension", normalizedDimension(string(bounds, "dimension")),
                "minX", intValue(bounds, "minX"),
                "minY", intValue(bounds, "minY"),
                "minZ", intValue(bounds, "minZ"),
                "maxX", intValue(bounds, "maxX"),
                "maxY", intValue(bounds, "maxY"),
                "maxZ", intValue(bounds, "maxZ"),
                "vaults", new ArrayList<>(List.of(vault))
        );
    }

    private static void configureNestedVaultForPreservation(Map<String, Object> safeArea) {
        Map<String, Object> vault = singleVault(safeArea);
        vault.put("status", "READY");
        vault.put("routeStatus", "WIRED");
        vault.put("vaultDoorX", intValue(safeArea, "minX"));
        vault.put("vaultDoorY", intValue(safeArea, "minY"));
        vault.put("vaultDoorZ", intValue(safeArea, "minZ"));
        vault.put("doorIndex", 3);
        vault.put("routeHooks", new ArrayList<>(List.of(map(
                "tellerId", "configured-sibling-teller",
                "bankBound", true,
                "outboundRouteRef", "configured-sibling-outbound",
                "returnRouteRef", "configured-sibling-return"
        ))));
    }

    private static void assertGeneratedNestedSafeArea(Map<String, Object> safeArea,
                                                      String premiseId,
                                                      Map<String, Object> bounds) {
        assertNotEquals(null, safeArea, "expected generated nested safe area for " + bounds);
        String safeAreaId = generatedSafeAreaId(premiseId, bounds);
        assertEquals(safeAreaId, string(safeArea, "id"));
        assertEquals(premiseId, string(safeArea, "premiseId"));
        assertMapBounds(safeArea, bounds);
        Map<String, Object> vault = singleVault(safeArea);
        assertEquals(generatedVaultId(safeAreaId), string(vault, "id"));
        assertEquals(safeAreaId, string(vault, "safeAreaId"));
        assertEquals("SETUP_PENDING", string(vault, "status"));
        assertEquals("UNWIRED", string(vault, "routeStatus"));
        assertTrue(list(vault, "routeHooks").isEmpty());
        assertFalse(vault.containsKey("vaultDoorX"));
    }

    private static Map<String, Object> premiseConfiguration(Map<String, Object> premise) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        for (String key : List.of(
                "id", "bankId", "dimension",
                "minX", "minY", "minZ", "maxX", "maxY", "maxZ",
                "exitX", "exitY", "exitZ", "exitYaw", "mode")) {
            configuration.put(key, premise.get(key));
        }
        return configuration;
    }

    private static void assertMapBounds(Map<String, Object> actual, Map<String, Object> expected) {
        assertEquals(normalizedDimension(string(expected, "dimension")),
                normalizedDimension(string(actual, "dimension")));
        for (String key : List.of("minX", "minY", "minZ", "maxX", "maxY", "maxZ")) {
            assertEquals(intValue(expected, key), intValue(actual, key), "unexpected " + key);
        }
    }

    private static Map<String, Object> safeAreaById(List<Map<String, Object>> safeAreas, String id) {
        for (Map<String, Object> safeArea : safeAreas) {
            if (id.equals(string(safeArea, "id"))) {
                return safeArea;
            }
        }
        return null;
    }

    private static String generatedSafeAreaId(String premiseId, Map<String, Object> bounds) {
        return stableId("safe-area", BANK_ID, premiseId, normalizedDimension(string(bounds, "dimension")),
                intValue(bounds, "minX"), intValue(bounds, "minY"), intValue(bounds, "minZ"),
                intValue(bounds, "maxX"), intValue(bounds, "maxY"), intValue(bounds, "maxZ"));
    }

    private static String generatedVaultId(String safeAreaId) {
        return stableId("vault", BANK_ID, safeAreaId);
    }

    private static void configurePremiseForPreservation(Map<String, Object> premise) {
        premise.put("mode", "STAFF_ONLY");
        premise.put("exitYaw", 270.0F);
        Map<String, Object> vault = singleVault(singleSafeArea(premise));
        vault.put("status", "READY");
        vault.put("vaultDoorX", intValue(premise, "minX"));
        vault.put("vaultDoorY", intValue(premise, "minY"));
        vault.put("vaultDoorZ", intValue(premise, "minZ"));
        vault.put("doorIndex", 7);
        vault.put("routeHooks", new ArrayList<>(List.of(map("tellerId", "preserved-teller", "bankBound", true,
                "outboundRouteRef", "route-out", "returnRouteRef", "route-back"))));
    }

    private static Map<String, Object> premiseById(List<Map<String, Object>> premises, String id) {
        for (Map<String, Object> premise : premises) {
            if (id.equals(string(premise, "id"))) {
                return premise;
            }
        }
        return null;
    }

    private static void assertMigrationOwnedPendingPremise(List<Map<String, Object>> premises, Map<String, Object> area) {
        Map<String, Object> premise = premiseById(premises, migrationPremiseId(area));
        assertNotEquals(null, premise, "expected migration-owned premise for " + area);
        Map<String, Object> vault = singleVault(singleSafeArea(premise));
        assertEquals("SETUP_PENDING", string(vault, "status"));
        assertFalse(vault.containsKey("vaultDoorX"));
    }

    private static String migrationPremiseId(Map<String, Object> area) {
        return stableId("premise", BANK_ID, normalizedDimension(string(area, "dimension")),
                intValue(area, "minX"), intValue(area, "minY"), intValue(area, "minZ"),
                intValue(area, "maxX"), intValue(area, "maxY"), intValue(area, "maxZ"));
    }

    private static String stableId(Object... values) {
        StringBuilder builder = new StringBuilder("ubs-safe-deposit-setup-v1");
        for (Object value : values) {
            builder.append('|').append(value == null ? "" : value);
        }
        return UUID.nameUUIDFromBytes(builder.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normalizedDimension(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Object areaNbt(String dimension,
                                  int minX,
                                  int minY,
                                  int minZ,
                                  int maxX,
                                  int maxY,
                                  int maxZ) throws Exception {
        Object area = newCompoundTag();
        putBounds(area, dimension, minX, minY, minZ, maxX, maxY, maxZ);
        return area;
    }

    private static Object customPremiseNbt(String premiseId,
                                           String dimension,
                                           int minX,
                                           int minY,
                                           int minZ,
                                           int maxX,
                                           int maxY,
                                           int maxZ) throws Exception {
        Object premise = newCompoundTag();
        putString(premise, "id", premiseId);
        putString(premise, "bankId", BANK_ID.toString());
        putBounds(premise, dimension, minX, minY, minZ, maxX, maxY, maxZ);
        putInt(premise, "exitX", minX - 1);
        putInt(premise, "exitY", minY);
        putInt(premise, "exitZ", minZ);
        putFloat(premise, "exitYaw", 90.0F);
        putString(premise, "mode", "STAFF_ONLY");

        Object safeAreas = newListTag();
        Object safeArea = newCompoundTag();
        putString(safeArea, "id", premiseId + "-safe-area");
        putString(safeArea, "premiseId", premiseId);
        putBounds(safeArea, dimension, minX, minY, minZ, maxX, maxY, maxZ);

        Object vaults = newListTag();
        Object vault = newCompoundTag();
        putString(vault, "id", premiseId + "-vault");
        putString(vault, "safeAreaId", premiseId + "-safe-area");
        putString(vault, "dimension", dimension);
        putString(vault, "status", "READY");
        putInt(vault, "vaultDoorX", minX);
        putInt(vault, "vaultDoorY", minY);
        putInt(vault, "vaultDoorZ", minZ);
        putInt(vault, "doorIndex", 0);
        putTag(vault, "routeHooks", newListTag());
        addToList(vaults, vault);
        putTag(safeArea, "vaults", vaults);
        addToList(safeAreas, safeArea);
        putTag(premise, "safeAreas", safeAreas);
        return premise;
    }

    private static Object nbtPremiseById(Object premises, String id) throws Exception {
        for (int i = 0; i < size(premises); i++) {
            Object premise = getCompound(premises, i);
            if (id.equals(getString(premise, "id"))) {
                return premise;
            }
        }
        return null;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> map, String key) {
        return (List<Map<String, Object>>) map.getOrDefault(key, List.of());
    }

    private static Map<String, Object> first(List<Map<String, Object>> list) {
        return list.get(0);
    }

    private static Map<String, Object> singleSafeArea(Map<String, Object> premise) {
        List<Map<String, Object>> safeAreas = list(premise, "safeAreas");
        assertEquals(1, safeAreas.size(), "safe area should be nested in exactly one premise");
        return safeAreas.get(0);
    }

    private static Map<String, Object> singleVault(Map<String, Object> safeArea) {
        List<Map<String, Object>> vaults = list(safeArea, "vaults");
        assertEquals(1, vaults.size(), "each migrated safe area should own exactly one setup-pending placeholder vault");
        return vaults.get(0);
    }

    private static String string(Map<String, Object> map, String key) {
        return String.valueOf(map.getOrDefault(key, ""));
    }

    private static int intValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static float floatValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.floatValue() : Float.parseFloat(String.valueOf(value));
    }

    private static boolean pointInside(Map<String, Object> bounds, int x, int y, int z) {
        return x >= intValue(bounds, "minX") && x <= intValue(bounds, "maxX")
                && y >= intValue(bounds, "minY") && y <= intValue(bounds, "maxY")
                && z >= intValue(bounds, "minZ") && z <= intValue(bounds, "maxZ");
    }
}
