package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxEscortRuntimeStateTest {
    @Test
    void failedMapRemovalDoesNotSkipPeersAndRetriesWithoutLosingSessionOwnership() {
        ThrowOnceRemoveMap players = new ThrowOnceRemoveMap();
        ThrowOnceRemoveMap tellers = new ThrowOnceRemoveMap();
        var sessions = new LinkedHashMap<UUID, SafeBoxEscortRuntimeState.Active>();
        SafeBoxEscortRuntimeState state = new SafeBoxEscortRuntimeState(players, tellers, sessions);
        SafeBoxEscortRuntimeContext context = context();
        SafeBoxEscortRuntimeState.Active active = state.reserve(context);
        players.failNextRemove = true;

        assertFalse(state.remove(active));

        assertTrue(state.busyPlayer(context.playerId()));
        assertFalse(state.busyTeller(context.tellerId()));
        assertSame(active, state.forSession(context.sessionId()));
        assertTrue(state.remove(active));
        assertFalse(state.busyPlayer(context.playerId()));
        assertNull(state.forSession(context.sessionId()));
    }

    private static SafeBoxEscortRuntimeContext context() {
        UUID bank = UUID.randomUUID();
        UUID teller = UUID.randomUUID();
        SafeBoxEscortTarget target = new SafeBoxEscortTarget(
                bank, "vault", UUID.randomUUID(), "minecraft:overworld",
                new EscortBlockPosition(12, 64, 14), 3, teller);
        return new SafeBoxEscortRuntimeContext(
                UUID.randomUUID(), UUID.randomUUID(), target,
                new SafeBoxArea("minecraft:overworld", 0, 50, 0, 40, 80, 40),
                new SafeBoxArea("minecraft:overworld", 10, 60, 10, 20, 70, 20),
                new SafeBoxEscortRuntimeContext.Exit("minecraft:overworld", -2, 64, -2, 0),
                new EscortBlockPosition(18, 64, 18),
                route(bank, teller, SafeTellerRouteDirection.OUTBOUND),
                route(bank, teller, SafeTellerRouteDirection.RETURN), "Box A-1");
    }

    private static SafeTellerRoute route(UUID bank, UUID teller,
                                         SafeTellerRouteDirection direction) {
        SafeTellerRoutePosition start = new SafeTellerRoutePosition(1, 64, 1);
        SafeTellerRoutePosition finish = new SafeTellerRoutePosition(18, 64, 18);
        return SafeTellerRoute.create(bank.toString(), "vault", teller.toString(), direction,
                "minecraft:overworld", start, finish,
                List.of(new SafeTellerRouteStep.Walk(finish)));
    }

    private static final class ThrowOnceRemoveMap
            extends LinkedHashMap<UUID, SafeBoxEscortRuntimeState.Active> {
        private boolean failNextRemove;

        @Override
        public boolean remove(Object key, Object value) {
            if (failNextRemove) {
                failNextRemove = false;
                throw new IllegalStateException("injected map removal failure");
            }
            return super.remove(key, value);
        }
    }
}
