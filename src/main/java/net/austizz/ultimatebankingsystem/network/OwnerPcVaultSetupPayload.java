package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record OwnerPcVaultSetupPayload(String premiseId,
                                       String vaultId,
                                       String mode,
                                       String premiseBounds,
                                       boolean exitValid,
                                       String safeAreaId,
                                       String safeAreaBounds,
                                       String status,
                                       boolean ready,
                                       List<String> missingReasons,
                                       List<String> missingReasonLabels,
                                       String doorStatus,
                                       String rowStatus,
                                       String viewingRoomStatus) {
    private static final int MAX_REASON_COUNT = 16;

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultSetupPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.premiseId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.vaultId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.mode());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.premiseBounds());
                        ByteBufCodecs.BOOL.encode(buf, payload.exitValid());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeAreaId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeAreaBounds());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.status());
                        ByteBufCodecs.BOOL.encode(buf, payload.ready());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_REASON_COUNT))
                                .encode(buf, payload.missingReasons());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_REASON_COUNT))
                                .encode(buf, payload.missingReasonLabels());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.doorStatus());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.rowStatus());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.viewingRoomStatus());
                    },
                    buf -> new OwnerPcVaultSetupPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_REASON_COUNT)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_REASON_COUNT)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    public OwnerPcVaultSetupPayload {
        premiseId = safe(premiseId);
        vaultId = safe(vaultId);
        mode = safe(mode);
        premiseBounds = safe(premiseBounds);
        safeAreaId = safe(safeAreaId);
        safeAreaBounds = safe(safeAreaBounds);
        status = safe(status);
        missingReasons = copy(missingReasons);
        missingReasonLabels = copy(missingReasonLabels);
        doorStatus = safe(doorStatus);
        rowStatus = safe(rowStatus);
        viewingRoomStatus = safe(viewingRoomStatus);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values.stream().limit(MAX_REASON_COUNT).toList());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
