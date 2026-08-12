package net.austizz.ultimatebankingsystem.entity.custom;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.npc.BankTellerInteractionManager;
import net.austizz.ultimatebankingsystem.npc.BankTellerPaymentInteractionManager;
import net.austizz.ultimatebankingsystem.npc.BankTellerService;
import net.austizz.ultimatebankingsystem.npc.BankTellerUseLease;
import net.austizz.ultimatebankingsystem.npc.ShopCashierInteractionManager;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

public class BankTellerEntity extends PathfinderMob {

    public static final int VARIANT_MALE = 0;
    public static final int VARIANT_FEMALE = 1;
    public static final String EGG_BOUND_BANK_ID_TAG = "ubs_teller_bank_id";
    private static final double ESCORT_MOVEMENT_SPEED = 0.35D;
    private static final double CUSTOMER_USE_DISTANCE_SQ = 8.0D * 8.0D;
    private static final long CUSTOMER_USE_LEASE_TICKS = 20L * 10L;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> FIXED_YAW =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> BOUND_BANK_UUID =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> CASHIER =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> SHOP_ID =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> EMPLOYEE_ID =
            SynchedEntityData.defineId(BankTellerEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID pendingRemovePlayer;
    private long pendingRemoveUntilTick;
    private UUID escortMovementLease;
    private final BankTellerUseLease customerUseLease = new BankTellerUseLease(CUSTOMER_USE_LEASE_TICKS);

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
        builder.define(CASHIER, false);
        builder.define(SHOP_ID, Optional.empty());
        builder.define(EMPLOYEE_ID, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (escortMovementLease == null) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoAi(true);
            setMovementSpeed(0.0D);
            applyBodyRotation(this.entityData.get(FIXED_YAW));
        } else {
            this.setNoAi(false);
            setMovementSpeed(ESCORT_MOVEMENT_SPEED);
        }
        this.setInvulnerable(true);
        if (this.getHealth() < this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
        if (!level().isClientSide()) {
            clearInvalidCustomerUse();
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
        if (escortMovementLease != null) {
            super.travel(travelVector);
        }
    }

    public boolean beginEscortMovementLease(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        if (escortMovementLease != null) {
            return escortMovementLease.equals(sessionId);
        }
        escortMovementLease = sessionId;
        this.setNoAi(false);
        setMovementSpeed(ESCORT_MOVEMENT_SPEED);
        return true;
    }

    public boolean hasEscortMovementLease(UUID sessionId) {
        return sessionId != null && sessionId.equals(escortMovementLease);
    }

    public boolean endEscortMovementLease(UUID sessionId) {
        if (!hasEscortMovementLease(sessionId)) {
            return false;
        }
        restoreStationaryState();
        return true;
    }

    private void restoreStationaryState() {
        escortMovementLease = null;
        this.getNavigation().stop();
        this.setNoAi(true);
        setMovementSpeed(0.0D);
        this.setDeltaMovement(Vec3.ZERO);
        this.setInvulnerable(true);
        applyBodyRotation(this.entityData.get(FIXED_YAW));
    }

    private void setMovementSpeed(double speed) {
        var attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.setBaseValue(speed);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        if (!isCashier()) {
            InteractionResult viewingResult = handleViewingInteraction(serverPlayer);
            if (viewingResult != null) {
                return viewingResult;
            }
        }

        if (isCashier()) {
            MinecraftServer server = serverPlayer.getServer();
            CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
            if (centralBank != null && ShopService.hasCashierTerminalSelection(serverPlayer.getUUID())) {
                ShopService.ShopActionResult result = serverPlayer.isShiftKeyDown()
                        ? ShopService.cancelCashierTerminalSelection(serverPlayer, "Cashier-terminal link mode cancelled.")
                        : ShopService.applyCashierTerminalSelection(serverPlayer, centralBank, this);
                serverPlayer.sendSystemMessage(UbsTranslations.literal((result.success() ? "§a" : "§c") + result.message()));
                return InteractionResult.CONSUME;
            }
        }

        if (serverPlayer.isShiftKeyDown()) {
            return handleRemovalClick(serverPlayer);
        }

        if (isCashier()) {
            if (ShopCashierInteractionManager.handleInteract(serverPlayer, this, hand)) {
                return InteractionResult.CONSUME;
            }
            serverPlayer.sendSystemMessage(UbsTranslations.literal("§eHold a shopping basket to checkout."));
            return InteractionResult.CONSUME;
        }

        if (!tryBeginCustomerUse(serverPlayer)) {
            serverPlayer.sendSystemMessage(UbsTranslations.literal(
                    "§eThis bank teller is currently assisting another customer."));
            return InteractionResult.CONSUME;
        }

        if (BankTellerPaymentInteractionManager.handleInteract(serverPlayer, this, hand)) {
            return InteractionResult.CONSUME;
        }

        sendBankMottoMessage(serverPlayer);
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            endCustomerUse(serverPlayer.getUUID());
            serverPlayer.sendSystemMessage(UbsTranslations.literal("§cBank teller service is unavailable."));
            return InteractionResult.CONSUME;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            endCustomerUse(serverPlayer.getUUID());
            serverPlayer.sendSystemMessage(UbsTranslations.literal("§cBank data is unavailable right now."));
            return InteractionResult.CONSUME;
        }
        PacketDistributor.sendToPlayer(serverPlayer, BankTellerService.buildOpenPayload(server, centralBank, serverPlayer, this));
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleViewingInteraction(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        boolean playerActive = SafeBoxViewingCoordinator.isPlayerActive(server, player.getUUID());
        boolean tellerActive = SafeBoxViewingCoordinator.isTellerActive(server, this.getUUID());
        if (!playerActive && !tellerActive) {
            return null;
        }
        SafeBoxViewingCoordinator.TellerInteraction status =
                SafeBoxViewingCoordinator.handleTellerInteraction(
                        server, player.getUUID(), this.getUUID());
        String message = switch (status) {
            case CONFIRM_REQUIRED -> "§eInteract with this teller again within 10 seconds to finish viewing.";
            case FINISHED -> "§aPrivate box viewing finished. Everything has been returned.";
            case WRONG_TELLER -> "§cFinish the session with the teller who brought your box.";
            case NOT_ACTIVE -> "§eThis teller is currently assisting another customer.";
        };
        player.sendSystemMessage(UbsTranslations.literal(message));
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleRemovalClick(ServerPlayer player) {
        if (!canRemove(player)) {
            player.sendSystemMessage(UbsTranslations.literal("§cOnly the owner or an operator can remove this teller."));
            return InteractionResult.CONSUME;
        }

        long now = this.level().getGameTime();
        if (player.getUUID().equals(pendingRemovePlayer) && now <= pendingRemoveUntilTick) {
            ItemStack egg = new ItemStack(isCashier()
                    ? ModItems.CASHIER_SPAWN_EGG.get()
                    : ModItems.BANK_TELLER_SPAWN_EGG.get());
            UUID bankId = getBoundBankId();
            if (!isCashier() && bankId != null) {
                applyBankBindingToEgg(egg, bankId, resolveBankName(player.getServer(), bankId));
            }
            if (!player.getInventory().add(egg)) {
                player.drop(egg, false);
            }
            BankTellerInteractionManager.cancelForTeller(this.getUUID(), "Teller removed.");
            BankTellerPaymentInteractionManager.cancelForTeller(this.getUUID(), "Teller removed.");
            ShopCashierInteractionManager.cancelForCashier(this.getUUID(), "Cashier removed.");
            if (isCashier()) {
                ShopService.unlinkCashierTerminal(
                        player.getServer(),
                        getOwnerUUID(),
                        getShopId(),
                        getUUID()
                );
            }
            clearCustomerUse();
            this.discard();
            player.sendSystemMessage(UbsTranslations.literal("§a" + (isCashier() ? "Cashier" : "Bank Teller")
                    + " removed and spawn egg returned."));
            return InteractionResult.CONSUME;
        }

        pendingRemovePlayer = player.getUUID();
        pendingRemoveUntilTick = now + 100L;
        player.sendSystemMessage(UbsTranslations.literal("§eWarning: shift-right-click again within 5 seconds to remove this teller."));
        return InteractionResult.CONSUME;
    }

    private boolean canRemove(ServerPlayer player) {
        UUID owner = getOwnerUUID();
        return (owner != null && owner.equals(player.getUUID())) || player.hasPermissions(2);
    }

    public boolean tryBeginCustomerUse(ServerPlayer player) {
        if (player == null || isCashier() || player.level() != level()
                || player.distanceToSqr(this) > CUSTOMER_USE_DISTANCE_SQ) {
            return false;
        }
        clearInvalidCustomerUse();
        return customerUseLease.acquire(player.getUUID(), level().getGameTime());
    }

    public boolean refreshCustomerUse(ServerPlayer player) {
        if (player == null || isCashier() || player.level() != level()
                || player.distanceToSqr(this) > CUSTOMER_USE_DISTANCE_SQ) {
            return false;
        }
        clearInvalidCustomerUse();
        return customerUseLease.refresh(player.getUUID(), level().getGameTime());
    }

    public boolean isCustomerUseHeldBy(UUID playerId) {
        return playerId != null && playerId.equals(customerUseLease.holder(level().getGameTime()));
    }

    public boolean endCustomerUse(UUID playerId) {
        return customerUseLease.release(playerId);
    }

    public void clearCustomerUse() {
        customerUseLease.clear();
    }

    private void clearInvalidCustomerUse() {
        UUID holderId = customerUseLease.holder(level().getGameTime());
        if (holderId == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer holder = serverLevel.getServer().getPlayerList().getPlayer(holderId);
        if (holder == null || !holder.isAlive() || holder.level() != level()
                || holder.distanceToSqr(this) > CUSTOMER_USE_DISTANCE_SQ) {
            customerUseLease.clear();
        }
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

    public boolean isCashier() {
        return this.entityData.get(CASHIER);
    }

    public void setCashier(boolean cashier) {
        this.entityData.set(CASHIER, cashier);
        updateDisplayNameFromBank();
    }

    public UUID getShopId() {
        return this.entityData.get(SHOP_ID).orElse(null);
    }

    public void setShopId(UUID shopId) {
        this.entityData.set(SHOP_ID, Optional.ofNullable(shopId));
    }

    public UUID getEmployeeId() {
        return this.entityData.get(EMPLOYEE_ID).orElse(null);
    }

    public void setEmployeeId(UUID employeeId) {
        this.entityData.set(EMPLOYEE_ID, Optional.ofNullable(employeeId));
    }

    public void setBoundBankId(UUID bankId) {
        this.entityData.set(BOUND_BANK_UUID, Optional.ofNullable(bankId));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("NoAI", true);
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Variant", getVariant());
        tag.putFloat("FixedYaw", this.entityData.get(FIXED_YAW));
        tag.putBoolean("Cashier", isCashier());
        UUID shopId = getShopId();
        if (shopId != null) {
            tag.putUUID("ShopId", shopId);
        }
        UUID employeeId = getEmployeeId();
        if (employeeId != null) {
            tag.putUUID("EmployeeId", employeeId);
        }
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
        if (tag.contains("Cashier")) {
            setCashier(tag.getBoolean("Cashier"));
        } else {
            setCashier(false);
        }
        if (tag.hasUUID("ShopId")) {
            setShopId(tag.getUUID("ShopId"));
        } else {
            setShopId(null);
        }
        if (tag.hasUUID("EmployeeId")) {
            setEmployeeId(tag.getUUID("EmployeeId"));
        } else {
            setEmployeeId(null);
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
        restoreStationaryState();
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
        this.setCashier(false);
        this.setShopId(null);
        this.setEmployeeId(null);
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
        ItemStackDataCompat.setCustomData(egg, tag);

        String safeBankName = bankName == null || bankName.isBlank() ? shortId(bankId) : bankName.trim();
        ItemStackDataCompat.setCustomName(egg,
                Component.literal("[" + safeBankName + "] ")
                        .append(UbsTranslations.literal("Teller Spawn Egg"))
                        .withStyle(ChatFormatting.AQUA));
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
                if (teller.isCashier()) {
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
            String role = isCashier() ? "Cashier" : "Bank Teller";
            this.setCustomName(Component.literal("[" + prefix + "] ")
                    .append(UbsTranslations.literal(role))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            this.setCustomName(UbsTranslations.literal(isCashier() ? "Cashier" : "Bank Teller").withStyle(ChatFormatting.AQUA));
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
        CompoundTag data = ItemStackDataCompat.getCustomData(stack);
        return data == null ? null : data.copy();
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
