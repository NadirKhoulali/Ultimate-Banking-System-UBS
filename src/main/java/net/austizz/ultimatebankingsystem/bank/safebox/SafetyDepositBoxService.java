package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.BankLevelService;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.menu.SafetyDepositBoxMenu;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SafetyDepositBoxService {
    public static final String AREAS_KEY = "safeDepositAreas";
    public static final String ASSIGNMENTS_KEY = "safeDepositAssignments";
    public static final String ESCROW_KEY = "safeDepositEscrow";
    public static final String NEXT_NUMBER_KEY = "safeDepositNextNumber";
    public static final String POLICY_MODE_KEY = "safeDepositPolicyMode";
    public static final String ONE_TIME_FEE_KEY = "safeDepositOneTimeFee";
    public static final String RENT_AMOUNT_KEY = "safeDepositRentAmount";
    public static final String RENT_PERIOD_TICKS_KEY = "safeDepositRentPeriodTicks";
    public static final String OVERDUE_TICKS_KEY = "safeDepositOverdueTicks";

    private static final long OPEN_MENU_TIMEOUT_TICKS = 20L * 60L;
    private static final long OPEN_MENU_DELAY_TICKS = SafetyDepositBoxRowBlockEntity.OPEN_ANIMATION_TICKS + 2L;
    private static final int SAFE_CLAIM_TOOL_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final String SAFE_CLAIM_TOOL_ITEM_TAG = "ubs_safe_claim_tool_item";
    private static final String SAFE_CLAIM_TOOL_ADD = "add";
    private static final String SAFE_CLAIM_TOOL_REMOVE = "remove";
    private static final String SAFE_CLAIM_TOOL_APPLY = "apply";
    private static final String SAFE_CLAIM_TOOL_CLEAR = "clear";
    private static final String SAFE_CLAIM_TOOL_OVERLAY = "overlay";
    private static final String SAFE_CLAIM_TOOL_LOCK = "lock";
    private static final String SAFE_CLAIM_TOOL_FINISH = "finish";
    private static final ConcurrentHashMap<UUID, PendingMenuOpen> PENDING_MENU_OPENS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SafeClaimToolSession> SAFE_CLAIM_TOOL_SESSIONS = new ConcurrentHashMap<>();

    private SafetyDepositBoxService() {
    }

    public static void openDoorForPlayer(ServerPlayer player, SafetyDepositBoxRowBlockEntity row, int doorIndex) {
        if (player == null || row == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        syncAssignmentsToRow(centralBank, row);
        OpenBoxContext context = resolveOpenBoxContext(centralBank, player, row, doorIndex, true);
        if (context == null) {
            return;
        }

        ServerLevel rowLevel = row.getLevel() instanceof ServerLevel serverLevel ? serverLevel : player.serverLevel();
        String dimension = rowLevel.dimension().location().toString();
        BlockPos rowPos = row.getBlockPos().immutable();
        PendingMenuOpen existing = PENDING_MENU_OPENS.get(player.getUUID());
        if (existing != null
                && context.account().getAccountUUID().equals(existing.accountId())
                && dimension.equals(existing.dimension())
                && rowPos.equals(existing.rowPos())
                && context.doorIndex() == existing.doorIndex()) {
            return;
        }

        long now = rowLevel.getGameTime();
        row.openDoor(context.doorIndex(), now + OPEN_MENU_TIMEOUT_TICKS);
        float currentProgress = Math.max(0.0F, Math.min(1.0F, row.getCurrentDoorProgress(context.doorIndex())));
        long remainingAnimationTicks = Math.max(2L, (long) Math.ceil((1.0F - currentProgress) * OPEN_MENU_DELAY_TICKS));
        PENDING_MENU_OPENS.put(player.getUUID(), new PendingMenuOpen(
                player.getUUID(),
                context.account().getAccountUUID(),
                dimension,
                rowPos,
                context.doorIndex(),
                context.displayBoxNumber(),
                now + remainingAnimationTicks,
                now + OPEN_MENU_TIMEOUT_TICKS
        ));
        player.displayClientMessage(Component.literal("Opening " + context.displayBoxNumber() + "..."), true);
    }

    private static OpenBoxContext resolveOpenBoxContext(CentralBank centralBank,
                                                        ServerPlayer player,
                                                        SafetyDepositBoxRowBlockEntity row,
                                                        int doorIndex,
                                                        boolean notifyPlayer) {
        if (centralBank == null) {
            if (notifyPlayer && player != null) {
                player.sendSystemMessage(Component.literal("Bank data is unavailable."));
            }
            return null;
        }
        if (row == null || player == null) {
            return null;
        }

        int clickedRow = Math.max(0, Math.min(SafetyDepositBoxRowBlockEntity.DOOR_COUNT - 1, doorIndex));
        int normalizedDoorIndex = row.getModuleStartForRow(clickedRow);
        if (normalizedDoorIndex < 0) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("This safety deposit shell row is empty."));
            }
            return null;
        }
        SafetyDepositBoxRowBlockEntity.ModuleType moduleType = row.getModuleType(normalizedDoorIndex);
        if (!moduleType.assignable()) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("This shell row is covered and does not contain a safety deposit box."));
            }
            return null;
        }
        Assignment assignment = findAssignment(centralBank, row, normalizedDoorIndex);
        UUID accountId = assignment == null ? row.getAssignedAccountId(normalizedDoorIndex) : assignment.accountId();
        String boxNumber = assignment == null ? row.getBoxNumber(normalizedDoorIndex) : assignment.boxNumber();
        if (accountId == null) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("This safety deposit box is not assigned."));
            }
            return null;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("This safety deposit box is assigned to a missing account."));
            }
            return null;
        }
        if (!canOpenAccountBox(account, player.getUUID())) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("You do not have access to this safety deposit box."));
            }
            return null;
        }
        if (assignment != null && assignment.locked()) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("This safety deposit box is locked for overdue rent."));
            }
            return null;
        }

        String displayBoxNumber = boxNumber == null || boxNumber.isBlank() ? "Safety Box" : boxNumber;
        int slotCount = Math.max(1, Math.min(54, moduleType.inventorySlots()));
        return new OpenBoxContext(account, normalizedDoorIndex, displayBoxNumber, slotCount);
    }

    private static void openSafetyDepositBoxMenu(ServerPlayer player,
                                                 SafetyDepositBoxRowBlockEntity row,
                                                 OpenBoxContext context) {
        AccountHolder account = context.account();
        int slotCount = Math.max(1, Math.min(54, context.slotCount()));
        String displayBoxNumber = context.displayBoxNumber();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new SafetyDepositBoxMenu(
                                containerId,
                                inventory,
                                account.getAccountUUID(),
                                row.getBlockPos(),
                                context.doorIndex(),
                                slotCount,
                                displayBoxNumber
                        ),
                        Component.literal(displayBoxNumber)
                ),
                buffer -> {
                    buffer.writeLong(account.getAccountUUID().getMostSignificantBits());
                    buffer.writeLong(account.getAccountUUID().getLeastSignificantBits());
                    buffer.writeBlockPos(row.getBlockPos());
                    buffer.writeVarInt(context.doorIndex());
                    buffer.writeVarInt(slotCount);
                    buffer.writeUtf(displayBoxNumber, 64);
                }
        );
    }

    public static ActionResult startSafeAreaClaimToolSession(MinecraftServer server,
                                                             CentralBank centralBank,
                                                             ServerPlayer player,
                                                             UUID bankId) {
        if (server == null || centralBank == null || player == null || bankId == null) {
            return ActionResult.fail("Safe-area claim tool failed: missing bank data.");
        }
        if (!canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail("Only bank owners, directors, and server operators can claim safe areas.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Safe-area claim tool failed: bank no longer exists.");
        }
        if (SAFE_CLAIM_TOOL_SESSIONS.containsKey(player.getUUID()) || ShopService.hasAnyClaimToolSession(player.getUUID())) {
            return ActionResult.fail("A claim tool is already active. Use the barrier to finish first.");
        }

        long gameTime = player.serverLevel().getGameTime();
        List<ItemStack> snapshot = snapshotHotbar(player);
        int selected = Math.max(0, Math.min(8, player.getInventory().selected));
        installSafeClaimToolHotbar(player);
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), new SafeClaimToolSession(
                player.getUUID(),
                bankId,
                true,
                true,
                gameTime,
                gameTime,
                "",
                null,
                null,
                snapshot,
                selected
        ));
        return ActionResult.ok("Safe-area claim tool enabled for " + bank.getBankName()
                + ". Left-click with stick for Pos1, right-click with stick for Pos2, paper to apply, sponge to clear, eye to toggle overlay, barrier to finish.");
    }

    public static boolean hasSafeAreaClaimToolSession(UUID playerId) {
        return playerId != null && SAFE_CLAIM_TOOL_SESSIONS.containsKey(playerId);
    }

    public static boolean isSafeAreaClaimToolStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String marker = ItemStackDataCompat.getCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, stack);
        if ("wand".equals(marker)) {
            return true;
        }
        return SAFE_CLAIM_TOOL_ADD.equals(marker)
                || SAFE_CLAIM_TOOL_REMOVE.equals(marker)
                || SAFE_CLAIM_TOOL_APPLY.equals(marker)
                || SAFE_CLAIM_TOOL_CLEAR.equals(marker)
                || SAFE_CLAIM_TOOL_OVERLAY.equals(marker)
                || SAFE_CLAIM_TOOL_LOCK.equals(marker)
                || SAFE_CLAIM_TOOL_FINISH.equals(marker);
    }

    public static String safeAreaClaimToolMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return ItemStackDataCompat.getCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, stack);
    }

    public static ActionResult setSafeAreaClaimToolMode(ServerPlayer player, boolean addMode) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                addMode,
                session.overlayEnabled(),
                session.firstDimensionId(),
                session.firstCorner(),
                session.secondCorner()
        ));
        return ActionResult.ok("Mode set to " + (addMode ? "Add Safe Area" : "Remove Safe Area")
                + ". Select corners with the stick, then use paper to apply.");
    }

    public static ActionResult toggleSafeAreaClaimOverlay(ServerPlayer player) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        boolean overlayEnabled = !session.overlayEnabled();
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                overlayEnabled,
                session.firstDimensionId(),
                session.firstCorner(),
                session.secondCorner()
        ));
        return ActionResult.ok("Safe-area overlay " + (overlayEnabled ? "enabled" : "disabled") + ".");
    }

    public static ActionResult setSafeAreaClaimToolFirstCorner(ServerPlayer player, BlockPos clickedPos) {
        if (player == null || clickedPos == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        String dim = normalizedDimension(player.serverLevel().dimension().location().toString());
        BlockPos second = session.secondCorner() != null
                && dim.equals(normalizedDimension(session.firstDimensionId()))
                ? session.secondCorner().immutable()
                : null;
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                dim,
                clickedPos.immutable(),
                second
        ));
        return ActionResult.ok("Pos1 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                + ". Right-click with stick to set Pos2.");
    }

    public static ActionResult setSafeAreaClaimToolSecondCorner(ServerPlayer player, BlockPos clickedPos) {
        if (player == null || clickedPos == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        String dim = normalizedDimension(player.serverLevel().dimension().location().toString());
        BlockPos first = session.firstCorner();
        if (!dim.equals(normalizedDimension(session.firstDimensionId()))) {
            first = null;
        }
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                dim,
                first == null ? null : first.immutable(),
                clickedPos.immutable()
        ));
        if (first == null) {
            return ActionResult.ok("Pos2 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                    + ". Left-click with stick to set Pos1.");
        }
        return ActionResult.ok("Pos2 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                + ". Use paper to apply " + (session.addMode() ? "Add" : "Remove") + " safe area.");
    }

    public static ActionResult clearSafeAreaClaimToolSelection(ServerPlayer player) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                "",
                null,
                null
        ));
        return ActionResult.ok("Safe-area selection cleared.");
    }

    public static ActionResult applySafeAreaClaimToolSelection(MinecraftServer server,
                                                               CentralBank centralBank,
                                                               ServerPlayer player) {
        if (server == null || centralBank == null || player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        if (session.firstCorner() == null || session.secondCorner() == null
                || session.firstDimensionId() == null || session.firstDimensionId().isBlank()) {
            return ActionResult.fail("Select Pos1 and Pos2 first, then apply.");
        }
        String currentDim = normalizedDimension(player.serverLevel().dimension().location().toString());
        String selectedDim = normalizedDimension(session.firstDimensionId());
        if (!selectedDim.equals(currentDim)) {
            return ActionResult.fail("Return to " + selectedDim + " to apply this safe-area selection.");
        }

        ActionResult result = session.addMode()
                ? claimSafeArea(centralBank, player, session.bankId(), selectedDim, session.firstCorner(), session.secondCorner())
                : removeSafeArea(centralBank, player, session.bankId(), selectedDim, session.firstCorner(), session.secondCorner());
        SAFE_CLAIM_TOOL_SESSIONS.put(player.getUUID(), copySafeClaimSession(
                player,
                session,
                session.addMode(),
                session.overlayEnabled(),
                result.success() ? "" : selectedDim,
                result.success() ? null : session.firstCorner().immutable(),
                result.success() ? null : session.secondCorner().immutable()
        ));
        return result.success()
                ? ActionResult.ok(result.message() + " Selection cleared for next region.")
                : result;
    }

    public static ActionResult finishSafeAreaClaimToolSession(ServerPlayer player, String reason) {
        if (player == null) {
            return ActionResult.fail("Safe-area claim tool is unavailable.");
        }
        SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.remove(player.getUUID());
        if (session == null) {
            return ActionResult.fail("No safe-area claim tool session is active.");
        }
        restoreHotbar(player, session.hotbarSnapshot(), session.selectedHotbarSlot());
        return ActionResult.ok(reason == null || reason.isBlank() ? "Safe-area claim tool closed." : reason);
    }

    public static void closeSafeAreaClaimToolSession(ServerPlayer player, String reason) {
        if (player == null || !hasSafeAreaClaimToolSession(player.getUUID())) {
            return;
        }
        finishSafeAreaClaimToolSession(player, reason);
    }

    public static void tickSessions(MinecraftServer server) {
        if (server == null) {
            return;
        }
        tickPendingMenuOpens(server);
        if (SAFE_CLAIM_TOOL_SESSIONS.isEmpty()) {
            return;
        }
        for (UUID playerId : new ArrayList<>(SAFE_CLAIM_TOOL_SESSIONS.keySet())) {
            SafeClaimToolSession session = SAFE_CLAIM_TOOL_SESSIONS.get(playerId);
            if (session == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                SAFE_CLAIM_TOOL_SESSIONS.remove(playerId);
                continue;
            }
            long now = player.serverLevel().getGameTime();
            purgeSafeClaimToolItemsOutsideHotbar(player);
            boolean validToolHotbar = true;
            for (int i = 0; i < 9; i++) {
                if (!isSafeAreaClaimToolStack(player.getInventory().getItem(i))) {
                    validToolHotbar = false;
                    break;
                }
            }
            if (!validToolHotbar) {
                installSafeClaimToolHotbar(player);
            }
            if (session.overlayEnabled()) {
                renderSafeAreaClaimOverlay(server, player, session);
            }
            if ((now % 20L) == 0L) {
                sendSafeAreaClaimToolStatusActionBar(player, session);
            }
            if ((now - session.lastUpdatedTick()) > SAFE_CLAIM_TOOL_TIMEOUT_TICKS) {
                finishSafeAreaClaimToolSession(player, "Safe-area claim tool timed out and your hotbar was restored.");
                player.sendSystemMessage(UbsTranslations.literal("§eSafe-area claim tool timed out."));
            }
        }
    }

    private static void tickPendingMenuOpens(MinecraftServer server) {
        if (PENDING_MENU_OPENS.isEmpty()) {
            return;
        }
        for (UUID playerId : new ArrayList<>(PENDING_MENU_OPENS.keySet())) {
            PendingMenuOpen pending = PENDING_MENU_OPENS.get(playerId);
            if (pending == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                PENDING_MENU_OPENS.remove(playerId);
                continue;
            }

            ServerLevel level = levelForDimension(server, pending.dimension());
            long now = level == null ? player.serverLevel().getGameTime() : level.getGameTime();
            if (now > pending.expiresAtGameTime()) {
                PENDING_MENU_OPENS.remove(playerId);
                continue;
            }
            if (now < pending.openAtGameTime()) {
                continue;
            }

            PENDING_MENU_OPENS.remove(playerId);
            if (player.containerMenu != player.inventoryMenu) {
                continue;
            }
            if (level == null || !(level.getBlockEntity(pending.rowPos()) instanceof SafetyDepositBoxRowBlockEntity row)) {
                player.sendSystemMessage(Component.literal("Safety deposit box is no longer available."));
                continue;
            }

            CentralBank centralBank = BankManager.getCentralBank(server);
            OpenBoxContext context = resolveOpenBoxContext(centralBank, player, row, pending.doorIndex(), true);
            if (context == null) {
                continue;
            }
            if (!context.account().getAccountUUID().equals(pending.accountId())) {
                player.sendSystemMessage(Component.literal("This safety deposit box assignment changed."));
                continue;
            }
            openSafetyDepositBoxMenu(player, row, context);
        }
    }

    public static ActionResult claimSafeArea(CentralBank centralBank,
                                             ServerPlayer player,
                                             UUID bankId,
                                             String dimension,
                                             BlockPos min,
                                             BlockPos max) {
        if (centralBank == null || bankId == null || min == null || max == null) {
            return ActionResult.fail("Safe-area claim failed: invalid bounds.");
        }
        if (player != null && !canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail("Only bank owners, directors, and server operators can claim safe areas.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Safe-area claim failed: bank no longer exists.");
        }
        int rowCapacity = safeBoxRowCapacity(bank);
        int claimedRows = countClaimedRows(centralBank, bankId);
        int newVolume = blockVolume(min, max);
        if (claimedRows + Math.max(1, newVolume / 8) > rowCapacity) {
            return ActionResult.fail("Safe-area claim exceeds this bank's tier capacity (" + rowCapacity + " row units).");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        String normalizedDimension = normalizedDimension(dimension);
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());
        for (Tag tag : areas) {
            if (!(tag instanceof CompoundTag existing)) {
                continue;
            }
            if (!normalizedDimension(existing.getString("dimension")).equals(normalizedDimension)) {
                continue;
            }
            if (regionsOverlap(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    existing.getInt("minX"), existing.getInt("minY"), existing.getInt("minZ"),
                    existing.getInt("maxX"), existing.getInt("maxY"), existing.getInt("maxZ")
            )) {
                return ActionResult.fail("New safe area overlaps an existing safe area. Add adjacent regions instead.");
            }
        }
        CompoundTag area = buildSafeAreaTag(normalizedDimension, minX, minY, minZ, maxX, maxY, maxZ);
        area.putLong("claimedAtMillis", System.currentTimeMillis());
        areas.add(area);
        metadata.put(AREAS_KEY, areas);
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
        return ActionResult.ok("Safe area claimed for " + bank.getBankName() + ".");
    }

    public static ActionResult removeSafeArea(CentralBank centralBank,
                                              ServerPlayer player,
                                              UUID bankId,
                                              String dimension,
                                              BlockPos min,
                                              BlockPos max) {
        if (centralBank == null || bankId == null || min == null || max == null) {
            return ActionResult.fail("Safe-area remove failed: invalid bounds.");
        }
        if (player != null && !canManageSafeArea(centralBank, player, bankId)) {
            return ActionResult.fail("Only bank owners, directors, and server operators can remove safe areas.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return ActionResult.fail("Safe-area remove failed: bank no longer exists.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        if (areas.isEmpty()) {
            return ActionResult.fail("No safe areas are claimed for this bank.");
        }
        String normalizedDimension = normalizedDimension(dimension);
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());

        List<CompoundTag> next = new ArrayList<>();
        boolean changed = false;
        for (Tag tag : areas) {
            if (!(tag instanceof CompoundTag existing)) {
                continue;
            }
            if (!normalizedDimension(existing.getString("dimension")).equals(normalizedDimension)) {
                next.add(existing.copy());
                continue;
            }
            if (!regionsOverlap(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    existing.getInt("minX"), existing.getInt("minY"), existing.getInt("minZ"),
                    existing.getInt("maxX"), existing.getInt("maxY"), existing.getInt("maxZ")
            )) {
                next.add(existing.copy());
                continue;
            }
            changed = true;
            next.addAll(subtractSafeArea(existing, minX, minY, minZ, maxX, maxY, maxZ));
        }
        if (!changed) {
            return ActionResult.fail("No claimed safe area intersects that selection.");
        }
        ListTag replaced = new ListTag();
        for (CompoundTag area : next) {
            replaced.add(area);
        }
        metadata.put(AREAS_KEY, replaced);
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
        return ActionResult.ok("Safe area removed for " + bank.getBankName() + ".");
    }

    public static ActionResult assignFirstFreeBox(MinecraftServer server,
                                                  CentralBank centralBank,
                                                  UUID bankId,
                                                  UUID accountId) {
        if (server == null || centralBank == null || bankId == null || accountId == null) {
            return ActionResult.fail("Safety box assignment failed: missing data.");
        }
        Bank bank = centralBank.getBank(bankId);
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (bank == null || account == null || !bankId.equals(account.getBankId())) {
            return ActionResult.fail("Safety box assignment failed: account is not in this bank.");
        }
        Assignment existing = findAssignmentForAccount(centralBank, accountId);
        if (existing != null) {
            return ActionResult.fail("This account already has safety box " + existing.boxNumber() + ".");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        if (areas.isEmpty()) {
            return ActionResult.fail("No safe area is claimed for this bank.");
        }

        for (int i = 0; i < areas.size(); i++) {
            CompoundTag area = areas.getCompound(i);
            ServerLevel level = levelForDimension(server, area.getString("dimension"));
            if (level == null) {
                continue;
            }
            for (int y = area.getInt("minY"); y <= area.getInt("maxY"); y++) {
                for (int z = area.getInt("minZ"); z <= area.getInt("maxZ"); z++) {
                    for (int x = area.getInt("minX"); x <= area.getInt("maxX"); x++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.hasChunkAt(pos)) {
                            continue;
                        }
                        if (!(level.getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row)) {
                            continue;
                        }
                        for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
                            if (!row.isAssignableBoxStart(door)
                                    || row.isAssigned(door)
                                    || findAssignmentAt(centralBank, level, pos, door) != null) {
                                continue;
                            }
                            String boxNumber = nextBoxNumber(metadata);
                            row.assignDoor(door, accountId, boxNumber);
                            addAssignment(metadata, account, level, pos, door, boxNumber);
                            centralBank.putBankMetadata(bankId, metadata);
                            return ActionResult.ok("Assigned safety box " + boxNumber + " to account " + shortId(accountId) + ".");
                        }
                    }
                }
            }
        }
        return ActionResult.fail("No free safety deposit boxes were found in loaded safe areas.");
    }

    public static ActionResult setPricingPolicy(CentralBank centralBank,
                                                UUID bankId,
                                                String modeRaw,
                                                String amountRaw,
                                                String periodTicksRaw,
                                                String overdueTicksRaw) {
        if (centralBank == null || bankId == null) {
            return ActionResult.fail("Pricing update failed: missing bank data.");
        }
        String mode = modeRaw == null ? "FREE" : modeRaw.trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("FREE") && !mode.equals("ONE_TIME") && !mode.equals("RECURRING")) {
            return ActionResult.fail("Pricing mode must be FREE, ONE_TIME, or RECURRING.");
        }
        BigDecimal amount = parseMoney(amountRaw);
        if ((mode.equals("ONE_TIME") || mode.equals("RECURRING")) && amount.compareTo(BigDecimal.ZERO) < 0) {
            return ActionResult.fail("Pricing amount must be non-negative.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        metadata.putString(POLICY_MODE_KEY, mode);
        metadata.putString(ONE_TIME_FEE_KEY, mode.equals("ONE_TIME") ? amount.toPlainString() : metadata.getString(ONE_TIME_FEE_KEY));
        metadata.putString(RENT_AMOUNT_KEY, mode.equals("RECURRING") ? amount.toPlainString() : metadata.getString(RENT_AMOUNT_KEY));
        metadata.putLong(RENT_PERIOD_TICKS_KEY, parsePositiveLong(periodTicksRaw, 7L * 24L * 60L * 60L * 20L));
        metadata.putLong(OVERDUE_TICKS_KEY, parsePositiveLong(overdueTicksRaw, 3L * 24L * 60L * 60L * 20L));
        centralBank.putBankMetadata(bankId, metadata);
        return ActionResult.ok("Safety deposit box pricing updated to " + mode + ".");
    }

    public static ActionResult seizeOverdueBox(CentralBank centralBank, UUID bankId, UUID accountId) {
        if (centralBank == null || bankId == null || accountId == null) {
            return ActionResult.fail("Seizure failed: missing data.");
        }
        Assignment assignment = findAssignmentForAccount(centralBank, accountId);
        if (assignment == null || !bankId.equals(assignment.bankId())) {
            return ActionResult.fail("No safety box assignment found for that account.");
        }
        if (!assignment.locked()) {
            return ActionResult.fail("Only locked overdue safety boxes can be seized.");
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            return ActionResult.fail("Account no longer exists.");
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag escrow = metadata.getList(ESCROW_KEY, Tag.TAG_COMPOUND);
        CompoundTag seized = new CompoundTag();
        seized.putUUID("accountId", accountId);
        seized.putString("boxNumber", assignment.boxNumber());
        seized.putLong("seizedAtMillis", System.currentTimeMillis());
        ListTag contents = new ListTag();
        account.getSafeBoxSlots().forEach((slot, stackTag) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot);
            entry.put("stack", stackTag.copy());
            contents.add(entry);
        });
        seized.put("contents", contents);
        escrow.add(seized);
        metadata.put(ESCROW_KEY, escrow);
        account.getSafeBoxSlots().clear();
        removeAssignment(metadata, assignment);
        centralBank.putBankMetadata(bankId, metadata);
        BankManager.markDirty();
        return ActionResult.ok("Seized contents of " + assignment.boxNumber() + " into bank escrow.");
    }

    public static boolean isInsideSafeArea(CentralBank centralBank, Level level, BlockPos pos) {
        return findBankIdForSafeArea(centralBank, level, pos) != null;
    }

    public static UUID findBankIdForSafeArea(CentralBank centralBank, Level level, BlockPos pos) {
        if (centralBank == null || level == null || pos == null) {
            return null;
        }
        String dimension = level.dimension().location().toString();
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < areas.size(); i++) {
                if (contains(areas.getCompound(i), dimension, pos)) {
                    return bankId;
                }
            }
        }
        return null;
    }

    public static boolean canManageSafeArea(CentralBank centralBank, ServerPlayer player, UUID bankId) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(3)) {
            return true;
        }
        return canManageSafeArea(centralBank, player.getUUID(), bankId);
    }

    public static boolean canManageSafeArea(CentralBank centralBank, UUID playerId, UUID bankId) {
        if (centralBank == null || playerId == null || bankId == null) {
            return false;
        }
        if (BankOwnerPcService.isOwner(centralBank, playerId, bankId)) {
            return true;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        String role = valueForUuid(metadata.getString("roles"), playerId);
        if ("DIRECTOR".equalsIgnoreCase(role) || "FOUNDER".equalsIgnoreCase(role)) {
            return true;
        }
        String employeeRole = employeeRoleForUuid(metadata.getString("employees"), playerId);
        if ("DIRECTOR".equalsIgnoreCase(employeeRole)) {
            return true;
        }
        if (uuidListContains(metadata.getString("cofounders"), playerId)) {
            return true;
        }
        BigDecimal share = shareForUuid(metadata.getString("shares"), playerId);
        return share.compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean canOpenAccountBox(AccountHolder account, UUID playerId) {
        return account != null && playerId != null
                && (playerId.equals(account.getPlayerUUID()) || account.canManage(playerId) || account.canWithdraw(playerId));
    }

    public static void syncAssignmentsToRow(SafetyDepositBoxRowBlockEntity row) {
        if (row == null || !(row.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(serverLevel.getServer());
        syncAssignmentsToRow(centralBank, row);
    }

    private static void syncAssignmentsToRow(CentralBank centralBank, SafetyDepositBoxRowBlockEntity row) {
        if (centralBank == null || row == null) {
            return;
        }
        for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
            if (!row.isAssignableBoxStart(door)) {
                continue;
            }
            Assignment assignment = findAssignment(centralBank, row, door);
            if (assignment == null) {
                continue;
            }
            if (!Objects.equals(row.getAssignedAccountId(door), assignment.accountId())
                    || !Objects.equals(row.getBoxNumber(door), assignment.boxNumber())) {
                row.assignDoor(door, assignment.accountId(), assignment.boxNumber());
            }
        }
    }

    private static Assignment findAssignment(CentralBank centralBank, SafetyDepositBoxRowBlockEntity row, int doorIndex) {
        Level level = row.getLevel();
        return level == null ? null : findAssignmentAt(centralBank, level, row.getBlockPos(), doorIndex);
    }

    private static Assignment findAssignmentAt(CentralBank centralBank, Level level, BlockPos pos, int doorIndex) {
        if (centralBank == null || level == null || pos == null) {
            return null;
        }
        String dimension = level.dimension().location().toString();
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag assignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < assignments.size(); i++) {
                Assignment assignment = readAssignment(assignments.getCompound(i));
                if (assignment != null
                        && dimension.equals(assignment.dimension())
                        && pos.equals(assignment.pos())
                        && doorIndex == assignment.doorIndex()) {
                    return assignment;
                }
            }
        }
        return null;
    }

    private static Assignment findAssignmentForAccount(CentralBank centralBank, UUID accountId) {
        if (centralBank == null || accountId == null) {
            return null;
        }
        for (UUID bankId : centralBank.getBankMetadata().keySet()) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
            ListTag assignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < assignments.size(); i++) {
                Assignment assignment = readAssignment(assignments.getCompound(i));
                if (assignment != null && accountId.equals(assignment.accountId())) {
                    return assignment;
                }
            }
        }
        return null;
    }

    private static void addAssignment(CompoundTag metadata,
                                      AccountHolder account,
                                      Level level,
                                      BlockPos pos,
                                      int doorIndex,
                                      String boxNumber) {
        ListTag assignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("accountId", account.getAccountUUID());
        tag.putUUID("bankId", account.getBankId());
        tag.putString("dimension", level.dimension().location().toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putInt("doorIndex", doorIndex);
        tag.putString("boxNumber", boxNumber == null ? "" : boxNumber);
        tag.putBoolean("locked", false);
        tag.putLong("assignedAtMillis", System.currentTimeMillis());
        assignments.add(tag);
        metadata.put(ASSIGNMENTS_KEY, assignments);
    }

    private static void removeAssignment(CompoundTag metadata, Assignment assignment) {
        ListTag oldAssignments = metadata.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        ListTag nextAssignments = new ListTag();
        for (int i = 0; i < oldAssignments.size(); i++) {
            Assignment current = readAssignment(oldAssignments.getCompound(i));
            if (current == null || !current.matches(assignment)) {
                nextAssignments.add(oldAssignments.getCompound(i));
            }
        }
        metadata.put(ASSIGNMENTS_KEY, nextAssignments);
    }

    private static Assignment readAssignment(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("accountId") || !tag.hasUUID("bankId")) {
            return null;
        }
        return new Assignment(
                tag.getUUID("bankId"),
                tag.getUUID("accountId"),
                tag.getString("dimension"),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getInt("doorIndex"),
                tag.getString("boxNumber"),
                tag.getBoolean("locked")
        );
    }

    private static String nextBoxNumber(CompoundTag metadata) {
        int next = Math.max(1, metadata.getInt(NEXT_NUMBER_KEY));
        metadata.putInt(NEXT_NUMBER_KEY, next + 1);
        return "SDB-" + String.format(Locale.ROOT, "%04d", next);
    }

    private static ServerLevel levelForDimension(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static boolean contains(CompoundTag area, String dimension, BlockPos pos) {
        return area != null
                && normalizedDimension(dimension).equals(normalizedDimension(area.getString("dimension")))
                && pos.getX() >= area.getInt("minX")
                && pos.getX() <= area.getInt("maxX")
                && pos.getY() >= area.getInt("minY")
                && pos.getY() <= area.getInt("maxY")
                && pos.getZ() >= area.getInt("minZ")
                && pos.getZ() <= area.getInt("maxZ");
    }

    public static int safeBoxRowCapacity(Bank bank) {
        if (bank == null) {
            return 0;
        }
        MinecraftServer server = bank.getBankAccounts() == null ? null : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        CentralBank centralBank = server == null || BankManager.isDirtySuspended() ? null : BankManager.getCentralBank(server);
        return centralBank == null
                ? BankLevelService.safeRowCapacityForLevel(1)
                : BankLevelService.safeRowCapacity(centralBank, bank);
    }

    public static int countClaimedRows(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return 0;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        ListTag areas = metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND);
        int claimed = 0;
        for (int i = 0; i < areas.size(); i++) {
            claimed += claimedRowUnits(areas.getCompound(i));
        }
        return claimed;
    }

    public static int claimedRowUnits(CompoundTag area) {
        if (area == null) {
            return 0;
        }
        return Math.max(1, blockVolume(
                new BlockPos(area.getInt("minX"), area.getInt("minY"), area.getInt("minZ")),
                new BlockPos(area.getInt("maxX"), area.getInt("maxY"), area.getInt("maxZ"))
        ) / 8);
    }

    private static int blockVolume(BlockPos min, BlockPos max) {
        int width = Math.abs(max.getX() - min.getX()) + 1;
        int height = Math.abs(max.getY() - min.getY()) + 1;
        int depth = Math.abs(max.getZ() - min.getZ()) + 1;
        return Math.max(1, width * height * depth);
    }

    private static SafeClaimToolSession copySafeClaimSession(ServerPlayer player,
                                                             SafeClaimToolSession session,
                                                             boolean addMode,
                                                             boolean overlayEnabled,
                                                             String firstDimensionId,
                                                             BlockPos firstCorner,
                                                             BlockPos secondCorner) {
        long now = player == null || player.level() == null ? session.lastUpdatedTick() : player.serverLevel().getGameTime();
        return new SafeClaimToolSession(
                session.playerId(),
                session.bankId(),
                addMode,
                overlayEnabled,
                session.startedTick(),
                now,
                firstDimensionId == null ? "" : firstDimensionId,
                firstCorner == null ? null : firstCorner.immutable(),
                secondCorner == null ? null : secondCorner.immutable(),
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        );
    }

    private static List<ItemStack> snapshotHotbar(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            out.add(player.getInventory().getItem(i).copy());
        }
        return out;
    }

    private static void restoreHotbar(ServerPlayer player, List<ItemStack> snapshot, int selectedSlot) {
        if (player == null || snapshot == null || snapshot.size() < 9) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, snapshot.get(i).copy());
        }
        player.getInventory().selected = Math.max(0, Math.min(8, selectedSlot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void purgeSafeClaimToolItemsOutsideHotbar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean changed = false;
        int size = player.getInventory().getContainerSize();
        for (int slot = 9; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isSafeAreaClaimToolStack(stack)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void installSafeClaimToolHotbar(ServerPlayer player) {
        ItemStack wand = new ItemStack(Items.STICK);
        ItemStackDataCompat.setCustomName(wand, UbsTranslations.literal("Safe Area Claim Wand").withStyle(ChatFormatting.GOLD));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, wand, "wand");

        ItemStack add = new ItemStack(Items.LIME_CONCRETE);
        ItemStackDataCompat.setCustomName(add, UbsTranslations.literal("Add Safe Area").withStyle(ChatFormatting.GREEN));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, add, SAFE_CLAIM_TOOL_ADD);

        ItemStack remove = new ItemStack(Items.RED_CONCRETE);
        ItemStackDataCompat.setCustomName(remove, UbsTranslations.literal("Remove Safe Area").withStyle(ChatFormatting.RED));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, remove, SAFE_CLAIM_TOOL_REMOVE);

        ItemStack apply = new ItemStack(Items.PAPER);
        ItemStackDataCompat.setCustomName(apply, UbsTranslations.literal("Apply Selection").withStyle(ChatFormatting.YELLOW));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, apply, SAFE_CLAIM_TOOL_APPLY);

        ItemStack clear = new ItemStack(Items.SPONGE);
        ItemStackDataCompat.setCustomName(clear, UbsTranslations.literal("Clear Selection").withStyle(ChatFormatting.GOLD));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, clear, SAFE_CLAIM_TOOL_CLEAR);

        ItemStack overlay = new ItemStack(Items.ENDER_EYE);
        ItemStackDataCompat.setCustomName(overlay, UbsTranslations.literal("Toggle Safe Overlay").withStyle(ChatFormatting.AQUA));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, overlay, SAFE_CLAIM_TOOL_OVERLAY);

        ItemStack locked = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        ItemStackDataCompat.setCustomName(locked, UbsTranslations.literal("Safe Claim Slot (Locked)").withStyle(ChatFormatting.DARK_GRAY));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, locked, SAFE_CLAIM_TOOL_LOCK);

        ItemStack finish = new ItemStack(Items.BARRIER);
        ItemStackDataCompat.setCustomName(finish, UbsTranslations.literal("Finish Safe Claim Tool").withStyle(ChatFormatting.YELLOW));
        ItemStackDataCompat.putCustomString(SAFE_CLAIM_TOOL_ITEM_TAG, finish, SAFE_CLAIM_TOOL_FINISH);

        player.getInventory().setItem(0, wand.copy());
        player.getInventory().setItem(1, add.copy());
        player.getInventory().setItem(2, remove.copy());
        player.getInventory().setItem(3, apply.copy());
        player.getInventory().setItem(4, clear.copy());
        player.getInventory().setItem(5, overlay.copy());
        player.getInventory().setItem(6, locked.copy());
        player.getInventory().setItem(7, locked.copy());
        player.getInventory().setItem(8, finish.copy());
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void renderSafeAreaClaimOverlay(MinecraftServer server, ServerPlayer player, SafeClaimToolSession session) {
        if (server == null || player == null || session == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(session.bankId());
        String playerDim = normalizedDimension(player.serverLevel().dimension().location().toString());
        int[] budget = new int[]{220};
        renderSafeRegionsForPlayer(player, metadata.getList(AREAS_KEY, Tag.TAG_COMPOUND), playerDim, ParticleTypes.HAPPY_VILLAGER, budget);
        if (session.firstCorner() != null && normalizedDimension(session.firstDimensionId()).equals(playerDim)) {
            sendOverlayParticle(
                    player,
                    ParticleTypes.END_ROD,
                    session.firstCorner().getX() + 0.5D,
                    session.firstCorner().getY() + 1.05D,
                    session.firstCorner().getZ() + 0.5D,
                    budget
            );
            if (session.secondCorner() != null) {
                sendOverlayParticle(
                        player,
                        ParticleTypes.GLOW,
                        session.secondCorner().getX() + 0.5D,
                        session.secondCorner().getY() + 1.05D,
                        session.secondCorner().getZ() + 0.5D,
                        budget
                );
                BlockPos first = session.firstCorner();
                BlockPos second = session.secondCorner();
                drawRegionOutlineForPlayer(
                        player,
                        Math.min(first.getX(), second.getX()),
                        Math.min(first.getY(), second.getY()),
                        Math.min(first.getZ(), second.getZ()),
                        Math.max(first.getX(), second.getX()),
                        Math.max(first.getY(), second.getY()),
                        Math.max(first.getZ(), second.getZ()),
                        session.addMode() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.FLAME,
                        budget
                );
            }
        }
    }

    private static void renderSafeRegionsForPlayer(ServerPlayer player,
                                                   ListTag regions,
                                                   String playerDim,
                                                   ParticleOptions particle,
                                                   int[] budget) {
        if (player == null || regions == null || regions.isEmpty() || budget == null || budget.length == 0 || budget[0] <= 0) {
            return;
        }
        BlockPos viewer = player.blockPosition();
        for (Tag tag : regions) {
            if (!(tag instanceof CompoundTag region)) {
                continue;
            }
            if (!normalizedDimension(region.getString("dimension")).equals(playerDim)) {
                continue;
            }
            int minX = Math.min(region.getInt("minX"), region.getInt("maxX"));
            int minY = Math.min(region.getInt("minY"), region.getInt("maxY"));
            int minZ = Math.min(region.getInt("minZ"), region.getInt("maxZ"));
            int maxX = Math.max(region.getInt("minX"), region.getInt("maxX"));
            int maxY = Math.max(region.getInt("minY"), region.getInt("maxY"));
            int maxZ = Math.max(region.getInt("minZ"), region.getInt("maxZ"));
            if (!regionCloseToPlayer(viewer, minX, minY, minZ, maxX, maxY, maxZ, 96)) {
                continue;
            }
            drawRegionOutlineForPlayer(player, minX, minY, minZ, maxX, maxY, maxZ, particle, budget);
            if (budget[0] <= 0) {
                return;
            }
        }
    }

    private static void sendSafeAreaClaimToolStatusActionBar(ServerPlayer player, SafeClaimToolSession session) {
        if (player == null || session == null) {
            return;
        }
        String mode = session.addMode() ? "Add" : "Remove";
        String pos1 = session.firstCorner() == null ? "Pos1: -" : "Pos1: set";
        String pos2 = session.secondCorner() == null ? "Pos2: -" : "Pos2: set";
        String selectionInfo = "";
        if (session.firstCorner() != null && session.secondCorner() != null) {
            selectionInfo = " | Sel: " + blockVolume(session.firstCorner(), session.secondCorner()) + " blocks";
        }
        player.displayClientMessage(
                UbsTranslations.literal("§bSafe Claim §7| §fMode: " + mode + " §7| §f" + pos1 + " §7| §f" + pos2
                        + selectionInfo + " §7| §ePaper=Apply"),
                true
        );
    }

    private static void drawRegionOutlineForPlayer(ServerPlayer player,
                                                   int minX, int minY, int minZ,
                                                   int maxX, int maxY, int maxZ,
                                                   ParticleOptions particle,
                                                   int[] budget) {
        if (player == null || particle == null || budget == null || budget.length == 0 || budget[0] <= 0) {
            return;
        }
        int xSpan = Math.max(1, maxX - minX + 1);
        int ySpan = Math.max(1, maxY - minY + 1);
        int zSpan = Math.max(1, maxZ - minZ + 1);
        int hStep = Math.max(1, Math.max(xSpan, zSpan) / 24);
        int vStep = Math.max(1, ySpan / 12);
        double topY = maxY + 1.02D;

        for (int x = minX; x <= maxX; x += hStep) {
            if (!sendOverlayParticle(player, particle, x + 0.5D, topY, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, x + 0.5D, topY, maxZ + 0.5D, budget)) {
                return;
            }
        }
        for (int z = minZ; z <= maxZ; z += hStep) {
            if (!sendOverlayParticle(player, particle, minX + 0.5D, topY, z + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, topY, z + 0.5D, budget)) {
                return;
            }
        }
        for (int y = minY; y <= maxY; y += vStep) {
            double py = y + 0.08D;
            if (!sendOverlayParticle(player, particle, minX + 0.5D, py, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, minX + 0.5D, py, maxZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, py, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, py, maxZ + 0.5D, budget)) {
                return;
            }
        }
    }

    private static boolean sendOverlayParticle(ServerPlayer player,
                                               ParticleOptions particle,
                                               double x,
                                               double y,
                                               double z,
                                               int[] budget) {
        if (player == null || particle == null || budget == null || budget.length == 0 || budget[0] <= 0) {
            return false;
        }
        player.serverLevel().sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        budget[0]--;
        return budget[0] > 0;
    }

    private static boolean regionCloseToPlayer(BlockPos viewer,
                                               int minX, int minY, int minZ,
                                               int maxX, int maxY, int maxZ,
                                               int margin) {
        if (viewer == null) {
            return false;
        }
        return viewer.getX() >= (minX - margin) && viewer.getX() <= (maxX + margin)
                && viewer.getY() >= (minY - margin) && viewer.getY() <= (maxY + margin)
                && viewer.getZ() >= (minZ - margin) && viewer.getZ() <= (maxZ + margin);
    }

    private static CompoundTag buildSafeAreaTag(String dimension,
                                                int minX,
                                                int minY,
                                                int minZ,
                                                int maxX,
                                                int maxY,
                                                int maxZ) {
        CompoundTag area = new CompoundTag();
        area.putString("dimension", normalizedDimension(dimension));
        area.putInt("minX", Math.min(minX, maxX));
        area.putInt("minY", Math.min(minY, maxY));
        area.putInt("minZ", Math.min(minZ, maxZ));
        area.putInt("maxX", Math.max(minX, maxX));
        area.putInt("maxY", Math.max(minY, maxY));
        area.putInt("maxZ", Math.max(minZ, maxZ));
        area.putLong("claimedAtMillis", System.currentTimeMillis());
        return area;
    }

    private static List<CompoundTag> subtractSafeArea(CompoundTag source,
                                                      int removeMinX,
                                                      int removeMinY,
                                                      int removeMinZ,
                                                      int removeMaxX,
                                                      int removeMaxY,
                                                      int removeMaxZ) {
        List<CompoundTag> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        String dim = normalizedDimension(source.getString("dimension"));
        int ax1 = Math.min(source.getInt("minX"), source.getInt("maxX"));
        int ay1 = Math.min(source.getInt("minY"), source.getInt("maxY"));
        int az1 = Math.min(source.getInt("minZ"), source.getInt("maxZ"));
        int ax2 = Math.max(source.getInt("minX"), source.getInt("maxX"));
        int ay2 = Math.max(source.getInt("minY"), source.getInt("maxY"));
        int az2 = Math.max(source.getInt("minZ"), source.getInt("maxZ"));

        int bx1 = Math.min(removeMinX, removeMaxX);
        int by1 = Math.min(removeMinY, removeMaxY);
        int bz1 = Math.min(removeMinZ, removeMaxZ);
        int bx2 = Math.max(removeMinX, removeMaxX);
        int by2 = Math.max(removeMinY, removeMaxY);
        int bz2 = Math.max(removeMinZ, removeMaxZ);

        if (!regionsOverlap(ax1, ay1, az1, ax2, ay2, az2, bx1, by1, bz1, bx2, by2, bz2)) {
            out.add(buildSafeAreaTag(dim, ax1, ay1, az1, ax2, ay2, az2));
            return out;
        }

        int ox1 = Math.max(ax1, bx1);
        int oy1 = Math.max(ay1, by1);
        int oz1 = Math.max(az1, bz1);
        int ox2 = Math.min(ax2, bx2);
        int oy2 = Math.min(ay2, by2);
        int oz2 = Math.min(az2, bz2);

        addSafeAreaIfValid(out, dim, ax1, ay1, az1, ox1 - 1, ay2, az2);
        addSafeAreaIfValid(out, dim, ox2 + 1, ay1, az1, ax2, ay2, az2);

        int rx1 = Math.max(ax1, ox1);
        int rx2 = Math.min(ax2, ox2);
        addSafeAreaIfValid(out, dim, rx1, ay1, az1, rx2, oy1 - 1, az2);
        addSafeAreaIfValid(out, dim, rx1, oy2 + 1, az1, rx2, ay2, az2);

        int ry1 = Math.max(ay1, oy1);
        int ry2 = Math.min(ay2, oy2);
        addSafeAreaIfValid(out, dim, rx1, ry1, az1, rx2, ry2, oz1 - 1);
        addSafeAreaIfValid(out, dim, rx1, ry1, oz2 + 1, rx2, ry2, az2);

        return out;
    }

    private static void addSafeAreaIfValid(List<CompoundTag> out,
                                           String dimension,
                                           int minX,
                                           int minY,
                                           int minZ,
                                           int maxX,
                                           int maxY,
                                           int maxZ) {
        if (out == null || minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }
        out.add(buildSafeAreaTag(dimension, minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static boolean regionsOverlap(int aMinX, int aMinY, int aMinZ,
                                          int aMaxX, int aMaxY, int aMaxZ,
                                          int bMinX, int bMinY, int bMinZ,
                                          int bMaxX, int bMaxY, int bMaxZ) {
        int ax1 = Math.min(aMinX, aMaxX);
        int ay1 = Math.min(aMinY, aMaxY);
        int az1 = Math.min(aMinZ, aMaxZ);
        int ax2 = Math.max(aMinX, aMaxX);
        int ay2 = Math.max(aMinY, aMaxY);
        int az2 = Math.max(aMinZ, aMaxZ);

        int bx1 = Math.min(bMinX, bMaxX);
        int by1 = Math.min(bMinY, bMaxY);
        int bz1 = Math.min(bMinZ, bMaxZ);
        int bx2 = Math.max(bMinX, bMaxX);
        int by2 = Math.max(bMinY, bMaxY);
        int bz2 = Math.max(bMinZ, bMaxZ);

        return ax1 <= bx2 && bx1 <= ax2
                && ay1 <= by2 && by1 <= ay2
                && az1 <= bz2 && bz1 <= az2;
    }

    private static String normalizedDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return Level.OVERWORLD.location().toString();
        }
        return dimension.trim().toLowerCase(Locale.ROOT);
    }

    private static long parsePositiveLong(String raw, long fallback) {
        try {
            return Math.max(1L, Long.parseLong(raw == null ? "" : raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_EVEN);
        } catch (NumberFormatException ignored) {
            return BigDecimal.valueOf(-1);
        }
    }

    private static String valueForUuid(String encoded, UUID playerId) {
        if (encoded == null || encoded.isBlank() || playerId == null) {
            return "";
        }
        String prefix = playerId + "=";
        for (String entry : encoded.split(";")) {
            String raw = entry.trim();
            if (raw.startsWith(prefix)) {
                return raw.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String employeeRoleForUuid(String encoded, UUID playerId) {
        String value = valueForUuid(encoded, playerId);
        int split = value.indexOf(':');
        return split < 0 ? value : value.substring(0, split);
    }

    private static boolean uuidListContains(String encoded, UUID playerId) {
        if (encoded == null || encoded.isBlank() || playerId == null) {
            return false;
        }
        for (String entry : encoded.split(",")) {
            if (playerId.toString().equals(entry.trim())) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal shareForUuid(String encoded, UUID playerId) {
        String value = valueForUuid(encoded, playerId);
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String shortId(UUID id) {
        return id == null ? "unknown" : id.toString().substring(0, 8);
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message == null ? "" : message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message == null ? "" : message);
        }
    }

    private record Assignment(UUID bankId,
                              UUID accountId,
                              String dimension,
                              BlockPos pos,
                              int doorIndex,
                              String boxNumber,
                              boolean locked) {
        private boolean matches(Assignment other) {
            return other != null
                    && bankId.equals(other.bankId)
                    && accountId.equals(other.accountId)
                    && dimension.equals(other.dimension)
                    && pos.equals(other.pos)
                    && doorIndex == other.doorIndex;
        }
    }

    private record OpenBoxContext(AccountHolder account,
                                  int doorIndex,
                                  String displayBoxNumber,
                                  int slotCount) {
    }

    private record PendingMenuOpen(UUID playerId,
                                   UUID accountId,
                                   String dimension,
                                   BlockPos rowPos,
                                   int doorIndex,
                                   String displayBoxNumber,
                                   long openAtGameTime,
                                   long expiresAtGameTime) {
    }

    private record SafeClaimToolSession(UUID playerId,
                                        UUID bankId,
                                        boolean addMode,
                                        boolean overlayEnabled,
                                        long startedTick,
                                        long lastUpdatedTick,
                                        String firstDimensionId,
                                        BlockPos firstCorner,
                                        BlockPos secondCorner,
                                        List<ItemStack> hotbarSnapshot,
                                        int selectedHotbarSlot) {
    }
}
