package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardComponentDefinition {
    private final String id;
    private final String type;
    private final String title;
    private final String subtitle;
    private final String dataPath;
    private final String width;
    private final int order;
    private final Map<String, Object> options;
    private final List<DashboardComponentDefinition> children;
    private final List<DashboardActionDefinition> actions;

    private DashboardComponentDefinition(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.dataPath = builder.dataPath;
        this.width = builder.width;
        this.order = builder.order;
        this.options = Map.copyOf(builder.options);
        this.children = List.copyOf(builder.children);
        this.actions = List.copyOf(builder.actions);
    }

    public static Builder builder(String id, String type) {
        return new Builder(id, type);
    }

    public String id() { return id; }
    public String type() { return type; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public String dataPath() { return dataPath; }
    public String width() { return width; }
    public int order() { return order; }
    public Map<String, Object> options() { return options; }
    public List<DashboardComponentDefinition> children() { return children; }
    public List<DashboardActionDefinition> actions() { return actions; }

    public static final class Builder {
        private final String id;
        private final String type;
        private String title = "";
        private String subtitle = "";
        private String dataPath = "";
        private String width = "full";
        private int order;
        private final Map<String, Object> options = new LinkedHashMap<>();
        private final List<DashboardComponentDefinition> children = new ArrayList<>();
        private final List<DashboardActionDefinition> actions = new ArrayList<>();

        private Builder(String id, String type) {
            this.id = id;
            this.type = type;
        }

        public Builder title(String value) { this.title = value == null ? "" : value; return this; }
        public Builder subtitle(String value) { this.subtitle = value == null ? "" : value; return this; }
        public Builder dataPath(String value) { this.dataPath = value == null ? "" : value; return this; }
        public Builder width(String value) { this.width = value == null || value.isBlank() ? "full" : value; return this; }
        public Builder order(int value) { this.order = value; return this; }
        public Builder option(String key, Object value) { if (key != null && !key.isBlank()) this.options.put(key, value); return this; }
        public Builder child(DashboardComponentDefinition value) { if (value != null) this.children.add(value); return this; }
        public Builder action(DashboardActionDefinition value) { if (value != null) this.actions.add(value); return this; }

        public DashboardComponentDefinition build() {
            return new DashboardComponentDefinition(this);
        }
    }
}
