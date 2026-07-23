package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultSnapshot;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

record OwnerPcVaultRouteTarget(SafePremiseSnapshot premise,
                               SafeAreaSnapshot safeArea,
                               SafeVaultSnapshot vault) {
    static OwnerPcVaultRouteTarget resolve(CompoundTag metadata, UUID bankId, String vaultId) {
        if (metadata == null || bankId == null || vaultId == null || vaultId.isBlank()) {
            return null;
        }
        OwnerPcVaultRouteTarget found = null;
        for (SafePremiseSnapshot premise : SafeDepositSetupNbtCodec.snapshot(metadata).premises()) {
            if (!bankId.toString().equals(premise.bankId())) {
                continue;
            }
            for (SafeAreaSnapshot safeArea : premise.safeAreas()) {
                for (SafeVaultSnapshot vault : safeArea.vaults()) {
                    if (!vaultId.equals(vault.id())) {
                        continue;
                    }
                    if (found != null || premise.bounds() == null || safeArea.bounds() == null
                            || !safeArea.bounds().dimension().equals(vault.dimension())) {
                        return null;
                    }
                    found = new OwnerPcVaultRouteTarget(premise, safeArea, vault);
                }
            }
        }
        return found;
    }
}
