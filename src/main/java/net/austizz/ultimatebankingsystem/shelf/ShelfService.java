package net.austizz.ultimatebankingsystem.shelf;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.ShopSellingTableLargeBlock;
import net.austizz.ultimatebankingsystem.block.custom.TallWallShelfBlock;
import net.austizz.ultimatebankingsystem.block.custom.ModularWallDisplayBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.GlassCounterDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ModularWallDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.TallWallShelfBlockEntity;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShelfService {
    private static final int MAX_CONNECTED_SHELVES = 64;
    /**
     * Tracks temporary game mode overrides while a player is in the item positioner editor.
     * Key: player UUID, Value: original game mode to restore when editor closes.
     */
    private static final Map<UUID, GameType> POSITIONER_PREVIOUS_MODE = new ConcurrentHashMap<>();

    private ShelfService() {
    }

    public static boolean isShelf(BlockState state) {
        return state != null
                && (state.is(ModBlocks.TALL_WALL_SHELF.get())
                || state.is(ModBlocks.SHOP_SHELF.get())
                || state.is(ModBlocks.MODULAR_WALL_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get())
                || state.is(ModBlocks.GLASS_COUNTER_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get())
                || state.is(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get())
                || state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get())
                || state.is(ModBlocks.SHOP_SELLING_TABLE.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get())
                || state.is(ModBlocks.SHOP_SELLING_TABLE_LARGE.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_LARGE.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get()));
    }

    /**
     * Enables temporary spectator mode during shelf item positioning so the player body cannot block the editor camera.
     */
    public static void beginPositionerSpectator(ServerPlayer player) {
        if (player == null || player.gameMode == null) {
            return;
        }
        UUID playerId = player.getUUID();
        GameType current = player.gameMode.getGameModeForPlayer();
        POSITIONER_PREVIOUS_MODE.putIfAbsent(playerId, current);
        if (current != GameType.SPECTATOR) {
            player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
        }
    }

    /**
     * Restores player game mode after leaving the shelf item positioner.
     */
    public static void endPositionerSpectator(ServerPlayer player) {
        if (player == null || player.gameMode == null) {
            return;
        }
        GameType previous = POSITIONER_PREVIOUS_MODE.remove(player.getUUID());
        if (previous == null) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() != previous) {
            player.gameMode.changeGameModeForPlayer(previous);
        }
    }

    public static boolean isCreativeShelf(BlockState state) {
        return state != null
                && (state.is(ModBlocks.TALL_WALL_SHELF.get())
                || state.is(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get()));
    }

    public static boolean isStockShelf(BlockState state) {
        return state != null
                && (state.is(ModBlocks.SHOP_SHELF.get())
                || state.is(ModBlocks.MODULAR_WALL_DISPLAY.get())
                || state.is(ModBlocks.GLASS_COUNTER_DISPLAY.get())
                || state.is(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get()));
    }

    public static boolean isGlassCounter(BlockState state) {
        return state != null
                && (state.is(ModBlocks.GLASS_COUNTER_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get())
                || state.is(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get())
                || state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get()));
    }

    public static boolean isSellingTable(BlockState state) {
        return state != null
                && (state.is(ModBlocks.SHOP_SELLING_TABLE.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get())
                || state.is(ModBlocks.SHOP_SELLING_TABLE_LARGE.get())
                || state.is(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.INVISIBLE_DISPLAY_LARGE.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get())
                || state.is(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get()));
    }

    public static BlockPos toLowerShelfPos(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return pos;
        }
        BlockState state = level.getBlockState(pos);
        if (!isShelf(state)) {
            return pos;
        }
        if (isSellingTable(state)
                && state.hasProperty(ShopSellingTableLargeBlock.PART)) {
            return ShopSellingTableLargeBlock.getMasterPos(state, pos);
        }
        if ((state.is(ModBlocks.MODULAR_WALL_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get()))
                && state.hasProperty(ModularWallDisplayBlock.PART)) {
            return ModularWallDisplayBlock.getMasterPos(state, pos);
        }
        if (state.hasProperty(TallWallShelfBlock.HALF)
                && state.getValue(TallWallShelfBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    public static ShelfDisplayBlockEntity getDisplayEntity(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        BlockPos lower = toLowerShelfPos(level, pos);
        if (level.getBlockEntity(lower) instanceof ShelfDisplayBlockEntity shelfEntity) {
            return shelfEntity;
        }
        return null;
    }

    public static TallWallShelfBlockEntity getShelfEntity(Level level, BlockPos pos) {
        ShelfDisplayBlockEntity display = getDisplayEntity(level, pos);
        return display instanceof TallWallShelfBlockEntity shelfEntity ? shelfEntity : null;
    }

    public static boolean canManageShelf(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null || player == null) {
            return false;
        }
        if (player.hasPermissions(3)) {
            return true;
        }

        ShelfDisplayBlockEntity shelfEntity = getDisplayEntity(level, pos);
        if (shelfEntity == null) {
            return false;
        }

        UUID owner = shelfEntity.getOwnerUuid();
        UUID shopId = shelfEntity.getShopId();
        UUID playerId = player.getUUID();

        // Shop-linked displays are managed by build-capable shop roles (OWNER/MANAGER/BUILDER)
        // so delegated plot permissions are actually honored for shelf inventory operations.
        if (shopId != null) {
            return hasBuildPermissionForShelf(level, shelfEntity, playerId);
        }

        // Unlinked/legacy displays fall back to direct owner metadata.
        if (owner != null) {
            if (owner.equals(playerId)) {
                return true;
            }
            return false;
        }

        // Owner-less unlinked shelves can still be recovered by linked-shop ownership when metadata exists.
        return hasBuildPermissionForShelf(level, shelfEntity, playerId);
    }

    private static boolean hasBuildPermissionForShelf(Level level,
                                                      ShelfDisplayBlockEntity shelfEntity,
                                                      UUID playerId) {
        if (level == null || shelfEntity == null || playerId == null) {
            return false;
        }
        UUID shopId = shelfEntity.getShopId();
        if (shopId == null || level.getServer() == null) {
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(level.getServer());
        if (centralBank == null) {
            return false;
        }
        return ShopService.canBuildInShop(centralBank, playerId, shopId);
    }

    public static List<BlockPos> collectConnectedShelves(Level level, BlockPos origin) {
        List<BlockPos> out = new ArrayList<>();
        if (level == null || origin == null) {
            return out;
        }

        BlockPos start = toLowerShelfPos(level, origin);
        BlockState startState = level.getBlockState(start);
        if (!isShelf(startState)) {
            return out;
        }
        ShelfFamily rootFamily = familyOf(startState);

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty() && out.size() < MAX_CONNECTED_SHELVES) {
            BlockPos current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            BlockState state = level.getBlockState(current);
            if (!isShelf(state)) {
                continue;
            }
            if (familyOf(state) != rootFamily) {
                continue;
            }
            if (state.hasProperty(TallWallShelfBlock.HALF)
                    && state.getValue(TallWallShelfBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            out.add(current);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                BlockState nextState = level.getBlockState(next);
                if (!isShelf(nextState)) {
                    continue;
                }
                if (familyOf(nextState) != rootFamily) {
                    continue;
                }
                BlockPos nextLower = toLowerShelfPos(level, next);
                if (!visited.contains(nextLower)) {
                    queue.add(nextLower);
                }
            }
        }

        out.sort(Comparator.comparingInt((BlockPos p) -> p.getY())
                .thenComparingInt(p -> p.getZ())
                .thenComparingInt(p -> p.getX()));
        return out;
    }

    public static int resolveSlotByHitY(BlockPos lowerPos, double hitY) {
        if (lowerPos == null) {
            return 0;
        }
        double localY = hitY - lowerPos.getY();
        if (localY >= 1.32D) {
            return 2;
        }
        if (localY >= 0.86D) {
            return 1;
        }
        return 0;
    }

    public static int resolveSlotByHit(Level level,
                                       BlockPos shelfPos,
                                       double hitX,
                                       double hitY,
                                       double hitZ) {
        if (level == null || shelfPos == null) {
            return 0;
        }
        BlockPos lowerPos = toLowerShelfPos(level, shelfPos);
        BlockState state = level.getBlockState(lowerPos);
        if (isSellingTable(state)) {
            return 0;
        }
        if (isGlassCounter(state)) {
            double localY16 = (hitY - lowerPos.getY()) * 16.0D;
            // One item per glass panel/shelf. Pick nearest panel center by vertical hit.
            final double[] rowCenters = new double[]{5.5D, 10.5D, 15.5D, 20.5D};
            int nearestRow = 0;
            double nearestDist = Math.abs(localY16 - rowCenters[0]);
            for (int i = 1; i < rowCenters.length; i++) {
                double dist = Math.abs(localY16 - rowCenters[i]);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestRow = i;
                }
            }
            return clamp(nearestRow, 0, GlassCounterDisplayBlockEntity.SHELF_ROWS - 1);
        }
        if (state.is(ModBlocks.MODULAR_WALL_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get())) {
            // New modular wall uses 2 rows; layout can be compact (1 per row) or expanded (2 per row).
            ShelfDisplayBlockEntity display = getDisplayEntity(level, lowerPos);
            int visibleSlots = Math.max(1, display == null ? 2 : display.getSlotCount());
            int row = (hitY - lowerPos.getY()) >= 0.5D ? 0 : 1; // Row 0 = top, row 1 = bottom.
            if (visibleSlots <= 2) {
                return row;
            }

            double axis = modularHorizontalAxis(state, lowerPos, hitX, hitZ);
            int col = axis >= 0.0D ? 1 : 0; // Left-to-right split for expanded mode.
            return row * 2 + col;
        }
        return resolveSlotByHitY(lowerPos, hitY);
    }

    public static boolean addShelfItemToBasket(ServerPlayer player, BlockPos shelfPos, int slot) {
        return addShelfItemToBasket(player, shelfPos, slot, false);
    }

    /**
     * Adds either one item or a whole stack from a configured shelf slot to the active basket.
     */
    public static boolean addShelfItemToBasket(ServerPlayer player, BlockPos shelfPos, int slot, boolean stackMode) {
        if (player == null) {
            return false;
        }
        Level level = player.level();
        ShelfDisplayBlockEntity shelf = getDisplayEntity(level, shelfPos);
        if (shelf == null) {
            return false;
        }
        if (!shelf.isShopMode()) {
            player.sendSystemMessage(Component.literal("This display is in regular mode. Use empty hand right-click to take items."));
            return false;
        }
        // Shopping interactions are disabled while a linked shop is closed.
        if (!ensureShopOpenForShopping(player, shelf.getShopId())) {
            return false;
        }
        slot = clamp(slot, 0, shelf.getSlotCount() - 1);

        ItemStack display = shelf.getDisplayItem(slot);
        long priceCents = shelf.getSlotPrice(slot);
        if (display.isEmpty() || priceCents < 0L) {
            player.sendSystemMessage(Component.literal("This shelf slot is not configured."));
            return false;
        }
        String blockedReason = ShelfDisplayRules.blockedReason(display);
        if (blockedReason != null) {
            player.sendSystemMessage(Component.literal(blockedReason));
            return false;
        }
        if (!shelf.isCreativeShelf() && shelf.getSlotStock(slot) <= 0) {
            player.sendSystemMessage(Component.literal("This shelf slot is out of stock."));
            return false;
        }

        ItemStack basket = findBasketInHands(player);
        if (basket.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold a shopping basket to add items."));
            return false;
        }

        String sourceKey = buildShelfSlotSourceKey(level, shelfPos, slot);
        int targetAdds = stackMode ? Math.max(1, display.getMaxStackSize()) : 1;
        if (!shelf.isCreativeShelf()) {
            targetAdds = Math.min(targetAdds, Math.max(0, shelf.getSlotStock(slot)));
        }
        if (targetAdds <= 0) {
            player.sendSystemMessage(Component.literal("This shelf slot is out of stock."));
            return false;
        }

        int added = 0;
        int latestQty = 0;
        for (int i = 0; i < targetAdds; i++) {
            if (!shelf.isCreativeShelf() && !shelf.consumeOneStock(slot)) {
                break;
            }
            int count = ShelfCartService.addItem(basket, display, priceCents, sourceKey);
            if (count <= 0) {
                if (!shelf.isCreativeShelf()) {
                    // Roll back any stock consumed this loop iteration.
                    shelf.addStock(slot, 1);
                }
                break;
            }
            latestQty = count;
            added++;
        }
        if (added <= 0) {
            player.sendSystemMessage(Component.literal("Could not add that item right now."));
            return false;
        }

        String unitLabel = priceCents == 0L ? "Free" : "$" + ShelfPrice.abbreviateFromCents(priceCents);
        String stockLabel = shelf.isCreativeShelf() ? "" : " | Stock: " + shelf.getSlotStock(slot);
        if (stackMode) {
            player.sendSystemMessage(Component.literal("Added stack x" + added + " of ")
                    .append(display.getHoverName())
                    .append(Component.literal(". Basket qty: " + latestQty + " | Unit: " + unitLabel + stockLabel)));
        } else {
            player.sendSystemMessage(Component.literal("Added ")
                    .append(display.getHoverName())
                    .append(Component.literal(" to basket. Qty: " + latestQty + " | Unit: "
                            + unitLabel
                            + stockLabel)));
        }
        return true;
    }

    public static boolean removeShelfItemFromBasket(ServerPlayer player, BlockPos shelfPos, int slot) {
        return removeShelfItemFromBasket(player, shelfPos, slot, false);
    }

    /**
     * Removes either one item or a whole stack from basket back into the shelf stock.
     */
    public static boolean removeShelfItemFromBasket(ServerPlayer player, BlockPos shelfPos, int slot, boolean stackMode) {
        if (player == null) {
            return false;
        }
        Level level = player.level();
        ShelfDisplayBlockEntity shelf = getDisplayEntity(level, shelfPos);
        if (shelf == null) {
            return false;
        }
        if (!shelf.isShopMode()) {
            player.sendSystemMessage(Component.literal("This display is in regular mode. Basket remove is disabled."));
            return false;
        }
        // Shopping interactions are disabled while a linked shop is closed.
        if (!ensureShopOpenForShopping(player, shelf.getShopId())) {
            return false;
        }
        slot = clamp(slot, 0, shelf.getSlotCount() - 1);

        ItemStack display = shelf.getDisplayItem(slot);
        long priceCents = shelf.getSlotPrice(slot);
        if (display.isEmpty() || priceCents < 0L) {
            player.sendSystemMessage(Component.literal("This shelf slot is not configured."));
            return false;
        }

        ItemStack basket = findBasketInHands(player);
        if (basket.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold a shopping basket to remove items."));
            return false;
        }

        String sourceKey = buildShelfSlotSourceKey(level, shelfPos, slot);
        int targetRemovals = stackMode ? Math.max(1, display.getMaxStackSize()) : 1;
        int removed = 0;
        int remaining = -1;
        for (int i = 0; i < targetRemovals; i++) {
            int result = ShelfCartService.removeItem(basket, display, priceCents, sourceKey);
            if (result < 0) {
                break;
            }
            remaining = result;
            removed++;
            if (result == 0) {
                break;
            }
        }
        if (removed <= 0) {
            player.sendSystemMessage(Component.literal("That item is not currently in your basket."));
            return false;
        }
        if (remaining <= 0) {
            player.sendSystemMessage(Component.literal("Removed from basket: ").append(display.getHoverName()));
        } else if (stackMode) {
            player.sendSystemMessage(Component.literal("Removed stack x" + removed + " of ")
                    .append(display.getHoverName())
                    .append(Component.literal(". Remaining in basket: " + remaining)));
        } else {
            player.sendSystemMessage(Component.literal("Removed one ")
                    .append(display.getHoverName())
                    .append(Component.literal(". Remaining in basket: " + remaining)));
        }
        if (!shelf.isCreativeShelf()) {
            shelf.addStock(slot, removed);
        }
        return true;
    }

    public static ItemStack findBasketInHands(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (ShelfCartService.isBasketStack(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (ShelfCartService.isBasketStack(off)) {
            return off;
        }
        if (player instanceof ServerPlayer serverPlayer
                && ShelfBasketSessionService.hasActiveSession(serverPlayer.getUUID())) {
            ItemStack sessionBasket = ShelfBasketSessionService.findSessionBasket(serverPlayer);
            if (!sessionBasket.isEmpty()) {
                return sessionBasket;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns whether customer-facing shopping interactions are currently allowed.
     * Shop closing rules are enforced from server time and are shared across shelf,
     * basket-holder and cashier flows.
     */
    public static boolean ensureShopOpenForShopping(ServerPlayer player, UUID shopId) {
        if (player == null || shopId == null) {
            return true;
        }
        if (player.getServer() == null) {
            return true;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return true;
        }
        boolean open = ShopService.isShopOpenForShopping(centralBank, shopId, player.level().getGameTime());
        if (open) {
            return true;
        }
        player.sendSystemMessage(Component.literal("Store is currently closed. Shopping is unavailable right now."));
        return false;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Returns signed horizontal axis across the modular wall width.
     * Negative = left side, positive = right side from the viewer-facing front.
     */
    private static double modularHorizontalAxis(BlockState state, BlockPos origin, double hitX, double hitZ) {
        Direction facing = Direction.NORTH;
        if (state != null && state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            facing = state.getValue(HorizontalDirectionalBlock.FACING);
        }
        Direction right = facing.getClockWise();
        double dx = hitX - origin.getX();
        double dz = hitZ - origin.getZ();
        return dx * right.getStepX() + dz * right.getStepZ();
    }

    private static String buildShelfSlotSourceKey(Level level, BlockPos shelfPos, int slot) {
        if (shelfPos == null) {
            return "";
        }
        String dim = "minecraft:overworld";
        if (level != null && level.dimension() != null && level.dimension().location() != null) {
            dim = level.dimension().location().toString();
        }
        return dim + ";" + shelfPos.getX() + ";" + shelfPos.getY() + ";" + shelfPos.getZ() + ";" + Math.max(0, slot);
    }

    private static ShelfFamily familyOf(BlockState state) {
        if (isGlassCounter(state)) {
            return ShelfFamily.COUNTER;
        }
        if (isSellingTable(state)) {
            return ShelfFamily.TABLE;
        }
        if (isShelf(state)) {
            return ShelfFamily.WALL;
        }
        return ShelfFamily.UNKNOWN;
    }

    private enum ShelfFamily {
        WALL,
        TABLE,
        COUNTER,
        UNKNOWN
    }
}
