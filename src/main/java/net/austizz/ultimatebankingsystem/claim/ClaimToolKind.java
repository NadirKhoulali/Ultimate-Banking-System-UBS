package net.austizz.ultimatebankingsystem.claim;

public enum ClaimToolKind {
    SHOP_PLOT("Shop Plot", ClaimSelectionType.CUBOID, true, false),
    SHOP_STOCKROOM("Stockroom", ClaimSelectionType.CUBOID, true, false),
    DELIVERY_PALLET("Delivery Pallets", ClaimSelectionType.BLOCK_TARGET, true, true),
    BANK_PREMISE_CREATE("Bank Premise", ClaimSelectionType.CUBOID, false, false),
    BANK_PREMISE_EXIT_EDIT("Premise Exit", ClaimSelectionType.POSITION_AND_FACING, false, false),
    BANK_SAFE_AREA("Safe Area", ClaimSelectionType.CUBOID, true, false),
    VIEWING_ROOM_CREATE("Viewing Room", ClaimSelectionType.CUBOID, false, false),
    VIEWING_ROOM_CUSTOMER_ANCHOR("Customer Anchor", ClaimSelectionType.POSITION_AND_FACING, false, false),
    VIEWING_ROOM_TELLER_ANCHOR("Teller Anchor", ClaimSelectionType.POSITION_AND_FACING, false, false),
    VIEWING_ROOM_DISPLAY_ANCHOR("Deposit Box Anchor", ClaimSelectionType.POSITION_AND_FACING, false, false);

    private final String displayName;
    private final ClaimSelectionType selectionType;
    private final boolean supportsMode;
    private final boolean staged;

    ClaimToolKind(String displayName, ClaimSelectionType selectionType, boolean supportsMode, boolean staged) {
        this.displayName = displayName;
        this.selectionType = selectionType;
        this.supportsMode = supportsMode;
        this.staged = staged;
    }

    public String displayName() {
        return displayName;
    }

    public ClaimSelectionType selectionType() {
        return selectionType;
    }

    public boolean supportsMode() {
        return supportsMode;
    }

    public boolean staged() {
        return staged;
    }

    public boolean requiresExitCapture() {
        return this == BANK_PREMISE_CREATE || this == BANK_PREMISE_EXIT_EDIT;
    }

    public boolean isViewingRoomAnchor() {
        return this == VIEWING_ROOM_CUSTOMER_ANCHOR
                || this == VIEWING_ROOM_TELLER_ANCHOR
                || this == VIEWING_ROOM_DISPLAY_ANCHOR;
    }

    public static ClaimToolKind byName(String raw) {
        if (raw != null) {
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return SHOP_PLOT;
    }
}
