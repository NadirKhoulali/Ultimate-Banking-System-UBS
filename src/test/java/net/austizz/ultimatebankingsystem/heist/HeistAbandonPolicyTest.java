package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeistAbandonPolicyTest {
    private static final SafeBlockBounds PREMISE = new SafeBlockBounds(
            "minecraft:overworld", 0, 0, 0, 9, 12, 9);

    @Test
    void measuresHorizontalDistanceFromNearestPremiseEdge() {
        assertFalse(HeistAbandonPolicy.beyondPremise(
                PREMISE, "minecraft:overworld", 5.0D, 5.0D, 96.0D));
        assertFalse(HeistAbandonPolicy.beyondPremise(
                PREMISE, "minecraft:overworld", 106.0D, 5.0D, 96.0D));
        assertTrue(HeistAbandonPolicy.beyondPremise(
                PREMISE, "minecraft:overworld", 106.01D, 5.0D, 96.0D));
        assertTrue(HeistAbandonPolicy.beyondPremise(
                PREMISE, "minecraft:overworld", 90.0D, 70.0D, 96.0D));
    }

    @Test
    void changingDimensionAlwaysLeavesTheHeistArea() {
        assertTrue(HeistAbandonPolicy.beyondPremise(
                PREMISE, "minecraft:the_nether", 5.0D, 5.0D, 96.0D));
    }
}
