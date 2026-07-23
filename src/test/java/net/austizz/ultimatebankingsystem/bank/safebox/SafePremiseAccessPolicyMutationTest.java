package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseAccessPolicy;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePremiseAccessPolicyMutationTest {
    @Test
    void premiseAccessKeepsPublicCustomerAndStaffOnlyManagerEmployeeSemantics() {
        SafePremiseAccessPolicy.Decision publicCustomer = SafePremiseAccessPolicy.decide(
                SafePremiseMode.PUBLIC,
                true,
                false,
                false,
                false
        );
        assertTrue(publicCustomer.allowed());
        assertTrue(publicCustomer.normalCustomerAccess());

        SafePremiseAccessPolicy.Decision staffOnlyCustomer = SafePremiseAccessPolicy.decide(
                SafePremiseMode.STAFF_ONLY,
                true,
                false,
                false,
                false
        );
        assertFalse(staffOnlyCustomer.allowed());

        SafePremiseAccessPolicy.Decision staffOnlyManager = SafePremiseAccessPolicy.decide(
                SafePremiseMode.STAFF_ONLY,
                false,
                true,
                false,
                false
        );
        assertTrue(staffOnlyManager.allowed());
        assertTrue(staffOnlyManager.legacyManagementAccess());

        SafePremiseAccessPolicy.Decision staffOnlySafeAccess = SafePremiseAccessPolicy.decide(
                SafePremiseMode.STAFF_ONLY,
                false,
                false,
                true,
                false
        );
        assertTrue(staffOnlySafeAccess.allowed());
        assertTrue(staffOnlySafeAccess.explicitSafeAccess());
        assertFalse(staffOnlySafeAccess.legacyManagementAccess());
    }

    @Test
    void structuralMutationRequiresLegacyManagementInsteadOfPremiseSafeAccess() {
        SafePremiseAccessPolicy.Decision manager =
                SafePremiseAccessPolicy.decideStructuralMutation(true);
        assertTrue(manager.allowed());
        assertTrue(manager.legacyManagementAccess());

        SafePremiseAccessPolicy.Decision safeAccessOnlyEmployee =
                SafePremiseAccessPolicy.decideStructuralMutation(false);
        assertFalse(safeAccessOnlyEmployee.allowed(),
                "Safe Access is only physical premise access, not structural mutation authorization");
        assertFalse(safeAccessOnlyEmployee.legacyManagementAccess());
        assertFalse(safeAccessOnlyEmployee.explicitSafeAccess());
    }

    @Test
    void insertInstallationRequiresClaimedAreaAndLegacyManagementAccess() {
        SafePremiseAccessPolicy.Decision outsideClaim =
                SafePremiseAccessPolicy.decideInsertInstallation(false, false);
        assertFalse(outsideClaim.allowed(),
                "non-operators must not install deposit inserts outside a claimed bank safe area");

        SafePremiseAccessPolicy.Decision outsideClaimWithManagementFlag =
                SafePremiseAccessPolicy.decideInsertInstallation(false, true);
        assertFalse(outsideClaimWithManagementFlag.allowed(),
                "a claimed bank safe area is required before legacy management access is considered");

        SafePremiseAccessPolicy.Decision safeAccessOnlyEmployee =
                SafePremiseAccessPolicy.decideInsertInstallation(true, false);
        assertFalse(safeAccessOnlyEmployee.allowed(),
                "Safe Access must not grant capacity-changing insert installation");

        SafePremiseAccessPolicy.Decision manager =
                SafePremiseAccessPolicy.decideInsertInstallation(true, true);
        assertTrue(manager.allowed());
        assertTrue(manager.legacyManagementAccess());
    }

    @Test
    void liveStructuralMutationGateKeepsOutsideClaimOpenAndInsideClaimManagerOnly() throws Exception {
        assertTrue(liveStructuralMutationMayModify(false, false),
                "ordinary block mutation outside a claimed safe area must stay allowed");
        assertFalse(liveStructuralMutationMayModify(true, false),
                "inside-claim structural mutation must require legacy management access");
        assertTrue(liveStructuralMutationMayModify(true, true),
                "legacy managers must retain structural mutation access inside claims");
    }

    @Test
    void liveInsertGateDeniesNonManagerOutsideAndInsideClaims() throws Exception {
        assertFalse(liveInsertGate(false, false),
                "insert installation outside a claim must stay denied");
        assertFalse(liveInsertGate(true, false),
                "insert installation inside a claim must require legacy manager authorization");
    }

    @Test
    void liveInsertGatePreservesLegacyManagerAccess() throws Exception {
        assertTrue(liveInsertGate(true, true),
                "legacy managers must be able to install inserts inside claimed safe areas");
        assertFalse(liveInsertGate(false, true),
                "outside-claim insert installation must stay denied even if a management flag is present");
    }

    @Test
    void liveInsertUseOnGateDeniesCreativeNonManagerOutsideClaims() throws Exception {
        withCachedCentralBank(emptyCentralBank(), () -> assertFalse(liveUseOnInsertGate(
                insertPathPlayer(UUID.fromString("80000000-0000-0000-0000-000000000001"), 0, true),
                null,
                newBlockPos(90, 64, 90)
        ), "creative mode must not bypass the claimed-area requirement in the path called by useOn"));
    }

    @Test
    void liveInsertUseOnGateDeniesCreativeNonManagerInsideOtherBankClaims() throws Exception {
        UUID bankId = UUID.fromString("90000000-0000-0000-0000-000000000001");
        UUID ownerId = UUID.fromString("90000000-0000-0000-0000-000000000002");
        UUID intruderId = UUID.fromString("90000000-0000-0000-0000-000000000003");
        Object centralBank = centralBankWithClaim(bankId, ownerId);

        withCachedCentralBank(centralBank, () -> assertFalse(liveUseOnInsertGate(
                insertPathPlayer(intruderId, 0, true),
                claimedOverworldLevel(),
                newBlockPos(3, 64, 3)
        ), "creative mode must not bypass legacy manager authorization inside another bank claim"));
    }

    @Test
    void liveInsertUseOnGatePreservesLegacyManagerAndAdminAccess() throws Exception {
        UUID bankId = UUID.fromString("91000000-0000-0000-0000-000000000001");
        UUID ownerId = UUID.fromString("91000000-0000-0000-0000-000000000002");
        Object centralBank = centralBankWithClaim(bankId, ownerId);

        withCachedCentralBank(centralBank, () -> assertTrue(liveUseOnInsertGate(
                insertPathPlayer(ownerId, 0, false),
                claimedOverworldLevel(),
                newBlockPos(3, 64, 3)
        ), "legacy managers must retain insert installation access inside their claimed safe area"));

        withCachedCentralBank(emptyCentralBank(), () -> assertTrue(liveUseOnInsertGate(
                insertPathPlayer(UUID.fromString("91000000-0000-0000-0000-000000000003"), 3, false),
                null,
                newBlockPos(90, 64, 90)
        ), "permission-level-3 admins must retain explicit insert installation access"));
    }

    private static boolean liveInsertGate(boolean claimedBankAreaExists,
                                          boolean legacyManagementAccess) throws Exception {
        Class<?> authorization = Class.forName(
                "net.austizz.ultimatebankingsystem.item.SafetyDepositBoxInsertAuthorization");
        Method method = authorization.getDeclaredMethod(
                "canInstallInRow",
                boolean.class,
                boolean.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(
                null,
                claimedBankAreaExists,
                legacyManagementAccess
        );
    }

    private static boolean liveUseOnInsertGate(Object player, Object level, Object pos) throws Exception {
        Method method = itemClass().getDeclaredMethod(
                "canInstallInRow",
                serverPlayerClass(),
                levelClass(),
                blockPosClass()
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, player, level, pos);
    }

    private static boolean liveStructuralMutationMayModify(boolean claimedBankAreaExists,
                                                           boolean legacyManagementAccess) throws Exception {
        Class<?> authorization = Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.BankSafeAreaMutationAuthorization",
                true,
                serverClassLoader()
        );
        Method method = authorization.getDeclaredMethod(
                "mayModify",
                boolean.class,
                boolean.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, claimedBankAreaExists, legacyManagementAccess);
    }

    private static Object insertPathPlayer(UUID playerId, int permissionLevel, boolean creativeInstabuild) throws Exception {
        ensureMinecraftBootstrap();
        Object player = unsafe().allocateInstance(serverPlayerClass());
        Object profile = gameProfile(playerId);
        Object abilities = abilitiesClass().getConstructor().newInstance();
        setBooleanField(abilities, "instabuild", creativeInstabuild);
        setInstanceField(player, playerClass(), "abilities", abilities);
        setInstanceField(player, playerClass(), "gameProfile", profile);
        setInstanceField(player, entityClass(), "uuid", playerId);
        setInstanceField(player, entityClass(), "stringUUID", playerId.toString());
        Object server = minecraftServer(permissionLevel, profile);
        setInstanceField(player, serverPlayerClass(), "server", server);
        setBankManagerServerInstance(server);
        return player;
    }

    private static Object claimedOverworldLevel() throws Exception {
        ensureMinecraftBootstrap();
        Object level = unsafe().allocateInstance(serverLevelClass());
        setInstanceField(level, levelClass(), "dimension", levelClass().getField("OVERWORLD").get(null));
        return level;
    }

    private static Object newBlockPos(int x, int y, int z) throws Exception {
        ensureMinecraftBootstrap();
        return blockPosClass().getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
    }

    private static void ensureMinecraftBootstrap() throws Exception {
        Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList", true, serverClassLoader());
        if (loadingModList.getMethod("get").invoke(null) == null) {
            loadingModList.getMethod("of", List.class, List.class, List.class, List.class, Map.class)
                    .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        Class.forName("net.minecraft.SharedConstants", true, serverClassLoader())
                .getMethod("tryDetectVersion")
                .invoke(null);
        Class.forName("net.minecraft.server.Bootstrap", true, serverClassLoader())
                .getMethod("bootStrap")
                .invoke(null);
    }

    private static Object emptyCentralBank() throws Exception {
        Object centralBank = unsafe().allocateInstance(centralBankClass());
        setInstanceField(centralBank, centralBankClass(), "banks", new java.util.concurrent.ConcurrentHashMap<>());
        setInstanceField(centralBank, centralBankClass(), "bankMetadata", new java.util.concurrent.ConcurrentHashMap<>());
        return centralBank;
    }

    private static Object centralBankWithClaim(UUID bankId, UUID ownerId) throws Exception {
        Object centralBank = emptyCentralBank();
        Object bank = bankClass()
                .getConstructor(UUID.class, String.class, BigDecimal.class, double.class, UUID.class)
                .newInstance(bankId, "Claimed Bank", BigDecimal.ZERO, 0.0D, ownerId);
        getBanks(centralBank).put(bankId, bank);

        Object metadata = compoundTagClass().getConstructor().newInstance();
        Object areas = listTagClass().getConstructor().newInstance();
        Object area = compoundTagClass().getConstructor().newInstance();
        putString(area, "dimension", "minecraft:overworld");
        putInt(area, "minX", 0);
        putInt(area, "minY", 60);
        putInt(area, "minZ", 0);
        putInt(area, "maxX", 8);
        putInt(area, "maxY", 70);
        putInt(area, "maxZ", 8);
        addToList(areas, area);
        putTag(metadata, "safeDepositAreas", areas);
        getBankMetadata(centralBank).put(bankId, metadata);
        return centralBank;
    }

    private static void withCachedCentralBank(Object centralBank, ThrowingRunnable action) throws Exception {
        Class<?> bankManager = bankManagerClass();
        Field serverInstance = bankManager.getDeclaredField("serverInstance");
        Field cachedCentralBank = bankManager.getDeclaredField("centralBank");
        Field dataRef = bankManager.getDeclaredField("dataRef");
        serverInstance.setAccessible(true);
        cachedCentralBank.setAccessible(true);
        dataRef.setAccessible(true);
        Object previousServer = serverInstance.get(null);
        Object previousCentralBank = cachedCentralBank.get(null);
        Object previousDataRef = dataRef.get(null);
        serverInstance.set(null, null);
        cachedCentralBank.set(null, centralBank);
        dataRef.set(null, null);
        try {
            action.run();
        } finally {
            serverInstance.set(null, previousServer);
            cachedCentralBank.set(null, previousCentralBank);
            dataRef.set(null, previousDataRef);
        }
    }

    private static void setBankManagerServerInstance(Object server) throws Exception {
        Class<?> bankManager = bankManagerClass();
        Field serverInstance = bankManager.getDeclaredField("serverInstance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, server);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, Object> getBanks(Object centralBank) throws Exception {
        return (java.util.Map<UUID, Object>) centralBankClass().getMethod("getBanks").invoke(centralBank);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, Object> getBankMetadata(Object centralBank) throws Exception {
        return (java.util.Map<UUID, Object>) centralBankClass().getMethod("getBankMetadata").invoke(centralBank);
    }

    @SuppressWarnings("unchecked")
    private static void addToList(Object list, Object value) {
        ((List<Object>) list).add(value);
    }

    private static void putString(Object tag, String key, String value) throws Exception {
        compoundTagClass().getMethod("putString", String.class, String.class).invoke(tag, key, value);
    }

    private static void putInt(Object tag, String key, int value) throws Exception {
        compoundTagClass().getMethod("putInt", String.class, int.class).invoke(tag, key, value);
    }

    private static void putTag(Object tag, String key, Object value) throws Exception {
        compoundTagClass().getMethod("put", String.class, tagClass()).invoke(tag, key, value);
    }

    private static Object minecraftServer(int permissionLevel, Object profile) throws Exception {
        Object server = unsafe().allocateInstance(dedicatedServerClass());
        Object playerList = unsafe().allocateInstance(dedicatedPlayerListClass());
        Object ops = serverOpListClass().getConstructor(File.class).newInstance(new File("build/ulw-evidence/test-ops.json"));
        if (permissionLevel > 0) {
            Object entry = serverOpListEntryClass()
                    .getConstructor(gameProfileClass(), int.class, boolean.class)
                    .newInstance(profile, permissionLevel, false);
            storedUserListClass().getMethod("add", storedUserEntryClass()).invoke(ops, entry);
        }
        setInstanceField(playerList, playerListClass(), "server", server);
        setInstanceField(playerList, playerListClass(), "ops", ops);
        setInstanceField(server, minecraftServerClass(), "playerList", playerList);
        return server;
    }

    private static Object gameProfile(UUID playerId) throws Exception {
        return gameProfileClass().getConstructor(UUID.class, String.class).newInstance(playerId, "InsertPathTest");
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getField(fieldName);
        field.setBoolean(target, value);
    }

    private static void setInstanceField(Object target, Class<?> ownerClass, String fieldName, Object value)
            throws Exception {
        Field field = ownerClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Class<?> itemClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.item.SafetyDepositBoxInsertItem", true,
                serverClassLoader());
    }

    private static Class<?> bankClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.Bank", true, serverClassLoader());
    }

    private static Class<?> centralBankClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank", true,
                serverClassLoader());
    }

    private static Class<?> bankManagerClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.handler.BankManager", true,
                serverClassLoader());
    }

    private static Class<?> blockPosClass() throws Exception {
        return Class.forName("net.minecraft.core.BlockPos", true, serverClassLoader());
    }

    private static Class<?> compoundTagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.CompoundTag", true, serverClassLoader());
    }

    private static Class<?> listTagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.ListTag", true, serverClassLoader());
    }

    private static Class<?> tagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.Tag", true, serverClassLoader());
    }

    private static Class<?> serverPlayerClass() throws Exception {
        return Class.forName("net.minecraft.server.level.ServerPlayer", false, serverClassLoader());
    }

    private static Class<?> serverLevelClass() throws Exception {
        return Class.forName("net.minecraft.server.level.ServerLevel", false, serverClassLoader());
    }

    private static Class<?> levelClass() throws Exception {
        return Class.forName("net.minecraft.world.level.Level", true, serverClassLoader());
    }

    private static Class<?> playerClass() throws Exception {
        return Class.forName("net.minecraft.world.entity.player.Player", true, serverClassLoader());
    }

    private static Class<?> entityClass() throws Exception {
        return Class.forName("net.minecraft.world.entity.Entity", true, serverClassLoader());
    }

    private static Class<?> abilitiesClass() throws Exception {
        return Class.forName("net.minecraft.world.entity.player.Abilities", true, serverClassLoader());
    }

    private static Class<?> gameProfileClass() throws Exception {
        return Class.forName("com.mojang.authlib.GameProfile", true, serverClassLoader());
    }

    private static Class<?> dedicatedServerClass() throws Exception {
        return Class.forName("net.minecraft.server.dedicated.DedicatedServer", false, serverClassLoader());
    }

    private static Class<?> minecraftServerClass() throws Exception {
        return Class.forName("net.minecraft.server.MinecraftServer", false, serverClassLoader());
    }

    private static Class<?> dedicatedPlayerListClass() throws Exception {
        return Class.forName("net.minecraft.server.dedicated.DedicatedPlayerList", false, serverClassLoader());
    }

    private static Class<?> playerListClass() throws Exception {
        return Class.forName("net.minecraft.server.players.PlayerList", false, serverClassLoader());
    }

    private static Class<?> serverOpListClass() throws Exception {
        return Class.forName("net.minecraft.server.players.ServerOpList", true, serverClassLoader());
    }

    private static Class<?> serverOpListEntryClass() throws Exception {
        return Class.forName("net.minecraft.server.players.ServerOpListEntry", true, serverClassLoader());
    }

    private static Class<?> storedUserListClass() throws Exception {
        return Class.forName("net.minecraft.server.players.StoredUserList", false, serverClassLoader());
    }

    private static Class<?> storedUserEntryClass() throws Exception {
        return Class.forName("net.minecraft.server.players.StoredUserEntry", false, serverClassLoader());
    }

    private static ClassLoader serverClassLoader() {
        return ServerClassLoaderHolder.INSTANCE;
    }

    private static final class ServerClassLoaderHolder {
        private static final ClassLoader INSTANCE = new ChildFirstUrlClassLoader(serverClasspathUrls(),
                SafePremiseAccessPolicyMutationTest.class.getClassLoader());

        private static URL[] serverClasspathUrls() {
            List<URL> urls = new java.util.ArrayList<>();
            addUrl(urls, Path.of("build", "classes", "java", "main"));
            String runtimeClasspath = System.getProperty("ubs.testRuntimeClasspath", "").trim();
            for (String entry : runtimeClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!entry.isBlank()) {
                    addUrl(urls, Path.of(entry));
                }
            }
            Path legacyClasspath = Path.of("build", "moddev", "serverLegacyClasspath.txt");
            if (Files.exists(legacyClasspath)) {
                try {
                    for (String line : Files.readAllLines(legacyClasspath)) {
                        if (!line.isBlank()) {
                            addUrl(urls, Path.of(line));
                        }
                    }
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to read server test classpath: " + legacyClasspath,
                            exception);
                }
            }
            addUrl(urls, Path.of("build", "moddev", "artifacts", "neoforge-21.1.220.jar"));
            return urls.toArray(URL[]::new);
        }

        private static void addUrl(List<URL> urls, Path path) {
            try {
                urls.add(path.toAbsolutePath().normalize().toUri().toURL());
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to prepare server test classpath: " + path, exception);
            }
        }
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("net.minecraft.")
                    && !name.startsWith("net.neoforged.")
                    && !name.startsWith("net.austizz.ultimatebankingsystem.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
