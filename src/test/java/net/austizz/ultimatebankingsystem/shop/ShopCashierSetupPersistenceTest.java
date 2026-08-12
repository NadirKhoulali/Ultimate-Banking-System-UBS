package net.austizz.ultimatebankingsystem.shop;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopCashierSetupPersistenceTest {
    @Test
    void persistedCashierLinkKeepsSetupCompleteWhileCashierChunkIsUnloaded() throws Exception {
        Class<?> serviceType = Class.forName(
                "net.austizz.ultimatebankingsystem.shop.ShopService",
                true,
                NeoForgeTestClassLoader.get()
        );
        Method resolver = serviceType.getDeclaredMethod(
                "resolveSetupCashierStatusFromEvidence",
                Set.class,
                Set.class
        );
        resolver.setAccessible(true);

        UUID linkedCashierId = UUID.fromString("0ac2f196-53a5-4567-a878-4d4d74e2d54d");
        Object status = resolver.invoke(null, Set.of(), Set.of(linkedCashierId));

        assertEquals(1, readCount(status, "cashiers"));
        assertEquals(1, readCount(status, "linkedCashiers"));
    }

    @Test
    void terminalLinkPruningIsPositionBasedAndIgnoresEntityLiveness() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> serviceType = Class.forName(
                "net.austizz.ultimatebankingsystem.shop.ShopService", true, loader);
        Class<?> compoundType = Class.forName("net.minecraft.nbt.CompoundTag", true, loader);
        Class<?> listType = Class.forName("net.minecraft.nbt.ListTag", true, loader);
        Class<?> tagType = Class.forName("net.minecraft.nbt.Tag", true, loader);

        // The signature itself is the contract: no liveness input exists that could
        // wipe links for cashiers whose chunks happen to be unloaded.
        Method prune = serviceType.getDeclaredMethod("pruneTerminalLinksOutsideClaims", compoundType);
        prune.setAccessible(true);

        Object shopTag = compoundType.getConstructor().newInstance();
        Object claims = listType.getConstructor().newInstance();
        Object region = compoundType.getConstructor().newInstance();
        putString(compoundType, region, "dim", "minecraft:overworld");
        putInt(compoundType, region, "min_x", 0);
        putInt(compoundType, region, "min_y", 0);
        putInt(compoundType, region, "min_z", 0);
        putInt(compoundType, region, "max_x", 15);
        putInt(compoundType, region, "max_y", 255);
        putInt(compoundType, region, "max_z", 15);
        addTag(listType, tagType, claims, region);
        putTag(compoundType, tagType, shopTag, "claims", claims);

        Object links = listType.getConstructor().newInstance();
        Object insideLink = link(compoundType, "minecraft:overworld", 5, 64, 5,
                UUID.fromString("0ac2f196-53a5-4567-a878-4d4d74e2d54d"));
        Object outsideLink = link(compoundType, "minecraft:overworld", 40, 64, 40,
                UUID.fromString("1bd30287-64b6-4678-b989-5e5e85f3e65e"));
        addTag(listType, tagType, links, insideLink);
        addTag(listType, tagType, links, outsideLink);
        putTag(compoundType, tagType, shopTag, "cashier_terminals", links);

        int removed = (int) prune.invoke(null, shopTag);

        assertEquals(1, removed, "only the link outside the remaining claims may be pruned");
        Method getList = compoundType.getMethod("getList", String.class, int.class);
        Object remaining = getList.invoke(shopTag, "cashier_terminals", 10);
        Method size = listType.getMethod("size");
        assertEquals(1, (int) size.invoke(remaining),
                "the in-claim link must survive even though no cashier entity is loaded");
    }

    private static Object link(Class<?> compoundType, String dim, int x, int y, int z, UUID cashierId)
            throws Exception {
        Object entry = compoundType.getConstructor().newInstance();
        putString(compoundType, entry, "dim", dim);
        putInt(compoundType, entry, "x", x);
        putInt(compoundType, entry, "y", y);
        putInt(compoundType, entry, "z", z);
        compoundType.getMethod("putUUID", String.class, UUID.class).invoke(entry, "cashier_id", cashierId);
        return entry;
    }

    private static void putString(Class<?> compoundType, Object tag, String key, String value) throws Exception {
        compoundType.getMethod("putString", String.class, String.class).invoke(tag, key, value);
    }

    private static void putInt(Class<?> compoundType, Object tag, String key, int value) throws Exception {
        compoundType.getMethod("putInt", String.class, int.class).invoke(tag, key, value);
    }

    private static void putTag(Class<?> compoundType, Class<?> tagType, Object tag, String key, Object value)
            throws Exception {
        compoundType.getMethod("put", String.class, tagType).invoke(tag, key, value);
    }

    private static void addTag(Class<?> listType, Class<?> tagType, Object list, Object value) throws Exception {
        listType.getMethod("add", Object.class).invoke(list, value);
    }

    private static int readCount(Object status, String accessorName) throws Exception {
        Method accessor = status.getClass().getDeclaredMethod(accessorName);
        accessor.setAccessible(true);
        return (int) accessor.invoke(status);
    }
}
