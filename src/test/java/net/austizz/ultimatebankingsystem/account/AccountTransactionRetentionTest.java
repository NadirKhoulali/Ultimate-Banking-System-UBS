package net.austizz.ultimatebankingsystem.account;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTransactionRetentionTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String ACCOUNT_CLASS = "net.austizz.ultimatebankingsystem.account.AccountHolder";
    private static final String TRANSACTION_CLASS =
            "net.austizz.ultimatebankingsystem.account.transaction.UserTransaction";
    private static final int TEST_LIMIT = 20;

    @Test
    void keepsNewestEntriesWithoutReducingDailyOutgoingVolume() throws Exception {
        UUID accountId = UUID.randomUUID();
        Object account = newAccount(accountId);
        LocalDateTime start = LocalDate.now().atStartOfDay().plusHours(1);
        List<UUID> transactionIds = new ArrayList<>();

        for (int index = 0; index < TEST_LIMIT + 5; index++) {
            UUID transactionId = UUID.randomUUID();
            transactionIds.add(transactionId);
            addTransaction(account, newTransaction(
                    accountId,
                    BigDecimal.ONE,
                    start.plusMinutes(index),
                    "retention-test-" + index,
                    transactionId));
        }

        Map<?, ?> transactions = transactions(account);
        assertEquals(TEST_LIMIT, transactions.size());
        for (int index = 0; index < 5; index++) {
            assertFalse(transactions.containsKey(transactionIds.get(index)));
        }
        for (int index = 5; index < transactionIds.size(); index++) {
            assertTrue(transactions.containsKey(transactionIds.get(index)));
        }
        assertEquals(new BigDecimal("25"), dailyOutgoing(account));
    }

    @Test
    void legacySaveMigrationCountsAllRowsBeforeTrimmingHistory() throws Exception {
        UUID accountId = UUID.randomUUID();
        Object account = newAccount(accountId);
        Object legacyTag = save(account);
        Class<?> compoundTagType = load("net.minecraft.nbt.CompoundTag");
        compoundTagType.getMethod("remove", String.class).invoke(legacyTag, "dailyOutgoingTransactionDay");
        compoundTagType.getMethod("remove", String.class).invoke(legacyTag, "dailyOutgoingTransactionAmount");

        Object transactionList = load("net.minecraft.nbt.ListTag").getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        List<Object> transactionTags = (List<Object>) transactionList;
        LocalDateTime start = LocalDate.now().atStartOfDay().plusHours(1);
        for (int index = 0; index < TEST_LIMIT + 5; index++) {
            Object transaction = newTransaction(
                    accountId,
                    new BigDecimal("2.00"),
                    start.plusMinutes(index),
                    "legacy-retention-test-" + index,
                    UUID.randomUUID());
            Object transactionTag = saveTransaction(transaction);
            UUID transactionId = (UUID) load(TRANSACTION_CLASS).getMethod("getTransactionUUID").invoke(transaction);
            compoundTagType.getMethod("putUUID", String.class, UUID.class)
                    .invoke(transactionTag, "mapKey", transactionId);
            transactionTags.add(transactionTag);
        }
        compoundTagType.getMethod("put", String.class, load("net.minecraft.nbt.Tag"))
                .invoke(legacyTag, "transactions", transactionList);

        Object migrated = loadAccount(legacyTag);

        assertEquals(TEST_LIMIT, transactions(migrated).size());
        assertEquals(new BigDecimal("50.00"), dailyOutgoing(migrated));
        Object migratedTag = save(migrated);
        Object migratedTransactions = compoundTagType.getMethod("getList", String.class, int.class)
                .invoke(migratedTag, "transactions", 10);
        assertEquals(TEST_LIMIT, ((List<?>) migratedTransactions).size());
        assertTrue((boolean) compoundTagType.getMethod("contains", String.class)
                .invoke(migratedTag, "dailyOutgoingTransactionAmount"));
    }

    private static Object newAccount(UUID accountId) throws Exception {
        seedConfigValue("CREDIT_SCORE_DEFAULT", 500);
        seedConfigValue("ACCOUNT_TRANSACTION_LOG_LIMIT", TEST_LIMIT);
        Class<?> accountType = load(ACCOUNT_CLASS);
        Class<?> accountTypes = load("net.austizz.ultimatebankingsystem.accountTypes.AccountTypes");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object checking = Enum.valueOf((Class<? extends Enum>) accountTypes, "CheckingAccount");
        return accountType.getConstructor(
                        UUID.class, BigDecimal.class, accountTypes, String.class, UUID.class, UUID.class)
                .newInstance(UUID.randomUUID(), BigDecimal.ZERO, checking, "", UUID.randomUUID(), accountId);
    }

    private static Object newTransaction(UUID sender,
                                         BigDecimal amount,
                                         LocalDateTime timestamp,
                                         String description,
                                         UUID transactionId) throws Exception {
        Constructor<?> constructor = load(TRANSACTION_CLASS).getConstructor(
                UUID.class, UUID.class, BigDecimal.class, LocalDateTime.class, String.class, UUID.class);
        return constructor.newInstance(sender, UUID.randomUUID(), amount, timestamp, description, transactionId);
    }

    private static void addTransaction(Object account, Object transaction) throws Exception {
        load(ACCOUNT_CLASS).getMethod("addTransaction", load(TRANSACTION_CLASS)).invoke(account, transaction);
    }

    private static Map<?, ?> transactions(Object account) throws Exception {
        return (Map<?, ?>) load(ACCOUNT_CLASS).getMethod("getTransactions").invoke(account);
    }

    private static BigDecimal dailyOutgoing(Object account) throws Exception {
        return (BigDecimal) load(ACCOUNT_CLASS).getMethod("getDailyOutgoingTransactionVolume").invoke(account);
    }

    private static Object save(Object account) throws Exception {
        Class<?> compoundTagType = load("net.minecraft.nbt.CompoundTag");
        Class<?> registriesType = load("net.minecraft.core.HolderLookup$Provider");
        Object tag = compoundTagType.getConstructor().newInstance();
        return load(ACCOUNT_CLASS).getMethod("save", compoundTagType, registriesType)
                .invoke(account, tag, null);
    }

    private static Object saveTransaction(Object transaction) throws Exception {
        Class<?> compoundTagType = load("net.minecraft.nbt.CompoundTag");
        Class<?> registriesType = load("net.minecraft.core.HolderLookup$Provider");
        Object tag = compoundTagType.getConstructor().newInstance();
        return load(TRANSACTION_CLASS).getMethod("save", compoundTagType, registriesType)
                .invoke(transaction, tag, null);
    }

    private static Object loadAccount(Object tag) throws Exception {
        Class<?> compoundTagType = load("net.minecraft.nbt.CompoundTag");
        Class<?> registriesType = load("net.minecraft.core.HolderLookup$Provider");
        Method load = load(ACCOUNT_CLASS).getMethod("load", compoundTagType, registriesType);
        return load.invoke(null, tag, null);
    }

    private static void seedConfigValue(String fieldName, Object value) throws Exception {
        Object configValue = load("net.austizz.ultimatebankingsystem.Config").getField(fieldName).get(null);
        var cachedValue = configValue.getClass().getSuperclass().getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        cachedValue.set(configValue, value);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }
}
