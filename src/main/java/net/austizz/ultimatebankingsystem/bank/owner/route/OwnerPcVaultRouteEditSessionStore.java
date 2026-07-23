package net.austizz.ultimatebankingsystem.bank.owner.route;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class OwnerPcVaultRouteEditSessionStore {
    static final long DEFAULT_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final int TOKEN_ATTEMPTS = 16;

    enum Status {
        VALID,
        MISSING,
        EXPIRED,
        IDENTITY_MISMATCH
    }

    record Check(Status status, OwnerPcVaultRouteEditSession session) {
        boolean valid() {
            return status == Status.VALID && session != null;
        }
    }

    private final Map<UUID, OwnerPcVaultRouteEditSession> sessions = new HashMap<>();
    private final LongSupplier clock;
    private final Supplier<UUID> tokenSource;
    private final long ttlMillis;

    OwnerPcVaultRouteEditSessionStore() {
        this(System::currentTimeMillis, UUID::randomUUID, DEFAULT_TTL_MILLIS);
    }

    OwnerPcVaultRouteEditSessionStore(LongSupplier clock,
                                      Supplier<UUID> tokenSource,
                                      long ttlMillis) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenSource = Objects.requireNonNull(tokenSource, "tokenSource");
        if (ttlMillis <= 0L || ttlMillis > DEFAULT_TTL_MILLIS) {
            throw new IllegalArgumentException("route edit session TTL is out of range");
        }
        this.ttlMillis = ttlMillis;
    }

    synchronized OwnerPcVaultRouteEditSession issue(
            UUID playerId,
            OwnerPcVaultRouteEditSession.Identity identity,
            OwnerPcVaultRouteEditSession.Origin origin) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(origin, "origin");
        long nowMillis = clock.getAsLong();
        removeExpired(nowMillis);
        invalidatePlayer(playerId);
        UUID token = nextToken();
        OwnerPcVaultRouteEditSession session = new OwnerPcVaultRouteEditSession(
                token, playerId, identity, origin, nowMillis + ttlMillis);
        sessions.put(token, session);
        return session;
    }

    synchronized Check check(UUID token,
                             UUID playerId,
                             OwnerPcVaultRouteEditSession.Identity identity) {
        if (token == null || ZERO_UUID.equals(token)) {
            return new Check(Status.MISSING, null);
        }
        OwnerPcVaultRouteEditSession session = sessions.get(token);
        if (session == null) {
            return new Check(Status.MISSING, null);
        }
        if (clock.getAsLong() >= session.expiresAtMillis()) {
            sessions.remove(token);
            return new Check(Status.EXPIRED, null);
        }
        if (!session.playerId().equals(playerId) || !session.identity().equals(identity)) {
            sessions.remove(token);
            return new Check(Status.IDENTITY_MISMATCH, null);
        }
        return new Check(Status.VALID, session);
    }

    synchronized boolean consumeIfValid(
            UUID token,
            UUID playerId,
            OwnerPcVaultRouteEditSession.Identity identity) {
        Check check = check(token, playerId, identity);
        if (!check.valid()) {
            return false;
        }
        sessions.remove(token);
        return true;
    }

    synchronized void invalidate(UUID token, UUID playerId) {
        OwnerPcVaultRouteEditSession session = sessions.get(token);
        if (session != null && session.playerId().equals(playerId)) {
            sessions.remove(token);
        }
    }

    synchronized void invalidatePlayer(UUID playerId) {
        sessions.values().removeIf(session -> session.playerId().equals(playerId));
    }

    synchronized void clear() {
        sessions.clear();
    }

    synchronized int size() {
        return sessions.size();
    }

    private UUID nextToken() {
        for (int attempt = 0; attempt < TOKEN_ATTEMPTS; attempt++) {
            UUID token = tokenSource.get();
            if (token != null && !ZERO_UUID.equals(token) && !sessions.containsKey(token)) {
                return token;
            }
        }
        throw new IllegalStateException("unable to allocate a route edit session id");
    }

    private void removeExpired(long nowMillis) {
        sessions.values().removeIf(session -> nowMillis >= session.expiresAtMillis());
    }
}
