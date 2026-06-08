package net.austizz.ultimatebankingsystem.shelf;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBasketHolderBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.npc.ShopCashierInteractionManager;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class ShelfInteractionEvents {
    private ShelfInteractionEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        boolean sessionActive = ShelfBasketSessionService.hasActiveSession(event.getEntity().getUUID());
        boolean checkoutActive = ShopCashierInteractionManager.hasActiveSession(event.getEntity().getUUID());
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel serverLevel) {
            // In closed-hours courier access, only delivery-pallet box placement is allowed.
            if (isCourierDeliveryOnlyMode(player, serverLevel)
                    && !canUseCourierDeliveryPalletInteraction(player, serverLevel, event.getPos(), event.getItemStack())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                sendCourierDeliveryOnlyMessage(player);
                return;
            }
        }
        if (state.is(ModBlocks.SHOPPING_BASKET_HOLDER.get())) {
            return;
        }
        if (ShelfService.isShelf(state)) {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) {
                return;
            }
            ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(player.level(), event.getPos());
            if (shelf != null && !shelf.isShopMode()) {
                if (ShelfCartService.isBasketStack(event.getItemStack())) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    player.sendSystemMessage(Component.literal("This display is in regular mode. Basket shopping is disabled here."));
                }
                // For regular mode shelves, let normal block-use packet handling process right-click behavior.
                return;
            }
            // Keep direct server-side shelf handling only for explicit basket-in-hand clicks.
            // Session basket mode itself is gated in network payload handling to avoid config opens.
            if (!ShelfCartService.isBasketStack(event.getItemStack())) {
                return;
            }
            if (checkoutActive) {
                // Do not let shoppers modify basket contents while cashier payment flow is active.
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal("Finish or cancel cashier payment before editing basket items."));
                return;
            }

            // While in basket mode, force shelf-cart behavior instead of any shelf configuration/opening path.
            BlockHitResult hit = event.getHitVec();
            double hitX = event.getPos().getX() + 0.5D;
            double hitY = event.getPos().getY() + 0.5D;
            double hitZ = event.getPos().getZ() + 0.5D;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                hitX = hit.getLocation().x;
                hitY = hit.getLocation().y;
                hitZ = hit.getLocation().z;
            }
            int slot = ShelfService.resolveSlotByHit(player.level(), event.getPos(), hitX, hitY, hitZ);
            ShelfService.addShelfItemToBasket(player, event.getPos(), slot, player.isShiftKeyDown());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        ItemStack usedStack = event.getItemStack();
        if (usedStack.is(ModBlocks.SHOPPING_BASKET.get().asItem())) {
            // Shopping baskets are session-bound shopping tools and cannot be placed as world blocks.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Shopping baskets cannot be placed. Use shelves and cashier checkout."));
            }
            return;
        }

        if (checkoutActive) {
            // During cashier checkout, allow only card use on a terminal block.
            if (state.is(ModBlocks.PAYMENT_TERMINAL.get())) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (sessionActive) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        ItemStack basket = ShelfService.findBasketInHands(event.getEntity());
        if (basket.isEmpty()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        boolean sessionActive = ShelfBasketSessionService.hasActiveSession(event.getEntity().getUUID());
        boolean checkoutActive = ShopCashierInteractionManager.hasActiveSession(event.getEntity().getUUID());
        if (checkoutActive) {
            // Active checkout must still allow paying cash by right-clicking the cashier.
            if (event.getTarget() instanceof BankTellerEntity teller && teller.isCashier()) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (!sessionActive) {
            ItemStack basket = ShelfService.findBasketInHands(event.getEntity());
            if (basket.isEmpty()) {
                return;
            }
            if (event.getTarget() instanceof BankTellerEntity) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (!(event.getTarget() instanceof BankTellerEntity)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (ShelfBasketSessionService.hasActiveSession(event.getEntity().getUUID())
                || ShopCashierInteractionManager.hasActiveSession(event.getEntity().getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemStack basket = ShelfService.findBasketInHands(event.getEntity());
        if (basket.isEmpty()) {
            return;
        }
        if (ShelfCartService.isBasketStack(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean sessionActive = ShelfBasketSessionService.hasActiveSession(player.getUUID())
                || ShopCashierInteractionManager.hasActiveSession(player.getUUID());
        ItemStack basket = ShelfService.findBasketInHands(player);
        if (basket.isEmpty() && !sessionActive) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!ShelfService.isShelf(state)) {
            event.setCanceled(true);
            return;
        }
        ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(player.level(), event.getPos());
        if (shelf != null && !shelf.isShopMode()) {
            // Left-click basket removal only applies to shop-mode shelves.
            event.setCanceled(true);
            return;
        }

        HitResult pick = player.pick(5.0D, 0.0F, false);
        double hitX = event.getPos().getX() + 0.5D;
        double hitY = event.getPos().getY() + 0.5D;
        double hitZ = event.getPos().getZ() + 0.5D;
        if (pick instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            hitX = blockHit.getLocation().x;
            hitY = blockHit.getLocation().y;
            hitZ = blockHit.getLocation().z;
        }

        int slot = ShelfService.resolveSlotByHit(
                player.level(),
                event.getPos(),
                hitX,
                hitY,
                hitZ
        );
        // Shift + left-click returns a full stack from basket to shelf stock.
        ShelfService.removeShelfItemFromBasket(player, event.getPos(), slot, player.isShiftKeyDown());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel serverLevel && isCourierDeliveryOnlyMode(player, serverLevel)) {
            event.setCanceled(true);
            sendCourierDeliveryOnlyMessage(player);
            return;
        }

        if (ShelfBasketSessionService.hasActiveSession(player.getUUID())
                || ShopCashierInteractionManager.hasActiveSession(player.getUUID())
                || !ShelfService.findBasketInHands(player).isEmpty()) {
            event.setCanceled(true);
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(ModBlocks.SHOPPING_BASKET_HOLDER.get())) {
            BlockState lowerState = state;
            net.minecraft.core.BlockPos lowerPos = event.getPos();
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                lowerPos = event.getPos().below();
                lowerState = event.getLevel().getBlockState(lowerPos);
            }
            if (lowerState.is(ModBlocks.SHOPPING_BASKET_HOLDER.get())
                    && event.getLevel().getBlockEntity(lowerPos) instanceof ShoppingBasketHolderBlockEntity holder) {
                boolean canManageHolder = player.hasPermissions(3);
                if (!canManageHolder) {
                    if (holder.getShopId() != null && player.getServer() != null) {
                        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
                        canManageHolder = centralBank != null
                                && ShopService.canManageShop(centralBank, player.getUUID(), holder.getShopId());
                    } else if (holder.getOwnerId() != null) {
                        canManageHolder = holder.getOwnerId().equals(player.getUUID());
                    }
                }
                if (!canManageHolder) {
                    player.sendSystemMessage(Component.literal("Only the shop owner or an operator can remove this basket holder."));
                    event.setCanceled(true);
                }
            }
            return;
        }
        if (!ShelfService.isShelf(state)) {
            return;
        }
        if (!ShelfService.canManageShelf(player.level(), event.getPos(), player)) {
            player.sendSystemMessage(Component.literal("Only the shop owner or an operator can remove this shelf."));
            event.setCanceled(true);
            return;
        }

        player.sendSystemMessage(Component.literal("Use Shift-right-click and press Remove Selected Shelf to remove it."));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel serverLevel && isCourierDeliveryOnlyMode(player, serverLevel)) {
            event.setCanceled(true);
            sendCourierDeliveryOnlyMessage(player);
            return;
        }
        // Hard-stop basket block placement to prevent disposing of shopping baskets.
        if (event.getPlacedBlock().is(ModBlocks.SHOPPING_BASKET.get())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Shopping baskets cannot be placed. Use shelves and cashier checkout."));
            return;
        }
        if (!ShelfBasketSessionService.hasActiveSession(player.getUUID())
                && !ShopCashierInteractionManager.hasActiveSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("Shopping basket mode is active. Return basket or complete checkout."));
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (ShelfCartService.isBasketStack(event.getEntity().getItem())) {
            // Keep basket bound to the active shopping flow; dropping it must never be a disposal path.
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You cannot drop the shopping basket."));
            return;
        }
        if (!ShelfBasketSessionService.hasActiveSession(player.getUUID())
                && !ShopCashierInteractionManager.hasActiveSession(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot drop items while shopping basket mode is active."));
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (ShelfBasketSessionService.hasActiveSession(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        ItemEntity itemEntity = event.getItem();
        if (itemEntity == null) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (!ShopCashierInteractionManager.isProtectedBagDrop(stack)) {
            return;
        }
        if (!ShopCashierInteractionManager.canPickupProtectedBag(player, stack)) {
            player.sendSystemMessage(Component.literal("§cThis shopping bag is reserved for the original buyer."));
            event.setCanceled(true);
            return;
        }
        // Strip temporary lock/timer tags once the buyer or an admin picks up the dropped bag.
        ShopCashierInteractionManager.clearProtectedBagDrop(itemEntity);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Safety net: if a client disconnects/crashes while the position editor is open,
            // restore original mode so spectator is never persisted unintentionally.
            ShelfService.endPositionerSpectator(player);
            ShelfBasketSessionService.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShelfBasketSessionService.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ShelfBasketSessionService.interceptDeath(player)) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isCourierDeliveryOnlyMode(ServerPlayer player, ServerLevel level) {
        if (player == null || level == null || player.getServer() == null) {
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return false;
        }
        return ShopService.isCourierDeliveryOnlyMode(
                centralBank,
                player.getServer(),
                player.getUUID(),
                level.dimension().location().toString(),
                player.blockPosition()
        );
    }

    private static boolean canUseCourierDeliveryPalletInteraction(ServerPlayer player,
                                                                  ServerLevel level,
                                                                  net.minecraft.core.BlockPos clickedPos,
                                                                  ItemStack heldStack) {
        if (player == null || level == null || player.getServer() == null || clickedPos == null) {
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return false;
        }
        return ShopService.canUseCourierDeliveryPalletInteraction(
                centralBank,
                player.getServer(),
                player,
                level,
                clickedPos,
                heldStack
        );
    }

    private static void sendCourierDeliveryOnlyMessage(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.sendSystemMessage(Component.literal("§eDelivery access only: place a cardboard box on an assigned delivery pallet."));
    }
}
