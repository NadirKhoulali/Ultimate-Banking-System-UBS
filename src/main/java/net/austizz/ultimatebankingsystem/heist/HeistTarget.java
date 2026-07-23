package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record HeistTarget(UUID bankId,
                          String bankName,
                          String premiseId,
                          SafeBlockBounds bounds,
                          SafeExitSnapshot exit,
                          String dimension,
                          BlockPos ownerPcPos,
                          BlockPos vaultDoorPos,
                          boolean eligible,
                          List<String> blockers,
                          int physicalLootSources) {
    public HeistTarget {
        bankName = bankName == null ? "" : bankName;
        premiseId = premiseId == null ? "" : premiseId;
        dimension = dimension == null ? "" : dimension;
        ownerPcPos = ownerPcPos == null ? null : ownerPcPos.immutable();
        vaultDoorPos = vaultDoorPos == null ? null : vaultDoorPos.immutable();
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        physicalLootSources = Math.max(0, physicalLootSources);
    }
}
