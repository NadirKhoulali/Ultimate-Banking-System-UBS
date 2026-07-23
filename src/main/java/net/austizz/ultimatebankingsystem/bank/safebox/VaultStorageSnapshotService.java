package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceScope;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceStatistics;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletBlockEntity;
import net.austizz.ultimatebankingsystem.heist.HeistLootValueService;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultStorageClaimPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultStorageContentPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultStorageMarkerPayload;
import net.austizz.ultimatebankingsystem.shop.ShopMarketPriceService;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/** Produces a bounded, loaded-chunk-only overview of physical storage in each safe claim. */
public final class VaultStorageSnapshotService {
    private static final int MAX_MARKERS_PER_CLAIM = 256;
    private static final int MAX_CONTENT_TYPES = 64;
    private static final int MAX_CLAIMS = 128;

    private VaultStorageSnapshotService() {
    }

    public static List<OwnerPcVaultStorageClaimPayload> build(MinecraftServer server,
                                                               SafeDepositSetupSnapshot setup) {
        if (server == null || setup == null) return List.of();
        List<OwnerPcVaultStorageClaimPayload> claims = new ArrayList<>();
        for (var premise : setup.premises()) {
            if (premise == null) continue;
            for (SafeAreaSnapshot safeArea : premise.safeAreas()) {
                if (safeArea == null || safeArea.bounds() == null) continue;
                claims.add(scanClaim(server, premise.id(), safeArea));
                if (claims.size() >= MAX_CLAIMS) break;
            }
            if (claims.size() >= MAX_CLAIMS) break;
        }
        claims.sort(Comparator.comparing(OwnerPcVaultStorageClaimPayload::premiseId)
                .thenComparing(OwnerPcVaultStorageClaimPayload::claimId));
        return List.copyOf(claims);
    }

    private static OwnerPcVaultStorageClaimPayload scanClaim(MinecraftServer server,
                                                              String premiseId,
                                                              SafeAreaSnapshot safeArea) {
        SafeBlockBounds bounds = safeArea.bounds();
        ServerLevel level = level(server, bounds.dimension());
        boolean fullyLoaded = level != null;
        Map<ColumnKey, List<MetalPalletBlockEntity>> pallets = new LinkedHashMap<>();
        List<ContainerEntry> containers = new ArrayList<>();

        if (level != null) {
            for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
                for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        fullyLoaded = false;
                        continue;
                    }
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        BlockPos pos = blockEntity.getBlockPos();
                        if (!bounds.contains(pos.getX(), pos.getY(), pos.getZ())) continue;
                        if (blockEntity instanceof MetalPalletBlockEntity pallet) {
                            pallets.computeIfAbsent(new ColumnKey(pos.getX(), pos.getZ()), ignored -> new ArrayList<>())
                                    .add(pallet);
                        } else if (blockEntity instanceof Container container && isTrackedContainer(blockEntity)) {
                            containers.add(new ContainerEntry(pos.immutable(), blockEntity, container));
                        }
                    }
                }
            }
        }

        List<OwnerPcVaultStorageMarkerPayload> markers = new ArrayList<>();
        pallets.values().stream()
                .peek(stack -> stack.sort(Comparator.comparingInt(pallet -> pallet.getBlockPos().getY())))
                .sorted(Comparator.comparingInt((List<MetalPalletBlockEntity> stack) -> stack.get(0).getBlockPos().getZ())
                        .thenComparingInt(stack -> stack.get(0).getBlockPos().getX())
                        .thenComparingInt(stack -> stack.get(0).getBlockPos().getY()))
                .map(stack -> palletMarker(server, stack))
                .forEach(markers::add);
        containers.stream()
                .sorted(Comparator.comparingInt((ContainerEntry entry) -> entry.pos().getZ())
                        .thenComparingInt(entry -> entry.pos().getX())
                        .thenComparingInt(entry -> entry.pos().getY()))
                .map(entry -> containerMarker(server, entry))
                .forEach(markers::add);

        markers.sort(Comparator.comparing(OwnerPcVaultStorageMarkerPayload::kind)
                .thenComparingInt(OwnerPcVaultStorageMarkerPayload::z)
                .thenComparingInt(OwnerPcVaultStorageMarkerPayload::x)
                .thenComparingInt(OwnerPcVaultStorageMarkerPayload::y));
        int omitted = Math.max(0, markers.size() - MAX_MARKERS_PER_CLAIM);
        List<OwnerPcVaultStorageMarkerPayload> visible = markers.size() <= MAX_MARKERS_PER_CLAIM
                ? List.copyOf(markers)
                : List.copyOf(markers.subList(0, MAX_MARKERS_PER_CLAIM));

        return new OwnerPcVaultStorageClaimPayload(
                safeArea.id(), premiseId, bounds.dimension(),
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                fullyLoaded, omitted, visible
        );
    }

    private static OwnerPcVaultStorageMarkerPayload palletMarker(MinecraftServer server,
                                                                  List<MetalPalletBlockEntity> stack) {
        MetalPalletBlockEntity first = stack.get(0);
        Aggregate aggregate = new Aggregate();
        for (MetalPalletBlockEntity pallet : stack) {
            IItemHandler handler = pallet.getItemHandler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                aggregate.add(server, handler.getStackInSlot(slot));
            }
        }
        int count = stack.size();
        String indexes = numberedUnits(count);
        String label = count == 1 ? "Metal Pallet" : "Metal Pallets " + indexes;
        String location = count == 1
                ? "Pallet 1 | Y " + first.getBlockPos().getY()
                : "Pallets " + indexes + " | Y " + stack.stream()
                .map(pallet -> Integer.toString(pallet.getBlockPos().getY()))
                .reduce((left, right) -> left + ", " + right).orElse("");
        return aggregate.marker(server, "pallet:" + first.getBlockPos().asLong(), "PALLET", label, location,
                first.getBlockPos(), count);
    }

    private static OwnerPcVaultStorageMarkerPayload containerMarker(MinecraftServer server,
                                                                     ContainerEntry entry) {
        Aggregate aggregate = new Aggregate();
        for (int slot = 0; slot < entry.container().getContainerSize(); slot++) {
            aggregate.add(server, entry.container().getItem(slot));
        }
        String blockName = entry.blockEntity().getBlockState().getBlock().getName().getString();
        String label = blockName.isBlank() ? "Chest Storage" : blockName;
        return aggregate.marker(server, "chest:" + entry.pos().asLong(), "CHEST", label,
                "Chest | Y " + entry.pos().getY(), entry.pos(), 1);
    }

    private static boolean isTrackedContainer(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity
                || blockEntity instanceof BarrelBlockEntity
                || blockEntity instanceof ShulkerBoxBlockEntity) {
            return true;
        }
        String type = blockEntity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return type.contains("chest") || type.contains("barrel") || type.contains("storagecrate");
    }

    static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension == null ? "" : dimension.trim());
        if (server == null || id == null) return null;
        return server.getLevel(RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id));
    }

    private static String numberedUnits(int count) {
        if (count <= 1) return "1";
        StringBuilder result = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            if (index > 1) result.append(index == count ? " & " : ", ");
            result.append(index);
        }
        return result.toString();
    }

    private static long safeAdd(long left, long right) {
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) return Long.MAX_VALUE;
        try {
            return Math.addExact(Math.max(0L, left), Math.max(0L, right));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(Math.max(0L, value), Math.max(0, count));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private record ColumnKey(int x, int z) {
    }

    private record ContainerEntry(BlockPos pos, BlockEntity blockEntity, Container container) {
    }

    private static final class Aggregate {
        private final Map<String, MutableContent> contents = new LinkedHashMap<>();
        private int itemCount;

        private void add(MinecraftServer server, ItemStack stack) {
            if (stack == null || stack.isEmpty()) return;
            int count = Math.max(0, stack.getCount());
            itemCount = (int) Math.min(Integer.MAX_VALUE, (long) itemCount + count);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = id == null ? "minecraft:air" : id.toString();
            contents.computeIfAbsent(itemId, ignored -> new MutableContent(
                            itemId, stack.getHoverName().getString(), stack.copyWithCount(1)))
                    .add(count);
        }

        private OwnerPcVaultStorageMarkerPayload marker(MinecraftServer server,
                                                         String markerId,
                                                         String kind,
                                                         String label,
                                                         String location,
                                                         BlockPos pos,
                                                         int unitCount) {
            List<OwnerPcVaultStorageContentPayload> valuedContents = contents.values().stream()
                    .sorted(Comparator.comparing(MutableContent::displayName, String.CASE_INSENSITIVE_ORDER))
                    .map(content -> content.toPayload(server))
                    .toList();
            List<OwnerPcVaultStorageContentPayload> payloadContents = valuedContents.stream()
                    .limit(MAX_CONTENT_TYPES)
                    .toList();
            boolean valueKnown = valuedContents.stream().allMatch(OwnerPcVaultStorageContentPayload::valueKnown);
            long valueCents = 0L;
            for (OwnerPcVaultStorageContentPayload content : valuedContents) {
                if (content.valueKnown()) valueCents = safeAdd(valueCents, content.totalValueCents());
            }
            return new OwnerPcVaultStorageMarkerPayload(markerId, kind, label, location,
                    pos.getX(), pos.getY(), pos.getZ(), unitCount, itemCount,
                    valueKnown, valueCents, payloadContents);
        }
    }

    private static final class MutableContent {
        private final String itemId;
        private final String displayName;
        private final ItemStack template;
        private int count;

        private MutableContent(String itemId, String displayName, ItemStack template) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.template = template == null ? ItemStack.EMPTY : template.copyWithCount(1);
        }

        private void add(int amount) {
            count = (int) Math.min(Integer.MAX_VALUE, (long) count + Math.max(0, amount));
        }

        private String itemId() {
            return itemId;
        }

        private String displayName() {
            return displayName;
        }

        private int count() {
            return count;
        }

        private OwnerPcVaultStorageContentPayload toPayload(MinecraftServer server) {
            OptionalLong direct = HeistLootValueService.knownValueCents(server, template);
            if (direct.isPresent()) {
                long unit = Math.max(0L, direct.getAsLong());
                return new OwnerPcVaultStorageContentPayload(itemId, displayName, count, true,
                        unit, safeMultiply(unit, count), "DIRECT", 0, 0L);
            }
            ApiShopPriceStatistics market = ShopMarketPriceService.statistics(
                    server, template, ApiShopPriceScope.REGULAR);
            if (market.available()) {
                long median = market.medianPriceCents();
                return new OwnerPcVaultStorageContentPayload(itemId, displayName, count, true,
                        median, safeMultiply(median, count), "MARKET_MEDIAN",
                        market.sampleCount(), market.averagePriceCents());
            }
            return new OwnerPcVaultStorageContentPayload(itemId, displayName, count, false,
                    0L, 0L, "UNPRICED", 0, 0L);
        }
    }
}
