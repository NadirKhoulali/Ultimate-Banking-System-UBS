package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.MoneyBriefcaseBlock;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Money briefcase: a flat leather case holding exactly 20 money-stack items
 * (2 layers x 2 deep x 5 across, one strapped stack per slot).
 *
 * Item persistence mechanism: breaking a placed briefcase produces one
 * money_briefcase item carrying the whole inventory via the vanilla
 * BLOCK_ENTITY_DATA data component ({@link BlockItem#setBlockEntityData}).
 * On placement, vanilla BlockItem.updateCustomBlockEntityTag applies that
 * component back through {@link #loadAdditional}, restoring all 20 slots.
 */
public class MoneyBriefcaseBlockEntity extends BlockEntity {
    public static final int SLOT_COUNT = 20;
    public static final int SLOTS_PER_LAYER = 10;
    public static final int SLOTS_PER_ROW = 5;

    public static final int OPEN_ANIMATION_TICKS = 8;
    public static final int CLOSE_ANIMATION_TICKS = 7;

    private static final String STORAGE_KEY = "inventory";

    private boolean targetOpen;
    private float previousAnimationProgress;
    private float animationProgress;
    // Transient (never saved to NBT): set once a removal path has already
    // spawned the contents-carrying drop, so onRemove never dupes it.
    private boolean dropHandled;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected int getStackLimit(int slot, @NotNull ItemStack stack) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isMoneyStackItem(stack);
        }
    };

    public MoneyBriefcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONEY_BRIEFCASE.get(), pos, state);
    }

    public static boolean isMoneyStackItem(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && MoneyStackBlock.BillDenomination.fromStackItem(stack.getItem()) != null;
    }

    public boolean isTargetOpen() {
        return targetOpen;
    }

    public boolean isOpenForStorage() {
        return targetOpen && animationProgress >= 0.88F;
    }

    public float getAnimationProgress(float partialTick) {
        return Mth.lerp(partialTick, previousAnimationProgress, animationProgress);
    }

    public ItemStack getStoredStack(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return items.getStackInSlot(slot);
    }

    /** True once a removal path has already spawned the briefcase drop. */
    public boolean isDropHandled() {
        return dropHandled;
    }

    /** Marks this briefcase's removal drop as spawned (or intentionally skipped). */
    public void setDropHandled() {
        this.dropHandled = true;
    }

    public boolean hasStoredMoney() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MoneyBriefcaseBlockEntity briefcase) {
        if (level == null || briefcase == null) {
            return;
        }
        briefcase.previousAnimationProgress = briefcase.animationProgress;
        float step = briefcase.targetOpen ? 1.0F / OPEN_ANIMATION_TICKS : 1.0F / CLOSE_ANIMATION_TICKS;
        float next = briefcase.targetOpen
                ? Math.min(1.0F, briefcase.animationProgress + step)
                : Math.max(0.0F, briefcase.animationProgress - step);
        if (next == briefcase.animationProgress) {
            return;
        }
        briefcase.animationProgress = next;
        if (!level.isClientSide() && (next == 0.0F || next == 1.0F)) {
            briefcase.setChanged();
        }
    }

    /** Toggles the lid server-side, playing the matching latch sound. */
    public void setTargetOpen(boolean open) {
        if (targetOpen == open) {
            return;
        }
        targetOpen = open;
        if (level != null && !level.isClientSide()) {
            level.playSound(
                    null,
                    worldPosition,
                    open ? ModSounds.BRIEFCASE_OPEN.get() : ModSounds.BRIEFCASE_CLOSE.get(),
                    SoundSource.BLOCKS,
                    0.9F,
                    1.0F
            );
            BlockState state = getBlockState();
            if (state.hasProperty(MoneyBriefcaseBlock.OPEN)
                    && state.getValue(MoneyBriefcaseBlock.OPEN) != open) {
                level.setBlock(worldPosition, state.setValue(MoneyBriefcaseBlock.OPEN, open), 3);
            }
        }
        markUpdated();
    }

    /**
     * Inserts one money-stack item from the held stack into the first free slot.
     * Returns true when one item was stored; false when the case is full.
     */
    public boolean insertMoneyStack(ServerPlayer player, ItemStack held) {
        if (player == null || !isMoneyStackItem(held) || !isOpenForStorage()) {
            return false;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (items.getStackInSlot(slot).isEmpty()) {
                ItemStack stored = held.copy();
                stored.setCount(1);
                items.setStackInSlot(slot, stored);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    player.getInventory().setChanged();
                }
                return true;
            }
        }
        return false;
    }

    /** Withdraws the last occupied slot's stack into the player inventory. */
    public boolean withdrawLast(ServerPlayer player) {
        if (player == null || !isOpenForStorage()) {
            return false;
        }
        for (int slot = SLOT_COUNT - 1; slot >= 0; slot--) {
            ItemStack stored = items.getStackInSlot(slot);
            if (stored.isEmpty()) {
                continue;
            }
            items.setStackInSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(stored)) {
                player.drop(stored, false);
            }
            return true;
        }
        return false;
    }

    /**
     * Builds the briefcase item that keeps the whole inventory (shulker-style).
     * An empty case yields a plain component-free item so it stays stackable.
     */
    public ItemStack createDropStack() {
        ItemStack stack = new ItemStack(ModBlocks.MONEY_BRIEFCASE.get());
        if (level != null && hasStoredMoney()) {
            CompoundTag tag = new CompoundTag();
            tag.put(STORAGE_KEY, items.serializeNBT(level.registryAccess()));
            BlockItem.setBlockEntityData(stack, ModBlockEntities.MONEY_BRIEFCASE.get(), tag);
        }
        return stack;
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.targetOpen = tag.getBoolean("target_open");
        this.animationProgress = Mth.clamp(tag.getFloat("animation_progress"), 0.0F, 1.0F);
        this.previousAnimationProgress = animationProgress;
        CompoundTag storageTag = tag.contains(STORAGE_KEY)
                ? tag.getCompound(STORAGE_KEY).copy()
                : new CompoundTag();
        storageTag.putInt("Size", SLOT_COUNT);
        items.deserializeNBT(registries, storageTag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("target_open", targetOpen);
        tag.putFloat("animation_progress", Mth.clamp(animationProgress, 0.0F, 1.0F));
        tag.put(STORAGE_KEY, items.serializeNBT(registries));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
