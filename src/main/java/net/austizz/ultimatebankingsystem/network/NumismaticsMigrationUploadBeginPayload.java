package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record NumismaticsMigrationUploadBeginPayload(UUID token, UUID uploadId, String fileName,
                                                     long size, int chunks, String sha256)
        implements CustomPacketPayload {
    public static final Type<NumismaticsMigrationUploadBeginPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "numismatics_upload_begin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NumismaticsMigrationUploadBeginPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.token()); buf.writeUUID(value.uploadId()); buf.writeUtf(value.fileName(), 256); buf.writeVarLong(value.size()); buf.writeVarInt(value.chunks()); buf.writeUtf(value.sha256(), 128); },
            buf -> new NumismaticsMigrationUploadBeginPayload(buf.readUUID(), buf.readUUID(), buf.readUtf(256), buf.readVarLong(), buf.readVarInt(), buf.readUtf(128)));
    @Override public Type<NumismaticsMigrationUploadBeginPayload> type() { return TYPE; }
}
