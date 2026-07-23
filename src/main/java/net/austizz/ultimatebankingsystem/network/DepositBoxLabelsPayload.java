package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record DepositBoxLabelsPayload(String dimension, int x, int y, int z,
                                      boolean visible, List<String> labels) implements CustomPacketPayload {
    public static final Type<DepositBoxLabelsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "deposit_box_labels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositBoxLabelsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.dimension(), 128);
                        buf.writeVarInt(payload.x());
                        buf.writeVarInt(payload.y());
                        buf.writeVarInt(payload.z());
                        buf.writeBoolean(payload.visible());
                        buf.writeVarInt(payload.labels().size());
                        for (String label : payload.labels()) buf.writeUtf(label, 32);
                    },
                    buf -> {
                        String dimension = buf.readUtf(128);
                        int x = buf.readVarInt();
                        int y = buf.readVarInt();
                        int z = buf.readVarInt();
                        boolean visible = buf.readBoolean();
                        int count = Math.min(4, Math.max(0, buf.readVarInt()));
                        List<String> labels = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) labels.add(buf.readUtf(32));
                        return new DepositBoxLabelsPayload(dimension, x, y, z, visible, labels);
                    });

    public DepositBoxLabelsPayload {
        dimension = dimension == null ? "" : dimension;
        List<String> safe = new ArrayList<>(4);
        if (labels != null) {
            for (int i = 0; i < labels.size() && i < 4; i++) {
                String label = labels.get(i);
                safe.add(label == null ? "" : label.substring(0, Math.min(32, label.length())));
            }
        }
        labels = List.copyOf(safe);
    }

    @Override
    public Type<DepositBoxLabelsPayload> type() {
        return TYPE;
    }
}
