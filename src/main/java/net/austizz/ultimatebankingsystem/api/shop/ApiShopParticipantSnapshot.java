package net.austizz.ultimatebankingsystem.api.shop;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiShopParticipantSnapshot(UUID playerId,
                                         String role,
                                         boolean canManage,
                                         boolean canBuild) {
    public ApiShopParticipantSnapshot {
        role = role == null ? "" : role;
    }
}
