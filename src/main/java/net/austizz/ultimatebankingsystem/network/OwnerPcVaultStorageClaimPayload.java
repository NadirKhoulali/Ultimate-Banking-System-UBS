package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record OwnerPcVaultStorageClaimPayload(
        String claimId,
        String premiseId,
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean fullyLoaded,
        int omittedMarkers,
        List<OwnerPcVaultStorageMarkerPayload> markers
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultStorageClaimPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.claimId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.premiseId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.dimension());
                        buf.writeInt(value.minX());
                        buf.writeInt(value.minY());
                        buf.writeInt(value.minZ());
                        buf.writeInt(value.maxX());
                        buf.writeInt(value.maxY());
                        buf.writeInt(value.maxZ());
                        buf.writeBoolean(value.fullyLoaded());
                        buf.writeVarInt(value.omittedMarkers());
                        OwnerPcVaultStorageMarkerPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256))
                                .encode(buf, value.markers());
                    },
                    buf -> new OwnerPcVaultStorageClaimPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            OwnerPcVaultStorageMarkerPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf)
                    )
            );

    public OwnerPcVaultStorageClaimPayload {
        claimId = safe(claimId);
        premiseId = safe(premiseId);
        dimension = safe(dimension);
        int cleanMinX = Math.min(minX, maxX);
        int cleanMinY = Math.min(minY, maxY);
        int cleanMinZ = Math.min(minZ, maxZ);
        int cleanMaxX = Math.max(minX, maxX);
        int cleanMaxY = Math.max(minY, maxY);
        int cleanMaxZ = Math.max(minZ, maxZ);
        minX = cleanMinX;
        minY = cleanMinY;
        minZ = cleanMinZ;
        maxX = cleanMaxX;
        maxY = cleanMaxY;
        maxZ = cleanMaxZ;
        omittedMarkers = Math.max(0, omittedMarkers);
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
