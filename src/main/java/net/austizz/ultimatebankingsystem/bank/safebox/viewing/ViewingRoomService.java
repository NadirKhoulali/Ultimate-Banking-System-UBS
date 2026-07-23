package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.BankLevelService;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeAreaSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ViewingRoomService {
    public static final int MIN_WIDTH = 3;
    public static final int MIN_HEIGHT = 3;
    public static final int MIN_DEPTH = 3;
    public static final int MAX_WIDTH = 16;
    public static final int MAX_HEIGHT = 8;
    public static final int MAX_DEPTH = 16;

    private ViewingRoomService() {
    }

    public enum AnchorKind {
        CUSTOMER,
        TELLER,
        DISPLAY;

        public static AnchorKind parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static int capacity(CentralBank centralBank, UUID bankId) {
        Bank bank = resolveBank(centralBank, bankId);
        return bank == null ? 0 : BankLevelService.viewingRoomCapacity(centralBank, bank);
    }

    public static List<ViewingRoomState> states(MinecraftServer server,
                                                CentralBank centralBank,
                                                UUID bankId,
                                                Set<UUID> occupiedRoomIds) {
        if (centralBank == null || bankId == null) {
            return List.of();
        }
        Bank bank = resolveBank(centralBank, bankId);
        if (bank == null) {
            return List.of();
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        List<ViewingRoomSnapshot> rooms = ViewingRoomNbtStore.read(metadata);
        Set<UUID> occupied = occupiedRoomIds == null ? Set.of() : Set.copyOf(occupiedRoomIds);
        int capacity = BankLevelService.viewingRoomCapacity(centralBank, bank);
        Set<UUID> levelAllowed = new HashSet<>();
        rooms.stream()
                .sorted(Comparator.comparingLong(ViewingRoomSnapshot::createdAtMillis)
                        .thenComparing(room -> room.id().toString()))
                .limit(Math.max(0, capacity))
                .forEach(room -> levelAllowed.add(room.id()));

        List<ViewingRoomState> result = new ArrayList<>(rooms.size());
        for (ViewingRoomSnapshot room : rooms) {
            result.add(state(server, metadata, room, levelAllowed.contains(room.id()), occupied.contains(room.id())));
        }
        return List.copyOf(result);
    }

    public static Optional<ViewingRoomState> findState(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       UUID bankId,
                                                       UUID roomId,
                                                       Set<UUID> occupiedRoomIds) {
        if (roomId == null) {
            return Optional.empty();
        }
        return states(server, centralBank, bankId, occupiedRoomIds).stream()
                .filter(state -> roomId.equals(state.room().id()))
                .findFirst();
    }

    public static Optional<ViewingRoomState> selectReadyRoom(MinecraftServer server,
                                                             CentralBank centralBank,
                                                             UUID bankId,
                                                             String premiseId,
                                                             Set<UUID> occupiedRoomIds) {
        String targetPremise = premiseId == null ? "" : premiseId.trim();
        return states(server, centralBank, bankId, occupiedRoomIds).stream()
                .filter(ViewingRoomState::ready)
                .filter(state -> targetPremise.equals(state.room().premiseId()))
                .min(Comparator.comparingLong((ViewingRoomState state) -> state.room().lastUsedAtMillis())
                        .thenComparingLong(state -> state.room().createdAtMillis())
                        .thenComparing(state -> state.room().id().toString()));
    }

    public static boolean premiseHasReadyRoom(MinecraftServer server,
                                              CentralBank centralBank,
                                              UUID bankId,
                                              String premiseId,
                                              Set<UUID> occupiedRoomIds) {
        return selectReadyRoom(server, centralBank, bankId, premiseId, occupiedRoomIds).isPresent();
    }

    public static MutationResult claim(CentralBank centralBank,
                                       UUID bankId,
                                       SafeBlockBounds bounds) {
        return claim(centralBank, bankId, bounds, false);
    }

    public static MutationResult claim(CentralBank centralBank,
                                       UUID bankId,
                                       SafeBlockBounds bounds,
                                       boolean bypassCapacity) {
        if (centralBank == null || bankId == null || bounds == null) {
            return MutationResult.fail("Viewing-room claim failed: invalid bounds.");
        }
        Bank bank = resolveBank(centralBank, bankId);
        if (bank == null) {
            return MutationResult.fail("Viewing-room claim failed: bank no longer exists.");
        }
        String dimensionError = validateDimensions(bounds);
        if (!dimensionError.isEmpty()) {
            return MutationResult.fail(dimensionError);
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId).copy();
        List<SafePremiseSnapshot> containing = SafeDepositSetupNbtCodec.snapshot(metadata).premises().stream()
                .filter(premise -> premise != null && premise.bounds() != null && premise.bounds().contains(bounds))
                .toList();
        if (containing.size() != 1) {
            return MutationResult.fail("Viewing room must be wholly inside exactly one bank premise.");
        }
        SafePremiseSnapshot premise = containing.getFirst();
        for (SafeAreaSnapshot safeArea : premise.safeAreas()) {
            if (safeArea != null && safeArea.bounds() != null && safeArea.bounds().overlaps(bounds)) {
                return MutationResult.fail("Viewing rooms cannot overlap a claimed safe area.");
            }
        }
        List<ViewingRoomSnapshot> rooms = new ArrayList<>(ViewingRoomNbtStore.read(metadata));
        if (!bypassCapacity && rooms.size() >= BankLevelService.viewingRoomCapacity(centralBank, bank)) {
            return MutationResult.fail("Viewing-room capacity is full for bank level "
                    + BankLevelService.effectiveLevel(centralBank, bank) + ".");
        }
        if (rooms.stream().anyMatch(room -> room.bounds() != null && room.bounds().overlaps(bounds))) {
            return MutationResult.fail("Viewing rooms cannot overlap another viewing room.");
        }

        UUID id = UUID.randomUUID();
        int sequence = nextRoomSequence(rooms);
        long now = System.currentTimeMillis();
        ViewingRoomSnapshot room = new ViewingRoomSnapshot(id, "Viewing Room " + sequence,
                premise.id(), bounds, null, null, null, now, 0L, false);
        rooms.add(room);
        ViewingRoomNbtStore.write(metadata, rooms);
        commit(centralBank, bankId, metadata);
        return MutationResult.ok(room, "Viewing Room " + sequence
                + " claimed. Capture the customer, teller, and deposit-box anchors next.");
    }

    public static MutationResult setAnchor(CentralBank centralBank,
                                           UUID bankId,
                                           UUID roomId,
                                           AnchorKind kind,
                                           ViewingRoomAnchor anchor) {
        if (centralBank == null || bankId == null || roomId == null || kind == null || anchor == null) {
            return MutationResult.fail("Viewing-room anchor update failed: invalid request.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId).copy();
        List<ViewingRoomSnapshot> rooms = new ArrayList<>(ViewingRoomNbtStore.read(metadata));
        int index = indexOf(rooms, roomId);
        if (index < 0) {
            return MutationResult.fail("Viewing room no longer exists.");
        }
        ViewingRoomSnapshot current = rooms.get(index);
        if (!anchor.inside(current.bounds())) {
            return MutationResult.fail("The " + kind.name().toLowerCase(java.util.Locale.ROOT)
                    + " anchor must be inside the viewing room.");
        }
        ViewingRoomSnapshot updated = switch (kind) {
            case CUSTOMER -> copy(current, current.name(), anchor, current.tellerAnchor(),
                    current.displayAnchor(), current.lastUsedAtMillis(), current.adminSuspended());
            case TELLER -> copy(current, current.name(), current.customerAnchor(), anchor,
                    current.displayAnchor(), current.lastUsedAtMillis(), current.adminSuspended());
            case DISPLAY -> copy(current, current.name(), current.customerAnchor(), current.tellerAnchor(),
                    anchor, current.lastUsedAtMillis(), current.adminSuspended());
        };
        rooms.set(index, updated);
        ViewingRoomNbtStore.write(metadata, rooms);
        commit(centralBank, bankId, metadata);
        return MutationResult.ok(updated, kindLabel(kind) + " anchor saved for " + updated.name() + ".");
    }

    public static MutationResult rename(CentralBank centralBank, UUID bankId, UUID roomId, String name) {
        String cleanName = ViewingRoomSnapshot.normalizeName(name);
        if (name == null || name.isBlank()) {
            return MutationResult.fail("Viewing-room name cannot be empty.");
        }
        return mutate(centralBank, bankId, roomId, current -> copy(current, cleanName,
                        current.customerAnchor(), current.tellerAnchor(), current.displayAnchor(),
                        current.lastUsedAtMillis(), current.adminSuspended()),
                "Viewing room renamed to " + cleanName + ".");
    }

    public static MutationResult setAdminSuspended(CentralBank centralBank, UUID bankId,
                                                   UUID roomId, boolean suspended) {
        return mutate(centralBank, bankId, roomId, current -> copy(current, current.name(),
                        current.customerAnchor(), current.tellerAnchor(), current.displayAnchor(),
                        current.lastUsedAtMillis(), suspended),
                suspended ? "Viewing room suspended." : "Viewing room reactivated.");
    }

    public static MutationResult touch(CentralBank centralBank, UUID bankId, UUID roomId, long usedAtMillis) {
        return mutate(centralBank, bankId, roomId, current -> copy(current, current.name(),
                        current.customerAnchor(), current.tellerAnchor(), current.displayAnchor(),
                        Math.max(0L, usedAtMillis), current.adminSuspended()), "");
    }

    public static MutationResult delete(CentralBank centralBank, UUID bankId, UUID roomId) {
        if (centralBank == null || bankId == null || roomId == null) {
            return MutationResult.fail("Viewing-room deletion failed: invalid request.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId).copy();
        List<ViewingRoomSnapshot> rooms = new ArrayList<>(ViewingRoomNbtStore.read(metadata));
        int index = indexOf(rooms, roomId);
        if (index < 0) {
            return MutationResult.fail("Viewing room no longer exists.");
        }
        ViewingRoomSnapshot removed = rooms.remove(index);
        ViewingRoomNbtStore.write(metadata, rooms);
        commit(centralBank, bankId, metadata);
        return MutationResult.ok(removed, removed.name() + " deleted.");
    }

    public static int removeRoomsForPremise(CompoundTag metadata, String premiseId) {
        if (metadata == null || premiseId == null || premiseId.isBlank()) {
            return 0;
        }
        List<ViewingRoomSnapshot> before = ViewingRoomNbtStore.read(metadata);
        List<ViewingRoomSnapshot> retained = before.stream()
                .filter(room -> !premiseId.equals(room.premiseId()))
                .toList();
        ViewingRoomNbtStore.write(metadata, retained);
        return before.size() - retained.size();
    }

    private static ViewingRoomState state(MinecraftServer server,
                                          CompoundTag metadata,
                                          ViewingRoomSnapshot room,
                                          boolean levelAllowed,
                                          boolean occupied) {
        List<String> reasons = new ArrayList<>();
        if (!levelAllowed) {
            reasons.add("Suspended because the bank level no longer provides this room slot.");
            return new ViewingRoomState(room, ViewingRoomStatus.SUSPENDED_LEVEL, reasons);
        }
        if (room.adminSuspended()) {
            reasons.add("Suspended by a bank owner or administrator.");
            return new ViewingRoomState(room, ViewingRoomStatus.SUSPENDED_ADMIN, reasons);
        }
        if (!room.anchorsComplete()) {
            if (room.customerAnchor() == null) reasons.add("Customer anchor is missing.");
            if (room.tellerAnchor() == null) reasons.add("Teller anchor is missing.");
            if (room.displayAnchor() == null) reasons.add("Deposit-box display anchor is missing.");
            return new ViewingRoomState(room, ViewingRoomStatus.INCOMPLETE, reasons);
        }
        if (!storedStructureValid(metadata, room)) {
            reasons.add("Room bounds or anchors no longer match the containing premise.");
            return new ViewingRoomState(room, ViewingRoomStatus.OBSTRUCTED, reasons);
        }
        if (!anchorsClear(server, room)) {
            reasons.add("One or more anchors are physically obstructed.");
            return new ViewingRoomState(room, ViewingRoomStatus.OBSTRUCTED, reasons);
        }
        if (occupied) {
            return new ViewingRoomState(room, ViewingRoomStatus.OCCUPIED, List.of("Viewing session in progress."));
        }
        return new ViewingRoomState(room, ViewingRoomStatus.READY, List.of());
    }

    private static boolean storedStructureValid(CompoundTag metadata, ViewingRoomSnapshot room) {
        if (room.bounds() == null || !room.anchorsInsideBounds()) {
            return false;
        }
        List<SafePremiseSnapshot> premises = SafeDepositSetupNbtCodec.snapshot(metadata).premises().stream()
                .filter(premise -> premise.id().equals(room.premiseId()))
                .toList();
        if (premises.size() != 1 || !premises.getFirst().bounds().contains(room.bounds())) {
            return false;
        }
        for (SafeAreaSnapshot safeArea : premises.getFirst().safeAreas()) {
            if (safeArea != null && safeArea.bounds() != null && safeArea.bounds().overlaps(room.bounds())) {
                return false;
            }
        }
        return true;
    }

    private static boolean anchorsClear(MinecraftServer server, ViewingRoomSnapshot room) {
        if (server == null) {
            return true;
        }
        ServerLevel level = level(server, room.bounds().dimension());
        if (level == null) {
            return false;
        }
        if (!loaded(level, room.customerAnchor()) || !loaded(level, room.tellerAnchor())
                || !loaded(level, room.displayAnchor())) {
            return true;
        }
        return level.noCollision(null, actorBox(room.customerAnchor()))
                && level.noCollision(null, actorBox(room.tellerAnchor()))
                && level.noCollision(null, displayBox(room.displayAnchor()));
    }

    private static boolean loaded(ServerLevel level, ViewingRoomAnchor anchor) {
        return anchor != null && level.hasChunkAt(anchor.blockPosition());
    }

    private static AABB actorBox(ViewingRoomAnchor anchor) {
        return new AABB(anchor.x() - 0.31D, anchor.y(), anchor.z() - 0.31D,
                anchor.x() + 0.31D, anchor.y() + 1.86D, anchor.z() + 0.31D);
    }

    private static AABB displayBox(ViewingRoomAnchor anchor) {
        return new AABB(anchor.x() - 0.90D, anchor.y(), anchor.z() - 0.90D,
                anchor.x() + 0.90D, anchor.y() + 1.80D, anchor.z() + 0.90D);
    }

    private static String validateDimensions(SafeBlockBounds bounds) {
        int width = bounds.maxX() - bounds.minX() + 1;
        int height = bounds.maxY() - bounds.minY() + 1;
        int depth = bounds.maxZ() - bounds.minZ() + 1;
        if (width < MIN_WIDTH || height < MIN_HEIGHT || depth < MIN_DEPTH) {
            return "Viewing room must be at least 3x3x3 blocks.";
        }
        if (width > MAX_WIDTH || height > MAX_HEIGHT || depth > MAX_DEPTH) {
            return "Viewing room cannot exceed 16x16 blocks or 8 blocks in height.";
        }
        return "";
    }

    private static MutationResult mutate(CentralBank centralBank,
                                         UUID bankId,
                                         UUID roomId,
                                         java.util.function.UnaryOperator<ViewingRoomSnapshot> mutation,
                                         String message) {
        if (centralBank == null || bankId == null || roomId == null || mutation == null) {
            return MutationResult.fail("Viewing-room update failed: invalid request.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId).copy();
        List<ViewingRoomSnapshot> rooms = new ArrayList<>(ViewingRoomNbtStore.read(metadata));
        int index = indexOf(rooms, roomId);
        if (index < 0) {
            return MutationResult.fail("Viewing room no longer exists.");
        }
        ViewingRoomSnapshot updated = mutation.apply(rooms.get(index));
        rooms.set(index, updated);
        ViewingRoomNbtStore.write(metadata, rooms);
        commit(centralBank, bankId, metadata);
        return MutationResult.ok(updated, message);
    }

    private static ViewingRoomSnapshot copy(ViewingRoomSnapshot source,
                                            String name,
                                            ViewingRoomAnchor customer,
                                            ViewingRoomAnchor teller,
                                            ViewingRoomAnchor display,
                                            long lastUsed,
                                            boolean suspended) {
        return new ViewingRoomSnapshot(source.id(), name, source.premiseId(), source.bounds(),
                customer, teller, display, source.createdAtMillis(), lastUsed, suspended);
    }

    private static int nextRoomSequence(List<ViewingRoomSnapshot> rooms) {
        Set<String> names = new HashSet<>();
        rooms.forEach(room -> names.add(room.name().toLowerCase(java.util.Locale.ROOT)));
        int sequence = 1;
        while (names.contains(("Viewing Room " + sequence).toLowerCase(java.util.Locale.ROOT))) {
            sequence++;
        }
        return sequence;
    }

    private static int indexOf(List<ViewingRoomSnapshot> rooms, UUID roomId) {
        for (int index = 0; index < rooms.size(); index++) {
            if (roomId.equals(rooms.get(index).id())) {
                return index;
            }
        }
        return -1;
    }

    private static String kindLabel(AnchorKind kind) {
        return switch (kind) {
            case CUSTOMER -> "Customer";
            case TELLER -> "Teller";
            case DISPLAY -> "Deposit-box display";
        };
    }

    private static Bank resolveBank(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return null;
        }
        return bankId.equals(centralBank.getBankId()) ? centralBank : centralBank.getBank(bankId);
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        if (server == null || dimension == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static void commit(CentralBank centralBank, UUID bankId, CompoundTag metadata) {
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
    }

    public record MutationResult(boolean success, String message, ViewingRoomSnapshot room) {
        public static MutationResult ok(ViewingRoomSnapshot room, String message) {
            return new MutationResult(true, message == null ? "" : message, room);
        }

        public static MutationResult fail(String message) {
            return new MutationResult(false, message == null ? "" : message, null);
        }
    }
}
