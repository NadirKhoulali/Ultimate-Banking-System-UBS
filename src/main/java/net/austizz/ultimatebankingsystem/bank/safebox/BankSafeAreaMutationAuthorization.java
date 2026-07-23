package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseAccessPolicy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

final class BankSafeAreaMutationAuthorization {
    private BankSafeAreaMutationAuthorization() {
    }

    static boolean mayModify(boolean claimedBankAreaExists, boolean legacyManagementAccess) {
        if (!claimedBankAreaExists) {
            return true;
        }
        return SafePremiseAccessPolicy.decideStructuralMutation(legacyManagementAccess).allowed();
    }

    static <P> boolean mayModifyAll(Collection<P> positions,
                                    Function<P, Collection<UUID>> bankClaimsAt,
                                    Predicate<UUID> legacyManagementAccess) {
        Set<UUID> bankIds = new LinkedHashSet<>();
        if (positions != null && bankClaimsAt != null) {
            for (P pos : positions) {
                if (pos == null) {
                    continue;
                }
                Collection<UUID> claims = bankClaimsAt.apply(pos);
                if (claims != null) {
                    bankIds.addAll(claims);
                }
            }
        }
        if (bankIds.isEmpty()) {
            return true;
        }
        for (UUID bankId : bankIds) {
            if (!mayModify(true, legacyManagementAccess.test(bankId))) {
                return false;
            }
        }
        return true;
    }
}
