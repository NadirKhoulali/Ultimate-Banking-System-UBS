package net.austizz.ultimatebankingsystem.command;


import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.callback.CallBackManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem.*;
import org.checkerframework.checker.units.qual.C;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSCommands {

    private static final Component helpMessage = Component.literal("§6§lUltimate Banking System §7- §eAccount Commands\n" + "§8/§faccount §7help §8- §7Show this help\n" + "§8/§faccount §7create §8- §7Create a new account\n" + "§8/§faccount §7delete §8- §7Delete your account\n" + "§8/§faccount §7info §8- §7View your account info\n" + "§8/§faccount §7deposit §8<§famount§8> §8- §7Deposit money\n" + "§8/§faccount §7withdraw §8<§famount§8> §8- §7Withdraw money\n" + "§8/§faccount §7balance §8- §7Show your balance\n" + "§8/§faccount §7transfer §8<§fplayer§8> <§famount§8> §8- §7Transfer money");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("account")
                        .executes(context -> {
                            context.getSource().sendSystemMessage(helpMessage);
                            return 1;
                        })
                        .then(Commands.literal("help").executes(context -> {
                            context.getSource().sendSystemMessage(helpMessage);
                            return 1;
                        }))
                        .then(Commands.literal("create")
                                .then(Commands.argument("Account Type", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (AccountTypes account : AccountTypes.values()) {
                                                builder.suggest(account.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("Bank Name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();
                                                    CentralBank centralBank = BankManager.getCentralBank(server);

                                                    String BankName = StringArgumentType.getString(context, "Bank Name");
                                                    Bank BankChoise = centralBank.getBankByName(BankName);
                                                    String AccountType = StringArgumentType.getString(context, "Account Type");

                                                    if (BankChoise == null) {
                                                        context.getSource().sendSystemMessage(Component.literal("§cThe bank '§e" + BankName + "§c' could not be found."));
                                                        return 1;
                                                    }
                                                    BankChoise.AddAccount(new AccountHolder(
                                                            context.getSource().getPlayer().getUUID(),
                                                            new BigDecimal(BigInteger.ZERO),
                                                            Arrays.stream(
                                                                    AccountTypes.values()).filter(
                                                                            account -> account.name().equals(AccountType)
                                                                    ).findFirst().orElse(null),
                                                            "test",
                                                            BankChoise.getBankId(),
                                                            null));
                                                    BankManager.markDirty();
                                                    context.getSource().sendSystemMessage(Component.literal("§aSuccessfully created a §e" + AccountType + " §aaccount at §e" + BankChoise.getBankName() + "§a!"));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("info")
                                .then(Commands.literal("list")
                                        .executes(context -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            CentralBank centralBank = BankManager.getCentralBank(server);

                                            ConcurrentHashMap<UUID, AccountHolder> account =  centralBank.SearchForAccount(context.getSource().getPlayer().getUUID());


                                            // Header
                                            context.getSource().sendSystemMessage(
                                                    Component.literal("§6§lUltimate Banking System §7- §eYour Accounts (§f" + account.size() + "§e)\n§8────────────────────────")
                                            );
                                            if (account.isEmpty()) {
                                                context.getSource().sendSystemMessage(Component.literal("§cNo account found."));
                                                return 1;
                                            }

                                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                                            int index = 0;
                                            for (AccountHolder a : account.values()) {
                                                index++;
                                                String bankName = BankManager.getCentralBank(context.getSource().getServer()).getBank(a.getBankId()).getBankName();
                                                String shortAccId = a.getAccountUUID().toString().substring(0, 8);

                                                // Primary badge
                                                Component primaryBadge = a.isPrimaryAccount()
                                                        ? Component.literal(" §8[§2PRIMARY§8]")
                                                        : Component.empty();

                                                // Build a compact multi-line card per account
                                                net.minecraft.network.chat.MutableComponent entry = Component.literal("§7" + index + ". §e" + (a.getAccountType() != null ? a.getAccountType().label : "Account"));
                                                entry.append(primaryBadge);
                                                entry.append(Component.literal("\n§7Bank: §f" + bankName + " §8(§7ID: §f" + a.getBankId() + "§8)"));
                                                entry.append(Component.literal("\n§7Balance: §a" + a.getBalance().toPlainString() + " §7Created: §f" + a.getDateOfCreation().format(fmt)));

                                                // Actions row
                                                entry.append(Component.literal("\n§7Actions: "));
                                                entry.append(
                                                        Component.literal("§f§l[§9Open§f§l]")
                                                                .setStyle(
                                                                        Style.EMPTY
                                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account info " + a.getAccountUUID()))
                                                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open account details")))
                                                                )
                                                );
                                                entry.append(Component.literal(" "));
                                                entry.append(
                                                        Component.literal("§f§l[§3Copy ID§f§l]")
                                                                .setStyle(
                                                                        Style.EMPTY
                                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, a.getAccountUUID().toString()))
                                                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy Account ID (" + a.getAccountUUID() + ")")))
                                                                )
                                                );

                                                context.getSource().sendSystemMessage(entry);
                                                // Separator between accounts
                                                context.getSource().sendSystemMessage(Component.literal("§8────────────────────────"));
                                            }

                                            return 1;
                                        })
                                )
                                .then(Commands.literal("bank")
                                        .then(Commands.argument("Bank Name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();
                                                    CentralBank centralBank = BankManager.getCentralBank(server);
                                                    String BankName = StringArgumentType.getString(context, "Bank Name");
                                                    Bank Choise = centralBank.getBankByName(BankName);
                                                    if (Choise == null) {
                                                        context.getSource().sendSystemMessage(Component.literal("§cThe bank '§e" + BankName + "§c' could not be found."));
                                                        return 1;
                                                    }
                                                    AccountHolder account = Choise.getPlayerAccount(context.getSource().getPlayer());
                                                    if (account == null) {
                                                        context.getSource().sendSystemMessage(Component.literal("§cYou do not have an account at §e" + Choise.getBankName() + "§c."));
                                                        return 1;
                                                    }
                                                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                                    Component info = Component.literal("§6§lUltimate Banking System §7- §eAccount Info\n"
                                                                    + "§7Bank: §e" + Choise.getBankName() + "\n"
                                                                    + "§8(§7ID: §f" + Choise.getBankId() + "§8)\n")
                                                            .append(Component.literal("§7Account ID: §f" + account.getAccountUUID() + "\n").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, account.getAccountUUID().toString()))))
                                                            .append(Component.literal("§7Primary Account: §f" + account.isPrimaryAccount() + "\n"
                                                                            + "§7Type: §f" + (account.getAccountType() != null ? account.getAccountType().label : "Unknown") + "\n"
                                                                            + "§7Balance: §a" + account.getBalance().toPlainString() + "\n"
                                                                            + "§7Created: §f" + account.getDateOfCreation().format(fmt) + "\n"
                                                                            + "Actions: ")
                                                                    .append((Component) Component.literal("§f§l[§4Delete Account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account delete " + account.getAccountUUID()))))
                                                                    .append(Component.literal(" "))
                                                                    .append((Component) Component.literal("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + account.getAccountUUID())))));
                                                    context.getSource().sendSystemMessage(info);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.argument("accountID", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            CentralBank centralBank = BankManager.getCentralBank(server);
                                            UUID accountID = UUID.fromString(StringArgumentType.getString(context, "accountID"));
                                            AccountHolder account = centralBank.SearchForAccountByAccountId(accountID);
                                            if (account == null) {
                                                context.getSource().sendSystemMessage(Component.literal("§cThe account '§e" + accountID + "§c' could not be found."));
                                                return 1;
                                            }

                                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                            Component info = Component.literal("§6§lUltimate Banking System §7- §eAccount Info\n"
                                                    + "§7Bank: §e" + centralBank.getBank(account.getBankId()).getBankName() + "\n"
                                                    + "§8(§7ID: §f" + account.getBankId() + "§8)\n")
                                                    .append(Component.literal("§7Account ID: §f" + account.getAccountUUID() + "\n").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, account.getAccountUUID().toString()))))
                                                    .append(Component.literal("§7Primary Account: §f" + account.isPrimaryAccount() + "\n"
                                                    + "§7Type: §f" + (account.getAccountType() != null ? account.getAccountType().label : "Unknown") + "\n"
                                                    + "§7Balance: §a" + account.getBalance().toPlainString() + "\n"
                                                    + "§7Created: §f" + account.getDateOfCreation().format(fmt) + "\n"
                                                    + "Actions: ")
                                                    .append((Component) Component.literal("§f§l[§4Delete Account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account delete " + account.getAccountUUID()))))
                                                    .append(Component.literal(" "))
                                                    .append((Component) Component.literal("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + account.getAccountUUID())))));
                                            context.getSource().sendSystemMessage(info);
                                            return 1;
                                        })
                                )

                        )
                        .then(Commands.literal("delete")
                                .then(Commands.argument("Account ID", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            CentralBank centralBank = BankManager.getCentralBank(server);

                                            UUID accountID = UUID.fromString(StringArgumentType.getString(context, "Account ID"));
                                            AccountHolder account = centralBank.SearchForAccountByAccountId(accountID);
                                            if (account == null) {
                                                context.getSource().sendSystemMessage(Component.literal("§cYour account was not found."));
                                                return 1;
                                            }
                                            account.RequestAccountTermination(context.getSource().getPlayer());
                                            BankManager.markDirty();
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("primary")
                                .then(Commands.literal("set")
                                    .then(Commands.argument("Account ID", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                MinecraftServer server = context.getSource().getServer();
                                                CentralBank centralBank = BankManager.getCentralBank(server);
                                                ConcurrentHashMap<UUID, AccountHolder> result = centralBank.SearchForAccount(context.getSource().getPlayer().getUUID());
                                                if (result.isEmpty()) {
                                                    context.getSource().sendSystemMessage(Component.literal("§cYour account was not found."));
                                                    return 1;
                                                }
                                                UUID targetId = UUID.fromString(StringArgumentType.getString(context, "Account ID"));
                                                AccountHolder targetAccount = result.get(targetId);
                                                if (targetAccount == null) {
                                                    context.getSource().sendSystemMessage(Component.literal("§cAccount not found!"));
                                                    return 1;
                                                }
                                                if (targetAccount.isPrimaryAccount()) {
                                                    context.getSource().sendSystemMessage(Component.literal("§aAccount at §e" + centralBank.getBank(targetAccount.getBankId()).getBankName() + "§a is already the primary account!"));
                                                    return 1;
                                                }
                                                // First, clear primary on ALL accounts
                                                for (AccountHolder a : result.values()) {
                                                    a.setPrimaryAccount(false);
                                                }
                                                // Then set the target as primary
                                                targetAccount.setPrimaryAccount(true);
                                                context.getSource().sendSystemMessage(Component.literal("§aSuccessfully made account at §e" + centralBank.getBank(targetAccount.getBankId()).getBankName() + "§a the primary account! \n §7This means that actions, invoices and or recurring payments will default to this account."));
                                                return 1;
                                            })
                                    )
                                )
                        )

        );
        event.getDispatcher()
                .register(Commands.literal("ubs_action")
                    .then(Commands.argument("id", UuidArgument.uuid()) // Gebruik de ingebouwde UUID argument type
                        .executes(context -> {
                            UUID id = UuidArgument.getUuid(context, "id");
                            ServerPlayer player = context.getSource().getPlayerOrException();

                            // Voer de gekoppelde methode uit
                            CallBackManager.execute(id, player);

                            return 1;
                        })
                        .then(Commands.literal("cancel").executes(
                                context -> {
                                    UUID id = UuidArgument.getUuid(context, "id");
                                    context.getSource().sendSystemMessage(Component.literal("§cThe action has been cancelled."));
                                    CallBackManager.removeCallback(id);
                                    return 1;
                                }
                        ))
                    )

                );

    }
}
