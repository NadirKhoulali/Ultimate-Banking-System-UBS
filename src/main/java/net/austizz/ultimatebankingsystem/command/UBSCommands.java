package net.austizz.ultimatebankingsystem.command;


import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.npc.BankTellerInteractionManager;
import net.austizz.ultimatebankingsystem.npc.BankTellerPaymentInteractionManager;
import net.austizz.ultimatebankingsystem.network.HudStatePayload;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSCommands {

    private static final int HELP_ENTRIES_PER_PAGE = 14;
    private static final List<String> ACCOUNT_HELP_ENTRIES = List.of(
            "§8/§faccount §7help [page] §8- §7Show account help pages",
            "§8/§faccount §7open §8<§faccountType§8> [§fcertificateTier§8] <§fbankName§8> §8- §7Open account",
            "§8/§faccount §7close §8<§fbankName§8> §8- §7Close your account at a bank",
            "§8/§faccount §7balance §8- §7Show your primary account balance",
            "§8/§faccount §7info §8- §7View your primary account info",
            "§8/§faccount §7info list §8- §7List all your accounts",
            "§8/§faccount §7info bank §8<§fbankName§8> §8- §7View your account at a bank",
            "§8/§faccount §7info §8<§faccountId§8> §8- §7View specific account info",
            "§8/§faccount §7primary set §8<§faccountId§8> §8- §7Set primary account",
            "§8/§faccount §7primary bank §8<§fbankName§8> §8- §7Set primary by bank",
            "§8/§faccount §7delete §8<§faccountId§8> §8- §7Delete your account",
            "§8/§faccount §7transfer §8<§ffromAccountId§8> <§ftoAccountId§8> <§famount§8> §8- §7Transfer funds",
            "§8/§faccount §7transfer bank §8<§ffromBank§8> <§ftoBank§8> <§famount§8> §8- §7Move funds between your banks",
            "§8/§faccount §7send §8<§fplayer§8> <§famount§8> [§fbankName§8] §8- §7Send money to a player",
            "§8/§faccount §7transaction §8<§ftransactionId§8> §8- §7Show transaction details",
            "§8/§faccount §7transaction list §8<§faccountId§8> §8- §7List account transactions",
            "§8/§faccount §7payrequest §8<§fplayer§8> <§famount§8> [§fdestinationAccountId§8] §8- §7Create pay request",
            "§8/§faccount §7credit §8- §7Show your credit score",
            "§8/§faccount §7loan request §8<§famount§8> §8- §7Preview a personal loan",
            "§8/§faccount §7loan confirm §8- §7Confirm your pending personal loan",
            "§8/§faccount §7loan status §8- §7Show your active personal loans",
            "§8/§faccount §7shop pay §8<§famount§8> [§fshop§8] §8- §7Pay from your account",
            "§8/§faccount §7note write §8<§famount§8> §8- §7Create a bank note item",
            "§8/§faccount §7cheque write §8<§fplayer§8> <§famount§8> §8- §7Write cheque",
            "§8/§faccount §7hud toggle §8- §7Toggle account HUD",
            "§8/§faccount §7hud primary §8- §7Monitor primary account on HUD",
            "§8/§faccount §7hud account §8<§faccountId§8> §8- §7Monitor a specific account on HUD",
            "§8/§faccount §7safebox list §8- §7List your safe box slots",
            "§8/§faccount §7safebox deposit §8- §7Store held item in safe box",
            "§8/§faccount §7safebox withdraw §8<§fslot§8> §8- §7Withdraw safe box slot",
            "§8/§faccount §7cd break §8<§faccountId§8> §8- §7Request early CD break",
            "§8/§faccount §7cd confirm §8- §7Confirm pending CD break",
            "§8/§faccount §7joint create §8<§fplayer§8> <§fbankName§8> §8- §7Create joint account",
            "§8/§faccount §7joint info §8<§faccountId§8> §8- §7Show shared account details",
            "§8/§faccount §7joint deposit §8<§faccountId§8> <§famount§8> §8- §7Deposit to shared account",
            "§8/§faccount §7joint withdraw §8<§faccountId§8> <§famount§8> §8- §7Withdraw from shared account",
            "§8/§faccount §7joint transfer §8<§ffrom§8> <§fto§8> <§famount§8> §8- §7Transfer between shared accounts",
            "§8/§faccount §7joint close §8<§faccountId§8> §8- §7Close shared account",
            "§8/§faccount §7business create §8<§flabel§8> <§fbankName§8> §8- §7Create business account",
            "§8/§faccount §7business grant §8<§faccountId§8> <§fplayer§8> <§frole§8> §8- §7Grant business role",
            "§8/§faccount §7business revoke §8<§faccountId§8> <§fplayer§8> §8- §7Revoke business role",
            "§8/§faccount §7business transferowner §8<§faccountId§8> <§fplayer§8> §8- §7Transfer business ownership"
    );
    private static final List<String> BANK_HELP_ENTRIES = List.of(
            "§8/§fbank §7help [page] §8- §7Show bank help pages",
            "§8/§fbank §7list §8- §7List available banks",
            "§8/§fbank §7create §8<§fname§8> [§fownershipModel§8] §8- §7Create a player bank",
            "§8/§fbank §7motto §8<§ftext§8> §8- §7Set your bank motto",
            "§8/§fbank §7color §8<§fvalue§8> §8- §7Set your bank color",
            "§8/§fbank §7teller get §8- §7Issue a bank teller spawn egg",
            "§8/§fbank §7teller count §8- §7Show teller count for your bank",
            "§8/§fbank §7info §8<§fbankName§8> §8- §7Show bank profile",
            "§8/§fbank §7reserve §8- §7Show your bank reserve state",
            "§8/§fbank §7dashboard §8- §7Show compact bank dashboard",
            "§8/§fbank §7accounts §8- §7List account holders in your bank",
            "§8/§fbank §7cds §8- §7Show certificate schedule",
            "§8/§fbank §7limit set §8<§ftype§8> <§famount§8> §8- §7Set bank limit",
            "§8/§fbank §7limit view §8- §7View active custom limits",
            "§8/§fbank §7role assign §8<§fplayer§8> <§frole§8> §8- §7Assign role",
            "§8/§fbank §7role revoke §8<§fplayer§8> §8- §7Revoke role",
            "§8/§fbank §7role list §8- §7List assigned roles",
            "§8/§fbank §7shares §8- §7List ownership shares",
            "§8/§fbank §7shares set §8<§fplayer§8> <§fpercent§8> §8- §7Set share %",
            "§8/§fbank §7cofounder add §8<§fplayer§8> §8- §7Add cofounder",
            "§8/§fbank §7cofounder list §8- §7List cofounders",
            "§8/§fbank §7hire §8<§fplayer§8> <§frole§8> <§fsalary§8> §8- §7Hire employee",
            "§8/§fbank §7fire §8<§fplayer§8> §8- §7Fire employee",
            "§8/§fbank §7employees §8- §7List employees",
            "§8/§fbank §7quit §8<§fbankName§8> §8- §7Quit your employee role",
            "§8/§fbank §7borrow §8<§famount§8> §8- §7Borrow from Central Bank",
            "§8/§fbank §7lend offer §8<§famount§8> <§fannualRate§8> <§ftermTicks§8> §8- §7Post inter-bank offer",
            "§8/§fbank §7lend market §8- §7View inter-bank offer market",
            "§8/§fbank §7lend accept §8<§fofferId§8> §8- §7Accept inter-bank offer",
            "§8/§fbank §7loans §8- §7Show your bank loan summary",
            "§8/§fbank §7loan create §8<§fname§8> <§fmaxAmount§8> <§finterestRate§8> <§fdurationTicks§8> §8- §7Create consumer loan product",
            "§8/§fbank §7loan list §8<§fbankName§8> §8- §7List bank loan products",
            "§8/§fbank §7loan apply §8<§fbankName§8> <§fproduct§8> <§famount§8> §8- §7Apply for loan product",
            "§8/§fbank §7loan active §8- §7Show active loan obligations",
            "§8/§fbank §7appeal §8<§fmessage§8> §8- §7Submit compliance appeal",
            "§8/§fbank §7heist start §8<§fbankName§8> §8- §eComing Soon"
    );
    private static final ConcurrentHashMap<UUID, LoanService.LoanQuote> PENDING_LOAN_CONFIRMATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> HUD_ENABLED_OVERRIDES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> HUD_ACCOUNT_MONITOR_OVERRIDES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_BANK_CREATE_ATTEMPT_MILLIS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CompoundTag> PENDING_CD_BREAK_CONFIRMATIONS = new ConcurrentHashMap<>();
    private static final List<String> CERTIFICATE_TIER_SUGGESTIONS = List.of(
            "short",
            "medium",
            "long"
    );
    private static final List<String> BANK_LIMIT_TYPE_SUGGESTIONS = List.of(
            "single",
            "dailyplayer",
            "dailybank",
            "teller"
    );

    private record EmployeeSpec(String role, BigDecimal salary) {}
    private record LoanProductSpec(String name, BigDecimal maxAmount, double interestRate, long durationTicks) {}

    private static MutableComponent moneyLiteral(String text) {
        return Component.literal(MoneyText.abbreviateCurrencyTokens(text == null ? "" : text));
    }

    private static Component ubsMessage(ChatFormatting accentColor, String title, Component body) {
        return moneyLiteral("§6§lUltimate Banking System §7- ")
                .append(moneyLiteral(title).withStyle(accentColor))
                .append(moneyLiteral("\n§8────────────────────────\n"))
                .append(body);
    }

    private static Component ubsError(String title, String message) {
        return ubsMessage(ChatFormatting.RED, "§c" + title, moneyLiteral("§c" + message));
    }

    private static Component ubsSuccess(String title, String message) {
        return ubsMessage(ChatFormatting.GREEN, "§a" + title, moneyLiteral("§a" + message));
    }

    private static int sendAccountHelp(CommandSourceStack source, int requestedPage) {
        return sendPagedHelp(source, "§eAccount Commands", "account", ACCOUNT_HELP_ENTRIES, requestedPage);
    }

    private static int sendBankHelp(CommandSourceStack source, int requestedPage) {
        return sendPagedHelp(source, "§eBank Commands", "bank", BANK_HELP_ENTRIES, requestedPage);
    }

    private static int sendPagedHelp(CommandSourceStack source,
                                     String title,
                                     String rootLiteral,
                                     List<String> entries,
                                     int requestedPage) {
        int totalEntries = entries == null ? 0 : entries.size();
        int totalPages = Math.max(1, (totalEntries + HELP_ENTRIES_PER_PAGE - 1) / HELP_ENTRIES_PER_PAGE);
        int page = Math.max(1, Math.min(totalPages, requestedPage));

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Page §f" + page + "§7/§f" + totalPages + "\n"));

        if (totalEntries == 0) {
            body.append(moneyLiteral("§8No commands available."));
            source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, title, body));
            return 1;
        }

        int start = (page - 1) * HELP_ENTRIES_PER_PAGE;
        int end = Math.min(totalEntries, start + HELP_ENTRIES_PER_PAGE);
        for (int i = start; i < end; i++) {
            body.append(moneyLiteral(entries.get(i))).append(moneyLiteral("\n"));
        }

        if (totalPages > 1) {
            body.append(moneyLiteral("§8────────────────────────\n"));
            body.append(moneyLiteral("§7Use §f/" + rootLiteral + " help <page> §7or click: "));

            if (page > 1) {
                body.append(
                        moneyLiteral("§f§l[§bPrevious§f§l]")
                                .setStyle(Style.EMPTY.withClickEvent(
                                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + rootLiteral + " help " + (page - 1))
                                ))
                );
            } else {
                body.append(moneyLiteral("§8[§7Previous§8]"));
            }

            body.append(moneyLiteral(" "));

            if (page < totalPages) {
                body.append(
                        moneyLiteral("§f§l[§bNext§f§l]")
                                .setStyle(Style.EMPTY.withClickEvent(
                                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + rootLiteral + " help " + (page + 1))
                                ))
                );
            } else {
                body.append(moneyLiteral("§8[§7Next§8]"));
            }
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, title, body));
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("account")
                        .executes(context -> sendAccountHelp(context.getSource(), 1))
                        .then(Commands.literal("help")
                                .executes(context -> sendAccountHelp(context.getSource(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> sendAccountHelp(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "page")
                                        ))
                                )
                        )
                        .then(Commands.literal("info")
                                .executes(context -> {
                                    MinecraftServer server = context.getSource().getServer();
                                    CentralBank centralBank = BankManager.getCentralBank(server);
                                    ConcurrentHashMap<UUID, AccountHolder> account = centralBank.SearchForAccount(context.getSource().getPlayer().getUUID());
                                    if (account.isEmpty()) {
                                        context.getSource().sendSystemMessage(moneyLiteral("§cYou currently do not have any accounts."));
                                        return 1;
                                    }
                                    for(AccountHolder a :  account.values()){
                                        if (a.isPrimaryAccount()){
                                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                            Component info = moneyLiteral("§6§lUltimate Banking System §7- §eAccount Info\n"
                                                            + "§7Bank: §e" + centralBank.getBank(a.getBankId()).getBankName() + "\n"
                                                            + "§8(§7ID: §f" + a.getBankId() + "§8)\n")
                                                    .append(moneyLiteral("§7Account ID: §f" + a.getAccountUUID() + "\n").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to copy"))).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, a.getAccountUUID().toString()))))
                                                    .append(moneyLiteral("§7Primary Account: §f" + a.isPrimaryAccount() + "\n"
                                                                    + "§7Type: §f" + (a.getAccountType() != null ? a.getAccountType().label : "Unknown") + "\n"
                                                                    + "§7Balance: §a$" + a.getBalance().toPlainString() + "\n"
                                                                    + "§7Created: §f" + a.getDateOfCreation().format(fmt) + "\n"
                                                                    + "Actions: ")
                                                            .append((Component) moneyLiteral("§f§l[§4Delete Account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account delete " + a.getAccountUUID()))))
                                                            .append(moneyLiteral(" "))
                                                            .append((Component) moneyLiteral("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + a.getAccountUUID())))))
                                                    .append(moneyLiteral(" \n"))
                                                    .append((Component) moneyLiteral("§f§l[§3Transfer Money§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/account transfer " + a.getAccountUUID()))))
                                                    .append((Component) moneyLiteral("§f§l[§6See Transactions§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account transaction list " + a.getAccountUUID()))));
                                            context.getSource().sendSystemMessage(info);
                                            return 1;
                                        }
                                    }
                                    context.getSource().sendSystemMessage(moneyLiteral("§cNo primary account could be determined. Please check your accounts and set a primary account."));
                                    return 1;
                                })
                                .then(Commands.literal("list")
                                        .executes(context -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            CentralBank centralBank = BankManager.getCentralBank(server);

                                            ConcurrentHashMap<UUID, AccountHolder> account =  centralBank.SearchForAccount(context.getSource().getPlayer().getUUID());


                                            // Header
                                            context.getSource().sendSystemMessage(
                                                    moneyLiteral("§6§lUltimate Banking System §7- §eYour Accounts (§f" + account.size() + "§e)\n§8────────────────────────")
                                            );
                                            if (account.isEmpty()) {
                                                context.getSource().sendSystemMessage(moneyLiteral("§cNo account found."));
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
                                                        ? moneyLiteral(" §8[§2PRIMARY§8]")
                                                        : Component.empty();

                                                // Build a compact multi-line card per account
                                                MutableComponent entry = moneyLiteral("§7" + index + ". §e" + (a.getAccountType() != null ? a.getAccountType().label : "Account"));
                                                entry.append(primaryBadge);
                                                entry.append(moneyLiteral("\n§7Bank: §f" + bankName + " §8(§7ID: §f" + a.getBankId() + "§8)"));
                                                entry.append(moneyLiteral("\n§7Balance: §a$" + a.getBalance().toPlainString() + " §7Created: §f" + a.getDateOfCreation().format(fmt)));

                                                // Actions row
                                                entry.append(moneyLiteral("\n§7Actions: "));
                                                entry.append(
                                                        moneyLiteral("§f§l[§9Open§f§l]")
                                                                .setStyle(
                                                                        Style.EMPTY
                                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account info " + a.getAccountUUID()))
                                                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to open account details")))
                                                                )
                                                );
                                                entry.append(moneyLiteral(" "));
                                                entry.append(
                                                        moneyLiteral("§f§l[§3Copy ID§f§l]")
                                                                .setStyle(
                                                                        Style.EMPTY
                                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, a.getAccountUUID().toString()))
                                                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to copy Account ID (" + a.getAccountUUID() + ")")))
                                                                )
                                                );

                                                context.getSource().sendSystemMessage(entry);
                                                // Separator between accounts
                                                context.getSource().sendSystemMessage(moneyLiteral("§8────────────────────────"));
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
                                                        context.getSource().sendSystemMessage(moneyLiteral("§cThe bank '§e" + BankName + "§c' could not be found."));
                                                        return 1;
                                                    }
                                                    AccountHolder account = Choise.getPlayerAccount(context.getSource().getPlayer());
                                                    if (account == null) {
                                                        context.getSource().sendSystemMessage(moneyLiteral("§cYou do not have an account at §e" + Choise.getBankName() + "§c."));
                                                        return 1;
                                                    }
                                                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                                    Component info = moneyLiteral("§6§lUltimate Banking System §7- §eAccount Info\n"
                                                                    + "§7Bank: §e" + Choise.getBankName() + "\n"
                                                                    + "§8(§7ID: §f" + Choise.getBankId() + "§8)\n")
                                                            .append(moneyLiteral("§7Account ID: §f" + account.getAccountUUID() + "\n").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to copy"))).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, account.getAccountUUID().toString()))))
                                                            .append(moneyLiteral("§7Primary Account: §f" + account.isPrimaryAccount() + "\n"
                                                                            + "§7Type: §f" + (account.getAccountType() != null ? account.getAccountType().label : "Unknown") + "\n"
                                                                            + "§7Balance: §a$" + account.getBalance().toPlainString() + "\n"
                                                                            + "§7Created: §f" + account.getDateOfCreation().format(fmt) + "\n"
                                                                            + "Actions: ")
                                                                    .append((Component) moneyLiteral("§f§l[§4Delete Account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account delete " + account.getAccountUUID()))))
                                                                    .append(moneyLiteral(" "))
                                                                    .append((Component) moneyLiteral("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + account.getAccountUUID())))))
                                                            .append(moneyLiteral(" \n"))
                                                            .append((Component) moneyLiteral("§f§l[§3Transfer Money§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/account transfer " + account.getAccountUUID()))))
                                                            .append((Component) moneyLiteral("§f§l[§6See Transactions§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account transaction list " + account.getAccountUUID()))));
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
                                                context.getSource().sendSystemMessage(moneyLiteral("§cThe account '§e" + accountID + "§c' could not be found."));
                                                return 1;
                                            }

                                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                            Component info = moneyLiteral("§6§lUltimate Banking System §7- §eAccount Info\n"
                                                            + "§7Bank: §e" + centralBank.getBank(account.getBankId()).getBankName() + "\n"
                                                            + "§8(§7ID: §f" + account.getBankId() + "§8)\n")
                                                    .append(moneyLiteral("§7Account ID: §f" + account.getAccountUUID() + "\n").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to copy"))).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, account.getAccountUUID().toString()))))
                                                    .append(moneyLiteral("§7Primary Account: §f" + account.isPrimaryAccount() + "\n"
                                                                    + "§7Type: §f" + (account.getAccountType() != null ? account.getAccountType().label : "Unknown") + "\n"
                                                                    + "§7Balance: §a$" + account.getBalance().toPlainString() + "\n"
                                                                    + "§7Created: §f" + account.getDateOfCreation().format(fmt) + "\n"
                                                                    + "Actions: ")
                                                            .append((Component) moneyLiteral("§f§l[§4Delete Account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account delete " + account.getAccountUUID()))))
                                                            .append(moneyLiteral(" "))
                                                            .append((Component) moneyLiteral("§f§l[§2Set primary account§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account primary set " + account.getAccountUUID())))))
                                                    .append(moneyLiteral(" \n"))
                                                    .append((Component) moneyLiteral("§f§l[§3Transfer Money§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/account transfer " + account.getAccountUUID()))))
                                                    .append((Component) moneyLiteral("§f§l[§6See Transactions§f§l]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/account transaction list " + account.getAccountUUID()))));

                                            context.getSource().sendSystemMessage(info);
                                            return 1;
                                        })
                                )

                        )
                        .then(Commands.literal("open")
                                .then(Commands.argument("accountType", StringArgumentType.word())
                                        .suggests(UBSCommands::suggestAccountOpenTypes)
                                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                                .suggests(UBSCommands::suggestBankNames)
                                                .executes(context -> handleBankOpenAccount(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "bankName"),
                                                        StringArgumentType.getString(context, "accountType"),
                                                        ""
                                                ))
                                        )
                                        .then(Commands.argument("certificateTier", StringArgumentType.word())
                                                .suggests(UBSCommands::suggestCertificateTiersForAccountOpen)
                                                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                                        .suggests(UBSCommands::suggestBankNames)
                                                        .executes(context -> {
                                                            String accountType = StringArgumentType.getString(context, "accountType");
                                                            String certificateTier = StringArgumentType.getString(context, "certificateTier");
                                                            String bankTail = StringArgumentType.getString(context, "bankName");
                                                            if (parseAccountType(accountType) != AccountTypes.CertificateAccount) {
                                                                String mergedBankName = normalizeBankName(certificateTier + " " + bankTail);
                                                                return handleBankOpenAccount(
                                                                        context.getSource(),
                                                                        mergedBankName,
                                                                        accountType,
                                                                        ""
                                                                );
                                                            }
                                                            return handleBankOpenAccount(
                                                                    context.getSource(),
                                                                    bankTail,
                                                                    accountType,
                                                                    certificateTier
                                                            );
                                                        })
                                                )
                                        )
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
                        .then(Commands.literal("balance")
                                .executes(context -> handleAccountBalance(context.getSource())))
                        .then(Commands.literal("transfer")
                                .then(Commands.literal("bank")
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
                                .then(Commands.argument("Account ID (sending)", UuidArgument.uuid())
                                        .then(Commands.argument("Account ID (receiving)", UuidArgument.uuid())
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            MinecraftServer server = context.getSource().getServer();
                                                            CentralBank centralBank = BankManager.getCentralBank(server);
                                                            AccountHolder sender = centralBank.SearchForAccountByAccountId(UuidArgument.getUuid(context, "Account ID (sending)"));
                                                            AccountHolder receiver = centralBank.SearchForAccountByAccountId(UuidArgument.getUuid(context, "Account ID (receiving)"));

                                                            if (sender == null) {
                                                                context.getSource().sendSystemMessage(moneyLiteral("§cThe sender's account could not be found."));
                                                                return 1;
                                                            }
                                                            if (receiver == null) {
                                                                context.getSource().sendSystemMessage(moneyLiteral("§cThe receiver's account could not be found."));
                                                                return 1;
                                                            }

                                                            if (!sender.getPlayerUUID().equals(context.getSource().getPlayer().getUUID())) {
                                                                context.getSource().sendSystemMessage(moneyLiteral("§cYou do not own the sender account."));
                                                                return 1;
                                                            }

                                                            if (sender.getAccountUUID().equals(receiver.getAccountUUID())) {
                                                                context.getSource().sendSystemMessage(moneyLiteral("§cYou cannot transfer to the same account."));
                                                                return 1;
                                                            }

                                                            if (sender.isFrozen()) {
                                                                String reason = sender.getFrozenReason();
                                                                context.getSource().sendSystemMessage(moneyLiteral(
                                                                        "§cSender account is frozen." + (reason.isEmpty() ? "" : " Reason: " + reason)
                                                                ));
                                                                return 1;
                                                            }

                                                            if (receiver.isFrozen()) {
                                                                context.getSource().sendSystemMessage(moneyLiteral("§cReceiver account is frozen."));
                                                                return 1;
                                                            }

                                                            String amountStr = StringArgumentType.getString(context, "amount");
                                                            BigDecimal amount;
                                                            try {
                                                                amount = new BigDecimal(amountStr);
                                                            } catch (NumberFormatException e) {
                                                                context.getSource().sendSystemMessage(moneyLiteral("§cThe amount '§e" + amountStr + "§c' is not a valid number."));
                                                                return 1;
                                                            }

                                                            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                                                                context.getSource().sendSystemMessage(moneyLiteral("§cThe transfer amount must be greater than zero."));
                                                                return 1;
                                                            }
                                                            
                                                            if(!new UserTransaction(sender.getAccountUUID(),receiver.getAccountUUID(), amount, LocalDateTime.now(), "Bank to Bank UserTransaction").makeTransaction(server)) {
                                                                String receiverName = server.getPlayerList().getPlayer(receiver.getPlayerUUID()) != null
                                                                        ? server.getPlayerList().getPlayer(receiver.getPlayerUUID()).getName().getString()
                                                                        : receiver.getPlayerUUID().toString();

                                                                context.getSource().sendSystemMessage(
                                                                        ubsError(
                                                                                "Transfer Failed",
                                                                                "Could not transfer §e$" + amount.toPlainString() + "§c to §e" + receiverName + "§c. Please try again."
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
                                                                            "Transferred §e$" + amount.toPlainString() + "§a to §e" + receiverName + "§a successfully."
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

                                            MutableComponent body = (moneyLiteral("§7Transaction ID: §f" + transaction.getTransactionUUID() + "\n"))
                                                    .append(moneyLiteral("§7Amount: §a$" + transaction.getAmount().toPlainString() + "\n"))
                                                    .append(moneyLiteral("§7Time: §f" + timeStr + "\n"))
                                                    .append(moneyLiteral("§7Description: §f" + transaction.getTransactionDescription()));

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
                                                                ubsMessage(ChatFormatting.GOLD, "§eTransactions", moneyLiteral("§7No transactions on this account!"))
                                                        );
                                                        return 1;
                                                    }

                                                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                                                    MutableComponent list = moneyLiteral("§7Transactions (§f" + account.getTransactions().size() + "§7)\n");
                                                    account.getTransactions().forEach((uuid, transaction) -> {
                                                        String amountStr = "$" + transaction.getAmount().toPlainString();
                                                        String timeStr = transaction.getTimestamp().format(fmt);
                                                        String desc = transaction.getTransactionDescription();

                                                        MutableComponent entry = moneyLiteral("§8- §7ID: §f" + uuid + "\n")
                                                                .setStyle(
                                                                        Style.EMPTY
                                                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                                        moneyLiteral("§eTransaction Details\n")
                                                                                                .append(moneyLiteral("§7Amount: §a" + amountStr + "\n"))
                                                                                                .append(moneyLiteral("§7Time: §f" + timeStr + "\n"))
                                                                                                .append(moneyLiteral("§7Description: §f" + desc + "\n"))
                                                                                                .append(moneyLiteral("\n§7Click to open transaction info"))
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
                        .then(Commands.literal("payrequest")
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
                        .then(Commands.literal("delete")
                                .then(Commands.argument("Account ID", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            MinecraftServer server = context.getSource().getServer();
                                            CentralBank centralBank = BankManager.getCentralBank(server);

                                            UUID accountID = UUID.fromString(StringArgumentType.getString(context, "Account ID"));
                                            AccountHolder account = centralBank.SearchForAccountByAccountId(accountID);
                                            if (account == null) {
                                                context.getSource().sendSystemMessage(moneyLiteral("§cYour account was not found."));
                                                return 1;
                                            }
                                            account.RequestAccountTermination(context.getSource().getPlayer());
                                            BankManager.markDirty();
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("primary")
                                .then(Commands.literal("bank")
                                        .then(Commands.argument("Bank Name", StringArgumentType.greedyString())
                                                .executes(context -> handleSetPrimaryBank(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "Bank Name")
                                                ))
                                        )
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("Account ID", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();
                                                    CentralBank centralBank = BankManager.getCentralBank(server);
                                                    ConcurrentHashMap<UUID, AccountHolder> result = centralBank.SearchForAccount(context.getSource().getPlayer().getUUID());
                                                    if (result.isEmpty()) {
                                                        context.getSource().sendSystemMessage(moneyLiteral("§cYour account was not found."));
                                                        return 1;
                                                    }
                                                    UUID targetId = UUID.fromString(StringArgumentType.getString(context, "Account ID"));
                                                    AccountHolder targetAccount = result.get(targetId);
                                                    if (targetAccount == null) {
                                                        context.getSource().sendSystemMessage(moneyLiteral("§cAccount not found!"));
                                                        return 1;
                                                    }
                                                    if (targetAccount.isPrimaryAccount()) {
                                                        context.getSource().sendSystemMessage(moneyLiteral("§aAccount at §e" + centralBank.getBank(targetAccount.getBankId()).getBankName() + "§a is already the primary account!"));
                                                        return 1;
                                                    }
                                                    // First, clear primary on ALL accounts
                                                    for (AccountHolder a : result.values()) {
                                                        a.setPrimaryAccount(false);
                                                    }
                                                    // Then set the target as primary
                                                    targetAccount.setPrimaryAccount(true);
                                                    context.getSource().sendSystemMessage(moneyLiteral("§aSuccessfully made account at §e" + centralBank.getBank(targetAccount.getBankId()).getBankName() + "§a the primary account! \n §7This means that actions, invoices and or recurring payments will default to this account."));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(buildAccountCreditCommand())
                        .then(buildAccountLoanCommand())
                        .then(buildAccountShopCommand())
                        .then(buildAccountNoteCommand())
                        .then(buildAccountChequeCommand())
                        .then(buildAccountHudCommand())
                        .then(buildAccountSafeBoxCommand())
                        .then(buildAccountCdCommand())
                        .then(buildAccountJointCommand())
                        .then(buildAccountBusinessCommand())

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
                                    context.getSource().sendSystemMessage(moneyLiteral("§cThe action has been cancelled."));
                                    CallBackManager.removeCallback(id);
                                    return 1;
                                }
                        ))
                    )

                );

        event.getDispatcher().register(buildHiddenPayRequestCommand());
        event.getDispatcher().register(buildBankCommand());
        event.getDispatcher().register(buildBankTellerCommand());

    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountCreditCommand() {
        return Commands.literal("credit")
                .executes(context -> handleBankCredit(context.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountLoanCommand() {
        return Commands.literal("loan")
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
                        .executes(context -> handleLoanStatus(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountShopCommand() {
        return Commands.literal("shop")
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
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountNoteCommand() {
        return Commands.literal("note")
                .then(Commands.literal("write")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> handleWithdrawNote(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "amount")
                                ))
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountChequeCommand() {
        return Commands.literal("cheque")
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
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountHudCommand() {
        return Commands.literal("hud")
                .then(Commands.literal("toggle")
                        .executes(context -> handleHudToggle(context.getSource()))
                )
                .then(Commands.literal("primary")
                        .executes(context -> handleHudMonitorPrimary(context.getSource()))
                )
                .then(Commands.literal("account")
                        .then(Commands.argument("accountId", UuidArgument.uuid())
                                .executes(context -> handleHudMonitorAccount(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "accountId")
                                ))
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountSafeBoxCommand() {
        return Commands.literal("safebox")
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
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountCdCommand() {
        return Commands.literal("cd")
                .then(Commands.literal("break")
                        .then(Commands.argument("accountId", UuidArgument.uuid())
                                .executes(context -> handleCdBreakRequest(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "accountId")
                                ))
                        )
                )
                .then(Commands.literal("confirm")
                        .executes(context -> handleBankConfirm(context.getSource()))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountJointCommand() {
        return Commands.literal("joint")
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
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAccountBusinessCommand() {
        return Commands.literal("business")
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
                );
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
                        .executes(context -> {
                            int paymentCancelled = BankTellerPaymentInteractionManager.handleCancel(context.getSource());
                            if (paymentCancelled > 0) {
                                return paymentCancelled;
                            }
                            return BankTellerInteractionManager.handleCancel(context.getSource());
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBankCommand() {
        return Commands.literal("bank")
                .executes(context -> sendBankHelp(context.getSource(), 1))
                .then(Commands.literal("help")
                        .executes(context -> sendBankHelp(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> sendBankHelp(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")
                                ))
                        )
                )
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
                                        .suggests(UBSCommands::suggestBankLimitTypes)
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
                .then(Commands.literal("loan")
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
                .then(Commands.literal("heist")
                        .executes(context -> handleBankHeistComingSoon(context.getSource()))
                        .then(Commands.literal("start")
                                .executes(context -> handleBankHeistComingSoon(context.getSource()))
                                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                        .executes(context -> handleBankHeistComingSoon(context.getSource()))
                                )
                        )
                );
    }

    private static int handleBankCreate(CommandSourceStack source, String bankNameRaw, String ownershipModelRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can create banks."));
            return 1;
        }

        if (!Config.PLAYER_BANKS_ENABLED.get()) {
            source.sendSystemMessage(moneyLiteral("§cPlayer-created banks are disabled by server config."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        String bankName = normalizeBankName(bankNameRaw);
        if (bankName.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cBank name cannot be empty."));
            return 1;
        }
        if (bankName.length() > Config.PLAYER_BANKS_NAME_MAX_LENGTH.get()) {
            source.sendSystemMessage(moneyLiteral(
                    "§cBank name is too long. Max length: " + Config.PLAYER_BANKS_NAME_MAX_LENGTH.get()
            ));
            return 1;
        }
        if (resolveBankByName(centralBank, bankName) != null) {
            source.sendSystemMessage(moneyLiteral("§cA bank with that name already exists."));
            return 1;
        }

        int maxOwned = Math.max(1, Config.PLAYER_BANKS_MAX_BANKS_PER_PLAYER.get());
        int currentlyOwned = (int) getOwnedBanks(centralBank, player.getUUID()).size();
        if (currentlyOwned >= maxOwned) {
            source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral(
                    "§cYou must wait " + remainingMinutes + " more minute(s) before another bank creation attempt."
            ));
            return 1;
        }

        int requiredPlayHours = Math.max(0, Config.PLAYER_BANKS_MIN_PLAYTIME_HOURS.get());
        int playTimeTicks = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
        long playHours = playTimeTicks / (20L * 60L * 60L);
        if (playHours < requiredPlayHours) {
            source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cYou need a bank account before creating a player bank."));
            return 1;
        }

        BigDecimal minimumBalance = BigDecimal.valueOf(Math.max(0, Config.PLAYER_BANKS_MIN_BALANCE.get()));
        if (fundingAccount.getBalance().compareTo(minimumBalance) < 0) {
            source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral(
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
                source.sendSystemMessage(moneyLiteral(
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

            source.sendSystemMessage(moneyLiteral(
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
                online.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCould not deduct the required creation fees."));
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
            source.sendSystemMessage(moneyLiteral("§cBank created, but could not create founder account."));
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

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can open bank accounts."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, bank, gameTime, source.getServer());
        String status = getBankStatus(centralBank, bank);
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status)) {
            source.sendSystemMessage(moneyLiteral("§cThis bank is " + status.toLowerCase(Locale.ROOT) + " and cannot open new accounts."));
            return 1;
        }
        if ("RESTRICTED".equals(status)) {
            source.sendSystemMessage(moneyLiteral("§cThis bank is currently restricted and cannot open new accounts."));
            return 1;
        }

        AccountTypes accountType = parseAccountType(accountTypeRaw);
        if (accountType == null) {
            source.sendSystemMessage(moneyLiteral(
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
                source.sendSystemMessage(moneyLiteral("§cCertificate account requires a tier: short, medium, or long."));
                return 1;
            }
            long maturityTicks = switch (tier) {
                case "short" -> Config.CD_SHORT_TERM_TICKS.get();
                case "medium" -> Config.CD_MEDIUM_TERM_TICKS.get();
                case "long" -> Config.CD_LONG_TERM_TICKS.get();
                default -> -1L;
            };
            if (maturityTicks <= 0L) {
                source.sendSystemMessage(moneyLiteral("§cInvalid certificate tier configuration."));
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
            source.sendSystemMessage(moneyLiteral(
                    "§cYou already have this account type at " + bank.getBankName() + "."
            ));
            return 1;
        }

        if (findPrimaryAccount(centralBank, player.getUUID()) == null) {
            account.setPrimaryAccount(true);
        }

        MutableComponent message = moneyLiteral(
                "§aOpened " + account.getAccountType().label + " at §e" + bank.getBankName()
                        + "§a.\n§7Account ID: §f" + account.getAccountUUID()
        );
        if (accountType == AccountTypes.CertificateAccount) {
            message.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can set a primary bank."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }

        List<AccountHolder> inBank = getPlayerAccountsInBank(bank, player.getUUID());
        if (inBank.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have an account at that bank."));
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

        source.sendSystemMessage(moneyLiteral(
                "§aPrimary account set to §e" + bank.getBankName()
                        + " §7(" + selected.getAccountType().label + " " + shortId(selected.getAccountUUID()) + ")"
        ));
        return 1;
    }

    private static int handleCloseBankAccount(CommandSourceStack source, String bankNameRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can close bank accounts."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }

        List<AccountHolder> inBank = getPlayerAccountsInBank(bank, player.getUUID());
        if (inBank.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have an account at that bank."));
            return 1;
        }

        List<AccountHolder> allAccounts = new ArrayList<>(centralBank.SearchForAccount(player.getUUID()).values());
        if (allAccounts.size() <= 1) {
            source.sendSystemMessage(moneyLiteral("§cYou must keep at least one account open."));
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
            source.sendSystemMessage(moneyLiteral("§cNo fallback account available for balance transfer."));
            return 1;
        }

        BigDecimal transferAmount = toClose.getBalance();
        if (transferAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (!toClose.forceRemoveBalance(transferAmount) || !fallback.forceAddBalance(transferAmount)) {
                source.sendSystemMessage(moneyLiteral("§cCould not transfer remaining balance to fallback account."));
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

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can transfer between banks."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            source.sendSystemMessage(moneyLiteral(
                    "§cAmount exceeds global per-transaction limit of $" + Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()
            ));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank fromBank = resolveBankByName(centralBank, fromBankRaw);
        Bank toBank = resolveBankByName(centralBank, toBankRaw);
        if (fromBank == null || toBank == null) {
            source.sendSystemMessage(moneyLiteral("§cOne or both bank names are invalid."));
            return 1;
        }
        if (fromBank.getBankId().equals(toBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cSource and destination bank must be different."));
            return 1;
        }

        AccountHolder fromAccount = findPlayerAccountInBank(fromBank, player.getUUID());
        AccountHolder toAccount = findPlayerAccountInBank(toBank, player.getUUID());
        if (fromAccount == null || toAccount == null) {
            source.sendSystemMessage(moneyLiteral("§cYou must have an account at both banks."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, fromBank, gameTime, source.getServer());
        refreshBankOperationalState(centralBank, toBank, gameTime, source.getServer());
        if (!allowsCustomerTransfers(getBankStatus(centralBank, fromBank))
                || !allowsCustomerTransfers(getBankStatus(centralBank, toBank))) {
            source.sendSystemMessage(moneyLiteral("§cOne of the banks is not currently operational for transfers."));
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
            source.sendSystemMessage(moneyLiteral("§cTransfer failed."));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can send bank payments."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            source.sendSystemMessage(moneyLiteral(
                    "§cAmount exceeds global per-transaction limit of $" + Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()
            ));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder senderAccount = resolveDefaultLoanAccount(centralBank, sender.getUUID());
        if (senderAccount == null) {
            source.sendSystemMessage(moneyLiteral("§cNo source account available."));
            return 1;
        }

        String targetName = playerNameRaw == null ? "" : playerNameRaw.trim();
        if (targetName.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cInvalid player name."));
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
            source.sendSystemMessage(moneyLiteral("§cPlayer not found: " + targetName));
            return 1;
        }

        AccountHolder receiverAccount;
        if (bankNameRaw == null || bankNameRaw.isBlank()) {
            receiverAccount = resolveDefaultLoanAccount(centralBank, targetId);
        } else {
            Bank targetBank = resolveBankByName(centralBank, bankNameRaw);
            if (targetBank == null) {
                source.sendSystemMessage(moneyLiteral("§cTarget bank not found: " + bankNameRaw));
                return 1;
            }
            receiverAccount = findPlayerAccountInBank(targetBank, targetId);
        }

        if (receiverAccount == null) {
            source.sendSystemMessage(moneyLiteral("§cTarget player has no matching destination account."));
            return 1;
        }
        if (senderAccount.getAccountUUID().equals(receiverAccount.getAccountUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou cannot send money to the same account."));
            return 1;
        }

        Bank senderBank = centralBank.getBank(senderAccount.getBankId());
        Bank receiverBank = centralBank.getBank(receiverAccount.getBankId());
        if (senderBank == null || receiverBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is inconsistent for this transfer."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, senderBank, gameTime, source.getServer());
        refreshBankOperationalState(centralBank, receiverBank, gameTime, source.getServer());
        if (!allowsCustomerTransfers(getBankStatus(centralBank, senderBank))
                || !allowsCustomerTransfers(getBankStatus(centralBank, receiverBank))) {
            source.sendSystemMessage(moneyLiteral("§cOne of the banks is not currently accepting transfers."));
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
            source.sendSystemMessage(moneyLiteral("§cTransfer failed."));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral(
                "§aSent $" + amount.toPlainString() + " to §e" + resolvedTargetName
                        + "§a. Destination account: §f" + shortId(receiverAccount.getAccountUUID())
        ));
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can set bank branding."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cYou do not own a player bank."));
            return 1;
        }

        String motto = mottoRaw == null ? "" : mottoRaw.trim();
        if (motto.length() > 80) {
            source.sendSystemMessage(moneyLiteral("§cMotto is too long (max 80 characters)."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("motto", motto);
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(moneyLiteral(
                "§aUpdated motto for §e" + bank.getBankName()
                        + "§a to: §f" + (motto.isBlank() ? "(empty)" : motto)
        ));
        return 1;
    }

    private static int handleSetBankColor(CommandSourceStack source, String colorRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can set bank branding."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cYou do not own a player bank."));
            return 1;
        }

        String color = normalizeBankColor(colorRaw);
        if (color == null) {
            source.sendSystemMessage(moneyLiteral("§cInvalid color. Use #RRGGBB or common names like blue, red, green."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("color", color);
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(moneyLiteral(
                "§aUpdated color branding for §e" + bank.getBankName() + "§a to §f" + color
        ));
        return 1;
    }

    private static int handleBankTellerGet(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can request bank tellers."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can request teller eggs."));
            return 1;
        }

        int activeCount = BankTellerEntity.countActiveTellersForBank(source.getServer(), bank.getBankId());
        if (activeCount >= BankTellerEntity.MAX_TELLERS_PER_BANK) {
            source.sendSystemMessage(moneyLiteral(
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

        source.sendSystemMessage(moneyLiteral(
                "§aIssued teller egg for §e" + bank.getBankName()
                        + "§a. Active tellers: §f" + activeCount
                        + "§7/§f" + BankTellerEntity.MAX_TELLERS_PER_BANK
        ));
        return 1;
    }

    private static int handleBankTellerCount(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can view teller count."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can view teller count."));
            return 1;
        }

        int activeCount = BankTellerEntity.countActiveTellersForBank(source.getServer(), bank.getBankId());
        source.sendSystemMessage(moneyLiteral(
                "§7Active tellers for §e" + bank.getBankName() + "§7: §b"
                        + activeCount + "§7/§f" + BankTellerEntity.MAX_TELLERS_PER_BANK
        ));
        return 1;
    }

    private static int handleBankInfo(CommandSourceStack source, String bankNameRaw) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
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
        body.append(moneyLiteral("§7Bank Name: §e" + bank.getBankName() + "\n"));
        body.append(moneyLiteral("§7Bank ID: §f" + bank.getBankId() + "\n"));
        body.append(moneyLiteral("§7Owner: §f" + resolvePlayerName(source.getServer(), bank.getBankOwnerId()) + "\n"));
        body.append(moneyLiteral("§7Status: §f" + status + "\n"));
        body.append(moneyLiteral("§7Ownership Model: §f" + (ownershipModel == null || ownershipModel.isBlank() ? "SOLE" : ownershipModel) + "\n"));
        body.append(moneyLiteral("§7Brand Color: §f" + (color == null || color.isBlank() ? "#55AAFF" : color) + "\n"));
        body.append(moneyLiteral("§7Motto: §f" + (motto == null || motto.isBlank() ? "-" : motto) + "\n"));
        body.append(moneyLiteral("§7Accounts: §b" + bank.getBankAccounts().size() + "\n"));
        body.append(moneyLiteral("§7Reserve: §a$" + reserve.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Deposits: §6$" + deposits.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Reserve Ratio: §e" + ratio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(moneyLiteral("§7Minimum Required Reserve: §f$" + minReserve.toPlainString()));

        if (metadata.contains("nextLicenseFeeTick")) {
            body.append(moneyLiteral("\n§7Next License Due Tick: §f" + metadata.getLong("nextLicenseFeeTick")));
        }

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eBank Info", body));
        return 1;
    }

    private static int handleBankReserve(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can view reserve data."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
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
            source.sendSystemMessage(moneyLiteral("§cNo bank context available."));
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
        body.append(moneyLiteral("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(moneyLiteral("§7Status: §f" + getBankStatus(centralBank, bank) + "\n"));
        body.append(moneyLiteral("§7Reserve: §a$" + reserve.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Total Deposits: §6$" + deposits.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Reserve Ratio: §e" + ratio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(moneyLiteral("§7Minimum Reserve: §f$" + minReserve.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Daily Bank Cap: §b$" + dailyCap.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Daily Withdrawn: §f$" + dailyUsed.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Remaining Today: §a$" + dailyRemaining.toPlainString()));

        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bReserve Status", body));
        return 1;
    }

    private static int handleBankDashboard(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can view the dashboard."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cDashboard is available to bank owners."));
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
        body.append(moneyLiteral("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(moneyLiteral("§7Status: §f" + status + "\n"));
        body.append(moneyLiteral("§7Risk Level: ").append(moneyLiteral(risk).withStyle(riskColor)).append(moneyLiteral("\n")));
        body.append(moneyLiteral("§7Reserve: §a$" + reserve.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Reserve Ratio: §e" + reserveRatio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(moneyLiteral("§7Minimum Reserve: §f$" + minReserve.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Reserve Shortfall: §c$" + shortfall.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Daily Capacity: §b$" + dailyCap.toPlainString() + " / used $" + dailyUsed.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Outstanding Player Loans: §6$" + outstandingLoans.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Max Lendable: §f$" + maxLendable.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Queued Withdrawals: §f" + queuedWithdrawals + "\n"));
        body.append(moneyLiteral("§7Federal Funds Rate: §e" + centralBank.getFederalFundsRate() + "%"));

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eLiquidity Dashboard", body));
        return 1;
    }

    private static int handleBankAccountsList(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can view bank account rosters."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can list account holders."));
            return 1;
        }

        List<AccountHolder> accounts = bank.getBankAccounts().values().stream()
                .sorted(Comparator.comparing(a -> a.getAccountUUID().toString()))
                .toList();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(moneyLiteral("§7Account Holders: §b" + accounts.size() + "\n\n"));
        for (AccountHolder account : accounts) {
            body.append(moneyLiteral(
                    "§8- §f" + resolvePlayerName(source.getServer(), account.getPlayerUUID())
                            + " §7(" + account.getAccountType().label + " " + shortId(account.getAccountUUID()) + ")\n"
            ));
        }
        if (accounts.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bBank Accounts", body));
        return 1;
    }

    private static int handleBankCertificateSchedule(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can view CD schedules."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can view CD schedules."));
            return 1;
        }
        long now = currentOverworldGameTime(source.getServer());
        List<AccountHolder> cds = bank.getBankAccounts().values().stream()
                .filter(a -> a.getAccountType() == AccountTypes.CertificateAccount)
                .sorted(Comparator.comparingLong(AccountHolder::getCertificateMaturityGameTime))
                .toList();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Active CDs: §b" + cds.size() + "\n\n"));
        for (AccountHolder cd : cds) {
            body.append(moneyLiteral(
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
            body.append(moneyLiteral("§8- none"));
        }
        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eCertificate Schedule", body));
        return 1;
    }

    private static int handleBankLimitSet(CommandSourceStack source, String typeRaw, String amountRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can configure bank limits."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can configure limits."));
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
                    source.sendSystemMessage(moneyLiteral("§cSingle limit cannot exceed global max $" + maxAllowed.toPlainString()));
                    return 1;
                }
                metadata.putString("limitSingle", amount.toPlainString());
            }
            case "dailyplayer", "playerdaily" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    source.sendSystemMessage(moneyLiteral("§cDaily player limit cannot exceed global max $" + maxAllowed.toPlainString()));
                    return 1;
                }
                metadata.putString("limitDailyPlayer", amount.toPlainString());
            }
            case "dailybank", "bankdaily" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get());
                if (amount.compareTo(maxAllowed) > 0) {
                    source.sendSystemMessage(moneyLiteral("§cDaily bank limit cannot exceed global max $" + maxAllowed.toPlainString()));
                    return 1;
                }
                metadata.putString("limitDailyBank", amount.toPlainString());
            }
            case "teller", "tellercash", "cash", "withdrawal" -> {
                BigDecimal maxAllowed = BigDecimal.valueOf(Integer.MAX_VALUE / 100L);
                if (amount.compareTo(maxAllowed) > 0) {
                    source.sendSystemMessage(moneyLiteral("§cTeller limit cannot exceed $" + maxAllowed.toPlainString()));
                    return 1;
                }
                metadata.putString("limitTeller", amount.toPlainString());
            }
            default -> {
                source.sendSystemMessage(moneyLiteral("§cUnknown limit type. Use single, dailyplayer, dailybank, or teller."));
                return 1;
            }
        }
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral("§aUpdated " + type + " limit to $" + amount.toPlainString() + "."));
        return 1;
    }

    private static int handleBankLimitView(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can view bank limits."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can view custom limits."));
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
        BigDecimal teller = metadata.contains("limitTeller")
                ? readBigDecimal(metadata, "limitTeller")
                : new BigDecimal("250000");

        source.sendSystemMessage(ubsMessage(
                ChatFormatting.GOLD,
                "§eBank Limits",
                moneyLiteral("§7Bank: §e" + bank.getBankName() + "\n")
                        .append(moneyLiteral("§7Single Tx Limit: §f$" + single.toPlainString() + "\n"))
                        .append(moneyLiteral("§7Daily Player Limit: §f$" + dailyPlayer.toPlainString() + "\n"))
                        .append(moneyLiteral("§7Daily Bank Limit: §f$" + dailyBank.toPlainString() + "\n"))
                        .append(moneyLiteral("§7Teller Cash Limit: §f$" + teller.toPlainString()))
        ));
        return 1;
    }

    private static int handleBankRoleAssign(CommandSourceStack source, ServerPlayer target, String roleRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can assign roles."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can assign roles."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"ROLE_BASED".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            source.sendSystemMessage(moneyLiteral("§cThis bank is not configured for role-based governance."));
            return 1;
        }

        String role = roleRaw == null ? "" : roleRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("FOUNDER", "DIRECTOR", "TELLER", "AUDITOR").contains(role)) {
            source.sendSystemMessage(moneyLiteral("§cRole must be FOUNDER, DIRECTOR, TELLER, or AUDITOR."));
            return 1;
        }

        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));
        roleMap.put(target.getUUID(), role);
        metadata.putString("roles", encodeUuidStringMap(roleMap));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(moneyLiteral("§aAssigned role §e" + role + " §ato " + target.getName().getString()));
        target.sendSystemMessage(moneyLiteral("§aYou were assigned role §e" + role + " §aat bank " + bank.getBankName()));
        return 1;
    }

    private static int handleBankRoleRevoke(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can revoke roles."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can revoke roles."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));
        roleMap.remove(target.getUUID());
        metadata.putString("roles", encodeUuidStringMap(roleMap));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral("§aRevoked governance role for " + target.getName().getString()));
        return 1;
    }

    private static int handleBankRoleList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can list roles."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can list roles."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Governance Roles for §e" + bank.getBankName() + "\n\n"));
        if (roleMap.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            roleMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(entry -> body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can manage shares."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can manage shares."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"PERCENTAGE_SHARES".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            source.sendSystemMessage(moneyLiteral("§cThis bank is not using percentage-share governance."));
            return 1;
        }

        BigDecimal percent;
        try {
            percent = new BigDecimal(percentRaw.trim()).setScale(2, RoundingMode.HALF_EVEN);
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid percent."));
            return 1;
        }
        if (percent.compareTo(BigDecimal.ZERO) <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            source.sendSystemMessage(moneyLiteral("§cPercent must be > 0 and <= 100."));
            return 1;
        }

        Map<UUID, BigDecimal> shares = decodeShareMap(metadata.getString("shares"));
        shares.put(target.getUUID(), percent);
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : shares.values()) {
            total = total.add(value);
        }
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            source.sendSystemMessage(moneyLiteral(
                    "§cTotal shares would exceed 100%. Current proposed total: " + total.toPlainString() + "%"
            ));
            return 1;
        }
        metadata.putString("shares", encodeShareMap(shares));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral(
                "§aSet shares for " + target.getName().getString() + " to " + percent.toPlainString() + "%"
        ));
        return 1;
    }

    private static int handleBankSharesList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can list shares."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can list shares."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, BigDecimal> shares = decodeShareMap(metadata.getString("shares"));
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Share Distribution for §e" + bank.getBankName() + "\n\n"));
        if (shares.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            shares.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(entry -> body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can manage co-founders."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can manage co-founders."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        if (!"FIXED_COFOUNDERS".equalsIgnoreCase(metadata.getString("ownershipModel"))) {
            source.sendSystemMessage(moneyLiteral("§cThis bank is not configured for fixed co-founders."));
            return 1;
        }

        List<UUID> cofounders = decodeUuidList(metadata.getString("cofounders"));
        if (!cofounders.contains(target.getUUID())) {
            cofounders.add(target.getUUID());
            metadata.putString("cofounders", encodeUuidList(cofounders));
            centralBank.putBankMetadata(bank.getBankId(), metadata);
        }
        source.sendSystemMessage(moneyLiteral("§aAdded co-founder: " + target.getName().getString()));
        return 1;
    }

    private static int handleBankCofounderList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can list co-founders."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can list co-founders."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<UUID> cofounders = decodeUuidList(metadata.getString("cofounders"));
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Co-founders for §e" + bank.getBankName() + "\n\n"));
        if (cofounders.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (UUID id : cofounders) {
                body.append(moneyLiteral("§8- §f" + resolvePlayerName(source.getServer(), id) + "\n"));
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can hire employees."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can hire employees."));
            return 1;
        }
        String role = roleRaw == null ? "" : roleRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("TELLER", "DIRECTOR", "AUDITOR", "STAFF").contains(role)) {
            source.sendSystemMessage(moneyLiteral("§cInvalid role. Use TELLER, DIRECTOR, AUDITOR, or STAFF."));
            return 1;
        }
        BigDecimal salary;
        try {
            salary = new BigDecimal(salaryRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid salary."));
            return 1;
        }
        if (salary.compareTo(BigDecimal.ZERO) < 0) {
            source.sendSystemMessage(moneyLiteral("§cSalary must be non-negative."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        employees.put(target.getUUID(), new EmployeeSpec(role, salary));
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(moneyLiteral("§aHired " + target.getName().getString()
                + " as " + role + " ($" + salary.toPlainString() + ")."));
        target.sendSystemMessage(moneyLiteral("§aYou were hired by " + bank.getBankName()
                + " as " + role + " ($" + salary.toPlainString() + ")."));
        return 1;
    }

    private static int handleBankFire(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can fire employees."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can fire employees."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        employees.remove(target.getUUID());
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral("§eFired employee " + target.getName().getString()));
        target.sendSystemMessage(moneyLiteral("§cYou were removed from employment at " + bank.getBankName()));
        return 1;
    }

    private static int handleBankEmployeesList(CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can list employees."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can list employees."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Employees at §e" + bank.getBankName() + "\n\n"));
        if (employees.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            employees.forEach((id, employee) -> body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can quit bank employment."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        Map<UUID, EmployeeSpec> employees = decodeEmployeeMap(metadata.getString("employees"));
        if (employees.remove(player.getUUID()) == null) {
            source.sendSystemMessage(moneyLiteral("§cYou are not employed at this bank."));
            return 1;
        }
        metadata.putString("employees", encodeEmployeeMap(employees));
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral("§aYou resigned from " + bank.getBankName() + "."));
        return 1;
    }

    private static int handleBankLoanProductCreate(CommandSourceStack source,
                                                   String name,
                                                   String maxAmountRaw,
                                                   String interestRateRaw,
                                                   String durationTicksRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can create loan products."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveOwnedBankForPlayer(centralBank, actor.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can create loan products."));
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
            source.sendSystemMessage(moneyLiteral("§cInvalid interest rate."));
            return 1;
        }
        if (rate <= 0.0) {
            source.sendSystemMessage(moneyLiteral("§cInterest rate must be positive."));
            return 1;
        }
        long durationTicks;
        try {
            durationTicks = Long.parseLong(durationTicksRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid duration ticks."));
            return 1;
        }
        if (durationTicks < 20L) {
            source.sendSystemMessage(moneyLiteral("§cDuration must be at least 20 ticks."));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
        products.removeIf(product -> product.name().equalsIgnoreCase(name));
        products.add(new LoanProductSpec(name, maxAmount, rate, durationTicks));
        metadata.putString("loanProducts", encodeLoanProducts(products));
        centralBank.putBankMetadata(bank.getBankId(), metadata);

        source.sendSystemMessage(moneyLiteral(
                "§aLoan product created: §e" + name
                        + " §7max $"+ maxAmount.toPlainString()
                        + " §7APR " + rate + "% §7duration " + durationTicks + " ticks."
        ));
        return 1;
    }

    private static int handleBankLoanProductList(CommandSourceStack source, String bankNameRaw) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Loan products at §e" + bank.getBankName() + "\n\n"));
        if (products.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (LoanProductSpec product : products) {
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can apply for loan products."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        AccountHolder account = findPlayerAccountInBank(bank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cYou need an account at this bank to apply."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
        LoanProductSpec product = products.stream()
                .filter(entry -> entry.name().equalsIgnoreCase(productName))
                .findFirst()
                .orElse(null);
        if (product == null) {
            source.sendSystemMessage(moneyLiteral("§cLoan product not found: " + productName));
            return 1;
        }

        BigDecimal requested = parsePositiveWholeAmount(source, amountRaw);
        if (requested == null) {
            return 1;
        }
        if (requested.compareTo(product.maxAmount()) > 0) {
            source.sendSystemMessage(moneyLiteral(
                    "§cRequested amount exceeds product max of $" + product.maxAmount().toPlainString()
            ));
            return 1;
        }
        if (!bank.canIssueLoan(requested)) {
            source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cLoan issuance failed (reserve or compliance restrictions)."));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral(
                "§aLoan approved from product §e" + product.name()
                        + "§a for §6$" + requested.toPlainString()
                        + "§a. Repayment: §f" + payments + " x $" + periodic.toPlainString()
        ));
        return 1;
    }

    private static int handleBorrowFromCentralBank(CommandSourceStack source, String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can borrow for banks."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (amount.compareTo(BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()))) > 0) {
            source.sendSystemMessage(moneyLiteral(
                    "§cBorrow amount exceeds global transaction cap of $" + Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()
            ));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can borrow from the Central Bank."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        refreshBankOperationalState(centralBank, bank, gameTime, source.getServer());
        String status = getBankStatus(centralBank, bank);
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status)) {
            source.sendSystemMessage(moneyLiteral("§cYour bank cannot borrow while " + status.toLowerCase(Locale.ROOT) + "."));
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

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can view bank loans."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        Bank bank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can view this loan summary."));
            return 1;
        }

        List<Map.Entry<UUID, CompoundTag>> loans = centralBank.getInterbankLoans().entrySet().stream()
                .filter(entry -> bank.getBankId().equals(readUuid(entry.getValue(), "bankId")))
                .filter(entry -> "ACTIVE".equalsIgnoreCase(entry.getValue().getString("status")))
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(moneyLiteral("§7Outstanding CB Loans: §b" + loans.size() + "\n\n"));
        if (loans.isEmpty()) {
            body.append(moneyLiteral("§8No active Central Bank loans."));
        } else {
            for (Map.Entry<UUID, CompoundTag> entry : loans) {
                CompoundTag loan = entry.getValue();
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can post inter-bank offers."));
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
            source.sendSystemMessage(moneyLiteral("§cInvalid annual rate."));
            return 1;
        }
        if (annualRate <= 0.0 || annualRate > 1000.0) {
            source.sendSystemMessage(moneyLiteral("§cAnnual rate must be > 0 and <= 1000."));
            return 1;
        }

        long termTicks;
        try {
            termTicks = Long.parseLong(termTicksRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid term ticks."));
            return 1;
        }
        if (termTicks < 20L) {
            source.sendSystemMessage(moneyLiteral("§cTerm must be at least 20 ticks."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank lenderBank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (lenderBank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can post lending offers."));
            return 1;
        }
        if (lenderBank.getDeclaredReserve().compareTo(amount) < 0) {
            source.sendSystemMessage(moneyLiteral("§cInsufficient reserve to back this offer."));
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

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        long nowTick = currentOverworldGameTime(source.getServer());

        List<CompoundTag> offers = centralBank.getInterbankOffers().values().stream()
                .filter(tag -> "OPEN".equalsIgnoreCase(tag.getString("status")))
                .filter(tag -> !tag.contains("expiryTick") || tag.getLong("expiryTick") >= nowTick)
                .sorted(Comparator.comparingLong(tag -> tag.getLong("createdTick")))
                .toList();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Open Offers: §b" + offers.size() + "\n\n"));
        if (offers.isEmpty()) {
            body.append(moneyLiteral("§8No open inter-bank offers."));
        } else {
            for (CompoundTag offer : offers) {
                UUID lenderId = offer.getUUID("lenderBankId");
                Bank lender = centralBank.getBank(lenderId);
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can accept inter-bank offers."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        CompoundTag offer = centralBank.getInterbankOffers().get(offerId);
        if (offer == null) {
            source.sendSystemMessage(moneyLiteral("§cOffer not found: " + offerId));
            return 1;
        }
        if (!"OPEN".equalsIgnoreCase(offer.getString("status"))) {
            source.sendSystemMessage(moneyLiteral("§cOffer is not open."));
            return 1;
        }

        long nowTick = currentOverworldGameTime(source.getServer());
        if (offer.contains("expiryTick") && offer.getLong("expiryTick") < nowTick) {
            offer.putString("status", "EXPIRED");
            centralBank.getInterbankOffers().put(offerId, offer);
            BankManager.markDirty();
            source.sendSystemMessage(moneyLiteral("§cOffer has expired."));
            return 1;
        }

        Bank borrowerBank = resolveOwnedBankForPlayer(centralBank, player.getUUID());
        if (borrowerBank == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly bank owners can accept inter-bank offers."));
            return 1;
        }

        UUID lenderBankId = offer.getUUID("lenderBankId");
        Bank lenderBank = centralBank.getBank(lenderBankId);
        if (lenderBank == null) {
            source.sendSystemMessage(moneyLiteral("§cLender bank no longer exists."));
            return 1;
        }
        if (lenderBank.getBankId().equals(borrowerBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cYou cannot accept your own offer."));
            return 1;
        }

        BigDecimal principal = readBigDecimal(offer, "amount");
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(moneyLiteral("§cOffer amount is invalid."));
            return 1;
        }
        if (lenderBank.getDeclaredReserve().compareTo(principal) < 0) {
            source.sendSystemMessage(moneyLiteral("§cLender bank no longer has sufficient reserve."));
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

        source.sendSystemMessage(moneyLiteral(
                "§aAccepted inter-bank offer " + shortId(offerId)
                        + " for $" + principal.toPlainString()
                        + ". Maturity tick: " + (nowTick + termTicks)
        ));

        ServerPlayer lenderOwner = source.getServer().getPlayerList().getPlayer(lenderBank.getBankOwnerId());
        if (lenderOwner != null) {
            lenderOwner.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can submit appeals."));
            return 1;
        }

        String message = messageRaw == null ? "" : messageRaw.trim();
        if (message.isBlank()) {
            source.sendSystemMessage(moneyLiteral("§cAppeal message cannot be empty."));
            return 1;
        }
        if (message.length() > 256) {
            source.sendSystemMessage(moneyLiteral("§cAppeal message is too long (max 256 chars)."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        boolean hasPendingAppeal = centralBank.getBankAppeals().values().stream()
                .anyMatch(tag -> player.getUUID().equals(readUuid(tag, "playerId"))
                        && "PENDING".equalsIgnoreCase(tag.getString("status")));
        if (hasPendingAppeal) {
            source.sendSystemMessage(moneyLiteral("§cYou already have a pending appeal."));
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

        source.sendSystemMessage(moneyLiteral(
                "§aAppeal submitted. ID: §f" + appealId + "\n§7Message: §f" + message
        ));

        for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
            if (!online.hasPermissions(3)) {
                continue;
            }
            online.sendSystemMessage(moneyLiteral(
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

    private static CompletableFuture<Suggestions> suggestAccountOpenTypes(CommandContext<CommandSourceStack> context,
                                                                          SuggestionsBuilder builder) {
        List<String> dynamicTypes = new ArrayList<>();
        for (AccountTypes type : AccountTypes.values()) {
            String token = accountTypeSuggestionToken(type);
            if (token != null && !token.isBlank() && !dynamicTypes.contains(token)) {
                dynamicTypes.add(token);
            }
        }
        if (dynamicTypes.isEmpty()) {
            dynamicTypes = List.of("checking", "saving", "moneymarket", "certificate");
        }
        return SharedSuggestionProvider.suggest(dynamicTypes, builder);
    }

    private static CompletableFuture<Suggestions> suggestCertificateTiers(CommandContext<CommandSourceStack> context,
                                                                          SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(CERTIFICATE_TIER_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestCertificateTiersForAccountOpen(CommandContext<CommandSourceStack> context,
                                                                                         SuggestionsBuilder builder) {
        String accountTypeRaw;
        try {
            accountTypeRaw = StringArgumentType.getString(context, "accountType");
        } catch (IllegalArgumentException ignored) {
            return builder.buildFuture();
        }
        if (parseAccountType(accountTypeRaw) != AccountTypes.CertificateAccount) {
            return builder.buildFuture();
        }
        return suggestCertificateTiers(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestBankNames(CommandContext<CommandSourceStack> context,
                                                                   SuggestionsBuilder builder) {
        CentralBank centralBank = BankManager.getCentralBank(context.getSource().getServer());
        if (centralBank == null) {
            return builder.buildFuture();
        }
        List<String> names = centralBank.getBanks().values().stream()
                .map(Bank::getBankName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestBankLimitTypes(CommandContext<CommandSourceStack> context,
                                                                        SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(BANK_LIMIT_TYPE_SUGGESTIONS, builder);
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

    private static String accountTypeSuggestionToken(AccountTypes type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case CheckingAccount -> "checking";
            case SavingAccount -> "saving";
            case MoneyMarketAccount -> "moneymarket";
            case CertificateAccount -> "certificate";
            default -> "";
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
            source.sendSystemMessage(moneyLiteral(
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

            source.sendSystemMessage(moneyLiteral(
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
                    moneyLiteral("§c[UBS] Bank run detected at " + bank.getBankName()
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
        owner.sendSystemMessage(moneyLiteral(
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

    private static int handleAccountBalance(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        ConcurrentHashMap<UUID, AccountHolder> accounts = centralBank.SearchForAccount(player.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have any bank accounts yet."));
            return 1;
        }

        AccountHolder primary = findPrimaryAccount(centralBank, player.getUUID());
        if (primary == null) {
            source.sendSystemMessage(moneyLiteral(
                    "§cNo primary account set. Use §e/account primary set <Account ID>§c first."
            ));
            return 1;
        }

        Bank bank = centralBank.getBank(primary.getBankId());
        String bankName = bank == null ? "Unknown" : bank.getBankName();
        String accountType = primary.getAccountType() == null ? "Unknown" : primary.getAccountType().label;
        String abbreviatedBalance = MoneyText.abbreviateWithDollar(primary.getBalance());
        source.sendSystemMessage(moneyLiteral(
                "§6§lUltimate Banking System §7- §eAccount Balance\n"
                        + "§7Bank: §f" + bankName + "\n"
                        + "§7Type: §f" + accountType + "\n"
                        + "§7Account ID: §f" + primary.getAccountUUID() + "\n"
                        + "§7Balance: §a" + abbreviatedBalance
        ));
        return 1;
    }

    private static int handleBankList(CommandSourceStack source) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        List<Bank> banks = centralBank.getBanks().values().stream()
                .sorted(Comparator.comparing(Bank::getBankName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Banks Registered: §b" + banks.size() + "\n"));
        if (banks.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (Bank bank : banks) {
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (selected == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account found. Create an account first."));
            return 1;
        }

        source.sendSystemMessage(ubsMessage(
                ChatFormatting.AQUA,
                "§bCredit Score",
                moneyLiteral("§7Account: §f" + shortId(selected.getAccountUUID()) + "\n")
                        .append(moneyLiteral("§7Score: §e" + selected.getCreditScore()))
        ));
        return 1;
    }

    private static int handleLoanRequest(CommandSourceStack source, String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can request loans."));
            return 1;
        }

        BigDecimal principal;
        try {
            principal = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid loan amount."));
            return 1;
        }

        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(moneyLiteral("§cAmount must be greater than zero."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (selected == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account found. Create an account first."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        LoanService.LoanQuote quote = LoanService.createQuote(selected, principal, gameTime);
        PENDING_LOAN_CONFIRMATIONS.put(player.getUUID(), quote);

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Account: §f" + selected.getAccountUUID() + "\n"));
        body.append(moneyLiteral("§7Principal: §6$" + quote.principal().toPlainString() + "\n"));
        body.append(moneyLiteral("§7Interest Rate (APR): §e" + quote.annualInterestRate() + "%\n"));
        body.append(moneyLiteral("§7Total Repayable: §6$" + quote.totalRepayable().toPlainString() + "\n"));
        body.append(moneyLiteral("§7Payments: §f" + quote.totalPayments() + " x $" + quote.periodicPayment().toPlainString() + "\n"));
        body.append(moneyLiteral("§7First Due (game ticks): §f" + quote.firstDueGameTime() + "\n"));
        if (quote.requiresAdminApproval()) {
            body.append(moneyLiteral("§eThis loan requires admin approval after confirmation.\n"));
            body.append(moneyLiteral("§7Reason: §f" + quote.approvalReason() + "\n"));
        } else {
            body.append(moneyLiteral("§aEligible for auto-approval after confirmation.\n"));
        }
        body.append(moneyLiteral("\n§7Run §f/account loan confirm §7to continue."));

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eLoan Terms Preview", body));
        return 1;
    }

    private static int handleLoanConfirm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can confirm loans."));
            return 1;
        }

        LoanService.LoanQuote quote = PENDING_LOAN_CONFIRMATIONS.remove(player.getUUID());
        if (quote == null) {
            source.sendSystemMessage(moneyLiteral("§cNo pending loan request. Use /account loan request <amount> first."));
            return 1;
        }

        if (quote.requiresAdminApproval()) {
            LoanService.queueAdminApproval(quote);
            source.sendSystemMessage(moneyLiteral(
                    "§eLoan submitted for admin approval. You will be notified when it is reviewed."
            ));
            for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
                if (online.hasPermissions(3)) {
                    online.sendSystemMessage(moneyLiteral(
                            "§6[UBS] Loan approval needed: " + player.getName().getString()
                                    + " requested $" + quote.principal().toPlainString()
                    ));
                }
            }
            return 1;
        }

        var issued = LoanService.issueLoan(source.getServer(), quote);
        if (issued == null) {
            source.sendSystemMessage(moneyLiteral("§cLoan issuance failed. Please try again later."));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral(
                "§aLoan approved and issued: §6$" + quote.principal().toPlainString()
                        + "§a. Repayment: §f" + quote.totalPayments()
                        + " x $" + quote.periodicPayment().toPlainString()
        ));
        return 1;
    }

    private static int handleLoanStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can view loan status."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (selected == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account found."));
            return 1;
        }

        var loans = selected.getActiveLoans().values().stream()
                .sorted(Comparator.comparing(l -> l.getLoanId().toString()))
                .toList();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Account: §f" + selected.getAccountUUID() + "\n"));
        body.append(moneyLiteral("§7Credit Score: §e" + selected.getCreditScore() + "\n"));
        body.append(moneyLiteral("§7Defaulted: " + (selected.isDefaulted() ? "§cYES" : "§aNO") + "\n"));
        body.append(moneyLiteral("§7Active Loans: §b" + loans.size() + "\n\n"));
        if (loans.isEmpty()) {
            body.append(moneyLiteral("§8No active loans."));
        } else {
            for (var loan : loans) {
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can request CD early withdrawal."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder cdAccount = centralBank.SearchForAccountByAccountId(accountId);
        if (cdAccount == null || !cdAccount.getPlayerUUID().equals(player.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cCD account not found or not owned by you."));
            return 1;
        }
        if (cdAccount.getAccountType() != AccountTypes.CertificateAccount) {
            source.sendSystemMessage(moneyLiteral("§cThat account is not a Certificate of Deposit account."));
            return 1;
        }

        long gameTime = currentOverworldGameTime(source.getServer());
        if (!cdAccount.isCertificateLocked(gameTime)) {
            source.sendSystemMessage(moneyLiteral("§aThis CD is already matured/unlocked. No penalty required."));
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

        source.sendSystemMessage(moneyLiteral(
                "§eEarly CD withdrawal confirmation required.\n"
                        + "§7Account: §f" + shortId(cdAccount.getAccountUUID()) + "\n"
                        + "§7Principal: §6$" + principal.toPlainString() + "\n"
                        + "§7Penalty: §c$" + penalty.toPlainString() + " §8(" + (int) Math.round(penaltyFactor * 100) + "% of earned interest)\n"
                        + "§7Net payout: §a$" + payout.toPlainString() + "\n"
                        + "§7Run §f/account cd confirm §7within 60 seconds to proceed."
        ));
        return 1;
    }

    private static int handleBankConfirm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can confirm this action."));
            return 1;
        }

        CompoundTag pending = PENDING_CD_BREAK_CONFIRMATIONS.get(player.getUUID());
        if (pending == null) {
            source.sendSystemMessage(moneyLiteral("§cNo pending confirmation action."));
            return 1;
        }

        if (System.currentTimeMillis() > pending.getLong("expiresAtMillis")) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(moneyLiteral("§cConfirmation expired. Start the action again."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder cdAccount = centralBank.SearchForAccountByAccountId(pending.getUUID("accountId"));
        if (cdAccount == null || !cdAccount.getPlayerUUID().equals(player.getUUID())) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(moneyLiteral("§cCD account is no longer available."));
            return 1;
        }

        BigDecimal principal = readBigDecimal(pending, "principal");
        BigDecimal penalty = readBigDecimal(pending, "penalty");
        BigDecimal payout = readBigDecimal(pending, "payout");
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(moneyLiteral("§cInvalid pending action."));
            return 1;
        }

        Bank bank = centralBank.getBank(cdAccount.getBankId());
        if (bank == null) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(moneyLiteral("§cBank no longer exists for this CD."));
            return 1;
        }

        AccountHolder destination = getPlayerAccountsInBank(bank, player.getUUID()).stream()
                .filter(account -> account.getAccountType() == AccountTypes.CheckingAccount)
                .findFirst()
                .orElse(null);
        if (destination == null) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(moneyLiteral("§cYou need a checking account at this bank to receive the payout."));
            return 1;
        }

        if (!cdAccount.forceRemoveBalance(principal)) {
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(moneyLiteral("§cFailed to release CD principal."));
            return 1;
        }
        if (!destination.forceAddBalance(payout)) {
            cdAccount.forceAddBalance(principal);
            PENDING_CD_BREAK_CONFIRMATIONS.remove(player.getUUID());
            source.sendSystemMessage(moneyLiteral("§cFailed to transfer payout to destination account."));
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
        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid amount."));
            return 1;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.stripTrailingZeros().scale() > 0) {
            source.sendSystemMessage(moneyLiteral("§cAmount must be a positive whole number."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder selected;
        boolean usedHeldCard = false;

        var heldCard = CreditCardService.findHeldCard(centralBank, player);
        if (heldCard.hasCard()) {
            if (!heldCard.validation().valid()) {
                source.sendSystemMessage(moneyLiteral("§cCredit card rejected: " + heldCard.validation().message()));
                return 1;
            }
            selected = centralBank.SearchForAccountByAccountId(heldCard.validation().accountId());
            if (selected == null || !player.getUUID().equals(selected.getPlayerUUID())) {
                source.sendSystemMessage(moneyLiteral("§cCredit card rejected: linked account is unavailable."));
                return 1;
            }
            usedHeldCard = true;
        } else {
            selected = resolveDefaultLoanAccount(centralBank, player.getUUID());
            if (selected == null) {
                source.sendSystemMessage(moneyLiteral("§cNo account available."));
                return 1;
            }
        }

        long amountLong;
        try {
            amountLong = amount.longValueExact();
        } catch (ArithmeticException ex) {
            source.sendSystemMessage(moneyLiteral("§cAmount is too large."));
            return 1;
        }

        var api = UltimateBankingApiProvider.get();
        UUID merchantAccountId = null;
        if (shopName != null && !shopName.isBlank()) {
            try {
                merchantAccountId = UUID.fromString(shopName.trim());
            } catch (IllegalArgumentException ignored) {
                merchantAccountId = null;
            }
        }

        var result = merchantAccountId == null
                ? api.shopPurchase(selected.getAccountUUID(), amountLong, shopName)
                : api.shopPurchase(
                        selected.getAccountUUID(),
                        merchantAccountId,
                        amountLong,
                        "Shop Payment",
                        "command"
                );
        if (!result.success()) {
            source.sendSystemMessage(moneyLiteral("§cPurchase failed: " + result.reason()));
            return 1;
        }

        String targetLabel = merchantAccountId == null
                ? (shopName == null || shopName.isBlank() ? "Shop" : shopName)
                : ("merchant account " + merchantAccountId.toString().substring(0, 8));
        source.sendSystemMessage(moneyLiteral(
                "§aPaid $" + amount.toPlainString() + " at " + targetLabel + ". New balance: $" + result.balanceAfter().toPlainString()
        ));
        if (usedHeldCard) {
            source.sendSystemMessage(moneyLiteral("§bPayment source: held credit card account §f" + shortId(selected.getAccountUUID()) + "§b."));
        }
        return 1;
    }

    private static int handleWithdrawNote(CommandSourceStack source, String amountRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account available."));
            return 1;
        }

        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cWithdraw note failed: insufficient funds."));
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
        note.set(DataComponents.CUSTOM_NAME, moneyLiteral("Bank Note - $" + amount.toPlainString()).withStyle(ChatFormatting.GOLD));

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

        source.sendSystemMessage(moneyLiteral(
                "§aIssued bank note for §6$" + amount.toPlainString() + "§a. Serial: §f" + serial
        ));
        return 1;
    }

    private static int handleDepositNote(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() != ModItems.BANK_NOTE.get()) {
            source.sendSystemMessage(moneyLiteral("§cHold a bank note in your main hand."));
            return 1;
        }

        CompoundTag tag = readCustomTag(held);
        if (tag == null || !tag.contains("ubs_note_serial") || !tag.contains("ubs_note_amount")) {
            source.sendSystemMessage(moneyLiteral("§cInvalid bank note data."));
            return 1;
        }

        String serial = tag.getString("ubs_note_serial");
        BigDecimal amount;
        try {
            amount = new BigDecimal(tag.getString("ubs_note_amount"));
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid bank note amount."));
            return 1;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(moneyLiteral("§cInvalid bank note value."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        if (centralBank.isNoteSerialRedeemed(serial)) {
            source.sendSystemMessage(moneyLiteral("§cThis bank note serial has already been redeemed."));
            return 1;
        }

        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account available."));
            return 1;
        }

        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cDeposit failed."));
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
        source.sendSystemMessage(moneyLiteral(
                "§aDeposited bank note: §6$" + amount.toPlainString() + "§a."
        ));
        return 1;
    }

    private static int handleWriteCheque(CommandSourceStack source, String recipientNameRaw, String amountRaw) {
        ServerPlayer writer = source.getPlayer();
        if (writer == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        String recipientName = recipientNameRaw == null ? "" : recipientNameRaw.trim();
        if (recipientName.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cInvalid recipient."));
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
            source.sendSystemMessage(moneyLiteral("§cUnknown player profile: " + recipientNameRaw));
            return 1;
        }

        if (writer.getUUID().equals(recipientUuid)) {
            source.sendSystemMessage(moneyLiteral("§cYou cannot write a cheque to yourself."));
            return 1;
        }

        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder account = resolveDefaultLoanAccount(centralBank, writer.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cNo source account available."));
            return 1;
        }
        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cInsufficient funds for cheque."));
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
        cheque.set(DataComponents.CUSTOM_NAME, moneyLiteral("Cheque - $" + amount.toPlainString()).withStyle(ChatFormatting.GREEN));

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
        source.sendSystemMessage(moneyLiteral(
                "§aCheque written for §6$" + amount.toPlainString() + "§a to §e" + recipientName
                        + "§a. ID: §f" + chequeId
        ));
        return 1;
    }

    private static int handleDepositCheque(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() != ModItems.CHEQUE.get()) {
            source.sendSystemMessage(moneyLiteral("§cHold a cheque in your main hand."));
            return 1;
        }
        CompoundTag tag = readCustomTag(held);
        if (tag == null || !tag.contains("ubs_cheque_id") || !tag.contains("ubs_cheque_amount") || !tag.contains("ubs_cheque_recipient")) {
            source.sendSystemMessage(moneyLiteral("§cInvalid cheque data."));
            return 1;
        }

        String chequeId = tag.getString("ubs_cheque_id");
        UUID recipientId = tag.getUUID("ubs_cheque_recipient");
        if (!player.getUUID().equals(recipientId)) {
            source.sendSystemMessage(moneyLiteral("§cThis cheque is not payable to you."));
            return 1;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(tag.getString("ubs_cheque_amount"));
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid cheque amount."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        if (centralBank.isChequeRedeemed(chequeId)) {
            source.sendSystemMessage(moneyLiteral("§cThis cheque has already been redeemed."));
            return 1;
        }

        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cNo destination account available."));
            return 1;
        }
        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cFailed to redeem cheque."));
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
        source.sendSystemMessage(moneyLiteral(
                "§aCheque deposited: §6$" + amount.toPlainString() + "§a."
        ));
        return 1;
    }

    private static int handleHudToggle(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }

        boolean current = HUD_ENABLED_OVERRIDES.getOrDefault(player.getUUID(), Config.HUD_ENABLED_BY_DEFAULT.get());
        boolean next = !current;
        HUD_ENABLED_OVERRIDES.put(player.getUUID(), next);

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        HudStatePayload hudState = buildHudStatePayload(centralBank, player.getUUID());
        PacketDistributor.sendToPlayer(player, hudState);
        if (!next) {
            source.sendSystemMessage(moneyLiteral("§aBalance HUD is now §4disabled§a."));
            return 1;
        }

        if (hudState.enabled()) {
            source.sendSystemMessage(moneyLiteral("§aBalance HUD is now §2enabled§a."));
        } else {
            source.sendSystemMessage(moneyLiteral(
                    "§eBalance HUD is toggled on, but hidden because no HUD account is available."
            ));
        }
        return 1;
    }

    private static int handleHudMonitorPrimary(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        HUD_ACCOUNT_MONITOR_OVERRIDES.remove(player.getUUID());
        HudStatePayload hudState = buildHudStatePayload(centralBank, player.getUUID());
        PacketDistributor.sendToPlayer(player, hudState);

        if (hudState.enabled()) {
            source.sendSystemMessage(moneyLiteral("§aHUD monitor set to your §2primary account§a."));
        } else {
            source.sendSystemMessage(moneyLiteral("§eNo primary account is set, so HUD is currently hidden."));
        }
        return 1;
    }

    private static int handleHudMonitorAccount(CommandSourceStack source, UUID accountId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }
        if (accountId == null) {
            source.sendSystemMessage(moneyLiteral("§cInvalid account id."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !player.getUUID().equals(account.getPlayerUUID())) {
            source.sendSystemMessage(moneyLiteral("§cThat account does not belong to you."));
            return 1;
        }

        HUD_ACCOUNT_MONITOR_OVERRIDES.put(player.getUUID(), accountId);
        HudStatePayload hudState = buildHudStatePayload(centralBank, player.getUUID());
        PacketDistributor.sendToPlayer(player, hudState);

        Bank bank = centralBank.getBank(account.getBankId());
        String bankName = bank == null ? "Unknown Bank" : bank.getBankName();
        source.sendSystemMessage(moneyLiteral(
                "§aHUD monitor set to account §f" + shortId(accountId)
                        + " §7(" + account.getAccountType().label + " @ " + bankName + ")"
        ));
        return 1;
    }

    private static int handleSafeBoxList(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use safe deposit boxes."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account available."));
            return 1;
        }

        int maxSlots = Math.max(1, account.getSafeBoxSlotCount());
        int usedSlots = account.getSafeBoxSlots().size();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Account: §f" + accountLabel(account) + "\n"));
        body.append(moneyLiteral("§7Slots Used: §b" + usedSlots + "§7/§b" + maxSlots + "\n\n"));
        for (int slot = 0; slot < maxSlots; slot++) {
            CompoundTag stackTag = account.getSafeBoxSlots().get(slot);
            if (stackTag == null) {
                body.append(moneyLiteral("§8[" + slot + "] §7(empty)\n"));
                continue;
            }
            var parsed = ItemStack.parse(source.getServer().registryAccess(), stackTag);
            if (parsed.isEmpty() || parsed.get().isEmpty()) {
                body.append(moneyLiteral("§8[" + slot + "] §7(invalid item)\n"));
                continue;
            }
            ItemStack stack = parsed.get();
            body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can use safe deposit boxes."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account available."));
            return 1;
        }

        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cHold an item in your main hand to deposit."));
            return 1;
        }
        ItemStack copy = held.copy();
        if (!account.depositToSafeBox(copy, source.getServer().registryAccess())) {
            source.sendSystemMessage(moneyLiteral("§cSafe box is full for this account type."));
            return 1;
        }
        held.shrink(held.getCount());
        source.sendSystemMessage(moneyLiteral(
                "§aDeposited §f" + copy.getHoverName().getString()
                        + " §ax" + copy.getCount()
                        + " §ainto safe deposit box."
        ));
        return 1;
    }

    private static int handleSafeBoxWithdraw(CommandSourceStack source, String slotRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use safe deposit boxes."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = resolveDefaultLoanAccount(centralBank, player.getUUID());
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cNo account available."));
            return 1;
        }

        int slot;
        try {
            slot = Integer.parseInt(slotRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cSlot must be a number."));
            return 1;
        }
        if (slot < 0 || slot >= account.getSafeBoxSlotCount()) {
            source.sendSystemMessage(moneyLiteral("§cSlot out of range."));
            return 1;
        }

        ItemStack withdrawn = account.withdrawFromSafeBox(slot, source.getServer().registryAccess());
        if (withdrawn.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cNo item in that slot."));
            return 1;
        }
        if (!player.getInventory().add(withdrawn)) {
            player.drop(withdrawn, false);
        }
        source.sendSystemMessage(moneyLiteral(
                "§aWithdrew §f" + withdrawn.getHoverName().getString()
                        + " §ax" + withdrawn.getCount() + " §afrom slot " + slot + "."
        ));
        return 1;
    }

    private static int handleBankHeistComingSoon(CommandSourceStack source) {
        source.sendSystemMessage(moneyLiteral(
                "§eBank Heist is currently §6Coming Soon§e and temporarily disabled."
        ));
        return 1;
    }

    private static int handleJointCreate(CommandSourceStack source, ServerPlayer invitedPlayer, String bankName) {
        ServerPlayer creator = source.getPlayer();
        if (creator == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can create joint accounts."));
            return 1;
        }
        if (invitedPlayer.getUUID().equals(creator.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou cannot create a joint account with yourself."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = centralBank.getBankByName(bankName);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankName));
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
            source.sendSystemMessage(moneyLiteral("§cUnable to create joint account (duplicate account type for owner in this bank)."));
            return 1;
        }
        source.sendSystemMessage(moneyLiteral(
                "§aJoint account created: §f" + account.getAccountUUID() + " §7at §e" + bank.getBankName()
        ));
        invitedPlayer.sendSystemMessage(moneyLiteral(
                "§aYou were added as co-owner of joint account §f" + account.getAccountUUID()
        ));
        return 1;
    }

    private static int handleBusinessCreate(CommandSourceStack source, String label, String bankName) {
        ServerPlayer creator = source.getPlayer();
        if (creator == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can create business accounts."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        Bank bank = centralBank.getBankByName(bankName);
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankName));
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
            source.sendSystemMessage(moneyLiteral("§cUnable to create business account (duplicate account type for owner in this bank)."));
            return 1;
        }
        source.sendSystemMessage(moneyLiteral(
                "§aBusiness account created for §e" + label + "§a. Account: §f" + account.getAccountUUID()
        ));
        return 1;
    }

    private static int handleBusinessGrant(CommandSourceStack source, UUID accountId, ServerPlayer target, String role) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can grant access."));
            return 1;
        }

        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!"BUSINESS".equals(account.getAccountAccessType())) {
            source.sendSystemMessage(moneyLiteral("§cThat account is not a business account."));
            return 1;
        }
        if (!account.canManage(actor.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have manage permission for this account."));
            return 1;
        }
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!List.of("VIEW", "DEPOSIT", "WITHDRAW", "MANAGE", "OWNER").contains(normalizedRole)) {
            source.sendSystemMessage(moneyLiteral("§cInvalid role. Use VIEW, DEPOSIT, WITHDRAW, MANAGE, or OWNER."));
            return 1;
        }
        account.grantAccessRole(target.getUUID(), normalizedRole);
        source.sendSystemMessage(moneyLiteral(
                "§aGranted role §e" + normalizedRole + " §ato §f" + target.getName().getString()
        ));
        target.sendSystemMessage(moneyLiteral(
                "§aYou were granted role §e" + normalizedRole + " §aon business account §f" + account.getAccountUUID()
        ));
        return 1;
    }

    private static int handleBusinessRevoke(CommandSourceStack source, UUID accountId, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can revoke access."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!"BUSINESS".equals(account.getAccountAccessType())) {
            source.sendSystemMessage(moneyLiteral("§cThat account is not a business account."));
            return 1;
        }
        if (!account.canManage(actor.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have manage permission for this account."));
            return 1;
        }
        account.revokeAccessRole(target.getUUID());
        source.sendSystemMessage(moneyLiteral("§aRevoked account access for §f" + target.getName().getString()));
        target.sendSystemMessage(moneyLiteral("§eYour access to business account §f" + account.getAccountUUID() + " §ewas revoked."));
        return 1;
    }

    private static int handleBusinessTransferOwner(CommandSourceStack source, UUID accountId, ServerPlayer target) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can transfer ownership."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!"BUSINESS".equals(account.getAccountAccessType())) {
            source.sendSystemMessage(moneyLiteral("§cThat account is not a business account."));
            return 1;
        }
        if (!"OWNER".equals(account.getRole(actor.getUUID()))) {
            source.sendSystemMessage(moneyLiteral("§cOnly current owner can transfer ownership."));
            return 1;
        }
        account.grantAccessRole(actor.getUUID(), "MANAGE");
        account.grantAccessRole(target.getUUID(), "OWNER");
        source.sendSystemMessage(moneyLiteral("§aOwnership transferred to §f" + target.getName().getString()));
        target.sendSystemMessage(moneyLiteral("§aYou are now owner of business account §f" + account.getAccountUUID()));
        return 1;
    }

    private static int handleSharedAccountInfo(CommandSourceStack source, UUID accountId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!account.canView(player.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have access to this account."));
            return 1;
        }

        String owners = account.getAccessRoles().entrySet().stream()
                .filter(e -> "OWNER".equals(e.getValue()))
                .map(e -> shortId(e.getKey()))
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Account ID: §f" + account.getAccountUUID() + "\n"));
        body.append(moneyLiteral("§7Mode: §f" + account.getAccountAccessType() + "\n"));
        if ("BUSINESS".equals(account.getAccountAccessType())) {
            body.append(moneyLiteral("§7Business: §f" + account.getBusinessLabel() + "\n"));
        }
        body.append(moneyLiteral("§7Balance: §a$" + account.getBalance().toPlainString() + "\n"));
        body.append(moneyLiteral("§7Owners: §f" + owners + "\n"));
        body.append(moneyLiteral("§7Your role: §e" + account.getRole(player.getUUID())));

        source.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§eShared Account", body));
        return 1;
    }

    private static int handleSharedAccountDeposit(CommandSourceStack source, UUID accountId, String amountRaw) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!account.canDeposit(actor.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have deposit access for this account."));
            return 1;
        }
        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cDeposit failed."));
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder account = resolveAccount(source, accountId);
        if (account == null) {
            return 1;
        }
        if (!account.canWithdraw(actor.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have withdraw access for this account."));
            return 1;
        }
        BigDecimal amount = parsePositiveWholeAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cWithdraw failed (insufficient funds or frozen)."));
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }
        AccountHolder from = resolveAccount(source, fromAccountId);
        AccountHolder to = resolveAccount(source, toAccountId);
        if (from == null || to == null) {
            return 1;
        }
        if (!from.canWithdraw(actor.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have withdraw access on the source account."));
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
            source.sendSystemMessage(moneyLiteral("§cTransfer failed."));
            return 1;
        }
        notifyAllAccountMembers(source, from, "§bTransfer from shared account " + shortId(from.getAccountUUID()) + ": $" + amount.toPlainString());
        return 1;
    }

    private static int handleSharedAccountClose(CommandSourceStack source, UUID accountId) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can use this command."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cAccount not found."));
            return 1;
        }
        if (!account.canManage(actor.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou do not have close permission for this account."));
            return 1;
        }
        Bank bank = centralBank.getBank(account.getBankId());
        if (bank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank not found for this account."));
            return 1;
        }
        notifyAllAccountMembers(source, account, "§cShared account " + shortId(account.getAccountUUID()) + " was closed by " + actor.getName().getString());
        bank.RemoveAccount(account);
        source.sendSystemMessage(moneyLiteral("§aClosed shared account §f" + accountId));
        return 1;
    }

    private static AccountHolder resolveAccount(CommandSourceStack source, UUID accountId) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return null;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cAccount not found: " + accountId));
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
            source.sendSystemMessage(moneyLiteral("§cInvalid amount."));
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(moneyLiteral("§cAmount must be greater than zero."));
            return null;
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            source.sendSystemMessage(moneyLiteral("§cAmount must be a whole number."));
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
                member.sendSystemMessage(moneyLiteral(message));
            }
        }
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can send pay requests."));
            return 1;
        }

        if (payer.getUUID().equals(requester.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cYou cannot send a pay request to yourself."));
            return 1;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid amount."));
            return 1;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(moneyLiteral("§cAmount must be greater than zero."));
            return 1;
        }

        MinecraftServer server = source.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        AccountHolder destinationAccount = destinationAccountId == null
                ? findPreferredReceiverAccount(centralBank, requester.getUUID())
                : findAccountForPlayer(centralBank, requester.getUUID(), destinationAccountId);

        if (destinationAccount == null) {
            if (destinationAccountId == null) {
                source.sendSystemMessage(moneyLiteral(
                        "§cNo primary receiving account is set. Set one as primary or provide a destination account ID."
                ));
            } else {
                source.sendSystemMessage(moneyLiteral("§cDestination account ID is invalid or not yours."));
            }
            return 1;
        }

        var request = PayRequestManager.createRequest(
                requester.getUUID(),
                payer.getUUID(),
                destinationAccount.getAccountUUID(),
                amount
        );
        source.sendSystemMessage(moneyLiteral(
                "§aPay request sent to §e" + payer.getName().getString() + " §afor §6$" + amount.toPlainString()
                        + "§a. Destination: §f" + accountLabel(destinationAccount)
        ));
        sendPayRequestPrompt(payer, requester, request);
        return 1;
    }

    private static int handlePayRequestAccept(CommandSourceStack source, UUID requestId, UUID accountId) {
        ServerPlayer payer = source.getPlayer();
        if (payer == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can accept pay requests."));
            return 1;
        }

        MinecraftServer server = source.getServer();
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
            return 1;
        }

        PayRequestManager.PayRequest request = PayRequestManager.getRequest(requestId);
        if (request == null) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request no longer exists."));
            return 1;
        }
        if (!request.getPayerUUID().equals(payer.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request is not addressed to you."));
            return 1;
        }
        if (request.getStatus() != PayRequestManager.Status.PENDING) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request is already " + request.getStatus().name().toLowerCase() + "."));
            return 1;
        }
        if (PayRequestManager.isExpired(request)) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request has expired."));
            return 1;
        }

        AccountHolder senderAccount = accountId == null
                ? findPrimaryAccount(centralBank, payer.getUUID())
                : findAccountForPlayer(centralBank, payer.getUUID(), accountId);

        if (senderAccount == null) {
            source.sendSystemMessage(moneyLiteral("§cNo valid account selected. Choose an account first."));
            sendPayRequestAccountChoices(payer, request, "Choose an account to pay with:");
            return 1;
        }

        AccountHolder receiverAccount = findReceiverAccountForRequest(centralBank, request);
        if (receiverAccount == null) {
            source.sendSystemMessage(moneyLiteral("§cRequester destination account is unavailable."));
            ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
            if (requester != null) {
                requester.sendSystemMessage(moneyLiteral(
                        "§cYour pay request could not be completed because your destination account is unavailable."
                ));
            }
            return 1;
        }

        if (senderAccount.getAccountUUID().equals(receiverAccount.getAccountUUID())) {
            source.sendSystemMessage(moneyLiteral("§cCannot pay the same account."));
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
            source.sendSystemMessage(moneyLiteral("§cPayment failed. Check balance and account status."));
            ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
            if (requester != null) {
                requester.sendSystemMessage(moneyLiteral(
                        "§e" + payer.getName().getString() + " tried to accept your pay request, but payment failed."
                ));
            }
            return 1;
        }

        PayRequestManager.markAccepted(requestId);
        source.sendSystemMessage(moneyLiteral(
                "§aPaid §6$" + request.getAmount().toPlainString() + "§a to §e"
                        + resolvePlayerName(server, request.getRequesterUUID())
                        + "§a using account §7" + shortId(senderAccount.getAccountUUID())
        ));

        ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
        if (requester != null) {
            requester.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cOnly players can decline pay requests."));
            return 1;
        }

        MinecraftServer server = source.getServer();
        PayRequestManager.PayRequest request = PayRequestManager.getRequest(requestId);
        if (request == null) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request no longer exists."));
            return 1;
        }
        if (!request.getPayerUUID().equals(payer.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request is not addressed to you."));
            return 1;
        }
        if (request.getStatus() != PayRequestManager.Status.PENDING) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request is already " + request.getStatus().name().toLowerCase() + "."));
            return 1;
        }

        PayRequestManager.markDeclined(requestId);
        source.sendSystemMessage(moneyLiteral("§7You declined the pay request."));

        ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
        if (requester != null) {
            requester.sendSystemMessage(moneyLiteral(
                    "§c" + payer.getName().getString() + " declined your pay request for §6$" + request.getAmount().toPlainString() + "§c."
            ));
        }
        return 1;
    }

    private static int handlePayRequestChoose(CommandSourceStack source, UUID requestId) {
        ServerPlayer payer = source.getPlayer();
        if (payer == null) {
            source.sendSystemMessage(moneyLiteral("§cOnly players can choose account for pay requests."));
            return 1;
        }

        PayRequestManager.PayRequest request = PayRequestManager.getRequest(requestId);
        if (request == null) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request no longer exists."));
            return 1;
        }
        if (!request.getPayerUUID().equals(payer.getUUID())) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request is not addressed to you."));
            return 1;
        }
        if (request.getStatus() != PayRequestManager.Status.PENDING || PayRequestManager.isExpired(request)) {
            source.sendSystemMessage(moneyLiteral("§cThis pay request is no longer pending."));
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
            payer.sendSystemMessage(moneyLiteral(
                    "§6Pay Request: §e" + requester.getName().getString() + " §7requests §6$"
                            + request.getAmount().toPlainString() + "§7.\n"
                            + "§7Destination: §f" + destinationLabel
            ));
            sendPayRequestAccountChoices(payer, request, "No primary account set. Choose account to accept:");
            return;
        }

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7From: §e" + requester.getName().getString() + "\n"));
        body.append(moneyLiteral("§7Amount: §6$" + request.getAmount().toPlainString() + "\n"));
        body.append(moneyLiteral("§7Destination: §f" + destinationLabel + "\n"));
        body.append(moneyLiteral("§7Primary account: §f" + accountLabel(primary) + "\n\n"));

        String requestId = request.getRequestId().toString();
        body.append(clickAction("[Accept]", ChatFormatting.GREEN, "/ubs_payrequest accept " + requestId, "Accept with primary account"));
        body.append(moneyLiteral(" "));
        body.append(clickAction("[Decline]", ChatFormatting.RED, "/ubs_payrequest decline " + requestId, "Decline this request"));
        body.append(moneyLiteral(" "));
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
        body.append(moneyLiteral("§7" + titleLine + "\n"));
        body.append(moneyLiteral("§7Requested amount: §6$" + request.getAmount().toPlainString() + "\n\n"));
        AccountHolder destination = findReceiverAccountForRequest(centralBank, request);
        body.append(moneyLiteral("§7Destination: §f" + (destination == null ? "Unavailable" : accountLabel(destination)) + "\n\n"));

        if (payerAccounts.isEmpty()) {
            body.append(moneyLiteral("§cYou have no accounts available.\n"));
        } else {
            for (AccountHolder account : payerAccounts) {
                String buttonLabel = "[" + account.getAccountType().label + " $" + account.getBalance().toPlainString() + "]";
                String command = "/ubs_payrequest accept " + request.getRequestId() + " " + account.getAccountUUID();
                body.append(clickAction(buttonLabel, ChatFormatting.AQUA, command, "Pay using " + accountLabel(account)));
                body.append(moneyLiteral(" §7" + shortId(account.getAccountUUID()) + "\n"));
            }
        }

        body.append(moneyLiteral("\n"));
        body.append(clickAction("[Decline]", ChatFormatting.RED,
                "/ubs_payrequest decline " + request.getRequestId(),
                "Decline this request"));

        payer.sendSystemMessage(ubsMessage(ChatFormatting.AQUA, "§bPay Request Account Choice", body));
    }

    private static MutableComponent clickAction(String label,
                                                ChatFormatting color,
                                                String runCommand,
                                                String hoverText) {
        return moneyLiteral(label).setStyle(
                Style.EMPTY
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, runCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral(hoverText)))
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

    public static void clearHudMonitorOverride(UUID playerId) {
        if (playerId == null) {
            return;
        }
        HUD_ACCOUNT_MONITOR_OVERRIDES.remove(playerId);
    }

    public static void clearHudMonitorOverridesForMissingPlayers(java.util.Set<UUID> onlinePlayers) {
        if (onlinePlayers == null) {
            return;
        }
        HUD_ACCOUNT_MONITOR_OVERRIDES.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }

    public static void clearHudStateForMissingPlayers(java.util.Set<UUID> onlinePlayers) {
        if (onlinePlayers == null) {
            return;
        }
        HUD_ENABLED_OVERRIDES.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }

    public static HudStatePayload buildHudStatePayload(CentralBank centralBank, UUID playerId) {
        if (playerId == null) {
            return new HudStatePayload("", false);
        }
        boolean toggled = isHudEnabled(playerId);
        AccountHolder target = centralBank == null ? null : resolveHudTargetAccount(centralBank, playerId);
        boolean visible = toggled && target != null;
        String balance = target == null ? "" : target.getBalance().toPlainString();
        return new HudStatePayload(balance, visible);
    }

    private static AccountHolder resolveHudTargetAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        UUID overrideId = HUD_ACCOUNT_MONITOR_OVERRIDES.get(playerId);
        if (overrideId != null) {
            AccountHolder override = centralBank.SearchForAccountByAccountId(overrideId);
            if (override != null && playerId.equals(override.getPlayerUUID())) {
                return override;
            }
            HUD_ACCOUNT_MONITOR_OVERRIDES.remove(playerId, overrideId);
        }
        return findPrimaryAccount(centralBank, playerId);
    }
}
