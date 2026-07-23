package net.austizz.ultimatebankingsystem.heist;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeistCrewLeadershipTest {
    @Test
    void acceptedMemberTakesPriorityOverPendingInvite() {
        UUID pendingId = UUID.randomUUID();
        UUID acceptedId = UUID.randomUUID();

        UUID successor = HeistCrewLeadership.chooseSuccessor(List.of(
                new HeistCrewLeadership.Candidate(pendingId, false),
                new HeistCrewLeadership.Candidate(acceptedId, true)));

        assertEquals(acceptedId, successor);
    }

    @Test
    void pendingMemberIsFallbackWhenNoAcceptedMemberExists() {
        UUID pendingId = UUID.randomUUID();

        assertEquals(pendingId, HeistCrewLeadership.chooseSuccessor(List.of(
                new HeistCrewLeadership.Candidate(pendingId, false))));
        assertNull(HeistCrewLeadership.chooseSuccessor(List.of()));
    }
}
