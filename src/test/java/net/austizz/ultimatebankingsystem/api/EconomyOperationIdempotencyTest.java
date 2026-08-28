package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyOperationIdempotencyTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void duplicateTransferReturnsOriginalReceiptWithoutMovingMoneyTwice() throws Exception {
        seedEconomyConfig();

        Object centralBank = load("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank")
                .getConstructor().newInstance();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        UUID senderPlayer = UUID.randomUUID();
        Object sender = createAccount(senderPlayer, new BigDecimal("100.00"), bankId);
        Object receiver = createAccount(UUID.randomUUID(), BigDecimal.ZERO, bankId);
        centralBank.getClass().getMethod("AddAccount", load("net.austizz.ultimatebankingsystem.account.AccountHolder"))
                .invoke(centralBank, sender);
        centralBank.getClass().getMethod("AddAccount", load("net.austizz.ultimatebankingsystem.account.AccountHolder"))
                .invoke(centralBank, receiver);

        Object api = economyApi(centralBank);
        String key = "web-transfer:test-0001";
        Object request = transferRequest(key, senderPlayer, accountId(sender), accountId(receiver), "25.00");
        Object first = execute(api, request);
        Object duplicate = execute(api, request);

        assertTrue(success(first), resultSummary(first));
        assertFalse((boolean) first.getClass().getMethod("duplicate").invoke(first));
        assertTrue(success(duplicate));
        assertTrue((boolean) duplicate.getClass().getMethod("duplicate").invoke(duplicate));
        assertEquals(new BigDecimal("75.00"), balance(sender));
        assertEquals(new BigDecimal("25.00"), balance(receiver));
        assertEquals(first.getClass().getMethod("operationId").invoke(first),
                duplicate.getClass().getMethod("operationId").invoke(duplicate));

        Object conflict = execute(api,
                transferRequest(key, senderPlayer, accountId(sender), accountId(receiver), "5.00"));
        assertFalse(success(conflict));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.getClass().getMethod("code").invoke(conflict));
        assertEquals(new BigDecimal("75.00"), balance(sender));
        assertEquals(new BigDecimal("25.00"), balance(receiver));

        Object reloadedBank = reloadCentralBank(centralBank);
        Object replayAfterRestart = execute(economyApi(reloadedBank), request);
        assertTrue(success(replayAfterRestart));
        assertTrue((boolean) replayAfterRestart.getClass().getMethod("duplicate").invoke(replayAfterRestart));
        Object reloadedSender = reloadedBank.getClass()
                .getMethod("SearchForAccountByAccountId", UUID.class).invoke(reloadedBank, accountId(sender));
        Object reloadedReceiver = reloadedBank.getClass()
                .getMethod("SearchForAccountByAccountId", UUID.class).invoke(reloadedBank, accountId(receiver));
        assertEquals(new BigDecimal("75.00"), balance(reloadedSender));
        assertEquals(new BigDecimal("25.00"), balance(reloadedReceiver));
    }

    @Test
    void matchedEscrowFundingIsAtomicAndReleasesTheCompletePot() throws Exception {
        seedEconomyConfig();
        Object centralBank = load("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank")
                .getConstructor().newInstance();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        UUID attackerPlayer = UUID.randomUUID();
        Object attacker = createAccount(attackerPlayer, new BigDecimal("100.00"), bankId);
        Object defender = createAccount(UUID.randomUUID(), new BigDecimal("100.00"), bankId);
        addAccount(centralBank, attacker);
        addAccount(centralBank, defender);
        Object api = economyApi(centralBank);

        Object created = execute(api, operationRequest(
                "escrow:create:war-1", "CREATE_ESCROW", "OFFICIAL_SYSTEM",
                null, null, null, bankId, null, "", "war-1", "",
                "0.00", "Territory war stake", Map.of("purpose", "Matched war stake"), List.of()));
        assertTrue(success(created), resultSummary(created));
        Object escrow = created.getClass().getMethod("escrow").invoke(created);
        UUID holdingId = (UUID) escrow.getClass().getMethod("holdingAccountId").invoke(escrow);
        Object holding = centralBank.getClass().getMethod("SearchForAccountByAccountId", UUID.class)
                .invoke(centralBank, holdingId);

        Object invalidFunding = execute(api, operationRequest(
                "escrow:fund:war-1:bad", "FUND_ESCROW", "OFFICIAL_SYSTEM",
                null, null, null, null, null, "", "war-1", "",
                "0.00", "", Map.of(), List.of(
                        transferLeg(accountId(attacker), null, "30.00"),
                        transferLeg(accountId(defender), null, "130.00"))));
        assertFalse(success(invalidFunding));
        assertEquals(new BigDecimal("100.00"), balance(attacker));
        assertEquals(new BigDecimal("100.00"), balance(defender));
        assertEquals(new BigDecimal("0"), balance(holding));

        Object funded = execute(api, operationRequest(
                "escrow:fund:war-1", "FUND_ESCROW", "OFFICIAL_SYSTEM",
                null, null, null, null, null, "", "war-1", "",
                "0.00", "", Map.of(), List.of(
                        transferLeg(accountId(attacker), null, "30.00"),
                        transferLeg(accountId(defender), null, "30.00"))));
        assertTrue(success(funded), resultSummary(funded));
        assertEquals(new BigDecimal("70.00"), balance(attacker));
        assertEquals(new BigDecimal("70.00"), balance(defender));
        assertEquals(new BigDecimal("60.00"), balance(holding));
        assertEquals(1, recordList(snapshotForPlayer(api, attackerPlayer), "escrows").size());
        assertTrue(recordList(snapshotForPlayer(api, UUID.randomUUID()), "escrows").isEmpty());

        Object released = execute(api, operationRequest(
                "escrow:release:war-1", "RELEASE_ESCROW", "OFFICIAL_SYSTEM",
                null, null, accountId(attacker), null, null, "", "war-1", "",
                "0.00", "Approved victory", Map.of(), List.of()));
        assertTrue(success(released), resultSummary(released));
        assertEquals(new BigDecimal("130.00"), balance(attacker));
        assertEquals(new BigDecimal("70.00"), balance(defender));
        assertEquals(new BigDecimal("0.00"), balance(holding));
    }

    @Test
    void reconciliationNormalizesLegacySubCentBalancesInsteadOfReportingZero() throws Exception {
        seedEconomyConfig();
        Object centralBank = load("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank")
                .getConstructor().newInstance();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        addAccount(centralBank, createAccount(UUID.randomUUID(), new BigDecimal("12.346"), bankId));

        Class<?> snapshotRequest = load(
                "net.austizz.ultimatebankingsystem.api.economy.ApiEconomySnapshotRequest");
        Object request = snapshotRequest.getMethod("reconciliation").invoke(null);
        Object snapshot = economyApi(centralBank).getClass()
                .getMethod("snapshot", snapshotRequest).invoke(economyApi(centralBank), request);
        List<?> accounts = (List<?>) snapshot.getClass().getMethod("accounts").invoke(snapshot);
        Object account = accounts.getFirst();

        assertEquals(new BigDecimal("12.35"), account.getClass().getMethod("balance").invoke(account));
    }

    @Test
    void institutionalPayoutLimitsComeFromCurrentAccountGrant() throws Exception {
        seedEconomyConfig();
        Object centralBank = load("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank")
                .getConstructor().newInstance();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        UUID officer = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        Object treasury = createInstitutionalAccount("nation:test", new BigDecimal("100000.00"), bankId);
        Object receiver = createAccount(UUID.randomUUID(), BigDecimal.ZERO, bankId);
        treasury.getClass().getMethod("grantAccessRole", UUID.class, String.class)
                .invoke(treasury, officer, "WITHDRAW");
        treasury.getClass().getMethod("grantAccessRole", UUID.class, String.class)
                .invoke(treasury, leader, "MANAGE");
        addAccount(centralBank, treasury);
        addAccount(centralBank, receiver);

        Object api = economyApi(centralBank);
        Object officerPayout = execute(api, transferRequest(
                "nation:payout:officer", officer, accountId(treasury), accountId(receiver), "11000.00"));
        assertFalse(success(officerPayout));
        assertEquals("POLICY_LIMIT", officerPayout.getClass().getMethod("code").invoke(officerPayout));

        Object leaderPayout = execute(api, transferRequest(
                "nation:payout:leader", leader, accountId(treasury), accountId(receiver), "11000.00"));
        assertTrue(success(leaderPayout), resultSummary(leaderPayout));
        assertEquals(new BigDecimal("89000.00"), balance(treasury));
        assertEquals(new BigDecimal("11000.00"), balance(receiver));
    }

    @Test
    void playerSnapshotDoesNotExposeOtherPlayersAccountGrants() throws Exception {
        seedEconomyConfig();
        Object centralBank = load("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank")
                .getConstructor().newInstance();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        UUID owner = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Object account = createAccount(owner, new BigDecimal("15.00"), bankId);
        account.getClass().getMethod("grantAccessRole", UUID.class, String.class)
                .invoke(account, viewer, "VIEW");
        addAccount(centralBank, account);
        Object api = economyApi(centralBank);

        Object playerAccount = recordList(snapshotForPlayer(api, owner), "accounts").getFirst();
        Map<?, ?> playerGrants = (Map<?, ?>) playerAccount.getClass().getMethod("accessRoles").invoke(playerAccount);
        assertEquals(1, playerGrants.size());
        assertTrue(playerGrants.containsKey(owner));
        assertFalse(playerGrants.containsKey(viewer));

        Class<?> snapshotRequest = load(
                "net.austizz.ultimatebankingsystem.api.economy.ApiEconomySnapshotRequest");
        Object reconciliationRequest = snapshotRequest.getMethod("reconciliation").invoke(null);
        Object reconciliation = api.getClass().getMethod("snapshot", snapshotRequest)
                .invoke(api, reconciliationRequest);
        Object reconciledAccount = recordList(reconciliation, "accounts").getFirst();
        Map<?, ?> allGrants = (Map<?, ?>) reconciledAccount.getClass()
                .getMethod("accessRoles").invoke(reconciledAccount);
        assertEquals(2, allGrants.size());
    }

    private static Object economyApi(Object centralBank) throws Exception {
        Class<?> implementation = load(
                "net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApiImpl");
        Constructor<?> constructor = implementation.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        Supplier<Object> supplier = () -> centralBank;
        return constructor.newInstance(supplier);
    }

    private static Object transferRequest(String key,
                                          UUID actor,
                                          UUID senderAccount,
                                          UUID receiverAccount,
                                          String amount) throws Exception {
        return operationRequest(key, "TRANSFER", "PLAYER", actor, senderAccount, receiverAccount,
                null, null, "", "", "", amount, "idempotency test", Map.of(), List.of());
    }

    private static Object operationRequest(String key,
                                           String operationName,
                                           String actorName,
                                           UUID actor,
                                           UUID account,
                                           UUID counterparty,
                                           UUID bank,
                                           UUID targetPlayer,
                                           String institution,
                                           String escrow,
                                           String role,
                                           String amount,
                                           String reference,
                                           Map<String, String> metadata,
                                           List<Object> legs) throws Exception {
        Class<?> request = load("net.austizz.ultimatebankingsystem.api.economy.ApiEconomyOperationRequest");
        Class<?> operationType = load("net.austizz.ultimatebankingsystem.api.economy.ApiEconomyOperationType");
        Class<?> actorType = load("net.austizz.ultimatebankingsystem.api.economy.ApiEconomyActorType");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object operation = Enum.valueOf((Class<? extends Enum>) operationType, operationName);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object actorKind = Enum.valueOf((Class<? extends Enum>) actorType, actorName);
        return request.getConstructor(
                        String.class, operationType, actorType,
                        UUID.class, UUID.class, UUID.class, UUID.class, UUID.class,
                        String.class, String.class, String.class, BigDecimal.class, String.class,
                        Map.class, List.class)
                .newInstance(key, operation, actorKind, actor, account, counterparty, bank, targetPlayer,
                        institution, escrow, role, new BigDecimal(amount), reference, metadata, legs);
    }

    private static Object transferLeg(UUID sender, UUID receiver, String amount) throws Exception {
        Class<?> leg = load("net.austizz.ultimatebankingsystem.api.economy.ApiEconomyTransferLeg");
        return leg.getConstructor(UUID.class, UUID.class, BigDecimal.class, String.class)
                .newInstance(sender, receiver, new BigDecimal(amount), "war stake");
    }

    private static Object execute(Object api, Object request) throws Exception {
        return api.getClass().getMethod("execute", request.getClass()).invoke(api, request);
    }

    private static Object snapshotForPlayer(Object api, UUID playerId) throws Exception {
        Class<?> snapshotRequest = load(
                "net.austizz.ultimatebankingsystem.api.economy.ApiEconomySnapshotRequest");
        Object request = snapshotRequest.getMethod("forPlayer", UUID.class).invoke(null, playerId);
        return api.getClass().getMethod("snapshot", snapshotRequest).invoke(api, request);
    }

    private static List<?> recordList(Object record, String accessor) throws Exception {
        return (List<?>) record.getClass().getMethod(accessor).invoke(record);
    }

    private static boolean success(Object result) throws Exception {
        return (boolean) result.getClass().getMethod("success").invoke(result);
    }

    private static String resultSummary(Object result) throws Exception {
        return result.getClass().getMethod("code").invoke(result) + ": "
                + result.getClass().getMethod("message").invoke(result);
    }

    private static Object createAccount(UUID playerId, BigDecimal balance, UUID bankId) throws Exception {
        Class<?> account = load("net.austizz.ultimatebankingsystem.account.AccountHolder");
        Class<?> accountTypes = load("net.austizz.ultimatebankingsystem.accountTypes.AccountTypes");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object checking = Enum.valueOf((Class<? extends Enum>) accountTypes, "CheckingAccount");
        return account.getConstructor(UUID.class, BigDecimal.class, accountTypes,
                        String.class, UUID.class, UUID.class)
                .newInstance(playerId, balance, checking, "", bankId, UUID.randomUUID());
    }

    private static Object createInstitutionalAccount(String institutionId,
                                                     BigDecimal balance,
                                                     UUID bankId) throws Exception {
        Class<?> account = load("net.austizz.ultimatebankingsystem.account.AccountHolder");
        Class<?> accountTypes = load("net.austizz.ultimatebankingsystem.accountTypes.AccountTypes");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object checking = Enum.valueOf((Class<? extends Enum>) accountTypes, "CheckingAccount");
        return account.getMethod("createInstitutional", String.class, BigDecimal.class, accountTypes,
                        UUID.class, UUID.class)
                .invoke(null, institutionId, balance, checking, bankId, UUID.randomUUID());
    }

    private static void addAccount(Object centralBank, Object account) throws Exception {
        centralBank.getClass().getMethod("AddAccount",
                        load("net.austizz.ultimatebankingsystem.account.AccountHolder"))
                .invoke(centralBank, account);
    }

    private static Object reloadCentralBank(Object centralBank) throws Exception {
        Class<?> compoundTag = load("net.minecraft.nbt.CompoundTag");
        Class<?> registries = load("net.minecraft.core.HolderLookup$Provider");
        Object saved = centralBank.getClass().getMethod("save", compoundTag, registries)
                .invoke(centralBank, compoundTag.getConstructor().newInstance(), null);
        return centralBank.getClass().getMethod("load", compoundTag, registries)
                .invoke(null, saved, null);
    }

    private static UUID accountId(Object account) throws Exception {
        return (UUID) account.getClass().getMethod("getAccountUUID").invoke(account);
    }

    private static BigDecimal balance(Object account) throws Exception {
        return (BigDecimal) account.getClass().getMethod("getBalance").invoke(account);
    }

    private static void seedConfigValue(String fieldName, Object value) throws Exception {
        Object configValue = load("net.austizz.ultimatebankingsystem.Config").getField(fieldName).get(null);
        var cachedValue = configValue.getClass().getSuperclass().getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        cachedValue.set(configValue, value);
    }

    private static void seedEconomyConfig() throws Exception {
        seedConfigValue("DEFAULT_FEDERAL_FUNDS_RATE", 1.0D);
        seedConfigValue("MIN_CUSTOM_BANK_INTEREST_RATE", 0.0D);
        seedConfigValue("MAX_CUSTOM_BANK_INTEREST_RATE", 100.0D);
        seedConfigValue("CREDIT_SCORE_DEFAULT", 500);
        seedConfigValue("ACCOUNT_TRANSACTION_LOG_LIMIT", 100);
        seedConfigValue("GLOBAL_MAX_SINGLE_TRANSACTION", 1_000_000);
        seedConfigValue("GLOBAL_MAX_DAILY_PLAYER_VOLUME", 1_000_000);
        seedConfigValue("BANK_MIN_RESERVE_RATIO", 0.1D);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }
}
