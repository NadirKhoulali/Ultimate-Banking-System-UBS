package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SafeDepositSetupBinder {
    private SafeDepositSetupBinder() {
    }

    public static ParentResolution resolveContainingPremise(CompoundTag metadata,
                                                             UUID bankId,
                                                             SafeBlockBounds safeAreaBounds) {
        if (metadata == null || bankId == null || safeAreaBounds == null) {
            return new ParentResolution(Containment.NONE, "");
        }
        String containingPremiseId = "";
        int containingCount = 0;
        for (SafePremiseSnapshot premise : SafeDepositSetupNbtCodec.snapshot(metadata).premises()) {
            if (!bankId.toString().equals(premise.bankId()) || !premise.bounds().contains(safeAreaBounds)) {
                continue;
            }
            containingPremiseId = premise.id();
            containingCount++;
        }
        if (containingCount == 0) {
            return new ParentResolution(Containment.NONE, "");
        }
        if (containingCount > 1) {
            return new ParentResolution(Containment.MULTIPLE, "");
        }
        return new ParentResolution(Containment.UNIQUE, containingPremiseId);
    }

    public static boolean attachGeneratedSafeArea(CompoundTag metadata,
                                                  UUID bankId,
                                                  String premiseId,
                                                  SafeBlockBounds safeAreaBounds) {
        if (metadata == null || bankId == null || premiseId == null || premiseId.isBlank()
                || safeAreaBounds == null) {
            return false;
        }
        ListTag premises = metadata.getList(SafeDepositSetupMigration.PREMISES_KEY, Tag.TAG_COMPOUND);
        CompoundTag parent = null;
        int parentIndex = -1;
        int matches = 0;
        for (int i = 0; i < premises.size(); i++) {
            CompoundTag candidate = premises.getCompound(i);
            if (!premiseId.equals(candidate.getString("id"))
                    || !bankId.toString().equals(candidate.getString("bankId"))) {
                continue;
            }
            SafeBlockBounds parentBounds = bounds(candidate);
            if (parentBounds == null || !parentBounds.contains(safeAreaBounds)) {
                continue;
            }
            parent = candidate;
            parentIndex = i;
            matches++;
        }
        if (matches != 1 || parent == null || !parent.contains("safeAreas", Tag.TAG_LIST)) {
            return false;
        }

        ListTag safeAreas = parent.getList("safeAreas", Tag.TAG_COMPOUND);
        String generatedId = SafeDepositSetupIds.safeAreaId(bankId, premiseId, safeAreaBounds);
        for (int i = 0; i < safeAreas.size(); i++) {
            CompoundTag existing = safeAreas.getCompound(i);
            if (generatedId.equals(existing.getString("id")) || safeAreaBounds.equals(bounds(existing))) {
                return false;
            }
        }

        safeAreas.add(writeCompound(SafeDepositSetupFactory.nestedSafeArea(
                premiseId, bankId, safeAreaBounds)));
        parent.put("safeAreas", safeAreas);
        premises.set(parentIndex, parent);
        metadata.put(SafeDepositSetupMigration.PREMISES_KEY, premises);
        return true;
    }

    private static SafeBlockBounds bounds(CompoundTag tag) {
        if (tag == null || tag.getString("dimension").isBlank()
                || !tag.contains("minX") || !tag.contains("minY") || !tag.contains("minZ")
                || !tag.contains("maxX") || !tag.contains("maxY") || !tag.contains("maxZ")) {
            return null;
        }
        return new SafeBlockBounds(
                tag.getString("dimension"),
                tag.getInt("minX"),
                tag.getInt("minY"),
                tag.getInt("minZ"),
                tag.getInt("maxX"),
                tag.getInt("maxY"),
                tag.getInt("maxZ")
        );
    }

    private static CompoundTag writeCompound(Map<String, Object> source) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                tag.put(key, writeCompound(stringKeyMap(nested)));
            } else if (value instanceof List<?> list) {
                ListTag nestedList = new ListTag();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nested) {
                        nestedList.add(writeCompound(stringKeyMap(nested)));
                    }
                }
                tag.put(key, nestedList);
            } else if (value instanceof Integer integer) {
                tag.putInt(key, integer);
            } else if (value instanceof Long longValue) {
                tag.putLong(key, longValue);
            } else if (value instanceof Float floatValue) {
                tag.putFloat(key, floatValue);
            } else if (value instanceof Double doubleValue) {
                tag.putDouble(key, doubleValue);
            } else if (value instanceof Boolean booleanValue) {
                tag.putBoolean(key, booleanValue);
            } else {
                tag.putString(key, value == null ? "" : String.valueOf(value));
            }
        }
        return tag;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public enum Containment {
        NONE,
        UNIQUE,
        MULTIPLE
    }

    public record ParentResolution(Containment containment, String premiseId) {
    }
}
