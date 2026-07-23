package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcShopBusinessPanelLayoutTest {
    @Test
    void responsiveSectionsStayInsideThePanelWithoutOverlap() {
        for (int width : new int[]{220, 339, 340, 419, 420, 519, 520, 899, 900, 1200}) {
            OwnerPcShopBusinessPanelLayout.Layout layout =
                    OwnerPcShopBusinessPanelLayout.layout(width, 320, 480, 320, 0);

            assertEquals(width >= OwnerPcShopBusinessPanelLayout.WIDE_BREAKPOINT,
                    layout.dimensions().wide(), "wide mode at width " + width);
            assertEquals(4, layout.metrics().size());
            assertInside(layout.header(), width, layout.contentHeight());
            layout.metrics().forEach(rect -> assertInside(rect, width, layout.contentHeight()));
            assertInside(layout.main(), width, layout.contentHeight());
            assertInside(layout.actions(), width, layout.contentHeight());
            assertNoOverlap(layout.metrics(), "metric cards at width " + width);

            if (layout.dimensions().wide()) {
                assertFalse(overlaps(layout.main(), layout.actions()),
                        "wide main and action columns must not overlap at width " + width);
                assertEquals(layout.main().y(), layout.actions().y());
            } else {
                assertTrue(layout.actions().y() >= layout.main().bottom(),
                        "compact action center must follow main content at width " + width);
            }
        }
    }

    @Test
    void headerControlsAdaptAtEveryBreakpoint() {
        for (int width : new int[]{180, 339, 340, 519, 520, 899, 900, 1200}) {
            OwnerPcShopBusinessPanelLayout.Dimensions dimensions =
                    OwnerPcShopBusinessPanelLayout.dimensions(width);
            OwnerPcShopBusinessPanelLayout.HeaderControls controls = dimensions.headerControls();
            List<OwnerPcShopBusinessPanelLayout.Rect> all = List.of(
                    controls.typeSummary(), controls.refresh(), controls.payFees(), controls.changeType());

            all.forEach(rect -> assertInside(rect, width, dimensions.headerHeight()));
            assertNoOverlap(all, "header controls at width " + width);
            assertTrue(controls.typeSummary().y() < controls.refresh().bottom());
            assertEquals(dimensions, OwnerPcShopBusinessPanelLayout.dimensions(width),
                    "layout dimensions must be deterministic");
        }
    }

    @Test
    void dynamicContentAndScrollRemainStableAcrossResize() {
        OwnerPcShopBusinessPanelLayout.Layout tall =
                OwnerPcShopBusinessPanelLayout.layout(1200, 300, 940, 940, Integer.MAX_VALUE);
        assertTrue(tall.maxScroll() > 0);
        assertEquals(tall.maxScroll(), tall.clampedScroll());

        OwnerPcShopBusinessPanelLayout.Layout resized =
                OwnerPcShopBusinessPanelLayout.layout(300, 300, 940, 320, tall.clampedScroll());
        assertEquals(Math.min(tall.clampedScroll(), resized.maxScroll()), resized.clampedScroll());
        assertEquals(resized.contentHeight() - 300, resized.maxScroll());

        OwnerPcShopBusinessPanelLayout.Layout shortContent =
                OwnerPcShopBusinessPanelLayout.layout(1200, 2000, 1, 1, Integer.MAX_VALUE);
        assertEquals(0, shortContent.maxScroll());
        assertEquals(0, shortContent.clampedScroll());
        assertEquals(0, OwnerPcShopBusinessPanelLayout.layout(600, 300, 400, 260, -50).clampedScroll());
    }

    @Test
    void invalidGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OwnerPcShopBusinessPanelLayout.dimensions(0));
        assertThrows(IllegalArgumentException.class,
                () -> OwnerPcShopBusinessPanelLayout.layout(500, 300, -1, 20, 0));
        assertThrows(IllegalArgumentException.class,
                () -> OwnerPcShopBusinessPanelLayout.layout(500, 300, 20, -1, 0));
    }

    private static void assertInside(OwnerPcShopBusinessPanelLayout.Rect rect, int width, int height) {
        assertTrue(rect.x() >= 0 && rect.y() >= 0);
        assertTrue(rect.width() > 0 && rect.height() > 0);
        assertTrue(rect.right() <= width,
                "rectangle extends past width " + width + ": " + rect);
        assertTrue(rect.bottom() <= height,
                "rectangle extends past height " + height + ": " + rect);
    }

    private static void assertNoOverlap(List<OwnerPcShopBusinessPanelLayout.Rect> rectangles, String message) {
        List<OwnerPcShopBusinessPanelLayout.Rect> copy = new ArrayList<>(rectangles);
        for (int left = 0; left < copy.size(); left++) {
            for (int right = left + 1; right < copy.size(); right++) {
                assertFalse(overlaps(copy.get(left), copy.get(right)), message);
            }
        }
    }

    private static boolean overlaps(OwnerPcShopBusinessPanelLayout.Rect left,
                                    OwnerPcShopBusinessPanelLayout.Rect right) {
        return left.x() < right.right()
                && left.right() > right.x()
                && left.y() < right.bottom()
                && left.bottom() > right.y();
    }
}
