package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DashboardWidgetDefinition {
    private final String id;
    private final String type;
    private final String title;
    private final String subtitle;
    private final String width;
    private final int order;
    private final String iframePath;
    private final Map<String, Object> options;
    private final DashboardDataProvider dataProvider;
    private final DashboardActionHandler actionHandler;
    private final DashboardRouteHandler routeHandler;

    private DashboardWidgetDefinition(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.width = builder.width;
        this.order = builder.order;
        this.iframePath = builder.iframePath;
        this.options = Map.copyOf(builder.options);
        this.dataProvider = builder.dataProvider;
        this.actionHandler = builder.actionHandler;
        this.routeHandler = builder.routeHandler;
    }

    public static Builder builder(String id, String type) {
        return new Builder(id, type);
    }

    public String id() { return id; }
    public String type() { return type; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public String width() { return width; }
    public int order() { return order; }
    public String iframePath() { return iframePath; }
    public Map<String, Object> options() { return options; }
    public DashboardDataProvider dataProvider() { return dataProvider; }
    public DashboardActionHandler actionHandler() { return actionHandler; }
    public DashboardRouteHandler routeHandler() { return routeHandler; }

    public static final class Builder {
        private final String id;
        private final String type;
        private String title = "";
        private String subtitle = "";
        private String width = "full";
        private int order;
        private String iframePath = "";
        private final Map<String, Object> options = new LinkedHashMap<>();
        private DashboardDataProvider dataProvider;
        private DashboardActionHandler actionHandler;
        private DashboardRouteHandler routeHandler;

        private Builder(String id, String type) {
            this.id = id;
            this.type = type;
        }

        public Builder title(String value) { this.title = value == null ? "" : value; return this; }
        public Builder subtitle(String value) { this.subtitle = value == null ? "" : value; return this; }
        public Builder width(String value) { this.width = value == null || value.isBlank() ? "full" : value; return this; }
        public Builder order(int value) { this.order = value; return this; }
        public Builder iframePath(String value) { this.iframePath = value == null ? "" : value; return this; }
        public Builder option(String key, Object value) { if (key != null && !key.isBlank()) this.options.put(key, value); return this; }
        public Builder dataProvider(DashboardDataProvider value) { this.dataProvider = value; return this; }
        public Builder actionHandler(DashboardActionHandler value) { this.actionHandler = value; return this; }
        public Builder routeHandler(DashboardRouteHandler value) { this.routeHandler = value; return this; }

        public DashboardWidgetDefinition build() {
            return new DashboardWidgetDefinition(this);
        }
    }
}
