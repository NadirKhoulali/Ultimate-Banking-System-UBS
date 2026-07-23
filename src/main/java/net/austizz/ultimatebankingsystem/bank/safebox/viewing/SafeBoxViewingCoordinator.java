package net.austizz.ultimatebankingsystem.bank.safebox.viewing;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupNbtCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseSnapshot;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.entity.custom.SafetyDepositBoxDisplayProxyEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.network.BankTellerSafeBoxState;
import net.austizz.ultimatebankingsystem.network.SafeBoxDisplayContentsPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SafeBoxViewingCoordinator {
    private static final int SESSION_TICKS = 20 * 60 * 5;
    private static final int WARNING_TICKS = SESSION_TICKS - 20 * 30;
    private static final int CONFIRM_TICKS = 20 * 10;
    private static final int CHUNK_TICKET_LEVEL = 2;
    private static final TicketType<UUID> CHUNK_TICKET = TicketType.create(
            "ubs_safe_box_viewing", Comparator.comparing(UUID::toString));
    private static final Map<MinecraftServer, RuntimeState> RUNTIMES = new IdentityHashMap<>();

    private SafeBoxViewingCoordinator() {
    }

    public static synchronized StartResult start(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 ServerPlayer player,
                                                 BankTellerEntity teller,
                                                 BankTellerSafeBoxState.AccountAssignment assignment) {
        if (server == null || centralBank == null || player == null || teller == null || assignment == null) {
            return StartResult.fail("Private box viewing is unavailable.");
        }
        RuntimeState runtime = runtime(server);
        runtime.reconcileStaleJournals();
        if (runtime.byPlayer.containsKey(player.getUUID())) {
            return StartResult.fail("You already have an active box-viewing session.");
        }
        if (runtime.byTeller.containsKey(teller.getUUID())) {
            return StartResult.fail("This teller is currently assisting another customer.");
        }
        UUID bankId = teller.getBoundBankId();
        if (bankId == null || !bankId.equals(assignmentBankId(centralBank, assignment.accountId()))) {
            return StartResult.fail("The teller and safety deposit box are not linked to the same bank.");
        }

        ServerLevel sourceLevel = level(server, assignment.dimension());
        if (sourceLevel == null) {
            return StartResult.fail("The safety deposit box dimension is unavailable.");
        }
        BlockPos sourcePos = new BlockPos(assignment.x(), assignment.y(), assignment.z());
        String premiseId = commonPremiseId(centralBank, bankId, sourceLevel, sourcePos, teller);
        if (premiseId.isBlank()) {
            return StartResult.fail("The teller and safety deposit box must be inside the same bank premise.");
        }
        Optional<ViewingRoomState> selected = ViewingRoomService.selectReadyRoom(
                server, centralBank, bankId, premiseId, runtime.activeRoomIds());
        if (selected.isEmpty()) {
            return StartResult.fail("No ready private viewing room is available in this bank premise.");
        }
        ViewingRoomSnapshot room = selected.get().room();
        ServerLevel roomLevel = level(server, room.bounds().dimension());
        if (roomLevel == null || room.customerAnchor() == null || room.tellerAnchor() == null
                || room.displayAnchor() == null) {
            return StartResult.fail("The selected viewing room is incomplete.");
        }

        UUID sessionId = UUID.randomUUID();
        ChunkLease chunks = new ChunkLease(sourceLevel, sourcePos, roomLevel,
                room.displayAnchor().blockPosition(), sessionId);
        chunks.acquire();
        sourceLevel.getChunkAt(sourcePos);
        roomLevel.getChunkAt(room.displayAnchor().blockPosition());
        if (!(sourceLevel.getBlockEntity(sourcePos) instanceof SafetyDepositBoxRowBlockEntity row)) {
            chunks.release();
            return StartResult.fail("The assigned deposit row is not currently available.");
        }
        int doorIndex = row.getModuleStartForRow(assignment.doorIndex());
        if (doorIndex < 0 || !row.isAssignableBoxStart(doorIndex)
                || !assignment.accountId().equals(row.getAssignedAccountId(doorIndex))) {
            chunks.release();
            return StartResult.fail("The safety deposit box assignment changed. Refresh the teller.");
        }

        ViewingRoomAnchor playerReturn = anchor(player);
        ViewingRoomAnchor tellerReturn = anchor(teller);
        boolean playerWasInvulnerable = player.isInvulnerable();
        ViewingSessionSavedData.Journal journal = new ViewingSessionSavedData.Journal(
                sessionId, bankId, room.id(), player.getUUID(), teller.getUUID(), assignment.accountId(),
                premiseId, assignment.dimension(), sourcePos, doorIndex, playerReturn, tellerReturn,
                playerWasInvulnerable);
        ViewingSessionSavedData.get(server).put(journal);
        if (!row.beginViewingTransfer(doorIndex, sessionId)) {
            ViewingSessionSavedData.get(server).remove(sessionId);
            chunks.release();
            return StartResult.fail("This safety deposit box is already in another viewing session.");
        }

        try {
            player.closeContainer();
            teleport(player, roomLevel, room.customerAnchor());
            teleportTeller(teller, roomLevel, room.tellerAnchor(), room.customerAnchor());
            SafetyDepositBoxDisplayProxyEntity proxy = spawnProxy(
                    roomLevel, room, sessionId, row.getModuleType(doorIndex));
            if (proxy == null) {
                throw new IllegalStateException("Could not create the viewing-room deposit box display.");
            }
            List<ItemStack> displayContents = snapshotDisplayContents(
                    centralBank, assignment.accountId(), row.getModuleType(doorIndex), roomLevel);
            Session session = new Session(sessionId, bankId, room, player.getUUID(), teller.getUUID(),
                    assignment.accountId(), assignment.assignmentLabel(), assignment.dimension(), sourcePos,
                    doorIndex, row.getModuleType(doorIndex), playerReturn, tellerReturn,
                    playerWasInvulnerable, server.getTickCount(), proxy.getUUID(), displayContents, chunks);
            runtime.add(session);
            sendDisplayContents(player, proxy.getUUID(), displayContents);
            ViewingRoomService.touch(centralBank, bankId, room.id(), System.currentTimeMillis());
            player.sendSystemMessage(UbsTranslations.literal(
                    "§aYour private box-viewing session is ready. Interact with the displayed box to inspect it."));
            return StartResult.ok(sessionId,
                    "Private viewing started. The requesting teller and your deposit box are now in the viewing room.");
        } catch (RuntimeException exception) {
            row.endViewingTransfer(doorIndex, sessionId);
            restoreTeller(server, teller.getUUID(), tellerReturn);
            restoreOrDeferPlayer(server, player.getUUID(), playerReturn, bankId, premiseId,
                    playerWasInvulnerable);
            chunks.release();
            ViewingSessionSavedData.get(server).remove(sessionId);
            return StartResult.fail(exception.getMessage() == null
                    ? "Private viewing could not be started." : exception.getMessage());
        }
    }

    public static synchronized boolean handleProxyInteraction(MinecraftServer server,
                                                               UUID sessionId,
                                                               ServerPlayer player) {
        RuntimeState runtime = RUNTIMES.get(server);
        Session session = runtime == null || sessionId == null ? null : runtime.byId.get(sessionId);
        if (session == null || player == null || !session.playerId.equals(player.getUUID())) {
            return false;
        }
        return SafetyDepositBoxService.openViewingDepositBox(
                player, session.accountId, session.sourceDimension, session.sourcePos, session.doorIndex);
    }

    public static synchronized TellerInteraction handleTellerInteraction(MinecraftServer server,
                                                                         UUID playerId,
                                                                         UUID tellerId) {
        RuntimeState runtime = RUNTIMES.get(server);
        Session session = runtime == null || playerId == null ? null : runtime.byPlayer.get(playerId);
        if (session == null) {
            return TellerInteraction.NOT_ACTIVE;
        }
        if (!session.tellerId.equals(tellerId)) {
            return TellerInteraction.WRONG_TELLER;
        }
        long now = server.getTickCount();
        if (session.confirmUntilTick >= now) {
            runtime.finish(session, FinishReason.CUSTOMER_CONFIRMED);
            return TellerInteraction.FINISHED;
        }
        session.confirmUntilTick = now + CONFIRM_TICKS;
        return TellerInteraction.CONFIRM_REQUIRED;
    }

    public static synchronized AccessDecision inspectAccess(MinecraftServer server,
                                                            UUID playerId,
                                                            UUID bankId,
                                                            UUID accountId,
                                                            String dimension,
                                                            BlockPos rowPos,
                                                            int doorIndex) {
        RuntimeState runtime = RUNTIMES.get(server);
        Session session = runtime == null || playerId == null ? null : runtime.byPlayer.get(playerId);
        if (session == null) {
            return AccessDecision.NO_ACTIVE_VIEWING;
        }
        return session.bankId.equals(bankId)
                && session.accountId.equals(accountId)
                && session.sourceDimension.equals(dimension)
                && session.sourcePos.equals(rowPos)
                && session.doorIndex == doorIndex
                ? AccessDecision.ALLOWED : AccessDecision.DENIED_ACTIVE_VIEWING;
    }

    public static synchronized boolean hasMenuAuthority(MinecraftServer server,
                                                        UUID playerId,
                                                        UUID accountId,
                                                        BlockPos rowPos,
                                                        int doorIndex) {
        RuntimeState runtime = RUNTIMES.get(server);
        Session session = runtime == null || playerId == null ? null : runtime.byPlayer.get(playerId);
        return session != null && session.accountId.equals(accountId)
                && session.sourcePos.equals(rowPos) && session.doorIndex == doorIndex;
    }

    public static synchronized void updateDisplayContents(MinecraftServer server,
                                                          UUID playerId,
                                                          UUID accountId,
                                                          BlockPos rowPos,
                                                          int doorIndex,
                                                          List<ItemStack> slots) {
        RuntimeState runtime = RUNTIMES.get(server);
        Session session = runtime == null || playerId == null ? null : runtime.byPlayer.get(playerId);
        if (session == null || !session.accountId.equals(accountId)
                || !session.sourcePos.equals(rowPos) || session.doorIndex != doorIndex) {
            return;
        }
        session.displayContents = normalizeDisplayContents(slots, session.moduleType.inventorySlots());
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            sendDisplayContents(player, session.proxyId, session.displayContents);
        }
    }

    public static synchronized boolean isPlayerActive(MinecraftServer server, UUID playerId) {
        RuntimeState runtime = RUNTIMES.get(server);
        return runtime != null && runtime.byPlayer.containsKey(playerId);
    }

    public static synchronized boolean isTellerActive(MinecraftServer server, UUID tellerId) {
        RuntimeState runtime = RUNTIMES.get(server);
        return runtime != null && runtime.byTeller.containsKey(tellerId);
    }

    public static synchronized Set<UUID> activeRoomIds(MinecraftServer server) {
        RuntimeState runtime = RUNTIMES.get(server);
        return runtime == null ? Set.of() : runtime.activeRoomIds();
    }

    public static synchronized Set<String> activePremiseIds(MinecraftServer server) {
        RuntimeState runtime = RUNTIMES.get(server);
        return runtime == null ? Set.of() : Set.copyOf(runtime.byId.values().stream()
                .map(session -> session.room.premiseId()).toList());
    }

    public static synchronized boolean isRoomActive(MinecraftServer server, UUID roomId) {
        return activeRoomIds(server).contains(roomId);
    }

    public static synchronized void tick(MinecraftServer server) {
        RuntimeState runtime = runtime(server);
        runtime.reconcileStaleJournals();
        runtime.tick();
    }

    public static synchronized void handlePlayerTick(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        RuntimeState runtime = RUNTIMES.get(player.getServer());
        if (runtime != null) {
            runtime.enforcePlayer(player);
        }
    }

    public static synchronized boolean blocksCustomerAction(ServerPlayer player) {
        return player != null && player.getServer() != null
                && isPlayerActive(player.getServer(), player.getUUID());
    }

    public static synchronized boolean blocksWorldAction(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        RuntimeState runtime = RUNTIMES.get(player.getServer());
        if (runtime == null) {
            return false;
        }
        if (runtime.byPlayer.containsKey(player.getUUID())) {
            return true;
        }
        String dimension = player.level().dimension().location().toString();
        BlockPos pos = player.blockPosition();
        return runtime.byId.values().stream().anyMatch(session ->
                session.room.bounds().dimension().equals(dimension)
                        && session.room.bounds().contains(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static synchronized boolean canInteractWithViewingEntity(ServerPlayer player, Entity target) {
        if (player == null || player.getServer() == null || target == null
                || !isPlayerActive(player.getServer(), player.getUUID())) {
            return false;
        }
        if (target instanceof SafetyDepositBoxDisplayProxyEntity proxy) {
            RuntimeState runtime = RUNTIMES.get(player.getServer());
            Session session = runtime == null ? null : runtime.byPlayer.get(player.getUUID());
            return session != null && session.sessionId.equals(proxy.getSessionId());
        }
        if (target instanceof BankTellerEntity teller) {
            RuntimeState runtime = RUNTIMES.get(player.getServer());
            Session session = runtime == null ? null : runtime.byPlayer.get(player.getUUID());
            return session != null && session.tellerId.equals(teller.getUUID());
        }
        return false;
    }

    public static synchronized boolean isProtectedActor(MinecraftServer server, UUID entityId) {
        RuntimeState runtime = RUNTIMES.get(server);
        return runtime != null && (runtime.byPlayer.containsKey(entityId) || runtime.byTeller.containsKey(entityId));
    }

    public static synchronized boolean rowHasActiveTransfer(MinecraftServer server,
                                                             String dimension,
                                                             BlockPos pos) {
        RuntimeState runtime = RUNTIMES.get(server);
        if (runtime == null || dimension == null || pos == null) {
            return false;
        }
        return runtime.byId.values().stream()
                .anyMatch(session -> dimension.equals(session.sourceDimension) && pos.equals(session.sourcePos));
    }

    public static synchronized int cancelRoom(MinecraftServer server, UUID roomId) {
        RuntimeState runtime = RUNTIMES.get(server);
        if (runtime == null || roomId == null) {
            return 0;
        }
        List<Session> sessions = runtime.byId.values().stream()
                .filter(session -> roomId.equals(session.room.id())).toList();
        sessions.forEach(session -> runtime.finish(session, FinishReason.ADMIN_CANCELLED));
        return sessions.size();
    }

    public static synchronized void onLogout(MinecraftServer server, UUID playerId) {
        RuntimeState runtime = RUNTIMES.get(server);
        Session session = runtime == null ? null : runtime.byPlayer.get(playerId);
        if (session != null) {
            runtime.finish(session, FinishReason.PLAYER_UNAVAILABLE);
        }
    }

    public static synchronized void onDeath(MinecraftServer server, UUID playerId) {
        onLogout(server, playerId);
    }

    public static synchronized void onDimensionChange(MinecraftServer server, UUID playerId) {
        RuntimeState runtime = RUNTIMES.get(server);
        Session session = runtime == null ? null : runtime.byPlayer.get(playerId);
        if (session != null) {
            runtime.finish(session, FinishReason.PLAYER_UNAVAILABLE);
        }
    }

    public static synchronized void applyDeferredReturn(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        ViewingSessionSavedData.DeferredReturn deferred = ViewingSessionSavedData.get(player.getServer())
                .takeDeferredReturn(player.getUUID());
        if (deferred != null) {
            ViewingRoomAnchor target = deferred.anchor();
            ServerLevel level = level(player.getServer(), target.dimension());
            if (level != null) {
                player.setInvulnerable(deferred.wasInvulnerable());
                teleport(player, level, target);
            } else {
                ViewingSessionSavedData.get(player.getServer()).deferReturn(
                        player.getUUID(), target, deferred.wasInvulnerable());
            }
        }
    }

    public static synchronized void stop(MinecraftServer server) {
        RuntimeState runtime = RUNTIMES.remove(server);
        if (runtime != null) {
            new ArrayList<>(runtime.byId.values()).forEach(
                    session -> runtime.finish(session, FinishReason.SERVER_STOP));
        }
    }

    private static RuntimeState runtime(MinecraftServer server) {
        return RUNTIMES.computeIfAbsent(server, RuntimeState::new);
    }

    private static UUID assignmentBankId(CentralBank centralBank, UUID accountId) {
        var account = centralBank.SearchForAccountByAccountId(accountId);
        return account == null ? null : account.getBankId();
    }

    private static String commonPremiseId(CentralBank centralBank,
                                          UUID bankId,
                                          ServerLevel sourceLevel,
                                          BlockPos sourcePos,
                                          BankTellerEntity teller) {
        if (teller.level() != sourceLevel) {
            return "";
        }
        String dimension = sourceLevel.dimension().location().toString();
        return SafeDepositSetupNbtCodec.snapshot(centralBank.getOrCreateBankMetadata(bankId)).premises().stream()
                .filter(premise -> premise.bounds() != null
                        && dimension.equals(premise.bounds().dimension())
                        && premise.bounds().contains(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ())
                        && premise.bounds().contains(teller.blockPosition().getX(), teller.blockPosition().getY(),
                        teller.blockPosition().getZ()))
                .map(SafePremiseSnapshot::id)
                .findFirst().orElse("");
    }

    private static ViewingRoomAnchor anchor(Entity entity) {
        return new ViewingRoomAnchor(entity.level().dimension().location().toString(),
                entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
    }

    private static void teleport(ServerPlayer player, ServerLevel level, ViewingRoomAnchor anchor) {
        player.teleportTo(level, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private static void teleportTeller(BankTellerEntity teller,
                                       ServerLevel level,
                                       ViewingRoomAnchor tellerAnchor,
                                       ViewingRoomAnchor customerAnchor) {
        if (teller.level() != level) {
            teller.teleportTo(level, tellerAnchor.x(), tellerAnchor.y(), tellerAnchor.z(),
                    Set.of(), tellerAnchor.yaw(), 0.0F);
        } else {
            teller.teleportTo(tellerAnchor.x(), tellerAnchor.y(), tellerAnchor.z());
        }
        double dx = customerAnchor.x() - tellerAnchor.x();
        double dz = customerAnchor.z() - tellerAnchor.z();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        teller.alignBodyTo(yaw);
        teller.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (server == null || id == null) {
            return null;
        }
        ResourceKey<Level> key = RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
        return server.getLevel(key);
    }

    private static void restoreTeller(MinecraftServer server, UUID tellerId, ViewingRoomAnchor target) {
        Entity entity = findEntity(server, tellerId);
        ServerLevel level = level(server, target.dimension());
        if (entity instanceof BankTellerEntity teller && level != null) {
            if (teller.level() != level) {
                teller.teleportTo(level, target.x(), target.y(), target.z(), Set.of(), target.yaw(), target.pitch());
            } else {
                teller.teleportTo(target.x(), target.y(), target.z());
            }
            teller.alignBodyTo(target.yaw());
        }
    }

    private static void restoreOrDeferPlayer(MinecraftServer server,
                                             UUID playerId,
                                             ViewingRoomAnchor preferred,
                                             UUID bankId,
                                             String premiseId,
                                             boolean wasInvulnerable) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        ViewingRoomAnchor target = safeReturn(server, player, preferred)
                ? preferred : premiseExit(server, bankId, premiseId, preferred);
        if (player == null) {
            ViewingSessionSavedData.get(server).deferReturn(playerId, target, wasInvulnerable);
            return;
        }
        ServerLevel level = level(server, target.dimension());
        if (level != null) {
            player.setInvulnerable(wasInvulnerable);
            teleport(player, level, target);
        } else {
            ViewingSessionSavedData.get(server).deferReturn(playerId, target, wasInvulnerable);
        }
    }

    private static SafetyDepositBoxDisplayProxyEntity spawnProxy(
            ServerLevel level,
            ViewingRoomSnapshot room,
            UUID sessionId,
            SafetyDepositBoxRowBlockEntity.ModuleType moduleType) {
        SafetyDepositBoxDisplayProxyEntity proxy = new SafetyDepositBoxDisplayProxyEntity(
                ModEntities.SAFETY_DEPOSIT_BOX_DISPLAY_PROXY.get(), level);
        proxy.setSessionId(sessionId);
        proxy.setModuleType(moduleType);
        proxy.moveTo(room.displayAnchor().x(), room.displayAnchor().y(), room.displayAnchor().z(),
                room.displayAnchor().yaw(), room.displayAnchor().pitch());
        return level.addFreshEntity(proxy) ? proxy : null;
    }

    private static List<ItemStack> snapshotDisplayContents(CentralBank centralBank,
                                                           UUID accountId,
                                                           SafetyDepositBoxRowBlockEntity.ModuleType moduleType,
                                                           ServerLevel level) {
        int slotCount = moduleType == null ? 0 : moduleType.inventorySlots();
        List<ItemStack> contents = new ArrayList<>(slotCount);
        var account = centralBank == null || accountId == null
                ? null : centralBank.SearchForAccountByAccountId(accountId);
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = ItemStack.EMPTY;
            if (account != null) {
                var stackTag = account.getSafeBoxSlots().get(slot);
                if (stackTag != null) {
                    stack = ItemStackDataCompat.parseStack(stackTag, level.registryAccess());
                }
            }
            contents.add(stack == null || stack.isEmpty()
                    ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return List.copyOf(contents);
    }

    private static List<ItemStack> normalizeDisplayContents(List<ItemStack> slots, int slotCount) {
        int safeCount = Math.max(0, Math.min(54, slotCount));
        List<ItemStack> normalized = new ArrayList<>(safeCount);
        for (int slot = 0; slot < safeCount; slot++) {
            ItemStack stack = slots != null && slot < slots.size() ? slots.get(slot) : ItemStack.EMPTY;
            normalized.add(stack == null || stack.isEmpty()
                    ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return List.copyOf(normalized);
    }

    private static void sendDisplayContents(ServerPlayer player, UUID proxyId, List<ItemStack> contents) {
        if (player != null && proxyId != null) {
            PacketDistributor.sendToPlayer(player,
                    new SafeBoxDisplayContentsPayload(proxyId, true, contents));
        }
    }

    private static void clearDisplayContents(ServerPlayer player, UUID proxyId) {
        if (player != null && proxyId != null) {
            PacketDistributor.sendToPlayer(player, SafeBoxDisplayContentsPayload.clear(proxyId));
        }
    }

    private static boolean safeReturn(MinecraftServer server, ServerPlayer player, ViewingRoomAnchor target) {
        ServerLevel level = level(server, target.dimension());
        if (level == null) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(target.blockPosition())) {
            return false;
        }
        AABB box = player == null
                ? new AABB(target.x() - 0.3D, target.y(), target.z() - 0.3D,
                target.x() + 0.3D, target.y() + 1.8D, target.z() + 0.3D)
                : player.getBoundingBox().move(target.x() - player.getX(), target.y() - player.getY(),
                target.z() - player.getZ());
        return level.noCollision(player, box);
    }

    private static ViewingRoomAnchor premiseExit(MinecraftServer server,
                                                   UUID bankId,
                                                   String premiseId,
                                                   ViewingRoomAnchor fallback) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return fallback;
        }
        return SafeDepositSetupNbtCodec.snapshot(centralBank.getOrCreateBankMetadata(bankId)).premises().stream()
                .filter(premise -> premise.id().equals(premiseId) && premise.exit() != null)
                .map(premise -> {
                    SafeExitSnapshot exit = premise.exit();
                    return new ViewingRoomAnchor(exit.dimension(), exit.x() + 0.5D, exit.y(), exit.z() + 0.5D,
                            exit.yaw(), 0.0F);
                }).findFirst().orElse(fallback);
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    public enum AccessDecision { ALLOWED, DENIED_ACTIVE_VIEWING, NO_ACTIVE_VIEWING }
    public enum TellerInteraction { NOT_ACTIVE, WRONG_TELLER, CONFIRM_REQUIRED, FINISHED }
    private enum FinishReason { CUSTOMER_CONFIRMED, TIMEOUT, PLAYER_UNAVAILABLE, SERVER_STOP, ADMIN_CANCELLED }

    public record StartResult(boolean success, UUID sessionId, String message) {
        static StartResult ok(UUID sessionId, String message) {
            return new StartResult(true, sessionId, message);
        }

        static StartResult fail(String message) {
            return new StartResult(false, null, message == null ? "" : message);
        }
    }

    private static final class RuntimeState {
        private final MinecraftServer server;
        private final Map<UUID, Session> byId = new LinkedHashMap<>();
        private final Map<UUID, Session> byPlayer = new LinkedHashMap<>();
        private final Map<UUID, Session> byTeller = new LinkedHashMap<>();
        private boolean reconciled;

        private RuntimeState(MinecraftServer server) {
            this.server = server;
        }

        private void add(Session session) {
            byId.put(session.sessionId, session);
            byPlayer.put(session.playerId, session);
            byTeller.put(session.tellerId, session);
        }

        private Set<UUID> activeRoomIds() {
            return Set.copyOf(byId.values().stream().map(session -> session.room.id()).toList());
        }

        private void reconcileStaleJournals() {
            if (reconciled) {
                return;
            }
            reconciled = true;
            ViewingSessionSavedData data = ViewingSessionSavedData.get(server);
            for (ViewingSessionSavedData.Journal journal : data.journals()) {
                ServerLevel sourceLevel = level(server, journal.sourceDimension());
                if (sourceLevel != null) {
                    sourceLevel.getChunkAt(journal.sourcePos());
                    if (sourceLevel.getBlockEntity(journal.sourcePos()) instanceof SafetyDepositBoxRowBlockEntity row) {
                        row.endViewingTransfer(journal.doorIndex(), journal.sessionId());
                    }
                }
                restoreTeller(server, journal.tellerId(), journal.tellerReturn());
                restoreOrDeferPlayer(server, journal.playerId(), journal.playerReturn(),
                        journal.bankId(), journal.premiseId(), journal.playerWasInvulnerable());
                data.remove(journal.sessionId());
            }
        }

        private void tick() {
            long now = server.getTickCount();
            for (Session session : new ArrayList<>(byId.values())) {
                ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
                Entity teller = findEntity(server, session.tellerId);
                if (player == null || !(teller instanceof BankTellerEntity)) {
                    finish(session, FinishReason.PLAYER_UNAVAILABLE);
                    continue;
                }
                Entity proxy = findEntity(server, session.proxyId);
                if (!(proxy instanceof SafetyDepositBoxDisplayProxyEntity)) {
                    UUID missingProxyId = session.proxyId;
                    ServerLevel roomLevel = level(server, session.room.bounds().dimension());
                    SafetyDepositBoxDisplayProxyEntity replacement = roomLevel == null ? null : spawnProxy(
                            roomLevel, session.room, session.sessionId, session.moduleType);
                    if (replacement == null) {
                        finish(session, FinishReason.PLAYER_UNAVAILABLE);
                        continue;
                    }
                    clearDisplayContents(player, missingProxyId);
                    session.proxyId = replacement.getUUID();
                    sendDisplayContents(player, session.proxyId, session.displayContents);
                }
                long elapsed = now - session.startedTick;
                if (!session.warned && elapsed >= WARNING_TICKS) {
                    session.warned = true;
                    player.sendSystemMessage(Component.literal(
                            "Your private box-viewing session closes in 30 seconds."));
                }
                if (elapsed >= SESSION_TICKS) {
                    finish(session, FinishReason.TIMEOUT);
                }
            }
        }

        private void enforcePlayer(ServerPlayer player) {
            Session own = byPlayer.get(player.getUUID());
            if (own != null) {
                if (!own.room.bounds().dimension().equals(player.level().dimension().location().toString())
                        || !own.room.bounds().contains(player.blockPosition().getX(), player.blockPosition().getY(),
                        player.blockPosition().getZ())) {
                    ServerLevel target = level(server, own.room.customerAnchor().dimension());
                    if (target != null) {
                        teleport(player, target, own.room.customerAnchor());
                    }
                }
                player.setInvulnerable(true);
                return;
            }
            String dimension = player.level().dimension().location().toString();
            for (Session session : byId.values()) {
                if (session.room.bounds().dimension().equals(dimension)
                        && session.room.bounds().contains(player.blockPosition().getX(), player.blockPosition().getY(),
                        player.blockPosition().getZ())) {
                    ViewingRoomAnchor exit = premiseExit(server, session.bankId, session.room.premiseId(),
                            session.playerReturn);
                    ServerLevel target = level(server, exit.dimension());
                    if (target != null) {
                        teleport(player, target, exit);
                        player.sendSystemMessage(Component.literal("This private viewing room is currently occupied."));
                    }
                    return;
                }
            }
        }

        private void finish(Session session, FinishReason reason) {
            if (session == null || byId.remove(session.sessionId) == null) {
                return;
            }
            byPlayer.remove(session.playerId, session);
            byTeller.remove(session.tellerId, session);
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player != null) {
                clearDisplayContents(player, session.proxyId);
                player.closeContainer();
                player.setInvulnerable(session.playerWasInvulnerable);
            }
            Entity proxy = findEntity(server, session.proxyId);
            if (proxy != null) {
                proxy.discard();
            }
            ServerLevel sourceLevel = level(server, session.sourceDimension);
            if (sourceLevel != null) {
                sourceLevel.getChunkAt(session.sourcePos);
                if (sourceLevel.getBlockEntity(session.sourcePos) instanceof SafetyDepositBoxRowBlockEntity row) {
                    row.endViewingTransfer(session.doorIndex, session.sessionId);
                }
            }
            restoreTeller(server, session.tellerId, session.tellerReturn);
            restoreOrDeferPlayer(server, session.playerId, session.playerReturn,
                    session.bankId, session.room.premiseId(), session.playerWasInvulnerable);
            session.chunks.release();
            ViewingSessionSavedData.get(server).remove(session.sessionId);
            if (player != null && reason == FinishReason.TIMEOUT) {
                player.sendSystemMessage(Component.literal("Your private box-viewing session timed out."));
            }
        }
    }

    private static final class Session {
        private final UUID sessionId;
        private final UUID bankId;
        private final ViewingRoomSnapshot room;
        private final UUID playerId;
        private final UUID tellerId;
        private final UUID accountId;
        private final String boxNumber;
        private final String sourceDimension;
        private final BlockPos sourcePos;
        private final int doorIndex;
        private final SafetyDepositBoxRowBlockEntity.ModuleType moduleType;
        private final ViewingRoomAnchor playerReturn;
        private final ViewingRoomAnchor tellerReturn;
        private final boolean playerWasInvulnerable;
        private final long startedTick;
        private UUID proxyId;
        private List<ItemStack> displayContents;
        private final ChunkLease chunks;
        private long confirmUntilTick = -1L;
        private boolean warned;

        private Session(UUID sessionId, UUID bankId, ViewingRoomSnapshot room, UUID playerId,
                        UUID tellerId, UUID accountId, String boxNumber, String sourceDimension,
                        BlockPos sourcePos, int doorIndex, SafetyDepositBoxRowBlockEntity.ModuleType moduleType,
                        ViewingRoomAnchor playerReturn, ViewingRoomAnchor tellerReturn,
                        boolean playerWasInvulnerable, long startedTick,
                        UUID proxyId, List<ItemStack> displayContents, ChunkLease chunks) {
            this.sessionId = sessionId;
            this.bankId = bankId;
            this.room = room;
            this.playerId = playerId;
            this.tellerId = tellerId;
            this.accountId = accountId;
            this.boxNumber = boxNumber;
            this.sourceDimension = sourceDimension;
            this.sourcePos = sourcePos;
            this.doorIndex = doorIndex;
            this.moduleType = moduleType;
            this.playerReturn = playerReturn;
            this.tellerReturn = tellerReturn;
            this.playerWasInvulnerable = playerWasInvulnerable;
            this.startedTick = startedTick;
            this.proxyId = proxyId;
            this.displayContents = normalizeDisplayContents(displayContents, moduleType.inventorySlots());
            this.chunks = chunks;
        }
    }

    private static final class ChunkLease {
        private final ServerLevel sourceLevel;
        private final ChunkPos sourceChunk;
        private final ServerLevel roomLevel;
        private final ChunkPos roomChunk;
        private final UUID token;
        private boolean held;

        private ChunkLease(ServerLevel sourceLevel, BlockPos sourcePos,
                           ServerLevel roomLevel, BlockPos roomPos, UUID token) {
            this.sourceLevel = sourceLevel;
            this.sourceChunk = new ChunkPos(sourcePos);
            this.roomLevel = roomLevel;
            this.roomChunk = new ChunkPos(roomPos);
            this.token = token;
        }

        private void acquire() {
            if (held) {
                return;
            }
            sourceLevel.getChunkSource().addRegionTicket(CHUNK_TICKET, sourceChunk,
                    CHUNK_TICKET_LEVEL, token);
            if (sourceLevel != roomLevel || !sourceChunk.equals(roomChunk)) {
                roomLevel.getChunkSource().addRegionTicket(CHUNK_TICKET, roomChunk,
                        CHUNK_TICKET_LEVEL, token);
            }
            held = true;
        }

        private void release() {
            if (!held) {
                return;
            }
            sourceLevel.getChunkSource().removeRegionTicket(CHUNK_TICKET, sourceChunk,
                    CHUNK_TICKET_LEVEL, token);
            if (sourceLevel != roomLevel || !sourceChunk.equals(roomChunk)) {
                roomLevel.getChunkSource().removeRegionTicket(CHUNK_TICKET, roomChunk,
                        CHUNK_TICKET_LEVEL, token);
            }
            held = false;
        }
    }
}
