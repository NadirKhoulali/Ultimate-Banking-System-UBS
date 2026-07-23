package net.austizz.ultimatebankingsystem.bank.safebox;

// SIZE_OK: inherited 1144-line server-gate matrix; +10 typed-request lines share one child-first fixture.

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"rawtypes", "unchecked"})
class SafeAreaServerGateTest {
    private static final UUID CENTRAL_BANK_ID = new UUID(0L, 0L);
    private static final UUID SECOND_BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_BANK_OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID BANK_A_MANAGER_ID = UUID.fromString("20000000-0000-0000-0000-000000000010");
    private static final int TAG_COMPOUND = 10;
    private static final int ROW_X = 3;
    private static final int ROW_Y = 64;
    private static final int ROW_Z = 3;

    @Test
    void fullRowInvariantRequiresEveryPhysicalUnitToBeCoveredByAssignableSpans() throws Exception {
        assertTrue(isFullyAssignableRow(row("MEDIUM", "EMPTY", "MEDIUM", "EMPTY")));
        assertTrue(isFullyAssignableRow(row("LARGE", "EMPTY", "EMPTY", "SMALL")));

        assertFalse(isFullyAssignableRow(row("SMALL", "SMALL", "SMALL", "COVER")),
                "cover plates must not make a row fully assignable");
        assertFalse(isFullyAssignableRow(row("COVER", "COVER", "COVER", "COVER")),
                "a cover-only row is not assignable capacity");
        assertFalse(isFullyAssignableRow(row("MEDIUM", "EMPTY", "COVER", "EMPTY")),
                "empty and cover units leave the row unavailable even when another span is valid");
    }

    @Test
    void coverInstallRequiresAnotherFullyAssignableLoadedRowInTheSameVault() throws Exception {
        Object onlyRow = validateCoverInstallationForVaultRows(
                rows(row("EMPTY", "EMPTY", "EMPTY", "EMPTY")),
                0
        );
        assertFalse(success(onlyRow));
        assertTrue(message(onlyRow).contains("only loaded row"));

        Object noAssignableRow = validateCoverInstallationForVaultRows(
                rows(row("EMPTY", "EMPTY", "EMPTY", "EMPTY"), row("SMALL", "COVER", "EMPTY", "EMPTY")),
                0
        );
        assertFalse(success(noAssignableRow));
        assertTrue(message(noAssignableRow).contains("another fully assignable row"));

        Object protectedByOtherRow = validateCoverInstallationForVaultRows(
                rows(row("EMPTY", "EMPTY", "EMPTY", "EMPTY"), row("MEDIUM", "EMPTY", "MEDIUM", "EMPTY")),
                0
        );
        assertTrue(success(protectedByOtherRow), message(protectedByOtherRow));
    }

    @Test
    void typedReadinessResolverMapsRowToOneVaultAndDerivesHumanReasonsFromLoadedFacts() throws Exception {
        Object snapshot = snapshot(List.of(vault("vault-1", readyRoute())));
        Object readyFacts = loadedWorldFacts(
                Map.of("vault-1", true),
                Map.of("vault-1", blockPos(2, 63, 0)),
                List.of(loadedRow("minecraft:overworld", rowPos(), row("MEDIUM", "EMPTY", "MEDIUM", "EMPTY")))
        );

        Object ready = resolveForRow(
                snapshot,
                rowPos(),
                readyFacts
        );

        assertTrue((Boolean) value(ready, "mapped"));
        assertEquals("premise-1", value(value(ready, "premise"), "id"));
        assertEquals("safe-area-1", value(value(ready, "safeArea"), "id"));
        assertEquals("vault-1", value(value(ready, "vault"), "id"));
        assertTrue((Boolean) value(value(ready, "summary"), "ready"), value(ready, "humanMissingReasons").toString());
        assertEquals(blockPos(2, 63, 0), resolvedDoorPos(readyFacts, "vault-1").orElseThrow());

        Object missingDoor = resolveForRow(
                snapshot,
                rowPos(),
                loadedWorldFacts(
                        Map.of("vault-1", false),
                        loadedRows(readyFacts)
                )
        );

        assertFalse((Boolean) value(value(missingDoor, "summary"), "ready"));
        assertEquals(List.of(missingReason("VAULT_DOOR_MISSING")),
                value(value(missingDoor, "summary"), "missingReasons"));
        assertTrue(firstString(value(missingDoor, "humanMissingReasons")).contains("BANK_VAULT_DOOR"));
    }

    @Test
    void notReadyVaultsBlockCustomerAndRentalPathsButNotManagementAccess() throws Exception {
        Object notReady = safeVaultReadinessSummary(
                "vault-1",
                false,
                List.of(missingReason("VAULT_DOOR_MISSING"))
        );

        assertTrue(shouldSkipVaultForAssignment(newCompoundTag(), rowReadiness(notReady)),
                "rental assignment must skip NOT_READY vaults");

        Object customerGate = validateVaultAccessForReadiness(
                false,
                notReady
        );
        assertFalse(success(customerGate));
        assertTrue(message(customerGate).contains("temporarily unavailable"));

        Object managementGate = validateVaultAccessForReadiness(
                true,
                notReady
        );
        assertTrue(success(managementGate), "management access must remain possible for recovery");
    }

    @Test
    void staffOnlyPremisePolicyDeniesCustomersButAllowsManagersSafeAccessAndEscorts() throws Exception {
        Object publicCustomer = decidePremiseAccess("PUBLIC", true, false, false, false);
        assertTrue((Boolean) value(publicCustomer, "allowed"));
        assertTrue((Boolean) value(publicCustomer, "normalCustomerAccess"));

        Object staffOnlyCustomer = decidePremiseAccess("STAFF_ONLY", true, false, false, false);
        assertFalse((Boolean) value(staffOnlyCustomer, "allowed"),
                "ordinary eligible customers must not enter staff-only premises");
        assertTrue(((String) value(staffOnlyCustomer, "denialMessage")).contains("staff-only"));

        Object staffOnlyManager = decidePremiseAccess("STAFF_ONLY", false, true, false, false);
        assertTrue((Boolean) value(staffOnlyManager, "allowed"));
        assertTrue((Boolean) value(staffOnlyManager, "legacyManagementAccess"));

        Object staffOnlySafeAccess = decidePremiseAccess("STAFF_ONLY", false, false, true, false);
        assertTrue((Boolean) value(staffOnlySafeAccess, "allowed"));
        assertTrue((Boolean) value(staffOnlySafeAccess, "explicitSafeAccess"));
        assertFalse((Boolean) value(staffOnlySafeAccess, "legacyManagementAccess"),
                "Safe Access must not become owner/director management authorization");

        Object escortedCustomer = decidePremiseAccess("STAFF_ONLY", true, false, false, true);
        assertTrue((Boolean) value(escortedCustomer, "allowed"),
                "reserved escorted-session flag must be able to admit one otherwise eligible customer later");
        assertTrue((Boolean) value(escortedCustomer, "escortedSessionAccess"));
    }

    @Test
    void explicitSafeAccessDoesNotBypassNotReadyVaultRecoveryGate() throws Exception {
        Object safeAccessDecision = decidePremiseAccess("STAFF_ONLY", false, false, true, false);
        assertTrue((Boolean) value(safeAccessDecision, "allowed"));
        assertFalse((Boolean) value(safeAccessDecision, "legacyManagementAccess"));

        Object notReady = safeVaultReadinessSummary(
                "vault-1",
                false,
                List.of(missingReason("VAULT_DOOR_MISSING"))
        );
        Object readinessGate = validateVaultAccessForReadiness(
                (Boolean) value(safeAccessDecision, "legacyManagementAccess"),
                notReady
        );

        assertFalse(success(readinessGate),
                "explicit Safe Access must not count as management readiness recovery access");
        assertTrue(message(readinessGate).contains("temporarily unavailable"));
    }

    @Test
    void safeManagementDenialLabelNamesEveryLegacyManagerClass() throws Exception {
        String label = (String) serviceClass()
                .getMethod("safeAreaManagerLabel")
                .invoke(null);

        assertTrue(label.contains("bank owners"), label);
        assertTrue(label.contains("founders/directors"), label);
        assertTrue(label.contains("director employees"), label);
        assertTrue(label.contains("cofounders"), label);
        assertTrue(label.contains("shareholders"), label);
        assertTrue(label.contains("server operators"), label);
    }

    @Test
    void resolverIgnoresMalformedSafeAreasSoAHealthySingleVaultRowIsNotAmbiguous() throws Exception {
        Object healthySafeArea = safeAreaSnapshot(
                "safe-area-healthy",
                "premise-1",
                bounds(1, 63, 1, 5, 66, 5),
                List.of(vaultInArea("vault-healthy", "safe-area-healthy", readyRoute()))
        );
        Object zeroVaultSafeArea = safeAreaSnapshot(
                "safe-area-zero-vault",
                "premise-1",
                bounds(1, 63, 1, 5, 66, 5),
                List.of()
        );
        Object multiVaultSafeArea = safeAreaSnapshot(
                "safe-area-multi-vault",
                "premise-1",
                bounds(1, 63, 1, 5, 66, 5),
                List.of(
                        vaultInArea("vault-extra-1", "safe-area-multi-vault", readyRoute()),
                        vaultInArea("vault-extra-2", "safe-area-multi-vault", readyRoute())
                )
        );
        Object premise = safePremiseSnapshot(
                "premise-1",
                "10000000-0000-0000-0000-000000000001",
                bounds(0, 62, 0, 6, 67, 6),
                safeExitSnapshot("minecraft:overworld", 0, 63, -1, 180.0F),
                safePremiseMode("PUBLIC"),
                List.of(zeroVaultSafeArea, multiVaultSafeArea, healthySafeArea)
        );
        Object snapshot = safeDepositSetupSnapshot(1, List.of(premise));
        Object facts = loadedWorldFacts(
                Map.of("vault-healthy", true, "vault-extra-1", true, "vault-extra-2", true),
                Map.of("vault-healthy", blockPos(2, 63, 0)),
                List.of(loadedRow("minecraft:overworld", rowPos(), row("MEDIUM", "EMPTY", "MEDIUM", "EMPTY")))
        );

        Object readiness = resolveForRow(snapshot, rowPos(), facts);

        assertTrue((Boolean) value(readiness, "mapped"),
                "malformed zero-vault and multi-vault safe areas must not make a healthy one-vault safe area ambiguous");
        assertEquals("safe-area-healthy", value(value(readiness, "safeArea"), "id"));
        assertEquals("vault-healthy", value(value(readiness, "vault"), "id"));
        assertTrue((Boolean) value(value(readiness, "summary"), "ready"), value(readiness, "humanMissingReasons").toString());
    }

    @Test
    void resolverRequiresExactlyOneVaultWithinTheMatchedSafeArea() throws Exception {
        Object snapshot = snapshot(List.of(vault("vault-1", readyRoute()), vault("vault-2", readyRoute())));
        Object facts = loadedWorldFacts(
                Map.of("vault-1", true, "vault-2", true),
                Map.of("vault-1", blockPos(2, 63, 0), "vault-2", blockPos(2, 63, 0)),
                List.of(loadedRow("minecraft:overworld", rowPos(), row("MEDIUM", "EMPTY", "MEDIUM", "EMPTY")))
        );

        Object readiness = resolveForRow(snapshot, rowPos(), facts);

        assertFalse((Boolean) value(readiness, "mapped"),
                "a safe area with multiple vault records is malformed and must not map a row");
        assertTrue(value(readiness, "humanMissingReasons").toString().contains("exactly one vault"),
                value(readiness, "humanMissingReasons").toString());
    }

    @Test
    void resolvedDoorFactCanBecomeReadyNotReadyAndReadyAgainWithoutPersistedDoorNbt() throws Exception {
        Object snapshot = snapshot(List.of(vaultWithoutPersistedDoor("vault-1", readyRoute())));
        List<Object> loadedRows = List.of(loadedRow("minecraft:overworld", rowPos(),
                row("MEDIUM", "EMPTY", "MEDIUM", "EMPTY")));

        Object discovered = loadedWorldFacts(
                Map.of("vault-1", true),
                Map.of("vault-1", blockPos(2, 63, 0)),
                loadedRows
        );
        Object ready = resolveForRow(snapshot, rowPos(), discovered);
        assertTrue((Boolean) value(value(ready, "summary"), "ready"), value(ready, "humanMissingReasons").toString());
        assertEquals(blockPos(2, 63, 0), resolvedDoorPos(discovered, "vault-1").orElseThrow());

        Object removed = loadedWorldFacts(
                Map.of("vault-1", false),
                Map.of(),
                loadedRows
        );
        Object notReady = resolveForRow(snapshot, rowPos(), removed);
        assertFalse((Boolean) value(value(notReady, "summary"), "ready"),
                "removing every complete in-bounds vault door must make the vault NOT_READY");
        assertTrue(value(value(notReady, "summary"), "missingReasons").toString().contains("VAULT_DOOR_MISSING"));
        assertTrue(resolvedDoorPos(removed, "vault-1").isEmpty());

        Object replacement = loadedWorldFacts(
                Map.of("vault-1", true),
                Map.of("vault-1", blockPos(4, 63, 2)),
                loadedRows
        );
        Object readyAgain = resolveForRow(snapshot, rowPos(), replacement);
        assertTrue((Boolean) value(value(readyAgain, "summary"), "ready"),
                "placing a valid replacement complete vault door must make the vault READY again");
        assertEquals(blockPos(4, 63, 2), resolvedDoorPos(replacement, "vault-1").orElseThrow());
    }

    @Test
    void directTwoBankSafeAreaRegressionRejectsCrossBankOverlapAndForeignManagerAccess() throws Exception {
        Object centralBank = newCentralBank();
        Object secondBank = newBank(SECOND_BANK_ID, "Second Bank", SECOND_BANK_OWNER_ID);
        addBank(centralBank, secondBank);
        Object centralMetadata = bankMetadata(centralBank, CENTRAL_BANK_ID);
        putString(centralMetadata, "roles", BANK_A_MANAGER_ID + "=DIRECTOR");
        seedCurrentPremises(centralMetadata, emptyCustomPremise(
                "custom-premise-overlap-primary", CENTRAL_BANK_ID,
                "minecraft:overworld", 0, 60, 0, 20, 80, 20));
        seedCurrentPremises(bankMetadata(centralBank, SECOND_BANK_ID), emptyCustomPremise(
                "custom-premise-overlap-second", SECOND_BANK_ID,
                "minecraft:overworld", 5, 60, 5, 20, 80, 20));

        Object first = claimSafeArea(centralBank, CENTRAL_BANK_ID, "minecraft:overworld",
                blockPos(10, 64, 10), blockPos(12, 66, 12));
        assertTrue(success(first), message(first));
        assertTrue(canManageSafeArea(centralBank, BANK_A_MANAGER_ID, CENTRAL_BANK_ID));
        assertFalse(canManageSafeArea(centralBank, BANK_A_MANAGER_ID, SECOND_BANK_ID),
                "a manager of only bank A must not receive bank B mutation authority");

        Object crossBankOverlap = claimSafeArea(centralBank, SECOND_BANK_ID, "minecraft:overworld",
                blockPos(11, 64, 11), blockPos(13, 66, 13));
        assertFalse(success(crossBankOverlap), "cross-bank second claim must fail instead of creating ambiguity");
        assertTrue(message(crossBankOverlap).contains("overlaps"), message(crossBankOverlap));
    }

    @Test
    void existingAmbiguousSafeAreaLookupFailsClosedAndForeignOnlyVolumeKeepsBankScopedAuthorization() throws Exception {
        Object centralBank = newCentralBank();
        addBank(centralBank, newBank(SECOND_BANK_ID, "Second Bank", SECOND_BANK_OWNER_ID));
        putString(bankMetadata(centralBank, CENTRAL_BANK_ID), "roles", BANK_A_MANAGER_ID + "=DIRECTOR");
        seedSafeArea(bankMetadata(centralBank, CENTRAL_BANK_ID), "minecraft:overworld", 10, 64, 10, 12, 66, 12);
        seedSafeArea(bankMetadata(centralBank, SECOND_BANK_ID), "minecraft:overworld", 11, 64, 11, 13, 66, 13);

        Object level = dimensionOnlyLevel("minecraft:overworld");
        Object ambiguousPos = blockPos(11, 65, 11);
        Object ambiguousBank = findBankIdForSafeArea(centralBank, level, blockPos(11, 65, 11));
        assertEquals(null, ambiguousBank, "pre-existing ambiguous overlap must fail closed instead of first-map-match");
        assertTrue(isInsideSafeArea(centralBank, level, ambiguousPos),
                "ambiguous claimed positions must still count as protected safe-area volume");
        Object ambiguousPlacement = validateSafeRowPlacement(null, centralBank, null, level, ambiguousPos);
        assertFalse(success(ambiguousPlacement),
                "ambiguous safe-area volume must deny row placement instead of behaving as unclaimed");
        assertTrue(message(ambiguousPlacement).contains("multiple bank safe areas"), message(ambiguousPlacement));
        Object ambiguousCover = validateCoverInstallation(null, centralBank, level, ambiguousPos);
        assertFalse(success(ambiguousCover),
                "ambiguous safe-area volume must deny cover installation instead of behaving as unclaimed");
        assertTrue(message(ambiguousCover).contains("multiple bank safe areas"), message(ambiguousCover));
        assertFalse(BankSafeAreaMutationAuthorization.mayModifyAll(
                List.of(ambiguousPos),
                ignored -> List.of(CENTRAL_BANK_ID, SECOND_BANK_ID),
                bankId -> canManageSafeAreaUnchecked(centralBank, BANK_A_MANAGER_ID, bankId)
        ), "a manager of only bank A must not mutate ambiguous volume claimed by bank B too");
        Object ambiguousInsert = decideInsertInstallation(true, false);
        assertFalse((Boolean) value(ambiguousInsert, "allowed"),
                "ambiguous safe-area volume must deny insert installation without unique-bank management authority");
        assertTrue(BankSafeAreaMutationAuthorization.mayModifyAll(
                List.of(blockPos(30, 65, 30)),
                ignored -> List.of(),
                bankId -> false
        ), "truly unclaimed protected mutation behavior must remain open");

        Object secondOnlyBank = findBankIdForSafeArea(centralBank, level, blockPos(13, 65, 13));
        assertEquals(SECOND_BANK_ID, secondOnlyBank);
        assertFalse(canManageSafeArea(centralBank, BANK_A_MANAGER_ID, (UUID) secondOnlyBank),
                "a manager of only bank A must not mutate/install in bank B-only volume");
    }

    private static Object snapshot(List<Object> vaults) throws Exception {
        Object safeArea = safeAreaSnapshot(
                "safe-area-1",
                "premise-1",
                bounds(1, 63, 1, 5, 66, 5),
                vaults
        );
        Object premise = safePremiseSnapshot(
                "premise-1",
                "10000000-0000-0000-0000-000000000001",
                bounds(0, 62, 0, 6, 67, 6),
                safeExitSnapshot("minecraft:overworld", 0, 63, -1, 180.0F),
                safePremiseMode("PUBLIC"),
                List.of(safeArea)
        );
        return safeDepositSetupSnapshot(1, List.of(premise));
    }

    private static Object vault(String id, Object routeHook) throws Exception {
        return vaultInArea(id, "safe-area-1", routeHook);
    }

    private static Object vaultInArea(String id, String safeAreaId, Object routeHook) throws Exception {
        return safeVaultSnapshot(
                id,
                safeAreaId,
                "minecraft:overworld",
                safeVaultSetupStatus("READY"),
                OptionalInt.of(2),
                OptionalInt.of(63),
                OptionalInt.of(0),
                OptionalInt.empty(),
                List.of(routeHook)
        );
    }

    private static Object vaultWithoutPersistedDoor(String id, Object routeHook) throws Exception {
        return safeVaultSnapshot(
                id,
                "safe-area-1",
                "minecraft:overworld",
                safeVaultSetupStatus("READY"),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                List.of(routeHook)
        );
    }

    private static Object readyRoute() throws Exception {
        return safeTellerRouteHook("teller-1", true, "outbound-route", "return-route");
    }

    private static Object bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) throws Exception {
        return safeBlockBounds("minecraft:overworld", minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Object rowPos() throws Exception {
        return blockPos(ROW_X, ROW_Y, ROW_Z);
    }

    private static Object blockPos(int x, int y, int z) throws Exception {
        return blockPosClass().getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
    }

    private static Object newCentralBank() throws Exception {
        seedConfigValue("DEFAULT_FEDERAL_FUNDS_RATE", 3.5D);
        seedConfigValue("BANK_MIN_RESERVE_RATIO", 0.10D);
        return centralBankClass().getConstructor().newInstance();
    }

    private static Object newBank(UUID bankId, String name, UUID ownerId) throws Exception {
        return bankClass()
                .getConstructor(UUID.class, String.class, BigDecimal.class, double.class, UUID.class)
                .newInstance(bankId, name, BigDecimal.ZERO, 1.0D, ownerId);
    }

    private static void seedConfigValue(String fieldName, Object value) throws Exception {
        Object configValue = configClass().getField(fieldName).get(null);
        var cachedValue = configValue.getClass().getSuperclass().getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        cachedValue.set(configValue, value);
    }

    private static void addBank(Object centralBank, Object bank) throws Exception {
        centralBankClass()
                .getMethod("addBank", bankClass())
                .invoke(centralBank, bank);
    }

    private static Object bankMetadata(Object centralBank, UUID bankId) throws Exception {
        return centralBankClass()
                .getMethod("getOrCreateBankMetadata", UUID.class)
                .invoke(centralBank, bankId);
    }

    private static void seedCurrentPremises(Object metadata, Object... premises) throws Exception {
        putInt(metadata, "safeDepositSetupVersion", 1);
        putTag(metadata, "safeDepositAreas", newListTag());
        Object premiseList = newListTag();
        for (Object premise : premises) {
            addToList(premiseList, premise);
        }
        putTag(metadata, "safeDepositPremises", premiseList);
    }

    private static Object emptyCustomPremise(String premiseId,
                                             UUID bankId,
                                             String dimension,
                                             int minX,
                                             int minY,
                                             int minZ,
                                             int maxX,
                                             int maxY,
                                             int maxZ) throws Exception {
        Object premise = newCompoundTag();
        putString(premise, "id", premiseId);
        putString(premise, "bankId", bankId.toString());
        putBounds(premise, dimension, minX, minY, minZ, maxX, maxY, maxZ);
        putInt(premise, "exitX", minX - 1);
        putInt(premise, "exitY", minY);
        putInt(premise, "exitZ", minZ);
        putFloat(premise, "exitYaw", 135.0F);
        putString(premise, "mode", "STAFF_ONLY");
        putTag(premise, "safeAreas", newListTag());
        return premise;
    }

    private static Object claimSafeArea(Object centralBank, UUID bankId, String dimension, Object min, Object max)
            throws Exception {
        return serviceClass()
                .getMethod("claimSafeArea", centralBankClass(), serverPlayerClass(), UUID.class, String.class,
                        blockPosClass(), blockPosClass())
                .invoke(null, centralBank, null, bankId, dimension, min, max);
    }

    private static boolean canManageSafeArea(Object centralBank, UUID playerId, UUID bankId) throws Exception {
        return (Boolean) serviceClass()
                .getMethod("canManageSafeArea", centralBankClass(), UUID.class, UUID.class)
                .invoke(null, centralBank, playerId, bankId);
    }

    private static Object findBankIdForSafeArea(Object centralBank, Object level, Object pos) throws Exception {
        return serviceClass()
                .getMethod("findBankIdForSafeArea", centralBankClass(), levelClass(), blockPosClass())
                .invoke(null, centralBank, level, pos);
    }

    private static boolean isInsideSafeArea(Object centralBank, Object level, Object pos) throws Exception {
        return (Boolean) serviceClass()
                .getMethod("isInsideSafeArea", centralBankClass(), levelClass(), blockPosClass())
                .invoke(null, centralBank, level, pos);
    }

    private static Object validateSafeRowPlacement(Object server, Object centralBank, Object player, Object level,
                                                   Object pos) throws Exception {
        return serviceClass()
                .getMethod("validateSafeRowPlacement", minecraftServerClass(), centralBankClass(), serverPlayerClass(),
                        levelClass(), blockPosClass())
                .invoke(null, server, centralBank, player, level, pos);
    }

    private static Object validateCoverInstallation(Object server, Object centralBank, Object level, Object pos)
            throws Exception {
        return serviceClass()
                .getMethod("validateCoverInstallation", minecraftServerClass(), centralBankClass(), levelClass(),
                        blockPosClass())
                .invoke(null, server, centralBank, level, pos);
    }

    private static boolean canManageSafeAreaUnchecked(Object centralBank, UUID playerId, UUID bankId) {
        try {
            return canManageSafeArea(centralBank, playerId, bankId);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void seedSafeArea(Object metadata,
                                     String dimension,
                                     int minX,
                                     int minY,
                                     int minZ,
                                     int maxX,
                                     int maxY,
                                     int maxZ) throws Exception {
        Object areas = getList(metadata, "safeDepositAreas", TAG_COMPOUND);
        Object area = newCompoundTag();
        putString(area, "dimension", dimension);
        putInt(area, "minX", minX);
        putInt(area, "minY", minY);
        putInt(area, "minZ", minZ);
        putInt(area, "maxX", maxX);
        putInt(area, "maxY", maxY);
        putInt(area, "maxZ", maxZ);
        addToList(areas, area);
        putTag(metadata, "safeDepositAreas", areas);
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

    private static void putString(Object tag, String key, String value) throws Exception {
        compoundTagClass().getMethod("putString", String.class, String.class).invoke(tag, key, value);
    }

    private static void putInt(Object tag, String key, int value) throws Exception {
        compoundTagClass().getMethod("putInt", String.class, int.class).invoke(tag, key, value);
    }

    private static void putBoolean(Object tag, String key, boolean value) throws Exception {
        compoundTagClass().getMethod("putBoolean", String.class, boolean.class).invoke(tag, key, value);
    }

    private static void putFloat(Object tag, String key, float value) throws Exception {
        compoundTagClass().getMethod("putFloat", String.class, float.class).invoke(tag, key, value);
    }

    private static void putTag(Object tag, String key, Object value) throws Exception {
        compoundTagClass().getMethod("put", String.class, tagClass()).invoke(tag, key, value);
    }

    private static Object newCompoundTag() throws Exception {
        return compoundTagClass().getConstructor().newInstance();
    }

    private static Object newListTag() throws Exception {
        return listTagClass().getConstructor().newInstance();
    }

    private static Object getList(Object tag, String key, int elementType) throws Exception {
        return compoundTagClass().getMethod("getList", String.class, int.class).invoke(tag, key, elementType);
    }

    @SuppressWarnings("unchecked")
    private static void addToList(Object list, Object value) {
        ((List<Object>) list).add(value);
    }

    private static Object dimensionOnlyLevel(String dimension) throws Exception {
        Path sourceDir = IsolatedServerClassPath.buildPath("tmp", "dimensionOnlyLevelSource");
        Path outputDir = IsolatedServerClassPath.buildPath("tmp", "dimensionOnlyLevelClasses");
        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);
        Path source = sourceDir.resolve("DimensionOnlyLevel.java");
        String code = """
                package net.austizz.ultimatebankingsystem.bank.safebox.probe;

                import java.lang.reflect.Field;
                import net.minecraft.core.Direction;
                import net.minecraft.core.particles.ParticleOptions;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.core.BlockPos;
                import net.minecraft.core.Holder;
                import net.minecraft.core.RegistryAccess;
                import net.minecraft.core.registries.Registries;
                import net.minecraft.resources.ResourceKey;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.sounds.SoundEvent;
                import net.minecraft.sounds.SoundSource;
                import net.minecraft.util.RandomSource;
                import net.minecraft.world.DifficultyInstance;
                import net.minecraft.world.TickRateManager;
                import net.minecraft.world.entity.Entity;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.item.alchemy.PotionBrewing;
                import net.minecraft.world.item.crafting.RecipeManager;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.level.biome.Biome;
                import net.minecraft.world.level.biome.BiomeManager;
                import net.minecraft.world.level.chunk.ChunkAccess;
                import net.minecraft.world.level.chunk.ChunkSource;
                import net.minecraft.world.level.chunk.status.ChunkStatus;
                import net.minecraft.world.level.ColorResolver;
                import net.minecraft.world.level.dimension.DimensionType;
                import net.minecraft.world.level.entity.LevelEntityGetter;
                import net.minecraft.world.level.gameevent.GameEvent;
                import net.minecraft.world.level.levelgen.Heightmap;
                import net.minecraft.world.level.lighting.LevelLightEngine;
                import net.minecraft.world.level.material.Fluid;
                import net.minecraft.world.level.saveddata.maps.MapId;
                import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
                import net.minecraft.world.level.storage.LevelData;
                import net.minecraft.world.ticks.LevelTickAccess;
                import net.minecraft.world.flag.FeatureFlagSet;
                import net.minecraft.world.phys.Vec3;
                import net.minecraft.world.scores.Scoreboard;
                import sun.misc.Unsafe;

                public final class DimensionOnlyLevel extends Level {
                    private ResourceKey<Level> key;

                    private DimensionOnlyLevel() {
                        super(null, Level.OVERWORLD, null, null, () -> null, false, false, 0L, 0);
                    }

                    public static DimensionOnlyLevel create(String dimension) throws Exception {
                        Field field = Unsafe.class.getDeclaredField("theUnsafe");
                        field.setAccessible(true);
                        Unsafe unsafe = (Unsafe) field.get(null);
                        DimensionOnlyLevel level = (DimensionOnlyLevel) unsafe.allocateInstance(DimensionOnlyLevel.class);
                        level.key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
                        return level;
                    }

                    @Override
                    public ResourceKey<Level> dimension() {
                        return key;
                    }

                    @Override
                    public long nextSubTickCount() {
                        return 0L;
                    }

                    @Override
                    public LevelTickAccess<Block> getBlockTicks() {
                        return null;
                    }

                    @Override
                    public LevelTickAccess<Fluid> getFluidTicks() {
                        return null;
                    }

                    @Override
                    public LevelData getLevelData() {
                        return null;
                    }

                    @Override
                    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
                        return null;
                    }

                    @Override
                    public MinecraftServer getServer() {
                        return null;
                    }

                    @Override
                    public ChunkSource getChunkSource() {
                        return null;
                    }

                    @Override
                    public RandomSource getRandom() {
                        return null;
                    }

                    @Override
                    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus requiredStatus,
                                                boolean nonnull) {
                        return null;
                    }

                    @Override
                    public boolean hasChunk(int chunkX, int chunkZ) {
                        return false;
                    }

                    @Override
                    public int getHeight(Heightmap.Types type, int x, int z) {
                        return 0;
                    }

                    @Override
                    public int getSkyDarken() {
                        return 0;
                    }

                    @Override
                    public BiomeManager getBiomeManager() {
                        return null;
                    }

                    @Override
                    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
                        return null;
                    }

                    @Override
                    public boolean isClientSide() {
                        return false;
                    }

                    @Override
                    public int getSeaLevel() {
                        return 0;
                    }

                    @Override
                    public DimensionType dimensionType() {
                        return null;
                    }

                    @Override
                    public RegistryAccess registryAccess() {
                        return null;
                    }

                    @Override
                    public FeatureFlagSet enabledFeatures() {
                        return FeatureFlagSet.of();
                    }

                    @Override
                    public float getShade(Direction direction, boolean shade) {
                        return 0.0F;
                    }

                    @Override
                    public LevelLightEngine getLightEngine() {
                        return null;
                    }

                    @Override
                    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
                        return 0;
                    }

                    @Override
                    public void playSound(Player player, BlockPos pos, SoundEvent sound, SoundSource source,
                                          float volume, float pitch) {
                    }

                    @Override
                    public void addParticle(ParticleOptions particle, double x, double y, double z,
                                            double xSpeed, double ySpeed, double zSpeed) {
                    }

                    @Override
                    public java.util.List<? extends Player> players() {
                        return java.util.List.of();
                    }

                    @Override
                    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) {
                    }

                    @Override
                    public void levelEvent(Player player, int type, BlockPos pos, int data) {
                    }

                    @Override
                    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
                    }

                    @Override
                    public void playSeededSound(Player player, double x, double y, double z, Holder<SoundEvent> sound,
                                                SoundSource source, float volume, float pitch, long seed) {
                    }

                    @Override
                    public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound,
                                                SoundSource source, float volume, float pitch, long seed) {
                    }

                    @Override
                    public String gatherChunkSourceStats() {
                        return "";
                    }

                    @Override
                    public Entity getEntity(int id) {
                        return null;
                    }

                    @Override
                    public TickRateManager tickRateManager() {
                        return new TickRateManager();
                    }

                    @Override
                    public MapItemSavedData getMapData(MapId id) {
                        return null;
                    }

                    @Override
                    public void setMapData(MapId id, MapItemSavedData data) {
                    }

                    @Override
                    public MapId getFreeMapId() {
                        return new MapId(0);
                    }

                    @Override
                    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
                    }

                    @Override
                    public Scoreboard getScoreboard() {
                        return null;
                    }

                    @Override
                    public RecipeManager getRecipeManager() {
                        return null;
                    }

                    @Override
                    protected LevelEntityGetter<Entity> getEntities() {
                        return null;
                    }

                    @Override
                    public PotionBrewing potionBrewing() {
                        return null;
                    }

                    @Override
                    public void setDayTimeFraction(float dayTimeFraction) {
                    }

                    @Override
                    public float getDayTimeFraction() {
                        return 0.0F;
                    }

                    @Override
                    public float getDayTimePerTick() {
                        return 0.0F;
                    }

                    @Override
                    public void setDayTimePerTick(float dayTimePerTick) {
                    }
                }
                """;
        Files.writeString(source, code, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable for dimension-only level test");
        }
        int result = compiler.run(null, null, null, "-classpath", IsolatedServerClassPath.serverClasspath(),
                "-d", outputDir.toString(),
                source.toString());
        if (result != 0) {
            throw new IllegalStateException("Unable to compile dimension-only level, javac exit " + result);
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()}, serverClassLoader());
        Class<?> probeClass = Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.probe.DimensionOnlyLevel",
                true, loader);
        return probeClass.getMethod("create", String.class).invoke(null, dimension);
    }

    private static Object row(String first, String second, String third, String fourth) throws Exception {
        Object row = Array.newInstance(moduleTypeClass(), 4);
        Array.set(row, 0, moduleType(first));
        Array.set(row, 1, moduleType(second));
        Array.set(row, 2, moduleType(third));
        Array.set(row, 3, moduleType(fourth));
        return row;
    }

    private static List<Object> rows(Object... rows) {
        return List.of(rows);
    }

    private static boolean isFullyAssignableRow(Object row) throws Exception {
        return (Boolean) rowEntityClass()
                .getMethod("isFullyAssignableRow", moduleTypeArrayClass())
                .invoke(null, row);
    }

    private static Object validateCoverInstallationForVaultRows(List<Object> rows, int targetRowIndex) throws Exception {
        return serviceClass()
                .getMethod("validateCoverInstallationForVaultRows", List.class, int.class)
                .invoke(null, rows, targetRowIndex);
    }

    private static boolean shouldSkipVaultForAssignment(Object metadata, Object readiness) throws Exception {
        return (Boolean) serviceClass()
                .getMethod("shouldSkipVaultForAssignment", compoundTagClass(), rowReadinessClass())
                .invoke(null, metadata, readiness);
    }

    private static Object validateVaultAccessForReadiness(boolean managementAccess, Object summary) throws Exception {
        return serviceClass()
                .getMethod("validateVaultAccessForReadiness", boolean.class, safeVaultReadinessSummaryClass())
                .invoke(null, managementAccess, summary);
    }

    private static Object decidePremiseAccess(String mode, boolean eligibleCustomer, boolean legacyManagement,
                                              boolean explicitSafeAccess, boolean escortedSession) throws Exception {
        return safePremiseAccessPolicyClass()
                .getMethod("decide", safePremiseModeClass(), boolean.class, boolean.class, boolean.class,
                        boolean.class)
                .invoke(null, safePremiseMode(mode), eligibleCustomer, legacyManagement, explicitSafeAccess,
                        escortedSession);
    }

    private static Object decideInsertInstallation(boolean claimedBankAreaExists, boolean legacyManagement)
            throws Exception {
        return safePremiseAccessPolicyClass()
                .getMethod("decideInsertInstallation", boolean.class, boolean.class)
                .invoke(null, claimedBankAreaExists, legacyManagement);
    }

    private static Object resolveForRow(Object snapshot, Object rowPos, Object facts) throws Exception {
        Class<?> contextType = readinessNestedClass("EvaluationContext");
        Object context = contextType.getConstructor(compoundTagClass(), loadedWorldFactsClass())
                .newInstance(exactRouteMetadata(snapshot), facts);
        Class<?> locationType = readinessNestedClass("RowLocation");
        Object location = locationType.getConstructor(String.class, blockPosClass())
                .newInstance("minecraft:overworld", rowPos);
        Class<?> requestType = readinessNestedClass("RowRequest");
        Object request = requestType.getConstructor(
                        contextType, safeDepositSetupSnapshotClass(), locationType)
                .newInstance(context, snapshot, location);
        return resolverClass().getMethod("resolveForRow", requestType).invoke(null, request);
    }

    private static Class<?> readinessNestedClass(String name) throws ClassNotFoundException {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver$" + name,
                true, serverClassLoader());
    }

    private static Object exactRouteMetadata(Object snapshot) throws Exception {
        Object metadata = newCompoundTag();
        Object premises = newListTag();
        Object routes = newListTag();
        String tellerId = new UUID(0L, 9L).toString();
        for (Object premiseSnapshot : (List<Object>) value(snapshot, "premises")) {
            Object premise = newCompoundTag();
            String bankId = (String) value(premiseSnapshot, "bankId");
            putString(premise, "bankId", bankId);
            Object areas = newListTag();
            for (Object areaSnapshot : (List<Object>) value(premiseSnapshot, "safeAreas")) {
                Object area = newCompoundTag();
                Object vaults = newListTag();
                for (Object vaultSnapshot : (List<Object>) value(areaSnapshot, "vaults")) {
                    String vaultId = (String) value(vaultSnapshot, "id");
                    Object vault = newCompoundTag();
                    putString(vault, "id", vaultId);
                    Object hooks = newListTag();
                    Object hook = newCompoundTag();
                    putString(hook, "tellerId", tellerId);
                    putBoolean(hook, "bankBound", true);
                    putString(hook, "outboundRouteRef", stableRouteId(bankId, vaultId, tellerId, "OUTBOUND"));
                    putString(hook, "returnRouteRef", stableRouteId(bankId, vaultId, tellerId, "RETURN"));
                    addToList(hooks, hook);
                    putTag(vault, "routeHooks", hooks);
                    addToList(vaults, vault);
                    addToList(routes, routeTag(bankId, vaultId, tellerId, "OUTBOUND"));
                    addToList(routes, routeTag(bankId, vaultId, tellerId, "RETURN"));
                }
                putTag(area, "vaults", vaults);
                addToList(areas, area);
            }
            putTag(premise, "safeAreas", areas);
            addToList(premises, premise);
        }
        putTag(metadata, "safeDepositPremises", premises);
        putTag(metadata, "safeTellerRoutes", routes);
        return metadata;
    }

    private static Object routeTag(String bankId, String vaultId, String tellerId, String direction) throws Exception {
        Object route = newCompoundTag();
        putString(route, "id", stableRouteId(bankId, vaultId, tellerId, direction));
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

    private static String stableRouteId(String bankId, String vaultId, String tellerId, String direction)
            throws Exception {
        Class<?> directionClass = Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection",
                true, serverClassLoader());
        Object directionValue = Enum.valueOf(directionClass.asSubclass(Enum.class), direction);
        Class<?> routeClass = Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute",
                true, serverClassLoader());
        return (String) routeClass.getMethod(
                        "stableId", String.class, String.class, String.class, directionClass)
                .invoke(null, bankId, vaultId, tellerId, directionValue);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> loadedRows(Object facts) throws Exception {
        return (List<Object>) value(facts, "loadedRows");
    }

    private static boolean success(Object actionResult) throws Exception {
        return (Boolean) value(actionResult, "success");
    }

    private static String message(Object actionResult) throws Exception {
        return (String) value(actionResult, "message");
    }

    private static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static String firstString(Object values) {
        return ((List<String>) values).get(0);
    }

    private static Object safeDepositSetupSnapshot(int version, List<Object> premises) throws Exception {
        return safeDepositSetupSnapshotClass().getConstructor(int.class, List.class).newInstance(version, premises);
    }

    private static Object safePremiseSnapshot(String id, String bankId, Object bounds, Object exit, Object mode,
                                             List<Object> safeAreas) throws Exception {
        return safePremiseSnapshotClass()
                .getConstructor(String.class, String.class, safeBlockBoundsClass(), safeExitSnapshotClass(),
                        safePremiseModeClass(), List.class)
                .newInstance(id, bankId, bounds, exit, mode, safeAreas);
    }

    private static Object safeAreaSnapshot(String id, String premiseId, Object bounds, List<Object> vaults)
            throws Exception {
        return safeAreaSnapshotClass()
                .getConstructor(String.class, String.class, safeBlockBoundsClass(), List.class)
                .newInstance(id, premiseId, bounds, vaults);
    }

    private static Object safeVaultSnapshot(String id, String safeAreaId, String dimension, Object status,
                                            OptionalInt vaultDoorX, OptionalInt vaultDoorY, OptionalInt vaultDoorZ,
                                            OptionalInt doorIndex, List<Object> routeHooks) throws Exception {
        return safeVaultSnapshotClass()
                .getConstructor(String.class, String.class, String.class, safeVaultSetupStatusClass(),
                        OptionalInt.class, OptionalInt.class, OptionalInt.class, OptionalInt.class, List.class)
                .newInstance(id, safeAreaId, dimension, status, vaultDoorX, vaultDoorY, vaultDoorZ, doorIndex,
                        routeHooks);
    }

    private static Object safeVaultReadinessSummary(String vaultId, boolean ready, List<Object> missingReasons)
            throws Exception {
        return safeVaultReadinessSummaryClass()
                .getConstructor(String.class, boolean.class, List.class)
                .newInstance(vaultId, ready, missingReasons);
    }

    private static Object rowReadiness(Object summary) throws Exception {
        return rowReadinessClass()
                .getConstructor(boolean.class, safePremiseSnapshotClass(), safeAreaSnapshotClass(),
                        safeVaultSnapshotClass(), safeVaultReadinessSummaryClass(), List.class)
                .newInstance(true, null, null, null, summary, List.of());
    }

    private static Object safeTellerRouteHook(String tellerId, boolean bankBound, String outboundRouteRef,
                                              String returnRouteRef) throws Exception {
        return safeTellerRouteHookClass()
                .getConstructor(String.class, boolean.class, String.class, String.class)
                .newInstance(tellerId, bankBound, outboundRouteRef, returnRouteRef);
    }

    private static Object safeBlockBounds(String dimension, int minX, int minY, int minZ, int maxX, int maxY,
                                          int maxZ) throws Exception {
        return safeBlockBoundsClass()
                .getConstructor(String.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .newInstance(dimension, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Object safeExitSnapshot(String dimension, int x, int y, int z, float yaw) throws Exception {
        return safeExitSnapshotClass()
                .getConstructor(String.class, int.class, int.class, int.class, float.class)
                .newInstance(dimension, x, y, z, yaw);
    }

    private static Object loadedWorldFacts(Map<String, Boolean> completeVaultDoors, List<Object> loadedRows)
            throws Exception {
        return loadedWorldFacts(completeVaultDoors, Map.of(), loadedRows);
    }

    private static Object loadedWorldFacts(Map<String, Boolean> completeVaultDoors,
                                           Map<String, Object> resolvedDoorAnchors,
                                           List<Object> loadedRows) throws Exception {
        return loadedWorldFactsClass()
                .getConstructor(Map.class, Map.class, List.class, Set.class)
                .newInstance(completeVaultDoors, resolvedDoorAnchors, loadedRows, Set.of("premise-1"));
    }

    private static Object loadedRow(String dimension, Object pos, Object row) throws Exception {
        return loadedRowSnapshotClass()
                .getConstructor(String.class, blockPosClass(), moduleTypeArrayClass())
                .newInstance(dimension, pos, row);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Optional<Object> resolvedDoorPos(Object facts, String vaultId) throws Exception {
        return (java.util.Optional<Object>) loadedWorldFactsClass()
                .getMethod("resolvedDoorPos", String.class)
                .invoke(facts, vaultId);
    }

    private static Object moduleType(String name) throws Exception {
        return Enum.valueOf(moduleTypeClass().asSubclass(Enum.class), name);
    }

    private static Object safePremiseMode(String name) throws Exception {
        return Enum.valueOf(safePremiseModeClass().asSubclass(Enum.class), name);
    }

    private static Object safeVaultSetupStatus(String name) throws Exception {
        return Enum.valueOf(safeVaultSetupStatusClass().asSubclass(Enum.class), name);
    }

    private static Object missingReason(String name) throws Exception {
        return Enum.valueOf(safeReadinessMissingReasonClass().asSubclass(Enum.class), name);
    }

    private static Class<?> serviceClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService", true,
                serverClassLoader());
    }

    private static Class<?> centralBankClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank", true,
                serverClassLoader());
    }

    private static Class<?> bankClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.Bank", true, serverClassLoader());
    }

    private static Class<?> configClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.Config", true, serverClassLoader());
    }

    private static Class<?> serverPlayerClass() throws Exception {
        return Class.forName("net.minecraft.server.level.ServerPlayer", false, serverClassLoader());
    }

    private static Class<?> minecraftServerClass() throws Exception {
        return Class.forName("net.minecraft.server.MinecraftServer", false, serverClassLoader());
    }

    private static Class<?> resolverClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver", true,
                serverClassLoader());
    }

    private static Class<?> rowEntityClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity",
                true, serverClassLoader());
    }

    private static Class<?> moduleTypeClass() throws Exception {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity$ModuleType",
                true, serverClassLoader());
    }

    private static Class<?> moduleTypeArrayClass() throws Exception {
        return Array.newInstance(moduleTypeClass(), 0).getClass();
    }

    private static Class<?> blockPosClass() throws Exception {
        return Class.forName("net.minecraft.core.BlockPos", true, serverClassLoader());
    }

    private static Class<?> levelClass() throws Exception {
        return Class.forName("net.minecraft.world.level.Level", true, serverClassLoader());
    }

    private static Class<?> compoundTagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.CompoundTag", true, serverClassLoader());
    }

    private static Class<?> listTagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.ListTag", true, serverClassLoader());
    }

    private static Class<?> tagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.Tag", true, serverClassLoader());
    }

    private static Class<?> safeDepositSetupSnapshotClass() throws Exception {
        return setupClass("SafeDepositSetupSnapshot");
    }

    private static Class<?> safePremiseSnapshotClass() throws Exception {
        return setupClass("SafePremiseSnapshot");
    }

    private static Class<?> safeAreaSnapshotClass() throws Exception {
        return setupClass("SafeAreaSnapshot");
    }

    private static Class<?> safeVaultSnapshotClass() throws Exception {
        return setupClass("SafeVaultSnapshot");
    }

    private static Class<?> safeVaultReadinessSummaryClass() throws Exception {
        return setupClass("SafeVaultReadinessSummary");
    }

    private static Class<?> safeTellerRouteHookClass() throws Exception {
        return setupClass("SafeTellerRouteHook");
    }

    private static Class<?> safeBlockBoundsClass() throws Exception {
        return setupClass("SafeBlockBounds");
    }

    private static Class<?> safeExitSnapshotClass() throws Exception {
        return setupClass("SafeExitSnapshot");
    }

    private static Class<?> safePremiseModeClass() throws Exception {
        return setupClass("SafePremiseMode");
    }

    private static Class<?> safePremiseAccessPolicyClass() throws Exception {
        return setupClass("SafePremiseAccessPolicy");
    }

    private static Class<?> safeReadinessMissingReasonClass() throws Exception {
        return setupClass("SafeReadinessMissingReason");
    }

    private static Class<?> safeVaultSetupStatusClass() throws Exception {
        return setupClass("SafeVaultSetupStatus");
    }

    private static Class<?> loadedWorldFactsClass() throws Exception {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver$LoadedWorldFacts",
                true, serverClassLoader());
    }

    private static Class<?> rowReadinessClass() throws Exception {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver$RowReadiness",
                true, serverClassLoader());
    }

    private static Class<?> loadedRowSnapshotClass() throws Exception {
        return Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver$LoadedRowSnapshot",
                true, serverClassLoader());
    }

    private static Class<?> setupClass(String simpleName) throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.setup." + simpleName, true,
                serverClassLoader());
    }

    private static ClassLoader serverClassLoader() {
        return ServerClassLoaderHolder.INSTANCE;
    }

    private static final class ServerClassLoaderHolder {
        private static final ClassLoader INSTANCE = IsolatedServerClassPath.childFirst(
                SafeAreaServerGateTest.class.getClassLoader());
    }
}
