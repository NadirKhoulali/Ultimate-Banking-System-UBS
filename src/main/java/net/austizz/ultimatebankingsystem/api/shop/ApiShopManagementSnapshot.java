package net.austizz.ultimatebankingsystem.api.shop;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiShopManagementSnapshot(UUID shopId,
                                        UUID ownerId,
                                        String name,
                                        String type,
                                        String displayType,
                                        int level,
                                        long revenueDollars,
                                        long nextLevelTargetDollars,
                                        long usedClaimBlocks,
                                        long claimCapacityBlocks,
                                        int claimRegions,
                                        int stockroomRegions,
                                        boolean setupComplete,
                                        boolean currentlyOpen,
                                        int maxDisplayBlocks,
                                        int maxCashiers,
                                        int maxAssignedOrderPallets,
                                        List<ApiShopParticipantSnapshot> participants) {
    public ApiShopManagementSnapshot {
        name = name == null ? "" : name;
        type = type == null ? "" : type;
        displayType = displayType == null ? "" : displayType;
        level = Math.max(1, level);
        revenueDollars = Math.max(0L, revenueDollars);
        nextLevelTargetDollars = Math.max(0L, nextLevelTargetDollars);
        usedClaimBlocks = Math.max(0L, usedClaimBlocks);
        claimCapacityBlocks = Math.max(0L, claimCapacityBlocks);
        claimRegions = Math.max(0, claimRegions);
        stockroomRegions = Math.max(0, stockroomRegions);
        maxDisplayBlocks = Math.max(0, maxDisplayBlocks);
        maxCashiers = Math.max(0, maxCashiers);
        maxAssignedOrderPallets = Math.max(0, maxAssignedOrderPallets);
        participants = participants == null ? List.of() : List.copyOf(participants);
    }
}
