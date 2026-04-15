package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HandheldTerminalSavePayload(
        String terminalId,
        String shopName,
        String priceDollars,
        String merchantAccountId
) implements CustomPacketPayload {

    public static final Type<HandheldTerminalSavePayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "handheld_terminal_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandheldTerminalSavePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.terminalId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shopName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.priceDollars());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantAccountId());
                    },
                    buf -> new HandheldTerminalSavePayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    @Override
    public Type<HandheldTerminalSavePayload> type() {
        return TYPE;
    }
}
