package net.austizz.ultimatebankingsystem.entity.custom;

import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public final class SafetyDepositBoxDisplayProxyEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> SESSION_ID =
            SynchedEntityData.defineId(SafetyDepositBoxDisplayProxyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> MODULE_TYPE =
            SynchedEntityData.defineId(SafetyDepositBoxDisplayProxyEntity.class, EntityDataSerializers.INT);

    public SafetyDepositBoxDisplayProxyEntity(EntityType<? extends SafetyDepositBoxDisplayProxyEntity> type,
                                              Level level) {
        super(type, level);
        noPhysics = true;
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SESSION_ID, Optional.empty());
        builder.define(MODULE_TYPE, SafetyDepositBoxRowBlockEntity.ModuleType.SMALL.ordinal());
    }

    public UUID getSessionId() {
        return entityData.get(SESSION_ID).orElse(null);
    }

    public void setSessionId(UUID sessionId) {
        entityData.set(SESSION_ID, Optional.ofNullable(sessionId));
    }

    public SafetyDepositBoxRowBlockEntity.ModuleType getModuleType() {
        int ordinal = entityData.get(MODULE_TYPE);
        SafetyDepositBoxRowBlockEntity.ModuleType[] values = SafetyDepositBoxRowBlockEntity.ModuleType.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal] : SafetyDepositBoxRowBlockEntity.ModuleType.SMALL;
    }

    public void setModuleType(SafetyDepositBoxRowBlockEntity.ModuleType type) {
        SafetyDepositBoxRowBlockEntity.ModuleType safe = type == null
                ? SafetyDepositBoxRowBlockEntity.ModuleType.SMALL : type;
        entityData.set(MODULE_TYPE, safe.ordinal());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND
                && SafeBoxViewingCoordinator.handleProxyInteraction(
                serverPlayer.getServer(), getSessionId(), serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
