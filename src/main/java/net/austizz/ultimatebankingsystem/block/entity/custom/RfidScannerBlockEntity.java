package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.RfidScannerBlock;
import net.austizz.ultimatebankingsystem.block.custom.RfidSignalRelayBlock;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.item.RfidCardItem;
import net.austizz.ultimatebankingsystem.network.RfidScannerActionPayload;
import net.austizz.ultimatebankingsystem.network.RfidScannerOpenPayload;
import net.austizz.ultimatebankingsystem.network.RfidTargetSelectPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RfidScannerBlockEntity extends BlockEntity {
    private static final int MIN_SIGNAL = 0;
    private static final int MAX_SIGNAL = 15;
    private static final int MIN_ACCESS_LEVEL = 0;
    private static final int MAX_ACCESS_LEVEL = 100;
    private static final int MIN_ATTEMPTS = 1;
    private static final int MAX_ATTEMPTS = 20;
    private static final int MIN_DURATION = 5;
    private static final int MAX_DURATION = 20 * 60 * 5;

    private UUID readerId = UUID.randomUUID();
    private boolean configured = false;
    private String pinHash = "";
    private boolean enabled = true;
    private int requiredAccessLevel = 1;
    private String linkType = "NONE";
    private String linkName = "";
    private ForceMode forceMode = ForceMode.NORMAL;
    private int idleSignal = 0;
    private int successSignal = 15;
    private int failSignal = 14;
    private int successDurationTicks = 60;
    private int failDurationTicks = 40;
    private int failThreshold = 3;
    private int failAttempts = 0;
    private ActiveState activeState = ActiveState.IDLE;
    private boolean failRelaysActive = false;
    private long activeUntilGameTime = 0L;
    private UUID authorizedPlayerId;
    private UUID escortSessionId;
    private final Map<UUID, CardGrant> cardGrants = new LinkedHashMap<>();
    private final List<TargetBinding> successTargets = new ArrayList<>();
    private final List<TargetBinding> failTargets = new ArrayList<>();

    public RfidScannerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.RFID_SCANNER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RfidScannerBlockEntity scanner) {
        scanner.tickServer();
    }

    public void openFor(ServerPlayer player) {
        sendOpen(player, false, "", true);
    }

    public boolean activateForEscort(UUID sessionId, UUID playerId) {
        if (!(getLevel() instanceof ServerLevel serverLevel)
                || sessionId == null || playerId == null || !configured
                || !enabled || forceMode == ForceMode.CLOSED) {
            return false;
        }
        if (forceMode == ForceMode.OPEN) {
            return true;
        }
        failAttempts = 0;
        authorizedPlayerId = playerId;
        escortSessionId = sessionId;
        activeState = ActiveState.SUCCESS;
        failRelaysActive = false;
        activeUntilGameTime = serverLevel.getGameTime() + successDurationTicks;
        setScannerStatus(RfidScannerBlock.RfidStatus.SUCCESS);
        applyRelaySignals();
        markUpdated();
        return true;
    }

    public boolean canSpoofSuccess() {
        return getLevel() instanceof ServerLevel
                && configured
                && enabled
                && forceMode == ForceMode.NORMAL
                && activeState != ActiveState.SUCCESS
                && !successTargets.isEmpty();
    }

    /** Activates only this reader's configured success relays without granting RFID access. */
    public boolean activateHeistSpoof(UUID heistSessionId) {
        if (!(getLevel() instanceof ServerLevel serverLevel)
                || heistSessionId == null
                || !canSpoofSuccess()) {
            return false;
        }
        failAttempts = 0;
        authorizedPlayerId = null;
        escortSessionId = heistSessionId;
        activeState = ActiveState.SUCCESS;
        failRelaysActive = false;
        activeUntilGameTime = serverLevel.getGameTime() + successDurationTicks;
        setScannerStatus(RfidScannerBlock.RfidStatus.SUCCESS);
        applyRelaySignals();
        markUpdated();
        return true;
    }

    public void revokeEscortAccess(UUID sessionId) {
        if (sessionId == null || !sessionId.equals(escortSessionId)) {
            return;
        }
        escortSessionId = null;
        authorizedPlayerId = null;
        if (forceMode == ForceMode.NORMAL && activeState == ActiveState.SUCCESS) {
            activeState = ActiveState.IDLE;
            failRelaysActive = false;
            activeUntilGameTime = 0L;
            setScannerStatus(RfidScannerBlock.RfidStatus.IDLE);
            applyRelaySignals();
            markUpdated();
        }
    }

    /** Fires the configured fail relays when this reader is explicitly linked to the attacked bank. */
    public boolean triggerBankAlarm(UUID bankId, String bankName) {
        if (!(getLevel() instanceof ServerLevel serverLevel)
                || !configured || !enabled || forceMode == ForceMode.CLOSED
                || !"BANK".equals(linkType) || !matchesBankLink(bankId, bankName)) {
            return false;
        }
        activeState = ActiveState.FAIL;
        failRelaysActive = true;
        activeUntilGameTime = serverLevel.getGameTime() + failDurationTicks;
        setScannerStatus(RfidScannerBlock.RfidStatus.FAIL);
        applyRelaySignals();
        markUpdated();
        return true;
    }

    public boolean isAlarmLinkedToBank(UUID bankId, String bankName) {
        return configured && enabled && "BANK".equals(linkType) && matchesBankLink(bankId, bankName);
    }

    public void scanCard(ServerPlayer player, ItemStack stack) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!enabled || forceMode == ForceMode.CLOSED) {
            triggerFail(player, false, "RFID reader is disabled.");
            return;
        }
        if (forceMode == ForceMode.OPEN) {
            player.sendSystemMessage(UbsTranslations.literal("RFID reader is forced open."));
            return;
        }
        UUID cardId = RfidCardItem.getCardId(stack);
        int grantLevel = RfidCardItem.getGrantLevel(stack, readerId);
        CardGrant grant = cardId == null ? null : cardGrants.get(cardId);
        if (cardId != null && grant != null && grantLevel >= requiredAccessLevel && grant.level() >= requiredAccessLevel) {
            this.failAttempts = 0;
            this.authorizedPlayerId = player.getUUID();
            this.escortSessionId = null;
            this.activeState = ActiveState.SUCCESS;
            this.failRelaysActive = false;
            this.activeUntilGameTime = serverLevel.getGameTime() + successDurationTicks;
            setScannerStatus(RfidScannerBlock.RfidStatus.SUCCESS);
            applyRelaySignals();
            markUpdated();
            player.sendSystemMessage(UbsTranslations.literal("RFID accepted. Access window open.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        triggerFail(player, true, "RFID denied.");
    }

    public void handleAction(ServerPlayer player, RfidScannerActionPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        String action = payload.action().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "SETUP" -> setup(player, payload);
            case "LOGIN" -> login(player, payload.pin());
            case "SAVE_GENERAL" -> saveGeneral(player, payload);
            case "SAVE_SIGNALS" -> saveSignals(player, payload);
            case "SET_MODE" -> setMode(player, payload);
            case "ISSUE_CARD" -> issueCard(player, payload);
            case "REMOVE_CARD" -> removeCard(player, payload);
            case "REMOVE_TARGET" -> removeTarget(player, payload);
            default -> sendOpen(player, false, "Unknown RFID action.", false);
        }
    }

    public void handleTargetSelection(ServerPlayer player, RfidTargetSelectPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        if (!requirePin(player, payload.pin())) {
            return;
        }
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        String targetDimension = payload.targetDimensionId().trim();
        if (!serverLevel.dimension().location().toString().equals(targetDimension)) {
            sendOpen(player, true, "Target must be in the same dimension as the reader.", false);
            return;
        }
        BlockPos targetPos = new BlockPos(payload.targetX(), payload.targetY(), payload.targetZ());
        if (targetPos.distSqr(player.blockPosition()) > 16 * 16) {
            sendOpen(player, true, "Target is too far away.", false);
            return;
        }
        Direction face = parseDirection(payload.face(), Direction.UP);
        BlockPos relayPos = targetPos.relative(face);
        BlockState relayState = serverLevel.getBlockState(relayPos);
        if (!relayState.isAir() && !relayState.is(ModBlocks.RFID_SIGNAL_RELAY.get())) {
            sendOpen(player, true, "Selected side is occupied. Pick another face of the target block.", false);
            return;
        }
        serverLevel.setBlock(relayPos, ModBlocks.RFID_SIGNAL_RELAY.get().defaultBlockState()
                .setValue(RfidSignalRelayBlock.SIGNAL_SIDE, face), Block.UPDATE_ALL);
        TargetBinding binding = new TargetBinding(targetDimension, targetPos, relayPos, face, targetLabel(serverLevel, targetPos));
        List<TargetBinding> list = isFailTarget(payload.targetType()) ? failTargets : successTargets;
        list.removeIf(existing -> existing.targetPos().equals(targetPos) && existing.relayPos().equals(relayPos));
        list.add(binding);
        applyRelaySignals();
        markUpdated();
        sendOpen(player, true, "RFID " + (isFailTarget(payload.targetType()) ? "fail" : "success") + " target linked.", true);
    }

    public void removeRelays() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (TargetBinding target : allTargets()) {
            if (serverLevel.getBlockState(target.relayPos()).is(ModBlocks.RFID_SIGNAL_RELAY.get())) {
                serverLevel.setBlock(target.relayPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private void tickServer() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        if (forceMode == ForceMode.OPEN) {
            setScannerStatus(RfidScannerBlock.RfidStatus.SUCCESS);
            applyRelaySignals();
            return;
        }
        if (forceMode == ForceMode.CLOSED || !enabled) {
            setScannerStatus(RfidScannerBlock.RfidStatus.IDLE);
            applyRelaySignals();
            return;
        }
        if (activeState != ActiveState.IDLE && activeUntilGameTime <= gameTime) {
            activeState = ActiveState.IDLE;
            failRelaysActive = false;
            authorizedPlayerId = null;
            escortSessionId = null;
            setScannerStatus(RfidScannerBlock.RfidStatus.IDLE);
            applyRelaySignals();
            markUpdated();
            return;
        }
        if (activeState == ActiveState.SUCCESS) {
            enforceGuardZones(serverLevel);
        }
    }

    private void setup(ServerPlayer player, RfidScannerActionPayload payload) {
        if (configured) {
            sendOpen(player, false, "PIN already configured.", false);
            return;
        }
        String newPin = sanitizePin(payload.text1());
        if (!isValidPin(newPin)) {
            sendOpen(player, false, "PIN must be 4-12 digits.", false);
            return;
        }
        this.pinHash = hashPin(newPin);
        this.configured = true;
        this.requiredAccessLevel = clampAccess(payload.int1() <= 0 ? requiredAccessLevel : payload.int1());
        this.enabled = true;
        this.failAttempts = 0;
        markUpdated();
        sendOpen(player, true, "RFID scanner initialized.", true);
    }

    private void login(ServerPlayer player, String pin) {
        if (!configured) {
            sendOpen(player, false, "Set a PIN first.", false);
            return;
        }
        if (!matchesPin(pin)) {
            sendOpen(player, false, "PIN rejected.", false);
            return;
        }
        sendOpen(player, true, "RFID settings unlocked.", true);
    }

    private void saveGeneral(ServerPlayer player, RfidScannerActionPayload payload) {
        if (!requirePin(player, payload.pin())) {
            return;
        }
        this.enabled = payload.bool1();
        this.requiredAccessLevel = clampAccess(payload.int1());
        this.linkType = normalizeLinkType(payload.text1());
        this.linkName = payload.text2().trim();
        applyRelaySignals();
        markUpdated();
        sendOpen(player, true, "General RFID settings saved.", true);
    }

    private void saveSignals(ServerPlayer player, RfidScannerActionPayload payload) {
        if (!requirePin(player, payload.pin())) {
            return;
        }
        this.idleSignal = clampSignal(payload.int1());
        this.successSignal = clampSignal(payload.int2());
        this.failSignal = clampSignal(payload.int3());
        this.successDurationTicks = clampDuration(payload.int4(), successDurationTicks);
        this.failDurationTicks = clampDuration(parseInt(payload.text1(), failDurationTicks), failDurationTicks);
        this.failThreshold = clampAttempts(parseInt(payload.text2(), failThreshold));
        applyRelaySignals();
        markUpdated();
        sendOpen(player, true, "RFID signal policy saved.", true);
    }

    private void setMode(ServerPlayer player, RfidScannerActionPayload payload) {
        if (!requirePin(player, payload.pin())) {
            return;
        }
        this.forceMode = ForceMode.parse(payload.text1());
        this.activeState = ActiveState.IDLE;
        this.failRelaysActive = false;
        this.authorizedPlayerId = null;
        this.escortSessionId = null;
        applyRelaySignals();
        markUpdated();
        sendOpen(player, true, "Reader mode set to " + forceMode.label() + ".", true);
    }

    private void issueCard(ServerPlayer player, RfidScannerActionPayload payload) {
        if (!requirePin(player, payload.pin())) {
            return;
        }
        String targetName = payload.text1().trim();
        if (targetName.isBlank() || player.getServer() == null) {
            sendOpen(player, true, "Enter an online player name.", false);
            return;
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayers()
                .stream()
                .filter(candidate -> candidate.getGameProfile().getName().equalsIgnoreCase(targetName))
                .findFirst()
                .orElse(null);
        if (target == null) {
            sendOpen(player, true, "Player is not online.", false);
            return;
        }
        int level = clampAccess(payload.int1() <= 0 ? requiredAccessLevel : payload.int1());
        ItemStack card = findExistingCard(target);
        boolean createdCard = card.isEmpty();
        if (createdCard) {
            card = new ItemStack(ModItems.RFID_CARD.get());
        }
        UUID cardId = RfidCardItem.ensureCardId(card);
        String label = linkName.isBlank() ? "RFID " + shortId(readerId) : linkName;
        RfidCardItem.writeGrant(card, readerId, level, label);
        if (createdCard && !target.getInventory().add(card)) {
            target.drop(card, false);
        }
        cardGrants.put(cardId, new CardGrant(cardId, label, level, target.getGameProfile().getName()));
        markUpdated();
        target.sendSystemMessage(UbsTranslations.literal("RFID card granted: " + label + " level " + level)
                .withStyle(ChatFormatting.AQUA));
        sendOpen(player, true, "RFID card written for " + target.getGameProfile().getName() + ".", true);
    }

    private void removeCard(ServerPlayer player, RfidScannerActionPayload payload) {
        if (!requirePin(player, payload.pin())) {
            return;
        }
        UUID cardId = parseUuid(payload.text1());
        if (cardId == null || cardGrants.remove(cardId) == null) {
            sendOpen(player, true, "Card grant not found.", false);
            return;
        }
        markUpdated();
        sendOpen(player, true, "Card removed from this reader.", true);
    }

    private void removeTarget(ServerPlayer player, RfidScannerActionPayload payload) {
        if (!requirePin(player, payload.pin())) {
            return;
        }
        List<TargetBinding> list = isFailTarget(payload.text1()) ? failTargets : successTargets;
        int index = payload.int1();
        if (index < 0 || index >= list.size()) {
            sendOpen(player, true, "Target not found.", false);
            return;
        }
        TargetBinding removed = list.remove(index);
        if (getLevel() instanceof ServerLevel serverLevel
                && serverLevel.getBlockState(removed.relayPos()).is(ModBlocks.RFID_SIGNAL_RELAY.get())) {
            serverLevel.setBlock(removed.relayPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        markUpdated();
        sendOpen(player, true, "Target removed.", true);
    }

    private boolean requirePin(ServerPlayer player, String pin) {
        if (!configured) {
            sendOpen(player, false, "Set a PIN first.", false);
            return false;
        }
        if (matchesPin(pin)) {
            return true;
        }
        sendOpen(player, false, "PIN rejected.", false);
        return false;
    }

    private void triggerFail(ServerPlayer player, boolean countAttempt, String message) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean thresholdReached = false;
        if (countAttempt) {
            failAttempts = Math.max(0, failAttempts + 1);
            thresholdReached = failAttempts >= failThreshold;
        }
        this.activeState = ActiveState.FAIL;
        this.failRelaysActive = thresholdReached;
        this.activeUntilGameTime = serverLevel.getGameTime() + failDurationTicks;
        setScannerStatus(RfidScannerBlock.RfidStatus.FAIL);
        if (thresholdReached) {
            failAttempts = 0;
        }
        applyRelaySignals();
        markUpdated();
        player.sendSystemMessage(UbsTranslations.literal(message + " Attempts: " + failAttempts + "/" + failThreshold)
                .withStyle(ChatFormatting.RED));
    }

    private void enforceGuardZones(ServerLevel serverLevel) {
        UUID allowed = authorizedPlayerId;
        if (allowed == null || successTargets.isEmpty()) {
            return;
        }
        for (TargetBinding target : successTargets) {
            AABB guard = new AABB(target.targetPos()).inflate(0.18D, 0.0D, 0.18D).expandTowards(0.0D, 1.8D, 0.0D);
            List<ServerPlayer> players = serverLevel.getPlayers(player -> !player.getUUID().equals(allowed)
                    && guard.intersects(sweptMovementBounds(player)));
            BlockState targetState = serverLevel.getBlockState(target.targetPos());
            for (ServerPlayer blocked : players) {
                Direction.Axis axis = guardAxis(targetState, target.face(), blocked, target.targetPos());
                Direction approachSide = approachSide(blocked, target.targetPos(), axis);
                moveOutsideGuard(blocked, guard, approachSide);
            }
        }
    }

    private static AABB sweptMovementBounds(ServerPlayer player) {
        return player.getBoundingBox().expandTowards(
                player.xo - player.getX(),
                player.yo - player.getY(),
                player.zo - player.getZ()
        );
    }

    private static Direction.Axis guardAxis(BlockState targetState,
                                            Direction selectedFace,
                                            ServerPlayer player,
                                            BlockPos targetPos) {
        if (targetState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return targetState.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        }
        if (selectedFace.getAxis() != Direction.Axis.Y) {
            return selectedFace.getAxis();
        }
        double centerX = targetPos.getX() + 0.5D;
        double centerZ = targetPos.getZ() + 0.5D;
        return Math.abs(player.xo - centerX) >= Math.abs(player.zo - centerZ)
                ? Direction.Axis.X
                : Direction.Axis.Z;
    }

    private static Direction approachSide(ServerPlayer player, BlockPos targetPos, Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            double center = targetPos.getX() + 0.5D;
            double previous = Math.abs(player.xo - center) > 0.01D ? player.xo : player.getX();
            return previous < center ? Direction.WEST : Direction.EAST;
        }
        double center = targetPos.getZ() + 0.5D;
        double previous = Math.abs(player.zo - center) > 0.01D ? player.zo : player.getZ();
        return previous < center ? Direction.NORTH : Direction.SOUTH;
    }

    private static void moveOutsideGuard(ServerPlayer player, AABB guard, Direction approachSide) {
        AABB playerBounds = player.getBoundingBox();
        double halfWidth = Math.max(0.1D, playerBounds.getXsize() * 0.5D);
        double clearance = halfWidth + 0.03D;
        double x = player.getX();
        double z = player.getZ();
        Vec3 movement = player.getDeltaMovement();
        if (approachSide == Direction.WEST) {
            x = guard.minX - clearance;
            movement = new Vec3(0.0D, movement.y, movement.z);
        } else if (approachSide == Direction.EAST) {
            x = guard.maxX + clearance;
            movement = new Vec3(0.0D, movement.y, movement.z);
        } else if (approachSide == Direction.NORTH) {
            z = guard.minZ - clearance;
            movement = new Vec3(movement.x, movement.y, 0.0D);
        } else {
            z = guard.maxZ + clearance;
            movement = new Vec3(movement.x, movement.y, 0.0D);
        }
        player.teleportTo(x, player.getY(), z);
        player.setDeltaMovement(movement);
        player.hurtMarked = true;
    }

    private void setRelays(List<TargetBinding> targets, int power) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (TargetBinding target : targets) {
            if (!serverLevel.dimension().location().toString().equals(target.dimensionId())) {
                continue;
            }
            if (!serverLevel.getBlockState(target.relayPos()).is(ModBlocks.RFID_SIGNAL_RELAY.get())) {
                if (serverLevel.getBlockState(target.relayPos()).isAir()) {
                    serverLevel.setBlock(target.relayPos(), ModBlocks.RFID_SIGNAL_RELAY.get().defaultBlockState()
                            .setValue(RfidSignalRelayBlock.SIGNAL_SIDE, target.face()), Block.UPDATE_ALL);
                } else {
                    continue;
                }
            }
            RfidSignalRelayBlock.setPower(serverLevel, target.relayPos(), target.face(), power);
        }
    }

    private void applyRelaySignals() {
        if (!enabled || forceMode == ForceMode.CLOSED) {
            setRelays(successTargets, 0);
            setRelays(failTargets, 0);
            return;
        }
        int successPower = forceMode == ForceMode.OPEN || activeState == ActiveState.SUCCESS
                ? successSignal
                : idleSignal;
        int failPower = activeState == ActiveState.FAIL && failRelaysActive
                ? failSignal
                : idleSignal;
        setRelays(successTargets, successPower);
        setRelays(failTargets, failPower);
    }

    private void setScannerStatus(RfidScannerBlock.RfidStatus status) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(RfidScannerBlock.STATUS) && state.getValue(RfidScannerBlock.STATUS) != status) {
            level.setBlock(worldPosition, state.setValue(RfidScannerBlock.STATUS, status), Block.UPDATE_ALL);
        }
    }

    private ItemStack findExistingCard(ServerPlayer target) {
        Inventory inventory = target.getInventory();
        for (ItemStack stack : inventory.items) {
            if (stack.getItem() instanceof RfidCardItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void sendOpen(ServerPlayer player, boolean authenticated, String message, boolean messageSuccess) {
        PacketDistributor.sendToPlayer(player, snapshot(authenticated, message, messageSuccess));
    }

    private RfidScannerOpenPayload snapshot(boolean authenticated, String message, boolean messageSuccess) {
        Level level = getLevel();
        return new RfidScannerOpenPayload(
                level == null ? "" : level.dimension().location().toString(),
                worldPosition.getX(),
                worldPosition.getY(),
                worldPosition.getZ(),
                readerId.toString(),
                configured,
                authenticated,
                enabled,
                requiredAccessLevel,
                linkType,
                linkName,
                forceMode.name(),
                idleSignal,
                successSignal,
                failSignal,
                successDurationTicks,
                failDurationTicks,
                failThreshold,
                failAttempts,
                activeState.name(),
                message,
                messageSuccess,
                cardGrants.values()
                        .stream()
                        .sorted(Comparator.comparing(CardGrant::holderName).thenComparing(CardGrant::cardId))
                        .map(grant -> new RfidScannerOpenPayload.CardSummary(
                                grant.cardId().toString(),
                                grant.label(),
                                grant.level(),
                                grant.holderName()))
                        .toList(),
                successTargets.stream().map(TargetBinding::summary).toList(),
                failTargets.stream().map(TargetBinding::summary).toList()
        );
    }

    private List<TargetBinding> allTargets() {
        List<TargetBinding> all = new ArrayList<>(successTargets.size() + failTargets.size());
        all.addAll(successTargets);
        all.addAll(failTargets);
        return all;
    }

    private boolean matchesPin(String pin) {
        String clean = sanitizePin(pin);
        return configured && !pinHash.isBlank() && hashPin(clean).equals(pinHash);
    }

    private boolean matchesBankLink(UUID bankId, String bankName) {
        String configuredLink = linkName == null ? "" : linkName.trim();
        return !configuredLink.isEmpty()
                && (bankId != null && configuredLink.equalsIgnoreCase(bankId.toString())
                || bankName != null && configuredLink.equalsIgnoreCase(bankName.trim()));
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salt = "ultimatebankingsystem:rfid_scanner:" + readerId + ":" + worldPosition.asLong();
            byte[] bytes = digest.digest((salt + ":" + sanitizePin(pin)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sanitizePin(String pin) {
        return pin == null ? "" : pin.trim();
    }

    private static boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{4,12}");
    }

    private static int clampSignal(int value) {
        return Mth.clamp(value, MIN_SIGNAL, MAX_SIGNAL);
    }

    private static int clampAccess(int value) {
        return Mth.clamp(value, MIN_ACCESS_LEVEL, MAX_ACCESS_LEVEL);
    }

    private static int clampAttempts(int value) {
        return Mth.clamp(value <= 0 ? 3 : value, MIN_ATTEMPTS, MAX_ATTEMPTS);
    }

    private static int clampDuration(int value, int fallback) {
        return Mth.clamp(value <= 0 ? fallback : value, MIN_DURATION, MAX_DURATION);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw == null ? "" : raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Direction parseDirection(String raw, Direction fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Direction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static boolean isFailTarget(String type) {
        return "FAIL".equalsIgnoreCase(type == null ? "" : type.trim());
    }

    private static String normalizeLinkType(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("BANK") && !value.equals("SHOP")) {
            return "NONE";
        }
        return value;
    }

    private static String targetLabel(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Component name = state.getBlock().getName();
        return name == null ? pos.toShortString() : name.getString() + " " + pos.toShortString();
    }

    private static String shortId(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.readerId = parseUuid(tag.getString("reader_id"));
        if (readerId == null) {
            readerId = UUID.randomUUID();
        }
        this.configured = tag.getBoolean("configured");
        this.pinHash = tag.getString("pin_hash");
        this.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        this.requiredAccessLevel = clampAccess(tag.contains("required_access_level") ? tag.getInt("required_access_level") : 1);
        this.linkType = normalizeLinkType(tag.getString("link_type"));
        this.linkName = tag.getString("link_name");
        this.forceMode = ForceMode.parse(tag.getString("force_mode"));
        this.idleSignal = clampSignal(tag.getInt("idle_signal"));
        this.successSignal = clampSignal(tag.contains("success_signal") ? tag.getInt("success_signal") : 15);
        this.failSignal = clampSignal(tag.contains("fail_signal") ? tag.getInt("fail_signal") : 14);
        this.successDurationTicks = clampDuration(tag.getInt("success_duration_ticks"), 60);
        this.failDurationTicks = clampDuration(tag.getInt("fail_duration_ticks"), 40);
        this.failThreshold = clampAttempts(tag.getInt("fail_threshold"));
        this.failAttempts = Math.max(0, tag.getInt("fail_attempts"));
        this.activeState = ActiveState.IDLE;
        this.failRelaysActive = false;
        this.authorizedPlayerId = null;
        cardGrants.clear();
        for (Tag entry : tag.getList("card_grants", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag grantTag) {
                CardGrant grant = CardGrant.fromTag(grantTag);
                if (grant.cardId() != null) {
                    cardGrants.put(grant.cardId(), grant);
                }
            }
        }
        successTargets.clear();
        loadTargets(tag.getList("success_targets", Tag.TAG_COMPOUND), successTargets);
        failTargets.clear();
        loadTargets(tag.getList("fail_targets", Tag.TAG_COMPOUND), failTargets);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("reader_id", readerId.toString());
        tag.putBoolean("configured", configured);
        tag.putString("pin_hash", pinHash == null ? "" : pinHash);
        tag.putBoolean("enabled", enabled);
        tag.putInt("required_access_level", requiredAccessLevel);
        tag.putString("link_type", linkType == null ? "NONE" : linkType);
        tag.putString("link_name", linkName == null ? "" : linkName);
        tag.putString("force_mode", forceMode.name());
        tag.putInt("idle_signal", idleSignal);
        tag.putInt("success_signal", successSignal);
        tag.putInt("fail_signal", failSignal);
        tag.putInt("success_duration_ticks", successDurationTicks);
        tag.putInt("fail_duration_ticks", failDurationTicks);
        tag.putInt("fail_threshold", failThreshold);
        tag.putInt("fail_attempts", failAttempts);
        ListTag cards = new ListTag();
        cardGrants.values().forEach(grant -> cards.add(grant.toTag()));
        tag.put("card_grants", cards);
        tag.put("success_targets", saveTargets(successTargets));
        tag.put("fail_targets", saveTargets(failTargets));
    }

    private static void loadTargets(ListTag source, List<TargetBinding> target) {
        for (Tag entry : source) {
            if (entry instanceof CompoundTag compound) {
                TargetBinding binding = TargetBinding.fromTag(compound);
                if (binding != null) {
                    target.add(binding);
                }
            }
        }
    }

    private static ListTag saveTargets(List<TargetBinding> targets) {
        ListTag tag = new ListTag();
        for (TargetBinding target : targets) {
            tag.add(target.toTag());
        }
        return tag;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private enum ActiveState {
        IDLE,
        SUCCESS,
        FAIL
    }

    private enum ForceMode {
        NORMAL,
        OPEN,
        CLOSED;

        static ForceMode parse(String raw) {
            String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if ("FORCE_OPEN".equals(normalized)) {
                return OPEN;
            }
            if ("FORCE_CLOSED".equals(normalized)) {
                return CLOSED;
            }
            try {
                return ForceMode.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return NORMAL;
            }
        }

        String label() {
            return switch (this) {
                case NORMAL -> "Normal";
                case OPEN -> "Force Open";
                case CLOSED -> "Force Closed";
            };
        }
    }

    private record CardGrant(UUID cardId, String label, int level, String holderName) {
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("card_id", cardId == null ? "" : cardId.toString());
            tag.putString("label", label == null ? "" : label);
            tag.putInt("level", clampAccess(level));
            tag.putString("holder_name", holderName == null ? "" : holderName);
            return tag;
        }

        static CardGrant fromTag(CompoundTag tag) {
            UUID cardId = parseUuid(tag.getString("card_id"));
            return new CardGrant(
                    cardId,
                    tag.getString("label"),
                    clampAccess(tag.getInt("level")),
                    tag.getString("holder_name")
            );
        }
    }

    private record TargetBinding(String dimensionId,
                                 BlockPos targetPos,
                                 BlockPos relayPos,
                                 Direction face,
                                 String label) {
        RfidScannerOpenPayload.TargetSummary summary() {
            return new RfidScannerOpenPayload.TargetSummary(
                    dimensionId,
                    targetPos.getX(),
                    targetPos.getY(),
                    targetPos.getZ(),
                    face.name(),
                    label
            );
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimensionId == null ? "" : dimensionId);
            tag.putInt("target_x", targetPos.getX());
            tag.putInt("target_y", targetPos.getY());
            tag.putInt("target_z", targetPos.getZ());
            tag.putInt("relay_x", relayPos.getX());
            tag.putInt("relay_y", relayPos.getY());
            tag.putInt("relay_z", relayPos.getZ());
            tag.putString("face", face.name());
            tag.putString("label", label == null ? "" : label);
            return tag;
        }

        static TargetBinding fromTag(CompoundTag tag) {
            String dimensionId = tag.getString("dimension");
            Direction face = parseDirection(tag.getString("face"), Direction.UP);
            return new TargetBinding(
                    dimensionId,
                    new BlockPos(tag.getInt("target_x"), tag.getInt("target_y"), tag.getInt("target_z")),
                    new BlockPos(tag.getInt("relay_x"), tag.getInt("relay_y"), tag.getInt("relay_z")),
                    face,
                    tag.getString("label")
            );
        }
    }
}
