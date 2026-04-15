package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record ShopTerminalOpenPayload(
        String dimensionId,
        int x,
        int y,
        int z,
        String shopName,
        long priceDollars,
        String ownerName,
        String merchantAccountId,
        boolean pulseOnSuccess,
        boolean pulseOnFailure,
        boolean pulseOnIdle,
        int successPulseTicks,
        int failurePulseTicks,
        int idlePulseStrength,
        long totalSalesDollars,
        List<ShopTerminalAccountSummary> accounts
) implements CustomPacketPayload {

    public static final Type<ShopTerminalOpenPayload> TYPE = new Type<>(
            new ResourceLocation(UltimateBankingSystem.MODID, "shop_terminal_open"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTerminalOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shopName());
                        LONG_CODEC.encode(buf, payload.priceDollars());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownerName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantAccountId());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnSuccess());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnFailure());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnIdle());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.successPulseTicks());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.failurePulseTicks());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.idlePulseStrength());
                        LONG_CODEC.encode(buf, payload.totalSalesDollars());
                        ShopTerminalAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, payload.accounts());
                    },
                    buf -> new ShopTerminalOpenPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            LONG_CODEC.decode(buf),
                            ShopTerminalAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf)
                    )
            );

    public UUID parseMerchantAccountId() {
        if (merchantAccountId == null || merchantAccountId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(merchantAccountId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public Type<ShopTerminalOpenPayload> type() {
        return TYPE;
    }
}
