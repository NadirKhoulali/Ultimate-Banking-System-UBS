package net.austizz.ultimatebankingsystem.bank.safebox.route;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidation.Issue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeTellerRouteValidatorExhaustivenessTest {
    private static final SafeTellerRoutePosition START = new SafeTellerRoutePosition(0, 64, 0);
    private static final SafeTellerRoutePosition FINISH = new SafeTellerRoutePosition(4, 64, 7);

    @Test
    void everyCurrentSealedStepVariantValidates() {
        SafeTellerRouteValidation result = SafeTellerRouteValidator.validate(route(List.of(
                new SafeTellerRouteStep.Walk(FINISH),
                new SafeTellerRouteStep.Wait(1),
                new SafeTellerRouteStep.Redstone(FINISH, SafeTellerRouteFace.NORTH, 9, 1),
                new SafeTellerRouteStep.Rfid(FINISH))));

        assertTrue(result.valid(), () -> "current variants rejected: " + result.issues());
    }

    @Test
    void nullAndMalformedCurrentVariantsFailClosed() {
        assertEquals(List.of(Issue.ROUTE_MISSING), SafeTellerRouteValidator.validate(null).issues());
        assertIssue(Collections.singletonList(null), Issue.STEP_MISSING);
        assertIssue(List.of(new SafeTellerRouteStep.Walk(null)), Issue.WALK_TARGET_MISSING);
        assertIssue(List.of(new SafeTellerRouteStep.Wait(0)), Issue.WAIT_DURATION_INVALID);
        assertIssue(List.of(new SafeTellerRouteStep.Redstone(null, null, 0, 0)),
                Issue.REDSTONE_TARGET_MISSING);
        assertIssue(List.of(new SafeTellerRouteStep.Rfid(null)), Issue.RFID_SCANNER_MISSING);
    }

    private static void assertIssue(List<SafeTellerRouteStep> steps, Issue expected) {
        SafeTellerRouteValidation result = SafeTellerRouteValidator.validate(route(steps));
        assertFalse(result.valid());
        assertTrue(result.issues().contains(expected), () -> "missing " + expected + ": " + result.issues());
    }

    private static SafeTellerRoute route(List<SafeTellerRouteStep> steps) {
        return SafeTellerRoute.create("bank", "vault", "teller",
                SafeTellerRouteDirection.OUTBOUND, "minecraft:overworld", START, FINISH, steps);
    }
}
