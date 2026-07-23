package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.claim.ClaimOutline;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ClaimOutlineSummary(String dimensionId,
                                  String type,
                                  String ownerName,
                                  int minX,
                                  int minY,
                                  int minZ,
                                  int maxX,
                                  int maxY,
                                  int maxZ) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimOutlineSummary> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.dimensionId(), 96);
                buf.writeUtf(value.type(), 48);
                buf.writeUtf(value.ownerName(), 72);
                buf.writeInt(value.minX());
                buf.writeInt(value.minY());
                buf.writeInt(value.minZ());
                buf.writeInt(value.maxX());
                buf.writeInt(value.maxY());
                buf.writeInt(value.maxZ());
            },
            buf -> new ClaimOutlineSummary(
                    buf.readUtf(96), buf.readUtf(48), buf.readUtf(72),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt())
    );

    public ClaimOutlineSummary {
        dimensionId = trim(dimensionId, 96);
        type = trim(type, 48);
        ownerName = trim(ownerName, 72);
    }

    public static ClaimOutlineSummary from(ClaimOutline outline) {
        return new ClaimOutlineSummary(outline.dimensionId(), outline.type(), outline.ownerName(),
                outline.minX(), outline.minY(), outline.minZ(),
                outline.maxX(), outline.maxY(), outline.maxZ());
    }

    private static String trim(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
