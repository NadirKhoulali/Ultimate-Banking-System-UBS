package net.austizz.ultimatebankingsystem.api.dashboard;

public final class DashboardRegistrationResult {
    private final boolean success;
    private final String message;
    private final DashboardDefinition dashboard;

    private DashboardRegistrationResult(boolean success, String message, DashboardDefinition dashboard) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.dashboard = dashboard;
    }

    public static DashboardRegistrationResult ok(DashboardDefinition dashboard, boolean replaced) {
        return new DashboardRegistrationResult(true, replaced ? "Dashboard replaced." : "Dashboard registered.", dashboard);
    }

    public static DashboardRegistrationResult fail(String message) {
        return new DashboardRegistrationResult(false, message, null);
    }

    public boolean success() {
        return success;
    }

    public String message() {
        return message;
    }

    public DashboardDefinition dashboard() {
        return dashboard;
    }
}

