package net.austizz.ultimatebankingsystem.migration.numismatics;

import com.mojang.authlib.GameProfile;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NumismaticsAccountMigrator {
    private static final UUID MIGRATION_SYSTEM_ACCOUNT = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:numismatics-migration".getBytes(StandardCharsets.UTF_8));

    private NumismaticsAccountMigrator() {
    }

    public static Result migrate(MinecraftServer server,
                                 NumismaticsSourceSnapshot source,
                                 NumismaticsMigrationSavedData journal) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) return new Result(0, 0, BigDecimal.ZERO, List.of("Central Bank data is unavailable."));
        List<String> errors = new ArrayList<>();
        int created = 0;
        int credited = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (NumismaticsAccountRecord record : source.accounts()) {
            if (journal.isAccountApplied(record.sourceAccountId())) continue;
            try {
                AccountHolder target;
                boolean wasCreated = false;
                UUID mapped = journal.accountMappings().get(record.sourceAccountId());
                if (mapped != null) {
                    target = centralBank.SearchForAccountByAccountId(mapped);
                    if (target == null) throw new IllegalStateException("Mapped UBS account no longer exists: " + mapped);
                } else if (record.kind() == NumismaticsAccountRecord.AccountKind.BLAZE_BANKER) {
                    if (record.trustedPlayers().isEmpty()) {
                        throw new IllegalStateException("Shared account has no trusted co-owner.");
                    }
                    target = createJointAccount(centralBank, record);
                    wasCreated = true;
                    journal.mapAccount(record.sourceAccountId(), target.getAccountUUID());
                } else {
                    AccountResolution resolution = resolvePlayerAccount(centralBank, record.sourceAccountId());
                    target = resolution.account();
                    wasCreated = resolution.created();
                    journal.mapAccount(record.sourceAccountId(), target.getAccountUUID());
                }

                BigDecimal amount = BigDecimal.valueOf(
                        Math.multiplyExact((long) record.balanceSpurs(), journal.options().centsPerSpur()), 2);
                UUID transactionId = UUID.nameUUIDFromBytes(("numismatics:" + journal.sourceHash() + ":"
                        + record.sourceAccountId()).getBytes(StandardCharsets.UTF_8));
                if (amount.signum() > 0 && !target.getTransactions().containsKey(transactionId)) {
                    if (!target.forceAddBalance(amount)) throw new IllegalStateException("UBS rejected the balance credit.");
                    target.addTransaction(new UserTransaction(
                            MIGRATION_SYSTEM_ACCOUNT,
                            target.getAccountUUID(),
                            amount,
                            LocalDateTime.now(),
                            "Create: Numismatics migration (" + record.sourceAccountId() + ")",
                            transactionId
                    ));
                    total = total.add(amount);
                    credited++;
                }
                if (wasCreated) created++;
                journal.markAccountApplied(record.sourceAccountId());
                journal.audit("ACCOUNT", record.sourceAccountId() + " -> " + target.getAccountUUID()
                        + " amount=" + amount.toPlainString());
            } catch (RuntimeException exception) {
                errors.add(record.sourceAccountId() + ": " + rootMessage(exception));
            }
        }
        BankManager.markDirty();
        return new Result(created, credited, total, errors);
    }

    private static AccountResolution resolvePlayerAccount(CentralBank centralBank, UUID playerId) {
        AccountHolder primary = centralBank.SearchForAccount(playerId).values().stream()
                .filter(AccountHolder::isPrimaryAccount)
                .max(Comparator.comparing(AccountHolder::getDateOfCreation))
                .orElse(null);
        if (primary != null) return new AccountResolution(primary, false);

        AccountHolder centralAccount = centralBank.getBankAccounts().values().stream()
                .filter(account -> account != null && account.isOwnedByPlayer(playerId))
                .filter(account -> account.getAccountType() == AccountTypes.CheckingAccount)
                .findFirst()
                .orElse(null);
        if (centralAccount != null) {
            centralBank.setPrimaryAccountForPlayer(playerId, centralAccount.getAccountUUID(), true);
            return new AccountResolution(centralAccount, false);
        }

        AccountHolder created = new AccountHolder(playerId, BigDecimal.ZERO, AccountTypes.CheckingAccount,
                "", centralBank.getBankId(), UUID.randomUUID());
        created.setPrimaryAccount(true);
        if (!centralBank.AddAccount(created)) {
            throw new IllegalStateException("Could not create a Central Bank checking account.");
        }
        centralBank.setPrimaryAccountForPlayer(playerId, created.getAccountUUID(), true);
        return new AccountResolution(created, true);
    }

    private static AccountHolder createJointAccount(CentralBank centralBank, NumismaticsAccountRecord source) {
        UUID owner = source.trustedPlayers().getFirst();
        UUID targetId = UUID.randomUUID();
        AccountHolder account = new AccountHolder(owner, BigDecimal.ZERO, AccountTypes.CheckingAccount,
                "", centralBank.getBankId(), targetId);
        account.setAccountAccessType("JOINT");
        account.setBusinessLabel(source.label().isBlank() ? "Migrated shared account" : source.label());
        for (UUID member : source.trustedPlayers()) account.grantAccessRole(member, "OWNER");
        // Shared migration accounts must not be rejected because their first co-owner has a personal checking account.
        centralBank.getBankAccounts().put(targetId, account);
        BankManager.markDirty();
        return account;
    }

    public static String profileName(MinecraftServer server, UUID playerId) {
        if (playerId == null) return "Migrated Account Holder";
        Optional<GameProfile> cached = server.getProfileCache().get(playerId);
        return cached.map(GameProfile::getName).filter(name -> !name.isBlank()).orElse(playerId.toString());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private record AccountResolution(AccountHolder account, boolean created) {
    }

    public record Result(int createdAccounts, int creditedAccounts, BigDecimal creditedAmount, List<String> errors) {
        public Result { errors = errors == null ? List.of() : List.copyOf(errors); }
        public boolean success() { return errors.isEmpty(); }
    }
}
