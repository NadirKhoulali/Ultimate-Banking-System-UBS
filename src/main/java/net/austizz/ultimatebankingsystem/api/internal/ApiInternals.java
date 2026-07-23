package net.austizz.ultimatebankingsystem.api.internal;

import net.austizz.ultimatebankingsystem.api.ApiBlockBounds;
import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.Internal
public final class ApiInternals {
    private ApiInternals() {
    }

    public static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static CentralBank centralBank() {
        MinecraftServer server = server();
        return server == null ? null : BankManager.getCentralBank(server);
    }

    public static ServerPlayer onlinePlayer(UUID playerId) {
        MinecraftServer server = server();
        return server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    public static boolean canMutate(MinecraftServer server) {
        return server != null && server.isSameThread();
    }

    public static ApiBlockPosition position(String dimension, BlockPos pos) {
        return pos == null ? null : new ApiBlockPosition(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    public static ApiBlockPosition position(SafeExitSnapshot exit) {
        return exit == null ? null : new ApiBlockPosition(exit.dimension(), exit.x(), exit.y(), exit.z());
    }

    public static ApiBlockBounds bounds(SafeBlockBounds bounds) {
        return bounds == null ? null : new ApiBlockBounds(bounds.dimension(),
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }
}
