package net.austizz.ultimatebankingsystem.api.bank;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiBankStaffingSnapshot(UUID bankId,
                                      List<ApiBankEmployeeSnapshot> employees,
                                      List<ApiBankTellerSnapshot> tellers) {
    public ApiBankStaffingSnapshot {
        employees = employees == null ? List.of() : List.copyOf(employees);
        tellers = tellers == null ? List.of() : List.copyOf(tellers);
    }
}
