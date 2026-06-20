package net.austizz.ultimatebankingsystem.api.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardRegistryTest {
    @Test
    void registersValidDashboard() {
        DashboardRegistry registry = new DashboardRegistry();

        DashboardRegistrationResult result = registry.registerDashboard(DashboardDefinition.builder("auctionaddon", "Auction Addon")
                .panel(DashboardPanelDefinition.builder("overview", "Overview")
                        .widget(DashboardWidgetDefinition.builder("sales", DashboardWidgetType.KPI_GRID).build())
                        .build())
                .build());

        assertTrue(result.success(), result.message());
        assertTrue(registry.getDashboard("auctionaddon").isPresent());
    }

    @Test
    void rejectsDuplicatePanelIds() {
        DashboardRegistry registry = new DashboardRegistry();

        DashboardRegistrationResult result = registry.registerDashboard(DashboardDefinition.builder("auctionaddon", "Auction Addon")
                .panel(DashboardPanelDefinition.builder("overview", "Overview").build())
                .panel(DashboardPanelDefinition.builder("overview", "Duplicate").build())
                .build());

        assertFalse(result.success());
    }

    @Test
    void rejectsDuplicateWidgetIdsInsidePanel() {
        DashboardRegistry registry = new DashboardRegistry();

        DashboardRegistrationResult result = registry.registerDashboard(DashboardDefinition.builder("auctionaddon", "Auction Addon")
                .panel(DashboardPanelDefinition.builder("overview", "Overview")
                        .widget(DashboardWidgetDefinition.builder("sales", DashboardWidgetType.KPI_GRID).build())
                        .widget(DashboardWidgetDefinition.builder("sales", DashboardWidgetType.TABLE).build())
                        .build())
                .build());

        assertFalse(result.success());
    }

    @Test
    void registersValidComponentDashboard() {
        DashboardRegistry registry = new DashboardRegistry();

        DashboardRegistrationResult result = registry.registerDashboard(DashboardDefinition.builder("auctionaddon", "Auction Addon")
                .page(DashboardPageDefinition.builder("overview", "Overview")
                        .dataUrl("/api/auction/overview")
                        .component(DashboardComponents.kpiGroup("sales_kpis", "metrics", java.util.List.of()).build())
                        .component(DashboardComponents.twoColumn("market_split")
                                .child(DashboardComponents.table("top_sales", "Top Sales", "sales", java.util.List.of()).build())
                                .child(DashboardComponents.table("top_buyers", "Top Buyers", "buyers", java.util.List.of()).build())
                                .build())
                        .build())
                .build());

        assertTrue(result.success(), result.message());
        assertTrue(registry.getPage("auctionaddon", "overview").isPresent());
    }

    @Test
    void rejectsDuplicateComponentIdsInsidePage() {
        DashboardRegistry registry = new DashboardRegistry();

        DashboardRegistrationResult result = registry.registerDashboard(DashboardDefinition.builder("auctionaddon", "Auction Addon")
                .page(DashboardPageDefinition.builder("overview", "Overview")
                        .component(DashboardComponentDefinition.builder("sales", DashboardComponents.KPI_GROUP).build())
                        .component(DashboardComponentDefinition.builder("sales", DashboardComponents.TABLE).build())
                        .build())
                .build());

        assertFalse(result.success());
    }

    @Test
    void rejectsDuplicateActionIdsInsideComponent() {
        DashboardRegistry registry = new DashboardRegistry();

        DashboardRegistrationResult result = registry.registerDashboard(DashboardDefinition.builder("auctionaddon", "Auction Addon")
                .page(DashboardPageDefinition.builder("overview", "Overview")
                        .component(DashboardComponentDefinition.builder("tools", DashboardComponents.ACTION_BUTTONS)
                                .action(DashboardActionDefinition.builder("refresh", "Refresh").build())
                                .action(DashboardActionDefinition.builder("refresh", "Refresh Again").build())
                                .build())
                        .build())
                .build());

        assertFalse(result.success());
    }
}
