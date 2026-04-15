package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server payload sent when a player right-clicks an ATM block.
 *
 * <p>Carries no data — the server uses the sending player's identity to look up
 * their accounts and respond with an {@link AccountListPayload}.</p>
 */
public record OpenATMPayload() implements CustomPacketPayload {

    public static final Type<OpenATMPayload> TYPE = new Type<>(
        new ResourceLocation(UltimateBankingSystem.MODID, "open_atm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenATMPayload> STREAM_CODEC =
        StreamCodec.of((buf, val) -> {}, buf -> new OpenATMPayload());

    @Override
    public Type<OpenATMPayload> type() { return TYPE; }
}
