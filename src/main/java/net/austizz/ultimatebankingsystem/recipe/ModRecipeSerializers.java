package net.austizz.ultimatebankingsystem.recipe;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, UltimateBankingSystem.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, MoneyBundlingRecipe.Serializer> MONEY_BUNDLING =
            RECIPE_SERIALIZERS.register("money_bundling", MoneyBundlingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, MoneyUnbundlingRecipe.Serializer> MONEY_UNBUNDLING =
            RECIPE_SERIALIZERS.register("money_unbundling", MoneyUnbundlingRecipe.Serializer::new);

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
