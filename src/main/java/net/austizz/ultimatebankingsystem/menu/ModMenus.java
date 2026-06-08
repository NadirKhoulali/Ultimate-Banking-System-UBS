package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, UltimateBankingSystem.MODID);

    public static final RegistryObject<MenuType<CardboardBoxMenu>> CARDBOARD_BOX =
            MENUS.register("cardboard_box", () ->
                    IForgeMenuType.create((windowId, playerInventory, data) ->
                            new CardboardBoxMenu(windowId, playerInventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<ShoppingBagMenu>> SHOPPING_BAG =
            MENUS.register("shopping_bag", () ->
                    IForgeMenuType.create((windowId, playerInventory, data) ->
                            new ShoppingBagMenu(windowId, playerInventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<ShoppingBagMenu>> SHOPPING_BAG_ITEM =
            MENUS.register("shopping_bag_item", () ->
                    IForgeMenuType.create((windowId, playerInventory, data) ->
                            ShoppingBagMenu.forItem(windowId, playerInventory, data.readEnum(InteractionHand.class))));

    private ModMenus() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
