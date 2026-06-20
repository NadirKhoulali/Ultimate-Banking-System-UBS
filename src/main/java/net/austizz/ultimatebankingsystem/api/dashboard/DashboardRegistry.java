package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class DashboardRegistry implements UltimateBankingDashboardApi {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_][a-z0-9_.-]{1,63}");
    private final ConcurrentHashMap<String, DashboardDefinition> dashboards = new ConcurrentHashMap<>();

    @Override
    public String getDashboardApiVersion() {
        return "2.0.0";
    }

    @Override
    public DashboardRegistrationResult registerDashboard(DashboardDefinition dashboard) {
        String validation = validate(dashboard);
        if (!validation.isBlank()) {
            return DashboardRegistrationResult.fail(validation);
        }
        boolean replaced = dashboards.put(normalizeId(dashboard.modId()), dashboard) != null;
        return DashboardRegistrationResult.ok(dashboard, replaced);
    }

    @Override
    public Optional<DashboardDefinition> getDashboard(String modId) {
        return Optional.ofNullable(dashboards.get(normalizeId(modId)));
    }

    @Override
    public List<DashboardDefinition> getRegisteredDashboards() {
        ArrayList<DashboardDefinition> values = new ArrayList<>(dashboards.values());
        values.sort(Comparator.comparingInt(DashboardDefinition::order).thenComparing(DashboardDefinition::title));
        return List.copyOf(values);
    }

    public Optional<DashboardPanelDefinition> getPanel(String modId, String panelId) {
        return getDashboard(modId)
                .flatMap(dashboard -> dashboard.panels().stream()
                        .filter(panel -> panel.id().equals(normalizeId(panelId)))
                        .findFirst());
    }

    public Optional<DashboardWidgetDefinition> getWidget(String modId, String panelId, String widgetId) {
        return getPanel(modId, panelId)
                .flatMap(panel -> panel.widgets().stream()
                        .filter(widget -> widget.id().equals(normalizeId(widgetId)))
                .findFirst());
    }

    public Optional<DashboardPageDefinition> getPage(String modId, String pageId) {
        return getDashboard(modId)
                .flatMap(dashboard -> dashboard.pages().stream()
                        .filter(page -> page.id().equals(normalizeId(pageId)))
                        .findFirst());
    }

    private String validate(DashboardDefinition dashboard) {
        if (dashboard == null) {
            return "Dashboard definition is required.";
        }
        if (!isValidId(dashboard.modId())) {
            return "Dashboard mod id must be a lowercase identifier.";
        }
        if (dashboard.title() == null || dashboard.title().isBlank()) {
            return "Dashboard title is required.";
        }
        Set<String> panelIds = new HashSet<>();
        for (DashboardPanelDefinition panel : dashboard.panels()) {
            if (!isValidId(panel.id())) {
                return "Panel id must be a lowercase identifier: " + panel.id();
            }
            if (!panelIds.add(panel.id())) {
                return "Duplicate panel id: " + panel.id();
            }
            if (panel.title() == null || panel.title().isBlank()) {
                return "Panel title is required for " + panel.id();
            }
            Set<String> widgetIds = new HashSet<>();
            for (DashboardWidgetDefinition widget : panel.widgets()) {
                if (!isValidId(widget.id())) {
                    return "Widget id must be a lowercase identifier: " + widget.id();
                }
                if (!widgetIds.add(widget.id())) {
                    return "Duplicate widget id " + widget.id() + " in panel " + panel.id();
                }
                if (widget.type() == null || widget.type().isBlank()) {
                    return "Widget type is required for " + widget.id();
                }
            }
        }
        Set<String> pageIds = new HashSet<>();
        for (DashboardPageDefinition page : dashboard.pages()) {
            if (!isValidId(page.id())) {
                return "Page id must be a lowercase identifier: " + page.id();
            }
            if (!pageIds.add(page.id())) {
                return "Duplicate page id: " + page.id();
            }
            if (page.title() == null || page.title().isBlank()) {
                return "Page title is required for " + page.id();
            }
            Set<String> componentIds = new HashSet<>();
            for (DashboardComponentDefinition component : page.components()) {
                String problem = validateComponent(page.id(), component, componentIds);
                if (!problem.isBlank()) {
                    return problem;
                }
            }
        }
        return "";
    }

    private String validateComponent(String pageId, DashboardComponentDefinition component, Set<String> componentIds) {
        if (component == null) {
            return "Component is required in page " + pageId;
        }
        if (!isValidId(component.id())) {
            return "Component id must be a lowercase identifier: " + component.id();
        }
        if (!componentIds.add(component.id())) {
            return "Duplicate component id " + component.id() + " in page " + pageId;
        }
        if (component.type() == null || component.type().isBlank()) {
            return "Component type is required for " + component.id();
        }
        Set<String> actionIds = new HashSet<>();
        for (DashboardActionDefinition action : component.actions()) {
            if (!isValidId(action.id())) {
                return "Action id must be a lowercase identifier: " + action.id();
            }
            if (!actionIds.add(action.id())) {
                return "Duplicate action id " + action.id() + " in component " + component.id();
            }
        }
        for (DashboardComponentDefinition child : component.children()) {
            String problem = validateComponent(pageId, child, componentIds);
            if (!problem.isBlank()) {
                return problem;
            }
        }
        return "";
    }

    public static boolean isValidId(String value) {
        return value != null && ID_PATTERN.matcher(value).matches();
    }

    public static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
