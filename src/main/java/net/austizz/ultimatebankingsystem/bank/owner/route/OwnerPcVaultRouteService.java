package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteCancelPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteEditorPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteRequestPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class OwnerPcVaultRouteService {
    private OwnerPcVaultRouteService() {
    }

    public record Result(OwnerPcVaultRouteEditorPayload editor, boolean persisted) {
    }

    public static Result request(MinecraftServer server,
                                 CentralBank centralBank,
                                 ServerPlayer player,
                                 OwnerPcVaultRouteRequestPayload payload) {
        if (server == null || player == null) {
            return unavailable(payload);
        }
        return request(new OwnerPcVaultRouteServerPorts(server, centralBank, player),
                OwnerPcVaultRouteEditSessions.forServer(server), player.getUUID(), payload);
    }

    public static Result save(MinecraftServer server,
                              CentralBank centralBank,
                              ServerPlayer player,
                              OwnerPcVaultRouteSavePayload payload) {
        if (server == null || player == null) {
            return unavailable(payload);
        }
        return save(new OwnerPcVaultRouteServerPorts(server, centralBank, player),
                OwnerPcVaultRouteEditSessions.forServer(server), player.getUUID(), payload);
    }

    public static void cancel(MinecraftServer server,
                              ServerPlayer player,
                              OwnerPcVaultRouteCancelPayload payload) {
        if (server != null && player != null && payload != null) {
            cancel(OwnerPcVaultRouteEditSessions.forServer(server), player.getUUID(), payload);
        }
    }

    static void cancel(OwnerPcVaultRouteEditSessionStore sessions,
                       UUID playerId,
                       OwnerPcVaultRouteCancelPayload payload) {
        if (sessions != null && playerId != null && payload != null) {
            sessions.invalidate(payload.editSessionId(), playerId);
        }
    }

    static Result request(OwnerPcVaultRoutePorts ports,
                          OwnerPcVaultRouteEditSessionStore sessions,
                          UUID playerId,
                          OwnerPcVaultRouteRequestPayload payload) {
        OwnerPcVaultRouteEditSession.Identity identity =
                OwnerPcVaultRouteEditSession.Identity.from(payload);
        OwnerPcVaultRoutePorts.Authority authority = ports == null ? null
                : ports.requestAuthority(payload.bankId(), payload.tellerId());
        OwnerPcVaultRoutePolicy.AccessFacts access = access(
                authority, payload.bankId(), payload.vaultId(), true);
        Result result = OwnerPcVaultRouteTransaction.request(
                authority == null ? null : authority.metadata(), payload, access);
        if (!result.editor().success()) {
            return result;
        }
        if (sessions == null || playerId == null || authority == null
                || authority.origin() == null) {
            return OwnerPcVaultRouteTransaction.reject(
                    identity, "A live bank owner PC is required to start route editing.");
        }
        OwnerPcVaultRouteEditSession session = sessions.issue(
                playerId, identity, authority.origin());
        return withSession(result, session);
    }

    static Result save(OwnerPcVaultRoutePorts ports,
                       OwnerPcVaultRouteEditSessionStore sessions,
                       UUID playerId,
                       OwnerPcVaultRouteSavePayload payload) {
        OwnerPcVaultRouteEditSession.Identity identity =
                OwnerPcVaultRouteEditSession.Identity.from(payload);
        if (sessions == null || playerId == null) {
            return OwnerPcVaultRouteTransaction.reject(
                    identity, "Route edit session is unavailable. Reopen the editor at the Owner PC.");
        }
        OwnerPcVaultRouteEditSessionStore.Check checked = sessions.check(
                payload.editSessionId(), playerId, identity);
        if (!checked.valid()) {
            return OwnerPcVaultRouteTransaction.reject(identity, sessionDenial(checked.status()));
        }

        OwnerPcVaultRoutePorts.Authority authority = ports == null ? null
                : ports.saveAuthority(payload.bankId(), payload.tellerId());
        OwnerPcVaultRoutePolicy.Result authorization = OwnerPcVaultRoutePolicy.authorizeSave(
                access(authority, payload.bankId(), payload.vaultId(), false));
        if (!authorization.allowed()) {
            sessions.invalidate(payload.editSessionId(), playerId);
            return OwnerPcVaultRouteTransaction.reject(identity, authorization.message());
        }

        OwnerPcVaultRoutePorts.WorldView world = ports.world(payload.dimension());
        boolean tellerInDimension = world != null && world.available()
                && SafeBlockBounds.normalizeDimension(authority.tellerDimension())
                .equals(SafeBlockBounds.normalizeDimension(world.dimension()));
        OwnerPcVaultRouteTransaction.SaveAttempt attempt =
                OwnerPcVaultRouteTransaction.saveAuthorized(
                        authority.metadata(), payload,
                        world != null && world.available(), tellerInDimension,
                        authority.tellerX(), authority.tellerY(), authority.tellerZ(),
                        world == null ? null : world::accepts,
                        world == null ? null : world::isRfidScanner,
                        staged -> {
                            if (!sessions.consumeIfValid(
                                    payload.editSessionId(), playerId, identity)) {
                                return false;
                            }
                            ports.commit(payload.bankId(), staged);
                            return true;
                        });
        if (attempt.result().persisted()) {
            return attempt.result();
        }
        if (!attempt.retainSession()) {
            sessions.invalidate(payload.editSessionId(), playerId);
            return attempt.result();
        }
        OwnerPcVaultRouteEditSessionStore.Check current = sessions.check(
                payload.editSessionId(), playerId, identity);
        return current.valid() ? withSession(attempt.result(), current.session())
                : attempt.result();
    }

    static void invalidatePlayer(MinecraftServer server, UUID playerId) {
        OwnerPcVaultRouteEditSessions.invalidatePlayer(server, playerId);
    }

    static void clear(MinecraftServer server) {
        OwnerPcVaultRouteEditSessions.clear(server);
    }

    private static OwnerPcVaultRoutePolicy.AccessFacts access(
            OwnerPcVaultRoutePorts.Authority authority,
            UUID bankId,
            String vaultId,
            boolean requireOrigin) {
        if (authority == null) {
            return null;
        }
        boolean vaultFound = authority.bankExists()
                && OwnerPcVaultRouteTarget.resolve(authority.metadata(), bankId, vaultId) != null;
        return new OwnerPcVaultRoutePolicy.AccessFacts(
                authority.activePc() && (!requireOrigin || authority.origin() != null),
                authority.poweredOn(), authority.sessionUnlocked(), authority.owner(),
                authority.permissionLevelThree(), authority.tellerLoaded(),
                authority.tellerBound(), authority.tellerSameBank(), authority.cashier(),
                vaultFound);
    }

    private static Result withSession(Result result, OwnerPcVaultRouteEditSession session) {
        OwnerPcVaultRouteEditorPayload editor = result.editor();
        return new Result(new OwnerPcVaultRouteEditorPayload(
                editor.success(), editor.message(), session.id(), session.expiresAtMillis(),
                editor.bankId(), editor.vaultId(), editor.tellerId(), editor.direction(),
                editor.hasRoute(), editor.dimension(), editor.start(), editor.finish(),
                editor.steps()), result.persisted());
    }

    private static Result unavailable(OwnerPcVaultRouteRequestPayload payload) {
        return OwnerPcVaultRouteTransaction.reject(
                OwnerPcVaultRouteEditSession.Identity.from(payload),
                "Vault route service is unavailable.");
    }

    private static Result unavailable(OwnerPcVaultRouteSavePayload payload) {
        return OwnerPcVaultRouteTransaction.reject(
                OwnerPcVaultRouteEditSession.Identity.from(payload),
                "Vault route service is unavailable.");
    }

    private static String sessionDenial(OwnerPcVaultRouteEditSessionStore.Status status) {
        return switch (status) {
            case EXPIRED -> "Route edit session expired. Reopen the editor at the Owner PC.";
            case IDENTITY_MISMATCH ->
                    "Route edit session does not match this save. Reopen the editor at the Owner PC.";
            case MISSING, VALID ->
                    "Route edit session is missing or already used. Reopen the editor at the Owner PC.";
        };
    }
}
