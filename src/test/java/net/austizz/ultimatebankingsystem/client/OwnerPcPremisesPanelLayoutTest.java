package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcPremisesPanelLayoutTest {
    private static final String LAYOUT_CLASS =
            "net.austizz.ultimatebankingsystem.gui.screens.OwnerPcPremisesPanelLayout";

    @Test
    void cardsSwitchBetweenOneAndTwoColumnsWithoutOverlap() throws Exception {
        for (int width : new int[]{420, 759, 760, 1000}) {
            int expectedColumns = width < 760 ? 1 : 2;
            LayoutSnapshot layout = layout(width, 280, 7, 0);

            assertEquals(expectedColumns, layout.columns(), "column count at width " + width);
            assertEquals(7, layout.cards().size(), "card count at width " + width);
            assertEquals(layout, layout(width, 280, 7, 0),
                    "layout must be deterministic at width " + width);
            assertCardGeometry(width, layout);
            assertFixedRowMajorRhythm(layout);
            assertScrollMath(layout, 280);
            assertEquals(0, layout.clampedScroll());
        }
    }

    @Test
    void scrollClampsAfterCardRemovalAndNarrowResize() throws Exception {
        LayoutSnapshot longWide = layout(1000, 220, 1000, Integer.MAX_VALUE);
        assertTrue(longWide.maxScroll() > 0, "long list must be scrollable");
        assertEquals(longWide.maxScroll(), longWide.clampedScroll());

        LayoutSnapshot narrowAndShrunk = layout(420, 220, 3, longWide.clampedScroll());
        assertEquals(1, narrowAndShrunk.columns());
        assertEquals(3, narrowAndShrunk.cards().size());
        assertEquals(Math.min(longWide.clampedScroll(), narrowAndShrunk.maxScroll()),
                narrowAndShrunk.clampedScroll());
        assertScrollMath(narrowAndShrunk, 220);
        assertEquals(narrowAndShrunk.cards(), layout(420, 220, 3, 0).cards(),
                "scroll must not change content-space card rectangles");

        LayoutSnapshot longNarrow = layout(759, 220, 1000, Integer.MAX_VALUE);
        LayoutSnapshot listShrunk = layout(759, 220, 2, longNarrow.clampedScroll());
        assertEquals(Math.min(longNarrow.clampedScroll(), listShrunk.maxScroll()),
                listShrunk.clampedScroll());
        assertScrollMath(listShrunk, 220);

        assertEquals(0, layout(420, 220, 3, -50).clampedScroll(),
                "negative scroll must clamp to zero");
        LayoutSnapshot empty = layout(420, 220, 0, longNarrow.clampedScroll());
        assertTrue(empty.cards().isEmpty());
        assertEquals(0, empty.maxScroll(), "empty state must not scroll");
        assertEquals(0, empty.clampedScroll(), "stale empty-state scroll must clamp");
    }

    @Test
    void narrowAndWideCardControlsRemainInsideAndNeverOverlap() throws Exception {
        for (int width : new int[]{180, 420, 760, 1000}) {
            LayoutSnapshot layout = layout(width, 280, 4, 0);
            for (CardSnapshot card : layout.cards()) {
                List<RectSnapshot> controls = controls(card.width(), card.height());
                for (RectSnapshot control : controls) {
                    assertTrue(control.x() >= 0 && control.y() >= 0);
                    assertTrue(control.width() > 0 && control.height() > 0);
                    assertTrue(control.x() + control.width() <= card.width(),
                            "control must fit card width at panel width " + width);
                    assertTrue(control.y() + control.height() <= card.height(),
                            "control must fit card height at panel width " + width);
                }
                for (int left = 0; left < controls.size(); left++) {
                    for (int right = left + 1; right < controls.size(); right++) {
                        assertFalse(overlaps(controls.get(left), controls.get(right)),
                                "controls overlap at panel width " + width);
                    }
                }
            }
        }
    }

    private static LayoutSnapshot layout(int width,
                                         int viewportHeight,
                                         int cardCount,
                                         int requestedScroll) throws Exception {
        Class<?> helper;
        try {
            helper = Class.forName(LAYOUT_CLASS);
        } catch (ClassNotFoundException error) {
            throw new AssertionError("Missing pure layout helper " + LAYOUT_CLASS, error);
        }
        assertTrue(Modifier.isFinal(helper.getModifiers()), "layout helper must be final");

        Method method;
        try {
            method = helper.getDeclaredMethod(
                    "layout", int.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException error) {
            throw new AssertionError(
                    "Expected layout(int width, int viewportHeight, int cardCount, int requestedScroll)",
                    error);
        }
        assertTrue(Modifier.isStatic(method.getModifiers()), "layout method must be static");
        method.setAccessible(true);

        Object result;
        try {
            result = method.invoke(null, width, viewportHeight, cardCount, requestedScroll);
        } catch (InvocationTargetException error) {
            throw new AssertionError("Layout calculation failed", error.getCause());
        }
        assertTrue(result.getClass().isRecord(), "layout result must be a JDK record");
        assertEquals(List.of("columns", "cards", "contentHeight", "maxScroll", "clampedScroll"),
                recordComponentNames(result.getClass()));

        Object rawCards = accessor(result, "cards");
        assertTrue(rawCards instanceof List<?>, "cards must be a java.util.List");
        List<CardSnapshot> cards = new ArrayList<>();
        for (Object rawCard : (List<?>) rawCards) {
            assertTrue(rawCard.getClass().isRecord(), "card rectangle must be a JDK record");
            assertEquals(List.of("index", "x", "y", "width", "height"),
                    recordComponentNames(rawCard.getClass()));
            cards.add(new CardSnapshot(
                    intAccessor(rawCard, "index"),
                    intAccessor(rawCard, "x"),
                    intAccessor(rawCard, "y"),
                    intAccessor(rawCard, "width"),
                    intAccessor(rawCard, "height")));
        }
        return new LayoutSnapshot(
                intAccessor(result, "columns"),
                List.copyOf(cards),
                intAccessor(result, "contentHeight"),
                intAccessor(result, "maxScroll"),
                intAccessor(result, "clampedScroll"));
    }

    private static List<RectSnapshot> controls(int cardWidth, int cardHeight) throws Exception {
        Class<?> helper = Class.forName(LAYOUT_CLASS);
        Method method = helper.getDeclaredMethod("controls", int.class, int.class);
        assertTrue(Modifier.isStatic(method.getModifiers()), "controls method must be static");
        method.setAccessible(true);
        Object result = method.invoke(null, cardWidth, cardHeight);
        assertEquals(List.of("publicMode", "staffOnlyMode", "updateExit", "delete"),
                recordComponentNames(result.getClass()));
        List<RectSnapshot> controls = new ArrayList<>();
        for (String name : recordComponentNames(result.getClass())) {
            Object rectangle = accessor(result, name);
            controls.add(new RectSnapshot(
                    intAccessor(rectangle, "x"),
                    intAccessor(rectangle, "y"),
                    intAccessor(rectangle, "width"),
                    intAccessor(rectangle, "height")));
        }
        return List.copyOf(controls);
    }

    private static void assertCardGeometry(int panelWidth, LayoutSnapshot layout) {
        for (int index = 0; index < layout.cards().size(); index++) {
            CardSnapshot card = layout.cards().get(index);
            assertEquals(index, card.index(), "card order must retain input order");
            assertTrue(card.x() >= 0 && card.y() >= 0, "card origin must be in content bounds");
            assertTrue(card.width() > 0 && card.height() > 0, "card dimensions must be positive");
            assertTrue((long) card.x() + card.width() <= panelWidth,
                    "card must remain within panel width");
            assertTrue((long) card.y() + card.height() <= layout.contentHeight(),
                    "card must remain within content height");
        }
        for (int left = 0; left < layout.cards().size(); left++) {
            for (int right = left + 1; right < layout.cards().size(); right++) {
                assertFalse(overlaps(layout.cards().get(left), layout.cards().get(right)),
                        "cards " + left + " and " + right + " overlap");
            }
        }
    }

    private static void assertFixedRowMajorRhythm(LayoutSnapshot layout) {
        List<CardSnapshot> cards = layout.cards();
        int columns = layout.columns();
        assertTrue(columns == 1 || columns == 2);
        int cardWidth = cards.get(0).width();
        int cardHeight = cards.get(0).height();
        cards.forEach(card -> {
            assertEquals(cardWidth, card.width(), "card widths must be fixed");
            assertEquals(cardHeight, card.height(), "card heights must be fixed");
        });

        List<Integer> columnXs = cards.stream().limit(columns).map(CardSnapshot::x).toList();
        List<Integer> rowYs = new ArrayList<>(
                new LinkedHashSet<>(cards.stream().map(CardSnapshot::y).toList()));
        assertEquals((cards.size() + columns - 1) / columns, rowYs.size());
        for (int index = 0; index < cards.size(); index++) {
            assertEquals(columnXs.get(index % columns), cards.get(index).x(),
                    "cards must be row-major");
            assertEquals(rowYs.get(index / columns), cards.get(index).y(),
                    "cards must be row-major");
        }
        if (columns == 2) {
            assertTrue(columnXs.get(1) >= columnXs.get(0) + cardWidth,
                    "column gap must not be negative");
        }
        if (rowYs.size() > 1) {
            int rowStep = rowYs.get(1) - rowYs.get(0);
            assertTrue(rowStep >= cardHeight, "row gap must not be negative");
            for (int row = 2; row < rowYs.size(); row++) {
                assertEquals(rowStep, rowYs.get(row) - rowYs.get(row - 1),
                        "vertical rhythm must be fixed");
            }
        }
    }

    private static void assertScrollMath(LayoutSnapshot layout, int viewportHeight) {
        long expectedMax = Math.max(0L, (long) layout.contentHeight() - viewportHeight);
        assertTrue(expectedMax <= Integer.MAX_VALUE, "scroll range must fit an int");
        assertEquals((int) expectedMax, layout.maxScroll());
        assertTrue(layout.clampedScroll() >= 0 && layout.clampedScroll() <= layout.maxScroll(),
                "clamped scroll must stay within [0, maxScroll]");
    }

    private static boolean overlaps(CardSnapshot first, CardSnapshot second) {
        return first.x() < second.x() + second.width()
                && second.x() < first.x() + first.width()
                && first.y() < second.y() + second.height()
                && second.y() < first.y() + first.height();
    }

    private static boolean overlaps(RectSnapshot first, RectSnapshot second) {
        return first.x() < second.x() + second.width()
                && second.x() < first.x() + first.width()
                && first.y() < second.y() + second.height()
                && second.y() < first.y() + first.height();
    }

    private static Object accessor(Object target, String name) throws Exception {
        Method accessor = target.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }

    private static int intAccessor(Object target, String name) throws Exception {
        Object value = accessor(target, name);
        assertTrue(value instanceof Integer, name + " must be an int");
        return (Integer) value;
    }

    private static List<String> recordComponentNames(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents()).map(component -> component.getName()).toList();
    }

    private record CardSnapshot(int index, int x, int y, int width, int height) {
    }

    private record RectSnapshot(int x, int y, int width, int height) {
    }

    private record LayoutSnapshot(int columns,
                                  List<CardSnapshot> cards,
                                  int contentHeight,
                                  int maxScroll,
                                  int clampedScroll) {
    }
}
