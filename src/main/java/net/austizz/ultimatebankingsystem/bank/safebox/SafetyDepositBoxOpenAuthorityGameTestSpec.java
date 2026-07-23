package net.austizz.ultimatebankingsystem.bank.safebox;

import net.minecraft.core.BlockPos;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

record SafetyDepositBoxOpenAuthorityGameTestSpec(Identities identities, Layout layout) {
    SafetyDepositBoxOpenAuthorityGameTestSpec {
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(layout, "layout");
    }

    RouteBinding routeBinding(UUID tellerId, BlockPos tellerPos) {
        return new RouteBinding(identities.bankId(), tellerId, tellerPos, layout.dimension());
    }

    record Identities(UUID bankId, UUID accountId, UUID siblingAccountId, UUID playerId) {
        Identities {
            Objects.requireNonNull(bankId, "bankId");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(siblingAccountId, "siblingAccountId");
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    record Layout(String dimension, BlockPos rowPos, BlockPos doorMaster) {
        Layout {
            if (dimension == null || dimension.isBlank()) {
                throw new IllegalArgumentException("dimension must not be blank");
            }
            dimension = dimension.trim().toLowerCase(Locale.ROOT);
            rowPos = Objects.requireNonNull(rowPos, "rowPos").immutable();
            doorMaster = Objects.requireNonNull(doorMaster, "doorMaster").immutable();
        }
    }

    record RouteBinding(UUID bankId, UUID tellerId, BlockPos tellerPos, String dimension) {
        RouteBinding {
            Objects.requireNonNull(bankId, "bankId");
            Objects.requireNonNull(tellerId, "tellerId");
            tellerPos = Objects.requireNonNull(tellerPos, "tellerPos").immutable();
            Objects.requireNonNull(dimension, "dimension");
        }
    }
}
