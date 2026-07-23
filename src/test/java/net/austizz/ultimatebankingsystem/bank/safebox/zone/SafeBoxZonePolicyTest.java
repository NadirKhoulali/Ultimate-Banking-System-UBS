package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortPhase;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTarget;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxZonePolicyTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final UUID BANK_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BANK_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TELLER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void publicExteriorIsOpenButSafeInteriorRequiresStaffOrExactEscort() {
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(zone(
                BANK_A, "premise-a", SafePremiseMode.PUBLIC, 0, 20,
                area("area-a", 5, 10), new SafeExitSnapshot(DIMENSION, -2, 64, 0, 90.0F))));

        assertTrue(decide(index, 2, id -> false, Optional.empty()).allowed());
        assertFalse(decide(index, 7, id -> false, Optional.empty()).allowed());
        assertTrue(decide(index, 7, id -> id.equals(BANK_A), Optional.empty()).allowed());
        assertTrue(decide(index, 7, id -> false,
                Optional.of(escort(BANK_A, 7, SafeBoxEscortPhase.OUTBOUND))).allowed());
    }

    @Test
    void staffOnlyExteriorAcceptsStaffOrExactPremiseEscortOnly() {
        SafeBoxZoneRecord first = zone(BANK_A, "premise-a", SafePremiseMode.STAFF_ONLY, 0, 20,
                area("area-a", 5, 10), new SafeExitSnapshot(DIMENSION, -2, 64, 0, 0.0F));
        SafeBoxZoneRecord second = zone(BANK_A, "premise-b", SafePremiseMode.STAFF_ONLY, 30, 50,
                area("area-b", 35, 40), new SafeExitSnapshot(DIMENSION, 28, 64, 0, 0.0F));
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(first, second));

        assertFalse(decide(index, 2, id -> false, Optional.empty()).allowed());
        assertTrue(decide(index, 2, id -> true, Optional.empty()).allowed());
        assertTrue(decide(index, 2, id -> false,
                Optional.of(escort(BANK_A, 7, SafeBoxEscortPhase.AT_VAULT))).allowed());
        assertFalse(decide(index, 32, id -> false,
                Optional.of(escort(BANK_A, 7, SafeBoxEscortPhase.AT_VAULT))).allowed(),
                "same-bank escort must not authorize a different premise");
    }

    @Test
    void samePremiseDifferentSafeAreaAndReturningEscortDoNotAuthorizeSafeInterior() {
        SafeBoxZoneRecord record = new SafeBoxZoneRecord(
                BANK_A, "premise-a", SafePremiseMode.PUBLIC,
                bounds(0, 30), new SafeExitSnapshot(DIMENSION, -2, 64, 0, 0.0F),
                List.of(area("area-a", 5, 10), area("area-b", 15, 20)));
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(record));

        assertFalse(decide(index, 17, id -> false,
                Optional.of(escort(BANK_A, 7, SafeBoxEscortPhase.INSPECTING))).allowed());
        assertFalse(decide(index, 7, id -> false,
                Optional.of(escort(BANK_A, 7, SafeBoxEscortPhase.RETURNING))).allowed());
    }

    @Test
    void overlapsRequireAuthorizationForEveryContainingZoneAndOnlyUseGloballySafeExit() {
        SafeBoxZoneRecord first = zone(BANK_A, "premise-a", SafePremiseMode.STAFF_ONLY, 0, 20,
                area("area-a", 5, 10), new SafeExitSnapshot(DIMENSION, 25, 64, 0, 0.0F));
        SafeBoxZoneRecord overlapping = zone(BANK_B, "premise-b", SafePremiseMode.STAFF_ONLY, 10, 30,
                area("area-b", 15, 18), new SafeExitSnapshot(DIMENSION, 35, 64, 0, 0.0F));
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(first, overlapping));

        SafeBoxZonePolicy.Decision denied = decide(index, 12, id -> id.equals(BANK_A), Optional.empty());
        assertFalse(denied.allowed(), "access to one overlapping bank must not authorize the other");
        assertEquals(35, denied.exit().orElseThrow().x(),
                "an exit inside another protected premise must be rejected");

        SafeBoxZoneRecord trapped = zone(BANK_B, "premise-c", SafePremiseMode.STAFF_ONLY, 31, 40,
                area("area-c", 32, 34), new SafeExitSnapshot(DIMENSION, 25, 64, 0, 0.0F));
        SafeBoxZoneIndex noSafeExit = new SafeBoxZoneIndex(List.of(first, overlapping, trapped));
        assertTrue(decide(noSafeExit, 12, id -> false, Optional.empty()).exit().isEmpty(),
                "overlapping or looping exits must deny without teleporting");
    }

    @Test
    void cacheRefreshWindowNeverRebuildsMoreThanOncePerTwentyTicks() {
        assertTrue(SafeBoxZoneCache.shouldRebuild(Long.MIN_VALUE, 100));
        assertFalse(SafeBoxZoneCache.shouldRebuild(100, 119));
        assertTrue(SafeBoxZoneCache.shouldRebuild(100, 120));
        assertTrue(SafeBoxZoneCache.shouldRebuild(120, 5), "server tick rollback must rebuild safely");
    }

    @Test
    void cacheBuildsRecordsFromValidatedSnapshotsAndRejectsMetadataBankMismatch() {
        SafeAreaSnapshot area = new SafeAreaSnapshot("area-a", "premise-a", bounds(5, 10), List.of());
        SafePremiseSnapshot premise = new SafePremiseSnapshot(
                "premise-a", BANK_A.toString().toUpperCase(), bounds(0, 20),
                new SafeExitSnapshot(DIMENSION, -2, 64, 0, 0.0F), SafePremiseMode.PUBLIC, List.of(area));
        SafeDepositSetupSnapshot setup = new SafeDepositSetupSnapshot(1, List.of(premise));

        assertEquals(1, SafeBoxZoneCache.fromSnapshots(Map.of(BANK_A, setup)).records().size());
        assertTrue(SafeBoxZoneCache.fromSnapshots(Map.of(BANK_B, setup)).records().isEmpty());
    }

    @Test
    void emptyPublicPremiseStaysOpenAndEmptyStaffOnlyPremiseUsesValidatedExit() {
        SafePremiseSnapshot publicPremise = new SafePremiseSnapshot(
                "premise-public", BANK_A.toString(), bounds(0, 20),
                new SafeExitSnapshot(DIMENSION, -2, 64, 0, 90.0F),
                SafePremiseMode.PUBLIC, List.of());
        SafePremiseSnapshot staffPremise = new SafePremiseSnapshot(
                "premise-staff", BANK_A.toString(), bounds(30, 50),
                new SafeExitSnapshot(DIMENSION, 28, 64, 0, 180.0F),
                SafePremiseMode.STAFF_ONLY, List.of());
        SafeBoxZoneIndex index = SafeBoxZoneCache.fromSnapshots(Map.of(
                BANK_A, new SafeDepositSetupSnapshot(1, List.of(publicPremise, staffPremise))));

        assertEquals(2, index.records().size());
        assertTrue(decide(index, 2, id -> false, Optional.empty()).allowed(),
                "PUBLIC empty premises remain open to visitors");
        SafeBoxZonePolicy.Decision denied = decide(index, 32, id -> false, Optional.empty());
        assertFalse(denied.allowed(), "STAFF_ONLY empty premises still enforce access");
        assertEquals(28, denied.exit().orElseThrow().x());
    }

    @Test
    void emptySiblingDoesNotChangePopulatedPremiseEscortScope() {
        SafePremiseSnapshot empty = new SafePremiseSnapshot(
                "premise-empty", BANK_A.toString(), bounds(30, 50),
                new SafeExitSnapshot(DIMENSION, 28, 64, 0, 0.0F),
                SafePremiseMode.PUBLIC, List.of());
        SafeAreaSnapshot populatedArea = new SafeAreaSnapshot(
                "area-populated", "premise-populated", bounds(5, 10), List.of());
        SafePremiseSnapshot populated = new SafePremiseSnapshot(
                "premise-populated", BANK_A.toString(), bounds(0, 20),
                new SafeExitSnapshot(DIMENSION, -2, 64, 0, 0.0F),
                SafePremiseMode.PUBLIC, List.of(populatedArea));
        SafeBoxZoneIndex index = SafeBoxZoneCache.fromSnapshots(Map.of(
                BANK_A, new SafeDepositSetupSnapshot(1, List.of(empty, populated))));

        SafeBoxZoneIndex.EscortScope scope = index.scopeFor(
                escort(BANK_A, 7, SafeBoxEscortPhase.OUTBOUND)).orElseThrow();

        assertEquals("premise-populated", scope.premiseId());
        assertEquals("area-populated", scope.safeAreaId());
    }

    @Test
    void rebuildingAfterImmediateClearRemovesDeletedPremiseEnforcement() {
        SafePremiseSnapshot deleted = new SafePremiseSnapshot(
                "premise-deleted", BANK_A.toString(), bounds(0, 20),
                new SafeExitSnapshot(DIMENSION, -2, 64, 0, 0.0F),
                SafePremiseMode.STAFF_ONLY, List.of());
        SafeBoxZoneIndex before = SafeBoxZoneCache.fromSnapshots(Map.of(
                BANK_A, new SafeDepositSetupSnapshot(1, List.of(deleted))));
        SafeBoxZoneIndex after = SafeBoxZoneCache.fromSnapshots(Map.of(
                BANK_A, new SafeDepositSetupSnapshot(1, List.of())));

        assertFalse(decide(before, 2, id -> false, Optional.empty()).allowed());
        assertTrue(decide(after, 2, id -> false, Optional.empty()).allowed(),
                "a post-commit rebuild must not retain a deleted premise zone");
    }

    private static SafeBoxZonePolicy.Decision decide(SafeBoxZoneIndex index, int x,
                                                       SafeBoxZonePolicy.StaffAccess staff,
                                                       Optional<SafeBoxEscortSession> escort) {
        return SafeBoxZonePolicy.decide(index, DIMENSION, x, 64, 0, staff, escort);
    }

    private static SafeBoxEscortSession escort(UUID bankId, int targetX, SafeBoxEscortPhase phase) {
        SafeBoxEscortTarget target = new SafeBoxEscortTarget(bankId, "vault", UUID.randomUUID(), DIMENSION,
                new EscortBlockPosition(targetX, 64, 0), 0, TELLER);
        return new SafeBoxEscortSession(UUID.randomUUID(), PLAYER, target,
                new SafeBoxArea(DIMENSION, 5, 60, -2, 10, 70, 2), phase, -1L, null);
    }

    private static SafeBoxZoneRecord zone(UUID bankId, String premiseId, SafePremiseMode mode,
                                           int minX, int maxX, SafeBoxZoneRecord.Area area,
                                           SafeExitSnapshot exit) {
        return new SafeBoxZoneRecord(bankId, premiseId, mode, bounds(minX, maxX), exit, List.of(area));
    }

    private static SafeBoxZoneRecord.Area area(String id, int minX, int maxX) {
        return new SafeBoxZoneRecord.Area(id, bounds(minX, maxX));
    }

    private static SafeBlockBounds bounds(int minX, int maxX) {
        return new SafeBlockBounds(DIMENSION, minX, 60, -2, maxX, 70, 2);
    }
}
