package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record OwnerPcPremiseActionPayload(
        UUID bankId,
        UUID operationId,
        Action action,
        String premiseId,
        SafePremiseMode mode
) implements CustomPacketPayload {
    private static final int MAX_ID_LENGTH = 128;

    public static final Type<OwnerPcPremiseActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "owner_pc_premise_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcPremiseActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        writeUuid(buf, payload.bankId());
                        writeUuid(buf, payload.operationId());
                        buf.writeVarInt(payload.action().ordinal());
                        buf.writeUtf(payload.premiseId(), MAX_ID_LENGTH);
                        buf.writeBoolean(payload.mode() != null);
                        if (payload.mode() != null) {
                            buf.writeVarInt(payload.mode().ordinal());
                        }
                    },
                    buf -> new OwnerPcPremiseActionPayload(
                            readUuid(buf),
                            readUuid(buf),
                            enumValue(Action.values(), buf.readVarInt(), "premise action"),
                            buf.readUtf(MAX_ID_LENGTH),
                            buf.readBoolean()
                                    ? enumValue(SafePremiseMode.values(), buf.readVarInt(), "premise mode")
                                    : null)
            );

    public OwnerPcPremiseActionPayload {
        bankId = Objects.requireNonNull(bankId, "bankId");
        operationId = Objects.requireNonNull(operationId, "operationId");
        action = Objects.requireNonNull(action, "action");
        premiseId = premiseId == null ? "" : premiseId.trim();
        if (premiseId.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("premiseId exceeds " + MAX_ID_LENGTH + " characters.");
        }
        switch (action) {
            case START_CLAIM -> {
                if (!premiseId.isEmpty() || mode != null) {
                    throw new IllegalArgumentException("START_CLAIM cannot target an existing premise or mode.");
                }
            }
            case SET_MODE -> {
                if (premiseId.isEmpty() || mode == null) {
                    throw new IllegalArgumentException("SET_MODE requires a premise and mode.");
                }
            }
            case START_EXIT_EDIT, DELETE -> {
                if (premiseId.isEmpty() || mode != null) {
                    throw new IllegalArgumentException(action + " requires only a premise target.");
                }
            }
        }
    }

    public OwnerPcPremiseActionPayload(UUID bankId,
                                       Action action,
                                       String premiseId,
                                       SafePremiseMode mode) {
        this(bankId, UUID.randomUUID(), action, premiseId, mode);
    }

    @Override
    public Type<OwnerPcPremiseActionPayload> type() {
        return TYPE;
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

    public enum Action {
        START_CLAIM,
        SET_MODE,
        START_EXIT_EDIT,
        DELETE
    }
}
