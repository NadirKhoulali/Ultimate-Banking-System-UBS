package net.austizz.ultimatebankingsystem.api.general;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.0.0")
public enum ApiFeature {
    BANKING,
    SHOPS,
    HEISTS,
    SMARTPHONE,
    SAFETY_DEPOSIT_BOXES,
    RFID_ACCESS,
    PHYSICAL_CURRENCY,
    WALLET,
    OWNER_PC,
    INSTITUTIONAL_ECONOMY,
    IDEMPOTENT_OPERATIONS,
    MONETARY_ESCROW
}
