package net.austizz.ultimatebankingsystem.compat.neoforge.network.registration;

import net.austizz.ultimatebankingsystem.network.ForgePayloadNetwork;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

public final class PayloadRegistrar {
    private final String version;

    public PayloadRegistrar(String version) {
        this.version = version == null || version.isBlank() ? "1" : version;
    }

    public String version() {
        return version;
    }

    public <T extends CustomPacketPayload> void playToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler
    ) {
        ForgePayloadNetwork.register(type, codec, handler, ForgePayloadNetwork.Direction.PLAY_TO_SERVER);
    }

    public <T extends CustomPacketPayload> void playToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler
    ) {
        ForgePayloadNetwork.register(type, codec, handler, ForgePayloadNetwork.Direction.PLAY_TO_CLIENT);
    }
}
