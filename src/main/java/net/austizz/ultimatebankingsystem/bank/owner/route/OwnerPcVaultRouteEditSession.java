package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteRequestPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;

import java.util.Objects;
import java.util.UUID;

record OwnerPcVaultRouteEditSession(UUID id,
                                    UUID playerId,
                                    Identity identity,
                                    Origin origin,
                                    long expiresAtMillis) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    OwnerPcVaultRouteEditSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(origin, "origin");
        if (ZERO_UUID.equals(id)) {
            throw new IllegalArgumentException("route edit session id must not be zero");
        }
        if (expiresAtMillis <= 0L) {
            throw new IllegalArgumentException("route edit session expiry must be positive");
        }
    }

    record Identity(UUID bankId,
                    String vaultId,
                    UUID tellerId,
                    SafeTellerRouteDirection direction) {
        Identity {
            Objects.requireNonNull(bankId, "bankId");
            Objects.requireNonNull(tellerId, "tellerId");
            Objects.requireNonNull(direction, "direction");
            vaultId = Objects.requireNonNull(vaultId, "vaultId").trim();
            if (vaultId.isEmpty()) {
                throw new IllegalArgumentException("vaultId is required");
            }
        }

        static Identity from(OwnerPcVaultRouteRequestPayload payload) {
            return new Identity(payload.bankId(), payload.vaultId(), payload.tellerId(),
                    payload.direction());
        }

        static Identity from(OwnerPcVaultRouteSavePayload payload) {
            return new Identity(payload.bankId(), payload.vaultId(), payload.tellerId(),
                    payload.direction());
        }
    }

    record Origin(String computerId, String dimension, int x, int y, int z) {
        Origin {
            computerId = Objects.requireNonNull(computerId, "computerId").trim();
            dimension = Objects.requireNonNull(dimension, "dimension").trim();
            if (computerId.isEmpty() || dimension.isEmpty()) {
                throw new IllegalArgumentException("route edit session origin is incomplete");
            }
        }
    }
}
