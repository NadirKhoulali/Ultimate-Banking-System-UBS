package net.austizz.ultimatebankingsystem.bank.safebox.route;

public record SafeTellerRouteSaveResult(Status status,
                                        SafeTellerRoute route,
                                        SafeTellerRouteValidation validation) {
    public boolean success() {
        return status == Status.SAVED;
    }

    static SafeTellerRouteSaveResult saved(SafeTellerRoute route) {
        return new SafeTellerRouteSaveResult(Status.SAVED, route,
                SafeTellerRouteValidator.validate(route));
    }

    static SafeTellerRouteSaveResult failure(Status status,
                                             SafeTellerRoute route,
                                             SafeTellerRouteValidation validation) {
        return new SafeTellerRouteSaveResult(status, route, validation);
    }

    public enum Status {
        SAVED,
        INVALID_ROUTE,
        METADATA_MISSING,
        ROUTE_STORAGE_MALFORMED,
        PREMISES_MALFORMED,
        VAULT_NOT_FOUND,
        VAULT_AMBIGUOUS,
        ROUTE_HOOKS_MALFORMED,
        ROUTE_HOOK_AMBIGUOUS
    }
}
