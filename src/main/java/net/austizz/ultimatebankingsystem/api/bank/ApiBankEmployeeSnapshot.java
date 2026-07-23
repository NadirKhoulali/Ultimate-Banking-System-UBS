package net.austizz.ultimatebankingsystem.api.bank;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiBankEmployeeSnapshot(UUID playerId,
                                      String name,
                                      String role,
                                      BigDecimal salary,
                                      boolean online,
                                      boolean safeAccess) {
    public ApiBankEmployeeSnapshot {
        name = name == null ? "" : name;
        role = role == null ? "" : role;
        salary = salary == null ? BigDecimal.ZERO : salary;
    }
}
