package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class BankVaultDoorBlockEntity extends BlockEntity {
    public static final int OPEN_ANIMATION_TICKS = 96;
    public static final int CLOSE_ANIMATION_TICKS = 80;

    private float previousAnimationProgress;
    private float animationProgress;
    private boolean targetOpen;

    public BankVaultDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANK_VAULT_DOOR.get(), pos, state);
    }

    public float getAnimationProgress(float partialTick) {
        return Mth.lerp(partialTick, previousAnimationProgress, animationProgress);
    }

    public float getCurrentAnimationProgress() {
        return animationProgress;
    }

    public void setTargetOpen(boolean targetOpen) {
        if (this.targetOpen == targetOpen) {
            return;
        }
        this.targetOpen = targetOpen;
        markUpdated();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BankVaultDoorBlockEntity vault) {
        if (vault == null || level == null) {
            return;
        }
        vault.previousAnimationProgress = vault.animationProgress;
        boolean shouldOpen = state.hasProperty(BankVaultDoorBlock.POWERED)
                && state.getValue(BankVaultDoorBlock.POWERED);
        vault.targetOpen = shouldOpen;

        float previousProgress = vault.animationProgress;
        float step = shouldOpen ? 1.0F / OPEN_ANIMATION_TICKS : 1.0F / CLOSE_ANIMATION_TICKS;
        float next = shouldOpen
                ? Math.min(1.0F, vault.animationProgress + step)
                : Math.max(0.0F, vault.animationProgress - step);
        if (next == vault.animationProgress) {
            return;
        }
        vault.animationProgress = next;
        if (!level.isClientSide()) {
            BankVaultDoorBlock.pushEntitiesForAnimatedDoor(level, pos, state, previousProgress, next);
            BankVaultDoorBlock.setOpenStateIfNeeded(level, pos, state, shouldOpen && next >= 1.0F);
            if (next == 0.0F || next == 1.0F) {
                vault.setChanged();
            }
        }
    }

    public AABB getRenderBoundingBox() {
        return new AABB(
                worldPosition.getX() - 4.0D,
                worldPosition.getY() - 1.0D,
                worldPosition.getZ() - 4.0D,
                worldPosition.getX() + 5.0D,
                worldPosition.getY() + 5.0D,
                worldPosition.getZ() + 5.0D
        );
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        targetOpen = tag.getBoolean("target_open");
        animationProgress = Mth.clamp(tag.getFloat("animation_progress"), 0.0F, 1.0F);
        previousAnimationProgress = animationProgress;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("target_open", targetOpen);
        tag.putFloat("animation_progress", Mth.clamp(animationProgress, 0.0F, 1.0F));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
}
