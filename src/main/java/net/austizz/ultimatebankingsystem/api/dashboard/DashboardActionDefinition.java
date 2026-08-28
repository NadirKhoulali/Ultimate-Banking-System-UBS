package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DashboardActionDefinition {
    private final String id;
    private final String label;
    private final String method;
    private final String endpoint;
    private final String confirm;
    private final boolean refreshPage;
    private final Map<String, Object> options;

    private DashboardActionDefinition(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.method = builder.method;
        this.endpoint = builder.endpoint;
        this.confirm = builder.confirm;
        this.refreshPage = builder.refreshPage;
        this.options = Map.copyOf(builder.options);
    }

    public static Builder builder(String id, String label) {
        return new Builder(id, label);
    }

    public String id() { return id; }
    public String label() { return label; }
    public String method() { return method; }
    public String endpoint() { return endpoint; }
    public String confirm() { return confirm; }
    public boolean refreshPage() { return refreshPage; }
    public Map<String, Object> options() { return options; }

    public static final class Builder {
        private final String id;
        private final String label;
        private String method = "POST";
        private String endpoint = "";
        private String confirm = "";
        private boolean refreshPage = true;
        private final Map<String, Object> options = new LinkedHashMap<>();

        private Builder(String id, String label) {
            this.id = id;
            this.label = label == null ? "" : label;
        }

        public Builder method(String value) { this.method = value == null || value.isBlank() ? "POST" : value; return this; }
        public Builder endpoint(String value) { this.endpoint = value == null ? "" : value; return this; }
        public Builder confirm(String value) { this.confirm = value == null ? "" : value; return this; }
        public Builder refreshPage(boolean value) { this.refreshPage = value; return this; }
        public Builder option(String key, Object value) { if (key != null && !key.isBlank()) this.options.put(key, value); return this; }

        public DashboardActionDefinition build() {
            return new DashboardActionDefinition(this);
        }
    }
}

