package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class SmartphoneKeyMappings {
    private static final String CATEGORY = "key.categories.ultimatebankingsystem";

    public static final KeyMapping OPEN_PHONE = new KeyMapping(
            "key.ultimatebankingsystem.smartphone",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
    );

    private SmartphoneKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PHONE);
    }
}
