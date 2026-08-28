package net.austizz.ultimatebankingsystem.migration.numismatics;

public record NumismaticsMigrationOptions(
        int centsPerSpur,
        Scope scope,
        boolean convertBankCards,
        boolean allowUnsafeAccountOnlyRemoval
) {
    public NumismaticsMigrationOptions {
        if (centsPerSpur < 1 || centsPerSpur > 100_000_000) {
            throw new IllegalArgumentException("The exchange rate must be between $0.01 and $1,000,000 per Spur.");
        }
        scope = scope == null ? Scope.ACCOUNTS_ONLY : scope;
    }

    public static NumismaticsMigrationOptions defaults() {
        return new NumismaticsMigrationOptions(100, Scope.FULL_ECONOMY, true, false);
    }

    public enum Scope {
        ACCOUNTS_ONLY,
        FULL_ECONOMY
    }
}
