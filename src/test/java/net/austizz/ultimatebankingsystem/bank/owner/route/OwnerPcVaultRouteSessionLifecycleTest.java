package net.austizz.ultimatebankingsystem.bank.owner.route;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteServiceHarness.*;
import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnerPcVaultRouteSessionLifecycleTest {
    private static final long TTL_MILLIS = 60_000L;
    private static final UUID NEXT_SESSION_ID = UUID.fromString(
            "80000000-0000-0000-0000-000000000008");
    private static final UUID OTHER_PLAYER = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");

    @Test
    void explicitCancelInvalidatesOnlyTheOwningPlayersSession() throws Exception {
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        request(ports, sessions, PLAYER_ID, request());

        cancel(sessions, OTHER_PLAYER, cancel(SESSION_ID));
        assertEquals(1, storeSize(sessions));

        cancel(sessions, PLAYER_ID, cancel(SESSION_ID));
        assertEquals(0, storeSize(sessions));
        assertFalse(success(save(ports, sessions, PLAYER_ID, validSave(SESSION_ID))));
        assertEquals(0, ports.saveAuthorityCalls);
    }

    @Test
    void logoutCleanupRemovesPlayersOutstandingSession() throws Exception {
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        request(ports, sessions, PLAYER_ID, request());

        invalidatePlayer(sessions, PLAYER_ID);

        assertEquals(0, storeSize(sessions));
        assertFalse(success(save(ports, sessions, PLAYER_ID, validSave(SESSION_ID))));
        assertEquals(0, ports.saveAuthorityCalls);
    }

    @Test
    void serverStopCleanupClearsAllOutstandingSessions() throws Exception {
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        request(ports, sessions, PLAYER_ID, request());
        assertEquals(1, storeSize(sessions));

        clear(sessions);

        assertEquals(0, storeSize(sessions));
        assertFalse(success(save(ports, sessions, PLAYER_ID, validSave(SESSION_ID))));
    }

    @Test
    void openingAReplacementSessionMakesThePreviousTokenStale() throws Exception {
        Deque<UUID> tokens = new ArrayDeque<>();
        tokens.add(SESSION_ID);
        tokens.add(NEXT_SESSION_ID);
        Object sessions = store(new AtomicLong(1_000L), tokens::removeFirst, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        assertEquals(SESSION_ID,
                sessionId(request(ports, sessions, PLAYER_ID, request())));

        assertEquals(NEXT_SESSION_ID,
                sessionId(request(ports, sessions, PLAYER_ID, request())));

        assertFalse(success(save(ports, sessions, PLAYER_ID, validSave(SESSION_ID))));
        assertEquals(1, storeSize(sessions));
        assertTrue(success(save(ports, sessions, PLAYER_ID, validSave(NEXT_SESSION_ID))));
    }
}
