package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SafeBoxZoneCache {
    static final long REFRESH_TICKS = 20L;
    private static final Map<MinecraftServer, Entry> BY_SERVER = new WeakHashMap<>();

    private SafeBoxZoneCache() {
    }

    public static synchronized SafeBoxZoneIndex index(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       long serverTick) {
        if (server == null || centralBank == null) {
            return new SafeBoxZoneIndex(List.of());
        }
        Entry current = BY_SERVER.get(server);
        if (current == null || current.source() != centralBank
                || shouldRebuild(current.builtAtTick(), serverTick)) {
            current = new Entry(centralBank, serverTick, rebuild(centralBank));
            BY_SERVER.put(server, current);
        }
        return current.index();
    }

    public static synchronized void clear(MinecraftServer server) {
        if (server != null) {
            BY_SERVER.remove(server);
        }
    }

    static boolean shouldRebuild(long builtAtTick, long serverTick) {
        return builtAtTick == Long.MIN_VALUE || serverTick < builtAtTick
                || serverTick - builtAtTick >= REFRESH_TICKS;
    }

    static SafeBoxZoneIndex fromSnapshots(Map<UUID, SafeDepositSetupSnapshot> snapshots) {
        List<SafeBoxZoneRecord> records = new ArrayList<>();
        if (snapshots != null) {
            snapshots.forEach((bankId, setup) -> addSetup(records, bankId, setup));
        }
        return new SafeBoxZoneIndex(records);
    }

    private static SafeBoxZoneIndex rebuild(CentralBank centralBank) {
        List<SafeBoxZoneRecord> records = new ArrayList<>();
        for (Map.Entry<UUID, CompoundTag> bank : centralBank.getBankMetadata().entrySet()) {
            try {
                SafeDepositSetupSnapshot setup = SafeDepositSetupNbtCodec.snapshot(bank.getValue());
                addSetup(records, bank.getKey(), setup);
            } catch (RuntimeException ignored) {
                // One malformed bank must not suppress enforcement for other banks.
            }
        }
        return new SafeBoxZoneIndex(records);
    }

    private static void addSetup(List<SafeBoxZoneRecord> records,
                                 UUID bankId,
                                 SafeDepositSetupSnapshot setup) {
        if (bankId == null || setup == null) {
            return;
        }
        for (SafePremiseSnapshot premise : setup.premises()) {
            add(records, bankId, premise);
        }
    }

    private static void add(List<SafeBoxZoneRecord> records,
                            UUID metadataBankId,
                            SafePremiseSnapshot premise) {
        if (premise == null || premise.id() == null || premise.id().isBlank()
                || premise.bounds() == null || premise.mode() == null
                || !matchesBank(metadataBankId, premise.bankId())) {
            return;
        }
        List<SafeBoxZoneRecord.Area> areas = new ArrayList<>();
        for (SafeAreaSnapshot area : premise.safeAreas()) {
            if (area != null && area.bounds() != null) {
                areas.add(new SafeBoxZoneRecord.Area(area.id(), area.bounds()));
            }
        }
        records.add(new SafeBoxZoneRecord(metadataBankId, premise.id(), premise.mode(),
                premise.bounds(), premise.exit(), areas));
    }

    private static boolean matchesBank(UUID metadataBankId, String snapshotBankId) {
        try {
            return metadataBankId.equals(UUID.fromString(snapshotBankId));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return false;
        }
    }

    private record Entry(CentralBank source, long builtAtTick, SafeBoxZoneIndex index) {
    }
}
