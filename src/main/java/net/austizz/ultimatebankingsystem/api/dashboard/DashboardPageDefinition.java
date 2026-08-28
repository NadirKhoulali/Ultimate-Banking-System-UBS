package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.ArrayList;
import java.util.List;

public final class DashboardPageDefinition {
    private final String id;
    private final String title;
    private final String subtitle;
    private final String routePattern;
    private final String dataUrl;
    private final int order;
    private final List<DashboardComponentDefinition> components;

    private DashboardPageDefinition(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.routePattern = builder.routePattern;
        this.dataUrl = builder.dataUrl;
        this.order = builder.order;
        this.components = List.copyOf(builder.components);
    }

    public static Builder builder(String id, String title) {
        return new Builder(id, title);
    }

    public String id() { return id; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public String routePattern() { return routePattern; }
    public String dataUrl() { return dataUrl; }
    public int order() { return order; }
    public List<DashboardComponentDefinition> components() { return components; }

    public static final class Builder {
        private final String id;
        private final String title;
        private String subtitle = "";
        private String routePattern = "";
        private String dataUrl = "";
        private int order;
        private final List<DashboardComponentDefinition> components = new ArrayList<>();

        private Builder(String id, String title) {
            this.id = id;
            this.title = title == null ? "" : title;
        }

        public Builder subtitle(String value) { this.subtitle = value == null ? "" : value; return this; }
        public Builder routePattern(String value) { this.routePattern = value == null ? "" : value; return this; }
        public Builder dataUrl(String value) { this.dataUrl = value == null ? "" : value; return this; }
        public Builder order(int value) { this.order = value; return this; }
        public Builder component(DashboardComponentDefinition value) { if (value != null) this.components.add(value); return this; }

        public DashboardPageDefinition build() {
            return new DashboardPageDefinition(this);
        }
    }
}

