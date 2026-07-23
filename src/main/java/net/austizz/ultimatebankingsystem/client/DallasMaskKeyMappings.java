package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class DallasMaskKeyMappings {
    private static final String CATEGORY = "key.categories.ultimatebankingsystem";

    public static final KeyMapping TOGGLE_MASK = new KeyMapping(
            "key.ultimatebankingsystem.toggle_dallas_mask",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    private DallasMaskKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_MASK);
    }
}
