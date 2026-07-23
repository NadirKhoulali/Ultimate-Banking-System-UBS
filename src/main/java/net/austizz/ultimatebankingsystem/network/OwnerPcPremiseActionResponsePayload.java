package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record OwnerPcPremiseActionResponsePayload(
        UUID bankId,
        UUID operationId,
        OwnerPcPremiseActionPayload.Action action,
        String premiseId,
        boolean success,
        String message
) implements CustomPacketPayload {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 512;

    public static final Type<OwnerPcPremiseActionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UltimateBankingSystem.MODID, "owner_pc_premise_action_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcPremiseActionResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        writeUuid(buf, payload.bankId());
                        writeUuid(buf, payload.operationId());
                        buf.writeVarInt(payload.action().ordinal());
                        buf.writeUtf(payload.premiseId(), MAX_ID_LENGTH);
                        buf.writeBoolean(payload.success());
                        buf.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
                    },
                    buf -> new OwnerPcPremiseActionResponsePayload(
                            readUuid(buf),
                            readUuid(buf),
                            enumValue(OwnerPcPremiseActionPayload.Action.values(),
                                    buf.readVarInt(), "premise action"),
                            buf.readUtf(MAX_ID_LENGTH),
                            buf.readBoolean(),
                            buf.readUtf(MAX_MESSAGE_LENGTH))
            );

    public OwnerPcPremiseActionResponsePayload {
        bankId = Objects.requireNonNull(bankId, "bankId");
        operationId = Objects.requireNonNull(operationId, "operationId");
        action = Objects.requireNonNull(action, "action");
        premiseId = bounded(premiseId, "premiseId", MAX_ID_LENGTH);
        message = bounded(message, "message", MAX_MESSAGE_LENGTH);
    }

    @Override
    public Type<OwnerPcPremiseActionResponsePayload> type() {
        return TYPE;
    }

    private static String bounded(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters.");
        }
        return normalized;
    }

    private static void writeUuid(RegistryFriendlyByteBuf buf, UUID value) {
        buf.writeLong(value.getMostSignificantBits());
        buf.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(RegistryFriendlyByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    private static <E extends Enum<E>> E enumValue(E[] values, int ordinal, String field) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + field + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
