package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStaffSafeAccessTest {
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    @Test
    void newHireDefaultsToNoSafeAccessAndGrantRevokeAreStructuralAndIdempotent() throws Exception {
        Object metadata = metadataWithEmployee(EMPLOYEE_ID, "STAFF", "125.50");

        Object initial = readStaffingRoster(metadata);
        Object employee = first(playerEmployees(initial));
        assertEquals(EMPLOYEE_ID, value(employee, "playerId"));
        assertEquals("STAFF", value(employee, "role"));
        assertEquals(new BigDecimal("125.50"), value(employee, "salary"));
        assertFalse((Boolean) value(employee, "online"));
        assertFalse((Boolean) value(employee, "safeAccessGranted"),
                "new hires must not receive Safe Access by default");
        assertFalse(contains(metadata, safeAccessKey(), TAG_LIST));

        assertTrue(grantSafeAccess(metadata, EMPLOYEE_ID));
        assertFalse(grantSafeAccess(metadata, EMPLOYEE_ID), "grant must be idempotent");
        Object safeAccess = getList(metadata, safeAccessKey(), TAG_COMPOUND);
        assertEquals(1, size(safeAccess), "Safe Access must be stored as one structural list entry");
        Object entry = getCompound(safeAccess, 0);
        assertEquals(EMPLOYEE_ID, getUuid(entry, "playerId"));
        assertTrue(getBoolean(entry, "safeAccess"));
        assertTrue(hasSafeAccess(metadata, EMPLOYEE_ID));
        assertTrue((Boolean) value(first(playerEmployees(readStaffingRoster(metadata))), "safeAccessGranted"));

        assertTrue(revokeSafeAccess(metadata, EMPLOYEE_ID));
        assertFalse(revokeSafeAccess(metadata, EMPLOYEE_ID), "revoke must be idempotent");
        assertFalse(hasSafeAccess(metadata, EMPLOYEE_ID));
        assertFalse((Boolean) value(first(playerEmployees(readStaffingRoster(metadata))), "safeAccessGranted"));
    }

    @Test
    void removingEmployeeClearsItsGrantWithoutDestroyingOtherHireData() throws Exception {
        UUID otherEmployee = UUID.fromString("30000000-0000-0000-0000-000000000002");
        Object metadata = metadataWithEmployee(EMPLOYEE_ID, "TELLER", "80");
        putString(metadata, "employees", getString(metadata, "employees") + ";" + otherEmployee + "=AUDITOR:90");
        assertTrue(grantSafeAccess(metadata, EMPLOYEE_ID));
        assertTrue(grantSafeAccess(metadata, otherEmployee));

        assertTrue(removeEmployeeForStaffing(metadata, EMPLOYEE_ID));

        assertFalse(getString(metadata, "employees").contains(EMPLOYEE_ID.toString()));
        assertTrue(getString(metadata, "employees").contains(otherEmployee.toString()));
        assertFalse(hasSafeAccess(metadata, EMPLOYEE_ID));
        assertTrue(hasSafeAccess(metadata, otherEmployee));
    }

    @Test
    void protectedSafeAccessAllowsOrdinaryEmployeesOnlyWhenExplicitlyGranted() throws Exception {
        Object metadata = metadataWithEmployee(EMPLOYEE_ID, "STAFF", "40");

        assertTrue(canAccessProtectedSafeArea(true, metadata, OWNER_ID));
        assertFalse(canAccessProtectedSafeArea(false, metadata, EMPLOYEE_ID));

        assertTrue(grantSafeAccess(metadata, EMPLOYEE_ID));

        assertTrue(canAccessProtectedSafeArea(false, metadata, EMPLOYEE_ID));
    }

    @Test
    void orphanedExplicitSafeAccessDoesNotAuthorizeProtectedArea() throws Exception {
        Object metadata = newCompoundTag();
        Object entries = newListTag();
        Object entry = newCompoundTag();
        putUuid(entry, "playerId", EMPLOYEE_ID);
        putBoolean(entry, "safeAccess", true);
        addTag(entries, entry);
        putTag(metadata, safeAccessKey(), entries);

        assertTrue(hasSafeAccess(metadata, EMPLOYEE_ID),
                "the persisted explicit flag exists, but it must not be enough on its own");
        assertFalse(canAccessProtectedSafeArea(false, metadata, EMPLOYEE_ID),
                "explicit Safe Access must only authorize players who are still current employees");
    }

    @Test
    void rosterSummariesAreImmutableAndIncludeTellerList() throws Exception {
        Object metadata = metadataWithEmployee(EMPLOYEE_ID, "DIRECTOR", "250");

        Object roster = readStaffingRoster(metadata);

        assertThrows(UnsupportedOperationException.class, () -> playerEmployees(roster).clear());
        assertThrows(UnsupportedOperationException.class, () -> bankTellers(roster).clear());
        assertEquals(0, bankTellers(roster).size(), "null server has no loaded teller levels to scan");
    }

    private static Object readStaffingRoster(Object metadata) throws Exception {
        return staffingServiceClass()
                .getMethod("readRoster", compoundTagClass())
                .invoke(null, metadata);
    }

    private static boolean grantSafeAccess(Object metadata, UUID employeeId) throws Exception {
        return (Boolean) staffingServiceClass()
                .getMethod("grantSafeAccess", compoundTagClass(), UUID.class)
                .invoke(null, metadata, employeeId);
    }

    private static boolean revokeSafeAccess(Object metadata, UUID employeeId) throws Exception {
        return (Boolean) staffingServiceClass()
                .getMethod("revokeSafeAccess", compoundTagClass(), UUID.class)
                .invoke(null, metadata, employeeId);
    }

    private static boolean hasSafeAccess(Object metadata, UUID employeeId) throws Exception {
        return (Boolean) staffingServiceClass()
                .getMethod("hasExplicitSafeAccess", compoundTagClass(), UUID.class)
                .invoke(null, metadata, employeeId);
    }

    private static boolean removeEmployeeForStaffing(Object metadata, UUID employeeId) throws Exception {
        return (Boolean) staffingServiceClass()
                .getMethod("removeEmployee", compoundTagClass(), UUID.class)
                .invoke(null, metadata, employeeId);
    }

    private static boolean canAccessProtectedSafeArea(boolean manager, Object metadata, UUID playerId) throws Exception {
        return (Boolean) staffingServiceClass()
                .getMethod("canAccessProtectedSafeArea", boolean.class, compoundTagClass(), UUID.class)
                .invoke(null, manager, metadata, playerId);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> playerEmployees(Object roster) throws Exception {
        return (List<Object>) value(roster, "playerEmployees");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> bankTellers(Object roster) throws Exception {
        return (List<Object>) value(roster, "bankTellers");
    }

    private static Object first(List<Object> values) {
        return values.get(0);
    }

    private static Object value(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    private static String safeAccessKey() throws Exception {
        Field field = staffingServiceClass().getField("SAFE_ACCESS_KEY");
        return (String) field.get(null);
    }

    private static Object metadataWithEmployee(UUID employeeId, String role, String salary) throws Exception {
        Object metadata = newCompoundTag();
        putString(metadata, "employees", employeeId + "=" + role + ":" + salary);
        return metadata;
    }

    private static Object newCompoundTag() throws Exception {
        return compoundTagClass().getConstructor().newInstance();
    }

    private static Object newListTag() throws Exception {
        return listTagClass().getConstructor().newInstance();
    }

    private static Class<?> compoundTagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.CompoundTag", true, nbtClassLoader());
    }

    private static Class<?> listTagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.ListTag", true, nbtClassLoader());
    }

    private static Class<?> staffingServiceClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService",
                true, nbtClassLoader());
    }

    private static ClassLoader nbtClassLoader() {
        return NeoForgeTestClassLoader.get();
    }

    private static void putString(Object tag, String key, String value) throws Exception {
        tag.getClass().getMethod("putString", String.class, String.class).invoke(tag, key, value);
    }

    private static void putUuid(Object tag, String key, UUID value) throws Exception {
        tag.getClass().getMethod("putUUID", String.class, UUID.class).invoke(tag, key, value);
    }

    private static void putBoolean(Object tag, String key, boolean value) throws Exception {
        tag.getClass().getMethod("putBoolean", String.class, boolean.class).invoke(tag, key, value);
    }

    private static void putTag(Object tag, String key, Object value) throws Exception {
        tag.getClass().getMethod("put", String.class, tagClass()).invoke(tag, key, value);
    }

    private static void addTag(Object list, Object value) throws Exception {
        list.getClass().getMethod("add", int.class, tagClass()).invoke(list, size(list), value);
    }

    private static Class<?> tagClass() throws Exception {
        return Class.forName("net.minecraft.nbt.Tag", true, nbtClassLoader());
    }

    private static String getString(Object tag, String key) throws Exception {
        return (String) tag.getClass().getMethod("getString", String.class).invoke(tag, key);
    }

    private static boolean contains(Object tag, String key, int type) throws Exception {
        return (Boolean) tag.getClass().getMethod("contains", String.class, int.class).invoke(tag, key, type);
    }

    private static Object getList(Object tag, String key, int type) throws Exception {
        return tag.getClass().getMethod("getList", String.class, int.class).invoke(tag, key, type);
    }

    private static int size(Object list) throws Exception {
        return (Integer) list.getClass().getMethod("size").invoke(list);
    }

    private static Object getCompound(Object list, int index) throws Exception {
        Method method = list.getClass().getMethod("getCompound", int.class);
        return method.invoke(list, index);
    }

    private static UUID getUuid(Object tag, String key) throws Exception {
        return (UUID) tag.getClass().getMethod("getUUID", String.class).invoke(tag, key);
    }

    private static boolean getBoolean(Object tag, String key) throws Exception {
        return (Boolean) tag.getClass().getMethod("getBoolean", String.class).invoke(tag, key);
    }
}
