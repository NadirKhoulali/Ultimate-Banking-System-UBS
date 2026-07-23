package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.util.UUID;

public final class OwnerPcBankReadSupport {
    private static final BigDecimal DEFAULT_CARD_ISSUE_FEE = new BigDecimal("25");
    private static final BigDecimal DEFAULT_CARD_REPLACEMENT_FEE = new BigDecimal("50");

    private OwnerPcBankReadSupport() {
    }

    public static CompoundTag metadataSnapshot(CentralBank centralBank, UUID bankId) {
        CompoundTag metadata = centralBank == null ? null : centralBank.readBankMetadata(bankId);
        if (metadata != null) {
            return metadata;
        }
        CompoundTag defaults = new CompoundTag();
        defaults.putString("status", "ACTIVE");
        defaults.putString("ownershipModel", "SOLE");
        defaults.putString("motto", "");
        defaults.putString("color", "#55AAFF");
        defaults.putBoolean("rateExempt", false);
        defaults.putLong("nextLicenseFeeTick", 0L);
        defaults.putString("dailyWithdrawn", "0");
        defaults.putLong("dailyWindowDay", 0L);
        defaults.putString("reserveMinRatio", String.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()));
        defaults.putString(CreditCardService.META_CARD_ISSUE_FEE, DEFAULT_CARD_ISSUE_FEE.toPlainString());
        defaults.putString(CreditCardService.META_CARD_REPLACEMENT_FEE, DEFAULT_CARD_REPLACEMENT_FEE.toPlainString());
        return defaults;
    }

    public static CompoundTag operationalMetadataSnapshot(CentralBank centralBank,
                                                          Bank bank,
                                                          long gameTime) {
        if (centralBank == null || bank == null) {
            return new CompoundTag();
        }
        CompoundTag metadata = metadataSnapshot(centralBank, bank.getBankId());
        if (bank.getBankId().equals(centralBank.getBankId())) {
            return metadata;
        }
        OwnerPcOperationalBankProjection.State state = new OwnerPcOperationalBankProjection.State(
                metadata.getString("status"),
                metadata.contains("lockdownUntilTick") ? metadata.getLong("lockdownUntilTick") : null,
                metadata.contains("reserveBreachStartTick") ? metadata.getLong("reserveBreachStartTick") : null,
                metadata.contains("dailyWindowDay") ? metadata.getLong("dailyWindowDay") : null,
                metadata.getString("dailyWithdrawn"),
                metadata.getInt("queuedWithdrawalCount")
        );
        OwnerPcOperationalBankProjection.Projection projection = OwnerPcOperationalBankProjection.project(
                state,
                bank.getDeclaredReserve(),
                bank.getTotalDeposits(),
                gameTime,
                Config.BANK_MIN_RESERVE_RATIO.get(),
                Config.BANK_RESERVE_GRACE_TICKS.get()
        );
        OwnerPcOperationalBankProjection.State projected = projection.state();
        metadata.putString("status", projected.status());
        if (projected.reserveBreachStartTick() == null) {
            metadata.remove("reserveBreachStartTick");
        } else {
            metadata.putLong("reserveBreachStartTick", projected.reserveBreachStartTick());
        }
        metadata.putLong("dailyWindowDay", projected.dailyWindowDay());
        metadata.putString("dailyWithdrawn", projected.dailyWithdrawn());
        metadata.putInt("queuedWithdrawalCount", projected.queuedWithdrawalCount());
        return metadata;
    }

    public static BigDecimal cardIssueFee(CompoundTag metadata) {
        return fee(metadata, CreditCardService.META_CARD_ISSUE_FEE, DEFAULT_CARD_ISSUE_FEE);
    }

    public static BigDecimal cardReplacementFee(CompoundTag metadata) {
        return fee(metadata, CreditCardService.META_CARD_REPLACEMENT_FEE, DEFAULT_CARD_REPLACEMENT_FEE);
    }

    public static String positiveLimit(CompoundTag metadata,
                                       String key,
                                       BigDecimal fallback,
                                       BigDecimal maximum) {
        BigDecimal value = fallback == null ? BigDecimal.ZERO : fallback;
        if (metadata != null && key != null && !key.isBlank() && metadata.contains(key)) {
            try {
                BigDecimal parsed = new BigDecimal(metadata.getString(key).trim());
                if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                    value = parsed;
                }
            } catch (NumberFormatException ignored) {
                value = fallback == null ? BigDecimal.ZERO : fallback;
            }
        }
        if (maximum != null && value.compareTo(maximum) > 0) {
            value = maximum;
        }
        return value.max(BigDecimal.ZERO).toPlainString();
    }

    public static String stableTagId(CompoundTag tag) {
        if (tag == null) {
            return "missing-0";
        }
        return "missing-" + Integer.toUnsignedString(tag.toString().hashCode(), 16);
    }

    private static BigDecimal fee(CompoundTag metadata, String key, BigDecimal fallback) {
        if (metadata == null || !metadata.contains(key)) {
            return fallback;
        }
        try {
            BigDecimal parsed = new BigDecimal(metadata.getString(key).trim());
            if (parsed.compareTo(BigDecimal.ZERO) < 0 || parsed.stripTrailingZeros().scale() > 0) {
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
