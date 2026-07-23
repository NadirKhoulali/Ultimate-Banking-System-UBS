package net.austizz.ultimatebankingsystem.bank.owner.premise;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimSelection;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupMigration;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMutationResult;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMutationService;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionResponsePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class OwnerPcPremiseService {
    /**
     * Correlation value for deferred picker completions, which are unsolicited server results.
     */
    public static final UUID UNSOLICITED_OPERATION_ID = new UUID(0L, 0L);

    private OwnerPcPremiseService() {
    }

    public interface Ports {
        Authority authority(UUID bankId);

        SessionResult startSession(UUID bankId, SafeClaimToolPurpose purpose, String premiseId);

        Result withMutation(UUID bankId,
                            OwnerPcPremiseActionPayload.Action action,
                            String premiseId,
                            Mutation mutation);
    }

    @FunctionalInterface
    public interface Mutation {
        Result apply(Authority authority,
                     Set<String> activeVaultIds,
                     Consumer<Map<String, Object>> commit);
    }

    public record Authority(Map<String, Object> metadata,
                            boolean bankExists,
                            boolean activeComputer,
                            boolean poweredOn,
                            boolean sessionUnlocked,
                            boolean owner,
                            boolean permissionLevelThree) {
    }

    public record SessionResult(boolean success, String message) {
        public SessionResult {
            message = message == null ? "" : message;
        }
    }

    public record Result(UUID bankId,
                         UUID operationId,
                         OwnerPcPremiseActionPayload.Action action,
                         String premiseId,
                         boolean success,
                         String message) {
        public Result {
            operationId = Objects.requireNonNull(operationId, "operationId");
            premiseId = premiseId == null ? "" : premiseId;
            message = message == null ? "" : message;
        }

        public Result(UUID bankId,
                      OwnerPcPremiseActionPayload.Action action,
                      String premiseId,
                      boolean success,
                      String message) {
            this(bankId, UNSOLICITED_OPERATION_ID, action, premiseId, success, message);
        }

        public OwnerPcPremiseActionResponsePayload response() {
            return new OwnerPcPremiseActionResponsePayload(
                    bankId, operationId, action, premiseId, success, message);
        }
    }

    public static Result execute(MinecraftServer server,
                                 CentralBank centralBank,
                                 ServerPlayer player,
                                 OwnerPcPremiseActionPayload payload) {
        return execute(OwnerPcPremiseServerPorts.forDirectAction(
                server, centralBank, player), payload);
    }

    public static Result execute(Ports ports, OwnerPcPremiseActionPayload payload) {
        if (payload == null) {
            return unavailable(null, OwnerPcPremiseActionPayload.Action.START_CLAIM, "");
        }
        if (ports == null) {
            return correlated(payload,
                    unavailable(payload.bankId(), payload.action(), payload.premiseId()));
        }
        Result result = switch (payload.action()) {
            case START_CLAIM -> startSession(
                    ports, payload, SafeClaimToolPurpose.PREMISE_CREATE);
            case START_EXIT_EDIT -> startSession(
                    ports, payload, SafeClaimToolPurpose.PREMISE_EXIT_EDIT);
            case SET_MODE -> ports.withMutation(
                    payload.bankId(), payload.action(), payload.premiseId(),
                    (authority, activeVaultIds, commit) -> mutateMode(
                            authority, payload, commit));
            case DELETE -> ports.withMutation(
                    payload.bankId(), payload.action(), payload.premiseId(),
                    (authority, activeVaultIds, commit) -> delete(
                            authority, payload, activeVaultIds, commit));
        };
        return correlated(payload, result);
    }

    public static Result applyObservedSelection(MinecraftServer server,
                                                CentralBank centralBank,
                                                ServerPlayer player,
                                                UUID bankId,
                                                SafeClaimToolPurpose purpose,
                                                String premiseId,
                                                SafeClaimSelection selection) {
        UUID playerId = player == null ? null : player.getUUID();
        if (!SafetyDepositBoxService.hasMatchingPremiseClaimToolSession(
                playerId, bankId, purpose, premiseId)) {
            OwnerPcPremiseActionPayload.Action action =
                    purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                            ? OwnerPcPremiseActionPayload.Action.START_EXIT_EDIT
                            : OwnerPcPremiseActionPayload.Action.START_CLAIM;
            return unavailable(bankId, action, premiseId);
        }
        return applyObservedSelection(
                OwnerPcPremiseServerPorts.forDeferredClaimApply(
                        server, centralBank, player),
                bankId, purpose, premiseId, selection);
    }

    public static Result applyObservedSelection(Ports ports,
                                                UUID bankId,
                                                SafeClaimToolPurpose purpose,
                                                String premiseId,
                                                SafeClaimSelection selection) {
        OwnerPcPremiseActionPayload.Action action = purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                ? OwnerPcPremiseActionPayload.Action.START_EXIT_EDIT
                : OwnerPcPremiseActionPayload.Action.START_CLAIM;
        String targetId = premiseId == null ? "" : premiseId;
        if (ports == null || bankId == null || selection == null
                || (purpose != SafeClaimToolPurpose.PREMISE_CREATE
                && purpose != SafeClaimToolPurpose.PREMISE_EXIT_EDIT)) {
            return unavailable(bankId, action, targetId);
        }
        Result result = ports.withMutation(bankId, action, targetId,
                (authority, activeVaultIds, commit) -> purpose == SafeClaimToolPurpose.PREMISE_CREATE
                        ? create(authority, bankId, action, selection, commit)
                        : setExit(authority, bankId, targetId, action, selection, commit));
        Result completed = result == null ? unavailable(bankId, action, targetId) : result;
        return withOperationId(UNSOLICITED_OPERATION_ID, completed);
    }

    private static Result startSession(Ports ports,
                                       OwnerPcPremiseActionPayload payload,
                                       SafeClaimToolPurpose purpose) {
        Authority authority = ports.authority(payload.bankId());
        Result denied = denial(authority, payload.bankId(),
                payload.action(), payload.premiseId());
        if (denied != null) {
            return denied;
        }
        if (purpose == SafeClaimToolPurpose.PREMISE_EXIT_EDIT
                && premise(authority, payload.bankId(), payload.premiseId()) == null) {
            return fail(payload.bankId(), payload.action(), payload.premiseId(),
                    "The selected premise no longer exists.");
        }
        SessionResult started = ports.startSession(
                payload.bankId(), purpose, payload.premiseId());
        if (started == null) {
            return unavailable(payload.bankId(), payload.action(), payload.premiseId());
        }
        return new Result(payload.bankId(), payload.action(), payload.premiseId(),
                started.success(), started.message());
    }

    private static Result mutateMode(Authority authority,
                                     OwnerPcPremiseActionPayload payload,
                                     Consumer<Map<String, Object>> commit) {
        Result denied = denial(authority, payload.bankId(), payload.action(), payload.premiseId());
        if (denied != null) {
            return denied;
        }
        SafePremiseMutationResult mutation = SafePremiseMutationService.setMode(
                authority.metadata(), payload.bankId(), payload.premiseId(), payload.mode());
        if (!mutation.success()) {
            return fail(payload.bankId(), payload.action(), payload.premiseId(),
                    "The selected premise no longer exists or changed before the update.");
        }
        commit.accept(mutation.metadata());
        return ok(payload.bankId(), payload.action(), payload.premiseId(),
                "Premise access mode changed to " + payload.mode().name() + ".");
    }

    private static Result delete(Authority authority,
                                 OwnerPcPremiseActionPayload payload,
                                 Set<String> activeVaultIds,
                                 Consumer<Map<String, Object>> commit) {
        Result denied = denial(authority, payload.bankId(), payload.action(), payload.premiseId());
        if (denied != null) {
            return denied;
        }
        SafePremiseMutationResult mutation = SafePremiseMutationService.delete(
                authority.metadata(), payload.bankId(), payload.premiseId(), activeVaultIds);
        if (!mutation.success()) {
            String blockers = mutation.blockers().isEmpty()
                    ? "STALE"
                    : String.join(", ", mutation.blockers().stream().map(Enum::name).toList());
            return fail(payload.bankId(), payload.action(), payload.premiseId(),
                    "Premise cannot be deleted: " + blockers + ".");
        }
        commit.accept(mutation.metadata());
        return ok(payload.bankId(), payload.action(), payload.premiseId(), "Premise deleted.");
    }

    private static Result create(Authority authority,
                                 UUID bankId,
                                 OwnerPcPremiseActionPayload.Action action,
                                 SafeClaimSelection selection,
                                 Consumer<Map<String, Object>> commit) {
        Result denied = denial(authority, bankId, action, "");
        if (denied != null) {
            return denied;
        }
        SafeClaimSelection.Corner first = selection.firstCorner();
        SafeClaimSelection.Corner second = selection.secondCorner();
        SafeClaimSelection.Exit exit = selection.exit();
        if (first == null || second == null) {
            return fail(bankId, action, "", "Select Pos1 and Pos2 before applying the premise.");
        }
        if (exit == null) {
            return fail(bankId, action, "", "Capture an outside premise exit before applying.");
        }
        SafeBlockBounds bounds = bounds(first, second);
        if (bounds == null || !bounds.dimension().equals(exit.dimension())) {
            return fail(bankId, action, "", "Premise corners and exit must use one dimension.");
        }
        if (bounds.contains(exit.x(), exit.y(), exit.z())) {
            return fail(bankId, action, "", "Premise exit must be outside the premise.");
        }
        Set<String> beforeIds = premiseIds(authority.metadata(), bankId);
        SafePremiseMutationResult mutation = SafePremiseMutationService.create(
                authority.metadata(), bankId, bounds,
                new SafeExitSnapshot(exit.dimension(), exit.x(), exit.y(), exit.z(), exit.yaw()));
        if (!mutation.success()) {
            return fail(bankId, action, "",
                    "Premise claim failed because its bounds overlap or the live setup changed.");
        }
        String createdId = premiseIds(mutation.metadata(), bankId).stream()
                .filter(id -> !beforeIds.contains(id))
                .findFirst()
                .orElse("");
        if (createdId.isEmpty()) {
            return fail(bankId, action, "", "Premise claim did not produce a stable server id.");
        }
        commit.accept(mutation.metadata());
        return ok(bankId, action, createdId, "Premise claimed.");
    }

    private static Result setExit(Authority authority,
                                  UUID bankId,
                                  String premiseId,
                                  OwnerPcPremiseActionPayload.Action action,
                                  SafeClaimSelection selection,
                                  Consumer<Map<String, Object>> commit) {
        Result denied = denial(authority, bankId, action, premiseId);
        if (denied != null) {
            return denied;
        }
        SafePremiseSnapshot premise = premise(authority, bankId, premiseId);
        SafeClaimSelection.Exit exit = selection.exit();
        if (premise == null) {
            return fail(bankId, action, premiseId, "The selected premise no longer exists.");
        }
        if (exit == null || !premise.bounds().dimension().equals(exit.dimension())) {
            return fail(bankId, action, premiseId,
                    "Capture the replacement exit in the premise dimension.");
        }
        if (premise.bounds().contains(exit.x(), exit.y(), exit.z())) {
            return fail(bankId, action, premiseId, "Premise exit must be outside the premise.");
        }
        SafePremiseMutationResult mutation = SafePremiseMutationService.setExit(
                authority.metadata(), bankId, premiseId,
                new SafeExitSnapshot(exit.dimension(), exit.x(), exit.y(), exit.z(), exit.yaw()));
        if (!mutation.success()) {
            return fail(bankId, action, premiseId,
                    "The selected premise changed before its exit was updated.");
        }
        commit.accept(mutation.metadata());
        return ok(bankId, action, premiseId, "Premise exit updated.");
    }

    private static Result denial(Authority authority,
                                 UUID bankId,
                                 OwnerPcPremiseActionPayload.Action action,
                                 String premiseId) {
        if (authority == null || !authority.activeComputer()) {
            return fail(bankId, action, premiseId,
                    "Open an active bank owner PC before managing premises.");
        }
        if (!authority.poweredOn()) {
            return fail(bankId, action, premiseId, "This bank owner PC is powered off.");
        }
        if (!authority.sessionUnlocked()) {
            return fail(bankId, action, premiseId, "This bank owner PC session is locked.");
        }
        if (!authority.bankExists() || authority.metadata() == null) {
            return fail(bankId, action, premiseId, "The selected bank no longer exists.");
        }
        if (!authority.owner() && !authority.permissionLevelThree()) {
            return fail(bankId, action, premiseId,
                    "Only the bank owner or a level 3 operator may manage premises.");
        }
        return null;
    }

    private static SafePremiseSnapshot premise(Authority authority,
                                               UUID bankId,
                                               String premiseId) {
        if (authority == null || authority.metadata() == null || premiseId == null) {
            return null;
        }
        return SafeDepositSetupMigration.snapshot(authority.metadata()).premises().stream()
                .filter(candidate -> premiseId.equals(candidate.id()))
                .filter(candidate -> bankId.toString().equalsIgnoreCase(candidate.bankId()))
                .findFirst()
                .orElse(null);
    }

    private static Set<String> premiseIds(Map<String, Object> metadata, UUID bankId) {
        Set<String> ids = new LinkedHashSet<>();
        for (SafePremiseSnapshot premise : SafeDepositSetupMigration.snapshot(metadata).premises()) {
            if (bankId.toString().equalsIgnoreCase(premise.bankId())) {
                ids.add(premise.id());
            }
        }
        return ids;
    }

    private static SafeBlockBounds bounds(SafeClaimSelection.Corner first,
                                          SafeClaimSelection.Corner second) {
        if (first == null || second == null || !first.dimension().equals(second.dimension())) {
            return null;
        }
        return new SafeBlockBounds(first.dimension(),
                first.x(), first.y(), first.z(), second.x(), second.y(), second.z());
    }

    private static Result correlated(OwnerPcPremiseActionPayload payload, Result result) {
        Result completed = result == null
                ? unavailable(payload.bankId(), payload.action(), payload.premiseId())
                : result;
        return withOperationId(payload.operationId(), completed);
    }

    private static Result withOperationId(UUID operationId, Result completed) {
        if (operationId.equals(completed.operationId())) {
            return completed;
        }
        return new Result(completed.bankId(), operationId, completed.action(),
                completed.premiseId(), completed.success(), completed.message());
    }

    private static Result ok(UUID bankId,
                             OwnerPcPremiseActionPayload.Action action,
                             String premiseId,
                             String message) {
        return new Result(bankId, action, premiseId, true, message);
    }

    private static Result fail(UUID bankId,
                               OwnerPcPremiseActionPayload.Action action,
                               String premiseId,
                               String message) {
        return new Result(bankId, action, premiseId, false, message);
    }

    private static Result unavailable(UUID bankId,
                                      OwnerPcPremiseActionPayload.Action action,
                                      String premiseId) {
        return fail(bankId, action, premiseId, "Premise management is unavailable.");
    }
}
