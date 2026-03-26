package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigDecimal;
import java.util.UUID;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSAdminCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("ubs")
                        .then(Commands.literal("money")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("accountId", UuidArgument.uuid())
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            if (context.getSource().getPlayer() != null){
                                                                if(!context.getSource().getPlayer().hasPermissions(3)){
                                                                    context.getSource().sendSystemMessage(Component.literal("§4You do not have access to this command"));
                                                                    return 1;
                                                                }
                                                            }
                                                            MinecraftServer server = context.getSource().getServer();
                                                            CentralBank centralBank = BankManager.getCentralBank(server);
                                                            UUID accountId = UuidArgument.getUuid(context, "accountId");
                                                            AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
                                                            if(account == null){
                                                                context.getSource().sendSystemMessage(Component.literal("§cThe account '§e" + accountId + "§c' could not be found."));
                                                                return 1;
                                                            }
                                                            String amountStr = StringArgumentType.getString(context, "amount");
                                                                BigDecimal amount;
                                                                try {
                                                                    amount = new BigDecimal(amountStr);
                                                                } catch (NumberFormatException e) {
                                                                    context.getSource().sendSystemMessage(Component.literal("§cThe amount '§e" + amountStr + "§c' is not a valid number."));
                                                                    return 1;
                                                                }

                                                                // TODO: use amount and player to implement the rest of the logic
                                                                account.AddBalance(amount);
                                                                context.getSource().sendSystemMessage(Component.literal("§aSuccessfully added '§e" + amount +"§a' to '§e" + accountId + "§a' Balance: '§2" + account.getBalance() + "§a'."));
                                                            return 1;
                                                            }))))
                                .then(Commands.literal("remove"))
                ));
    }

}
