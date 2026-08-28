package net.austizz.ultimatebankingsystem.account;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstitutionalAccountPrincipalTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void institutionHasStableCompatibilityIdButNoSyntheticHumanOwnerGrant() throws Exception {
        Object account = createInstitution("nation:iron-vanguard");
        Class<?> type = account.getClass();

        assertTrue((boolean) type.getMethod("isInstitutional").invoke(account));
        assertEquals("nation:iron-vanguard", type.getMethod("getPrincipalId").invoke(account));
        assertEquals("INSTITUTION", type.getMethod("getPrincipalType").invoke(account).toString());
        UUID compatibilityId = (UUID) type.getMethod("getPlayerUUID").invoke(account);
        assertNotEquals(new UUID(0L, 0L), compatibilityId);
        assertTrue(((Map<?, ?>) type.getMethod("getAccessRoles").invoke(account)).isEmpty());
        assertFalse((boolean) type.getMethod("isPrimaryAccount").invoke(account));

        type.getMethod("setPrimaryAccount", boolean.class).invoke(account, true);
        assertFalse((boolean) type.getMethod("isPrimaryAccount").invoke(account));
    }

    @Test
    void explicitInstitutionGrantCanBeRevokedAndSurvivesPersistence() throws Exception {
        Object account = createInstitution("nation:gold-reach");
        Class<?> type = account.getClass();
        UUID leader = UUID.randomUUID();
        type.getMethod("grantAccessRole", UUID.class, String.class).invoke(account, leader, "MANAGE");
        assertEquals("MANAGE", type.getMethod("getRole", UUID.class).invoke(account, leader));

        Object saved = save(account);
        Object loaded = loadAccount(saved);
        assertEquals("MANAGE", type.getMethod("getRole", UUID.class).invoke(loaded, leader));
        assertEquals("nation:gold-reach", type.getMethod("getPrincipalId").invoke(loaded));

        type.getMethod("revokeAccessRole", UUID.class).invoke(loaded, leader);
        assertEquals("", type.getMethod("getRole", UUID.class).invoke(loaded, leader));
        assertTrue(((Map<?, ?>) type.getMethod("getAccessRoles").invoke(loaded)).isEmpty());
    }

    private static Object createInstitution(String institutionId) throws Exception {
        seedConfigValue("CREDIT_SCORE_DEFAULT", 500);
        seedConfigValue("ACCOUNT_TRANSACTION_LOG_LIMIT", 20);
        Class<?> accountType = load("net.austizz.ultimatebankingsystem.account.AccountHolder");
        Class<?> accountTypes = load("net.austizz.ultimatebankingsystem.accountTypes.AccountTypes");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object checking = Enum.valueOf((Class<? extends Enum>) accountTypes, "CheckingAccount");
        Method factory = accountType.getMethod("createInstitutional", String.class, BigDecimal.class,
                accountTypes, UUID.class, UUID.class);
        return factory.invoke(null, institutionId, BigDecimal.ZERO, checking, UUID.randomUUID(), UUID.randomUUID());
    }

    private static Object save(Object account) throws Exception {
        Class<?> compoundTag = load("net.minecraft.nbt.CompoundTag");
        Class<?> registries = load("net.minecraft.core.HolderLookup$Provider");
        return account.getClass().getMethod("save", compoundTag, registries)
                .invoke(account, compoundTag.getConstructor().newInstance(), null);
    }

    private static Object loadAccount(Object tag) throws Exception {
        Class<?> account = load("net.austizz.ultimatebankingsystem.account.AccountHolder");
        Class<?> compoundTag = load("net.minecraft.nbt.CompoundTag");
        Class<?> registries = load("net.minecraft.core.HolderLookup$Provider");
        return account.getMethod("load", compoundTag, registries).invoke(null, tag, null);
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
