package net.austizz.ultimatebankingsystem.api.bank;

import net.austizz.ultimatebankingsystem.api.ApiBlockPosition;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.0.0")
public record ApiBankTellerSnapshot(UUID entityId,
                                    String name,
                                    int variant,
                                    ApiBlockPosition position,
                                    boolean active,
                                    boolean bound) {
    public ApiBankTellerSnapshot {
        name = name == null ? "" : name;
    }
}
