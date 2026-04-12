package net.austizz.ultimatebankingsystem.payments;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.util.UUID;

public final class ScheduledPayment {
    private final UUID paymentId;
    private final UUID sourceAccountId;
    private final UUID targetAccountId;
    private final BigDecimal amount;
    private final long frequencyTicks;
    private long nextExecutionGameTime;
    private final String createdBy;
    private boolean active;

    public ScheduledPayment(UUID paymentId,
                            UUID sourceAccountId,
                            UUID targetAccountId,
                            BigDecimal amount,
                            long frequencyTicks,
                            long nextExecutionGameTime,
                            String createdBy,
                            boolean active) {
        this.paymentId = paymentId == null ? UUID.randomUUID() : paymentId;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.frequencyTicks = frequencyTicks;
        this.nextExecutionGameTime = nextExecutionGameTime;
        this.createdBy = createdBy == null ? "unknown" : createdBy;
        this.active = active;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getFrequencyTicks() {
        return frequencyTicks;
    }

    public long getNextExecutionGameTime() {
        return nextExecutionGameTime;
    }

    public void setNextExecutionGameTime(long nextExecutionGameTime) {
        this.nextExecutionGameTime = nextExecutionGameTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("paymentId", this.paymentId);
        tag.putUUID("sourceAccountId", this.sourceAccountId);
        tag.putUUID("targetAccountId", this.targetAccountId);
        tag.putString("amount", this.amount.toPlainString());
        tag.putLong("frequencyTicks", this.frequencyTicks);
        tag.putLong("nextExecutionGameTime", this.nextExecutionGameTime);
        tag.putString("createdBy", this.createdBy);
        tag.putBoolean("active", this.active);
        return tag;
    }

    public static ScheduledPayment load(CompoundTag tag, HolderLookup.Provider registries) {
        return new ScheduledPayment(
                tag.getUUID("paymentId"),
                tag.getUUID("sourceAccountId"),
                tag.getUUID("targetAccountId"),
                new BigDecimal(tag.getString("amount")),
                tag.getLong("frequencyTicks"),
                tag.getLong("nextExecutionGameTime"),
                tag.getString("createdBy"),
                tag.getBoolean("active")
        );
    }
}
