package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.List;
import java.util.Map;

public record SafePremiseMutationResult(boolean success,
                                        Map<String, Object> metadata,
                                        List<SafePremiseDeletionPolicy> blockers) {
    public SafePremiseMutationResult {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    static SafePremiseMutationResult committed(Map<String, Object> metadata) {
        return new SafePremiseMutationResult(true, metadata, List.of());
    }

    static SafePremiseMutationResult rejected() {
        return rejected(List.of());
    }

    static SafePremiseMutationResult rejected(List<SafePremiseDeletionPolicy> blockers) {
        return new SafePremiseMutationResult(false, null, blockers);
    }
}
