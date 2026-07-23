package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ClaimModeSnapshotPayload(boolean active,
                                       String sessionId,
                                       String kind,
                                       String title,
                                       String contextName,
                                       String ownerName,
                                       String dimensionId,
                                       boolean addMode,
                                       boolean outlinesVisible,
                                       boolean hasPos1,
                                       int pos1X,
                                       int pos1Y,
                                       int pos1Z,
                                       boolean hasPos2,
                                       int pos2X,
                                       int pos2Y,
                                       int pos2Z,
                                       boolean hasAnchor,
                                       double anchorX,
                                       double anchorY,
                                       double anchorZ,
                                       float anchorYaw,
                                       int pendingAdd,
                                       int pendingRemove,
                                       int remainingTicks,
                                       String statusMessage,
                                       boolean statusSuccess,
                                       boolean appliedSuccessfully,
                                       List<ClaimOutlineSummary> outlines) implements CustomPacketPayload {
    private static final int MAX_OUTLINES = 96;
    public static final Type<ClaimModeSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "claim_mode_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimModeSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            ClaimModeSnapshotPayload::encode,
            ClaimModeSnapshotPayload::decode
    );

    public ClaimModeSnapshotPayload {
        sessionId = trim(sessionId, 36);
        kind = trim(kind, 48);
        title = trim(title, 96);
        contextName = trim(contextName, 96);
        ownerName = trim(ownerName, 72);
        dimensionId = trim(dimensionId, 96);
        statusMessage = trim(statusMessage, 384);
        pendingAdd = Math.max(0, pendingAdd);
        pendingRemove = Math.max(0, pendingRemove);
        remainingTicks = Math.max(0, remainingTicks);
        outlines = outlines == null ? List.of() : List.copyOf(outlines.stream()
                .filter(java.util.Objects::nonNull).limit(MAX_OUTLINES).toList());
    }

    public static ClaimModeSnapshotPayload inactive() {
        return new ClaimModeSnapshotPayload(false, "", "", "", "", "", "",
                true, true, false, 0, 0, 0, false, 0, 0, 0,
                false, 0.0D, 0.0D, 0.0D, 0.0F,
                0, 0, 0, "", true, false, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClaimModeSnapshotPayload value) {
        buf.writeBoolean(value.active());
        buf.writeUtf(value.sessionId(), 36);
        buf.writeUtf(value.kind(), 48);
        buf.writeUtf(value.title(), 96);
        buf.writeUtf(value.contextName(), 96);
        buf.writeUtf(value.ownerName(), 72);
        buf.writeUtf(value.dimensionId(), 96);
        buf.writeBoolean(value.addMode());
        buf.writeBoolean(value.outlinesVisible());
        buf.writeBoolean(value.hasPos1());
        buf.writeInt(value.pos1X());
        buf.writeInt(value.pos1Y());
        buf.writeInt(value.pos1Z());
        buf.writeBoolean(value.hasPos2());
        buf.writeInt(value.pos2X());
        buf.writeInt(value.pos2Y());
        buf.writeInt(value.pos2Z());
        buf.writeBoolean(value.hasAnchor());
        buf.writeDouble(value.anchorX());
        buf.writeDouble(value.anchorY());
        buf.writeDouble(value.anchorZ());
        buf.writeFloat(value.anchorYaw());
        buf.writeVarInt(value.pendingAdd());
        buf.writeVarInt(value.pendingRemove());
        buf.writeVarInt(value.remainingTicks());
        buf.writeUtf(value.statusMessage(), 384);
        buf.writeBoolean(value.statusSuccess());
        buf.writeBoolean(value.appliedSuccessfully());
        int count = Math.min(MAX_OUTLINES, value.outlines().size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ClaimOutlineSummary.STREAM_CODEC.encode(buf, value.outlines().get(i));
        }
    }

    private static ClaimModeSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        String sessionId = buf.readUtf(36);
        String kind = buf.readUtf(48);
        String title = buf.readUtf(96);
        String contextName = buf.readUtf(96);
        String ownerName = buf.readUtf(72);
        String dimensionId = buf.readUtf(96);
        boolean addMode = buf.readBoolean();
        boolean outlinesVisible = buf.readBoolean();
        boolean hasPos1 = buf.readBoolean();
        int pos1X = buf.readInt();
        int pos1Y = buf.readInt();
        int pos1Z = buf.readInt();
        boolean hasPos2 = buf.readBoolean();
        int pos2X = buf.readInt();
        int pos2Y = buf.readInt();
        int pos2Z = buf.readInt();
        boolean hasAnchor = buf.readBoolean();
        double anchorX = buf.readDouble();
        double anchorY = buf.readDouble();
        double anchorZ = buf.readDouble();
        float anchorYaw = buf.readFloat();
        int pendingAdd = Math.max(0, buf.readVarInt());
        int pendingRemove = Math.max(0, buf.readVarInt());
        int remainingTicks = Math.max(0, buf.readVarInt());
        String statusMessage = buf.readUtf(384);
        boolean statusSuccess = buf.readBoolean();
        boolean appliedSuccessfully = buf.readBoolean();
        int count = Math.max(0, Math.min(MAX_OUTLINES, buf.readVarInt()));
        List<ClaimOutlineSummary> outlines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            outlines.add(ClaimOutlineSummary.STREAM_CODEC.decode(buf));
        }
        return new ClaimModeSnapshotPayload(active, sessionId, kind, title, contextName, ownerName,
                dimensionId, addMode, outlinesVisible,
                hasPos1, pos1X, pos1Y, pos1Z,
                hasPos2, pos2X, pos2Y, pos2Z,
                hasAnchor, anchorX, anchorY, anchorZ, anchorYaw,
                pendingAdd, pendingRemove, remainingTicks,
                statusMessage, statusSuccess, appliedSuccessfully, outlines);
    }

    private static String trim(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    @Override
    public Type<ClaimModeSnapshotPayload> type() {
        return TYPE;
    }
}
