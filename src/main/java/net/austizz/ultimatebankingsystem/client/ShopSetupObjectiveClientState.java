package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.BankSetupObjectivesPayload;
import net.austizz.ultimatebankingsystem.network.ShopSetupObjectivePayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side state for the guided "shop requirements" objective card.
 */
public final class ShopSetupObjectiveClientState {
    private static boolean collapsed;
    private static boolean dismissed;
    private static final Map<String, Project> PROJECTS = new LinkedHashMap<>();
    private static String selectedKey = "";

    private ShopSetupObjectiveClientState() {
    }

    public static void set(boolean nextActive,
                           String nextShopName,
                           int nextStep,
                           int nextTotalSteps,
                           String nextObjectiveTitle,
                           String nextObjectiveDetail,
                           List<ShopSetupObjectivePayload.RequirementProgress> nextRequirements) {
        boolean wasEmpty = PROJECTS.isEmpty();
        if (!nextActive) {
            clearShopProject();
            return;
        }
        PROJECTS.put("shop", new Project(
                "shop",
                "Store",
                sanitize(nextShopName),
                Math.max(1, nextStep),
                Math.max(Math.max(1, nextStep), nextTotalSteps),
                sanitize(nextObjectiveTitle),
                sanitize(nextObjectiveDetail),
                nextRequirements == null ? List.of() : List.copyOf(nextRequirements)
        ));
        normalizeSelection();
        if (wasEmpty) {
            dismissed = false;
        }
    }

    public static void replaceBankProjects(List<BankSetupObjectivesPayload.Project> projects) {
        boolean wasEmpty = PROJECTS.isEmpty();
        PROJECTS.keySet().removeIf(key -> key.startsWith("bank:"));
        if (projects != null) {
            for (BankSetupObjectivesPayload.Project project : projects) {
                if (project == null || project.projectId().isBlank()) {
                    continue;
                }
                String key = "bank:" + project.projectId();
                PROJECTS.put(key, new Project(
                        key,
                        "Bank",
                        sanitize(project.projectName()),
                        project.step(),
                        project.totalSteps(),
                        sanitize(project.objectiveTitle()),
                        sanitize(project.objectiveDetail()),
                        List.of()
                ));
            }
        }
        normalizeSelection();
        if (wasEmpty && !PROJECTS.isEmpty()) {
            dismissed = false;
        }
    }

    public static void clearShopProject() {
        PROJECTS.remove("shop");
        normalizeSelection();
    }

    public static void clear() {
        collapsed = false;
        dismissed = false;
        PROJECTS.clear();
        selectedKey = "";
    }

    public static boolean isActive() {
        return !PROJECTS.isEmpty() && !dismissed;
    }

    public static boolean isCollapsed() {
        return collapsed;
    }

    public static void toggleCollapsed() {
        if (PROJECTS.isEmpty()) {
            return;
        }
        collapsed = !collapsed;
    }

    public static void dismiss() {
        if (PROJECTS.isEmpty()) {
            return;
        }
        dismissed = true;
        collapsed = false;
    }

    public static String getShopName() {
        return selected().name();
    }

    public static String getProjectType() {
        return selected().type();
    }

    public static int getProjectCount() {
        return PROJECTS.size();
    }

    public static int getSelectedProjectIndex() {
        if (PROJECTS.isEmpty()) {
            return 0;
        }
        List<String> keys = new ArrayList<>(PROJECTS.keySet());
        int index = keys.indexOf(selectedKey);
        return index < 0 ? 0 : index;
    }

    public static void cycleProject() {
        if (PROJECTS.size() < 2) {
            return;
        }
        List<String> keys = new ArrayList<>(PROJECTS.keySet());
        int current = keys.indexOf(selectedKey);
        selectedKey = keys.get((Math.max(0, current) + 1) % keys.size());
        dismissed = false;
    }

    public static int getStep() {
        return selected().step();
    }

    public static int getTotalSteps() {
        return selected().totalSteps();
    }

    public static String getObjectiveTitle() {
        return selected().title();
    }

    public static String getObjectiveDetail() {
        return selected().detail();
    }

    public static List<ShopSetupObjectivePayload.RequirementProgress> getRequirements() {
        return selected().requirements();
    }

    private static Project selected() {
        normalizeSelection();
        Project selected = PROJECTS.get(selectedKey);
        return selected == null ? Project.EMPTY : selected;
    }

    private static void normalizeSelection() {
        if (PROJECTS.isEmpty()) {
            selectedKey = "";
            collapsed = false;
            dismissed = false;
            return;
        }
        if (!PROJECTS.containsKey(selectedKey)) {
            selectedKey = PROJECTS.keySet().iterator().next();
        }
    }

    private static String sanitize(String text) {
        return text == null ? "" : text.trim();
    }

    private record Project(String key,
                           String type,
                           String name,
                           int step,
                           int totalSteps,
                           String title,
                           String detail,
                           List<ShopSetupObjectivePayload.RequirementProgress> requirements) {
        private static final Project EMPTY = new Project("", "Setup", "", 1, 1, "", "", List.of());

        private Project {
            key = sanitize(key);
            type = sanitize(type);
            name = sanitize(name);
            totalSteps = Math.max(1, totalSteps);
            step = Math.max(1, Math.min(step, totalSteps));
            title = sanitize(title);
            detail = sanitize(detail);
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }
    }
}
