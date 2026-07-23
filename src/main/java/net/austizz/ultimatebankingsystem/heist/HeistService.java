package net.austizz.ultimatebankingsystem.heist;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.api.heist.HeistLifecycleEvent;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.owner.OwnerPcBankReadSupport;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAccessLogService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAlarmSettingsService;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.custom.SecureSafeBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.RfidScannerBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.SecureSafeBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.HeistDuffelData;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.SafeBoxDisplayContentsPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class HeistService {
    public static final int MAX_CREW = 4;
    public static final long COUNTDOWN_TICKS = 100L;
    public static final long HEIST_DURATION_TICKS = 15L * 60L * 20L;
    public static final long EXTRACTION_HOLD_TICKS = 60L;
    public static final long DISCONNECT_GRACE_MILLIS = 60_000L;
    public static final long BANK_COOLDOWN_MILLIS = 2L * 60L * 60L * 1000L;
    public static final long PLAYER_COOLDOWN_MILLIS = 30L * 60L * 1000L;
    public static final long VICTIM_PROTECTION_MILLIS = 24L * 60L * 60L * 1000L;
    public static final double EXTRACTION_RADIUS = 5.0D;
    public static final double EXTRACTION_VERTICAL_TOLERANCE = 5.0D;
    public static final double MAX_CREW_DISTANCE_FROM_PREMISE = 96.0D;
    private static final UUID CENTRAL_BANK_ID = new UUID(0L, 0L);
    private static final Set<MinecraftServer> RECONCILED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<MinecraftServer, Map<String, Integer>> TELLER_SUSPICION = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Long>> STAFF_REENTRY_BLOCK = new IdentityHashMap<>();

    private HeistService() {}

    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    record ExfillStatus(HeistExfillZone.VisualState visualState, boolean lootArmed,
                        int crewInside, int crewRequired, int remainingTicks) {}

    public static HeistSession planningSession(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        HeistSavedData data = HeistSavedData.get(player.getServer());
        HeistSession existing = sessionFor(data, player.getUUID());
        if (existing != null) return existing;
        HeistSession created = new HeistSession(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName());
        data.put(created);
        return created;
    }

    public static Result invite(ServerPlayer leader, String playerName) {
        HeistSession session = requirePlanningLeader(leader);
        if (session == null) return Result.fail("Only the planning leader can invite crew.");
        if (session.members().size() >= MAX_CREW) return Result.fail("A heist crew can contain at most four players.");
        ServerPlayer target = leader.getServer().getPlayerList().getPlayerByName(playerName == null ? "" : playerName.trim());
        if (target == null) return Result.fail("That player must be online to receive an invitation.");
        if (target.getUUID().equals(leader.getUUID())) return Result.fail("You are already the crew leader.");
        HeistSession other = sessionFor(HeistSavedData.get(leader.getServer()), target.getUUID());
        if (other != null && !other.id().equals(session.id())) return Result.fail("That player is already in another crew.");
        if (session.member(target.getUUID()) != null) return Result.fail("That player is already invited.");
        session.putMember(new HeistMember(target.getUUID(), target.getGameProfile().getName(), false));
        HeistSavedData.get(leader.getServer()).put(session);
        target.sendSystemMessage(Component.literal("§6" + leader.getGameProfile().getName()
                + " invited you to a bank heist. Open /heist to accept or decline."));
        return Result.ok("Invitation sent to " + target.getGameProfile().getName() + ".");
    }

    public static Result accept(ServerPlayer player, boolean accepted) {
        if (player == null || player.getServer() == null) return Result.fail("Heist service unavailable.");
        HeistSavedData data = HeistSavedData.get(player.getServer());
        HeistSession session = sessionFor(data, player.getUUID());
        if (session == null || player.getUUID().equals(session.leaderId())) return Result.fail("No pending invitation was found.");
        HeistMember member = session.member(player.getUUID());
        if (member == null || member.accepted()) return Result.fail("No pending invitation was found.");
        if (!accepted) {
            session.removeMember(player.getUUID());
            data.put(session);
            return Result.ok("Heist invitation declined.");
        }
        member.setAccepted(true);
        member.setReady(false);
        member.setLastKnownName(player.getGameProfile().getName());
        data.put(session);
        notifyCrew(player.getServer(), session, "§a" + member.lastKnownName() + " joined the crew.");
        return Result.ok("You joined the heist crew.");
    }

    public static Result leave(ServerPlayer player) {
        if (player == null || player.getServer() == null) return Result.fail("Heist service unavailable.");
        HeistSavedData data = HeistSavedData.get(player.getServer());
        HeistSession session = sessionFor(data, player.getUUID());
        if (session == null) return Result.fail("You are not in a heist crew.");
        if (session.phase().isRunning()) return abandon(player);
        boolean countdownCancelled = session.phase() == HeistPhase.COUNTDOWN;
        String playerName = player.getGameProfile().getName();
        session.removeMember(player.getUUID());
        boolean acceptedCrewRemains = session.members().values().stream().anyMatch(HeistMember::accepted);
        if (!acceptedCrewRemains) {
            data.remove(session.id());
            notifyCrew(player.getServer(), session, "§cThe heist crew was disbanded because no accepted members remain.");
            return Result.ok("You left the heist crew. The empty crew was disbanded.");
        }
        if (countdownCancelled) {
            session.setPhase(HeistPhase.PLANNING);
            session.members().values().forEach(member -> member.setReady(false));
        }
        data.put(session);
        notifyCrew(player.getServer(), session, "§e" + playerName + " left the crew."
                + (countdownCancelled ? " The countdown was cancelled." : ""));
        return Result.ok("You left the heist crew.");
    }

    public static Result selectTarget(ServerPlayer leader, UUID bankId, String premiseId) {
        HeistSession session = requirePlanningLeader(leader);
        if (session == null) return Result.fail("Only the planning leader can select a target.");
        CentralBank centralBank = BankManager.getCentralBank(leader.getServer());
        HeistTarget target = HeistEligibilityService.find(leader.getServer(), centralBank,
                HeistSavedData.get(leader.getServer()), bankId, premiseId);
        if (target == null) return Result.fail("That bank premise no longer exists.");
        if (!target.eligible()) return Result.fail(String.join(" ", target.blockers()));
        session.selectTarget(target.bankId(), target.bankName(), target.premiseId(), target.bounds(), target.exit());
        HeistSavedData.get(leader.getServer()).put(session);
        notifyCrew(leader.getServer(), session, "§6Target selected: " + target.bankName() + " / " + target.premiseId());
        return Result.ok("Target selected.");
    }

    public static Result setReady(ServerPlayer player, boolean ready) {
        if (player == null || player.getServer() == null) return Result.fail("Heist service unavailable.");
        HeistSession session = sessionFor(HeistSavedData.get(player.getServer()), player.getUUID());
        HeistMember member = session == null ? null : session.member(player.getUUID());
        if (session == null || member == null || !member.accepted() || session.phase() != HeistPhase.PLANNING) {
            return Result.fail("You cannot change ready state now.");
        }
        member.setReady(ready);
        HeistSavedData.get(player.getServer()).put(session);
        notifyCrew(player.getServer(), session, (ready ? "§a" : "§e") + member.lastKnownName()
                + (ready ? " is ready." : " is no longer ready."));
        return Result.ok(ready ? "Ready." : "Not ready.");
    }

    public static Result setDevInsiderBypass(ServerPlayer player, boolean enabled) {
        if (player == null || player.getServer() == null) return Result.fail("Heist service unavailable.");
        HeistSavedData data = HeistSavedData.get(player.getServer());
        HeistSession session = sessionFor(data, player.getUUID());
        if (session == null && enabled) session = planningSession(player);
        if (session == null) return Result.fail("That player is not in a heist planning crew.");
        if (session.phase() != HeistPhase.PLANNING) {
            return Result.fail("Cancel the countdown or finish the active heist before changing the insider bypass.");
        }
        HeistMember member = session.member(player.getUUID());
        if (member == null || !member.accepted()) {
            return Result.fail("That player must accept the crew invitation first.");
        }
        member.setDevInsiderBypass(enabled);
        member.setReady(false);
        data.put(session);
        return Result.ok("Insider development bypass " + (enabled ? "enabled" : "disabled")
                + " for " + member.lastKnownName() + " in this planning session.");
    }

    public static Result startCountdown(ServerPlayer leader) {
        HeistSession session = requirePlanningLeader(leader);
        if (session == null) return Result.fail("Only the planning leader can start the heist.");
        Result validation = validateStart(leader.getServer(), session, true);
        if (!validation.success()) return validation;
        session.startCountdown(leader.getServer().getTickCount() + COUNTDOWN_TICKS);
        HeistSavedData.get(leader.getServer()).put(session);
        notifyCrew(leader.getServer(), session, "§6Heist starts in 5 seconds. Stay ready.");
        return Result.ok("Countdown started.");
    }

    public static Result cancelCountdown(ServerPlayer leader) {
        if (leader == null || leader.getServer() == null) return Result.fail("Heist service unavailable.");
        HeistSession session = sessionFor(HeistSavedData.get(leader.getServer()), leader.getUUID());
        if (session == null || !leader.getUUID().equals(session.leaderId())
                || session.phase() != HeistPhase.COUNTDOWN) {
            return Result.fail("Only the crew leader can cancel an active countdown.");
        }
        session.setPhase(HeistPhase.PLANNING);
        session.members().values().forEach(member -> member.setReady(false));
        HeistSavedData.get(leader.getServer()).put(session);
        notifyCrew(leader.getServer(), session, "§eHeist countdown cancelled.");
        return Result.ok("Countdown cancelled.");
    }

    public static Result voteAbandon(ServerPlayer player) {
        return abandon(player);
    }

    public static Result abandon(ServerPlayer player) {
        HeistSession session = activeSession(player);
        if (session == null) return Result.fail("You are not in an active heist.");
        return abandonMember(player.getServer(), session, player,
                player.getGameProfile().getName() + " abandoned the heist.");
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        HeistSavedData data = HeistSavedData.get(server);
        reconcile(server, data);
        long nowMillis = System.currentTimeMillis();
        if (server.getTickCount() % 20 == 0) data.prune(nowMillis);
        for (HeistSession session : new ArrayList<>(data.sessions())) {
            if (session.phase() == HeistPhase.COUNTDOWN) {
                if (server.getTickCount() >= session.countdownEndsAtTick()) {
                    Result validation = validateStart(server, session, true);
                    if (!validation.success()) {
                        session.setPhase(HeistPhase.PLANNING);
                        session.members().values().forEach(member -> member.setReady(false));
                        data.put(session);
                        notifyCrew(server, session, "§cCountdown cancelled: " + validation.message());
                    } else {
                        begin(server, session);
                    }
                }
                continue;
            }
            if (!session.phase().isRunning()) continue;
            tickRunning(server, session, nowMillis);
        }
        HeistInteractionService.tick(server);
        if (server.getTickCount() % 5L == 0L) HeistPlanningService.syncHud(server);
    }

    private static void tickRunning(MinecraftServer server, HeistSession session, long nowMillis) {
        List<ServerPlayer> distantMembers = new ArrayList<>();
        for (HeistMember member : session.members().values()) {
            if (!member.active()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null) {
                if (member.disconnectedAtMillis() == 0L) member.setDisconnectedAtMillis(nowMillis);
                if (nowMillis - member.disconnectedAtMillis() >= DISCONNECT_GRACE_MILLIS) member.setActive(false);
                continue;
            }
            member.setDisconnectedAtMillis(0L);
            member.setLastKnownName(player.getGameProfile().getName());
            if (!atExit(session, player) && HeistAbandonPolicy.beyondPremise(session.premiseBounds(),
                    player.level().dimension().location().toString(), player.getX(), player.getZ(),
                    MAX_CREW_DISTANCE_FROM_PREMISE)) {
                distantMembers.add(player);
                continue;
            }
            if (inside(session, player) && session.phase() == HeistPhase.CASING) session.setPhase(HeistPhase.ACTIVE);
        }
        for (ServerPlayer player : distantMembers) {
            if (session.phase().isTerminal()) return;
            abandonMember(server, session, player,
                    player.getGameProfile().getName() + " moved more than "
                            + (int) MAX_CREW_DISTANCE_FROM_PREMISE + " blocks away from the bank premise.");
        }
        if (session.phase().isTerminal()) return;
        if (session.members().values().stream().noneMatch(HeistMember::active)) {
            finish(server, session, false, "No crew members remain active.");
            return;
        }
        evacuateBystanders(server, session);
        tickTellerSuspicion(server, session);
        tickAlarmSiren(server, session);
        tickHack(server, session);
        tickDrill(server, session);
        tickSafeDrill(server, session);
        if (server.getTickCount() >= session.deadlineTick()) {
            finish(server, session, false, "The 15-minute heist timer expired.");
            return;
        }
        if (session.lootArmed()) {
            boolean gathered = activeCrewGathered(server, session);
            if (gathered) {
                if (session.extractionStartedTick() <= 0L) session.setExtractionStartedTick(server.getTickCount());
                session.setPhase(HeistPhase.ESCAPING);
                if (server.getTickCount() - session.extractionStartedTick() >= EXTRACTION_HOLD_TICKS) {
                    finish(server, session, true, "Crew extracted successfully.");
                    return;
                }
            } else {
                session.setExtractionStartedTick(0L);
                if (session.phase() == HeistPhase.ESCAPING) session.setPhase(HeistPhase.ACTIVE);
            }
        }
        HeistSavedData.get(server).put(session);
    }

    private static void begin(MinecraftServer server, HeistSession session) {
        Result drill = escrowCrewDrill(server, session);
        if (!drill.success()) {
            session.setPhase(HeistPhase.PLANNING);
            session.members().values().forEach(member -> member.setReady(false));
            HeistSavedData.get(server).put(session);
            notifyCrew(server, session, "§c" + drill.message());
            return;
        }
        session.start(server.getTickCount(), HEIST_DURATION_TICKS);
        for (HeistMember member : session.members().values()) {
            member.setActive(true);
            member.setDead(false);
            member.setDisconnectedAtMillis(0L);
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null) continue;
            UUID bagId = UUID.randomUUID();
            ItemStack bag = HeistDuffelData.createActive(new ItemStack(ModBlocks.HEIST_DUFFEL.get().asItem()),
                    session.id(), bagId, player.getUUID());
            giveOrDrop(player, bag);
            member.addBag(bagId);
            player.closeContainer();
            teleportToExit(server, session, player);
        }
        evacuateBystanders(server, session);
        HeistSavedData data = HeistSavedData.get(server);
        data.put(session);
        List<UUID> insiderBypasses = session.members().values().stream()
                .filter(HeistMember::devInsiderBypass)
                .map(HeistMember::playerId)
                .toList();
        data.audit(session.id(), "START", "Bank=" + session.bankId() + " premise=" + session.premiseId()
                + " crew=" + session.members().keySet() + " dev_insider_bypass=" + insiderBypasses);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new HeistLifecycleEvent(
                HeistLifecycleEvent.Stage.STARTED, session.id(), session.bankId(), session.premiseId(),
                session.members().keySet(), "Heist started"));
        notifyCrew(server, session, "§cHEIST STARTED. Put on your mask before performing criminal actions.");
    }

    private static Result validateStart(MinecraftServer server, HeistSession session, boolean requireReady) {
        if (session == null || session.bankId() == null || session.premiseId().isBlank()) return Result.fail("Select an eligible target first.");
        CentralBank centralBank = BankManager.getCentralBank(server);
        HeistTarget target = HeistEligibilityService.find(server, centralBank, HeistSavedData.get(server),
                session.bankId(), session.premiseId());
        if (target == null || !target.eligible()) return Result.fail(target == null
                ? "Target is unavailable." : String.join(" ", target.blockers()));
        int accepted = 0;
        boolean ownsDrill = false;
        long now = System.currentTimeMillis();
        for (HeistMember member : session.members().values()) {
            if (!member.accepted()) return Result.fail("Every invited player must accept or be removed.");
            accepted++;
            if (requireReady && !member.ready()) return Result.fail(member.lastKnownName() + " is not ready.");
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null) return Result.fail(member.lastKnownName() + " is offline.");
            if (HeistSavedData.get(server).playerCooldown(player.getUUID()) > now) return Result.fail(member.lastKnownName() + " is on heist cooldown.");
            if (primaryAccount(centralBank, player.getUUID()) == null) return Result.fail(member.lastKnownName() + " needs a primary bank account.");
            if (!ownsMask(player)) return Result.fail(member.lastKnownName() + " needs a Dallas mask.");
            if (!member.devInsiderBypass()
                    && isBankStaff(server, centralBank, session.bankId(), player.getUUID())) {
                return Result.fail(member.lastKnownName() + " is an insider at this bank.");
            }
            ownsDrill |= inventoryContains(player, ModBlocks.THERMAL_DRILL.get().asItem());
        }
        if (accepted < 1 || accepted > MAX_CREW) return Result.fail("Crew size must be between one and four players.");
        if (!ownsDrill) return Result.fail("The crew must own one thermal drill.");
        return Result.ok("Ready.");
    }

    private static Result escrowCrewDrill(MinecraftServer server, HeistSession session) {
        for (HeistMember member : session.members().values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null) continue;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(ModBlocks.THERMAL_DRILL.get().asItem())) continue;
                ItemStack escrow = stack.copyWithCount(1);
                stack.shrink(1);
                if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
                session.escrowDrill(player.getUUID(), ItemStackDataCompat.saveStack(escrow, player.registryAccess()));
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastFullState();
                return Result.ok("Thermal drill secured.");
            }
        }
        return Result.fail("The crew thermal drill could not be secured.");
    }

    public static void triggerAlarm(MinecraftServer server, HeistSession session, String reason) {
        if (server == null || session == null || session.alarmed()) return;
        session.alarm(reason);
        HeistSavedData.get(server).put(session);
        notifyCrew(server, session, "§cALARM: " + reason);
        CentralBank centralBank = BankManager.getCentralBank(server);
        SafeAccessLogService.recordSystem(centralBank, session.bankId(),
                SafeAccessLogService.CATEGORY_SECURITY, SafeAccessLogService.OUTCOME_DENIED,
                "BANK_ALARM_TRIGGERED", session.premiseId(), reason,
                session.premiseBounds().dimension(),
                new BlockPos(
                        (session.premiseBounds().minX() + session.premiseBounds().maxX()) / 2,
                        session.premiseBounds().minY(),
                        (session.premiseBounds().minZ() + session.premiseBounds().maxZ()) / 2));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isBankStaff(server, centralBank, session.bankId(), player.getUUID()) || player.hasPermissions(3)) {
                player.sendSystemMessage(Component.literal("§c[Bank Alarm] " + session.bankName()
                        + " is under attack. Premise " + session.premiseId() + "."));
            }
        }
        HeistSavedData.get(server).audit(session.id(), "ALARM", reason);
        triggerConfiguredAlarmRelays(server, session);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new HeistLifecycleEvent(
                HeistLifecycleEvent.Stage.ALARMED, session.id(), session.bankId(), session.premiseId(),
                session.members().keySet(), reason));
    }

    private static void triggerConfiguredAlarmRelays(MinecraftServer server, HeistSession session) {
        ServerLevel level = level(server, session.premiseBounds().dimension());
        if (level == null) return;
        SafeBlockBounds bounds = session.premiseBounds();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) continue;
                    if (level.getBlockEntity(cursor) instanceof RfidScannerBlockEntity scanner) {
                        scanner.triggerBankAlarm(session.bankId(), session.bankName());
                    }
                }
            }
        }
    }

    private static void tickTellerSuspicion(MinecraftServer server, HeistSession session) {
        ServerLevel level = level(server, session.premiseBounds().dimension());
        if (level == null || session.alarmed()) return;
        AABB bounds = box(session.premiseBounds());
        Map<String, Integer> suspicion = TELLER_SUSPICION.computeIfAbsent(server, ignored -> new HashMap<>());
        for (BankTellerEntity teller : level.getEntitiesOfClass(BankTellerEntity.class, bounds,
                teller -> !teller.isCashier() && session.bankId().equals(teller.getBoundBankId()))) {
            boolean sees = false;
            for (HeistMember member : session.members().values()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
                if (player != null && member.active() && isMasked(player)
                        && player.distanceToSqr(teller) <= 16.0D * 16.0D && teller.hasLineOfSight(player)) {
                    sees = true;
                    break;
                }
            }
            String key = session.id() + ":" + teller.getUUID();
            int ticks = suspicion.getOrDefault(key, 0);
            ticks = sees ? Math.min(40, ticks + 1) : Math.max(0, ticks - 2);
            suspicion.put(key, ticks);
            if (ticks >= 40) {
                triggerAlarm(server, session, "A teller identified a masked crew member.");
                break;
            }
        }
    }

    private static void tickAlarmSiren(MinecraftServer server, HeistSession session) {
        if (!session.alarmed()) return;
        CentralBank centralBank = BankManager.getCentralBank(server);
        SafeAlarmSettingsService.Settings alarm = SafeAlarmSettingsService.read(
                centralBank.getOrCreateBankMetadata(session.bankId()));
        if (!alarm.enabled() || server.getTickCount() % alarm.intervalTicks() != 0) return;
        ServerLevel level = level(server, session.premiseBounds().dimension());
        if (level == null) return;
        double x = (session.premiseBounds().minX() + session.premiseBounds().maxX() + 1) * .5;
        double y = (session.premiseBounds().minY() + session.premiseBounds().maxY() + 1) * .5;
        double z = (session.premiseBounds().minZ() + session.premiseBounds().maxZ() + 1) * .5;
        SafeAlarmSettingsService.play(level, x, y, z, alarm);
    }

    private static void tickHack(MinecraftServer server, HeistSession session) {
        long now = server.getTickCount();
        for (HeistSession.HackState hack : session.activeHacks()) {
            if (hack.finishesTick() <= 0L || hack.pausedUntilTick() == Long.MAX_VALUE) continue;
            if (hack.pausedUntilTick() > 0L && now >= hack.pausedUntilTick()) {
                hack.pauseForRestart();
                notifyCrew(server, session, "§eThe owner-PC hack at " + shortPos(hack.pos())
                        + " paused. Hold the action key at that PC for 3 seconds to resume it.");
                continue;
            }
            if (now >= hack.finishesTick()) completeHack(server, session, hack);
        }
    }

    private static void completeHack(MinecraftServer server, HeistSession session, HeistSession.HackState hack) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        Bank bank = bank(centralBank, session.bankId());
        if (bank == null) return;
        long nowMillis = System.currentTimeMillis();
        List<AccountHolder> eligible = bank.getBankAccounts().values().stream()
                .filter(account -> account != null && account.getPlayerUUID() != null)
                .filter(account -> "PERSONAL".equalsIgnoreCase(account.getAccountAccessType()))
                .filter(account -> account.getAccountType() != AccountTypes.CertificateAccount)
                .filter(account -> account.getBalance().signum() > 0)
                .filter(account -> session.member(account.getPlayerUUID()) == null)
                .filter(account -> !session.hackedAccountIds().contains(account.getAccountUUID()))
                .filter(account -> HeistSavedData.get(server).victimProtectedUntil(account.getPlayerUUID()) <= nowMillis)
                .sorted(Comparator.comparing(account -> account.getAccountUUID().toString()))
                .toList();
        if (eligible.isEmpty()) {
            session.completeHackWithoutTransfer(hack.dimension(), hack.pos());
            notifyCrew(server, session, "§eThe owner-PC hack at " + shortPos(hack.pos())
                    + " completed, but no eligible liquid customer account was found.");
            return;
        }
        int index = Math.floorMod(session.id().hashCode() ^ hack.pos().hashCode(), eligible.size());
        AccountHolder victim = eligible.get(index);
        BigDecimal amount = victim.getBalance().multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.DOWN);
        if (amount.signum() <= 0 || !victim.forceRemoveBalance(amount)) return;
        session.completeHack(hack.dimension(), hack.pos(), victim.getAccountUUID(), victim.getPlayerUUID(),
                amount, bank.getBankName());
        HeistSavedData data = HeistSavedData.get(server);
        data.setVictimProtection(victim.getPlayerUUID(), nowMillis + VICTIM_PROTECTION_MILLIS);
        data.audit(session.id(), "HACK_ESCROW", "victim=" + victim.getPlayerUUID() + " account="
                + victim.getAccountUUID() + " amount=" + amount + " crew=" + session.members().keySet());
        notifyCrew(server, session, "§aOwner-PC transfer at " + shortPos(hack.pos())
                + " completed. Escrow: $" + amount.toPlainString());
    }

    private static void tickDrill(MinecraftServer server, HeistSession session) {
        long now = server.getTickCount();
        for (HeistSession.DrillState drill : session.vaultDrills()) {
            if (drill.completed() || drill.jammedAtTick() > 0L) continue;
            if ((now & 3L) == 0L) emitDrillParticles(server, drill.dimension(), drill.pos(), true);
            long remaining = drill.finishesTick() - now;
            if (drill.jamsRemaining() > 0 && remaining <= drill.jamsRemaining() * 2400L) {
                drill.setJammed(now);
                notifyCrew(server, session, "§cThe thermal drill at " + shortPos(drill.pos())
                        + " stalled. Hold the action key for 3 seconds to restart it.");
                continue;
            }
            if (now >= drill.finishesTick()) {
                drill.complete();
                notifyCrew(server, session, "§aVault drilling at " + shortPos(drill.pos())
                        + " complete. Recover that thermal drill to open its vault.");
            }
        }
    }

    private static void tickSafeDrill(MinecraftServer server, HeistSession session) {
        long now = server.getTickCount();
        for (HeistSession.DrillState drill : session.safeDrills()) {
            if (drill.completed() || drill.jammedAtTick() > 0L) continue;
            if ((now & 3L) == 0L) emitDrillParticles(server, drill.dimension(), drill.pos(), false);
            long remaining = drill.finishesTick() - now;
            if (drill.jamsRemaining() > 0 && remaining <= drill.jamsRemaining() * 1200L) {
                drill.setJammed(now);
                notifyCrew(server, session, "§cThe safe drill at " + shortPos(drill.pos())
                        + " stalled. Hold the action key for 3 seconds to restart it.");
                continue;
            }
            if (now >= drill.finishesTick()) {
                drill.complete();
                notifyCrew(server, session, "§aSafe drilling at " + shortPos(drill.pos())
                        + " complete. Recover that heist drill to open its safe.");
            }
        }
    }

    private static void emitDrillParticles(MinecraftServer server, String dimension, BlockPos pos, boolean vault) {
        ServerLevel level = level(server, dimension);
        if (level == null || pos == null) return;
        BlockState state = level.getBlockState(pos);
        Direction facing = Direction.NORTH;
        double height = 0.65D;
        if (vault && state.hasProperty(BankVaultDoorBlock.FACING)) {
            facing = state.getValue(BankVaultDoorBlock.FACING);
            Vec3 tip = HeistDrillGeometry.vaultVisibleTip(pos, facing);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    tip.x, tip.y, tip.z, 4, 0.08D, 0.08D, 0.08D, 0.018D);
            return;
        } else if (!vault && state.hasProperty(SecureSafeBlock.FACING)) {
            facing = state.getValue(SecureSafeBlock.FACING);
            if (level.getBlockEntity(pos) instanceof SecureSafeBlockEntity safe && safe.isTallSafe()) {
                height = 1.05D;
            }
        }
        double x = pos.getX() + 0.5D + facing.getStepX() * 0.54D;
        double z = pos.getZ() + 0.5D + facing.getStepZ() * 0.54D;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                x, pos.getY() + height, z, 4, 0.13D, 0.13D, 0.13D, 0.025D);
    }

    public static void beginHack(MinecraftServer server, HeistSession session, BlockPos pcPos) {
        String dimension = session.premiseBounds().dimension();
        if (session.activeHack(dimension, pcPos) != null || session.isComputerHacked(dimension, pcPos)) return;
        long now = server.getTickCount();
        long pauseAt = ((session.id().getLeastSignificantBits() ^ pcPos.asLong()) & 1L) == 0L
                ? now + 20L * 25L : 0L;
        session.beginHack(dimension, pcPos, now + 20L * 60L, pauseAt);
        triggerAlarm(server, session, "The bank owner PC was compromised.");
        HeistSavedData.get(server).put(session);
    }

    public static void resumeHack(MinecraftServer server, HeistSession session, String dimension, BlockPos pcPos) {
        HeistSession.HackState hack = session.activeHack(dimension, pcPos);
        if (hack == null || hack.pausedUntilTick() != Long.MAX_VALUE) return;
        session.resumeHack(dimension, pcPos, server.getTickCount() + 20L * 35L);
        HeistSavedData.get(server).put(session);
    }

    private static Result abandonMember(MinecraftServer server,
                                        HeistSession session,
                                        ServerPlayer player,
                                        String reason) {
        if (server == null || session == null || player == null || !session.phase().isRunning()) {
            return Result.fail("You are not in an active heist.");
        }
        HeistMember member = session.member(player.getUUID());
        if (member == null || !member.accepted() || !member.active()) {
            return Result.fail("You are not an active member of this heist.");
        }
        long activeMembers = session.members().values().stream()
                .filter(HeistMember::accepted)
                .filter(HeistMember::active)
                .count();
        if (activeMembers <= 1L) {
            finish(server, session, false, reason + " No crew members remain.");
            return Result.ok("Heist abandoned.");
        }

        Set<UUID> forfeitedBags = forfeitCarriedSessionBags(server, session, player);
        session.removeMember(player.getUUID());
        session.setExtractionStartedTick(0L);
        if (session.phase() == HeistPhase.ESCAPING) session.setPhase(HeistPhase.ACTIVE);

        HeistSavedData data = HeistSavedData.get(server);
        data.setPlayerCooldown(player.getUUID(), System.currentTimeMillis() + PLAYER_COOLDOWN_MILLIS);
        if (session.exit() != null) {
            data.deferExit(player.getUUID(), session.exit());
            if (teleportToExit(server, session.exit(), player)) data.takeDeferredExit(player.getUUID());
        }
        data.audit(session.id(), "MEMBER_ABANDONED", reason + " player=" + player.getUUID()
                + " forfeitedBags=" + forfeitedBags);
        data.put(session);
        HeistPlanningService.clearClientState(player, "You abandoned the heist.");
        player.sendSystemMessage(Component.literal("§cYou abandoned the heist and forfeited carried heist loot."));
        notifyCrew(server, session, "§e" + member.lastKnownName()
                + " abandoned the heist. The remaining crew may continue.");
        return Result.ok("You abandoned the heist.");
    }

    private static Set<UUID> forfeitCarriedSessionBags(MinecraftServer server,
                                                       HeistSession session,
                                                       ServerPlayer player) {
        Set<UUID> bagIds = new LinkedHashSet<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!session.id().equals(HeistDuffelData.sessionId(stack))) continue;
            UUID bagId = HeistDuffelData.bagId(stack);
            if (bagId != null) bagIds.add(bagId);
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        if (!bagIds.isEmpty()) {
            for (HeistLootJournalEntry entry : new ArrayList<>(session.lootJournal())) {
                if (entry.bagId() == null || !bagIds.contains(entry.bagId())) continue;
                restoreEntry(server, entry);
                session.rollbackJournal(entry.entryId());
            }
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastFullState();
        return Set.copyOf(bagIds);
    }

    public static void finish(MinecraftServer server, HeistSession session, boolean success, String reason) {
        if (server == null || session == null || session.phase().isTerminal()) return;
        Set<UUID> extractedBags = success ? extractedBagIds(server, session) : Set.of();
        session.setPhase(success ? HeistPhase.SUCCESS : HeistPhase.FAILED);
        HeistSavedData data = HeistSavedData.get(server);
        cleanupBreaches(server, session);
        cleanupDrill(server, session);
        cleanupSpoofedRfidReaders(server, session);
        cleanupBags(server, session, success, extractedBags);
        restoreOrCommitLoot(server, session, success, extractedBags);
        settleHack(server, session, success);
        evacuateCrew(server, session, data);
        long now = System.currentTimeMillis();
        data.setBankCooldown(session.bankId(), now + BANK_COOLDOWN_MILLIS);
        session.members().keySet().forEach(id -> data.setPlayerCooldown(id, now + PLAYER_COOLDOWN_MILLIS));
        data.audit(session.id(), success ? "SUCCESS" : "FAILED", reason + " loot=$"
                + BigDecimal.valueOf(session.totalLootCents(), 2) + " extractedBags=" + extractedBags
                + " crew=" + session.members().keySet());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new HeistLifecycleEvent(
                success ? HeistLifecycleEvent.Stage.SUCCEEDED : HeistLifecycleEvent.Stage.FAILED,
                session.id(), session.bankId(), session.premiseId(), session.members().keySet(), reason));
        notifyCrew(server, session, (success ? "§aHEIST COMPLETE: " : "§cHEIST FAILED: ") + reason);
        data.remove(session.id());
        Map<String, Integer> suspicion = TELLER_SUSPICION.get(server);
        if (suspicion != null) suspicion.keySet().removeIf(key -> key.startsWith(session.id().toString()));
    }

    private static void settleHack(MinecraftServer server, HeistSession session, boolean success) {
        if (session.hackTransfers().isEmpty()) return;
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (!success) {
            for (HeistSession.HackTransfer transfer : session.hackTransfers()) {
                AccountHolder victim = centralBank.SearchForAccountByAccountId(transfer.accountId());
                if (victim != null) victim.forceAddBalance(transfer.amount());
            }
            return;
        }
        List<AccountHolder> recipients = session.members().values().stream()
                .filter(HeistMember::active)
                .map(member -> primaryAccount(centralBank, member.playerId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (recipients.isEmpty()) {
            for (HeistSession.HackTransfer transfer : session.hackTransfers()) {
                AccountHolder victim = centralBank.SearchForAccountByAccountId(transfer.accountId());
                if (victim != null) victim.forceAddBalance(transfer.amount());
            }
            return;
        }
        for (HeistSession.HackTransfer transfer : session.hackTransfers()) {
            long cents = transfer.amount().movePointRight(2).longValue();
            long each = cents / recipients.size();
            long remainder = cents % recipients.size();
            for (int i = 0; i < recipients.size(); i++) {
                long payout = each + (i == 0 ? remainder : 0L);
                if (payout > 0L) recipients.get(i).forceAddBalance(BigDecimal.valueOf(payout, 2));
            }
            if (transfer.playerId() != null) {
                ServerPlayer onlineVictim = server.getPlayerList().getPlayer(transfer.playerId());
                String notice = "§cUnauthorized transfer: $" + transfer.amount().toPlainString()
                        + " from " + transfer.bankName() + " at " + java.time.Instant.now()
                        + ". Reference " + session.id() + ".";
                if (onlineVictim != null) onlineVictim.sendSystemMessage(Component.literal(notice));
                else HeistSavedData.get(server).deferMessage(transfer.playerId(), notice);
            }
        }
    }

    private static void cleanupBreaches(MinecraftServer server, HeistSession session) {
        for (String key : session.breachedTargets()) {
            String[] parts = key.split("\\|", -1);
            if (parts.length < 3) continue;
            ServerLevel level = level(server, parts[1]);
            if (level == null) continue;
            try {
                BlockPos pos = BlockPos.of(Long.parseLong(parts[2]));
                if ("row".equals(parts[0]) && parts.length >= 4) {
                    int door = Integer.parseInt(parts[3]);
                    if (level.getBlockEntity(pos) instanceof SafetyDepositBoxRowBlockEntity row) {
                        row.endHeistBreach(door, session.id());
                    }
                    UUID displayId = SafetyDepositBoxRowBlockEntity.heistDisplayId(parts[1], pos, door);
                    for (HeistMember member : session.members().values()) {
                        ServerPlayer crew = server.getPlayerList().getPlayer(member.playerId());
                        if (crew != null) PacketDistributor.sendToPlayer(crew,
                                SafeBoxDisplayContentsPayload.clear(displayId));
                    }
                } else if ("door".equals(parts[0])) {
                    HeistDoorSupport.setBreached(level, pos, false);
                } else if ("safe".equals(parts[0])
                        && level.getBlockEntity(pos) instanceof SecureSafeBlockEntity safe) {
                    safe.endHeistBreach(session.id());
                } else if ("vault".equals(parts[0])
                        && level.getBlockEntity(pos) instanceof BankVaultDoorBlockEntity vault) {
                    vault.removeEscortHold(session.id());
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private static void cleanupDrill(MinecraftServer server, HeistSession session) {
        HeistSession.DrillEscrow pending = session.takeEscrowedDrill();
        if (pending != null && pending.ownerId() != null && !pending.stackTag().isEmpty()) {
            returnItem(server, pending.ownerId(), pending.stackTag());
        }
        for (HeistSession.DrillState drill : session.vaultDrills()) {
            ServerLevel level = level(server, drill.dimension());
            if (level != null && level.getBlockEntity(drill.pos()) instanceof BankVaultDoorBlockEntity vault) {
                vault.detachHeistDrill(session.id());
            }
            if (drill.ownerId() != null && !drill.stackTag().isEmpty()) {
                returnItem(server, drill.ownerId(), drill.stackTag());
            }
        }
        for (HeistSession.DrillState drill : session.safeDrills()) {
            ServerLevel level = level(server, drill.dimension());
            if (level != null && level.getBlockEntity(drill.pos()) instanceof SecureSafeBlockEntity safe) {
                safe.clearHeistDrill(session.id());
            }
            if (drill.ownerId() != null && !drill.stackTag().isEmpty()) {
                returnItem(server, drill.ownerId(), drill.stackTag());
            }
        }
        session.clearDrills();
    }

    private static void cleanupSpoofedRfidReaders(MinecraftServer server, HeistSession session) {
        ServerLevel level = level(server, session.premiseBounds().dimension());
        if (level == null) return;
        SafeBlockBounds bounds = session.premiseBounds();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    cursor.set(x, y, z);
                    if (level.hasChunkAt(cursor)
                            && level.getBlockEntity(cursor) instanceof RfidScannerBlockEntity scanner) {
                        scanner.revokeEscortAccess(session.id());
                    }
                }
            }
        }
    }

    private static void restoreOrCommitLoot(MinecraftServer server, HeistSession session,
                                            boolean success, Set<UUID> extractedBags) {
        for (HeistLootJournalEntry entry : session.lootJournal()) {
            if (success && entry.bagId() != null && extractedBags.contains(entry.bagId())) {
                entry.markCommitted();
            } else {
                restoreEntry(server, entry);
            }
        }
    }

    private static void restoreEntry(MinecraftServer server, HeistLootJournalEntry entry) {
        if (entry == null || entry.restored() || entry.committed()) return;
        ServerLevel level = level(server, entry.dimension());
        ItemStack stack = level == null ? ItemStack.EMPTY
                : ItemStackDataCompat.parseStack(entry.stackTag(), level.registryAccess());
        switch (entry.sourceType()) {
            case SAFE_BOX_ACCOUNT -> {
                CentralBank centralBank = BankManager.getCentralBank(server);
                AccountHolder account = centralBank == null ? null : centralBank.SearchForAccountByAccountId(entry.accountId());
                if (account != null && !account.getSafeBoxSlots().containsKey(entry.sourceSlot())) {
                    account.getSafeBoxSlots().put(entry.sourceSlot(), entry.stackTag());
                    BankManager.markDirty();
                }
            }
            case METAL_PALLET -> {
                if (level != null && level.getBlockEntity(entry.sourcePos()) instanceof MetalPalletBlockEntity pallet
                        && !stack.isEmpty()) {
                    ItemStack current = pallet.getItemHandler().getStackInSlot(entry.sourceSlot());
                    if (current.isEmpty()) pallet.getItemHandler().insertItem(entry.sourceSlot(), stack, false);
                }
            }
            case SECURE_SAFE -> {
                if (level != null && level.getBlockEntity(entry.sourcePos()) instanceof SecureSafeBlockEntity safe
                        && !stack.isEmpty() && entry.sourceSlot() >= 0
                        && entry.sourceSlot() < safe.getItemHandler().getSlots()) {
                    ItemStack current = safe.getItemHandler().getStackInSlot(entry.sourceSlot());
                    if (current.isEmpty()) safe.getItemHandler().insertItem(entry.sourceSlot(), stack, false);
                }
            }
            case WORLD_BLOCK -> {
                if (level != null && !entry.blockStateTag().isEmpty() && level.getBlockState(entry.sourcePos()).isAir()) {
                    BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), entry.blockStateTag());
                    level.setBlock(entry.sourcePos(), state, 3);
                }
            }
        }
        entry.markRestored();
    }

    private static void cleanupBags(MinecraftServer server, HeistSession session,
                                    boolean success, Set<UUID> extractedBags) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!session.id().equals(HeistDuffelData.sessionId(stack))) continue;
                UUID bagId = HeistDuffelData.bagId(stack);
                if (success && extractedBags.contains(bagId)) HeistDuffelData.makePermanent(stack);
                else player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            player.inventoryMenu.broadcastFullState();
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, AABB.INFINITE,
                    item -> session.id().equals(HeistDuffelData.sessionId(item.getItem())))) {
                UUID bagId = HeistDuffelData.bagId(entity.getItem());
                if (success && extractedBags.contains(bagId)) {
                    ItemStack permanent = entity.getItem().copy(); HeistDuffelData.makePermanent(permanent); entity.setItem(permanent);
                } else entity.discard();
            }
        }
    }

    private static Set<UUID> extractedBagIds(MinecraftServer server, HeistSession session) {
        Set<UUID> result = new LinkedHashSet<>();
        for (HeistMember member : session.members().values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null || !atExit(session, player)) continue;
            for (ItemStack stack : player.getInventory().items) {
                if (session.id().equals(HeistDuffelData.sessionId(stack)) && HeistDuffelData.bagId(stack) != null) result.add(HeistDuffelData.bagId(stack));
            }
            ItemStack offhand = player.getOffhandItem();
            if (session.id().equals(HeistDuffelData.sessionId(offhand)) && HeistDuffelData.bagId(offhand) != null) result.add(HeistDuffelData.bagId(offhand));
        }
        ServerLevel exitLevel = level(server, session.exit().dimension());
        if (exitLevel != null) {
            AABB area = new AABB(session.exit().x() + .5, session.exit().y() + .5, session.exit().z() + .5,
                    session.exit().x() + .5, session.exit().y() + .5, session.exit().z() + .5).inflate(EXTRACTION_RADIUS);
            for (ItemEntity entity : exitLevel.getEntitiesOfClass(ItemEntity.class, area)) {
                ItemStack stack = entity.getItem();
                if (session.id().equals(HeistDuffelData.sessionId(stack)) && HeistDuffelData.bagId(stack) != null) result.add(HeistDuffelData.bagId(stack));
            }
        }
        return Set.copyOf(result);
    }

    public static HeistSession activeSession(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        HeistSession session = sessionFor(HeistSavedData.get(player.getServer()), player.getUUID());
        return session != null && session.phase().isRunning() ? session : null;
    }

    public static HeistSession session(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        return sessionFor(HeistSavedData.get(player.getServer()), player.getUUID());
    }

    public static HeistSession activeSession(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return null;
        HeistSession session = sessionFor(HeistSavedData.get(server), playerId);
        return session != null && session.phase().isRunning() ? session : null;
    }

    public static HeistSession activeAt(MinecraftServer server, String dimension, BlockPos pos) {
        if (server == null || pos == null) return null;
        return HeistSavedData.get(server).sessions().stream().filter(session -> session.phase().isRunning()
                && session.premiseBounds() != null && session.premiseBounds().contains(dimension, pos.getX(), pos.getY(), pos.getZ()))
                .findFirst().orElse(null);
    }

    public static boolean isBankFrozen(MinecraftServer server, UUID bankId) {
        return server != null && bankId != null && HeistSavedData.get(server).sessions().stream()
                .anyMatch(session -> session.phase().isRunning() && bankId.equals(session.bankId()));
    }

    public static boolean isCrew(MinecraftServer server, UUID playerId, HeistSession session) {
        HeistMember member = session == null ? null : session.member(playerId);
        return member != null && member.accepted() && member.active();
    }

    public static boolean isMasked(ServerPlayer player) {
        return player != null && player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.DALLAS_MASK.get());
    }

    public static boolean isBankStaff(MinecraftServer server, CentralBank centralBank, UUID bankId, UUID playerId) {
        if (server == null || centralBank == null || bankId == null || playerId == null) return false;
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (CENTRAL_BANK_ID.equals(bankId) && online != null && online.hasPermissions(3)) return true;
        Bank bank = bank(centralBank, bankId);
        if (bank != null && playerId.equals(bank.getBankOwnerId())) return true;
        if (BankStaffingService.hasEmployee(OwnerPcBankReadSupport.metadataSnapshot(centralBank, bankId), playerId)) return true;
        return BankOwnerPcService.canAccessBank(centralBank, playerId, bankId, false)
                || SafetyDepositBoxService.canManageSafeArea(centralBank, playerId, bankId);
    }

    public static boolean canCombat(ServerPlayer attacker, Entity target) {
        if (attacker == null || attacker.getServer() == null || target == null) return false;
        HeistSession session = activeAt(attacker.getServer(), target.level().dimension().location().toString(), target.blockPosition());
        if (session == null) return false;
        CentralBank centralBank = BankManager.getCentralBank(attacker.getServer());
        boolean attackerCrew = isCrew(attacker.getServer(), attacker.getUUID(), session);
        boolean attackerStaff = isBankStaff(attacker.getServer(), centralBank, session.bankId(), attacker.getUUID());
        if (target instanceof ServerPlayer targetPlayer) {
            boolean targetCrew = isCrew(attacker.getServer(), targetPlayer.getUUID(), session);
            boolean targetStaff = isBankStaff(attacker.getServer(), centralBank, session.bankId(), targetPlayer.getUUID());
            return attackerCrew && targetStaff || attackerStaff && targetCrew;
        }
        if (target instanceof BankTellerEntity teller) return attackerCrew && session.bankId().equals(teller.getBoundBankId());
        return false;
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        HeistSavedData data = HeistSavedData.get(player.getServer());
        SafeExitSnapshot pendingExit = data.takeDeferredExit(player.getUUID());
        if (pendingExit != null && !teleportToExit(player.getServer(), pendingExit, player)) {
            data.deferExit(player.getUUID(), pendingExit);
        }
        for (CompoundTag stackTag : data.takeDeferredItems(player.getUUID())) {
            ItemStack stack = ItemStackDataCompat.parseStack(stackTag, player.registryAccess());
            if (!stack.isEmpty()) giveOrDrop(player, stack);
        }
        for (String message : data.takeDeferredMessages(player.getUUID())) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        HeistSession session = activeSession(player);
        if (session == null) return;
        dropActiveBags(player, session.id());
        HeistMember member = session.member(player.getUUID());
        if (member != null) member.setDisconnectedAtMillis(System.currentTimeMillis());
        HeistSavedData data = HeistSavedData.get(player.getServer());
        data.deferExit(player.getUUID(), session.exit());
        data.put(session);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        HeistSession session = activeSession(player);
        if (session != null) {
            dropActiveBags(player, session.id());
            HeistMember member = session.member(player.getUUID());
            if (member != null) { member.setDead(true); member.setActive(false); }
            HeistSavedData.get(player.getServer()).put(session);
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        for (HeistSession active : HeistSavedData.get(player.getServer()).sessions()) {
            if (active.phase().isRunning() && isBankStaff(player.getServer(), centralBank, active.bankId(), player.getUUID())) {
                STAFF_REENTRY_BLOCK.computeIfAbsent(player.getServer(), ignored -> new HashMap<>())
                        .put(player.getUUID(), System.currentTimeMillis() + 60_000L);
            }
        }
    }

    public static boolean staffReentryBlocked(MinecraftServer server, UUID playerId) {
        Long until = STAFF_REENTRY_BLOCK.getOrDefault(server, Map.of()).get(playerId);
        return until != null && until > System.currentTimeMillis();
    }

    public static void stop(MinecraftServer server) {
        if (server == null) return;
        RECONCILED.remove(server);
        TELLER_SUSPICION.remove(server);
        STAFF_REENTRY_BLOCK.remove(server);
        HeistInteractionService.stop(server);
        HeistPlanningService.stop(server);
    }

    private static void reconcile(MinecraftServer server, HeistSavedData data) {
        if (!RECONCILED.add(server)) return;
        for (HeistSession session : new ArrayList<>(data.sessions())) {
            if (session.phase().isRunning() || session.phase() == HeistPhase.COUNTDOWN) {
                finish(server, session, false, "Server recovery restored all uncommitted heist assets.");
            } else if (session.phase().isTerminal()) {
                data.remove(session.id());
            }
        }
    }

    private static void evacuateBystanders(MinecraftServer server, HeistSession session) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!inside(session, player) || isCrew(server, player.getUUID(), session)) continue;
            boolean staff = isBankStaff(server, centralBank, session.bankId(), player.getUUID());
            if (staff && !staffReentryBlocked(server, player.getUUID())) continue;
            teleportToExit(server, session, player);
            player.displayClientMessage(Component.literal(staff
                    ? "You can re-enter this bank in 60 seconds."
                    : "This bank premise is closed during an active heist."), true);
        }
    }

    private static boolean activeCrewGathered(MinecraftServer server, HeistSession session) {
        boolean any = false;
        for (HeistMember member : session.members().values()) {
            if (!member.active()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null || !atExit(session, player)) return false;
            any = true;
        }
        return any;
    }

    static HeistExfillZone.Boundary exfillBoundary(HeistSession session) {
        if (session == null || session.exit() == null) {
            return new HeistExfillZone.Boundary(List.of());
        }
        return HeistExfillZone.square(session.exit().x() + 0.5D,
                session.exit().z() + 0.5D, EXTRACTION_RADIUS);
    }

    static HeistExfillZone.VisualState exfillVisualState(MinecraftServer server, HeistSession session) {
        return exfillStatus(server, session).visualState();
    }

    static ExfillStatus exfillStatus(MinecraftServer server, HeistSession session) {
        if (server == null || session == null || !session.phase().isRunning()) {
            return new ExfillStatus(HeistExfillZone.VisualState.HIDDEN, false, 0, 0, 0);
        }
        int activeCrew = 0;
        int crewInside = 0;
        for (HeistMember member : session.members().values()) {
            if (!member.active()) continue;
            activeCrew++;
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player != null && atExit(session, player)) crewInside++;
        }
        HeistExfillZone.VisualState visualState = HeistExfillZone.visualState(
                session.lootArmed(), activeCrew, crewInside);
        long remaining = EXTRACTION_HOLD_TICKS;
        if (visualState == HeistExfillZone.VisualState.ACTIVE && session.extractionStartedTick() > 0L) {
            remaining = Math.max(0L, EXTRACTION_HOLD_TICKS
                    - (server.getTickCount() - session.extractionStartedTick()));
        }
        return new ExfillStatus(visualState, session.lootArmed(), crewInside, activeCrew,
                (int) Math.min(Integer.MAX_VALUE, remaining));
    }

    private static boolean atExit(HeistSession session, ServerPlayer player) {
        if (session.exit() == null || player == null
                || !session.exit().dimension().equals(player.level().dimension().location().toString())) return false;
        double dy = player.getY() - session.exit().y();
        return Math.abs(dy) <= EXTRACTION_VERTICAL_TOLERANCE
                && exfillBoundary(session).contains(player.getX(), player.getZ());
    }

    private static boolean inside(HeistSession session, ServerPlayer player) {
        return session != null && session.premiseBounds() != null && player != null
                && session.premiseBounds().contains(player.level().dimension().location().toString(),
                player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
    }

    private static void teleportToExit(MinecraftServer server, HeistSession session, ServerPlayer player) {
        if (session != null) teleportToExit(server, session.exit(), player);
    }

    private static boolean teleportToExit(MinecraftServer server,
                                          SafeExitSnapshot exit,
                                          ServerPlayer player) {
        if (server == null || exit == null || player == null) return false;
        ServerLevel level = level(server, exit.dimension());
        if (level == null) return false;
        player.teleportTo(level, exit.x() + .5, exit.y(), exit.z() + .5, exit.yaw(), 0F);
        return true;
    }

    private static void evacuateCrew(MinecraftServer server, HeistSession session, HeistSavedData data) {
        if (session.exit() == null) return;
        for (HeistMember member : session.members().values()) {
            data.deferExit(member.playerId(), session.exit());
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player != null && teleportToExit(server, session.exit(), player)) {
                data.takeDeferredExit(member.playerId());
            }
        }
    }

    private static Result requireResult(boolean condition, String message) { return condition ? Result.ok("") : Result.fail(message); }

    private static HeistSession requirePlanningLeader(ServerPlayer leader) {
        if (leader == null || leader.getServer() == null) return null;
        HeistSession session = planningSession(leader);
        return session != null && leader.getUUID().equals(session.leaderId())
                && session.phase() == HeistPhase.PLANNING ? session : null;
    }

    private static HeistSession sessionFor(HeistSavedData data, UUID playerId) {
        if (data == null || playerId == null) return null;
        return data.sessions().stream().filter(session -> session.member(playerId) != null).findFirst().orElse(null);
    }

    private static boolean ownsMask(ServerPlayer player) {
        return isMasked(player) || inventoryContains(player, ModItems.DALLAS_MASK.get());
    }

    private static boolean inventoryContains(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    private static AccountHolder primaryAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) return null;
        return centralBank.SearchForAccount(playerId).values().stream()
                .filter(AccountHolder::isPrimaryAccount)
                .findFirst().orElse(null);
    }

    private static Bank bank(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) return null;
        return bankId.equals(centralBank.getBankId()) ? centralBank : centralBank.getBank(bankId);
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "unknown" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void notifyCrew(MinecraftServer server, HeistSession session, String message) {
        if (server == null || session == null) return;
        for (HeistMember member : session.members().values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player != null) player.sendSystemMessage(Component.literal(message));
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remaining = stack.copy();
        player.getInventory().add(remaining);
        if (!remaining.isEmpty()) player.drop(remaining, false);
        player.inventoryMenu.broadcastFullState();
    }

    private static void returnItem(MinecraftServer server, UUID playerId, CompoundTag stackTag) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            HeistSavedData.get(server).deferItem(playerId, stackTag);
            return;
        }
        ItemStack stack = ItemStackDataCompat.parseStack(stackTag, player.registryAccess());
        if (!stack.isEmpty()) giveOrDrop(player, stack);
    }

    private static void dropActiveBags(ServerPlayer player, UUID sessionId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!sessionId.equals(HeistDuffelData.sessionId(stack))) continue;
            ItemStack dropped = stack.copy();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + .5, player.getZ(), dropped);
            entity.setDefaultPickUpDelay();
            player.level().addFreshEntity(entity);
        }
        player.inventoryMenu.broadcastFullState();
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (server == null || id == null) return null;
        ResourceKey<Level> key = RegistryKeysCompat.createValueKey(RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
        return server.getLevel(key);
    }

    private static AABB box(SafeBlockBounds bounds) {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
    }
}
