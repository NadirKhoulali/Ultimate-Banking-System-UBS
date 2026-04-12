package net.austizz.ultimatebankingsystem.command;


import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.callback.CallBackManager;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.loan.LoanService;
import net.austizz.ultimatebankingsystem.payrequest.PayRequestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSCommands {

    private static final Component helpMessage = Component.literal("§6§lUltimate Banking System §7- §eAccount Commands\n" + "§8/§faccount §7help §8- §7Show this help\n" + "§8/§faccount §7create §8- §7Create a new account\n" + "§8/§faccount §7delete §8- §7Delete your account\n" + "§8/§faccount §7info §8- §7View your account info\n" + "§8/§faccount §7deposit §8<§famount§8> §8- §7Deposit money\n" + "§8/§faccount §7withdraw §8<§famount§8> §8- §7Withdraw money\n" + "§8/§faccount §7balance §8- §7Show your balance\n" + "§8/§faccount §7transfer §8<§fplayer§8> <§famount§8> §8- §7Transfer money\n" + "§8/§fpayrequest §8<§fplayer§8> <§famount§8> [§fdestinationAccountId§8] §8- §7Request money from a player");
    private static final ConcurrentHashMap<UUID, LoanService.LoanQuote> PENDING_LOAN_CONFIRMATIONS = new ConcurrentHashMap<>();

    private static Component ubsMessage(ChatFormatting accentColor, String title, Component body) {
        return Component.literal("§6§lUltimate Banking System §7- ")
                .append(Component.literal(title).withStyle(accentColor))
                .append(Component.literal("\n§8────────────────────────\n"))
                .append(body);
    }

    private static Component ubsError(String title, String message) {
        return ubsMessage(ChatFormatting.RED, "§c" + title, Component.literal("§c" + message));
    }

    private static Component ubsSuccess(String title, String message) {
        return ubsMessage(ChatFormatting.GREEN, "§a" + title, Component.literal("§a" + message));
    }

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
                        .then(Commands.literal("info")
                                .executes(context -> {
                                    MinecraftServer server = context.getSource().getServer();
                                    CentralBank centralBank = BankManager.getCentralBank(server);
                                    ConcurrentHashMap<UUID, AccountHolder> account = centralBank.SearchForAccount(context.getSource().getPlayer().getUUID());
                                    if (account.isEmpty()) {
                                        context.getSource().sendSystemMessage(Component.literal("§cYou currently do not have any accounts."));
                                        return 1;
                                    }
                                    for(AccountHolder a :  account.values()){
                                        if (a.isPrimaryAccount()){
                                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                            Component info = Component.literal("§6§lUltimate Banking System §7- §eAccount Info\n"
                                                            + "§7Bank: §e" + centralBank.getBank(a.getBankId()).getBankName() + "\n"
                                                            + "§8(§7ID: §f" + a.getBankId() + "§8)\n")
                                                    .append(Component.literal("§7Account ID: §f" + a.getAccountUUID() + "\n").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, a.getAccountUUID().toString()))))
                                                    .append(Component.literal("§7Primary Account: §f" + a.isPrimaryAccount() + "\n"
                                                                    + "§7Type: §f" + (a.getAccountType() != null ? a.getAccountType().label : "Unknown") + "\n"
                                                                    + "§7Balance: §a" + a.getBalance().toPlainString() + "\n"
                                                                    + "§7Created: §f" + a.getDateOfCreation().format(fmt) + "\n"
                                                                    + "Actions: ")
                                                            .append((Component) Component.literal("§f§l[§4Delete Account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account delete " + a.getAccountUUID()))))
                                                            .append(Component.literal(" "))
                                                            .append((Component) Component.literal("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + a.getAccountUUID())))))
                                                    .append(Component.literal(" \n"))
                                                    .append((Component) Component.literal("§f§l[§3Transfer Money§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/account transfer " + a.getAccountUUID()))))
                                                    .append((Component) Component.literal("§f§l[§6See Transactions§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account transaction list " + a.getAccountUUID()))));
                                            context.getSource().sendSystemMessage(info);
                                            return 1;
                                        }
                                    }
                                    context.getSource().sendSystemMessage(Component.literal("§cNo primary account could be determined. Please check your accounts and set a primary account."));
                                    return 1;
                                })
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
                                                MutableComponent entry = Component.literal("§7" + index + ". §e" + (a.getAccountType() != null ? a.getAccountType().label : "Account"));
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
                                                                    .append((Component) Component.literal("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + account.getAccountUUID())))))
                                                            .append(Component.literal(" \n"))
                                                            .append((Component) Component.literal("§f§l[§3Transfer Money§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/account transfer " + account.getAccountUUID()))))
                                                            .append((Component) Component.literal("§f§l[§6See Transactions§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account transaction list " + account.getAccountUUID()))));
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
                                                            .append((Component) Component.literal("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + account.getAccountUUID())))))
                                                    .append(Component.literal(" \n"))
                                                    .append((Component) Component.literal("§f§l[§3Transfer Money§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/account transfer " + account.getAccountUUID()))))
                                                    .append((Component) Component.literal("§f§l[§6See Transactions§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account transaction list " + account.getAccountUUID()))));

                                            context.getSource().sendSystemMessage(info);
                                            return 1;
                                        })
                                )

                        )
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
                                                    Bank BankChoice = centralBank.getBankByName(BankName);
                                                    String AccountType = StringArgumentType.getString(context, "Account Type");

                                                    if (BankChoice == null) {
                                                        context.getSource().sendSystemMessage(Component.literal("§cThe bank '§e" + BankName + "§c' could not be found."));
                                                        return 1;
                                                    }


                                                    // Validate/parse the account type once.
                                                    AccountTypes selectedType = Arrays.stream(AccountTypes.values())
                                                            .filter(t -> t.name().equalsIgnoreCase(AccountType))
                                                            .findFirst()
                                                            .orElse(null);

                                                    if (selectedType == null) {
                                                        context.getSource().sendSystemMessage(Component.literal("§cAccount Type does not exist!"));
                                                        return 1;
                                                    }

                                                    if (!BankChoice.AddAccount(new AccountHolder(
                                                            context.getSource().getPlayer().getUUID(),
                                                            BigDecimal.ZERO,
                                                            selectedType,
                                                            "test",
                                                            BankChoice.getBankId(),
                                                            null
                                                    ))) {
                                                        context.getSource().sendSystemMessage(Component.literal("§cAccount with exact Account Type already exists at this bank!"));
                                                        return 1;
                                                    }

                                                    BankManager.markDirty();
                                                    context.getSource().sendSystemMessage(Component.literal("§aSuccessfully created a §e" + selectedType.name() + " §aaccount at §e" + BankChoice.getBankName() + "§a!"));

                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("Account ID (sending)", UuidArgument.uuid())
                                        .then(Commands.argument("Account ID (receiving)", UuidArgument.uuid())
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            MinecraftServer server = context.getSource().getServer();
                                                            CentralBank centralBank = BankManager.getCentralBank(server);
                                                            AccountHolder sender = centralBank.SearchForAccountByAccountId(UuidArgument.getUuid(context, "Account ID (sending)"));
                                                            AccountHolder receiver = centralBank.SearchForAccountByAccountId(UuidArgument.getUuid(context, "Account ID (receiving)"));

                                                            if (sender == null) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cThe sender's account could not be found."));
                                                                return 1;
                                                            }
                                                            if (receiver == null) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cThe receiver's account could not be found."));
                                                                return 1;
                                                            }

                                                            if (!sender.getPlayerUUID().equals(context.getSource().getPlayer().getUUID())) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cYou do not own the sender account."));
                                                                return 1;
                                                            }

                                                            if (sender.getAccountUUID().equals(receiver.getAccountUUID())) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cYou cannot transfer to the same account."));
                                                                return 1;
                                                            }

                                                            if (sender.isFrozen()) {
                                                                String reason = sender.getFrozenReason();
                                                                context.getSource().sendSystemMessage(Component.literal(
                                                                        "§cSender account is frozen." + (reason.isEmpty() ? "" : " Reason: " + reason)
                                                                ));
                                                                return 1;
                                                            }

                                                            if (receiver.isFrozen()) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cReceiver account is frozen."));
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

                                                            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                                                                context.getSource().sendSystemMessage(Component.literal("§cThe transfer amount must be greater than zero."));
                                                                return 1;
                                                            }
                                                            
                                                            if(!new UserTransaction(sender.getAccountUUID(),receiver.getAccountUUID(), amount, LocalDateTime.now(), "Bank to Bank UserTransaction").makeTransaction(server)) {
                                                                String receiverName = server.getPlayerList().getPlayer(receiver.getPlayerUUID()) != null
                                                                        ? server.getPlayerList().getPlayer(receiver.getPlayerUUID()).getName().getString()
                                                                        : receiver.getPlayerUUID().toString();

                                                                context.getSource().sendSystemMessage(
                                                                        ubsError(
                                                                                "Transfer Failed",
                                                                                "Could not transfer §e" + amount.toPlainString() + "§c to §e" + receiverName + "§c. Please try again."
                                                                        )
                                                                );
                                                                return 1;
                                                            }

                                                            String receiverName = server.getPlayerList().getPlayer(receiver.getPlayerUUID()) != null
                                                                    ? server.getPlayerList().getPlayer(receiver.getPlayerUUID()).getName().getString()
                                                                    : receiver.getPlayerUUID().toString();

                                                            context.getSource().sendSystemMessage(
                                                                    ubsSuccess(
                                                                            "Transfer Complete",
                                                                            "Transferred §e" + amount.toPlainString() + "§a to §e" + receiverName + "§a successfully."
                                                                    )
                                                            );
                                                            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(sender, sender.getBalance(), amount, false));
                                                            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(receiver, receiver.getBalance(), amount, true));

                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("transaction")
                                .then(Commands.argument("UserTransaction ID", UuidArgument.uuid())
                                        .executes(context -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            CentralBank centralBank = BankManager.getCentralBank(server);
                                            UserTransaction transaction = centralBank.getTransaction(UuidArgument.getUuid(context, "UserTransaction ID"));

                                            if (transaction == null) {
                                                context.getSource().sendSystemMessage(ubsError("UserTransaction Info","UserTransaction not found."));
                                                return 1;
                                            }

                                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                            String timeStr = transaction.getTimestamp().format(fmt);

                                            MutableComponent body = (Component.literal("§7Transaction ID: §f" + transaction.getTransactionUUID() + "\n"))
                                                    .append(Component.literal("§7Amount: §a" + transaction.getAmount().toPlainString() + "\n"))
                                                    .append(Component.literal("§7Time: §f" + timeStr + "\n"))
                                                    .append(Component.literal("§7Description: §f" + transaction.getTransactionDescription()));

                                            context.getSource().sendSystemMessage(
                                                    ubsMessage(ChatFormatting.GOLD, "§eTransaction Info", body)
                                            );
                                            return 1;
                                        }))
                                .then(Commands.literal("list")
                                        .then(Commands.argument("Account ID", UuidArgument.uuid())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();
                                                    CentralBank centralBank = BankManager.getCentralBank(server);
                                                    AccountHolder account = centralBank.SearchForAccountByAccountId(UuidArgument.getUuid(context, "Account ID"));

                                                    if (account == null) {
                                                        context.getSource().sendSystemMessage(
                                                                ubsError("Transactions", "That account couldn't be found.")
                                                        );
                                                        return 1;
                                                    }

                                                    if (account.getTransactions().isEmpty()) {
                                                        context.getSource().sendSystemMessage(
                                                                ubsMessage(ChatFormatting.GOLD, "§eTransactions", Component.literal("§7No transactions on this account!"))
                                                        );
                                                        return 1;
                                                    }

                                                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                                                    MutableComponent list = Component.literal("§7Transactions (§f" + account.getTransactions().size() + "§7)\n");
                                                    account.getTransactions().forEach((uuid, transaction) -> {
                                                        String amountStr = transaction.getAmount().toPlainString();
                                                        String timeStr = transaction.getTimestamp().format(fmt);
                                                        String desc = transaction.getTransactionDescription();

                                                        MutableComponent entry = Component.literal("§8- §7ID: §f" + uuid + "\n")
                                                                .setStyle(
                                                                        Style.EMPTY
                                                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                                        Component.literal("§eTransaction Details\n")
                                                                                                .append(Component.literal("§7Amount: §a" + amountStr + "\n"))
                                                                                                .append(Component.literal("§7Time: §f" + timeStr + "\n"))
                                                                                                .append(Component.literal("§7Description: §f" + desc + "\n"))
                                                                                                .append(Component.literal("\n§7Click to open transaction info"))
                                                                                ))
                                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account transaction " + uuid))
                                                                );

                                                        list.append(entry);
                                                    });

                                                    context.getSource().sendSystemMessage(
                                                            ubsMessage(ChatFormatting.GOLD, "§eTransactions", list)
                                                    );
                                                    return 1;
                                                })
                                        )
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

        ); // closes event.getDispatcher().register(Commands.literal("account") ... )

        // Separate command registration
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

        event.getDispatcher().register(buildPayRequestCommand());
        event.getDispatcher().register(buildHiddenPayRequestCommand());
        event.getDispatcher().register(buildBankCommand());

    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBankCommand() {
        return Commands.literal("bank")
                .executes(context -> {
                    context.getSource().sendSystemMessage(Component.literal(
                            "§6§lUltimate Banking System §7- §eBank Commands\n"
                                    + "§8/§fbank §7balance §8- §7Show your primary account balance\n"
                                    + "§8/§fbank §7list §8- §7List available banks\n"
                                    + "§8/§fbank §7loan request <amount> §8- §7Preview a loan\n"
                                    + "§8/§fbank §7loan confirm §8- §7Confirm your last loan request\n"
                                    + "§8/§fbank §7loan status §8- §7Show active loan balances\n"
                                    + "§8/§fbank §7credit §8- §7Show your credit score\n"
                                    + "§8/§fbank §7shop pay <amount> [shop] §8- §7Simulate a shop checkout using bank funds"
                    ));
                    return 1;
                })
                .then(Commands.literal("balance")
                        .executes(context -> handleBankBalance(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> handleBankList(context.getSource())))
                .then(Commands.literal("credit")
                        .executes(context -> handleBankCredit(context.getSource())))
                .then(Commands.literal("loan")
                        .then(Commands.literal("request")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> handleLoanRequest(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "amount")
                                        ))
                                )
                        )
                        .then(Commands.literal("confirm")
                                .executes(context -> handleLoanConfirm(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> handleLoanStatus(context.getSource()))))
                .then(Commands.literal("shop")
                        .then(Commands.literal("pay")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> handleShopPay(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "amount"),
                                                "Generic Shop"
                                        ))
                                        .then(Commands.argument("shop", StringArgumentType.greedyString())
                                                .executes(context -> handleShopPay(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "amount"),
                                                        StringArgumentType.getString(context, "shop")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("joint")
                        .then(Commands.literal("create")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                                .executes(context -> handleJointCreate(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "bankName")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("info")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .executes(context -> handleSharedAccountInfo(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "accountId")
                                        ))
                                )
                        )
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> handleSharedAccountDeposit(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "accountId"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> handleSharedAccountWithdraw(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "accountId"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("fromAccountId", UuidArgument.uuid())
                                        .then(Commands.argument("toAccountId", UuidArgument.uuid())
                                                .then(Commands.argument("amount", StringArgumentType.word())
                                                        .executes(context -> handleSharedAccountTransfer(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "fromAccountId"),
                                                                UuidArgument.getUuid(context, "toAccountId"),
                                                                StringArgumentType.getString(context, "amount")
                                                        ))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("close")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .executes(context -> handleSharedAccountClose(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "accountId")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("business")
                        .then(Commands.literal("create")
                                .then(Commands.argument("label", StringArgumentType.word())
                                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                                .executes(context -> handleBusinessCreate(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "label"),
                                                        StringArgumentType.getString(context, "bankName")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("grant")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("role", StringArgumentType.word())
                                                        .executes(context -> handleBusinessGrant(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "accountId"),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "role")
                                                        ))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> handleBusinessRevoke(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "accountId"),
                                                        EntityArgument.getPlayer(context, "player")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("transferowner")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> handleBusinessTransferOwner(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "accountId"),
                                                        EntityArgument.getPlayer(context, "player")
                                                ))
                                        )
                                )
                        )
                );
    }

    private static int handleBankBalance(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        ConcurrentHashMap<UUID, AccountHolder> accounts = centralBank.SearchForAccount(player.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cYou do not have any bank accounts yet."));
            return 1;
        }

        AccountHolder selected = findPrimaryAccount(centralBank, player.getUUID());
        if (selected == null) {
            selected = accounts.values().iterator().next();
        }

        Bank bank = centralBank.getBank(selected.getBankId());
        String bankName = bank == null ? "Unknown" : bank.getBankName();
        source.sendSystemMessage(Component.literal(
                "§6§lUltimate Banking System §7- §eBalance\n"
                        + "§7Bank: §f" + bankName + "\n"
                        + "§7Type: §f" + selected.getAccountType().label + "\n"
                        + "§7Account ID: §f" + selected.getAccountUUID() + "\n"
                        + "§7Balance: §a$" + selected.getBalance().toPlainString()
        ));
        return 1;
    }

    private static int handleBankList(CommandSourceStack source) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        List<Bank> banks = centralBank.getBanks().values().stream()
                .sorted(Comparator.comparing(Bank::getBankName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Banks Registered: §b" + banks.size() + "\n"));
        if (banks.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (Bank bank : banks) {
                body.append(Component.literal(
                        "§8- §e" + bank.getBankName() + " §7(" + bank.getBankId() + ")\n"
                ));
            }
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eBank List", body));
        return 1;
    }

    private static int handleBankCredit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (selected == null) {
            source.sendSystemMessage(Component.literal("§cNo account found. Create an account first."));
            return 1;
        }

        source.sendSystemMessage(ubsMessage(
                ChatFormatting.AQUA,
                "§bCredit Score",
                Component.literal("§7Account: §f" + shortId(selected.getAccountUUID()) + "\n")
                        .append(Component.literal("§7Score: §e" + selected.getCreditScore()))
        ));
        return 1;
    }

    private static int handleLoanRequest(CommandSourceStack source, String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can request loans."));
            return 1;
        }

        BigDecimal principal;
        try {
            principal = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid loan amount."));
            return 1;
        }

        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(Component.literal("§cAmount must be greater than zero."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (selected == null) {
            source.sendSystemMessage(Component.literal("§cNo account found. Create an account first."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        LoanService.LoanQuote quote = LoanService.createQuote(selected, principal, gameTime);
        PENDING_LOAN_CONFIRMATIONS.put(player.getUUID(), quote);

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Account: §f" + selected.getAccountUUID() + "\n"));
        body.append(Component.literal("§7Principal: §6$" + quote.principal().toPlainString() + "\n"));
        body.append(Component.literal("§7Interest Rate (APR): §e" + quote.annualInterestRate() + "%\n"));
        body.append(Component.literal("§7Total Repayable: §6$" + quote.totalRepayable().toPlainString() + "\n"));
        body.append(Component.literal("§7Payments: §f" + quote.totalPayments() + " x $" + quote.periodicPayment().toPlainString() + "\n"));
        body.append(Component.literal("§7First Due (game ticks): §f" + quote.firstDueGameTime() + "\n"));
        if (quote.requiresAdminApproval()) {
            body.append(Component.literal("§eThis loan requires admin approval after confirmation.\n"));
            body.append(Component.literal("§7Reason: §f" + quote.approvalReason() + "\n"));
        } else {
            body.append(Component.literal("§aEligible for auto-approval after confirmation.\n"));
        }
        body.append(Component.literal("\n§7Run §f/bank loan confirm §7to continue."));

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eLoan Terms Preview", body));
        return 1;
    }

    private static int handleLoanConfirm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can confirm loans."));
            return 1;
        }

        LoanService.LoanQuote quote = PENDING_LOAN_CONFIRMATIONS.remove(player.getUUID());
        if (quote == null) {
            source.sendSystemMessage(Component.literal("§cNo pending loan request. Use /bank loan request <amount> first."));
            return 1;
        }

        if (quote.requiresAdminApproval()) {
            LoanService.queueAdminApproval(quote);
            source.sendSystemMessage(Component.literal(
                    "§eLoan submitted for admin approval. You will be notified when it is reviewed."
            ));
            for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
                if (online.hasPermissions(3)) {
                    online.sendSystemMessage(Component.literal(
                            "§6[UBS] Loan approval needed: " + player.getName().getString()
                                    + " requested $" + quote.principal().toPlainString()
                    ));
                }
            }
            return 1;
        }

        var issued = LoanService.issueLoan(source.getServer(), quote);
        if (issued == null) {
            source.sendSystemMessage(Component.literal("§cLoan issuance failed. Please try again later."));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§aLoan approved and issued: §6$" + quote.principal().toPlainString()
                        + "§a. Repayment: §f" + quote.totalPayments()
                        + " x $" + quote.periodicPayment().toPlainString()
        ));
        return 1;
    }

    private static int handleLoanStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view loan status."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (selected == null) {
            source.sendSystemMessage(Component.literal("§cNo account found."));
            return 1;
        }

        var loans = selected.getActiveLoans().values().stream()
                .sorted(Comparator.comparing(l -> l.getLoanId().toString()))
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Account: §f" + selected.getAccountUUID() + "\n"));
        body.append(Component.literal("§7Credit Score: §e" + selected.getCreditScore() + "\n"));
        body.append(Component.literal("§7Defaulted: " + (selected.isDefaulted() ? "§cYES" : "§aNO") + "\n"));
        body.append(Component.literal("§7Active Loans: §b" + loans.size() + "\n\n"));
        if (loans.isEmpty()) {
            body.append(Component.literal("§8No active loans."));
        } else {
            for (var loan : loans) {
                body.append(Component.literal(
                        "§8- §f" + shortId(loan.getLoanId())
                                + " §7remaining §6$" + loan.getRemainingBalance().toPlainString()
                                + " §7next due tick §f" + loan.getNextDueGameTime()
                                + " §7payment §f$" + loan.getPeriodicPayment().toPlainString()
                                + "\n"
                ));
            }
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eLoan Status", body));
        return 1;
    }

    private static AccountHolder resolveDefaultLoanAccount(CentralBank centralBank, UUID playerId) {
        AccountHolder primary = findPrimaryAccount(centralBank, playerId);
        if (primary != null) {
            return primary;
        }
        ConcurrentHashMap<UUID, AccountHolder> all = centralBank.SearchForAccount(playerId);
        if (all.isEmpty()) {
            return null;
        }
        return all.values().iterator().next();
    }

    private static int handleShopPay(CommandSourceStack source, String amountRaw, String shopName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid amount."));
            return 1;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.stripTrailingZeros().scale() > 0) {
            source.sendSystemMessage(Component.literal("§cAmount must be a positive whole number."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (selected == null) {
            source.sendSystemMessage(Component.literal("§cNo account available."));
            return 1;
        }

        long amountLong;
        try {
            amountLong = amount.longValueExact();
        } catch (ArithmeticException ex) {
            source.sendSystemMessage(Component.literal("§cAmount is too large."));
            return 1;
        }

        var result = UltimateBankingApiProvider.get().shopPurchase(
                selected.getAccountUUID(),
                amountLong,
                shopName
        );
        if (!result.success()) {
            source.sendSystemMessage(Component.literal("§cPurchase failed: " + result.reason()));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§aPaid $" + amount.toPlainString() + " at " + shopName + ". New balance: $" + result.balanceAfter().toPlainString()
        ));
        return 1;
    }

    private static int handleJointCreate(CommandSourceStack source, ServerPlayer invitedPlayer, String bankName) {
        ServerPlayer creator = source.getPlayer();
        if (creator == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can create joint accounts."));
            return 1;
        }
        if (invitedPlayer.getUUID().equals(creator.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou cannot create a joint account with yourself."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = centralBank.getBankByName(bankName);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankName));
            return 1;
        }

        AccountHolder account = new AccountHolder(
                creator.getUUID(),
                BigDecimal.ZERO,
                AccountTypes.CheckingAccount,
                "",
                bank.getBankId(),
                null
        );
        account.setAccountAccessType("JOINT");
        account.grantAccessRole(creator.getUUID(), "OWNER");
        account.grantAccessRole(invitedPlayer.getUUID(), "OWNER");
        if (!bank.AddAccount(account)) {
            source.sendSystemMessage(Component.literal("§cUnable to create joint account (duplicate account type for owner in this bank)."));
            return 1;
        }
        source.sendSystemMessage(Component.literal(
                "§aJoint account created: §f" + account.getAccountUUID() + " §7at §e" + bank.getBankName()
        ));
        invitedPlayer.sendSystemMessage(Component.literal(
                "§aYou were added as co-owner of joint account §f" + account.getAccountUUID()
        ));
        return 1;
    }

    private static int handleBusinessCreate(CommandSourceStack source, String label, String bankName) {
        ServerPlayer creator = source.getPlayer();
        if (creator == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can create business accounts."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = centralBank.getBankByName(bankName);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankName));
            return 1;
        }

        AccountHolder account = new AccountHolder(
                creator.getUUID(),
                BigDecimal.ZERO,
                AccountTypes.CheckingAccount,
                "",
                bank.getBankId(),
                null
        );
        account.setAccountAccessType("BUSINESS");
        account.setBusinessLabel(label);
        account.grantAccessRole(creator.getUUID(), "OWNER");
        if (!bank.AddAccount(account)) {
            source.sendSystemMessage(Component.literal("§cUnable to create business account (duplicate account type for owner in this bank)."));
            return 1;
        }
        source.sendSystemMessage(Component.literal(
                "§aBusiness account created for §e" + label + "§a. Account: §f" + account.getAccountUUID()
        ));
        return 1;
    }

    private static int handleBusinessGrant(CommandSourceStack source, UUID accountId, ServerPlayer target, String role) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can grant access."));
            return 1;
        }

        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!"BUSINESS".equals(account.getAccountAccessType())) {
            source.sendSystemMessage(Component.literal("§cThat account is not a business account."));
            return 1;
        }
        if (!account.canManage(actor.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou do not have manage permission for this account."));
            return 1;
        }
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!List.of("VIEW", "DEPOSIT", "WITHDRAW", "MANAGE", "OWNER").contains(normalizedRole)) {
            source.sendSystemMessage(Component.literal("§cInvalid role. Use VIEW, DEPOSIT, WITHDRAW, MANAGE, or OWNER."));
            return 1;
        }
        account.grantAccessRole(target.getUUID(), normalizedRole);
        source.sendSystemMessage(Component.literal(
                "§aGranted role §e" + normalizedRole + " §ato §f" + target.getName().getString()
        ));
        target.sendSystemMessage(Component.literal(
                "§aYou were granted role §e" + normalizedRole + " §aon business account §f" + account.getAccountUUID()
        ));
        return 1;
    }

    private static int handleBusinessRevoke(CommandSourceStack source, UUID accountId, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can revoke access."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!"BUSINESS".equals(account.getAccountAccessType())) {
            source.sendSystemMessage(Component.literal("§cThat account is not a business account."));
            return 1;
        }
        if (!account.canManage(actor.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou do not have manage permission for this account."));
            return 1;
        }
        account.revokeAccessRole(target.getUUID());
        source.sendSystemMessage(Component.literal("§aRevoked account access for §f" + target.getName().getString()));
        target.sendSystemMessage(Component.literal("§eYour access to business account §f" + account.getAccountUUID() + " §ewas revoked."));
        return 1;
    }

    private static int handleBusinessTransferOwner(CommandSourceStack source, UUID accountId, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can transfer ownership."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!"BUSINESS".equals(account.getAccountAccessType())) {
            source.sendSystemMessage(Component.literal("§cThat account is not a business account."));
            return 1;
        }
        if (!"OWNER".equals(account.getRole(actor.getUUID()))) {
            source.sendSystemMessage(Component.literal("§cOnly current owner can transfer ownership."));
            return 1;
        }
        account.grantAccessRole(actor.getUUID(), "MANAGE");
        account.grantAccessRole(target.getUUID(), "OWNER");
        source.sendSystemMessage(Component.literal("§aOwnership transferred to §f" + target.getName().getString()));
        target.sendSystemMessage(Component.literal("§aYou are now owner of business account §f" + account.getAccountUUID()));
        return 1;
    }

    private static int handleSharedAccountInfo(CommandSourceStack source, UUID accountId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!account.canView(player.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou do not have access to this account."));
            return 1;
        }

        String owners = account.getAccessRoles().entrySet().stream()
                .filter(e -> "OWNER".equals(e.getValue()))
                .map(e -> shortId(e.getKey()))
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Account ID: §f" + account.getAccountUUID() + "\n"));
        body.append(Component.literal("§7Mode: §f" + account.getAccountAccessType() + "\n"));
        if ("BUSINESS".equals(account.getAccountAccessType())) {
            body.append(Component.literal("§7Business: §f" + account.getBusinessLabel() + "\n"));
        }
        body.append(Component.literal("§7Balance: §a$" + account.getBalance().toPlainString() + "\n"));
        body.append(Component.literal("§7Owners: §f" + owners + "\n"));
        body.append(Component.literal("§7Your role: §e" + account.getRole(player.getUUID())));

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eShared Account", body));
        return 1;
    }

    private static int handleSharedAccountDeposit(CommandSourceStack source, UUID accountId, String amountRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!account.canDeposit(actor.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou do not have deposit access for this account."));
            return 1;
        }
        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cDeposit failed."));
            return 1;
        }
        account.addTransaction(new UserTransaction(
                actor.getUUID(),
                account.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "SHARED_DEPOSIT:" + actor.getName().getString()
        ));
        notifyAllAccountMembers(source, account, "§aDeposit on shared account " + shortId(account.getAccountUUID()) + ": $" + amount.toPlainString());
        return 1;
    }

    private static int handleSharedAccountWithdraw(CommandSourceStack source, UUID accountId, String amountRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!account.canWithdraw(actor.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou do not have withdraw access for this account."));
            return 1;
        }
        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cWithdraw failed (insufficient funds or frozen)."));
            return 1;
        }
        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                actor.getUUID(),
                amount,
                LocalDateTime.now(),
                "SHARED_WITHDRAW:" + actor.getName().getString()
        ));
        notifyAllAccountMembers(source, account, "§eWithdraw on shared account " + shortId(account.getAccountUUID()) + ": $" + amount.toPlainString());
        return 1;
    }

    private static int handleSharedAccountTransfer(CommandSourceStack source, UUID fromAccountId, UUID toAccountId, String amountRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder from = resolveAccount(source, fromAccountId);
        AccountHolder to = resolveAccount(source, toAccountId);
        if (from == null || to == null) {
            return 1;
        }
        if (!from.canWithdraw(actor.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou do not have withdraw access on the source account."));
            return 1;
        }
        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        boolean success = new UserTransaction(
                from.getAccountUUID(),
                to.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "SHARED_TRANSFER:" + actor.getName().getString()
        ).makeTransaction(source.getServer());
        if (!success) {
            source.sendSystemMessage(Component.literal("§cTransfer failed."));
            return 1;
        }
        notifyAllAccountMembers(source, from, "§bTransfer from shared account " + shortId(from.getAccountUUID()) + ": $" + amount.toPlainString());
        return 1;
    }

    private static int handleSharedAccountClose(CommandSourceStack source, UUID accountId) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cAccount not found."));
            return 1;
        }
        if (!account.canManage(actor.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou do not have close permission for this account."));
            return 1;
        }
        Bank bank = centralBank.getBank(account.getBankId());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found for this account."));
            return 1;
        }
        notifyAllAccountMembers(source, account, "§cShared account " + shortId(account.getAccountUUID()) + " was closed by " + actor.getName().getString());
        bank.RemoveAccount(account);
        source.sendSystemMessage(Component.literal("§aClosed shared account §f" + accountId));
        return 1;
    }

    private static AccountHolder resolveAccount(CommandSourceStack source, UUID accountId) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return null;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cAccount not found: " + accountId));
        }
        return account;
    }

    private static BigDecimal parsePositiveWholeAmount(CommandSourceStack source, String amountRaw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid amount."));
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(Component.literal("§cAmount must be greater than zero."));
            return null;
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            source.sendSystemMessage(Component.literal("§cAmount must be a whole number."));
            return null;
        }
        return amount;
    }

    private static void notifyAllAccountMembers(CommandSourceStack source, AccountHolder account, String message) {
        if (source.getServer() == null) {
            return;
        }
        for (UUID memberId : account.getAccessRoles().keySet()) {
            ServerPlayer member = source.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPayRequestCommand() {
        return Commands.literal("payrequest")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> handlePayRequestCreate(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player"),
                                        StringArgumentType.getString(context, "amount"),
                                        null
                                ))
                                .then(Commands.argument("destinationAccountId", UuidArgument.uuid())
                                        .executes(context -> handlePayRequestCreate(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "amount"),
                                                UuidArgument.getUuid(context, "destinationAccountId")
                                        ))
                        ))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildHiddenPayRequestCommand() {
        return Commands.literal("ubs_payrequest")
                .then(Commands.literal("accept")
                        .then(Commands.argument("requestId", UuidArgument.uuid())
                                .executes(context -> handlePayRequestAccept(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "requestId"),
                                        null
                                ))
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .executes(context -> handlePayRequestAccept(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "requestId"),
                                                UuidArgument.getUuid(context, "accountId")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("decline")
                        .then(Commands.argument("requestId", UuidArgument.uuid())
                                .executes(context -> handlePayRequestDecline(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "requestId")
                                ))
                        )
                )
                .then(Commands.literal("choose")
                        .then(Commands.argument("requestId", UuidArgument.uuid())
                                .executes(context -> handlePayRequestChoose(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "requestId")
                                ))
                        )
                );
    }

    private static int handlePayRequestCreate(CommandSourceStack source,
                                              ServerPlayer payer,
                                              String amountRaw,
                                              UUID destinationAccountId) {
        ServerPlayer requester = source.getPlayer();
        if (requester == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can send pay requests."));
            return 1;
        }

        if (payer.getUUID().equals(requester.getUUID())) {
            source.sendSystemMessage(Component.literal("§cYou cannot send a pay request to yourself."));
            return 1;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid amount."));
            return 1;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(Component.literal("§cAmount must be greater than zero."));
            return 1;
        }

        MinecraftServer server = source.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder destinationAccount = destinationAccountId == null
                ? findPreferredReceiverAccount(centralBank, requester.getUUID())
                : findAccountForPlayer(centralBank, requester.getUUID(), destinationAccountId);

        if (destinationAccount == null) {
            if (destinationAccountId == null) {
                source.sendSystemMessage(Component.literal(
                        "§cNo primary receiving account is set. Set one as primary or provide a destination account ID."
                ));
            } else {
                source.sendSystemMessage(Component.literal("§cDestination account ID is invalid or not yours."));
            }
            return 1;
        }

        var request = PayRequestManager.createRequest(
                requester.getUUID(),
                payer.getUUID(),
                destinationAccount.getAccountUUID(),
                amount
        );
        source.sendSystemMessage(Component.literal(
                "§aPay request sent to §e" + payer.getName().getString() + " §afor §6$" + amount.toPlainString()
                        + "§a. Destination: §f" + accountLabel(destinationAccount)
        ));
        sendPayRequestPrompt(payer, requester, request);
        return 1;
    }

    private static int handlePayRequestAccept(CommandSourceStack source, UUID requestId, UUID accountId) {
        ServerPlayer payer = source.getPlayer();
        if (payer == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can accept pay requests."));
            return 1;
        }

        MinecraftServer server = source.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        PayRequestManager.PayRequest request = PayRequestManager.getRequest(requestId);
        if (request == null) {
            source.sendSystemMessage(Component.literal("§cThis pay request no longer exists."));
            return 1;
        }
        if (!request.getPayerUUID().equals(payer.getUUID())) {
            source.sendSystemMessage(Component.literal("§cThis pay request is not addressed to you."));
            return 1;
        }
        if (request.getStatus() != PayRequestManager.Status.PENDING) {
            source.sendSystemMessage(Component.literal("§cThis pay request is already " + request.getStatus().name().toLowerCase() + "."));
            return 1;
        }
        if (PayRequestManager.isExpired(request)) {
            source.sendSystemMessage(Component.literal("§cThis pay request has expired."));
            return 1;
        }

        AccountHolder senderAccount = accountId == null
                ? findPrimaryAccount(centralBank, payer.getUUID())
                : findAccountForPlayer(centralBank, payer.getUUID(), accountId);

        if (senderAccount == null) {
            source.sendSystemMessage(Component.literal("§cNo valid account selected. Choose an account first."));
            sendPayRequestAccountChoices(payer, request, "Choose an account to pay with:");
            return 1;
        }

        AccountHolder receiverAccount = findReceiverAccountForRequest(centralBank, request);
        if (receiverAccount == null) {
            source.sendSystemMessage(Component.literal("§cRequester destination account is unavailable."));
            ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                        "§cYour pay request could not be completed because your destination account is unavailable."
                ));
            }
            return 1;
        }

        if (senderAccount.getAccountUUID().equals(receiverAccount.getAccountUUID())) {
            source.sendSystemMessage(Component.literal("§cCannot pay the same account."));
            return 1;
        }

        boolean transferSuccess = new UserTransaction(
                senderAccount.getAccountUUID(),
                receiverAccount.getAccountUUID(),
                request.getAmount(),
                LocalDateTime.now(),
                "Pay Request"
        ).makeTransaction(server);

        if (!transferSuccess) {
            source.sendSystemMessage(Component.literal("§cPayment failed. Check balance and account status."));
            ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                        "§e" + payer.getName().getString() + " tried to accept your pay request, but payment failed."
                ));
            }
            return 1;
        }

        PayRequestManager.markAccepted(requestId);
        source.sendSystemMessage(Component.literal(
                "§aPaid §6$" + request.getAmount().toPlainString() + "§a to §e"
                        + resolvePlayerName(server, request.getRequesterUUID())
                        + "§a using account §7" + shortId(senderAccount.getAccountUUID())
        ));

        ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                    "§a" + payer.getName().getString() + " accepted your pay request for §6$" + request.getAmount().toPlainString() + "§a."
            ));
        }

        NeoForge.EVENT_BUS.post(new BalanceChangedEvent(senderAccount, senderAccount.getBalance(), request.getAmount(), false));
        NeoForge.EVENT_BUS.post(new BalanceChangedEvent(receiverAccount, receiverAccount.getBalance(), request.getAmount(), true));
        return 1;
    }

    private static int handlePayRequestDecline(CommandSourceStack source, UUID requestId) {
        ServerPlayer payer = source.getPlayer();
        if (payer == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can decline pay requests."));
            return 1;
        }

        MinecraftServer server = source.getServer();
        PayRequestManager.PayRequest request = PayRequestManager.getRequest(requestId);
        if (request == null) {
            source.sendSystemMessage(Component.literal("§cThis pay request no longer exists."));
            return 1;
        }
        if (!request.getPayerUUID().equals(payer.getUUID())) {
            source.sendSystemMessage(Component.literal("§cThis pay request is not addressed to you."));
            return 1;
        }
        if (request.getStatus() != PayRequestManager.Status.PENDING) {
            source.sendSystemMessage(Component.literal("§cThis pay request is already " + request.getStatus().name().toLowerCase() + "."));
            return 1;
        }

        PayRequestManager.markDeclined(requestId);
        source.sendSystemMessage(Component.literal("§7You declined the pay request."));

        ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                    "§c" + payer.getName().getString() + " declined your pay request for §6$" + request.getAmount().toPlainString() + "§c."
            ));
        }
        return 1;
    }

    private static int handlePayRequestChoose(CommandSourceStack source, UUID requestId) {
        ServerPlayer payer = source.getPlayer();
        if (payer == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can choose account for pay requests."));
            return 1;
        }

        PayRequestManager.PayRequest request = PayRequestManager.getRequest(requestId);
        if (request == null) {
            source.sendSystemMessage(Component.literal("§cThis pay request no longer exists."));
            return 1;
        }
        if (!request.getPayerUUID().equals(payer.getUUID())) {
            source.sendSystemMessage(Component.literal("§cThis pay request is not addressed to you."));
            return 1;
        }
        if (request.getStatus() != PayRequestManager.Status.PENDING || PayRequestManager.isExpired(request)) {
            source.sendSystemMessage(Component.literal("§cThis pay request is no longer pending."));
            return 1;
        }

        sendPayRequestAccountChoices(payer, request, "Choose an account to pay with:");
        return 1;
    }

    private static void sendPayRequestPrompt(ServerPlayer payer,
                                             ServerPlayer requester,
                                             PayRequestManager.PayRequest request) {
        MinecraftServer server = payer.getServer();
        if (server == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        AccountHolder destination = findReceiverAccountForRequest(centralBank, request);
        String destinationLabel = destination == null
                ? "Unavailable"
                : accountLabel(destination);

        AccountHolder primary = findPrimaryAccount(centralBank, payer.getUUID());
        if (primary == null) {
            payer.sendSystemMessage(Component.literal(
                    "§6Pay Request: §e" + requester.getName().getString() + " §7requests §6$"
                            + request.getAmount().toPlainString() + "§7.\n"
                            + "§7Destination: §f" + destinationLabel
            ));
            sendPayRequestAccountChoices(payer, request, "No primary account set. Choose account to accept:");
            return;
        }

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7From: §e" + requester.getName().getString() + "\n"));
        body.append(Component.literal("§7Amount: §6$" + request.getAmount().toPlainString() + "\n"));
        body.append(Component.literal("§7Destination: §f" + destinationLabel + "\n"));
        body.append(Component.literal("§7Primary account: §f" + accountLabel(primary) + "\n\n"));

        String requestId = request.getRequestId().toString();
        body.append(clickAction("[Accept]", ChatFormatting.GREEN, "/ubs_payrequest accept " + requestId, "Accept with primary account"));
        body.append(Component.literal(" "));
        body.append(clickAction("[Decline]", ChatFormatting.RED, "/ubs_payrequest decline " + requestId, "Decline this request"));
        body.append(Component.literal(" "));
        body.append(clickAction("[Choose Account]", ChatFormatting.AQUA, "/ubs_payrequest choose " + requestId, "Pay from a different account"));

        payer.sendSystemMessage(
                ubsMessage(ChatFormatting.GOLD, "§ePay Request", body)
        );
    }

    private static void sendPayRequestAccountChoices(ServerPlayer payer,
                                                     PayRequestManager.PayRequest request,
                                                     String titleLine) {
        MinecraftServer server = payer.getServer();
        if (server == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        List<AccountHolder> payerAccounts = centralBank.SearchForAccount(payer.getUUID())
                .values()
                .stream()
                .sorted(Comparator.comparing(a -> a.getAccountUUID().toString()))
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7" + titleLine + "\n"));
        body.append(Component.literal("§7Requested amount: §6$" + request.getAmount().toPlainString() + "\n\n"));
        AccountHolder destination = findReceiverAccountForRequest(centralBank, request);
        body.append(Component.literal("§7Destination: §f" + (destination == null ? "Unavailable" : accountLabel(destination)) + "\n\n"));

        if (payerAccounts.isEmpty()) {
            body.append(Component.literal("§cYou have no accounts available.\n"));
        } else {
            for (AccountHolder account : payerAccounts) {
                String buttonLabel = "[" + account.getAccountType().label + " $" + account.getBalance().toPlainString() + "]";
                String command = "/ubs_payrequest accept " + request.getRequestId() + " " + account.getAccountUUID();
                body.append(clickAction(buttonLabel, ChatFormatting.AQUA, command, "Pay using " + accountLabel(account)));
                body.append(Component.literal(" §7" + shortId(account.getAccountUUID()) + "\n"));
            }
        }

        body.append(Component.literal("\n"));
        body.append(clickAction("[Decline]", ChatFormatting.RED,
                "/ubs_payrequest decline " + request.getRequestId(),
                "Decline this request"));

        payer.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bPay Request Account Choice", body));
    }

    private static MutableComponent clickAction(String label,
                                                ChatFormatting color,
                                                String runCommand,
                                                String hoverText) {
        return Component.literal(label).setStyle(
                Style.EMPTY
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, runCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
        );
    }

    private static AccountHolder findPrimaryAccount(CentralBank centralBank, UUID playerId) {
        for (AccountHolder account : centralBank.SearchForAccount(playerId).values()) {
            if (account.isPrimaryAccount()) {
                return account;
            }
        }
        return null;
    }

    private static AccountHolder findPreferredReceiverAccount(CentralBank centralBank, UUID requesterId) {
        ConcurrentHashMap<UUID, AccountHolder> accounts = centralBank.SearchForAccount(requesterId);
        if (accounts.isEmpty()) {
            return null;
        }
        for (AccountHolder account : accounts.values()) {
            if (account.isPrimaryAccount()) {
                return account;
            }
        }
        if (accounts.size() == 1) {
            return accounts.values().iterator().next();
        }
        return null;
    }

    private static AccountHolder findReceiverAccountForRequest(CentralBank centralBank,
                                                               PayRequestManager.PayRequest request) {
        UUID destinationAccountId = request.getReceiverAccountUUID();
        if (destinationAccountId != null) {
            AccountHolder destination = centralBank.SearchForAccountByAccountId(destinationAccountId);
            if (destination != null && destination.getPlayerUUID().equals(request.getRequesterUUID())) {
                return destination;
            }
            return null;
        }
        return findPreferredReceiverAccount(centralBank, request.getRequesterUUID());
    }

    private static AccountHolder findAccountForPlayer(CentralBank centralBank, UUID playerId, UUID accountId) {
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !account.getPlayerUUID().equals(playerId)) {
            return null;
        }
        return account;
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player.getName().getString();
        }
        return shortId(uuid);
    }

    private static long currentOverworldGameTime(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        var level = server.getLevel(Level.OVERWORLD);
        return level == null ? 0L : level.getGameTime();
    }

    private static String shortId(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private static String accountLabel(AccountHolder account) {
        return account.getAccountType().label + " (" + shortId(account.getAccountUUID()) + ")";
    }
}
