package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;

import java.util.UUID;

public record OwnerPcBankAppSummary(
        UUID bankId,
        String bankName,
        String color,
        String status,
        boolean owner,
        String roleLabel,
        String appType
) {
    public static final String APP_TYPE_BANK = "BANK";
    public static final String APP_TYPE_SHOP = "SHOP";

    public OwnerPcBankAppSummary {
        appType = normalizeAppType(appType);
    }

    public boolean isShopApp() {
        return APP_TYPE_SHOP.equals(appType);
    }

    public boolean isBankApp() {
        return !isShopApp();
    }

    private static String normalizeAppType(String appType) {
        if (appType == null || appType.isBlank()) {
            return APP_TYPE_BANK;
        }
        String normalized = appType.trim().toUpperCase(java.util.Locale.ROOT);
        return APP_TYPE_SHOP.equals(normalized) ? APP_TYPE_SHOP : APP_TYPE_BANK;
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcBankAppSummary> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        UUID_CODEC.encode(buf, value.bankId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.bankName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.color());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.status());
                        ByteBufCodecs.BOOL.encode(buf, value.owner());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.roleLabel());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.appType());
                    },
                    buf -> new OwnerPcBankAppSummary(
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );
}
