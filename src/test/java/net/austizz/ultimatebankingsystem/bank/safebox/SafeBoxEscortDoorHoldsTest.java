package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortDoorHolds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortDoorHoldsTest {
    @Test
    void holdsAreSessionScopedReferenceCountedAndStaleReleaseSafe() {
        SafeBoxEscortDoorHolds holds = new SafeBoxEscortDoorHolds();

        assertTrue(holds.add(id(40)));
        assertFalse(holds.add(id(40)));
        assertTrue(holds.add(id(41)));
        assertEquals(2, holds.count());
        assertFalse(holds.remove(id(42)));
        assertTrue(holds.remove(id(40)));
        assertTrue(holds.active());
        assertTrue(holds.remove(id(41)));
        assertFalse(holds.active());
    }

    @Test
    void snapshotCanRestoreClientAndPersistedHoldState() {
        SafeBoxEscortDoorHolds source = new SafeBoxEscortDoorHolds();
        source.add(id(50));
        source.add(id(51));

        SafeBoxEscortDoorHolds restored = new SafeBoxEscortDoorHolds();
        restored.replaceWith(source.snapshot());

        assertEquals(2, restored.count());
        assertTrue(restored.contains(id(50)));
        assertTrue(restored.contains(id(51)));
        restored.replaceWith(List.of());
        assertFalse(restored.active());
    }
}
