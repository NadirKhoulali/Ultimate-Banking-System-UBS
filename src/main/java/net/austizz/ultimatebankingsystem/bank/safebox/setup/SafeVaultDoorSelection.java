package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.Optional;
import java.util.Set;

public final class SafeVaultDoorSelection {
    private SafeVaultDoorSelection() {
    }

    public static <T> Optional<T> select(Optional<T> persistedAnchor, Set<T> completeContainedMasters) {
        Set<T> candidates = completeContainedMasters == null ? Set.of() : Set.copyOf(completeContainedMasters);
        Optional<T> persisted = persistedAnchor == null ? Optional.empty() : persistedAnchor;
        if (persisted.isPresent() && candidates.contains(persisted.get())) {
            return persisted;
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.iterator().next());
        }
        return Optional.empty();
    }
}
