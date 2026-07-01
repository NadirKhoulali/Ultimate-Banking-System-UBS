package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.client.model.BankSafeIronBarGateBlockEntityModel;
import net.austizz.ultimatebankingsystem.client.model.BankVaultDoorBlockEntityModel;
import net.austizz.ultimatebankingsystem.client.model.SafetyDepositBoxRowBlockEntityModel;
import net.austizz.ultimatebankingsystem.client.renderer.BankSafeIronBarGateItemRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.BankSafeIronBarGateRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.BankTellerRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.BankVaultDoorItemRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.BankVaultDoorRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.GlassCounterDisplayRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.ModularWallDisplayRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.PalletRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.SafetyDepositBoxRowItemRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.SafetyDepositBoxRowRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.ShopSellingTableRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.ShoppingBasketRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.TallWallShelfRenderer;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BankTellerClientEvents {

    private BankTellerClientEvents() {}

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BANK_TELLER.get(), BankTellerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TALL_WALL_SHELF.get(), TallWallShelfRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP_SELLING_TABLE.get(), ShopSellingTableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHOPPING_BASKET.get(), ShoppingBasketRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PALLET.get(), PalletRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MODULAR_WALL_DISPLAY.get(), ModularWallDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GLASS_COUNTER_DISPLAY.get(), GlassCounterDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BANK_VAULT_DOOR.get(), BankVaultDoorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BANK_SAFE_IRON_BAR_GATE.get(), BankSafeIronBarGateRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SAFETY_DEPOSIT_BOX_ROW.get(), SafetyDepositBoxRowRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BankVaultDoorBlockEntityModel.LAYER_LOCATION, BankVaultDoorBlockEntityModel::createBodyLayer);
        event.registerLayerDefinition(BankSafeIronBarGateBlockEntityModel.LAYER_LOCATION, BankSafeIronBarGateBlockEntityModel::createBodyLayer);
        event.registerLayerDefinition(SafetyDepositBoxRowBlockEntityModel.LAYER_LOCATION, SafetyDepositBoxRowBlockEntityModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BankVaultDoorItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new BankVaultDoorItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(),
                            minecraft.getEntityModels()
                    );
                }
                return renderer;
            }
        }, ModBlocks.BANK_VAULT_DOOR.get().asItem());

        event.registerItem(new IClientItemExtensions() {
            private BankSafeIronBarGateItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new BankSafeIronBarGateItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(),
                            minecraft.getEntityModels()
                    );
                }
                return renderer;
            }
        }, ModBlocks.BANK_SAFE_IRON_BAR_GATE.get().asItem());

        event.registerItem(new IClientItemExtensions() {
            private SafetyDepositBoxRowItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new SafetyDepositBoxRowItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(),
                            minecraft.getEntityModels()
                    );
                }
                return renderer;
            }
        }, ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get().asItem());
    }
}
