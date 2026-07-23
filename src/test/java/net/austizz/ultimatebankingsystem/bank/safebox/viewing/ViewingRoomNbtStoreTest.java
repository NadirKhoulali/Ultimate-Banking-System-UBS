package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewingRoomNbtStoreTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();
    private static final String VIEWING_PACKAGE =
            "net.austizz.ultimatebankingsystem.bank.safebox.viewing.";

    @Test
    void roundTripPreservesRoomIdentityGeometryAnchorsAndState() throws Exception {
        Object metadata = compoundTagClass().getConstructor().newInstance();
        Object later = room(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                "Second Room", "premise-main", 200L, 300L, true);
        Object earlier = room(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "First Room", "premise-main", 100L, 250L, false);

        write(metadata, List.of(later, earlier));
        List<?> restored = read(metadata);

        assertEquals(2, restored.size());
        assertEquals("First Room", value(restored.get(0), "name"));
        assertEquals("Second Room", value(restored.get(1), "name"));
        assertEquals(250L, value(restored.get(0), "lastUsedAtMillis"));
        assertEquals(true, value(restored.get(1), "adminSuspended"));
        Object bounds = value(restored.get(0), "bounds");
        assertEquals("minecraft:overworld", value(bounds, "dimension"));
        assertEquals(10, value(bounds, "minX"));
        assertEquals(15, value(bounds, "maxZ"));
        Object display = value(restored.get(0), "displayAnchor");
        assertEquals(12.5D, value(display, "x"));
        assertEquals(180.0F, value(display, "yaw"));
        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) restored).add(earlier));
    }

    @Test
    void readDropsDuplicateIdsAndMalformedEntries() throws Exception {
        Object metadata = compoundTagClass().getConstructor().newInstance();
        UUID sharedId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        List<Object> supplied = new ArrayList<>();
        supplied.add(room(sharedId, "Original", "premise-main", 10L, 0L, false));
        supplied.add(room(sharedId, "Duplicate", "premise-main", 20L, 0L, false));
        supplied.add(null);

        write(metadata, supplied);
        List<?> restored = read(metadata);

        assertEquals(1, restored.size());
        assertEquals(sharedId, value(restored.getFirst(), "id"));
        assertEquals("Original", value(restored.getFirst(), "name"));
        assertTrue((Boolean) value(restored.getFirst(), "anchorsComplete"));
    }

    private static Object room(UUID id,
                               String name,
                               String premiseId,
                               long createdAt,
                               long lastUsedAt,
                               boolean suspended) throws Exception {
        Class<?> boundsClass = LOADER.loadClass(
                "net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds");
        Class<?> anchorClass = LOADER.loadClass(VIEWING_PACKAGE + "ViewingRoomAnchor");
        Class<?> roomClass = LOADER.loadClass(VIEWING_PACKAGE + "ViewingRoomSnapshot");
        Object bounds = boundsClass
                .getConstructor(String.class, int.class, int.class, int.class,
                        int.class, int.class, int.class)
                .newInstance("minecraft:overworld", 10, 60, 10, 15, 65, 15);
        Object customer = anchor(anchorClass, 11.5D, 61.0D, 11.5D, 0.0F);
        Object teller = anchor(anchorClass, 14.5D, 61.0D, 11.5D, 90.0F);
        Object display = anchor(anchorClass, 12.5D, 61.0D, 14.5D, 180.0F);
        return roomClass.getConstructor(UUID.class, String.class, String.class, boundsClass,
                        anchorClass, anchorClass, anchorClass, long.class, long.class, boolean.class)
                .newInstance(id, name, premiseId, bounds, customer, teller, display,
                        createdAt, lastUsedAt, suspended);
    }

    private static Object anchor(Class<?> anchorClass,
                                 double x,
                                 double y,
                                 double z,
                                 float yaw) throws Exception {
        return anchorClass.getConstructor(
                        String.class, double.class, double.class, double.class, float.class, float.class)
                .newInstance("minecraft:overworld", x, y, z, yaw, 0.0F);
    }

    private static void write(Object metadata, List<?> rooms) throws Exception {
        storeClass().getMethod("write", compoundTagClass(), List.class).invoke(null, metadata, rooms);
    }

    @SuppressWarnings("unchecked")
    private static List<?> read(Object metadata) throws Exception {
        return (List<?>) storeClass().getMethod("read", compoundTagClass()).invoke(null, metadata);
    }

    private static Object value(Object target, String method) throws Exception {
        Method accessor = target.getClass().getMethod(method);
        return accessor.invoke(target);
    }

    private static Class<?> storeClass() throws Exception {
        return LOADER.loadClass(VIEWING_PACKAGE + "ViewingRoomNbtStore");
    }

    private static Class<?> compoundTagClass() throws Exception {
        return LOADER.loadClass("net.minecraft.nbt.CompoundTag");
    }
}
