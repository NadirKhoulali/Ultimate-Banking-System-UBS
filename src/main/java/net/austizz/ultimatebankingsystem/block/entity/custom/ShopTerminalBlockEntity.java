package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.ShopTerminalBlock;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class ShopTerminalBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    private static final long MAX_PRICE_DOLLARS = 1_000_000_000_000L;
    private static final int MIN_PULSE_STRENGTH = 1;
    private static final int MAX_PULSE_STRENGTH = 15;

    private UUID ownerUuid;
    private String ownerName = "";
    private String shopName = "Payment Terminal";
    private long priceDollars = 50L;
    private UUID merchantAccountId;
    private boolean pulseOnSuccess = true;
    private boolean pulseOnFailure = true;
    private boolean pulseOnIdle = false;
    private int successPulseTicks = 15;
    private int failurePulseTicks = 8;
    private int idlePulseStrength = 3;
    private long totalSalesDollars = 0L;
    private int displayResult = 0; // 0=idle, 1=success, 2=denied
    private long displayResultUntilGameTime = 0L;

    public ShopTerminalBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PAYMENT_TERMINAL.get(), pos, blockState);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    public String getShopName() {
        return shopName == null || shopName.isBlank() ? "Payment Terminal" : shopName;
    }

    public long getPriceDollars() {
        return priceDollars;
    }

    public UUID getMerchantAccountId() {
        return merchantAccountId;
    }

    public boolean isPulseOnSuccess() {
        return pulseOnSuccess;
    }

    public boolean isPulseOnFailure() {
        return pulseOnFailure;
    }

    public int getSuccessPulseTicks() {
        return successPulseTicks;
    }

    public int getFailurePulseTicks() {
        return failurePulseTicks;
    }

    public boolean isPulseOnIdle() {
        return pulseOnIdle;
    }

    public int getIdlePulseStrength() {
        return idlePulseStrength;
    }

    public long getTotalSalesDollars() {
        return totalSalesDollars;
    }

    public int getDisplayResult() {
        return Mth.clamp(displayResult, 0, 2);
    }

    public boolean isFeedbackActive() {
        int result = getDisplayResult();
        if (result == 0) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return true;
        }
        return level.getGameTime() < displayResultUntilGameTime;
    }

    public int getFeedbackTicksRemaining() {
        if (getDisplayResult() == 0) {
            return 0;
        }
        Level level = getLevel();
        if (level == null) {
            return 20;
        }
        long remaining = displayResultUntilGameTime - level.getGameTime();
        return (int) Math.max(0L, remaining);
    }

    public boolean canConfigure(Player player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(3)) {
            return true;
        }
        if (ownerUuid == null) {
            return true;
        }
        return ownerUuid.equals(player.getUUID());
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null ? "" : ownerName.trim();
        markUpdated();
    }

    public void updateConfig(String newShopName,
                             long newPrice,
                             UUID newMerchantAccountId,
                             boolean newPulseOnSuccess,
                             boolean newPulseOnFailure,
                             boolean newPulseOnIdle,
                             int newSuccessPulseTicks,
                             int newFailurePulseTicks,
                             int newIdlePulseStrength) {
        this.shopName = sanitizeShopName(newShopName);
        this.priceDollars = clampLong(newPrice, 1L, MAX_PRICE_DOLLARS);
        this.merchantAccountId = newMerchantAccountId;
        this.pulseOnSuccess = newPulseOnSuccess;
        this.pulseOnFailure = newPulseOnFailure;
        this.pulseOnIdle = newPulseOnIdle;
        this.successPulseTicks = Mth.clamp(newSuccessPulseTicks, MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH);
        this.failurePulseTicks = Mth.clamp(newFailurePulseTicks, MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH);
        this.idlePulseStrength = Mth.clamp(newIdlePulseStrength, MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH);
        markUpdated();
    }

    public void addSale(long amountDollars) {
        if (amountDollars <= 0L) {
            return;
        }
        long safeNext;
        try {
            safeNext = Math.addExact(this.totalSalesDollars, amountDollars);
        } catch (ArithmeticException ex) {
            safeNext = Long.MAX_VALUE;
        }
        this.totalSalesDollars = safeNext;
        markUpdated();
    }

    public void triggerPulse(boolean success) {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        // Activity is in progress; idle output is overridden by the active result state.
        showPaymentResult(success);
        int activeStrength = 0;
        if (success && pulseOnSuccess) {
            activeStrength = successPulseTicks;
        } else if (!success && pulseOnFailure) {
            activeStrength = failurePulseTicks;
        }
        // Keep this active output until the result window expires and idle state takes over.
        ShopTerminalBlock.setPowerLevel(level, worldPosition, activeStrength);
    }

    public void showPaymentResult(boolean success) {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        int result = success ? 1 : 2;
        this.displayResult = result;
        int feedbackTicks = 20 * 2;
        this.displayResultUntilGameTime = level.getGameTime() + feedbackTicks;
        BlockState current = level.getBlockState(worldPosition);
        if (current.is(ModBlocks.PAYMENT_TERMINAL.get())
                && current.hasProperty(ShopTerminalBlock.RESULT)
                && current.getValue(ShopTerminalBlock.RESULT) != result) {
            level.setBlock(worldPosition, current.setValue(ShopTerminalBlock.RESULT, result), Block.UPDATE_ALL);
        }
        markUpdated();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShopTerminalBlockEntity terminal) {
        if (terminal == null || level == null || level.isClientSide()) {
            return;
        }
        if (terminal.displayResult != 0 && level.getGameTime() >= terminal.displayResultUntilGameTime) {
            terminal.displayResult = 0;
            terminal.displayResultUntilGameTime = 0L;
            BlockState current = level.getBlockState(pos);
            if (current.is(ModBlocks.PAYMENT_TERMINAL.get())
                    && current.hasProperty(ShopTerminalBlock.RESULT)
                    && current.getValue(ShopTerminalBlock.RESULT) != 0) {
                level.setBlock(pos, current.setValue(ShopTerminalBlock.RESULT, 0), Block.UPDATE_ALL);
            }
            terminal.markUpdated();
        }

        // Keep idle output continuously active while terminal is idle.
        if (terminal.displayResult == 0) {
            int idlePower = terminal.pulseOnIdle ? terminal.idlePulseStrength : 0;
            ShopTerminalBlock.setPowerLevel(level, pos, idlePower);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.ownerUuid = tag.contains("owner_uuid") ? tag.getUUID("owner_uuid") : null;
        this.ownerName = tag.getString("owner_name");
        this.shopName = sanitizeShopName(tag.getString("shop_name"));
        this.priceDollars = clampLong(tag.getLong("price_dollars"), 1L, MAX_PRICE_DOLLARS);
        this.merchantAccountId = tag.contains("merchant_account_id") ? tag.getUUID("merchant_account_id") : null;
        this.pulseOnSuccess = !tag.contains("pulse_on_success") || tag.getBoolean("pulse_on_success");
        this.pulseOnFailure = !tag.contains("pulse_on_failure") || tag.getBoolean("pulse_on_failure");
        this.pulseOnIdle = tag.getBoolean("pulse_on_idle");
        this.successPulseTicks = Mth.clamp(tag.getInt("success_pulse_ticks"), MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH);
        this.failurePulseTicks = Mth.clamp(tag.getInt("failure_pulse_ticks"), MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH);
        this.idlePulseStrength = Mth.clamp(
                tag.contains("idle_pulse_strength") ? tag.getInt("idle_pulse_strength") : 3,
                MIN_PULSE_STRENGTH,
                MAX_PULSE_STRENGTH
        );
        this.totalSalesDollars = Math.max(0L, tag.getLong("total_sales_dollars"));
        this.displayResult = Mth.clamp(tag.getInt("display_result"), 0, 2);
        this.displayResultUntilGameTime = Math.max(0L, tag.getLong("display_result_until_game_time"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUuid != null) {
            tag.putUUID("owner_uuid", ownerUuid);
        }
        tag.putString("owner_name", getOwnerName());
        tag.putString("shop_name", getShopName());
        tag.putLong("price_dollars", Math.max(1L, priceDollars));
        if (merchantAccountId != null) {
            tag.putUUID("merchant_account_id", merchantAccountId);
        }
        tag.putBoolean("pulse_on_success", pulseOnSuccess);
        tag.putBoolean("pulse_on_failure", pulseOnFailure);
        tag.putBoolean("pulse_on_idle", pulseOnIdle);
        tag.putInt("success_pulse_ticks", Mth.clamp(successPulseTicks, MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH));
        tag.putInt("failure_pulse_ticks", Mth.clamp(failurePulseTicks, MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH));
        tag.putInt("idle_pulse_strength", Mth.clamp(idlePulseStrength, MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH));
        tag.putLong("total_sales_dollars", Math.max(0L, totalSalesDollars));
        tag.putInt("display_result", Mth.clamp(displayResult, 0, 2));
        tag.putLong("display_result_until_game_time", Math.max(0L, displayResultUntilGameTime));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    private static String sanitizeShopName(String input) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isEmpty()) {
            return "Payment Terminal";
        }
        if (normalized.length() > 42) {
            return normalized.substring(0, 42);
        }
        return normalized;
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
