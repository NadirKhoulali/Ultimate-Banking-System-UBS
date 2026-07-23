package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.custom.SecureSafeBlock;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.heist.HeistSavedData;
import net.austizz.ultimatebankingsystem.heist.HeistSession;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.menu.SecureSafeMenu;
import net.austizz.ultimatebankingsystem.network.SecureSafeActionPayload;
import net.austizz.ultimatebankingsystem.network.SecureSafeOpenPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public class SecureSafeBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS_PER_SHELF = 6;
    public static final int COMPACT_SHELVES = 1;
    public static final int TALL_SHELVES = 2;
    public static final int COMPACT_DISPLAY_SLOTS = COMPACT_SHELVES * SLOTS_PER_SHELF;
    public static final int TALL_DISPLAY_SLOTS = TALL_SHELVES * SLOTS_PER_SHELF;
    public static final int MAX_DISPLAY_SLOTS = TALL_DISPLAY_SLOTS;
    public static final int SHELF_SLOT_LIMIT = 25;
    public static final int CHEST_SLOT_COUNT = 9;
    public static final int CHEST_SLOT_START = MAX_DISPLAY_SLOTS;
    public static final int TOTAL_SLOT_COUNT = CHEST_SLOT_START + CHEST_SLOT_COUNT;

    public static final int OPEN_ANIMATION_TICKS = 30;
    public static final int CLOSE_ANIMATION_TICKS = 26;

    private static final int MIN_ATTEMPTS = 1;
    private static final int MAX_ATTEMPTS = 12;
    private static final String STORAGE_KEY = "inventory";
    private static final double MAX_INTERACT_DISTANCE = 6.25D;
    private static final double SHELF_MIN_X = 1.55D;
    private static final double SHELF_MAX_X = 14.45D;
    private static final double SHELF_MIN_Z = 1.55D;
    private static final double SHELF_MAX_Z = 14.45D;
    private static final double SHELF_ROW_SPLIT_Z = 7.75D;
    private static final double[] SHELF_HIT_Y_OFFSETS = {1.15D, 2.35D, 3.55D};

    private boolean tall;
    private boolean configured;
    private String pinHash = "";
    private int maxAttempts = 3;
    private int attemptsRemaining = 3;
    private boolean chestUpgradeInstalled;
    private boolean targetOpen;
    private float previousAnimationProgress;
    private float animationProgress;
    private UUID heistDrillSessionId;
    private UUID heistBreachSessionId;

    private final ItemStackHandler items = new ItemStackHandler(TOTAL_SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }

        @Override
        public int getSlotLimit(int slot) {
            return isDisplaySlot(slot) ? SHELF_SLOT_LIMIT : super.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < 0 || slot >= getSlots()) {
                return false;
            }
            if (isDisplaySlot(slot)) {
                return isShelfDisplayItem(stack);
            }
            if (isChestSlot(slot)) {
                return chestUpgradeInstalled;
            }
            return false;
        }
    };

    public SecureSafeBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state != null && state.is(ModBlocks.STANDING_SAFE.get()));
    }

    public SecureSafeBlockEntity(BlockPos pos, BlockState state, boolean tall) {
        super(ModBlockEntities.SECURE_SAFE.get(), pos, state);
        this.tall = tall;
    }

    public boolean isTallSafe() {
        return tall;
    }

    public int shelfCount() {
        return tall ? TALL_SHELVES : COMPACT_SHELVES;
    }

    public int displaySlotCount() {
        return tall ? TALL_DISPLAY_SLOTS : COMPACT_DISPLAY_SLOTS;
    }

    public boolean hasChestUpgrade() {
        return chestUpgradeInstalled;
    }

    public boolean isTargetOpen() {
        return targetOpen;
    }

    public boolean attachHeistDrill(UUID sessionId) {
        clearStaleHeistState();
        if (sessionId == null || heistDrillSessionId != null || heistBreachSessionId != null) return false;
        heistDrillSessionId = sessionId;
        markUpdated();
        return true;
    }

    private void clearStaleHeistState() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        HeistSavedData data = HeistSavedData.get(serverLevel.getServer());
        if (heistDrillSessionId != null) {
            HeistSession session = data.session(heistDrillSessionId);
            String dimension = serverLevel.dimension().location().toString();
            boolean live = session != null && session.phase().isRunning()
                    && session.safeDrill(dimension, worldPosition) != null;
            if (!live) heistDrillSessionId = null;
        }
        if (heistBreachSessionId != null) {
            HeistSession session = data.session(heistBreachSessionId);
            String key = "safe|" + serverLevel.dimension().location() + "|" + worldPosition.asLong();
            boolean live = session != null && session.phase().isRunning() && session.isBreached(key);
            if (!live) {
                heistBreachSessionId = null;
                setTargetOpen(false);
            }
        }
    }

    public boolean hasHeistDrill(UUID sessionId) {
        return sessionId != null && sessionId.equals(heistDrillSessionId);
    }

    public boolean isHeistDrillAttached() {
        return heistDrillSessionId != null;
    }

    public boolean completeHeistBreach(UUID sessionId) {
        if (!hasHeistDrill(sessionId)) return false;
        heistDrillSessionId = null;
        heistBreachSessionId = sessionId;
        setTargetOpen(true);
        markUpdated();
        return true;
    }

    public boolean isHeistBreached(UUID sessionId) {
        return sessionId != null && sessionId.equals(heistBreachSessionId);
    }

    public void clearHeistDrill(UUID sessionId) {
        if (sessionId != null && sessionId.equals(heistDrillSessionId)) {
            heistDrillSessionId = null;
            markUpdated();
        }
    }

    public void endHeistBreach(UUID sessionId) {
        boolean changed = false;
        if (sessionId != null && sessionId.equals(heistDrillSessionId)) {
            heistDrillSessionId = null;
            changed = true;
        }
        if (sessionId != null && sessionId.equals(heistBreachSessionId)) {
            heistBreachSessionId = null;
            setTargetOpen(false);
            changed = true;
        }
        if (changed) markUpdated();
    }

    public boolean isOpenForStorage() {
        return targetOpen && animationProgress >= 0.88F;
    }

    public float getAnimationProgress(float partialTick) {
        return Mth.lerp(partialTick, previousAnimationProgress, animationProgress);
    }

    public IItemHandler getItemHandler() {
        return items;
    }

    public ItemStack getDisplayStack(int slot) {
        if (!isDisplaySlot(slot)) {
            return ItemStack.EMPTY;
        }
        return items.getStackInSlot(slot);
    }

    public void openFor(ServerPlayer player) {
        sendOpen(player, false, "", true);
    }

    public void handleAction(ServerPlayer player, SecureSafeActionPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        String action = payload.action().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "SETUP" -> setup(player, payload);
            case "LOGIN" -> login(player, payload.pin());
            case "TOGGLE_OPEN" -> toggleOpen(player, payload.pin());
            case "CLOSE_SAFE" -> closeSafe(player, payload.pin());
            case "OPEN_STORAGE" -> openChestCompartment(player);
            case "OPEN_UPGRADE_SLOT" -> openChestUpgradeInstaller(player, payload.pin());
            case "INSTALL_CHEST_UPGRADE" -> openChestUpgradeInstaller(player, payload.pin());
            default -> sendOpen(player, false, "Unknown safe action.", false);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SecureSafeBlockEntity safe) {
        if (level == null || safe == null) {
            return;
        }
        safe.previousAnimationProgress = safe.animationProgress;
        float step = safe.targetOpen ? 1.0F / OPEN_ANIMATION_TICKS : 1.0F / CLOSE_ANIMATION_TICKS;
        float next = safe.targetOpen
                ? Math.min(1.0F, safe.animationProgress + step)
                : Math.max(0.0F, safe.animationProgress - step);
        if (next == safe.animationProgress) {
            return;
        }
        safe.animationProgress = next;
        if (!level.isClientSide() && (next == 0.0F || next == 1.0F)) {
            safe.setChanged();
        }
    }

    public int resolveShelfSlotFromLook(Player player, Direction facing) {
        if (player == null || level == null) {
            return -1;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        if (look.lengthSqr() < 1.0E-6D || Math.abs(look.y) < 1.0E-5D) {
            return -1;
        }

        int bestSlot = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int shelf = 0; shelf < shelfCount(); shelf++) {
            double shelfBase = shelfBasePixels(tall, shelf);
            for (double yOffset : SHELF_HIT_Y_OFFSETS) {
                double planeY = worldPosition.getY() + (shelfBase + yOffset) / 16.0D;
                double distance = (planeY - eye.y) / look.y;
                if (distance < 0.0D || distance > MAX_INTERACT_DISTANCE || distance >= bestDistance) {
                    continue;
                }
                Vec3 point = eye.add(look.scale(distance));
                double[] model = toModelCoordinates(facing, point);
                int slot = slotFromModelCoordinates(shelf, model[0], model[1]);
                if (slot >= 0) {
                    bestSlot = slot;
                    bestDistance = distance;
                }
            }
        }
        return bestSlot;
    }

    public boolean isChestCompartmentHit(Direction facing, Vec3 hitLocation) {
        double[] model = toModelCoordinates(facing, hitLocation);
        double modelY = (hitLocation.y - worldPosition.getY()) * 16.0D;
        double lowestShelf = shelfBasePixels(tall, 0);
        return model[0] >= SHELF_MIN_X
                && model[0] <= SHELF_MAX_X
                && model[1] <= 3.0D
                && modelY >= 1.4D
                && modelY <= lowestShelf - 1.0D;
    }

    public boolean placeShelfItem(ServerPlayer player, int slot, ItemStack held) {
        if (player == null || held == null || held.isEmpty() || !isOpenForStorage() || !isDisplaySlot(slot)) {
            return false;
        }
        if (!isShelfDisplayItem(held)) {
            showStatus(player, "Only gold bars, silver bars, bills, and coins fit on the safe shelves.");
            return true;
        }

        ItemStack existing = items.getStackInSlot(slot);
        if (existing.isEmpty()) {
            ItemStack placed = held.copy();
            placed.setCount(1);
            items.setStackInSlot(slot, placed);
            shrinkHeld(player, held, 1);
            return true;
        }

        if (!ItemStackDataCompat.sameItemSameComponents(existing, held)) {
            showStatus(player, "That shelf slot already holds another item type.");
            return true;
        }

        int limit = Math.min(SHELF_SLOT_LIMIT, Math.min(existing.getMaxStackSize(), items.getSlotLimit(slot)));
        if (existing.getCount() >= limit) {
            showStatus(player, "That shelf stack is full.");
            return true;
        }
        existing.grow(1);
        shrinkHeld(player, held, 1);
        markUpdated();
        return true;
    }

    public boolean takeShelfItem(ServerPlayer player, int slot, boolean fullStack) {
        if (player == null || !isOpenForStorage() || !isDisplaySlot(slot)) {
            return false;
        }
        ItemStack stored = items.getStackInSlot(slot);
        if (stored.isEmpty()) {
            return true;
        }
        int amount = fullStack ? stored.getCount() : 1;
        ItemStack extracted = items.extractItem(slot, amount, false);
        if (extracted.isEmpty()) {
            return true;
        }
        if (!player.getInventory().add(extracted)) {
            player.drop(extracted, false);
        }
        return true;
    }

    public boolean installChestUpgradeFromWorld(ServerPlayer player, ItemStack held) {
        if (player == null || !isOpenForStorage()) {
            return false;
        }
        if (chestUpgradeInstalled) {
            showStatus(player, "Chest compartment already installed. Right-click it empty-handed to open it.");
            return true;
        }
        if (held == null || held.isEmpty() || !isChestUpgradeItem(held)) {
            showStatus(player, "Install a safe chest upgrade through the verified safe panel.");
            return true;
        }
        installChestUpgradeItem(held);
        showStatus(player, "Chest compartment installed.");
        return true;
    }

    public boolean installChestUpgradeItem(ItemStack stack) {
        if (level == null || level.isClientSide() || chestUpgradeInstalled || stack == null || stack.isEmpty() || !isChestUpgradeItem(stack)) {
            return false;
        }
        chestUpgradeInstalled = true;
        stack.shrink(1);
        markUpdated();
        return true;
    }

    public void openChestUpgradeInstaller(ServerPlayer player, String pin) {
        if (player == null) {
            return;
        }
        if (!requirePin(player, pin)) {
            return;
        }
        if (!isOpenForStorage()) {
            sendOpen(player, true, "Open the safe door before installing the chest upgrade.", false);
            return;
        }
        if (chestUpgradeInstalled) {
            sendOpen(player, true, "Chest compartment already installed. Right-click the lower compartment to open it.", true);
            return;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new SecureSafeMenu(containerId, inventory, this),
                        Component.literal("Safe Chest Upgrade")
                ),
                buffer -> {
                    buffer.writeBlockPos(worldPosition);
                    buffer.writeBoolean(tall);
                    buffer.writeBoolean(chestUpgradeInstalled);
                }
        );
    }

    public boolean openChestCompartment(ServerPlayer player) {
        if (player == null || !isOpenForStorage()) {
            return false;
        }
        if (!chestUpgradeInstalled) {
            showStatus(player, "Install a chest upgrade in the lower compartment first.");
            return true;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new SecureSafeMenu(containerId, inventory, this),
                        getDisplayName()
                ),
                buffer -> {
                    buffer.writeBlockPos(worldPosition);
                    buffer.writeBoolean(tall);
                    buffer.writeBoolean(chestUpgradeInstalled);
                }
        );
        return true;
    }

    public void onMenuClosed() {
        // Chest access no longer controls the door. The PIN panel toggles the animated door explicitly.
    }

    public boolean stillValid(Player player) {
        if (level == null || player == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D
        ) <= 100.0D;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(tall
                ? "container.ultimatebankingsystem.standing_safe"
                : "container.ultimatebankingsystem.compact_safe");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SecureSafeMenu(containerId, playerInventory, this);
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.6D,
                    worldPosition.getZ() + 0.5D,
                    stack.copy()
            );
            items.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public boolean isDisplaySlot(int slot) {
        return slot >= 0 && slot < displaySlotCount();
    }

    public boolean isChestSlot(int slot) {
        return slot >= CHEST_SLOT_START && slot < CHEST_SLOT_START + CHEST_SLOT_COUNT;
    }

    public boolean isActiveSlot(int slot) {
        return isDisplaySlot(slot) || isChestSlot(slot);
    }

    public static boolean isShelfDisplayItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(ModBlocks.GOLD_BAR.get().asItem())
                || stack.is(ModBlocks.SILVER_BAR.get().asItem())
                || stack.is(ModItems.ONE_DOLLAR_BILL.get())
                || stack.is(ModItems.TWO_DOLLAR_BILL.get())
                || stack.is(ModItems.FIVE_DOLLAR_BILL.get())
                || stack.is(ModItems.TEN_DOLLAR_BILL.get())
                || stack.is(ModItems.TWENTY_DOLLAR_BILL.get())
                || stack.is(ModItems.FIFTY_DOLLAR_BILL.get())
                || stack.is(ModItems.HUNDRED_DOLLAR_BILL.get())
                || stack.is(ModItems.PENNY_COIN.get())
                || stack.is(ModItems.NICKEL_COIN.get())
                || stack.is(ModItems.DIME_COIN.get())
                || stack.is(ModItems.QUARTER_COIN.get())
                || stack.is(ModItems.HALF_DOLLAR_COIN.get())
                || MoneyStackBlock.BillDenomination.fromStackItem(stack.getItem()) != null;
    }

    public static boolean isChestUpgradeItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.SAFE_CHEST_UPGRADE.get());
    }

    public static double shelfBasePixels(boolean tall, int shelf) {
        if (tall) {
            return shelf <= 0 ? 11.0D : 22.0D;
        }
        return 8.0D;
    }

    private void setup(ServerPlayer player, SecureSafeActionPayload payload) {
        if (configured) {
            sendOpen(player, false, "Safe PIN already configured.", false);
            return;
        }
        String newPin = sanitizePin(payload.newPin());
        if (!isValidPin(newPin)) {
            sendOpen(player, false, "PIN must be 4-12 digits.", false);
            return;
        }
        this.pinHash = hashPin(newPin);
        this.configured = true;
        this.maxAttempts = clampAttempts(payload.maxAttempts());
        this.attemptsRemaining = this.maxAttempts;
        markUpdated();
        sendOpen(player, true, "Safe PIN set. Use the door toggle to open it.", true);
    }

    private void login(ServerPlayer player, String pin) {
        if (!configured) {
            sendOpen(player, false, "Set a safe PIN first.", false);
            return;
        }
        if (matchesPin(pin)) {
            attemptsRemaining = maxAttempts;
            markUpdated();
            sendOpen(player, true, "Safe unlocked.", true);
            return;
        }
        registerFailedAttempt();
        sendOpen(player, false, attemptsRemaining <= 0 ? "Safe locked after too many wrong attempts." : "PIN rejected.", false);
    }

    private void toggleOpen(ServerPlayer player, String pin) {
        if (!requirePin(player, pin)) {
            return;
        }
        setTargetOpen(!targetOpen);
        sendOpen(player, true, targetOpen ? "Safe door opening." : "Safe door closing.", true);
    }

    private void closeSafe(ServerPlayer player, String pin) {
        if (!requirePin(player, pin)) {
            return;
        }
        setTargetOpen(false);
        sendOpen(player, true, "Safe door closing.", true);
    }

    private boolean requirePin(ServerPlayer player, String pin) {
        if (!configured) {
            sendOpen(player, false, "Set a safe PIN first.", false);
            return false;
        }
        if (matchesPin(pin)) {
            attemptsRemaining = maxAttempts;
            markUpdated();
            return true;
        }
        registerFailedAttempt();
        sendOpen(player, false, attemptsRemaining <= 0 ? "Safe locked after too many wrong attempts." : "PIN rejected.", false);
        return false;
    }

    private void registerFailedAttempt() {
        attemptsRemaining = Math.max(0, attemptsRemaining - 1);
        markUpdated();
    }

    private void setTargetOpen(boolean open) {
        if (targetOpen == open) {
            return;
        }
        targetOpen = open;
        setOpenBlockState(open);
        markUpdated();
    }

    private void setOpenBlockState(boolean open) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(SecureSafeBlock.OPEN)) {
            return;
        }
        if (state.getValue(SecureSafeBlock.OPEN) != open) {
            level.setBlock(worldPosition, state.setValue(SecureSafeBlock.OPEN, open), 3);
        }
        if (tall) {
            BlockPos upperPos = worldPosition.above();
            BlockState upper = level.getBlockState(upperPos);
            if (upper.getBlock() == state.getBlock()
                    && upper.hasProperty(SecureSafeBlock.HALF)
                    && upper.getValue(SecureSafeBlock.HALF) == DoubleBlockHalf.UPPER
                    && upper.hasProperty(SecureSafeBlock.OPEN)
                    && upper.getValue(SecureSafeBlock.OPEN) != open) {
                level.setBlock(upperPos, upper.setValue(SecureSafeBlock.OPEN, open), 3);
            }
        }
    }

    private SecureSafeOpenPayload snapshot(boolean authenticated, String message, boolean messageSuccess) {
        Level level = getLevel();
        return new SecureSafeOpenPayload(
                level == null ? "" : level.dimension().location().toString(),
                worldPosition.getX(),
                worldPosition.getY(),
                worldPosition.getZ(),
                configured,
                authenticated,
                tall,
                chestUpgradeInstalled,
                targetOpen,
                maxAttempts,
                attemptsRemaining,
                message,
                messageSuccess
        );
    }

    private void sendOpen(ServerPlayer player, boolean authenticated, String message, boolean messageSuccess) {
        PacketDistributor.sendToPlayer(player, snapshot(authenticated, message, messageSuccess));
    }

    private int slotFromModelCoordinates(int shelf, double modelX, double modelZ) {
        if (shelf < 0 || shelf >= shelfCount()
                || modelX < SHELF_MIN_X || modelX > SHELF_MAX_X
                || modelZ < SHELF_MIN_Z || modelZ > SHELF_MAX_Z) {
            return -1;
        }
        int col = 2 - Mth.clamp((int) ((modelX - SHELF_MIN_X) / ((SHELF_MAX_X - SHELF_MIN_X) / 3.0D)), 0, 2);
        int row = modelZ < SHELF_ROW_SPLIT_Z ? 0 : 1;
        int slot = shelf * SLOTS_PER_SHELF + row * 3 + col;
        return isDisplaySlot(slot) ? slot : -1;
    }

    private double[] toModelCoordinates(Direction facing, Vec3 worldPoint) {
        double relX = worldPoint.x - worldPosition.getX();
        double relZ = worldPoint.z - worldPosition.getZ();
        relX = Mth.clamp(relX, -0.25D, 1.25D);
        relZ = Mth.clamp(relZ, -0.25D, 1.25D);
        Direction horizontal = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
        double x = relX;
        double z = relZ;
        if (horizontal == Direction.SOUTH) {
            x = 1.0D - relX;
            z = 1.0D - relZ;
        } else if (horizontal == Direction.EAST) {
            x = 1.0D - relZ;
            z = 1.0D - relX;
        } else if (horizontal == Direction.WEST) {
            x = relZ;
            z = relX;
        }
        return new double[]{x * 16.0D, z * 16.0D};
    }

    private void shrinkHeld(ServerPlayer player, ItemStack stack, int amount) {
        if (player.getAbilities().instabuild || stack == null || stack.isEmpty() || amount <= 0) {
            return;
        }
        stack.shrink(amount);
        player.getInventory().setChanged();
    }

    private void showStatus(ServerPlayer player, String message) {
        if (player != null && message != null && !message.isBlank()) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    private boolean matchesPin(String pin) {
        String clean = sanitizePin(pin);
        return configured && !pinHash.isBlank() && hashPin(clean).equals(pinHash);
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salt = "ultimatebankingsystem:secure_safe:" + worldPosition.asLong();
            byte[] bytes = digest.digest((salt + ":" + sanitizePin(pin)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sanitizePin(String pin) {
        return pin == null ? "" : pin.trim();
    }

    private static boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{4,12}");
    }

    private static int clampAttempts(int value) {
        return Mth.clamp(value <= 0 ? 3 : value, MIN_ATTEMPTS, MAX_ATTEMPTS);
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
        BlockState state = getBlockState();
        this.tall = tag.contains("tall") ? tag.getBoolean("tall") : state.is(ModBlocks.STANDING_SAFE.get());
        this.configured = tag.getBoolean("configured");
        this.pinHash = tag.getString("pin_hash");
        this.maxAttempts = clampAttempts(tag.getInt("max_attempts"));
        this.attemptsRemaining = Mth.clamp(
                tag.contains("attempts_remaining") ? tag.getInt("attempts_remaining") : maxAttempts,
                0,
                maxAttempts
        );
        this.chestUpgradeInstalled = tag.getBoolean("chest_upgrade_installed");
        this.targetOpen = tag.getBoolean("target_open");
        this.heistDrillSessionId = tag.hasUUID("heist_drill_session") ? tag.getUUID("heist_drill_session") : null;
        this.heistBreachSessionId = tag.hasUUID("heist_breach_session") ? tag.getUUID("heist_breach_session") : null;
        this.animationProgress = Mth.clamp(tag.getFloat("animation_progress"), 0.0F, 1.0F);
        this.previousAnimationProgress = animationProgress;
        if (tag.contains(STORAGE_KEY)) {
            CompoundTag storageTag = tag.getCompound(STORAGE_KEY).copy();
            storageTag.putInt("Size", TOTAL_SLOT_COUNT);
            items.deserializeNBT(registries, storageTag);
        } else {
            CompoundTag empty = new CompoundTag();
            empty.putInt("Size", TOTAL_SLOT_COUNT);
            items.deserializeNBT(registries, empty);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("tall", tall);
        tag.putBoolean("configured", configured);
        tag.putString("pin_hash", pinHash == null ? "" : pinHash);
        tag.putInt("max_attempts", maxAttempts);
        tag.putInt("attempts_remaining", attemptsRemaining);
        tag.putBoolean("chest_upgrade_installed", chestUpgradeInstalled);
        tag.putBoolean("target_open", targetOpen);
        if (heistDrillSessionId != null) tag.putUUID("heist_drill_session", heistDrillSessionId);
        if (heistBreachSessionId != null) tag.putUUID("heist_breach_session", heistBreachSessionId);
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
