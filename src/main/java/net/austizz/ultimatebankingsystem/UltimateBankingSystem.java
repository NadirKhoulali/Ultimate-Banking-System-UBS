package net.austizz.ultimatebankingsystem;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.data.DataHandler;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Objects;
import java.util.UUID;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(UltimateBankingSystem.MODID)

public class UltimateBankingSystem {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "ultimatebankingsystem";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UltimateBankingSystem(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (UltimateBankingSystem) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.CASH);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS){
            event.accept(ModBlocks.ATM_MACHINE);
        }
    }

    public static CentralBank CentralBank ;
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerAboutToStartEvent event) {
        BankManager.init(event.getServer());
    }
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        BankManager.init(event.getServer());
    }
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Clear cached references so singleplayer world switches don't leak state across worlds
        BankManager.shutdown();
    }
    @SubscribeEvent
    public void onBalanceChanged(BalanceChangedEvent event) {
        String message = event.isPositiveNumber()
                ? "§a✅ Deposit Successful! You have received: " + event.getChangeAmount() + " into your bank account. Current Balance: §6" + event.getNewBalance() + "\n §aAccount ID: §6" + event.getAccount().getAccountUUID()
                : "§c❌ Withdrawal Notice: " + event.getChangeAmount() + " has been deducted from your account. Current Balance: §6" + event.getNewBalance() + "\n §cAccount ID: §6" + event.getAccount().getAccountUUID();
        ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(event.getAccount().getPlayerUUID()).sendSystemMessage(Component.literal(message));
    }

}
