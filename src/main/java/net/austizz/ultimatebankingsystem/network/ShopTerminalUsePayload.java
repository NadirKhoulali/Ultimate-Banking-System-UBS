package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopTerminalUsePayload(
        String dimensionId,
        int x,
        int y,
        int z,
        boolean configureAction
) implements CustomPacketPayload {

    public static final Type<ShopTerminalUsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "shop_terminal_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTerminalUsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.BOOL.encode(buf, payload.configureAction());
                    },
                    buf -> new ShopTerminalUsePayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    @Override
    public Type<ShopTerminalUsePayload> type() {
        return TYPE;
    }
}
