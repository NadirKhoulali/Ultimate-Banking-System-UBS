package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.command.UBSCommands;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.network.HandheldTerminalOpenPayload;
import net.austizz.ultimatebankingsystem.network.ShopTerminalAccountSummary;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HandheldPaymentTerminalItem extends Item {
    private static final long RESULT_DISPLAY_MILLIS = 2_000L;

    public static final String TAG_TERMINAL_ID = "ubs_hterm_id";
    public static final String TAG_OWNER_ID = "ubs_hterm_owner";
    public static final String TAG_OWNER_NAME = "ubs_hterm_owner_name";
    public static final String TAG_SHOP_NAME = "ubs_hterm_shop_name";
    public static final String TAG_PRICE_DOLLARS = "ubs_hterm_price_dollars";
    public static final String TAG_MERCHANT_ACCOUNT_ID = "ubs_hterm_merchant_account";
    public static final String TAG_TOTAL_SALES_DOLLARS = "ubs_hterm_total_sales";
    public static final String TAG_RESULT = "ubs_hterm_result";
    public static final String TAG_RESULT_UNTIL = "ubs_hterm_result_until";

    public static final int RESULT_IDLE = 0;
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_DENIED = 2;

    public HandheldPaymentTerminalItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        syncExpiredResultState(stack);
        if (player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer) {
            openConfig(serverPlayer, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack,
                                                  Player player,
                                                  LivingEntity interactionTarget,
                                                  InteractionHand hand) {
        syncExpiredResultState(stack);
        if (player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer) {
            openConfig(serverPlayer, stack);
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        syncExpiredResultState(stack);
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    private static void openConfig(ServerPlayer player, ItemStack stack) {
        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            player.sendSystemMessage(Component.literal("§cBank data is unavailable right now."));
            return;
        }

        UUID terminalId = ensureTerminalId(stack);
        if (!canConfigure(stack, player)) {
            player.sendSystemMessage(Component.literal("§cOnly the owner or an operator can configure this handheld terminal."));
            return;
        }

        ensureOwner(stack, player);
        PacketDistributor.sendToPlayer(player, buildOpenPayload(centralBank, player.getUUID(), stack, terminalId));
    }

    public static void processCharge(ServerPlayer merchantPlayer, ServerPlayer payerPlayer, ItemStack stack) {
        if (isFeedbackActive(stack)) {
            long remainingMs = feedbackMillisRemaining(stack);
            long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
            String message = "Handheld terminal is busy. Try again in " + seconds + "s.";
            sendChargeFailure(merchantPlayer, payerPlayer, message);
            return;
        }

        CentralBank centralBank = BankManager.getCentralBank(merchantPlayer.server);
        if (centralBank == null) {
            sendChargeFailure(merchantPlayer, payerPlayer, "Bank data is unavailable right now.");
            setResultState(stack, RESULT_DENIED);
            return;
        }

        UUID merchantAccountId = getMerchantAccountId(stack);
        if (merchantAccountId == null) {
            AccountHolder merchantPrimary = findPrimaryAccount(centralBank, merchantPlayer.getUUID());
            if (merchantPrimary != null) {
                merchantAccountId = merchantPrimary.getAccountUUID();
                updateConfig(stack, getShopName(stack), getPriceDollars(stack), merchantAccountId);
            } else {
                sendChargeFailure(merchantPlayer, payerPlayer, "Merchant account is not configured.");
                setResultState(stack, RESULT_DENIED);
                return;
            }
        }

        AccountHolder merchantAccount = centralBank.SearchForAccountByAccountId(merchantAccountId);
        if (merchantAccount == null) {
            sendChargeFailure(merchantPlayer, payerPlayer, "Merchant account is unavailable.");
            setResultState(stack, RESULT_DENIED);
            return;
        }

        long price = getPriceDollars(stack);
        if (price <= 0L) {
            sendChargeFailure(merchantPlayer, payerPlayer, "Price is invalid on this handheld terminal.");
            setResultState(stack, RESULT_DENIED);
            return;
        }

        PayerLookup lookup = resolvePayerAccount(centralBank, payerPlayer);
        if (!lookup.success()) {
            sendChargeFailure(merchantPlayer, payerPlayer, lookup.errorMessage());
            setResultState(stack, RESULT_DENIED);
            return;
        }

        String sourceRef = "handheld@" + merchantPlayer.getUUID() + ":" + ensureTerminalId(stack);
        var result = UltimateBankingApiProvider.get().shopPurchase(
                lookup.account().getAccountUUID(),
                merchantAccount.getAccountUUID(),
                price,
                getShopName(stack),
                sourceRef
        );

        if (!result.success()) {
            sendChargeFailure(merchantPlayer, payerPlayer, result.reason());
            setResultState(stack, RESULT_DENIED);
            return;
        }

        addSale(stack, price);
        setResultState(stack, RESULT_SUCCESS);

        String amountText = "$" + MoneyText.abbreviate(BigDecimal.valueOf(price));
        String shop = getShopName(stack);
        if (merchantPlayer.getUUID().equals(payerPlayer.getUUID())) {
            merchantPlayer.sendSystemMessage(Component.literal(
                    "§aPaid " + amountText + " §aat §b" + shop + "§a. Balance: §6$" + result.balanceAfter().toPlainString()
            ));
        } else {
            payerPlayer.sendSystemMessage(Component.literal(
                    "§aPayment complete: §6" + amountText + " §ato §b" + shop + "§a. Balance: §6$" + result.balanceAfter().toPlainString()
            ));
            merchantPlayer.sendSystemMessage(Component.literal(
                    "§aPayment received: §6" + amountText + " §afrom §f" + payerPlayer.getName().getString() + " §aat §b" + shop
            ));
        }

        PacketDistributor.sendToPlayer(payerPlayer, UBSCommands.buildHudStatePayload(centralBank, payerPlayer.getUUID()));
        if (!merchantPlayer.getUUID().equals(payerPlayer.getUUID())) {
            PacketDistributor.sendToPlayer(merchantPlayer, UBSCommands.buildHudStatePayload(centralBank, merchantPlayer.getUUID()));
        }
    }

    private static PayerLookup resolvePayerAccount(CentralBank centralBank, ServerPlayer payer) {
        var cardLookup = CreditCardService.findHeldCard(centralBank, payer);
        if (cardLookup.hasCard()) {
            if (!cardLookup.validation().valid()) {
                return PayerLookup.fail("Credit card rejected: " + cardLookup.validation().message());
            }
            AccountHolder linked = centralBank.SearchForAccountByAccountId(cardLookup.validation().accountId());
            if (linked == null || !payer.getUUID().equals(linked.getPlayerUUID())) {
                return PayerLookup.fail("Linked card account is unavailable.");
            }
            return PayerLookup.ok(linked);
        }

        AccountHolder primary = findPrimaryAccount(centralBank, payer.getUUID());
        if (primary == null) {
            return PayerLookup.fail("No primary account is set for " + payer.getName().getString() + ".");
        }
        return PayerLookup.ok(primary);
    }

    private static void sendChargeFailure(ServerPlayer merchant, ServerPlayer payer, String reason) {
        String safeReason = reason == null || reason.isBlank() ? "Unknown error." : reason;
        if (merchant.getUUID().equals(payer.getUUID())) {
            merchant.sendSystemMessage(Component.literal("§cPayment failed: " + safeReason));
            return;
        }
        merchant.sendSystemMessage(Component.literal(
                "§cCharge failed for §f" + payer.getName().getString() + "§c: " + safeReason
        ));
        payer.sendSystemMessage(Component.literal(
                "§cPayment request from §f" + merchant.getName().getString() + " §cfailed: " + safeReason
        ));
    }

    public static HandheldTerminalOpenPayload buildOpenPayload(CentralBank centralBank,
                                                               UUID playerId,
                                                               ItemStack stack,
                                                               UUID terminalId) {
        List<ShopTerminalAccountSummary> accountSummaries = new ArrayList<>();
        if (centralBank != null) {
            var accounts = centralBank.SearchForAccount(playerId)
                    .values()
                    .stream()
                    .sorted(Comparator
                            .comparing(AccountHolder::isPrimaryAccount).reversed()
                            .thenComparing(account -> account.getDateOfCreation().toString()))
                    .toList();

            for (AccountHolder account : accounts) {
                Bank bank = centralBank.getBank(account.getBankId());
                String bankName = bank == null ? "Unknown Bank" : bank.getBankName();
                accountSummaries.add(new ShopTerminalAccountSummary(
                        account.getAccountUUID(),
                        account.getAccountType().label,
                        bankName,
                        account.getBalance().toPlainString(),
                        account.isPrimaryAccount()
                ));
            }
        }

        UUID merchant = getMerchantAccountId(stack);
        return new HandheldTerminalOpenPayload(
                terminalId == null ? "" : terminalId.toString(),
                getShopName(stack),
                getPriceDollars(stack),
                getOwnerName(stack),
                merchant == null ? "" : merchant.toString(),
                getTotalSalesDollars(stack),
                accountSummaries
        );
    }

    public static boolean canConfigure(ItemStack stack, ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(3)) {
            return true;
        }
        UUID owner = getOwnerId(stack);
        return owner == null || owner.equals(player.getUUID());
    }

    public static void ensureOwner(ItemStack stack, ServerPlayer player) {
        UUID owner = getOwnerId(stack);
        if (owner != null) {
            return;
        }
        CompoundTag tag = getOrCreateData(stack);
        tag.putUUID(TAG_OWNER_ID, player.getUUID());
        tag.putString(TAG_OWNER_NAME, player.getName().getString());
        writeData(stack, tag);
    }

    public static ItemStack findByTerminalId(ServerPlayer player, UUID terminalId) {
        if (player == null || terminalId == null) {
            return ItemStack.EMPTY;
        }

        ItemStack main = player.getMainHandItem();
        if (isHandheldTerminal(main) && terminalId.equals(readTerminalId(main))) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isHandheldTerminal(off) && terminalId.equals(readTerminalId(off))) {
            return off;
        }

        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isHandheldTerminal(stack) && terminalId.equals(readTerminalId(stack))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findHeldTerminal(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (isHandheldTerminal(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isHandheldTerminal(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    public static UUID ensureTerminalId(ItemStack stack) {
        UUID existing = readTerminalId(stack);
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        CompoundTag tag = getOrCreateData(stack);
        tag.putUUID(TAG_TERMINAL_ID, created);
        if (!tag.contains(TAG_SHOP_NAME) || tag.getString(TAG_SHOP_NAME).isBlank()) {
            tag.putString(TAG_SHOP_NAME, "Handheld Terminal");
        }
        if (!tag.contains(TAG_PRICE_DOLLARS)) {
            tag.putLong(TAG_PRICE_DOLLARS, 50L);
        }
        writeData(stack, tag);
        setResultState(stack, RESULT_IDLE);
        return created;
    }

    public static UUID readTerminalId(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        return tag.hasUUID(TAG_TERMINAL_ID) ? tag.getUUID(TAG_TERMINAL_ID) : null;
    }

    public static String getShopName(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        String raw = tag.contains(TAG_SHOP_NAME) ? tag.getString(TAG_SHOP_NAME).trim() : "";
        return raw.isBlank() ? "Handheld Terminal" : raw;
    }

    public static long getPriceDollars(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        return clampPrice(tag.contains(TAG_PRICE_DOLLARS) ? tag.getLong(TAG_PRICE_DOLLARS) : 50L);
    }

    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        String value = tag.contains(TAG_OWNER_NAME) ? tag.getString(TAG_OWNER_NAME).trim() : "";
        return value.isBlank() ? "Unknown" : value;
    }

    public static UUID getOwnerId(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        return tag.hasUUID(TAG_OWNER_ID) ? tag.getUUID(TAG_OWNER_ID) : null;
    }

    public static UUID getMerchantAccountId(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        return tag.hasUUID(TAG_MERCHANT_ACCOUNT_ID) ? tag.getUUID(TAG_MERCHANT_ACCOUNT_ID) : null;
    }

    public static long getTotalSalesDollars(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        return Math.max(0L, tag.contains(TAG_TOTAL_SALES_DOLLARS) ? tag.getLong(TAG_TOTAL_SALES_DOLLARS) : 0L);
    }

    public static void addSale(ItemStack stack, long amount) {
        if (amount <= 0L) {
            return;
        }
        CompoundTag tag = getOrCreateData(stack);
        long total = Math.max(0L, tag.getLong(TAG_TOTAL_SALES_DOLLARS));
        long next;
        try {
            next = Math.addExact(total, amount);
        } catch (ArithmeticException ex) {
            next = Long.MAX_VALUE;
        }
        tag.putLong(TAG_TOTAL_SALES_DOLLARS, next);
        writeData(stack, tag);
    }

    public static void updateConfig(ItemStack stack, String shopName, long priceDollars, UUID merchantAccountId) {
        CompoundTag tag = getOrCreateData(stack);
        String cleanShop = shopName == null ? "" : shopName.trim();
        if (cleanShop.isBlank()) {
            cleanShop = "Handheld Terminal";
        }
        if (cleanShop.length() > 42) {
            cleanShop = cleanShop.substring(0, 42);
        }
        tag.putString(TAG_SHOP_NAME, cleanShop);
        tag.putLong(TAG_PRICE_DOLLARS, clampPrice(priceDollars));
        if (merchantAccountId == null) {
            tag.remove(TAG_MERCHANT_ACCOUNT_ID);
        } else {
            tag.putUUID(TAG_MERCHANT_ACCOUNT_ID, merchantAccountId);
        }
        writeData(stack, tag);
    }

    public static boolean isFeedbackActive(ItemStack stack) {
        syncExpiredResultState(stack);
        return getResultState(stack) != RESULT_IDLE;
    }

    public static long feedbackMillisRemaining(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        long until = tag.contains(TAG_RESULT_UNTIL) ? tag.getLong(TAG_RESULT_UNTIL) : 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public static int getResultState(ItemStack stack) {
        syncExpiredResultState(stack);
        CompoundTag tag = getOrCreateData(stack);
        int value = tag.contains(TAG_RESULT) ? tag.getInt(TAG_RESULT) : RESULT_IDLE;
        return Math.max(RESULT_IDLE, Math.min(RESULT_DENIED, value));
    }

    public static void setResultState(ItemStack stack, int result) {
        int clamped = Math.max(RESULT_IDLE, Math.min(RESULT_DENIED, result));
        CompoundTag tag = getOrCreateData(stack);
        tag.putInt(TAG_RESULT, clamped);
        if (clamped == RESULT_IDLE) {
            tag.remove(TAG_RESULT_UNTIL);
        } else {
            tag.putLong(TAG_RESULT_UNTIL, System.currentTimeMillis() + RESULT_DISPLAY_MILLIS);
        }
        writeData(stack, tag);
        ItemStackDataCompat.setCustomModelData(stack, clamped);
    }

    public static void syncExpiredResultState(ItemStack stack) {
        CompoundTag tag = getOrCreateData(stack);
        int result = tag.contains(TAG_RESULT) ? tag.getInt(TAG_RESULT) : RESULT_IDLE;
        if (result == RESULT_IDLE) {
            if (!ItemStackDataCompat.hasCustomModelData(stack)) {
                ItemStackDataCompat.setCustomModelData(stack, RESULT_IDLE);
            }
            return;
        }
        long until = tag.contains(TAG_RESULT_UNTIL) ? tag.getLong(TAG_RESULT_UNTIL) : 0L;
        if (until > System.currentTimeMillis()) {
            ItemStackDataCompat.setCustomModelData(stack, Math.max(RESULT_IDLE, Math.min(RESULT_DENIED, result)));
            return;
        }
        tag.putInt(TAG_RESULT, RESULT_IDLE);
        tag.remove(TAG_RESULT_UNTIL);
        writeData(stack, tag);
        ItemStackDataCompat.setCustomModelData(stack, RESULT_IDLE);
    }

    public static boolean isHandheldTerminal(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModItems.HANDHELD_PAYMENT_TERMINAL.get();
    }

    public static String summarizeForOverlay(ItemStack stack) {
        String name = getShopName(stack);
        long price = getPriceDollars(stack);
        return name + " | $" + MoneyText.abbreviate(BigDecimal.valueOf(price));
    }

    private static long clampPrice(long value) {
        if (value < 1L) {
            return 1L;
        }
        long configuredMax = Math.max(1L, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get());
        return Math.min(value, configuredMax);
    }

    private static CompoundTag getOrCreateData(ItemStack stack) {
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        if (tag == null) {
            tag = new CompoundTag();
        }
        return tag;
    }

    private static void writeData(ItemStack stack, CompoundTag tag) {
        ItemStackDataCompat.setCustomData(stack, tag == null ? new CompoundTag() : tag);
    }

    private static AccountHolder findPrimaryAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        var accounts = centralBank.SearchForAccount(playerId);
        if (accounts == null || accounts.isEmpty()) {
            return null;
        }
        for (AccountHolder account : accounts.values()) {
            if (account != null && account.isPrimaryAccount()) {
                return account;
            }
        }
        return null;
    }

    private record PayerLookup(AccountHolder account, String errorMessage) {
        static PayerLookup ok(AccountHolder account) {
            return new PayerLookup(account, "");
        }

        static PayerLookup fail(String message) {
            String safe = message == null || message.isBlank() ? "Payment account is unavailable." : message;
            return new PayerLookup(null, safe);
        }

        boolean success() {
            return account != null;
        }
    }
}
