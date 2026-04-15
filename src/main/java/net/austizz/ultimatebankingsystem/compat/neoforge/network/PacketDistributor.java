package net.austizz.ultimatebankingsystem.compat.neoforge.network;

import net.austizz.ultimatebankingsystem.network.ForgePayloadNetwork;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ForgePayloadNetwork.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgePayloadNetwork.sendToPlayer(player, payload);
    }
}
