package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAccessAuditService;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.custom.CashStackBlock;
import net.austizz.ultimatebankingsystem.block.custom.MetalPalletBlock;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.custom.SafetyDepositBoxRowBlock;
import net.austizz.ultimatebankingsystem.block.custom.SecureSafeBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.RfidScannerBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SecureSafeBlockEntity;
import net.austizz.ultimatebankingsystem.item.HeistDuffelData;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.SafeBoxDisplayContentsPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative hold-action controller for active bank heists. */
public final class HeistInteractionService {
    public static final double ACTION_RANGE = 5.5D;
    public static final int LOOT_TICKS = 25;
    public static final int LOCKPICK_TICKS = 20 * 20;
    public static final int SAW_CONTACT_TICKS = 20;
    public static final int SAW_RELOAD_TICKS = 75;
    public static final int DRILL_PLACE_TICKS = 20;
    public static final int DRILL_RESTART_TICKS = 60;
    public static final int DRILL_RECOVER_TICKS = 20;
    public static final int VAULT_DRILL_DURATION_TICKS = 360 * 20;
    public static final int SAFE_DRILL_DURATION_TICKS = 180 * 20;
    public static final int HACK_START_TICKS = 100;
    public static final int HACK_RESUME_TICKS = 60;
    public static final int RFID_SPOOF_TICKS = 120;
    public static final int SAW_CHARGE_PER_CONTACT = 5;
    private static final Map<MinecraftServer, Map<UUID, HoldState>> HOLDS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<String, Integer>> SAW_CONTACTS = new IdentityHashMap<>();

    private HeistInteractionService() {}

    public record ActionView(boolean heistActive, boolean actionable, String prompt,
                             int elapsedTicks, int requiredTicks) {
        public static ActionView none(boolean heistActive) {
            return new ActionView(heistActive, false, "", 0, 0);
        }
    }

    private enum Action {
        LOOT("Hold {key} to loot", LOOT_TICKS, true),
        LOCKPICK("Hold {key} to pick the lock", LOCKPICK_TICKS, false),
        SAW("Hold {key} to cut the lock", SAW_CONTACT_TICKS, false),
        RELOAD_SAW("Hold {key} to replace the saw blade", SAW_RELOAD_TICKS, false),
        PLACE_DRILL("Hold {key} to place the thermal drill", DRILL_PLACE_TICKS, false),
        RESTART_DRILL("Hold {key} to restart the stalled drill", DRILL_RESTART_TICKS, false),
        RECOVER_DRILL("Hold {key} to recover the thermal drill", DRILL_RECOVER_TICKS, false),
        PLACE_SAFE_DRILL("Hold {key} to place the heist drill", DRILL_PLACE_TICKS, false),
        RESTART_SAFE_DRILL("Hold {key} to restart the stalled heist drill", DRILL_RESTART_TICKS, false),
        RECOVER_SAFE_DRILL("Hold {key} to recover the heist drill", DRILL_RECOVER_TICKS, false),
        SPOOF_RFID("Hold {key} to spoof RFID success outputs", RFID_SPOOF_TICKS, false),
        START_HACK("Hold {key} to initialize the owner-PC hack", HACK_START_TICKS, false),
        RESUME_HACK("Hold {key} to resume the owner-PC hack", HACK_RESUME_TICKS, false);

        final String prompt;
        final int duration;
        final boolean repeats;

        Action(String prompt, int duration, boolean repeats) {
            this.prompt = prompt;
            this.duration = duration;
            this.repeats = repeats;
        }
    }

    private record Target(Action action, String key, BlockHitResult hit, int rowDoor, String prompt) {
        Target(Action action, String key, BlockHitResult hit, int rowDoor) {
            this(action, key, hit, rowDoor, "");
        }

        String displayPrompt() {
            return prompt == null || prompt.isBlank() ? action.prompt : prompt;
        }
    }

    private static final class HoldState {
        boolean held;
        String targetKey = "";
        Action action;
        int elapsed;
        long lastInputTick;
        String prompt = "";

        void resetProgress() {
            targetKey = "";
            action = null;
            elapsed = 0;
            prompt = "";
        }
    }

    public static void setHolding(ServerPlayer player, boolean held) {
        if (player == null || player.getServer() == null) return;
        HoldState state = states(player.getServer()).computeIfAbsent(player.getUUID(), ignored -> new HoldState());
        state.held = held;
        state.lastInputTick = player.getServer().getTickCount();
        if (!held) state.resetProgress();
    }

    public static ActionView view(ServerPlayer player) {
        if (player == null || player.getServer() == null) return ActionView.none(false);
        HeistSession session = HeistService.activeSession(player);
        if (session == null) return ActionView.none(false);
        HoldState hold = states(player.getServer()).get(player.getUUID());
        Target target = resolve(player, session);
        if (target == null) return ActionView.none(true);
        int elapsed = hold != null && target.key.equals(hold.targetKey) ? hold.elapsed : 0;
        return new ActionView(true, true, target.displayPrompt(), elapsed, target.action.duration);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        Map<UUID, HoldState> states = states(server);
        long now = server.getTickCount();
        states.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null
                && now - entry.getValue().lastInputTick > 200L);
        for (Map.Entry<UUID, HoldState> entry : new ArrayList<>(states.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            HoldState hold = entry.getValue();
            if (player == null || !hold.held || now - hold.lastInputTick > 5L) {
                if (hold.held && now - hold.lastInputTick > 5L) hold.held = false;
                hold.resetProgress();
                continue;
            }
            HeistSession session = HeistService.activeSession(player);
            if (session == null || !HeistService.isMasked(player)) {
                hold.resetProgress();
                continue;
            }
            Target target = resolve(player, session);
            if (target == null) {
                hold.resetProgress();
                continue;
            }
            if (hold.action != target.action || !hold.targetKey.equals(target.key)) {
                hold.action = target.action;
                hold.targetKey = target.key;
                hold.elapsed = 0;
                hold.prompt = target.displayPrompt();
            }
            hold.elapsed++;
            if (hold.elapsed < target.action.duration) continue;
            boolean completed = perform(player, session, target);
            hold.elapsed = 0;
            if (!target.action.repeats || !completed) hold.held = false;
            HeistSavedData.get(server).put(session);
        }
    }

    public static void stop(MinecraftServer server) {
        HOLDS.remove(server);
        SAW_CONTACTS.remove(server);
    }

    private static Target resolve(ServerPlayer player, HeistSession session) {
        if (player == null || session == null || !HeistService.isCrew(player.getServer(), player.getUUID(), session)) return null;
        ItemStack held = player.getMainHandItem();
        if (held.is(ModItems.OVE9000_SAW.get()) && sawCharge(held) <= 0 && hasBlade(player)) {
            return new Target(Action.RELOAD_SAW, "reload:" + player.getUUID(), null, -1);
        }
        HitResult raw = player.pick(ACTION_RANGE, 0.0F, false);
        if (!(raw instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (!session.premiseBounds().contains(player.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ())) return null;
        BlockState state = player.level().getBlockState(pos);
        String dimension = player.level().dimension().location().toString();

        if (state.is(ModBlocks.RFID_SCANNER.get())
                && held.is(ModItems.RFID_SPOOFER.get())
                && player.level().getBlockEntity(pos) instanceof RfidScannerBlockEntity scanner
                && scanner.canSpoofSuccess()) {
            return target(Action.SPOOF_RFID, dimension, pos, hit, -1);
        }
        if (state.is(ModBlocks.BANK_OWNER_PC.get())) {
            if (session.isComputerHacked(dimension, pos)) return null;
            HeistSession.HackState hack = session.activeHack(dimension, pos);
            if (hack == null) return target(Action.START_HACK, dimension, pos, hit, -1);
            if (hack.pausedUntilTick() == Long.MAX_VALUE) {
                return target(Action.RESUME_HACK, dimension, pos, hit, -1);
            }
            return null;
        }
        if (state.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            BlockPos master = BankVaultDoorBlock.getMasterPos(state, pos);
            HeistSession.DrillState drill = session.vaultDrill(dimension, master);
            if (drill == null && hasVaultDrillAvailable(player, session)) {
                return new Target(Action.PLACE_DRILL, "vault|" + dimension + "|" + master.asLong(), hit, -1);
            }
            if (drill != null && player.level().getBlockEntity(master) instanceof BankVaultDoorBlockEntity vault
                    && vault.hasHeistDrill(session.id())) {
                if (drill.jammedAtTick() > 0L) return target(Action.RESTART_DRILL, dimension, master, hit, -1);
                if (drill.completed()) return target(Action.RECOVER_DRILL, dimension, master, hit, -1);
            }
            return null;
        }
        if (SecureSafeBlock.isSafeBlock(state)) {
            BlockPos master = SecureSafeBlock.getMasterPos(state, pos);
            if (!(player.level().getBlockEntity(master) instanceof SecureSafeBlockEntity safe)) return null;
            HeistSession.DrillState drill = session.safeDrill(dimension, master);
            if (drill != null && safe.hasHeistDrill(session.id())) {
                if (drill.jammedAtTick() > 0L) return target(Action.RESTART_SAFE_DRILL, dimension, master, hit, -1);
                if (drill.completed()) return target(Action.RECOVER_SAFE_DRILL, dimension, master, hit, -1);
                return null;
            }
            if (safe.isHeistBreached(session.id())) {
                ItemStack bag = activeBag(player, session);
                BlockState masterState = player.level().getBlockState(master);
                Direction safeFacing = masterState.hasProperty(SecureSafeBlock.FACING)
                        ? masterState.getValue(SecureSafeBlock.FACING)
                        : Direction.NORTH;
                int safeSlot = safe.isOpenForStorage() ? safe.resolveShelfSlotFromLook(player, safeFacing) : -1;
                ItemStack loot = safeSlot >= 0 ? safe.getItemHandler().getStackInSlot(safeSlot) : ItemStack.EMPTY;
                if (bag == null || loot.isEmpty() || !fits(bag, loot, player)) return null;
                String prompt = "Hold {key} to steal " + loot.getCount() + "x " + loot.getHoverName().getString();
                String key = "safe-loot|" + dimension + "|" + master.asLong() + "|" + safeSlot;
                return new Target(Action.LOOT, key, hit, safeSlot, prompt);
            }
            if (drill == null && held.is(ModBlocks.HEIST_DRILL.get().asItem())) {
                return target(Action.PLACE_SAFE_DRILL, dimension, master, hit, -1);
            }
            return null;
        }
        if (player.level().getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row) {
            int door = row.getModuleStartForRow(SafetyDepositBoxRowBlock.doorIndexForHit(state, pos, hit));
            if (door < 0 || !row.isAssignableBoxStart(door)) return null;
            String key = "row|" + dimension + "|" + pos.asLong() + "|" + door;
            if (row.isHeistBreached(door, session.id())) {
                return activeBag(player, session) == null ? null : new Target(
                        Action.LOOT, key, hit, door, "Hold {key} to search this deposit box");
            }
            if (held.is(ModItems.LOCKPICKING_TOOL.get())) return new Target(Action.LOCKPICK, key, hit, door);
            if (held.is(ModItems.OVE9000_SAW.get()) && sawCharge(held) > 0) return new Target(Action.SAW, key, hit, door);
            return null;
        }
        if (state.is(ModBlocks.METAL_PALLET.get())) {
            BlockPos master = MetalPalletBlock.getMasterPos(state, pos);
            if (!(player.level().getBlockEntity(master) instanceof MetalPalletBlockEntity pallet)) return null;
            ItemStack bag = activeBag(player, session);
            Candidate candidate = bag == null ? null : nextPalletCandidate(player, bag, pallet);
            if (candidate == null) return null;
            String prompt = "Hold {key} to steal 1x " + candidate.stack.getHoverName().getString();
            return new Target(Action.LOOT, "loot|" + dimension + "|" + master.asLong(), hit, -1, prompt);
        }
        if (isWorldLoot(state)) {
            ItemStack bag = activeBag(player, session);
            ItemStack loot = worldLootStack(state);
            if (bag == null || loot.isEmpty() || !fits(bag, loot, player)) return null;
            String prompt = "Hold {key} to steal " + loot.getCount() + "x " + loot.getHoverName().getString();
            return new Target(Action.LOOT, "loot|" + dimension + "|" + pos.asLong(), hit, -1, prompt);
        }
        if (HeistDoorSupport.isBreachable(state)) {
            String key = HeistDoorSupport.targetKey(player.serverLevel(), pos);
            if (session.isBreached(key)) return null;
            if (held.is(ModItems.LOCKPICKING_TOOL.get())) return new Target(Action.LOCKPICK, key, hit, -1);
            if (held.is(ModItems.OVE9000_SAW.get()) && sawCharge(held) > 0) return new Target(Action.SAW, key, hit, -1);
        }
        return null;
    }

    private static Target target(Action action, String dimension, BlockPos pos, BlockHitResult hit, int rowDoor) {
        return new Target(action, action.name().toLowerCase() + "|" + dimension + "|" + pos.asLong(), hit, rowDoor);
    }

    private static boolean perform(ServerPlayer player, HeistSession session, Target target) {
        return switch (target.action) {
            case LOOT -> loot(player, session, target);
            case LOCKPICK -> breach(player, session, target, false);
            case SAW -> saw(player, session, target);
            case RELOAD_SAW -> reloadSaw(player);
            case PLACE_DRILL -> placeDrill(player, session, target);
            case RESTART_DRILL -> restartDrill(player, session, target);
            case RECOVER_DRILL -> recoverDrill(player, session, target);
            case PLACE_SAFE_DRILL -> placeSafeDrill(player, session, target);
            case RESTART_SAFE_DRILL -> restartSafeDrill(player, session, target);
            case RECOVER_SAFE_DRILL -> recoverSafeDrill(player, session, target);
            case SPOOF_RFID -> spoofRfid(player, session, target);
            case START_HACK -> startHack(player, session, target);
            case RESUME_HACK -> resumeHack(player, session, target);
        };
    }

    private static boolean spoofRfid(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null || !player.getMainHandItem().is(ModItems.RFID_SPOOFER.get())) return false;
        BlockPos pos = target.hit.getBlockPos();
        if (!(player.level().getBlockEntity(pos) instanceof RfidScannerBlockEntity scanner)
                || !scanner.canSpoofSuccess()) {
            return false;
        }
        HeistService.triggerAlarm(player.getServer(), session, "An RFID reader was spoofed.");
        if (!scanner.activateHeistSpoof(session.id())) return false;
        player.displayClientMessage(Component.literal(
                "RFID spoofed. All configured success outputs are active."), true);
        return true;
    }

    private static boolean loot(ServerPlayer player, HeistSession session, Target target) {
        ItemStack bag = activeBag(player, session);
        if (bag == null || target.hit == null) return false;
        ServerLevel level = player.serverLevel();
        BlockPos pos = target.hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (SecureSafeBlock.isSafeBlock(state)) {
            BlockPos master = SecureSafeBlock.getMasterPos(state, pos);
            if (level.getBlockEntity(master) instanceof SecureSafeBlockEntity safe
                    && safe.isHeistBreached(session.id())) {
                return lootSecureSafe(player, session, bag, safe, master, target.rowDoor);
            }
            return false;
        }
        if (level.getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row) {
            return lootSafeBox(player, session, bag, row, pos, target.rowDoor);
        }
        if (state.is(ModBlocks.METAL_PALLET.get())) {
            BlockPos master = MetalPalletBlock.getMasterPos(state, pos);
            if (level.getBlockEntity(master) instanceof MetalPalletBlockEntity pallet) {
                return lootPallet(player, session, bag, pallet, master);
            }
            return false;
        }
        return lootWorldBlock(player, session, bag, pos, level.getBlockState(pos));
    }

    private static boolean lootSafeBox(ServerPlayer player, HeistSession session, ItemStack bag,
                                       SafetyDepositBoxRowBlockEntity row, BlockPos rowPos, int door) {
        UUID accountId = row.getAssignedAccountId(door);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        AccountHolder account = centralBank == null ? null : centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            player.displayClientMessage(Component.literal("This deposit box is empty."), true);
            return false;
        }
        Candidate candidate = account.getSafeBoxSlots().entrySet().stream()
                .map(entry -> new Candidate(entry.getKey(), ItemStackDataCompat.parseStack(entry.getValue(), player.registryAccess())))
                .filter(entry -> !entry.stack.isEmpty() && fits(bag, entry.stack, player))
                .sorted(candidateOrder(player.getServer()))
                .findFirst().orElse(null);
        if (candidate == null) {
            player.displayClientMessage(Component.literal("The deposit box is empty or your duffel is full."), true);
            return false;
        }
        UUID bagId = HeistDuffelData.bagId(bag);
        HeistLootJournalEntry journal = journal(player, HeistLootJournalEntry.SourceType.SAFE_BOX_ACCOUNT,
                rowPos, candidate.slot, accountId, bagId, candidate.stack, new CompoundTag());
        session.addJournal(journal);
        account.getSafeBoxSlots().remove(candidate.slot);
        BankManager.markDirty();
        if (!HeistDuffelData.insert(bag, candidate.stack, player.registryAccess())) {
            account.getSafeBoxSlots().put(candidate.slot, journal.stackTag());
            session.rollbackJournal(journal.entryId());
            BankManager.markDirty();
            syncSafeBoxContents(player.getServer(), session, row, rowPos, door, account);
            return false;
        }
        syncSafeBoxContents(player.getServer(), session, row, rowPos, door, account);
        syncInventory(player);
        return true;
    }

    private static boolean lootPallet(ServerPlayer player, HeistSession session, ItemStack bag,
                                      MetalPalletBlockEntity pallet, BlockPos pos) {
        Candidate candidate = nextPalletCandidate(player, bag, pallet);
        if (candidate == null) {
            player.displayClientMessage(Component.literal("The pallet has no fitting loot."), true);
            return false;
        }
        UUID bagId = HeistDuffelData.bagId(bag);
        HeistLootJournalEntry journal = journal(player, HeistLootJournalEntry.SourceType.METAL_PALLET, pos,
                candidate.slot, null, bagId, candidate.stack, new CompoundTag());
        session.addJournal(journal);
        ItemStack extracted = pallet.getItemHandler().extractItem(candidate.slot, 1, false);
        if (extracted.isEmpty() || !HeistDuffelData.insert(bag, extracted, player.registryAccess())) {
            if (!extracted.isEmpty()) pallet.getItemHandler().insertItem(candidate.slot, extracted, false);
            session.rollbackJournal(journal.entryId());
            return false;
        }
        SafeAccessAuditService.recordStorageRemoval(player.serverLevel(), pos, player, "Metal Pallet", extracted);
        syncInventory(player);
        return true;
    }

    private static Candidate nextPalletCandidate(ServerPlayer player, ItemStack bag, MetalPalletBlockEntity pallet) {
        List<Candidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < pallet.getItemHandler().getSlots(); slot++) {
            ItemStack stack = pallet.getItemHandler().getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            ItemStack single = stack.copyWithCount(1);
            if (fits(bag, single, player)) candidates.add(new Candidate(slot, single));
        }
        return candidates.stream().sorted(candidateOrder(player.getServer())).findFirst().orElse(null);
    }

    private static boolean lootSecureSafe(ServerPlayer player, HeistSession session, ItemStack bag,
                                          SecureSafeBlockEntity safe, BlockPos pos, int slot) {
        if (!safe.isDisplaySlot(slot)) return false;
        ItemStack stored = safe.getItemHandler().getStackInSlot(slot);
        if (stored.isEmpty() || !fits(bag, stored, player)) {
            player.displayClientMessage(Component.literal("That safe slot is empty or your duffel is full."), true);
            return false;
        }
        Candidate candidate = new Candidate(slot, stored.copy());
        HeistLootJournalEntry journal = journal(player, HeistLootJournalEntry.SourceType.SECURE_SAFE, pos,
                candidate.slot, null, HeistDuffelData.bagId(bag), candidate.stack, new CompoundTag());
        session.addJournal(journal);
        ItemStack extracted = safe.getItemHandler().extractItem(candidate.slot, candidate.stack.getCount(), false);
        if (extracted.isEmpty() || !HeistDuffelData.insert(bag, extracted, player.registryAccess())) {
            if (!extracted.isEmpty()) safe.getItemHandler().insertItem(candidate.slot, extracted, false);
            session.rollbackJournal(journal.entryId());
            return false;
        }
        SafeAccessAuditService.recordStorageRemoval(player.serverLevel(), pos, player, "Secure Safe", extracted);
        syncInventory(player);
        return true;
    }

    private static boolean lootWorldBlock(ServerPlayer player, HeistSession session, ItemStack bag,
                                           BlockPos pos, BlockState state) {
        ItemStack stack = worldLootStack(state);
        if (stack.isEmpty() || !fits(bag, stack, player)) return false;
        CompoundTag blockState = NbtUtils.writeBlockState(state);
        HeistLootJournalEntry journal = journal(player, HeistLootJournalEntry.SourceType.WORLD_BLOCK, pos,
                -1, null, HeistDuffelData.bagId(bag), stack, blockState);
        session.addJournal(journal);
        player.serverLevel().removeBlock(pos, false);
        if (!HeistDuffelData.insert(bag, stack, player.registryAccess())) {
            player.serverLevel().setBlock(pos, state, Block.UPDATE_ALL);
            session.rollbackJournal(journal.entryId());
            return false;
        }
        syncInventory(player);
        return true;
    }

    private static HeistLootJournalEntry journal(ServerPlayer player, HeistLootJournalEntry.SourceType type,
                                                  BlockPos pos, int slot, UUID accountId, UUID bagId,
                                                  ItemStack stack, CompoundTag blockState) {
        return new HeistLootJournalEntry(UUID.randomUUID(), type,
                player.level().dimension().location().toString(), pos, slot, accountId, bagId,
                HeistLootValueService.valueCents(player.getServer(), stack),
                ItemStackDataCompat.saveStack(stack, player.registryAccess()), blockState);
    }

    private static boolean breach(ServerPlayer player, HeistSession session, Target target, boolean saw) {
        if (target.hit == null) return false;
        ServerLevel level = player.serverLevel();
        BlockPos pos = target.hit.getBlockPos();
        if (level.getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row) {
            int door = target.rowDoor;
            if (!row.beginHeistBreach(door, session.id())) return false;
            String key = "row|" + level.dimension().location() + "|" + pos.asLong() + "|" + door;
            session.breach(key);
            CentralBank centralBank = BankManager.getCentralBank(player.getServer());
            AccountHolder account = centralBank == null ? null
                    : centralBank.SearchForAccountByAccountId(row.getAssignedAccountId(door));
            syncSafeBoxContents(player.getServer(), session, row, pos, door, account);
            player.displayClientMessage(Component.literal("Deposit-box lock opened."), true);
        } else {
            BlockPos canonical = HeistDoorSupport.canonicalPos(level, pos);
            BlockState state = level.getBlockState(canonical);
            if (!HeistDoorSupport.isBreachable(state)) return false;
            String key = HeistDoorSupport.targetKey(level, canonical);
            session.breach(key);
            HeistDoorSupport.setBreached(level, canonical, true);
            player.displayClientMessage(Component.literal("Door breached."), true);
        }
        if (!saw) level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                net.minecraft.sounds.SoundSource.BLOCKS, .65F, 1.35F);
        return true;
    }

    private static void syncSafeBoxContents(MinecraftServer server,
                                            HeistSession session,
                                            SafetyDepositBoxRowBlockEntity row,
                                            BlockPos rowPos,
                                            int door,
                                            AccountHolder account) {
        if (server == null || session == null || row == null || rowPos == null) return;
        int slots = Math.max(0, row.getModuleType(door).inventorySlots());
        List<ItemStack> contents = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            CompoundTag tag = account == null ? null : account.getSafeBoxSlots().get(slot);
            contents.add(tag == null ? ItemStack.EMPTY
                    : ItemStackDataCompat.parseStack(tag, server.registryAccess()));
        }
        UUID displayId = SafetyDepositBoxRowBlockEntity.heistDisplayId(
                row.getLevel() == null ? "" : row.getLevel().dimension().location().toString(), rowPos, door);
        SafeBoxDisplayContentsPayload payload = new SafeBoxDisplayContentsPayload(displayId, true, contents);
        for (HeistMember member : session.members().values()) {
            ServerPlayer crew = server.getPlayerList().getPlayer(member.playerId());
            if (crew != null && member.active()) PacketDistributor.sendToPlayer(crew, payload);
        }
    }

    private static boolean saw(ServerPlayer player, HeistSession session, Target target) {
        ItemStack saw = player.getMainHandItem();
        if (!saw.is(ModItems.OVE9000_SAW.get()) || sawCharge(saw) < SAW_CHARGE_PER_CONTACT) return false;
        setSawCharge(saw, sawCharge(saw) - SAW_CHARGE_PER_CONTACT);
        Map<String, Integer> contacts = SAW_CONTACTS.computeIfAbsent(player.getServer(), ignored -> new HashMap<>());
        String key = session.id() + "|" + target.key;
        int required = target.key.startsWith("row|") ? 2 : 3;
        int count = contacts.merge(key, 1, Integer::sum);
        HeistService.triggerAlarm(player.getServer(), session, "An OVE9000 saw was used inside the bank.");
        player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, .7F);
        if (count < required) {
            player.displayClientMessage(Component.literal("Saw contact " + count + "/" + required + "."), true);
            syncInventory(player);
            return true;
        }
        contacts.remove(key);
        syncInventory(player);
        return breach(player, session, target, true);
    }

    private static boolean reloadSaw(ServerPlayer player) {
        ItemStack saw = player.getMainHandItem();
        if (!saw.is(ModItems.OVE9000_SAW.get()) || sawCharge(saw) > 0 || !consumeBlade(player)) return false;
        setSawCharge(saw, 100);
        syncInventory(player);
        player.displayClientMessage(Component.literal("OVE9000 replacement blade installed."), true);
        return true;
    }

    private static boolean placeDrill(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        ServerLevel level = player.serverLevel();
        BlockState hitState = level.getBlockState(target.hit.getBlockPos());
        if (!hitState.is(ModBlocks.BANK_VAULT_DOOR.get())) return false;
        BlockPos master = BankVaultDoorBlock.getMasterPos(hitState, target.hit.getBlockPos());
        String dimension = level.dimension().location().toString();
        if (session.vaultDrill(dimension, master) != null) return false;
        if (!(level.getBlockEntity(master) instanceof BankVaultDoorBlockEntity vault)
                || !vault.attachHeistDrill(session.id())) return false;
        HeistSession.DrillEscrow escrow = takeVaultDrill(player, session);
        if (escrow == null) {
            vault.detachHeistDrill(session.id());
            return false;
        }
        int jams = Math.floorMod(session.id().hashCode() ^ master.hashCode(), 4);
        if (!session.deployDrill(escrow.ownerId(), escrow.stackTag(), dimension, master,
                player.getServer().getTickCount() + VAULT_DRILL_DURATION_TICKS, jams)) {
            vault.detachHeistDrill(session.id());
            giveOrDrop(player, ItemStackDataCompat.parseStack(escrow.stackTag(), player.registryAccess()));
            return false;
        }
        HeistService.triggerAlarm(player.getServer(), session, "A thermal drill was attached to the vault.");
        syncInventory(player);
        player.displayClientMessage(Component.literal("Thermal drill running: 6:00."), true);
        return true;
    }

    private static boolean restartDrill(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        BlockState state = player.level().getBlockState(target.hit.getBlockPos());
        if (!state.is(ModBlocks.BANK_VAULT_DOOR.get())) return false;
        BlockPos master = BankVaultDoorBlock.getMasterPos(state, target.hit.getBlockPos());
        HeistSession.DrillState drill = session.vaultDrill(
                player.level().dimension().location().toString(), master);
        if (drill == null || drill.jammedAtTick() <= 0L) return false;
        long remaining = Math.max(20L, drill.finishesTick() - drill.jammedAtTick());
        drill.restart(player.getServer().getTickCount() + remaining);
        player.displayClientMessage(Component.literal("Thermal drill restarted."), true);
        return true;
    }

    private static boolean recoverDrill(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(target.hit.getBlockPos());
        if (!state.is(ModBlocks.BANK_VAULT_DOOR.get())) return false;
        BlockPos master = BankVaultDoorBlock.getMasterPos(state, target.hit.getBlockPos());
        String dimension = level.dimension().location().toString();
        HeistSession.DrillState drill = session.vaultDrill(dimension, master);
        if (drill == null || !drill.completed()
                || !(level.getBlockEntity(master) instanceof BankVaultDoorBlockEntity vault)
                || !vault.hasHeistDrill(session.id())) return false;
        vault.detachHeistDrill(session.id());
        ItemStack drillStack = ItemStackDataCompat.parseStack(drill.stackTag(), player.registryAccess());
        if (!drillStack.isEmpty()) giveOrDrop(player, drillStack);
        vault.addEscortHold(session.id());
        session.breach("vault|" + dimension + "|" + master.asLong());
        session.removeDrill(dimension, master);
        player.displayClientMessage(Component.literal("Drill recovered. Vault opening."), true);
        return true;
    }

    private static boolean placeSafeDrill(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(target.hit.getBlockPos());
        if (!SecureSafeBlock.isSafeBlock(state)) return false;
        BlockPos master = SecureSafeBlock.getMasterPos(state, target.hit.getBlockPos());
        String dimension = level.dimension().location().toString();
        if (session.safeDrill(dimension, master) != null) return false;
        if (!(level.getBlockEntity(master) instanceof SecureSafeBlockEntity safe)
                || !safe.attachHeistDrill(session.id())) return false;
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModBlocks.HEIST_DRILL.get().asItem())) {
            safe.clearHeistDrill(session.id());
            return false;
        }
        ItemStack escrow = held.copyWithCount(1);
        int jams = Math.floorMod(session.id().hashCode() ^ master.hashCode(), 3);
        if (!session.deploySafeDrill(player.getUUID(), ItemStackDataCompat.saveStack(escrow, player.registryAccess()),
                dimension, master, player.getServer().getTickCount() + SAFE_DRILL_DURATION_TICKS, jams)) {
            safe.clearHeistDrill(session.id());
            return false;
        }
        held.shrink(1);
        if (held.isEmpty()) player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        HeistService.triggerAlarm(player.getServer(), session, "A heist drill was attached to a secure safe.");
        syncInventory(player);
        player.displayClientMessage(Component.literal("Heist drill running: 3:00."), true);
        return true;
    }

    private static boolean restartSafeDrill(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        BlockState state = player.level().getBlockState(target.hit.getBlockPos());
        if (!SecureSafeBlock.isSafeBlock(state)) return false;
        BlockPos master = SecureSafeBlock.getMasterPos(state, target.hit.getBlockPos());
        HeistSession.DrillState drill = session.safeDrill(
                player.level().dimension().location().toString(), master);
        if (drill == null || drill.jammedAtTick() <= 0L) return false;
        long remaining = Math.max(20L, drill.finishesTick() - drill.jammedAtTick());
        drill.restart(player.getServer().getTickCount() + remaining);
        player.displayClientMessage(Component.literal("Heist drill restarted."), true);
        return true;
    }

    private static boolean recoverSafeDrill(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(target.hit.getBlockPos());
        if (!SecureSafeBlock.isSafeBlock(state)) return false;
        BlockPos pos = SecureSafeBlock.getMasterPos(state, target.hit.getBlockPos());
        String dimension = level.dimension().location().toString();
        HeistSession.DrillState drillState = session.safeDrill(dimension, pos);
        if (drillState == null || !drillState.completed()) return false;
        if (!(level.getBlockEntity(pos) instanceof SecureSafeBlockEntity safe)
                || !safe.hasHeistDrill(session.id())) return false;
        if (!safe.completeHeistBreach(session.id())) return false;
        ItemStack drill = ItemStackDataCompat.parseStack(drillState.stackTag(), player.registryAccess());
        if (!drill.isEmpty()) giveOrDrop(player, drill);
        session.breach("safe|" + dimension + "|" + pos.asLong());
        session.removeSafeDrill(dimension, pos);
        player.displayClientMessage(Component.literal("Heist drill recovered. Safe opening."), true);
        return true;
    }

    private static boolean startHack(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        String dimension = player.level().dimension().location().toString();
        if (session.isComputerHacked(dimension, target.hit.getBlockPos())
                || session.activeHack(dimension, target.hit.getBlockPos()) != null) return false;
        HeistService.beginHack(player.getServer(), session, target.hit.getBlockPos());
        player.displayClientMessage(Component.literal("Owner-PC transfer initialized: 1:00."), true);
        return true;
    }

    private static boolean resumeHack(ServerPlayer player, HeistSession session, Target target) {
        if (target.hit == null) return false;
        String dimension = player.level().dimension().location().toString();
        HeistSession.HackState hack = session.activeHack(dimension, target.hit.getBlockPos());
        if (hack == null || hack.pausedUntilTick() != Long.MAX_VALUE) return false;
        HeistService.resumeHack(player.getServer(), session, dimension, target.hit.getBlockPos());
        player.displayClientMessage(Component.literal("Owner-PC transfer resumed."), true);
        return true;
    }

    private static boolean hasVaultDrillAvailable(ServerPlayer player, HeistSession session) {
        if (session.hasEscrowedDrill()) return true;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModBlocks.THERMAL_DRILL.get().asItem())) return true;
        }
        return false;
    }

    private static HeistSession.DrillEscrow takeVaultDrill(ServerPlayer player, HeistSession session) {
        HeistSession.DrillEscrow escrow = session.takeEscrowedDrill();
        if (escrow != null) return escrow;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ModBlocks.THERMAL_DRILL.get().asItem())) continue;
            ItemStack removed = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            player.getInventory().setChanged();
            return new HeistSession.DrillEscrow(player.getUUID(),
                    ItemStackDataCompat.saveStack(removed, player.registryAccess()));
        }
        return null;
    }

    private static ItemStack activeBag(ServerPlayer player, HeistSession session) {
        ItemStack stack = player.getMainHandItem();
        if (isActiveBag(stack, session)) return stack;
        stack = player.getOffhandItem();
        if (isActiveBag(stack, session)) return stack;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            stack = player.getInventory().getItem(slot);
            if (isActiveBag(stack, session)) return stack;
        }
        return null;
    }

    private static boolean isActiveBag(ItemStack stack, HeistSession session) {
        return stack != null && stack.is(ModBlocks.HEIST_DUFFEL.get().asItem())
                && session != null && session.id().equals(HeistDuffelData.sessionId(stack));
    }

    private static boolean fits(ItemStack bag, ItemStack loot, ServerPlayer player) {
        ItemStackHandler handler = HeistDuffelData.readInventory(bag, player.registryAccess());
        ItemStack remaining = loot.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining.isEmpty();
    }

    private record Candidate(int slot, ItemStack stack) {}

    private static Comparator<Candidate> candidateOrder(MinecraftServer server) {
        return Comparator.<Candidate>comparingLong(candidate -> HeistLootValueService.valueCents(server, candidate.stack))
                .reversed().thenComparingInt(Candidate::slot);
    }

    private static boolean isWorldLoot(BlockState state) {
        return state != null && (state.is(ModBlocks.GOLD_BAR.get()) || state.is(ModBlocks.SILVER_BAR.get())
                || state.is(ModBlocks.CASH_STACK.get()) || state.is(ModBlocks.MONEY_STACK.get()));
    }

    private static ItemStack worldLootStack(BlockState state) {
        if (state == null) return ItemStack.EMPTY;
        if (state.is(ModBlocks.GOLD_BAR.get())) return new ItemStack(ModBlocks.GOLD_BAR.get().asItem());
        if (state.is(ModBlocks.SILVER_BAR.get())) return new ItemStack(ModBlocks.SILVER_BAR.get().asItem());
        if (state.is(ModBlocks.CASH_STACK.get())) {
            return new ItemStack(state.getValue(CashStackBlock.KIND).item(), state.getValue(CashStackBlock.COUNT));
        }
        if (state.is(ModBlocks.MONEY_STACK.get())) {
            return new ItemStack(state.getValue(MoneyStackBlock.KIND).stackItem(), state.getValue(MoneyStackBlock.COUNT));
        }
        return ItemStack.EMPTY;
    }

    private static int sawCharge(ItemStack stack) {
        if (stack == null || !stack.is(ModItems.OVE9000_SAW.get())) return 0;
        return Math.max(0, 100 - stack.getDamageValue());
    }

    private static void setSawCharge(ItemStack stack, int charge) {
        if (stack != null && stack.is(ModItems.OVE9000_SAW.get())) {
            stack.setDamageValue(Math.max(0, Math.min(100, 100 - charge)));
        }
    }

    private static boolean hasBlade(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.OVE9000_SAW_BLADE.get())) return true;
        }
        return false;
    }

    private static boolean consumeBlade(ServerPlayer player) {
        if (player.getAbilities().instabuild) return true;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ModItems.OVE9000_SAW_BLADE.get())) continue;
            stack.shrink(1);
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack remaining = stack.copy();
        player.getInventory().add(remaining);
        if (!remaining.isEmpty()) {
            ItemEntity drop = player.drop(remaining, false);
            if (drop != null) drop.setDefaultPickUpDelay();
        }
        syncInventory(player);
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
    }

    private static Map<UUID, HoldState> states(MinecraftServer server) {
        return HOLDS.computeIfAbsent(server, ignored -> new HashMap<>());
    }
}
