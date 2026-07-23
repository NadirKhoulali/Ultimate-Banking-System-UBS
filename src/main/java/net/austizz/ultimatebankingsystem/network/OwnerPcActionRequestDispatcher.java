package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;

import java.util.Objects;
import java.util.UUID;

public final class OwnerPcActionRequestDispatcher {
    public record Request(UUID bankId,
                          String action,
                          String arg1,
                          String arg2,
                          String arg3,
                          String arg4) {
    }

    public record Response(UUID bankId, boolean success, String message) {
    }

    public interface Ports {
        BankOwnerPcService.ActionResult executeDirect(Request request);

        void sendResponse(Response response);

        void refreshBankData(UUID bankId);
    }

    private OwnerPcActionRequestDispatcher() {
    }

    public static void dispatch(Request request, Ports ports) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(ports, "ports");
        BankOwnerPcService.ActionResult result = ports.executeDirect(request);
        if (result == null) {
            result = new BankOwnerPcService.ActionResult(false, "Bank action failed without a response.");
        }
        ports.sendResponse(new Response(request.bankId(), result.success(), result.message()));
        if (!result.success()) {
            return;
        }
        ports.refreshBankData(request.bankId());
    }
}
