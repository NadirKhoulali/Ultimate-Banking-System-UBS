package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class PickpocketKeyMappings {
    private static final String KEY_CATEGORY = "key.categories.ultimatebankingsystem";

    private static final KeyMapping PICKPOCKET_KEY = new KeyMapping(
            "key.ultimatebankingsystem.pickpocket",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            KEY_CATEGORY
    );

    private PickpocketKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        if (event != null) {
            event.register(PICKPOCKET_KEY);
        }
    }

    public static boolean isPickpocketChordDown() {
        // Default chord is Shift+F: key can be rebound in Controls while Shift remains the safety modifier.
        return PICKPOCKET_KEY.isDown() && Screen.hasShiftDown();
    }

    public static String getBoundKeyName() {
        return PICKPOCKET_KEY.getTranslatedKeyMessage().getString();
    }
}
