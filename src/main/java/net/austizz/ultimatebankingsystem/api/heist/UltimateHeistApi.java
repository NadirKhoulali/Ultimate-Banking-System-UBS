package net.austizz.ultimatebankingsystem.api.heist;

import net.austizz.ultimatebankingsystem.api.ApiManagementResult;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0.0")
public interface UltimateHeistApi {
    boolean isAvailable();

    List<ApiHeistSessionSnapshot> getSessions();

    List<ApiHeistSessionSnapshot> getActiveSessions();

    Optional<ApiHeistSessionSnapshot> getSession(UUID sessionId);

    Optional<ApiHeistSessionSnapshot> getPlayerSession(UUID playerId);

    List<ApiHeistTargetSnapshot> getTargets();

    Optional<ApiHeistTargetSnapshot> getTarget(UUID bankId, String premiseId);

    boolean isPlayerInHeist(UUID playerId);

    boolean isPlayerInActiveHeist(UUID playerId);

    boolean isBankUnderAttack(UUID bankId);

    long getPlayerCooldownRemainingMillis(UUID playerId);

    long getBankCooldownRemainingMillis(UUID bankId);

    long getVictimProtectionRemainingMillis(UUID playerId);

    int getMaximumCrewSize();

    long getCountdownDurationTicks();

    long getHeistDurationTicks();

    ApiManagementResult createPlanningSession(UUID playerId);

    ApiManagementResult invite(UUID leaderId, String playerName);

    ApiManagementResult respondToInvite(UUID playerId, boolean accepted);

    ApiManagementResult leave(UUID playerId);

    ApiManagementResult selectTarget(UUID leaderId, UUID bankId, String premiseId);

    ApiManagementResult setReady(UUID playerId, boolean ready);

    ApiManagementResult startCountdown(UUID leaderId);

    ApiManagementResult cancelCountdown(UUID leaderId);

    ApiManagementResult abandon(UUID playerId);
}
