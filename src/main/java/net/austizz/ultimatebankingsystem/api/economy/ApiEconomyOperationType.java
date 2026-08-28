package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.1.0")
public enum ApiEconomyOperationType {
    TRANSFER,
    TRANSFER_TO_PRIMARY,
    SET_PRIMARY_ACCOUNT,
    PROVISION_INSTITUTION_ACCOUNT,
    SET_ACCESS_ROLE,
    SET_ACCOUNT_FROZEN,
    ADMIN_DEPOSIT,
    ADMIN_WITHDRAW,
    CREATE_ESCROW,
    FUND_ESCROW,
    RELEASE_ESCROW,
    REFUND_ESCROW
}
