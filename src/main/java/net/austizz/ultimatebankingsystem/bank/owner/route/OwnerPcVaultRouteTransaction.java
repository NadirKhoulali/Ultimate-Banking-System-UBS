package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteNbtStore;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteSaveResult;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteEditorPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteRequestPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteStepPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Predicate;

final class OwnerPcVaultRouteTransaction {
    private OwnerPcVaultRouteTransaction() {
    }

    static OwnerPcVaultRouteService.Result request(
            CompoundTag metadata,
            OwnerPcVaultRouteRequestPayload payload,
            OwnerPcVaultRoutePolicy.AccessFacts access) {
        OwnerPcVaultRouteEditSession.Identity identity =
                OwnerPcVaultRouteEditSession.Identity.from(payload);
        OwnerPcVaultRouteTarget target = target(metadata, identity);
        OwnerPcVaultRoutePolicy.Result authorization = authorize(access, target != null);
        if (!authorization.allowed()) {
            return failure(identity, authorization.message());
        }
        String routeId = SafeTellerRoute.stableId(identity.bankId().toString(),
                identity.vaultId(), identity.tellerId().toString(), identity.direction());
        SafeTellerRoute route = SafeTellerRouteNbtStore.resolve(metadata, routeId).orElse(null);
        if (route == null) {
            return result(identity, true, "No saved route.", false,
                    target.vault().dimension(), OwnerPcVaultRoutePosition.ZERO,
                    OwnerPcVaultRoutePosition.ZERO, List.of(), false);
        }
        if (!target.vault().dimension().equals(route.dimension())) {
            return failure(identity, "The saved route dimension no longer matches this vault.");
        }
        return result(identity, true, "Route loaded.", true, route.dimension(),
                OwnerPcVaultRouteMapper.toNetwork(route.start()),
                OwnerPcVaultRouteMapper.toNetwork(route.finish()),
                OwnerPcVaultRouteMapper.toNetwork(route.steps()), false);
    }

    static SaveAttempt saveAuthorized(
            CompoundTag metadata,
            OwnerPcVaultRouteSavePayload payload,
            boolean dimensionAvailable,
            boolean tellerInDimension,
            double tellerX,
            double tellerY,
            double tellerZ,
            Predicate<OwnerPcVaultRoutePosition> loaded,
            Predicate<OwnerPcVaultRoutePosition> rfidScanner,
            Predicate<CompoundTag> commit) {
        OwnerPcVaultRouteEditSession.Identity identity =
                OwnerPcVaultRouteEditSession.Identity.from(payload);
        OwnerPcVaultRouteTarget target = target(metadata, identity);
        if (target == null) {
            return terminalFailure(identity, "The selected vault does not belong to this bank.");
        }
        if (ResourceLocation.tryParse(payload.dimension()) == null) {
            return retainableFailure(identity, "The route dimension is invalid.");
        }
        if (!dimensionAvailable) {
            return retainableFailure(identity,
                    "The route dimension is not available on this server.");
        }
        if (!tellerInDimension) {
            return retainableFailure(identity,
                    "The selected teller is not in the target vault dimension.");
        }
        if (loaded == null || rfidScanner == null || commit == null) {
            return terminalFailure(identity, "Vault route service is unavailable.");
        }
        OwnerPcVaultRoutePolicy.Result draft = OwnerPcVaultRoutePolicy.validateDraft(
                payload, target.premise().bounds(), target.safeArea().bounds(),
                target.vault().dimension(), tellerX, tellerY, tellerZ, loaded, rfidScanner);
        if (!draft.allowed()) {
            return retainableFailure(identity, draft.message());
        }
        SafeTellerRoute route = OwnerPcVaultRouteMapper.toDomain(payload);
        CompoundTag staged = metadata.copy();
        SafeTellerRouteSaveResult saved = SafeTellerRouteNbtStore.saveAndBind(staged, route);
        if (!saved.success()) {
            String message = "Route persistence rejected the draft: " + saved.status().name();
            return saved.status() == SafeTellerRouteSaveResult.Status.INVALID_ROUTE
                    ? retainableFailure(identity, message)
                    : terminalFailure(identity, message);
        }
        if (!commit.test(staged)) {
            return terminalFailure(identity,
                    "Route edit session expired before the route could be committed.");
        }
        return new SaveAttempt(result(identity, true, "Route saved.", true, route.dimension(),
                OwnerPcVaultRouteMapper.toNetwork(route.start()),
                OwnerPcVaultRouteMapper.toNetwork(route.finish()),
                OwnerPcVaultRouteMapper.toNetwork(route.steps()), true), false);
    }

    static OwnerPcVaultRouteService.Result reject(
            OwnerPcVaultRouteEditSession.Identity identity, String message) {
        return failure(identity, message);
    }

    private static OwnerPcVaultRouteTarget target(
            CompoundTag metadata, OwnerPcVaultRouteEditSession.Identity identity) {
        return identity == null ? null : OwnerPcVaultRouteTarget.resolve(
                metadata, identity.bankId(), identity.vaultId());
    }

    private static OwnerPcVaultRoutePolicy.Result authorize(
            OwnerPcVaultRoutePolicy.AccessFacts facts, boolean vaultFound) {
        if (facts == null) {
            return new OwnerPcVaultRoutePolicy.Result(false,
                    "Vault route service is unavailable.");
        }
        return OwnerPcVaultRoutePolicy.authorize(new OwnerPcVaultRoutePolicy.AccessFacts(
                facts.activePc(), facts.poweredOn(), facts.sessionUnlocked(), facts.owner(),
                facts.permissionLevelThree(), facts.tellerLoaded(), facts.tellerBound(),
                facts.tellerSameBank(), facts.cashier(), facts.vaultFound() && vaultFound));
    }

    private static SaveAttempt retainableFailure(
            OwnerPcVaultRouteEditSession.Identity identity, String message) {
        return new SaveAttempt(failure(identity, message), true);
    }

    private static SaveAttempt terminalFailure(
            OwnerPcVaultRouteEditSession.Identity identity, String message) {
        return new SaveAttempt(failure(identity, message), false);
    }

    private static OwnerPcVaultRouteService.Result failure(
            OwnerPcVaultRouteEditSession.Identity identity, String message) {
        return result(identity, false, message, false, "", OwnerPcVaultRoutePosition.ZERO,
                OwnerPcVaultRoutePosition.ZERO, List.of(), false);
    }

    private static OwnerPcVaultRouteService.Result result(
            OwnerPcVaultRouteEditSession.Identity identity,
            boolean success,
            String message,
            boolean hasRoute,
            String dimension,
            OwnerPcVaultRoutePosition start,
            OwnerPcVaultRoutePosition finish,
            List<OwnerPcVaultRouteStepPayload> steps,
            boolean persisted) {
        OwnerPcVaultRouteEditorPayload editor = new OwnerPcVaultRouteEditorPayload(
                success, message, null, 0L, identity.bankId(), identity.vaultId(),
                identity.tellerId(), identity.direction(), hasRoute, dimension, start,
                finish, steps);
        return new OwnerPcVaultRouteService.Result(editor, persisted);
    }

    record SaveAttempt(OwnerPcVaultRouteService.Result result, boolean retainSession) {
    }
}
