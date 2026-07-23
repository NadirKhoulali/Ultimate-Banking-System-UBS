package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.owner.OwnerPcBankReadSupport;
import net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.BankPremiseProtectionPolicy;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.SafeBoxZoneCache;
import net.austizz.ultimatebankingsystem.bank.safebox.zone.SafeBoxZoneIndex;
import net.austizz.ultimatebankingsystem.heist.HeistService;
import net.austizz.ultimatebankingsystem.heist.HeistDoorSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class BankPremiseProtectionEvents {
    private static final long MESSAGE_COOLDOWN_TICKS = 30L;
    private static final Component PROTECTED_MESSAGE = Component.literal(
            "This bank premise is protected. Only bank staff can modify it.");
    private static final Map<MinecraftServer, Map<UUID, Long>> LAST_MESSAGE = new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> PENDING_INVENTORY_RESYNCS = new WeakHashMap<>();

    private BankPremiseProtectionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ProtectionContext context = context(player.serverLevel());
        if (context != null && !context.canModifyAll(player,
                BankSafeAreaEvents.affectedPositionsFor(event.getState(), event.getPos()))) {
            event.setCanceled(true);
            notifyDenied(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        ProtectionContext context = context(event.getLevel());
        if (context == null) {
            return;
        }
        Collection<BlockPos> positions = affectedPlacementPositions(event);
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!context.canModifyAll(player, positions)) {
                event.setCanceled(true);
                queueInventoryResync(player);
                notifyDenied(player);
            }
            return;
        }
        if (context.protectsAny(positions)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        ProtectionContext context = context(event.getLevel());
        if (context == null) {
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (!context.canModify(player, event.getPos())) {
                event.setCanceled(true);
                queueInventoryResync(player);
                notifyDenied(player);
            }
        } else if (context.protects(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDestructiveItemUse(UseItemOnBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !isDestructiveUse(event.getItemStack())) {
            return;
        }
        ProtectionContext context = context(event.getLevel());
        if (context == null) {
            return;
        }
        Set<BlockPos> positions = new LinkedHashSet<>();
        positions.add(event.getPos());
        if (event.getFace() != null) {
            positions.add(event.getPos().relative(event.getFace()));
        }
        if (!context.canModifyAll(player, positions)) {
            event.cancelWithResult(ItemInteractionResult.FAIL);
            queueInventoryResync(player);
            notifyDenied(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFluidChange(BlockEvent.FluidPlaceBlockEvent event) {
        ProtectionContext context = context(event.getLevel());
        if (context != null
                && (context.protects(event.getPos()) || context.protects(event.getLiquidPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        ProtectionContext context = context(event.getLevel());
        if (context == null || !context.protects(event.getPos())) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && context.canModify(player, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            notifyDenied(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        ProtectionContext context = context(event.getLevel());
        if (context != null && context.protects(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFeatureGrowth(BlockGrowFeatureEvent event) {
        ProtectionContext context = context(event.getLevel());
        if (context != null && context.protects(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        ProtectionContext context = context(event.getEntity().level());
        if (context != null && context.protects(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        ProtectionContext context = context(event.getEntity().level());
        if (context != null && context.protects(event.getEntity().blockPosition())) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        ProtectionContext context = context(event.getLevel());
        if (context == null) {
            return;
        }
        boolean blocked = context.crossesPremiseBoundary(event.getPos(), event.getFaceOffsetPos());
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver != null && resolver.resolve()) {
            for (BlockPos source : resolver.getToPush()) {
                blocked |= context.crossesPremiseBoundary(
                        source, source.relative(resolver.getPushDirection()));
            }
            blocked |= context.protectsAny(resolver.getToDestroy());
        }
        if (blocked) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionStart(ExplosionEvent.Start event) {
        ProtectionContext context = context(event.getLevel());
        if (context == null) {
            return;
        }
        Vec3 center = event.getExplosion().center();
        if (context.protects(BlockPos.containing(center))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        ProtectionContext context = context(event.getLevel());
        if (context == null) {
            return;
        }
        event.getAffectedBlocks().removeIf(context::protects);
        event.getAffectedEntities().removeIf(context::protects);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        ProtectionContext context = context(projectile.level());
        if (event.getRayTraceResult() instanceof EntityHitResult entityHit
                && projectile.getOwner() instanceof ServerPlayer attacker
                && HeistService.canCombat(attacker, entityHit.getEntity())) {
            return;
        }
        if (context != null
                && context.protects(BlockPos.containing(event.getRayTraceResult().getLocation()))) {
            event.setCanceled(true);
            projectile.discard();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHazardJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!isHazardEntity(entity)) {
            return;
        }
        ProtectionContext context = context(event.getLevel());
        if (context != null && context.protects(entity.blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHazardTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!isHazardEntity(entity)) {
            return;
        }
        ProtectionContext context = context(entity.level());
        if (context != null && context.protects(entity.blockPosition())) {
            entity.discard();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        if (HeistService.canCombat(player, target)) {
            return;
        }
        ProtectionContext context = context(target.level());
        if (context != null && !context.canModify(player, target.blockPosition())) {
            event.setCanceled(true);
            notifyDenied(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        Entity target = event.getEntity();
        ProtectionContext context = context(target.level());
        if (context == null || !context.protects(target.blockPosition())) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker && HeistService.canCombat(attacker, target)) {
            return;
        }
        if (isHazardDamage(source)
                || source.getEntity() instanceof ServerPlayer attacker
                && !context.canModify(attacker, target.blockPosition())) {
            event.setInvulnerable(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        ProtectionContext context = context(event.getEntity().level());
        if (context == null || !context.protects(event.getEntity().blockPosition())) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker
                && HeistService.canCombat(attacker, event.getEntity())) {
            return;
        }
        if (isHazardDamage(source)
                || source.getEntity() instanceof ServerPlayer attacker
                && !context.canModify(attacker, event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Set<UUID> pending;
        synchronized (PENDING_INVENTORY_RESYNCS) {
            pending = PENDING_INVENTORY_RESYNCS.remove(event.getServer());
        }
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (UUID playerId : pending) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.inventoryMenu.broadcastFullState();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastFullState();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDoorUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos pos = event.getPos();
        if (!HeistDoorSupport.isBreachable(player.level().getBlockState(pos))) return;
        var session = HeistService.activeAt(player.getServer(),
                player.level().dimension().location().toString(), pos);
        if (session == null) return;
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (HeistService.isBankStaff(player.getServer(), centralBank, session.bankId(), player.getUUID())) return;
        String key = HeistDoorSupport.targetKey(player.serverLevel(), pos);
        if (HeistService.isCrew(player.getServer(), player.getUUID(), session) && session.isBreached(key)) return;
        event.setCanceled(true);
        player.displayClientMessage(Component.literal("This door is locked during the heist."), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            synchronized (LAST_MESSAGE) {
                Map<UUID, Long> byPlayer = LAST_MESSAGE.get(player.getServer());
                if (byPlayer != null) {
                    byPlayer.remove(player.getUUID());
                }
            }
            synchronized (PENDING_INVENTORY_RESYNCS) {
                Set<UUID> pending = PENDING_INVENTORY_RESYNCS.get(player.getServer());
                if (pending != null) {
                    pending.remove(player.getUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (LAST_MESSAGE) {
            LAST_MESSAGE.remove(event.getServer());
        }
        synchronized (PENDING_INVENTORY_RESYNCS) {
            PENDING_INVENTORY_RESYNCS.remove(event.getServer());
        }
    }

    private static Collection<BlockPos> affectedPlacementPositions(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            return multiPlace.getReplacedBlockSnapshots().stream()
                    .map(snapshot -> snapshot.getPos().immutable())
                    .toList();
        }
        return BankSafeAreaEvents.affectedPositionsFor(event.getPlacedBlock(), event.getPos());
    }

    private static boolean isDestructiveUse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BucketItem
                || stack.canPerformAction(ItemAbilities.FIRESTARTER_LIGHT)
                || stack.is(Items.END_CRYSTAL)
                || stack.is(Items.TNT_MINECART)
                || stack.is(Items.FIREWORK_ROCKET)
                || stack.is(Items.WIND_CHARGE);
    }

    private static boolean isHazardEntity(Entity entity) {
        return entity instanceof Projectile
                || entity instanceof PrimedTnt
                || entity instanceof MinecartTNT
                || entity instanceof EndCrystal;
    }

    private static boolean isHazardDamage(DamageSource source) {
        return source != null && (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.getDirectEntity() instanceof Projectile);
    }

    private static void queueInventoryResync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        synchronized (PENDING_INVENTORY_RESYNCS) {
            PENDING_INVENTORY_RESYNCS
                    .computeIfAbsent(server, ignored -> new LinkedHashSet<>())
                    .add(player.getUUID());
        }
    }

    private static ProtectionContext context(LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return null;
        }
        SafeBoxZoneIndex index = SafeBoxZoneCache.index(server, centralBank, server.getTickCount());
        return new ProtectionContext(centralBank, index,
                serverLevel.dimension().location().toString());
    }

    private static void notifyDenied(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long now = server.getTickCount();
        synchronized (LAST_MESSAGE) {
            Map<UUID, Long> byPlayer = LAST_MESSAGE.computeIfAbsent(server, ignored -> new HashMap<>());
            Long previous = byPlayer.get(player.getUUID());
            if (previous != null && now >= previous && now - previous < MESSAGE_COOLDOWN_TICKS) {
                return;
            }
            byPlayer.put(player.getUUID(), now);
        }
        player.displayClientMessage(PROTECTED_MESSAGE, true);
    }

    private record ProtectionContext(CentralBank centralBank,
                                     SafeBoxZoneIndex index,
                                     String dimension) {
        boolean protects(BlockPos pos) {
            return pos != null && BankPremiseProtectionPolicy.protects(
                    index, dimension, pos.getX(), pos.getY(), pos.getZ());
        }

        boolean protects(Entity entity) {
            return entity != null && protects(entity.blockPosition());
        }

        boolean protectsAny(Collection<BlockPos> positions) {
            return positions != null && positions.stream().anyMatch(this::protects);
        }

        boolean crossesPremiseBoundary(BlockPos from, BlockPos to) {
            return !premiseScopes(from).equals(premiseScopes(to));
        }

        boolean canModify(ServerPlayer player, BlockPos pos) {
            if (player == null || pos == null) {
                return false;
            }
            var heist = HeistService.activeAt(player.getServer(), dimension, pos);
            if (heist != null && !player.hasPermissions(3)) {
                return false;
            }
            return BankPremiseProtectionPolicy.decide(
                    index,
                    dimension,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    bankId -> isStaff(player, bankId)
            ).modificationAllowed();
        }

        boolean canModifyAll(ServerPlayer player, Collection<BlockPos> positions) {
            if (positions == null || positions.isEmpty()) {
                return true;
            }
            for (BlockPos pos : positions) {
                if (!canModify(player, pos)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isStaff(ServerPlayer player, UUID bankId) {
            if (player.hasPermissions(3)) {
                return true;
            }
            UUID playerId = player.getUUID();
            if (BankOwnerPcService.canAccessBank(centralBank, playerId, bankId, false)
                    || SafetyDepositBoxService.canManageSafeArea(centralBank, playerId, bankId)) {
                return true;
            }
            return BankStaffingService.hasEmployee(
                    OwnerPcBankReadSupport.metadataSnapshot(centralBank, bankId), playerId);
        }

        private Set<PremiseScope> premiseScopes(BlockPos pos) {
            if (pos == null) {
                return Set.of();
            }
            Set<PremiseScope> scopes = new LinkedHashSet<>();
            for (SafeBoxZoneIndex.Presence presence : index.premisesAt(
                    dimension, pos.getX(), pos.getY(), pos.getZ())) {
                scopes.add(new PremiseScope(
                        presence.record().bankId(), presence.record().premiseId()));
            }
            return Set.copyOf(scopes);
        }
    }

    private record PremiseScope(UUID bankId, String premiseId) {
    }
}
