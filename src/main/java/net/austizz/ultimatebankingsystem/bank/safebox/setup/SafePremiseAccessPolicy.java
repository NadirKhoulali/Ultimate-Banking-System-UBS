package net.austizz.ultimatebankingsystem.bank.safebox.setup;

public final class SafePremiseAccessPolicy {
    private SafePremiseAccessPolicy() {
    }

    public record Decision(boolean allowed,
                           boolean normalCustomerAccess,
                           boolean legacyManagementAccess,
                           boolean explicitSafeAccess,
                           boolean escortedSessionAccess,
                           String denialMessage) {
        public Decision {
            denialMessage = denialMessage == null ? "" : denialMessage;
        }
    }

    public static Decision decide(SafePremiseMode mode,
                                  boolean eligibleCustomerAccess,
                                  boolean legacyManagementAccess,
                                  boolean explicitSafeAccess,
                                  boolean escortedSessionAccess) {
        SafePremiseMode cleanMode = mode == null ? SafePremiseMode.PUBLIC : mode;
        if (legacyManagementAccess) {
            return new Decision(true, false, true, false, false, "");
        }
        if (explicitSafeAccess) {
            return new Decision(true, false, false, true, false, "");
        }
        if (escortedSessionAccess) {
            return new Decision(true, false, false, false, true, "");
        }
        if (cleanMode == SafePremiseMode.PUBLIC && eligibleCustomerAccess) {
            return new Decision(true, true, false, false, false, "");
        }
        if (cleanMode == SafePremiseMode.STAFF_ONLY) {
            return new Decision(false, false, false, false, false,
                    "This safety deposit premise is staff-only.");
        }
        return new Decision(false, false, false, false, false,
                "You do not have access to this safety deposit premise.");
    }

    public static Decision decideStructuralMutation(boolean legacyManagementAccess) {
        if (legacyManagementAccess) {
            return new Decision(true, false, true, false, false, "");
        }
        return new Decision(false, false, false, false, false,
                "This bank safe area is protected.");
    }

    public static Decision decideInsertInstallation(boolean claimedBankAreaExists,
                                                    boolean legacyManagementAccess) {
        return decideStructuralMutation(claimedBankAreaExists && legacyManagementAccess);
    }
}
