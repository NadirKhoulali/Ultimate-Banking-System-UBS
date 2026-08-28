package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record NumismaticsMigrationActionPayload(UUID token, String action, String text,
                                                int number, boolean flag, boolean secondFlag)
        implements CustomPacketPayload {
    public static final Type<NumismaticsMigrationActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "numismatics_migration_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NumismaticsMigrationActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.token()); buf.writeUtf(value.action(), 64); buf.writeUtf(value.text(), 4_096);
                buf.writeVarInt(value.number()); buf.writeBoolean(value.flag()); buf.writeBoolean(value.secondFlag());
            },
            buf -> new NumismaticsMigrationActionPayload(buf.readUUID(), buf.readUtf(64), buf.readUtf(4_096),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
    @Override public Type<NumismaticsMigrationActionPayload> type() { return TYPE; }
}
