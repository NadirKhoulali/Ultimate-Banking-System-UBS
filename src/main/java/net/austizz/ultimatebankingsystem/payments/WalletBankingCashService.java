package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.WalletData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class WalletBankingCashService {
    private WalletBankingCashService() {
    }

    public static CashStorage resolve(ServerPlayer player) {
        ItemStack heldWallet = WalletData.findHeldWallet(player);
        boolean walletHeld = !heldWallet.isEmpty();
        if (walletHeld) {
            WalletData.ensureOwner(heldWallet, player);
        }
        CashStoragePreference preference = CashStoragePreference.select(
                walletHeld,
                walletHeld && WalletData.isOwner(heldWallet, player)
        );
        return new CashStorage(
                preference == CashStoragePreference.WALLET ? heldWallet : ItemStack.EMPTY,
                preference
        );
    }

    public record CashStorage(ItemStack wallet, CashStoragePreference preference) {
        public CashStorage {
            wallet = wallet == null ? ItemStack.EMPTY : wallet;
            preference = preference == null ? CashStoragePreference.CARRIED_CASH : preference;
        }

        public boolean walletBacked() {
            return preference == CashStoragePreference.WALLET;
        }

        public String label() {
            return walletBacked() ? "wallet cash" : "carried cash";
        }

        public int[] availableCounts(ServerPlayer player) {
            return walletBacked()
                    ? WalletData.getCashCounts(wallet)
                    : DollarBills.getAvailableTenderAsCashCounts(player);
        }

        public boolean canAdd(int[] plan) {
            return !walletBacked() || WalletData.canAddCashPlan(wallet, plan);
        }

        public boolean remove(ServerPlayer player, int[] plan) {
            if (walletBacked()) {
                if (!WalletData.removeCashPlan(wallet, plan)) {
                    return false;
                }
                sync(player);
                return true;
            }
            DollarBills.removeTender(player, plan);
            return true;
        }

        public boolean add(ServerPlayer player, int[] plan) {
            if (walletBacked()) {
                if (!WalletData.canAddCashPlan(wallet, plan)) {
                    return false;
                }
                WalletData.addCashPlan(wallet, plan);
                sync(player);
                return true;
            }
            DollarBills.giveCash(player, plan);
            return true;
        }

        private static void sync(ServerPlayer player) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}
