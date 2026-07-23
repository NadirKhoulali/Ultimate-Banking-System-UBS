package net.austizz.ultimatebankingsystem.bank.owner.staffing;

import java.util.List;

public record BankStaffingRoster(List<PlayerEmployeeSummary> playerEmployees,
                                 List<BankTellerSummary> bankTellers) {
    public BankStaffingRoster {
        playerEmployees = playerEmployees == null ? List.of() : List.copyOf(playerEmployees);
        bankTellers = bankTellers == null ? List.of() : List.copyOf(bankTellers);
    }
}
