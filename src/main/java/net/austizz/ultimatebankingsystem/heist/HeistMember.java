package net.austizz.ultimatebankingsystem.heist;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class HeistMember {
    private final UUID playerId;
    private String lastKnownName;
    private boolean accepted;
    private boolean ready;
    private boolean active = true;
    private boolean dead;
    private boolean devInsiderBypass;
    private long disconnectedAtMillis;
    private long scoreCents;
    private final LinkedHashSet<UUID> bagIds = new LinkedHashSet<>();

    public HeistMember(UUID playerId, String lastKnownName, boolean accepted) {
        this.playerId = playerId;
        this.lastKnownName = cleanName(lastKnownName);
        this.accepted = accepted;
    }

    public UUID playerId() { return playerId; }
    public String lastKnownName() { return lastKnownName; }
    public boolean accepted() { return accepted; }
    public boolean ready() { return ready; }
    public boolean active() { return active; }
    public boolean dead() { return dead; }
    public boolean devInsiderBypass() { return devInsiderBypass; }
    public long disconnectedAtMillis() { return disconnectedAtMillis; }
    public long scoreCents() { return scoreCents; }
    public Set<UUID> bagIds() { return Set.copyOf(bagIds); }

    public void setLastKnownName(String value) { lastKnownName = cleanName(value); }
    public void setAccepted(boolean value) { accepted = value; }
    public void setReady(boolean value) { ready = value; }
    public void setActive(boolean value) { active = value; }
    public void setDead(boolean value) { dead = value; }
    public void setDevInsiderBypass(boolean value) { devInsiderBypass = value; }
    public void setDisconnectedAtMillis(long value) { disconnectedAtMillis = Math.max(0L, value); }
    public void addScore(long cents) { scoreCents = Math.max(0L, scoreCents + Math.max(0L, cents)); }
    public void addBag(UUID bagId) { if (bagId != null) bagIds.add(bagId); }
    public void removeBag(UUID bagId) { bagIds.remove(bagId); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", playerId);
        tag.putString("name", lastKnownName);
        tag.putBoolean("accepted", accepted);
        tag.putBoolean("ready", ready);
        tag.putBoolean("active", active);
        tag.putBoolean("dead", dead);
        tag.putBoolean("dev_insider_bypass", devInsiderBypass);
        tag.putLong("disconnected_at", disconnectedAtMillis);
        tag.putLong("score_cents", scoreCents);
        ListTag bags = new ListTag();
        for (UUID bagId : bagIds) {
            CompoundTag bag = new CompoundTag();
            bag.putUUID("id", bagId);
            bags.add(bag);
        }
        tag.put("bags", bags);
        return tag;
    }

    public static HeistMember load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("player")) {
            return null;
        }
        HeistMember member = new HeistMember(tag.getUUID("player"), tag.getString("name"),
                tag.getBoolean("accepted"));
        member.ready = tag.getBoolean("ready");
        member.active = !tag.contains("active") || tag.getBoolean("active");
        member.dead = tag.getBoolean("dead");
        member.devInsiderBypass = tag.getBoolean("dev_insider_bypass");
        member.disconnectedAtMillis = Math.max(0L, tag.getLong("disconnected_at"));
        member.scoreCents = Math.max(0L, tag.getLong("score_cents"));
        ListTag bags = tag.getList("bags", Tag.TAG_COMPOUND);
        for (int i = 0; i < bags.size(); i++) {
            CompoundTag bag = bags.getCompound(i);
            if (bag.hasUUID("id")) {
                member.bagIds.add(bag.getUUID("id"));
            }
        }
        return member;
    }

    private static String cleanName(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= 48 ? safe : safe.substring(0, 48);
    }
}
