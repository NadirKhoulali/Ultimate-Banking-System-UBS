package net.austizz.ultimatebankingsystem.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBoxDisplayLayoutTest {
    @Test
    void mapsBoxCapacityToNineItemLayers() {
        assertEquals(1, SafeBoxDisplayLayout.layerCount(9));
        assertEquals(2, SafeBoxDisplayLayout.layerCount(18));
        assertEquals(3, SafeBoxDisplayLayout.layerCount(27));
        assertEquals(6, SafeBoxDisplayLayout.layerCount(54));
    }

    @Test
    void eachInventoryRowStacksAboveTheSameTrayGrid() {
        SafeBoxDisplayLayout.Position first = SafeBoxDisplayLayout.position(0, 54, 0.794D);
        SafeBoxDisplayLayout.Position secondLayer = SafeBoxDisplayLayout.position(9, 54, 0.794D);
        SafeBoxDisplayLayout.Position lastLayer = SafeBoxDisplayLayout.position(45, 54, 0.794D);

        assertEquals(first.x(), secondLayer.x());
        assertEquals(first.z(), secondLayer.z());
        assertEquals(0, first.layer());
        assertEquals(1, secondLayer.layer());
        assertEquals(5, lastLayer.layer());
        assertTrue(secondLayer.y() > first.y());
        assertTrue(lastLayer.y() > secondLayer.y());
        assertTrue(lastLayer.y() < 0.794D);
    }

    @Test
    void nineSlotsFormAThreeByThreeTrayGrid() {
        SafeBoxDisplayLayout.Position topLeft = SafeBoxDisplayLayout.position(0, 9, 0.128D);
        SafeBoxDisplayLayout.Position center = SafeBoxDisplayLayout.position(4, 9, 0.128D);
        SafeBoxDisplayLayout.Position bottomRight = SafeBoxDisplayLayout.position(8, 9, 0.128D);

        assertTrue(topLeft.x() < center.x());
        assertTrue(center.x() < bottomRight.x());
        assertTrue(topLeft.z() < center.z());
        assertTrue(center.z() < bottomRight.z());
        assertEquals(topLeft.y(), bottomRight.y());
    }
}
