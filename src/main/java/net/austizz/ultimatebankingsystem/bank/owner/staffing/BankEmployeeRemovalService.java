package net.austizz.ultimatebankingsystem.bank.owner.staffing;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class BankEmployeeRemovalService {
    private BankEmployeeRemovalService() {
    }

    public static boolean removeAndPersist(CentralBank centralBank, UUID bankId, UUID employeeId) {
        if (centralBank == null || bankId == null || employeeId == null) {
            return false;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        boolean removed = BankStaffingService.removeEmployee(metadata, employeeId);
        centralBank.putBankMetadata(bankId, metadata);
        return removed;
    }
}
