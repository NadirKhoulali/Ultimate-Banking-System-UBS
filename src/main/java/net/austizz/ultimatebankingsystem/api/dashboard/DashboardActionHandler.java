package net.austizz.ultimatebankingsystem.api.dashboard;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface DashboardActionHandler {
    DashboardResponse handle(DashboardRequestContext context, JsonObject body) throws Exception;
}
