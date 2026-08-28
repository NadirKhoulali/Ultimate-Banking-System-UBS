package net.austizz.ultimatebankingsystem.payments;

public enum CashStoragePreference {
    WALLET,
    CARRIED_CASH;

    public static CashStoragePreference select(boolean walletHeld, boolean walletOwned) {
        return walletHeld && walletOwned ? WALLET : CARRIED_CASH;
    }
}
