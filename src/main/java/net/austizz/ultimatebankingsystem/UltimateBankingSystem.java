package net.austizz.ultimatebankingsystem;

import com.mojang.logging.LogUtils;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.BankRegulationService;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.command.UBSCommands;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.economy.WorldCashEconomyService;
import net.austizz.ultimatebankingsystem.item.ModCreativeTabs;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.loan.LoanService;
import net.austizz.ultimatebankingsystem.menu.ModMenus;
import net.austizz.ultimatebankingsystem.npc.BankTellerInteractionManager;
import net.austizz.ultimatebankingsystem.npc.BankTellerPaymentInteractionManager;
import net.austizz.ultimatebankingsystem.npc.ShopCashierInteractionManager;
import net.austizz.ultimatebankingsystem.payments.ScheduledPaymentService;
import net.austizz.ultimatebankingsystem.pickpocket.PickpocketService;
import net.austizz.ultimatebankingsystem.shelf.ShelfBasketSessionService;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.network.ForgePayloadNetwork;
import net.austizz.ultimatebankingsystem.network.HudStatePayload;
import net.austizz.ultimatebankingsystem.network.ModPayloads;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(UltimateBankingSystem.MODID)
public class UltimateBankingSystem {
    public static final String MODID = "ultimatebankingsystem";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final int PERF_WINDOW_TICKS = 600; // 30s at 20 TPS.
    private static final double TARGET_MSPT = 50.0D;
    private static volatile UltimateBankingSystem INSTANCE;

    private long lastAutosaveTick = -1L;
    private long lastInterestTick = -1L;
    private long lastHudSyncTick = -1L;
    private final ConcurrentHashMap<UUID, String> hudStateCache = new ConcurrentHashMap<>();
    private final ArrayDeque<Long> modTickWindowNanos = new ArrayDeque<>();
    private final ArrayDeque<Long> modAllocWindowBytes = new ArrayDeque<>();
    private long modTickWindowTotalNanos;
    private long modAllocWindowTotalBytes;
    private long modSamplesTotal;
    private long modLastTickNanos;
    private long modLastAllocBytes;
    private long modLastSampleEpochMillis;
    private long modLastHeapUsedBytes = -1L;
    private Object webAdminService;
    private boolean webAdminUnavailableLogged;

    public UltimateBankingSystem() {
        INSTANCE = this;
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModMenus.register(modEventBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static UltimateBankingSystem getInstance() {
        return INSTANCE;
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ForgePayloadNetwork.init();
            ModPayloads.register(new RegisterPayloadHandlersEvent());
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerAboutToStartEvent event) {
        BankManager.init(event.getServer());
        PickpocketService.onServerStarting();
        WorldCashEconomyService.onServerStarting();
        lastAutosaveTick = -1L;
        lastInterestTick = -1L;
        lastHudSyncTick = -1L;
        hudStateCache.clear();
        resetPerformanceAnalytics();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        BankManager.init(event.getServer());
        if (Config.WEB_ADMIN_ENABLED.get()) {
            startWebAdmin(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        hudStateCache.clear();
        resetPerformanceAnalytics();
        PickpocketService.onServerStopping();
        WorldCashEconomyService.onServerStopping();
        stopWebAdmin();
        BankManager.shutdown();
    }

    /**
     * Runtime toggle used by admin commands so owners can enable/disable the panel
     * without rebooting the server process.
     */
    public synchronized boolean setWebAdminEnabled(MinecraftServer server, boolean enabled) {
        Config.WEB_ADMIN_ENABLED.set(enabled);
        persistConfigChanges();

        if (enabled) {
            webAdminUnavailableLogged = false;
            startWebAdmin(server);
        } else {
            stopWebAdmin();
        }
        return isWebAdminRunning();
    }

    public synchronized boolean isWebAdminRunning() {
        Object running = invokeWebAdminMethod("isRunning");
        return running instanceof Boolean value && value;
    }

    public synchronized String getWebAdminBindHost() {
        Object host = invokeWebAdminMethod("bindHost");
        if (host instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        String configured = Config.WEB_ADMIN_BIND_HOST.get();
        return configured == null || configured.isBlank() ? "0.0.0.0" : configured.trim();
    }

    public synchronized int getWebAdminBindPort() {
        Object port = invokeWebAdminMethod("bindPort");
        if (port instanceof Number number) {
            int value = number.intValue();
            if (value >= 1 && value <= 65535) {
                return value;
            }
        }
        int configured = Config.WEB_ADMIN_PORT.get();
        return Math.max(1, Math.min(65535, configured));
    }

    public synchronized String getWebAdminDisplayUrl() {
        String host = getWebAdminBindHost();
        if (isWildcardHost(host)) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + getWebAdminBindPort() + "/ubs-admin/";
    }

    private synchronized void startWebAdmin(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            if (webAdminService == null) {
                Class<?> serviceClass = Class.forName("net.austizz.ultimatebankingsystem.webadmin.WebAdminService");
                webAdminService = serviceClass.getDeclaredConstructor().newInstance();
            }
            webAdminService.getClass()
                    .getMethod("start", MinecraftServer.class)
                    .invoke(webAdminService, server);
        } catch (Throwable throwable) {
            if (!webAdminUnavailableLogged) {
                webAdminUnavailableLogged = true;
                LOGGER.error("[UBS WebAdmin] Disabled due to missing runtime dependencies or startup failure.", throwable);
            }
            webAdminService = null;
        }
    }

    private synchronized void stopWebAdmin() {
        if (webAdminService == null) {
            return;
        }
        try {
            webAdminService.getClass()
                    .getMethod("stop")
                    .invoke(webAdminService);
        } catch (Throwable throwable) {
            LOGGER.warn("[UBS WebAdmin] Failed to stop cleanly: {}", throwable.getMessage());
        } finally {
            webAdminService = null;
        }
    }

    private void persistConfigChanges() {
        try {
            Config.SPEC.getClass().getMethod("save").invoke(Config.SPEC);
        } catch (Throwable ignored) {
            // Keep runtime toggles working even if the config backend does not support direct save calls.
        }
    }

    private static boolean isWildcardHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String cleaned = host.trim();
        return "0.0.0.0".equals(cleaned)
                || "::".equals(cleaned)
                || "[::]".equals(cleaned)
                || "*".equals(cleaned);
    }

    private Object invokeWebAdminMethod(String methodName) {
        if (webAdminService == null || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            return webAdminService.getClass().getMethod(methodName).invoke(webAdminService);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        long tickStartedAtNanos = System.nanoTime();
        try {
            long gameTime = overworld.getGameTime();

            LoanService.processRepayments(server, gameTime);
            ScheduledPaymentService.process(server, gameTime);
            BankRegulationService.process(server, gameTime);
            BankTellerInteractionManager.tick(server);
            BankTellerPaymentInteractionManager.tick(server);
            ShopCashierInteractionManager.tick(server);
            ShopService.tickSessions(server);
            ShelfBasketSessionService.tick(server);
            PickpocketService.tick(server);
            WorldCashEconomyService.tick(server);

            long autosaveIntervalTicks = Math.max(1, Config.AUTOSAVE_INTERVAL_MINUTES.get()) * 60L * 20L;
            if (gameTime % autosaveIntervalTicks == 0L && gameTime != lastAutosaveTick) {
                BankManager.markDirty();
                lastAutosaveTick = gameTime;
            }

            CentralBank centralBank = BankManager.getCentralBank(server);
            if (gameTime % 20L == 0L && gameTime != lastHudSyncTick) {
                syncHudStates(server, centralBank);
                lastHudSyncTick = gameTime;
            }

            long interestIntervalTicks = Math.max(20, Config.SAVINGS_INTEREST_INTERVAL_TICKS.get());
            if (gameTime % interestIntervalTicks != 0L || gameTime == lastInterestTick) {
                return;
            }

            if (centralBank == null) {
                return;
            }

            for (Bank bank : centralBank.getBanks().values()) {
                bank.payInterestAllSavingAccounts();
            }
            BankManager.markDirty();
            lastInterestTick = gameTime;
        } finally {
            // Capture rolling mod-impact timing so web admin can report UBS tick pressure.
            recordPerformanceSample(System.nanoTime() - tickStartedAtNanos);
        }
    }

    private synchronized void resetPerformanceAnalytics() {
        modTickWindowNanos.clear();
        modAllocWindowBytes.clear();
        modTickWindowTotalNanos = 0L;
        modAllocWindowTotalBytes = 0L;
        modSamplesTotal = 0L;
        modLastTickNanos = 0L;
        modLastAllocBytes = 0L;
        modLastSampleEpochMillis = 0L;
        modLastHeapUsedBytes = -1L;
    }

    /**
     * Record rolling timing/allocation samples for the mod's server tick workload.
     * Allocation is best-effort and represents observed heap growth between ticks.
     */
    private synchronized void recordPerformanceSample(long elapsedNanos) {
        long safeNanos = Math.max(0L, elapsedNanos);
        modTickWindowNanos.addLast(safeNanos);
        modTickWindowTotalNanos += safeNanos;
        while (modTickWindowNanos.size() > PERF_WINDOW_TICKS) {
            Long removed = modTickWindowNanos.pollFirst();
            if (removed != null) {
                modTickWindowTotalNanos -= removed;
            }
        }

        Runtime runtime = Runtime.getRuntime();
        long usedHeapBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long observedAllocBytes = 0L;
        if (modLastHeapUsedBytes >= 0L && usedHeapBytes > modLastHeapUsedBytes) {
            observedAllocBytes = usedHeapBytes - modLastHeapUsedBytes;
        }
        modLastHeapUsedBytes = usedHeapBytes;

        modAllocWindowBytes.addLast(observedAllocBytes);
        modAllocWindowTotalBytes += observedAllocBytes;
        while (modAllocWindowBytes.size() > PERF_WINDOW_TICKS) {
            Long removed = modAllocWindowBytes.pollFirst();
            if (removed != null) {
                modAllocWindowTotalBytes -= removed;
            }
        }

        modLastTickNanos = safeNanos;
        modLastAllocBytes = observedAllocBytes;
        modLastSampleEpochMillis = System.currentTimeMillis();
        modSamplesTotal++;
    }

    public synchronized Map<String, Object> buildPerformanceSnapshot(MinecraftServer server) {
        Runtime runtime = Runtime.getRuntime();
        long heapUsedBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long heapCommittedBytes = Math.max(0L, runtime.totalMemory());
        long heapMaxBytes = Math.max(0L, runtime.maxMemory());

        int sampleSize = modTickWindowNanos.size();
        double avgModMspt = sampleSize > 0
                ? (modTickWindowTotalNanos / (double) sampleSize) / 1_000_000.0D
                : 0.0D;
        double lastModMspt = modLastTickNanos / 1_000_000.0D;
        double avgAllocBytesPerTick = sampleSize > 0
                ? (modAllocWindowTotalBytes / (double) sampleSize)
                : 0.0D;
        double avgAllocMbPerTick = avgAllocBytesPerTick / (1024.0D * 1024.0D);

        double maxModMspt = 0.0D;
        List<Long> sorted = new ArrayList<>(modTickWindowNanos);
        for (Long sample : sorted) {
            if (sample != null) {
                maxModMspt = Math.max(maxModMspt, sample / 1_000_000.0D);
            }
        }
        sorted.sort(Long::compareTo);
        double p95ModMspt = percentileMs(sorted, 95);

        double serverAvgMspt = server == null ? 0.0D : Math.max(0.0D, server.getAverageTickTime());
        double estimatedTps = serverAvgMspt <= 0.0D
                ? 20.0D
                : clampDouble(1000.0D / serverAvgMspt, 0.0D, 20.0D);

        double modTickBudgetPct = clampDouble((avgModMspt / TARGET_MSPT) * 100.0D, 0.0D, 100.0D);
        double modShareOfServerTickPct = serverAvgMspt > 0.0D
                ? clampDouble((avgModMspt / serverAvgMspt) * 100.0D, 0.0D, 100.0D)
                : 0.0D;

        double overloadMspt = Math.max(0.0D, serverAvgMspt - TARGET_MSPT);
        double modEstimatedLagSharePct = overloadMspt > 0.0D
                ? clampDouble((Math.min(avgModMspt, overloadMspt) / overloadMspt) * 100.0D, 0.0D, 100.0D)
                : 0.0D;
        double modAllocHeapSharePct = heapUsedBytes > 0L
                ? clampDouble((avgAllocBytesPerTick / (double) heapUsedBytes) * 100.0D, 0.0D, 100.0D)
                : 0.0D;
        double heapUsedPct = heapMaxBytes > 0L
                ? clampDouble((heapUsedBytes / (double) heapMaxBytes) * 100.0D, 0.0D, 100.0D)
                : 0.0D;

        double processCpuLoadPct = -1.0D;
        try {
            java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
            if (baseBean instanceof com.sun.management.OperatingSystemMXBean bean) {
                double raw = bean.getProcessCpuLoad();
                if (raw >= 0.0D) {
                    processCpuLoadPct = clampDouble(raw * 100.0D, 0.0D, 100.0D);
                }
            }
        } catch (Throwable ignored) {
            processCpuLoadPct = -1.0D;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetMspt", TARGET_MSPT);
        payload.put("serverAvgMspt", round2(serverAvgMspt));
        payload.put("serverEstimatedTps", round2(estimatedTps));
        payload.put("processCpuLoadPct", round2(processCpuLoadPct));
        payload.put("jvmHeapUsedBytes", heapUsedBytes);
        payload.put("jvmHeapCommittedBytes", heapCommittedBytes);
        payload.put("jvmHeapMaxBytes", heapMaxBytes);
        payload.put("jvmHeapUsedPct", round2(heapUsedPct));

        payload.put("sampleSizeTicks", sampleSize);
        payload.put("sampledTicksTotal", modSamplesTotal);
        payload.put("lastSampleEpochMillis", modLastSampleEpochMillis);
        payload.put("modLastMspt", round2(lastModMspt));
        payload.put("modAvgMspt", round2(avgModMspt));
        payload.put("modP95Mspt", round2(p95ModMspt));
        payload.put("modMaxMspt", round2(maxModMspt));
        payload.put("modTickBudgetPct", round2(modTickBudgetPct));
        payload.put("modShareOfServerTickPct", round2(modShareOfServerTickPct));
        payload.put("modEstimatedLagSharePct", round2(modEstimatedLagSharePct));
        payload.put("modLastAllocBytes", modLastAllocBytes);
        payload.put("modAvgAllocBytesPerTick", Math.round(avgAllocBytesPerTick));
        payload.put("modAvgAllocMbPerTick", round2(avgAllocMbPerTick));
        payload.put("modAllocHeapSharePct", round2(modAllocHeapSharePct));
        return payload;
    }

    private static double percentileMs(List<Long> sortedNanos, int percentile) {
        if (sortedNanos == null || sortedNanos.isEmpty()) {
            return 0.0D;
        }
        int safePercentile = Math.max(0, Math.min(100, percentile));
        int index = (int) Math.ceil((safePercentile / 100.0D) * sortedNanos.size()) - 1;
        int safeIndex = Math.max(0, Math.min(sortedNanos.size() - 1, index));
        Long sample = sortedNanos.get(safeIndex);
        if (sample == null) {
            return 0.0D;
        }
        return sample / 1_000_000.0D;
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    @SubscribeEvent
    public void onBalanceChanged(BalanceChangedEvent event) {
        char checkmark = '\u2705';
        char cross = '\u274C';
        String message = event.isPositiveNumber()
                ? "§a" + checkmark + " Deposit Successful! You have received: $" + MoneyText.abbreviate(event.getChangeAmount()) + " into your bank account. Current Balance: §6$" + MoneyText.abbreviate(event.getNewBalance()) + "\n §aAccount ID: §6" + event.getAccount().getAccountUUID()
                : "§c" + cross + " Withdrawal Notice: $" + MoneyText.abbreviate(event.getChangeAmount()) + " has been deducted from your account. Current Balance: §6$" + MoneyText.abbreviate(event.getNewBalance()) + "\n §cAccount ID: §6" + event.getAccount().getAccountUUID();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        var targetPlayer = server.getPlayerList().getPlayer(event.getAccount().getPlayerUUID());
        if (targetPlayer == null) {
            return;
        }
        targetPlayer.sendSystemMessage(Component.literal(MoneyText.abbreviateCurrencyTokens(message)));
        CentralBank centralBank = BankManager.getCentralBank(server);
        HudStatePayload payload = UBSCommands.buildHudStatePayload(centralBank, targetPlayer.getUUID());
        PacketDistributor.sendToPlayer(targetPlayer, payload);
        hudStateCache.put(targetPlayer.getUUID(), payload.enabled() + "|" + payload.balance());
    }

    private void syncHudStates(net.minecraft.server.MinecraftServer server, CentralBank centralBank) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }
            UUID playerId = player.getUUID();
            online.add(playerId);
            HudStatePayload payload = UBSCommands.buildHudStatePayload(centralBank, playerId);
            String signature = payload.enabled() + "|" + payload.balance();
            String previous = hudStateCache.put(playerId, signature);
            if (!signature.equals(previous)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        hudStateCache.keySet().removeIf(id -> !online.contains(id));
        UBSCommands.clearHudMonitorOverridesForMissingPlayers(online);
        UBSCommands.clearHudStateForMissingPlayers(online);
    }
}
