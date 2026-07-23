package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimSelection;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolLifecycle;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeClaimToolLifecycleTest {
    @Test
    void terminalReasonsRestoreHotbarExactlyOnce() {
        assertEquals(List.of(
                        "FINISH", "BARRIER_CANCEL", "APPLY_SUCCESS", "TIMEOUT", "LOGOUT",
                        "DIMENSION_CHANGE", "DEATH", "SERVER_STOP", "INTERRUPTED"),
                Arrays.stream(SafeClaimToolLifecycle.TerminalReason.values()).map(Enum::name).toList());
        SnapshotToken snapshotToken = new SnapshotToken(List.of(
                "diamond:3:{insured=true}", "emerald:5:{}", "air:0:{}",
                "paper:1:{name=apply}", "barrier:1:{name=cancel}", "air:0:{}",
                "compass:1:{lodestone=outside}", "air:0:{}", "gold_ingot:7:{mint=ubs}"));

        for (SafeClaimToolLifecycle.TerminalReason reason : SafeClaimToolLifecycle.TerminalReason.values()) {
            AtomicInteger restoreCalls = new AtomicInteger();
            AtomicReference<Object> restoredToken = new AtomicReference<>();
            AtomicInteger restoredSlot = new AtomicInteger(-1);
            SafeClaimToolLifecycle lifecycle = new SafeClaimToolLifecycle(
                    SafeClaimToolPurpose.SAFE_AREA, snapshotToken, 7, (token, slot) -> {
                        restoreCalls.incrementAndGet();
                        restoredToken.set(token);
                        restoredSlot.set(slot);
                    });

            assertSame(snapshotToken, lifecycle.snapshotToken());
            assertEquals(7, lifecycle.selectedSlot());
            assertTrue(lifecycle.close(reason));
            assertFalse(lifecycle.close(reason));
            assertEquals(1, restoreCalls.get(), reason.name());
            assertSame(snapshotToken, restoredToken.get(), reason.name());
            assertEquals(7, restoredSlot.get(), reason.name());
        }
    }

    @Test
    void premiseCreateRequiresTwoCornersAndOutsideExit() {
        assertEquals(List.of(
                        "SAFE_AREA", "PREMISE_CREATE", "PREMISE_EXIT_EDIT", "VIEWING_ROOM_CREATE",
                        "VIEWING_ROOM_CUSTOMER_ANCHOR", "VIEWING_ROOM_TELLER_ANCHOR",
                        "VIEWING_ROOM_DISPLAY_ANCHOR"),
                Arrays.stream(SafeClaimToolPurpose.values()).map(Enum::name).toList());
        SafeClaimToolLifecycle create = newLifecycle(SafeClaimToolPurpose.PREMISE_CREATE);
        assertFalse(create.readyToApply());
        assertTrue(create.observeFirstCorner("minecraft:overworld", 0, 60, 0));
        assertFalse(create.readyToApply());
        assertTrue(create.observeSecondCorner("minecraft:overworld", 10, 70, 10));
        assertFalse(create.readyToApply());
        assertTrue(create.observeExit("minecraft:overworld", 5, 65, 5, 90.0F));
        assertFalse(create.readyToApply(), "an exit inside the premise must not be apply-ready");
        assertTrue(create.observeExit("minecraft:overworld", 11, 65, 5, 90.0F));
        assertTrue(create.readyToApply());

        SafeClaimToolLifecycle exitEdit = newLifecycle(SafeClaimToolPurpose.PREMISE_EXIT_EDIT);
        assertFalse(exitEdit.readyToApply());
        assertTrue(exitEdit.observeExit("minecraft:overworld", -4, 64, 8, -45.0F));
        assertTrue(exitEdit.readyToApply(), "exit editing requires only a server-observed exit");
    }

    @Test
    void purposeSpecificTransitionsFailClosed() {
        assertTrue(SafeClaimSelection.class.isRecord(), "selection snapshots must be immutable value records");
        SafeClaimToolLifecycle safeArea = newLifecycle(SafeClaimToolPurpose.SAFE_AREA);
        SafeClaimSelection safeAreaBefore = safeArea.selection();
        assertFalse(safeArea.observeExit("minecraft:overworld", 20, 64, 20, 0.0F));
        assertEquals(safeAreaBefore, safeArea.selection());

        SafeClaimToolLifecycle exitEdit = newLifecycle(SafeClaimToolPurpose.PREMISE_EXIT_EDIT);
        SafeClaimSelection exitEditBefore = exitEdit.selection();
        assertFalse(exitEdit.observeFirstCorner("minecraft:overworld", 1, 64, 1));
        assertFalse(exitEdit.observeSecondCorner("minecraft:overworld", 2, 65, 2));
        assertEquals(exitEditBefore, exitEdit.selection());

        SafeClaimToolLifecycle mismatchedCorners = newLifecycle(SafeClaimToolPurpose.PREMISE_CREATE);
        SafeClaimSelection emptySnapshot = mismatchedCorners.selection();
        String emptyText = emptySnapshot.toString();
        int emptyHash = emptySnapshot.hashCode();
        assertTrue(mismatchedCorners.observeFirstCorner("minecraft:overworld", 0, 60, 0));
        SafeClaimSelection firstCornerSnapshot = mismatchedCorners.selection();
        assertNotSame(emptySnapshot, firstCornerSnapshot);
        assertTrue(mismatchedCorners.observeSecondCorner("minecraft:the_nether", 10, 70, 10));
        assertTrue(mismatchedCorners.observeExit("minecraft:overworld", 11, 65, 5, 0.0F));
        assertFalse(mismatchedCorners.readyToApply());
        assertEquals(emptyText, emptySnapshot.toString());
        assertEquals(emptyHash, emptySnapshot.hashCode());

        SafeClaimToolLifecycle mismatchedExit = newLifecycle(SafeClaimToolPurpose.PREMISE_CREATE);
        assertTrue(mismatchedExit.observeFirstCorner("minecraft:overworld", 0, 60, 0));
        SafeClaimSelection oneCornerSnapshot = mismatchedExit.selection();
        String oneCornerText = oneCornerSnapshot.toString();
        int oneCornerHash = oneCornerSnapshot.hashCode();
        assertTrue(mismatchedExit.observeSecondCorner("minecraft:overworld", 10, 70, 10));
        assertNotSame(oneCornerSnapshot, mismatchedExit.selection());
        assertTrue(mismatchedExit.observeExit("minecraft:the_nether", 11, 65, 5, 0.0F));
        assertFalse(mismatchedExit.readyToApply());
        assertEquals(oneCornerText, oneCornerSnapshot.toString());
        assertEquals(oneCornerHash, oneCornerSnapshot.hashCode());
    }

    private static SafeClaimToolLifecycle newLifecycle(SafeClaimToolPurpose purpose) {
        return new SafeClaimToolLifecycle(purpose, new Object(), 0, (token, slot) -> {});
    }

    private record SnapshotToken(List<String> slots) {
        private SnapshotToken {
            slots = List.copyOf(slots);
            if (slots.size() != 9) {
                throw new IllegalArgumentException("A hotbar snapshot must contain nine slots");
            }
        }
    }
}
