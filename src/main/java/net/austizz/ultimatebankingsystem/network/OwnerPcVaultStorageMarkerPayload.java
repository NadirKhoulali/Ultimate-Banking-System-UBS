package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record OwnerPcVaultStorageMarkerPayload(
        String markerId,
        String kind,
        String label,
        String locationSummary,
        int x,
        int y,
        int z,
        int unitCount,
        int itemCount,
        boolean valueKnown,
        long valueCents,
        List<OwnerPcVaultStorageContentPayload> contents
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultStorageMarkerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.markerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.kind());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.label());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.locationSummary());
                        buf.writeInt(value.x());
                        buf.writeInt(value.y());
                        buf.writeInt(value.z());
                        buf.writeVarInt(value.unitCount());
                        buf.writeVarInt(value.itemCount());
                        buf.writeBoolean(value.valueKnown());
                        buf.writeLong(value.valueCents());
                        OwnerPcVaultStorageContentPayload.STREAM_CODEC.apply(ByteBufCodecs.list(64))
                                .encode(buf, value.contents());
                    },
                    buf -> new OwnerPcVaultStorageMarkerPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readLong(),
                            OwnerPcVaultStorageContentPayload.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf)
                    )
            );

    public OwnerPcVaultStorageMarkerPayload {
        markerId = safe(markerId);
        kind = safe(kind);
        label = safe(label);
        locationSummary = safe(locationSummary);
        unitCount = Math.max(1, unitCount);
        itemCount = Math.max(0, itemCount);
        valueCents = Math.max(0L, valueCents);
        contents = contents == null ? List.of() : List.copyOf(contents);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
