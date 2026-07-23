package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ViewingRoomNbtStore {
    public static final String ROOMS_KEY = "safeDepositViewingRooms";
    private static final int MAX_ROOMS = 512;

    private ViewingRoomNbtStore() {
    }

    public static List<ViewingRoomSnapshot> read(CompoundTag metadata) {
        if (metadata == null) {
            return List.of();
        }
        ListTag tags = metadata.getList(ROOMS_KEY, Tag.TAG_COMPOUND);
        List<ViewingRoomSnapshot> rooms = new ArrayList<>(Math.min(tags.size(), MAX_ROOMS));
        for (int index = 0; index < tags.size() && rooms.size() < MAX_ROOMS; index++) {
            ViewingRoomSnapshot room = readRoom(tags.getCompound(index));
            if (room != null && rooms.stream().noneMatch(existing -> existing.id().equals(room.id()))) {
                rooms.add(room);
            }
        }
        rooms.sort(Comparator.comparingLong(ViewingRoomSnapshot::createdAtMillis)
                .thenComparing(room -> room.id().toString()));
        return List.copyOf(rooms);
    }

    public static void write(CompoundTag metadata, List<ViewingRoomSnapshot> rooms) {
        if (metadata == null) {
            return;
        }
        ListTag tags = new ListTag();
        if (rooms != null) {
            rooms.stream()
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparingLong(ViewingRoomSnapshot::createdAtMillis)
                            .thenComparing(room -> room.id().toString()))
                    .limit(MAX_ROOMS)
                    .map(ViewingRoomNbtStore::writeRoom)
                    .forEach(tags::add);
        }
        metadata.put(ROOMS_KEY, tags);
    }

    private static ViewingRoomSnapshot readRoom(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("id")) {
            return null;
        }
        SafeBlockBounds bounds = readBounds(tag.getCompound("bounds"));
        String premiseId = tag.getString("premiseId").trim();
        if (bounds == null || premiseId.isEmpty()) {
            return null;
        }
        try {
            return new ViewingRoomSnapshot(
                    tag.getUUID("id"), tag.getString("name"), premiseId, bounds,
                    readAnchor(tag, "customerAnchor"), readAnchor(tag, "tellerAnchor"),
                    readAnchor(tag, "displayAnchor"), tag.getLong("createdAtMillis"),
                    tag.getLong("lastUsedAtMillis"), tag.getBoolean("adminSuspended"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static CompoundTag writeRoom(ViewingRoomSnapshot room) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", room.id());
        tag.putString("name", room.name());
        tag.putString("premiseId", room.premiseId());
        tag.put("bounds", writeBounds(room.bounds()));
        writeAnchor(tag, "customerAnchor", room.customerAnchor());
        writeAnchor(tag, "tellerAnchor", room.tellerAnchor());
        writeAnchor(tag, "displayAnchor", room.displayAnchor());
        tag.putLong("createdAtMillis", room.createdAtMillis());
        tag.putLong("lastUsedAtMillis", room.lastUsedAtMillis());
        tag.putBoolean("adminSuspended", room.adminSuspended());
        return tag;
    }

    private static SafeBlockBounds readBounds(CompoundTag tag) {
        if (tag == null || tag.getString("dimension").isBlank()) {
            return null;
        }
        return new SafeBlockBounds(tag.getString("dimension"),
                tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"),
                tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
    }

    private static CompoundTag writeBounds(SafeBlockBounds bounds) {
        CompoundTag tag = new CompoundTag();
        if (bounds != null) {
            tag.putString("dimension", bounds.dimension());
            tag.putInt("minX", bounds.minX());
            tag.putInt("minY", bounds.minY());
            tag.putInt("minZ", bounds.minZ());
            tag.putInt("maxX", bounds.maxX());
            tag.putInt("maxY", bounds.maxY());
            tag.putInt("maxZ", bounds.maxZ());
        }
        return tag;
    }

    private static ViewingRoomAnchor readAnchor(CompoundTag owner, String key) {
        if (!owner.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = owner.getCompound(key);
        if (tag.getString("dimension").isBlank()) {
            return null;
        }
        try {
            return new ViewingRoomAnchor(tag.getString("dimension"),
                    tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                    tag.getFloat("yaw"), tag.getFloat("pitch"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void writeAnchor(CompoundTag owner, String key, ViewingRoomAnchor anchor) {
        if (anchor == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", anchor.dimension());
        tag.putDouble("x", anchor.x());
        tag.putDouble("y", anchor.y());
        tag.putDouble("z", anchor.z());
        tag.putFloat("yaw", anchor.yaw());
        tag.putFloat("pitch", anchor.pitch());
        owner.put(key, tag);
    }
}
