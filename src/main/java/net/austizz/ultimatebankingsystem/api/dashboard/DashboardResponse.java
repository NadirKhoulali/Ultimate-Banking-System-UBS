package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.Map;

public final class DashboardResponse {
    private final int statusCode;
    private final Object payload;

    private DashboardResponse(int statusCode, Object payload) {
        this.statusCode = statusCode;
        this.payload = payload;
    }

    public static DashboardResponse ok(Object payload) {
        return new DashboardResponse(200, payload);
    }

    public static DashboardResponse badRequest(String message) {
        return new DashboardResponse(400, Map.of("ok", false, "message", message));
    }

    public static DashboardResponse notFound(String message) {
        return new DashboardResponse(404, Map.of("ok", false, "message", message));
    }

    public static DashboardResponse methodNotAllowed(String message) {
        return new DashboardResponse(405, Map.of("ok", false, "message", message));
    }

    public static DashboardResponse serverError(String message) {
        return new DashboardResponse(500, Map.of("ok", false, "message", message));
    }

    public int statusCode() {
        return statusCode;
    }

    public Object payload() {
        return payload;
    }
}

