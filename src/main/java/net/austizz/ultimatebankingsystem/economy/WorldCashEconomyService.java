package net.austizz.ultimatebankingsystem.economy;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerActionAlert;
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
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class WorldCashEconomyService {
    private static final ResourceLocation PILLAGER_OUTPOST_LOOT = new ResourceLocation("minecraft", "chests/pillager_outpost");
    private static final ResourceLocation WOODLAND_MANSION_LOOT = new ResourceLocation("minecraft", "chests/woodland_mansion");
    private static final String VILLAGE_LOOT_PREFIX = "minecraft:chests/village/";
    private static final ConcurrentHashMap<UUID, DeathCashDrop> DEATH_CASH_DROPS = new ConcurrentHashMap<>();

    private record DeathCashDrop(String dimensionId, long expiresAtTick, int totalDropCents) {
    }

    private WorldCashEconomyService() {
    }

    public static void onServerStarting() {
        DEATH_CASH_DROPS.clear();
    }

    public static void onServerStopping() {
        DEATH_CASH_DROPS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (server == null || DEATH_CASH_DROPS.isEmpty()) {
            return;
        }
        int refreshInterval = Math.max(1, Config.DEATH_CASH_DROP_LABEL_REFRESH_TICKS.get());
        boolean refreshLabels = (server.getTickCount() % refreshInterval) == 0;

        for (Map.Entry<UUID, DeathCashDrop> entry : DEATH_CASH_DROPS.entrySet()) {
            UUID dropId = entry.getKey();
            DeathCashDrop drop = entry.getValue();
            if (dropId == null || drop == null) {
                DEATH_CASH_DROPS.remove(dropId);
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(drop.dimensionId()));
            if (level == null) {
                DEATH_CASH_DROPS.remove(dropId);
                continue;
            }
            Entity entity = level.getEntity(dropId);
            if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                DEATH_CASH_DROPS.remove(dropId);
                continue;
            }

            long now = level.getGameTime();
            if (now >= drop.expiresAtTick()) {
                itemEntity.setCustomName(null);
                itemEntity.setCustomNameVisible(false);
                itemEntity.discard();
                DEATH_CASH_DROPS.remove(dropId);
                continue;
            }

            if (refreshLabels) {
                refreshDeathCashLabel(itemEntity, drop.totalDropCents(), now, drop.expiresAtTick());
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
    public static void onLivingDrops(LivingDropsEvent event) {
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
            ServerActionAlert.send(
                    killer,
                    "Cash Drop",
                    "Dropped $" + DollarBills.formatCents(dropCents) + " from " + mobName + ".",
                    DeliveryAlertPayload.AlertTone.SUCCESS,
                    3600
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event == null || event.isCanceled() || !Config.DEATH_CASH_DROP_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isDeathCashModeAllowed(player)) {
            return;
        }

        boolean keepInventory = player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        if (keepInventory && !Config.DEATH_CASH_DROP_APPLY_WITH_KEEP_INVENTORY.get()) {
            return;
        }

        int carriedCashCents = countCarriedCashCents(player);
        if (carriedCashCents <= 0) {
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

        // Remove selected tender before vanilla death processing so only the configured share is lost.
        DollarBills.removeCash(player, extractionPlan);
        spawnDeathCashDrops(player.serverLevel(), player, extractionPlan, actualDropCents);

        // Explicitly notify the player that physical cash was dropped and will despawn on timer.
        int despawnTicks = Math.max(20, Config.DEATH_CASH_DROP_DESPAWN_TICKS.get());
        ServerActionAlert.send(
                player,
                "Death Cash Drop",
                "Dropped $" + DollarBills.formatCents(actualDropCents)
                        + " on death. Despawns in " + formatTickCountdown(despawnTicks) + ".",
                DeliveryAlertPayload.AlertTone.WARNING,
                4200
        );
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

    private static double readAttribute(net.minecraft.world.entity.LivingEntity entity, Attribute attribute) {
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

    private static void spawnDeathCashDrops(ServerLevel level,
                                            ServerPlayer player,
                                            int[] extractionPlan,
                                            int totalDropCents) {
        if (level == null || player == null || extractionPlan == null || totalDropCents <= 0) {
            return;
        }
        long now = level.getGameTime();
        long expiresAt = now + Math.max(20, Config.DEATH_CASH_DROP_DESPAWN_TICKS.get());

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
            refreshDeathCashLabel(drop, totalDropCents, now, expiresAt);
            level.addFreshEntity(drop);
            DEATH_CASH_DROPS.put(
                    drop.getUUID(),
                    new DeathCashDrop(level.dimension().location().toString(), expiresAt, totalDropCents)
            );
        }
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
            int maxStack = Math.max(1, cashItem.getMaxStackSize());
            while (remaining > 0) {
                int give = Math.min(maxStack, remaining);
                stacks.add(new ItemStack(cashItem, give));
                remaining -= give;
            }
        }
        return stacks;
    }

    private static void refreshDeathCashLabel(ItemEntity itemEntity, int cents, long nowTick, long expiresAtTick) {
        if (itemEntity == null) {
            return;
        }
        long remaining = Math.max(0L, expiresAtTick - nowTick);
        String amount = "$" + DollarBills.formatCents(Math.max(0, cents));
        itemEntity.setCustomName(Component.literal("Cash Drop: " + amount + " | ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("despawns in " + formatTickCountdown(remaining))
                        .withStyle(ChatFormatting.YELLOW)));
        itemEntity.setCustomNameVisible(true);
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
