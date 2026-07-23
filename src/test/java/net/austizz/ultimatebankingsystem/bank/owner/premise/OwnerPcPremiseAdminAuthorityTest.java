package net.austizz.ultimatebankingsystem.bank.owner.premise;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremiseAdminAuthorityTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void adminPortsAuthorizeCentralBankWithoutAnOwnerPcSession() throws Exception {
        seedConfigValue("DEFAULT_FEDERAL_FUNDS_RATE", 3.5D);
        seedConfigValue("BANK_MIN_RESERVE_RATIO", 0.10D);
        Class<?> centralBankType = type(
                "net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank");
        Object centralBank = centralBankType.getConstructor().newInstance();
        UUID bankId = (UUID) centralBankType.getMethod("getBankId").invoke(centralBank);
        centralBankType.getMethod("getOrCreateBankMetadata", UUID.class)
                .invoke(centralBank, bankId);

        Class<?> portsType = type(
                "net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremiseServerPorts");
        Method factory = portsType.getDeclaredMethod(
                "forAdmin",
                type("net.minecraft.server.MinecraftServer"), centralBankType, UUID.class);
        factory.setAccessible(true);
        Object ports = factory.invoke(null, null, centralBank, bankId);
        Method authorityMethod = portsType.getDeclaredMethod("authority", UUID.class);
        authorityMethod.setAccessible(true);
        Object authority = authorityMethod.invoke(ports, bankId);

        assertNotNull(value(authority, "metadata"));
        assertTrue((Boolean) value(authority, "bankExists"));
        assertTrue((Boolean) value(authority, "activeComputer"));
        assertTrue((Boolean) value(authority, "poweredOn"));
        assertTrue((Boolean) value(authority, "sessionUnlocked"));
        assertTrue((Boolean) value(authority, "permissionLevelThree"));
        assertFalse((Boolean) value(authority, "owner"));
    }

    private static void seedConfigValue(String fieldName, Object value) throws Exception {
        Class<?> configType = type("net.austizz.ultimatebankingsystem.Config");
        Object configValue = configType.getField(fieldName).get(null);
        var cachedValue = configValue.getClass().getSuperclass().getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        cachedValue.set(configValue, value);
    }

    private static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, LOADER);
    }
}
