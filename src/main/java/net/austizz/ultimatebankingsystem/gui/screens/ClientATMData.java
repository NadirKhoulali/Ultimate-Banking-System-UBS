package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side cache for ATM account data received from the server.
 *
 * <p>Populated by the {@code AccountListPayload} handler and cleared when the
 * ATM screen closes. This allows screen layers to read account information
 * without additional network round-trips.</p>
 */
@OnlyIn(Dist.CLIENT)
public class ClientATMData {

    private static List<AccountSummary> accounts = new ArrayList<>();
    private static AccountSummary selectedAccount = null;
    private static UUID authenticatedAccountId = null;

    public static void setAccounts(List<AccountSummary> accs) { accounts = new ArrayList<>(accs); }
    public static List<AccountSummary> getAccounts() { return accounts; }
    public static AccountSummary getSelectedAccount() { return selectedAccount; }
    public static void setSelectedAccount(AccountSummary acc) {
        selectedAccount = acc;
        if (acc == null) {
            return;
        }
        for (int i = 0; i < accounts.size(); i++) {
            AccountSummary current = accounts.get(i);
            if (current.accountId().equals(acc.accountId())) {
                accounts.set(i, acc);
                break;
            }
        }
    }
    public static void setAuthenticatedAccountId(UUID accountId) { authenticatedAccountId = accountId; }
    public static UUID getAuthenticatedAccountId() { return authenticatedAccountId; }
    public static boolean isSelectedAccountAuthenticated() {
        return selectedAccount != null
                && authenticatedAccountId != null
                && selectedAccount.accountId().equals(authenticatedAccountId);
    }
    public static void clear() {
        accounts.clear();
        selectedAccount = null;
        authenticatedAccountId = null;
    }

    public static void refreshTemporaryWithdrawalLimitExpiry(long currentGameTime) {
        if (selectedAccount == null) {
            return;
        }

        if (selectedAccount.temporaryWithdrawalLimit().isBlank()) {
            return;
        }

        if (selectedAccount.temporaryLimitExpiresAtGameTime() > currentGameTime) {
            return;
        }

        AccountSummary refreshed = new AccountSummary(
                selectedAccount.accountId(),
                selectedAccount.accountType(),
                selectedAccount.bankName(),
                selectedAccount.balance(),
                selectedAccount.isPrimary(),
                selectedAccount.pinSet(),
                selectedAccount.defaultWithdrawalLimit(),
                selectedAccount.defaultWithdrawalLimit(),
                "",
                -1L,
                selectedAccount.dailyWithdrawalLimit(),
                selectedAccount.dailyWithdrawnToday(),
                selectedAccount.dailyWithdrawalRemaining(),
                selectedAccount.dailyResetEpochMillis()
        );

        selectedAccount = refreshed;
        for (int i = 0; i < accounts.size(); i++) {
            AccountSummary account = accounts.get(i);
            if (account.accountId().equals(refreshed.accountId())) {
                accounts.set(i, refreshed);
                break;
            }
        }
    }

    public static void applyPrimaryState(UUID accountId, boolean isPrimary) {
        if (accountId == null) {
            return;
        }

        for (int i = 0; i < accounts.size(); i++) {
            AccountSummary account = accounts.get(i);
            boolean nextPrimary = isPrimary
                    ? account.accountId().equals(accountId)
                    : (account.accountId().equals(accountId) ? false : account.isPrimary());
            if (account.isPrimary() != nextPrimary) {
                accounts.set(i, withPrimary(account, nextPrimary));
            }
        }

        if (selectedAccount != null) {
            boolean selectedNextPrimary = isPrimary
                    ? selectedAccount.accountId().equals(accountId)
                    : (selectedAccount.accountId().equals(accountId) ? false : selectedAccount.isPrimary());
            if (selectedAccount.isPrimary() != selectedNextPrimary) {
                selectedAccount = withPrimary(selectedAccount, selectedNextPrimary);
            }
        }
    }

    private static AccountSummary withPrimary(AccountSummary account, boolean isPrimary) {
        return new AccountSummary(
                account.accountId(),
                account.accountType(),
                account.bankName(),
                account.balance(),
                isPrimary,
                account.pinSet(),
                account.defaultWithdrawalLimit(),
                account.effectiveWithdrawalLimit(),
                account.temporaryWithdrawalLimit(),
                account.temporaryLimitExpiresAtGameTime(),
                account.dailyWithdrawalLimit(),
                account.dailyWithdrawnToday(),
                account.dailyWithdrawalRemaining(),
                account.dailyResetEpochMillis()
        );
    }
}
