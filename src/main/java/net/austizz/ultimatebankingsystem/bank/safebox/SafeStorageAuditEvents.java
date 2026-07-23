package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Captures player-driven removals from arbitrary block/entity inventories inside safe claims. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class SafeStorageAuditEvents {
    private static final Map<UUID, AuditSession> PENDING = new HashMap<>();
    private static final Map<UUID, AuditSession> ACTIVE = new HashMap<>();

    private SafeStorageAuditEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos().immutable();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MetalPalletBlockEntity || !SafeAccessAuditService.isInsideSafeClaim(level, pos)) {
            return;
        }
        InventorySnapshot snapshot = snapshotBlock(level, pos);
        if (snapshot == null) return;
        String label = blockEntity == null
                ? level.getBlockState(pos).getBlock().getName().getString()
                : blockEntity.getBlockState().getBlock().getName().getString();
        PENDING.put(player.getUUID(), new AuditSession(
                new StorageSource(false, level.dimension().location().toString(), pos, null),
                label, snapshot, level.getGameTime()));
    }

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        Entity target = event.getTarget();
        BlockPos pos = target.blockPosition();
        if (!SafeAccessAuditService.isInsideSafeClaim(level, pos)) return;
        InventorySnapshot snapshot = snapshotEntity(target);
        if (snapshot == null) return;
        PENDING.put(player.getUUID(), new AuditSession(
                new StorageSource(true, level.dimension().location().toString(), pos.immutable(), target.getUUID()),
                target.getName().getString(), snapshot, level.getGameTime()));
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AuditSession pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        long age = Math.max(0L, player.serverLevel().getGameTime() - pending.startedAtTick());
        if (age <= 10L) ACTIVE.put(player.getUUID(), pending);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AuditSession session = ACTIVE.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        if (session == null) return;
        ServerLevel level = level(player, session.source().dimension());
        if (level == null) return;
        InventorySnapshot after = session.source().entity()
                ? snapshotEntity(session.source().entityId() == null ? null : level.getEntity(session.source().entityId()))
                : snapshotBlock(level, session.source().pos());
        if (after == null) return;
        for (Map.Entry<String, ItemCount> beforeEntry : session.before().items().entrySet()) {
            ItemCount previous = beforeEntry.getValue();
            int current = after.items().getOrDefault(beforeEntry.getKey(), ItemCount.EMPTY).count();
            int removed = Math.max(0, previous.count() - current);
            if (removed > 0) {
                SafeAccessAuditService.recordStorageRemoval(level, session.source().pos(), player,
                        session.label(), previous.displayName(), removed);
            }
        }
    }

    private static InventorySnapshot snapshotBlock(ServerLevel level, BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            for (Direction direction : Direction.values()) {
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
                if (handler != null) break;
            }
        }
        if (handler != null) return snapshot(handler);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? snapshot(container) : null;
    }

    private static InventorySnapshot snapshotEntity(Entity entity) {
        if (entity == null) return null;
        IItemHandler handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler != null) return snapshot(handler);
        return entity instanceof Container container ? snapshot(container) : null;
    }

    private static InventorySnapshot snapshot(IItemHandler handler) {
        Map<String, ItemCount> items = new LinkedHashMap<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) add(items, handler.getStackInSlot(slot));
        return new InventorySnapshot(Map.copyOf(items));
    }

    private static InventorySnapshot snapshot(Container container) {
        Map<String, ItemCount> items = new LinkedHashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) add(items, container.getItem(slot));
        return new InventorySnapshot(Map.copyOf(items));
    }

    private static void add(Map<String, ItemCount> target, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String key = id == null ? "minecraft:air" : id.toString();
        ItemCount previous = target.get(key);
        int total = previous == null
                ? stack.getCount()
                : (int) Math.min(Integer.MAX_VALUE, (long) previous.count() + stack.getCount());
        target.put(key, new ItemCount(stack.getHoverName().getString(), Math.max(0, total)));
    }

    private static ServerLevel level(ServerPlayer player, String dimension) {
        if (player.serverLevel().dimension().location().toString().equals(dimension)) return player.serverLevel();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) return level;
        }
        return null;
    }

    private record StorageSource(boolean entity, String dimension, BlockPos pos, UUID entityId) {
    }

    private record AuditSession(StorageSource source,
                                String label,
                                InventorySnapshot before,
                                long startedAtTick) {
    }

    private record InventorySnapshot(Map<String, ItemCount> items) {
    }

    private record ItemCount(String displayName, int count) {
        private static final ItemCount EMPTY = new ItemCount("", 0);
    }
}
