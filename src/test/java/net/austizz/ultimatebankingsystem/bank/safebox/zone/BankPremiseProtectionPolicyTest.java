package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
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

class BankPremiseProtectionPolicyTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final UUID BANK_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BANK_B = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void visitorsCannotModifyPublicPremiseButStaffCan() {
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(
                zone(BANK_A, "bank-a", bounds(0, 20, 0, 20))));

        BankPremiseProtectionPolicy.Decision visitor = decide(index, 5, 5, bankId -> false);
        BankPremiseProtectionPolicy.Decision staff = decide(index, 5, 5, BANK_A::equals);

        assertTrue(visitor.insidePremise());
        assertFalse(visitor.modificationAllowed());
        assertEquals(List.of("bank-a"), premiseIds(visitor.deniedPremises()));
        assertTrue(staff.insidePremise());
        assertTrue(staff.modificationAllowed());
        assertTrue(staff.deniedPremises().isEmpty());
    }

    @Test
    void outsideWorldAndDetachedSafeAreaAreNotPremiseProtected() {
        SafeBoxZoneRecord.Area detached = new SafeBoxZoneRecord.Area(
                "detached-safe", bounds(40, 45, 40, 45));
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(new SafeBoxZoneRecord(
                BANK_A,
                "bank-a",
                SafePremiseMode.PUBLIC,
                bounds(0, 20, 0, 20),
                null,
                List.of(detached)
        )));

        assertFalse(decide(index, 30, 30, bankId -> false).insidePremise());
        assertFalse(decide(index, 42, 42, bankId -> false).insidePremise(),
                "safe-area indexing outside the premise must not expand premise protection");
        assertTrue(decide(index, 42, 42, bankId -> false).modificationAllowed());
    }

    @Test
    void overlappingPremisesRequireStaffAuthorityForEveryBank() {
        SafeBoxZoneIndex index = new SafeBoxZoneIndex(List.of(
                zone(BANK_A, "bank-a", bounds(0, 20, 0, 20)),
                zone(BANK_B, "bank-b", bounds(10, 30, 10, 30))));

        BankPremiseProtectionPolicy.Decision oneBankOnly = decide(
                index, 15, 15, BANK_A::equals);
        BankPremiseProtectionPolicy.Decision bothBanks = decide(
                index, 15, 15, bankId -> bankId.equals(BANK_A) || bankId.equals(BANK_B));

        assertFalse(oneBankOnly.modificationAllowed());
        assertEquals(List.of("bank-b"), premiseIds(oneBankOnly.deniedPremises()));
        assertTrue(bothBanks.modificationAllowed());
    }

    @Test
    void incompletePremiseWithoutExitStillEntersProtectionIndex() {
        SafePremiseSnapshot premise = new SafePremiseSnapshot(
                "incomplete",
                BANK_A.toString(),
                bounds(0, 20, 0, 20),
                null,
                SafePremiseMode.STAFF_ONLY,
                List.of(new SafeAreaSnapshot("safe", "incomplete", bounds(2, 4, 2, 4), List.of()))
        );
        SafeBoxZoneIndex index = SafeBoxZoneCache.fromSnapshots(Map.of(
                BANK_A, new SafeDepositSetupSnapshot(1, List.of(premise))));

        assertEquals(1, index.records().size());
        assertTrue(BankPremiseProtectionPolicy.protects(index, DIMENSION, 10, 64, 10));
        assertFalse(decide(index, 10, 10, bankId -> false).modificationAllowed());
        SafeBoxZonePolicy.Decision access = SafeBoxZonePolicy.decide(
                index, DIMENSION, 10, 64, 10, bankId -> false, Optional.empty());
        assertFalse(access.allowed(), "an incomplete staff-only premise must remain access protected");
        assertTrue(access.exit().isEmpty(), "a missing exit must never produce an invalid teleport target");
    }

    private static BankPremiseProtectionPolicy.Decision decide(
            SafeBoxZoneIndex index,
            int x,
            int z,
            BankPremiseProtectionPolicy.StaffAccess staffAccess) {
        return BankPremiseProtectionPolicy.decide(index, DIMENSION, x, 64, z, staffAccess);
    }

    private static SafeBoxZoneRecord zone(UUID bankId, String premiseId, SafeBlockBounds bounds) {
        return new SafeBoxZoneRecord(
                bankId, premiseId, SafePremiseMode.PUBLIC, bounds, null, List.of());
    }

    private static SafeBlockBounds bounds(int minX, int maxX, int minZ, int maxZ) {
        return new SafeBlockBounds(DIMENSION, minX, 60, minZ, maxX, 70, maxZ);
    }

    private static List<String> premiseIds(List<SafeBoxZoneIndex.Presence> presences) {
        return presences.stream().map(presence -> presence.record().premiseId()).toList();
    }
}
