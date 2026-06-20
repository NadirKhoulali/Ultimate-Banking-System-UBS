package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.WalletData;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class WalletPaymentService {
    public record WalletCashTender(boolean success,
                                   String message,
                                   UUID walletOpenId,
                                   int[] plan,
                                   long tenderedCents) {
    }

    public record WalletCardCandidate(int slot,
                                      CreditCardService.CardValidationResult validation) {
    }

    private WalletPaymentService() {
    }

    public static ItemStack findHeldWallet(ServerPlayer player) {
        return WalletData.findHeldWallet(player);
    }

    public static boolean shouldHandleAsWalletPayment(ServerPlayer player) {
        return !findHeldWallet(player).isEmpty();
    }

    public static WalletCashTender takeCashForPayment(ServerPlayer player, long requiredCents) {
        ItemStack wallet = findHeldWallet(player);
        if (wallet.isEmpty()) {
            return new WalletCashTender(false, "No wallet in hand.", null, new int[WalletData.CASH_SLOT_COUNT], 0L);
        }
        WalletData.ensureOwner(wallet, player);
        if (!WalletData.isOwner(wallet, player)) {
            return new WalletCashTender(false, "This wallet belongs to " + WalletData.getOwnerName(wallet) + ".", null,
                    new int[WalletData.CASH_SLOT_COUNT], 0L);
        }
        if (WalletData.getMode(wallet) != WalletData.PaymentMode.CASH) {
            return new WalletCashTender(false, "Wallet is set to card mode.", null, new int[WalletData.CASH_SLOT_COUNT], 0L);
        }
        if (requiredCents <= 0L) {
            return new WalletCashTender(false, "No payment is required.", null, new int[WalletData.CASH_SLOT_COUNT], 0L);
        }
        int[] available = WalletData.getCashCounts(wallet);
        long total = 0L;
        for (int i = 0; i < available.length; i++) {
            total += (long) available[i] * DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i];
        }
        if (total < requiredCents) {
            return new WalletCashTender(false, "Wallet cash is short by $" + DollarBills.formatCents((int) Math.min(Integer.MAX_VALUE, requiredCents - total)) + ".",
                    null, new int[WalletData.CASH_SLOT_COUNT], 0L);
        }
        int[] plan = findSmallestCoveringPlan(available, requiredCents, total);
        if (plan == null) {
            return new WalletCashTender(false, "Wallet cash cannot cover this exact/overpay amount.", null,
                    new int[WalletData.CASH_SLOT_COUNT], 0L);
        }
        if (!WalletData.removeCashPlan(wallet, plan)) {
            return new WalletCashTender(false, "Wallet cash changed before payment could finish.", null,
                    new int[WalletData.CASH_SLOT_COUNT], 0L);
        }
        UUID walletOpenId = WalletData.ensureOpenReference(wallet);
        return new WalletCashTender(true, "OK", walletOpenId, plan, totalForPlan(plan));
    }

    public static void returnCashToWalletOrPlayer(ServerPlayer player, UUID walletOpenId, int[] plan) {
        if (player == null || plan == null || Arrays.stream(plan).allMatch(value -> value <= 0)) {
            return;
        }
        ItemStack wallet = WalletData.findWalletByOpenId(player, walletOpenId);
        if (!wallet.isEmpty() && WalletData.isOwner(wallet, player)) {
            WalletData.addCashPlan(wallet, plan);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return;
        }
        WalletData.giveCashPlanToPlayer(player, plan);
    }

    public static List<WalletData.WalletCardSlot> paymentCards(ServerPlayer player, HolderLookup.Provider registries) {
        ItemStack wallet = findHeldWallet(player);
        if (wallet.isEmpty()) {
            return List.of();
        }
        WalletData.ensureOwner(wallet, player);
        if (!WalletData.isOwner(wallet, player) || WalletData.getMode(wallet) != WalletData.PaymentMode.CARD) {
            return List.of();
        }
        return WalletData.getPaymentCards(wallet, registries);
    }

    public static String walletCardUnavailableMessage(ServerPlayer player) {
        ItemStack wallet = findHeldWallet(player);
        if (wallet.isEmpty()) {
            return "No wallet in hand.";
        }
        WalletData.ensureOwner(wallet, player);
        if (!WalletData.isOwner(wallet, player)) {
            return "This wallet belongs to " + WalletData.getOwnerName(wallet) + ".";
        }
        if (WalletData.getMode(wallet) != WalletData.PaymentMode.CARD) {
            return "Wallet is set to cash mode.";
        }
        return "Wallet has no credit card in the active slot" + (WalletData.isCardFallbackEnabled(wallet) ? "s." : ".");
    }

    public static CreditCardService.CardValidationResult validateWalletCard(CentralBank centralBank,
                                                                           ServerPlayer player,
                                                                           ItemStack cardStack) {
        return CreditCardService.validateCardStack(centralBank, cardStack, player == null ? null : player.getUUID());
    }

    public static boolean ownsLinkedAccount(ServerPlayer player,
                                            CentralBank centralBank,
                                            CreditCardService.CardValidationResult validation) {
        if (player == null || centralBank == null || validation == null || validation.accountId() == null) {
            return false;
        }
        AccountHolder payer = centralBank.SearchForAccountByAccountId(validation.accountId());
        return payer != null && player.getUUID().equals(payer.getPlayerUUID());
    }

    private static int[] findSmallestCoveringPlan(int[] available, long requiredCents, long totalAvailable) {
        if (requiredCents > Integer.MAX_VALUE) {
            return null;
        }
        int required = (int) requiredCents;
        int upper = (int) Math.min(totalAvailable, requiredCents + 9_999L);
        for (int target = required; target <= upper; target++) {
            int[] plan = DollarBills.findCashDepositPlan(target, available);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private static long totalForPlan(int[] plan) {
        long total = 0L;
        for (int i = 0; i < plan.length && i < DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length; i++) {
            total += (long) Math.max(0, plan[i]) * DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i];
        }
        return total;
    }
}
