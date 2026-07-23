package net.austizz.ultimatebankingsystem.heist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeistExfillZoneTest {
    @Test
    void squareBoundaryRoundTripsAndIncludesItsEdges() {
        HeistExfillZone.Boundary boundary = HeistExfillZone.square(100.5D, -20.5D, 5.0D);

        assertTrue(boundary.valid());
        assertEquals(40.0D, boundary.perimeterLength(), 0.000001D);
        assertTrue(boundary.contains(100.5D, -20.5D));
        assertTrue(boundary.contains(95.5D, -20.5D));
        assertTrue(boundary.contains(105.5D, -15.5D));
        assertFalse(boundary.contains(105.5001D, -20.5D));

        HeistExfillZone.Boundary decoded = HeistExfillZone.Boundary.decode(boundary.encode());
        assertEquals(boundary, decoded);
    }

    @Test
    void visualStateReflectsExtractionReadiness() {
        assertEquals(HeistExfillZone.VisualState.IDLE,
                HeistExfillZone.visualState(true, 4, 0));
        assertEquals(HeistExfillZone.VisualState.CONTESTED,
                HeistExfillZone.visualState(false, 4, 1));
        assertEquals(HeistExfillZone.VisualState.CONTESTED,
                HeistExfillZone.visualState(true, 4, 3));
        assertEquals(HeistExfillZone.VisualState.ACTIVE,
                HeistExfillZone.visualState(true, 4, 4));
    }

    @Test
    void malformedBoundariesAreRejectedWithoutThrowing() {
        assertFalse(HeistExfillZone.Boundary.decode("").valid());
        assertFalse(HeistExfillZone.Boundary.decode("1,2;3,4").valid());
        assertFalse(HeistExfillZone.Boundary.decode("1,2;bad,4;5,6").valid());
    }
}
