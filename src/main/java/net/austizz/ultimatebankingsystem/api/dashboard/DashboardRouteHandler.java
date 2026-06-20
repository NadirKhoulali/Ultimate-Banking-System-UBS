package net.austizz.ultimatebankingsystem.api.dashboard;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface DashboardRouteHandler {
    DashboardResponse handle(DashboardRequestContext context, String routePath, JsonObject body) throws Exception;
}
