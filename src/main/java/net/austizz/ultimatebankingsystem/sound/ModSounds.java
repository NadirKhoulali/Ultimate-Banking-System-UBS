package net.austizz.ultimatebankingsystem.sound;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, UltimateBankingSystem.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BRIEFCASE_OPEN =
            SOUND_EVENTS.register("briefcase_open", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "briefcase_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BRIEFCASE_CLOSE =
            SOUND_EVENTS.register("briefcase_close", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "briefcase_close")));

    private ModSounds() {}

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
