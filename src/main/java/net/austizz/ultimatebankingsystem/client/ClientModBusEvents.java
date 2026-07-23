package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.client.model.MetalPalletContentsBakedModel;
import net.austizz.ultimatebankingsystem.client.renderer.DallasMaskAnimationLayer;
import net.austizz.ultimatebankingsystem.gui.screens.CardboardBoxScreen;
import net.austizz.ultimatebankingsystem.gui.screens.HeistDuffelScreen;
import net.austizz.ultimatebankingsystem.gui.screens.SecureSafeScreen;
import net.austizz.ultimatebankingsystem.gui.screens.SafetyDepositBoxScreen;
import net.austizz.ultimatebankingsystem.gui.screens.ShoppingBagScreen;
import net.austizz.ultimatebankingsystem.gui.screens.WalletScreen;
import net.austizz.ultimatebankingsystem.menu.ModMenus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModBusEvents {
    private ClientModBusEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.GLASS_COUNTER_DISPLAY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MODULAR_WALL_DISPLAY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CASH_STACK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MONEY_STACK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.STANDING_SAFE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.COMPACT_SAFE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RFID_SCANNER.get(), RenderType.cutout());
        });
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // Wrap ONLY the metal pallet CENTER blockstate variant (part_x=1,
        // part_z=1 -- the block's default state) with the content-appending
        // model; the 8 part variants keep their plain baked models.
        // BlockModelShaper.stateToModelLocation builds the exact registry key
        // vanilla uses (properties listed in state order, alphabetical:
        // "ultimatebankingsystem:metal_pallet#part_x=1,part_z=1").
        ModelResourceLocation centerKey =
                BlockModelShaper.stateToModelLocation(ModBlocks.METAL_PALLET.get().defaultBlockState());
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        BakedModel base = models.get(centerKey);
        if (base != null) {
            models.put(centerKey, new MetalPalletContentsBakedModel(base));
        }
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CARDBOARD_BOX.get(), CardboardBoxScreen::new);
        event.register(ModMenus.SHOPPING_BAG.get(), ShoppingBagScreen::new);
        event.register(ModMenus.SHOPPING_BAG_ITEM.get(), ShoppingBagScreen::new);
        event.register(ModMenus.WALLET.get(), WalletScreen::new);
        event.register(ModMenus.SAFETY_DEPOSIT_BOX.get(), SafetyDepositBoxScreen::new);
        event.register(ModMenus.SECURE_SAFE.get(), SecureSafeScreen::new);
        event.register(ModMenus.HEIST_DUFFEL.get(), HeistDuffelScreen::new);
        event.register(ModMenus.HEIST_DUFFEL_ITEM.get(), HeistDuffelScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        PickpocketKeyMappings.register(event);
        SmartphoneKeyMappings.register(event);
        DallasMaskKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void onAddEntityLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new DallasMaskAnimationLayer(
                        renderer,
                        event.getContext().getItemInHandRenderer()
                ));
            }
        }
    }
}
