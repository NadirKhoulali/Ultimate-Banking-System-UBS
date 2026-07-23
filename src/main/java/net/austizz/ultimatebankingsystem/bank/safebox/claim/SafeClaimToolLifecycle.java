package net.austizz.ultimatebankingsystem.bank.safebox.claim;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class SafeClaimToolLifecycle {
    public enum TerminalReason {
        FINISH,
        BARRIER_CANCEL,
        APPLY_SUCCESS,
        TIMEOUT,
        LOGOUT,
        DIMENSION_CHANGE,
        DEATH,
        SERVER_STOP,
        INTERRUPTED
    }

    private final SafeClaimToolPurpose purpose;
    private final Object snapshotToken;
    private final int selectedSlot;
    private final BiConsumer<Object, Integer> restore;
    private SafeClaimSelection selection = new SafeClaimSelection(null, null, null);
    private boolean closed;
    private boolean restoring;

    public SafeClaimToolLifecycle(SafeClaimToolPurpose purpose, Object snapshotToken,
                                  int selectedSlot, BiConsumer<Object, Integer> restore) {
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.snapshotToken = snapshotToken;
        this.selectedSlot = selectedSlot;
        this.restore = Objects.requireNonNull(restore, "restore");
    }

    public Object snapshotToken() {
        return snapshotToken;
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public SafeClaimSelection selection() {
        return selection;
    }

    public boolean observeFirstCorner(String dimension, int x, int y, int z) {
        if (closed || purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                || purpose.isViewingRoomAnchor()) return false;
        String normalized = normalizeDimension(dimension);
        if (normalized == null) return false;
        selection = new SafeClaimSelection(
                new SafeClaimSelection.Corner(normalized, x, y, z),
                selection.secondCorner(), selection.exit());
        return true;
    }

    public boolean observeSecondCorner(String dimension, int x, int y, int z) {
        if (closed || purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                || purpose.isViewingRoomAnchor()) return false;
        String normalized = normalizeDimension(dimension);
        if (normalized == null) return false;
        selection = new SafeClaimSelection(selection.firstCorner(),
                new SafeClaimSelection.Corner(normalized, x, y, z), selection.exit());
        return true;
    }

    public boolean observeExit(String dimension, int x, int y, int z, float yaw) {
        if (closed || purpose == SafeClaimToolPurpose.SAFE_AREA
                || purpose == SafeClaimToolPurpose.VIEWING_ROOM_CREATE
                || purpose.isViewingRoomAnchor() || !Float.isFinite(yaw)) return false;
        String normalized = normalizeDimension(dimension);
        if (normalized == null) return false;
        selection = new SafeClaimSelection(selection.firstCorner(), selection.secondCorner(),
                new SafeClaimSelection.Exit(normalized, x, y, z, yaw));
        return true;
    }

    public boolean readyToApply() {
        if (closed) return false;
        return switch (purpose) {
            case SAFE_AREA -> matchingCorners();
            case PREMISE_EXIT_EDIT -> selection.exit() != null;
            case PREMISE_CREATE -> matchingCorners() && matchingExit() && exitStrictlyOutside();
            case VIEWING_ROOM_CREATE -> matchingCorners();
            case VIEWING_ROOM_CUSTOMER_ANCHOR, VIEWING_ROOM_TELLER_ANCHOR,
                 VIEWING_ROOM_DISPLAY_ANCHOR -> false;
        };
    }

    public boolean clearSelection() {
        if (closed) return false;
        selection = new SafeClaimSelection(null, null, null);
        return true;
    }

    public boolean close(TerminalReason reason) {
        Objects.requireNonNull(reason, "reason");
        synchronized (this) {
            if (closed || restoring) return false;
            restoring = true;
        }
        boolean restored = false;
        try {
            restore.accept(snapshotToken, selectedSlot);
            restored = true;
            return true;
        } finally {
            synchronized (this) {
                closed = restored;
                restoring = false;
            }
        }
    }

    private boolean matchingCorners() {
        SafeClaimSelection.Corner first = selection.firstCorner();
        SafeClaimSelection.Corner second = selection.secondCorner();
        return first != null && second != null && first.dimension().equals(second.dimension());
    }

    private boolean matchingExit() {
        return selection.exit() != null
                && selection.firstCorner().dimension().equals(selection.exit().dimension());
    }

    private boolean exitStrictlyOutside() {
        SafeClaimSelection.Corner a = selection.firstCorner();
        SafeClaimSelection.Corner b = selection.secondCorner();
        SafeClaimSelection.Exit exit = selection.exit();
        boolean inside = exit.x() >= Math.min(a.x(), b.x()) && exit.x() <= Math.max(a.x(), b.x())
                && exit.y() >= Math.min(a.y(), b.y()) && exit.y() <= Math.max(a.y(), b.y())
                && exit.z() >= Math.min(a.z(), b.z()) && exit.z() <= Math.max(a.z(), b.z());
        return !inside;
    }

    private static String normalizeDimension(String dimension) {
        if (dimension == null) return null;
        String normalized = dimension.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
