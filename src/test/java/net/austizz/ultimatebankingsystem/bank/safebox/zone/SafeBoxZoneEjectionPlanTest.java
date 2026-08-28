package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxZoneEjectionPlanTest {
    @Test
    void deniedEntryCentersPlayerAtExitAndCancelsTheirMotion() {
        SafeExitSnapshot exit = new SafeExitSnapshot("minecraft:overworld", 12, 65, -8, 90.0F);
        SafeBoxZonePolicy.Decision denied = new SafeBoxZonePolicy.Decision(
                false, Optional.of(exit), List.of());

        SafeBoxZoneEjectionPlan plan = SafeBoxZoneEjectionPlan.from(denied).orElseThrow();

        assertEquals("minecraft:overworld", plan.dimension());
        assertEquals(12.5D, plan.x());
        assertEquals(65.0D, plan.y());
        assertEquals(-7.5D, plan.z());
        assertEquals(90.0F, plan.yaw());
        assertTrue(plan.cancelMotion(), "entry velocity must not push the player back into the premise");
    }

    @Test
    void allowedEntryOrMissingExitDoesNotTeleport() {
        assertTrue(SafeBoxZoneEjectionPlan.from(
                new SafeBoxZonePolicy.Decision(true, Optional.empty(), List.of())).isEmpty());
        assertTrue(SafeBoxZoneEjectionPlan.from(
                new SafeBoxZonePolicy.Decision(false, Optional.empty(), List.of())).isEmpty());
    }
}
