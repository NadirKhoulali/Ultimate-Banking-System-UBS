package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SafeBoxEscortDoorHolds {
    private final Set<UUID> sessions = new HashSet<>();

    public boolean add(UUID sessionId) {
        return sessionId != null && sessions.add(sessionId);
    }

    public boolean remove(UUID sessionId) {
        return sessionId != null && sessions.remove(sessionId);
    }

    public boolean contains(UUID sessionId) {
        return sessionId != null && sessions.contains(sessionId);
    }

    public boolean active() {
        return !sessions.isEmpty();
    }

    public int count() {
        return sessions.size();
    }

    public Set<UUID> snapshot() {
        return Set.copyOf(sessions);
    }

    public void replaceWith(Iterable<UUID> sessionIds) {
        sessions.clear();
        if (sessionIds == null) {
            return;
        }
        for (UUID sessionId : sessionIds) {
            if (sessionId != null) {
                sessions.add(sessionId);
            }
        }
    }

    public void clear() {
        sessions.clear();
    }
}
