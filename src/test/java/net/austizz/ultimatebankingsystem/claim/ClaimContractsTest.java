package net.austizz.ultimatebankingsystem.claim;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimContractsTest {
    @Test
    void everyWorkflowUsesTheExpectedUniversalSelectionContract() {
        assertEquals(ClaimSelectionType.CUBOID, ClaimToolKind.SHOP_PLOT.selectionType());
        assertEquals(ClaimSelectionType.CUBOID, ClaimToolKind.SHOP_STOCKROOM.selectionType());
        assertEquals(ClaimSelectionType.BLOCK_TARGET, ClaimToolKind.DELIVERY_PALLET.selectionType());
        assertEquals(ClaimSelectionType.CUBOID, ClaimToolKind.BANK_PREMISE_CREATE.selectionType());
        assertEquals(ClaimSelectionType.POSITION_AND_FACING,
                ClaimToolKind.BANK_PREMISE_EXIT_EDIT.selectionType());
        assertEquals(ClaimSelectionType.CUBOID, ClaimToolKind.BANK_SAFE_AREA.selectionType());
        assertEquals(ClaimSelectionType.CUBOID, ClaimToolKind.VIEWING_ROOM_CREATE.selectionType());
        assertTrue(ClaimToolKind.VIEWING_ROOM_CUSTOMER_ANCHOR.isViewingRoomAnchor());
        assertTrue(ClaimToolKind.VIEWING_ROOM_TELLER_ANCHOR.isViewingRoomAnchor());
        assertTrue(ClaimToolKind.VIEWING_ROOM_DISPLAY_ANCHOR.isViewingRoomAnchor());

        assertTrue(ClaimToolKind.SHOP_PLOT.supportsMode());
        assertTrue(ClaimToolKind.BANK_SAFE_AREA.supportsMode());
        assertFalse(ClaimToolKind.BANK_PREMISE_CREATE.supportsMode());
        assertTrue(ClaimToolKind.DELIVERY_PALLET.staged());
        assertFalse(ClaimToolKind.SHOP_PLOT.staged());
    }

    @Test
    void actionParsingIsBoundedToKnownServerActions() {
        assertEquals(ClaimAction.SET_POS1, ClaimAction.byName(" set_pos1 "));
        assertEquals(ClaimAction.SAVE_AND_EXIT, ClaimAction.byName("save_and_exit"));
        assertEquals(ClaimAction.FINISH_AND_EXIT, ClaimAction.byName("finish_and_exit"));
        assertEquals(ClaimAction.REQUEST_SYNC, ClaimAction.byName("forged-action"));
        assertEquals(ClaimAction.REQUEST_SYNC, ClaimAction.byName(null));
    }

    @Test
    void outlinesNormalizeBoundsAndUseOverflowSafeDistanceChecks() {
        ClaimOutline outline = new ClaimOutline(
                " minecraft:overworld ", " SHOP_PLOT ", " Owner ",
                30_000_000, 90, 30_000_000,
                -30_000_000, 60, -30_000_000);

        assertEquals(-30_000_000, outline.minX());
        assertEquals(60, outline.minY());
        assertEquals(-30_000_000, outline.minZ());
        assertEquals(30_000_000, outline.maxX());
        assertEquals(90, outline.maxY());
        assertEquals(30_000_000, outline.maxZ());
        assertTrue(outline.near(0, 70, 0, 0));

        ClaimOutline small = new ClaimOutline(
                "minecraft:overworld", "SHOP_PLOT", "Owner",
                0, 60, 0, 10, 70, 10);
        assertTrue(small.near(15, 65, 10, 5));
        assertFalse(small.near(16, 65, 10, 5));
        assertFalse(small.near(30_000_000, 65, 30_000_000, 128));

        UUID ownerId = UUID.randomUUID();
        ClaimOutline owned = new ClaimOutline(
                "minecraft:overworld", "SHOP_PLOT", ownerId.toString(), "Owner",
                0, 60, 0, 10, 70, 10);
        assertTrue(owned.ownedBy(ownerId));
        assertFalse(owned.ownedBy(UUID.randomUUID()));
    }
}
