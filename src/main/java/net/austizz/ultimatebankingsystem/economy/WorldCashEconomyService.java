package net.austizz.ultimatebankingsystem.economy;

import net.minecraft.core.Holder;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerNotification;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class WorldCashEconomyService {
    private static final ResourceLocation PILLAGER_OUTPOST_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/pillager_outpost");
    private static final ResourceLocation WOODLAND_MANSION_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/woodland_mansion");
    private static final String VILLAGE_LOOT_PREFIX = "minecraft:chests/village/";
    private static final ConcurrentHashMap<UUID, DeathCashDropGroup> DEATH_CASH_DROP_GROUPS = new ConcurrentHashMap<>();

    private record DeathCashDropGroup(String dimensionId, long expiresAtTick, List<UUID> entityIds) {
        private DeathCashDropGroup {
            entityIds = List.copyOf(entityIds);
        }
    }

    private WorldCashEconomyService() {
    }

    public static void onServerStarting() {
        DEATH_CASH_DROP_GROUPS.clear();
    }

    public static void onServerStopping() {
        DEATH_CASH_DROP_GROUPS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (server == null || DEATH_CASH_DROP_GROUPS.isEmpty()) {
            return;
        }
        int refreshInterval = Math.max(1, Config.DEATH_CASH_DROP_LABEL_REFRESH_TICKS.get());
        boolean refreshLabels = (server.getTickCount() % refreshInterval) == 0;

        for (Map.Entry<UUID, DeathCashDropGroup> entry : DEATH_CASH_DROP_GROUPS.entrySet()) {
            UUID groupId = entry.getKey();
            DeathCashDropGroup group = entry.getValue();
            if (groupId == null || group == null) {
                DEATH_CASH_DROP_GROUPS.remove(groupId);
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(group.dimensionId()));
            if (level == null) {
                DEATH_CASH_DROP_GROUPS.remove(groupId);
                continue;
            }
            long now = level.getGameTime();
            List<ItemEntity> liveDrops = resolveLiveCashDrops(level, group.entityIds());
            if (now >= group.expiresAtTick()) {
                liveDrops.forEach(WorldCashEconomyService::discardManagedCashDrop);
                DEATH_CASH_DROP_GROUPS.remove(groupId);
                continue;
            }
            if (liveDrops.isEmpty()) {
                DEATH_CASH_DROP_GROUPS.remove(groupId);
                continue;
            }
            if (refreshLabels) {
                refreshDeathCashGroupLabel(liveDrops, now, group.expiresAtTick());
            }
        }
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (event == null || !Config.CHEST_CASH_LOOT_ENABLED.get()) {
            return;
        }
        ResourceLocation lootTableId = event.getName();
        if (!isTargetedStructureLoot(lootTableId)) {
            return;
        }

        float chance = Math.max(0, Math.min(100, Config.CHEST_CASH_LOOT_CHANCE_PERCENT.get())) / 100.0F;
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(LootItem.lootTableItem(ModItems.ONE_DOLLAR_BILL.get()).setWeight(26).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 7.0F))))
                .add(LootItem.lootTableItem(ModItems.TWO_DOLLAR_BILL.get()).setWeight(18).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 4.0F))))
                .add(LootItem.lootTableItem(ModItems.FIVE_DOLLAR_BILL.get()).setWeight(13).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 3.0F))))
                .add(LootItem.lootTableItem(ModItems.TEN_DOLLAR_BILL.get()).setWeight(9).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F))))
                .add(LootItem.lootTableItem(ModItems.TWENTY_DOLLAR_BILL.get()).setWeight(5).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 1.0F))))
                .add(LootItem.lootTableItem(ModItems.QUARTER_COIN.get()).setWeight(8).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 10.0F))))
                .add(LootItem.lootTableItem(ModItems.DIME_COIN.get()).setWeight(8).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(3.0F, 12.0F))))
                .add(LootItem.lootTableItem(ModItems.NICKEL_COIN.get()).setWeight(6).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 14.0F))))
                .add(LootItem.lootTableItem(ModItems.PENNY_COIN.get()).setWeight(4).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(6.0F, 20.0F))));

        event.getTable().addPool(pool.build());
    }

    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        if (event == null || !Config.MOB_CASH_DROPS_ENABLED.get()) {
            return;
        }
        net.minecraft.world.entity.LivingEntity living = event.getEntity();
        if (living == null || living.level().isClientSide()) {
            return;
        }
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isEligibleMobForCashDrop(living)) {
            return;
        }
        if (Config.MOB_CASH_DROPS_PLAYER_KILL_ONLY.get() && !isPlayerCausedKill(event)) {
            return;
        }
        ServerPlayer killer = findResponsiblePlayer(event);

        int dropCents = computeMobDropCents(living);
        if (dropCents <= 0) {
            return;
        }

        for (ItemStack stack : buildCashStacksForCents(dropCents)) {
            if (stack.isEmpty()) {
                continue;
            }
            event.getDrops().add(new ItemEntity(
                    level,
                    living.getX(),
                    living.getY() + 0.10D,
                    living.getZ(),
                    stack
            ));
        }

        // Surface world-economy rewards clearly so players can identify cash-yielding kills.
        if (killer != null && dropCents > 0) {
            String mobName = living.getName() == null ? "mob" : living.getName().getString();
            ServerNotification.send(
                    killer,
                    "Cash Drop",
                    "Dropped $" + DollarBills.formatCents(dropCents) + " from " + mobName + ".",
                    DeliveryAlertPayload.AlertTone.SUCCESS,
                    3600
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (event == null || !Config.DEATH_CASH_DROP_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isDeathCashModeAllowed(player)) {
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        List<ItemEntity> existingCashDrops = collectCashDrops(event.getDrops());
        boolean keepInventory = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        int carriedCashCents = countCarriedCashCents(player);
        DeathCashDropPolicy.Decision decision = DeathCashDropPolicy.decide(
                event.isCanceled(),
                !existingCashDrops.isEmpty(),
                keepInventory,
                Config.DEATH_CASH_DROP_APPLY_WITH_KEEP_INVENTORY.get(),
                carriedCashCents > 0
        );

        if (decision == DeathCashDropPolicy.Decision.SKIP) {
            return;
        }
        if (decision == DeathCashDropPolicy.Decision.MANAGE_EXISTING_DROPS) {
            long totalDropCents = registerDeathCashDropGroup(level, existingCashDrops);
            notifyDeathCashDrop(player, totalDropCents);
            return;
        }

        int percent = Math.max(0, Math.min(100, Config.DEATH_CASH_DROP_PERCENT.get()));
        int requestedDropCents = (int) Math.floor(carriedCashCents * (percent / 100.0D));
        if (percent > 0 && requestedDropCents <= 0) {
            requestedDropCents = 1;
        }
        requestedDropCents = Math.min(carriedCashCents, requestedDropCents);
        if (requestedDropCents <= 0) {
            return;
        }

        int[] availableCashCounts = DollarBills.getAvailableCashCounts(player);
        int[] extractionPlan = DollarBills.findCashDepositPlan(requestedDropCents, availableCashCounts);
        if (extractionPlan == null) {
            return;
        }

        int actualDropCents = DollarBills.totalCashValueCents(extractionPlan);
        if (actualDropCents <= 0) {
            return;
        }

        // This branch is only reached for vanilla keepInventory after every death-drop handler ran.
        DollarBills.removeCash(player, extractionPlan);
        List<ItemEntity> forcedDrops = createDeathCashDrops(level, player, extractionPlan);
        event.getDrops().addAll(forcedDrops);
        registerDeathCashDropGroup(level, forcedDrops);
        notifyDeathCashDrop(player, actualDropCents);
    }

    private static boolean isTargetedStructureLoot(ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return false;
        }
        if (PILLAGER_OUTPOST_LOOT.equals(lootTableId) || WOODLAND_MANSION_LOOT.equals(lootTableId)) {
            return true;
        }
        String raw = lootTableId.toString();
        return raw.startsWith(VILLAGE_LOOT_PREFIX);
    }

    private static boolean isEligibleMobForCashDrop(net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null || entity instanceof ServerPlayer) {
            return false;
        }
        return entity instanceof Villager || entity instanceof Enemy;
    }

    private static boolean isPlayerCausedKill(LivingDropsEvent event) {
        if (event == null || event.getSource() == null) {
            return false;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer) {
            return true;
        }
        if (attacker instanceof TamableAnimal tamable && tamable.getOwner() instanceof ServerPlayer) {
            return true;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer) {
            return true;
        }
        return direct instanceof TamableAnimal tamableDirect
                && tamableDirect.getOwner() instanceof ServerPlayer;
    }

    private static ServerPlayer findResponsiblePlayer(LivingDropsEvent event) {
        if (event == null || event.getSource() == null) {
            return null;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        if (attacker instanceof TamableAnimal tamable && tamable.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (direct instanceof TamableAnimal tamable && tamable.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    private static int computeMobDropCents(net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null) {
            return 0;
        }

        double difficultyMultiplier = switch (entity.level().getDifficulty()) {
            case PEACEFUL -> 0.0D;
            case EASY -> 0.80D;
            case NORMAL -> 1.00D;
            case HARD -> 1.25D;
        };
        if (difficultyMultiplier <= 0.0D) {
            return 0;
        }

        double health = Math.max(1.0D, entity.getMaxHealth());
        double attack = readAttribute(entity, Attributes.ATTACK_DAMAGE);
        double armor = readAttribute(entity, Attributes.ARMOR);

        double baseCents = (health * Config.MOB_CASH_DROP_HEALTH_WEIGHT.get())
                + (attack * Config.MOB_CASH_DROP_ATTACK_WEIGHT.get())
                + (armor * Config.MOB_CASH_DROP_ARMOR_WEIGHT.get());

        int variancePercent = Math.max(0, Config.MOB_CASH_DROP_VARIANCE_PERCENT.get());
        double varianceWindow = variancePercent / 100.0D;
        double varianceScale = 1.0D + ((entity.getRandom().nextDouble() * 2.0D) - 1.0D) * varianceWindow;

        double adjusted = baseCents * difficultyMultiplier * Math.max(0.0D, varianceScale);
        int computed = (int) Math.round(adjusted);

        int minCents = Math.max(0, Config.MOB_CASH_DROP_MIN_CENTS.get());
        int maxCents = Math.max(minCents, Config.MOB_CASH_DROP_MAX_CENTS.get());
        if (computed < minCents) {
            computed = minCents;
        }
        if (computed > maxCents) {
            computed = maxCents;
        }
        return computed;
    }

    private static double readAttribute(net.minecraft.world.entity.LivingEntity entity, Holder<Attribute> attribute) {
        if (entity == null || attribute == null) {
            return 0.0D;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return 0.0D;
        }
        return Math.max(0.0D, instance.getValue());
    }

    private static boolean isDeathCashModeAllowed(ServerPlayer player) {
        if (player == null || player.gameMode == null) {
            return false;
        }
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
    }

    private static int countCarriedCashCents(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        long total = 0L;
        total += countCashCentsInStacks(player.getInventory().items);
        total += countCashCentsInStacks(player.getInventory().offhand);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, total));
    }

    private static long countCashCentsInStacks(NonNullList<ItemStack> stacks) {
        if (stacks == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int cents = DollarBills.cashCentsForItem(stack.getItem());
            if (cents <= 0) {
                continue;
            }
            total += (long) cents * Math.max(0, stack.getCount());
        }
        return total;
    }

    private static List<ItemEntity> createDeathCashDrops(ServerLevel level,
                                                         ServerPlayer player,
                                                         int[] extractionPlan) {
        List<ItemEntity> drops = new ArrayList<>();
        if (level == null || player == null || extractionPlan == null) {
            return drops;
        }
        for (ItemStack stack : buildCashStacksFromPlan(extractionPlan)) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity drop = new ItemEntity(
                    level,
                    player.getX(),
                    player.getY() + 0.15D,
                    player.getZ(),
                    stack
            );
            drop.setDeltaMovement(
                    (level.random.nextDouble() - 0.5D) * 0.06D,
                    0.08D + (level.random.nextDouble() * 0.05D),
                    (level.random.nextDouble() - 0.5D) * 0.06D
            );
            drop.setPickUpDelay(0);
            drops.add(drop);
        }
        return drops;
    }

    private static List<ItemStack> buildCashStacksForCents(int cents) {
        if (cents <= 0) {
            return List.of();
        }
        int[] plan = DollarBills.buildCashWithdrawPlan(cents);
        if (plan == null) {
            return List.of();
        }
        return buildCashStacksFromPlan(plan);
    }

    private static List<ItemStack> buildCashStacksFromPlan(int[] plan) {
        List<ItemStack> stacks = new ArrayList<>();
        if (plan == null) {
            return stacks;
        }
        for (int i = 0; i < plan.length; i++) {
            int remaining = Math.max(0, plan[i]);
            if (remaining <= 0) {
                continue;
            }
            int denominationCents = DollarBills.cashDenominationCentsForIndex(i);
            Item cashItem = DollarBills.getCashItemForDenominationCents(denominationCents);
            if (cashItem == null) {
                continue;
            }
            int maxStack = Math.max(1, new ItemStack(cashItem).getMaxStackSize());
            while (remaining > 0) {
                int give = Math.min(maxStack, remaining);
                stacks.add(new ItemStack(cashItem, give));
                remaining -= give;
            }
        }
        return stacks;
    }

    private static List<ItemEntity> collectCashDrops(Collection<ItemEntity> drops) {
        if (drops == null || drops.isEmpty()) {
            return List.of();
        }
        List<ItemEntity> cashDrops = new ArrayList<>();
        for (ItemEntity drop : drops) {
            if (cashDropValueCents(drop) > 0L) {
                cashDrops.add(drop);
            }
        }
        return cashDrops;
    }

    private static long registerDeathCashDropGroup(ServerLevel level, List<ItemEntity> cashDrops) {
        if (level == null || cashDrops == null || cashDrops.isEmpty()) {
            return 0L;
        }
        long expiresAt = level.getGameTime() + Math.max(20, Config.DEATH_CASH_DROP_DESPAWN_TICKS.get());
        List<UUID> entityIds = cashDrops.stream()
                .filter(drop -> drop != null && cashDropValueCents(drop) > 0L)
                .map(Entity::getUUID)
                .distinct()
                .toList();
        if (entityIds.isEmpty()) {
            return 0L;
        }
        UUID groupId = UUID.randomUUID();
        DEATH_CASH_DROP_GROUPS.put(groupId, new DeathCashDropGroup(
                level.dimension().location().toString(),
                expiresAt,
                entityIds
        ));
        refreshDeathCashGroupLabel(cashDrops, level.getGameTime(), expiresAt);
        return totalCashDropCents(cashDrops);
    }

    private static List<ItemEntity> resolveLiveCashDrops(ServerLevel level, List<UUID> entityIds) {
        if (level == null || entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        List<ItemEntity> drops = new ArrayList<>();
        for (UUID entityId : entityIds) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof ItemEntity itemEntity && itemEntity.isAlive() && cashDropValueCents(itemEntity) > 0L) {
                drops.add(itemEntity);
            }
        }
        return drops;
    }

    private static void refreshDeathCashGroupLabel(List<ItemEntity> cashDrops, long nowTick, long expiresAtTick) {
        if (cashDrops == null || cashDrops.isEmpty()) {
            return;
        }
        List<ItemEntity> liveDrops = cashDrops.stream()
                .filter(drop -> drop != null && drop.isAlive() && cashDropValueCents(drop) > 0L)
                .toList();
        if (liveDrops.isEmpty()) {
            return;
        }
        ItemEntity labelDrop = liveDrops.getFirst();
        long cents = totalCashDropCents(liveDrops);
        int itemCount = liveDrops.stream().mapToInt(drop -> Math.max(0, drop.getItem().getCount())).sum();
        long remaining = Math.max(0L, expiresAtTick - nowTick);
        String amount = "$" + formatCashCents(cents);
        labelDrop.setCustomName(Component.literal("Cash Drop: " + amount + " | " + itemCount + " item" + (itemCount == 1 ? "" : "s") + " | ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("despawns in " + formatTickCountdown(remaining))
                        .withStyle(ChatFormatting.YELLOW)));
        labelDrop.setCustomNameVisible(true);
        for (int i = 1; i < liveDrops.size(); i++) {
            clearDeathCashLabel(liveDrops.get(i));
        }
    }

    private static long totalCashDropCents(List<ItemEntity> cashDrops) {
        if (cashDrops == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemEntity drop : cashDrops) {
            long value = cashDropValueCents(drop);
            if (value <= 0L || Long.MAX_VALUE - total < value) {
                return value > 0L ? Long.MAX_VALUE : total;
            }
            total += value;
        }
        return total;
    }

    private static long cashDropValueCents(ItemEntity drop) {
        if (drop == null || drop.getItem().isEmpty()) {
            return 0L;
        }
        long unitValue = DollarBills.physicalTenderCents(drop.getItem().getItem());
        if (unitValue <= 0L) {
            return 0L;
        }
        int count = Math.max(0, drop.getItem().getCount());
        return unitValue > Long.MAX_VALUE / Math.max(1, count) ? Long.MAX_VALUE : unitValue * count;
    }

    private static void discardManagedCashDrop(ItemEntity itemEntity) {
        clearDeathCashLabel(itemEntity);
        if (itemEntity != null && itemEntity.isAlive()) {
            itemEntity.discard();
        }
    }

    private static void clearDeathCashLabel(ItemEntity itemEntity) {
        if (itemEntity == null) {
            return;
        }
        itemEntity.setCustomName(null);
        itemEntity.setCustomNameVisible(false);
    }

    private static void notifyDeathCashDrop(ServerPlayer player, long dropCents) {
        if (player == null || dropCents <= 0L) {
            return;
        }
        int despawnTicks = Math.max(20, Config.DEATH_CASH_DROP_DESPAWN_TICKS.get());
        ServerNotification.send(
                player,
                "Death Cash Drop",
                "Dropped $" + formatCashCents(dropCents)
                        + " on death. Despawns in " + formatTickCountdown(despawnTicks) + ".",
                DeliveryAlertPayload.AlertTone.WARNING,
                4200
        );
    }

    private static String formatCashCents(long cents) {
        long safeCents = Math.max(0L, cents);
        if (safeCents % 100L == 0L) {
            return Long.toString(safeCents / 100L);
        }
        return BigDecimal.valueOf(safeCents, 2).toPlainString();
    }

    private static String formatTickCountdown(long ticksRemaining) {
        long totalTenths = Math.max(0L, (ticksRemaining + 1L) / 2L);
        long minutes = totalTenths / 600L;
        long seconds = (totalTenths / 10L) % 60L;
        long tenths = totalTenths % 10L;
        return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths);
    }

    private static net.minecraft.resources.ResourceKey<Level> serverLevelKey(String dimId) {
        ResourceLocation id = ResourceLocation.tryParse(dimId == null ? "" : dimId.trim());
        if (id == null) {
            id = Level.OVERWORLD.location();
        }
        return RegistryKeysCompat.createValueKey(RegistryKeysCompat.DIMENSION_REGISTRY_KEY, id);
    }
}
