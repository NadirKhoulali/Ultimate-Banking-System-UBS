package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HandheldTerminalSaveResponsePayload(
        boolean success,
        String message,
        String terminalId,
        String merchantAccountId,
        String shopName,
        long priceDollars,
        long totalSalesDollars
) implements CustomPacketPayload {

    public static final Type<HandheldTerminalSaveResponsePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "handheld_terminal_save_response"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, HandheldTerminalSaveResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.success());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.terminalId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantAccountId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shopName());
                        LONG_CODEC.encode(buf, payload.priceDollars());
                        LONG_CODEC.encode(buf, payload.totalSalesDollars());
                    },
                    buf -> new HandheldTerminalSaveResponsePayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf)
                    )
            );

    @Override
    public Type<HandheldTerminalSaveResponsePayload> type() {
        return TYPE;
    }
}
