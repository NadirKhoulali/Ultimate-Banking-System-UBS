package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DashboardLayoutDefaults {
    private final Map<String, Object> values;

    private DashboardLayoutDefaults(Builder builder) {
        this.values = Map.copyOf(builder.values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DashboardLayoutDefaults ubs() {
        return builder()
                .value("density", "comfortable")
                .value("sectionGap", 12)
                .value("panelPadding", 16)
                .value("kpiColumns", 6)
                .value("compactKpiColumns", 4)
                .value("twoColumnMinWidth", 760)
                .value("tableMaxHeight", 420)
                .value("chartHeight", 250)
                .build();
    }

    public Map<String, Object> values() {
        return values;
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder value(String key, Object value) {
            if (key != null && !key.isBlank()) {
                values.put(key, value);
            }
            return this;
        }

        public DashboardLayoutDefaults build() {
            return new DashboardLayoutDefaults(this);
        }
    }
}
