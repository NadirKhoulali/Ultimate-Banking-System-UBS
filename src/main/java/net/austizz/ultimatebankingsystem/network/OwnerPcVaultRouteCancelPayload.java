package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record OwnerPcVaultRouteCancelPayload(UUID editSessionId)
        implements CustomPacketPayload {
    public static final Type<OwnerPcVaultRouteCancelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UltimateBankingSystem.MODID, "owner_pc_vault_route_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultRouteCancelPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buf, value) -> buf.writeUUID(value.editSessionId()),
                    buf -> new OwnerPcVaultRouteCancelPayload(buf.readUUID()));

    public OwnerPcVaultRouteCancelPayload {
        editSessionId = OwnerPcVaultRouteCodecs.requireSessionId(editSessionId);
    }

    @Override
    public Type<OwnerPcVaultRouteCancelPayload> type() {
        return TYPE;
    }
}
