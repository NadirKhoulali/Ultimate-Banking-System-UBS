package net.austizz.ultimatebankingsystem.heist;

import java.util.List;
import java.util.UUID;

final class HeistCrewLeadership {
    private HeistCrewLeadership() {
    }

    record Candidate(UUID playerId, boolean accepted) {
    }

    static UUID chooseSuccessor(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        for (Candidate candidate : candidates) {
            if (candidate != null && candidate.playerId() != null && candidate.accepted()) {
                return candidate.playerId();
            }
        }
        for (Candidate candidate : candidates) {
            if (candidate != null && candidate.playerId() != null) return candidate.playerId();
        }
        return null;
    }
}
