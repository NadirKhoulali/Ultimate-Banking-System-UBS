package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record OwnerPcVaultRouteRequestPayload(UUID bankId,
                                              String vaultId,
                                              UUID tellerId,
                                              SafeTellerRouteDirection direction)
        implements CustomPacketPayload {
    public static final Type<OwnerPcVaultRouteRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UltimateBankingSystem.MODID, "owner_pc_vault_route_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultRouteRequestPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buf, value) -> {
                        buf.writeUUID(value.bankId());
                        buf.writeUtf(value.vaultId(), OwnerPcVaultRouteCodecs.MAX_ID_CHARS);
                        buf.writeUUID(value.tellerId());
                        OwnerPcVaultRouteCodecs.writeDirection(buf, value.direction());
                    },
                    buf -> new OwnerPcVaultRouteRequestPayload(
                            buf.readUUID(),
                            buf.readUtf(OwnerPcVaultRouteCodecs.MAX_ID_CHARS),
                            buf.readUUID(),
                            OwnerPcVaultRouteCodecs.readDirection(buf))
            );

    public OwnerPcVaultRouteRequestPayload {
        bankId = OwnerPcVaultRouteCodecs.requireUuid(bankId, "bankId");
        vaultId = OwnerPcVaultRouteCodecs.requireText(
                vaultId, OwnerPcVaultRouteCodecs.MAX_ID_CHARS, "vaultId");
        tellerId = OwnerPcVaultRouteCodecs.requireUuid(tellerId, "tellerId");
        direction = OwnerPcVaultRouteCodecs.requireDirection(direction);
    }

    @Override
    public Type<OwnerPcVaultRouteRequestPayload> type() {
        return TYPE;
    }
}
