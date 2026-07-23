package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ViewingSessionSavedData extends SavedData {
    private static final String DATA_NAME = "ubs_safe_box_viewing_sessions";
    private final Map<UUID, Journal> journals = new LinkedHashMap<>();
    private final Map<UUID, DeferredReturn> deferredReturns = new LinkedHashMap<>();

    public static ViewingSessionSavedData get(MinecraftServer server) {
        ServerLevel overworld = server == null ? null : server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new ViewingSessionSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<ViewingSessionSavedData> factory() {
        return new SavedData.Factory<>(ViewingSessionSavedData::new, ViewingSessionSavedData::load, null);
    }

    public synchronized void put(Journal journal) {
        if (journal != null) {
            journals.put(journal.sessionId(), journal);
            setDirty();
        }
    }

    public synchronized void remove(UUID sessionId) {
        if (sessionId != null && journals.remove(sessionId) != null) {
            setDirty();
        }
    }

    public synchronized List<Journal> journals() {
        return List.copyOf(journals.values());
    }

    public synchronized void deferReturn(UUID playerId,
                                         ViewingRoomAnchor anchor,
                                         boolean wasInvulnerable) {
        if (playerId != null && anchor != null) {
            deferredReturns.put(playerId, new DeferredReturn(anchor, wasInvulnerable));
            setDirty();
        }
    }

    public synchronized DeferredReturn takeDeferredReturn(UUID playerId) {
        DeferredReturn deferred = playerId == null ? null : deferredReturns.remove(playerId);
        if (deferred != null) {
            setDirty();
        }
        return deferred;
    }

    private static ViewingSessionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ViewingSessionSavedData data = new ViewingSessionSavedData();
        ListTag journals = tag.getList("journals", Tag.TAG_COMPOUND);
        for (int index = 0; index < journals.size(); index++) {
            Journal journal = readJournal(journals.getCompound(index));
            if (journal != null) {
                data.journals.put(journal.sessionId(), journal);
            }
        }
        ListTag returns = tag.getList("deferredReturns", Tag.TAG_COMPOUND);
        for (int index = 0; index < returns.size(); index++) {
            CompoundTag entry = returns.getCompound(index);
            if (entry.hasUUID("playerId")) {
                ViewingRoomAnchor anchor = readAnchor(entry.getCompound("anchor"));
                if (anchor != null) {
                    data.deferredReturns.put(entry.getUUID("playerId"),
                            new DeferredReturn(anchor, entry.getBoolean("wasInvulnerable")));
                }
            }
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag serializedJournals = new ListTag();
        journals.values().forEach(journal -> serializedJournals.add(writeJournal(journal)));
        tag.put("journals", serializedJournals);
        ListTag returns = new ListTag();
        deferredReturns.forEach((playerId, deferred) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("playerId", playerId);
            entry.put("anchor", writeAnchor(deferred.anchor()));
            entry.putBoolean("wasInvulnerable", deferred.wasInvulnerable());
            returns.add(entry);
        });
        tag.put("deferredReturns", returns);
        return tag;
    }

    private static CompoundTag writeJournal(Journal journal) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("sessionId", journal.sessionId());
        tag.putUUID("bankId", journal.bankId());
        tag.putUUID("roomId", journal.roomId());
        tag.putUUID("playerId", journal.playerId());
        tag.putUUID("tellerId", journal.tellerId());
        tag.putUUID("accountId", journal.accountId());
        tag.putString("premiseId", journal.premiseId());
        tag.putString("sourceDimension", journal.sourceDimension());
        tag.putLong("sourcePos", journal.sourcePos().asLong());
        tag.putInt("doorIndex", journal.doorIndex());
        tag.put("playerReturn", writeAnchor(journal.playerReturn()));
        tag.put("tellerReturn", writeAnchor(journal.tellerReturn()));
        tag.putBoolean("playerWasInvulnerable", journal.playerWasInvulnerable());
        return tag;
    }

    private static Journal readJournal(CompoundTag tag) {
        if (!tag.hasUUID("sessionId") || !tag.hasUUID("bankId") || !tag.hasUUID("roomId")
                || !tag.hasUUID("playerId") || !tag.hasUUID("tellerId") || !tag.hasUUID("accountId")
                || !tag.contains("sourcePos", Tag.TAG_LONG)) {
            return null;
        }
        ViewingRoomAnchor playerReturn = readAnchor(tag.getCompound("playerReturn"));
        ViewingRoomAnchor tellerReturn = readAnchor(tag.getCompound("tellerReturn"));
        if (playerReturn == null || tellerReturn == null) {
            return null;
        }
        return new Journal(tag.getUUID("sessionId"), tag.getUUID("bankId"), tag.getUUID("roomId"),
                tag.getUUID("playerId"), tag.getUUID("tellerId"), tag.getUUID("accountId"),
                tag.getString("premiseId"), tag.getString("sourceDimension"),
                BlockPos.of(tag.getLong("sourcePos")), tag.getInt("doorIndex"),
                playerReturn, tellerReturn, tag.getBoolean("playerWasInvulnerable"));
    }

    private static CompoundTag writeAnchor(ViewingRoomAnchor anchor) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", anchor.dimension());
        tag.putDouble("x", anchor.x());
        tag.putDouble("y", anchor.y());
        tag.putDouble("z", anchor.z());
        tag.putFloat("yaw", anchor.yaw());
        tag.putFloat("pitch", anchor.pitch());
        return tag;
    }

    private static ViewingRoomAnchor readAnchor(CompoundTag tag) {
        if (tag == null || tag.getString("dimension").isBlank()) {
            return null;
        }
        try {
            return new ViewingRoomAnchor(tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"),
                    tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record Journal(UUID sessionId,
                          UUID bankId,
                          UUID roomId,
                          UUID playerId,
                          UUID tellerId,
                          UUID accountId,
                          String premiseId,
                          String sourceDimension,
                          BlockPos sourcePos,
                          int doorIndex,
                          ViewingRoomAnchor playerReturn,
                          ViewingRoomAnchor tellerReturn,
                          boolean playerWasInvulnerable) {
    }

    public record DeferredReturn(ViewingRoomAnchor anchor, boolean wasInvulnerable) {
    }
}
