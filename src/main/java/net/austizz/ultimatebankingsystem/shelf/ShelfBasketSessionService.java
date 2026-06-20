package net.austizz.ultimatebankingsystem.shelf;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBasketHolderBlockEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.item.WalletData;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShelfBasketSessionService {
    public record ActionResult(boolean success, String message) {}

    private static final int SESSION_BASKET_SLOT = 4;
    private static final String TAG_SESSION_ID = "ubs_basket_session_id";
    private static final String TAG_OWNER_ID = "ubs_basket_owner_id";
    private static final String TAG_SHOP_ID = "ubs_basket_shop_id";
    private static final String TAG_HOLDER_ID = "ubs_basket_holder_id";
    private static final String TAG_HOLDER_DIM = "ubs_basket_holder_dim";
    private static final String TAG_HOLDER_X = "ubs_basket_holder_x";
    private static final String TAG_HOLDER_Y = "ubs_basket_holder_y";
    private static final String TAG_HOLDER_Z = "ubs_basket_holder_z";
    private static final String TAG_PERSIST_BACKUP = "ubs_basket_backup";
    private static final String TAG_BACKUP_INV = "inventory";
    private static final String TAG_BACKUP_PAYMENTS = "payment_slots";
    private static final String TAG_BACKUP_SELECTED = "selected_slot";
    private static final String TAG_BACKUP_BASKET = "basket_data";

    private static final ConcurrentHashMap<UUID, BasketSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> FORCED_DEATH_BYPASS = ConcurrentHashMap.newKeySet();

    private ShelfBasketSessionService() {
    }

    private static final class BasketSession {
        private final UUID playerId;
        private final UUID ownerId;
        private final UUID shopId;
        private final UUID holderId;
        private final UUID sessionId;
        private final String holderDimension;
        private final BlockPos holderPos;
        private final List<ItemStack> inventorySnapshot;
        // Snapshot of original payment-item slots for collision-safe restore.
        private final boolean[] paymentSlotSnapshot;
        private final int selectedSlotSnapshot;
        private CompoundTag latestBasketData;

        private BasketSession(UUID playerId,
                              UUID ownerId,
                              UUID shopId,
                              UUID holderId,
                              UUID sessionId,
                              String holderDimension,
                              BlockPos holderPos,
                              List<ItemStack> inventorySnapshot,
                              boolean[] paymentSlotSnapshot,
                              int selectedSlotSnapshot) {
            this.playerId = playerId;
            this.ownerId = ownerId;
            this.shopId = shopId;
            this.holderId = holderId;
            this.sessionId = sessionId;
            this.holderDimension = holderDimension;
            this.holderPos = holderPos;
            this.inventorySnapshot = inventorySnapshot;
            this.paymentSlotSnapshot = paymentSlotSnapshot;
            this.selectedSlotSnapshot = selectedSlotSnapshot;
            this.latestBasketData = new CompoundTag();
        }
    }

    public static boolean hasActiveSession(UUID playerId) {
        return playerId != null && ACTIVE_SESSIONS.containsKey(playerId);
    }

    public static ActionResult startSessionFromHolder(ServerPlayer player, ShoppingBasketHolderBlockEntity holder) {
        if (player == null || holder == null) {
            return new ActionResult(false, "Basket holder is unavailable.");
        }
        if (hasActiveSession(player.getUUID())) {
            return new ActionResult(false, "You already have an active shopping basket.");
        }

        UUID ownerId = holder.getOwnerId();
        UUID shopId = holder.getShopId();
        UUID holderId = holder.getHolderId();
        if (ownerId == null || shopId == null || holderId == null) {
            return new ActionResult(false, "Basket holder is not linked to a valid shop.");
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return new ActionResult(false, "Server context is unavailable.");
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return new ActionResult(false, "Bank data is unavailable.");
        }

        String playerDim = player.level().dimension().location().toString();
        UUID currentShop = ShopService.resolveOwnerShopAtPos(
                centralBank,
                ownerId,
                playerDim,
                player.blockPosition()
        );
        if (!shopId.equals(currentShop)) {
            return new ActionResult(false, "You must be inside this shop to take a basket.");
        }

        List<ItemStack> snapshot = snapshotInventory(player);
        boolean[] paymentSlots = snapshotPaymentSlots(snapshot);
        int selected = Math.max(0, Math.min(8, player.getInventory().selected));
        clearInventoryExceptPaymentItems(player);

        UUID sessionId = UUID.randomUUID();
        BasketSession session = new BasketSession(
                player.getUUID(),
                ownerId,
                shopId,
                holderId,
                sessionId,
                holder.getLevel() == null
                        ? "minecraft:overworld"
                        : holder.getLevel().dimension().location().toString(),
                holder.getBlockPos(),
                snapshot,
                paymentSlots,
                selected
        );
        ItemStack basket = createSessionBasket(session, session.latestBasketData);
        placeSessionBasket(player, basket);
        player.getInventory().selected = SESSION_BASKET_SLOT;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        ACTIVE_SESSIONS.put(player.getUUID(), session);
        persistSessionBackup(player, session);
        return new ActionResult(true, "Shopping basket issued (" + shortId(sessionId) + "). Return it to this holder or complete checkout.");
    }

    public static ActionResult tryReturnBasket(ServerPlayer player, ShoppingBasketHolderBlockEntity holder) {
        if (player == null || holder == null) {
            return new ActionResult(false, "Basket holder is unavailable.");
        }
        BasketSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ActionResult(false, "No active shopping basket session.");
        }
        UUID holderId = holder.getHolderId();
        if (holderId == null || !holderId.equals(session.holderId)) {
            return new ActionResult(false, "This basket belongs to another holder: " + holderLabel(session) + ".");
        }

        ItemStack basket = findOrCreateSessionBasket(player, session);
        int units = basket.isEmpty() ? 0 : ShelfCartService.getTotalUnits(basket);
        int returned = endSession(player, session, true, true, "Shopping basket returned.");
        if (units > 0 && returned > 0) {
            return new ActionResult(true, "Shopping basket returned. " + returned + " item(s) were returned to shelf stock.");
        }
        return new ActionResult(true, "Shopping basket returned.");
    }

    public static boolean completeCheckout(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        BasketSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        endSession(player, session, true, false, "Checkout complete.");
        player.sendSystemMessage(UbsTranslations.literal("§aYour original inventory has been restored."));
        return true;
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        BasketSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        endSession(player, session, true, true, "Shopping basket closed (logout).");
    }

    public static void onLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        BasketSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session != null) {
            // Keep session metadata healthy if a player reconnects while the in-memory session still exists.
            findOrCreateSessionBasket(player, session);
            persistSessionBackup(player, session);
            return;
        }
        recoverFromPersistentBackup(player);
    }

    public static boolean interceptDeath(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (FORCED_DEATH_BYPASS.remove(playerId)) {
            return false;
        }
        BasketSession session = ACTIVE_SESSIONS.get(playerId);
        if (session == null) {
            return false;
        }

        endSession(player, session, true, true, "Shopping basket session closed before death.");
        String shopName = resolveShopName(player.getServer(), session.ownerId, session.shopId);
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(player.getScoreboardName())
                            .append(UbsTranslations.literal(" died of unknown cause inside ["))
                            .append(Component.literal(shopName))
                            .append(Component.literal("]")),
                    false
            );
            FORCED_DEATH_BYPASS.add(playerId);
            server.execute(() -> {
                if (player.isAlive() && !player.isRemoved()) {
                    player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
                }
            });
        }
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ACTIVE_SESSIONS.isEmpty()) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        List<UUID> ids = new ArrayList<>(ACTIVE_SESSIONS.keySet());
        for (UUID playerId : ids) {
            BasketSession session = ACTIVE_SESSIONS.get(playerId);
            if (session == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                ACTIVE_SESSIONS.remove(playerId);
                continue;
            }

            ItemStack basket = findOrCreateSessionBasket(player, session);
            if (!basket.isEmpty()) {
                session.latestBasketData = ShelfCartService.extractBasketData(basket);
            }
            persistSessionBackup(player, session);

            ServerLevel holderLevel = server.getLevel(serverLevelKey(session.holderDimension));
            if (holderLevel == null || !(holderLevel.getBlockEntity(session.holderPos) instanceof ShoppingBasketHolderBlockEntity holderEntity)
                    || holderEntity.getHolderId() == null
                    || !holderEntity.getHolderId().equals(session.holderId)) {
                endSession(player, session, true, true, "Shopping basket holder was removed. Inventory restored.");
                continue;
            }

            if (centralBank == null) {
                endSession(player, session, true, true, "Bank data is unavailable. Inventory restored.");
                continue;
            }
            UUID insideShop = ShopService.resolveOwnerShopAtPos(
                    centralBank,
                    session.ownerId,
                    player.level().dimension().location().toString(),
                    player.blockPosition()
            );
            if (insideShop == null || !insideShop.equals(session.shopId)) {
                endSession(player, session, true, true, "You left the shop area. Basket was returned and inventory restored.");
            }
        }
    }

    public static ItemStack findSessionBasket(ServerPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        BasketSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ItemStack.EMPTY;
        }
        return findOrCreateSessionBasket(player, session);
    }

    private static int endSession(ServerPlayer player,
                                  BasketSession session,
                                  boolean restoreInventory,
                                  boolean restockBasketItems,
                                  String reason) {
        if (player == null || session == null) {
            return 0;
        }
        ItemStack basket = findMatchingSessionBasket(player, session);
        if (!basket.isEmpty()) {
            session.latestBasketData = ShelfCartService.extractBasketData(basket);
        }

        int returnedToStock = 0;
        if (restockBasketItems) {
            returnedToStock = restockFromBasketData(player.getServer(), session.latestBasketData);
        }
        ACTIVE_SESSIONS.remove(player.getUUID());
        clearSessionBackup(player);

        if (restoreInventory) {
            restoreInventory(player, session.inventorySnapshot, session.paymentSlotSnapshot, session.selectedSlotSnapshot);
        } else {
            clearInventory(player);
        }
        if (reason != null && !reason.isBlank()) {
            player.sendSystemMessage(UbsTranslations.literal(reason).withStyle(ChatFormatting.YELLOW));
        }
        return returnedToStock;
    }

    private static int restockFromBasketData(MinecraftServer server, CompoundTag basketData) {
        if (server == null || basketData == null || basketData.isEmpty()) {
            return 0;
        }
        ItemStack tempBasket = new ItemStack(ModBlocks.SHOPPING_BASKET.get().asItem());
        ShelfCartService.applyBasketData(tempBasket, basketData);
        List<ShelfCartService.BasketEntryView> entries = ShelfCartService.getEntries(tempBasket);
        int restored = 0;
        for (ShelfCartService.BasketEntryView entry : entries) {
            if (entry == null || entry.quantity() <= 0 || entry.source() == null || entry.source().isBlank()) {
                continue;
            }
            ShelfSource source = parseSource(entry.source());
            if (source == null) {
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(source.dimensionId));
            if (level == null) {
                continue;
            }
            ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(level, source.pos);
            if (shelf == null || shelf.isCreativeShelf()) {
                continue;
            }
            int slot = Math.max(0, Math.min(shelf.getSlotCount() - 1, source.slot));
            ItemStack display = shelf.getDisplayItem(slot);
            if (display.isEmpty() || !ItemStackDataCompat.sameItemSameComponents(display, entry.stack())) {
                continue;
            }
            int before = Math.max(0, shelf.getSlotStock(slot));
            shelf.addStock(slot, entry.quantity());
            int after = Math.max(0, shelf.getSlotStock(slot));
            restored += Math.max(0, after - before);
        }
        return restored;
    }

    private record ShelfSource(String dimensionId, BlockPos pos, int slot) {}

    private static ShelfSource parseSource(String sourceRaw) {
        if (sourceRaw == null || sourceRaw.isBlank()) {
            return null;
        }
        String[] parts = sourceRaw.trim().split(";");
        if (parts.length < 5) {
            return null;
        }
        try {
            String dim = parts[0].trim();
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            int slot = Integer.parseInt(parts[4].trim());
            return new ShelfSource(dim.isBlank() ? "minecraft:overworld" : dim, new BlockPos(x, y, z), slot);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemStack createSessionBasket(BasketSession session, CompoundTag basketData) {
        ItemStack basket = new ItemStack(ModBlocks.SHOPPING_BASKET.get().asItem());
        ShelfCartService.applyBasketData(basket, basketData == null ? new CompoundTag() : basketData);
        stampBasketMetadata(basket, session);
        return basket;
    }

    private static ItemStack findOrCreateSessionBasket(ServerPlayer player, BasketSession session) {
        if (player == null || session == null) {
            return ItemStack.EMPTY;
        }
        ItemStack found = findMatchingSessionBasket(player, session);
        if (!found.isEmpty()) {
            session.latestBasketData = ShelfCartService.extractBasketData(found);
            stampBasketMetadata(found, session);
            return found;
        }
        ItemStack recreated = createSessionBasket(session, session.latestBasketData);
        placeSessionBasket(player, recreated);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        int slot = Math.max(0, Math.min(8, SESSION_BASKET_SLOT));
        return player.getInventory().getItem(slot);
    }

    private static ItemStack findMatchingSessionBasket(ServerPlayer player, BasketSession session) {
        if (player == null || session == null) {
            return ItemStack.EMPTY;
        }
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!ShelfCartService.isBasketStack(stack)) {
                continue;
            }
            CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
            if (tag == null || !tag.hasUUID(TAG_SESSION_ID) || !session.sessionId.equals(tag.getUUID(TAG_SESSION_ID))) {
                continue;
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void stampBasketMetadata(ItemStack basket, BasketSession session) {
        if (!ShelfCartService.isBasketStack(basket) || session == null) {
            return;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(basket);
        tag.putUUID(TAG_SESSION_ID, session.sessionId);
        tag.putUUID(TAG_OWNER_ID, session.ownerId);
        tag.putUUID(TAG_SHOP_ID, session.shopId);
        tag.putUUID(TAG_HOLDER_ID, session.holderId);
        tag.putString(TAG_HOLDER_DIM, session.holderDimension);
        tag.putInt(TAG_HOLDER_X, session.holderPos.getX());
        tag.putInt(TAG_HOLDER_Y, session.holderPos.getY());
        tag.putInt(TAG_HOLDER_Z, session.holderPos.getZ());
        ItemStackDataCompat.setCustomData(basket, tag);

        String shortId = shortId(session.sessionId);
        ItemStackDataCompat.setCustomName(basket, UbsTranslations.literal("Shopping Basket ")
                .append(Component.literal("[" + shortId + "]"))
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        List<ItemStack> snapshot = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            snapshot.add(player.getInventory().getItem(i).copy());
        }
        return snapshot;
    }

    private static boolean[] snapshotPaymentSlots(List<ItemStack> snapshot) {
        int size = snapshot == null ? 0 : snapshot.size();
        boolean[] paymentSlots = new boolean[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = snapshot.get(i);
            paymentSlots[i] = isPaymentStack(stack);
        }
        return paymentSlots;
    }

    private static void placeSessionBasket(ServerPlayer player, ItemStack basket) {
        int slot = Math.max(0, Math.min(8, SESSION_BASKET_SLOT));
        ItemStack displaced = player.getInventory().getItem(slot).copy();
        player.getInventory().setItem(slot, basket);
        if (!displaced.isEmpty()) {
            // Preserve displaced payment items if the basket hotbar slot was occupied.
            if (!player.getInventory().add(displaced.copy())) {
                player.drop(displaced.copy(), false);
            }
        }
    }

    private record SlotStack(int slot, ItemStack stack) {}
    private record SnapshotEntry(int slot, ItemStack stack) {}

    private static void clearInventory(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void clearInventoryExceptPaymentItems(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isPaymentStack(stack)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static List<SlotStack> captureLivePaymentStacks(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        List<SlotStack> payment = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isPaymentStack(stack)) {
                payment.add(new SlotStack(i, stack.copy()));
            }
        }
        return payment;
    }

    private static void restoreInventory(ServerPlayer player, List<ItemStack> snapshot, boolean[] originalPaymentSlots, int selectedSlot) {
        int size = player.getInventory().getContainerSize();
        List<SlotStack> livePaymentStacks = captureLivePaymentStacks(player);

        // Full clear first, then rebuild around current payment stacks.
        for (int i = 0; i < size; i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }

        // Keep payment items exactly where the player left them during basket mode.
        for (SlotStack slotStack : livePaymentStacks) {
            int slot = Math.max(0, Math.min(size - 1, slotStack.slot()));
            if (slotStack.stack().isEmpty()) {
                continue;
            }
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing.isEmpty()) {
                player.getInventory().setItem(slot, slotStack.stack().copy());
            } else {
                ItemStack toAdd = slotStack.stack().copy();
                if (!player.getInventory().add(toAdd)) {
                    player.drop(toAdd, false);
                }
            }
        }

        boolean[] reservedPaymentSlots = new boolean[size];
        if (originalPaymentSlots != null) {
            for (int i = 0; i < size && i < originalPaymentSlots.length; i++) {
                reservedPaymentSlots[i] = originalPaymentSlots[i];
            }
        }

        List<SnapshotEntry> pending = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = (snapshot != null && i < snapshot.size() && snapshot.get(i) != null)
                    ? snapshot.get(i).copy()
                    : ItemStack.EMPTY;
            if (stack.isEmpty() || isPaymentStack(stack)) {
                continue;
            }
            ItemStack existing = player.getInventory().getItem(i);
            if (existing.isEmpty() && !reservedPaymentSlots[i]) {
                player.getInventory().setItem(i, stack);
            } else {
                pending.add(new SnapshotEntry(i, stack));
            }
        }

        for (SnapshotEntry entry : pending) {
            ItemStack stack = entry.stack().copy();
            if (stack.isEmpty()) {
                continue;
            }
            int targetSlot = firstEmptyNonReservedSlot(player, reservedPaymentSlots);
            if (targetSlot >= 0) {
                player.getInventory().setItem(targetSlot, stack);
            } else {
                int anyEmpty = firstEmptySlot(player);
                if (anyEmpty >= 0) {
                    player.getInventory().setItem(anyEmpty, stack);
                } else {
                    if (!player.getInventory().add(stack.copy())) {
                        player.drop(stack.copy(), false);
                    }
                }
            }
        }
        player.getInventory().selected = Math.max(0, Math.min(8, selectedSlot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static int firstEmptyNonReservedSlot(ServerPlayer player, boolean[] reservedSlots) {
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            boolean reserved = reservedSlots != null && i < reservedSlots.length && reservedSlots[i];
            if (reserved) {
                continue;
            }
            if (player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static int firstEmptySlot(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isPaymentStack(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (DollarBills.isCashTenderItem(stack.getItem())
                || stack.is(ModItems.CREDIT_CARD.get())
                || WalletData.isWallet(stack));
    }

    private static String holderLabel(BasketSession session) {
        if (session == null) {
            return "-";
        }
        return session.holderDimension + " (" + session.holderPos.getX() + ", "
                + session.holderPos.getY() + ", " + session.holderPos.getZ() + ")";
    }

    private static String resolveShopName(MinecraftServer server, UUID ownerId, UUID shopId) {
        if (server == null || ownerId == null || shopId == null) {
            return "Unknown Shop";
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return "Unknown Shop";
        }
        for (ShopService.ShopSummary summary : ShopService.listOwnerShopSummaries(centralBank, ownerId)) {
            if (summary != null && shopId.equals(summary.shopId())) {
                String name = summary.name();
                if (name != null && !name.isBlank()) {
                    return name;
                }
                break;
            }
        }
        return "Unknown Shop";
    }

    private static void recoverFromPersistentBackup(ServerPlayer player) {
        CompoundTag backup = readSessionBackup(player);
        if (backup.isEmpty()) {
            // Safety net: remove any stale session-tagged basket that survived an interrupted session.
            int purged = purgeSessionTaggedBaskets(player, true);
            if (purged > 0) {
                player.sendSystemMessage(UbsTranslations.literal("§eRecovered ")
                        .append(Component.literal(String.valueOf(purged)))
                        .append(UbsTranslations.literal(" stale basket item(s).")));
            }
            return;
        }

        CompoundTag basketData = firstSessionBasketData(player);
        if (basketData.isEmpty() && backup.contains(TAG_BACKUP_BASKET, Tag.TAG_COMPOUND)) {
            basketData = backup.getCompound(TAG_BACKUP_BASKET).copy();
        }
        int restocked = restockFromBasketData(player.getServer(), basketData);
        int purged = purgeSessionTaggedBaskets(player, false);

        int size = player.getInventory().getContainerSize();
        List<ItemStack> snapshot = decodeInventorySnapshot(backup, size);
        boolean[] paymentSlots = decodePaymentSlots(backup, size);
        int selected = Math.max(0, Math.min(8, backup.getInt(TAG_BACKUP_SELECTED)));
        restoreInventory(player, snapshot, paymentSlots, selected);
        clearSessionBackup(player);

        MutableComponent message = UbsTranslations.literal("Recovered shopping session after reconnect. Inventory restored.")
                .withStyle(ChatFormatting.YELLOW);
        if (restocked > 0) {
            message.append(Component.literal(" ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(String.valueOf(restocked)).withStyle(ChatFormatting.YELLOW))
                    .append(UbsTranslations.literal(" item(s) returned to shelf stock.").withStyle(ChatFormatting.YELLOW));
        }
        if (purged > 0) {
            message.append(Component.literal(" ").withStyle(ChatFormatting.YELLOW))
                    .append(UbsTranslations.literal("Removed ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(String.valueOf(purged)).withStyle(ChatFormatting.YELLOW))
                    .append(UbsTranslations.literal(" stale basket item(s).").withStyle(ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(message);
    }

    private static CompoundTag readSessionBackup(ServerPlayer player) {
        if (player == null) {
            return new CompoundTag();
        }
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(TAG_PERSIST_BACKUP, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return persistent.getCompound(TAG_PERSIST_BACKUP).copy();
    }

    private static void persistSessionBackup(ServerPlayer player, BasketSession session) {
        if (player == null || session == null) {
            return;
        }
        CompoundTag backup = new CompoundTag();
        backup.put(TAG_BACKUP_INV, encodeInventorySnapshot(session.inventorySnapshot));
        backup.putByteArray(TAG_BACKUP_PAYMENTS, encodePaymentSlots(session.paymentSlotSnapshot));
        backup.putInt(TAG_BACKUP_SELECTED, session.selectedSlotSnapshot);
        if (session.latestBasketData != null && !session.latestBasketData.isEmpty()) {
            backup.put(TAG_BACKUP_BASKET, session.latestBasketData.copy());
        }
        player.getPersistentData().put(TAG_PERSIST_BACKUP, backup);
    }

    private static void clearSessionBackup(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.getPersistentData().remove(TAG_PERSIST_BACKUP);
    }

    private static ListTag encodeInventorySnapshot(List<ItemStack> snapshot) {
        ListTag out = new ListTag();
        if (snapshot == null) {
            return out;
        }
        for (int i = 0; i < snapshot.size(); i++) {
            ItemStack stack = snapshot.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            entry.put("stack", ItemStackDataCompat.saveStack(stack));
            out.add(entry);
        }
        return out;
    }

    private static List<ItemStack> decodeInventorySnapshot(CompoundTag backup, int size) {
        List<ItemStack> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(ItemStack.EMPTY);
        }
        if (backup == null || !backup.contains(TAG_BACKUP_INV, Tag.TAG_LIST)) {
            return out;
        }
        ListTag list = backup.getList(TAG_BACKUP_INV, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("stack", Tag.TAG_COMPOUND)) {
                continue;
            }
            int slot = entry.getInt("slot");
            if (slot < 0 || slot >= size) {
                continue;
            }
            out.set(slot, ItemStackDataCompat.parseStack(entry.getCompound("stack")));
        }
        return out;
    }

    private static byte[] encodePaymentSlots(boolean[] slots) {
        if (slots == null || slots.length == 0) {
            return new byte[0];
        }
        byte[] out = new byte[slots.length];
        for (int i = 0; i < slots.length; i++) {
            out[i] = (byte) (slots[i] ? 1 : 0);
        }
        return out;
    }

    private static boolean[] decodePaymentSlots(CompoundTag backup, int size) {
        boolean[] out = new boolean[size];
        if (backup == null || !backup.contains(TAG_BACKUP_PAYMENTS, Tag.TAG_BYTE_ARRAY)) {
            return out;
        }
        byte[] raw = backup.getByteArray(TAG_BACKUP_PAYMENTS);
        int len = Math.min(size, raw.length);
        for (int i = 0; i < len; i++) {
            out[i] = raw[i] != 0;
        }
        return out;
    }

    private static CompoundTag firstSessionBasketData(ServerPlayer player) {
        if (player == null) {
            return new CompoundTag();
        }
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!ShelfCartService.isBasketStack(stack)) {
                continue;
            }
            CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
            if (tag == null || !tag.hasUUID(TAG_SESSION_ID)) {
                continue;
            }
            return ShelfCartService.extractBasketData(stack);
        }
        return new CompoundTag();
    }

    private static int purgeSessionTaggedBaskets(ServerPlayer player, boolean restock) {
        if (player == null) {
            return 0;
        }
        int removed = 0;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!ShelfCartService.isBasketStack(stack)) {
                continue;
            }
            CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
            if (tag == null || !tag.hasUUID(TAG_SESSION_ID)) {
                continue;
            }
            if (restock) {
                restockFromBasketData(player.getServer(), ShelfCartService.extractBasketData(stack));
            }
            player.getInventory().setItem(i, ItemStack.EMPTY);
            removed++;
        }
        if (removed > 0) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return removed;
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "N/A";
        }
        String raw = id.toString().replace("-", "").toUpperCase(Locale.ROOT);
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private static ResourceKey<Level> serverLevelKey(String dimId) {
        ResourceLocation id = ResourceLocation.tryParse(dimId == null ? "" : dimId.trim());
        if (id == null) {
            id = Level.OVERWORLD.location();
        }
        return RegistryKeysCompat.createValueKey(RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
    }
}
