package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

record SafeBoxEscortAuthorizationSnapshot(UUID accountOwnerId,
                                          UUID accountBankId,
                                          Teller teller,
                                          Assignment assignment,
                                          Vault vault,
                                          Routes routes) {
    SafeBoxEscortAuthorizationSnapshot {
        Objects.requireNonNull(accountOwnerId, "accountOwnerId");
        Objects.requireNonNull(accountBankId, "accountBankId");
        Objects.requireNonNull(teller, "teller");
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(routes, "routes");
    }

    boolean matches(SafeBoxEscortRuntimeContext context) {
        SafeBoxEscortTarget target = context.target();
        return context.playerId().equals(accountOwnerId)
                && target.bankId().equals(accountBankId)
                && teller.matches(context)
                && assignment.matches(context)
                && vault.mapped() && vault.ready()
                && target.vaultId().equals(vault.id())
                && context.vaultDoorMaster().equals(vault.doorMaster())
                && context.outboundRoute().id().equals(routes.outboundRef())
                && context.returnRoute().id().equals(routes.returnRef())
                && context.outboundRoute().equals(routes.outbound())
                && context.returnRoute().equals(routes.returning());
    }

    record Teller(UUID id, UUID bankId, boolean alive, boolean cashier) {
        Teller {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bankId, "bankId");
        }

        boolean matches(SafeBoxEscortRuntimeContext context) {
            return alive && !cashier && context.tellerId().equals(id)
                    && context.target().bankId().equals(bankId);
        }
    }

    record Assignment(UUID bankId,
                      UUID accountId,
                      String dimension,
                      EscortBlockPosition rowPosition,
                      int doorIndex,
                      String label,
                      boolean locked) {
        Assignment {
            Objects.requireNonNull(bankId, "bankId");
            Objects.requireNonNull(accountId, "accountId");
            dimension = text(dimension, "dimension").toLowerCase(Locale.ROOT);
            Objects.requireNonNull(rowPosition, "rowPosition");
            if (doorIndex < 0) {
                throw new IllegalArgumentException("doorIndex must not be negative");
            }
            label = text(label, "label");
        }

        boolean matches(SafeBoxEscortRuntimeContext context) {
            SafeBoxEscortTarget target = context.target();
            return !locked && bankId.equals(target.bankId()) && accountId.equals(target.accountId())
                    && dimension.equals(target.dimension()) && rowPosition.equals(target.rowPosition())
                    && doorIndex == target.doorIndex() && label.equals(context.label());
        }
    }

    record Vault(boolean mapped, boolean ready, String id, EscortBlockPosition doorMaster) {
        Vault {
            id = text(id, "id");
            Objects.requireNonNull(doorMaster, "doorMaster");
        }
    }

    record Routes(String outboundRef,
                  String returnRef,
                  SafeTellerRoute outbound,
                  SafeTellerRoute returning) {
        Routes {
            outboundRef = text(outboundRef, "outboundRef");
            returnRef = text(returnRef, "returnRef");
            if (outboundRef.equals(returnRef)) {
                throw new IllegalArgumentException("route references must be distinct");
            }
            Objects.requireNonNull(outbound, "outbound");
            Objects.requireNonNull(returning, "returning");
        }
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
