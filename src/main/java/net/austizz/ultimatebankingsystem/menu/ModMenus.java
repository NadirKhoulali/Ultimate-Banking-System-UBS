package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, UltimateBankingSystem.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CardboardBoxMenu>> CARDBOARD_BOX =
            MENUS.register("cardboard_box", () ->
                    IMenuTypeExtension.create((windowId, playerInventory, data) ->
                            new CardboardBoxMenu(windowId, playerInventory, data.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<ShoppingBagMenu>> SHOPPING_BAG =
            MENUS.register("shopping_bag", () ->
                    IMenuTypeExtension.create((windowId, playerInventory, data) ->
                            new ShoppingBagMenu(windowId, playerInventory, data.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<ShoppingBagMenu>> SHOPPING_BAG_ITEM =
            MENUS.register("shopping_bag_item", () ->
                    IMenuTypeExtension.create((windowId, playerInventory, data) ->
                            ShoppingBagMenu.forItem(windowId, playerInventory, data.readEnum(InteractionHand.class))));

    public static final DeferredHolder<MenuType<?>, MenuType<WalletMenu>> WALLET =
            MENUS.register("wallet", () ->
                    IMenuTypeExtension.create((windowId, playerInventory, data) ->
                            WalletMenu.forItem(windowId, playerInventory, data.readEnum(InteractionHand.class))));

    public static final DeferredHolder<MenuType<?>, MenuType<SafetyDepositBoxMenu>> SAFETY_DEPOSIT_BOX =
            MENUS.register("safety_deposit_box", () ->
                    IMenuTypeExtension.create(SafetyDepositBoxMenu::fromNetwork));

    private ModMenus() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
