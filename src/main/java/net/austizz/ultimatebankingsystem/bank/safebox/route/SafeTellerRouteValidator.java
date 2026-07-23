package net.austizz.ultimatebankingsystem.bank.safebox.route;

import java.util.ArrayList;
import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidation.Issue;

public final class SafeTellerRouteValidator {
    public static final int MAX_STEPS = 256;
    public static final int MAX_WAIT_TICKS = 20 * 60;
    public static final int MAX_REDSTONE_DURATION_TICKS = 20 * 60;

    private SafeTellerRouteValidator() {
    }

    public static SafeTellerRouteValidation validate(SafeTellerRoute route) {
        List<Issue> issues = new ArrayList<>();
        if (route == null) {
            return new SafeTellerRouteValidation(false, List.of(Issue.ROUTE_MISSING));
        }
        requireText(route.id(), Issue.ROUTE_ID_BLANK, issues);
        requireText(route.bankId(), Issue.BANK_ID_BLANK, issues);
        requireText(route.vaultId(), Issue.VAULT_ID_BLANK, issues);
        requireText(route.tellerId(), Issue.TELLER_ID_BLANK, issues);
        requireText(route.dimension(), Issue.DIMENSION_BLANK, issues);
        if (route.direction() == null) {
            issues.add(Issue.DIRECTION_MISSING);
        }
        if (route.start() == null) {
            issues.add(Issue.START_MISSING);
        }
        if (route.finish() == null) {
            issues.add(Issue.FINISH_MISSING);
        }
        String stableId = SafeTellerRoute.stableId(route.bankId(), route.vaultId(), route.tellerId(),
                route.direction());
        if (!route.id().isBlank() && !stableId.isBlank() && !route.id().equals(stableId)) {
            issues.add(Issue.ROUTE_ID_NOT_STABLE);
        }
        validateSteps(route.steps(), issues);
        return new SafeTellerRouteValidation(issues.isEmpty(), issues);
    }

    private static void validateSteps(List<SafeTellerRouteStep> steps, List<Issue> issues) {
        if (steps.isEmpty()) {
            issues.add(Issue.STEPS_EMPTY);
            return;
        }
        if (steps.size() > MAX_STEPS) {
            issues.add(Issue.STEP_COUNT_EXCEEDED);
            return;
        }
        for (SafeTellerRouteStep step : steps) {
            issues.addAll(validateStep(step));
        }
    }

    private static List<Issue> validateStep(SafeTellerRouteStep step) {
        return switch (step) {
            case null -> List.of(Issue.STEP_MISSING);
            case SafeTellerRouteStep.Walk walk -> walk.target() == null
                    ? List.of(Issue.WALK_TARGET_MISSING)
                    : List.of();
            case SafeTellerRouteStep.Wait wait ->
                    wait.durationTicks() < 1 || wait.durationTicks() > MAX_WAIT_TICKS
                            ? List.of(Issue.WAIT_DURATION_INVALID)
                            : List.of();
            case SafeTellerRouteStep.Redstone redstone -> validateRedstone(redstone);
            case SafeTellerRouteStep.Rfid rfid -> rfid.scanner() == null
                    ? List.of(Issue.RFID_SCANNER_MISSING)
                    : List.of();
        };
    }

    private static List<Issue> validateRedstone(SafeTellerRouteStep.Redstone redstone) {
        List<Issue> issues = new ArrayList<>();
        if (redstone.target() == null) {
            issues.add(Issue.REDSTONE_TARGET_MISSING);
        }
        if (redstone.face() == null) {
            issues.add(Issue.REDSTONE_FACE_INVALID);
        }
        if (redstone.strength() < 1 || redstone.strength() > 15) {
            issues.add(Issue.REDSTONE_STRENGTH_INVALID);
        }
        if (redstone.durationTicks() < 1 || redstone.durationTicks() > MAX_REDSTONE_DURATION_TICKS) {
            issues.add(Issue.REDSTONE_DURATION_INVALID);
        }
        return issues;
    }

    private static void requireText(String value, Issue issue, List<Issue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(issue);
        }
    }
}
