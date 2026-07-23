package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeliveryPalletLabelsClientStateTest {
    @AfterEach
    void clearState() {
        DeliveryPalletLabelsClientState.clear();
    }

    @Test
    void acceptsMinecraftNamespaceAndLooksUpByNormalizedDimension() {
        DeliveryPalletLabelsClientState.setLabels("minecraft:overworld", List.of(
                new DeliveryPalletLabelsClientState.Label(-9, -60, 23, " Walmart ")
        ));

        DeliveryPalletLabelsClientState.Label label =
                DeliveryPalletLabelsClientState.getLabel("overworld", -9, -60, 23);

        assertEquals("Walmart", label.shopName());
        assertEquals(1, DeliveryPalletLabelsClientState.getLabels("minecraft:overworld").size());
    }

    @Test
    void rejectsLabelsFromOtherDimensions() {
        DeliveryPalletLabelsClientState.setLabels("overworld", List.of(
                new DeliveryPalletLabelsClientState.Label(198, -60, -39, "Bazzzaaalright")
        ));

        assertEquals(List.of(), DeliveryPalletLabelsClientState.getLabels("the_nether"));
        assertNull(DeliveryPalletLabelsClientState.getLabel("minecraft:the_nether", 198, -60, -39));
    }

    @Test
    void replacingWithEmptyPayloadClearsVisibleLabelsForDimension() {
        DeliveryPalletLabelsClientState.setLabels("overworld", List.of(
                new DeliveryPalletLabelsClientState.Label(1, 2, 3, "Shop")
        ));

        DeliveryPalletLabelsClientState.setLabels("minecraft:overworld", List.of());

        assertEquals(List.of(), DeliveryPalletLabelsClientState.getLabels("overworld"));
        assertNull(DeliveryPalletLabelsClientState.getLabel("overworld", 1, 2, 3));
    }
}
