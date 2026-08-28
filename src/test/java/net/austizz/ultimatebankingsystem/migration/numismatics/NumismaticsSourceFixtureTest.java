package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("unchecked")
class NumismaticsSourceFixtureTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsOfficialWrappedSavedDataShapeAndTrustList() throws Exception {
        Class<?> compoundType = load("net.minecraft.nbt.CompoundTag");
        Class<?> tagType = load("net.minecraft.nbt.Tag");
        Object root = compoundType.getConstructor().newInstance();
        Object data = compoundType.getConstructor().newInstance();
        Object accounts = load("net.minecraft.nbt.ListTag").getConstructor().newInstance();

        UUID playerId = UUID.randomUUID();
        Object player = account(compoundType, playerId, "PLAYER", 25, "");
        ((List<Object>) accounts).add(player);

        UUID sharedId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Object shared = account(compoundType, sharedId, "BLAZE_BANKER", 4096, "Town Treasury");
        Object trust = load("net.minecraft.nbt.ListTag").getConstructor().newInstance();
        Object trustRow = compoundType.getConstructor().newInstance();
        compoundType.getMethod("putUUID", String.class, UUID.class).invoke(trustRow, "UUID", owner);
        ((List<Object>) trust).add(trustRow);
        compoundType.getMethod("put", String.class, tagType).invoke(shared, "TrustList", trust);
        ((List<Object>) accounts).add(shared);

        compoundType.getMethod("put", String.class, tagType).invoke(data, "Accounts", accounts);
        compoundType.getMethod("put", String.class, tagType).invoke(root, "data", data);
        Path source = temporaryDirectory.resolve("numismatics_bank.dat");
        load("net.minecraft.nbt.NbtIo").getMethod("writeCompressed", compoundType, Path.class)
                .invoke(null, root, source);

        Object snapshot = load("net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsDataReader")
                .getMethod("read", Path.class).invoke(null, source);
        assertEquals(2, ((List<?>) snapshot.getClass().getMethod("accounts").invoke(snapshot)).size());
        assertEquals(4121L, snapshot.getClass().getMethod("totalSpurs").invoke(snapshot));
        Object sharedRecord = ((List<?>) snapshot.getClass().getMethod("accounts").invoke(snapshot)).get(1);
        assertEquals("BLAZE_BANKER", sharedRecord.getClass().getMethod("kind").invoke(sharedRecord).toString());
        assertEquals(List.of(owner), sharedRecord.getClass().getMethod("trustedPlayers").invoke(sharedRecord));
    }

    @Test
    void scannerCountsOfficialCoinIdsAtExactRatios() throws Exception {
        Class<?> compoundType = load("net.minecraft.nbt.CompoundTag");
        Object stack = compoundType.getConstructor().newInstance();
        compoundType.getMethod("putString", String.class, String.class).invoke(stack, "id", "numismatics:sun");
        compoundType.getMethod("putInt", String.class, int.class).invoke(stack, "count", 3);
        Class<?> scanner = load("net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsWorldScanner");
        Object counts = scanner.getMethod("inspect", load("net.minecraft.nbt.Tag")).invoke(null, stack);
        assertEquals(3L, counts.getClass().getMethod("coinItems").invoke(counts));
        assertEquals(12_288L, counts.getClass().getMethod("physicalSpurs").invoke(counts));
    }

    private static Object account(Class<?> compoundType, UUID id, String type, int balance, String label)
            throws Exception {
        Object account = compoundType.getConstructor().newInstance();
        compoundType.getMethod("putUUID", String.class, UUID.class).invoke(account, "id", id);
        compoundType.getMethod("putString", String.class, String.class).invoke(account, "AccountType", type);
        compoundType.getMethod("putInt", String.class, int.class).invoke(account, "balance", balance);
        if (!label.isBlank()) compoundType.getMethod("putString", String.class, String.class)
                .invoke(account, "Label", label);
        return account;
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }
}
