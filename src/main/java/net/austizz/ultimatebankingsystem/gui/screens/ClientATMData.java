package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
    public static void setSelectedAccount(AccountSummary acc) { selectedAccount = acc; }
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
}
