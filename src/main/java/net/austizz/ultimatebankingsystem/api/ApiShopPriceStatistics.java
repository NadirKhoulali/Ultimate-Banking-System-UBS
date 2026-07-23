package net.austizz.ultimatebankingsystem.api;

/** A price-per-item sample in cents. Median and average are both exposed to avoid hidden policy choices. */
public record ApiShopPriceStatistics(
        boolean available,
        String itemId,
        ApiShopPriceScope scope,
        int sampleCount,
        long medianPriceCents,
        long averagePriceCents,
        long minimumPriceCents,
        long maximumPriceCents
) {
    public ApiShopPriceStatistics {
        itemId = itemId == null ? "" : itemId;
        scope = scope == null ? ApiShopPriceScope.REGULAR : scope;
        sampleCount = Math.max(0, sampleCount);
        medianPriceCents = Math.max(0L, medianPriceCents);
        averagePriceCents = Math.max(0L, averagePriceCents);
        minimumPriceCents = Math.max(0L, minimumPriceCents);
        maximumPriceCents = Math.max(0L, maximumPriceCents);
    }

    public static ApiShopPriceStatistics unavailable(String itemId, ApiShopPriceScope scope) {
        return new ApiShopPriceStatistics(false, itemId, scope, 0, 0L, 0L, 0L, 0L);
    }
}
