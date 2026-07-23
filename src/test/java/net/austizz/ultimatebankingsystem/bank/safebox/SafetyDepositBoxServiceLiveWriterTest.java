package net.austizz.ultimatebankingsystem.bank.safebox;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"rawtypes", "unchecked"})
class SafetyDepositBoxServiceLiveWriterTest {
    private static final UUID CENTRAL_BANK_ID = new UUID(0L, 0L);
    private static final UUID SECOND_BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_BANK_OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String AREAS_KEY = "safeDepositAreas";
    private static final String ASSIGNMENTS_KEY = "safeDepositAssignments";
    private static final String ESCROW_KEY = "safeDepositEscrow";
    private static final String BOX_CONTENTS_KEY = "safeDepositBoxContents";
    private static final String RENT_AMOUNT_KEY = "safeDepositRentAmount";
    private static final String PREMISES_KEY = "safeDepositPremises";
    private static final int TAG_COMPOUND = 10;

    @Test
    void isolatedLoaderUsesConfiguredProductionOutput() throws Exception {
        Class<?> loadedCentralBank = centralBankClass();
        Path expected = IsolatedServerClassPath.productionClasses().toRealPath();
        Path actual = IsolatedServerClassPath.loadedOrigin(loadedCentralBank).toRealPath();

        assertEquals(expected, actual, "focused tests must load current isolated production classes");
        assertArrayEquals(Files.readAllBytes(IsolatedServerClassPath.productionClassFile(loadedCentralBank)),
                IsolatedServerClassPath.loadedClassBytes(loadedCentralBank),
                "loaded production bytecode must match the current isolated class hash");
    }

    @Test
    void administratorForceRemovalDropsAssignedBoxesInsidePremiseOnly() throws Exception {
        Object metadata = newCompoundTag();
        Object assignments = newListTag();
        addToList(assignments, assignment(CENTRAL_BANK_ID, ACCOUNT_ID,
                "minecraft:overworld", 3, 64, 10, 1));
        addToList(assignments, assignment(CENTRAL_BANK_ID, UUID.randomUUID(),
                "minecraft:overworld", 40, 64, 40, 2));
        addToList(assignments, assignment(SECOND_BANK_ID, UUID.randomUUID(),
                "minecraft:overworld", 4, 64, 10, 3));
        putTag(metadata, ASSIGNMENTS_KEY, assignments);
        Object bounds = setupClass("SafeBlockBounds")
                .getConstructor(String.class, int.class, int.class, int.class,
                        int.class, int.class, int.class)
                .newInstance("minecraft:overworld", 0, 60, 0, 16, 80, 16);

        int removed = (Integer) serviceClass().getMethod(
                        "forceRemoveAssignmentsInPremise",
                        minecraftServerClass(), compoundTagClass(), UUID.class,
                        setupClass("SafeBlockBounds"))
                .invoke(null, null, metadata, CENTRAL_BANK_ID, bounds);

        Object retained = getList(metadata, ASSIGNMENTS_KEY, TAG_COMPOUND);
        assertEquals(1, removed);
        assertEquals(2, size(retained));
        assertEquals(40, getInt(getCompound(retained, 0), "x"));
        assertEquals(SECOND_BANK_ID, getUuid(getCompound(retained, 1), "bankId"));
    }

    @Test
    void claimAndRemoveSafeAreaWritersImmediatelyReconcileTypedPremisesAndPreserveLegacyData() throws Exception {
        String premiseId = "custom-premise-live-writer";
        Object centralBank = newCentralBank();
        Object metadata = bankMetadata(centralBank);
        seedCurrentPremises(metadata, emptyCustomPremise(
                premiseId, "minecraft:overworld", 0, 60, 0, 16, 80, 16));
        seedPreservedSafeDepositFields(metadata);
        Object assignmentsBefore = copyTag(getTag(metadata, ASSIGNMENTS_KEY));
        Object escrowBefore = copyTag(getTag(metadata, ESCROW_KEY));
        Object contentsBefore = copyTag(getTag(metadata, BOX_CONTENTS_KEY));
        String rentBefore = getString(metadata, RENT_AMOUNT_KEY);

        Object claim = serviceClass()
                .getMethod("claimSafeArea", centralBankClass(), serverPlayerClass(), UUID.class, String.class,
                        blockPosClass(), blockPosClass())
                .invoke(null, centralBank, null, CENTRAL_BANK_ID, " Minecraft:OverWorld ",
                        blockPos(1, 63, 9), blockPos(5, 66, 12));

        assertTrue(success(claim), message(claim));
        Object afterClaim = bankMetadata(centralBank);
        Object claimedAreas = getList(afterClaim, AREAS_KEY, TAG_COMPOUND);
        Object claimedPremises = getList(afterClaim, PREMISES_KEY, TAG_COMPOUND);
        assertEquals(1, size(claimedAreas), "live claim writer must persist the legacy area");
        assertEquals(1, size(claimedPremises), "live claim writer must retain the containing typed premise");
        Object claimedPremise = getCompound(claimedPremises, 0);
        assertEquals(premiseId, getString(claimedPremise, "id"));
        Object claimedNestedAreas = getList(claimedPremise, "safeAreas", TAG_COMPOUND);
        assertEquals(1, size(claimedNestedAreas));
        String originalSafeAreaId = getString(getCompound(claimedNestedAreas, 0), "id");
        assertNotEquals("", originalSafeAreaId);
        assertPreservedSafeDepositFields(afterClaim, assignmentsBefore, escrowBefore, contentsBefore, rentBefore);

        Object removeMiddle = serviceClass()
                .getMethod("removeSafeArea", centralBankClass(), serverPlayerClass(), UUID.class, String.class,
                        blockPosClass(), blockPosClass())
                .invoke(null, centralBank, null, CENTRAL_BANK_ID, "minecraft:overworld",
                        blockPos(2, 63, 9), blockPos(4, 66, 12));

        assertTrue(success(removeMiddle), message(removeMiddle));
        Object afterRemove = bankMetadata(centralBank);
        Object remainingAreas = getList(afterRemove, AREAS_KEY, TAG_COMPOUND);
        Object reconciledPremises = getList(afterRemove, PREMISES_KEY, TAG_COMPOUND);
        assertEquals(2, size(remainingAreas), "partial live removal should subdivide the legacy safe area");
        assertEquals(1, size(reconciledPremises), "subdivision must retain one custom parent premise");
        Object retainedPremise = getCompound(reconciledPremises, 0);
        assertEquals(premiseId, getString(retainedPremise, "id"));
        Object reconciledNestedAreas = getList(retainedPremise, "safeAreas", TAG_COMPOUND);
        assertEquals(2, size(reconciledNestedAreas), "typed fragments must remain nested under their custom parent");
        assertNullSafeArea(reconciledNestedAreas, originalSafeAreaId);
        assertPreservedSafeDepositFields(afterRemove, assignmentsBefore, escrowBefore, contentsBefore, rentBefore);
    }

    @Test
    void safeAreaClaimAttachesToOneContainingPremiseWithoutDuplicateMigrationPremise() throws Exception {
        String premiseId = "custom-premise-live-claim";
        Object centralBank = newCentralBank();
        Object metadata = bankMetadata(centralBank);
        seedCurrentPremises(metadata, emptyCustomPremise(
                premiseId, "minecraft:overworld", 0, 60, 0, 16, 80, 16));

        Object beforePremises = getList(metadata, PREMISES_KEY, TAG_COMPOUND);
        assertEquals(1, size(beforePremises));
        assertPremiseConfiguration(getCompound(beforePremises, 0), premiseId,
                "minecraft:overworld", 0, 60, 0, 16, 80, 16,
                -1, 60, 0, 135.0F, "STAFF_ONLY");

        Object claim = claimSafeArea(centralBank, CENTRAL_BANK_ID, " Minecraft:OverWorld ",
                blockPos(8, 68, 8), blockPos(4, 64, 4));

        assertTrue(success(claim), message(claim));
        Object afterClaim = bankMetadata(centralBank);
        Object compatibilityAreas = getList(afterClaim, AREAS_KEY, TAG_COMPOUND);
        assertEquals(1, size(compatibilityAreas), "claim must still write one compatibility area");
        assertBounds(getCompound(compatibilityAreas, 0), "minecraft:overworld", 4, 64, 4, 8, 68, 8);

        Object premises = getList(afterClaim, PREMISES_KEY, TAG_COMPOUND);
        assertEquals(1, size(premises),
                "claim inside a custom premise must not create a same-sized migration premise");
        Object premise = getCompound(premises, 0);
        assertPremiseConfiguration(premise, premiseId,
                "minecraft:overworld", 0, 60, 0, 16, 80, 16,
                -1, 60, 0, 135.0F, "STAFF_ONLY");

        Object safeAreas = getList(premise, "safeAreas", TAG_COMPOUND);
        assertEquals(1, size(safeAreas), "the compatibility area must be represented by one nested safe area");
        Object safeArea = getCompound(safeAreas, 0);
        assertEquals(premiseId, getString(safeArea, "premiseId"));
        assertBounds(safeArea, "minecraft:overworld", 4, 64, 4, 8, 68, 8);
        String safeAreaId = getString(safeArea, "id");
        assertNotEquals("", safeAreaId);

        Object vaults = getList(safeArea, "vaults", TAG_COMPOUND);
        assertEquals(1, size(vaults), "a newly nested safe area must own one placeholder vault");
        Object vault = getCompound(vaults, 0);
        assertNotEquals("", getString(vault, "id"));
        assertEquals(safeAreaId, getString(vault, "safeAreaId"));
        assertEquals("minecraft:overworld", getString(vault, "dimension"));
        assertEquals("SETUP_PENDING", getString(vault, "status"));
        assertEquals("UNWIRED", getString(vault, "routeStatus"));
        assertEquals(0, size(getList(vault, "routeHooks", TAG_COMPOUND)));

        Object afterFirstMigration = copyTag(afterClaim);
        assertFalse(migrateNbt(afterClaim, CENTRAL_BANK_ID), "a second migration must be idempotent");
        assertEquals(afterFirstMigration, afterClaim, "a second migration must not rewrite the attached safe area");
        assertEquals(1, size(getList(afterClaim, PREMISES_KEY, TAG_COMPOUND)),
                "a second migration must not append a same-sized migration premise");
    }

    @Test
    void safeAreaClaimOutsideOrAmbiguousPremisesMutatesNothing() throws Exception {
        Object outsideCentralBank = newCentralBank();
        Object outsideMetadata = bankMetadata(outsideCentralBank);
        seedCurrentPremises(outsideMetadata, emptyCustomPremise(
                "custom-premise-outside", "minecraft:overworld", 0, 60, 0, 10, 75, 10));

        assertClaimRejectedWithoutMutation(
                outsideCentralBank,
                blockPos(20, 64, 20),
                blockPos(24, 68, 24),
                "Safe area must be inside exactly one bank premise."
        );

        Object ambiguousCentralBank = newCentralBank();
        Object ambiguousMetadata = bankMetadata(ambiguousCentralBank);
        seedCurrentPremises(
                ambiguousMetadata,
                emptyCustomPremise(
                        "custom-premise-ambiguous-a", "minecraft:overworld", 0, 60, 0, 12, 75, 12),
                emptyCustomPremise(
                        "custom-premise-ambiguous-b", "minecraft:overworld", 2, 62, 2, 14, 77, 14)
        );

        assertClaimRejectedWithoutMutation(
                ambiguousCentralBank,
                blockPos(4, 64, 4),
                blockPos(8, 68, 8),
                "Safe area is contained by multiple bank premises."
        );
    }

    @Test
    void safeAreaClaimsRejectOverlapsAcrossBanksAsWellAsWithinOneBank() throws Exception {
        Object centralBank = newCentralBank();
        addBank(centralBank, newBank(SECOND_BANK_ID, "Second Bank", SECOND_BANK_OWNER_ID));
        seedCurrentPremises(bankMetadata(centralBank, CENTRAL_BANK_ID), emptyCustomPremise(
                "custom-premise-overlap-primary", CENTRAL_BANK_ID,
                "minecraft:overworld", 0, 60, 0, 20, 80, 20));
        seedCurrentPremises(bankMetadata(centralBank, SECOND_BANK_ID), emptyCustomPremise(
                "custom-premise-overlap-second", SECOND_BANK_ID,
                "minecraft:overworld", 5, 60, 5, 20, 80, 20));

        Object first = claimSafeArea(centralBank, CENTRAL_BANK_ID, "minecraft:overworld",
                blockPos(10, 64, 10), blockPos(12, 66, 12));
        assertTrue(success(first), message(first));

        Object sameBankOverlap = claimSafeArea(centralBank, CENTRAL_BANK_ID, "minecraft:overworld",
                blockPos(11, 64, 11), blockPos(13, 66, 13));
        assertFalse(success(sameBankOverlap), "same-bank overlap must remain rejected");

        Object crossBankOverlap = claimSafeArea(centralBank, SECOND_BANK_ID, "minecraft:overworld",
                blockPos(11, 64, 11), blockPos(13, 66, 13));
        assertFalse(success(crossBankOverlap), "cross-bank overlap must be rejected at claim time");
        assertTrue(message(crossBankOverlap).contains("overlaps"), message(crossBankOverlap));
    }

    @Test
    void currentSetupMigrationFastPathDoesNotCopyUnrelatedMetadataOrParseUnusedAssignments() throws Exception {
        Object migrated = directLegacyMetadata();
        assertTrue(migrateNbt(migrated, CENTRAL_BANK_ID));

        Object current = newFastPathProbeTag();
        for (String key : getAllKeys(migrated)) {
            putTag(current, key, copyTag(getTag(migrated, key)));
        }
        putString(current, "unrelatedLargeMetadata", "must remain untouched");

        assertFalse(migrateNbt(current, CENTRAL_BANK_ID),
                "current setup shape should be a cheap no-op for ordinary reads");
        Object snapshot = snapshotNbt(current);
        assertEquals(1, ((List<?>) value(snapshot, "premises")).size());
        assertEquals("must remain untouched", getString(current, "unrelatedLargeMetadata"));
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

    private static void addBank(Object centralBank, Object bank) throws Exception {
        centralBankClass()
                .getMethod("addBank", bankClass())
                .invoke(centralBank, bank);
    }

    private static void seedConfigValue(String fieldName, Object value) throws Exception {
        Object configValue = configClass().getField(fieldName).get(null);
        var cachedValue = configValue.getClass().getSuperclass().getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        cachedValue.set(configValue, value);
    }

    private static Object bankMetadata(Object centralBank) throws Exception {
        return bankMetadata(centralBank, CENTRAL_BANK_ID);
    }

    private static Object bankMetadata(Object centralBank, UUID bankId) throws Exception {
        return centralBankClass()
                .getMethod("getOrCreateBankMetadata", UUID.class)
                .invoke(centralBank, bankId);
    }

    private static Object claimSafeArea(Object centralBank, UUID bankId, String dimension, Object min, Object max)
            throws Exception {
        return serviceClass()
                .getMethod("claimSafeArea", centralBankClass(), serverPlayerClass(), UUID.class, String.class,
                        blockPosClass(), blockPosClass())
                .invoke(null, centralBank, null, bankId, dimension, min, max);
    }

    private static void seedCurrentPremises(Object metadata, Object... premises) throws Exception {
        putInt(metadata, "safeDepositSetupVersion", 1);
        putTag(metadata, AREAS_KEY, newListTag());
        Object premiseList = newListTag();
        for (Object premise : premises) {
            addToList(premiseList, premise);
        }
        putTag(metadata, PREMISES_KEY, premiseList);
    }

    private static Object emptyCustomPremise(String premiseId,
                                             String dimension,
                                             int minX,
                                             int minY,
                                             int minZ,
                                             int maxX,
                                             int maxY,
                                             int maxZ) throws Exception {
        return emptyCustomPremise(
                premiseId, CENTRAL_BANK_ID, dimension, minX, minY, minZ, maxX, maxY, maxZ);
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

    private static void assertClaimRejectedWithoutMutation(Object centralBank,
                                                           Object min,
                                                           Object max,
                                                           String expectedMessage) throws Exception {
        Object before = bankMetadata(centralBank);
        Object areasBefore = copyTag(getTag(before, AREAS_KEY));
        Object premisesBefore = copyTag(getTag(before, PREMISES_KEY));
        Object metadataBefore = copyTag(before);

        Object result = claimSafeArea(centralBank, CENTRAL_BANK_ID, " Minecraft:OverWorld ", min, max);

        assertFalse(success(result));
        assertEquals(expectedMessage, message(result));
        Object after = bankMetadata(centralBank);
        assertEquals(areasBefore, getTag(after, AREAS_KEY), "rejected claim must not change compatibility areas");
        assertEquals(premisesBefore, getTag(after, PREMISES_KEY), "rejected claim must not change typed premises");
        assertEquals(metadataBefore, after, "rejected claim must leave all bank metadata byte-equivalent");
    }

    private static void assertPremiseConfiguration(Object premise,
                                                   String premiseId,
                                                   String dimension,
                                                   int minX,
                                                   int minY,
                                                   int minZ,
                                                   int maxX,
                                                   int maxY,
                                                   int maxZ,
                                                   int exitX,
                                                   int exitY,
                                                   int exitZ,
                                                   float exitYaw,
                                                   String mode) throws Exception {
        assertEquals(premiseId, getString(premise, "id"));
        assertEquals(CENTRAL_BANK_ID.toString(), getString(premise, "bankId"));
        assertBounds(premise, dimension, minX, minY, minZ, maxX, maxY, maxZ);
        assertEquals(exitX, getInt(premise, "exitX"));
        assertEquals(exitY, getInt(premise, "exitY"));
        assertEquals(exitZ, getInt(premise, "exitZ"));
        assertEquals(exitYaw, getFloat(premise, "exitYaw"));
        assertEquals(mode, getString(premise, "mode"));
    }

    private static void assertBounds(Object tag,
                                     String dimension,
                                     int minX,
                                     int minY,
                                     int minZ,
                                     int maxX,
                                     int maxY,
                                     int maxZ) throws Exception {
        assertEquals(dimension, getString(tag, "dimension"));
        assertEquals(minX, getInt(tag, "minX"));
        assertEquals(minY, getInt(tag, "minY"));
        assertEquals(minZ, getInt(tag, "minZ"));
        assertEquals(maxX, getInt(tag, "maxX"));
        assertEquals(maxY, getInt(tag, "maxY"));
        assertEquals(maxZ, getInt(tag, "maxZ"));
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

    private static void seedPreservedSafeDepositFields(Object metadata) throws Exception {
        Object assignments = newListTag();
        Object assignment = newCompoundTag();
        putUuid(assignment, "accountId", ACCOUNT_ID);
        putUuid(assignment, "bankId", CENTRAL_BANK_ID);
        putString(assignment, "dimension", "minecraft:overworld");
        putInt(assignment, "x", 3);
        putInt(assignment, "y", 64);
        putInt(assignment, "z", 10);
        putInt(assignment, "doorIndex", 2);
        putString(assignment, "boxNumber", "SDB-2");
        putBoolean(assignment, "locked", false);
        putString(assignment, "legacyLocation", "minecraft:overworld:3,64,10#2");
        addToList(assignments, assignment);
        putTag(metadata, ASSIGNMENTS_KEY, assignments);

        Object escrow = newListTag();
        Object escrowEntry = newCompoundTag();
        putUuid(escrowEntry, "accountId", ACCOUNT_ID);
        putUuid(escrowEntry, "bankId", CENTRAL_BANK_ID);
        putString(escrowEntry, "boxNumber", "SDB-2");
        addToList(escrow, escrowEntry);
        putTag(metadata, ESCROW_KEY, escrow);

        Object contents = newCompoundTag();
        putString(contents, "slot.0", "minecraft:diamond");
        putTag(metadata, BOX_CONTENTS_KEY, contents);
        putString(metadata, RENT_AMOUNT_KEY, "42.00");
    }

    private static Object assignment(UUID bankId,
                                     UUID accountId,
                                     String dimension,
                                     int x,
                                     int y,
                                     int z,
                                     int doorIndex) throws Exception {
        Object assignment = newCompoundTag();
        putUuid(assignment, "accountId", accountId);
        putUuid(assignment, "bankId", bankId);
        putString(assignment, "dimension", dimension);
        putInt(assignment, "x", x);
        putInt(assignment, "y", y);
        putInt(assignment, "z", z);
        putInt(assignment, "doorIndex", doorIndex);
        putString(assignment, "boxNumber", "SDB-force-" + doorIndex);
        putBoolean(assignment, "locked", false);
        return assignment;
    }

    private static void assertPreservedSafeDepositFields(Object metadata,
                                                         Object assignmentsBefore,
                                                         Object escrowBefore,
                                                         Object contentsBefore,
                                                         String rentBefore) throws Exception {
        assertEquals(assignmentsBefore, getTag(metadata, ASSIGNMENTS_KEY));
        assertEquals(escrowBefore, getTag(metadata, ESCROW_KEY));
        assertEquals(contentsBefore, getTag(metadata, BOX_CONTENTS_KEY));
        assertEquals(rentBefore, getString(metadata, RENT_AMOUNT_KEY));
        Object assignment = getCompound(getList(metadata, ASSIGNMENTS_KEY, TAG_COMPOUND), 0);
        assertTrue(hasUuid(assignment, "accountId"));
        assertTrue(hasUuid(assignment, "bankId"));
        assertEquals(ACCOUNT_ID, getUuid(assignment, "accountId"));
        assertEquals(CENTRAL_BANK_ID, getUuid(assignment, "bankId"));
    }

    private static void assertNullSafeArea(Object safeAreas, String safeAreaId) throws Exception {
        assertNotNull(safeAreaId);
        for (int i = 0; i < size(safeAreas); i++) {
            assertNotEquals(safeAreaId, getString(getCompound(safeAreas, i), "id"),
                    "subdivision must not leave the original generated safe area");
        }
    }

    private static Object safeVaultReadinessSummary(String vaultId, boolean ready, List<Object> missingReasons)
            throws Exception {
        return safeVaultReadinessSummaryClass()
                .getConstructor(String.class, boolean.class, List.class)
                .newInstance(vaultId, ready, missingReasons);
    }

    private static Object directLegacyMetadata() throws Exception {
        Object metadata = newCompoundTag();
        Object areas = newListTag();
        Object area = newCompoundTag();
        putString(area, "dimension", "minecraft:overworld");
        putInt(area, "minX", 1);
        putInt(area, "minY", 63);
        putInt(area, "minZ", 9);
        putInt(area, "maxX", 5);
        putInt(area, "maxY", 66);
        putInt(area, "maxZ", 12);
        addToList(areas, area);
        putTag(metadata, AREAS_KEY, areas);

        Object assignments = newListTag();
        Object assignment = newCompoundTag();
        putUuid(assignment, "accountId", ACCOUNT_ID);
        putUuid(assignment, "bankId", CENTRAL_BANK_ID);
        putString(assignment, "dimension", "minecraft:overworld");
        putInt(assignment, "x", 3);
        putInt(assignment, "y", 64);
        putInt(assignment, "z", 10);
        putInt(assignment, "doorIndex", 2);
        addToList(assignments, assignment);
        putTag(metadata, ASSIGNMENTS_KEY, assignments);
        return metadata;
    }

    private static boolean migrateNbt(Object metadata, UUID bankId) throws Exception {
        return (Boolean) safeDepositSetupNbtCodecClass()
                .getMethod("migrateLegacy", compoundTagClass(), UUID.class)
                .invoke(null, metadata, bankId);
    }

    private static Object snapshotNbt(Object metadata) throws Exception {
        return safeDepositSetupNbtCodecClass()
                .getMethod("snapshot", compoundTagClass())
                .invoke(null, metadata);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getAllKeys(Object tag) throws Exception {
        return (Set<String>) compoundTagClass().getMethod("getAllKeys").invoke(tag);
    }

    private static Object newFastPathProbeTag() throws Exception {
        Path sourceDir = Path.of("build", "tmp", "fastPathProbeSource");
        Path outputDir = Path.of("build", "tmp", "fastPathProbeClasses");
        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);
        Path source = sourceDir.resolve("FastPathProbeTag.java");
        String code = """
                package net.austizz.ultimatebankingsystem.bank.safebox.probe;

                import net.minecraft.nbt.CompoundTag;
                import net.minecraft.nbt.ListTag;

                public final class FastPathProbeTag extends CompoundTag {
                    @Override
                    public CompoundTag copy() {
                        throw new AssertionError("current setup fast path must not deep-copy unrelated metadata");
                    }

                    @Override
                    public ListTag getList(String key, int type) {
                        if ("safeDepositAssignments".equals(key)) {
                            throw new AssertionError("current setup fast path must not parse unused legacy assignments");
                        }
                        return super.getList(key, type);
                    }
                }
                """;
        Files.writeString(source, code, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable for fast-path probe test");
        }
        int result = compiler.run(
                null,
                null,
                null,
                "-classpath",
                IsolatedServerClassPath.serverClasspath(),
                "-d",
                outputDir.toString(),
                source.toString()
        );
        if (result != 0) {
            throw new IllegalStateException("Unable to compile fast-path probe tag, javac exit " + result);
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()}, serverClassLoader());
        return Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.probe.FastPathProbeTag", true, loader)
                .getConstructor()
                .newInstance();
    }

    private static Object missingReason(String name) throws Exception {
        return Enum.valueOf(safeReadinessMissingReasonClass().asSubclass(Enum.class), name);
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

    private static Object blockPos(int x, int y, int z) throws Exception {
        return blockPosClass().getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
    }

    private static Object newCompoundTag() throws Exception {
        return compoundTagClass().getConstructor().newInstance();
    }

    private static Object newListTag() throws Exception {
        return listTagClass().getConstructor().newInstance();
    }

    private static void putTag(Object tag, String key, Object value) throws Exception {
        compoundTagClass().getMethod("put", String.class, tagClass()).invoke(tag, key, value);
    }

    private static void putString(Object tag, String key, String value) throws Exception {
        compoundTagClass().getMethod("putString", String.class, String.class).invoke(tag, key, value);
    }

    private static void putInt(Object tag, String key, int value) throws Exception {
        compoundTagClass().getMethod("putInt", String.class, int.class).invoke(tag, key, value);
    }

    private static void putFloat(Object tag, String key, float value) throws Exception {
        compoundTagClass().getMethod("putFloat", String.class, float.class).invoke(tag, key, value);
    }

    private static void putBoolean(Object tag, String key, boolean value) throws Exception {
        compoundTagClass().getMethod("putBoolean", String.class, boolean.class).invoke(tag, key, value);
    }

    private static void putUuid(Object tag, String key, UUID value) throws Exception {
        compoundTagClass().getMethod("putUUID", String.class, UUID.class).invoke(tag, key, value);
    }

    private static void addToList(Object list, Object value) {
        ((List<Object>) list).add(value);
    }

    private static Object getTag(Object tag, String key) throws Exception {
        return compoundTagClass().getMethod("get", String.class).invoke(tag, key);
    }

    private static Object copyTag(Object tag) throws Exception {
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

    private static float getFloat(Object tag, String key) throws Exception {
        return (Float) compoundTagClass().getMethod("getFloat", String.class).invoke(tag, key);
    }

    private static boolean hasUuid(Object tag, String key) throws Exception {
        return (Boolean) compoundTagClass().getMethod("hasUUID", String.class).invoke(tag, key);
    }

    private static UUID getUuid(Object tag, String key) throws Exception {
        return (UUID) compoundTagClass().getMethod("getUUID", String.class).invoke(tag, key);
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

    private static Class<?> blockPosClass() throws Exception {
        return Class.forName("net.minecraft.core.BlockPos", true, serverClassLoader());
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

    private static Class<?> safeVaultReadinessSummaryClass() throws Exception {
        return setupClass("SafeVaultReadinessSummary");
    }

    private static Class<?> safeDepositSetupNbtCodecClass() throws Exception {
        return setupClass("SafeDepositSetupNbtCodec");
    }

    private static Class<?> safeReadinessMissingReasonClass() throws Exception {
        return setupClass("SafeReadinessMissingReason");
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
                SafetyDepositBoxServiceLiveWriterTest.class.getClassLoader());
    }
}
