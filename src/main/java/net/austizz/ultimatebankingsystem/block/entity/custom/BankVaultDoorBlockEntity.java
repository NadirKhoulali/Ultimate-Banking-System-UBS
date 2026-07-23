package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.bank.safebox.LoadedSafeStructureIndex;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAccessAuditService;
import net.austizz.ultimatebankingsystem.bank.safebox.escort.SafeBoxEscortDoorHolds;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BankVaultDoorBlockEntity extends BlockEntity {
    public static final int OPEN_ANIMATION_TICKS = 96;
    public static final int CLOSE_ANIMATION_TICKS = 80;

    private float previousAnimationProgress;
    private float animationProgress;
    private boolean targetOpen;
    private UUID heistDrillSessionId;
    private boolean auditStateInitialized;
    private boolean lastAuditedOpen;
    private final SafeBoxEscortDoorHolds escortHolds = new SafeBoxEscortDoorHolds();

    public BankVaultDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANK_VAULT_DOOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LoadedSafeStructureIndex.register(level, worldPosition, LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER);
    }

    @Override
    public void setRemoved() {
        LoadedSafeStructureIndex.unregister(level, worldPosition, LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER);
        super.setRemoved();
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

    public boolean addEscortHold(UUID sessionId) {
        if (!escortHolds.add(sessionId)) {
            return false;
        }
        if (escortHolds.count() == 1) {
            markUpdated();
        }
        return true;
    }

    public boolean removeEscortHold(UUID sessionId) {
        if (!escortHolds.remove(sessionId)) {
            return false;
        }
        if (!escortHolds.active()) {
            markUpdated();
        }
        return true;
    }

    public boolean hasEscortHold(UUID sessionId) {
        return escortHolds.contains(sessionId);
    }

    public int escortHoldCount() {
        return escortHolds.count();
    }

    public boolean attachHeistDrill(UUID sessionId) {
        if (sessionId == null || heistDrillSessionId != null) return false;
        heistDrillSessionId = sessionId;
        markUpdated();
        return true;
    }

    public boolean detachHeistDrill(UUID sessionId) {
        if (sessionId == null || !sessionId.equals(heistDrillSessionId)) return false;
        heistDrillSessionId = null;
        markUpdated();
        return true;
    }

    public boolean hasHeistDrill(UUID sessionId) {
        return sessionId != null && sessionId.equals(heistDrillSessionId);
    }

    public boolean isHeistDrillAttached() {
        return heistDrillSessionId != null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BankVaultDoorBlockEntity vault) {
        if (vault == null || level == null) {
            return;
        }
        vault.previousAnimationProgress = vault.animationProgress;
        if (!level.isClientSide() && !vault.auditStateInitialized) {
            vault.lastAuditedOpen = vault.animationProgress >= 1.0F;
            vault.auditStateInitialized = true;
        }
        boolean shouldOpen = (state.hasProperty(BankVaultDoorBlock.POWERED)
                && state.getValue(BankVaultDoorBlock.POWERED))
                || vault.escortHolds.active();
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
                boolean fullyOpen = next == 1.0F;
                if (fullyOpen != vault.lastAuditedOpen && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    vault.lastAuditedOpen = fullyOpen;
                    SafeAccessAuditService.recordDoorTransition(serverLevel, pos, "Bank Vault Door", fullyOpen);
                }
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
        ListTag holdTags = tag.getList("escort_holds", Tag.TAG_COMPOUND);
        java.util.ArrayList<UUID> holdIds = new java.util.ArrayList<>(holdTags.size());
        for (int index = 0; index < holdTags.size(); index++) {
            CompoundTag holdTag = holdTags.getCompound(index);
            if (holdTag.hasUUID("session")) {
                holdIds.add(holdTag.getUUID("session"));
            }
        }
        escortHolds.replaceWith(holdIds);
        targetOpen = tag.getBoolean("target_open");
        heistDrillSessionId = tag.hasUUID("heist_drill_session") ? tag.getUUID("heist_drill_session") : null;
        animationProgress = Mth.clamp(tag.getFloat("animation_progress"), 0.0F, 1.0F);
        previousAnimationProgress = animationProgress;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("target_open", targetOpen);
        ListTag holdTags = new ListTag();
        for (UUID sessionId : escortHolds.snapshot()) {
            CompoundTag holdTag = new CompoundTag();
            holdTag.putUUID("session", sessionId);
            holdTags.add(holdTag);
        }
        tag.put("escort_holds", holdTags);
        if (heistDrillSessionId != null) tag.putUUID("heist_drill_session", heistDrillSessionId);
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
