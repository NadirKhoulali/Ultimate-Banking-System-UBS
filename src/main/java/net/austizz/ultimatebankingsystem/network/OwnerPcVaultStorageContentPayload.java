package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record OwnerPcVaultStorageContentPayload(
        String itemId,
        String displayName,
        int count,
        boolean valueKnown,
        long unitValueCents,
        long totalValueCents,
        String valueSource,
        int marketSampleCount,
        long marketAverageCents
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultStorageContentPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.itemId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.displayName());
                        buf.writeVarInt(value.count());
                        buf.writeBoolean(value.valueKnown());
                        buf.writeLong(value.unitValueCents());
                        buf.writeLong(value.totalValueCents());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.valueSource());
                        buf.writeVarInt(value.marketSampleCount());
                        buf.writeLong(value.marketAverageCents());
                    },
                    buf -> new OwnerPcVaultStorageContentPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readLong(),
                            buf.readLong(),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readVarInt(),
                            buf.readLong()
                    )
            );

    public OwnerPcVaultStorageContentPayload {
        itemId = itemId == null ? "" : itemId;
        displayName = displayName == null ? "" : displayName;
        count = Math.max(0, count);
        unitValueCents = Math.max(0L, unitValueCents);
        totalValueCents = Math.max(0L, totalValueCents);
        valueSource = valueSource == null ? "" : valueSource;
        marketSampleCount = Math.max(0, marketSampleCount);
        marketAverageCents = Math.max(0L, marketAverageCents);
    }
}
