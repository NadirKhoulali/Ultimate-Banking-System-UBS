package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record NumismaticsMigrationUploadFinishPayload(UUID token, UUID uploadId) implements CustomPacketPayload {
    public static final Type<NumismaticsMigrationUploadFinishPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "numismatics_upload_finish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NumismaticsMigrationUploadFinishPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.token()); buf.writeUUID(value.uploadId()); },
            buf -> new NumismaticsMigrationUploadFinishPayload(buf.readUUID(), buf.readUUID()));
    @Override public Type<NumismaticsMigrationUploadFinishPayload> type() { return TYPE; }
}
