package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementApiContractTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void providerPublishesAllVersionTwoFacadesWithoutRemovingCoreApi() throws Exception {
        Class<?> provider = load("api.UltimateBankingApiProvider");
        assertNotNull(provider.getMethod("get"));
        assertNotNull(provider.getMethod("server"));
        assertNotNull(provider.getMethod("banks"));
        assertNotNull(provider.getMethod("shops"));
        assertNotNull(provider.getMethod("heists"));
    }

    @Test
    void shopApiCoversOwnershipRolesStateAndAuthorizedManagement() throws Exception {
        Class<?> api = load("api.shop.UltimateShopManagementApi");
        assertMethods(api,
                "getShops", "getShop", "findShop", "getOwnedShops", "getAccessibleShops",
                "shopExists", "playerOwnsShop", "playerOwnsAnyShop", "getPlayerRole",
                "playerCanManageShop", "playerCanBuildInShop", "isShopSetupComplete",
                "isShopCurrentlyOpen", "createShop", "renameShop", "setShopType",
                "setOpeningHours", "setParticipantRole", "removeParticipant", "deleteShop");
    }

    @Test
    void bankAndHeistApisExposeOperationalSnapshotsAndControls() throws Exception {
        Class<?> banks = load("api.bank.UltimateBankManagementApi");
        assertMethods(banks, "getBanks", "getBank", "findBank", "getOwnedBanks", "getAccessibleBanks",
                "playerOwnsAnyBank", "playerCanAccessBank", "playerCanManageSafeArea",
                "playerCanAccessProtectedSafeArea", "isBankUnderAttack", "getStaffing",
                "getSafeDepositSetup", "setEmployeeSafeAccess", "setInterestRate");

        Class<?> heists = load("api.heist.UltimateHeistApi");
        assertMethods(heists, "getSessions", "getActiveSessions", "getPlayerSession", "getTargets",
                "isPlayerInHeist", "isPlayerInActiveHeist", "isBankUnderAttack",
                "getPlayerCooldownRemainingMillis", "getBankCooldownRemainingMillis",
                "getVictimProtectionRemainingMillis", "createPlanningSession", "invite",
                "respondToInvite", "leave", "selectTarget", "setReady", "startCountdown",
                "cancelCountdown", "abandon");
    }

    @Test
    void publicManagementSignaturesDoNotLeakMutableDomainTypes() throws Exception {
        List<Class<?>> apis = List.of(
                load("api.general.UltimateServerApi"),
                load("api.bank.UltimateBankManagementApi"),
                load("api.shop.UltimateShopManagementApi"),
                load("api.heist.UltimateHeistApi"));
        for (Class<?> api : apis) {
            for (Method method : api.getMethods()) {
                assertApiBoundary(method.getReturnType(), api, method);
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertApiBoundary(parameter, api, method);
                }
            }
        }
    }

    @Test
    void snapshotsDefensivelyCopyCollectionsAndBoundsNormalizeCoordinates() throws Exception {
        Class<?> serverSnapshot = load("api.general.ApiServerSnapshot");
        Class<?> feature = load("api.general.ApiFeature");
        Object banking = Enum.valueOf(feature.asSubclass(Enum.class), "BANKING");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object snapshot = serverSnapshot.getConstructor(String.class, int.class, int.class, int.class,
                        int.class, int.class, Set.class)
                .newInstance("2.0.0", -1, -1, -1, -1, -1, Set.of(banking));
        assertEquals(0, serverSnapshot.getMethod("onlinePlayers").invoke(snapshot));
        Set<?> features = (Set<?>) serverSnapshot.getMethod("features").invoke(snapshot);
        assertThrows(UnsupportedOperationException.class, () -> ((Set) features).clear());

        Class<?> bounds = load("api.ApiBlockBounds");
        Object normalized = bounds.getConstructor(String.class, int.class, int.class, int.class,
                        int.class, int.class, int.class)
                .newInstance("MINECRAFT:OVERWORLD", 9, 8, 7, 1, 2, 3);
        assertEquals("minecraft:overworld", bounds.getMethod("dimension").invoke(normalized));
        assertEquals(1, bounds.getMethod("minX").invoke(normalized));
        assertEquals(9, bounds.getMethod("maxX").invoke(normalized));
        assertTrue((Long) bounds.getMethod("volume").invoke(normalized) > 0L);
    }

    private static void assertMethods(Class<?> type, String... names) {
        Set<String> actual = Arrays.stream(type.getMethods()).map(Method::getName).collect(java.util.stream.Collectors.toSet());
        for (String name : names) assertTrue(actual.contains(name), type.getName() + " missing " + name);
    }

    private static void assertApiBoundary(Class<?> type, Class<?> api, Method method) {
        if (type.isPrimitive() || type.isArray() || type.getName().startsWith("java.")) return;
        assertTrue(type.getName().startsWith("net.austizz.ultimatebankingsystem.api."),
                api.getSimpleName() + "." + method.getName() + " leaks " + type.getName());
        assertTrue(Modifier.isPublic(type.getModifiers()));
    }

    private static Class<?> load(String suffix) throws ClassNotFoundException {
        return Class.forName("net.austizz.ultimatebankingsystem." + suffix, true, LOADER);
    }
}
