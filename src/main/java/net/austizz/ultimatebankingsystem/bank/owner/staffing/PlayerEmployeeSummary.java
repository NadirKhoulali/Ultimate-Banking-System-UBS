package net.austizz.ultimatebankingsystem.bank.owner.staffing;

import java.math.BigDecimal;
import java.util.UUID;

public record PlayerEmployeeSummary(UUID playerId,
                                    String resolvedName,
                                    String role,
                                    BigDecimal salary,
                                    boolean online,
                                    boolean safeAccessGranted) {
    public PlayerEmployeeSummary {
        resolvedName = resolvedName == null || resolvedName.isBlank() ? shortId(playerId) : resolvedName.trim();
        role = role == null ? "" : role.trim().toUpperCase(java.util.Locale.ROOT);
        salary = salary == null ? BigDecimal.ZERO : salary;
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }
}
