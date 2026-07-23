package net.austizz.ultimatebankingsystem.npc.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidator;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxArea;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public final class TellerEscortNavigationService {
    private final TellerEscortNavigationCoordinator coordinator = new TellerEscortNavigationCoordinator();

    public synchronized TellerEscortStartResult start(MinecraftServer server,
                                                       UUID sessionId,
                                                       UUID tellerId,
                                                       UUID playerId,
                                                       SafeBoxArea premiseBounds,
                                                       SafeTellerRoute route) {
        if (sessionId == null || tellerId == null || playerId == null || premiseBounds == null || route == null
                || !SafeTellerRouteValidator.validate(route).valid()
                || !tellerId.toString().equalsIgnoreCase(route.tellerId())
                || !premiseBounds.dimension().equals(route.dimension())) {
            return invalid(sessionId, tellerId);
        }
        if (server == null) {
            return unavailable(sessionId, tellerId);
        }
        ServerLevel routeLevel = resolveLevel(server, route.dimension());
        if (routeLevel == null) {
            return unavailable(sessionId, tellerId);
        }
        MinecraftTellerEscortActor actor = new MinecraftTellerEscortActor(
                server, routeLevel, tellerId, playerId, premiseBounds);
        if (!actor.available()) {
            return unavailable(sessionId, tellerId);
        }
        return coordinator.start(sessionId, tellerId, route, actor);
    }

    public synchronized void tick(MinecraftServer server) {
        if (server != null) {
            coordinator.tick();
        }
    }

    public synchronized Optional<TellerEscortNavigationState> status(UUID sessionId) {
        return coordinator.status(sessionId);
    }

    public synchronized Optional<TellerEscortNavigationState> activeStatusForTeller(UUID tellerId) {
        return coordinator.statusForTeller(tellerId);
    }

    public synchronized boolean cancelSession(UUID sessionId) {
        return coordinator.cancelSession(sessionId);
    }

    public synchronized boolean cancelTeller(UUID tellerId) {
        return coordinator.cancelTeller(tellerId);
    }

    public synchronized boolean forget(UUID sessionId) {
        return coordinator.forget(sessionId);
    }

    public synchronized int activeCount() {
        return coordinator.activeCount();
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId == null ? "" : dimensionId.trim());
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
        return server.getLevel(key);
    }

    private static TellerEscortStartResult unavailable(UUID sessionId, UUID tellerId) {
        TellerEscortNavigationState state = new TellerEscortNavigationState(sessionId, tellerId,
                TellerEscortNavigationState.Status.FAILED,
                TellerEscortNavigationState.FailureReason.TELLER_UNAVAILABLE, 0);
        return new TellerEscortStartResult(TellerEscortStartResult.Status.TELLER_UNAVAILABLE, state);
    }

    private static TellerEscortStartResult invalid(UUID sessionId, UUID tellerId) {
        TellerEscortNavigationState state = new TellerEscortNavigationState(sessionId, tellerId,
                TellerEscortNavigationState.Status.FAILED,
                TellerEscortNavigationState.FailureReason.INVALID_ROUTE, 0);
        return new TellerEscortStartResult(TellerEscortStartResult.Status.INVALID_ROUTE, state);
    }
}
