package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class LegacySafetyDepositRowData {
    private static final int FIRST_HIDDEN_INDEX = 4;
    private static final int LEGACY_DOOR_COUNT = 7;
    private static final String[] PERSISTED_FIELDS = {
            "module_", "account_", "box_number_", "open_until_", "door_progress_"
    };

    private final CompoundTag values;

    private LegacySafetyDepositRowData(CompoundTag values) {
        this.values = values;
    }

    static LegacySafetyDepositRowData empty() {
        return new LegacySafetyDepositRowData(new CompoundTag());
    }

    static LegacySafetyDepositRowData load(CompoundTag source) {
        CompoundTag values = new CompoundTag();
        for (int index = FIRST_HIDDEN_INDEX; index < LEGACY_DOOR_COUNT; index++) {
            for (String field : PERSISTED_FIELDS) {
                String key = field + index;
                Tag value = source.get(key);
                if (value != null) {
                    values.put(key, value.copy());
                }
            }
        }
        return new LegacySafetyDepositRowData(values);
    }

    void saveTo(CompoundTag target) {
        for (String key : values.getAllKeys()) {
            Tag value = values.get(key);
            if (value != null) {
                target.put(key, value.copy());
            }
        }
    }
}
