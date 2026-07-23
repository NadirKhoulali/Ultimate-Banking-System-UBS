package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record OwnerPcVaultRoutePosition(int x, int y, int z) {
    public static final OwnerPcVaultRoutePosition ZERO = new OwnerPcVaultRoutePosition(0, 0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultRoutePosition> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeVarInt(value.x());
                        buf.writeVarInt(value.y());
                        buf.writeVarInt(value.z());
                    },
                    buf -> new OwnerPcVaultRoutePosition(
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
            );
}
