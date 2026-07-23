package net.austizz.ultimatebankingsystem.bank.owner.premise;

import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomNbtStore;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomState;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomStatus;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupIds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupMigration;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseDeletionPolicy;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMutationService;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultSnapshot;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremisePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class OwnerPcPremisePayloadBuilder {
    private OwnerPcPremisePayloadBuilder() {
    }

    public static List<OwnerPcPremisePayload> build(MinecraftServer server,
                                                    CompoundTag metadata,
                                                    UUID bankId) {
        if (metadata == null || bankId == null) {
            return List.of();
        }
        SafeDepositSetupSnapshot snapshot = SafeDepositSetupNbtCodec.snapshot(metadata);
        List<SafeVaultReadinessResolver.RowReadiness> readiness =
                SafetyDepositBoxService.safeDepositVaultReadiness(server, metadata);
        Set<String> activeVaultIds = SafeBoxViewingCoordinator.activePremiseIds(server);
        return buildProjected(metadataModel(metadata), snapshot, readiness, bankId, activeVaultIds,
                viewingRoomCounts(server, metadata, bankId));
    }

    static List<OwnerPcPremisePayload> build(
            CompoundTag staffingMetadata,
            Map<String, Object> metadata,
            SafeDepositSetupSnapshot snapshot,
            List<SafeVaultReadinessResolver.RowReadiness> readiness,
            UUID bankId,
            Set<String> activeVaultIds) {
        return buildProjected(metadata, snapshot,
                SafetyDepositBoxService.applyStaffingReadiness(staffingMetadata, readiness),
                bankId, activeVaultIds, viewingRoomCounts(null, staffingMetadata, bankId));
    }

    private static List<OwnerPcPremisePayload> buildProjected(
            Map<String, Object> metadata,
            SafeDepositSetupSnapshot snapshot,
            List<SafeVaultReadinessResolver.RowReadiness> readiness,
            UUID bankId,
            Set<String> activeVaultIds,
            Map<String, RoomCounts> viewingRooms) {
        if (metadata == null || snapshot == null || bankId == null) {
            return List.of();
        }
        Set<String> readyVaultIds = readyVaultIds(readiness);
        List<OwnerPcPremisePayload> payloads = new ArrayList<>();
        for (SafePremiseSnapshot premise : snapshot.premises()) {
            if (premise == null || premise.bounds() == null || premise.exit() == null
                    || !bankId.toString().equalsIgnoreCase(premise.bankId())) {
                continue;
            }
            int safeAreaCount = premise.safeAreas().size();
            int vaultCount = 0;
            int readyVaultCount = 0;
            for (SafeAreaSnapshot safeArea : premise.safeAreas()) {
                if (safeArea == null) {
                    continue;
                }
                for (SafeVaultSnapshot vault : safeArea.vaults()) {
                    if (vault == null) {
                        continue;
                    }
                    vaultCount++;
                    if (readyVaultIds.contains(vault.id())) {
                        readyVaultCount++;
                    }
                }
            }
            List<OwnerPcPremisePayload.DeleteBlocker> blockers = blockers(
                    metadata, bankId, premise.id(), activeVaultIds);
            RoomCounts roomCounts = viewingRooms.getOrDefault(premise.id(), RoomCounts.EMPTY);
            payloads.add(new OwnerPcPremisePayload(
                    premise.id(),
                    premise.bounds().dimension(),
                    premise.bounds().minX(), premise.bounds().minY(), premise.bounds().minZ(),
                    premise.bounds().maxX(), premise.bounds().maxY(), premise.bounds().maxZ(),
                    premise.exit().dimension(),
                    premise.exit().x(), premise.exit().y(), premise.exit().z(), premise.exit().yaw(),
                    premise.mode(),
                    readyVaultCount > 0
                            ? OwnerPcPremisePayload.Status.READY
                            : OwnerPcPremisePayload.Status.NOT_READY,
                    safeAreaCount,
                    vaultCount,
                    readyVaultCount,
                    roomCounts.total(),
                    roomCounts.ready(),
                    SafeDepositSetupIds.isMigrationOwnedPremise(
                            premise.id(), bankId, premise.bounds()),
                    blockers));
        }
        return List.copyOf(payloads);
    }

    static Map<String, Object> metadataModel(CompoundTag metadata) {
        return metadata == null ? new LinkedHashMap<>() : readCompound(metadata);
    }

    static CompoundTag applySetupMutation(CompoundTag source,
                                          Map<String, Object> before,
                                          Map<String, Object> after) {
        CompoundTag staged = source == null ? new CompoundTag() : source.copy();
        if (after == null) {
            return staged;
        }

        Map<String, Map<String, Object>> beforeById = premisesById(before);
        Map<String, Map<String, Object>> afterById = premisesById(after);
        Set<String> retained = new LinkedHashSet<>();
        ListTag original = source == null
                ? new ListTag()
                : source.getList(SafeDepositSetupMigration.PREMISES_KEY, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        for (int index = 0; index < original.size(); index++) {
            CompoundTag originalPremise = original.getCompound(index);
            String id = originalPremise.getString("id");
            Map<String, Object> updatedPremise = afterById.get(id);
            if (id.isBlank() || updatedPremise == null) {
                continue;
            }
            CompoundTag premise = originalPremise.copy();
            applyChangedFields(premise, beforeById.get(id), updatedPremise);
            updated.add(premise);
            retained.add(id);
        }
        for (Map.Entry<String, Map<String, Object>> entry : afterById.entrySet()) {
            if (retained.add(entry.getKey())) {
                updated.add(writeCompound(entry.getValue()));
            }
        }
        staged.put(SafeDepositSetupMigration.PREMISES_KEY, updated);
        Object version = after.get(SafeDepositSetupMigration.SETUP_VERSION_KEY);
        if (version instanceof Number number) {
            staged.putInt(SafeDepositSetupMigration.SETUP_VERSION_KEY, number.intValue());
        }
        return staged;
    }

    private static Set<String> readyVaultIds(
            List<SafeVaultReadinessResolver.RowReadiness> readiness) {
        Set<String> ready = new LinkedHashSet<>();
        if (readiness == null) {
            return ready;
        }
        for (SafeVaultReadinessResolver.RowReadiness row : readiness) {
            if (row != null && row.vault() != null && row.summary() != null
                    && row.summary().ready()) {
                ready.add(row.vault().id());
            }
        }
        return ready;
    }

    private static Map<String, RoomCounts> viewingRoomCounts(MinecraftServer server,
                                                             CompoundTag metadata,
                                                             UUID bankId) {
        Map<String, RoomCounts> counts = new LinkedHashMap<>();
        ViewingRoomNbtStore.read(metadata).forEach(room -> counts.merge(
                room.premiseId(), new RoomCounts(1, 0), RoomCounts::add));
        if (server == null) {
            return counts;
        }
        var centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return counts;
        }
        for (ViewingRoomState state : ViewingRoomService.states(
                server, centralBank, bankId, SafeBoxViewingCoordinator.activeRoomIds(server))) {
            if (state.status() == ViewingRoomStatus.READY || state.status() == ViewingRoomStatus.OCCUPIED) {
                counts.computeIfPresent(state.room().premiseId(),
                        (ignored, current) -> new RoomCounts(current.total(), current.ready() + 1));
            }
        }
        return counts;
    }

    private record RoomCounts(int total, int ready) {
        private static final RoomCounts EMPTY = new RoomCounts(0, 0);

        private RoomCounts add(RoomCounts other) {
            return new RoomCounts(total + other.total, ready + other.ready);
        }
    }

    private static List<OwnerPcPremisePayload.DeleteBlocker> blockers(
            Map<String, Object> metadata,
            UUID bankId,
            String premiseId,
            Set<String> activeVaultIds) {
        EnumSet<OwnerPcPremisePayload.DeleteBlocker> blockers =
                EnumSet.noneOf(OwnerPcPremisePayload.DeleteBlocker.class);
        try {
            for (SafePremiseDeletionPolicy blocker : SafePremiseMutationService.deletionBlockers(
                    metadata, bankId, premiseId, activeVaultIds)) {
                blockers.add(OwnerPcPremisePayload.DeleteBlocker.valueOf(blocker.name()));
            }
        } catch (RuntimeException ignored) {
            blockers.addAll(EnumSet.allOf(OwnerPcPremisePayload.DeleteBlocker.class));
        }
        return List.of(OwnerPcPremisePayload.DeleteBlocker.values()).stream()
                .filter(blockers::contains)
                .toList();
    }

    private static void applyChangedFields(CompoundTag target,
                                           Map<String, Object> before,
                                           Map<String, Object> after) {
        if (before == null || after == null) {
            return;
        }
        if (!Objects.equals(before.get("mode"), after.get("mode"))) {
            target.putString("mode", String.valueOf(after.get("mode")));
        }
        applyInt(target, before, after, "exitX");
        applyInt(target, before, after, "exitY");
        applyInt(target, before, after, "exitZ");
        if (!Objects.equals(before.get("exitYaw"), after.get("exitYaw"))
                && after.get("exitYaw") instanceof Number yaw) {
            target.putFloat("exitYaw", yaw.floatValue());
        }
    }

    private static void applyInt(CompoundTag target,
                                 Map<String, Object> before,
                                 Map<String, Object> after,
                                 String key) {
        if (!Objects.equals(before.get(key), after.get(key))
                && after.get(key) instanceof Number value) {
            target.putInt(key, value.intValue());
        }
    }

    private static Map<String, Map<String, Object>> premisesById(Map<String, Object> metadata) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        if (metadata == null
                || !(metadata.get(SafeDepositSetupMigration.PREMISES_KEY) instanceof List<?> premises)) {
            return byId;
        }
        for (Object raw : premises) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> premise = stringKeyMap(map);
            Object rawId = premise == null ? null : premise.get("id");
            String id = rawId instanceof String value ? value : "";
            if (!id.isBlank() && !byId.containsKey(id)) {
                byId.put(id, premise);
            }
        }
        return byId;
    }

    private static Map<String, Object> readCompound(CompoundTag tag) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value != null) {
                map.put(key, readValue(tag, key, value));
            }
        }
        return map;
    }

    private static Object readValue(CompoundTag owner, String key, Tag value) {
        if (owner.hasUUID(key)) {
            return owner.getUUID(key).toString();
        }
        return switch (value.getId()) {
            case Tag.TAG_BYTE -> owner.getBoolean(key);
            case Tag.TAG_SHORT, Tag.TAG_INT -> owner.getInt(key);
            case Tag.TAG_LONG -> owner.getLong(key);
            case Tag.TAG_FLOAT -> owner.getFloat(key);
            case Tag.TAG_DOUBLE -> owner.getDouble(key);
            case Tag.TAG_STRING -> owner.getString(key);
            case Tag.TAG_COMPOUND -> readCompound(owner.getCompound(key));
            case Tag.TAG_LIST -> readList(owner.getList(key, Tag.TAG_COMPOUND));
            default -> "";
        };
    }

    private static List<Map<String, Object>> readList(ListTag list) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            values.add(readCompound(list.getCompound(index)));
        }
        return values;
    }

    private static CompoundTag writeCompound(Map<String, Object> map) {
        CompoundTag tag = new CompoundTag();
        if (map == null) {
            return tag;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMap = stringKeyMap(nested);
                if (nestedMap != null) {
                    tag.put(key, writeCompound(nestedMap));
                }
            } else if (value instanceof List<?> list) {
                ListTag nestedList = new ListTag();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nested) {
                        Map<String, Object> nestedMap = stringKeyMap(nested);
                        if (nestedMap != null) {
                            nestedList.add(writeCompound(nestedMap));
                        }
                    }
                }
                tag.put(key, nestedList);
            } else if (value instanceof Integer number) {
                tag.putInt(key, number);
            } else if (value instanceof Long number) {
                tag.putLong(key, number);
            } else if (value instanceof Float number) {
                tag.putFloat(key, number);
            } else if (value instanceof Double number) {
                tag.putDouble(key, number);
            } else if (value instanceof Boolean bool) {
                tag.putBoolean(key, bool);
            } else {
                tag.putString(key, value == null ? "" : String.valueOf(value));
            }
        }
        return tag;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                return null;
            }
            map.put(key, entry.getValue());
        }
        return map;
    }
}
