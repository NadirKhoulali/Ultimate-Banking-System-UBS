package net.austizz.ultimatebankingsystem.gui.screens;

import java.util.ArrayList;
import java.util.List;

final class OwnerPcShopBusinessPanelLayout {
    static final int WIDE_BREAKPOINT = 900;
    static final int THREE_ACTION_BREAKPOINT = 520;
    static final int TWO_ACTION_BREAKPOINT = 340;
    static final int TWO_METRIC_BREAKPOINT = 420;
    static final int GAP = 12;
    static final int METRIC_GAP = 10;
    static final int METRIC_HEIGHT = 62;
    static final int BOTTOM_PADDING = 24;

    private static final int PANEL_INSET = 14;
    private static final int SUMMARY_Y = 52;
    private static final int SUMMARY_HEIGHT = 42;
    private static final int ACTION_HEIGHT = 24;
    private static final int ACTION_GAP = 8;

    private OwnerPcShopBusinessPanelLayout() {
    }

    static Dimensions dimensions(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }

        HeaderControls controls = headerControls(width);
        int headerHeight = Math.max(
                controls.typeSummary().bottom(),
                Math.max(controls.refresh().bottom(),
                        Math.max(controls.payFees().bottom(), controls.changeType().bottom()))) + 12;
        int metricColumns = width >= WIDE_BREAKPOINT ? 4 : width >= TWO_METRIC_BREAKPOINT ? 2 : 1;
        int metricRows = (4 + metricColumns - 1) / metricColumns;
        int metricHeight = (metricRows * METRIC_HEIGHT) + ((metricRows - 1) * METRIC_GAP);
        boolean wide = width >= WIDE_BREAKPOINT;
        int actionWidth = wide ? Math.min(320, Math.max(270, width / 3)) : width;
        int mainWidth = wide ? Math.max(1, width - actionWidth - GAP) : width;
        return new Dimensions(wide, headerHeight, metricColumns, metricHeight, mainWidth, actionWidth, controls);
    }

    static HeaderControls headerControls(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }

        int inset = Math.min(PANEL_INSET, Math.max(0, (width - 1) / 2));
        int innerWidth = Math.max(1, width - (inset * 2));
        Rect summary;
        Rect refresh;
        Rect payFees;
        Rect changeType;

        if (width >= WIDE_BREAKPOINT) {
            int summaryWidth = Math.min(300, Math.max(220, innerWidth / 3));
            summary = new Rect(inset, SUMMARY_Y, summaryWidth, SUMMARY_HEIGHT);
            int actionX = inset + summaryWidth + GAP;
            int availableWidth = Math.max(3, innerWidth - summaryWidth - GAP);
            List<Rect> actions = row(actionX, 61, availableWidth, 3, ACTION_GAP, ACTION_HEIGHT);
            refresh = actions.get(0);
            payFees = actions.get(1);
            changeType = actions.get(2);
        } else {
            summary = new Rect(inset, SUMMARY_Y, innerWidth, SUMMARY_HEIGHT);
            int actionY = SUMMARY_Y + SUMMARY_HEIGHT + 10;
            if (width >= THREE_ACTION_BREAKPOINT) {
                List<Rect> actions = row(inset, actionY, innerWidth, 3, ACTION_GAP, ACTION_HEIGHT);
                refresh = actions.get(0);
                payFees = actions.get(1);
                changeType = actions.get(2);
            } else if (width >= TWO_ACTION_BREAKPOINT) {
                List<Rect> firstRow = row(inset, actionY, innerWidth, 2, ACTION_GAP, ACTION_HEIGHT);
                refresh = firstRow.get(0);
                payFees = firstRow.get(1);
                changeType = new Rect(inset, actionY + ACTION_HEIGHT + 6, innerWidth, ACTION_HEIGHT);
            } else {
                refresh = new Rect(inset, actionY, innerWidth, ACTION_HEIGHT);
                payFees = new Rect(inset, actionY + ACTION_HEIGHT + 6, innerWidth, ACTION_HEIGHT);
                changeType = new Rect(inset, actionY + ((ACTION_HEIGHT + 6) * 2), innerWidth, ACTION_HEIGHT);
            }
        }
        return new HeaderControls(summary, refresh, payFees, changeType);
    }

    static Layout layout(int width,
                         int viewportHeight,
                         int mainHeight,
                         int actionHeight,
                         int requestedScroll) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (mainHeight < 0 || actionHeight < 0) {
            throw new IllegalArgumentException("section heights must not be negative");
        }

        Dimensions dimensions = dimensions(width);
        Rect header = new Rect(0, 0, width, dimensions.headerHeight());
        int metricY = header.bottom() + GAP;
        List<Rect> metrics = metricRects(width, metricY, dimensions.metricColumns());
        int bodyY = metricY + dimensions.metricHeight() + 18;
        Rect main = new Rect(0, bodyY, dimensions.mainWidth(), Math.max(1, mainHeight));
        Rect actions;
        int bodyBottom;
        if (dimensions.wide()) {
            actions = new Rect(dimensions.mainWidth() + GAP, bodyY,
                    dimensions.actionWidth(), Math.max(1, actionHeight));
            bodyBottom = Math.max(main.bottom(), actions.bottom());
        } else {
            actions = new Rect(0, main.bottom() + GAP, width, Math.max(1, actionHeight));
            bodyBottom = actions.bottom();
        }

        int contentHeight = saturatingInt((long) bodyBottom + BOTTOM_PADDING);
        int safeViewportHeight = Math.max(0, viewportHeight);
        int maxScroll = Math.max(0, contentHeight - safeViewportHeight);
        int clampedScroll = Math.max(0, Math.min(requestedScroll, maxScroll));
        return new Layout(dimensions, header, metrics, main, actions, contentHeight, maxScroll, clampedScroll);
    }

    private static List<Rect> metricRects(int width, int y, int columns) {
        List<Rect> result = new ArrayList<>(4);
        int baseWidth = Math.max(1, (width - (METRIC_GAP * (columns - 1))) / columns);
        for (int index = 0; index < 4; index++) {
            int column = index % columns;
            int row = index / columns;
            int x = column * (baseWidth + METRIC_GAP);
            int cardWidth = column == columns - 1 ? Math.max(1, width - x) : baseWidth;
            result.add(new Rect(x, y + (row * (METRIC_HEIGHT + METRIC_GAP)), cardWidth, METRIC_HEIGHT));
        }
        return List.copyOf(result);
    }

    private static List<Rect> row(int x, int y, int width, int columns, int gap, int height) {
        List<Rect> result = new ArrayList<>(columns);
        int baseWidth = Math.max(1, (width - (gap * (columns - 1))) / columns);
        for (int column = 0; column < columns; column++) {
            int columnX = x + (column * (baseWidth + gap));
            int columnWidth = column == columns - 1
                    ? Math.max(1, x + width - columnX)
                    : baseWidth;
            result.add(new Rect(columnX, y, columnWidth, height));
        }
        return result;
    }

    private static int saturatingInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    record HeaderControls(Rect typeSummary, Rect refresh, Rect payFees, Rect changeType) {
    }

    record Dimensions(boolean wide,
                      int headerHeight,
                      int metricColumns,
                      int metricHeight,
                      int mainWidth,
                      int actionWidth,
                      HeaderControls headerControls) {
    }

    record Layout(Dimensions dimensions,
                  Rect header,
                  List<Rect> metrics,
                  Rect main,
                  Rect actions,
                  int contentHeight,
                  int maxScroll,
                  int clampedScroll) {
    }
}
