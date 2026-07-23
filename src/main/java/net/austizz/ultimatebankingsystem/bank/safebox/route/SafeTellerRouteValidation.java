package net.austizz.ultimatebankingsystem.bank.safebox.route;

import java.util.List;

public record SafeTellerRouteValidation(boolean valid, List<Issue> issues) {
    public SafeTellerRouteValidation {
        issues = issues == null ? List.of() : List.copyOf(issues);
        valid = issues.isEmpty();
    }

    public enum Issue {
        ROUTE_MISSING,
        ROUTE_ID_BLANK,
        ROUTE_ID_NOT_STABLE,
        BANK_ID_BLANK,
        VAULT_ID_BLANK,
        TELLER_ID_BLANK,
        DIRECTION_MISSING,
        DIMENSION_BLANK,
        START_MISSING,
        FINISH_MISSING,
        STEPS_EMPTY,
        STEP_COUNT_EXCEEDED,
        STEP_MISSING,
        WALK_TARGET_MISSING,
        WAIT_DURATION_INVALID,
        REDSTONE_TARGET_MISSING,
        REDSTONE_FACE_INVALID,
        REDSTONE_STRENGTH_INVALID,
        REDSTONE_DURATION_INVALID,
        RFID_SCANNER_MISSING
    }
}
