package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class Ove9000SawClientModEvents {
    private Ove9000SawClientModEvents() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(
                    LivingEntity entity,
                    InteractionHand hand,
                    ItemStack itemStack
            ) {
                if (hand == InteractionHand.MAIN_HAND
                        && entity.getMainHandItem().is(ModItems.OVE9000_SAW.get())) {
                    return HumanoidModel.ArmPose.CROSSBOW_HOLD;
                }
                return null;
            }
        }, ModItems.OVE9000_SAW.get());
    }
}
