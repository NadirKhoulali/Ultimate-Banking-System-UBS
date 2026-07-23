package net.austizz.ultimatebankingsystem.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.item.MoneyBundles;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Count-aware bundling recipe: exactly one slot holding currency straps of a denomination
 * plus bill stacks of that same denomination totaling AT LEAST
 * {@link MoneyBundles#BILLS_PER_STACK} (100) bills anywhere in the grid produce one money
 * stack per craft. Each craft consumes exactly 100 bills and 1 strap; surplus bills and
 * straps stay in the grid, so shift-clicking bundles 200/300/... bills in one go (limited
 * by bills and straps present).
 *
 * <p><b>Consumption technique</b>: vanilla's {@code ResultSlot} removes exactly ONE item
 * from each non-empty grid slot after calling {@link #getRemainingItems(CraftingInput)}.
 * {@code getRemainingItems} therefore rewrites each LIVE bill stack to
 * {@code taken-adjusted count + 1} so that vanilla's subsequent take-1 leaves exactly the
 * surplus: fully-consumed slots become 1 (then 0), partially-consumed slots keep their
 * remainder, and untouched slots get +1 (then back to their original count; the transient
 * over-max count exists only within the craft tick). The strap slot is left untouched:
 * vanilla removes exactly 1 strap. The returned remainder list is all-empty.</p>
 */
public class MoneyBundlingRecipe implements CraftingRecipe {
    public static final MapCodec<MoneyBundlingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MoneyStackBlock.BillDenomination.CODEC.fieldOf("denomination").forGetter(recipe -> recipe.denomination)
    ).apply(instance, MoneyBundlingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoneyBundlingRecipe> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(
                    ordinal -> new MoneyBundlingRecipe(MoneyStackBlock.BillDenomination.values()[ordinal]),
                    recipe -> recipe.denomination.ordinal()
            )
            .cast();

    private final MoneyStackBlock.BillDenomination denomination;

    public MoneyBundlingRecipe(MoneyStackBlock.BillDenomination denomination) {
        this.denomination = denomination;
    }

    public MoneyStackBlock.BillDenomination getDenomination() {
        return denomination;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Item strapItem = denomination.strapItem();
        Item billItem = denomination.billItem();
        int strapSlots = 0;
        int billCount = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (item == strapItem) {
                strapSlots++;
                if (strapSlots > 1) {
                    return false;
                }
            } else if (item == billItem) {
                billCount += stack.getCount();
            } else {
                return false;
            }
        }
        return strapSlots == 1 && billCount >= MoneyBundles.BILLS_PER_STACK;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(denomination.stackItem());
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(denomination.stackItem());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        Item billItem = denomination.billItem();
        int remainingToTake = MoneyBundles.BILLS_PER_STACK;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty() || stack.getItem() != billItem) {
                continue;
            }
            int count = stack.getCount();
            int take = Math.min(count, remainingToTake);
            remainingToTake -= take;
            // Vanilla removes 1 from every non-empty slot after this call, so leave
            // (count - take + 1): fully-taken slots end at 0, partial slots keep the
            // surplus, untouched slots return to their original count.
            stack.setCount(count - take + 1);
        }
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
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
        return ModRecipeSerializers.MONEY_BUNDLING.get();
    }

    public static class Serializer implements RecipeSerializer<MoneyBundlingRecipe> {
        @Override
        public MapCodec<MoneyBundlingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MoneyBundlingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
