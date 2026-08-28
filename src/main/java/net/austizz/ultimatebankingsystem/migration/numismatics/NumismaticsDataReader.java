package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Strict, dependency-free reader for Create: Numismatics bank SavedData. */
public final class NumismaticsDataReader {
    public static final long MAX_SOURCE_BYTES = 32L * 1024L * 1024L;
    public static final int MAX_ACCOUNTS = 1_000_000;

    private NumismaticsDataReader() {
    }

    public static NumismaticsSourceSnapshot read(Path path) throws IOException {
        if (path == null) {
            throw new IOException("No Numismatics source file was selected.");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IOException("Numismatics source file does not exist: " + normalized);
        }
        long size = Files.size(normalized);
        if (size <= 0L || size > MAX_SOURCE_BYTES) {
            throw new IOException("Numismatics source must be between 1 byte and 32 MiB.");
        }

        byte[] bytes = Files.readAllBytes(normalized);
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(normalized, NbtAccounter.create(MAX_SOURCE_BYTES * 16L));
        } catch (RuntimeException exception) {
            throw new IOException("The selected file is not valid compressed Minecraft NBT.", exception);
        }
        CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
        if (!data.contains("Accounts", Tag.TAG_LIST)) {
            throw new IOException("The selected file does not contain a Numismatics Accounts list.");
        }
        ListTag accountTags = data.getList("Accounts", Tag.TAG_COMPOUND);
        if (accountTags.size() > MAX_ACCOUNTS) {
            throw new IOException("Numismatics source exceeds the supported account limit.");
        }

        List<NumismaticsAccountRecord> accounts = new ArrayList<>(accountTags.size());
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        long totalSpurs = 0L;
        for (int index = 0; index < accountTags.size(); index++) {
            CompoundTag accountTag = accountTags.getCompound(index);
            if (!accountTag.hasUUID("id")) {
                throw new IOException("Numismatics account " + index + " has no UUID.");
            }
            UUID id = accountTag.getUUID("id");
            if (!ids.add(id)) {
                throw new IOException("Duplicate Numismatics account UUID: " + id);
            }
            int balance = accountTag.getInt("balance");
            if (balance < 0) {
                throw new IOException("Numismatics account " + id + " has a negative balance.");
            }
            totalSpurs = Math.addExact(totalSpurs, balance);

            String rawType = accountTag.getString("AccountType").trim().toUpperCase(Locale.ROOT);
            NumismaticsAccountRecord.AccountKind kind = "BLAZE_BANKER".equals(rawType)
                    ? NumismaticsAccountRecord.AccountKind.BLAZE_BANKER
                    : NumismaticsAccountRecord.AccountKind.PLAYER;
            List<UUID> trust = readTrustList(accountTag);
            accounts.add(new NumismaticsAccountRecord(
                    id,
                    kind,
                    balance,
                    accountTag.getString("Label"),
                    trust
            ));
        }
        return new NumismaticsSourceSnapshot(normalized, sha256(bytes), accounts, totalSpurs);
    }

    private static List<UUID> readTrustList(CompoundTag accountTag) {
        if (!accountTag.contains("TrustList", Tag.TAG_LIST)) {
            return List.of();
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        ListTag list = accountTag.getList("TrustList", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("UUID")) {
                unique.add(entry.getUUID("UUID"));
            }
        }
        return List.copyOf(unique);
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable.", impossible);
        }
    }
}
