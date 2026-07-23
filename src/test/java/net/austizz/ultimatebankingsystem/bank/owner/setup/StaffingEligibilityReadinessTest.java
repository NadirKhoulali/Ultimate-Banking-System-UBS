package net.austizz.ultimatebankingsystem.bank.owner.setup;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.EMPLOYEE_ID;
import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.STAFFING_REASON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingEligibilityReadinessTest {
    private static final UUID OTHER_EMPLOYEE_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID MALFORMED_SALARY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID NEGATIVE_SALARY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000004");

    @Test
    void noGrantGrantAndRevokeDriveTheAuthoritativeProjection() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario = SafeStaffReadinessTestSupport.scenario();
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();

        assertFalse(SafeStaffReadinessTestSupport.hasEligibleStaff(metadata));
        assertBlocked(scenario, metadata);

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        assertTrue(SafeStaffReadinessTestSupport.hasEligibleStaff(metadata));
        assertReady(scenario, metadata);

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("revokeSafeAccess", metadata, EMPLOYEE_ID));
        assertFalse(SafeStaffReadinessTestSupport.hasEligibleStaff(metadata));
        assertBlocked(scenario, metadata);
    }

    @Test
    void orphanRoleAndNonemployeeFlagsNeverCountAndOrphansCanBeCleaned() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario = SafeStaffReadinessTestSupport.scenario();
        Object nonemployee = SafeStaffReadinessTestSupport.emptyMetadata();
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "grantSafeAccess", nonemployee, EMPLOYEE_ID));
        assertBlocked(scenario, nonemployee);

        Object orphan = SafeStaffReadinessTestSupport.metadataWithEmployee();
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", orphan, EMPLOYEE_ID));
        SafeStaffReadinessTestSupport.putString(orphan, "employees", "");
        SafeStaffReadinessTestSupport.putString(orphan, "roles", EMPLOYEE_ID + "=DIRECTOR");

        assertFalse(SafeStaffReadinessTestSupport.hasEligibleStaff(orphan));
        assertBlocked(scenario, orphan);
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "removeEmployee", orphan, EMPLOYEE_ID), "orphan cleanup must not invent employment");
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasExplicitSafeAccess", orphan, EMPLOYEE_ID));
    }

    @Test
    void legacyAndMalformedRosterSegmentsSurviveMutationWithoutGrantingEligibility() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario = SafeStaffReadinessTestSupport.scenario();
        Object metadata = SafeStaffReadinessTestSupport.emptyMetadata();
        String retainedLegacy = "  " + EMPLOYEE_ID + "=staff:125.500  ";
        String opaqueLegacy = "legacy-v0-record";
        String malformedUuid = "not-a-uuid=STAFF:75";
        String malformedSalary = MALFORMED_SALARY_ID + "=TELLER:not-a-number";
        String negativeSalary = NEGATIVE_SALARY_ID + "=TELLER:-1";
        String retained = String.join(";",
                retainedLegacy, opaqueLegacy, malformedUuid, malformedSalary, negativeSalary);
        SafeStaffReadinessTestSupport.putString(
                metadata, "employees", retained + ";" + OTHER_EMPLOYEE_ID + "=TELLER:90");

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("hasEmployee", metadata, EMPLOYEE_ID));
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasEmployee", metadata, MALFORMED_SALARY_ID));
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasEmployee", metadata, NEGATIVE_SALARY_ID));
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "removeEmployee", metadata, OTHER_EMPLOYEE_ID));
        assertEquals(retained, SafeStaffReadinessTestSupport.getString(metadata, "employees"));

        Object reloaded = SafeStaffReadinessTestSupport.copy(metadata);
        assertEquals(retained, SafeStaffReadinessTestSupport.getString(reloaded, "employees"));
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasEmployee", reloaded, MALFORMED_SALARY_ID));
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasEmployee", reloaded, NEGATIVE_SALARY_ID));
        assertTrue(SafeStaffReadinessTestSupport.hasEligibleStaff(reloaded));
        assertReady(scenario, reloaded);
    }

    private static void assertReady(SafeStaffReadinessTestSupport.Scenario scenario, Object metadata)
            throws Exception {
        Object projected = SafeStaffReadinessTestSupport.project(metadata, scenario.readiness());
        assertEquals(true, SafeStaffReadinessTestSupport.value(
                SafeStaffReadinessTestSupport.value(projected, "summary"), "ready"));
    }

    private static void assertBlocked(SafeStaffReadinessTestSupport.Scenario scenario, Object metadata)
            throws Exception {
        Object projected = SafeStaffReadinessTestSupport.project(metadata, scenario.readiness());
        Object summary = SafeStaffReadinessTestSupport.value(projected, "summary");
        assertEquals(false, SafeStaffReadinessTestSupport.value(summary, "ready"));
        assertEquals(List.of(STAFFING_REASON), SafeStaffReadinessTestSupport.listValue(summary, "missingReasons")
                .stream().map(Object::toString).toList());
    }
}
