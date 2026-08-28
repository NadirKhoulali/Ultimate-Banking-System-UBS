package net.austizz.ultimatebankingsystem.api.dashboard;

import java.util.ArrayList;
import java.util.List;

public final class DashboardDefinition {
    private final String modId;
    private final String title;
    private final String subtitle;
    private final String icon;
    private final int order;
    private final Class<?> resourceAnchor;
    private final String resourceRoot;
    private final List<DashboardPanelDefinition> panels;
    private final List<DashboardPageDefinition> pages;
    private final DashboardLayoutDefaults defaults;

    private DashboardDefinition(Builder builder) {
        this.modId = builder.modId;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.icon = builder.icon;
        this.order = builder.order;
        this.resourceAnchor = builder.resourceAnchor;
        this.resourceRoot = builder.resourceRoot;
        this.panels = List.copyOf(builder.panels);
        this.pages = List.copyOf(builder.pages);
        this.defaults = builder.defaults;
    }

    public static Builder builder(String modId, String title) {
        return new Builder(modId, title);
    }

    public String modId() { return modId; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public String icon() { return icon; }
    public int order() { return order; }
    public Class<?> resourceAnchor() { return resourceAnchor; }
    public String resourceRoot() { return resourceRoot; }
    public List<DashboardPanelDefinition> panels() { return panels; }
    public List<DashboardPageDefinition> pages() { return pages; }
    public DashboardLayoutDefaults defaults() { return defaults; }

    public static final class Builder {
        private final String modId;
        private final String title;
        private String subtitle = "";
        private String icon = "";
        private int order;
        private Class<?> resourceAnchor;
        private String resourceRoot = "";
        private final List<DashboardPanelDefinition> panels = new ArrayList<>();
        private final List<DashboardPageDefinition> pages = new ArrayList<>();
        private DashboardLayoutDefaults defaults = DashboardLayoutDefaults.ubs();

        private Builder(String modId, String title) {
            this.modId = modId;
            this.title = title == null ? "" : title;
        }

        public Builder subtitle(String value) { this.subtitle = value == null ? "" : value; return this; }
        public Builder icon(String value) { this.icon = value == null ? "" : value; return this; }
        public Builder order(int value) { this.order = value; return this; }
        public Builder resourceRoot(Class<?> anchor, String root) {
            this.resourceAnchor = anchor;
            this.resourceRoot = root == null ? "" : root;
            return this;
        }
        public Builder panel(DashboardPanelDefinition value) { if (value != null) this.panels.add(value); return this; }
        public Builder page(DashboardPageDefinition value) { if (value != null) this.pages.add(value); return this; }
        public Builder defaults(DashboardLayoutDefaults value) { if (value != null) this.defaults = value; return this; }

        public DashboardDefinition build() {
            return new DashboardDefinition(this);
        }
    }
}

