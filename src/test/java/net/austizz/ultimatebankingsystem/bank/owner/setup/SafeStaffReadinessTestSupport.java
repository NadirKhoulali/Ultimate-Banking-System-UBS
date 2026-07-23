package net.austizz.ultimatebankingsystem.bank.owner.setup;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

public final class SafeStaffReadinessTestSupport {
    public static final UUID BANK_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID EMPLOYEE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final String STAFFING_REASON = "SAFE_ACCESS_STAFF_MISSING";
    static final String STAFFING_LABEL = "At least one current bank employee must have Safe Access.";
    private static final String ROOT_PACKAGE = "net.austizz.ultimatebankingsystem.";

    private SafeStaffReadinessTestSupport() {
    }

    public record Scenario(Object snapshot, Object readiness, Object premise, Object area, Object vault,
                           Object summary) {
    }

    public static Scenario scenario(String... missingReasonNames) throws Exception {
        Object vault = vault();
        Object area = area(vault);
        Object premise = premise(area);
        Object snapshot = construct(load("bank.safebox.setup.SafeDepositSetupSnapshot"),
                new Class<?>[]{int.class, List.class}, 1, List.of(premise));
        List<Object> missing = new ArrayList<>();
        Class<?> reasonType = load("bank.safebox.setup.SafeReadinessMissingReason");
        for (String name : missingReasonNames) {
            missing.add(enumValue(reasonType, name));
        }
        Object summary = construct(load("bank.safebox.setup.SafeVaultReadinessSummary"),
                new Class<?>[]{String.class, boolean.class, List.class},
                "vault-1", missing.isEmpty(), missing);
        Object readiness = construct(load("bank.safebox.setup.SafeVaultReadinessResolver$RowReadiness"),
                new Class<?>[]{boolean.class,
                        load("bank.safebox.setup.SafePremiseSnapshot"),
                        load("bank.safebox.setup.SafeAreaSnapshot"),
                        load("bank.safebox.setup.SafeVaultSnapshot"),
                        load("bank.safebox.setup.SafeVaultReadinessSummary"), List.class},
                true, premise, area, vault, summary, humanReasons(missing));
        return new Scenario(snapshot, readiness, premise, area, vault, summary);
    }

    public static Object metadataWithEmployee() throws Exception {
        Object metadata = emptyMetadata();
        putString(metadata, "employees", EMPLOYEE_ID + "=STAFF:125.50");
        return metadata;
    }

    static Object emptyMetadata() throws Exception {
        return construct(minecraft("nbt.CompoundTag"), new Class<?>[]{});
    }

    static Object setupMetadataWithEmployee() throws Exception {
        Object metadata = metadataWithEmployee();
        Method apply = load("bank.owner.premise.OwnerPcPremisePayloadBuilder").getDeclaredMethod(
                "applySetupMutation", minecraft("nbt.CompoundTag"), Map.class, Map.class);
        apply.setAccessible(true);
        return apply.invoke(null, metadata, Map.of(), setupModel());
    }

    public static boolean staffingMutation(String methodName, Object metadata, UUID employeeId) throws Exception {
        Method method = load("bank.owner.staffing.BankStaffingService")
                .getMethod(methodName, minecraft("nbt.CompoundTag"), UUID.class);
        return (Boolean) method.invoke(null, metadata, employeeId);
    }

    static boolean hasEligibleStaff(Object metadata) throws Exception {
        Method method = load("bank.owner.staffing.BankStaffingService")
                .getMethod("hasEligibleSafeAccessEmployee", minecraft("nbt.CompoundTag"));
        return (Boolean) method.invoke(null, metadata);
    }

    static Object project(Object metadata, Object readiness) throws Exception {
        Method method = load("bank.safebox.SafetyDepositBoxService").getMethod(
                "applyStaffingReadiness",
                minecraft("nbt.CompoundTag"),
                load("bank.safebox.setup.SafeVaultReadinessResolver$RowReadiness"));
        return method.invoke(null, metadata, readiness);
    }

    static Object premiseProjection(Scenario scenario, Object metadata) throws Exception {
        Class<?> builder = load("bank.owner.premise.OwnerPcPremisePayloadBuilder");
        Method model = builder.getDeclaredMethod("metadataModel", minecraft("nbt.CompoundTag"));
        model.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadataModel = (Map<String, Object>) model.invoke(null, metadata);
        Method build = builder.getDeclaredMethod("build", minecraft("nbt.CompoundTag"), Map.class,
                load("bank.safebox.setup.SafeDepositSetupSnapshot"), List.class, UUID.class, Set.class);
        build.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> payloads = (List<Object>) build.invoke(null, metadata, metadataModel, scenario.snapshot(),
                List.of(scenario.readiness()), BANK_ID, Set.of());
        return payloads.getFirst();
    }

    static List<Object> publicServiceReadiness(Object metadata) throws Exception {
        ensureModLoading();
        Method method = load("bank.safebox.SafetyDepositBoxService").getMethod(
                "safeDepositVaultReadiness",
                minecraft("server.MinecraftServer"),
                minecraft("nbt.CompoundTag"));
        @SuppressWarnings("unchecked")
        List<Object> readiness = (List<Object>) method.invoke(null, null, metadata);
        return readiness;
    }

    static Object publicSetupProjection(Object metadata) throws Exception {
        ensureModLoading();
        Method method = load("bank.owner.setup.BankSafeSetupPayloadBuilder").getMethod(
                "build", minecraft("server.MinecraftServer"), minecraft("nbt.CompoundTag"));
        return method.invoke(null, null, metadata);
    }

    static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    @SuppressWarnings("unchecked")
    static List<Object> listValue(Object target, String accessor) throws Exception {
        return (List<Object>) value(target, accessor);
    }

    static String getString(Object metadata, String key) throws Exception {
        return (String) invoke(metadata, "getString", new Class<?>[]{String.class}, key);
    }

    static void putString(Object metadata, String key, String value) throws Exception {
        invoke(metadata, "putString", new Class<?>[]{String.class, String.class}, key, value);
    }

    static Object copy(Object metadata) throws Exception {
        return invoke(metadata, "copy", new Class<?>[]{});
    }

    static Class<?> load(String relativeName) throws Exception {
        return Class.forName(ROOT_PACKAGE + relativeName, true, NeoForgeTestClassLoader.get());
    }

    static Class<?> minecraft(String relativeName) throws Exception {
        return Class.forName("net.minecraft." + relativeName, true, NeoForgeTestClassLoader.get());
    }

    private static List<Object> humanReasons(List<Object> missing) throws Exception {
        Method method = load("bank.safebox.setup.SafeVaultReadinessResolver")
                .getMethod("humanReasons", List.class);
        @SuppressWarnings("unchecked")
        List<Object> reasons = (List<Object>) method.invoke(null, missing);
        return reasons;
    }

    private static Object premise(Object area) throws Exception {
        Class<?> mode = load("bank.safebox.setup.SafePremiseMode");
        return construct(load("bank.safebox.setup.SafePremiseSnapshot"),
                new Class<?>[]{String.class, String.class, load("bank.safebox.setup.SafeBlockBounds"),
                        load("bank.safebox.setup.SafeExitSnapshot"), mode, List.class},
                "premise-1", BANK_ID.toString(), bounds(0, 10),
                construct(load("bank.safebox.setup.SafeExitSnapshot"),
                        new Class<?>[]{String.class, int.class, int.class, int.class, float.class},
                        "minecraft:overworld", -1, 64, 0, 90.0F),
                enumValue(mode, "PUBLIC"), List.of(area));
    }

    private static Object area(Object vault) throws Exception {
        return construct(load("bank.safebox.setup.SafeAreaSnapshot"),
                new Class<?>[]{String.class, String.class, load("bank.safebox.setup.SafeBlockBounds"), List.class},
                "safe-area-1", "premise-1", bounds(1, 9), List.of(vault));
    }

    private static Object vault() throws Exception {
        Class<?> status = load("bank.safebox.setup.SafeVaultSetupStatus");
        Object route = construct(load("bank.safebox.setup.SafeTellerRouteHook"),
                new Class<?>[]{String.class, boolean.class, String.class, String.class},
                "teller-1", true, "outbound-route", "return-route");
        return construct(load("bank.safebox.setup.SafeVaultSnapshot"),
                new Class<?>[]{String.class, String.class, String.class, status,
                        OptionalInt.class, OptionalInt.class, OptionalInt.class, OptionalInt.class, List.class},
                "vault-1", "safe-area-1", "minecraft:overworld", enumValue(status, "READY"),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), List.of(route));
    }

    private static Object bounds(int min, int max) throws Exception {
        return construct(load("bank.safebox.setup.SafeBlockBounds"),
                new Class<?>[]{String.class, int.class, int.class, int.class, int.class, int.class, int.class},
                "minecraft:overworld", min, 60, min, max, 70, max);
    }

    private static Map<String, Object> setupModel() {
        Map<String, Object> route = Map.of(
                "tellerId", "teller-1",
                "bankBound", true,
                "outboundRouteRef", "outbound-route",
                "returnRouteRef", "return-route");
        Map<String, Object> vault = Map.of(
                "id", "vault-1",
                "safeAreaId", "safe-area-1",
                "dimension", "minecraft:overworld",
                "status", "READY",
                "routeHooks", List.of(route));
        Map<String, Object> area = Map.of(
                "id", "safe-area-1", "premiseId", "premise-1",
                "dimension", "minecraft:overworld",
                "minX", 1, "minY", 60, "minZ", 1,
                "maxX", 9, "maxY", 70, "maxZ", 9,
                "vaults", List.of(vault));
        Map<String, Object> premise = map(
                "id", "premise-1", "bankId", BANK_ID.toString(),
                "dimension", "minecraft:overworld",
                "minX", 0, "minY", 60, "minZ", 0,
                "maxX", 10, "maxY", 70, "maxZ", 10,
                "exitX", -1, "exitY", 64, "exitZ", 0, "exitYaw", 90.0F,
                "mode", "PUBLIC", "safeAreas", List.of(area));
        return Map.of("safeDepositSetupVersion", 1, "safeDepositPremises", List.of(premise));
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return values;
    }

    private static void ensureModLoading() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList", true, loader);
        if (loadingModList.getMethod("get").invoke(null) == null) {
            loadingModList.getMethod("of", List.class, List.class, List.class, List.class, Map.class)
                    .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        }
    }

    private static Object invoke(Object target, String method, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        return target.getClass().getMethod(method, parameterTypes).invoke(target, args);
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) throws Exception {
        Constructor<?> constructor = type.getConstructor(parameterTypes);
        return constructor.newInstance(args);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }
}
