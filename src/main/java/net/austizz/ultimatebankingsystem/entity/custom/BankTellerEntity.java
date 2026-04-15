package net.austizz.ultimatebankingsystem.entity.custom;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.npc.BankTellerInteractionManager;
import net.austizz.ultimatebankingsystem.npc.BankTellerPaymentInteractionManager;
import net.austizz.ultimatebankingsystem.npc.BankTellerService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

public class BankTellerEntity extends PathfinderMob {

    public static final int VARIANT_MALE = 0;
    public static final int VARIANT_FEMALE = 1;
    public static final int MAX_TELLERS_PER_BANK = 5;
    public static final String EGG_BOUND_BANK_ID_TAG = "ubs_teller_bank_id";

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> FIXED_YAW =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> BOUND_BANK_UUID =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID pendingRemovePlayer;
    private long pendingRemoveUntilTick;

    public BankTellerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setNoAi(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(VARIANT, VARIANT_MALE);
        builder.define(FIXED_YAW, 0.0F);
        builder.define(BOUND_BANK_UUID, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoAi(true);
        this.setInvulnerable(true);
        applyBodyRotation(this.entityData.get(FIXED_YAW));
        if (this.getHealth() < this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    @Override
    public void checkDespawn() {
        // Never despawn naturally.
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        // No-op to keep teller stationary.
    }

    @Override
    public void push(double x, double y, double z) {
        // No-op to keep teller stationary.
    }

    @Override
    public void travel(Vec3 travelVector) {
        // No-op: teller cannot move.
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        if (serverPlayer.isShiftKeyDown()) {
            return handleRemovalClick(serverPlayer);
        }

        if (BankTellerPaymentInteractionManager.handleInteract(serverPlayer, this, hand)) {
            return InteractionResult.CONSUME;
        }

        sendBankMottoMessage(serverPlayer);
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            serverPlayer.sendSystemMessage(Component.literal("§cBank teller service is unavailable."));
            return InteractionResult.CONSUME;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            serverPlayer.sendSystemMessage(Component.literal("§cBank data is unavailable right now."));
            return InteractionResult.CONSUME;
        }
        PacketDistributor.sendToPlayer(serverPlayer, BankTellerService.buildOpenPayload(server, centralBank, serverPlayer, this));
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleRemovalClick(ServerPlayer player) {
        if (!canRemove(player)) {
            player.sendSystemMessage(Component.literal("§cOnly the owner or an operator can remove this teller."));
            return InteractionResult.CONSUME;
        }

        long now = this.level().getGameTime();
        if (player.getUUID().equals(pendingRemovePlayer) && now <= pendingRemoveUntilTick) {
            ItemStack egg = new ItemStack(ModItems.BANK_TELLER_SPAWN_EGG.get());
            UUID bankId = getBoundBankId();
            if (bankId != null) {
                applyBankBindingToEgg(egg, bankId, resolveBankName(player.getServer(), bankId));
            }
            if (!player.getInventory().add(egg)) {
                player.drop(egg, false);
            }
            BankTellerInteractionManager.cancelForTeller(this.getUUID(), "Teller removed.");
            BankTellerPaymentInteractionManager.cancelForTeller(this.getUUID(), "Teller removed.");
            this.discard();
            player.sendSystemMessage(Component.literal("§aBank Teller removed and spawn egg returned."));
            return InteractionResult.CONSUME;
        }

        pendingRemovePlayer = player.getUUID();
        pendingRemoveUntilTick = now + 100L;
        player.sendSystemMessage(Component.literal("§eWarning: shift-right-click again within 5 seconds to remove this teller."));
        return InteractionResult.CONSUME;
    }

    private boolean canRemove(ServerPlayer player) {
        UUID owner = getOwnerUUID();
        return (owner != null && owner.equals(player.getUUID())) || player.hasPermissions(2);
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUUID(UUID owner) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(owner));
    }

    public int getVariant() {
        return Mth.clamp(this.entityData.get(VARIANT), VARIANT_MALE, VARIANT_FEMALE);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, Mth.clamp(variant, VARIANT_MALE, VARIANT_FEMALE));
    }

    public UUID getBoundBankId() {
        return this.entityData.get(BOUND_BANK_UUID).orElse(null);
    }

    public void setBoundBankId(UUID bankId) {
        this.entityData.set(BOUND_BANK_UUID, Optional.ofNullable(bankId));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Variant", getVariant());
        tag.putFloat("FixedYaw", this.entityData.get(FIXED_YAW));
        UUID bankId = getBoundBankId();
        if (bankId != null) {
            tag.putUUID("BoundBank", bankId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            setOwnerUUID(tag.getUUID("Owner"));
        }
        if (tag.contains("Variant")) {
            setVariant(tag.getInt("Variant"));
        }
        if (tag.contains("FixedYaw")) {
            this.entityData.set(FIXED_YAW, Mth.wrapDegrees(tag.getFloat("FixedYaw")));
        } else {
            this.entityData.set(FIXED_YAW, Mth.wrapDegrees(this.getYRot()));
        }
        if (tag.hasUUID("BoundBank")) {
            setBoundBankId(tag.getUUID("BoundBank"));
        } else {
            setBoundBankId(null);
        }
        this.setNoAi(true);
        this.setInvulnerable(true);
        applyBodyRotation(this.entityData.get(FIXED_YAW));
        updateDisplayNameFromBank();
    }

    public void initializeFromSpawn(ServerPlayer ownerPlayer) {
        var server = ownerPlayer.serverLevel().getServer();
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        int variant = centralBank != null
                ? centralBank.claimNextBankTellerVariant()
                : VARIANT_MALE;
        initializeFromSpawn(ownerPlayer, variant);
    }

    public void initializeFromSpawn(ServerPlayer ownerPlayer, int variant) {
        initializeFromSpawn(ownerPlayer, variant, null);
    }

    public void initializeFromSpawn(ServerPlayer ownerPlayer, int variant, UUID bankId) {
        this.setOwnerUUID(ownerPlayer.getUUID());
        this.setVariant(variant);
        this.setBoundBankId(bankId);
        updateDisplayNameFromBank();
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public void alignBodyTo(float yaw) {
        float normalized = Mth.wrapDegrees(yaw);
        this.entityData.set(FIXED_YAW, normalized);
        applyBodyRotation(normalized);
    }

    private void applyBodyRotation(float yaw) {
        float normalized = Mth.wrapDegrees(yaw);
        this.setYRot(normalized);
        this.yRotO = normalized;
        this.setYBodyRot(normalized);
        this.yBodyRotO = normalized;
        this.setYHeadRot(normalized);
        this.yHeadRotO = normalized;
        this.setXRot(0.0F);
        this.xRotO = 0.0F;
    }

    private void sendBankMottoMessage(ServerPlayer player) {
        UUID bankId = getBoundBankId();
        if (bankId == null || player.getServer() == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        String motto = metadata.getString("motto");
        if (motto == null || motto.isBlank()) {
            return;
        }
        String bankName = resolveBankName(player.getServer(), bankId);
        String prefix = bankName == null || bankName.isBlank() ? "Bank" : bankName;
        player.sendSystemMessage(Component.literal("§b[" + prefix + "] §f" + motto));
    }

    private String getChatPrefix(ServerPlayer player) {
        UUID bankId = getBoundBankId();
        if (bankId == null || player.getServer() == null) {
            return "[Bank Teller]";
        }
        String bankName = resolveBankName(player.getServer(), bankId);
        if (bankName == null || bankName.isBlank()) {
            return "[Bank Teller]";
        }
        return "[" + bankName + " Teller]";
    }

    public static void applyBankBindingToEgg(ItemStack egg, UUID bankId, String bankName) {
        if (egg == null || egg.isEmpty() || bankId == null) {
            return;
        }
        CompoundTag tag = readCustomTag(egg);
        if (tag == null) {
            tag = new CompoundTag();
        }
        tag.putUUID(EGG_BOUND_BANK_ID_TAG, bankId);
        egg.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        String safeBankName = bankName == null || bankName.isBlank() ? shortId(bankId) : bankName.trim();
        egg.set(DataComponents.CUSTOM_NAME,
                Component.literal("[" + safeBankName + "] Teller Spawn Egg").withStyle(ChatFormatting.AQUA));
    }

    public static UUID readBoundBankIdFromEgg(ItemStack stack) {
        CompoundTag tag = readCustomTag(stack);
        if (tag == null || !tag.hasUUID(EGG_BOUND_BANK_ID_TAG)) {
            return null;
        }
        return tag.getUUID(EGG_BOUND_BANK_ID_TAG);
    }

    public static int countActiveTellersForBank(MinecraftServer server, UUID bankId) {
        if (server == null || bankId == null) {
            return 0;
        }
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof BankTellerEntity teller)) {
                    continue;
                }
                if (bankId.equals(teller.getBoundBankId())) {
                    count++;
                }
            }
        }
        return count;
    }

    private void updateDisplayNameFromBank() {
        UUID bankId = getBoundBankId();
        MinecraftServer server = this.level() instanceof ServerLevel serverLevel ? serverLevel.getServer() : null;
        if (bankId != null && server != null) {
            String bankName = resolveBankName(server, bankId);
            String prefix = (bankName == null || bankName.isBlank())
                    ? shortId(bankId)
                    : bankName.trim();
            this.setCustomName(Component.literal("[" + prefix + "] Bank Teller").withStyle(ChatFormatting.AQUA));
        } else {
            this.setCustomName(Component.literal("Bank Teller").withStyle(ChatFormatting.AQUA));
        }
        this.setCustomNameVisible(true);
    }

    private static String resolveBankName(MinecraftServer server, UUID bankId) {
        if (server == null || bankId == null) {
            return "";
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return "";
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null || bank.getBankName() == null) {
            return "";
        }
        return bank.getBankName();
    }

    private static CompoundTag readCustomTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static String shortId(UUID id) {
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    public static BankTellerEntity spawn(ServerLevel level, Vec3 position, ServerPlayer owner, float yaw) {
        BankTellerEntity entity = new BankTellerEntity(
                net.austizz.ultimatebankingsystem.entity.ModEntities.BANK_TELLER.get(),
                level
        );
        entity.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        entity.initializeFromSpawn(owner);
        entity.alignBodyTo(yaw);
        return entity;
    }
}
