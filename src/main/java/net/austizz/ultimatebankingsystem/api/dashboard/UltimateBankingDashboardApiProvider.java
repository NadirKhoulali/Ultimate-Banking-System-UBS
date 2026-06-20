package net.austizz.ultimatebankingsystem.api.dashboard;

public final class UltimateBankingDashboardApiProvider {
    private static final DashboardRegistry REGISTRY = new DashboardRegistry();

    private UltimateBankingDashboardApiProvider() {
    }

    public static UltimateBankingDashboardApi get() {
        return REGISTRY;
    }

    public static DashboardRegistry registry() {
        return REGISTRY;
    }
}
