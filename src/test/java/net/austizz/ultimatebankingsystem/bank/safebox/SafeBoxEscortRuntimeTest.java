package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.escort.EscortBlockPosition;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntime;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortRuntimePorts;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.ACCOUNT;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.BANK;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.Call;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.PLAYER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.SESSION;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.TELLER;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.accessRequest;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.context;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.count;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.fixture;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.id;
import static net.austizz.ultimatebankingsystem.bank.safebox.SafeBoxEscortRuntimeTestSupport.target;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortRuntimeTest {
    @Test
    void administratorPremiseDeletionCancelsOnlyMatchingVaultEscorts() {
        var fixture = fixture();
        assertEquals(SafeBoxEscortRuntime.StartStatus.STARTED,
                fixture.runtime.start(context()).status());

        assertEquals(0, fixture.runtime.cancelVaults(Set.of("different-vault")));
        assertTrue(fixture.runtime.busyPlayer(PLAYER));
        assertEquals(1, fixture.runtime.cancelVaults(Set.of("vault-a")));
        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertFalse(fixture.runtime.busyTeller(TELLER));
        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isEmpty());
        assertEquals(0, fixture.runtime.cancelVaults(Set.of("vault-a")));
    }

    @Test
    void startsImmediatelyAndRejectsBusyPlayerOrTellerWithoutQueueing() {
        var fixture = fixture();

        assertEquals(SafeBoxEscortRuntime.StartStatus.STARTED, fixture.runtime.start(context()).status());
        assertEquals(List.of("start:OUTBOUND"), fixture.navigation.events);
        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isPresent());
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(SafeBoxEscortRuntime.StartStatus.PLAYER_BUSY,
                fixture.runtime.start(context(id(6), PLAYER, id(7))).status());
        assertEquals(SafeBoxEscortRuntime.StartStatus.TELLER_BUSY,
                fixture.runtime.start(context(id(8), id(9), TELLER)).status());
        assertEquals(1, fixture.faults.calls(Call.START));
    }

    @Test
    void freshDenialDoesNotGrantAndExactGrantReturnsOnlyAfterSafeExit() {
        var fixture = fixture();
        fixture.outboundArrived(100);
        fixture.effects.authorized = false;

        assertEquals(SafeBoxEscortRuntime.InteractionStatus.AUTHORIZATION_DENIED,
                fixture.runtime.handleTellerInteraction(PLAYER, TELLER, 101));
        assertEquals(SafeBoxEscortRuntime.AccessDecision.DENIED_ACTIVE_ESCORT,
                fixture.runtime.inspectAccess(accessRequest(PLAYER, target()), 101));

        fixture.effects.authorized = true;
        assertEquals(SafeBoxEscortRuntime.InteractionStatus.ACCESS_GRANTED,
                fixture.runtime.handleTellerInteraction(PLAYER, TELLER, 102));
        assertEquals(SafeBoxEscortRuntime.AccessDecision.ALLOWED,
                fixture.runtime.inspectAccess(accessRequest(PLAYER, target()), 102));
        assertEquals(SafeBoxEscortRuntime.AccessDecision.DENIED_ACTIVE_ESCORT,
                fixture.runtime.inspectAccess(accessRequest(PLAYER, new SafeBoxEscortTarget(
                        BANK, "vault-a", ACCOUNT, "minecraft:overworld",
                        new EscortBlockPosition(12, 64, 14), 4, TELLER)), 102));

        assertEquals(SafeBoxEscortRuntime.InteractionStatus.WAITING_FOR_EXIT,
                fixture.runtime.handleTellerInteraction(PLAYER, TELLER, 103));
        fixture.runtime.onPlayerPosition(PLAYER, "minecraft:overworld",
                new EscortBlockPosition(15, 64, 15));
        assertEquals(1, fixture.faults.calls(Call.START));
        fixture.runtime.onPlayerPosition(PLAYER, "minecraft:overworld",
                new EscortBlockPosition(30, 64, 30));
        assertEquals(2, fixture.faults.calls(Call.START));
        fixture.navigation.arrive(SESSION);
        fixture.runtime.tick(104);
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void timeoutEjectsAndClearsPlayerWhileTellerRemainsBusyUntilRecoveryArrives() {
        var fixture = fixture();
        fixture.inspectingAt(100);

        fixture.runtime.tick(6_100);

        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isEmpty());
        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertTrue(fixture.runtime.busyTeller(TELLER));
        assertEquals(2, fixture.faults.calls(Call.START));
        assertEquals(List.of("access-", "marker-", "hold-", "eject"),
                fixture.effects.events.subList(fixture.effects.events.size() - 4,
                        fixture.effects.events.size()));
        fixture.navigation.arrive(SESSION);
        fixture.runtime.tick(6_101);
        assertFalse(fixture.runtime.busyTeller(TELLER));
    }

    @Test
    void grantAndMarkerFailuresCompensateWithoutPhantomAccess() {
        for (Call failed : List.of(Call.GRANT, Call.SHOW)) {
            var fixture = fixture();
            fixture.outboundArrived(10);
            fixture.faults.once(failed);

            assertEquals(SafeBoxEscortRuntime.InteractionStatus.NOT_FOUND,
                    fixture.runtime.handleTellerInteraction(PLAYER, TELLER, 11));
            assertTrue(fixture.runtime.activeForPlayer(PLAYER).isEmpty());
            assertFalse(fixture.runtime.busyPlayer(PLAYER));
            assertTrue(fixture.runtime.busyTeller(TELLER));
            assertEquals(1, count(fixture.effects.events, "access-"));
            assertEquals(failed == Call.SHOW ? 1 : 0,
                    count(fixture.effects.events, "marker-"));
            assertEquals(1, count(fixture.effects.events, "hold-"));
            fixture.navigation.arrive(SESSION);
            fixture.runtime.tick(12);
            assertFalse(fixture.runtime.busyTeller(TELLER));
        }
    }

    @Test
    void failedReturnStartReleasesDomainMovementAndBusyIndexes() {
        var fixture = fixture();
        fixture.waitingAt(10);
        fixture.navigation.nextStart = SafeBoxEscortRuntimePorts.NavigationStart.INVALID_ROUTE;

        fixture.runtime.onPlayerPosition(PLAYER, "minecraft:overworld",
                new EscortBlockPosition(30, 64, 30));

        assertTrue(fixture.runtime.activeForPlayer(PLAYER).isEmpty());
        assertFalse(fixture.runtime.busyPlayer(PLAYER));
        assertFalse(fixture.runtime.busyTeller(TELLER));
        assertEquals(1, count(fixture.effects.events, "access-"));
        assertEquals(1, count(fixture.effects.events, "marker-"));
        assertEquals(1, count(fixture.effects.events, "hold-"));
    }
}
