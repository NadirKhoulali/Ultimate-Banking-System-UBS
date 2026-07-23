package net.austizz.ultimatebankingsystem.npc;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagDataKeys;
import net.austizz.ultimatebankingsystem.command.UBSCommands;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.item.WalletData;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerNotification;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.payments.WalletPaymentService;
import net.austizz.ultimatebankingsystem.shelf.ShelfCartService;
import net.austizz.ultimatebankingsystem.shelf.ShelfBasketSessionService;
import net.austizz.ultimatebankingsystem.shelf.ShelfService;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopCashierInteractionManager {
    private static final double CANCEL_DISTANCE_SQ = 8.0D * 8.0D;
    private static final long SESSION_TIMEOUT_TICKS = 20L * 30L;
    private static final long BAG_DROP_DESPAWN_TICKS = 20L * 120L;
    private static final long BAG_DROP_LABEL_REFRESH_TICKS = 20L;
    private static final double BAG_DROP_HOLOGRAM_CLUSTER_RANGE_SQ = 0.80D * 0.80D;
    private static final long CASHIER_BAG_STATUS_REFRESH_TICKS = 20L;
    private static final UUID CASH_TENDER_SOURCE_ID = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:shop-cashier-cash".getBytes(StandardCharsets.UTF_8)
    );

    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, BagDropSession> BAG_DROPS = new ConcurrentHashMap<>();

    private static final class Session {
        private final UUID playerId;
        private final UUID cashierId;
        private final UUID shopId;
        private final String cashierDimension;
        private final long startedTick;
        private final UUID shopOwnerId;
        private final UUID merchantAccountId;
        private final String shopName;
        private final String terminalDimension;
        private final int terminalX;
        private final int terminalY;
        private final int terminalZ;
        private final long requiredCents;
        private final int requiredBagCount;
        private int reservedBagCount;
        private final int[] insertedCash = new int[DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length];
        private final int[] walletInsertedCash = new int[DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length];
        private UUID walletCashOpenId;
        private long cashPaidCents;
        private long cardPaidCents;

        private Session(UUID playerId,
                        UUID cashierId,
                        UUID shopId,
                        String cashierDimension,
                        long startedTick,
                        UUID shopOwnerId,
                        UUID merchantAccountId,
                        String shopName,
                        String terminalDimension,
                        int terminalX,
                        int terminalY,
                        int terminalZ,
                        long requiredCents,
                        int requiredBagCount) {
            this.playerId = playerId;
            this.cashierId = cashierId;
            this.shopId = shopId;
            this.cashierDimension = cashierDimension;
            this.startedTick = startedTick;
            this.shopOwnerId = shopOwnerId;
            this.merchantAccountId = merchantAccountId;
            this.shopName = shopName;
            this.terminalDimension = terminalDimension;
            this.terminalX = terminalX;
            this.terminalY = terminalY;
            this.terminalZ = terminalZ;
            this.requiredCents = requiredCents;
            this.requiredBagCount = Math.max(0, requiredBagCount);
            this.reservedBagCount = Math.max(0, requiredBagCount);
        }
    }

    private static final class BagDropSession {
        private final UUID itemEntityId;
        private final String dimensionId;
        private final long expiresAtTick;

        private BagDropSession(UUID itemEntityId, String dimensionId, long expiresAtTick) {
            this.itemEntityId = itemEntityId;
            this.dimensionId = dimensionId;
            this.expiresAtTick = expiresAtTick;
        }
    }

    private static final class ActiveBagDrop {
        private final UUID itemEntityId;
        private final ItemEntity itemEntity;
        private final long nowTick;
        private final long expiresAtTick;
        private final long remainingTicks;

        private ActiveBagDrop(UUID itemEntityId, ItemEntity itemEntity, long nowTick, long expiresAtTick) {
            this.itemEntityId = itemEntityId;
            this.itemEntity = itemEntity;
            this.nowTick = nowTick;
            this.expiresAtTick = expiresAtTick;
            this.remainingTicks = Math.max(0L, expiresAtTick - nowTick);
        }
    }

    private record ShopBagReservation(boolean success, int availableBags, int reservedBags) {
    }

    private ShopCashierInteractionManager() {
    }

    public static boolean hasActiveSession(UUID playerId) {
        return playerId != null && SESSIONS.containsKey(playerId);
    }

    public static boolean handleTerminalCardUse(ServerPlayer player,
                                                ServerLevel level,
                                                BlockPos terminalPos,
                                                ShopTerminalBlockEntity terminal) {
        if (player == null || level == null || terminalPos == null || terminal == null) {
            return false;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        if (!isShopOpenForSession(player, session)) {
            cancelAndRefund(player, session, "Store closed during checkout. Payment cancelled.", true);
            return true;
        }
        if (!terminalMatchesSession(session, level, terminalPos)) {
            String message = "This checkout is linked to another payment terminal: "
                    + terminalLabel(session);
            player.sendSystemMessage(Component.literal("§e" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.WARNING, message, 5200);
            sendCancelHint(player);
            return true;
        }
        if (remainingCents(session) <= 0L) {
            completeSession(player, session);
            return true;
        }
        processCardPayment(player, session, true);
        return true;
    }

    private static boolean isShopOpenForSession(ServerPlayer player, Session session) {
        if (player == null || session == null || player.getServer() == null || session.shopId == null) {
            return true;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return true;
        }
        return ShopService.isShopOpenForShopping(centralBank, session.shopId, player.level().getGameTime());
    }

    public static boolean handleInteract(ServerPlayer player, BankTellerEntity cashier, InteractionHand hand) {
        if (player == null || cashier == null || !cashier.isCashier()) {
            return false;
        }

        // Cashier checkout is unavailable while the shop is closed.
        if (!isShopOpenForCheckout(player, cashier)) {
            Session active = SESSIONS.get(player.getUUID());
            if (active != null && active.cashierId.equals(cashier.getUUID())) {
                cancelAndRefund(player, active, "Store closed during checkout. Payment cancelled.", true);
            }
            return true;
        }

        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            if (!session.cashierId.equals(cashier.getUUID())) {
                String message = "You already have an active checkout at another cashier.";
                player.sendSystemMessage(Component.literal("§e" + message));
                pushCashierAlert(player, DeliveryAlertPayload.AlertTone.WARNING, message, 5200);
                return true;
            }
            return processPaymentInput(player, cashier, hand, session);
        }

        Session activeForCashier = findActiveSessionForCashier(player.getServer(), cashier.getUUID(), player.getUUID());
        if (activeForCashier != null) {
            String message = "This cashier is currently assisting another customer.";
            player.sendSystemMessage(Component.literal("§e" + message));
            player.sendSystemMessage(Component.literal("§7Please wait until that checkout completes or times out."));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.WARNING,
                    "This cashier is currently assisting another customer. Please wait until that checkout completes or times out.",
                    5600);
            return true;
        }

        ItemStack basket = ShelfService.findBasketInHands(player);
        if (basket.isEmpty()) {
            String message = "Hold a shopping basket to checkout.";
            player.sendSystemMessage(Component.literal("§e" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.INFO, message);
            return true;
        }
        int totalUnits = ShelfCartService.getTotalUnits(basket);
        if (totalUnits <= 0) {
            String message = "Your basket is empty.";
            player.sendSystemMessage(Component.literal("§e" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.INFO, message);
            return true;
        }
        List<ShelfCartService.BasketEntryView> soldEntries = ShelfCartService.getEntries(basket);
        String fallbackShopName = resolveCashierShopName(cashier);
        int requiredBagCount = estimateRequiredBagCount(soldEntries, fallbackShopName);

        var server = player.getServer();
        if (server == null) {
            String message = "Server context is unavailable.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            return true;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            String message = "Bank data is unavailable.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            return true;
        }
        UUID ownerId = cashier.getOwnerUUID();
        if (ownerId == null) {
            String message = "This cashier is not linked to a shop owner.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            return true;
        }
        UUID shopId = cashier.getShopId();
        if (shopId == null) {
            shopId = ShopService.resolveOwnerShopAtPos(
                    centralBank,
                    ownerId,
                    cashier.level().dimension().location().toString(),
                    cashier.blockPosition()
            );
            if (shopId != null) {
                cashier.setShopId(shopId);
            }
        }
        if (shopId == null) {
            String message = "This cashier is not linked to a valid shop.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            return true;
        }

        ShopBagReservation reservation = reserveShoppingBagsForCheckout(
                server,
                centralBank,
                ownerId,
                shopId,
                requiredBagCount
        );
        if (!reservation.success()) {
            sendBagStockShortageMessage(player, reservation.availableBags(), requiredBagCount);
            return true;
        }

        long totalCents = ShelfCartService.getTotalPriceDollars(basket);
        if (totalCents <= 0L) {
            List<ItemStack> packedBags = buildShoppingBagsForCheckout(soldEntries, fallbackShopName);
            clearBasket(player);
            ShelfBasketSessionService.completeCheckout(player);
            int droppedBags = deliverPackedBags(player, packedBags);
            String message = "Checkout complete. All items were free.";
            player.sendSystemMessage(Component.literal("§a" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.SUCCESS, message, 5000);
            sendBagDeliverySummary(player, packedBags, droppedBags);
            return true;
        }

        ShopService.CheckoutTerminalTarget target = ShopService.resolveCheckoutTerminal(
                server,
                centralBank,
                ownerId,
                shopId,
                cashier.getUUID()
        );
        if (target == null) {
            String message = "No checkout terminal is configured for this shop.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            return true;
        }
        UUID merchantAccountId = target.terminal().getMerchantAccountId();
        if (merchantAccountId == null) {
            String message = "Checkout terminal has no merchant account configured.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            return true;
        }
        if (centralBank.SearchForAccountByAccountId(merchantAccountId) == null) {
            String message = "Checkout terminal merchant account is missing.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            return true;
        }
        merchantAccountId = ShopService.resolveSettlementAccountId(
                centralBank,
                ownerId,
                shopId,
                merchantAccountId
        );

        Session newSession = new Session(
                player.getUUID(),
                cashier.getUUID(),
                shopId,
                cashier.level().dimension().location().toString(),
                cashier.level().getGameTime(),
                ownerId,
                merchantAccountId,
                target.shopName(),
                target.dimensionId(),
                target.pos().getX(),
                target.pos().getY(),
                target.pos().getZ(),
                totalCents,
                reservation.reservedBags()
        );
        SESSIONS.put(player.getUUID(), newSession);
        sendPrompt(player, newSession);
        return true;
    }

    /**
     * Verifies whether the cashier's shop is currently open for customer checkout.
     */
    private static boolean isShopOpenForCheckout(ServerPlayer player, BankTellerEntity cashier) {
        if (player == null || cashier == null || player.getServer() == null) {
            return true;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return true;
        }
        UUID shopId = cashier.getShopId();
        if (shopId == null) {
            UUID ownerId = cashier.getOwnerUUID();
            if (ownerId != null) {
                shopId = ShopService.resolveOwnerShopAtPos(
                        centralBank,
                        ownerId,
                        cashier.level().dimension().location().toString(),
                        cashier.blockPosition()
                );
                if (shopId != null) {
                    cashier.setShopId(shopId);
                }
            }
        }
        if (shopId == null) {
            return true;
        }
        boolean open = ShopService.isShopOpenForShopping(centralBank, shopId, cashier.level().getGameTime());
        if (open) {
            return true;
        }
        String message = "Store is currently closed. Cashier checkout is unavailable.";
        player.sendSystemMessage(Component.literal("§e" + message));
        pushCashierAlert(player, DeliveryAlertPayload.AlertTone.WARNING, message, 5200);
        return false;
    }

    public static int handleCancel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this."));
            return 0;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0;
        }
        cancelAndRefund(player, session, "Checkout cancelled.", true);
        return 1;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        tickCashierBagStatus(server);
        if (SESSIONS.isEmpty()) {
            tickBagDrops(server);
            return;
        }
        List<UUID> playerIds = new ArrayList<>(SESSIONS.keySet());
        for (UUID playerId : playerIds) {
            Session session = SESSIONS.get(playerId);
            if (session == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                SESSIONS.remove(playerId);
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (!session.cashierDimension.equals(level.dimension().location().toString())) {
                cancelAndRefund(player, session, "You moved away from the cashier. Checkout cancelled.", false);
                continue;
            }
            Entity entity = level.getEntity(session.cashierId);
            if (!(entity instanceof BankTellerEntity cashier) || !cashier.isCashier()) {
                cancelAndRefund(player, session, "This cashier is no longer available.", false);
                continue;
            }
            if (player.distanceToSqr(cashier) > CANCEL_DISTANCE_SQ) {
                cancelAndRefund(player, session, "You walked too far from the cashier. Checkout cancelled.", false);
                continue;
            }
            if (Math.abs(level.getGameTime() - session.startedTick) > SESSION_TIMEOUT_TICKS) {
                cancelAndRefund(player, session, "Checkout timed out after 30 seconds.", true);
            }
        }
        tickBagDrops(server);
    }

    public static void cancelForCashier(UUID cashierId, String reason) {
        if (cashierId == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<UUID> ids = new ArrayList<>(SESSIONS.keySet());
        for (UUID id : ids) {
            Session session = SESSIONS.get(id);
            if (session == null || !cashierId.equals(session.cashierId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                cancelAndRefund(player, session, reason == null ? "Checkout cancelled." : reason, false);
            } else {
                SESSIONS.remove(id);
            }
        }
    }

    private static boolean processPaymentInput(ServerPlayer player, BankTellerEntity cashier, InteractionHand hand, Session session) {
        ItemStack wallet = WalletPaymentService.findHeldWallet(player);
        if (!wallet.isEmpty()) {
            if (WalletData.getMode(wallet) == WalletData.PaymentMode.CASH) {
                processWalletCashTender(player, cashier, session);
            } else {
                processCardPayment(player, session, false);
            }
            return true;
        }

        ItemStack held = player.getItemInHand(hand);
        int cashIndex = held == null || held.isEmpty() ? -1 : DollarBills.cashIndexForItem(held.getItem());
        if (cashIndex >= 0) {
            processCashTender(player, cashier, session, held, cashIndex);
            return true;
        }
        int stackBillIndex = held == null || held.isEmpty() ? -1 : DollarBills.billIndexForMoneyStackItem(held.getItem());
        if (stackBillIndex >= 0) {
            processMoneyStackTender(player, cashier, session, held, stackBillIndex);
            return true;
        }
        if (held != null && !held.isEmpty() && held.is(ModItems.CREDIT_CARD.get())) {
            sendTerminalRedirect(player, session, true);
            return true;
        }

        ItemStack basket = ShelfService.findBasketInHands(player);
        if (!basket.isEmpty()) {
            String message = "Remaining: $" + MoneyText.abbreviate(BigDecimal.valueOf(remainingCents(session), 2));
            player.sendSystemMessage(Component.literal("§7" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.INFO, message, 4200);
            sendCancelHint(player);
            return true;
        }

        String message = "Pay with cash at the cashier, or use your credit card at the linked terminal.";
        player.sendSystemMessage(Component.literal("§7Pay with §acash§7 at the cashier, or use your §bcredit card§7 at the linked terminal."));
        pushCashierAlert(player, DeliveryAlertPayload.AlertTone.INFO, message, 4600);
        sendCancelHint(player);
        return true;
    }

    private static void processWalletCashTender(ServerPlayer player,
                                                BankTellerEntity cashier,
                                                Session session) {
        long remaining = remainingCents(session);
        if (remaining <= 0L) {
            completeSession(player, session);
            return;
        }

        WalletPaymentService.WalletCashTender tender = WalletPaymentService.takeCashForPayment(player, remaining);
        if (!tender.success()) {
            String message = "Wallet payment failed: " + tender.message();
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            sendCancelHint(player);
            return;
        }

        addCashPlan(session.insertedCash, tender.plan());
        addCashPlan(session.walletInsertedCash, tender.plan());
        session.walletCashOpenId = tender.walletOpenId();
        session.cashPaidCents += tender.tenderedCents();

        long newRemaining = remainingCents(session);
        if (newRemaining <= 0L) {
            completeSession(player, session);
            return;
        }

        player.sendSystemMessage(Component.literal("§aAccepted wallet cash. Remaining: §6$"
                + MoneyText.abbreviate(BigDecimal.valueOf(newRemaining, 2))));
        pushCashierAlert(player,
                DeliveryAlertPayload.AlertTone.SUCCESS,
                "Accepted wallet cash. Remaining: $" + MoneyText.abbreviate(BigDecimal.valueOf(newRemaining, 2)),
                4800);
        refreshTerminalForCheckout(player.getServer(), session);
        sendTerminalRedirect(player, session, false);
        sendCancelHint(player);
    }

    private static void processCashTender(ServerPlayer player,
                                          BankTellerEntity cashier,
                                          Session session,
                                          ItemStack stack,
                                          int cashIndex) {
        long remaining = remainingCents(session);
        if (remaining <= 0L) {
            completeSession(player, session);
            return;
        }

        int denominationCents = DollarBills.cashDenominationCentsForIndex(cashIndex);
        if (denominationCents <= 0) {
            return;
        }

        stack.shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        session.insertedCash[cashIndex] += 1;
        session.cashPaidCents += denominationCents;

        long newRemaining = remainingCents(session);
        if (newRemaining <= 0L) {
            completeSession(player, session);
            return;
        }

        player.sendSystemMessage(Component.literal("§aAccepted §6$"
                + MoneyText.abbreviate(BigDecimal.valueOf(denominationCents, 2))
                + "§a cash. Remaining: §6$"
                + MoneyText.abbreviate(BigDecimal.valueOf(newRemaining, 2))));
        pushCashierAlert(player,
                DeliveryAlertPayload.AlertTone.SUCCESS,
                "Accepted $" + MoneyText.abbreviate(BigDecimal.valueOf(denominationCents, 2))
                        + " cash. Remaining: $" + MoneyText.abbreviate(BigDecimal.valueOf(newRemaining, 2)),
                4800);
        refreshTerminalForCheckout(player.getServer(), session);
        sendTerminalRedirect(player, session, false);
        sendCancelHint(player);
    }

    private static void processMoneyStackTender(ServerPlayer player,
                                                BankTellerEntity cashier,
                                                Session session,
                                                ItemStack stack,
                                                int billIndex) {
        long remaining = remainingCents(session);
        if (remaining <= 0L) {
            completeSession(player, session);
            return;
        }

        int denominationCents = DollarBills.cashDenominationCentsForIndex(billIndex);
        if (denominationCents <= 0) {
            return;
        }
        long tenderedCents = (long) DollarBills.BILLS_PER_MONEY_STACK * denominationCents;

        stack.shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        session.insertedCash[billIndex] += DollarBills.BILLS_PER_MONEY_STACK;
        session.cashPaidCents += tenderedCents;

        long newRemaining = remainingCents(session);
        if (newRemaining <= 0L) {
            completeSession(player, session);
            return;
        }

        player.sendSystemMessage(Component.literal("\u00a7aAccepted \u00a76$"
                + MoneyText.abbreviate(BigDecimal.valueOf(tenderedCents, 2))
                + "\u00a7a money stack. Remaining: \u00a76$"
                + MoneyText.abbreviate(BigDecimal.valueOf(newRemaining, 2))));
        pushCashierAlert(player,
                DeliveryAlertPayload.AlertTone.SUCCESS,
                "Accepted $" + MoneyText.abbreviate(BigDecimal.valueOf(tenderedCents, 2))
                        + " money stack. Remaining: $" + MoneyText.abbreviate(BigDecimal.valueOf(newRemaining, 2)),
                4800);
        refreshTerminalForCheckout(player.getServer(), session);
        sendTerminalRedirect(player, session, false);
        sendCancelHint(player);
    }

    private static boolean processCardPayment(ServerPlayer player, Session session, boolean viaTerminal) {
        long remaining = remainingCents(session);
        if (remaining <= 0L) {
            completeSession(player, session);
            return true;
        }

        var server = player.getServer();
        if (server == null) {
            cancelAndRefund(player, session, "Server context is unavailable.", true);
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            cancelAndRefund(player, session, "Bank data is unavailable.", true);
            return false;
        }
        AccountHolder merchant = centralBank.SearchForAccountByAccountId(session.merchantAccountId);
        if (merchant == null) {
            cancelAndRefund(player, session, "Merchant account is unavailable.", true);
            return false;
        }

        ItemStack wallet = WalletPaymentService.findHeldWallet(player);
        if (!wallet.isEmpty()) {
            return processWalletCardPayment(player, session, centralBank, merchant, viaTerminal);
        }

        var cardLookup = CreditCardService.findHeldCard(centralBank, player);
        if (!cardLookup.hasCard()) {
            if (viaTerminal) {
                String message = "No credit card in hand. Hold your credit card and use the terminal again.";
                player.sendSystemMessage(Component.literal("§c" + message));
                pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            } else {
                String message = "No credit card detected. Hold a card and right-click again.";
                player.sendSystemMessage(Component.literal("§c" + message));
                pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            }
            return false;
        }
        if (!cardLookup.validation().valid()) {
            String message = "Card payment failed: " + cardLookup.validation().message();
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            if (viaTerminal) {
                triggerTerminalPulse(server, session, false, 0L);
            }
            return false;
        }
        AccountHolder payer = centralBank.SearchForAccountByAccountId(cardLookup.validation().accountId());
        if (payer == null || !player.getUUID().equals(payer.getPlayerUUID())) {
            String message = "Card payment failed: linked account is unavailable.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            if (viaTerminal) {
                triggerTerminalPulse(server, session, false, 0L);
            }
            return false;
        }

        BigDecimal amount = BigDecimal.valueOf(remaining, 2);
        UserTransaction tx = new UserTransaction(
                payer.getAccountUUID(),
                merchant.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "SHOP_CASHIER_CARD:" + safeShopName(session.shopName)
        );
        if (!tx.makeTransaction(server)) {
            String message = "Card payment failed.";
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            if (viaTerminal) {
                triggerTerminalPulse(server, session, false, 0L);
            }
            return false;
        }

        session.cardPaidCents += remaining;
        completeSession(player, session);
        return true;
    }

    private static boolean processWalletCardPayment(ServerPlayer player,
                                                    Session session,
                                                    CentralBank centralBank,
                                                    AccountHolder merchant,
                                                    boolean viaTerminal) {
        long remaining = remainingCents(session);
        var server = player.getServer();
        if (server == null) {
            return false;
        }

        var cards = WalletPaymentService.paymentCards(player, player.level().registryAccess());
        if (cards.isEmpty()) {
            String message = "Wallet card payment failed: " + WalletPaymentService.walletCardUnavailableMessage(player);
            player.sendSystemMessage(Component.literal("§c" + message));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, message, 5600);
            if (viaTerminal) {
                triggerTerminalPulse(server, session, false, 0L);
            }
            return false;
        }

        ItemStack wallet = WalletPaymentService.findHeldWallet(player);
        boolean fallback = WalletData.isCardFallbackEnabled(wallet);
        String firstFailure = "";
        for (WalletData.WalletCardSlot card : cards) {
            var validation = WalletPaymentService.validateWalletCard(centralBank, player, card.stack());
            if (!validation.valid()) {
                firstFailure = firstFailure.isBlank() ? "Slot " + (card.slot() + 1) + ": " + validation.message() : firstFailure;
                if (!fallback) {
                    break;
                }
                continue;
            }

            AccountHolder payer = centralBank.SearchForAccountByAccountId(validation.accountId());
            if (payer == null || !player.getUUID().equals(payer.getPlayerUUID())) {
                firstFailure = firstFailure.isBlank() ? "Slot " + (card.slot() + 1) + ": linked account is unavailable." : firstFailure;
                if (!fallback) {
                    break;
                }
                continue;
            }

            BigDecimal amount = BigDecimal.valueOf(remaining, 2);
            UserTransaction tx = new UserTransaction(
                    payer.getAccountUUID(),
                    merchant.getAccountUUID(),
                    amount,
                    LocalDateTime.now(),
                    "SHOP_CASHIER_CARD:" + safeShopName(session.shopName)
            );
            if (!tx.makeTransaction(server)) {
                firstFailure = firstFailure.isBlank() ? "Slot " + (card.slot() + 1) + ": charge failed." : firstFailure;
                if (!fallback) {
                    break;
                }
                continue;
            }

            session.cardPaidCents += remaining;
            completeSession(player, session);
            return true;
        }

        String message = firstFailure.isBlank() ? "No wallet card could complete the payment." : firstFailure;
        player.sendSystemMessage(Component.literal("§cWallet card payment failed: " + message));
        pushCashierAlert(player, DeliveryAlertPayload.AlertTone.ERROR, "Wallet card payment failed: " + message, 5600);
        if (viaTerminal) {
            triggerTerminalPulse(server, session, false, 0L);
        }
        return false;
    }

    private static void completeSession(ServerPlayer player, Session session) {
        long required = Math.max(0L, session.requiredCents);
        long paid = Math.max(0L, session.cashPaidCents + session.cardPaidCents);
        if (paid < required) {
            return;
        }
        long chargedFromAccountCents = Math.max(0L, session.cardPaidCents);

        var server = player.getServer();
        if (server == null) {
            cancelAndRefund(player, session, "Server context is unavailable.", true);
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            cancelAndRefund(player, session, "Bank data is unavailable.", true);
            return;
        }
        AccountHolder merchant = centralBank.SearchForAccountByAccountId(session.merchantAccountId);
        if (merchant == null) {
            cancelAndRefund(player, session, "Merchant account is unavailable.", true);
            return;
        }

        long cashContribution = Math.max(0L, required - session.cardPaidCents);
        if (cashContribution > 0L) {
            BigDecimal cashAmount = BigDecimal.valueOf(cashContribution, 2);
            if (!merchant.AddBalance(cashAmount)) {
                cancelAndRefund(player, session, "Unable to complete cash settlement. Refunding.", true);
                return;
            }
            merchant.addTransaction(new UserTransaction(
                    CASH_TENDER_SOURCE_ID,
                    merchant.getAccountUUID(),
                    cashAmount,
                    LocalDateTime.now(),
                    "SHOP_CASHIER_CASH:" + safeShopName(session.shopName)
            ));
        }

        long changeCents = Math.max(0L, paid - required);
        int[] changePlan = null;
        if (changeCents > 0L) {
            int changeInt;
            try {
                changeInt = Math.toIntExact(changeCents);
            } catch (ArithmeticException ex) {
                cancelAndRefund(player, session, "Unable to process change amount. Refunding.", true);
                return;
            }
            changePlan = DollarBills.buildCashWithdrawPlan(changeInt);
            if (changePlan == null) {
                cancelAndRefund(player, session, "Unable to prepare change cash. Refunding.", true);
                return;
            }
        }

        ItemStack basket = ShelfService.findBasketInHands(player);
        java.util.List<ShelfCartService.BasketEntryView> soldEntries = basket.isEmpty()
                ? java.util.List.of()
                : ShelfCartService.getEntries(basket);
        List<ItemStack> packedBags = buildShoppingBagsForCheckout(soldEntries, session.shopName);
        int bagCount = packedBags.size();
        if (bagCount > session.reservedBagCount) {
            int extraNeeded = bagCount - session.reservedBagCount;
            int extraPulled = ShopService.pullShoppingBagsFromStockroom(
                    server,
                    centralBank,
                    session.shopOwnerId,
                    session.shopId,
                    extraNeeded
            );
            if (extraPulled < extraNeeded) {
                if (extraPulled > 0) {
                    ShopService.pushShoppingBagsToStockroom(
                            server,
                            centralBank,
                            session.shopOwnerId,
                            session.shopId,
                            extraPulled
                    );
                }
                cancelAndRefund(player, session, "Shopping bag stock changed during checkout. Please try again.", true);
                return;
            }
            session.reservedBagCount += extraPulled;
        }
        int unusedReserved = Math.max(0, session.reservedBagCount - bagCount);
        if (unusedReserved > 0) {
            returnReservedBagsToStockroom(player, session, unusedReserved);
        }
        session.reservedBagCount = 0;
        clearBasket(player);
        ShelfBasketSessionService.completeCheckout(player);
        if (changePlan != null) {
            boolean returnedToWallet = hasCashPlan(session.walletInsertedCash);
            if (returnedToWallet) {
                WalletPaymentService.returnCashToWalletOrPlayer(player, session.walletCashOpenId, changePlan);
            } else {
                // Return change after restoring the shopper's original inventory snapshot.
                DollarBills.giveCash(player, changePlan);
            }
            player.sendSystemMessage(Component.literal("§aChange returned: §6$"
                    + MoneyText.abbreviate(BigDecimal.valueOf(changeCents, 2))
                    + (returnedToWallet ? " §7(to wallet)" : "")));
            pushCashierAlert(player,
                    DeliveryAlertPayload.AlertTone.SUCCESS,
                    "Change returned: $" + MoneyText.abbreviate(BigDecimal.valueOf(changeCents, 2))
                            + (returnedToWallet ? " to wallet" : ""),
                    4600);
        }
        int droppedBags = deliverPackedBags(player, packedBags);
        SESSIONS.remove(player.getUUID());

        long wholeDollars = required / 100L;
        ShopService.recordSaleForShop(centralBank, session.shopOwnerId, session.shopId, Math.max(0L, wholeDollars));
        ShopService.recordSlotSalesFromBasketEntries(centralBank, session.shopOwnerId, session.shopId, soldEntries);
        ShopService.recordCashVaultDeposit(centralBank, session.shopOwnerId, session.shopId, session.insertedCash);
        if (cashContribution > 0L) {
            ShopService.recordPaymentMethod(
                    centralBank,
                    session.shopOwnerId,
                    session.shopId,
                    player.getUUID(),
                    true,
                    cashContribution
            );
        }
        if (session.cardPaidCents > 0L) {
            ShopService.recordPaymentMethod(
                    centralBank,
                    session.shopOwnerId,
                    session.shopId,
                    player.getUUID(),
                    false,
                    session.cardPaidCents
            );
        }
        triggerTerminalPulse(server, session, true, wholeDollars);

        String totalLabel = MoneyText.abbreviate(BigDecimal.valueOf(required, 2));
        String chargedLabel = MoneyText.abbreviate(BigDecimal.valueOf(chargedFromAccountCents, 2));
        String checkoutSummary = "Checkout complete at " + safeShopName(session.shopName)
                + ". Total: $" + totalLabel
                + ". Charged from account: $" + chargedLabel + ".";
        player.sendSystemMessage(Component.literal("§a" + checkoutSummary));
        pushCashierAlert(player, DeliveryAlertPayload.AlertTone.SUCCESS, checkoutSummary, 5200);
        sendBagDeliverySummary(player, packedBags, droppedBags);

        ServerPlayer merchantPlayer = server.getPlayerList().getPlayer(merchant.getPlayerUUID());
        if (merchantPlayer != null && !merchantPlayer.getUUID().equals(player.getUUID())) {
            merchantPlayer.sendSystemMessage(Component.literal("§aSale completed at §b" + safeShopName(session.shopName)
                    + "§a: §6$" + totalLabel));
            ServerNotification.send(
                    merchantPlayer,
                    "Cashier Sale",
                    "Sale completed at " + safeShopName(session.shopName) + ": $" + totalLabel,
                    DeliveryAlertPayload.AlertTone.SUCCESS,
                    4600
            );
            PacketDistributor.sendToPlayer(merchantPlayer, UBSCommands.buildHudStatePayload(centralBank, merchantPlayer.getUUID()));
        }
        PacketDistributor.sendToPlayer(player, UBSCommands.buildHudStatePayload(centralBank, player.getUUID()));
    }

    private static void addCashPlan(int[] target, int[] plan) {
        if (target == null || plan == null) {
            return;
        }
        for (int i = 0; i < target.length && i < plan.length; i++) {
            target[i] += Math.max(0, plan[i]);
        }
    }

    private static int[] subtractCashPlan(int[] total, int[] subtract) {
        int size = DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length;
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            int base = total == null || i >= total.length ? 0 : Math.max(0, total[i]);
            int remove = subtract == null || i >= subtract.length ? 0 : Math.max(0, subtract[i]);
            result[i] = Math.max(0, base - remove);
        }
        return result;
    }

    private static boolean hasCashPlan(int[] plan) {
        if (plan == null) {
            return false;
        }
        for (int count : plan) {
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    private static void triggerTerminalPulse(MinecraftServer server, Session session, boolean success, long wholeDollars) {
        ServerLevel level = server.getLevel(serverLevelKey(session.terminalDimension));
        if (level == null) {
            return;
        }
        if (!(level.getBlockEntity(new net.minecraft.core.BlockPos(session.terminalX, session.terminalY, session.terminalZ))
                instanceof net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity terminal)) {
            return;
        }
        if (wholeDollars > 0L) {
            terminal.addSale(wholeDollars);
        }
        terminal.triggerPulse(success);
    }

    private static void refreshTerminalForCheckout(MinecraftServer server, Session session) {
        if (server == null || session == null) {
            return;
        }
        ServerLevel level = server.getLevel(serverLevelKey(session.terminalDimension));
        if (level == null) {
            return;
        }
        if (level.getBlockEntity(new BlockPos(session.terminalX, session.terminalY, session.terminalZ))
                instanceof ShopTerminalBlockEntity terminal) {
            terminal.clearPaymentResult();
        }
    }

    private static void cancelAndRefund(ServerPlayer player, Session session, String reason, boolean includeCancelHint) {
        if (session == null) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        releaseReservedBags(player, session);
        if (session.cashPaidCents > 0L) {
            int[] physicalRefund = subtractCashPlan(session.insertedCash, session.walletInsertedCash);
            if (hasCashPlan(session.walletInsertedCash)) {
                WalletPaymentService.returnCashToWalletOrPlayer(player, session.walletCashOpenId, session.walletInsertedCash);
            }
            if (hasCashPlan(physicalRefund)) {
                DollarBills.giveCash(player, physicalRefund);
            }
        }
        if (reason != null && !reason.isBlank()) {
            player.sendSystemMessage(Component.literal("§e" + reason));
            pushCashierAlert(player, DeliveryAlertPayload.AlertTone.WARNING, reason, 5200);
        }
        if (includeCancelHint) {
            sendCancelHint(player);
        }
        var server = player.getServer();
        if (server != null) {
            triggerTerminalPulse(server, session, false, 0L);
        }
    }

    private static void clearBasket(ServerPlayer player) {
        ItemStack basket = ShelfService.findBasketInHands(player);
        if (!basket.isEmpty()) {
            ShelfCartService.applyBasketData(basket, new net.minecraft.nbt.CompoundTag());
        }
    }

    private static int estimateRequiredBagCount(List<ShelfCartService.BasketEntryView> soldEntries, String shopName) {
        return Math.max(0, buildShoppingBagsForCheckout(soldEntries, shopName).size());
    }

    private static ShopBagReservation reserveShoppingBagsForCheckout(MinecraftServer server,
                                                                     CentralBank centralBank,
                                                                     UUID shopOwnerId,
                                                                     UUID shopId,
                                                                     int requiredBags) {
        int needed = Math.max(0, requiredBags);
        if (needed <= 0) {
            return new ShopBagReservation(true, Integer.MAX_VALUE, 0);
        }
        int available = ShopService.countShoppingBagsInStockroom(server, centralBank, shopOwnerId, shopId);
        if (available < needed) {
            return new ShopBagReservation(false, Math.max(0, available), 0);
        }
        int pulled = ShopService.pullShoppingBagsFromStockroom(server, centralBank, shopOwnerId, shopId, needed);
        if (pulled < needed) {
            if (pulled > 0) {
                ShopService.pushShoppingBagsToStockroom(server, centralBank, shopOwnerId, shopId, pulled);
            }
            return new ShopBagReservation(false, Math.max(0, available), Math.max(0, pulled));
        }
        return new ShopBagReservation(true, Math.max(0, available), Math.max(0, pulled));
    }

    private static void sendBagStockShortageMessage(ServerPlayer player, int availableBags, int requiredBags) {
        if (player == null) {
            return;
        }
        int available = Math.max(0, availableBags);
        int required = Math.max(0, requiredBags);
        player.sendSystemMessage(Component.literal(
                "§cCheckout unavailable: this shop has §f" + available
                        + "§c shopping bag(s), but needs §f" + required + "§c."
        ));
        player.sendSystemMessage(Component.literal(
                "§7Ask the shop owner to restock §fshopping_bag§7 items in the stockroom."
        ));
        pushCashierAlert(player,
                DeliveryAlertPayload.AlertTone.ERROR,
                "Checkout unavailable: this shop has " + available
                        + " shopping bag(s), but needs " + required
                        + ". Ask the shop owner to restock shopping_bag items in the stockroom.",
                6200);
    }

    private static void releaseReservedBags(ServerPlayer player, Session session) {
        if (player == null || session == null || session.reservedBagCount <= 0) {
            return;
        }
        returnReservedBagsToStockroom(player, session, session.reservedBagCount);
        session.reservedBagCount = 0;
    }

    private static void returnReservedBagsToStockroom(ServerPlayer player, Session session, int count) {
        if (player == null || session == null || count <= 0) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            giveFallbackShoppingBags(player, count);
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            giveFallbackShoppingBags(player, count);
            return;
        }
        int returned = ShopService.pushShoppingBagsToStockroom(
                server,
                centralBank,
                session.shopOwnerId,
                session.shopId,
                count
        );
        int overflow = Math.max(0, count - returned);
        if (overflow > 0) {
            giveFallbackShoppingBags(player, overflow);
        }
    }

    private static void giveFallbackShoppingBags(ServerPlayer player, int count) {
        if (player == null || count <= 0) {
            return;
        }
        int remaining = Math.max(0, count);
        while (remaining > 0) {
            int move = Math.min(64, remaining);
            ItemStack bagStack = new ItemStack(ModBlocks.SHOPPING_BAG.get().asItem(), move);
            if (!player.getInventory().add(bagStack.copy())) {
                dropBagOnGroundForBuyer(player, bagStack.copy());
            }
            remaining -= move;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static List<ItemStack> buildShoppingBagsForCheckout(List<ShelfCartService.BasketEntryView> soldEntries, String shopName) {
        List<ItemStack> soldStacks = expandSoldEntries(soldEntries);
        List<ItemStack> bags = new ArrayList<>();
        if (soldStacks.isEmpty()) {
            return bags;
        }

        ItemStackHandler currentBag = new ItemStackHandler(ShoppingBagBlockEntity.SLOT_COUNT);
        for (ItemStack soldStack : soldStacks) {
            ItemStack remaining = soldStack.copy();
            while (!remaining.isEmpty()) {
                remaining = ItemHandlerHelper.insertItemStacked(currentBag, remaining, false);
                if (!remaining.isEmpty()) {
                    if (!isBagHandlerEmpty(currentBag)) {
                        bags.add(toShoppingBagItem(currentBag, shopName));
                    }
                    currentBag = new ItemStackHandler(ShoppingBagBlockEntity.SLOT_COUNT);
                }
            }
        }

        if (!isBagHandlerEmpty(currentBag)) {
            bags.add(toShoppingBagItem(currentBag, shopName));
        }
        return bags;
    }

    private static List<ItemStack> expandSoldEntries(List<ShelfCartService.BasketEntryView> soldEntries) {
        List<ItemStack> soldStacks = new ArrayList<>();
        if (soldEntries == null || soldEntries.isEmpty()) {
            return soldStacks;
        }
        for (ShelfCartService.BasketEntryView entry : soldEntries) {
            if (entry == null || entry.stack() == null || entry.stack().isEmpty() || entry.quantity() <= 0) {
                continue;
            }
            ItemStack sample = entry.stack().copy();
            sample.setCount(1);
            int maxStack = Math.max(1, sample.getMaxStackSize());
            int remainingQty = Math.max(0, entry.quantity());
            while (remainingQty > 0) {
                int move = Math.min(maxStack, remainingQty);
                ItemStack toPack = sample.copy();
                toPack.setCount(move);
                soldStacks.add(toPack);
                remainingQty -= move;
            }
        }
        return soldStacks;
    }

    private static boolean isBagHandlerEmpty(ItemStackHandler handler) {
        if (handler == null) {
            return true;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack toShoppingBagItem(ItemStackHandler handler, String shopName) {
        ItemStack bag = new ItemStack(ModBlocks.SHOPPING_BAG.get().asItem());
        CompoundTag root = ItemStackDataCompat.getCustomData(bag);
        CompoundTag bagData = ItemStackDataCompat.serializeHandler(handler);
        if (!bagData.isEmpty()) {
            root.put(ShoppingBagDataKeys.BAG_DATA_KEY, bagData);
        }
        String safeName = safeShopName(shopName);
        root.putString(ShoppingBagDataKeys.BAG_STORE_NAME_KEY, safeName);
        ItemStackDataCompat.setCustomData(bag, root);
        ItemStackDataCompat.setCustomName(bag, Component.literal("[" + safeName + "] Shopping Bag").withStyle(ChatFormatting.GOLD));
        return bag;
    }

    private static int deliverPackedBags(ServerPlayer player, List<ItemStack> packedBags) {
        if (player == null || packedBags == null || packedBags.isEmpty()) {
            return 0;
        }
        int dropped = 0;
        for (ItemStack bag : packedBags) {
            if (bag == null || bag.isEmpty()) {
                continue;
            }
            ItemStack give = bag.copy();
            if (!player.getInventory().add(give)) {
                dropBagOnGroundForBuyer(player, give);
                dropped += 1;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return dropped;
    }

    private static void sendBagDeliverySummary(ServerPlayer player, List<ItemStack> packedBags, int droppedBags) {
        if (player == null || packedBags == null || packedBags.isEmpty()) {
            return;
        }
        int givenBags = Math.max(0, packedBags.size() - Math.max(0, droppedBags));
        if (droppedBags > 0) {
            player.sendSystemMessage(Component.literal(
                    "§ePacked your items into §f" + packedBags.size() + "§e shopping bag(s). "
                            + "§c" + droppedBags + "§e bag(s) were dropped because your inventory is full."
            ));
            player.sendSystemMessage(Component.literal(
                    "§7Dropped shopping bags are reserved for §fyou§7 (or admins) and despawn in §c2:00§7."
            ));
            pushCashierAlert(player,
                    DeliveryAlertPayload.AlertTone.WARNING,
                    "Packed " + packedBags.size() + " shopping bag(s). "
                            + droppedBags + " bag(s) were dropped because your inventory is full. "
                            + "Dropped bags are reserved for you (or admins) and despawn in 2:00.",
                    6200);
            return;
        }
        player.sendSystemMessage(Component.literal(
                "§aPacked your items into §f" + givenBags + "§a shopping bag(s)."
        ));
        pushCashierAlert(player,
                DeliveryAlertPayload.AlertTone.SUCCESS,
                "Packed your items into " + givenBags + " shopping bag(s).",
                4600);
    }

    private static void tickBagDrops(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (server.getTickCount() % BAG_DROP_LABEL_REFRESH_TICKS == 0) {
            discoverBagDrops(server);
        }
        if (BAG_DROPS.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(BAG_DROPS.keySet());
        List<ActiveBagDrop> activeDrops = new ArrayList<>();
        for (UUID id : ids) {
            BagDropSession drop = BAG_DROPS.get(id);
            if (drop == null) {
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(drop.dimensionId));
            if (level == null) {
                BAG_DROPS.remove(id);
                continue;
            }
            Entity entity = level.getEntity(drop.itemEntityId);
            if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                BAG_DROPS.remove(id);
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (!isProtectedBagDrop(stack)) {
                itemEntity.setCustomName(null);
                itemEntity.setCustomNameVisible(false);
                BAG_DROPS.remove(id);
                continue;
            }
            long now = level.getGameTime();
            long expiresAt = readBagDropExpiresTick(stack, drop.expiresAtTick);
            if (now >= expiresAt) {
                itemEntity.setCustomName(null);
                itemEntity.setCustomNameVisible(false);
                itemEntity.discard();
                BAG_DROPS.remove(id);
                continue;
            }
            activeDrops.add(new ActiveBagDrop(id, itemEntity, now, expiresAt));
        }
        if (activeDrops.isEmpty()) {
            return;
        }

        // Start by hiding all labels, then enable only one hologram per tight cluster.
        for (ActiveBagDrop drop : activeDrops) {
            drop.itemEntity.setCustomNameVisible(false);
        }

        boolean[] assigned = new boolean[activeDrops.size()];
        for (int i = 0; i < activeDrops.size(); i++) {
            if (assigned[i]) {
                continue;
            }
            List<Integer> cluster = new ArrayList<>();
            cluster.add(i);
            assigned[i] = true;

            // Flood-fill nearby drops so tightly packed bags share one readable hologram.
            for (int cursor = 0; cursor < cluster.size(); cursor++) {
                int sourceIndex = cluster.get(cursor);
                ActiveBagDrop source = activeDrops.get(sourceIndex);
                for (int j = 0; j < activeDrops.size(); j++) {
                    if (assigned[j]) {
                        continue;
                    }
                    ActiveBagDrop candidate = activeDrops.get(j);
                    if (!source.itemEntity.level().dimension().equals(candidate.itemEntity.level().dimension())) {
                        continue;
                    }
                    if (source.itemEntity.distanceToSqr(candidate.itemEntity) <= BAG_DROP_HOLOGRAM_CLUSTER_RANGE_SQ) {
                        assigned[j] = true;
                        cluster.add(j);
                    }
                }
            }

            int winnerIndex = cluster.get(0);
            for (int index : cluster) {
                ActiveBagDrop best = activeDrops.get(winnerIndex);
                ActiveBagDrop candidate = activeDrops.get(index);
                if (candidate.remainingTicks < best.remainingTicks) {
                    winnerIndex = index;
                }
            }

            ActiveBagDrop winner = activeDrops.get(winnerIndex);
            refreshBagDropLabel(winner.itemEntity, winner.nowTick, winner.expiresAtTick);
        }
    }

    private static void discoverBagDrops(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                    continue;
                }
                ItemStack stack = itemEntity.getItem();
                if (!isProtectedBagDrop(stack)) {
                    continue;
                }
                long expiresAt = readBagDropExpiresTick(stack, now + BAG_DROP_DESPAWN_TICKS);
                BAG_DROPS.putIfAbsent(itemEntity.getUUID(), new BagDropSession(
                        itemEntity.getUUID(),
                        level.dimension().location().toString(),
                        expiresAt
                ));
            }
        }
    }

    private static long readBagDropExpiresTick(ItemStack stack, long fallback) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        if (tag == null || !tag.contains(ShoppingBagDataKeys.BAG_DROP_EXPIRES_TICK_KEY)) {
            return Math.max(0L, fallback);
        }
        return Math.max(0L, tag.getLong(ShoppingBagDataKeys.BAG_DROP_EXPIRES_TICK_KEY));
    }

    public static boolean isProtectedBagDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ModBlocks.SHOPPING_BAG.get().asItem())) {
            return false;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        return tag != null
                && tag.hasUUID(ShoppingBagDataKeys.BAG_DROP_OWNER_KEY)
                && tag.contains(ShoppingBagDataKeys.BAG_DROP_EXPIRES_TICK_KEY);
    }

    public static boolean canPickupProtectedBag(ServerPlayer player, ItemStack stack) {
        if (player == null || !isProtectedBagDrop(stack)) {
            return true;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        if (tag == null || !tag.hasUUID(ShoppingBagDataKeys.BAG_DROP_OWNER_KEY)) {
            return true;
        }
        UUID ownerId = tag.getUUID(ShoppingBagDataKeys.BAG_DROP_OWNER_KEY);
        return player.hasPermissions(3) || ownerId.equals(player.getUUID());
    }

    public static void clearProtectedBagDrop(ItemEntity itemEntity) {
        if (itemEntity == null) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        clearProtectedBagDropTags(stack);
        itemEntity.setItem(stack);
        itemEntity.setCustomName(null);
        itemEntity.setCustomNameVisible(false);
        BAG_DROPS.remove(itemEntity.getUUID());
    }

    private static void clearProtectedBagDropTags(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStackDataCompat.removeCustomData(ShoppingBagDataKeys.BAG_DROP_OWNER_KEY, stack);
        ItemStackDataCompat.removeCustomData(ShoppingBagDataKeys.BAG_DROP_OWNER_NAME_KEY, stack);
        ItemStackDataCompat.removeCustomData(ShoppingBagDataKeys.BAG_DROP_EXPIRES_TICK_KEY, stack);
    }

    private static void dropBagOnGroundForBuyer(ServerPlayer player, ItemStack bag) {
        if (player == null || bag == null || bag.isEmpty()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            player.drop(bag, false);
            return;
        }

        long now = level.getGameTime();
        long expiresAt = now + BAG_DROP_DESPAWN_TICKS;
        markBagAsProtectedDrop(bag, player.getUUID(), player.getName().getString(), expiresAt);

        // Spawn directly on the ground with no launch velocity.
        ItemEntity itemEntity = new ItemEntity(
                level,
                player.getX(),
                player.getY() + 0.05D,
                player.getZ(),
                bag.copy()
        );
        itemEntity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        itemEntity.setPickUpDelay(0);
        refreshBagDropLabel(itemEntity, now, expiresAt);
        level.addFreshEntity(itemEntity);
        BAG_DROPS.put(itemEntity.getUUID(), new BagDropSession(
                itemEntity.getUUID(),
                level.dimension().location().toString(),
                expiresAt
        ));
    }

    private static void markBagAsProtectedDrop(ItemStack bag, UUID ownerId, String ownerName, long expiresAtTick) {
        if (bag == null || bag.isEmpty() || ownerId == null) {
            return;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(bag);
        tag.putUUID(ShoppingBagDataKeys.BAG_DROP_OWNER_KEY, ownerId);
        tag.putString(
                ShoppingBagDataKeys.BAG_DROP_OWNER_NAME_KEY,
                ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName.trim()
        );
        tag.putLong(ShoppingBagDataKeys.BAG_DROP_EXPIRES_TICK_KEY, expiresAtTick);
        ItemStackDataCompat.setCustomData(bag, tag);
    }

    private static void refreshBagDropLabel(ItemEntity itemEntity, long nowTick, long expiresAtTick) {
        if (itemEntity == null) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        String storeName = (tag != null && tag.contains(ShoppingBagDataKeys.BAG_STORE_NAME_KEY))
                ? safeShopName(tag.getString(ShoppingBagDataKeys.BAG_STORE_NAME_KEY))
                : "Shop";
        String ownerName = (tag != null && tag.contains(ShoppingBagDataKeys.BAG_DROP_OWNER_NAME_KEY))
                ? safeShopName(tag.getString(ShoppingBagDataKeys.BAG_DROP_OWNER_NAME_KEY))
                : "Unknown";
        long remaining = Math.max(0L, expiresAtTick - nowTick);
        itemEntity.setCustomName(Component.literal("[" + storeName + "] Bag ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("owner: " + ownerName + " | ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("despawns in " + formatTickCountdown(remaining))
                        .withStyle(ChatFormatting.YELLOW)));
        itemEntity.setCustomNameVisible(true);
    }

    private static String formatTickCountdown(long ticksRemaining) {
        long totalTenths = Math.max(0L, (ticksRemaining + 1L) / 2L);
        long minutes = totalTenths / 600L;
        long seconds = (totalTenths / 10L) % 60L;
        long tenths = totalTenths % 10L;
        return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths);
    }

    private static void tickCashierBagStatus(MinecraftServer server) {
        if (server == null || (server.getTickCount() % CASHIER_BAG_STATUS_REFRESH_TICKS) != 0L) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        int lowThreshold = ShopService.shopCashierLowBagThreshold();
        Map<String, Integer> bagCountCache = new HashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof BankTellerEntity cashier) || !cashier.isCashier()) {
                    continue;
                }
                UUID ownerId = cashier.getOwnerUUID();
                UUID shopId = cashier.getShopId();
                if (ownerId == null || shopId == null) {
                    continue;
                }

                String cacheKey = ownerId + "|" + shopId;
                int bagCount = bagCountCache.computeIfAbsent(
                        cacheKey,
                        ignored -> ShopService.countShoppingBagsInStockroom(server, centralBank, ownerId, shopId)
                );
                String warning = null;
                ChatFormatting warningColor = ChatFormatting.GREEN;
                if (bagCount <= 0) {
                    warning = "Shopping Bags Out Of Stock";
                    warningColor = ChatFormatting.RED;
                } else if (bagCount <= lowThreshold) {
                    warning = "Shopping Bags Low: " + bagCount;
                    warningColor = ChatFormatting.GOLD;
                }
                updateCashierHologram(cashier, warning, warningColor);
            }
        }
    }

    private static void updateCashierHologram(BankTellerEntity cashier,
                                              String warning,
                                              ChatFormatting warningColor) {
        if (cashier == null) {
            return;
        }
        String currentRaw = cashier.getCustomName() == null ? "" : cashier.getCustomName().getString();
        // Always rebuild from a stable base label so warning text can never
        // compound across refresh ticks.
        String baseLine = resolveCashierBaseLabel(cashier);

        MutableComponent targetName = Component.literal(baseLine).withStyle(ChatFormatting.AQUA);
        if (warning != null && !warning.isBlank()) {
            targetName = targetName
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(warning).withStyle(warningColor));
        }
        String targetRaw = targetName.getString();
        if (targetRaw.equals(currentRaw)) {
            return;
        }
        // Keep cashier bag-state hologram synced for both owners and shoppers.
        cashier.setCustomName(targetName);
        cashier.setCustomNameVisible(true);
    }

    private static String resolveCashierBaseLabel(BankTellerEntity cashier) {
        if (cashier == null) {
            return "Cashier";
        }
        String role = "Cashier";
        UUID bankId = cashier.getBoundBankId();
        if (bankId == null || !(cashier.level() instanceof ServerLevel serverLevel)) {
            return role;
        }
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return role;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return role;
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return role;
        }
        String bankName = bank.getBankName() == null ? "" : bank.getBankName().trim();
        if (bankName.isBlank()) {
            return role;
        }
        return "[" + bankName + "] " + role;
    }

    private static long remainingCents(Session session) {
        return Math.max(0L, session.requiredCents - session.cashPaidCents - session.cardPaidCents);
    }

    private static void sendPrompt(ServerPlayer player, Session session) {
        player.sendSystemMessage(Component.literal("§bCheckout started at §f" + safeShopName(session.shopName)));
        player.sendSystemMessage(Component.literal("§7Total: §6$"
                + MoneyText.abbreviate(BigDecimal.valueOf(session.requiredCents, 2))));
        player.sendSystemMessage(Component.literal("§7You have §e30 seconds§7 to complete payment."));
        player.sendSystemMessage(Component.literal("§7Right-click cashier with §acash§7. For §bcredit card§7 payment, use the linked terminal."));
        pushCashierAlert(player,
                DeliveryAlertPayload.AlertTone.INFO,
                "Checkout started at " + safeShopName(session.shopName)
                        + ". Total: $" + MoneyText.abbreviate(BigDecimal.valueOf(session.requiredCents, 2))
                        + ". You have 30 seconds to complete payment.",
                5600);
        sendTerminalRedirect(player, session, false);
        sendCancelHint(player);
    }

    private static Session findActiveSessionForCashier(MinecraftServer server, UUID cashierId, UUID excludePlayerId) {
        if (cashierId == null) {
            return null;
        }
        List<UUID> ids = new ArrayList<>(SESSIONS.keySet());
        for (UUID playerId : ids) {
            if (playerId == null || playerId.equals(excludePlayerId)) {
                continue;
            }
            Session session = SESSIONS.get(playerId);
            if (session == null) {
                continue;
            }
            if (!cashierId.equals(session.cashierId)) {
                continue;
            }
            if (server == null) {
                return session;
            }
            ServerPlayer checkoutPlayer = server.getPlayerList().getPlayer(playerId);
            if (checkoutPlayer == null) {
                SESSIONS.remove(playerId);
                continue;
            }
            long now = checkoutPlayer.serverLevel().getGameTime();
            if (Math.abs(now - session.startedTick) > SESSION_TIMEOUT_TICKS) {
                cancelAndRefund(checkoutPlayer, session, "Checkout timed out after 30 seconds.", true);
                continue;
            }
            return session;
        }
        return null;
    }

    private static void sendCancelHint(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§8Type §f/cashier cancel §8to cancel checkout."));
        pushCashierAlert(player, DeliveryAlertPayload.AlertTone.INFO, "Type /cashier cancel to cancel checkout.", 4200);
    }

    private static void sendTerminalRedirect(ServerPlayer player, Session session, boolean cardHeldAtCashier) {
        long remaining = remainingCents(session);
        player.sendSystemMessage(Component.literal("§7Linked terminal: §f" + terminalLabel(session)));
        if (remaining > 0L) {
            player.sendSystemMessage(Component.literal("§7Remaining for card payment: §6$"
                    + MoneyText.abbreviate(BigDecimal.valueOf(remaining, 2))));
        }
        if (cardHeldAtCashier) {
            player.sendSystemMessage(Component.literal("§eUse your credit card on the linked payment terminal to continue checkout."));
            pushCashierAlert(player,
                    DeliveryAlertPayload.AlertTone.INFO,
                    "Use your credit card on the linked payment terminal to continue checkout."
                            + (remaining > 0L ? " Remaining: $" + MoneyText.abbreviate(BigDecimal.valueOf(remaining, 2)) : ""),
                    5000);
        } else {
            player.sendSystemMessage(Component.literal("§7Use your credit card on that terminal to pay the remaining amount."));
            pushCashierAlert(player,
                    DeliveryAlertPayload.AlertTone.INFO,
                    "Use your credit card on the linked terminal to pay the remaining amount."
                            + (remaining > 0L ? " Remaining: $" + MoneyText.abbreviate(BigDecimal.valueOf(remaining, 2)) : ""),
                    5000);
        }
    }

    private static boolean terminalMatchesSession(Session session, ServerLevel level, BlockPos pos) {
        if (session == null || level == null || pos == null) {
            return false;
        }
        return session.terminalX == pos.getX()
                && session.terminalY == pos.getY()
                && session.terminalZ == pos.getZ()
                && session.terminalDimension.equals(level.dimension().location().toString());
    }

    private static String terminalLabel(Session session) {
        if (session == null) {
            return "-";
        }
        return session.terminalDimension + " (" + session.terminalX + ", " + session.terminalY + ", " + session.terminalZ + ")";
    }

    private static void pushCashierAlert(ServerPlayer player,
                                         DeliveryAlertPayload.AlertTone tone,
                                         String message) {
        pushCashierAlert(player, tone, message, 4800);
    }

    private static void pushCashierAlert(ServerPlayer player,
                                         DeliveryAlertPayload.AlertTone tone,
                                         String message,
                                         int durationMs) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        ServerNotification.sendLegacy(player, "Cashier", message, tone, durationMs);
    }

    private static String safeShopName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Shop";
        }
        return raw.trim();
    }

    private static String resolveCashierShopName(BankTellerEntity cashier) {
        if (cashier == null) {
            return "Shop";
        }
        Component customName = cashier.getCustomName();
        if (customName == null) {
            return "Shop";
        }
        String raw = customName.getString();
        if (raw == null || raw.isBlank()) {
            return "Shop";
        }
        String normalized = raw.replace("Cashier", "").trim();
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return safeShopName(normalized.isBlank() ? raw : normalized);
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> serverLevelKey(String dimId) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(dimId == null ? "" : dimId.trim());
        if (id == null) {
            id = net.minecraft.world.level.Level.OVERWORLD.location();
        }
        return net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.createValueKey(
                net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.DIMENSION_REGISTRY_KEY,
                id
        );
    }
}
