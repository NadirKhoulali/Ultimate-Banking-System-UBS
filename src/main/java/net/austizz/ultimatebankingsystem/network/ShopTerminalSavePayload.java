package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopTerminalSavePayload(
        String dimensionId,
        int x,
        int y,
        int z,
        String shopName,
        String priceDollars,
        String merchantAccountId,
        boolean pulseOnSuccess,
        boolean pulseOnFailure,
        boolean pulseOnIdle,
        String successPulseTicks,
        String failurePulseTicks,
        String idlePulseStrength
) implements CustomPacketPayload {

    public static final Type<ShopTerminalSavePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "shop_terminal_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTerminalSavePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.shopName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.priceDollars());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.merchantAccountId());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnSuccess());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnFailure());
                        ByteBufCodecs.BOOL.encode(buf, payload.pulseOnIdle());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.successPulseTicks());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.failurePulseTicks());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.idlePulseStrength());
                    },
                    buf -> new ShopTerminalSavePayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    @Override
    public Type<ShopTerminalSavePayload> type() {
        return TYPE;
    }
}
