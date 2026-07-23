package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HeistSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_banking_system_heists";
    private static final int MAX_AUDIT_ENTRIES = 256;
    private final LinkedHashMap<UUID, HeistSession> sessions = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Long> bankCooldowns = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Long> playerCooldowns = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Long> victimProtection = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, List<CompoundTag>> deferredItems = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, List<String>> deferredMessages = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, SafeExitSnapshot> deferredExits = new LinkedHashMap<>();
    private final ArrayDeque<CompoundTag> audit = new ArrayDeque<>();

    public static HeistSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<HeistSavedData> factory() {
        return new SavedData.Factory<>(HeistSavedData::new, HeistSavedData::load, null);
    }

    public Collection<HeistSession> sessions() { return List.copyOf(sessions.values()); }
    public HeistSession session(UUID id) { return sessions.get(id); }
    public void put(HeistSession session) { if (session != null) { sessions.put(session.id(), session); setDirty(); } }
    public void remove(UUID id) { if (sessions.remove(id) != null) setDirty(); }
    public long bankCooldown(UUID id) { return bankCooldowns.getOrDefault(id, 0L); }
    public long playerCooldown(UUID id) { return playerCooldowns.getOrDefault(id, 0L); }
    public long victimProtectedUntil(UUID id) { return victimProtection.getOrDefault(id, 0L); }
    public void setBankCooldown(UUID id, long until) { putExpiry(bankCooldowns, id, until); }
    public void setPlayerCooldown(UUID id, long until) { putExpiry(playerCooldowns, id, until); }
    public void setVictimProtection(UUID id, long until) { putExpiry(victimProtection, id, until); }
    public Map<UUID, Long> bankCooldowns() { return Map.copyOf(bankCooldowns); }
    public Map<UUID, Long> playerCooldowns() { return Map.copyOf(playerCooldowns); }
    public Map<UUID, Long> victimProtection() { return Map.copyOf(victimProtection); }

    public void deferItem(UUID playerId, CompoundTag stackTag) {
        if (playerId == null || stackTag == null || stackTag.isEmpty()) return;
        deferredItems.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(stackTag.copy());
        setDirty();
    }

    public List<CompoundTag> takeDeferredItems(UUID playerId) {
        List<CompoundTag> values = deferredItems.remove(playerId);
        if (values == null) return List.of();
        setDirty();
        return values.stream().map(CompoundTag::copy).toList();
    }

    public void deferMessage(UUID playerId, String message) {
        String safe = trim(message, 512);
        if (playerId == null || safe.isEmpty()) return;
        deferredMessages.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(safe);
        setDirty();
    }

    public List<String> takeDeferredMessages(UUID playerId) {
        List<String> values = deferredMessages.remove(playerId);
        if (values == null) return List.of();
        setDirty();
        return List.copyOf(values);
    }

    public void deferExit(UUID playerId, SafeExitSnapshot exit) {
        if (playerId == null || exit == null) return;
        deferredExits.put(playerId, exit);
        setDirty();
    }

    public SafeExitSnapshot takeDeferredExit(UUID playerId) {
        SafeExitSnapshot exit = deferredExits.remove(playerId);
        if (exit != null) setDirty();
        return exit;
    }

    public void audit(UUID sessionId, String action, String details) {
        CompoundTag entry = new CompoundTag();
        if (sessionId != null) entry.putUUID("session", sessionId);
        entry.putLong("time", System.currentTimeMillis());
        entry.putString("action", trim(action, 64));
        entry.putString("details", trim(details, 1024));
        audit.addLast(entry);
        while (audit.size() > MAX_AUDIT_ENTRIES) audit.removeFirst();
        setDirty();
    }

    public List<CompoundTag> auditEntries() { return audit.stream().map(CompoundTag::copy).toList(); }

    public void prune(long now) {
        boolean changed = bankCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        changed |= playerCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        changed |= victimProtection.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (changed) setDirty();
    }

    private void putExpiry(Map<UUID, Long> target, UUID id, long until) {
        if (id == null) return;
        if (until <= 0L) target.remove(id); else target.put(id, until);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag sessionList = new ListTag();
        sessions.values().forEach(session -> sessionList.add(session.save()));
        tag.put("sessions", sessionList);
        tag.put("bank_cooldowns", saveExpiries(bankCooldowns));
        tag.put("player_cooldowns", saveExpiries(playerCooldowns));
        tag.put("victim_protection", saveExpiries(victimProtection));
        ListTag pending = new ListTag();
        deferredItems.forEach((player, stacks) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("player", player);
            ListTag items = new ListTag(); stacks.forEach(stack -> items.add(stack.copy()));
            entry.put("items", items); pending.add(entry);
        });
        tag.put("deferred_items", pending);
        ListTag messages = new ListTag();
        deferredMessages.forEach((player, values) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            ListTag rows = new ListTag();
            values.forEach(value -> {
                CompoundTag row = new CompoundTag();
                row.putString("message", trim(value, 512));
                rows.add(row);
            });
            entry.put("messages", rows);
            messages.add(entry);
        });
        tag.put("deferred_messages", messages);
        ListTag exits = new ListTag();
        deferredExits.forEach((player, exit) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putString("dimension", exit.dimension());
            entry.putInt("x", exit.x());
            entry.putInt("y", exit.y());
            entry.putInt("z", exit.z());
            entry.putFloat("yaw", exit.yaw());
            exits.add(entry);
        });
        tag.put("deferred_exits", exits);
        ListTag auditList = new ListTag(); audit.forEach(entry -> auditList.add(entry.copy()));
        tag.put("audit", auditList);
        return tag;
    }

    public static HeistSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HeistSavedData data = new HeistSavedData();
        ListTag sessions = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < sessions.size(); i++) {
            HeistSession session = HeistSession.load(sessions.getCompound(i));
            if (session != null) data.sessions.put(session.id(), session);
        }
        loadExpiries(tag.getList("bank_cooldowns", Tag.TAG_COMPOUND), data.bankCooldowns);
        loadExpiries(tag.getList("player_cooldowns", Tag.TAG_COMPOUND), data.playerCooldowns);
        loadExpiries(tag.getList("victim_protection", Tag.TAG_COMPOUND), data.victimProtection);
        ListTag pending = tag.getList("deferred_items", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (!entry.hasUUID("player")) continue;
            List<CompoundTag> items = new ArrayList<>();
            ListTag stacks = entry.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < stacks.size(); j++) items.add(stacks.getCompound(j).copy());
            if (!items.isEmpty()) data.deferredItems.put(entry.getUUID("player"), items);
        }
        ListTag messages = tag.getList("deferred_messages", Tag.TAG_COMPOUND);
        for (int i = 0; i < messages.size(); i++) {
            CompoundTag entry = messages.getCompound(i);
            if (!entry.hasUUID("player")) continue;
            List<String> values = new ArrayList<>();
            ListTag rows = entry.getList("messages", Tag.TAG_COMPOUND);
            for (int j = 0; j < rows.size(); j++) {
                String value = trim(rows.getCompound(j).getString("message"), 512);
                if (!value.isEmpty()) values.add(value);
            }
            if (!values.isEmpty()) data.deferredMessages.put(entry.getUUID("player"), values);
        }
        ListTag exits = tag.getList("deferred_exits", Tag.TAG_COMPOUND);
        for (int i = 0; i < exits.size(); i++) {
            CompoundTag entry = exits.getCompound(i);
            if (!entry.hasUUID("player") || entry.getString("dimension").isBlank()) continue;
            data.deferredExits.put(entry.getUUID("player"), new SafeExitSnapshot(
                    entry.getString("dimension"), entry.getInt("x"), entry.getInt("y"),
                    entry.getInt("z"), entry.getFloat("yaw")));
        }
        ListTag audit = tag.getList("audit", Tag.TAG_COMPOUND);
        int start = Math.max(0, audit.size() - MAX_AUDIT_ENTRIES);
        for (int i = start; i < audit.size(); i++) data.audit.addLast(audit.getCompound(i).copy());
        return data;
    }

    private static ListTag saveExpiries(Map<UUID, Long> values) {
        ListTag list = new ListTag();
        values.forEach((id, until) -> { CompoundTag entry = new CompoundTag(); entry.putUUID("id", id); entry.putLong("until", until); list.add(entry); });
        return list;
    }

    private static void loadExpiries(ListTag list, Map<UUID, Long> target) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("id") && entry.getLong("until") > 0L) target.put(entry.getUUID("id"), entry.getLong("until"));
        }
    }

    private static String trim(String value, int max) {
        String safe = value == null ? "" : value.trim(); return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
