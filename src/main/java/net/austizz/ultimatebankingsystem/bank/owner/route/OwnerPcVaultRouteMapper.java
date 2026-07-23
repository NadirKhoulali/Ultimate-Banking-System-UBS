package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePosition;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteStep;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteStepPayload;

import java.util.List;

public final class OwnerPcVaultRouteMapper {
    private OwnerPcVaultRouteMapper() {
    }

    public static SafeTellerRoute toDomain(OwnerPcVaultRouteSavePayload draft) {
        List<SafeTellerRouteStep> steps = draft.steps().stream()
                .map(OwnerPcVaultRouteMapper::toDomain)
                .toList();
        return SafeTellerRoute.create(
                draft.bankId().toString(),
                draft.vaultId(),
                draft.tellerId().toString(),
                draft.direction(),
                draft.dimension(),
                toDomain(draft.start()),
                toDomain(draft.finish()),
                steps);
    }

    private static SafeTellerRouteStep toDomain(OwnerPcVaultRouteStepPayload step) {
        if (step instanceof OwnerPcVaultRouteStepPayload.Walk walk) {
            return new SafeTellerRouteStep.Walk(toDomain(walk.target()));
        }
        if (step instanceof OwnerPcVaultRouteStepPayload.Wait wait) {
            return new SafeTellerRouteStep.Wait(wait.durationTicks());
        }
        if (step instanceof OwnerPcVaultRouteStepPayload.Rfid rfid) {
            return new SafeTellerRouteStep.Rfid(toDomain(rfid.scanner()));
        }
        OwnerPcVaultRouteStepPayload.Redstone redstone =
                (OwnerPcVaultRouteStepPayload.Redstone) step;
        return new SafeTellerRouteStep.Redstone(
                toDomain(redstone.target()), redstone.face(),
                redstone.strength(), redstone.durationTicks());
    }

    static OwnerPcVaultRoutePosition toNetwork(SafeTellerRoutePosition position) {
        return new OwnerPcVaultRoutePosition(position.x(), position.y(), position.z());
    }

    static List<OwnerPcVaultRouteStepPayload> toNetwork(List<SafeTellerRouteStep> steps) {
        return steps.stream().map(OwnerPcVaultRouteMapper::toNetwork).toList();
    }

    private static OwnerPcVaultRouteStepPayload toNetwork(SafeTellerRouteStep step) {
        if (step instanceof SafeTellerRouteStep.Walk walk) {
            return new OwnerPcVaultRouteStepPayload.Walk(toNetwork(walk.target()));
        }
        if (step instanceof SafeTellerRouteStep.Wait wait) {
            return new OwnerPcVaultRouteStepPayload.Wait(wait.durationTicks());
        }
        if (step instanceof SafeTellerRouteStep.Rfid rfid) {
            return new OwnerPcVaultRouteStepPayload.Rfid(toNetwork(rfid.scanner()));
        }
        SafeTellerRouteStep.Redstone redstone = (SafeTellerRouteStep.Redstone) step;
        return new OwnerPcVaultRouteStepPayload.Redstone(
                toNetwork(redstone.target()), redstone.face(),
                redstone.strength(), redstone.durationTicks());
    }

    private static SafeTellerRoutePosition toDomain(OwnerPcVaultRoutePosition position) {
        return new SafeTellerRoutePosition(position.x(), position.y(), position.z());
    }
}
