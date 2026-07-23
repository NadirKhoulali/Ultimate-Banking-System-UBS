package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.util.Collections;
import java.util.List;

final class VaultRouteNbtTestSupport {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    private VaultRouteNbtTestSupport() {
    }

    static Object metadata() throws Exception {
        Object metadata = tag();
        putString(metadata, "arbitrary", "keep");
        putString(metadata, "safeDepositRentAmount", "42.00");
        put(metadata, "safeDepositAssignments", compounds("assignmentId", "assignment-1"));
        put(metadata, "safeDepositEscrow", compounds("contents", "keep"));
        Object hook = tag();
        putString(hook, "tellerId", "teller-1");
        putBoolean(hook, "bankBound", true);
        putString(hook, "returnRouteRef", "legacy-return");
        put(hook, "customHook", nested("hook"));
        Object vault = named("vault-1", "customVault", "keep");
        put(vault, "routeHooks", listTag(hook));
        Object siblingVault = named("vault-other", "sibling", "keep");
        Object area = named("safe-area-1", "premiseId", "premise-1");
        put(area, "vaults", listTag(vault, siblingVault));
        Object premise = named("premise-1", "bankId", "bank-1");
        put(premise, "safeAreas", listTag(area));
        Object siblingPremise = named("premise-other", "bankId", "bank-other");
        put(siblingPremise, "safeAreas", listTag());
        put(metadata, "safeDepositPremises", listTag(premise, siblingPremise));
        return metadata;
    }

    static Object routeTag(Object metadata, String routeId) throws Exception {
        for (Object route : list(metadata, "safeTellerRoutes")) {
            if (routeId.equals(string(route, "id"))) {
                return route;
            }
        }
        throw new AssertionError("route tag not found: " + routeId);
    }

    static Object stepTag(Object metadata, String routeId, int index) throws Exception {
        return list(routeTag(metadata, routeId), "steps").get(index);
    }

    static Object hook(Object metadata, String teller) throws Exception {
        return list(targetVault(metadata), "routeHooks").stream()
                .filter(candidate -> teller.equals(stringUnchecked(candidate, "tellerId")))
                .findFirst().orElseThrow();
    }

    static Object targetVault(Object metadata) throws Exception {
        return list(targetArea(metadata), "vaults").get(0);
    }

    static Object targetArea(Object metadata) throws Exception {
        return list(list(metadata, "safeDepositPremises").get(0), "safeAreas").get(0);
    }

    static String unrelated(Object metadata) throws Exception {
        List<Object> premises = list(metadata, "safeDepositPremises");
        Object area = list(premises.get(0), "safeAreas").get(0);
        Object vault = list(area, "vaults").get(0);
        return getTag(metadata, "safeDepositAssignments") + "|"
                + getTag(metadata, "safeDepositEscrow") + "|"
                + string(metadata, "safeDepositRentAmount") + "|" + string(metadata, "arbitrary")
                + "|" + string(vault, "customVault") + "|" + list(area, "vaults").get(1)
                + "|" + premises.get(1);
    }

    static Object nested(String marker) throws Exception {
        Object tag = tag();
        putString(tag, "marker", marker);
        return tag;
    }

    static Object copyTag(Object tag) throws Exception {
        return tag.getClass().getMethod("copy").invoke(tag);
    }

    static Object getTag(Object compound, String key) throws Exception {
        return compoundClass().getMethod("get", String.class).invoke(compound, key);
    }

    static String string(Object compound, String key) throws Exception {
        return (String) compoundClass().getMethod("getString", String.class).invoke(compound, key);
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object compound, String key) throws Exception {
        return (List<Object>) compoundClass().getMethod("getList", String.class, int.class)
                .invoke(compound, key, 10);
    }

    @SuppressWarnings("unchecked")
    static List<Object> listTag(Object... values) throws Exception {
        List<Object> list = (List<Object>) Class.forName("net.minecraft.nbt.ListTag", true, LOADER)
                .getConstructor().newInstance();
        Collections.addAll(list, values);
        return list;
    }

    static Object tag() throws Exception {
        return compoundClass().getConstructor().newInstance();
    }

    static void put(Object compound, String key, Object value) throws Exception {
        compoundClass().getMethod("put", String.class, tagClass()).invoke(compound, key, value);
    }

    static void putString(Object compound, String key, String value) throws Exception {
        compoundClass().getMethod("putString", String.class, String.class)
                .invoke(compound, key, value);
    }

    static void putBoolean(Object compound, String key, boolean value) throws Exception {
        compoundClass().getMethod("putBoolean", String.class, boolean.class)
                .invoke(compound, key, value);
    }

    static void putInt(Object compound, String key, int value) throws Exception {
        compoundClass().getMethod("putInt", String.class, int.class).invoke(compound, key, value);
    }

    static void putLong(Object compound, String key, long value) throws Exception {
        compoundClass().getMethod("putLong", String.class, long.class).invoke(compound, key, value);
    }

    static void putShort(Object compound, String key, short value) throws Exception {
        compoundClass().getMethod("putShort", String.class, short.class).invoke(compound, key, value);
    }

    static void putDouble(Object compound, String key, double value) throws Exception {
        compoundClass().getMethod("putDouble", String.class, double.class).invoke(compound, key, value);
    }

    static Class<?> compoundClass() throws Exception {
        return Class.forName("net.minecraft.nbt.CompoundTag", true, LOADER);
    }

    private static Class<?> tagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.Tag", true, LOADER);
    }

    private static Object named(String id, String key, String value) throws Exception {
        Object tag = tag();
        putString(tag, "id", id);
        putString(tag, key, value);
        return tag;
    }

    private static Object compounds(String key, String value) throws Exception {
        Object tag = tag();
        putString(tag, key, value);
        return listTag(tag);
    }

    private static String stringUnchecked(Object tag, String key) {
        try {
            return string(tag, key);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
