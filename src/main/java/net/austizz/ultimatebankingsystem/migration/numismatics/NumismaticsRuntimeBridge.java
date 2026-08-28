package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Optional reflection bridge used only while Create: Numismatics is installed. */
public final class NumismaticsRuntimeBridge {
    private NumismaticsRuntimeBridge() {
    }

    public static boolean consumeLiveBalances(Iterable<UUID> accountIds) {
        try {
            Class<?> rootClass = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
            Field bankField = rootClass.getField("BANK");
            Object manager = bankField.get(null);
            Field accountsField = manager.getClass().getField("accounts");
            Object raw = accountsField.get(manager);
            if (!(raw instanceof Map<?, ?> accounts)) return false;
            for (UUID accountId : accountIds) {
                Object account = accounts.get(accountId);
                if (account == null) continue;
                Method setBalance = account.getClass().getMethod("setBalance", int.class);
                setBalance.invoke(account, 0);
            }
            manager.getClass().getMethod("markBankDirty").invoke(manager);
            return true;
        } catch (ClassNotFoundException notInstalled) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException error) {
            UltimateBankingSystem.LOGGER.error("Could not consume live Numismatics balances", error);
            return false;
        }
    }

    public static void writeLiveSnapshot(Path destination) throws IOException {
        Path normalized = destination.toAbsolutePath().normalize();
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".ubs.tmp");
        try {
            Class<?> rootClass = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
            Object manager = rootClass.getField("BANK").get(null);
            Object raw = manager.getClass().getField("accounts").get(manager);
            if (!(raw instanceof Map<?, ?> accounts)) {
                throw new IOException("Numismatics live bank data is unavailable.");
            }

            ListTag accountTags = new ListTag();
            for (Object account : accounts.values()) {
                if (account == null) continue;
                Object saved = account.getClass().getMethod("save", CompoundTag.class)
                        .invoke(account, new CompoundTag());
                if (saved instanceof CompoundTag tag) accountTags.add(tag);
            }

            CompoundTag data = new CompoundTag();
            data.put("Accounts", accountTags);
            CompoundTag root = new CompoundTag();
            root.put("data", data);
            Files.createDirectories(normalized.getParent());
            NbtIo.writeCompressed(root, temporary);
            try {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (ClassNotFoundException notInstalled) {
            throw new IOException("Create: Numismatics is not loaded in this server.", notInstalled);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IOException("Could not snapshot Numismatics live bank data.", error);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
