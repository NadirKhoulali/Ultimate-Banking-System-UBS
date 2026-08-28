package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.List;
import java.util.Optional;

public interface UltimateBankingDashboardApi {
    String getDashboardApiVersion();

    DashboardRegistrationResult registerDashboard(DashboardDefinition dashboard);

    Optional<DashboardDefinition> getDashboard(String modId);

    List<DashboardDefinition> getRegisteredDashboards();
}
