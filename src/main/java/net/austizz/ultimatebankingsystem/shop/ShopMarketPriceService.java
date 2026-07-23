package net.austizz.ultimatebankingsystem.shop;

import net.austizz.ultimatebankingsystem.api.ApiShopPriceScope;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceStatistics;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Persistent physical-shelf price index used by the public API and vault valuation. */
public final class ShopMarketPriceService {
    private static final String INDEX_TAG = "shopMarketShelfIndexV1";
    private static final Map<MinecraftServer, IndexState> STATES = new WeakHashMap<>();

    private ShopMarketPriceService() {
    }

    public static void track(ShelfDisplayBlockEntity shelf) {
        if (!(shelf instanceof BlockEntity blockEntity) || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        IndexState state = state(server);
        ShelfKey key = new ShelfKey(level.dimension().location().toString(), blockEntity.getBlockPos().asLong());
        ShelfSnapshot snapshot = snapshot(shelf);
        synchronized (state) {
            if (snapshot.equals(state.shelves.get(key))) return;
            state.shelves.put(key, snapshot);
            persist(server, state);
        }
    }

    /** Defers removal so chunk unloads keep their persistent market snapshot. */
    public static void shelfRemoved(ShelfDisplayBlockEntity shelf) {
        if (!(shelf instanceof BlockEntity blockEntity) || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos().immutable();
        Block previousBlock = blockEntity.getBlockState().getBlock();
        level.getServer().execute(() -> {
            if (!level.hasChunkAt(pos)
                    || level.getBlockEntity(pos) instanceof ShelfDisplayBlockEntity
                    || level.getBlockState(pos).getBlock() == previousBlock) return;
            IndexState state = state(level.getServer());
            synchronized (state) {
                if (state.shelves.remove(new ShelfKey(level.dimension().location().toString(), pos.asLong())) != null) {
                    persist(level.getServer(), state);
                }
            }
        });
    }

    public static ApiShopPriceStatistics statistics(MinecraftServer server,
                                                     ItemStack item,
                                                     ApiShopPriceScope requestedScope) {
        ApiShopPriceScope scope = requestedScope == null ? ApiShopPriceScope.REGULAR : requestedScope;
        ResourceLocation itemKey = item == null || item.isEmpty()
                ? null
                : BuiltInRegistries.ITEM.getKey(item.getItem());
        String itemId = itemKey == null ? "" : itemKey.toString();
        if (server == null || itemId.isBlank()) return ApiShopPriceStatistics.unavailable(itemId, scope);

        CentralBank centralBank = BankManager.getCentralBank(server);
        Set<UUID> registered = new HashSet<>();
        if (centralBank != null) {
            for (ShopService.ShopSummary summary : ShopService.listAllShopSummaries(centralBank)) {
                if (summary != null && summary.shopId() != null) registered.add(summary.shopId());
            }
        }
        long gameTime = server.overworld() == null ? 0L : server.overworld().getGameTime();
        List<Long> prices = new ArrayList<>();
        IndexState state = state(server);
        synchronized (state) {
            for (ShelfSnapshot shelf : state.shelves.values()) {
                if (!includeShelf(shelf, scope, centralBank, registered, gameTime)) continue;
                for (SlotPrice slot : shelf.slots()) {
                    if (itemId.equals(slot.itemId())) prices.add(Math.max(0L, slot.priceCents()));
                }
            }
        }
        return summarize(itemId, scope, prices);
    }

    static ApiShopPriceStatistics summarize(String itemId,
                                             ApiShopPriceScope scope,
                                             List<Long> samples) {
        if (samples == null || samples.isEmpty()) return ApiShopPriceStatistics.unavailable(itemId, scope);
        List<Long> prices = samples.stream().map(price -> Math.max(0L, price == null ? 0L : price))
                .sorted(Comparator.naturalOrder()).toList();
        BigInteger sum = BigInteger.ZERO;
        for (long price : prices) sum = sum.add(BigInteger.valueOf(price));
        long average = sum.divide(BigInteger.valueOf(prices.size())).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        int middle = prices.size() / 2;
        long median = prices.size() % 2 == 1
                ? prices.get(middle)
                : BigInteger.valueOf(prices.get(middle - 1)).add(BigInteger.valueOf(prices.get(middle)))
                .divide(BigInteger.TWO).longValue();
        return new ApiShopPriceStatistics(true, itemId, scope, prices.size(), median, average,
                prices.getFirst(), prices.getLast());
    }

    private static boolean includeShelf(ShelfSnapshot shelf,
                                        ApiShopPriceScope scope,
                                        CentralBank centralBank,
                                        Set<UUID> registered,
                                        long gameTime) {
        if (!shelf.shopMode()) return false;
        return switch (scope) {
            case INCLUDE_ALL -> true;
            case ALL_SHELVES_EXCLUDE_CREATIVE -> !shelf.creative();
            case CREATIVE_ONLY -> shelf.creative();
            case REGULAR -> !shelf.creative()
                    && shelf.shopId() != null
                    && registered.contains(shelf.shopId())
                    && ShopService.isShopOpenForShopping(centralBank, shelf.shopId(), gameTime);
        };
    }

    private static ShelfSnapshot snapshot(ShelfDisplayBlockEntity shelf) {
        List<SlotPrice> slots = new ArrayList<>();
        for (int slot = 0; slot < Math.max(0, shelf.getSlotCount()); slot++) {
            ItemStack item = shelf.getDisplayItem(slot);
            if (item == null || item.isEmpty()) continue;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item.getItem());
            if (key != null) slots.add(new SlotPrice(key.toString(), Math.max(0L, shelf.getSlotPrice(slot))));
        }
        return new ShelfSnapshot(shelf.getShopId(), shelf.isCreativeShelf(), shelf.isShopMode(), List.copyOf(slots));
    }

    private static IndexState state(MinecraftServer server) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(server, ShopMarketPriceService::load);
        }
    }

    private static IndexState load(MinecraftServer server) {
        IndexState state = new IndexState();
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) return state;
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        ListTag entries = metadata.getList(INDEX_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)) continue;
            String dimension = entry.getString("dimension");
            long pos = entry.getLong("pos");
            UUID shopId = entry.hasUUID("shop") ? entry.getUUID("shop") : null;
            List<SlotPrice> slots = new ArrayList<>();
            for (Tag slotRaw : entry.getList("slots", Tag.TAG_COMPOUND)) {
                if (slotRaw instanceof CompoundTag slot) {
                    String itemId = slot.getString("item");
                    if (!itemId.isBlank()) slots.add(new SlotPrice(itemId, Math.max(0L, slot.getLong("price"))));
                }
            }
            if (!dimension.isBlank()) {
                state.shelves.put(new ShelfKey(dimension, pos), new ShelfSnapshot(
                        shopId, entry.getBoolean("creative"), entry.getBoolean("shopMode"), List.copyOf(slots)));
            }
        }
        return state;
    }

    private static void persist(MinecraftServer server, IndexState state) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) return;
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        ListTag entries = new ListTag();
        state.shelves.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(indexed -> {
                    ShelfKey key = indexed.getKey();
                    ShelfSnapshot shelf = indexed.getValue();
                    CompoundTag entry = new CompoundTag();
                    entry.putString("dimension", key.dimension());
                    entry.putLong("pos", key.pos());
                    if (shelf.shopId() != null) entry.putUUID("shop", shelf.shopId());
                    entry.putBoolean("creative", shelf.creative());
                    entry.putBoolean("shopMode", shelf.shopMode());
                    ListTag slots = new ListTag();
                    for (SlotPrice price : shelf.slots()) {
                        CompoundTag slot = new CompoundTag();
                        slot.putString("item", price.itemId());
                        slot.putLong("price", price.priceCents());
                        slots.add(slot);
                    }
                    entry.put("slots", slots);
                    entries.add(entry);
                });
        metadata.put(INDEX_TAG, entries);
        centralBank.putBankMetadata(centralBank.getBankId(), metadata);
    }

    private static final class IndexState {
        private final Map<ShelfKey, ShelfSnapshot> shelves = new LinkedHashMap<>();
    }

    private record ShelfKey(String dimension, long pos) implements Comparable<ShelfKey> {
        @Override
        public int compareTo(ShelfKey other) {
            int dimensionOrder = dimension.compareTo(other.dimension);
            return dimensionOrder != 0 ? dimensionOrder : Long.compare(pos, other.pos);
        }
    }

    private record ShelfSnapshot(UUID shopId, boolean creative, boolean shopMode, List<SlotPrice> slots) {
    }

    private record SlotPrice(String itemId, long priceCents) {
    }
}
