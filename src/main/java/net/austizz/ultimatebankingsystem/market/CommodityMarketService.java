package net.austizz.ultimatebankingsystem.market;

import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommodityMarketService {
    public static final String GOLD_INGOT = "minecraft:gold_ingot";
    public static final String GOLD_BAR = "ultimatebankingsystem:gold_bar";
    public static final String SILVER_INGOT = "c:ingots/silver";
    public static final String SILVER_BAR = "ultimatebankingsystem:silver_bar";
    private static final String DATA_NAME = "ultimate_banking_system_commodity_market";
    private static final BigDecimal GOLD_BAR_INGOT_BASIS = BigDecimal.valueOf(6L);
    private static final BigDecimal SILVER_BAR_INGOT_BASIS = BigDecimal.valueOf(6L);
    private static final BigDecimal HALF_PERCENT = new BigDecimal("0.005");

    private CommodityMarketService() {
    }

    public record MarketQuote(String id,
                              String displayName,
                              String unitName,
                              BigDecimal spot,
                              BigDecimal bid,
                              BigDecimal ask,
                              BigDecimal previousSpot,
                              BigDecimal high24h,
                              BigDecimal low24h,
                              String source,
                              long updatedAtMillis,
                              boolean seeded,
                              String formula) {
        public BigDecimal changePercent() {
            if (previousSpot == null || previousSpot.compareTo(BigDecimal.ZERO) <= 0
                    || spot == null || spot.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            return spot.subtract(previousSpot)
                    .multiply(BigDecimal.valueOf(100L))
                    .divide(previousSpot, 2, RoundingMode.HALF_UP);
        }

        public boolean priced() {
            return seeded && spot != null && spot.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    public record PhoneMarketQuote(String id,
                                   String displayName,
                                   String unitName,
                                   String spotLabel,
                                   String bidLabel,
                                   String askLabel,
                                   String changeLabel,
                                   String highLabel,
                                   String lowLabel,
                                   String source,
                                   long updatedAtMillis,
                                   boolean seeded,
                                   String formula,
                                   String confidenceLabel) {
    }

    public static MarketData get(MinecraftServer server) {
        if (server == null) {
            MarketData data = new MarketData();
            data.ensureDefaults();
            return data;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            MarketData data = new MarketData();
            data.ensureDefaults();
            return data;
        }
        MarketData data = overworld.getDataStorage().computeIfAbsent(MarketData.factory(), DATA_NAME);
        data.ensureDefaults();
        return data;
    }

    public static List<MarketQuote> quotes(MinecraftServer server) {
        MarketData data = get(server);
        data.ensureDefaults();
        return data.quotes();
    }

    public static List<PhoneMarketQuote> phoneQuotes(MinecraftServer server) {
        return quotes(server).stream()
                .map(CommodityMarketService::toPhoneQuote)
                .toList();
    }

    public static MarketQuote setSpot(MinecraftServer server, String commodityRaw, BigDecimal price, String actor) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero.");
        }
        MarketData data = get(server);
        String id = normalizeCommodityId(commodityRaw);
        if (GOLD_BAR.equals(id)) {
            price = price.divide(GOLD_BAR_INGOT_BASIS, 8, RoundingMode.HALF_UP);
            id = GOLD_INGOT;
        }
        if (SILVER_BAR.equals(id)) {
            price = price.divide(SILVER_BAR_INGOT_BASIS, 8, RoundingMode.HALF_UP);
            id = SILVER_INGOT;
        }
        MarketQuote updated = data.setManualSpot(id, price, actor);
        data.setDirty();
        return updated;
    }

    public static String normalizeCommodityId(String raw) {
        String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        id = id.replace(" ", "_").replace("-", "_");
        return switch (id) {
            case "gold", "gold_ingot", "minecraft_gold_ingot", "minecraft:gold", "minecraft:gold_ingot" -> GOLD_INGOT;
            case "gold_bar", "bullion", "bullion_bar", "ultimatebankingsystem_gold_bar",
                    "ultimatebankingsystem:gold_bar" -> GOLD_BAR;
            case "silver", "silver_ingot", "silver_ingots", "c_silver_ingot", "c_silver_ingots",
                    "c_ingots_silver", "c:ingots/silver", "forge_silver_ingot", "forge_silver_ingots",
                    "forge:ingots/silver" -> SILVER_INGOT;
            case "silver_bar", "silver_bullion", "ultimatebankingsystem_silver_bar",
                    "ultimatebankingsystem:silver_bar" -> SILVER_BAR;
            default -> id.contains(":") ? id : "minecraft:" + id;
        };
    }

    public static String commodityDisplayName(String id) {
        return switch (normalizeCommodityId(id)) {
            case GOLD_INGOT -> "Gold Ingot";
            case GOLD_BAR -> "Gold Bar";
            case SILVER_INGOT -> "Silver Ingot";
            case SILVER_BAR -> "Silver Bar";
            default -> {
                String safe = id == null ? "Commodity" : id.substring(id.indexOf(':') + 1).replace("_", " ");
                yield titleCase(safe);
            }
        };
    }

    private static PhoneMarketQuote toPhoneQuote(MarketQuote quote) {
        boolean priced = quote != null && quote.priced();
        BigDecimal change = quote == null ? BigDecimal.ZERO : quote.changePercent();
        String changeLabel = !priced || quote.previousSpot().compareTo(BigDecimal.ZERO) <= 0
                ? "Seed"
                : (change.signum() >= 0 ? "+" : "") + change.stripTrailingZeros().toPlainString() + "%";
        return new PhoneMarketQuote(
                quote.id(),
                quote.displayName(),
                quote.unitName(),
                priced ? MoneyText.abbreviateRoundedWithDollar(quote.spot()) : "Unpriced",
                priced ? MoneyText.abbreviateRoundedWithDollar(quote.bid()) : "-",
                priced ? MoneyText.abbreviateRoundedWithDollar(quote.ask()) : "-",
                changeLabel,
                priced ? MoneyText.abbreviateRoundedWithDollar(quote.high24h()) : "-",
                priced ? MoneyText.abbreviateRoundedWithDollar(quote.low24h()) : "-",
                quote.source(),
                quote.updatedAtMillis(),
                quote.seeded(),
                quote.formula(),
                priced ? "Central Bank seeded" : "Awaiting Central Bank seed"
        );
    }

    private static BigDecimal bidFor(BigDecimal spot) {
        return spot.subtract(spot.multiply(HALF_PERCENT)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal askFor(BigDecimal spot) {
        return spot.add(spot.multiply(HALF_PERCENT)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String titleCase(String raw) {
        String[] parts = (raw == null ? "" : raw.trim()).split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? "Commodity" : out.toString();
    }

    public static final class MarketData extends SavedData {
        private final Map<String, MarketQuote> quotes = new LinkedHashMap<>();

        public static SavedData.Factory<MarketData> factory() {
            return new SavedData.Factory<>(MarketData::new, MarketData::load, null);
        }

        public void ensureDefaults() {
            ensureQuote(GOLD_INGOT, "Gold Ingot", "ingot", "Central Bank spot desk",
                    "Base UBS gold price. Gold bars derive from this quote.");
            migrateLegacySilverBarQuote();
            ensureQuote(SILVER_INGOT, "Silver Ingot", "ingot", "Central Bank spot desk",
                    "Compatible silver ingot tag quote. Silver bars derive from this quote.");
        }

        public List<MarketQuote> quotes() {
            ensureDefaults();
            List<MarketQuote> rows = new ArrayList<>(quotes.values());
            MarketQuote gold = quotes.get(GOLD_INGOT);
            MarketQuote silver = quotes.get(SILVER_INGOT);
            rows.add(derivedGoldBar(gold));
            rows.add(derivedSilverBar(silver));
            rows.sort(Comparator.comparingInt(CommodityMarketService::quoteSortOrder)
                    .thenComparing(MarketQuote::displayName, String.CASE_INSENSITIVE_ORDER));
            return rows;
        }

        public MarketQuote setManualSpot(String commodityId, BigDecimal price, String actor) {
            ensureDefaults();
            String id = normalizeCommodityId(commodityId);
            MarketQuote current = quotes.get(id);
            if (current == null) {
                current = emptyQuote(id, commodityDisplayName(id), "unit",
                        "Central Bank spot desk", "Manual UBS commodity quote.");
            }
            BigDecimal spot = price.setScale(2, RoundingMode.HALF_UP);
            BigDecimal previous = current.priced() ? current.spot() : BigDecimal.ZERO;
            BigDecimal high = previous.compareTo(BigDecimal.ZERO) > 0 ? previous.max(spot) : spot;
            BigDecimal low = previous.compareTo(BigDecimal.ZERO) > 0 ? previous.min(spot) : spot;
            String actorLabel = actor == null || actor.isBlank() ? "Central Bank" : actor.trim();
            MarketQuote updated = new MarketQuote(
                    id,
                    current.displayName(),
                    current.unitName(),
                    spot,
                    bidFor(spot),
                    askFor(spot),
                    previous,
                    high,
                    low,
                    "Central Bank seed by " + actorLabel,
                    Instant.now().toEpochMilli(),
                    true,
                    current.formula()
            );
            quotes.put(id, updated);
            return updated;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ensureDefaults();
            ListTag list = new ListTag();
            for (MarketQuote quote : quotes.values()) {
                CompoundTag row = new CompoundTag();
                row.putString("id", quote.id());
                row.putString("display_name", quote.displayName());
                row.putString("unit_name", quote.unitName());
                row.putString("spot", decimalString(quote.spot()));
                row.putString("bid", decimalString(quote.bid()));
                row.putString("ask", decimalString(quote.ask()));
                row.putString("previous_spot", decimalString(quote.previousSpot()));
                row.putString("high_24h", decimalString(quote.high24h()));
                row.putString("low_24h", decimalString(quote.low24h()));
                row.putString("source", quote.source());
                row.putLong("updated_at", quote.updatedAtMillis());
                row.putBoolean("seeded", quote.seeded());
                row.putString("formula", quote.formula());
                list.add(row);
            }
            tag.put("quotes", list);
            return tag;
        }

        private static MarketData load(CompoundTag tag, HolderLookup.Provider registries) {
            MarketData data = new MarketData();
            ListTag list = tag.getList("quotes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag row = list.getCompound(i);
                String id = normalizeCommodityId(row.getString("id"));
                if (id.isBlank()) {
                    continue;
                }
                MarketQuote quote = new MarketQuote(
                        id,
                        row.getString("display_name").isBlank() ? commodityDisplayName(id) : row.getString("display_name"),
                        row.getString("unit_name").isBlank() ? "unit" : row.getString("unit_name"),
                        parseDecimal(row.getString("spot")),
                        parseDecimal(row.getString("bid")),
                        parseDecimal(row.getString("ask")),
                        parseDecimal(row.getString("previous_spot")),
                        parseDecimal(row.getString("high_24h")),
                        parseDecimal(row.getString("low_24h")),
                        row.getString("source").isBlank() ? "Central Bank spot desk" : row.getString("source"),
                        row.getLong("updated_at"),
                        row.getBoolean("seeded"),
                        row.getString("formula")
                );
                data.quotes.put(id, quote);
            }
            data.ensureDefaults();
            return data;
        }

        private void ensureQuote(String id, String displayName, String unitName, String source, String formula) {
            quotes.computeIfAbsent(id, ignored -> emptyQuote(id, displayName, unitName, source, formula));
        }

        private static MarketQuote emptyQuote(String id, String displayName, String unitName, String source, String formula) {
            return new MarketQuote(id, displayName, unitName,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    source, 0L, false, formula);
        }

        private void migrateLegacySilverBarQuote() {
            MarketQuote legacy = quotes.remove(SILVER_BAR);
            if (legacy == null) {
                return;
            }
            if (!quotes.containsKey(SILVER_INGOT)) {
                if (legacy.priced()) {
                    quotes.put(SILVER_INGOT, divideQuote(
                            legacy,
                            SILVER_INGOT,
                            "Silver Ingot",
                            "ingot",
                            SILVER_BAR_INGOT_BASIS,
                            "Migrated from legacy silver bar spot",
                            "Compatible silver ingot tag quote. Silver bars derive from this quote."
                    ));
                } else {
                    quotes.put(SILVER_INGOT, emptyQuote(SILVER_INGOT, "Silver Ingot", "ingot",
                            "Central Bank spot desk",
                            "Compatible silver ingot tag quote. Silver bars derive from this quote."));
                }
            }
            setDirty();
        }

        private static MarketQuote derivedGoldBar(MarketQuote gold) {
            if (gold == null || !gold.priced()) {
                return new MarketQuote(GOLD_BAR, "Gold Bar", "bar",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        "Derived from gold ingot spot", 0L, false,
                        "1 gold bar = 6 Minecraft gold ingots.");
            }
            return new MarketQuote(GOLD_BAR, "Gold Bar", "bar",
                    gold.spot().multiply(GOLD_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    gold.bid().multiply(GOLD_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    gold.ask().multiply(GOLD_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    gold.previousSpot().multiply(GOLD_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    gold.high24h().multiply(GOLD_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    gold.low24h().multiply(GOLD_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    "Derived from 6x gold ingot spot",
                    gold.updatedAtMillis(),
                    true,
                    "1 gold bar = 6 Minecraft gold ingots.");
        }

        private static MarketQuote derivedSilverBar(MarketQuote silver) {
            if (silver == null || !silver.priced()) {
                return new MarketQuote(SILVER_BAR, "Silver Bar", "bar",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        "Derived from silver ingot spot", 0L, false,
                        "1 silver bar = 6 compatible silver ingots.");
            }
            return new MarketQuote(SILVER_BAR, "Silver Bar", "bar",
                    silver.spot().multiply(SILVER_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    silver.bid().multiply(SILVER_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    silver.ask().multiply(SILVER_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    silver.previousSpot().multiply(SILVER_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    silver.high24h().multiply(SILVER_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    silver.low24h().multiply(SILVER_BAR_INGOT_BASIS).setScale(2, RoundingMode.HALF_UP),
                    "Derived from 6x silver ingot spot",
                    silver.updatedAtMillis(),
                    true,
                    "1 silver bar = 6 compatible silver ingots.");
        }

        private static MarketQuote divideQuote(MarketQuote source,
                                               String id,
                                               String displayName,
                                               String unitName,
                                               BigDecimal divisor,
                                               String newSource,
                                               String formula) {
            return new MarketQuote(
                    id,
                    displayName,
                    unitName,
                    source.spot().divide(divisor, 2, RoundingMode.HALF_UP),
                    source.bid().divide(divisor, 2, RoundingMode.HALF_UP),
                    source.ask().divide(divisor, 2, RoundingMode.HALF_UP),
                    source.previousSpot().divide(divisor, 2, RoundingMode.HALF_UP),
                    source.high24h().divide(divisor, 2, RoundingMode.HALF_UP),
                    source.low24h().divide(divisor, 2, RoundingMode.HALF_UP),
                    newSource,
                    source.updatedAtMillis(),
                    source.seeded(),
                    formula
            );
        }

        private static String decimalString(BigDecimal value) {
            return value == null ? "0" : value.stripTrailingZeros().toPlainString();
        }

        private static BigDecimal parseDecimal(String raw) {
            try {
                return new BigDecimal(raw == null || raw.isBlank() ? "0" : raw.trim());
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
    }

    private static int quoteSortOrder(MarketQuote quote) {
        return switch (quote.id()) {
            case GOLD_INGOT -> 0;
            case GOLD_BAR -> 1;
            case SILVER_INGOT -> 2;
            case SILVER_BAR -> 3;
            default -> 50;
        };
    }
}
