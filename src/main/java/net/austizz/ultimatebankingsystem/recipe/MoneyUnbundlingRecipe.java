package net.austizz.ultimatebankingsystem.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Unbundling recipe: exactly one money stack alone in the crafting grid yields 100 loose
 * bills of its denomination. The crafted result is a full 64-bill stack; the remaining 36
 * bills are handed back through {@link #getRemainingItems(CraftingInput)} (vanilla places
 * the remainder back into the grid slot or gives overflow to the player). The strap is
 * NOT returned, consistent with crouch-unwrapping the stack in hand.
 */
public class MoneyUnbundlingRecipe implements CraftingRecipe {
    private static final int RESULT_BILLS = 64;
    private static final int REMAINDER_BILLS = 36;

    public static final MapCodec<MoneyUnbundlingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MoneyStackBlock.BillDenomination.CODEC.fieldOf("denomination").forGetter(recipe -> recipe.denomination)
    ).apply(instance, MoneyUnbundlingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoneyUnbundlingRecipe> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(
                    ordinal -> new MoneyUnbundlingRecipe(MoneyStackBlock.BillDenomination.values()[ordinal]),
                    recipe -> recipe.denomination.ordinal()
            )
            .cast();

    private final MoneyStackBlock.BillDenomination denomination;

    public MoneyUnbundlingRecipe(MoneyStackBlock.BillDenomination denomination) {
        this.denomination = denomination;
    }

    public MoneyStackBlock.BillDenomination getDenomination() {
        return denomination;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int stackSlots = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() != denomination.stackItem()) {
                return false;
            }
            stackSlots++;
            if (stackSlots > 1) {
                return false;
            }
        }
        return stackSlots == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(denomination.billItem(), RESULT_BILLS);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(denomination.billItem(), RESULT_BILLS);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == denomination.stackItem()) {
                remaining.set(i, new ItemStack(denomination.billItem(), REMAINDER_BILLS));
                break;
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.MONEY_UNBUNDLING.get();
    }

    public static class Serializer implements RecipeSerializer<MoneyUnbundlingRecipe> {
        @Override
        public MapCodec<MoneyUnbundlingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MoneyUnbundlingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
