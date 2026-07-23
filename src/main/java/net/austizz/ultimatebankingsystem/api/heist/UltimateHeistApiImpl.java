package net.austizz.ultimatebankingsystem.api.heist;

import net.austizz.ultimatebankingsystem.api.ApiManagementResult;
import net.austizz.ultimatebankingsystem.api.internal.ApiInternals;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.heist.HeistEligibilityService;
import net.austizz.ultimatebankingsystem.heist.HeistMember;
import net.austizz.ultimatebankingsystem.heist.HeistSavedData;
import net.austizz.ultimatebankingsystem.heist.HeistService;
import net.austizz.ultimatebankingsystem.heist.HeistSession;
import net.austizz.ultimatebankingsystem.heist.HeistTarget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.Internal
public final class UltimateHeistApiImpl implements UltimateHeistApi {
    @Override
    public boolean isAvailable() {
        return ApiInternals.server() != null;
    }

    @Override
    public List<ApiHeistSessionSnapshot> getSessions() {
        MinecraftServer server = ApiInternals.server();
        if (server == null) return List.of();
        return HeistSavedData.get(server).sessions().stream()
                .map(session -> snapshot(server, session))
                .sorted(Comparator.comparingLong(ApiHeistSessionSnapshot::createdAtMillis).reversed())
                .toList();
    }

    @Override
    public List<ApiHeistSessionSnapshot> getActiveSessions() {
        return getSessions().stream()
                .filter(session -> switch (session.phase()) {
                    case "CASING", "ACTIVE", "ESCAPING" -> true;
                    default -> false;
                }).toList();
    }

    @Override
    public Optional<ApiHeistSessionSnapshot> getSession(UUID sessionId) {
        MinecraftServer server = ApiInternals.server();
        if (server == null || sessionId == null) return Optional.empty();
        return Optional.ofNullable(HeistSavedData.get(server).session(sessionId)).map(session -> snapshot(server, session));
    }

    @Override
    public Optional<ApiHeistSessionSnapshot> getPlayerSession(UUID playerId) {
        if (playerId == null) return Optional.empty();
        return getSessions().stream()
                .filter(session -> session.members().stream().anyMatch(member -> playerId.equals(member.playerId())))
                .findFirst();
    }

    @Override
    public List<ApiHeistTargetSnapshot> getTargets() {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        if (!ApiInternals.canMutate(server) || centralBank == null) return List.of();
        HeistSavedData data = HeistSavedData.get(server);
        return HeistEligibilityService.targets(server, centralBank, data).stream()
                .map(target -> target(data, target))
                .toList();
    }

    @Override
    public Optional<ApiHeistTargetSnapshot> getTarget(UUID bankId, String premiseId) {
        if (bankId == null || premiseId == null) return Optional.empty();
        return getTargets().stream()
                .filter(target -> bankId.equals(target.bankId()) && premiseId.equals(target.premiseId()))
                .findFirst();
    }

    @Override
    public boolean isPlayerInHeist(UUID playerId) {
        return getPlayerSession(playerId).isPresent();
    }

    @Override
    public boolean isPlayerInActiveHeist(UUID playerId) {
        return getPlayerSession(playerId).map(session -> switch (session.phase()) {
            case "CASING", "ACTIVE", "ESCAPING" -> true;
            default -> false;
        }).orElse(false);
    }

    @Override
    public boolean isBankUnderAttack(UUID bankId) {
        MinecraftServer server = ApiInternals.server();
        return server != null && bankId != null && HeistService.isBankFrozen(server, bankId);
    }

    @Override
    public long getPlayerCooldownRemainingMillis(UUID playerId) {
        return remaining(playerId, false, false);
    }

    @Override
    public long getBankCooldownRemainingMillis(UUID bankId) {
        return remaining(bankId, true, false);
    }

    @Override
    public long getVictimProtectionRemainingMillis(UUID playerId) {
        return remaining(playerId, false, true);
    }

    @Override
    public int getMaximumCrewSize() {
        return HeistService.MAX_CREW;
    }

    @Override
    public long getCountdownDurationTicks() {
        return HeistService.COUNTDOWN_TICKS;
    }

    @Override
    public long getHeistDurationTicks() {
        return HeistService.HEIST_DURATION_TICKS;
    }

    @Override
    public ApiManagementResult createPlanningSession(UUID playerId) {
        ServerPlayer player = mutationPlayer(playerId);
        if (player == null) return unavailable();
        HeistSession session = HeistService.planningSession(player);
        return session == null ? ApiManagementResult.fail("Heist planning session could not be created.")
                : ApiManagementResult.ok("Heist planning session ready: " + session.id());
    }

    @Override
    public ApiManagementResult invite(UUID leaderId, String playerName) {
        ServerPlayer leader = mutationPlayer(leaderId);
        return leader == null ? unavailable() : result(HeistService.invite(leader, playerName));
    }

    @Override
    public ApiManagementResult respondToInvite(UUID playerId, boolean accepted) {
        ServerPlayer player = mutationPlayer(playerId);
        return player == null ? unavailable() : result(HeistService.accept(player, accepted));
    }

    @Override
    public ApiManagementResult leave(UUID playerId) {
        ServerPlayer player = mutationPlayer(playerId);
        return player == null ? unavailable() : result(HeistService.leave(player));
    }

    @Override
    public ApiManagementResult selectTarget(UUID leaderId, UUID bankId, String premiseId) {
        ServerPlayer leader = mutationPlayer(leaderId);
        return leader == null ? unavailable() : result(HeistService.selectTarget(leader, bankId, premiseId));
    }

    @Override
    public ApiManagementResult setReady(UUID playerId, boolean ready) {
        ServerPlayer player = mutationPlayer(playerId);
        return player == null ? unavailable() : result(HeistService.setReady(player, ready));
    }

    @Override
    public ApiManagementResult startCountdown(UUID leaderId) {
        ServerPlayer leader = mutationPlayer(leaderId);
        return leader == null ? unavailable() : result(HeistService.startCountdown(leader));
    }

    @Override
    public ApiManagementResult cancelCountdown(UUID leaderId) {
        ServerPlayer leader = mutationPlayer(leaderId);
        return leader == null ? unavailable() : result(HeistService.cancelCountdown(leader));
    }

    @Override
    public ApiManagementResult abandon(UUID playerId) {
        ServerPlayer player = mutationPlayer(playerId);
        return player == null ? unavailable() : result(HeistService.abandon(player));
    }

    private ApiHeistSessionSnapshot snapshot(MinecraftServer server, HeistSession session) {
        List<ApiHeistMemberSnapshot> members = session.members().values().stream()
                .map(member -> member(server, member))
                .sorted(Comparator.comparing(ApiHeistMemberSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ApiHeistDrillSnapshot> drills = new ArrayList<>();
        session.vaultDrills().forEach(drill -> drills.add(drill("VAULT", drill)));
        session.safeDrills().forEach(drill -> drills.add(drill("SAFE", drill)));
        List<ApiHeistHackSnapshot> hacks = session.activeHacks().stream()
                .map(hack -> new ApiHeistHackSnapshot(ApiInternals.position(hack.dimension(), hack.pos()),
                        hack.finishesTick(), hack.pausedUntilTick(), hack.pausedUntilTick() == Long.MAX_VALUE))
                .toList();
        return new ApiHeistSessionSnapshot(session.id(), session.leaderId(), session.bankId(), session.bankName(),
                session.premiseId(), ApiInternals.bounds(session.premiseBounds()), ApiInternals.position(session.exit()),
                session.exit() == null ? 0.0F : session.exit().yaw(), session.phase().name(), session.createdAtMillis(),
                session.countdownEndsAtTick(), session.startedAtMillis(), session.startedAtTick(), session.deadlineTick(),
                session.extractionStartedTick(), session.totalLootCents(), session.lootArmed(), session.alarmed(),
                session.alarmReason(), members, drills, hacks, session.hackedComputerCount(), session.breachedTargets(),
                session.cancelVotes());
    }

    private ApiHeistMemberSnapshot member(MinecraftServer server, HeistMember member) {
        return new ApiHeistMemberSnapshot(member.playerId(), member.lastKnownName(), member.accepted(), member.ready(),
                member.active(), member.dead(), server.getPlayerList().getPlayer(member.playerId()) != null,
                member.scoreCents(), member.bagIds());
    }

    private ApiHeistDrillSnapshot drill(String type, HeistSession.DrillState drill) {
        return new ApiHeistDrillSnapshot(type, drill.ownerId(), ApiInternals.position(drill.dimension(), drill.pos()),
                drill.finishesTick(), drill.jamsRemaining(), drill.jammedAtTick() > 0L, drill.completed());
    }

    private ApiHeistTargetSnapshot target(HeistSavedData data, HeistTarget target) {
        long remaining = Math.max(0L, data.bankCooldown(target.bankId()) - System.currentTimeMillis());
        return new ApiHeistTargetSnapshot(target.bankId(), target.bankName(), target.premiseId(),
                ApiInternals.bounds(target.bounds()), ApiInternals.position(target.exit()),
                target.exit() == null ? 0.0F : target.exit().yaw(),
                ApiInternals.position(target.dimension(), target.ownerPcPos()),
                ApiInternals.position(target.dimension(), target.vaultDoorPos()), target.eligible(), target.blockers(),
                target.physicalLootSources(), remaining);
    }

    private long remaining(UUID id, boolean bank, boolean victim) {
        MinecraftServer server = ApiInternals.server();
        if (server == null || id == null) return 0L;
        HeistSavedData data = HeistSavedData.get(server);
        long until = victim ? data.victimProtectedUntil(id) : bank ? data.bankCooldown(id) : data.playerCooldown(id);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private ServerPlayer mutationPlayer(UUID playerId) {
        MinecraftServer server = ApiInternals.server();
        return ApiInternals.canMutate(server) ? ApiInternals.onlinePlayer(playerId) : null;
    }

    private ApiManagementResult unavailable() {
        return ApiManagementResult.fail("Player must be online and the call must run on the server thread.");
    }

    private ApiManagementResult result(HeistService.Result result) {
        return result == null ? ApiManagementResult.fail("Heist action returned no result.")
                : new ApiManagementResult(result.success(), result.message());
    }
}
