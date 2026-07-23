package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class OwnerPcVaultRouteEditSessions {
    private static final Map<MinecraftServer, OwnerPcVaultRouteEditSessionStore> STORES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private OwnerPcVaultRouteEditSessions() {
    }

    static OwnerPcVaultRouteEditSessionStore forServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        synchronized (STORES) {
            return STORES.computeIfAbsent(server, ignored ->
                    new OwnerPcVaultRouteEditSessionStore());
        }
    }

    static void invalidatePlayer(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return;
        }
        OwnerPcVaultRouteEditSessionStore store = STORES.get(server);
        if (store != null) {
            store.invalidatePlayer(playerId);
        }
    }

    static void clear(MinecraftServer server) {
        if (server == null) {
            return;
        }
        OwnerPcVaultRouteEditSessionStore store = STORES.remove(server);
        if (store != null) {
            store.clear();
        }
    }
}
