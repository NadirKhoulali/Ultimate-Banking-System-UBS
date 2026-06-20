package net.austizz.ultimatebankingsystem.api.dashboard;

@FunctionalInterface
public interface DashboardDataProvider {
    Object provide(DashboardRequestContext context) throws Exception;
}
