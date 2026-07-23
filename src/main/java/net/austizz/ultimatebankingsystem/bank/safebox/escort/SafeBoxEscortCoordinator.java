package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.network.BankTellerSafeBoxState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SafeBoxEscortCoordinator {
    private static final Map<MinecraftServer, SafeBoxEscortRuntime> RUNTIMES = new IdentityHashMap<>();

    private SafeBoxEscortCoordinator() {
    }

    public static synchronized StartResult start(StartRequest request) {
        MinecraftServer server = request == null ? null : request.server();
        CentralBank centralBank = request == null ? null : request.centralBank();
        ServerPlayer player = request == null ? null : request.player();
        BankTellerEntity teller = request == null ? null : request.teller();
        BankTellerSafeBoxState.AccountAssignment assignment = request == null ? null : request.assignment();
        if (server == null || player == null || teller == null) {
            return StartResult.failure(StartFailure.INVALID, "Safe-deposit escort service is unavailable.");
        }
        SafeBoxEscortRuntime runtime = runtime(server);
        if (runtime.busyPlayer(player.getUUID())) {
            return StartResult.failure(StartFailure.PLAYER_BUSY,
                    "You already have an active safety-box escort. No queue was created.");
        }
        if (runtime.busyTeller(teller.getUUID())) {
            return StartResult.failure(StartFailure.TELLER_BUSY,
                    "This safe-deposit teller is busy. No queue was created.");
        }
        SafeBoxEscortContextResolver.Resolution resolution = SafeBoxEscortContextResolver.resolve(
                new SafeBoxEscortContextResolver.CheckoutRequest(
                        server, centralBank, player, teller, assignment));
        if (!resolution.success()) {
            return StartResult.failure(StartFailure.INVALID_SETUP, resolution.message());
        }
        SafeBoxEscortRuntime.StartResult started = runtime.start(resolution.context());
        return switch (started.status()) {
            case STARTED -> StartResult.started(started.sessionId(),
                    "Your teller escort has started. Follow the teller to your safety deposit box.");
            case PLAYER_BUSY -> StartResult.failure(StartFailure.PLAYER_BUSY,
                    "You already have an active safety-box escort. No queue was created.");
            case TELLER_BUSY -> StartResult.failure(StartFailure.TELLER_BUSY,
                    "This safe-deposit teller is busy. No queue was created.");
            case INVALID_ROUTE -> StartResult.failure(StartFailure.INVALID_SETUP,
                    "The selected safety-box route is invalid.");
            case TELLER_UNAVAILABLE -> StartResult.failure(StartFailure.TELLER_UNAVAILABLE,
                    "The selected teller cannot start this escort.");
            case INVALID -> StartResult.failure(StartFailure.INVALID,
                    "Safe-deposit escort service is unavailable.");
        };
    }

    public static synchronized SafeBoxEscortRuntime.InteractionStatus handleTellerInteraction(
            InteractionRequest request) {
        if (request == null) {
            return SafeBoxEscortRuntime.InteractionStatus.NOT_FOUND;
        }
        SafeBoxEscortRuntime runtime = RUNTIMES.get(request.server());
        return runtime == null ? SafeBoxEscortRuntime.InteractionStatus.NOT_FOUND
                : runtime.handleTellerInteraction(request.playerId(), request.tellerId(), request.serverTick());
    }

    public static synchronized SafeBoxEscortRuntime.InteractionStatus handleTellerInteraction(
            MinecraftServer server, UUID playerId, UUID tellerId) {
        return server == null
                ? SafeBoxEscortRuntime.InteractionStatus.NOT_FOUND
                : handleTellerInteraction(new InteractionRequest(
                        server, playerId, tellerId, server.getTickCount()));
    }

    public static synchronized void tick(MinecraftServer server) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        if (runtime != null) {
            runtime.tick(server.getTickCount());
        }
    }

    public static synchronized Optional<SafeBoxEscortSession> activeForPlayer(
            MinecraftServer server, UUID playerId) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        return runtime == null ? Optional.empty() : runtime.activeForPlayer(playerId);
    }

    public static synchronized Optional<SafeBoxEscortSession> activeForTeller(
            MinecraftServer server, UUID tellerId) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        return runtime == null ? Optional.empty() : runtime.activeForTeller(tellerId);
    }

    public static synchronized Set<String> activeVaultIds(MinecraftServer server) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        return runtime == null ? Set.of() : runtime.activeVaultIds();
    }

    public static synchronized int cancelVaults(MinecraftServer server, Set<String> vaultIds) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        return runtime == null ? 0 : runtime.cancelVaults(vaultIds);
    }

    public static synchronized boolean isPlayerBusy(MinecraftServer server, UUID playerId) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        return runtime != null && runtime.busyPlayer(playerId);
    }

    public static synchronized boolean isTellerBusy(MinecraftServer server, UUID tellerId) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        return runtime != null && runtime.busyTeller(tellerId);
    }

    public static synchronized AccessDecision inspectAccess(MinecraftServer server,
                                                            SafeBoxEscortAccessRequest request) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        if (runtime == null || request == null) {
            return AccessDecision.NO_ACTIVE_ESCORT;
        }
        SafeBoxEscortRuntime.AccessDecision decision = runtime.inspectAccess(request, server.getTickCount());
        return AccessDecision.valueOf(decision.name());
    }

    public static synchronized void onPlayerPosition(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        SafeBoxEscortRuntime runtime = RUNTIMES.get(player.getServer());
        if (runtime != null) {
            BlockPos pos = player.blockPosition();
            runtime.onPlayerPosition(player.getUUID(), player.level().dimension().location().toString(),
                    new EscortBlockPosition(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public static synchronized void onLogout(MinecraftServer server, UUID playerId) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        if (runtime != null) {
            runtime.onLogout(playerId);
        }
    }

    public static synchronized void onDeath(MinecraftServer server, UUID playerId) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        if (runtime != null) {
            runtime.onDeath(playerId);
        }
    }

    public static synchronized void onDimensionChange(MinecraftServer server, UUID playerId) {
        SafeBoxEscortRuntime runtime = RUNTIMES.get(server);
        if (runtime != null) {
            runtime.onDimensionChange(playerId);
        }
    }

    public static synchronized void stop(MinecraftServer server) {
        SafeBoxEscortRuntime runtime = RUNTIMES.remove(server);
        if (runtime != null) {
            runtime.stop();
        }
    }

    private static SafeBoxEscortRuntime runtime(MinecraftServer server) {
        return RUNTIMES.computeIfAbsent(server, key -> new SafeBoxEscortRuntime(
                new MinecraftSafeBoxEscortPorts.Navigation(key),
                new MinecraftSafeBoxEscortPorts.Effects(key)));
    }

    public enum AccessDecision { ALLOWED, DENIED_ACTIVE_ESCORT, NO_ACTIVE_ESCORT }
    public enum StartFailure { NONE, PLAYER_BUSY, TELLER_BUSY, TELLER_UNAVAILABLE, INVALID_SETUP, INVALID }

    public record StartRequest(MinecraftServer server,
                               CentralBank centralBank,
                               ServerPlayer player,
                               BankTellerEntity teller,
                               BankTellerSafeBoxState.AccountAssignment assignment) {
    }

    public record InteractionRequest(MinecraftServer server,
                                     UUID playerId,
                                     UUID tellerId,
                                     long serverTick) {
    }

    public record StartResult(boolean success, StartFailure failure, UUID sessionId, String message) {
        public StartResult {
            Objects.requireNonNull(failure, "failure");
            message = message == null ? "" : message;
            if (success && (failure != StartFailure.NONE || sessionId == null)) {
                throw new IllegalArgumentException("successful start requires a session and no failure");
            }
            if (!success && (failure == StartFailure.NONE || sessionId != null)) {
                throw new IllegalArgumentException("failed start requires a failure and no session");
            }
        }

        static StartResult started(UUID sessionId, String message) {
            return new StartResult(true, StartFailure.NONE, sessionId, message);
        }

        static StartResult failure(StartFailure failure, String message) {
            return new StartResult(false, failure, null, message == null ? "" : message);
        }
    }
}
