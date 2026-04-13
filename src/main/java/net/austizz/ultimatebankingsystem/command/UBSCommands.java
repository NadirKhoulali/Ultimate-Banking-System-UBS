package net.austizz.ultimatebankingsystem.command;


import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.callback.CallBackManager;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.loan.LoanService;
import net.austizz.ultimatebankingsystem.payrequest.PayRequestManager;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.npc.BankTellerInteractionManager;
import net.austizz.ultimatebankingsystem.network.HudStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.stats.Stats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSCommands {

    private static final Component helpMessage = Component.literal("§6§lUltimate Banking System §7- §eAccount Commands\n" + "§8/§faccount §7help §8- §7Show this help\n" + "§8/§faccount §7create §8- §7Create a new account\n" + "§8/§faccount §7delete §8- §7Delete your account\n" + "§8/§faccount §7info §8- §7View your account info\n" + "§8/§faccount §7deposit §8<§famount§8> §8- §7Deposit money\n" + "§8/§faccount §7withdraw §8<§famount§8> §8- §7Withdraw money\n" + "§8/§faccount §7balance §8- §7Show your balance\n" + "§8/§faccount §7transfer §8<§fplayer§8> <§famount§8> §8- §7Transfer money\n" + "§8/§fpayrequest §8<§fplayer§8> <§famount§8> [§fdestinationAccountId§8] §8- §7Request money from a player");
    private static final ConcurrentHashMap<UUID, LoanService.LoanQuote> PENDING_LOAN_CONFIRMATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> HUD_ENABLED_OVERRIDES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_BANK_CREATE_ATTEMPT_MILLIS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CompoundTag> PENDING_CD_BREAK_CONFIRMATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> HEIST_WANTED_UNTIL = new ConcurrentHashMap<>();

    private record EmployeeSpec(String role, BigDecimal salary) {}
    private record LoanProductSpec(String name, BigDecimal maxAmount, double interestRate, long durationTicks) {}

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
        event.getDispatcher().register(buildBankTellerCommand());

    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBankTellerCommand() {
        return Commands.literal("bankteller")
                .then(Commands.literal("choose")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(context -> BankTellerInteractionManager.handleChoose(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "mode")
                                ))
                        )
                )
                .then(Commands.literal("page")
                        .then(Commands.argument("direction", StringArgumentType.word())
                                .executes(context -> BankTellerInteractionManager.handlePage(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "direction")
                                ))
                        )
                )
                .then(Commands.literal("account")
                        .then(Commands.argument("accountId", UuidArgument.uuid())
                                .executes(context -> BankTellerInteractionManager.handleAccountPick(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "accountId")
                                ))
                        )
                )
                .then(Commands.literal("cancel")
                        .executes(context -> BankTellerInteractionManager.handleCancel(context.getSource())));
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
                                    + "§8/§fbank §7shop pay <amount> [shop] §8- §7Simulate a shop checkout using bank funds\n"
                                    + "§8/§fbank §7withdraw note <amount> §8- §7Withdraw a serialized physical bank note\n"
                                    + "§8/§fbank §7deposit note §8- §7Deposit the held bank note\n"
                                    + "§8/§fbank §7cheque write <player> <amount> §8- §7Write a cheque item\n"
                                    + "§8/§fbank §7cheque deposit §8- §7Deposit the held cheque\n"
                                    + "§8/§fbank §7teller get §8- §7Issue a bank-bound teller spawn egg\n"
                                    + "§8/§fbank §7teller count §8- §7Show active teller count for your bank\n"
                                    + "§8/§fbank §7hud toggle §8- §7Toggle on-screen balance HUD"
                    ));
                    return 1;
                })
                .then(Commands.literal("balance")
                        .executes(context -> handleBankBalance(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> handleBankList(context.getSource())))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> handleBankCreate(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        "SOLE"
                                ))
                                .then(Commands.argument("ownershipModel", StringArgumentType.word())
                                        .executes(context -> handleBankCreate(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "ownershipModel")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("open")
                        .then(Commands.argument("bankName", StringArgumentType.word())
                                .executes(context -> handleBankOpenAccount(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName"),
                                        "CheckingAccount",
                                        ""
                                ))
                                .then(Commands.argument("accountType", StringArgumentType.word())
                                        .executes(context -> handleBankOpenAccount(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "bankName"),
                                                StringArgumentType.getString(context, "accountType"),
                                                ""
                                        ))
                                        .then(Commands.argument("certificateTier", StringArgumentType.word())
                                                .executes(context -> handleBankOpenAccount(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "bankName"),
                                                        StringArgumentType.getString(context, "accountType"),
                                                        StringArgumentType.getString(context, "certificateTier")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("primary")
                        .then(Commands.argument("bankName", StringArgumentType.word())
                                .executes(context -> handleSetPrimaryBank(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("close")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> handleCloseBankAccount(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("transfer")
                        .then(Commands.argument("fromBank", StringArgumentType.word())
                                .then(Commands.argument("toBank", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> handleInterBankSelfTransfer(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "fromBank"),
                                                        StringArgumentType.getString(context, "toBank"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("send")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> handleSendToPlayerAtBank(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount"),
                                                ""
                                        ))
                                        .then(Commands.argument("bankName", StringArgumentType.word())
                                                .executes(context -> handleSendToPlayerAtBank(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "amount"),
                                                        StringArgumentType.getString(context, "bankName")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("motto")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> handleSetBankMotto(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "text")
                                ))
                        )
                )
                .then(Commands.literal("color")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .executes(context -> handleSetBankColor(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "value")
                                ))
                        )
                )
                .then(Commands.literal("teller")
                        .then(Commands.literal("get")
                                .executes(context -> handleBankTellerGet(context.getSource())))
                        .then(Commands.literal("count")
                                .executes(context -> handleBankTellerCount(context.getSource())))
                )
                .then(Commands.literal("info")
                        .then(Commands.argument("bankName", StringArgumentType.word())
                                .executes(context -> handleBankInfo(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("reserve")
                        .executes(context -> handleBankReserve(context.getSource())))
                .then(Commands.literal("dashboard")
                        .executes(context -> handleBankDashboard(context.getSource())))
                .then(Commands.literal("accounts")
                        .executes(context -> handleBankAccountsList(context.getSource())))
                .then(Commands.literal("cds")
                        .executes(context -> handleBankCertificateSchedule(context.getSource())))
                .then(Commands.literal("limit")
                        .then(Commands.literal("set")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> handleBankLimitSet(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "type"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("view")
                                .executes(context -> handleBankLimitView(context.getSource())))
                )
                .then(Commands.literal("role")
                        .then(Commands.literal("assign")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .executes(context -> handleBankRoleAssign(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "role")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> handleBankRoleRevoke(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(context -> handleBankRoleList(context.getSource())))
                )
                .then(Commands.literal("shares")
                        .executes(context -> handleBankSharesList(context.getSource()))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("percent", StringArgumentType.word())
                                                .executes(context -> handleBankSharesSet(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "percent")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("cofounder")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> handleBankCofounderAdd(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(context -> handleBankCofounderList(context.getSource())))
                )
                .then(Commands.literal("hire")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .then(Commands.argument("salary", StringArgumentType.word())
                                                .executes(context -> handleBankHire(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "role"),
                                                        StringArgumentType.getString(context, "salary")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("fire")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> handleBankFire(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")
                                ))
                        )
                )
                .then(Commands.literal("employees")
                        .executes(context -> handleBankEmployeesList(context.getSource())))
                .then(Commands.literal("quit")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> handleBankEmployeeQuit(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("borrow")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> handleBorrowFromCentralBank(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "amount")
                                ))
                        )
                )
                .then(Commands.literal("lend")
                        .then(Commands.literal("offer")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .then(Commands.argument("annualRate", StringArgumentType.word())
                                                .then(Commands.argument("termTicks", StringArgumentType.word())
                                                        .executes(context -> handleInterbankOffer(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "amount"),
                                                                StringArgumentType.getString(context, "annualRate"),
                                                                StringArgumentType.getString(context, "termTicks")
                                                        ))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("market")
                                .executes(context -> handleInterbankMarket(context.getSource()))
                        )
                        .then(Commands.literal("accept")
                                .then(Commands.argument("offerId", UuidArgument.uuid())
                                        .executes(context -> handleInterbankAccept(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "offerId")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("loans")
                        .executes(context -> handleBankLoanSummary(context.getSource())))
                .then(Commands.literal("appeal")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> handleBankAppeal(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "message")
                                ))
                        )
                )
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
                                .executes(context -> handleLoanStatus(context.getSource())))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("maxAmount", StringArgumentType.word())
                                                .then(Commands.argument("interestRate", StringArgumentType.word())
                                                        .then(Commands.argument("durationTicks", StringArgumentType.word())
                                                                .executes(context -> handleBankLoanProductCreate(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "name"),
                                                                        StringArgumentType.getString(context, "maxAmount"),
                                                                        StringArgumentType.getString(context, "interestRate"),
                                                                        StringArgumentType.getString(context, "durationTicks")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                        .executes(context -> handleBankLoanProductList(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "bankName")
                                        ))
                                )
                        )
                        .then(Commands.literal("apply")
                                .then(Commands.argument("bankName", StringArgumentType.word())
                                        .then(Commands.argument("product", StringArgumentType.word())
                                                .then(Commands.argument("amount", StringArgumentType.word())
                                                        .executes(context -> handleBankLoanProductApply(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "bankName"),
                                                                StringArgumentType.getString(context, "product"),
                                                                StringArgumentType.getString(context, "amount")
                                                        ))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("active")
                                .executes(context -> handleBankLoanSummary(context.getSource()))))
                .then(Commands.literal("cd")
                        .then(Commands.literal("break")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .executes(context -> handleCdBreakRequest(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "accountId")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("confirm")
                        .executes(context -> handleBankConfirm(context.getSource())))
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
                .then(Commands.literal("withdraw")
                        .then(Commands.literal("note")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> handleWithdrawNote(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "amount")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("deposit")
                        .then(Commands.literal("note")
                                .executes(context -> handleDepositNote(context.getSource()))
                        )
                )
                .then(Commands.literal("cheque")
                        .then(Commands.literal("write")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> handleWriteCheque(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("deposit")
                                .executes(context -> handleDepositCheque(context.getSource()))
                        )
                )
                .then(Commands.literal("hud")
                        .then(Commands.literal("toggle")
                                .executes(context -> handleHudToggle(context.getSource()))
                        )
                )
                .then(Commands.literal("safebox")
                        .then(Commands.literal("list")
                                .executes(context -> handleSafeBoxList(context.getSource()))
                        )
                        .then(Commands.literal("deposit")
                                .executes(context -> handleSafeBoxDeposit(context.getSource()))
                        )
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .executes(context -> handleSafeBoxWithdraw(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "slot")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("heist")
                        .then(Commands.literal("start")
                                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                        .executes(context -> handleBankHeistStart(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "bankName")
                                        ))
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

    private static int handleBankCreate(CommandSourceStack source, String bankNameRaw, String ownershipModelRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can create banks."));
            return 1;
        }

        if (!Config.PLAYER_BANKS_ENABLED.get()) {
            source.sendSystemMessage(Component.literal("§cPlayer-created banks are disabled by server config."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        String bankName = normalizeBankName(bankNameRaw);
        if (bankName.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cBank name cannot be empty."));
            return 1;
        }
        if (bankName.length() > Config.PLAYER_BANKS_NAME_MAX_LENGTH.get()) {
            source.sendSystemMessage(Component.literal(
                    "§cBank name is too long. Max length: " + Config.PLAYER_BANKS_NAME_MAX_LENGTH.get()
            ));
            return 1;
        }
        if (resolveBankByName(centralBank, bankName) != null) {
            source.sendSystemMessage(Component.literal("§cA bank with that name already exists."));
            return 1;
        }

        int maxOwned = Math.max(1, Config.PLAYER_BANKS_MAX_BANKS_PER_PLAYER.get());
        int currentlyOwned = (int) getOwnedBanks(centralBank, player.getUUID()).size();
        if (currentlyOwned >= maxOwned) {
            source.sendSystemMessage(Component.literal(
                    "§cYou already own the maximum number of banks (" + maxOwned + ")."
            ));
            return 1;
        }

        long nowMillis = System.currentTimeMillis();
        long cooldownMs = Math.max(0, Config.PLAYER_BANKS_CREATION_COOLDOWN_HOURS.get()) * 60L * 60L * 1000L;
        Long lastAttempt = LAST_BANK_CREATE_ATTEMPT_MILLIS.get(player.getUUID());
        if (cooldownMs > 0L && lastAttempt != null && (nowMillis - lastAttempt) < cooldownMs) {
            long remainingMs = cooldownMs - (nowMillis - lastAttempt);
            long remainingMinutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
            source.sendSystemMessage(Component.literal(
                    "§cYou must wait " + remainingMinutes + " more minute(s) before another bank creation attempt."
            ));
            return 1;
        }

        int requiredPlayHours = Math.max(0, Config.PLAYER_BANKS_MIN_PLAYTIME_HOURS.get());
        int playTimeTicks = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
        long playHours = playTimeTicks / (20L * 60L * 60L);
        if (playHours < requiredPlayHours) {
            source.sendSystemMessage(Component.literal(
                    "§cYou need at least " + requiredPlayHours + " play-time hour(s) to create a bank."
            ));
            return 1;
        }

        AccountHolder fundingAccount = findPrimaryAccount(centralBank, player.getUUID());
        if (fundingAccount == null) {
            ConcurrentHashMap<UUID, AccountHolder> playerAccounts = centralBank.SearchForAccount(player.getUUID());
            if (!playerAccounts.isEmpty()) {
                fundingAccount = playerAccounts.values().iterator().next();
            }
        }
        if (fundingAccount == null) {
            source.sendSystemMessage(Component.literal("§cYou need a bank account before creating a player bank."));
            return 1;
        }

        BigDecimal minimumBalance = BigDecimal.valueOf(Math.max(0, Config.PLAYER_BANKS_MIN_BALANCE.get()));
        if (fundingAccount.getBalance().compareTo(minimumBalance) < 0) {
            source.sendSystemMessage(Component.literal(
                    "§cEligibility check failed: minimum required balance is $" + minimumBalance.toPlainString()
                            + " (you currently have $" + fundingAccount.getBalance().toPlainString() + ")."
            ));
            return 1;
        }

        BigDecimal creationFee = BigDecimal.valueOf(Math.max(0, Config.PLAYER_BANKS_CREATION_FEE.get()));
        boolean charterWaived = UBSAdminCommands.consumeCharterFeeWaiver(player.getUUID());
        BigDecimal charterFee = charterWaived
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.max(0, Config.BANK_CHARTER_FEE.get()));
        BigDecimal totalFee = creationFee.add(charterFee);
        if (fundingAccount.getBalance().compareTo(totalFee) < 0) {
            source.sendSystemMessage(Component.literal(
                    "§cYou cannot afford bank creation fees. Required: $" + totalFee.toPlainString()
                            + " (creation $" + creationFee.toPlainString()
                            + " + charter $" + charterFee.toPlainString()
                            + (charterWaived ? " (waived)" : "") + ")."
            ));
            return 1;
        }

        String ownershipModel = normalizeOwnershipModel(ownershipModelRaw);
        LAST_BANK_CREATE_ATTEMPT_MILLIS.put(player.getUUID(), nowMillis);

        if (Config.PLAYER_BANKS_REQUIRE_ADMIN_APPROVAL.get()) {
            boolean hasPending = centralBank.getBankApplications().values().stream()
                    .anyMatch(tag -> player.getUUID().equals(readUuid(tag, "applicant"))
                            && "PENDING".equalsIgnoreCase(tag.getString("status")));
            if (hasPending) {
                source.sendSystemMessage(Component.literal(
                        "§cYou already have a pending bank application. Wait for admin review first."
                ));
                return 1;
            }

            UUID applicationId = UUID.randomUUID();
            CompoundTag appTag = new CompoundTag();
            appTag.putUUID("id", applicationId);
            appTag.putUUID("applicant", player.getUUID());
            appTag.putString("bankName", bankName);
            appTag.putString("ownershipModel", ownershipModel);
            appTag.putString("status", "PENDING");
            appTag.putLong("createdMillis", nowMillis);
            appTag.putString("creationFee", creationFee.toPlainString());
            appTag.putString("charterFee", charterFee.toPlainString());
            appTag.putUUID("fundingAccountId", fundingAccount.getAccountUUID());
            centralBank.getBankApplications().put(applicationId, appTag);
            BankManager.markDirty();

            source.sendSystemMessage(Component.literal(
                    "§eApplication submitted. Await admin review.\n"
                            + "§7Application ID: §f" + applicationId + "\n"
                            + "§7Requested bank: §f" + bankName + "\n"
                            + "§7Ownership model: §f" + ownershipModel + "\n"
                            + "§7Pending fees on approval: §6$" + totalFee.toPlainString()
            ));

            for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
                if (!online.hasPermissions(3)) {
                    continue;
                }
                online.sendSystemMessage(Component.literal(
                        "§6[UBS] New bank application from "
                                + player.getName().getString()
                                + " for '" + bankName + "' (ID: " + applicationId + ")."
                ));
            }
            return 1;
        }

        return finalizeBankCreation(source, centralBank, player, fundingAccount, bankName, ownershipModel, creationFee, charterFee);
    }

    private static int finalizeBankCreation(CommandSourceStack source,
                                            CentralBank centralBank,
                                            ServerPlayer founder,
                                            AccountHolder fundingAccount,
                                            String bankName,
                                            String ownershipModel,
                                            BigDecimal creationFee,
                                            BigDecimal charterFee) {
        BigDecimal totalFee = creationFee.add(charterFee);
        if (totalFee.compareTo(BigDecimal.ZERO) > 0 && !fundingAccount.RemoveBalance(totalFee)) {
            source.sendSystemMessage(Component.literal("§cCould not deduct the required creation fees."));
            return 1;
        }
        if (totalFee.compareTo(BigDecimal.ZERO) > 0) {
            centralBank.setReserve(centralBank.getDeclaredReserve().add(totalFee));
            fundingAccount.addTransaction(new UserTransaction(
                    fundingAccount.getAccountUUID(),
                    UUID.nameUUIDFromBytes("ultimatebankingsystem:bank-create-fees".getBytes()),
                    totalFee,
                    LocalDateTime.now(),
                    "BANK_CREATION_FEES:" + bankName
            ));
        }

        Bank newBank = new Bank(
                UUID.randomUUID(),
                bankName,
                BigDecimal.ZERO,
                Config.DEFAULT_SERVER_INTEREST_RATE.get(),
                founder.getUUID()
        );
        centralBank.addBank(newBank);

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(newBank.getBankId());
        metadata.putString("status", "ACTIVE");
        metadata.putString("ownershipModel", ownershipModel);
        metadata.putString("motto", "");
        metadata.putString("color", "#55AAFF");
        metadata.putLong("createdMillis", System.currentTimeMillis());
        metadata.putUUID("founder", founder.getUUID());
        metadata.putString("dailyWithdrawn", "0");
        metadata.putLong("dailyWindowDay", -1L);
        metadata.putString("reserveMinRatio", String.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()));
        metadata.putString("dailyCapOverride", "");
        metadata.putString("employees", "");
        metadata.putString("loanProducts", "");
        if ("ROLE_BASED".equalsIgnoreCase(ownershipModel)) {
            java.util.HashMap<UUID, String> roles = new java.util.HashMap<>();
            roles.put(founder.getUUID(), "FOUNDER");
            metadata.putString("roles", encodeUuidStringMap(roles));
        } else if ("PERCENTAGE_SHARES".equalsIgnoreCase(ownershipModel)) {
            java.util.HashMap<UUID, BigDecimal> shares = new java.util.HashMap<>();
            shares.put(founder.getUUID(), BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_EVEN));
            metadata.putString("shares", encodeShareMap(shares));
        } else if ("FIXED_COFOUNDERS".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("cofounders", encodeUuidList(List.of(founder.getUUID())));
        }
        centralBank.putBankMetadata(newBank.getBankId(), metadata);

        AccountHolder founderAccount = new AccountHolder(
                founder.getUUID(),
                BigDecimal.ZERO,
                AccountTypes.CheckingAccount,
                "",
                newBank.getBankId(),
                null
        );
        if (!newBank.AddAccount(founderAccount)) {
            source.sendSystemMessage(Component.literal("§cBank created, but could not create founder account."));
            return 1;
        }

        if (findPrimaryAccount(centralBank, founder.getUUID()) == null) {
            founderAccount.setPrimaryAccount(true);
        }

        if (charterFee.compareTo(BigDecimal.ZERO) > 0) {
            recordSettlement(
                    centralBank,
                    fundingAccount.getBankId(),
                    centralBank.getBankId(),
                    charterFee,
                    "CHARTER_FEE:" + bankName,
                    true
            );
        }

        source.sendSystemMessage(Component.literal(
                "§aBank created successfully: §e" + newBank.getBankName() + "\n"
                        + "§7Bank ID: §f" + newBank.getBankId() + "\n"
                        + "§7Owner: §f" + founder.getName().getString() + "\n"
                        + "§7Ownership model: §f" + ownershipModel + "\n"
                        + "§7Fees paid: §6$" + totalFee.toPlainString()
        ));
        return 1;
    }

    private static int handleBankOpenAccount(CommandSourceStack source,
                                             String bankNameRaw,
                                             String accountTypeRaw,
                                             String certificateTierRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can open bank accounts."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, bank, gameTime, source.getServer());
        String status = getBankStatus(centralBank, bank);
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status)) {
            source.sendSystemMessage(Component.literal("§cThis bank is " + status.toLowerCase(Locale.ROOT) + " and cannot open new accounts."));
            return 1;
        }
        if ("RESTRICTED".equals(status)) {
            source.sendSystemMessage(Component.literal("§cThis bank is currently restricted and cannot open new accounts."));
            return 1;
        }

        AccountTypes accountType = parseAccountType(accountTypeRaw);
        if (accountType == null) {
            source.sendSystemMessage(Component.literal(
                    "§cUnknown account type. Use checking, saving, moneymarket, or certificate."
            ));
            return 1;
        }

        AccountHolder account = new AccountHolder(
                player.getUUID(),
                BigDecimal.ZERO,
                accountType,
                "",
                bank.getBankId(),
                null
        );

        if (accountType == AccountTypes.CertificateAccount) {
            String tier = normalizeCertificateTier(certificateTierRaw);
            if (tier.isBlank()) {
                source.sendSystemMessage(Component.literal("§cCertificate account requires a tier: short, medium, or long."));
                return 1;
            }
            long maturityTicks = switch (tier) {
                case "short" -> Config.CD_SHORT_TERM_TICKS.get();
                case "medium" -> Config.CD_MEDIUM_TERM_TICKS.get();
                case "long" -> Config.CD_LONG_TERM_TICKS.get();
                default -> -1L;
            };
            if (maturityTicks <= 0L) {
                source.sendSystemMessage(Component.literal("§cInvalid certificate tier configuration."));
                return 1;
            }
            double cdRate = switch (tier) {
                case "short" -> Config.CD_SHORT_RATE.get();
                case "medium" -> Config.CD_MEDIUM_RATE.get();
                case "long" -> Config.CD_LONG_RATE.get();
                default -> 0.0;
            };
            account.configureCertificate(tier, gameTime + maturityTicks, cdRate);
        }

        if (!bank.AddAccount(account)) {
            source.sendSystemMessage(Component.literal(
                    "§cYou already have this account type at " + bank.getBankName() + "."
            ));
            return 1;
        }

        if (findPrimaryAccount(centralBank, player.getUUID()) == null) {
            account.setPrimaryAccount(true);
        }

        MutableComponent message = Component.literal(
                "§aOpened " + account.getAccountType().label + " at §e" + bank.getBankName()
                        + "§a.\n§7Account ID: §f" + account.getAccountUUID()
        );
        if (accountType == AccountTypes.CertificateAccount) {
            message.append(Component.literal(
                    "\n§7CD Tier: §f" + account.getCertificateTier()
                            + " §7Maturity Tick: §f" + account.getCertificateMaturityGameTime()
                            + " §7APR: §e" + account.getCertificateRate() + "%"
            ));
        }
        source.sendSystemMessage(message);
        return 1;
    }

    private static int handleSetPrimaryBank(CommandSourceStack source, String bankNameRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can set a primary bank."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }

        List<AccountHolder> inBank = getPlayerAccountsInBank(bank, player.getUUID());
        if (inBank.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cYou do not have an account at that bank."));
            return 1;
        }

        AccountHolder selected = inBank.stream()
                .filter(AccountHolder::isPrimaryAccount)
                .findFirst()
                .orElse(inBank.get(0));

        for (AccountHolder account : centralBank.SearchForAccount(player.getUUID()).values()) {
            account.setPrimaryAccount(false);
        }
        selected.setPrimaryAccount(true);

        source.sendSystemMessage(Component.literal(
                "§aPrimary account set to §e" + bank.getBankName()
                        + " §7(" + selected.getAccountType().label + " " + shortId(selected.getAccountUUID()) + ")"
        ));
        return 1;
    }

    private static int handleCloseBankAccount(CommandSourceStack source, String bankNameRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can close bank accounts."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }

        List<AccountHolder> inBank = getPlayerAccountsInBank(bank, player.getUUID());
        if (inBank.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cYou do not have an account at that bank."));
            return 1;
        }

        List<AccountHolder> allAccounts = new ArrayList<>(centralBank.SearchForAccount(player.getUUID()).values());
        if (allAccounts.size() <= 1) {
            source.sendSystemMessage(Component.literal("§cYou must keep at least one account open."));
            return 1;
        }

        AccountHolder toClose = inBank.stream()
                .filter(AccountHolder::isPrimaryAccount)
                .findFirst()
                .orElse(inBank.get(0));

        AccountHolder fallback = allAccounts.stream()
                .filter(a -> !a.getAccountUUID().equals(toClose.getAccountUUID()))
                .filter(AccountHolder::isPrimaryAccount)
                .findFirst()
                .orElseGet(() -> allAccounts.stream()
                        .filter(a -> !a.getAccountUUID().equals(toClose.getAccountUUID()))
                        .findFirst()
                        .orElse(null));

        if (fallback == null) {
            source.sendSystemMessage(Component.literal("§cNo fallback account available for balance transfer."));
            return 1;
        }

        BigDecimal transferAmount = toClose.getBalance();
        if (transferAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (!toClose.forceRemoveBalance(transferAmount) || !fallback.forceAddBalance(transferAmount)) {
                source.sendSystemMessage(Component.literal("§cCould not transfer remaining balance to fallback account."));
                return 1;
            }
            UserTransaction tx = new UserTransaction(
                    toClose.getAccountUUID(),
                    fallback.getAccountUUID(),
                    transferAmount,
                    LocalDateTime.now(),
                    "ACCOUNT_CLOSE_TRANSFER"
            );
            toClose.addTransaction(tx);
            fallback.addTransaction(tx);
        }

        boolean wasPrimary = toClose.isPrimaryAccount();
        bank.RemoveAccount(toClose);

        if (wasPrimary) {
            for (AccountHolder account : centralBank.SearchForAccount(player.getUUID()).values()) {
                account.setPrimaryAccount(false);
            }
            fallback.setPrimaryAccount(true);
        }

        source.sendSystemMessage(Component.literal(
                "§aClosed account at §e" + bank.getBankName()
                        + "§a. Moved $" + transferAmount.toPlainString()
                        + " to §f" + accountLabel(fallback)
        ));
        return 1;
    }

    private static int handleInterBankSelfTransfer(CommandSourceStack source,
                                                   String fromBankRaw,
                                                   String toBankRaw,
                                                   String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can transfer between banks."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            source.sendSystemMessage(Component.literal(
                    "§cAmount exceeds global per-transaction limit of $" + Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()
            ));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank fromBank = resolveBankByName(centralBank, fromBankRaw);
        Bank toBank = resolveBankByName(centralBank, toBankRaw);
        if (fromBank == null || toBank == null) {
            source.sendSystemMessage(Component.literal("§cOne or both bank names are invalid."));
            return 1;
        }
        if (fromBank.getBankId().equals(toBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cSource and destination bank must be different."));
            return 1;
        }

        AccountHolder fromAccount = findPlayerAccountInBank(fromBank, player.getUUID());
        AccountHolder toAccount = findPlayerAccountInBank(toBank, player.getUUID());
        if (fromAccount == null || toAccount == null) {
            source.sendSystemMessage(Component.literal("§cYou must have an account at both banks."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, fromBank, gameTime, source.getServer());
        refreshBankOperationalState(centralBank, toBank, gameTime, source.getServer());
        if (!allowsCustomerTransfers(getBankStatus(centralBank, fromBank))
                || !allowsCustomerTransfers(getBankStatus(centralBank, toBank))) {
            source.sendSystemMessage(Component.literal("§cOne of the banks is not currently operational for transfers."));
            return 1;
        }

        if (!consumeBankWithdrawalCapacity(source, centralBank, fromBank, amount, gameTime)) {
            return 1;
        }

        boolean success = new UserTransaction(
                fromAccount.getAccountUUID(),
                toAccount.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "INTER_BANK_SELF_TRANSFER"
        ).makeTransaction(source.getServer());
        if (!success) {
            source.sendSystemMessage(Component.literal("§cTransfer failed."));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§aTransferred $" + amount.toPlainString()
                        + " from §e" + fromBank.getBankName()
                        + " §ato §e" + toBank.getBankName() + "§a."
        ));
        return 1;
    }

    private static int handleSendToPlayerAtBank(CommandSourceStack source,
                                                String playerNameRaw,
                                                String amountRaw,
                                                String bankNameRaw) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can send bank payments."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            source.sendSystemMessage(Component.literal(
                    "§cAmount exceeds global per-transaction limit of $" + Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()
            ));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder senderAccount = resolveDefaultLoanAccount(centralBank, sender.getUUID());
        if (senderAccount == null) {
            source.sendSystemMessage(Component.literal("§cNo source account available."));
            return 1;
        }

        String targetName = playerNameRaw == null ? "" : playerNameRaw.trim();
        if (targetName.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cInvalid player name."));
            return 1;
        }

        UUID targetId = null;
        String resolvedTargetName = targetName;
        ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (onlineTarget != null) {
            targetId = onlineTarget.getUUID();
            resolvedTargetName = onlineTarget.getName().getString();
        } else if (source.getServer().getProfileCache() != null) {
            var profile = source.getServer().getProfileCache().get(targetName);
            if (profile.isPresent()) {
                targetId = profile.get().getId();
                if (profile.get().getName() != null && !profile.get().getName().isBlank()) {
                    resolvedTargetName = profile.get().getName();
                }
            }
        }
        if (targetId == null) {
            source.sendSystemMessage(Component.literal("§cPlayer not found: " + targetName));
            return 1;
        }

        AccountHolder receiverAccount;
        if (bankNameRaw == null || bankNameRaw.isBlank()) {
            receiverAccount = resolveDefaultLoanAccount(centralBank, targetId);
        } else {
            Bank targetBank = resolveBankByName(centralBank, bankNameRaw);
            if (targetBank == null) {
                source.sendSystemMessage(Component.literal("§cTarget bank not found: " + bankNameRaw));
                return 1;
            }
            receiverAccount = findPlayerAccountInBank(targetBank, targetId);
        }

        if (receiverAccount == null) {
            source.sendSystemMessage(Component.literal("§cTarget player has no matching destination account."));
            return 1;
        }
        if (senderAccount.getAccountUUID().equals(receiverAccount.getAccountUUID())) {
            source.sendSystemMessage(Component.literal("§cYou cannot send money to the same account."));
            return 1;
        }

        Bank senderBank = centralBank.getBank(senderAccount.getBankId());
        Bank receiverBank = centralBank.getBank(receiverAccount.getBankId());
        if (senderBank == null || receiverBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is inconsistent for this transfer."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, senderBank, gameTime, source.getServer());
        refreshBankOperationalState(centralBank, receiverBank, gameTime, source.getServer());
        if (!allowsCustomerTransfers(getBankStatus(centralBank, senderBank))
                || !allowsCustomerTransfers(getBankStatus(centralBank, receiverBank))) {
            source.sendSystemMessage(Component.literal("§cOne of the banks is not currently accepting transfers."));
            return 1;
        }

        if (!consumeBankWithdrawalCapacity(source, centralBank, senderBank, amount, gameTime)) {
            return 1;
        }

        boolean success = new UserTransaction(
                senderAccount.getAccountUUID(),
                receiverAccount.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "INTER_BANK_SEND:" + sender.getName().getString()
        ).makeTransaction(source.getServer());
        if (!success) {
            source.sendSystemMessage(Component.literal("§cTransfer failed."));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§aSent $" + amount.toPlainString() + " to §e" + resolvedTargetName
                        + "§a. Destination account: §f" + shortId(receiverAccount.getAccountUUID())
        ));
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.literal(
                    "§aYou received $" + amount.toPlainString()
                            + " from §e" + sender.getName().getString()
                            + "§a into §f" + accountLabel(receiverAccount)
            ));
        }
        return 1;
    }

    private static int handleSetBankMotto(CommandSourceStack source, String mottoRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can set bank branding."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cYou do not own a player bank."));
            return 1;
        }

        String motto = mottoRaw == null ? "" : mottoRaw.trim();
        if (motto.length() > 80) {
            source.sendSystemMessage(Component.literal("§cMotto is too long (max 80 characters)."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("motto", motto);
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(Component.literal(
                "§aUpdated motto for §e" + bank.getBankName()
                        + "§a to: §f" + (motto.isBlank() ? "(empty)" : motto)
        ));
        return 1;
    }

    private static int handleSetBankColor(CommandSourceStack source, String colorRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can set bank branding."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cYou do not own a player bank."));
            return 1;
        }

        String color = normalizeBankColor(colorRaw);
        if (color == null) {
            source.sendSystemMessage(Component.literal("§cInvalid color. Use #RRGGBB or common names like blue, red, green."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("color", color);
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(Component.literal(
                "§aUpdated color branding for §e" + bank.getBankName() + "§a to §f" + color
        ));
        return 1;
    }

    private static int handleBankTellerGet(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can request bank tellers."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can request teller eggs."));
            return 1;
        }

        int activeCount = BankTellerEntity.countActiveTellersForBank(source.getServer(), bank.getBankId());
        if (activeCount >= BankTellerEntity.MAX_TELLERS_PER_BANK) {
            source.sendSystemMessage(Component.literal(
                    "§c" + bank.getBankName() + " already has the max "
                            + BankTellerEntity.MAX_TELLERS_PER_BANK + " active tellers."
            ));
            return 1;
        }

        ItemStack egg = new ItemStack(ModItems.BANK_TELLER_SPAWN_EGG.get());
        BankTellerEntity.applyBankBindingToEgg(egg, bank.getBankId(), bank.getBankName());

        if (!player.getInventory().add(egg)) {
            player.drop(egg, false);
        }

        source.sendSystemMessage(Component.literal(
                "§aIssued teller egg for §e" + bank.getBankName()
                        + "§a. Active tellers: §f" + activeCount
                        + "§7/§f" + BankTellerEntity.MAX_TELLERS_PER_BANK
        ));
        return 1;
    }

    private static int handleBankTellerCount(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view teller count."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can view teller count."));
            return 1;
        }

        int activeCount = BankTellerEntity.countActiveTellersForBank(source.getServer(), bank.getBankId());
        source.sendSystemMessage(Component.literal(
                "§7Active tellers for §e" + bank.getBankName() + "§7: §b"
                        + activeCount + "§7/§f" + BankTellerEntity.MAX_TELLERS_PER_BANK
        ));
        return 1;
    }

    private static int handleBankInfo(CommandSourceStack source, String bankNameRaw) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, bank, gameTime, source.getServer());

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String status = getBankStatus(centralBank, bank);
        String ownershipModel = metadata.getString("ownershipModel");
        String motto = metadata.getString("motto");
        String color = metadata.getString("color");

        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal reserve = bank.getDeclaredReserve();
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                ? reserve.divide(deposits, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Bank Name: §e" + bank.getBankName() + "\n"));
        body.append(Component.literal("§7Bank ID: §f" + bank.getBankId() + "\n"));
        body.append(Component.literal("§7Owner: §f" + resolvePlayerName(source.getServer(), bank.getBankOwnerId()) + "\n"));
        body.append(Component.literal("§7Status: §f" + status + "\n"));
        body.append(Component.literal("§7Ownership Model: §f" + (ownershipModel == null || ownershipModel.isBlank() ? "SOLE" : ownershipModel) + "\n"));
        body.append(Component.literal("§7Brand Color: §f" + (color == null || color.isBlank() ? "#55AAFF" : color) + "\n"));
        body.append(Component.literal("§7Motto: §f" + (motto == null || motto.isBlank() ? "-" : motto) + "\n"));
        body.append(Component.literal("§7Accounts: §b" + bank.getBankAccounts().size() + "\n"));
        body.append(Component.literal("§7Reserve: §a$" + reserve.toPlainString() + "\n"));
        body.append(Component.literal("§7Deposits: §6$" + deposits.toPlainString() + "\n"));
        body.append(Component.literal("§7Reserve Ratio: §e" + ratio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(Component.literal("§7Minimum Required Reserve: §f$" + minReserve.toPlainString()));

        if (metadata.contains("nextLicenseFeeTick")) {
            body.append(Component.literal("\n§7Next License Due Tick: §f" + metadata.getLong("nextLicenseFeeTick")));
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eBank Info", body));
        return 1;
    }

    private static int handleBankReserve(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view reserve data."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
            if (account != null) {
                bank = centralBank.getBank(account.getBankId());
            }
        }
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cNo bank context available."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, bank, gameTime, source.getServer());
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());

        BigDecimal reserve = bank.getDeclaredReserve();
        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                ? reserve.divide(deposits, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);

        BigDecimal dailyCap = getDailyCapForBank(bank, metadata);
        BigDecimal dailyUsed = readBigDecimal(metadata, "dailyWithdrawn");
        BigDecimal dailyRemaining = dailyCap.subtract(dailyUsed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(Component.literal("§7Status: §f" + getBankStatus(centralBank, bank) + "\n"));
        body.append(Component.literal("§7Reserve: §a$" + reserve.toPlainString() + "\n"));
        body.append(Component.literal("§7Total Deposits: §6$" + deposits.toPlainString() + "\n"));
        body.append(Component.literal("§7Reserve Ratio: §e" + ratio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(Component.literal("§7Minimum Reserve: §f$" + minReserve.toPlainString() + "\n"));
        body.append(Component.literal("§7Daily Bank Cap: §b$" + dailyCap.toPlainString() + "\n"));
        body.append(Component.literal("§7Daily Withdrawn: §f$" + dailyUsed.toPlainString() + "\n"));
        body.append(Component.literal("§7Remaining Today: §a$" + dailyRemaining.toPlainString()));

        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bReserve Status", body));
        return 1;
    }

    private static int handleBankDashboard(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view the dashboard."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cDashboard is available to bank owners."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, bank, gameTime, source.getServer());
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());

        BigDecimal reserve = bank.getDeclaredReserve();
        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal reserveRatio = deposits.compareTo(BigDecimal.ZERO) > 0
                ? reserve.divide(deposits, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);
        BigDecimal shortfall = minReserve.subtract(reserve).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal dailyCap = getDailyCapForBank(bank, metadata);
        BigDecimal dailyUsed = readBigDecimal(metadata, "dailyWithdrawn");
        BigDecimal outstandingLoans = sumOutstandingLoans(bank);
        BigDecimal maxLendable = calculateMaxLendable(bank);
        int queuedWithdrawals = metadata.contains("queuedWithdrawalCount") ? metadata.getInt("queuedWithdrawalCount") : 0;

        String status = getBankStatus(centralBank, bank);
        String risk;
        ChatFormatting riskColor;
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status) || "LOCKDOWN".equals(status)
                || reserve.compareTo(minReserve) < 0) {
            risk = "RED";
            riskColor = ChatFormatting.RED;
        } else if (reserveRatio.compareTo(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get() * 100 + 10)) <= 0
                || dailyCap.compareTo(BigDecimal.ZERO) > 0
                && dailyUsed.divide(dailyCap, 4, RoundingMode.HALF_EVEN).compareTo(BigDecimal.valueOf(0.90)) >= 0) {
            risk = "YELLOW";
            riskColor = ChatFormatting.YELLOW;
        } else {
            risk = "GREEN";
            riskColor = ChatFormatting.GREEN;
        }

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(Component.literal("§7Status: §f" + status + "\n"));
        body.append(Component.literal("§7Risk Level: ").append(Component.literal(risk).withStyle(riskColor)).append(Component.literal("\n")));
        body.append(Component.literal("§7Reserve: §a$" + reserve.toPlainString() + "\n"));
        body.append(Component.literal("§7Reserve Ratio: §e" + reserveRatio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(Component.literal("§7Minimum Reserve: §f$" + minReserve.toPlainString() + "\n"));
        body.append(Component.literal("§7Reserve Shortfall: §c$" + shortfall.toPlainString() + "\n"));
        body.append(Component.literal("§7Daily Capacity: §b$" + dailyCap.toPlainString() + " / used $" + dailyUsed.toPlainString() + "\n"));
        body.append(Component.literal("§7Outstanding Player Loans: §6$" + outstandingLoans.toPlainString() + "\n"));
        body.append(Component.literal("§7Max Lendable: §f$" + maxLendable.toPlainString() + "\n"));
        body.append(Component.literal("§7Queued Withdrawals: §f" + queuedWithdrawals + "\n"));
        body.append(Component.literal("§7Federal Funds Rate: §e" + centralBank.getFederalFundsRate() + "%"));

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eLiquidity Dashboard", body));
        return 1;
    }

    private static int handleBankAccountsList(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view bank account rosters."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can list account holders."));
            return 1;
        }

        List<AccountHolder> accounts = bank.getBankAccounts().values().stream()
                .sorted(Comparator.comparing(a -> a.getAccountUUID().toString()))
                .toList();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(Component.literal("§7Account Holders: §b" + accounts.size() + "\n\n"));
        for (AccountHolder account : accounts) {
            body.append(Component.literal(
                    "§8- §f" + resolvePlayerName(source.getServer(), account.getPlayerUUID())
                            + " §7(" + account.getAccountType().label + " " + shortId(account.getAccountUUID()) + ")\n"
            ));
        }
        if (accounts.isEmpty()) {
            body.append(Component.literal("§8- none"));
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bBank Accounts", body));
        return 1;
    }

    private static int handleBankCertificateSchedule(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view CD schedules."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can view CD schedules."));
            return 1;
        }
        long now = currentOverworldGameTime(source.getServer());
        List<AccountHolder> cds = bank.getBankAccounts().values().stream()
                .filter(a -> a.getAccountType() == AccountTypes.CertificateAccount)
                .sorted(Comparator.comparingLong(AccountHolder::getCertificateMaturityGameTime))
                .toList();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Active CDs: §b" + cds.size() + "\n\n"));
        for (AccountHolder cd : cds) {
            body.append(Component.literal(
                    "§8- §f" + shortId(cd.getAccountUUID())
                            + " §7holder §f" + resolvePlayerName(source.getServer(), cd.getPlayerUUID())
                            + " §7tier §f" + cd.getCertificateTier()
                            + " §7balance §6$" + cd.getBalance().toPlainString()
                            + " §7maturityTick §f" + cd.getCertificateMaturityGameTime()
                            + " §7locked §f" + cd.isCertificateLocked(now)
                            + "\n"
            ));
        }
        if (cds.isEmpty()) {
            body.append(Component.literal("§8- none"));
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eCertificate Schedule", body));
        return 1;
    }

    private static int handleBankLimitSet(CommandSourceStack source, String typeRaw, String amountRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can configure bank limits."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can configure limits."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        String type = typeRaw == null ? "" : typeRaw.trim().toLowerCase(Locale.ROOT);
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        switch (type) {
            case "single", "transaction", "singletransaction" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    source.sendSystemMessage(Component.literal("§cSingle limit cannot exceed global max $" + maxAllowed.toPlainString()));
                    return 1;
                }
                metadata.putString("limitSingle", amount.toPlainString());
            }
            case "dailyplayer", "playerdaily" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    source.sendSystemMessage(Component.literal("§cDaily player limit cannot exceed global max $" + maxAllowed.toPlainString()));
                    return 1;
                }
                metadata.putString("limitDailyPlayer", amount.toPlainString());
            }
            case "dailybank", "bankdaily" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    source.sendSystemMessage(Component.literal("§cDaily bank limit cannot exceed global max $" + maxAllowed.toPlainString()));
                    return 1;
                }
                metadata.putString("limitDailyBank", amount.toPlainString());
            }
            default -> {
                source.sendSystemMessage(Component.literal("§cUnknown limit type. Use single, dailyplayer, or dailybank."));
                return 1;
            }
        }
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal("§aUpdated " + type + " limit to $" + amount.toPlainString() + "."));
        return 1;
    }

    private static int handleBankLimitView(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view bank limits."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can view custom limits."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        BigDecimal single = metadata.contains("limitSingle")
                ? readBigDecimal(metadata, "limitSingle")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get());
        BigDecimal dailyPlayer = metadata.contains("limitDailyPlayer")
                ? readBigDecimal(metadata, "limitDailyPlayer")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get());
        BigDecimal dailyBank = metadata.contains("limitDailyBank")
                ? readBigDecimal(metadata, "limitDailyBank")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get());

        source.sendSystemMessage(ubsMessage(
                ChatFormatting.GOLD,
                "§eBank Limits",
                Component.literal("§7Bank: §e" + bank.getBankName() + "\n")
                        .append(Component.literal("§7Single Tx Limit: §f$" + single.toPlainString() + "\n"))
                        .append(Component.literal("§7Daily Player Limit: §f$" + dailyPlayer.toPlainString() + "\n"))
                        .append(Component.literal("§7Daily Bank Limit: §f$" + dailyBank.toPlainString()))
        ));
        return 1;
    }

    private static int handleBankRoleAssign(CommandSourceStack source, ServerPlayer target, String roleRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can assign roles."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can assign roles."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"ROLE_BASED".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            source.sendSystemMessage(Component.literal("§cThis bank is not configured for role-based governance."));
            return 1;
        }

        String role = roleRaw == null ? "" : roleRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("FOUNDER", "DIRECTOR", "TELLER", "AUDITOR").contains(role)) {
            source.sendSystemMessage(Component.literal("§cRole must be FOUNDER, DIRECTOR, TELLER, or AUDITOR."));
            return 1;
        }

        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));
        roleMap.put(target.getUUID(), role);
        metadata.putString("roles", encodeUuidStringMap(roleMap));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(Component.literal("§aAssigned role §e" + role + " §ato " + target.getName().getString()));
        target.sendSystemMessage(Component.literal("§aYou were assigned role §e" + role + " §aat bank " + bank.getBankName()));
        return 1;
    }

    private static int handleBankRoleRevoke(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can revoke roles."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can revoke roles."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));
        roleMap.remove(target.getUUID());
        metadata.putString("roles", encodeUuidStringMap(roleMap));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal("§aRevoked governance role for " + target.getName().getString()));
        return 1;
    }

    private static int handleBankRoleList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can list roles."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can list roles."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Governance Roles for §e" + bank.getBankName() + "\n\n"));
        if (roleMap.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            roleMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(entry -> body.append(Component.literal(
                            "§8- §f" + resolvePlayerName(source.getServer(), entry.getKey())
                                    + " §7-> §e" + entry.getValue() + "\n"
                    )));
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bRole Registry", body));
        return 1;
    }

    private static int handleBankSharesSet(CommandSourceStack source, ServerPlayer target, String percentRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can manage shares."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can manage shares."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"PERCENTAGE_SHARES".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            source.sendSystemMessage(Component.literal("§cThis bank is not using percentage-share governance."));
            return 1;
        }

        BigDecimal percent;
        try {
            percent = new BigDecimal(percentRaw.trim()).setScale(2, RoundingMode.HALF_EVEN);
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid percent."));
            return 1;
        }
        if (percent.compareTo(BigDecimal.ZERO) <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            source.sendSystemMessage(Component.literal("§cPercent must be > 0 and <= 100."));
            return 1;
        }

        Map<UUID, BigDecimal> shares = decodeShareMap(metadata.getString("shares"));
        shares.put(target.getUUID(), percent);
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : shares.values()) {
            total = total.add(value);
        }
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            source.sendSystemMessage(Component.literal(
                    "§cTotal shares would exceed 100%. Current proposed total: " + total.toPlainString() + "%"
            ));
            return 1;
        }
        metadata.putString("shares", encodeShareMap(shares));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal(
                "§aSet shares for " + target.getName().getString() + " to " + percent.toPlainString() + "%"
        ));
        return 1;
    }

    private static int handleBankSharesList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can list shares."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can list shares."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, BigDecimal> shares = decodeShareMap(metadata.getString("shares"));
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Share Distribution for §e" + bank.getBankName() + "\n\n"));
        if (shares.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            shares.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(entry -> body.append(Component.literal(
                            "§8- §f" + resolvePlayerName(source.getServer(), entry.getKey())
                                    + " §7-> §e" + entry.getValue().toPlainString() + "%\n"
                    )));
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eShares", body));
        return 1;
    }

    private static int handleBankCofounderAdd(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can manage co-founders."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can manage co-founders."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"FIXED_COFOUNDERS".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            source.sendSystemMessage(Component.literal("§cThis bank is not configured for fixed co-founders."));
            return 1;
        }

        List<UUID> cofounders = decodeUuidList(metadata.getString("cofounders"));
        if (!cofounders.contains(target.getUUID())) {
            cofounders.add(target.getUUID());
            metadata.putString("cofounders", encodeUuidList(cofounders));
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }
        source.sendSystemMessage(Component.literal("§aAdded co-founder: " + target.getName().getString()));
        return 1;
    }

    private static int handleBankCofounderList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can list co-founders."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can list co-founders."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<UUID> cofounders = decodeUuidList(metadata.getString("cofounders"));
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Co-founders for §e" + bank.getBankName() + "\n\n"));
        if (cofounders.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (UUID id : cofounders) {
                body.append(Component.literal("§8- §f" + resolvePlayerName(source.getServer(), id) + "\n"));
            }
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bCo-Founders", body));
        return 1;
    }

    private static int handleBankHire(CommandSourceStack source,
                                      ServerPlayer target,
                                      String roleRaw,
                                      String salaryRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can hire employees."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can hire employees."));
            return 1;
        }
        String role = roleRaw == null ? "" : roleRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("TELLER", "DIRECTOR", "AUDITOR", "STAFF").contains(role)) {
            source.sendSystemMessage(Component.literal("§cInvalid role. Use TELLER, DIRECTOR, AUDITOR, or STAFF."));
            return 1;
        }
        BigDecimal salary;
        try {
            salary = new BigDecimal(salaryRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid salary."));
            return 1;
        }
        if (salary.compareTo(BigDecimal.ZERO) < 0) {
            source.sendSystemMessage(Component.literal("§cSalary must be non-negative."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        employees.put(target.getUUID(), new EmployeeSpec(role, salary));
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(Component.literal("§aHired " + target.getName().getString()
                + " as " + role + " ($" + salary.toPlainString() + ")."));
        target.sendSystemMessage(Component.literal("§aYou were hired by " + bank.getBankName()
                + " as " + role + " ($" + salary.toPlainString() + ")."));
        return 1;
    }

    private static int handleBankFire(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can fire employees."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can fire employees."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        employees.remove(target.getUUID());
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal("§eFired employee " + target.getName().getString()));
        target.sendSystemMessage(Component.literal("§cYou were removed from employment at " + bank.getBankName()));
        return 1;
    }

    private static int handleBankEmployeesList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can list employees."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can list employees."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Employees at §e" + bank.getBankName() + "\n\n"));
        if (employees.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            employees.forEach((id, employee) -> body.append(Component.literal(
                    "§8- §f" + resolvePlayerName(source.getServer(), id)
                            + " §7role §e" + employee.role()
                            + " §7salary §6$" + employee.salary().toPlainString() + "\n"
            )));
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eEmployees", body));
        return 1;
    }

    private static int handleBankEmployeeQuit(CommandSourceStack source, String bankNameRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can quit bank employment."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        if (employees.remove(player.getUUID()) == null) {
            source.sendSystemMessage(Component.literal("§cYou are not employed at this bank."));
            return 1;
        }
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal("§aYou resigned from " + bank.getBankName() + "."));
        return 1;
    }

    private static int handleBankLoanProductCreate(CommandSourceStack source,
                                                   String name,
                                                   String maxAmountRaw,
                                                   String interestRateRaw,
                                                   String durationTicksRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can create loan products."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can create loan products."));
            return 1;
        }

        BigDecimal maxAmount = parsePositiveWholeAmount(source, maxAmountRaw);
        if (maxAmount == null) {
            return 1;
        }
        double rate;
        try {
            rate = Double.parseDouble(interestRateRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid interest rate."));
            return 1;
        }
        if (rate <= 0.0) {
            source.sendSystemMessage(Component.literal("§cInterest rate must be positive."));
            return 1;
        }
        long durationTicks;
        try {
            durationTicks = Long.parseLong(durationTicksRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid duration ticks."));
            return 1;
        }
        if (durationTicks < 20L) {
            source.sendSystemMessage(Component.literal("§cDuration must be at least 20 ticks."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
        products.removeIf(product -> product.name().equalsIgnoreCase(name));
        products.add(new LoanProductSpec(name, maxAmount, rate, durationTicks));
        metadata.putString("loanProducts", encodeLoanProducts(products));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(Component.literal(
                "§aLoan product created: §e" + name
                        + " §7max $"+ maxAmount.toPlainString()
                        + " §7APR " + rate + "% §7duration " + durationTicks + " ticks."
        ));
        return 1;
    }

    private static int handleBankLoanProductList(CommandSourceStack source, String bankNameRaw) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Loan products at §e" + bank.getBankName() + "\n\n"));
        if (products.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (LoanProductSpec product : products) {
                body.append(Component.literal(
                        "§8- §f" + product.name()
                                + " §7max §6$" + product.maxAmount().toPlainString()
                                + " §7APR §e" + product.interestRate() + "%"
                                + " §7duration §f" + product.durationTicks() + " ticks\n"
                ));
            }
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eLoan Products", body));
        return 1;
    }

    private static int handleBankLoanProductApply(CommandSourceStack source,
                                                  String bankNameRaw,
                                                  String productName,
                                                  String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can apply for loan products."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        AccountHolder account = findPlayerAccountInBank(bank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cYou need an account at this bank to apply."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
        LoanProductSpec product = products.stream()
                .filter(entry -> entry.name().equalsIgnoreCase(productName))
                .findFirst()
                .orElse(null);
        if (product == null) {
            source.sendSystemMessage(Component.literal("§cLoan product not found: " + productName));
            return 1;
        }

        BigDecimal requested = parsePositiveWholeAmount(source, amountRaw);
        if (requested == null) {
            return 1;
        }
        if (requested.compareTo(product.maxAmount()) > 0) {
            source.sendSystemMessage(Component.literal(
                    "§cRequested amount exceeds product max of $" + product.maxAmount().toPlainString()
            ));
            return 1;
        }
        if (!bank.canIssueLoan(requested)) {
            source.sendSystemMessage(Component.literal(
                    "§cLoan blocked by fractional reserve limit. Max lendable currently: $"
                            + bank.getMaxLendableAmount().toPlainString()
            ));
            return 1;
        }

        long interval = Math.max(20L, Config.LOAN_PAYMENT_INTERVAL_TICKS.get());
        int payments = Math.max(1, (int) Math.ceil(product.durationTicks() / (double) interval));
        BigDecimal totalRepayable = requested
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(product.interestRate())
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN)))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal periodic = totalRepayable.divide(BigDecimal.valueOf(payments), 2, RoundingMode.HALF_EVEN);
        long gameTime = currentOverworldGameTime(source.getServer());

        LoanService.LoanQuote quote = new LoanService.LoanQuote(
                account.getAccountUUID(),
                account.getPlayerUUID(),
                bank.getBankId(),
                requested,
                product.interestRate(),
                totalRepayable,
                periodic,
                payments,
                interval,
                gameTime + interval,
                false,
                "BANK_PRODUCT:" + product.name()
        );
        var issued = LoanService.issueLoan(source.getServer(), quote);
        if (issued == null) {
            source.sendSystemMessage(Component.literal("§cLoan issuance failed (reserve or compliance restrictions)."));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§aLoan approved from product §e" + product.name()
                        + "§a for §6$" + requested.toPlainString()
                        + "§a. Repayment: §f" + payments + " x $" + periodic.toPlainString()
        ));
        return 1;
    }

    private static int handleBorrowFromCentralBank(CommandSourceStack source, String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can borrow for banks."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            source.sendSystemMessage(Component.literal(
                    "§cBorrow amount exceeds global transaction cap of $" + Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()
            ));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can borrow from the Central Bank."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, bank, gameTime, source.getServer());
        String status = getBankStatus(centralBank, bank);
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status)) {
            source.sendSystemMessage(Component.literal("§cYour bank cannot borrow while " + status.toLowerCase(Locale.ROOT) + "."));
            return 1;
        }

        double annualRate = Math.max(Config.LOAN_BASE_INTEREST_RATE.get() + 2.0, centralBank.getFederalFundsRate());
        int payments = Math.max(1, Config.LOAN_TERM_PAYMENTS.get());
        long interval = Math.max(20, Config.LOAN_PAYMENT_INTERVAL_TICKS.get());
        BigDecimal totalRepayable = amount
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(annualRate).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN)))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal periodic = totalRepayable
                .divide(BigDecimal.valueOf(payments), 2, RoundingMode.HALF_EVEN);

        UUID loanId = UUID.randomUUID();
        CompoundTag loan = new CompoundTag();
        loan.putUUID("id", loanId);
        loan.putString("type", "CB_DISCOUNT");
        loan.putUUID("bankId", bank.getBankId());
        loan.putString("principal", amount.toPlainString());
        loan.putString("remaining", totalRepayable.toPlainString());
        loan.putDouble("annualRate", annualRate);
        loan.putString("periodicPayment", periodic.toPlainString());
        loan.putInt("paymentsRemaining", payments);
        loan.putLong("paymentIntervalTicks", interval);
        loan.putLong("nextDueTick", gameTime + interval);
        loan.putLong("createdTick", gameTime);
        loan.putString("status", "ACTIVE");
        centralBank.getInterbankLoans().put(loanId, loan);

        bank.setReserve(bank.getDeclaredReserve().add(amount));
        recordSettlement(centralBank, centralBank.getBankId(), bank.getBankId(), amount, "CB_LOAN_DISBURSEMENT", true);
        BankManager.markDirty();

        source.sendSystemMessage(Component.literal(
                "§aCentral Bank loan issued.\n"
                        + "§7Loan ID: §f" + loanId + "\n"
                        + "§7Principal: §6$" + amount.toPlainString() + "\n"
                        + "§7APR: §e" + annualRate + "%\n"
                        + "§7Repayment: §f" + payments + " x $" + periodic.toPlainString()
        ));
        return 1;
    }

    private static int handleBankLoanSummary(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can view bank loans."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can view this loan summary."));
            return 1;
        }

        List<Map.Entry<UUID, CompoundTag>> loans = centralBank.getInterbankLoans().entrySet().stream()
                .filter(entry -> bank.getBankId().equals(readUuid(entry.getValue(), "bankId")))
                .filter(entry -> "ACTIVE".equalsIgnoreCase(entry.getValue().getString("status")))
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(Component.literal("§7Outstanding CB Loans: §b" + loans.size() + "\n\n"));
        if (loans.isEmpty()) {
            body.append(Component.literal("§8No active Central Bank loans."));
        } else {
            for (Map.Entry<UUID, CompoundTag> entry : loans) {
                CompoundTag loan = entry.getValue();
                body.append(Component.literal(
                        "§8- §f" + shortId(entry.getKey())
                                + " §7remaining §6$" + readBigDecimal(loan, "remaining").toPlainString()
                                + " §7next due tick §f" + loan.getLong("nextDueTick")
                                + " §7payment §f$" + readBigDecimal(loan, "periodicPayment").toPlainString()
                                + "\n"
                ));
            }
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eCentral Borrowing", body));
        return 1;
    }

    private static int handleInterbankOffer(CommandSourceStack source,
                                            String amountRaw,
                                            String annualRateRaw,
                                            String termTicksRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can post inter-bank offers."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        double annualRate;
        try {
            annualRate = Double.parseDouble(annualRateRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid annual rate."));
            return 1;
        }
        if (annualRate <= 0.0 || annualRate > 1000.0) {
            source.sendSystemMessage(Component.literal("§cAnnual rate must be > 0 and <= 1000."));
            return 1;
        }

        long termTicks;
        try {
            termTicks = Long.parseLong(termTicksRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid term ticks."));
            return 1;
        }
        if (termTicks < 20L) {
            source.sendSystemMessage(Component.literal("§cTerm must be at least 20 ticks."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank lenderBank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (lenderBank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can post lending offers."));
            return 1;
        }
        if (lenderBank.getDeclaredReserve().compareTo(amount) < 0) {
            source.sendSystemMessage(Component.literal("§cInsufficient reserve to back this offer."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        UUID offerId = UUID.randomUUID();
        CompoundTag offer = new CompoundTag();
        offer.putUUID("id", offerId);
        offer.putUUID("lenderBankId", lenderBank.getBankId());
        offer.putString("amount", amount.toPlainString());
        offer.putDouble("annualRate", annualRate);
        offer.putLong("termTicks", termTicks);
        offer.putLong("createdTick", gameTime);
        offer.putLong("expiryTick", gameTime + Math.max(termTicks, Config.WITHDRAWAL_QUEUE_EXPIRY_TICKS.get()));
        offer.putString("status", "OPEN");
        centralBank.getInterbankOffers().put(offerId, offer);
        BankManager.markDirty();

        source.sendSystemMessage(Component.literal(
                "§aInter-bank offer posted.\n"
                        + "§7Offer ID: §f" + offerId + "\n"
                        + "§7Amount: §6$" + amount.toPlainString() + "\n"
                        + "§7APR: §e" + annualRate + "%\n"
                        + "§7Term: §f" + termTicks + " ticks"
        ));
        return 1;
    }

    private static int handleInterbankMarket(CommandSourceStack source) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        long nowTick = currentOverworldGameTime(source.getServer());

        List<CompoundTag> offers = centralBank.getInterbankOffers().values().stream()
                .filter(tag -> "OPEN".equalsIgnoreCase(tag.getString("status")))
                .filter(tag -> !tag.contains("expiryTick") || tag.getLong("expiryTick") >= nowTick)
                .sorted(Comparator.comparingLong(tag -> tag.getLong("createdTick")))
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Open Offers: §b" + offers.size() + "\n\n"));
        if (offers.isEmpty()) {
            body.append(Component.literal("§8No open inter-bank offers."));
        } else {
            for (CompoundTag offer : offers) {
                UUID lenderId = offer.getUUID("lenderBankId");
                Bank lender = centralBank.getBank(lenderId);
                body.append(Component.literal(
                        "§8- §f" + shortId(offer.getUUID("id"))
                                + " §7lender §e" + (lender == null ? shortId(lenderId) : lender.getBankName())
                                + " §7amount §6$" + readBigDecimal(offer, "amount").toPlainString()
                                + " §7APR §e" + offer.getDouble("annualRate") + "%"
                                + " §7term §f" + offer.getLong("termTicks") + "\n"
                ));
            }
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bInter-Bank Lending Market", body));
        return 1;
    }

    private static int handleInterbankAccept(CommandSourceStack source, UUID offerId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can accept inter-bank offers."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        CompoundTag offer = centralBank.getInterbankOffers().get(offerId);
        if (offer == null) {
            source.sendSystemMessage(Component.literal("§cOffer not found: " + offerId));
            return 1;
        }
        if (!"OPEN".equalsIgnoreCase(offer.getString("status"))) {
            source.sendSystemMessage(Component.literal("§cOffer is not open."));
            return 1;
        }

        long nowTick = currentOverworldGameTime(source.getServer());
        if (offer.contains("expiryTick") && offer.getLong("expiryTick") < nowTick) {
            offer.putString("status", "EXPIRED");
            centralBank.getInterbankOffers().put(offerId, offer);
            BankManager.markDirty();
            source.sendSystemMessage(Component.literal("§cOffer has expired."));
            return 1;
        }

        Bank borrowerBank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (borrowerBank == null) {
            source.sendSystemMessage(Component.literal("§cOnly bank owners can accept inter-bank offers."));
            return 1;
        }

        UUID lenderBankId = offer.getUUID("lenderBankId");
        Bank lenderBank = centralBank.getBank(lenderBankId);
        if (lenderBank == null) {
            source.sendSystemMessage(Component.literal("§cLender bank no longer exists."));
            return 1;
        }
        if (lenderBank.getBankId().equals(borrowerBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cYou cannot accept your own offer."));
            return 1;
        }

        BigDecimal principal = readBigDecimal(offer, "amount");
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(Component.literal("§cOffer amount is invalid."));
            return 1;
        }
        if (lenderBank.getDeclaredReserve().compareTo(principal) < 0) {
            source.sendSystemMessage(Component.literal("§cLender bank no longer has sufficient reserve."));
            recordSettlement(centralBank, lenderBank.getBankId(), borrowerBank.getBankId(), principal,
                    "INTERBANK_ACCEPT_FAILED_INSUFFICIENT_LENDER_RESERVE", false);
            return 1;
        }

        double annualRate = offer.getDouble("annualRate");
        long termTicks = Math.max(20L, offer.getLong("termTicks"));
        BigDecimal totalRepayable = principal
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(annualRate).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN)))
                .setScale(2, RoundingMode.HALF_EVEN);

        lenderBank.setReserve(lenderBank.getDeclaredReserve().subtract(principal));
        borrowerBank.setReserve(borrowerBank.getDeclaredReserve().add(principal));

        UUID loanId = UUID.randomUUID();
        CompoundTag loan = new CompoundTag();
        loan.putUUID("id", loanId);
        loan.putString("type", "INTERBANK");
        loan.putUUID("lenderBankId", lenderBank.getBankId());
        loan.putUUID("borrowerBankId", borrowerBank.getBankId());
        loan.putString("principal", principal.toPlainString());
        loan.putString("remaining", totalRepayable.toPlainString());
        loan.putDouble("annualRate", annualRate);
        loan.putLong("termTicks", termTicks);
        loan.putLong("createdTick", nowTick);
        loan.putLong("maturityTick", nowTick + termTicks);
        loan.putString("status", "ACTIVE");
        centralBank.getInterbankLoans().put(loanId, loan);

        offer.putString("status", "ACCEPTED");
        offer.putUUID("acceptedByBankId", borrowerBank.getBankId());
        offer.putLong("acceptedTick", nowTick);
        centralBank.getInterbankOffers().put(offerId, offer);
        BankManager.markDirty();

        recordSettlement(
                centralBank,
                lenderBank.getBankId(),
                borrowerBank.getBankId(),
                principal,
                "INTERBANK_LOAN_ORIGINATION:" + loanId,
                true
        );

        source.sendSystemMessage(Component.literal(
                "§aAccepted inter-bank offer " + shortId(offerId)
                        + " for $" + principal.toPlainString()
                        + ". Maturity tick: " + (nowTick + termTicks)
        ));

        ServerPlayer lenderOwner = source.getServer().getPlayerList().getPlayer(lenderBank.getBankOwnerId());
        if (lenderOwner != null) {
            lenderOwner.sendSystemMessage(Component.literal(
                    "§aYour inter-bank offer " + shortId(offerId)
                            + " was accepted by " + borrowerBank.getBankName()
                            + " for $" + principal.toPlainString()
            ));
        }
        return 1;
    }

    private static int handleBankAppeal(CommandSourceStack source, String messageRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can submit appeals."));
            return 1;
        }

        String message = messageRaw == null ? "" : messageRaw.trim();
        if (message.isBlank()) {
            source.sendSystemMessage(Component.literal("§cAppeal message cannot be empty."));
            return 1;
        }
        if (message.length() > 256) {
            source.sendSystemMessage(Component.literal("§cAppeal message is too long (max 256 chars)."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        boolean hasPendingAppeal = centralBank.getBankAppeals().values().stream()
                .anyMatch(tag -> player.getUUID().equals(readUuid(tag, "playerId"))
                        && "PENDING".equalsIgnoreCase(tag.getString("status")));
        if (hasPendingAppeal) {
            source.sendSystemMessage(Component.literal("§cYou already have a pending appeal."));
            return 1;
        }

        UUID appealId = UUID.randomUUID();
        CompoundTag appeal = new CompoundTag();
        appeal.putUUID("id", appealId);
        appeal.putUUID("playerId", player.getUUID());
        appeal.putString("playerName", player.getName().getString());
        appeal.putString("message", message);
        appeal.putString("status", "PENDING");
        appeal.putLong("createdMillis", System.currentTimeMillis());
        centralBank.getBankAppeals().put(appealId, appeal);
        BankManager.markDirty();

        source.sendSystemMessage(Component.literal(
                "§aAppeal submitted. ID: §f" + appealId + "\n§7Message: §f" + message
        ));

        for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
            if (!online.hasPermissions(3)) {
                continue;
            }
            online.sendSystemMessage(Component.literal(
                    "§6[UBS] New bank appeal from " + player.getName().getString()
                            + " (ID: " + appealId + ")."
            ));
        }

        return 1;
    }

    private static String normalizeBankName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeOwnershipModel(String raw) {
        if (raw == null) {
            return "SOLE";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        return switch (normalized) {
            case "sole", "single", "owner" -> "SOLE";
            case "fixedcofounders", "cofounders", "cofounder", "fixed" -> "FIXED_COFOUNDERS";
            case "percentageshares", "shares", "share" -> "PERCENTAGE_SHARES";
            case "rolebased", "roles", "role" -> "ROLE_BASED";
            default -> "SOLE";
        };
    }

    private static String normalizeCertificateTier(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("short") || normalized.equals("medium") || normalized.equals("long")) {
            return normalized;
        }
        return "";
    }

    private static AccountTypes parseAccountType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AccountTypes.CheckingAccount;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        return switch (normalized) {
            case "checking", "checkingaccount", "check" -> AccountTypes.CheckingAccount;
            case "saving", "savings", "savingaccount" -> AccountTypes.SavingAccount;
            case "moneymarket", "moneymarketaccount", "mma" -> AccountTypes.MoneyMarketAccount;
            case "certificate", "certificateaccount", "certificateofdeposit", "cd", "cert" ->
                    AccountTypes.CertificateAccount;
            default -> null;
        };
    }

    private static Bank resolveBankByName(CentralBank centralBank, String bankNameRaw) {
        if (centralBank == null || bankNameRaw == null) {
            return null;
        }
        String requested = normalizeBankName(bankNameRaw);
        if (requested.isBlank()) {
            return null;
        }
        return centralBank.getBanks().values().stream()
                .filter(bank -> bank.getBankName() != null)
                .filter(bank -> bank.getBankName().trim().equalsIgnoreCase(requested))
                .findFirst()
                .orElse(null);
    }

    private static List<Bank> getOwnedBanks(CentralBank centralBank, UUID ownerId) {
        if (centralBank == null || ownerId == null) {
            return List.of();
        }
        return centralBank.getBanks().values().stream()
                .filter(bank -> !bank.getBankId().equals(centralBank.getBankId()))
                .filter(bank -> ownerId.equals(bank.getBankOwnerId()))
                .sorted(Comparator.comparing(Bank::getBankName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Bank resolveOwnedBankForPlayer(CentralBank centralBank, UUID ownerId) {
        List<Bank> owned = getOwnedBanks(centralBank, ownerId);
        if (owned.isEmpty()) {
            return null;
        }
        AccountHolder primary = findPrimaryAccount(centralBank, ownerId);
        if (primary != null) {
            for (Bank bank : owned) {
                if (bank.getBankId().equals(primary.getBankId())) {
                    return bank;
                }
            }
        }
        return owned.get(0);
    }

    private static List<AccountHolder> getPlayerAccountsInBank(Bank bank, UUID playerId) {
        if (bank == null || playerId == null) {
            return List.of();
        }
        return bank.getBankAccounts().values().stream()
                .filter(account -> playerId.equals(account.getPlayerUUID()))
                .sorted(Comparator.comparing(account -> account.getAccountUUID().toString()))
                .toList();
    }

    private static AccountHolder findPlayerAccountInBank(Bank bank, UUID playerId) {
        List<AccountHolder> matches = getPlayerAccountsInBank(bank, playerId);
        if (matches.isEmpty()) {
            return null;
        }
        for (AccountHolder account : matches) {
            if (account.isPrimaryAccount()) {
                return account;
            }
        }
        return matches.get(0);
    }

    private static String getBankStatus(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return "UNKNOWN";
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String status = metadata.getString("status");
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean allowsCustomerTransfers(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("ACTIVE") || normalized.equals("WARNING");
    }

    private static BigDecimal getDailyCapForBank(Bank bank, CompoundTag metadata) {
        if (bank == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal configuredCap = bank.getDeclaredReserve()
                .multiply(BigDecimal.valueOf(Config.BANK_DAILY_LIQUIDITY_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);

        if (metadata != null && metadata.contains("dailyCapOverride")) {
            String overrideRaw = metadata.getString("dailyCapOverride");
            if (overrideRaw != null && !overrideRaw.isBlank()) {
                try {
                    BigDecimal override = new BigDecimal(overrideRaw);
                    if (override.compareTo(BigDecimal.ZERO) >= 0) {
                        return override.setScale(2, RoundingMode.HALF_EVEN);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return configuredCap.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal readBigDecimal(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.contains(key)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(tag.getString(key));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.hasUUID(key)) {
            return null;
        }
        return tag.getUUID(key);
    }

    private static BigDecimal sumOutstandingLoans(Bank bank) {
        if (bank == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (AccountHolder account : bank.getBankAccounts().values()) {
            if (account == null) {
                continue;
            }
            for (var loan : account.getActiveLoans().values()) {
                if (loan == null || loan.isDefaulted()) {
                    continue;
                }
                total = total.add(loan.getRemainingBalance());
            }
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal calculateMaxLendable(Bank bank) {
        if (bank == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal reserveRatio = BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get());
        BigDecimal lendableFactor = BigDecimal.ONE.subtract(reserveRatio);
        if (lendableFactor.compareTo(BigDecimal.ZERO) < 0) {
            lendableFactor = BigDecimal.ZERO;
        }
        return deposits.multiply(lendableFactor).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static boolean consumeBankWithdrawalCapacity(CommandSourceStack source,
                                                         CentralBank centralBank,
                                                         Bank bank,
                                                         BigDecimal amount,
                                                         long gameTime) {
        if (centralBank == null || bank == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (bank.getBankId().equals(centralBank.getBankId())) {
            return true;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        long day = gameTime / 24000L;
        long storedDay = metadata.contains("dailyWindowDay") ? metadata.getLong("dailyWindowDay") : Long.MIN_VALUE;
        BigDecimal dailyWithdrawn = readBigDecimal(metadata, "dailyWithdrawn");
        if (storedDay != day) {
            dailyWithdrawn = BigDecimal.ZERO;
        }
        BigDecimal dailyCap = getDailyCapForBank(bank, metadata);
        BigDecimal after = dailyWithdrawn.add(amount);
        if (after.compareTo(dailyCap) > 0) {
            source.sendSystemMessage(Component.literal(
                    "§cTransfer blocked by daily bank liquidity cap. Cap: $" + dailyCap.toPlainString()
                            + ", used: $" + dailyWithdrawn.toPlainString()
                            + ", attempted: $" + amount.toPlainString()
            ));
            return false;
        }

        BigDecimal minReserve = bank.getTotalDeposits()
                .multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal reserveAfter = bank.getDeclaredReserve().subtract(amount).setScale(2, RoundingMode.HALF_EVEN);
        if (reserveAfter.compareTo(minReserve) < 0) {
            int queueCount = metadata.contains("queuedWithdrawalCount") ? metadata.getInt("queuedWithdrawalCount") : 0;
            queueCount++;
            metadata.putInt("queuedWithdrawalCount", queueCount);
            metadata.putString("lastQueuedWithdrawalAmount", amount.toPlainString());
            metadata.putLong("lastQueuedWithdrawalTick", gameTime);
            metadata.putString("lastQueuedWithdrawalAccount", source.getPlayer() == null
                    ? ""
                    : source.getPlayer().getUUID().toString());
            centralBank.putBankMetadata(bank.getBankId(), metadata);

            source.sendSystemMessage(Component.literal(
                    "§eRequest queued: this withdrawal would breach the bank minimum reserve. Queue position: "
                            + queueCount
            ));
            return false;
        }

        metadata.putLong("dailyWindowDay", day);
        metadata.putString("dailyWithdrawn", after.setScale(2, RoundingMode.HALF_EVEN).toPlainString());

        long windowTicks = Math.max(20, Config.BANK_RUN_WINDOW_TICKS.get());
        long startTick = metadata.contains("bankRunWindowStartTick") ? metadata.getLong("bankRunWindowStartTick") : gameTime;
        BigDecimal windowWithdrawn = readBigDecimal(metadata, "bankRunWindowWithdrawn");
        if ((gameTime - startTick) > windowTicks) {
            startTick = gameTime;
            windowWithdrawn = BigDecimal.ZERO;
        }
        windowWithdrawn = windowWithdrawn.add(amount);
        metadata.putLong("bankRunWindowStartTick", startTick);
        metadata.putString("bankRunWindowWithdrawn", windowWithdrawn.setScale(2, RoundingMode.HALF_EVEN).toPlainString());

        BigDecimal threshold = bank.getTotalDeposits()
                .multiply(BigDecimal.valueOf(Config.BANK_RUN_THRESHOLD_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        if (threshold.compareTo(BigDecimal.ZERO) > 0
                && windowWithdrawn.compareTo(threshold) > 0
                && !"LOCKDOWN".equalsIgnoreCase(getBankStatus(centralBank, bank))) {
            long until = gameTime + Math.max(20, Config.BANK_RUN_LOCKDOWN_TICKS.get());
            metadata.putString("status", "LOCKDOWN");
            metadata.putLong("lockdownUntilTick", until);
            source.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§c[UBS] Bank run detected at " + bank.getBankName()
                            + ". Withdrawals are paused until tick " + until + "."),
                    false
            );
        }

        centralBank.putBankMetadata(bank.getBankId(), metadata);
        return true;
    }

    private static void refreshBankOperationalState(CentralBank centralBank,
                                                    Bank bank,
                                                    long gameTime,
                                                    MinecraftServer server) {
        if (centralBank == null || bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            return;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String previousStatus = getBankStatus(centralBank, bank);
        String nextStatus = previousStatus;

        if ("LOCKDOWN".equals(previousStatus) && metadata.contains("lockdownUntilTick")) {
            long until = metadata.getLong("lockdownUntilTick");
            if (gameTime >= until) {
                nextStatus = "ACTIVE";
            }
        }

        BigDecimal reserve = bank.getDeclaredReserve();
        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        long graceTicks = Math.max(20, Config.BANK_RESERVE_GRACE_TICKS.get());

        if (reserve.compareTo(minReserve) < 0) {
            long breachTick = metadata.contains("reserveBreachStartTick")
                    ? metadata.getLong("reserveBreachStartTick")
                    : gameTime;
            metadata.putLong("reserveBreachStartTick", breachTick);
            if ((gameTime - breachTick) >= graceTicks) {
                if (!"SUSPENDED".equals(previousStatus) && !"REVOKED".equals(previousStatus)) {
                    nextStatus = "RESTRICTED";
                }
            } else if (!"SUSPENDED".equals(previousStatus) && !"REVOKED".equals(previousStatus)) {
                nextStatus = "WARNING";
            }
        } else {
            metadata.remove("reserveBreachStartTick");
            if ("WARNING".equals(previousStatus) || "RESTRICTED".equals(previousStatus)) {
                nextStatus = "ACTIVE";
            }
        }

        if (!nextStatus.equals(previousStatus)) {
            metadata.putString("status", nextStatus);
            notifyBankOwnerStatusChange(server, bank, previousStatus, nextStatus);
        }

        long day = gameTime / 24000L;
        if (!metadata.contains("dailyWindowDay") || metadata.getLong("dailyWindowDay") != day) {
            metadata.putLong("dailyWindowDay", day);
            metadata.putString("dailyWithdrawn", "0");
            metadata.putInt("queuedWithdrawalCount", 0);
        }

        centralBank.putBankMetadata(bank.getBankId(), metadata);
    }

    private static void notifyBankOwnerStatusChange(MinecraftServer server, Bank bank, String oldStatus, String newStatus) {
        if (server == null || bank == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(bank.getBankOwnerId());
        if (owner == null) {
            return;
        }
        owner.sendSystemMessage(Component.literal(
                "§6[UBS] Bank status update for " + bank.getBankName()
                        + ": " + oldStatus + " -> " + newStatus
        ));
    }

    private static void recordSettlement(CentralBank centralBank,
                                         UUID fromBankId,
                                         UUID toBankId,
                                         BigDecimal amount,
                                         String reason,
                                         boolean success) {
        if (centralBank == null || fromBankId == null || toBankId == null || amount == null) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        UUID settlementId = UUID.randomUUID();
        entry.putUUID("id", settlementId);
        entry.putUUID("fromBankId", fromBankId);
        entry.putUUID("toBankId", toBankId);
        entry.putString("amount", amount.toPlainString());
        entry.putLong("timestampMillis", System.currentTimeMillis());
        entry.putString("reason", reason == null ? "" : reason);
        entry.putBoolean("success", success);

        if (success) {
            centralBank.getSettlementLedger().put(settlementId, entry);
            trimTagMap(centralBank.getSettlementLedger(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
        } else {
            centralBank.getSettlementSuspense().put(settlementId, entry);
            trimTagMap(centralBank.getSettlementSuspense(), Math.max(1, Config.CLEARING_LEDGER_LIMIT.get()));
        }
        BankManager.markDirty();
    }

    private static void trimTagMap(ConcurrentHashMap<UUID, CompoundTag> map, int maxSize) {
        if (map == null || maxSize < 1 || map.size() <= maxSize) {
            return;
        }
        List<Map.Entry<UUID, CompoundTag>> entries = map.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getLong("timestampMillis")))
                .toList();
        int removeCount = map.size() - maxSize;
        for (int i = 0; i < removeCount && i < entries.size(); i++) {
            map.remove(entries.get(i).getKey());
        }
    }

    private static String normalizeBankColor(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.matches("^#[0-9a-fA-F]{6}$")) {
            return value.toUpperCase(Locale.ROOT);
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "blue" -> "#55AAFF";
            case "lightblue", "aqua", "cyan" -> "#55FFFF";
            case "green" -> "#55FF55";
            case "red" -> "#FF5555";
            case "gold", "orange" -> "#FFAA00";
            case "yellow" -> "#FFFF55";
            case "white" -> "#FFFFFF";
            case "gray", "grey" -> "#AAAAAA";
            case "black" -> "#000000";
            case "purple", "magenta" -> "#AA55FF";
            default -> null;
        };
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

    private static int handleCdBreakRequest(CommandSourceStack source, UUID accountId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can request CD early withdrawal."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder cdAccount = centralBank.SearchForAccountByAccountId(accountId);
        if (cdAccount == null || !cdAccount.getPlayerUUID().equals(player.getUUID())) {
            source.sendSystemMessage(Component.literal("§cCD account not found or not owned by you."));
            return 1;
        }
        if (cdAccount.getAccountType() != AccountTypes.CertificateAccount) {
            source.sendSystemMessage(Component.literal("§cThat account is not a Certificate of Deposit account."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        if (!cdAccount.isCertificateLocked(gameTime)) {
            source.sendSystemMessage(Component.literal("§aThis CD is already matured/unlocked. No penalty required."));
            return 1;
        }

        String tier = cdAccount.getCertificateTier();
        long termTicks = switch (tier) {
            case "short" -> Config.CD_SHORT_TERM_TICKS.get();
            case "medium" -> Config.CD_MEDIUM_TERM_TICKS.get();
            case "long" -> Config.CD_LONG_TERM_TICKS.get();
            default -> Config.CD_SHORT_TERM_TICKS.get();
        };
        double penaltyFactor = switch (tier) {
            case "short" -> Config.CD_EARLY_PENALTY_FACTOR_SHORT.get();
            case "medium" -> Config.CD_EARLY_PENALTY_FACTOR_MEDIUM.get();
            case "long" -> Config.CD_EARLY_PENALTY_FACTOR_LONG.get();
            default -> 0.50D;
        };

        BigDecimal principal = cdAccount.getBalance();
        long maturityTick = cdAccount.getCertificateMaturityGameTime();
        long startTick = Math.max(0L, maturityTick - Math.max(1L, termTicks));
        long elapsedTicks = Math.max(0L, Math.min(Math.max(1L, termTicks), gameTime - startTick));
        BigDecimal elapsedYears = BigDecimal.valueOf(elapsedTicks)
                .divide(BigDecimal.valueOf(24000D * 365D), 10, RoundingMode.HALF_EVEN);

        BigDecimal earnedInterest = principal
                .multiply(BigDecimal.valueOf(cdAccount.getCertificateRate()).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN))
                .multiply(elapsedYears)
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal penalty;
        if (earnedInterest.compareTo(BigDecimal.ZERO) > 0) {
            penalty = earnedInterest
                    .multiply(BigDecimal.valueOf(Math.max(0.0D, penaltyFactor)))
                    .setScale(2, RoundingMode.HALF_EVEN);
        } else {
            penalty = principal.multiply(BigDecimal.valueOf(0.01D)).setScale(2, RoundingMode.HALF_EVEN);
        }
        if (penalty.compareTo(principal) > 0) {
            penalty = principal;
        }
        BigDecimal payout = principal.subtract(penalty).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);

        CompoundTag pending = new CompoundTag();
        pending.putUUID("accountId", cdAccount.getAccountUUID());
        pending.putString("principal", principal.toPlainString());
        pending.putString("penalty", penalty.toPlainString());
        pending.putString("payout", payout.toPlainString());
        pending.putLong("expiresAtMillis", System.currentTimeMillis() + 60_000L);
        pending.putString("tier", tier);
        PENDING_CD_BREAK_CONFIRMATIONS.put(player.getUUID(), pending);

        source.sendSystemMessage(Component.literal(
                "§eEarly CD withdrawal confirmation required.\n"
                        + "§7Account: §f" + shortId(cdAccount.getAccountUUID()) + "\n"
                        + "§7Principal: §6$" + principal.toPlainString() + "\n"
                        + "§7Penalty: §c$" + penalty.toPlainString() + " §8(" + (int) Math.round(penaltyFactor * 100) + "% of earned interest)\n"
                        + "§7Net payout: §a$" + payout.toPlainString() + "\n"
                        + "§7Run §f/bank confirm §7within 60 seconds to proceed."
        ));
        return 1;
    }

    private static int handleBankConfirm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can confirm this action."));
            return 1;
        }

        CompoundTag pending = PENDING_CD_BREAK_CONFIRMATIONS.get(player.getUUID());
        if (pending == null) {
            source.sendSystemMessage(Component.literal("§cNo pending confirmation action."));
            return 1;
        }

        if (System.currentTimeMillis() > pending.getLong("expiresAtMillis")) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(Component.literal("§cConfirmation expired. Start the action again."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder cdAccount = centralBank.SearchForAccountByAccountId(pending.getUUID("accountId"));
        if (cdAccount == null || !cdAccount.getPlayerUUID().equals(player.getUUID())) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(Component.literal("§cCD account is no longer available."));
            return 1;
        }

        BigDecimal principal = readBigDecimal(pending, "principal");
        BigDecimal penalty = readBigDecimal(pending, "penalty");
        BigDecimal payout = readBigDecimal(pending, "payout");
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(Component.literal("§cInvalid pending action."));
            return 1;
        }

        Bank bank = centralBank.getBank(cdAccount.getBankId());
        if (bank == null) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(Component.literal("§cBank no longer exists for this CD."));
            return 1;
        }

        AccountHolder destination = getPlayerAccountsInBank(bank, player.getUUID()).stream()
                .filter(account -> account.getAccountType() == AccountTypes.CheckingAccount)
                .findFirst()
                .orElse(null);
        if (destination == null) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(Component.literal("§cYou need a checking account at this bank to receive the payout."));
            return 1;
        }

        if (!cdAccount.forceRemoveBalance(principal)) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(Component.literal("§cFailed to release CD principal."));
            return 1;
        }
        if (!destination.forceAddBalance(payout)) {
            cdAccount.forceAddBalance(principal);
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(Component.literal("§cFailed to transfer payout to destination account."));
            return 1;
        }

        if (penalty.compareTo(BigDecimal.ZERO) > 0) {
            bank.setReserve(bank.getDeclaredReserve().add(penalty));
        }

        cdAccount.configureCertificate("", -1L, 0.0D);
        cdAccount.setCertificateMaturitySettled(true);

        UserTransaction tx = new UserTransaction(
                cdAccount.getAccountUUID(),
                destination.getAccountUUID(),
                payout,
                LocalDateTime.now(),
                "EARLY_WITHDRAWAL:" + penalty.toPlainString()
        );
        cdAccount.addTransaction(tx);
        destination.addTransaction(tx);

        PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
        source.sendSystemMessage(Component.literal(
                "§aEarly CD withdrawal completed.\n"
                        + "§7Payout: §a$" + payout.toPlainString()
                        + " §7Penalty retained by bank: §c$" + penalty.toPlainString()
        ));
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

    private static int handleWithdrawNote(CommandSourceStack source, String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cNo account available."));
            return 1;
        }

        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cWithdraw note failed: insufficient funds."));
            return 1;
        }

        String serial = UUID.randomUUID().toString();
        ItemStack note = new ItemStack(ModItems.BANK_NOTE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("ubs_note_serial", serial);
        tag.putString("ubs_note_amount", amount.toPlainString());
        tag.putUUID("ubs_note_account", account.getAccountUUID());
        tag.putUUID("ubs_note_issuer_uuid", player.getUUID());
        tag.putString("ubs_note_issuer_name", player.getName().getString());
        tag.putString("ubs_note_source_account", account.getAccountUUID().toString());
        Bank sourceBank = centralBank.getBank(account.getBankId());
        if (sourceBank != null && sourceBank.getBankName() != null && !sourceBank.getBankName().isBlank()) {
            tag.putString("ubs_note_source_bank", sourceBank.getBankName());
        }
        applyCustomTag(note, tag);
        note.set(DataComponents.CUSTOM_NAME, Component.literal("Bank Note - $" + amount.toPlainString()).withStyle(ChatFormatting.GOLD));

        if (!player.getInventory().add(note)) {
            player.drop(note, false);
        }
        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                UUID.nameUUIDFromBytes("ultimatebankingsystem:note-issuer".getBytes()),
                amount,
                LocalDateTime.now(),
                "BANK_NOTE_ISSUED"
        ));

        source.sendSystemMessage(Component.literal(
                "§aIssued bank note for §6$" + amount.toPlainString() + "§a. Serial: §f" + serial
        ));
        return 1;
    }

    private static int handleDepositNote(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() != ModItems.BANK_NOTE.get()) {
            source.sendSystemMessage(Component.literal("§cHold a bank note in your main hand."));
            return 1;
        }

        CompoundTag tag = readCustomTag(held);
        if (tag == null || !tag.contains("ubs_note_serial") || !tag.contains("ubs_note_amount")) {
            source.sendSystemMessage(Component.literal("§cInvalid bank note data."));
            return 1;
        }

        String serial = tag.getString("ubs_note_serial");
        BigDecimal amount;
        try {
            amount = new BigDecimal(tag.getString("ubs_note_amount"));
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid bank note amount."));
            return 1;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(Component.literal("§cInvalid bank note value."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        if (centralBank.isNoteSerialRedeemed(serial)) {
            source.sendSystemMessage(Component.literal("§cThis bank note serial has already been redeemed."));
            return 1;
        }

        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cNo account available."));
            return 1;
        }

        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cDeposit failed."));
            return 1;
        }
        centralBank.markNoteSerialRedeemed(serial);
        held.shrink(1);
        account.addTransaction(new UserTransaction(
                UUID.nameUUIDFromBytes("ultimatebankingsystem:note-redeem".getBytes()),
                account.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "BANK_NOTE_REDEEMED"
        ));
        source.sendSystemMessage(Component.literal(
                "§aDeposited bank note: §6$" + amount.toPlainString() + "§a."
        ));
        return 1;
    }

    private static int handleWriteCheque(CommandSourceStack source, String recipientNameRaw, String amountRaw) {
        ServerPlayer writer = source.getPlayer();
        if (writer == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        String recipientName = recipientNameRaw == null ? "" : recipientNameRaw.trim();
        if (recipientName.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cInvalid recipient."));
            return 1;
        }

        UUID recipientUuid = null;
        ServerPlayer onlineRecipient = source.getServer().getPlayerList().getPlayerByName(recipientName);
        if (onlineRecipient != null) {
            recipientUuid = onlineRecipient.getUUID();
            recipientName = onlineRecipient.getName().getString();
        } else {
            var cache = source.getServer().getProfileCache();
            if (cache != null) {
                java.util.Optional<GameProfile> profile = cache.get(recipientName);
                if (profile.isPresent()) {
                    recipientUuid = profile.get().getId();
                    if (profile.get().getName() != null && !profile.get().getName().isBlank()) {
                        recipientName = profile.get().getName();
                    }
                }
            }
        }

        if (recipientUuid == null) {
            source.sendSystemMessage(Component.literal("§cUnknown player profile: " + recipientNameRaw));
            return 1;
        }

        if (writer.getUUID().equals(recipientUuid)) {
            source.sendSystemMessage(Component.literal("§cYou cannot write a cheque to yourself."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder account = resolveDefaultLoanAccount(centralBank, writer.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cNo source account available."));
            return 1;
        }
        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cInsufficient funds for cheque."));
            return 1;
        }

        String chequeId = UUID.randomUUID().toString();
        ItemStack cheque = new ItemStack(ModItems.CHEQUE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("ubs_cheque_id", chequeId);
        tag.putString("ubs_cheque_amount", amount.toPlainString());
        tag.putUUID("ubs_cheque_recipient", recipientUuid);
        tag.putUUID("ubs_cheque_writer", writer.getUUID());
        tag.putString("ubs_cheque_recipient_name", recipientName);
        tag.putString("ubs_cheque_writer_name", writer.getName().getString());
        tag.putString("ubs_cheque_source_account", account.getAccountUUID().toString());
        Bank chequeSourceBank = centralBank.getBank(account.getBankId());
        if (chequeSourceBank != null && chequeSourceBank.getBankName() != null && !chequeSourceBank.getBankName().isBlank()) {
            tag.putString("ubs_cheque_source_bank", chequeSourceBank.getBankName());
        }
        applyCustomTag(cheque, tag);
        cheque.set(DataComponents.CUSTOM_NAME, Component.literal("Cheque - $" + amount.toPlainString()).withStyle(ChatFormatting.GREEN));

        if (!writer.getInventory().add(cheque)) {
            writer.drop(cheque, false);
        }

        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                UUID.nameUUIDFromBytes("ultimatebankingsystem:cheque-write".getBytes()),
                amount,
                LocalDateTime.now(),
                "CHEQUE_WRITE:" + recipientUuid
        ));
        source.sendSystemMessage(Component.literal(
                "§aCheque written for §6$" + amount.toPlainString() + "§a to §e" + recipientName
                        + "§a. ID: §f" + chequeId
        ));
        return 1;
    }

    private static int handleDepositCheque(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() != ModItems.CHEQUE.get()) {
            source.sendSystemMessage(Component.literal("§cHold a cheque in your main hand."));
            return 1;
        }
        CompoundTag tag = readCustomTag(held);
        if (tag == null || !tag.contains("ubs_cheque_id") || !tag.contains("ubs_cheque_amount") || !tag.contains("ubs_cheque_recipient")) {
            source.sendSystemMessage(Component.literal("§cInvalid cheque data."));
            return 1;
        }

        String chequeId = tag.getString("ubs_cheque_id");
        UUID recipientId = tag.getUUID("ubs_cheque_recipient");
        if (!player.getUUID().equals(recipientId)) {
            source.sendSystemMessage(Component.literal("§cThis cheque is not payable to you."));
            return 1;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(tag.getString("ubs_cheque_amount"));
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid cheque amount."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        if (centralBank.isChequeRedeemed(chequeId)) {
            source.sendSystemMessage(Component.literal("§cThis cheque has already been redeemed."));
            return 1;
        }

        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cNo destination account available."));
            return 1;
        }
        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cFailed to redeem cheque."));
            return 1;
        }

        centralBank.markChequeRedeemed(chequeId);
        held.shrink(1);
        account.addTransaction(new UserTransaction(
                UUID.nameUUIDFromBytes("ultimatebankingsystem:cheque-redeem".getBytes()),
                account.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "CHEQUE_REDEEMED:" + chequeId
        ));
        source.sendSystemMessage(Component.literal(
                "§aCheque deposited: §6$" + amount.toPlainString() + "§a."
        ));
        return 1;
    }

    private static int handleHudToggle(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use this command."));
            return 1;
        }

        boolean current = HUD_ENABLED_OVERRIDES.getOrDefault(player.getUUID(), Config.HUD_ENABLED_BY_DEFAULT.get());
        boolean next = !current;
        HUD_ENABLED_OVERRIDES.put(player.getUUID(), next);

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        HudStatePayload hudState = buildHudStatePayload(centralBank, player.getUUID());
        PacketDistributor.sendToPlayer(player, hudState);
        if (!next) {
            source.sendSystemMessage(Component.literal("§aBalance HUD is now §4disabled§a."));
            return 1;
        }

        if (hudState.enabled()) {
            source.sendSystemMessage(Component.literal("§aBalance HUD is now §2enabled§a."));
        } else {
            source.sendSystemMessage(Component.literal(
                    "§eBalance HUD is toggled on, but hidden because no primary account is set."
            ));
        }
        return 1;
    }

    private static int handleSafeBoxList(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use safe deposit boxes."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cNo account available."));
            return 1;
        }

        int maxSlots = Math.max(1, account.getSafeBoxSlotCount());
        int usedSlots = account.getSafeBoxSlots().size();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Account: §f" + accountLabel(account) + "\n"));
        body.append(Component.literal("§7Slots Used: §b" + usedSlots + "§7/§b" + maxSlots + "\n\n"));
        for (int slot = 0; slot < maxSlots; slot++) {
            CompoundTag stackTag = account.getSafeBoxSlots().get(slot);
            if (stackTag == null) {
                body.append(Component.literal("§8[" + slot + "] §7(empty)\n"));
                continue;
            }
            var parsed = ItemStack.parse(source.getServer().registryAccess(), stackTag);
            if (parsed.isEmpty() || parsed.get().isEmpty()) {
                body.append(Component.literal("§8[" + slot + "] §7(invalid item)\n"));
                continue;
            }
            ItemStack stack = parsed.get();
            body.append(Component.literal(
                    "§8[" + slot + "] §f" + stack.getHoverName().getString()
                            + " §7x" + stack.getCount() + "\n"
            ));
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bSafe Deposit Box", body));
        return 1;
    }

    private static int handleSafeBoxDeposit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use safe deposit boxes."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cNo account available."));
            return 1;
        }

        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cHold an item in your main hand to deposit."));
            return 1;
        }
        ItemStack copy = held.copy();
        if (!account.depositToSafeBox(copy, source.getServer().registryAccess())) {
            source.sendSystemMessage(Component.literal("§cSafe box is full for this account type."));
            return 1;
        }
        held.shrink(held.getCount());
        source.sendSystemMessage(Component.literal(
                "§aDeposited §f" + copy.getHoverName().getString()
                        + " §ax" + copy.getCount()
                        + " §ainto safe deposit box."
        ));
        return 1;
    }

    private static int handleSafeBoxWithdraw(CommandSourceStack source, String slotRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can use safe deposit boxes."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cNo account available."));
            return 1;
        }

        int slot;
        try {
            slot = Integer.parseInt(slotRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cSlot must be a number."));
            return 1;
        }
        if (slot < 0 || slot >= account.getSafeBoxSlotCount()) {
            source.sendSystemMessage(Component.literal("§cSlot out of range."));
            return 1;
        }

        ItemStack withdrawn = account.withdrawFromSafeBox(slot, source.getServer().registryAccess());
        if (withdrawn.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cNo item in that slot."));
            return 1;
        }
        if (!player.getInventory().add(withdrawn)) {
            player.drop(withdrawn, false);
        }
        source.sendSystemMessage(Component.literal(
                "§aWithdrew §f" + withdrawn.getHoverName().getString()
                        + " §ax" + withdrawn.getCount() + " §afrom slot " + slot + "."
        ));
        return 1;
    }

    private static int handleBankHeistStart(CommandSourceStack source, String bankNameRaw) {
        ServerPlayer initiator = source.getPlayer();
        if (initiator == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can start a heist."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cBank data is unavailable."));
            return 1;
        }
        Bank targetBank = resolveBankByName(centralBank, bankNameRaw);
        if (targetBank == null || targetBank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cInvalid heist target bank."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        long wantedUntil = HEIST_WANTED_UNTIL.getOrDefault(initiator.getUUID(), 0L);
        if (gameTime < wantedUntil) {
            source.sendSystemMessage(Component.literal(
                    "§cYou are still on cooldown from a failed heist until tick " + wantedUntil + "."
            ));
            return 1;
        }

        List<ServerPlayer> nearby = initiator.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                initiator.getBoundingBox().inflate(12.0D),
                candidate -> candidate != null && candidate.isAlive()
        );
        int minimum = Math.max(1, Config.HEIST_MIN_PLAYERS.get());
        if (nearby.size() < minimum) {
            source.sendSystemMessage(Component.literal(
                    "§cHeist requires at least " + minimum + " nearby participants."
            ));
            return 1;
        }

        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§4[UBS ALERT] §cA bank heist has started at §e" + targetBank.getBankName()
                        + "§c by §f" + initiator.getName().getString() + "§c!"),
                false
        );

        double successChance = Math.max(0.0D, Math.min(1.0D, Config.HEIST_SUCCESS_CHANCE.get()));
        boolean success = Math.random() <= successChance;
        if (!success) {
            long cooldownUntil = gameTime + Math.max(20L, Config.HEIST_COOLDOWN_TICKS.get());
            for (ServerPlayer participant : nearby) {
                HEIST_WANTED_UNTIL.put(participant.getUUID(), cooldownUntil);
                participant.sendSystemMessage(Component.literal(
                        "§cHeist failed. You are flagged until tick " + cooldownUntil + "."
                ));
            }
            return 1;
        }

        BigDecimal payoutTotal = targetBank.getDeclaredReserve()
                .multiply(BigDecimal.valueOf(Math.max(0.0D, Math.min(1.0D, Config.HEIST_PAYOUT_RATIO.get()))))
                .setScale(2, RoundingMode.HALF_EVEN);
        if (payoutTotal.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(Component.literal("§eHeist succeeded, but target reserve had no payoutable amount."));
            return 1;
        }

        BigDecimal perPlayer = payoutTotal
                .divide(BigDecimal.valueOf(nearby.size()), 2, RoundingMode.HALF_EVEN);
        BigDecimal distributed = perPlayer.multiply(BigDecimal.valueOf(nearby.size())).setScale(2, RoundingMode.HALF_EVEN);
        if (targetBank.getDeclaredReserve().compareTo(distributed) < 0) {
            distributed = targetBank.getDeclaredReserve();
            perPlayer = distributed.divide(BigDecimal.valueOf(nearby.size()), 2, RoundingMode.HALF_EVEN);
            distributed = perPlayer.multiply(BigDecimal.valueOf(nearby.size())).setScale(2, RoundingMode.HALF_EVEN);
        }

        targetBank.setReserve(targetBank.getDeclaredReserve().subtract(distributed));
        for (ServerPlayer participant : nearby) {
            AccountHolder participantAccount = resolveDefaultLoanAccount(centralBank, participant.getUUID());
            if (participantAccount == null || perPlayer.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (!participantAccount.AddBalance(perPlayer)) {
                continue;
            }
            participantAccount.addTransaction(new UserTransaction(
                    targetBank.getBankId(),
                    participantAccount.getAccountUUID(),
                    perPlayer,
                    LocalDateTime.now(),
                    "BANK_HEIST_PAYOUT:" + targetBank.getBankName()
            ));
            participant.sendSystemMessage(Component.literal(
                    "§aHeist success! You received §6$" + perPlayer.toPlainString()
                            + " §afrom " + targetBank.getBankName() + "."
            ));
        }

        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6[UBS ALERT] Heist on " + targetBank.getBankName()
                        + " succeeded. Total payout: $" + distributed.toPlainString()),
                false
        );
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

    private static CompoundTag readCustomTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return data.copyTag();
    }

    private static void applyCustomTag(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (tag == null) {
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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

    private static Map<UUID, String> decodeUuidStringMap(String encoded) {
        Map<UUID, String> result = new java.util.HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=")) {
                continue;
            }
            String[] parts = raw.split("=", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                String value = parts[1].trim();
                if (!value.isBlank()) {
                    result.put(id, value);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static String encodeUuidStringMap(Map<UUID, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static Map<UUID, BigDecimal> decodeShareMap(String encoded) {
        Map<UUID, BigDecimal> result = new java.util.HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=")) {
                continue;
            }
            String[] parts = raw.split("=", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                BigDecimal percent = new BigDecimal(parts[1].trim()).setScale(2, RoundingMode.HALF_EVEN);
                if (percent.compareTo(BigDecimal.ZERO) > 0) {
                    result.put(id, percent);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static String encodeShareMap(Map<UUID, BigDecimal> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue().setScale(2, RoundingMode.HALF_EVEN).toPlainString())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static List<UUID> decodeUuidList(String encoded) {
        List<UUID> result = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(",");
        for (String entry : entries) {
            try {
                result.add(UUID.fromString(entry.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static String encodeUuidList(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "";
        }
        return uuids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .map(UUID::toString)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private static Map<UUID, EmployeeSpec> decodeEmployeeMap(String encoded) {
        Map<UUID, EmployeeSpec> result = new java.util.HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=") || !raw.contains(":")) {
                continue;
            }
            String[] uuidAndRest = raw.split("=", 2);
            String[] roleAndSalary = uuidAndRest[1].split(":", 2);
            if (roleAndSalary.length < 2) {
                continue;
            }
            try {
                UUID id = UUID.fromString(uuidAndRest[0].trim());
                String role = roleAndSalary[0].trim().toUpperCase(Locale.ROOT);
                BigDecimal salary = new BigDecimal(roleAndSalary[1].trim());
                if (salary.compareTo(BigDecimal.ZERO) < 0) {
                    continue;
                }
                result.put(id, new EmployeeSpec(role, salary));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static String encodeEmployeeMap(Map<UUID, EmployeeSpec> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> entry.getKey() + "=" + entry.getValue().role() + ":" + entry.getValue().salary().toPlainString())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static List<LoanProductSpec> decodeLoanProducts(String encoded) {
        List<LoanProductSpec> products = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return products;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split("\\|");
            if (parts.length < 4) {
                continue;
            }
            try {
                String name = parts[0].trim();
                BigDecimal max = new BigDecimal(parts[1].trim());
                double rate = Double.parseDouble(parts[2].trim());
                long duration = Long.parseLong(parts[3].trim());
                if (!name.isBlank() && max.compareTo(BigDecimal.ZERO) > 0 && rate > 0.0 && duration >= 20L) {
                    products.add(new LoanProductSpec(name, max, rate, duration));
                }
            } catch (Exception ignored) {
            }
        }
        return products;
    }

    private static String encodeLoanProducts(List<LoanProductSpec> products) {
        if (products == null || products.isEmpty()) {
            return "";
        }
        return products.stream()
                .filter(product -> product != null && product.name() != null && !product.name().isBlank())
                .sorted(Comparator.comparing(LoanProductSpec::name, String.CASE_INSENSITIVE_ORDER))
                .map(product -> product.name() + "|"
                        + product.maxAmount().toPlainString() + "|"
                        + product.interestRate() + "|"
                        + product.durationTicks())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    public static boolean isHudEnabled(UUID playerId) {
        if (playerId == null) {
            return Config.HUD_ENABLED_BY_DEFAULT.get();
        }
        return HUD_ENABLED_OVERRIDES.getOrDefault(playerId, Config.HUD_ENABLED_BY_DEFAULT.get());
    }

    public static HudStatePayload buildHudStatePayload(CentralBank centralBank, UUID playerId) {
        if (playerId == null) {
            return new HudStatePayload("", false);
        }
        boolean toggled = isHudEnabled(playerId);
        AccountHolder primary = centralBank == null ? null : findPrimaryAccount(centralBank, playerId);
        boolean visible = toggled && primary != null;
        String balance = primary == null ? "" : primary.getBalance().toPlainString();
        return new HudStatePayload(balance, visible);
    }
}
