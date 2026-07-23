package net.austizz.ultimatebankingsystem.bank.safebox.escort.events;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortSession;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.SafeBoxZoneCache;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.SafeBoxZoneIndex;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.SafeBoxZonePolicy;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class SafeBoxEscortEvents {
    private static final long MESSAGE_COOLDOWN_TICKS = 40L;
    private static final Map<MinecraftServer, Map<UUID, Long>> LAST_MESSAGE = new WeakHashMap<>();

    private SafeBoxEscortEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long tick = server.getTickCount();
        enforceZone(server, player, Optional.empty(), tick);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            MinecraftServer server = player.getServer();
            forget(server, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            MinecraftServer server = player.getServer();
            forget(server, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            MinecraftServer server = player.getServer();
            forget(server, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        SafeBoxZoneCache.clear(server);
        synchronized (LAST_MESSAGE) {
            LAST_MESSAGE.remove(server);
        }
    }

    private static void enforceZone(MinecraftServer server,
                                    ServerPlayer player,
                                    Optional<SafeBoxEscortSession> escort,
                                    long tick) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        SafeBoxZoneIndex index = SafeBoxZoneCache.index(server, centralBank, tick);
        String dimension = player.level().dimension().location().toString();
        var pos = player.blockPosition();
        SafeBoxZonePolicy.Decision decision = SafeBoxZonePolicy.decide(
                index, dimension, pos.getX(), pos.getY(), pos.getZ(),
                bankId -> SafetyDepositBoxService.canAccessProtectedSafeArea(centralBank, player, bankId),
                escort);
        if (decision.allowed()) {
            return;
        }
        Optional<SafeExitSnapshot> exit = decision.exit();
        if (exit.isPresent()) {
            ServerLevel level = level(server, exit.get().dimension());
            if (level != null) {
                SafeExitSnapshot target = exit.get();
                player.teleportTo(level, target.x() + 0.5D, target.y(), target.z() + 0.5D,
                        target.yaw(), player.getXRot());
            }
        }
        if (claimMessage(server, player.getUUID(), tick)) {
            player.sendSystemMessage(Component.literal(exit.isPresent()
                    ? "You are not authorized to enter this bank safe area."
                    : "Bank safe-area access denied; no safe exit is available. Contact an operator."));
        }
    }

    private static boolean claimMessage(MinecraftServer server, UUID playerId, long tick) {
        synchronized (LAST_MESSAGE) {
            Map<UUID, Long> byPlayer = LAST_MESSAGE.computeIfAbsent(server, ignored -> new HashMap<>());
            Long previous = byPlayer.get(playerId);
            if (previous != null && tick >= previous && tick - previous < MESSAGE_COOLDOWN_TICKS) {
                return false;
            }
            byPlayer.put(playerId, tick);
            return true;
        }
    }

    private static void forget(MinecraftServer server, UUID playerId) {
        synchronized (LAST_MESSAGE) {
            Map<UUID, Long> byPlayer = LAST_MESSAGE.get(server);
            if (byPlayer != null) {
                byPlayer.remove(playerId);
            }
        }
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
        return server.getLevel(key);
    }
}
