package net.austizz.ultimatebankingsystem.api;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;

@ApiStatus.AvailableSince("1.0.0")
public record ApiItemResult(
        boolean success,
        String reason,
        ItemStack itemStack,
        String referenceId,
        BigDecimal amount
) {
    public static ApiItemResult ok(ItemStack itemStack, String referenceId, BigDecimal amount) {
        ItemStack safe = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        return new ApiItemResult(true, "", safe, referenceId == null ? "" : referenceId, safeAmount);
    }

    public static ApiItemResult fail(String reason) {
        return new ApiItemResult(false, reason == null ? "Unknown error" : reason, ItemStack.EMPTY, "", BigDecimal.ZERO);
    }
}
