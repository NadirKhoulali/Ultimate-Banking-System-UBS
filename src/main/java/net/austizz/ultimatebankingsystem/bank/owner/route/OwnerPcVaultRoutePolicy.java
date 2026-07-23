package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteStepPayload;

import java.util.function.Predicate;

public final class OwnerPcVaultRoutePolicy {
    // Drafts use integer block positions while teller entities use doubles. A 1.5 block
    // radius accepts the teller's own block and adjacent center jitter without accepting
    // a materially different route endpoint.
    private static final double TELLER_POSITION_TOLERANCE_SQUARED = 1.5D * 1.5D;

    private OwnerPcVaultRoutePolicy() {
    }

    public record AccessFacts(boolean activePc,
                              boolean poweredOn,
                              boolean sessionUnlocked,
                              boolean owner,
                              boolean permissionLevelThree,
                              boolean tellerLoaded,
                              boolean tellerBound,
                              boolean tellerSameBank,
                              boolean cashier,
                              boolean vaultFound) {
    }

    public record Result(boolean allowed, String message) {
    }

    public static Result authorize(AccessFacts facts) {
        if (facts == null || !facts.activePc()) {
            return deny("Open a bank owner PC before managing vault routes.");
        }
        if (!facts.poweredOn()) {
            return deny("This bank owner PC is powered off.");
        }
        if (!facts.sessionUnlocked()) {
            return deny("This bank owner PC session is locked.");
        }
        return authorizeSave(facts);
    }

    public static Result authorizeSave(AccessFacts facts) {
        if (facts == null) {
            return deny("Vault route service is unavailable.");
        }
        if (!facts.owner() && !facts.permissionLevelThree()) {
            return deny("Only the bank owner or a level 3 operator may manage vault routes.");
        }
        if (!facts.tellerLoaded()) {
            return deny("The selected bank teller is not loaded.");
        }
        if (!facts.tellerBound()) {
            return deny("The selected bank teller is not bound to a bank.");
        }
        if (!facts.tellerSameBank()) {
            return deny("The selected bank teller belongs to a different bank.");
        }
        if (facts.cashier()) {
            return deny("Cashier entities cannot use safe teller routes.");
        }
        if (!facts.vaultFound()) {
            return deny("The selected vault does not belong to this bank.");
        }
        return allow();
    }

    public static Result validateDraft(OwnerPcVaultRouteSavePayload draft,
                                       SafeBlockBounds premise,
                                       SafeBlockBounds safeArea,
                                       String vaultDimension,
                                       double tellerX,
                                       double tellerY,
                                       double tellerZ,
                                       Predicate<OwnerPcVaultRoutePosition> loaded,
                                       Predicate<OwnerPcVaultRoutePosition> rfidScanner) {
        if (draft == null || premise == null || safeArea == null
                || loaded == null || rfidScanner == null) {
            return deny("Route validation context is unavailable.");
        }
        String routeDimension = SafeBlockBounds.normalizeDimension(draft.dimension());
        String targetDimension = SafeBlockBounds.normalizeDimension(vaultDimension);
        if (routeDimension.isEmpty() || !routeDimension.equals(targetDimension)
                || !routeDimension.equals(safeArea.dimension())
                || !routeDimension.equals(premise.dimension())) {
            return deny("The route dimension must match the target vault.");
        }
        if (!loaded.test(draft.start()) || !loaded.test(draft.finish())) {
            return deny("Route start and finish must be in loaded chunks.");
        }
        if (!contains(premise, routeDimension, draft.start())
                || !contains(premise, routeDimension, draft.finish())) {
            return deny("Route start and finish must stay inside the bank's claimed premises.");
        }
        for (OwnerPcVaultRouteStepPayload step : draft.steps()) {
            if (step instanceof OwnerPcVaultRouteStepPayload.Walk walk) {
                if (!loaded.test(walk.target())) {
                    return deny("Every walk target must be in a loaded chunk.");
                }
                if (!contains(premise, routeDimension, walk.target())) {
                    return deny("Bank tellers cannot walk outside the bank's claimed premises.");
                }
            } else if (step instanceof OwnerPcVaultRouteStepPayload.Redstone redstone) {
                OwnerPcVaultRoutePosition relay = adjacent(redstone.target(), redstone.face());
                if (!loaded.test(redstone.target()) || !loaded.test(relay)) {
                    return deny("Every redstone target and face-adjacent relay must be loaded.");
                }
            } else if (step instanceof OwnerPcVaultRouteStepPayload.Rfid rfid) {
                if (!loaded.test(rfid.scanner())) {
                    return deny("Every RFID scanner must be in a loaded chunk.");
                }
                if (!contains(premise, routeDimension, rfid.scanner())) {
                    return deny("RFID scanners can only be linked inside the bank's claimed premises.");
                }
                if (!rfidScanner.test(rfid.scanner())) {
                    return deny("The selected RFID route target is not an RFID scanner.");
                }
            }
        }
        return switch (draft.direction()) {
            case OUTBOUND -> nearTeller(draft.start(), tellerX, tellerY, tellerZ)
                    && contains(safeArea, routeDimension, draft.finish())
                    ? allow()
                    : deny("Outbound routes must start at the teller and finish inside the safe area.");
            case RETURN -> contains(safeArea, routeDimension, draft.start())
                    && nearTeller(draft.finish(), tellerX, tellerY, tellerZ)
                    ? allow()
                    : deny("Return routes must start inside the safe area and finish at the teller.");
        };
    }

    private static boolean contains(SafeBlockBounds bounds,
                                    String dimension,
                                    OwnerPcVaultRoutePosition position) {
        return bounds.contains(dimension, position.x(), position.y(), position.z());
    }

    private static boolean nearTeller(OwnerPcVaultRoutePosition position,
                                      double tellerX,
                                      double tellerY,
                                      double tellerZ) {
        double dx = position.x() + 0.5D - tellerX;
        double dy = position.y() - tellerY;
        double dz = position.z() + 0.5D - tellerZ;
        return dx * dx + dy * dy + dz * dz <= TELLER_POSITION_TOLERANCE_SQUARED;
    }

    private static OwnerPcVaultRoutePosition adjacent(
            OwnerPcVaultRoutePosition target,
            net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace face) {
        return switch (face) {
            case DOWN -> new OwnerPcVaultRoutePosition(target.x(), target.y() - 1, target.z());
            case UP -> new OwnerPcVaultRoutePosition(target.x(), target.y() + 1, target.z());
            case NORTH -> new OwnerPcVaultRoutePosition(target.x(), target.y(), target.z() - 1);
            case SOUTH -> new OwnerPcVaultRoutePosition(target.x(), target.y(), target.z() + 1);
            case WEST -> new OwnerPcVaultRoutePosition(target.x() - 1, target.y(), target.z());
            case EAST -> new OwnerPcVaultRoutePosition(target.x() + 1, target.y(), target.z());
        };
    }

    private static Result allow() {
        return new Result(true, "");
    }

    private static Result deny(String message) {
        return new Result(false, message);
    }
}
