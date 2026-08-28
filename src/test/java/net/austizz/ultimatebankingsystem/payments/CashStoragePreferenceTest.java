package net.austizz.ultimatebankingsystem.payments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CashStoragePreferenceTest {
    @Test
    void ownedHeldWalletTakesPriorityOverLooseInventoryCash() {
        assertEquals(CashStoragePreference.WALLET,
                CashStoragePreference.select(true, true));
    }

    @Test
    void absentOrForeignWalletKeepsExistingCarriedCashBehavior() {
        assertEquals(CashStoragePreference.CARRIED_CASH,
                CashStoragePreference.select(false, false));
        assertEquals(CashStoragePreference.CARRIED_CASH,
                CashStoragePreference.select(true, false));
    }
}
