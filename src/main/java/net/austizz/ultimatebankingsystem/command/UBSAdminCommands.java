package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.util.UUID;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSAdminCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("ubs")
                        .then(Commands.literal("bank")
                                .then(Commands.literal("save")
                                        .executes(context -> {
                                            if (context.getSource().getPlayer() != null){
                                                if (!context.getSource().hasPermission(3)){
                                                        context.getSource().sendSystemMessage(Component.literal("§4You do not have permission to perform this action"));
                                                    return 1;
                                                }
                                            }
                                            BankManager.markDirty();
                                            context.getSource().sendSystemMessage(Component.literal("§aThe bank has been successfully saved§a."));
                                            return 1;
                                        }
                                        )
                                )
                                .then(Commands.literal("rename")
                                        .then(Commands.argument("New Name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String name  = StringArgumentType.getString(context, "New Name");
                                                    
                                                    if (context.getSource().getPlayer() != null){
                                                        if (!context.getSource().getPlayer().hasPermissions(3)){
                                                            context.getSource().sendSystemMessage(Component.literal("§4You do not have permission to perform this action"));
                                                            return 1;
                                                        }
                                                    }
                                                    MinecraftServer server = context.getSource().getServer();
                                                    CentralBank centralBank = BankManager.getCentralBank(server);
                                                    centralBank.setBankName(name);
                                                    context.getSource().sendSystemMessage(Component.literal("§aThe bank's name has been successfully updated to: §e" + name + "§a."));
                                                    return 1;
                                                }
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("money")
                                .then(Commands.literal("deposit")
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

                                                            if (!account.AddBalance(amount)){
                                                                context.getSource().sendSystemMessage(Component.literal("§cFailed to add amount '§e" + amount + "§c' to account '§e" + accountId + "§c'. Please try again."));
                                                                return 1;
                                                            }
                                                            context.getSource().sendSystemMessage(Component.literal("§aSuccessfully added '§e" + amount +"§a' to '§e" + accountId + "§a' Balance: '§2" + account.getBalance() + "§a'."));
                                                            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, true));
                                                            return 1;
                                                            }
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("withdraw")
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
                                                            if (!account.RemoveBalance(amount)){
                                                                context.getSource().sendSystemMessage(Component.literal("§cFailed to remove amount '§e" + amount + "§c' from account '§e" + accountId + "§c'. Please try again."));
                                                                return 1;
                                                            }
                                                            context.getSource().sendSystemMessage(Component.literal("§aSuccessfully removed '§e" + amount +"§a' from '§e" + accountId + "§a' Balance: '§2" + account.getBalance() + "§a'."));
                                                            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, false));
                                                            return 1;
                                                            }
                                                        )
                                                )
                                        )
                                )

                ));
    }

}
