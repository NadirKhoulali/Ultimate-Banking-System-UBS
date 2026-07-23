package net.austizz.ultimatebankingsystem.bank.owner.route;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteServiceHarness.*;
import static net.austizz.ultimatebankingsystem.bank.owner.route.OwnerPcVaultRouteTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnerPcVaultRouteServiceIntegrationTest {
    private static final long TTL_MILLIS = 60_000L;

    @Test
    void livePcRequestThenMatchingSaveOutsidePcRadiusSucceeds() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        Object sessions = store(clock, () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        Object draft = validSave(SESSION_ID);

        Object opened = request(ports, sessions, PLAYER_ID, request());

        assertTrue(success(opened));
        assertEquals(SESSION_ID, sessionId(opened));
        assertEquals(BANK_ID, ports.requestBankId);
        assertEquals(TELLER_ID, ports.requestTellerId);
        assertEquals(61_000L, value(value(opened, "editor"), "sessionExpiresAtMillis"));
        Object origin = sessionOrigin(sessions, SESSION_ID, PLAYER_ID, draft);
        assertEquals("owner-pc-test", value(origin, "computerId"));
        assertEquals(DIMENSION, value(origin, "dimension"));
        assertEquals(4, value(origin, "x"));
        assertEquals(64, value(origin, "y"));
        assertEquals(4, value(origin, "z"));

        Object saved = save(ports, sessions, PLAYER_ID, draft);

        assertTrue(success(saved));
        assertTrue((Boolean) value(saved, "persisted"));
        assertEquals(1, ports.commits);
        assertEquals(1, ports.saveAuthorityCalls);
        assertEquals(BANK_ID, ports.saveBankId);
        assertEquals(TELLER_ID, ports.saveTellerId);
        assertEquals(1, ports.worldCalls);
        assertEquals(0, storeSize(sessions));
        List<?> routes = readRoutes(ports.metadata);
        assertEquals(1, routes.size());
        Object persisted = routes.getFirst();
        assertEquals(value(draft, "bankId").toString(), value(persisted, "bankId"));
        assertEquals(value(draft, "vaultId"), value(persisted, "vaultId"));
        assertEquals(value(draft, "tellerId").toString(), value(persisted, "tellerId"));
        assertEquals(value(draft, "direction"), value(persisted, "direction"));
        assertEquals(value(draft, "dimension"), value(persisted, "dimension"));
    }

    @Test
    void saveWithoutServerOwnedSessionIsDeniedBeforeAuthorityOrWorldLookup() throws Exception {
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports forged = new Ports(Facts.liveRequest(), Facts.validSave());

        Object saved = save(forged, sessions, PLAYER_ID, validSave(SESSION_ID));

        assertFalse(success(saved));
        assertFalse((Boolean) value(saved, "persisted"));
        assertEquals(0, forged.saveAuthorityCalls);
        assertEquals(0, forged.worldCalls);
        assertEquals(0, forged.commits);
    }

    @Test
    void successfulSaveConsumesSessionAndReplayIsDenied() throws Exception {
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        assertTrue(success(request(ports, sessions, PLAYER_ID, request())));

        assertTrue(success(save(ports, sessions, PLAYER_ID, validSave(SESSION_ID))));
        Object replay = save(ports, sessions, PLAYER_ID, validSave(SESSION_ID));

        assertFalse(success(replay));
        assertEquals(1, ports.commits);
        assertEquals(1, ports.saveAuthorityCalls,
                "replay must be rejected before fresh authority lookup");
    }

    @Test
    void correctableDraftFailureRetainsSessionForRetry() throws Exception {
        Object sessions = store(new AtomicLong(1_000L), () -> SESSION_ID, TTL_MILLIS);
        Ports ports = new Ports(Facts.liveRequest(), Facts.validSave());
        request(ports, sessions, PLAYER_ID, request());
        Object invalid = save(SESSION_ID, "OUTBOUND", DIMENSION,
                position(0, 64, 0), position(20, 64, 20), List.of(walk(11, 64, 10)));

        Object rejected = save(ports, sessions, PLAYER_ID, invalid);

        assertFalse(success(rejected));
        assertEquals(SESSION_ID, sessionId(rejected));
        assertEquals(1, storeSize(sessions));
        assertEquals(0, ports.commits);

        Object corrected = save(ports, sessions, PLAYER_ID, validSave(SESSION_ID));
        assertTrue(success(corrected));
        assertEquals(1, ports.commits);
        assertEquals(0, storeSize(sessions));
    }

    private static List<?> readRoutes(Object metadata) throws Exception {
        return (List<?>) safeRoute("SafeTellerRouteNbtStore")
                .getMethod("readAll", Class.forName(
                        "net.minecraft.nbt.CompoundTag", true,
                        net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader.get()))
                .invoke(null, metadata);
    }
}
