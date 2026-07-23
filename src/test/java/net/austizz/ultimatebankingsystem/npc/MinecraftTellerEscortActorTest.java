package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinecraftTellerEscortActorTest {
    @Test
    void fullBlockRouteAnchorResolvesToStandingPositionAboveIt() {
        TellerRouteWalkDestination.Destination destination =
                TellerRouteWalkDestination.onSurface(
                        new SafeTellerRoutePosition(26, -57, 36), 1.0D);

        assertEquals(26.5D, destination.x());
        assertEquals(-56.0D, destination.y());
        assertEquals(36.5D, destination.z());
    }

    @Test
    void partialBlockRouteAnchorUsesItsActualTopSurface() {
        TellerRouteWalkDestination.Destination destination =
                TellerRouteWalkDestination.onSurface(
                        new SafeTellerRoutePosition(4, 12, 7), 0.5D);

        assertEquals(12.5D, destination.y());
    }

    @Test
    void invalidSurfaceHeightIsRejected() {
        assertNull(TellerRouteWalkDestination.onSurface(
                new SafeTellerRoutePosition(4, 12, 7), -0.1D));
    }
}
