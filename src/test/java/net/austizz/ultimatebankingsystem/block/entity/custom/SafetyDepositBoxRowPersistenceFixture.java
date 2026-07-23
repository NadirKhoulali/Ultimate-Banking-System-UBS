package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

@SuppressWarnings({"rawtypes", "unchecked"})
final class SafetyDepositBoxRowPersistenceFixture {
    static final String[] PERSISTED_FIELDS = {
            "module_", "account_", "box_number_", "open_until_", "door_progress_"
    };

    private static final String UBS_PACKAGE = "net.austizz.ultimatebankingsystem.";
    private static final String ROW_CLASS =
            "net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity";
    private static final ClassLoader LOADER = rowCompatibilityLoader();

    private final Object row;

    private SafetyDepositBoxRowPersistenceFixture(Object row) {
        this.row = row;
    }

    static SafetyDepositBoxRowPersistenceFixture create() throws Exception {
        Class<?> rowClass = rowClass();
        Object row = unsafe().allocateInstance(rowClass);
        int doorCount = rowClass.getField("DOOR_COUNT").getInt(null);
        Class<?> moduleClass = LOADER.loadClass(ROW_CLASS + "$ModuleType");
        Object modules = Array.newInstance(moduleClass, doorCount);
        Arrays.fill((Object[]) modules, enumValue(moduleClass, "EMPTY"));
        setField(row, "moduleTypes", modules);
        setField(row, "previousDoorProgress", new float[doorCount]);
        setField(row, "doorProgress", new float[doorCount]);
        setField(row, "openUntilGameTime", new long[doorCount]);
        setField(row, "assignedAccountIds", new UUID[doorCount]);
        setField(row, "viewingTransferIds", new UUID[doorCount]);
        setField(row, "heistBreachIds", new UUID[doorCount]);
        String[] boxNumbers = new String[doorCount];
        Arrays.fill(boxNumbers, "");
        setField(row, "boxNumbers", boxNumbers);
        return new SafetyDepositBoxRowPersistenceFixture(row);
    }

    static Object sevenDoorTag() throws Exception {
        Object tag = newCompoundTag();
        for (int index = 0; index < 7; index++) {
            putString(tag, "module_" + index, index < 4 ? "small" : "legacy_module_" + index);
            putUuid(tag, "account_" + index, new UUID(index + 1L, index + 101L));
            putString(tag, "box_number_" + index, " box-" + index + " ");
            putLong(tag, "open_until_" + index, 1_000L + index);
            putFloat(tag, "door_progress_" + index, index < 4 ? index / 4.0F : index + 0.25F);
        }
        return tag;
    }

    static Object activeOnlyTag() throws Exception {
        Object tag = newCompoundTag();
        for (int index = 0; index < 4; index++) {
            putString(tag, "module_" + index, "small");
            putUuid(tag, "account_" + index, new UUID(index + 201L, index + 301L));
            putString(tag, "box_number_" + index, "active-" + index);
            putLong(tag, "open_until_" + index, 2_000L + index);
            putFloat(tag, "door_progress_" + index, index / 4.0F);
        }
        return tag;
    }

    void load(Object tag) throws Exception {
        Method method = rowClass().getDeclaredMethod(
                "loadAdditional",
                compoundTagClass(),
                LOADER.loadClass("net.minecraft.core.HolderLookup$Provider")
        );
        method.setAccessible(true);
        method.invoke(row, tag, null);
    }

    Object save() throws Exception {
        Object tag = newCompoundTag();
        Method method = rowClass().getDeclaredMethod(
                "saveAdditional",
                compoundTagClass(),
                LOADER.loadClass("net.minecraft.core.HolderLookup$Provider")
        );
        method.setAccessible(true);
        method.invoke(row, tag, null);
        return tag;
    }

    RuntimeDoorState runtimeState(int index) throws Exception {
        Class<?> type = rowClass();
        return new RuntimeDoorState(
                ((Enum<?>) type.getMethod("getModuleType", int.class).invoke(row, index)).name(),
                (int) type.getMethod("getModuleStartForRow", int.class).invoke(row, index),
                (boolean) type.getMethod("isAssignableBoxStart", int.class).invoke(row, index),
                (boolean) type.getMethod("isAssigned", int.class).invoke(row, index),
                (UUID) type.getMethod("getAssignedAccountId", int.class).invoke(row, index),
                (String) type.getMethod("getBoxNumber", int.class).invoke(row, index),
                (float) type.getMethod("getDoorProgress", int.class, float.class).invoke(row, index, 0.5F),
                (float) type.getMethod("getCurrentDoorProgress", int.class).invoke(row, index)
        );
    }

    int moduleSnapshotLength() throws Exception {
        return Array.getLength(rowClass().getMethod("getModuleTypesSnapshot").invoke(row));
    }

    void assignDoor(int index, UUID accountId, String boxNumber) throws Exception {
        rowClass().getMethod("assignDoor", int.class, UUID.class, String.class)
                .invoke(row, index, accountId, boxNumber);
    }

    void clearDoorAssignment(int index) throws Exception {
        rowClass().getMethod("clearDoorAssignment", int.class).invoke(row, index);
    }

    boolean installSmallModule(int index) throws Exception {
        Class<?> moduleClass = LOADER.loadClass(ROW_CLASS + "$ModuleType");
        Object small = enumValue(moduleClass, "SMALL");
        return (boolean) rowClass().getMethod("installModule", int.class, moduleClass).invoke(row, index, small);
    }

    void openDoor(int index, long openUntil) throws Exception {
        rowClass().getMethod("openDoor", int.class, long.class).invoke(row, index, openUntil);
    }

    void closeDoor(int index) throws Exception {
        rowClass().getMethod("closeDoor", int.class).invoke(row, index);
    }

    boolean beginViewingTransfer(int index, UUID sessionId) throws Exception {
        return (boolean) rowClass().getMethod("beginViewingTransfer", int.class, UUID.class)
                .invoke(row, index, sessionId);
    }

    boolean endViewingTransfer(int index, UUID sessionId) throws Exception {
        return (boolean) rowClass().getMethod("endViewingTransfer", int.class, UUID.class)
                .invoke(row, index, sessionId);
    }

    boolean hasAnyViewingTransfer() throws Exception {
        return (boolean) rowClass().getMethod("hasAnyViewingTransfer").invoke(row);
    }

    static Object newCompoundTag() throws Exception {
        return compoundTagClass().getConstructor().newInstance();
    }

    static Object getTag(Object tag, String key) throws Exception {
        return compoundTagClass().getMethod("get", String.class).invoke(tag, key);
    }

    static Object getCompound(Object tag, String key) throws Exception {
        return compoundTagClass().getMethod("getCompound", String.class).invoke(tag, key);
    }

    static String getString(Object tag, String key) throws Exception {
        return (String) compoundTagClass().getMethod("getString", String.class).invoke(tag, key);
    }

    static UUID getUuid(Object tag, String key) throws Exception {
        return (UUID) compoundTagClass().getMethod("getUUID", String.class).invoke(tag, key);
    }

    static long getLong(Object tag, String key) throws Exception {
        return (long) compoundTagClass().getMethod("getLong", String.class).invoke(tag, key);
    }

    static boolean contains(Object tag, String key) throws Exception {
        return (boolean) compoundTagClass().getMethod("contains", String.class).invoke(tag, key);
    }

    static void putString(Object tag, String key, String value) throws Exception {
        compoundTagClass().getMethod("putString", String.class, String.class).invoke(tag, key, value);
    }

    static void putTag(Object tag, String key, Object value) throws Exception {
        compoundTagClass().getMethod("put", String.class, LOADER.loadClass("net.minecraft.nbt.Tag"))
                .invoke(tag, key, value);
    }

    private static void putUuid(Object tag, String key, UUID value) throws Exception {
        compoundTagClass().getMethod("putUUID", String.class, UUID.class).invoke(tag, key, value);
    }

    private static void putLong(Object tag, String key, long value) throws Exception {
        compoundTagClass().getMethod("putLong", String.class, long.class).invoke(tag, key, value);
    }

    private static void putFloat(Object tag, String key, float value) throws Exception {
        compoundTagClass().getMethod("putFloat", String.class, float.class).invoke(tag, key, value);
    }

    private static Class<?> compoundTagClass() throws Exception {
        return LOADER.loadClass("net.minecraft.nbt.CompoundTag");
    }

    private static Class<?> rowClass() throws Exception {
        return Class.forName(ROW_CLASS, true, LOADER);
    }

    private static ClassLoader rowCompatibilityLoader() {
        String jarPath = System.getProperty("ubs.rowCompatJar", "");
        if (jarPath.isBlank()) {
            return NeoForgeTestClassLoader.get();
        }
        try {
            URL jarUrl = Path.of(jarPath).toAbsolutePath().toUri().toURL();
            return new UbsJarClassLoader(new URL[]{jarUrl}, NeoForgeTestClassLoader.get());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare the UBS persistence test jar", exception);
        }
    }

    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf(type.asSubclass(Enum.class), name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    record RuntimeDoorState(String moduleType,
                            int moduleStart,
                            boolean assignable,
                            boolean assigned,
                            UUID accountId,
                            String boxNumber,
                            float doorProgress,
                            float currentDoorProgress) {
    }

    private static final class UbsJarClassLoader extends URLClassLoader {
        private UbsJarClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(UBS_PACKAGE)) {
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
