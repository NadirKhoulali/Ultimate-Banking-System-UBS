package net.austizz.ultimatebankingsystem.api.dashboard;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;

public final class DashboardRequestContext {
    private final MinecraftServer server;
    private final String sessionId;
    private final String remoteAddress;
    private final String modId;
    private final String panelId;
    private final String widgetId;
    private final String method;
    private final String routePath;
    private final Map<String, List<String>> queryParameters;

    public DashboardRequestContext(
            MinecraftServer server,
            String sessionId,
            String remoteAddress,
            String modId,
            String panelId,
            String widgetId,
            String method,
            String routePath,
            Map<String, List<String>> queryParameters
    ) {
        this.server = server;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.remoteAddress = remoteAddress == null ? "" : remoteAddress;
        this.modId = modId == null ? "" : modId;
        this.panelId = panelId == null ? "" : panelId;
        this.widgetId = widgetId == null ? "" : widgetId;
        this.method = method == null ? "" : method;
        this.routePath = routePath == null ? "" : routePath;
        this.queryParameters = queryParameters == null ? Map.of() : Map.copyOf(queryParameters);
    }

    public MinecraftServer server() {
        return server;
    }

    public String sessionId() {
        return sessionId;
    }

    public String remoteAddress() {
        return remoteAddress;
    }

    public String modId() {
        return modId;
    }

    public String panelId() {
        return panelId;
    }

    public String widgetId() {
        return widgetId;
    }

    public String method() {
        return method;
    }

    public String routePath() {
        return routePath;
    }

    public Map<String, List<String>> queryParameters() {
        return queryParameters;
    }

    public String firstQueryValue(String key) {
        List<String> values = queryParameters.get(key);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }
}

