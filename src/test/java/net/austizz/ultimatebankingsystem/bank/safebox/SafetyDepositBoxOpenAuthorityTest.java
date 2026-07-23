package net.austizz.ultimatebankingsystem.bank.safebox;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.Denial.ACCOUNT_DENIED;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.Denial.ASSIGNMENT_LOCKED;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.Denial.ASSIGNMENT_MISSING;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.Denial.NO_ACTIVE_ESCORT;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.Denial.VAULT_NOT_READY;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.Denial.WRONG_ESCORT;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.EscortAccess.ALLOWED;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxOpenAuthority.EscortAccess.DENIED_ACTIVE_ESCORT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyDepositBoxOpenAuthorityTest {
    private static final UUID PLAYER = id(1);
    private static final UUID BANK = id(2);
    private static final UUID ACCOUNT = id(3);
    private static final SafetyDepositBoxOpenAuthority.Target TARGET =
            new SafetyDepositBoxOpenAuthority.Target("minecraft:overworld", 12, 64, 14, 2);

    @Test
    void missingEscortFactFailsClosedInsteadOfThrowing() {
        FakePorts ports = readyPorts();
        ports.escortAccess = null;

        SafetyDepositBoxOpenAuthority.Decision decision = assertDoesNotThrow(() -> authorize(ports));

        assertDenied(decision, NO_ACTIVE_ESCORT);
    }

    @Test
    void authoritativeMetadataRemovalDeniesEvenWhenAllOtherFactsAllow() {
        FakePorts ports = readyPorts();
        ports.assignment = Optional.empty();

        SafetyDepositBoxOpenAuthority.Decision decision = authorize(ports);

        assertDenied(decision, ASSIGNMENT_MISSING);
    }

    @Test
    void accountPermissionCannotReplaceMissingEscort() {
        FakePorts ports = readyPorts();
        ports.escortAccess = SafetyDepositBoxOpenAuthority.EscortAccess.NO_ACTIVE_ESCORT;

        SafetyDepositBoxOpenAuthority.Decision decision = authorize(ports);

        assertDenied(decision, NO_ACTIVE_ESCORT);
    }

    @Test
    void wrongActiveEscortDeniesExactBox() {
        FakePorts ports = readyPorts();
        ports.escortAccess = DENIED_ACTIVE_ESCORT;

        assertDenied(authorize(ports), WRONG_ESCORT);
    }

    @Test
    void exactReadyUnlockedAuthorizedEscortedBoxIsAllowed() {
        SafetyDepositBoxOpenAuthority.Decision decision = authorize(readyPorts());

        assertTrue(decision.allowed());
        assertEquals(SafetyDepositBoxOpenAuthority.Denial.NONE, decision.denial());
        assertEquals(ACCOUNT, decision.assignment().accountId());
        assertEquals(TARGET, decision.assignment().target());
    }

    @Test
    void lockedAssignmentDeniesExactBox() {
        FakePorts ports = readyPorts();
        ports.assignment = Optional.of(assignment(true));

        assertDenied(authorize(ports), ASSIGNMENT_LOCKED);
    }

    @Test
    void notReadyVaultDeniesWhenAccountPermissionIsGranted() {
        FakePorts ports = readyPorts();
        ports.vaultReady = false;

        assertDenied(authorize(ports), VAULT_NOT_READY);
    }

    @Test
    void accountPermissionRevocationDeniesNextLiveMenuCheck() {
        FakePorts ports = readyPorts();
        assertTrue(authorize(ports).allowed());

        ports.accountAuthorized = false;

        assertDenied(authorize(ports), ACCOUNT_DENIED);
    }

    @Test
    void accountDenialPrecedesVaultReadinessDenial() {
        FakePorts ports = readyPorts();
        ports.accountAuthorized = false;
        ports.vaultReady = false;

        assertDenied(authorize(ports), ACCOUNT_DENIED);
    }

    @Test
    void mismatchedAssignmentReturnedByPortStillFailsClosed() {
        FakePorts ports = readyPorts();
        SafetyDepositBoxOpenAuthority.Target otherTarget =
                new SafetyDepositBoxOpenAuthority.Target("minecraft:overworld", 12, 64, 14, 3);
        ports.assignment = Optional.of(new SafetyDepositBoxOpenAuthority.Assignment(
                BANK, ACCOUNT, otherTarget, "SDB-0001", false));

        assertDenied(authorize(ports), ASSIGNMENT_MISSING);
    }

    private static SafetyDepositBoxOpenAuthority.Decision authorize(FakePorts ports) {
        return new SafetyDepositBoxOpenAuthority(ports).authorize(
                new SafetyDepositBoxOpenAuthority.Request(PLAYER, ACCOUNT, TARGET));
    }

    private static FakePorts readyPorts() {
        return new FakePorts(Optional.of(assignment(false)));
    }

    private static SafetyDepositBoxOpenAuthority.Assignment assignment(boolean locked) {
        return new SafetyDepositBoxOpenAuthority.Assignment(BANK, ACCOUNT, TARGET, "SDB-0001", locked);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static void assertDenied(SafetyDepositBoxOpenAuthority.Decision decision,
                                     SafetyDepositBoxOpenAuthority.Denial denial) {
        assertFalse(decision.allowed());
        assertEquals(denial, decision.denial());
    }

    private static final class FakePorts implements SafetyDepositBoxOpenAuthority.Ports {
        private Optional<SafetyDepositBoxOpenAuthority.Assignment> assignment;
        private boolean accountAuthorized = true;
        private boolean vaultReady = true;
        private SafetyDepositBoxOpenAuthority.EscortAccess escortAccess = ALLOWED;
        private FakePorts(Optional<SafetyDepositBoxOpenAuthority.Assignment> assignment) {
            this.assignment = assignment;
        }

        @Override
        public Optional<SafetyDepositBoxOpenAuthority.Assignment> findExactAssignment(
                SafetyDepositBoxOpenAuthority.Target target) {
            return assignment;
        }

        @Override
        public boolean accountAuthorized(UUID playerId, SafetyDepositBoxOpenAuthority.Assignment assignment) {
            return accountAuthorized;
        }

        @Override
        public boolean vaultReady(SafetyDepositBoxOpenAuthority.Assignment assignment) {
            return vaultReady;
        }

        @Override
        public SafetyDepositBoxOpenAuthority.EscortAccess escortAccess(
                UUID playerId, SafetyDepositBoxOpenAuthority.Assignment assignment) {
            return escortAccess;
        }
    }
}
