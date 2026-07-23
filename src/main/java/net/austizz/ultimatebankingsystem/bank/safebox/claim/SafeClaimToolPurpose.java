package net.austizz.ultimatebankingsystem.bank.safebox.claim;

public enum SafeClaimToolPurpose {
    SAFE_AREA,
    PREMISE_CREATE,
    PREMISE_EXIT_EDIT,
    VIEWING_ROOM_CREATE,
    VIEWING_ROOM_CUSTOMER_ANCHOR,
    VIEWING_ROOM_TELLER_ANCHOR,
    VIEWING_ROOM_DISPLAY_ANCHOR;

    public boolean isViewingRoomAnchor() {
        return this == VIEWING_ROOM_CUSTOMER_ANCHOR
                || this == VIEWING_ROOM_TELLER_ANCHOR
                || this == VIEWING_ROOM_DISPLAY_ANCHOR;
    }
}
