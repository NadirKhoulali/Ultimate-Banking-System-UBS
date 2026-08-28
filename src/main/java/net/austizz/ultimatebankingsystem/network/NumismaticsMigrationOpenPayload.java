package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NumismaticsMigrationOpenPayload(String snapshotJson) implements CustomPacketPayload {
    public static final int MAX_JSON = 1_048_576;
    public static final Type<NumismaticsMigrationOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "numismatics_migration_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NumismaticsMigrationOpenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.snapshotJson(), MAX_JSON),
            buf -> new NumismaticsMigrationOpenPayload(buf.readUtf(MAX_JSON)));
    @Override public Type<NumismaticsMigrationOpenPayload> type() { return TYPE; }
}
