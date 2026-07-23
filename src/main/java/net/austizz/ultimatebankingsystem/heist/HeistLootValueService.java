package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.api.heist.HeistLootValueProvider;
import net.austizz.ultimatebankingsystem.api.heist.HeistLootValueRegistry;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.market.CommodityMarketService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

public final class HeistLootValueService {
    public static final String CUSTOM_VALUE_CENTS = "ubsHeistValueCents";

    private HeistLootValueService() {}

    public static long valueCents(MinecraftServer server, ItemStack stack) {
        return knownValueCents(server, stack).orElse(0L);
    }

    /**
     * Returns a value only when UBS or an installed value provider recognizes the item.
     * This lets inventory dashboards distinguish a real zero value from an unknown price.
     */
    public static OptionalLong knownValueCents(MinecraftServer server, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return OptionalLong.of(0L);
        CompoundTag custom = ItemStackDataCompat.getCustomData(stack);
        if (custom.contains(CUSTOM_VALUE_CENTS)) {
            return OptionalLong.of(safeMultiply(
                    Math.max(0L, custom.getLong(CUSTOM_VALUE_CENTS)), stack.getCount()));
        }

        long tender = DollarBills.physicalTenderCents(stack.getItem());
        if (tender > 0L) return OptionalLong.of(safeMultiply(tender, stack.getCount()));

        MoneyStackBlock.BillDenomination denomination = MoneyStackBlock.BillDenomination.fromStackItem(stack.getItem());
        if (denomination != null) {
            return OptionalLong.of(safeMultiply(
                    (long) denomination.value() * 100L * 100L, stack.getCount()));
        }

        if (stack.is(ModBlocks.GOLD_BAR.get().asItem())) {
            return quoteCents(server, CommodityMarketService.GOLD_BAR, stack.getCount());
        }
        if (stack.is(ModBlocks.SILVER_BAR.get().asItem())) {
            return quoteCents(server, CommodityMarketService.SILVER_BAR, stack.getCount());
        }

        for (HeistLootValueProvider provider : HeistLootValueRegistry.providers()) {
            try {
                OptionalLong value = provider.valueCents(server, stack.copy());
                if (value.isPresent()) return OptionalLong.of(Math.max(0L, value.getAsLong()));
            } catch (RuntimeException ignored) {
                // A third-party provider cannot break the heist transaction.
            }
        }
        return OptionalLong.empty();
    }

    private static OptionalLong quoteCents(MinecraftServer server, String quoteId, int count) {
        return CommodityMarketService.quotes(server).stream()
                .filter(quote -> quote.id().equals(quoteId) && quote.priced())
                .findFirst()
                .map(quote -> OptionalLong.of(safeMultiply(toCents(quote.spot()), count)))
                .orElseGet(OptionalLong::empty);
    }

    private static long toCents(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return 0L;
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
