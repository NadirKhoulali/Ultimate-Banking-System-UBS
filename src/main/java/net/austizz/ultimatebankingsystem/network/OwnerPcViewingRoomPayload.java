package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record OwnerPcViewingRoomPayload(String roomId,
                                        String name,
                                        String premiseId,
                                        String dimension,
                                        String bounds,
                                        String status,
                                        List<String> reasons,
                                        String customerAnchor,
                                        String tellerAnchor,
                                        String displayAnchor) {
    private static final int MAX_REASONS = 12;

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcViewingRoomPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.roomId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.premiseId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimension());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bounds());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.status());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_REASONS))
                                .encode(buf, payload.reasons());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.customerAnchor());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.tellerAnchor());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.displayAnchor());
                    },
                    buf -> new OwnerPcViewingRoomPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_REASONS)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    public OwnerPcViewingRoomPayload {
        roomId = safe(roomId);
        name = safe(name);
        premiseId = safe(premiseId);
        dimension = safe(dimension);
        bounds = safe(bounds);
        status = safe(status);
        reasons = reasons == null ? List.of() : List.copyOf(reasons.stream().limit(MAX_REASONS).toList());
        customerAnchor = safe(customerAnchor);
        tellerAnchor = safe(tellerAnchor);
        displayAnchor = safe(displayAnchor);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
