package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;

import java.util.UUID;

public record ViewingRoomSnapshot(UUID id,
                                  String name,
                                  String premiseId,
                                  SafeBlockBounds bounds,
                                  ViewingRoomAnchor customerAnchor,
                                  ViewingRoomAnchor tellerAnchor,
                                  ViewingRoomAnchor displayAnchor,
                                  long createdAtMillis,
                                  long lastUsedAtMillis,
                                  boolean adminSuspended) {
    public ViewingRoomSnapshot {
        if (id == null) {
            throw new IllegalArgumentException("Viewing room id is required.");
        }
        name = normalizeName(name);
        premiseId = premiseId == null ? "" : premiseId.trim();
        createdAtMillis = Math.max(0L, createdAtMillis);
        lastUsedAtMillis = Math.max(0L, lastUsedAtMillis);
    }

    public boolean anchorsComplete() {
        return customerAnchor != null && tellerAnchor != null && displayAnchor != null;
    }

    public boolean anchorsInsideBounds() {
        return bounds != null && anchorsComplete()
                && customerAnchor.inside(bounds)
                && tellerAnchor.inside(bounds)
                && displayAnchor.inside(bounds);
    }

    public static String normalizeName(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return "Viewing Room";
        }
        return value.length() <= 48 ? value : value.substring(0, 48);
    }
}
