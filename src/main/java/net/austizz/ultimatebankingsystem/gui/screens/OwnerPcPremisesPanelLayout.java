package net.austizz.ultimatebankingsystem.gui.screens;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

final class OwnerPcPremisesPanelLayout {
    static final int TWO_COLUMN_BREAKPOINT = 760;
    static final int SUMMARY_HEIGHT = 94;
    static final int CARD_HEIGHT = 310;
    static final int NARROW_CARD_HEIGHT = 370;
    static final int CONTROL_STACK_BREAKPOINT = 300;
    static final int GAP = 10;
    private static final int CONTROL_INSET = 12;
    private static final int CONTROL_HEIGHT = 24;
    private static final int CONTROL_GAP = 8;
    private static final int STACKED_CONTROL_GAP = 6;
    private static final int CONTROL_BOTTOM_INSET = 12;

    private OwnerPcPremisesPanelLayout() {
    }

    static Layout layout(int width, int viewportHeight, int cardCount, int requestedScroll) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (cardCount < 0) {
            throw new IllegalArgumentException("cardCount must not be negative");
        }

        int columns = width < TWO_COLUMN_BREAKPOINT ? 1 : 2;
        int cardWidth = columns == 1 ? width : (width - GAP) / columns;
        int cardHeight = cardHeight(cardWidth);
        long rows = ((long) cardCount + columns - 1L) / columns;
        long measuredHeight = SUMMARY_HEIGHT;
        if (rows > 0L) {
            measuredHeight += GAP + (rows * cardHeight) + ((rows - 1L) * GAP);
        }

        int contentHeight = saturatingInt(measuredHeight);
        int safeViewportHeight = Math.max(0, viewportHeight);
        int maxScroll = saturatingInt(Math.max(0L, (long) contentHeight - safeViewportHeight));
        int clampedScroll = Math.max(0, Math.min(requestedScroll, maxScroll));
        List<Card> cards = cardCount == 0
                ? List.of()
                : new CardList(cardCount, columns, cardWidth, cardHeight);
        return new Layout(columns, cards, contentHeight, maxScroll, clampedScroll);
    }

    static Controls controls(int cardWidth, int cardHeight) {
        if (cardWidth <= 0 || cardHeight <= 0) {
            throw new IllegalArgumentException("card dimensions must be positive");
        }
        int inset = Math.min(CONTROL_INSET, Math.max(0, (cardWidth - 1) / 2));
        int innerWidth = Math.max(1, cardWidth - (inset * 2));
        if (cardWidth < CONTROL_STACK_BREAKPOINT) {
            int controlsHeight = (CONTROL_HEIGHT * 4) + (STACKED_CONTROL_GAP * 3);
            int y = Math.max(0, cardHeight - CONTROL_BOTTOM_INSET - controlsHeight);
            int step = CONTROL_HEIGHT + STACKED_CONTROL_GAP;
            return new Controls(
                    new Rect(inset, y, innerWidth, CONTROL_HEIGHT),
                    new Rect(inset, y + step, innerWidth, CONTROL_HEIGHT),
                    new Rect(inset, y + (step * 2), innerWidth, CONTROL_HEIGHT),
                    new Rect(inset, y + (step * 3), innerWidth, CONTROL_HEIGHT)
            );
        }

        int buttonWidth = Math.max(1, (innerWidth - CONTROL_GAP) / 2);
        int secondWidth = Math.max(1, innerWidth - CONTROL_GAP - buttonWidth);
        int y = Math.max(0, cardHeight - CONTROL_BOTTOM_INSET - (CONTROL_HEIGHT * 2) - CONTROL_GAP);
        return new Controls(
                new Rect(inset, y, buttonWidth, CONTROL_HEIGHT),
                new Rect(inset + buttonWidth + CONTROL_GAP, y, secondWidth, CONTROL_HEIGHT),
                new Rect(inset, y + CONTROL_HEIGHT + CONTROL_GAP, buttonWidth, CONTROL_HEIGHT),
                new Rect(inset + buttonWidth + CONTROL_GAP,
                        y + CONTROL_HEIGHT + CONTROL_GAP, secondWidth, CONTROL_HEIGHT)
        );
    }

    private static int cardHeight(int cardWidth) {
        return cardWidth < CONTROL_STACK_BREAKPOINT ? NARROW_CARD_HEIGHT : CARD_HEIGHT;
    }

    private static int saturatingInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private static final class CardList extends AbstractList<Card> implements RandomAccess {
        private final int size;
        private final int columns;
        private final int cardWidth;
        private final int cardHeight;

        private CardList(int size, int columns, int cardWidth, int cardHeight) {
            this.size = size;
            this.columns = columns;
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
        }

        @Override
        public Card get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }
            int column = index % columns;
            long row = index / columns;
            int x = column * (cardWidth + GAP);
            long measuredY = SUMMARY_HEIGHT + GAP + (row * (cardHeight + (long) GAP));
            int y = (int) Math.min(measuredY, Integer.MAX_VALUE - (long) cardHeight);
            return new Card(index, x, y, cardWidth, cardHeight);
        }

        @Override
        public int size() {
            return size;
        }
    }

    record Card(int index, int x, int y, int width, int height) {
    }

    record Rect(int x, int y, int width, int height) {
    }

    record Controls(Rect publicMode, Rect staffOnlyMode, Rect updateExit, Rect delete) {
    }

    record Layout(int columns,
                  List<Card> cards,
                  int contentHeight,
                  int maxScroll,
                  int clampedScroll) {
    }
}
