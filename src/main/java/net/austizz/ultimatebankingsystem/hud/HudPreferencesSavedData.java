package net.austizz.ultimatebankingsystem.hud;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent per-player preferences that affect only the balance HUD. */
public final class HudPreferencesSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_banking_system_hud_preferences";
    private final Map<UUID, String> positions = new LinkedHashMap<>();

    public static HudPreferencesSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<HudPreferencesSavedData> factory() {
        return new SavedData.Factory<>(HudPreferencesSavedData::new, HudPreferencesSavedData::load, null);
    }

    public String position(UUID playerId) {
        return playerId == null ? "" : positions.getOrDefault(playerId, "");
    }

    public void setPosition(UUID playerId, String value) {
        if (playerId == null) {
            return;
        }
        String normalized = HudPosition.normalize(value);
        if (normalized.isBlank()) {
            return;
        }
        if (!normalized.equals(positions.put(playerId, normalized))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        positions.forEach((playerId, position) -> {
            String normalized = HudPosition.normalize(position);
            if (playerId == null || normalized.isBlank()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", playerId);
            entry.putString("position", normalized);
            entries.add(entry);
        });
        tag.put("positions", entries);
        return tag;
    }

    private static HudPreferencesSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HudPreferencesSavedData data = new HudPreferencesSavedData();
        ListTag entries = tag.getList("positions", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.hasUUID("player")) {
                continue;
            }
            String position = HudPosition.normalize(entry.getString("position"));
            if (!position.isBlank()) {
                data.positions.put(entry.getUUID("player"), position);
            }
        }
        return data;
    }
}
