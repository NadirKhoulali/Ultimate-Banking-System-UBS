package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.DallasMaskAnimationPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class DallasMaskService {
    public static final int ANIMATION_TICKS = 24;

    private static final String ESCROW_KEY = "ultimatebankingsystem_dallas_mask_escrow";
    private static final String TAG_COMPLETE_AT = "CompleteAt";
    private static final String TAG_MASK_SLOT = "MaskSlot";
    private static final String TAG_HELD_SLOT = "HeldSlot";
    private static final String TAG_MASK_STACK = "MaskStack";
    private static final String TAG_HELD_STACK = "HeldStack";
    private static final String TAG_PUTTING_ON = "PuttingOn";

    private static final Map<UUID, PendingMaskAction> PENDING_ACTIONS = new HashMap<>();
    private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();

    private DallasMaskService() {
    }

    public static void requestToggle(ServerPlayer player) {
        if (player == null || player.isRemoved()) {
            return;
        }

        long now = player.serverLevel().getGameTime();
        Long busyUntil = BUSY_UNTIL.get(player.getUUID());
        if (busyUntil != null && busyUntil > now) {
            notify(player, "message.ultimatebankingsystem.dallas_mask.busy");
            return;
        }

        ItemStack equipped = player.getItemBySlot(EquipmentSlot.HEAD);
        if (equipped.is(ModItems.DALLAS_MASK.get())) {
            if (HeistService.activeSession(player) != null) {
                player.displayClientMessage(Component.literal("The mask cannot be removed during an active heist."), true);
                return;
            }
            takeOff(player, equipped, now);
            return;
        }
        if (!equipped.isEmpty()) {
            notify(player, "message.ultimatebankingsystem.dallas_mask.head_occupied");
            return;
        }
        int maskSlot = findMaskSlot(player);
        if (maskSlot < 0) {
            notify(player, "message.ultimatebankingsystem.dallas_mask.missing");
            return;
        }

        long completeAt = now + ANIMATION_TICKS;
        PendingMaskAction pending = beginEquip(player, maskSlot, completeAt);
        if (pending == null) {
            notify(player, "message.ultimatebankingsystem.dallas_mask.missing");
            return;
        }
        PENDING_ACTIONS.put(player.getUUID(), pending);
        BUSY_UNTIL.put(player.getUUID(), completeAt);
        broadcast(player, true);
    }

    private static PendingMaskAction beginEquip(ServerPlayer player, int maskSlot, long completeAt) {
        ItemStack source = player.getInventory().getItem(maskSlot);
        if (!source.is(ModItems.DALLAS_MASK.get())) {
            return null;
        }

        int heldSlot = Math.max(0, Math.min(8, player.getInventory().selected));
        ItemStack maskStack = source.copyWithCount(1);
        source.shrink(1);
        if (source.isEmpty()) {
            player.getInventory().setItem(maskSlot, ItemStack.EMPTY);
        }

        ItemStack heldStack = ItemStack.EMPTY;
        if (maskSlot != heldSlot) {
            ItemStack selected = player.getInventory().getItem(heldSlot);
            if (!selected.isEmpty()) {
                heldStack = selected.copy();
                player.getInventory().setItem(heldSlot, ItemStack.EMPTY);
            }
        }

        PendingMaskAction pending = new PendingMaskAction(
                completeAt,
                true,
                maskSlot,
                heldSlot,
                maskStack,
                heldStack
        );
        persistEscrow(player, pending);
        syncInventory(player);
        return pending;
    }

    private static void takeOff(ServerPlayer player, ItemStack equipped, long now) {
        long completeAt = now + ANIMATION_TICKS;
        PendingMaskAction pending = beginRemoval(player, equipped, completeAt);
        PENDING_ACTIONS.put(player.getUUID(), pending);
        BUSY_UNTIL.put(player.getUUID(), completeAt);
        broadcast(player, false);
    }

    private static PendingMaskAction beginRemoval(ServerPlayer player, ItemStack equipped, long completeAt) {
        ItemStack maskStack = equipped.copyWithCount(1);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

        int heldSlot = Math.max(0, Math.min(8, player.getInventory().selected));
        ItemStack heldStack = player.getInventory().getItem(heldSlot).copy();
        if (!heldStack.isEmpty()) {
            player.getInventory().setItem(heldSlot, ItemStack.EMPTY);
        }

        PendingMaskAction pending = new PendingMaskAction(
                completeAt,
                false,
                -1,
                heldSlot,
                maskStack,
                heldStack
        );
        persistEscrow(player, pending);
        syncInventory(player);
        return pending;
    }

    private static void completeAction(ServerPlayer player, PendingMaskAction pending) {
        if (pending.puttingOn()) {
            completeEquip(player, pending);
        } else {
            completeRemoval(player, pending);
        }
    }

    private static void completeEquip(ServerPlayer player, PendingMaskAction pending) {
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            restorePendingStacks(player, pending);
            clearEscrow(player);
            syncInventory(player);
            notify(player, "message.ultimatebankingsystem.dallas_mask.head_occupied");
            return;
        }

        player.setItemSlot(EquipmentSlot.HEAD, pending.maskStack().copyWithCount(1));
        restoreToPreferredSlot(player, pending.heldStack(), pending.heldSlot());
        clearEscrow(player);
        syncInventory(player);
    }

    private static void completeRemoval(ServerPlayer player, PendingMaskAction pending) {
        restorePendingStacks(player, pending);
        clearEscrow(player);
        syncInventory(player);
    }

    private static int findMaskSlot(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.DALLAS_MASK.get())) {
                return slot;
            }
        }
        return -1;
    }

    private static void broadcast(ServerPlayer player, boolean puttingOn) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new DallasMaskAnimationPayload(player.getUUID(), puttingOn, ANIMATION_TICKS)
        );
    }

    private static void notify(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }

    private static void restoreInterruptedAction(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PendingMaskAction pending = PENDING_ACTIONS.remove(playerId);
        if (pending == null) {
            pending = readEscrow(player);
        }
        BUSY_UNTIL.remove(playerId);
        if (pending == null) {
            clearEscrow(player);
            return;
        }

        restorePendingStacks(player, pending);
        clearEscrow(player);
        syncInventory(player);
    }

    private static void restorePendingStacks(ServerPlayer player, PendingMaskAction pending) {
        if (pending.puttingOn()) {
            restoreToPreferredSlot(player, pending.maskStack(), pending.maskSlot());
            restoreToPreferredSlot(player, pending.heldStack(), pending.heldSlot());
            return;
        }
        restoreToPreferredSlot(player, pending.heldStack(), pending.heldSlot());
        restoreToPreferredSlot(player, pending.maskStack(), pending.maskSlot());
    }

    private static void restoreToPreferredSlot(ServerPlayer player, ItemStack stack, int preferredSlot) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ItemStack restored = stack.copy();
        if (preferredSlot >= 0 && preferredSlot < player.getInventory().getContainerSize()) {
            ItemStack displaced = player.getInventory().getItem(preferredSlot).copy();
            player.getInventory().setItem(preferredSlot, restored);
            returnToInventoryOrDrop(player, displaced);
            return;
        }
        returnToInventoryOrDrop(player, restored);
    }

    private static void returnToInventoryOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack remainder = stack.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    private static void persistEscrow(ServerPlayer player, PendingMaskAction pending) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_COMPLETE_AT, pending.completeAt());
        tag.putBoolean(TAG_PUTTING_ON, pending.puttingOn());
        tag.putInt(TAG_MASK_SLOT, pending.maskSlot());
        tag.putInt(TAG_HELD_SLOT, pending.heldSlot());
        tag.put(TAG_MASK_STACK, ItemStackDataCompat.saveStack(pending.maskStack(), player.registryAccess()));
        if (!pending.heldStack().isEmpty()) {
            tag.put(TAG_HELD_STACK, ItemStackDataCompat.saveStack(pending.heldStack(), player.registryAccess()));
        }
        player.getPersistentData().put(ESCROW_KEY, tag);
    }

    private static PendingMaskAction readEscrow(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ESCROW_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag tag = persistent.getCompound(ESCROW_KEY);
        if (!tag.contains(TAG_MASK_STACK, Tag.TAG_COMPOUND)) {
            return null;
        }
        ItemStack maskStack = ItemStackDataCompat.parseStack(
                tag.getCompound(TAG_MASK_STACK),
                player.registryAccess()
        );
        if (!maskStack.is(ModItems.DALLAS_MASK.get())) {
            return null;
        }
        ItemStack heldStack = tag.contains(TAG_HELD_STACK, Tag.TAG_COMPOUND)
                ? ItemStackDataCompat.parseStack(tag.getCompound(TAG_HELD_STACK), player.registryAccess())
                : ItemStack.EMPTY;
        return new PendingMaskAction(
                tag.getLong(TAG_COMPLETE_AT),
                tag.getBoolean(TAG_PUTTING_ON),
                tag.getInt(TAG_MASK_SLOT),
                tag.getInt(TAG_HELD_SLOT),
                maskStack,
                heldStack
        );
    }

    private static void clearEscrow(ServerPlayer player) {
        player.getPersistentData().remove(ESCROW_KEY);
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();

        Iterator<Map.Entry<UUID, PendingMaskAction>> pending = PENDING_ACTIONS.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<UUID, PendingMaskAction> entry = pending.next();
            if (entry.getValue().completeAt() > now) {
                continue;
            }
            pending.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && !player.isRemoved()) {
                completeAction(player, entry.getValue());
            }
        }
        BUSY_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreInterruptedAction(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreInterruptedAction(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreInterruptedAction(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID playerId : PENDING_ACTIONS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved()) {
                restoreInterruptedAction(player);
            }
        }
        PENDING_ACTIONS.clear();
        BUSY_UNTIL.clear();
    }

    private record PendingMaskAction(
            long completeAt,
            boolean puttingOn,
            int maskSlot,
            int heldSlot,
            ItemStack maskStack,
            ItemStack heldStack
    ) {
    }
}
