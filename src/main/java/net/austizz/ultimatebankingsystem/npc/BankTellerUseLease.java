package net.austizz.ultimatebankingsystem.npc;

import java.util.UUID;

public final class BankTellerUseLease {
    private final long durationTicks;
    private UUID holder;
    private long expiresAtTick;

    public BankTellerUseLease(long durationTicks) {
        this.durationTicks = Math.max(1L, durationTicks);
    }

    public synchronized boolean acquire(UUID playerId, long nowTick) {
        if (playerId == null) {
            return false;
        }
        expire(nowTick);
        if (holder != null && !holder.equals(playerId)) {
            return false;
        }
        holder = playerId;
        expiresAtTick = deadline(nowTick);
        return true;
    }

    public synchronized boolean refresh(UUID playerId, long nowTick) {
        expire(nowTick);
        if (playerId == null || !playerId.equals(holder)) {
            return false;
        }
        expiresAtTick = deadline(nowTick);
        return true;
    }

    public synchronized boolean release(UUID playerId) {
        if (playerId == null || !playerId.equals(holder)) {
            return false;
        }
        clear();
        return true;
    }

    public synchronized UUID holder(long nowTick) {
        expire(nowTick);
        return holder;
    }

    public synchronized void clear() {
        holder = null;
        expiresAtTick = 0L;
    }

    private void expire(long nowTick) {
        if (holder != null && nowTick >= expiresAtTick) {
            clear();
        }
    }

    private long deadline(long nowTick) {
        return nowTick > Long.MAX_VALUE - durationTicks
                ? Long.MAX_VALUE : nowTick + durationTicks;
    }
}
