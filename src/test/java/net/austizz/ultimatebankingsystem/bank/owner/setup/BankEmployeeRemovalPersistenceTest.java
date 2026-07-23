package net.austizz.ultimatebankingsystem.bank.owner.setup;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.BANK_ID;
import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.EMPLOYEE_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankEmployeeRemovalPersistenceTest {
    @TempDir
    static Path gameDirectory;

    @BeforeAll
    static void loadConfigDefaultsThroughNeoForgeLifecycle() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList", true, loader);
        if (loadingModList.getMethod("get").invoke(null) == null) {
            loadingModList.getMethod("of", List.class, List.class, List.class, List.class, Map.class)
                    .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        Class.forName("net.minecraft.SharedConstants", true, loader)
                .getMethod("tryDetectVersion")
                .invoke(null);
        Class.forName("net.minecraft.server.Bootstrap", true, loader)
                .getMethod("bootStrap")
                .invoke(null);
        Class.forName("net.neoforged.fml.loading.FMLPaths", true, loader)
                .getMethod("loadAbsolutePaths", Path.class)
                .invoke(null, gameDirectory);
        Class.forName("net.neoforged.fml.loading.FMLConfig", true, loader)
                .getMethod("load")
                .invoke(null);

        Class<?> modInfoType = Class.forName(
                "net.neoforged.neoforgespi.language.IModInfo", true, loader);
        Object modInfo = Proxy.newProxyInstance(loader, new Class<?>[]{modInfoType},
                (proxy, method, arguments) -> "getModId".equals(method.getName())
                        ? "ultimatebankingsystem"
                        : null);
        Class<?> modContainerType = Class.forName("net.neoforged.fml.ModContainer", true, loader);
        Object modContainer = Class.forName(
                        "net.neoforged.fml.mclanguageprovider.MinecraftModContainer", true, loader)
                .getConstructor(modInfoType)
                .newInstance(modInfo);

        Class<?> busBuilderType = Class.forName("net.neoforged.bus.api.BusBuilder", true, loader);
        Object busBuilder = busBuilderType.getMethod("builder").invoke(null);
        Object eventBus = busBuilderType.getMethod("build").invoke(busBuilder);
        Class<?> eventBusType = Class.forName("net.neoforged.bus.api.IEventBus", true, loader);
        SafeStaffReadinessTestSupport.load("UltimateBankingSystem")
                .getConstructor(eventBusType, modContainerType)
                .newInstance(eventBus, modContainer);

        Class<?> modConfigType = Class.forName("net.neoforged.fml.config.ModConfig", true, loader);
        @SuppressWarnings("unchecked")
        List<Object> configs = (List<Object>) Class.forName(
                        "net.neoforged.fml.config.ModConfigs", true, loader)
                .getMethod("getModConfigs", String.class)
                .invoke(null, "ultimatebankingsystem");
        Class.forName("net.neoforged.fml.config.ConfigTracker", true, loader)
                .getMethod("acceptSyncedConfig", modConfigType, byte[].class)
                .invoke(null, configs.getFirst(), new byte[0]);
    }

    @Test
    void persistedRemovalClearsCurrentEmployeeAndExplicitSafeAccess() throws Exception {
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "grantSafeAccess", metadata, EMPLOYEE_ID));
        Object centralBank = centralBank(metadata);

        assertTrue(removeAndPersist(centralBank, EMPLOYEE_ID));

        Object persisted = persistedMetadata(centralBank);
        assertNotSame(metadata, persisted);
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasEmployee", persisted, EMPLOYEE_ID));
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasExplicitSafeAccess", persisted, EMPLOYEE_ID));
    }

    @Test
    void persistedRemovalCleansOrphanGrantEvenWhenNoRosterEntryWasRemoved() throws Exception {
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation(
                "grantSafeAccess", metadata, EMPLOYEE_ID));
        SafeStaffReadinessTestSupport.putString(metadata, "employees", "");
        Object centralBank = centralBank(metadata);

        assertFalse(removeAndPersist(centralBank, EMPLOYEE_ID));

        Object persisted = persistedMetadata(centralBank);
        assertNotSame(metadata, persisted);
        assertFalse(SafeStaffReadinessTestSupport.staffingMutation(
                "hasExplicitSafeAccess", persisted, EMPLOYEE_ID));
    }

    private static boolean removeAndPersist(Object centralBank, UUID employeeId) throws Exception {
        Class<?> centralBankType = SafeStaffReadinessTestSupport.load("bank.centralbank.CentralBank");
        Method remove = SafeStaffReadinessTestSupport.load(
                        "bank.owner.staffing.BankEmployeeRemovalService")
                .getMethod("removeAndPersist", centralBankType, UUID.class, UUID.class);
        return (Boolean) remove.invoke(null, centralBank, BANK_ID, employeeId);
    }

    private static Object centralBank(Object metadata) throws Exception {
        Class<?> centralBankType = SafeStaffReadinessTestSupport.load("bank.centralbank.CentralBank");
        Object centralBank = centralBankType.getConstructor().newInstance();
        centralBankType.getMethod("putBankMetadata", UUID.class,
                        SafeStaffReadinessTestSupport.minecraft("nbt.CompoundTag"))
                .invoke(centralBank, BANK_ID, metadata);
        return centralBank;
    }

    private static Object persistedMetadata(Object centralBank) throws Exception {
        Class<?> compoundType = SafeStaffReadinessTestSupport.minecraft("nbt.CompoundTag");
        Class<?> registriesType = SafeStaffReadinessTestSupport.minecraft("core.HolderLookup$Provider");
        Object registries = SafeStaffReadinessTestSupport.minecraft("core.RegistryAccess")
                .getField("EMPTY").get(null);
        Object saved = compoundType.getConstructor().newInstance();
        Object serialized = centralBank.getClass().getMethod("save", compoundType, registriesType)
                .invoke(centralBank, saved, registries);
        Object loaded = centralBank.getClass().getMethod("load", compoundType, registriesType)
                .invoke(null, serialized, registries);
        return loaded.getClass().getMethod("readBankMetadata", UUID.class).invoke(loaded, BANK_ID);
    }
}
