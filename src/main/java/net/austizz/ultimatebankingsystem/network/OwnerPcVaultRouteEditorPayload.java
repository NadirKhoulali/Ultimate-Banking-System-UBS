package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record OwnerPcVaultRouteEditorPayload(boolean success,
                                             String message,
                                             UUID editSessionId,
                                             long sessionExpiresAtMillis,
                                             UUID bankId,
                                             String vaultId,
                                             UUID tellerId,
                                             SafeTellerRouteDirection direction,
                                             boolean hasRoute,
                                             String dimension,
                                             OwnerPcVaultRoutePosition start,
                                             OwnerPcVaultRoutePosition finish,
                                             List<OwnerPcVaultRouteStepPayload> steps)
        implements CustomPacketPayload {
    public static final Type<OwnerPcVaultRouteEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UltimateBankingSystem.MODID, "owner_pc_vault_route_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultRouteEditorPayload>
            STREAM_CODEC = StreamCodec.of(OwnerPcVaultRouteEditorPayload::encode,
                    OwnerPcVaultRouteEditorPayload::decode);

    public OwnerPcVaultRouteEditorPayload {
        message = OwnerPcVaultRouteCodecs.optionalText(
                message, OwnerPcVaultRouteCodecs.MAX_MESSAGE_CHARS, "message");
        if (editSessionId == null) {
            if (sessionExpiresAtMillis != 0L) {
                throw new IllegalArgumentException("session expiry requires an edit session id");
            }
        } else {
            editSessionId = OwnerPcVaultRouteCodecs.requireSessionId(editSessionId);
            if (sessionExpiresAtMillis <= 0L) {
                throw new IllegalArgumentException("edit session expiry must be positive");
            }
        }
        bankId = OwnerPcVaultRouteCodecs.requireUuid(bankId, "bankId");
        vaultId = OwnerPcVaultRouteCodecs.requireText(
                vaultId, OwnerPcVaultRouteCodecs.MAX_ID_CHARS, "vaultId");
        tellerId = OwnerPcVaultRouteCodecs.requireUuid(tellerId, "tellerId");
        direction = OwnerPcVaultRouteCodecs.requireDirection(direction);
        dimension = hasRoute
                ? OwnerPcVaultRouteCodecs.requireText(
                        dimension, OwnerPcVaultRouteCodecs.MAX_ID_CHARS, "dimension")
                : OwnerPcVaultRouteCodecs.optionalText(
                        dimension, OwnerPcVaultRouteCodecs.MAX_ID_CHARS, "dimension");
        start = start == null ? OwnerPcVaultRoutePosition.ZERO : start;
        finish = finish == null ? OwnerPcVaultRoutePosition.ZERO : finish;
        steps = OwnerPcVaultRouteCodecs.copySteps(steps, !hasRoute);
    }

    @Override
    public Type<OwnerPcVaultRouteEditorPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, OwnerPcVaultRouteEditorPayload value) {
        buf.writeBoolean(value.success());
        buf.writeUtf(value.message(), OwnerPcVaultRouteCodecs.MAX_MESSAGE_CHARS);
        buf.writeBoolean(value.editSessionId() != null);
        if (value.editSessionId() != null) {
            buf.writeUUID(value.editSessionId());
            buf.writeLong(value.sessionExpiresAtMillis());
        }
        buf.writeUUID(value.bankId());
        buf.writeUtf(value.vaultId(), OwnerPcVaultRouteCodecs.MAX_ID_CHARS);
        buf.writeUUID(value.tellerId());
        OwnerPcVaultRouteCodecs.writeDirection(buf, value.direction());
        buf.writeBoolean(value.hasRoute());
        buf.writeUtf(value.dimension(), OwnerPcVaultRouteCodecs.MAX_ID_CHARS);
        OwnerPcVaultRoutePosition.STREAM_CODEC.encode(buf, value.start());
        OwnerPcVaultRoutePosition.STREAM_CODEC.encode(buf, value.finish());
        OwnerPcVaultRouteCodecs.writeSteps(buf, value.steps());
    }

    private static OwnerPcVaultRouteEditorPayload decode(RegistryFriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        String message = buf.readUtf(OwnerPcVaultRouteCodecs.MAX_MESSAGE_CHARS);
        boolean hasSession = buf.readBoolean();
        UUID editSessionId = hasSession ? buf.readUUID() : null;
        long sessionExpiresAtMillis = hasSession ? buf.readLong() : 0L;
        UUID bankId = buf.readUUID();
        String vaultId = buf.readUtf(OwnerPcVaultRouteCodecs.MAX_ID_CHARS);
        UUID tellerId = buf.readUUID();
        SafeTellerRouteDirection direction = OwnerPcVaultRouteCodecs.readDirection(buf);
        boolean hasRoute = buf.readBoolean();
        String dimension = buf.readUtf(OwnerPcVaultRouteCodecs.MAX_ID_CHARS);
        OwnerPcVaultRoutePosition start = OwnerPcVaultRoutePosition.STREAM_CODEC.decode(buf);
        OwnerPcVaultRoutePosition finish = OwnerPcVaultRoutePosition.STREAM_CODEC.decode(buf);
        List<OwnerPcVaultRouteStepPayload> steps = OwnerPcVaultRouteCodecs.readSteps(buf, !hasRoute);
        return new OwnerPcVaultRouteEditorPayload(success, message, editSessionId,
                sessionExpiresAtMillis, bankId, vaultId, tellerId, direction, hasRoute,
                dimension, start, finish, steps);
    }
}
