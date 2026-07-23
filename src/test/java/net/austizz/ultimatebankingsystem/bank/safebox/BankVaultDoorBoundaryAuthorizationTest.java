package net.austizz.ultimatebankingsystem.bank.safebox;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"rawtypes", "unchecked"})
class BankVaultDoorBoundaryAuthorizationTest {
    private static final UUID CLAIMED_BANK = UUID.fromString("10000000-0000-0000-0000-000000000123");
    private static final ClaimBounds CLAIMED_INSIDE_SLICE =
            new ClaimBounds(8, 64, 11, 12, 67, 13);

    @Test
    void masterOutsidePartInsidePlacementRequiresLegacyManagementForIntersectedClaim() throws Exception {
        Object master = blockPos(10, 64, 10);
        Object placementState = vaultDoorState("NORTH", 2, 0, 0);
        List<Object> affectedPositions = affectedVaultDoorPositions(placementState, master);

        assertEquals(80, affectedPositions.size(), "the placement proof must cover the full 5x4x4 door");
        assertTrue(claimsAt(master, CLAIMED_INSIDE_SLICE).isEmpty(), "master starts outside the bank claim");
        assertTrue(affectedPositions.stream().anyMatch(pos -> !claimsAt(pos, CLAIMED_INSIDE_SLICE).isEmpty()),
                "secondary door parts intersect the bank claim; affected bounds=" + describeBounds(affectedPositions));

        assertFalse(BankSafeAreaMutationAuthorization.mayModifyAll(
                affectedPositions,
                claimsInside(CLAIMED_INSIDE_SLICE),
                bankId -> false
        ), "placing any part inside a bank claim must require legacy management authorization");
    }

    @Test
    void outsidePartTeardownRequiresLegacyManagementBeforeRemovingClaimedInsideParts() throws Exception {
        Object outsidePart = blockPos(8, 64, 10);
        Object outsidePartState = vaultDoorState("NORTH", 0, 0, 0);
        List<Object> teardownPositions = affectedVaultDoorPositions(outsidePartState, outsidePart);

        assertEquals(80, teardownPositions.size(), "the teardown proof must cover the full 5x4x4 door");
        assertTrue(claimsAt(outsidePart, CLAIMED_INSIDE_SLICE).isEmpty(), "broken outside part starts outside the claim");
        assertTrue(teardownPositions.stream().anyMatch(pos -> !claimsAt(pos, CLAIMED_INSIDE_SLICE).isEmpty()),
                "teardown would otherwise remove parts inside the claim; affected bounds=" + describeBounds(teardownPositions));

        assertFalse(BankSafeAreaMutationAuthorization.mayModifyAll(
                teardownPositions,
                claimsInside(CLAIMED_INSIDE_SLICE),
                bankId -> false
        ), "breaking an outside part must not cascade into claimed parts without legacy management authorization");
    }

    @Test
    void unclaimedVaultDoorsAndLegacyManagersRetainExistingBehavior() throws Exception {
        Object master = blockPos(10, 64, 10);
        Object placementState = vaultDoorState("NORTH", 2, 0, 0);
        List<Object> affectedPositions = affectedVaultDoorPositions(placementState, master);

        assertTrue(BankSafeAreaMutationAuthorization.mayModifyAll(
                affectedPositions,
                pos -> List.of(),
                bankId -> false
        ), "unclaimed structures must keep existing placement and teardown behavior");
        assertTrue(BankSafeAreaMutationAuthorization.mayModifyAll(
                affectedPositions,
                claimsInside(CLAIMED_INSIDE_SLICE),
                bankId -> true
        ), "legacy managers, including the existing level-3 admin path, must retain access");
    }

    private static List<Object> affectedVaultDoorPositions(Object state, Object pos) throws Exception {
        Method method = eventsClass().getDeclaredMethod("affectedPositionsFor", blockStateClass(), blockPosClass());
        method.setAccessible(true);
        return ((Collection<Object>) method.invoke(null, state, pos)).stream().toList();
    }

    private static Object vaultDoorState(String facing, int partX, int partY, int partD) throws Exception {
        Class<?> doorClass = doorBlockClass();
        Object door = doorClass.getConstructor(propertiesClass()).newInstance(propertiesClass().getMethod("of").invoke(null));
        Object state = doorClass.getMethod("defaultBlockState").invoke(door);
        state = setValue(state, doorClass.getField("FACING").get(null), direction(facing));
        state = setValue(state, doorClass.getField("PART_X").get(null), partX);
        state = setValue(state, doorClass.getField("PART_Y").get(null), partY);
        return setValue(state, doorClass.getField("PART_D").get(null), partD);
    }

    private static Object setValue(Object state, Object property, Object value) throws Exception {
        return stateHolderClass()
                .getMethod("setValue", propertyClass(), Comparable.class)
                .invoke(state, property, value);
    }

    private static Function<Object, Collection<UUID>> claimsInside(ClaimBounds bounds) {
        return pos -> claimsAt(pos, bounds);
    }

    private static Collection<UUID> claimsAt(Object pos, ClaimBounds bounds) {
        if (bounds.contains(x(pos), y(pos), z(pos))) {
            return List.of(CLAIMED_BANK);
        }
        return List.of();
    }

    private static String describeBounds(List<Object> positions) {
        int minX = positions.stream().mapToInt(BankVaultDoorBoundaryAuthorizationTest::x).min().orElse(0);
        int minY = positions.stream().mapToInt(BankVaultDoorBoundaryAuthorizationTest::y).min().orElse(0);
        int minZ = positions.stream().mapToInt(BankVaultDoorBoundaryAuthorizationTest::z).min().orElse(0);
        int maxX = positions.stream().mapToInt(BankVaultDoorBoundaryAuthorizationTest::x).max().orElse(0);
        int maxY = positions.stream().mapToInt(BankVaultDoorBoundaryAuthorizationTest::y).max().orElse(0);
        int maxZ = positions.stream().mapToInt(BankVaultDoorBoundaryAuthorizationTest::z).max().orElse(0);
        return "[" + minX + "," + minY + "," + minZ + "]-[" + maxX + "," + maxY + "," + maxZ + "]";
    }

    private static Object direction(String name) throws Exception {
        return Enum.valueOf((Class<Enum>) directionClass().asSubclass(Enum.class), name);
    }

    private static Object blockPos(int x, int y, int z) throws Exception {
        return blockPosClass().getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
    }

    private static int x(Object pos) {
        return coordinate(pos, "getX");
    }

    private static int y(Object pos) {
        return coordinate(pos, "getY");
    }

    private static int z(Object pos) {
        return coordinate(pos, "getZ");
    }

    private static int coordinate(Object pos, String method) {
        try {
            return (int) pos.getClass().getMethod(method).invoke(pos);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to read BlockPos coordinate via " + method, exception);
        }
    }

    private static Class<?> eventsClass() throws Exception {
        return Class.forName("net.austizz.ultimatebankingsystem.bank.safebox.BankSafeAreaEvents", true,
                serverClassLoader());
    }

    private static Class<?> doorBlockClass() throws Exception {
        ensureMinecraftRuntimeMetadata();
        return Class.forName("net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock", true,
                serverClassLoader());
    }

    private static Class<?> blockPosClass() throws Exception {
        return Class.forName("net.minecraft.core.BlockPos", true, serverClassLoader());
    }

    private static Class<?> directionClass() throws Exception {
        return Class.forName("net.minecraft.core.Direction", true, serverClassLoader());
    }

    private static Class<?> blockStateClass() throws Exception {
        return Class.forName("net.minecraft.world.level.block.state.BlockState", true, serverClassLoader());
    }

    private static Class<?> propertiesClass() throws Exception {
        return Class.forName("net.minecraft.world.level.block.state.BlockBehaviour$Properties", true,
                serverClassLoader());
    }

    private static Class<?> propertyClass() throws Exception {
        return Class.forName("net.minecraft.world.level.block.state.properties.Property", true, serverClassLoader());
    }

    private static Class<?> stateHolderClass() throws Exception {
        return Class.forName("net.minecraft.world.level.block.state.StateHolder", true, serverClassLoader());
    }

    private static void ensureMinecraftRuntimeMetadata() throws Exception {
        ClassLoader loader = serverClassLoader();
        Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList", true, loader);
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
        allowTestBlockConstruction(loader);
    }

    private static void allowTestBlockConstruction(ClassLoader loader) throws Exception {
        Object blockRegistry = Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, loader)
                .getField("BLOCK")
                .get(null);
        blockRegistry.getClass().getMethod("unfreeze").invoke(blockRegistry);
        Class<?> mappedRegistry = Class.forName("net.minecraft.core.MappedRegistry", true, loader);
        java.lang.reflect.Field intrusiveHolders = mappedRegistry.getDeclaredField("unregisteredIntrusiveHolders");
        intrusiveHolders.setAccessible(true);
        if (intrusiveHolders.get(blockRegistry) == null) {
            intrusiveHolders.set(blockRegistry, new IdentityHashMap<>());
        }
    }

    private static ClassLoader serverClassLoader() {
        return ServerClassLoaderHolder.INSTANCE;
    }

    private record ClaimBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    private static final class ServerClassLoaderHolder {
        private static final ClassLoader INSTANCE = new ChildFirstUrlClassLoader(serverClasspathUrls(),
                BankVaultDoorBoundaryAuthorizationTest.class.getClassLoader());

        private static URL[] serverClasspathUrls() {
            List<URL> urls = new ArrayList<>();
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
                    throw new IllegalStateException("Unable to read server-gate test classpath: " + legacyClasspath,
                            exception);
                }
            }
            addUrl(urls, Path.of("build", "moddev", "artifacts", "neoforge-21.1.220.jar"));
            return urls.toArray(URL[]::new);
        }

        private static void addUrl(List<URL> urls, Path path) {
            try {
                urls.add(path.toAbsolutePath().toUri().toURL());
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to prepare server-gate test classpath: " + path, exception);
            }
        }
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("[")) {
                Class<?> loaded = Class.forName(name, false, this);
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
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
