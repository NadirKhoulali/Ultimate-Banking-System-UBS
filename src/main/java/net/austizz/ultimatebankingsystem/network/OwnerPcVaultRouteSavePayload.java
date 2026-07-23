package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record OwnerPcVaultRouteSavePayload(UUID editSessionId,
                                           UUID bankId,
                                           String vaultId,
                                           UUID tellerId,
                                           SafeTellerRouteDirection direction,
                                           String dimension,
                                           OwnerPcVaultRoutePosition start,
                                           OwnerPcVaultRoutePosition finish,
                                           List<OwnerPcVaultRouteStepPayload> steps)
        implements CustomPacketPayload {
    public static final Type<OwnerPcVaultRouteSavePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UltimateBankingSystem.MODID, "owner_pc_vault_route_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultRouteSavePayload>
            STREAM_CODEC = StreamCodec.of(OwnerPcVaultRouteSavePayload::encode,
                    OwnerPcVaultRouteSavePayload::decode);

    public OwnerPcVaultRouteSavePayload {
        editSessionId = OwnerPcVaultRouteCodecs.requireSessionId(editSessionId);
        bankId = OwnerPcVaultRouteCodecs.requireUuid(bankId, "bankId");
        vaultId = OwnerPcVaultRouteCodecs.requireText(
                vaultId, OwnerPcVaultRouteCodecs.MAX_ID_CHARS, "vaultId");
        tellerId = OwnerPcVaultRouteCodecs.requireUuid(tellerId, "tellerId");
        direction = OwnerPcVaultRouteCodecs.requireDirection(direction);
        dimension = OwnerPcVaultRouteCodecs.requireText(
                dimension, OwnerPcVaultRouteCodecs.MAX_ID_CHARS, "dimension");
        if (start == null || finish == null) {
            throw new IllegalArgumentException("route start and finish are required");
        }
        steps = OwnerPcVaultRouteCodecs.copySteps(steps, false);
    }

    @Override
    public Type<OwnerPcVaultRouteSavePayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, OwnerPcVaultRouteSavePayload value) {
        buf.writeUUID(value.editSessionId());
        buf.writeUUID(value.bankId());
        buf.writeUtf(value.vaultId(), OwnerPcVaultRouteCodecs.MAX_ID_CHARS);
        buf.writeUUID(value.tellerId());
        OwnerPcVaultRouteCodecs.writeDirection(buf, value.direction());
        buf.writeUtf(value.dimension(), OwnerPcVaultRouteCodecs.MAX_ID_CHARS);
        OwnerPcVaultRoutePosition.STREAM_CODEC.encode(buf, value.start());
        OwnerPcVaultRoutePosition.STREAM_CODEC.encode(buf, value.finish());
        OwnerPcVaultRouteCodecs.writeSteps(buf, value.steps());
    }

    private static OwnerPcVaultRouteSavePayload decode(RegistryFriendlyByteBuf buf) {
        return new OwnerPcVaultRouteSavePayload(
                buf.readUUID(),
                buf.readUUID(),
                buf.readUtf(OwnerPcVaultRouteCodecs.MAX_ID_CHARS),
                buf.readUUID(),
                OwnerPcVaultRouteCodecs.readDirection(buf),
                buf.readUtf(OwnerPcVaultRouteCodecs.MAX_ID_CHARS),
                OwnerPcVaultRoutePosition.STREAM_CODEC.decode(buf),
                OwnerPcVaultRoutePosition.STREAM_CODEC.decode(buf),
                OwnerPcVaultRouteCodecs.readSteps(buf, false));
    }
}
