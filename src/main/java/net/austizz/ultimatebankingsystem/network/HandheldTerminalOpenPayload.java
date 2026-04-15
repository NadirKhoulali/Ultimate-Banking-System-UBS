package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record HandheldTerminalOpenPayload(
        String terminalId,
        String shopName,
        long priceDollars,
        String ownerName,
        String merchantAccountId,
        long totalSalesDollars,
        List<ShopTerminalAccountSummary> accounts
) implements CustomPacketPayload {

    public static final Type<HandheldTerminalOpenPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "handheld_terminal_open"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, HandheldTerminalOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.terminalId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shopName());
                        LONG_CODEC.encode(buf, payload.priceDollars());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownerName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantAccountId());
                        LONG_CODEC.encode(buf, payload.totalSalesDollars());
                        ShopTerminalAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, payload.accounts());
                    },
                    buf -> new HandheldTerminalOpenPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            ShopTerminalAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf)
                    )
            );

    public UUID parseTerminalId() {
        if (terminalId == null || terminalId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(terminalId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public UUID parseMerchantAccountId() {
        if (merchantAccountId == null || merchantAccountId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(merchantAccountId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public Type<HandheldTerminalOpenPayload> type() {
        return TYPE;
    }
}
