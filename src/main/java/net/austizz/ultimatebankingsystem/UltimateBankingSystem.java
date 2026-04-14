package net.austizz.ultimatebankingsystem;

import com.mojang.logging.LogUtils;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.BankRegulationService;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.command.UBSCommands;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.loan.LoanService;
import net.austizz.ultimatebankingsystem.npc.BankTellerInteractionManager;
import net.austizz.ultimatebankingsystem.payments.ScheduledPaymentService;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(UltimateBankingSystem.MODID)
public class UltimateBankingSystem {
    public static final String MODID = "ultimatebankingsystem";
    public static final Logger LOGGER = LogUtils.getLogger();

    private long lastAutosaveTick = -1L;
    private long lastInterestTick = -1L;

    public UltimateBankingSystem(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (var bill : ModItems.USD_BILLS) {
                event.accept(bill);
            }
            event.accept(ModItems.BANK_NOTE);
            event.accept(ModItems.CHEQUE);
            event.accept(ModItems.BANK_TELLER_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.ATM_MACHINE);
            event.accept(ModBlocks.BANK_OWNER_PC);
            event.accept(ModBlocks.COLOR_BUTTON_BLOCK);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.BANK_TELLER_SPAWN_EGG);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerAboutToStartEvent event) {
        BankManager.init(event.getServer());
        lastAutosaveTick = -1L;
        lastInterestTick = -1L;
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        BankManager.init(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BankManager.shutdown();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        long gameTime = overworld.getGameTime();

        LoanService.processRepayments(server, gameTime);
        ScheduledPaymentService.process(server, gameTime);
        BankRegulationService.process(server, gameTime);
        BankTellerInteractionManager.tick(server);

        long autosaveIntervalTicks = Math.max(1, Config.AUTOSAVE_INTERVAL_MINUTES.get()) * 60L * 20L;
        if (gameTime % autosaveIntervalTicks == 0L && gameTime != lastAutosaveTick) {
            BankManager.markDirty();
            lastAutosaveTick = gameTime;
        }

        long interestIntervalTicks = Math.max(20, Config.SAVINGS_INTEREST_INTERVAL_TICKS.get());
        if (gameTime % interestIntervalTicks != 0L || gameTime == lastInterestTick) {
            return;
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        for (Bank bank : centralBank.getBanks().values()) {
            bank.payInterestAllSavingAccounts();
        }
        BankManager.markDirty();
        lastInterestTick = gameTime;
    }

    @SubscribeEvent
    public void onBalanceChanged(BalanceChangedEvent event) {
        String message = event.isPositiveNumber()
                ? "§a✅ Deposit Successful! You have received: $" + MoneyText.abbreviate(event.getChangeAmount()) + " into your bank account. Current Balance: §6$" + MoneyText.abbreviate(event.getNewBalance()) + "\n §aAccount ID: §6" + event.getAccount().getAccountUUID()
                : "§c❌ Withdrawal Notice: $" + MoneyText.abbreviate(event.getChangeAmount()) + " has been deducted from your account. Current Balance: §6$" + MoneyText.abbreviate(event.getNewBalance()) + "\n §cAccount ID: §6" + event.getAccount().getAccountUUID();
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
        PacketDistributor.sendToPlayer(targetPlayer, UBSCommands.buildHudStatePayload(centralBank, targetPlayer.getUUID()));
    }
}
