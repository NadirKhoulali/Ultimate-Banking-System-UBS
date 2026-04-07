package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server → client payload containing the list of accounts belonging to the
 * requesting player.
 *
 * <p>Sent in response to {@link OpenATMPayload}. The client stores the data in
 * {@link net.austizz.ultimatebankingsystem.gui.screens.ClientATMData} and opens
 * the ATM screen.</p>
 */
public record AccountListPayload(List<AccountSummary> accounts) implements CustomPacketPayload {

    public static final Type<AccountListPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "account_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AccountListPayload> STREAM_CODEC =
        StreamCodec.composite(
            AccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)),
            AccountListPayload::accounts,
            AccountListPayload::new
        );

    @Override
    public Type<AccountListPayload> type() { return TYPE; }
}
