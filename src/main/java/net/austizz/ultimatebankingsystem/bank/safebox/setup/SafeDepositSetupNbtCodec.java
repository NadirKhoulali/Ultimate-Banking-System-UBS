package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SafeDepositSetupNbtCodec {
    private SafeDepositSetupNbtCodec() {
    }

    public static boolean migrateLegacy(CompoundTag metadata, UUID bankId) {
        if (metadata == null || bankId == null) {
            return false;
        }
        Map<String, Object> model = migrationMetadataToMap(metadata);
        boolean changed = SafeDepositSetupMigration.migrateLegacy(model, bankId);
        if (changed) {
            metadata.putInt(SafeDepositSetupMigration.SETUP_VERSION_KEY, SafeDepositSetupMigration.SETUP_VERSION);
            metadata.put(SafeDepositSetupMigration.PREMISES_KEY,
                    writeList(SafeDepositSetupMigration.mapList(model.get(SafeDepositSetupMigration.PREMISES_KEY))));
        }
        return changed;
    }

    public static SafeDepositSetupSnapshot snapshot(CompoundTag metadata) {
        return SafeDepositSetupMigration.snapshot(snapshotMetadataToMap(metadata));
    }

    private static Map<String, Object> migrationMetadataToMap(CompoundTag metadata) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (metadata == null) {
            return map;
        }
        if (metadata.contains(SafeDepositSetupMigration.SETUP_VERSION_KEY)) {
            map.put(SafeDepositSetupMigration.SETUP_VERSION_KEY,
                    metadata.getInt(SafeDepositSetupMigration.SETUP_VERSION_KEY));
        }
        map.put(SafeDepositSetupMigration.LEGACY_AREAS_KEY,
                readList(metadata.getList(SafeDepositSetupMigration.LEGACY_AREAS_KEY, Tag.TAG_COMPOUND)));
        map.put(SafeDepositSetupMigration.PREMISES_KEY,
                readList(metadata.getList(SafeDepositSetupMigration.PREMISES_KEY, Tag.TAG_COMPOUND)));
        return map;
    }

    private static Map<String, Object> snapshotMetadataToMap(CompoundTag metadata) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (metadata == null) {
            return map;
        }
        if (metadata.contains(SafeDepositSetupMigration.SETUP_VERSION_KEY)) {
            map.put(SafeDepositSetupMigration.SETUP_VERSION_KEY,
                    metadata.getInt(SafeDepositSetupMigration.SETUP_VERSION_KEY));
        }
        map.put(SafeDepositSetupMigration.PREMISES_KEY,
                readList(metadata.getList(SafeDepositSetupMigration.PREMISES_KEY, Tag.TAG_COMPOUND)));
        return map;
    }

    private static List<Map<String, Object>> readList(ListTag listTag) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (listTag == null) {
            return list;
        }
        for (int i = 0; i < listTag.size(); i++) {
            list.add(readCompound(listTag.getCompound(i)));
        }
        return list;
    }

    private static Map<String, Object> readCompound(CompoundTag tag) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (tag == null) {
            return map;
        }
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value == null) {
                continue;
            }
            map.put(key, readValue(tag, key, value));
        }
        return map;
    }

    private static Object readValue(CompoundTag tag, String key, Tag value) {
        if (!isMigrationSchemaKey(key)) {
            return value.copy();
        }
        if (tag.hasUUID(key)) {
            return tag.getUUID(key).toString();
        }
        return switch (value.getId()) {
            case Tag.TAG_BYTE -> tag.getBoolean(key);
            case Tag.TAG_SHORT, Tag.TAG_INT -> tag.getInt(key);
            case Tag.TAG_LONG -> tag.getLong(key);
            case Tag.TAG_FLOAT -> tag.getFloat(key);
            case Tag.TAG_DOUBLE -> tag.getDouble(key);
            case Tag.TAG_STRING -> tag.getString(key);
            case Tag.TAG_COMPOUND -> readCompound(tag.getCompound(key));
            case Tag.TAG_LIST -> readList(tag.getList(key, Tag.TAG_COMPOUND));
            default -> "";
        };
    }

    private static boolean isMigrationSchemaKey(String key) {
        return switch (key) {
            case "id", "bankId", "dimension",
                    "minX", "minY", "minZ", "maxX", "maxY", "maxZ",
                    "exitX", "exitY", "exitZ", "exitYaw", "mode",
                    "safeAreas", "premiseId", "vaults", "safeAreaId",
                    "status", "vaultDoorX", "vaultDoorY", "vaultDoorZ", "doorIndex",
                    "routeHooks", "tellerId", "bankBound", "outboundRouteRef", "returnRouteRef" -> true;
            default -> false;
        };
    }

    private static ListTag writeList(List<Map<String, Object>> maps) {
        ListTag list = new ListTag();
        for (Map<String, Object> map : maps) {
            list.add(writeCompound(map));
        }
        return list;
    }

    private static CompoundTag writeCompound(Map<String, Object> map) {
        CompoundTag tag = new CompoundTag();
        if (map == null) {
            return tag;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof Tag rawTag) {
                tag.put(key, rawTag.copy());
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMap = stringKeyMap(nested);
                if (nestedMap != null) {
                    tag.put(key, writeCompound(nestedMap));
                }
            } else if (value instanceof List<?> list) {
                List<Map<String, Object>> nestedMaps = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedMap) {
                        Map<String, Object> typedMap = stringKeyMap(nestedMap);
                        if (typedMap != null) {
                            nestedMaps.add(typedMap);
                        }
                    }
                }
                tag.put(key, writeList(nestedMaps));
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

    private static Map<String, Object> stringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                return null;
            }
            map.put(key, entry.getValue());
        }
        return map;
    }
}
