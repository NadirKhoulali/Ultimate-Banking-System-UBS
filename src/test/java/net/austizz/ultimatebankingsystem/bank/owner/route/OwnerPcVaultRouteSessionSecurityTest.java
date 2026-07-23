package net.austizz.ultimatebankingsystem.bank.owner.route;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteMetadataFixture.ambiguousMetadata;
import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteServiceHarness.*;
import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnerPcVaultRouteSessionSecurityTest {
    private static final long TTL_MILLIS = 60_000L;
    private static final UUID OTHER_PLAYER = UUID.fromString(
            "50000000-0000-0000-0000-000000000005");
    private static final UUID OTHER_BANK = UUID.fromString(
            "60000000-0000-0000-0000-000000000006");
    private static final UUID OTHER_TELLER = UUID.fromString(
            "70000000-0000-0000-0000-000000000007");

    @Test
    void exactBankTellerVaultDirectionAndPlayerMismatchInvalidateSession() throws Exception {
        List<Mismatch> mismatches = List.of(
                new Mismatch(OTHER_BANK, VAULT_ID, TELLER_ID, "OUTBOUND", PLAYER_ID),
                new Mismatch(BANK_ID, VAULT_ID, OTHER_TELLER, "OUTBOUND", PLAYER_ID),
                new Mismatch(BANK_ID, "vault-other", TELLER_ID, "OUTBOUND", PLAYER_ID),
                new Mismatch(BANK_ID, VAULT_ID, TELLER_ID, "RETURN", PLAYER_ID),
                new Mismatch(BANK_ID, VAULT_ID, TELLER_ID, "OUTBOUND", OTHER_PLAYER));

        for (Mismatch mismatch : mismatches) {
            Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
            Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
            assertTrue(success(request(ports, sessions, PLAYER_ID, request())));
            Object altered = saveFor(SESSION_ID, mismatch.bankId(), mismatch.vaultId(),
                    mismatch.tellerId(), mismatch.direction());

            Object result = save(ports, sessions, mismatch.playerId(), altered);

            assertFalse(success(result), mismatch.toString());
            assertEquals(0, storeSize(sessions), mismatch.toString());
            assertEquals(0, ports.saveAuthorityCalls, mismatch.toString());
            assertEquals(0, ports.commits, mismatch.toString());
        }
    }

    @Test
    void expiredSessionIsRemovedBeforeFreshAuthorityLookup() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        Object sessions = store(clock, () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        request(ports, sessions, PLAYER_ID, request());
        clock.set(61_000L);

        Object result = save(ports, sessions, PLAYER_ID, validSave(SESSION_ID));

        assertFalse(success(result));
        assertEquals(0, storeSize(sessions));
        assertEquals(0, ports.saveAuthorityCalls);
        assertTrue(((String) value(value(result, "editor"), "message")).contains("expired"));
    }

    @Test
    void sessionExpiringDuringWorldValidationCannotCommit() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        Object sessions = store(clock, () -> SESSION_ID, TTL_MILLIS);
        World expiringWorld = new World(DIMENSION, true, 0, 256,
                -100, 100, -100, 100, ignored -> {
                    clock.set(61_000L);
                    return true;
                });
        Ports ports = new Ports(OwnerPcVaultRouteMetadataFixture.metadata(), Facts.liveRequest(),
                Facts.validSave(), expiringWorld);
        request(ports, sessions, PLAYER_ID, request());

        Object result = save(ports, sessions, PLAYER_ID, validSave(SESSION_ID));

        assertFalse(success(result));
        assertEquals(0, ports.commits);
        assertEquals(0, storeSize(sessions));
    }

    @Test
    void rememberedWrongOrRemovedPcCannotStartSessionButLivePcCan() throws Exception {
        Facts live = Facts.liveRequest();
        List<Facts> invalidContexts = List.of(
                live.withActivePc(false, false),
                live.withActivePc(false, true),
                live.withActivePc(true, false));

        for (Facts invalid : invalidContexts) {
            Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
            Ports ports = new Ports(invalid, Facts.validSave());
            assertFalse(success(request(ports, sessions, PLAYER_ID, request())));
            assertEquals(0, storeSize(sessions));
        }

        Object liveSessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports livePorts = new Ports(live, Facts.validSave());
        Object opened = request(livePorts, liveSessions, PLAYER_ID, request());
        assertTrue(success(opened));
        assertEquals(SESSION_ID, sessionId(opened));
    }

    @Test
    void saveFreshlyRevalidatesOwnerBankTellerAndVaultAuthority() throws Exception {
        Facts valid = Facts.validSave();
        List<Facts> revoked = List.of(
                valid.withAuthority(false, false),
                valid.withBankExists(false),
                valid.withTeller(false, false, false, false),
                valid.withTeller(true, false, true, false),
                valid.withTeller(true, true, false, false),
                valid.withTeller(true, true, true, true));

        for (Facts revokedFacts : revoked) {
            Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
            Ports ports = new Ports(Facts.liveRequest(), revokedFacts);
            request(ports, sessions, PLAYER_ID, request());

            Object result = save(ports, sessions, PLAYER_ID, validSave(SESSION_ID));

            assertFalse(success(result));
            assertEquals(0, storeSize(sessions));
            assertEquals(0, ports.worldCalls);
            assertEquals(0, ports.commits);
        }

        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports removedVault = new Ports(Facts.liveRequest(), valid);
        request(removedVault, sessions, PLAYER_ID, request());
        removedVault.metadata = ambiguousMetadata();
        assertFalse(success(save(removedVault, sessions, PLAYER_ID, validSave(SESSION_ID))));
        assertEquals(0, storeSize(sessions));

        Object operatorSessions = store(
                new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports operator = new Ports(
                Facts.liveRequest().withAuthority(false, true),
                valid.withAuthority(false, true));
        assertTrue(success(request(operator, operatorSessions, PLAYER_ID, request())));
        assertTrue(success(save(
                operator, operatorSessions, PLAYER_ID, validSave(SESSION_ID))));
    }

    @Test
    void routeDimensionAndWorldValidationRetainOnlyTheMatchingLiveSession() throws Exception {
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        request(ports, sessions, PLAYER_ID, request());
        Object wrongDimension = save(SESSION_ID, "OUTBOUND", "minecraft:the_nether",
                position(10, 64, 10), position(20, 64, 20), List.of(walk(11, 64, 10)));

        Object result = save(ports, sessions, PLAYER_ID, wrongDimension);

        assertFalse(success(result));
        assertEquals(SESSION_ID, sessionId(result));
        assertEquals(1, storeSize(sessions));
        assertEquals(0, ports.commits);
    }

    @Test
    void malformedPersistenceStateInvalidatesInsteadOfRetainingSession() throws Exception {
        Object malformed = OwnerPcVaultRouteMetadataFixture.metadata();
        malformed.getClass().getMethod("putString", String.class, String.class)
                .invoke(malformed, "safeTellerRoutes", "malformed");
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(malformed, Facts.liveRequest(), Facts.validSave(), World.valid());
        assertTrue(success(request(ports, sessions, PLAYER_ID, request())));

        Object result = save(ports, sessions, PLAYER_ID, validSave(SESSION_ID));

        assertFalse(success(result));
        assertNull(sessionId(result));
        assertEquals(0, storeSize(sessions));
        assertEquals(0, ports.commits);
    }

    private record Mismatch(UUID bankId, String vaultId, UUID tellerId,
                            String direction, UUID playerId) {
    }

}
