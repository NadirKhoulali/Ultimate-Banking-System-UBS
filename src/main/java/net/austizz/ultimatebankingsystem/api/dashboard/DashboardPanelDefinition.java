package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.ArrayList;
import java.util.List;

public final class DashboardPanelDefinition {
    private final String id;
    private final String title;
    private final String subtitle;
    private final int order;
    private final String nativeRoute;
    private final List<DashboardWidgetDefinition> widgets;

    private DashboardPanelDefinition(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.order = builder.order;
        this.nativeRoute = builder.nativeRoute;
        this.widgets = List.copyOf(builder.widgets);
    }

    public static Builder builder(String id, String title) {
        return new Builder(id, title);
    }

    public String id() { return id; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public int order() { return order; }
    public String nativeRoute() { return nativeRoute; }
    public List<DashboardWidgetDefinition> widgets() { return widgets; }

    public static final class Builder {
        private final String id;
        private final String title;
        private String subtitle = "";
        private int order;
        private String nativeRoute = "";
        private final List<DashboardWidgetDefinition> widgets = new ArrayList<>();

        private Builder(String id, String title) {
            this.id = id;
            this.title = title == null ? "" : title;
        }

        public Builder subtitle(String value) { this.subtitle = value == null ? "" : value; return this; }
        public Builder order(int value) { this.order = value; return this; }
        public Builder nativeRoute(String value) { this.nativeRoute = value == null ? "" : value; return this; }
        public Builder widget(DashboardWidgetDefinition value) { if (value != null) this.widgets.add(value); return this; }

        public DashboardPanelDefinition build() {
            return new DashboardPanelDefinition(this);
        }
    }
}
