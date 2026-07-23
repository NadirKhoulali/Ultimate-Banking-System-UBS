package net.austizz.ultimatebankingsystem.bank.owner.setup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.EMPLOYEE_ID;
import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.STAFFING_LABEL;
import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.STAFFING_REASON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedStaffReadinessConsumerTest {
    @Test
    void premiseConsumerStopsReportingReadyAfterLastSafeAccessRevoke() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario = SafeStaffReadinessTestSupport.scenario();
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        assertEquals("READY", SafeStaffReadinessTestSupport.value(
                SafeStaffReadinessTestSupport.premiseProjection(scenario, metadata), "status").toString());

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("revokeSafeAccess", metadata, EMPLOYEE_ID));

        assertEquals("NOT_READY", SafeStaffReadinessTestSupport.value(
                SafeStaffReadinessTestSupport.premiseProjection(scenario, metadata), "status").toString(),
                "the premise consumer must not retain raw READY after the last Safe Access revoke");
    }

    @Test
    void sharedProjectionPreservesOtherReasonsOrderingLabelsAndReferences() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario =
                SafeStaffReadinessTestSupport.scenario("VAULT_DOOR_MISSING", "TELLER_ROUTE_MISSING");
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();

        Object blocked = SafeStaffReadinessTestSupport.project(metadata, scenario.readiness());
        Object blockedSummary = SafeStaffReadinessTestSupport.value(blocked, "summary");
        assertEquals(List.of("VAULT_DOOR_MISSING", "TELLER_ROUTE_MISSING", STAFFING_REASON),
                SafeStaffReadinessTestSupport.listValue(blockedSummary, "missingReasons").stream()
                        .map(Object::toString).toList());
        assertEquals(List.of(
                        "Complete BANK_VAULT_DOOR multiblock is missing.",
                        "Bank-bound teller outbound and return routes are missing.",
                        STAFFING_LABEL),
                SafeStaffReadinessTestSupport.listValue(blocked, "humanMissingReasons"));
        assertSame(scenario.premise(), SafeStaffReadinessTestSupport.value(blocked, "premise"));
        assertSame(scenario.area(), SafeStaffReadinessTestSupport.value(blocked, "safeArea"));
        assertSame(scenario.vault(), SafeStaffReadinessTestSupport.value(blocked, "vault"));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        Object granted = SafeStaffReadinessTestSupport.project(metadata, blocked);
        Object grantedSummary = SafeStaffReadinessTestSupport.value(granted, "summary");
        assertEquals(List.of("VAULT_DOOR_MISSING", "TELLER_ROUTE_MISSING"),
                SafeStaffReadinessTestSupport.listValue(grantedSummary, "missingReasons").stream()
                        .map(Object::toString).toList());
        assertEquals(EMPLOYEE_ID + "=STAFF:125.50",
                SafeStaffReadinessTestSupport.getString(metadata, "employees"));
    }

    @Test
    void publicSetupBuilderAndServicePublishTheSameTypedObjectiveReasons() throws Exception {
        Object metadata = SafeStaffReadinessTestSupport.setupMetadataWithEmployee();

        assertPublicReasons(metadata, List.of(
                "VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING", STAFFING_REASON));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        assertPublicReasons(metadata, List.of(
                "VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING"));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("revokeSafeAccess", metadata, EMPLOYEE_ID));
        assertPublicReasons(metadata, List.of(
                "VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING", STAFFING_REASON));
        assertEquals(EMPLOYEE_ID + "=STAFF:125.50",
                SafeStaffReadinessTestSupport.getString(metadata, "employees"));
    }

    private static void assertPublicReasons(Object metadata, List<String> expectedReasons) throws Exception {
        Object serviceRow = SafeStaffReadinessTestSupport.publicServiceReadiness(metadata).getFirst();
        Object serviceSummary = SafeStaffReadinessTestSupport.value(serviceRow, "summary");
        assertEquals(expectedReasons, SafeStaffReadinessTestSupport.listValue(serviceSummary, "missingReasons")
                .stream().map(Object::toString).toList());

        Object setup = SafeStaffReadinessTestSupport.publicSetupProjection(metadata);
        Object vault = SafeStaffReadinessTestSupport.listValue(setup, "vaults").getFirst();
        assertEquals(expectedReasons, SafeStaffReadinessTestSupport.listValue(vault, "missingReasons"));
        assertEquals("premise-1", SafeStaffReadinessTestSupport.value(vault, "premiseId"));
        assertEquals("safe-area-1", SafeStaffReadinessTestSupport.value(vault, "safeAreaId"));
        assertEquals("vault-1", SafeStaffReadinessTestSupport.value(vault, "vaultId"));
        assertEquals(expectedReasons.contains(STAFFING_REASON),
                SafeStaffReadinessTestSupport.listValue(
                        SafeStaffReadinessTestSupport.value(setup, "objective"), "missingSteps")
                        .contains(STAFFING_LABEL));
    }
}
