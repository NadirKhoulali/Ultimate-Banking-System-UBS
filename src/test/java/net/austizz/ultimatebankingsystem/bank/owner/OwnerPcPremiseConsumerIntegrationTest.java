package net.austizz.ultimatebankingsystem.bank.owner;

// SIZE_OK: inherited 443-line consumer matrix; +3 typed-request lines share its single projection fixture.

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremiseConsumerIntegrationTest {
    private static final String ROOT_PACKAGE = "net.austizz.ultimatebankingsystem.";
    private static final UUID BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TELLER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final String DIMENSION = "minecraft:overworld";

    @Test
    void premiseSetupObjectiveUsesDedicatedPremisesPanel() throws Exception {
        Class<?> objectiveClass = load("network.OwnerPcSetupObjectivePayload");
        Object objective = construct(
                objectiveClass,
                new Class<?>[]{boolean.class, int.class, int.class, int.class, List.class},
                false, 0, 0, 0, List.of("Claim a bank premise."));
        Class<?> vaultClass = load("network.OwnerPcVaultSetupPayload");
        Class<?> serviceClass = load("bank.owner.setup.BankSetupObjectiveSyncService");
        Method resolve = serviceClass.getDeclaredMethod("resolveStep", objectiveClass, vaultClass);
        resolve.setAccessible(true);

        Object step = resolve.invoke(null, objective, null);

        assertEquals(1, value(step, "number"));
        assertEquals("Claim the bank premises", value(step, "title"));
        assertEquals(
                "Where: Bank Owner PC > Premises > Claim Premise. "
                        + "Select the bank building first so vault access can be enforced inside its premises.",
                value(step, "detail"));
    }

    @Test
    void emptyPremiseFeedsSetupZoneAndLeavesReadyVaultExact() throws Exception {
        Object empty = premise("premise-empty", 30, 50, "PUBLIC", List.of());
        Object populatedArea = construct(
                load("bank.safebox.setup.SafeAreaSnapshot"),
                new Class<?>[]{String.class, String.class,
                        load("bank.safebox.setup.SafeBlockBounds"), List.class},
                "area-populated", "premise-populated", bounds(5, 10), List.of());
        Object populated = premise(
                "premise-populated", 0, 20, "PUBLIC", List.of(populatedArea));
        Object setup = construct(
                load("bank.safebox.setup.SafeDepositSetupSnapshot"),
                new Class<?>[]{int.class, List.class},
                1, List.of(empty, populated));
        Class<?> cacheClass = load("bank.safebox.zone.SafeBoxZoneCache");
        Method fromSnapshots = cacheClass.getDeclaredMethod("fromSnapshots", Map.class);
        fromSnapshots.setAccessible(true);

        Object index = fromSnapshots.invoke(null, Map.of(BANK_ID, setup));
        List<?> records = listValue(index, "records");

        assertEquals(2, records.size(), "validated empty premises remain enforceable zones");
        Object emptyRecord = record(records, "premise-empty");
        assertTrue(listValue(emptyRecord, "safeAreas").isEmpty());
        Object populatedRecord = record(records, "premise-populated");
        List<String> areaIds = new ArrayList<>();
        for (Object area : listValue(populatedRecord, "safeAreas")) {
            areaIds.add((String) value(area, "id"));
        }
        assertEquals(List.of("area-populated"), areaIds,
                "an empty sibling must not alter the populated premise's exact area identity");
    }

    @Test
    void explicitEmptyPremiseCountsWithoutCreatingVaultReadiness() throws Exception {
        Object empty = premise("premise-empty", 30, 50, "PUBLIC", List.of());
        Object setup = setup(List.of(empty));

        Object result = setupProjection(setup, List.of());
        Object objective = value(result, "objective");

        assertEquals(false, value(objective, "ready"));
        assertEquals(1, value(objective, "premiseCount"));
        assertEquals(0, value(objective, "vaultCount"));
        assertEquals(0, value(objective, "readyVaultCount"));
        assertTrue(listValue(result, "vaults").isEmpty());
    }

    @Test
    void emptySiblingDoesNotChangeReadyVaultProjection() throws Exception {
        Object empty = premise("premise-empty", 30, 50, "PUBLIC", List.of());
        Object metadata = exactRouteMetadata();
        Object routeHook = construct(
                load("bank.safebox.setup.SafeTellerRouteHook"),
                new Class<?>[]{String.class, boolean.class, String.class, String.class},
                TELLER_ID.toString(), true, stableRouteId("OUTBOUND"), stableRouteId("RETURN"));
        Class<?> statusClass = load("bank.safebox.setup.SafeVaultSetupStatus");
        Object vault = construct(
                load("bank.safebox.setup.SafeVaultSnapshot"),
                new Class<?>[]{String.class, String.class, String.class, statusClass,
                        OptionalInt.class, OptionalInt.class, OptionalInt.class, OptionalInt.class,
                        List.class},
                "vault-ready", "area-populated", DIMENSION, enumValue(statusClass, "READY"),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                List.of(routeHook));
        Object area = construct(
                load("bank.safebox.setup.SafeAreaSnapshot"),
                new Class<?>[]{String.class, String.class,
                        load("bank.safebox.setup.SafeBlockBounds"), List.class},
                "area-populated", "premise-populated", bounds(5, 10), List.of(vault));
        Object populated = premise(
                "premise-populated", 0, 20, "PUBLIC", List.of(area));
        Object rowPos = construct(
                minecraft("core.BlockPos"),
                new Class<?>[]{int.class, int.class, int.class},
                6, 65, 0);
        Class<?> moduleType = load(
                "block.entity.custom.SafetyDepositBoxRowBlockEntity$ModuleType");
        Object modules = Array.newInstance(moduleType, 4);
        for (int index = 0; index < Array.getLength(modules); index++) {
            Array.set(modules, index, enumValue(moduleType, "SMALL"));
        }
        Object loadedRow = construct(
                load("bank.safebox.setup.SafeVaultReadinessResolver$LoadedRowSnapshot"),
                new Class<?>[]{String.class, minecraft("core.BlockPos"), modules.getClass()},
                DIMENSION, rowPos, modules);
        Object loadedFacts = construct(
                load("bank.safebox.setup.SafeVaultReadinessResolver$LoadedWorldFacts"),
                new Class<?>[]{Map.class, Map.class, List.class, Set.class},
                Map.of("vault-ready", true),
                Map.of("vault-ready", rowPos),
                List.of(loadedRow),
                Set.of("premise-populated"));
        Class<?> resolver = load("bank.safebox.setup.SafeVaultReadinessResolver");
        Class<?> contextType = load("bank.safebox.setup.SafeVaultReadinessResolver$EvaluationContext");
        Object context = construct(contextType,
                new Class<?>[]{minecraft("nbt.CompoundTag"), loadedFacts.getClass()}, metadata, loadedFacts);
        Class<?> locationType = load("bank.safebox.setup.SafeVaultReadinessResolver$RowLocation");
        Object location = construct(locationType,
                new Class<?>[]{String.class, minecraft("core.BlockPos")}, DIMENSION, rowPos);
        Class<?> requestType = load("bank.safebox.setup.SafeVaultReadinessResolver$RowRequest");
        Method resolve = resolver.getMethod("resolveForRow", requestType);

        Object withoutEmpty = resolve.invoke(null, construct(requestType,
                new Class<?>[]{contextType, load("bank.safebox.setup.SafeDepositSetupSnapshot"), locationType},
                context, setup(List.of(populated)), location));
        Object withEmpty = resolve.invoke(null, construct(requestType,
                new Class<?>[]{contextType, load("bank.safebox.setup.SafeDepositSetupSnapshot"), locationType},
                context, setup(List.of(empty, populated)), location));

        assertEquals(true, value(withoutEmpty, "mapped"));
        assertEquals("premise-populated", value(value(withoutEmpty, "premise"), "id"));
        assertEquals("area-populated", value(value(withoutEmpty, "safeArea"), "id"));
        assertEquals("vault-ready", value(value(withoutEmpty, "vault"), "id"));
        assertEquals(value(withoutEmpty, "premise"), value(withEmpty, "premise"),
                "an empty sibling must not change exact premise resolution");
        assertEquals(value(withoutEmpty, "safeArea"), value(withEmpty, "safeArea"),
                "an empty sibling must not change exact safe-area resolution");
        assertEquals(value(withoutEmpty, "vault"), value(withEmpty, "vault"),
                "an empty sibling must not change exact vault resolution");

        List<?> withoutRoutes = listValue(value(withoutEmpty, "vault"), "routeHooks");
        List<?> withRoutes = listValue(value(withEmpty, "vault"), "routeHooks");
        assertEquals(List.of(routeHook), withoutRoutes);
        assertEquals(withoutRoutes, withRoutes,
                "an empty sibling must not change exact teller-route binding");
        assertEquals(true, value(withoutRoutes.getFirst(), "ready"));

        Object withoutSummary = value(withoutEmpty, "summary");
        Object withSummary = value(withEmpty, "summary");
        assertEquals(withoutSummary, withSummary,
                "an empty sibling must not change resolver readiness");
        assertEquals(true, value(withoutSummary, "ready"));
        assertTrue(listValue(withoutSummary, "missingReasons").isEmpty());
        assertEquals(listValue(withoutEmpty, "humanMissingReasons"),
                listValue(withEmpty, "humanMissingReasons"));
    }

    @Test
    void successfulPremiseCommitClearsZoneCacheImmediately() throws Exception {
        ensureMinecraftBootstrap();
        Object server = unsafe().allocateInstance(minecraft("server.dedicated.DedicatedServer"));
        Object centralBank = centralBank();
        Object initialMetadata = setupMetadata(premiseMetadata("PUBLIC"));
        bankMetadata(centralBank).put(BANK_ID, initialMetadata);

        Class<?> cacheClass = load("bank.safebox.zone.SafeBoxZoneCache");
        Method index = cacheClass.getMethod("index",
                minecraft("server.MinecraftServer"),
                load("bank.centralbank.CentralBank"),
                long.class);
        Method clear = cacheClass.getMethod("clear", minecraft("server.MinecraftServer"));
        try {
            Object seeded = index.invoke(null, server, centralBank, 100L);
            assertEquals("PUBLIC", cachedMode(seeded, "premise-cache"));

            Map<String, Object> liveMetadata = metadataModel(initialMetadata);
            Object liveAuthority = authority(liveMetadata);
            Class<?> portsClass = load("bank.owner.premise.OwnerPcPremiseServerPorts");
            Method factory = portsClass.getDeclaredMethod(
                    "forIntegrationTest",
                    minecraft("server.MinecraftServer"),
                    load("bank.centralbank.CentralBank"),
                    load("bank.owner.premise.OwnerPcPremiseService$Authority"));
            factory.setAccessible(true);
            Object ports = factory.invoke(null, server, centralBank, liveAuthority);
            UUID operationId = UUID.fromString("40000000-0000-0000-0000-000000000001");
            Object result = executePremiseAction(ports,
                    premiseAction(operationId, "SET_MODE", "premise-cache", "STAFF_ONLY"));

            assertEquals(true, value(result, "success"));
            assertEquals(operationId, value(result, "operationId"));
            assertEquals("SET_MODE", ((Enum<?>) value(result, "action")).name());
            assertEquals("premise-cache", value(result, "premiseId"));
            Object reread = index.invoke(null, server, centralBank, 101L);
            assertEquals("STAFF_ONLY", cachedMode(reread, "premise-cache"),
                    "a successful production commit must be visible inside the 20-tick cache window");
            assertEquals(1, listValue(reread, "records").size());
        } finally {
            clear.invoke(null, server);
        }
    }

    private static Object authority(Map<String, Object> metadata) throws Exception {
        return construct(
                load("bank.owner.premise.OwnerPcPremiseService$Authority"),
                new Class<?>[]{Map.class, boolean.class, boolean.class, boolean.class,
                        boolean.class, boolean.class, boolean.class},
                metadata, true, true, true, true, true, false);
    }

    private static Object executePremiseAction(Object ports, Object payload) throws Exception {
        return load("bank.owner.premise.OwnerPcPremiseService")
                .getMethod("execute",
                        load("bank.owner.premise.OwnerPcPremiseService$Ports"),
                        load("network.OwnerPcPremiseActionPayload"))
                .invoke(null, ports, payload);
    }

    private static Object premiseAction(UUID operationId,
                                        String action,
                                        String premiseId,
                                        String mode) throws Exception {
        Class<?> actionType = load("network.OwnerPcPremiseActionPayload$Action");
        Class<?> modeType = load("bank.safebox.setup.SafePremiseMode");
        return construct(
                load("network.OwnerPcPremiseActionPayload"),
                new Class<?>[]{UUID.class, UUID.class, actionType, String.class, modeType},
                BANK_ID, operationId, enumValue(actionType, action), premiseId,
                enumValue(modeType, mode));
    }

    private static Object centralBank() throws Exception {
        Class<?> centralBankType = load("bank.centralbank.CentralBank");
        Object centralBank = unsafe().allocateInstance(centralBankType);
        setField(centralBank, centralBankType, "banks", new ConcurrentHashMap<>());
        setField(centralBank, centralBankType, "bankMetadata", new ConcurrentHashMap<>());
        return centralBank;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> bankMetadata(Object centralBank) throws Exception {
        return (Map<UUID, Object>) centralBank.getClass()
                .getMethod("getBankMetadata")
                .invoke(centralBank);
    }

    private static Object setupMetadata(Map<String, Object> metadata) throws Exception {
        Object source = construct(minecraft("nbt.CompoundTag"), new Class<?>[]{});
        return applySetupMutation(source, Map.of(), metadata);
    }

    private static Object exactRouteMetadata() throws Exception {
        Map<String, Object> vault = map(
                "id", "vault-ready", "safeAreaId", "area-populated",
                "dimension", DIMENSION, "status", "READY", "routeHooks", List.of());
        Map<String, Object> area = map(
                "id", "area-populated", "premiseId", "premise-populated",
                "dimension", DIMENSION, "minX", 5, "minY", 60, "minZ", -2,
                "maxX", 10, "maxY", 70, "maxZ", 2, "vaults", List.of(vault));
        Map<String, Object> premise = map(
                "id", "premise-populated", "bankId", BANK_ID.toString(),
                "dimension", DIMENSION, "minX", 0, "minY", 60, "minZ", -2,
                "maxX", 20, "maxY", 70, "maxZ", 2,
                "exitX", -2, "exitY", 64, "exitZ", 0, "exitYaw", 90.0F,
                "mode", "PUBLIC", "safeAreas", List.of(area));
        Object metadata = setupMetadata(map(
                "safeDepositSetupVersion", 1, "safeDepositPremises", List.of(premise)));
        saveRoute(metadata, "OUTBOUND");
        saveRoute(metadata, "RETURN");
        return metadata;
    }

    private static void saveRoute(Object metadata, String directionName) throws Exception {
        Class<?> direction = load("bank.safebox.route.SafeTellerRouteDirection");
        Class<?> position = load("bank.safebox.route.SafeTellerRoutePosition");
        Object start = construct(position, new Class<?>[]{int.class, int.class, int.class}, 1, 64, 1);
        Object finish = construct(position, new Class<?>[]{int.class, int.class, int.class}, 2, 64, 2);
        Object wait = construct(load("bank.safebox.route.SafeTellerRouteStep$Wait"),
                new Class<?>[]{int.class}, 1);
        Object route = load("bank.safebox.route.SafeTellerRoute").getMethod(
                        "create", String.class, String.class, String.class, direction,
                        String.class, position, position, List.class)
                .invoke(null, BANK_ID.toString(), "vault-ready", TELLER_ID.toString(),
                        enumValue(direction, directionName), DIMENSION, start, finish, List.of(wait));
        load("bank.safebox.route.SafeTellerRouteNbtStore").getMethod(
                        "saveAndBind", minecraft("nbt.CompoundTag"), load("bank.safebox.route.SafeTellerRoute"))
                .invoke(null, metadata, route);
    }

    private static String stableRouteId(String directionName) throws Exception {
        Class<?> direction = load("bank.safebox.route.SafeTellerRouteDirection");
        return (String) load("bank.safebox.route.SafeTellerRoute").getMethod(
                        "stableId", String.class, String.class, String.class, direction)
                .invoke(null, BANK_ID.toString(), "vault-ready", TELLER_ID.toString(),
                        enumValue(direction, directionName));
    }

    private static Object applySetupMutation(Object source,
                                             Map<String, Object> before,
                                             Map<String, Object> after) throws Exception {
        Class<?> builder = load("bank.owner.premise.OwnerPcPremisePayloadBuilder");
        Method apply = builder.getDeclaredMethod("applySetupMutation",
                minecraft("nbt.CompoundTag"), Map.class, Map.class);
        apply.setAccessible(true);
        return apply.invoke(null, source, before, after);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataModel(Object metadata) throws Exception {
        Class<?> builder = load("bank.owner.premise.OwnerPcPremisePayloadBuilder");
        Method model = builder.getDeclaredMethod("metadataModel", minecraft("nbt.CompoundTag"));
        model.setAccessible(true);
        return (Map<String, Object>) model.invoke(null, metadata);
    }

    private static Map<String, Object> premiseMetadata(String mode) {
        Map<String, Object> premise = map(
                "id", "premise-cache",
                "bankId", BANK_ID.toString(),
                "dimension", DIMENSION,
                "minX", 0,
                "minY", 60,
                "minZ", 0,
                "maxX", 10,
                "maxY", 70,
                "maxZ", 10,
                "exitX", -2,
                "exitY", 64,
                "exitZ", 0,
                "exitYaw", 90.0F,
                "mode", mode,
                "safeAreas", List.of());
        return map(
                "safeDepositSetupVersion", 1,
                "safeDepositPremises", List.of(premise));
    }

    private static String cachedMode(Object index, String premiseId) throws Exception {
        Object cached = record(listValue(index, "records"), premiseId);
        return ((Enum<?>) value(cached, "mode")).name();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return values;
    }

    private static void setField(Object target,
                                 Class<?> owner,
                                 String fieldName,
                                 Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static void ensureMinecraftBootstrap() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> loadingModList = Class.forName(
                "net.neoforged.fml.loading.LoadingModList", true, loader);
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
    }

    private static Class<?> minecraft(String relativeName) throws Exception {
        return Class.forName("net.minecraft." + relativeName, false, NeoForgeTestClassLoader.get());
    }

    private static Object premise(String id,
                                  int minX,
                                  int maxX,
                                  String modeName,
                                  List<?> areas) throws Exception {
        Class<?> modeClass = load("bank.safebox.setup.SafePremiseMode");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object mode = Enum.valueOf((Class<? extends Enum>) modeClass.asSubclass(Enum.class), modeName);
        return construct(
                load("bank.safebox.setup.SafePremiseSnapshot"),
                new Class<?>[]{String.class, String.class,
                        load("bank.safebox.setup.SafeBlockBounds"),
                        load("bank.safebox.setup.SafeExitSnapshot"), modeClass, List.class},
                id,
                BANK_ID.toString(),
                bounds(minX, maxX),
                construct(
                        load("bank.safebox.setup.SafeExitSnapshot"),
                        new Class<?>[]{String.class, int.class, int.class, int.class, float.class},
                        DIMENSION, minX - 2, 64, 0, 90.0F),
                mode,
                areas);
    }

    private static Object setup(List<?> premises) throws Exception {
        return construct(
                load("bank.safebox.setup.SafeDepositSetupSnapshot"),
                new Class<?>[]{int.class, List.class},
                1, premises);
    }

    private static Object setupProjection(Object setup, List<?> readiness) throws Exception {
        Class<?> builder = load("bank.owner.setup.BankSafeSetupPayloadBuilder");
        Method method = builder.getDeclaredMethod(
                "buildFromSnapshot", load("bank.safebox.setup.SafeDepositSetupSnapshot"), List.class,
                minecraft("nbt.CompoundTag"));
        method.setAccessible(true);
        Object metadata = construct(minecraft("nbt.CompoundTag"), new Class<?>[]{});
        return method.invoke(null, setup, readiness, metadata);
    }

    private static Object bounds(int minX, int maxX) throws Exception {
        return construct(
                load("bank.safebox.setup.SafeBlockBounds"),
                new Class<?>[]{String.class, int.class, int.class, int.class,
                        int.class, int.class, int.class},
                DIMENSION, minX, 60, -2, maxX, 70, 2);
    }

    private static Object record(List<?> records, String premiseId) throws Exception {
        for (Object record : records) {
            if (premiseId.equals(value(record, "premiseId"))) {
                return record;
            }
        }
        throw new AssertionError("Missing zone record for " + premiseId);
    }

    private static List<?> listValue(Object target, String accessor) throws Exception {
        return (List<?>) value(target, accessor);
    }

    private static Object value(Object target, String accessor) throws Exception {
        Method method = target.getClass().getMethod(accessor);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Constructor<?> constructor = type.getConstructor(parameterTypes);
        return constructor.newInstance(args);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private static Class<?> load(String relativeName) throws Exception {
        return Class.forName(ROOT_PACKAGE + relativeName, true, NeoForgeTestClassLoader.get());
    }
}
