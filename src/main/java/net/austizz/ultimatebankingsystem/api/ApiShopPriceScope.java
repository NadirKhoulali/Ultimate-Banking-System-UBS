package net.austizz.ultimatebankingsystem.api;

/** Defines which physical shop displays contribute to a market-price sample. */
public enum ApiShopPriceScope {
    /** Registered, setup-complete shops that are currently open; creative displays are excluded. */
    REGULAR,
    /** Every indexed shop-mode display, including creative and unregistered displays. */
    INCLUDE_ALL,
    /** Every indexed non-creative shop-mode display, whether registered or not. */
    ALL_SHELVES_EXCLUDE_CREATIVE,
    /** Only indexed creative shop-mode displays. */
    CREATIVE_ONLY
}
