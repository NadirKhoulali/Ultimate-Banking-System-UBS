package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record BankTellerOpenPayload(
        UUID tellerId,
        String tellerName,
        String boundBankId,
        String boundBankName,
        String bankMotto,
        String cardIssueFee,
        String cardReplacementFee,
        boolean openAccountFree,
        List<String> safeBoxPolicies,
        List<BankTellerAccountSummary> accounts,
        BankTellerSafeBoxState safeBoxState
) implements CustomPacketPayload {

    public static final Type<BankTellerOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "bank_teller_open"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public BankTellerOpenPayload {
        safeBoxPolicies = safeBoxPolicies == null ? List.of() : List.copyOf(safeBoxPolicies);
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        safeBoxState = safeBoxState == null
                ? BankTellerSafeBoxState.unavailable(tellerId, parseUuid(boundBankId),
                "Safe-deposit box state is unavailable.")
                : safeBoxState;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BankTellerOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        UUID_CODEC.encode(buf, payload.tellerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.tellerName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.boundBankId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.boundBankName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankMotto());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardIssueFee());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardReplacementFee());
                        ByteBufCodecs.BOOL.encode(buf, payload.openAccountFree());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).encode(buf, payload.safeBoxPolicies());
                        BankTellerAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.accounts());
                        BankTellerSafeBoxState.STREAM_CODEC.encode(buf, payload.safeBoxState());
                    },
                    buf -> new BankTellerOpenPayload(
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).decode(buf),
                            BankTellerAccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                            BankTellerSafeBoxState.STREAM_CODEC.decode(buf)
                    )
            );

    public UUID parseBoundBankId() {
        return parseUuid(boundBankId);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public Type<BankTellerOpenPayload> type() {
        return TYPE;
    }
}

