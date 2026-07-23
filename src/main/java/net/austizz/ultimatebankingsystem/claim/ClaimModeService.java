package net.austizz.ultimatebankingsystem.claim;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import net.austizz.ultimatebankingsystem.network.ClaimModeActionPayload;
import net.austizz.ultimatebankingsystem.network.ClaimModeSnapshotPayload;
import net.austizz.ultimatebankingsystem.network.ClaimOutlineSummary;
import net.austizz.ultimatebankingsystem.network.ModPayloads;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Single server-authoritative gateway for every UBS world-claim workflow. */
public final class ClaimModeService {
    public static final int TIMEOUT_TICKS = 20 * 60 * 5;
    private static final int OUTLINE_RANGE = 128;
    private static final int OUTLINE_LIMIT = 96;
    private static final long HEARTBEAT_TICKS = 100L;
    private static final double MAX_TARGET_RANGE = 8.0D;
    private static final Map<UUID, ActiveSession> SESSIONS = new ConcurrentHashMap<>();

    private ClaimModeService() {
    }

    public static boolean begin(ServerPlayer player, ClaimToolKind kind) {
        if (player == null || kind == null || SESSIONS.containsKey(player.getUUID())) {
            return false;
        }
        if (ShopService.hasStockroomLocateSession(player.getUUID())) {
            ShopService.cancelStockroomLocate(player, "Stockroom locate closed for claim mode.");
        }
        ActiveSession session = new ActiveSession(UUID.randomUUID(), player.getUUID(), kind);
        SESSIONS.put(player.getUUID(), session);
        sync(player, "Claim mode ready.", true, true);
        return true;
    }

    public static boolean hasSession(UUID playerId) {
        return playerId != null && SESSIONS.containsKey(playerId);
    }

    public static ClaimToolKind kind(UUID playerId) {
        ActiveSession session = playerId == null ? null : SESSIONS.get(playerId);
        return session == null ? null : session.kind;
    }

    public static void handleAction(ServerPlayer player, ClaimModeActionPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        ActiveSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.sessionId.toString().equals(payload.sessionId())) {
            PacketDistributor.sendToPlayer(player, ClaimModeSnapshotPayload.inactive());
            return;
        }
        ClaimAction action = ClaimAction.byName(payload.action());
        if ((action == ClaimAction.SET_POS1 || action == ClaimAction.SET_POS2
                || action == ClaimAction.STAGE_TARGET)
                && (!payload.hasTarget() || !validObservedTarget(player,
                new BlockPos(payload.targetX(), payload.targetY(), payload.targetZ())))) {
            sync(player, "The selected block is outside your current reach or crosshair target.", false, true);
            return;
        }

        DomainResult result = dispatch(player, session, action, payload);
        if (action == ClaimAction.APPLY && result.success()) {
            session.appliedSuccessfully = true;
        }
        if (!domainActive(player, session.kind)) {
            domainClosed(player);
            return;
        }
        sync(player, result.message, result.success, true);
    }

    public static void sync(ServerPlayer player, String message, boolean success) {
        sync(player, message, success, true);
    }

    public static void domainClosed(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, ClaimModeSnapshotPayload.inactive());
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            SESSIONS.remove(playerId);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || SESSIONS.isEmpty()) {
            return;
        }
        for (ActiveSession session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                SESSIONS.remove(session.playerId);
                continue;
            }
            if (!domainActive(player, session.kind)) {
                domainClosed(player);
                continue;
            }
            long now = player.serverLevel().getGameTime();
            boolean moved = session.lastOutlineOrigin == null
                    || session.lastOutlineOrigin.distSqr(player.blockPosition()) >= 64.0D;
            if (moved || now - session.lastSentTick >= HEARTBEAT_TICKS) {
                sync(player, session.message, session.success, moved);
            }
        }
    }

    public static void onServerStopping() {
        SESSIONS.clear();
    }

    private static DomainResult dispatch(ServerPlayer player,
                                         ActiveSession session,
                                         ClaimAction action,
                                         ClaimModeActionPayload payload) {
        if (action == ClaimAction.REQUEST_SYNC) {
            return DomainResult.ok(session.message);
        }
        if (action == ClaimAction.TOGGLE_OUTLINES) {
            session.outlinesVisible = !session.outlinesVisible;
            session.cachedOutlines = List.of();
            return DomainResult.ok("Nearby claim outlines "
                    + (session.outlinesVisible ? "enabled." : "hidden."));
        }
        if (action == ClaimAction.DISCARD_AND_EXIT) {
            return discard(player, session.kind);
        }
        if (action == ClaimAction.FINISH_AND_EXIT) {
            return session.appliedSuccessfully
                    ? finishApplied(player, session.kind)
                    : DomainResult.fail("Apply the claim before using Save & Exit.");
        }

        MinecraftServer server = player.getServer();
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (server == null || centralBank == null) {
            return DomainResult.fail("Bank data is unavailable.");
        }
        BlockPos target = payload.hasTarget()
                ? new BlockPos(payload.targetX(), payload.targetY(), payload.targetZ()) : null;

        DomainResult collision = foreignOwnerCollision(centralBank, player, session, action);
        if (collision != null) {
            return collision;
        }

        if (session.kind == ClaimToolKind.SHOP_PLOT || session.kind == ClaimToolKind.SHOP_STOCKROOM) {
            ShopService.ShopActionResult result = switch (action) {
                case SET_POS1 -> ShopService.setClaimToolFirstCorner(player, target);
                case SET_POS2 -> ShopService.setClaimToolSecondCorner(player, target);
                case SET_ADD_MODE -> ShopService.setClaimToolMode(player, true);
                case SET_REMOVE_MODE -> ShopService.setClaimToolMode(player, false);
                case APPLY -> ShopService.applyClaimToolSelection(server, centralBank, player);
                case CLEAR -> ShopService.clearClaimToolSelection(player);
                default -> new ShopService.ShopActionResult(false, "That action is unavailable for this claim tool.");
            };
            return new DomainResult(result.success(), result.message());
        }

        if (session.kind == ClaimToolKind.DELIVERY_PALLET) {
            ShopService.ShopActionResult result = switch (action) {
                case SET_ADD_MODE -> ShopService.setPalletClaimToolMode(player, true);
                case SET_REMOVE_MODE -> ShopService.setPalletClaimToolMode(player, false);
                case STAGE_TARGET -> ShopService.stagePalletClaimSelection(centralBank, player, target);
                case SAVE_AND_EXIT -> ShopService.savePalletClaimToolSession(server, centralBank, player);
                default -> new ShopService.ShopActionResult(false, "That action is unavailable for delivery pallets.");
            };
            return new DomainResult(result.success(), result.message());
        }

        SafetyDepositBoxService.ActionResult result;
        switch (action) {
            case SET_POS1 -> result = SafetyDepositBoxService.setSafeAreaClaimToolFirstCorner(player, target);
            case SET_POS2 -> result = SafetyDepositBoxService.setSafeAreaClaimToolSecondCorner(player, target);
            case SET_ADD_MODE -> result = SafetyDepositBoxService.setSafeAreaClaimToolMode(player, true);
            case SET_REMOVE_MODE -> result = SafetyDepositBoxService.setSafeAreaClaimToolMode(player, false);
            case CAPTURE_POSITION -> result = SafetyDepositBoxService.capturePremiseClaimToolExit(
                    player, player.blockPosition(), player.getYRot());
            case CLEAR -> result = SafetyDepositBoxService.clearSafeAreaClaimToolSelection(player);
            case APPLY -> {
                SafetyDepositBoxService.ClaimToolApplyResult apply =
                        SafetyDepositBoxService.applyClaimToolSelection(server, centralBank, player);
                if (apply.premiseResult() != null) {
                    ModPayloads.sendOwnerPcPremiseActionResult(player, centralBank, apply.premiseResult());
                }
                result = apply.result();
            }
            default -> result = SafetyDepositBoxService.ActionResult.fail(
                    "That action is unavailable for this bank claim tool.");
        }
        return new DomainResult(result.success(), result.message());
    }

    private static DomainResult discard(ServerPlayer player, ClaimToolKind kind) {
        if (kind == ClaimToolKind.SHOP_PLOT || kind == ClaimToolKind.SHOP_STOCKROOM) {
            ShopService.ShopActionResult result = ShopService.finishClaimToolSession(player, "Claim mode closed.");
            return new DomainResult(result.success(), result.message());
        }
        if (kind == ClaimToolKind.DELIVERY_PALLET) {
            ShopService.ShopActionResult result = ShopService.finishPalletClaimToolSession(
                    player, "Pending delivery-pallet changes discarded.");
            return new DomainResult(result.success(), result.message());
        }
        SafetyDepositBoxService.ActionResult result = SafetyDepositBoxService.finishSafeAreaClaimToolSession(
                player, "Bank claim mode closed.");
        return new DomainResult(result.success(), result.message());
    }

    private static DomainResult finishApplied(ServerPlayer player, ClaimToolKind kind) {
        if (kind == ClaimToolKind.DELIVERY_PALLET) {
            return DomainResult.fail("Save pending delivery-pallet changes before exiting.");
        }
        if (kind == ClaimToolKind.SHOP_PLOT || kind == ClaimToolKind.SHOP_STOCKROOM) {
            ShopService.ShopActionResult result = ShopService.finishClaimToolSession(
                    player, "Claim saved. Claim mode closed.");
            return new DomainResult(result.success(), result.message());
        }
        SafetyDepositBoxService.ActionResult result = SafetyDepositBoxService.finishSafeAreaClaimToolSession(
                player, "Bank claim saved. Claim mode closed.");
        return new DomainResult(result.success(), result.message());
    }

    private static DomainResult foreignOwnerCollision(CentralBank centralBank,
                                                       ServerPlayer player,
                                                       ActiveSession session,
                                                       ClaimAction action) {
        if (centralBank == null || player == null || session == null || action != ClaimAction.APPLY) {
            return null;
        }

        UUID ownerId;
        String dimension;
        BlockPos first;
        BlockPos second;
        boolean addMode;
        boolean adminAuthorized = false;
        if (session.kind == ClaimToolKind.SHOP_PLOT || session.kind == ClaimToolKind.SHOP_STOCKROOM) {
            ShopService.ClaimToolView view = ShopService.claimToolView(centralBank, player.getUUID());
            if (view == null) {
                return null;
            }
            ownerId = view.ownerId();
            dimension = view.dimensionId();
            first = view.firstCorner();
            second = view.secondCorner();
            addMode = view.addMode();
        } else if (session.kind == ClaimToolKind.DELIVERY_PALLET) {
            return null;
        } else {
            SafetyDepositBoxService.SafeClaimToolView view =
                    SafetyDepositBoxService.safeClaimToolView(centralBank, player.getUUID());
            if (view == null) {
                return null;
            }
            Bank bank = centralBank.getBank(view.bankId());
            ownerId = bank == null ? player.getUUID() : bank.getBankOwnerId();
            dimension = view.dimensionId();
            first = view.firstCorner();
            second = view.secondCorner();
            addMode = view.addMode();
            adminAuthorized = view.adminAuthorized();
        }
        if (adminAuthorized || !addMode || ownerId == null || first == null || second == null) {
            return null;
        }

        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        boolean shopConflict = ShopService.overlapsClaimOwnedByAnotherPlayer(
                centralBank, ownerId, dimension, minX, minY, minZ, maxX, maxY, maxZ);
        boolean bankConflict = SafetyDepositBoxService.overlapsClaimOwnedByAnotherPlayer(
                centralBank, ownerId, dimension, minX, minY, minZ, maxX, maxY, maxZ);
        return shopConflict || bankConflict
                ? DomainResult.fail("Selection overlaps a claim owned by another player.")
                : null;
    }

    private static boolean validObservedTarget(ServerPlayer player, BlockPos requested) {
        if (player == null || requested == null || player.level() == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(MAX_TARGET_RANGE));
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && requested.equals(hit.getBlockPos());
    }

    private static boolean domainActive(ServerPlayer player, ClaimToolKind kind) {
        if (player == null || kind == null) {
            return false;
        }
        return switch (kind) {
            case SHOP_PLOT, SHOP_STOCKROOM -> ShopService.hasClaimToolSession(player.getUUID());
            case DELIVERY_PALLET -> ShopService.hasPalletClaimToolSession(player.getUUID());
            default -> SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID());
        };
    }

    private static void sync(ServerPlayer player,
                             String message,
                             boolean success,
                             boolean refreshOutlines) {
        ActiveSession session = player == null ? null : SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.message = message == null ? "" : message;
        session.success = success;
        ClaimModeSnapshotPayload snapshot = snapshot(player, session, refreshOutlines);
        if (snapshot == null) {
            domainClosed(player);
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot);
        session.lastSentTick = player.serverLevel().getGameTime();
        session.lastOutlineOrigin = player.blockPosition().immutable();
    }

    private static ClaimModeSnapshotPayload snapshot(ServerPlayer player,
                                                     ActiveSession session,
                                                     boolean refreshOutlines) {
        MinecraftServer server = player.getServer();
        CentralBank centralBank = server == null ? null : BankManager.getCentralBank(server);
        if (centralBank == null) {
            return null;
        }

        String contextName;
        String ownerName;
        String dimension;
        boolean addMode;
        long lastUpdated;
        BlockPos first;
        BlockPos second;
        boolean hasAnchor = false;
        double anchorX = 0.0D;
        double anchorY = 0.0D;
        double anchorZ = 0.0D;
        float anchorYaw = 0.0F;
        int pendingAdd = 0;
        int pendingRemove = 0;

        if (session.kind == ClaimToolKind.SHOP_PLOT || session.kind == ClaimToolKind.SHOP_STOCKROOM) {
            ShopService.ClaimToolView view = ShopService.claimToolView(centralBank, player.getUUID());
            if (view == null) return null;
            contextName = view.shopName();
            ownerName = view.ownerName();
            dimension = view.dimensionId();
            addMode = view.addMode();
            lastUpdated = view.lastUpdatedTick();
            first = view.firstCorner();
            second = view.secondCorner();
        } else if (session.kind == ClaimToolKind.DELIVERY_PALLET) {
            ShopService.PalletClaimToolView view = ShopService.palletClaimToolView(centralBank, player.getUUID());
            if (view == null) return null;
            contextName = view.shopName();
            ownerName = view.ownerName();
            dimension = player.serverLevel().dimension().location().toString();
            addMode = view.addMode();
            lastUpdated = view.lastUpdatedTick();
            first = null;
            second = null;
            pendingAdd = view.pendingAdd();
            pendingRemove = view.pendingRemove();
        } else {
            SafetyDepositBoxService.SafeClaimToolView view =
                    SafetyDepositBoxService.safeClaimToolView(centralBank, player.getUUID());
            if (view == null) return null;
            contextName = view.bankName();
            ownerName = view.ownerName();
            dimension = view.dimensionId();
            addMode = view.addMode();
            lastUpdated = view.lastUpdatedTick();
            first = view.firstCorner();
            second = view.secondCorner();
            hasAnchor = view.hasAnchor();
            anchorX = view.anchorX();
            anchorY = view.anchorY();
            anchorZ = view.anchorZ();
            anchorYaw = view.anchorYaw();
        }

        if (dimension == null || dimension.isBlank()) {
            dimension = player.serverLevel().dimension().location().toString();
        }
        if (refreshOutlines || session.cachedOutlines.isEmpty()) {
            session.cachedOutlines = collectOutlines(
                    centralBank, player, session.kind, session.outlinesVisible);
        }
        long now = player.serverLevel().getGameTime();
        int remaining = (int) Math.max(0L, TIMEOUT_TICKS - Math.max(0L, now - lastUpdated));
        return new ClaimModeSnapshotPayload(
                true, session.sessionId.toString(), session.kind.name(),
                session.kind.displayName() + " Claim Mode", contextName, ownerName, dimension,
                addMode, session.outlinesVisible,
                first != null, first == null ? 0 : first.getX(), first == null ? 0 : first.getY(),
                first == null ? 0 : first.getZ(),
                second != null, second == null ? 0 : second.getX(), second == null ? 0 : second.getY(),
                second == null ? 0 : second.getZ(),
                hasAnchor, anchorX, anchorY, anchorZ, anchorYaw,
                pendingAdd, pendingRemove, remaining, session.message, session.success,
                session.appliedSuccessfully, session.cachedOutlines);
    }

    private static List<ClaimOutlineSummary> collectOutlines(CentralBank centralBank,
                                                              ServerPlayer player,
                                                              ClaimToolKind kind,
                                                              boolean visible) {
        Map<String, ClaimOutline> unique = new LinkedHashMap<>();
        if (kind == ClaimToolKind.DELIVERY_PALLET) {
            for (ClaimOutline outline : ShopService.collectPendingPalletClaimOutlines(
                    player.getServer(), centralBank, player.getUUID(), OUTLINE_LIMIT)) {
                unique.put(outlineKey(outline), outline);
            }
        }
        if (!visible || unique.size() >= OUTLINE_LIMIT) {
            return unique.values().stream().limit(OUTLINE_LIMIT).map(ClaimOutlineSummary::from).toList();
        }

        UUID visibleOwner = Config.CLAIM_OUTLINES_SHOW_ALL_PLAYERS.get()
                ? null : player.getUUID();
        int remaining = Math.max(0, OUTLINE_LIMIT - unique.size());
        for (ClaimOutline outline : ShopService.collectClaimOutlines(
                centralBank, player, OUTLINE_RANGE, remaining, visibleOwner)) {
            if (hasPendingPalletAt(unique, outline)) {
                continue;
            }
            unique.put(outlineKey(outline), outline);
        }
        remaining = Math.max(0, OUTLINE_LIMIT - unique.size());
        for (ClaimOutline outline : SafetyDepositBoxService.collectClaimOutlines(
                centralBank, player, OUTLINE_RANGE, remaining, visibleOwner, player.hasPermissions(2))) {
            unique.putIfAbsent(outlineKey(outline), outline);
            if (unique.size() >= OUTLINE_LIMIT) break;
        }
        return unique.values().stream().limit(OUTLINE_LIMIT).map(ClaimOutlineSummary::from).toList();
    }

    private static String outlineKey(ClaimOutline outline) {
        return outline.dimensionId() + '|' + outline.type() + '|'
                + outline.minX() + '|' + outline.minY() + '|' + outline.minZ() + '|'
                + outline.maxX() + '|' + outline.maxY() + '|' + outline.maxZ();
    }

    private static boolean hasPendingPalletAt(Map<String, ClaimOutline> outlines,
                                              ClaimOutline candidate) {
        if (candidate == null || !"DELIVERY_PALLET".equals(candidate.type())) {
            return false;
        }
        for (ClaimOutline outline : outlines.values()) {
            if (outline.type().startsWith("PENDING_PALLET_")
                    && outline.dimensionId().equals(candidate.dimensionId())
                    && outline.minX() == candidate.minX()
                    && outline.minY() == candidate.minY()
                    && outline.minZ() == candidate.minZ()
                    && outline.maxX() == candidate.maxX()
                    && outline.maxY() == candidate.maxY()
                    && outline.maxZ() == candidate.maxZ()) {
                return true;
            }
        }
        return false;
    }

    public static ClaimToolKind fromSafePurpose(SafeClaimToolPurpose purpose) {
        if (purpose == null) {
            return ClaimToolKind.BANK_SAFE_AREA;
        }
        return switch (purpose) {
            case SAFE_AREA -> ClaimToolKind.BANK_SAFE_AREA;
            case PREMISE_CREATE -> ClaimToolKind.BANK_PREMISE_CREATE;
            case PREMISE_EXIT_EDIT -> ClaimToolKind.BANK_PREMISE_EXIT_EDIT;
            case VIEWING_ROOM_CREATE -> ClaimToolKind.VIEWING_ROOM_CREATE;
            case VIEWING_ROOM_CUSTOMER_ANCHOR -> ClaimToolKind.VIEWING_ROOM_CUSTOMER_ANCHOR;
            case VIEWING_ROOM_TELLER_ANCHOR -> ClaimToolKind.VIEWING_ROOM_TELLER_ANCHOR;
            case VIEWING_ROOM_DISPLAY_ANCHOR -> ClaimToolKind.VIEWING_ROOM_DISPLAY_ANCHOR;
        };
    }

    private record DomainResult(boolean success, String message) {
        private DomainResult {
            message = message == null ? "" : message;
        }

        static DomainResult ok(String message) {
            return new DomainResult(true, message);
        }

        static DomainResult fail(String message) {
            return new DomainResult(false, message);
        }
    }

    private static final class ActiveSession {
        private final UUID sessionId;
        private final UUID playerId;
        private final ClaimToolKind kind;
        private boolean outlinesVisible = true;
        private String message = "";
        private boolean success = true;
        private boolean appliedSuccessfully;
        private long lastSentTick = Long.MIN_VALUE;
        private BlockPos lastOutlineOrigin;
        private List<ClaimOutlineSummary> cachedOutlines = List.of();

        private ActiveSession(UUID sessionId, UUID playerId, ClaimToolKind kind) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.kind = kind;
        }
    }
}
