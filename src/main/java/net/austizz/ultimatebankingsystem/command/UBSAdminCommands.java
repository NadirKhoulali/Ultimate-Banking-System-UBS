package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigDecimal;
import java.util.UUID;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSAdminCommands {

    private static Component ubsPanel(ChatFormatting accentColor, String title, Component body) {
        return Component.literal("§6§lUltimate Banking System §7- ")
                .append(Component.literal(title).withStyle(accentColor))
                .append(Component.literal("\n§8────────────────────────\n"))
                .append(body);
    }

    private static boolean requireAdminPermission(net.minecraft.commands.CommandSourceStack source, int level) {
        if (source.getPlayer() != null && !source.getPlayer().hasPermissions(level)) {
            source.sendSystemMessage(Component.literal("§4You do not have permission to perform this action"));
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("ubs")
                        .then(Commands.literal("centralbank")
                                .executes(context -> {
                                    if (!requireAdminPermission(context.getSource(), 3)) {
                                        return 1;
                                    }

                                    MinecraftServer server = context.getSource().getServer();
                                    CentralBank centralBank = BankManager.getCentralBank(server);

                                    int bankCount = centralBank.getBanks() != null ? centralBank.getBanks().size() : 0;

                                    MutableComponent body = Component.empty();

                                    body.append(Component.literal("§7Name: §e" + centralBank.getBankName() + "\n"));
                                    body.append(Component.literal("§7Bank ID: §f" + centralBank.getBankId() + "\n"));
                                    body.append(Component.literal("§7Reserve: §a" + centralBank.getBankReserve() + "\n"));
                                    body.append(Component.literal("§7Interest Rate: §e" + centralBank.getInterestRate() + "\n"));
                                    body.append(Component.literal("§7Registered Banks: §b" + bankCount + "\n"));

                                    body.append(Component.literal("\n§7Actions:\n"));

                                    // Save
                                    body.append(Component.literal("§f§l[§aSave§f§l]")
                                            .setStyle(Style.EMPTY
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ubs bank save"))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to save bank data")))
                                            ));
                                    body.append(Component.literal(" "));

                                    // Rename (suggest)
                                    body.append(Component.literal("§f§l[§eRename§f§l]")
                                            .setStyle(Style.EMPTY
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs bank rename "))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest /ubs bank rename <name>")))
                                            ));

                                    body.append(Component.literal("\n"));

                                    // Set interest rate (suggest)
                                    body.append(Component.literal("§f§l[§6Set Interest§f§l]")
                                            .setStyle(Style.EMPTY
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs centralbank interest set "))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest /ubs centralbank interest set <rate>")))
                                            ));

                                    body.append(Component.literal("\n"));

                                    // Deposit (suggest)
                                    body.append(Component.literal("§f§l[§2Deposit§f§l]")
                                            .setStyle(Style.EMPTY
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs money deposit <accountId> <amount>"))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest deposit to an account")))
                                            ));
                                    body.append(Component.literal(" "));

                                    // Withdraw (suggest)
                                    body.append(Component.literal("§f§l[§cWithdraw§f§l]")
                                            .setStyle(Style.EMPTY
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs money withdraw <accountId> <amount>"))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest withdraw from an account")))
                                            ));

                                    body.append(Component.literal("\n\n§8Tip: §7Click an action above to run/suggest the command."));

                                    context.getSource().sendSystemMessage(
                                            ubsPanel(ChatFormatting.GOLD, "§eCentral Bank", body)
                                    );
                                    return 1;
                                })
                        )
                        // Central Bank interest admin actions
                        .then(Commands.literal("centralbank")
                                .then(Commands.literal("interest")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("rate", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            if (!requireAdminPermission(context.getSource(), 3)) {
                                                                return 1;
                                                            }

                                                            String rateStr = StringArgumentType.getString(context, "rate");
                                                            double rate;
                                                            try {
                                                                rate = Double.parseDouble(rateStr);
                                                            } catch (NumberFormatException e) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cThe rate '§e" + rateStr + "§c' is not a valid number."));
                                                                return 1;
                                                            }

                                                            MinecraftServer server = context.getSource().getServer();
                                                            CentralBank centralBank = BankManager.getCentralBank(server);

                                                            // Uses Bank#setInterestRate which already validates against config min/max.
                                                            double before = centralBank.getInterestRate();
                                                            centralBank.setInterestRate(rate);
                                                            double after = centralBank.getInterestRate();

                                                            if (Double.compare(before, after) == 0 && Double.compare(before, rate) != 0) {
                                                                context.getSource().sendSystemMessage(Component.literal(
                                                                        "§cInterest rate not changed. Rate must be within allowed range. Current: §e" + before
                                                                ));
                                                                return 1;
                                                            }

                                                            context.getSource().sendSystemMessage(Component.literal(
                                                                    "§aCentral Bank interest rate updated: §e" + before + " §7-> §e" + after
                                                            ));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("bank")
                                .then(Commands.literal("save")
                                        .executes(context -> {
                                            if (!requireAdminPermission(context.getSource(), 3)) {
                                                return 1;
                                            }

                                            BankManager.markDirty();
                                            context.getSource().sendSystemMessage(Component.literal("§aThe bank has been successfully saved§a."));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("rename")
                                        .then(Commands.argument("New Name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "New Name");

                                                    if (!requireAdminPermission(context.getSource(), 3)) {
                                                        return 1;
                                                    }

                                                    MinecraftServer server = context.getSource().getServer();
                                                    CentralBank centralBank = BankManager.getCentralBank(server);
                                                    centralBank.setBankName(name);
                                                    context.getSource().sendSystemMessage(Component.literal("§aThe bank's name has been successfully updated to: §e" + name + "§a."));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("money")
                                .then(Commands.literal("deposit")
                                        .then(Commands.argument("accountId", UuidArgument.uuid())
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            if (!requireAdminPermission(context.getSource(), 3)) {
                                                                return 1;
                                                            }

                                                            MinecraftServer server = context.getSource().getServer();
                                                            CentralBank centralBank = BankManager.getCentralBank(server);
                                                            UUID accountId = UuidArgument.getUuid(context, "accountId");
                                                            AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
                                                            if (account == null) {
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

                                                            if (!account.AddBalance(amount)) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cFailed to add amount '§e" + amount + "§c' to account '§e" + accountId + "§c'. Please try again."));
                                                                return 1;
                                                            }
                                                            context.getSource().sendSystemMessage(Component.literal("§aSuccessfully added '§e" + amount + "§a' to '§e" + accountId + "§a' Balance: '§2" + account.getBalance() + "§a'."));
                                                            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, true));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("withdraw")
                                        .then(Commands.argument("accountId", UuidArgument.uuid())
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            if (!requireAdminPermission(context.getSource(), 3)) {
                                                                return 1;
                                                            }

                                                            MinecraftServer server = context.getSource().getServer();
                                                            CentralBank centralBank = BankManager.getCentralBank(server);
                                                            UUID accountId = UuidArgument.getUuid(context, "accountId");
                                                            AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
                                                            if (account == null) {
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

                                                            if (!account.RemoveBalance(amount)) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cFailed to remove amount '§e" + amount + "§c' from account '§e" + accountId + "§c'. Please try again."));
                                                                return 1;
                                                            }
                                                            context.getSource().sendSystemMessage(Component.literal("§aSuccessfully removed '§e" + amount + "§a' from '§e" + accountId + "§a' Balance: '§2" + account.getBalance() + "§a'."));
                                                            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, false));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                );
    }

}
