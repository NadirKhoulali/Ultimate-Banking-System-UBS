package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record NumismaticsMigrationUploadChunkPayload(UUID token, UUID uploadId, int index, byte[] bytes)
        implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 256 * 1024;
    public static final Type<NumismaticsMigrationUploadChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "numismatics_upload_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NumismaticsMigrationUploadChunkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.token()); buf.writeUUID(value.uploadId()); buf.writeVarInt(value.index()); buf.writeByteArray(value.bytes()); },
            buf -> new NumismaticsMigrationUploadChunkPayload(buf.readUUID(), buf.readUUID(), buf.readVarInt(), buf.readByteArray(MAX_CHUNK_BYTES)));
    @Override public Type<NumismaticsMigrationUploadChunkPayload> type() { return TYPE; }
}
