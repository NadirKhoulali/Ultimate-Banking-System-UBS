package net.austizz.ultimatebankingsystem.bank.owner.setup;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.BANK_ID;
import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.EMPLOYEE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingConsumerEntryPointTest {
    private static final UUID PLAYER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void assignmentGateAndReadinessOperationFollowGrantAndRevokeFromMetadata() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario = SafeStaffReadinessTestSupport.scenario();
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();

        assertTrue(assignmentSkips(metadata, scenario.readiness()));
        assertTrue(hasMissingReason(resolveVault(metadata, scenario), "SAFE_ACCESS_STAFF_MISSING"));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "grantSafeAccess", metadata, EMPLOYEE_ID));
        assertFalse(assignmentSkips(metadata, scenario.readiness()));
        assertFalse(hasMissingReason(resolveVault(metadata, scenario), "SAFE_ACCESS_STAFF_MISSING"));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "revokeSafeAccess", metadata, EMPLOYEE_ID));
        assertTrue(assignmentSkips(metadata, scenario.readiness()));
        assertTrue(hasMissingReason(resolveVault(metadata, scenario), "SAFE_ACCESS_STAFF_MISSING"));
    }

    @Test
    void openAuthorityReturnsVaultNotReadyAfterFinalGrantRevoke() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario = SafeStaffReadinessTestSupport.scenario();
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();
        OpenFixture open = new OpenFixture(metadata, scenario.readiness());

        assertDecision(open.authorize(), false, "VAULT_NOT_READY");

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "grantSafeAccess", metadata, EMPLOYEE_ID));
        assertDecision(open.authorize(), true, "NONE");

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "revokeSafeAccess", metadata, EMPLOYEE_ID));
        assertDecision(open.authorize(), false, "VAULT_NOT_READY");
    }

    private static boolean assignmentSkips(Object metadata, Object readiness) throws Exception {
        Method method = SafeStaffReadinessTestSupport.load("bank.safebox.SafetyDepositBoxService")
                .getMethod("shouldSkipVaultForAssignment",
                        SafeStaffReadinessTestSupport.minecraft("nbt.CompoundTag"),
                        SafeStaffReadinessTestSupport.load(
                                "bank.safebox.setup.SafeVaultReadinessResolver$RowReadiness"));
        return (Boolean) method.invoke(null, metadata, readiness);
    }

    private static Object resolveVault(
            Object metadata, SafeStaffReadinessTestSupport.Scenario scenario) throws Exception {
        Class<?> service = SafeStaffReadinessTestSupport.load("bank.safebox.SafetyDepositBoxService");
        Object operation = service.getMethod("safeDepositVaultReadinessOperation",
                        SafeStaffReadinessTestSupport.minecraft("server.MinecraftServer"),
                        SafeStaffReadinessTestSupport.minecraft("nbt.CompoundTag"))
                .invoke(null, null, metadata);
        Class<?> selectionType = SafeStaffReadinessTestSupport.load(
                "bank.safebox.setup.SafeVaultReadinessResolver$VaultSelection");
        Object selection = selectionType.getConstructor(
                        SafeStaffReadinessTestSupport.load("bank.safebox.setup.SafePremiseSnapshot"),
                        SafeStaffReadinessTestSupport.load("bank.safebox.setup.SafeAreaSnapshot"),
                        SafeStaffReadinessTestSupport.load("bank.safebox.setup.SafeVaultSnapshot"))
                .newInstance(scenario.premise(), scenario.area(), scenario.vault());
        return operation.getClass().getMethod("resolve", selectionType).invoke(operation, selection);
    }

    private static boolean hasMissingReason(Object readiness, String reason) throws Exception {
        Object summary = SafeStaffReadinessTestSupport.value(readiness, "summary");
        return SafeStaffReadinessTestSupport.listValue(summary, "missingReasons").stream()
                .map(Object::toString)
                .anyMatch(reason::equals);
    }

    private static void assertDecision(Object decision, boolean allowed, String denial) throws Exception {
        assertEquals(allowed, SafeStaffReadinessTestSupport.value(decision, "allowed"));
        assertEquals(denial, SafeStaffReadinessTestSupport.value(decision, "denial").toString());
    }

    private static final class OpenFixture {
        private final Object authority;
        private final Object request;

        private OpenFixture(Object metadata, Object readiness) throws Exception {
            Class<?> authorityType = SafeStaffReadinessTestSupport.load(
                    "bank.safebox.SafetyDepositBoxOpenAuthority");
            Class<?> portsType = SafeStaffReadinessTestSupport.load(
                    "bank.safebox.SafetyDepositBoxOpenAuthority$Ports");
            Class<?> targetType = SafeStaffReadinessTestSupport.load(
                    "bank.safebox.SafetyDepositBoxOpenAuthority$Target");
            Class<?> assignmentType = SafeStaffReadinessTestSupport.load(
                    "bank.safebox.SafetyDepositBoxOpenAuthority$Assignment");
            Object target = targetType.getConstructor(
                            String.class, int.class, int.class, int.class, int.class)
                    .newInstance("minecraft:overworld", 4, 64, 4, 0);
            Object assignment = assignmentType.getConstructor(
                            UUID.class, UUID.class, targetType, String.class, boolean.class)
                    .newInstance(BANK_ID, ACCOUNT_ID, target, "A-1", false);
            Object ports = Proxy.newProxyInstance(portsType.getClassLoader(), new Class<?>[]{portsType},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "findExactAssignment" -> Optional.of(assignment);
                        case "accountAuthorized" -> true;
                        case "vaultReady" -> !assignmentSkips(metadata, readiness);
                        case "escortAccess" -> enumValue(
                                "bank.safebox.SafetyDepositBoxOpenAuthority$EscortAccess", "ALLOWED");
                        default -> null;
                    });
            authority = authorityType.getConstructor(portsType).newInstance(ports);
            request = SafeStaffReadinessTestSupport.load(
                            "bank.safebox.SafetyDepositBoxOpenAuthority$Request")
                    .getConstructor(UUID.class, UUID.class, targetType)
                    .newInstance(PLAYER_ID, ACCOUNT_ID, target);
        }

        private Object authorize() throws Exception {
            return authority.getClass().getMethod("authorize", request.getClass())
                    .invoke(authority, request);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(String relativeName, String value) throws Exception {
        Class<?> type = SafeStaffReadinessTestSupport.load(relativeName);
        return Enum.valueOf((Class<? extends Enum>) type, value);
    }
}
