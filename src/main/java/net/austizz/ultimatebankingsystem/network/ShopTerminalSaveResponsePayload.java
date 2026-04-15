package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopTerminalSaveResponsePayload(
        boolean success,
        String message,
        String merchantAccountId,
        String shopName,
        long priceDollars,
        boolean pulseOnSuccess,
        boolean pulseOnFailure,
        boolean pulseOnIdle,
        int successPulseTicks,
        int failurePulseTicks,
        int idlePulseStrength,
        long totalSalesDollars
) implements CustomPacketPayload {

    public static final Type<ShopTerminalSaveResponsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "shop_terminal_save_response"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTerminalSaveResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.success());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantAccountId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shopName());
                        LONG_CODEC.encode(buf, payload.priceDollars());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnSuccess());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnFailure());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnIdle());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.successPulseTicks());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.failurePulseTicks());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.idlePulseStrength());
                        LONG_CODEC.encode(buf, payload.totalSalesDollars());
                    },
                    buf -> new ShopTerminalSaveResponsePayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            LONG_CODEC.decode(buf)
                    )
            );

    @Override
    public Type<ShopTerminalSaveResponsePayload> type() {
        return TYPE;
    }
}
