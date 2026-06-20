package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.List;

public final class DashboardComponents {
    public static final String STACK = "stack";
    public static final String PANEL = "panel";
    public static final String TWO_COLUMN = "two-column";
    public static final String KPI_GROUP = "kpi-group";
    public static final String KPI_CARD = "kpi-card";
    public static final String ALERT_LIST = "alert-list";
    public static final String TABLE = "table";
    public static final String KEY_VALUE = "key-value";
    public static final String LINE_CHART = "line-chart";
    public static final String BAR_CHART = "bar-chart";
    public static final String STATUS_CHART = "status-chart";
    public static final String SHOP_TYPE_CHART = "shop-type-chart";
    public static final String HEALTH_METERS = "health-meters";
    public static final String ITEM_CARD_LIST = "item-card-list";
    public static final String CARD_CAROUSEL = "card-carousel";
    public static final String ROADMAP = "roadmap";
    public static final String ACTION_FORM = "action-form";
    public static final String ACTION_BUTTONS = "action-buttons";
    public static final String COMMAND_RUNNER = "command-runner";
    public static final String OUTPUT = "output";
    public static final String IFRAME = "iframe";

    private DashboardComponents() {
    }

    public static DashboardComponentDefinition.Builder stack(String id) {
        return DashboardComponentDefinition.builder(id, STACK);
    }

    public static DashboardComponentDefinition.Builder panel(String id, String title) {
        return DashboardComponentDefinition.builder(id, PANEL).title(title);
    }

    public static DashboardComponentDefinition.Builder twoColumn(String id) {
        return DashboardComponentDefinition.builder(id, TWO_COLUMN);
    }

    public static DashboardComponentDefinition.Builder kpiGroup(String id, String dataPath, List<?> cards) {
        return DashboardComponentDefinition.builder(id, KPI_GROUP)
                .dataPath(dataPath)
                .option("cards", cards);
    }

    public static DashboardComponentDefinition.Builder table(String id, String title, String dataPath, List<?> columns) {
        return panel(id, title)
                .child(DashboardComponentDefinition.builder(id + "-table", TABLE)
                        .dataPath(dataPath)
                        .option("columns", columns)
                        .build());
    }

    public static DashboardComponentDefinition.Builder chartPanel(String id, String title, String type, String dataPath) {
        return panel(id, title)
                .child(DashboardComponentDefinition.builder(id + "-chart", type)
                        .dataPath(dataPath)
                        .build());
    }
}
