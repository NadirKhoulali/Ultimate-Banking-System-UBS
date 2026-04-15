package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record OwnerPcBankDataPayload(
        UUID bankId,
        String bankName,
        String status,
        String ownerName,
        String ownershipModel,
        String color,
        String motto,
        String reserve,
        String deposits,
        String reserveRatio,
        String minReserve,
        String accountsCount,
        String dailyCap,
        String dailyUsed,
        String dailyRemaining,
        String cardIssueFee,
        String cardReplacementFee,
        String federalFundsRate,
        boolean ownerView,
        List<String> roles,
        List<String> shares,
        List<String> cofounders,
        List<String> employees,
        List<String> loanProducts,
        List<String> interbankOffers,
        List<String> interbankLoans,
        List<String> accountRoster,
        List<String> certificateSchedule
) implements CustomPacketPayload {

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final Type<OwnerPcBankDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "owner_pc_bank_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcBankDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        UUID_CODEC.encode(buf, payload.bankId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.status());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownerName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownershipModel());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.color());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.motto());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.reserve());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.deposits());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.reserveRatio());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.minReserve());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.accountsCount());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyCap());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyUsed());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyRemaining());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardIssueFee());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardReplacementFee());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.federalFundsRate());
                        ByteBufCodecs.BOOL.encode(buf, payload.ownerView());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.roles());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.shares());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.cofounders());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.employees());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.loanProducts());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.interbankOffers());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.interbankLoans());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.accountRoster());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.certificateSchedule());
                    },
                    buf -> new OwnerPcBankDataPayload(
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf)
                    )
            );

    @Override
    public Type<OwnerPcBankDataPayload> type() {
        return TYPE;
    }
}
