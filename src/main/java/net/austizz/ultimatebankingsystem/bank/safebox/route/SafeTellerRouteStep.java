package net.austizz.ultimatebankingsystem.bank.safebox.route;

public sealed interface SafeTellerRouteStep
        permits SafeTellerRouteStep.Walk, SafeTellerRouteStep.Wait,
                SafeTellerRouteStep.Redstone, SafeTellerRouteStep.Rfid {
    record Walk(SafeTellerRoutePosition target) implements SafeTellerRouteStep {
    }

    record Wait(int durationTicks) implements SafeTellerRouteStep {
    }

    record Redstone(SafeTellerRoutePosition target,
                    SafeTellerRouteFace face,
                    int strength,
                    int durationTicks) implements SafeTellerRouteStep {
    }

    record Rfid(SafeTellerRoutePosition scanner) implements SafeTellerRouteStep {
    }
}
