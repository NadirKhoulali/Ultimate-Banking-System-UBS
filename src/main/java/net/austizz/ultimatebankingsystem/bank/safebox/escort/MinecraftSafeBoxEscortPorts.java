package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.austizz.ultimatebankingsystem.network.SafeBoxEscortMarkerPayload;
import net.austizz.ultimatebankingsystem.npc.escort.TellerEscortNavigationService;
import net.austizz.ultimatebankingsystem.npc.escort.TellerEscortNavigationState;
import net.austizz.ultimatebankingsystem.npc.escort.TellerEscortStartResult;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

final class MinecraftSafeBoxEscortPorts {
    private MinecraftSafeBoxEscortPorts() {
    }

    static final class Navigation implements SafeBoxEscortRuntimePorts.Navigation {
        private final MinecraftServer server;
        private final TellerEscortNavigationService service = new TellerEscortNavigationService();
        private final NavigationCleanup cleanup;

        Navigation(MinecraftServer server) {
            this(server, null);
        }

        Navigation(MinecraftServer server, NavigationCleanup cleanup) {
            this.server = server;
            this.cleanup = cleanup == null ? new NavigationCleanup() {
                @Override
                public boolean cancel(UUID sessionId) {
                    return service.cancelSession(sessionId);
                }

                @Override
                public boolean forget(UUID sessionId) {
                    return service.forget(sessionId);
                }
            } : cleanup;
        }

        @Override
        public SafeBoxEscortRuntimePorts.NavigationStart start(UUID sessionId,
                                                                UUID tellerId,
                                                                UUID playerId,
                                                                SafeBoxArea premiseBounds,
                                                                SafeTellerRoute route) {
            TellerEscortStartResult result = service.start(
                    server, sessionId, tellerId, playerId, premiseBounds, route);
            return switch (result.status()) {
                case STARTED -> SafeBoxEscortRuntimePorts.NavigationStart.STARTED;
                case BUSY, SESSION_EXISTS -> SafeBoxEscortRuntimePorts.NavigationStart.BUSY;
                case INVALID_ROUTE -> SafeBoxEscortRuntimePorts.NavigationStart.INVALID_ROUTE;
                case TELLER_UNAVAILABLE -> SafeBoxEscortRuntimePorts.NavigationStart.TELLER_UNAVAILABLE;
            };
        }

        @Override
        public SafeBoxEscortRuntimePorts.NavigationState state(UUID sessionId) {
            return service.status(sessionId).map(MinecraftSafeBoxEscortPorts::state)
                    .orElse(SafeBoxEscortRuntimePorts.NavigationState.MISSING);
        }

        @Override
        public void tick() {
            service.tick(server);
        }

        @Override
        public boolean cancel(UUID sessionId) {
            return cleanup.cancel(sessionId);
        }

        @Override
        public boolean forget(UUID sessionId) {
            return cleanup.forget(sessionId);
        }
    }

    interface NavigationCleanup {
        boolean cancel(UUID sessionId);

        boolean forget(UUID sessionId);
    }

    static final class Effects implements SafeBoxEscortRuntimePorts.Effects {
        private final MinecraftServer server;

        Effects(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public boolean freshlyAuthorized(SafeBoxEscortRuntimeContext context) {
            return SafeBoxEscortContextResolver.freshlyAuthorized(
                    server, BankManager.getCentralBank(server), context);
        }

        @Override
        public boolean acquireDoorHold(SafeBoxEscortRuntimeContext context) {
            BankVaultDoorBlockEntity door = door(context);
            return door != null && (door.addEscortHold(context.sessionId())
                    || door.hasEscortHold(context.sessionId()));
        }

        @Override
        public void releaseDoorHold(SafeBoxEscortRuntimeContext context) {
            BankVaultDoorBlockEntity door = door(context);
            if (door != null) {
                door.removeEscortHold(context.sessionId());
            }
        }

        @Override
        public void grantAccess(SafeBoxEscortRuntimeContext context) {
            // The runtime's exact inspection state is the ephemeral grant.
        }

        @Override
        public void revokeAccess(SafeBoxEscortRuntimeContext context) {
            SafetyDepositBoxService.revokeEscortAccess(context.playerId());
            ServerPlayer player = server.getPlayerList().getPlayer(context.playerId());
            if (player != null && player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
        }

        @Override
        public void showMarker(SafeBoxEscortRuntimeContext context) {
            ServerPlayer player = server.getPlayerList().getPlayer(context.playerId());
            if (player == null) {
                return;
            }
            SafeBoxEscortTarget target = context.target();
            PacketDistributor.sendToPlayer(player, new SafeBoxEscortMarkerPayload(
                    true, target.dimension(), target.rowPosition().x(), target.rowPosition().y(),
                    target.rowPosition().z(), target.doorIndex(), context.label()));
        }

        @Override
        public void clearMarker(SafeBoxEscortRuntimeContext context) {
            ServerPlayer player = server.getPlayerList().getPlayer(context.playerId());
            if (player != null) {
                PacketDistributor.sendToPlayer(player, SafeBoxEscortMarkerPayload.inactive());
            }
        }

        @Override
        public void eject(SafeBoxEscortRuntimeContext context) {
            ServerPlayer player = server.getPlayerList().getPlayer(context.playerId());
            ServerLevel exitLevel = level(server, context.premiseExit().dimension());
            if (player != null && exitLevel != null) {
                SafeBoxEscortRuntimeContext.Exit exit = context.premiseExit();
                player.teleportTo(exitLevel, exit.x() + 0.5D, exit.y(), exit.z() + 0.5D,
                        exit.yaw(), player.getXRot());
            }
        }

        @Override
        public void navigationFailed(SafeBoxEscortRuntimeContext context, boolean returning) {
            ServerPlayer player = server.getPlayerList().getPlayer(context.playerId());
            if (player != null) {
                player.sendSystemMessage(Component.literal(returning
                        ? "The bank teller could not complete the return route. Ask the bank owner to review it."
                        : "The bank teller could not reach the vault. Ask the bank owner to review the configured walk points."));
            }
        }

        private BankVaultDoorBlockEntity door(SafeBoxEscortRuntimeContext context) {
            ServerLevel level = level(server, context.target().dimension());
            EscortBlockPosition position = context.vaultDoorMaster();
            if (level == null) {
                return null;
            }
            BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
            return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof BankVaultDoorBlockEntity door
                    ? door : null;
        }
    }

    private static SafeBoxEscortRuntimePorts.NavigationState state(TellerEscortNavigationState state) {
        return switch (state.status()) {
            case RUNNING -> SafeBoxEscortRuntimePorts.NavigationState.RUNNING;
            case ARRIVED -> SafeBoxEscortRuntimePorts.NavigationState.ARRIVED;
            case FAILED -> SafeBoxEscortRuntimePorts.NavigationState.FAILED;
            case CANCELLED -> SafeBoxEscortRuntimePorts.NavigationState.CANCELLED;
        };
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension == null ? "" : dimension.trim());
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
        return server.getLevel(key);
    }
}
