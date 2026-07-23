package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.util.List;

final class OwnerPcVaultRouteMetadataFixture {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    private OwnerPcVaultRouteMetadataFixture() {
    }

    static Object metadata() throws Exception {
        Object vault = tag();
        putString(vault, "id", OwnerPcVaultRouteTestSupport.VAULT_ID);
        putString(vault, "safeAreaId", "area-main");
        putString(vault, "dimension", OwnerPcVaultRouteTestSupport.DIMENSION);
        putString(vault, "status", "ROUTES_PENDING");
        put(vault, "routeHooks", listTag());

        Object area = tag();
        putString(area, "id", "area-main");
        putString(area, "premiseId", "premise-main");
        bounds(area, 19, 63, 19, 24, 67, 24);
        put(area, "vaults", listTag(vault));

        Object premise = tag();
        putString(premise, "id", "premise-main");
        putString(premise, "bankId", OwnerPcVaultRouteTestSupport.BANK_ID.toString());
        putString(premise, "mode", "PUBLIC");
        bounds(premise, 0, 60, 0, 30, 80, 30);
        putInt(premise, "exitX", 31);
        putInt(premise, "exitY", 64);
        putInt(premise, "exitZ", 0);
        premise.getClass().getMethod("putFloat", String.class, float.class)
                .invoke(premise, "exitYaw", 0.0F);
        put(premise, "safeAreas", listTag(area));

        Object metadata = tag();
        put(metadata, "safeDepositPremises", listTag(premise));
        put(metadata, "safeTellerRoutes", listTag());
        return metadata;
    }

    @SuppressWarnings("unchecked")
    static Object ambiguousMetadata() throws Exception {
        Object metadata = metadata();
        List<Object> premises = (List<Object>) compound().getMethod(
                        "getList", String.class, int.class)
                .invoke(metadata, "safeDepositPremises", 10);
        Object duplicate = premises.get(0).getClass().getMethod("copy")
                .invoke(premises.get(0));
        premises.add(duplicate);
        return metadata;
    }

    static Class<?> compound() throws ClassNotFoundException {
        return load("net.minecraft.nbt.CompoundTag");
    }

    private static void bounds(Object tag, int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ) throws Exception {
        putString(tag, "dimension", OwnerPcVaultRouteTestSupport.DIMENSION);
        putInt(tag, "minX", minX);
        putInt(tag, "minY", minY);
        putInt(tag, "minZ", minZ);
        putInt(tag, "maxX", maxX);
        putInt(tag, "maxY", maxY);
        putInt(tag, "maxZ", maxZ);
    }

    private static Object tag() throws Exception {
        return compound().getConstructor().newInstance();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listTag(Object... values) throws Exception {
        List<Object> list = (List<Object>) load("net.minecraft.nbt.ListTag")
                .getConstructor().newInstance();
        list.addAll(List.of(values));
        return list;
    }

    private static void put(Object tag, String key, Object value) throws Exception {
        compound().getMethod("put", String.class, load("net.minecraft.nbt.Tag"))
                .invoke(tag, key, value);
    }

    private static void putString(Object tag, String key, String value) throws Exception {
        compound().getMethod("putString", String.class, String.class)
                .invoke(tag, key, value);
    }

    private static void putInt(Object tag, String key, int value) throws Exception {
        compound().getMethod("putInt", String.class, int.class).invoke(tag, key, value);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }
}
