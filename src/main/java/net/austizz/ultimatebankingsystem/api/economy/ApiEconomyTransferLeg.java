package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.UUID;

@ApiStatus.AvailableSince("2.1.0")
public record ApiEconomyTransferLeg(
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        String reference
) {
    public ApiEconomyTransferLeg {
        amount = amount == null ? BigDecimal.ZERO : amount;
        reference = reference == null ? "" : reference.trim();
    }
}
