package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.authlib.GameProfile;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.loan.LoanService;
import net.austizz.ultimatebankingsystem.payments.ScheduledPayment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSAdminCommands {
    private static final int ADMIN_PERMISSION_LEVEL = 3;
    private static final UUID ADMIN_SYSTEM_ID = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:admin-system".getBytes(StandardCharsets.UTF_8)
    );
    private static final DateTimeFormatter ADMIN_TX_TIME_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final DateTimeFormatter IMPORT_TX_TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ConcurrentHashMap<UUID, Long> CHARTER_FEE_WAIVERS = new ConcurrentHashMap<>();

    private static final class ImportStats {
        int created;
        int updated;
        int failed;
        int importedHistoryEntries;
        final List<String> errors = new ArrayList<>();
    }

    private static Component ubsPanel(ChatFormatting accentColor, String title, Component body) {
        return Component.literal("§6§lUltimate Banking System §7- ")
                .append(Component.literal(title).withStyle(accentColor))
                .append(Component.literal("\n§8────────────────────────\n"))
                .append(body);
    }

    private static boolean requireAdminPermission(CommandSourceStack source) {
        if (source.getPlayer() != null && !source.getPlayer().hasPermissions(ADMIN_PERMISSION_LEVEL)) {
            source.sendSystemMessage(Component.literal("§4You do not have permission to perform this action."));
            return false;
        }
        return true;
    }

    public static boolean consumeCharterFeeWaiver(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return CHARTER_FEE_WAIVERS.remove(playerId) != null;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildUbsRoot());
        event.getDispatcher().register(Commands.literal("bank").then(buildAdminLiteral()));
        event.getDispatcher().register(buildCentralBankRoot());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildUbsRoot() {
        return Commands.literal("ubs")
                .then(Commands.literal("centralbank")
                        .executes(context -> showCentralBankPanel(context.getSource()))
                        .then(Commands.literal("interest")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("rate", StringArgumentType.greedyString())
                                                .executes(context -> setCentralBankInterestRate(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "rate")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("bank")
                        .then(Commands.literal("save")
                                .executes(context -> saveBankData(context.getSource()))
                        )
                        .then(Commands.literal("rename")
                                .then(Commands.argument("New Name", StringArgumentType.greedyString())
                                        .executes(context -> renameCentralBank(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "New Name")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("money")
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                .executes(context -> depositToAccount(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "accountId"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                .executes(context -> withdrawFromAccount(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "accountId"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))
                                        )
                                )
                        )
                )
                .then(buildAdminLiteral());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCentralBankRoot() {
        return Commands.literal("centralbank")
                .executes(context -> centralBankRateShow(context.getSource()))
                .then(Commands.literal("rate")
                        .executes(context -> centralBankRateShow(context.getSource()))
                        .then(Commands.literal("set")
                                .then(Commands.argument("rate", StringArgumentType.word())
                                        .executes(context -> centralBankRateSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "rate")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("opm")
                        .then(Commands.literal("inject")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> centralBankOpenMarketOperation(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "amount"),
                                                true
                                        ))
                                )
                        )
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> centralBankOpenMarketOperation(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "amount"),
                                                false
                                        ))
                                )
                        )
                        .then(Commands.literal("history")
                                .executes(context -> centralBankOpenMarketHistory(context.getSource()))
                        )
                )
                .then(Commands.literal("audit")
                        .executes(context -> centralBankAudit(context.getSource(), ""))
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> centralBankAudit(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("report")
                        .executes(context -> centralBankReport(context.getSource(), false))
                        .then(Commands.literal("history")
                                .executes(context -> centralBankReport(context.getSource(), true))
                        )
                )
                .then(Commands.literal("ledger")
                        .executes(context -> centralBankLedger(context.getSource(), false))
                        .then(Commands.literal("suspense")
                                .executes(context -> centralBankLedger(context.getSource(), true))
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminLiteral() {
        return Commands.literal("admin")
                .then(Commands.literal("view")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> adminViewPlayer(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")
                                ))
                        )
                )
                .then(Commands.literal("freeze")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> adminFreezePlayer(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player"),
                                        ""
                                ))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> adminFreezePlayer(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "reason")
                                        ))
                                )
                        )
                        .then(Commands.literal("account")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .executes(context -> adminFreezeAccount(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "accountId"),
                                                ""
                                        ))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> adminFreezeAccount(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "accountId"),
                                                        StringArgumentType.getString(context, "reason")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("unfreeze")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> adminUnfreezePlayer(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")
                                ))
                        )
                        .then(Commands.literal("account")
                                .then(Commands.argument("accountId", UuidArgument.uuid())
                                        .executes(context -> adminUnfreezeAccount(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "accountId")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("report")
                        .executes(context -> adminEconomyReport(context.getSource()))
                )
                .then(Commands.literal("loan")
                        .then(Commands.literal("pending")
                                .executes(context -> adminListPendingLoanApprovals(context.getSource()))
                        )
                        .then(Commands.literal("approve")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminApproveLoan(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("deny")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminDenyLoan(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                ""
                                        ))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> adminDenyLoan(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "reason")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("schedule")
                        .then(Commands.literal("list")
                                .executes(context -> adminListSchedules(context.getSource()))
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("paymentId", UuidArgument.uuid())
                                        .executes(context -> adminRemoveSchedule(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "paymentId")
                                        ))
                                )
                        )
                        .then(Commands.literal("add")
                                .then(Commands.argument("sourceAccountId", UuidArgument.uuid())
                                        .then(Commands.argument("targetAccountId", UuidArgument.uuid())
                                                .then(Commands.argument("amount", StringArgumentType.word())
                                                        .then(Commands.argument("frequencyTicks", StringArgumentType.word())
                                                                .executes(context -> adminAddSchedule(
                                                                        context.getSource(),
                                                                        UuidArgument.getUuid(context, "sourceAccountId"),
                                                                        UuidArgument.getUuid(context, "targetAccountId"),
                                                                        StringArgumentType.getString(context, "amount"),
                                                                        StringArgumentType.getString(context, "frequencyTicks")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("import")
                        .then(Commands.literal("csv")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(context -> adminImportCsv(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "path")
                                        ))
                                )
                        )
                        .then(Commands.literal("essentialsx")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(context -> adminImportEssentialsX(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "path")
                                        ))
                                )
                        )
                        .then(Commands.literal("cmi")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(context -> adminImportCMI(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "path")
                                        ))
                                )
                        )
                        .then(Commands.literal("iconomy")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(context -> adminImportIconomy(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "path")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("applications")
                        .executes(context -> adminListBankApplications(context.getSource()))
                        .then(Commands.literal("approve")
                                .then(Commands.argument("applicationId", UuidArgument.uuid())
                                        .executes(context -> adminApproveBankApplication(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "applicationId")
                                        ))
                                )
                        )
                        .then(Commands.literal("deny")
                                .then(Commands.argument("applicationId", UuidArgument.uuid())
                                        .executes(context -> adminDenyBankApplication(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "applicationId"),
                                                ""
                                        ))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> adminDenyBankApplication(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "applicationId"),
                                                        StringArgumentType.getString(context, "reason")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("appeals")
                        .executes(context -> adminListAppeals(context.getSource()))
                )
                .then(Commands.literal("appeal")
                        .then(Commands.argument("appealId", UuidArgument.uuid())
                                .then(Commands.literal("approve")
                                        .executes(context -> adminReviewAppeal(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "appealId"),
                                                true,
                                                ""
                                        ))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> adminReviewAppeal(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "appealId"),
                                                        true,
                                                        StringArgumentType.getString(context, "reason")
                                                ))
                                        )
                                )
                                .then(Commands.literal("deny")
                                        .executes(context -> adminReviewAppeal(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "appealId"),
                                                false,
                                                ""
                                        ))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> adminReviewAppeal(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "appealId"),
                                                        false,
                                                        StringArgumentType.getString(context, "reason")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("reserve")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankReserve(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("compliance")
                        .executes(context -> adminBankCompliance(context.getSource(), ""))
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankCompliance(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("audit")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankAudit(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("suspend")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankSuspend(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName"),
                                        ""
                                ))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> adminBankSuspend(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "bankName"),
                                                StringArgumentType.getString(context, "reason")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("unsuspend")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankUnsuspend(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("unlock")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankUnlock(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("bankrun")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankRunStatus(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("revoke")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminBankRevoke(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName"),
                                        ""
                                ))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> adminBankRevoke(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "bankName"),
                                                StringArgumentType.getString(context, "reason")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("rateexempt")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminToggleRateExempt(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("setcap")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> adminSetDailyCapOverride(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "bankName"),
                                                StringArgumentType.getString(context, "amount")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("waivefee")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> adminWaiveCharterFee(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")
                                ))
                        )
                )
                .then(Commands.literal("deferrenwal")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminDeferLicenseRenewal(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("deferrenewal")
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                                .executes(context -> adminDeferLicenseRenewal(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bankName")
                                ))
                        )
                )
                .then(Commands.literal("flags")
                        .executes(context -> adminListFlags(context.getSource()))
                );
    }

    private static int showCentralBankPanel(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        int bankCount = centralBank.getBanks() != null ? centralBank.getBanks().size() : 0;

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Name: §e" + centralBank.getBankName() + "\n"));
        body.append(Component.literal("§7Bank ID: §f" + centralBank.getBankId() + "\n"));
        body.append(Component.literal("§7Reserve: §a" + centralBank.getBankReserve() + "\n"));
        body.append(Component.literal("§7Interest Rate: §e" + centralBank.getInterestRate() + "\n"));
        body.append(Component.literal("§7Registered Banks: §b" + bankCount + "\n"));

        body.append(Component.literal("\n§7Actions:\n"));
        body.append(Component.literal("§f§l[§aSave§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ubs bank save"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to save bank data")))));
        body.append(Component.literal(" "));
        body.append(Component.literal("§f§l[§eRename§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs bank rename "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest /ubs bank rename <name>")))));
        body.append(Component.literal("\n"));
        body.append(Component.literal("§f§l[§6Set Interest§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs centralbank interest set "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest /ubs centralbank interest set <rate>")))));
        body.append(Component.literal("\n"));
        body.append(Component.literal("§f§l[§2Deposit§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs money deposit <accountId> <amount>"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest deposit to an account")))));
        body.append(Component.literal(" "));
        body.append(Component.literal("§f§l[§cWithdraw§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs money withdraw <accountId> <amount>"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest withdraw from an account")))));
        body.append(Component.literal("\n"));
        body.append(Component.literal("§f§l[§bAdmin View§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs admin view "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suggest /ubs admin view <player>")))));

        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "§eCentral Bank", body));
        return 1;
    }

    private static int setCentralBankInterestRate(CommandSourceStack source, String rateStr) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        double rate;
        try {
            rate = Double.parseDouble(rateStr);
        } catch (NumberFormatException e) {
            source.sendSystemMessage(Component.literal("§cThe rate '§e" + rateStr + "§c' is not a valid number."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        double before = centralBank.getInterestRate();
        centralBank.setInterestRate(rate);
        double after = centralBank.getInterestRate();

        if (Double.compare(before, after) == 0 && Double.compare(before, rate) != 0) {
            source.sendSystemMessage(Component.literal(
                    "§cInterest rate not changed. Rate must be within allowed range. Current: §e" + before
            ));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§aCentral Bank interest rate updated: §e" + before + " §7-> §e" + after
        ));
        return 1;
    }

    private static int centralBankRateShow(CommandSourceStack source) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        double rate = centralBank.getFederalFundsRate();
        double floor = rate * Config.SAVINGS_RATE_FLOOR_MULTIPLIER.get();
        double ceiling = rate * Config.SAVINGS_RATE_CEILING_MULTIPLIER.get();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Federal Funds Rate: §e" + rate + "%\n"));
        body.append(Component.literal("§7Implied Savings Floor: §f" + floor + "%\n"));
        body.append(Component.literal("§7Implied Savings Ceiling: §f" + ceiling + "%\n"));
        body.append(Component.literal("§7Allowed FFR Range: §f" + Config.MIN_FEDERAL_FUNDS_RATE.get()
                + "% §7to §f" + Config.MAX_FEDERAL_FUNDS_RATE.get() + "%"));
        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "§eCentral Bank Rate", body));
        return 1;
    }

    private static int centralBankRateSet(CommandSourceStack source, String rateRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        double requested;
        try {
            requested = Double.parseDouble(rateRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid rate: " + rateRaw));
            return 1;
        }

        double previous = centralBank.getFederalFundsRate();
        if (!centralBank.setFederalFundsRate(requested)) {
            source.sendSystemMessage(Component.literal(
                    "§cRate out of bounds. Allowed range: "
                            + Config.MIN_FEDERAL_FUNDS_RATE.get()
                            + "% to " + Config.MAX_FEDERAL_FUNDS_RATE.get() + "%"
            ));
            return 1;
        }

        double next = centralBank.getFederalFundsRate();
        source.sendSystemMessage(Component.literal(
                "§aFederal Funds Rate updated: §e" + previous + "% §7-> §e" + next + "%"
        ));
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6[UBS] Federal Funds Rate updated to §e" + next + "%§6 by " + source.getTextName()),
                false
        );
        return 1;
    }

    private static int centralBankOpenMarketOperation(CommandSourceStack source, String amountRaw, boolean inject) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        BigDecimal amount = parsePositiveAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        BigDecimal before = centralBank.getDeclaredReserve();
        BigDecimal after = inject ? before.add(amount) : before.subtract(amount);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            source.sendSystemMessage(Component.literal("§cOperation rejected: Central Bank reserve cannot go negative."));
            return 1;
        }

        centralBank.setReserve(after);
        UUID opId = UUID.randomUUID();
        CompoundTag op = new CompoundTag();
        op.putUUID("id", opId);
        op.putLong("timestampMillis", System.currentTimeMillis());
        op.putString("type", inject ? "OMO_INJECT" : "OMO_WITHDRAW");
        op.putString("amount", amount.toPlainString());
        op.putString("actor", source.getTextName());
        op.putString("reserveBefore", before.toPlainString());
        op.putString("reserveAfter", after.toPlainString());
        centralBank.getOpenMarketOperations().put(opId, op);
        trimTagMap(centralBank.getOpenMarketOperations(), Math.max(1, Config.OMO_HISTORY_LIMIT.get()));
        BankManager.markDirty();

        source.sendSystemMessage(Component.literal(
                "§aOpen market operation executed: §f" + op.getString("type")
                        + " §6$" + amount.toPlainString()
                        + " §7(CB reserve " + before.toPlainString() + " -> " + after.toPlainString() + ")"
        ));
        return 1;
    }

    private static int centralBankOpenMarketHistory(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        List<CompoundTag> entries = centralBank.getOpenMarketOperations().values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Open Market Operations: §b" + entries.size() + "\n\n"));
        if (entries.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (int i = Math.max(0, entries.size() - Config.OMO_HISTORY_LIMIT.get()); i < entries.size(); i++) {
                CompoundTag tag = entries.get(i);
                body.append(Component.literal(
                        "§8- §f" + tag.getString("type")
                                + " §6$" + readDecimalTag(tag, "amount").toPlainString()
                                + " §7by §f" + tag.getString("actor")
                                + " §7at §f" + tag.getLong("timestampMillis") + "\n"
                ));
            }
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.AQUA, "§bOpen Market History", body));
        return 1;
    }

    private static int centralBankAudit(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        List<Bank> banks;
        String bankName = bankNameRaw == null ? "" : bankNameRaw.trim();
        if (bankName.isBlank()) {
            banks = centralBank.getBanks().values().stream()
                    .filter(bank -> !bank.getBankId().equals(centralBank.getBankId()))
                    .sorted(Comparator.comparing(Bank::getBankName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } else {
            Bank bank = resolveBankByName(centralBank, bankName);
            if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
                source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
                return 1;
            }
            banks = List.of(bank);
        }

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Audited Banks: §b" + banks.size() + "\n\n"));
        for (Bank bank : banks) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            BigDecimal deposits = bank.getTotalDeposits();
            BigDecimal reserve = bank.getDeclaredReserve();
            BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                    ? reserve.divide(deposits, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.valueOf(100);
            long breachStart = metadata.contains("reserveBreachStartTick") ? metadata.getLong("reserveBreachStartTick") : -1L;
            body.append(Component.literal(
                    "§8- §e" + bank.getBankName()
                            + " §7status §f" + getBankStatus(centralBank, bank)
                            + " §7reserve ratio §e" + ratio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"
                            + "  §7reserve §a$" + reserve.toPlainString()
                            + " §7deposits §6$" + deposits.toPlainString()
                            + " §7breachTick §f" + breachStart + "\n"
            ));
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.YELLOW, "§eReserve Audit", body));
        return 1;
    }

    private static int centralBankReport(CommandSourceStack source, boolean history) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        long now = System.currentTimeMillis();
        BigDecimal totalCirculation = BigDecimal.ZERO;
        BigDecimal totalReserves = centralBank.getDeclaredReserve();
        BigDecimal totalOutstandingLoans = BigDecimal.ZERO;
        BigDecimal reserveRatioTotal = BigDecimal.ZERO;
        int reserveRatioCount = 0;
        int warningOrRestricted = 0;
        int activeBanks = 0;

        for (Bank bank : centralBank.getBanks().values()) {
            if (!bank.getBankId().equals(centralBank.getBankId())) {
                activeBanks++;
            }
            for (AccountHolder account : bank.getBankAccounts().values()) {
                totalCirculation = totalCirculation.add(account.getBalance());
                for (var loan : account.getActiveLoans().values()) {
                    if (loan != null && !loan.isDefaulted()) {
                        totalOutstandingLoans = totalOutstandingLoans.add(loan.getRemainingBalance());
                    }
                }
            }
            if (bank.getBankId().equals(centralBank.getBankId())) {
                continue;
            }
            BigDecimal deposits = bank.getTotalDeposits();
            BigDecimal reserve = bank.getDeclaredReserve();
            BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                    ? reserve.divide(deposits, 6, RoundingMode.HALF_EVEN)
                    : BigDecimal.ONE;
            reserveRatioTotal = reserveRatioTotal.add(ratio);
            reserveRatioCount++;
            String status = getBankStatus(centralBank, bank);
            if ("WARNING".equals(status) || "RESTRICTED".equals(status) || "SUSPENDED".equals(status)) {
                warningOrRestricted++;
            }
            totalReserves = totalReserves.add(reserve);
        }

        BigDecimal avgReserveRatio = reserveRatioCount == 0
                ? BigDecimal.ZERO
                : reserveRatioTotal.divide(BigDecimal.valueOf(reserveRatioCount), 6, RoundingMode.HALF_EVEN)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal netOmo = BigDecimal.ZERO;
        for (CompoundTag op : centralBank.getOpenMarketOperations().values()) {
            BigDecimal value = readDecimalTag(op, "amount");
            if ("OMO_INJECT".equalsIgnoreCase(op.getString("type"))) {
                netOmo = netOmo.add(value);
            } else if ("OMO_WITHDRAW".equalsIgnoreCase(op.getString("type"))) {
                netOmo = netOmo.subtract(value);
            }
        }

        long cutoff = now - (24L * 60L * 60L * 1000L);
        long settlements24h = centralBank.getSettlementLedger().values().stream()
                .filter(tag -> tag.getLong("timestampMillis") >= cutoff)
                .count();

        CompoundTag snapshot = new CompoundTag();
        snapshot.putLong("timestampMillis", now);
        snapshot.putString("totalCirculation", totalCirculation.toPlainString());
        snapshot.putString("totalReserves", totalReserves.toPlainString());
        snapshot.putInt("activeBanks", activeBanks);
        snapshot.putString("avgReserveRatio", avgReserveRatio.toPlainString());
        snapshot.putInt("warningOrRestricted", warningOrRestricted);
        snapshot.putString("totalOutstandingLoans", totalOutstandingLoans.toPlainString());
        snapshot.putDouble("federalFundsRate", centralBank.getFederalFundsRate());
        snapshot.putString("netOmo", netOmo.toPlainString());
        snapshot.putLong("settlements24h", settlements24h);
        centralBank.getReportSnapshots().put(UUID.randomUUID(), snapshot);
        trimTagMap(centralBank.getReportSnapshots(), 200);

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Total Circulation: §a$" + totalCirculation.toPlainString() + "\n"));
        body.append(Component.literal("§7Total Central+Bank Reserves: §a$" + totalReserves.toPlainString() + "\n"));
        body.append(Component.literal("§7Active Player Banks: §b" + activeBanks + "\n"));
        body.append(Component.literal("§7Avg Reserve Ratio: §e" + avgReserveRatio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(Component.literal("§7Banks WARNING/RESTRICTED/SUSPENDED: §c" + warningOrRestricted + "\n"));
        body.append(Component.literal("§7Outstanding Loans: §6$" + totalOutstandingLoans.toPlainString() + "\n"));
        body.append(Component.literal("§7Federal Funds Rate: §e" + centralBank.getFederalFundsRate() + "%\n"));
        body.append(Component.literal("§7Net OMO Since Start: §f$" + netOmo.toPlainString() + "\n"));
        body.append(Component.literal("§7Inter-bank Settlements (24h): §f" + settlements24h + "\n"));

        if (history) {
            body.append(Component.literal("\n§7Recent Snapshots:\n"));
            List<CompoundTag> snapshots = centralBank.getReportSnapshots().values().stream()
                    .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                    .toList();
            int start = Math.max(0, snapshots.size() - 10);
            for (int i = start; i < snapshots.size(); i++) {
                CompoundTag snap = snapshots.get(i);
                body.append(Component.literal(
                        "§8- §f" + snap.getLong("timestampMillis")
                                + " §7circulation §a$" + readDecimalTag(snap, "totalCirculation").toPlainString()
                                + " §7rate §e" + snap.getDouble("federalFundsRate") + "%\n"
                ));
            }
        }

        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "§eCentral Bank Report", body));
        return 1;
    }

    private static int centralBankLedger(CommandSourceStack source, boolean suspense) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        var map = suspense ? centralBank.getSettlementSuspense() : centralBank.getSettlementLedger();
        List<CompoundTag> entries = map.values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Entries: §b" + entries.size() + "\n\n"));
        int limit = Math.max(1, Config.CLEARING_LEDGER_LIMIT.get());
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            CompoundTag tag = entries.get(i);
            body.append(Component.literal(
                    "§8- §f" + shortId(readUuidTag(tag, "id"))
                            + " §7from §f" + shortId(readUuidTag(tag, "fromBankId"))
                            + " §7to §f" + shortId(readUuidTag(tag, "toBankId"))
                            + " §7amount §6$" + readDecimalTag(tag, "amount").toPlainString()
                            + " §7reason §f" + tag.getString("reason")
                            + "\n"
            ));
        }

        source.sendSystemMessage(ubsPanel(
                suspense ? ChatFormatting.RED : ChatFormatting.AQUA,
                suspense ? "§cSettlement Suspense" : "§bSettlement Ledger",
                body
        ));
        return 1;
    }

    private static int adminListBankApplications(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        List<CompoundTag> apps = centralBank.getBankApplications().values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("createdMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Applications: §b" + apps.size() + "\n\n"));
        if (apps.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (CompoundTag app : apps) {
                UUID id = readUuidTag(app, "id");
                UUID applicant = readUuidTag(app, "applicant");
                body.append(Component.literal(
                        "§8- §f" + id + "\n"
                                + "  §7Applicant: §f" + resolvePlayerName(source.getServer(), applicant) + "\n"
                                + "  §7Bank: §e" + app.getString("bankName") + "\n"
                                + "  §7Model: §f" + app.getString("ownershipModel") + "\n"
                                + "  §7Status: §f" + app.getString("status") + "\n"
                ));
            }
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.YELLOW, "§eBank Applications", body));
        return 1;
    }

    private static int adminApproveBankApplication(CommandSourceStack source, UUID applicationId) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        CompoundTag app = centralBank.getBankApplications().get(applicationId);
        if (app == null) {
            source.sendSystemMessage(Component.literal("§cApplication not found: " + applicationId));
            return 1;
        }
        if (!"PENDING".equalsIgnoreCase(app.getString("status"))) {
            source.sendSystemMessage(Component.literal("§cApplication is not pending."));
            return 1;
        }

        UUID applicantId = readUuidTag(app, "applicant");
        if (applicantId == null) {
            source.sendSystemMessage(Component.literal("§cApplication has no applicant."));
            return 1;
        }
        String bankName = app.getString("bankName");
        if (resolveBankByName(centralBank, bankName) != null) {
            source.sendSystemMessage(Component.literal("§cA bank with this name already exists."));
            return 1;
        }

        AccountHolder funding = null;
        UUID fundingId = readUuidTag(app, "fundingAccountId");
        if (fundingId != null) {
            funding = centralBank.SearchForAccountByAccountId(fundingId);
        }
        if (funding == null) {
            funding = findPrimaryAccount(centralBank, applicantId);
        }
        if (funding == null) {
            source.sendSystemMessage(Component.literal("§cApplicant has no funding account available."));
            return 1;
        }

        BigDecimal creationFee = readDecimalTag(app, "creationFee");
        BigDecimal charterFee = readDecimalTag(app, "charterFee");
        BigDecimal totalFee = creationFee.add(charterFee);
        if (funding.getBalance().compareTo(totalFee) < 0) {
            source.sendSystemMessage(Component.literal("§cApplicant cannot afford required fees $" + totalFee.toPlainString()));
            return 1;
        }

        if (!funding.RemoveBalance(totalFee)) {
            source.sendSystemMessage(Component.literal("§cFailed to deduct applicant fees."));
            return 1;
        }
        centralBank.setReserve(centralBank.getDeclaredReserve().add(totalFee));

        Bank created = new Bank(
                UUID.randomUUID(),
                bankName,
                BigDecimal.ZERO,
                Config.DEFAULT_SERVER_INTEREST_RATE.get(),
                applicantId
        );
        centralBank.addBank(created);

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(created.getBankId());
        metadata.putString("status", "ACTIVE");
        metadata.putString("ownershipModel", app.getString("ownershipModel"));
        metadata.putString("motto", "");
        metadata.putString("color", "#55AAFF");
        metadata.putLong("createdMillis", System.currentTimeMillis());
        metadata.putUUID("founder", applicantId);
        metadata.putString("employees", "");
        metadata.putString("loanProducts", "");
        String ownershipModel = app.getString("ownershipModel");
        if ("ROLE_BASED".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("roles", applicantId + "=FOUNDER");
        } else if ("PERCENTAGE_SHARES".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("shares", applicantId + "=100.00");
        } else if ("FIXED_COFOUNDERS".equalsIgnoreCase(ownershipModel)) {
            metadata.putString("cofounders", applicantId.toString());
        }
        centralBank.putBankMetadata(created.getBankId(), metadata);

        AccountHolder ownerAccount = new AccountHolder(
                applicantId,
                BigDecimal.ZERO,
                AccountTypes.CheckingAccount,
                "",
                created.getBankId(),
                null
        );
        created.AddAccount(ownerAccount);
        if (findPrimaryAccount(centralBank, applicantId) == null) {
            ownerAccount.setPrimaryAccount(true);
        }

        app.putString("status", "APPROVED");
        app.putLong("reviewedMillis", System.currentTimeMillis());
        app.putString("reviewedBy", source.getTextName());
        centralBank.getBankApplications().put(applicationId, app);
        BankManager.markDirty();

        source.sendSystemMessage(Component.literal(
                "§aApproved application " + applicationId + " and created bank §e" + bankName
        ));
        ServerPlayer applicantOnline = source.getServer().getPlayerList().getPlayer(applicantId);
        if (applicantOnline != null) {
            applicantOnline.sendSystemMessage(Component.literal(
                    "§aYour bank application was approved. Bank created: §e" + bankName
            ));
        }
        return 1;
    }

    private static int adminDenyBankApplication(CommandSourceStack source, UUID applicationId, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        CompoundTag app = centralBank.getBankApplications().get(applicationId);
        if (app == null) {
            source.sendSystemMessage(Component.literal("§cApplication not found: " + applicationId));
            return 1;
        }

        app.putString("status", "DENIED");
        app.putString("denialReason", reason == null ? "" : reason.trim());
        app.putLong("reviewedMillis", System.currentTimeMillis());
        app.putString("reviewedBy", source.getTextName());
        centralBank.getBankApplications().put(applicationId, app);
        BankManager.markDirty();

        UUID applicantId = readUuidTag(app, "applicant");
        ServerPlayer applicantOnline = applicantId == null ? null : source.getServer().getPlayerList().getPlayer(applicantId);
        if (applicantOnline != null) {
            applicantOnline.sendSystemMessage(Component.literal(
                    "§cYour bank application was denied."
                            + ((reason == null || reason.isBlank()) ? "" : " Reason: " + reason)
                            + " §7You can appeal with /bank appeal <message>."
            ));
        }
        source.sendSystemMessage(Component.literal("§eApplication denied: " + applicationId));
        return 1;
    }

    private static int adminListAppeals(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        List<CompoundTag> appeals = centralBank.getBankAppeals().values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("createdMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Appeals: §b" + appeals.size() + "\n\n"));
        for (CompoundTag appeal : appeals) {
            UUID id = readUuidTag(appeal, "id");
            UUID playerId = readUuidTag(appeal, "playerId");
            body.append(Component.literal(
                    "§8- §f" + id + "\n"
                            + "  §7Player: §f" + resolvePlayerName(source.getServer(), playerId) + "\n"
                            + "  §7Status: §f" + appeal.getString("status") + "\n"
                            + "  §7Message: §f" + appeal.getString("message") + "\n"
            ));
        }
        if (appeals.isEmpty()) {
            body.append(Component.literal("§8- none"));
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.LIGHT_PURPLE, "§dAppeals Inbox", body));
        return 1;
    }

    private static int adminReviewAppeal(CommandSourceStack source, UUID appealId, boolean approve, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        CompoundTag appeal = centralBank.getBankAppeals().get(appealId);
        if (appeal == null) {
            source.sendSystemMessage(Component.literal("§cAppeal not found: " + appealId));
            return 1;
        }

        appeal.putString("status", approve ? "APPROVED" : "DENIED");
        appeal.putString("reviewReason", reason == null ? "" : reason.trim());
        appeal.putLong("reviewedMillis", System.currentTimeMillis());
        appeal.putString("reviewedBy", source.getTextName());
        centralBank.getBankAppeals().put(appealId, appeal);
        BankManager.markDirty();

        UUID playerId = readUuidTag(appeal, "playerId");
        ServerPlayer player = playerId == null ? null : source.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    (approve ? "§aYour bank appeal was approved." : "§cYour bank appeal was denied.")
                            + ((reason == null || reason.isBlank()) ? "" : " Reason: " + reason)
            ));
        }
        source.sendSystemMessage(Component.literal("§aAppeal " + appealId + " reviewed: " + (approve ? "APPROVED" : "DENIED")));
        return 1;
    }

    private static int adminBankReserve(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal reserve = bank.getDeclaredReserve();
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                ? reserve.divide(deposits, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);
        source.sendSystemMessage(Component.literal(
                "§7Bank: §e" + bank.getBankName()
                        + "\n§7Status: §f" + getBankStatus(centralBank, bank)
                        + "\n§7Reserve: §a$" + reserve.toPlainString()
                        + "\n§7Deposits: §6$" + deposits.toPlainString()
                        + "\n§7Reserve Ratio: §e" + ratio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%"
                        + "\n§7Minimum Reserve: §f$" + minReserve.toPlainString()
                        + "\n§7Daily Cap Override: §f" + (metadata.getString("dailyCapOverride").isBlank()
                        ? "(none)"
                        : metadata.getString("dailyCapOverride"))
        ));
        return 1;
    }

    private static int adminBankCompliance(CommandSourceStack source, String bankNameRaw) {
        return centralBankAudit(source, bankNameRaw);
    }

    private static int adminBankAudit(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(Component.literal("§7Status: §f" + getBankStatus(centralBank, bank) + "\n"));
        body.append(Component.literal("§7Owner: §f" + resolvePlayerName(source.getServer(), bank.getBankOwnerId()) + "\n"));
        body.append(Component.literal("§7Metadata:\n"));
        for (String key : metadata.getAllKeys()) {
            body.append(Component.literal("§8- §7" + key + ": §f" + metadata.get(key) + "\n"));
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.AQUA, "§bBank Audit", body));
        return 1;
    }

    private static int adminBankSuspend(CommandSourceStack source, String bankNameRaw, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "SUSPENDED");
        metadata.putString("suspendReason", reason == null ? "" : reason.trim());
        metadata.putLong("suspendedAtMillis", System.currentTimeMillis());
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal("§eSuspended bank: " + bank.getBankName()));
        notifyBankOwner(source.getServer(), bank, "§cYour bank has been suspended."
                + ((reason == null || reason.isBlank()) ? "" : " Reason: " + reason));
        return 1;
    }

    private static int adminBankUnsuspend(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "ACTIVE");
        metadata.remove("suspendReason");
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal("§aUnsuspended bank: " + bank.getBankName()));
        notifyBankOwner(source.getServer(), bank, "§aYour bank suspension has been lifted.");
        return 1;
    }

    private static int adminBankUnlock(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "ACTIVE");
        metadata.remove("lockdownUntilTick");
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal("§aLifted bank-run lockdown for " + bank.getBankName()));
        notifyBankOwner(source.getServer(), bank, "§aYour bank lockdown was lifted by an admin.");
        return 1;
    }

    private static int adminBankRunStatus(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        source.sendSystemMessage(Component.literal(
                "§7Bank: §e" + bank.getBankName()
                        + "\n§7Status: §f" + getBankStatus(centralBank, bank)
                        + "\n§7Window start tick: §f" + metadata.getLong("bankRunWindowStartTick")
                        + "\n§7Window withdrawn: §f$" + readDecimalTag(metadata, "bankRunWindowWithdrawn").toPlainString()
                        + "\n§7Lockdown until: §f" + (metadata.contains("lockdownUntilTick") ? metadata.getLong("lockdownUntilTick") : -1)
        ));
        return 1;
    }

    private static int adminBankRevoke(CommandSourceStack source, String bankNameRaw, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found or protected: " + bankNameRaw));
            return 1;
        }

        CentralBank central = centralBank;
        int movedAccounts = 0;
        BigDecimal movedAmount = BigDecimal.ZERO;
        List<AccountHolder> closingAccounts = new ArrayList<>(bank.getBankAccounts().values());
        for (AccountHolder account : closingAccounts) {
            if (account == null) {
                continue;
            }
            BigDecimal balance = account.getBalance();
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                AccountHolder destination = findOrCreateCentralCheckingAccount(central, account.getPlayerUUID());
                if (destination != null && account.forceRemoveBalance(balance) && destination.forceAddBalance(balance)) {
                    movedAmount = movedAmount.add(balance);
                    movedAccounts++;
                }
            }
            bank.RemoveAccount(account);
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "REVOKED");
        metadata.putString("revokeReason", reason == null ? "" : reason.trim());
        metadata.putLong("revokedAtMillis", System.currentTimeMillis());
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        centralBank.removeBank(bank);

        source.sendSystemMessage(Component.literal(
                "§cRevoked bank " + bank.getBankName()
                        + ". Recovered $" + movedAmount.toPlainString()
                        + " across " + movedAccounts + " account(s)."
        ));
        notifyBankOwner(source.getServer(), bank, "§cYour bank was revoked."
                + ((reason == null || reason.isBlank()) ? "" : " Reason: " + reason));
        return 1;
    }

    private static int adminToggleRateExempt(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        boolean next = !metadata.getBoolean("rateExempt");
        metadata.putBoolean("rateExempt", next);
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal(
                "§aRate-band exemption for " + bank.getBankName() + " is now " + (next ? "ENABLED" : "DISABLED")
        ));
        return 1;
    }

    private static int adminSetDailyCapOverride(CommandSourceStack source, String bankNameRaw, String amountRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid amount: " + amountRaw));
            return 1;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            source.sendSystemMessage(Component.literal("§cOverride amount must be >= 0."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("dailyCapOverride", amount.toPlainString());
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal(
                "§aDaily cap override for " + bank.getBankName() + " set to $" + amount.toPlainString()
        ));
        return 1;
    }

    private static int adminWaiveCharterFee(CommandSourceStack source, ServerPlayer player) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CHARTER_FEE_WAIVERS.put(player.getUUID(), System.currentTimeMillis());
        source.sendSystemMessage(Component.literal(
                "§aOne-time charter fee waiver granted to §e" + player.getName().getString()
        ));
        player.sendSystemMessage(Component.literal("§aYour next bank creation will waive the charter fee."));
        return 1;
    }

    private static int adminDeferLicenseRenewal(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(Component.literal("§cBank not found: " + bankNameRaw));
            return 1;
        }
        long deferBy = Math.max(20, Config.BANK_ANNUAL_LICENSE_INTERVAL_TICKS.get());
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        long currentDue = metadata.contains("nextLicenseFeeTick")
                ? metadata.getLong("nextLicenseFeeTick")
                : currentOverworldGameTime(source.getServer()) + deferBy;
        long nextDue = currentDue + deferBy;
        metadata.putLong("nextLicenseFeeTick", nextDue);
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(Component.literal(
                "§aDeferred annual license renewal for " + bank.getBankName()
                        + " to tick " + nextDue
        ));
        return 1;
    }

    private static int adminListFlags(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        List<CompoundTag> flags = centralBank.getSettlementSuspense().values().stream()
                .filter(tag -> tag.getString("reason").toUpperCase(Locale.ROOT).contains("FLAG"))
                .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Flagged events: §b" + flags.size() + "\n\n"));
        if (flags.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (CompoundTag flag : flags) {
                body.append(Component.literal(
                        "§8- §f" + shortId(readUuidTag(flag, "id"))
                                + " §7" + flag.getString("reason")
                                + " §7amount §6$" + readDecimalTag(flag, "amount").toPlainString()
                                + "\n"
                ));
            }
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.RED, "§cFraud / Flag Queue", body));
        return 1;
    }

    private static int saveBankData(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        BankManager.markDirty();
        source.sendSystemMessage(Component.literal("§aBank data marked dirty for save."));
        return 1;
    }

    private static int renameCentralBank(CommandSourceStack source, String newName) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }
        centralBank.setBankName(newName);
        source.sendSystemMessage(Component.literal("§aThe bank name has been updated to: §e" + newName + "§a."));
        return 1;
    }

    private static int depositToAccount(CommandSourceStack source, UUID accountId, String amountRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        BigDecimal amount = parsePositiveAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cFailed to add amount '§e" + amount + "§c' to account '§e" + accountId + "§c'."));
            return 1;
        }

        addAdminAuditTransaction(account, amount, true, source.getTextName());
        source.sendSystemMessage(Component.literal("§aSuccessfully added '§e" + amount + "§a' to '§e" + accountId + "§a'. New Balance: §2" + account.getBalance()));
        NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, true));
        return 1;
    }

    private static int withdrawFromAccount(CommandSourceStack source, UUID accountId, String amountRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        BigDecimal amount = parsePositiveAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(Component.literal("§cFailed to remove amount '§e" + amount + "§c' from account '§e" + accountId + "§c'."));
            return 1;
        }

        addAdminAuditTransaction(account, amount, false, source.getTextName());
        source.sendSystemMessage(Component.literal("§aSuccessfully removed '§e" + amount + "§a' from '§e" + accountId + "§a'. New Balance: §2" + account.getBalance()));
        NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, false));
        return 1;
    }

    private static int adminViewPlayer(CommandSourceStack source, ServerPlayer target) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        Map<UUID, AccountHolder> accounts = centralBank.SearchForAccount(target.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " has no accounts."));
            return 1;
        }

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Player: §f" + target.getName().getString() + " §8(" + target.getUUID() + ")\n"));
        body.append(Component.literal("§7Accounts: §b" + accounts.size() + "\n\n"));

        List<AccountHolder> ordered = new ArrayList<>(accounts.values());
        ordered.sort(Comparator.comparing(a -> a.getAccountUUID().toString()));
        for (AccountHolder account : ordered) {
            Bank bank = centralBank.getBank(account.getBankId());
            String bankName = bank != null ? bank.getBankName() : "Unknown";
            String accountId = account.getAccountUUID().toString();
            String shortAccountId = accountId.substring(0, Math.min(8, accountId.length()));

            body.append(Component.literal("§e" + shortAccountId + "§7 (" + account.getAccountType().label + ")\n"));
            body.append(Component.literal("§7Bank: §f" + bankName + "\n"));
            body.append(Component.literal("§7Balance: §a" + account.getBalance().toPlainString() + "  "));
            body.append(Component.literal("§7Primary: §f" + account.isPrimaryAccount() + "\n"));
            body.append(Component.literal("§7Frozen: " + (account.isFrozen() ? "§cYes" : "§aNo")));
            if (account.isFrozen() && !account.getFrozenReason().isEmpty()) {
                body.append(Component.literal(" §8(" + account.getFrozenReason() + ")"));
            }
            body.append(Component.literal("\n"));
            body.append(Component.literal("§7PIN Set: §f" + account.hasPin() + "  "));
            body.append(Component.literal("§7Used Today: §f$" + account.getDailyWithdrawnAmount().toPlainString() + "\n"));
            body.append(Component.literal("§7Account ID: §f" + accountId + "\n")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, accountId))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy account ID")))));

            List<UserTransaction> recent = account.getTransactions().values().stream()
                    .sorted(Comparator.comparing(UserTransaction::getTimestamp).reversed())
                    .limit(10)
                    .toList();
            body.append(Component.literal("§7Recent Transactions: §f" + recent.size() + "\n"));
            if (recent.isEmpty()) {
                body.append(Component.literal("§8- none\n"));
            } else {
                for (UserTransaction tx : recent) {
                    boolean incoming = account.getAccountUUID().equals(tx.getReceiverUUID());
                    String sign = incoming ? "+" : "-";
                    UUID cp = incoming ? tx.getSenderUUID() : tx.getReceiverUUID();
                    String cpShort = cp == null ? "unknown" : cp.toString().substring(0, Math.min(8, cp.toString().length()));
                    body.append(Component.literal(
                            "§8- §7" + ADMIN_TX_TIME_FMT.format(tx.getTimestamp())
                                    + " §f" + sign + tx.getAmount().toPlainString()
                                    + " §8[" + tx.getTransactionDescription() + "] §7cp:§f" + cpShort + "\n"
                    ));
                }
            }
            body.append(Component.literal("§8────────────────────────\n"));
        }

        source.sendSystemMessage(ubsPanel(ChatFormatting.AQUA, "§bAdmin Account View", body));
        return 1;
    }

    private static int adminFreezePlayer(CommandSourceStack source, ServerPlayer target, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        Map<UUID, AccountHolder> accounts = centralBank.SearchForAccount(target.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " has no accounts to freeze."));
            return 1;
        }

        int changed = 0;
        for (AccountHolder account : accounts.values()) {
            boolean wasFrozen = account.isFrozen();
            account.freeze(reason);
            addAdminAuditMarker(account, source.getTextName(), "ADMIN_FREEZE", account.getFrozenReason());
            if (!wasFrozen) {
                changed++;
            }
        }

        source.sendSystemMessage(Component.literal(
                "§aFroze §e" + accounts.size() + "§a account(s) for §e" + target.getName().getString()
                        + "§a. Newly frozen: §e" + changed + "§a."
        ));

        String cleanReason = reason == null ? "" : reason.trim();
        target.sendSystemMessage(Component.literal(
                "§cYour banking access has been frozen by an administrator."
                        + (cleanReason.isEmpty() ? "" : " Reason: " + cleanReason)
        ));
        return 1;
    }

    private static int adminUnfreezePlayer(CommandSourceStack source, ServerPlayer target) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        Map<UUID, AccountHolder> accounts = centralBank.SearchForAccount(target.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(Component.literal("§e" + target.getName().getString() + " has no accounts to unfreeze."));
            return 1;
        }

        int changed = 0;
        for (AccountHolder account : accounts.values()) {
            if (account.isFrozen()) {
                changed++;
            }
            account.unfreeze();
            addAdminAuditMarker(account, source.getTextName(), "ADMIN_UNFREEZE", "");
        }

        source.sendSystemMessage(Component.literal(
                "§aUnfroze §e" + accounts.size() + "§a account(s) for §e" + target.getName().getString()
                        + "§a. Previously frozen: §e" + changed + "§a."
        ));
        target.sendSystemMessage(Component.literal("§aYour banking access has been restored by an administrator."));
        return 1;
    }

    private static int adminFreezeAccount(CommandSourceStack source, UUID accountId, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        account.freeze(reason);
        addAdminAuditMarker(account, source.getTextName(), "ADMIN_FREEZE", account.getFrozenReason());
        source.sendSystemMessage(Component.literal("§aAccount §e" + accountId + "§a is now frozen."));

        ServerPlayer target = source.getServer().getPlayerList().getPlayer(account.getPlayerUUID());
        if (target != null) {
            String cleanReason = account.getFrozenReason();
            target.sendSystemMessage(Component.literal(
                    "§cYour account " + accountId + " has been frozen by an administrator."
                            + (cleanReason.isEmpty() ? "" : " Reason: " + cleanReason)
            ));
        }
        return 1;
    }

    private static int adminUnfreezeAccount(CommandSourceStack source, UUID accountId) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(Component.literal("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        account.unfreeze();
        addAdminAuditMarker(account, source.getTextName(), "ADMIN_UNFREEZE", "");
        source.sendSystemMessage(Component.literal("§aAccount §e" + accountId + "§a has been unfrozen."));

        ServerPlayer target = source.getServer().getPlayerList().getPlayer(account.getPlayerUUID());
        if (target != null) {
            target.sendSystemMessage(Component.literal("§aYour account " + accountId + " has been unfrozen by an administrator."));
        }
        return 1;
    }

    private static int adminEconomyReport(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        int registeredBanks = 0;
        int totalAccounts = 0;
        int activeAccounts = 0;
        int frozenAccounts = 0;
        BigDecimal totalBalances = BigDecimal.ZERO;
        BigDecimal totalDailyWithdrawn = BigDecimal.ZERO;
        Map<UUID, BigDecimal> playerTotals = new HashMap<>();
        var server = source.getServer();

        for (Bank bank : centralBank.getBanks().values()) {
            if (!bank.getBankId().equals(centralBank.getBankId())) {
                registeredBanks++;
            }
            for (AccountHolder account : bank.getBankAccounts().values()) {
                totalAccounts++;
                if (account.getBalance().compareTo(BigDecimal.ZERO) > 0 || !account.getTransactions().isEmpty()) {
                    activeAccounts++;
                }
                totalBalances = totalBalances.add(account.getBalance());
                totalDailyWithdrawn = totalDailyWithdrawn.add(account.getDailyWithdrawnAmount());
                if (account.isFrozen()) {
                    frozenAccounts++;
                }
                playerTotals.merge(account.getPlayerUUID(), account.getBalance(), BigDecimal::add);
            }
        }

        List<Map.Entry<UUID, BigDecimal>> richest = playerTotals.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Registered Banks: §b" + registeredBanks + "\n"));
        body.append(Component.literal("§7Total Accounts: §b" + totalAccounts + "\n"));
        body.append(Component.literal("§7Active Accounts: §b" + activeAccounts + "\n"));
        body.append(Component.literal("§7Frozen Accounts: §c" + frozenAccounts + "\n"));
        body.append(Component.literal("§7Total Circulation: §a$" + totalBalances.toPlainString() + "\n"));
        body.append(Component.literal("§7ATM Withdrawn Today: §e$" + totalDailyWithdrawn.toPlainString() + "\n"));
        body.append(Component.literal("§7Central Bank Reserve: §a$" + centralBank.getBankReserve().toPlainString() + "\n"));
        body.append(Component.literal("§7Central Bank Interest Rate: §e" + centralBank.getInterestRate() + "\n"));
        body.append(Component.literal("\n§7Top 10 Richest Players:\n"));
        if (richest.isEmpty()) {
            body.append(Component.literal("§8- none\n"));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, BigDecimal> entry : richest) {
                String name = resolvePlayerName(server, entry.getKey());
                body.append(Component.literal(
                        "§8" + rank + ". §f" + name + " §8(" + entry.getKey() + ") §7- §a$" + entry.getValue().toPlainString() + "\n"
                ));
                rank++;
            }
        }
        body.append(Component.literal("\n§8Generated at world time: §7" + currentOverworldGameTime(server)));

        source.sendSystemMessage(ubsPanel(ChatFormatting.YELLOW, "§eEconomy Report", body));
        return 1;
    }

    private static int adminListPendingLoanApprovals(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        List<LoanService.LoanQuote> pending = LoanService.listPendingApprovals();
        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Pending Loan Approvals: §b" + pending.size() + "\n\n"));
        if (pending.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (LoanService.LoanQuote quote : pending) {
                body.append(Component.literal(
                        "§8- §f" + quote.borrowerPlayerId()
                                + " §7amount §6$" + quote.principal().toPlainString()
                                + " §7APR §e" + quote.annualInterestRate() + "%\n"
                ));
            }
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "§eLoan Approval Queue", body));
        return 1;
    }

    private static int adminApproveLoan(CommandSourceStack source, ServerPlayer borrower) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        LoanService.LoanQuote pending = LoanService.getPendingApproval(borrower.getUUID());
        if (pending == null) {
            source.sendSystemMessage(Component.literal("§cNo pending loan request for that player."));
            return 1;
        }

        var issued = LoanService.approvePending(source.getServer(), borrower.getUUID());
        if (issued == null) {
            source.sendSystemMessage(Component.literal("§cLoan approval failed while issuing funds."));
            borrower.sendSystemMessage(Component.literal("§cYour loan request could not be issued after approval."));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§aLoan approved for §e" + borrower.getName().getString()
                        + "§a: $" + pending.principal().toPlainString()
        ));
        borrower.sendSystemMessage(Component.literal(
                "§aYour loan request was approved. Funds have been deposited to your account."
        ));
        return 1;
    }

    private static int adminDenyLoan(CommandSourceStack source, ServerPlayer borrower, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        boolean removed = LoanService.denyPending(borrower.getUUID());
        if (!removed) {
            source.sendSystemMessage(Component.literal("§cNo pending loan request for that player."));
            return 1;
        }

        source.sendSystemMessage(Component.literal(
                "§eDenied pending loan for §f" + borrower.getName().getString() + "§e."
        ));
        String cleanReason = reason == null ? "" : reason.trim();
        borrower.sendSystemMessage(Component.literal(
                "§cYour loan request was denied." + (cleanReason.isEmpty() ? "" : " Reason: " + cleanReason)
        ));
        return 1;
    }

    private static int adminAddSchedule(CommandSourceStack source,
                                        UUID sourceAccountId,
                                        UUID targetAccountId,
                                        String amountRaw,
                                        String frequencyRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is unavailable."));
            return 1;
        }

        AccountHolder sourceAccount = centralBank.SearchForAccountByAccountId(sourceAccountId);
        AccountHolder targetAccount = centralBank.SearchForAccountByAccountId(targetAccountId);
        if (sourceAccount == null || targetAccount == null) {
            source.sendSystemMessage(Component.literal("§cSource or target account was not found."));
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

        long frequencyTicks;
        try {
            frequencyTicks = Long.parseLong(frequencyRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid frequency ticks."));
            return 1;
        }

        if (frequencyTicks < 20L) {
            source.sendSystemMessage(Component.literal("§cFrequency must be at least 20 ticks."));
            return 1;
        }

        long firstRun = currentOverworldGameTime(source.getServer()) + frequencyTicks;
        ScheduledPayment payment = new ScheduledPayment(
                UUID.randomUUID(),
                sourceAccountId,
                targetAccountId,
                amount,
                frequencyTicks,
                firstRun,
                source.getTextName(),
                true
        );
        centralBank.addScheduledPayment(payment);

        source.sendSystemMessage(Component.literal(
                "§aScheduled payment created: §f" + payment.getPaymentId()
                        + " §7amount §6$" + amount.toPlainString()
                        + " §7every §f" + frequencyTicks + " ticks"
        ));
        return 1;
    }

    private static int adminListSchedules(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is unavailable."));
            return 1;
        }

        var payments = centralBank.getScheduledPayments().values().stream()
                .sorted(Comparator.comparing(ScheduledPayment::getPaymentId))
                .toList();

        MutableComponent body = Component.empty();
        body.append(Component.literal("§7Scheduled Payments: §b" + payments.size() + "\n\n"));
        if (payments.isEmpty()) {
            body.append(Component.literal("§8- none"));
        } else {
            for (ScheduledPayment payment : payments) {
                body.append(Component.literal(
                        "§8- §f" + payment.getPaymentId() + "\n"
                                + "  §7from: §f" + payment.getSourceAccountId() + "\n"
                                + "  §7to: §f" + payment.getTargetAccountId() + "\n"
                                + "  §7amount: §6$" + payment.getAmount().toPlainString()
                                + " §7freq: §f" + payment.getFrequencyTicks()
                                + " §7next: §f" + payment.getNextExecutionGameTime() + "\n"
                ));
            }
        }

        source.sendSystemMessage(ubsPanel(ChatFormatting.AQUA, "§bScheduled Payments", body));
        return 1;
    }

    private static int adminRemoveSchedule(CommandSourceStack source, UUID paymentId) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is unavailable."));
            return 1;
        }

        boolean removed = centralBank.removeScheduledPayment(paymentId);
        if (!removed) {
            source.sendSystemMessage(Component.literal("§cScheduled payment not found."));
            return 1;
        }

        source.sendSystemMessage(Component.literal("§aRemoved scheduled payment §f" + paymentId + "§a."));
        return 1;
    }

    private static int adminImportCsv(CommandSourceStack source, String rawPath) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        Path path = resolveImportPath(source, rawPath);
        if (path == null) {
            return 1;
        }
        if (!Files.exists(path)) {
            source.sendSystemMessage(Component.literal("§cCSV file not found: §e" + path));
            return 1;
        }

        ImportStats stats = new ImportStats();
        try {
            List<String> lines = Files.readAllLines(path);
            for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
                String raw = lines.get(lineNumber - 1).trim();
                if (raw.isEmpty() || raw.startsWith("#")) {
                    continue;
                }
                String lower = raw.toLowerCase(Locale.ROOT);
                if (lower.startsWith("player_uuid") || lower.startsWith("player,bank_name")) {
                    continue;
                }

                String[] cols = raw.split(",", -1);
                if (cols.length < 5) {
                    stats.failed++;
                    addImportError(stats.errors, lineNumber, "Expected at least 5 columns.");
                    continue;
                }

                try {
                    UUID playerUuid = resolvePlayerUuid(source, cols[0].trim());
                    if (playerUuid == null) {
                        throw new IllegalArgumentException("Unknown player/UUID: " + cols[0].trim());
                    }

                    String bankName = cols[1].trim();
                    if (bankName.isBlank()) {
                        bankName = "Central Bank";
                    }

                    AccountTypes accountType = parseAccountType(cols[2].trim());
                    if (accountType == null) {
                        throw new IllegalArgumentException("Unknown account type.");
                    }

                    BigDecimal balance = parseFlexibleMoney(cols[3].trim());
                    if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Balance must be a non-negative number.");
                    }

                    String pin = cols[4].trim();
                    if (!pin.isEmpty() && !pin.matches("\\d{4}")) {
                        throw new IllegalArgumentException("PIN must be empty or exactly 4 digits.");
                    }
                    boolean primary = cols.length >= 6 && parseBooleanFlexible(cols[5].trim());
                    String historyRaw = cols.length >= 7 ? cols[6].trim() : "";

                    AccountHolder account = upsertImportedAccount(
                            centralBank,
                            playerUuid,
                            bankName,
                            accountType,
                            balance,
                            pin,
                            primary,
                            source.getTextName(),
                            "CSV " + path,
                            stats
                    );
                    stats.importedHistoryEntries += importHistoryField(account, historyRaw, "CSV Import History");
                } catch (Exception ex) {
                    stats.failed++;
                    addImportError(stats.errors, lineNumber, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            source.sendSystemMessage(Component.literal("§cFailed to read CSV: §e" + ex.getMessage()));
            return 1;
        }

        finalizeImport(source, path, "CSV Import", stats);
        return 1;
    }

    private static int adminImportEssentialsX(CommandSourceStack source, String rawPath) {
        return adminImportYamlDirectory(source, rawPath, "EssentialsX");
    }

    private static int adminImportCMI(CommandSourceStack source, String rawPath) {
        return adminImportYamlDirectory(source, rawPath, "CMI");
    }

    private static int adminImportYamlDirectory(CommandSourceStack source, String rawPath, String sourceName) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        Path path = resolveImportPath(source, rawPath);
        if (path == null) {
            return 1;
        }
        if (!Files.exists(path)) {
            source.sendSystemMessage(Component.literal("§cImport path not found: §e" + path));
            return 1;
        }

        List<Path> files = listYamlFiles(path);
        if (files.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cNo .yml/.yaml files found at: §e" + path));
            return 1;
        }

        ImportStats stats = new ImportStats();
        for (Path file : files) {
            try {
                Map<String, String> yaml = readSimpleYaml(file);
                UUID playerUuid = resolveUuidFromYaml(source, file, yaml);
                if (playerUuid == null) {
                    throw new IllegalArgumentException("Could not resolve player UUID.");
                }

                BigDecimal balance = firstMoneyValue(yaml, "money", "balance", "bal");
                if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Missing valid non-negative money/balance value.");
                }

                String pin = firstNonBlank(yaml.get("pin"), yaml.get("password"), yaml.get("ubs_pin"));
                if (pin == null) {
                    pin = "";
                }
                if (!pin.isEmpty() && !pin.matches("\\d{4}")) {
                    pin = "";
                }

                boolean primary = parseBooleanFlexible(firstNonBlank(
                        yaml.get("primary"),
                        yaml.get("is_primary"),
                        yaml.get("default_account")
                ));
                String bankName = firstNonBlank(yaml.get("bank"), yaml.get("bank_name"), "Central Bank");
                AccountTypes accountType = parseAccountType(firstNonBlank(
                        yaml.get("account_type"),
                        yaml.get("account"),
                        "CheckingAccount"
                ));
                if (accountType == null) {
                    accountType = AccountTypes.CheckingAccount;
                }

                AccountHolder account = upsertImportedAccount(
                        centralBank,
                        playerUuid,
                        bankName,
                        accountType,
                        balance,
                        pin,
                        primary,
                        source.getTextName(),
                        sourceName + " " + file,
                        stats
                );

                String historyRaw = firstNonBlank(yaml.get("history"), yaml.get("transaction_history"), "");
                stats.importedHistoryEntries += importHistoryField(account, historyRaw, sourceName + " Import History");
            } catch (Exception ex) {
                stats.failed++;
                stats.errors.add(file.getFileName() + ": " + (ex.getMessage() == null ? "unknown error" : ex.getMessage()));
            }
        }

        finalizeImport(source, path, sourceName + " Import", stats);
        return 1;
    }

    private static int adminImportIconomy(CommandSourceStack source, String rawPath) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(Component.literal("§cCentral bank data is not available."));
            return 1;
        }

        Path path = resolveImportPath(source, rawPath);
        if (path == null) {
            return 1;
        }
        if (!Files.exists(path)) {
            source.sendSystemMessage(Component.literal("§cImport file not found: §e" + path));
            return 1;
        }

        ImportStats stats = new ImportStats();
        try {
            List<String> lines = Files.readAllLines(path);
            for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
                String raw = lines.get(lineNumber - 1).trim();
                if (raw.isEmpty() || raw.startsWith("#")) {
                    continue;
                }
                String lower = raw.toLowerCase(Locale.ROOT);
                if (lower.startsWith("player") || lower.startsWith("name")) {
                    continue;
                }

                String[] cols = raw.contains(",") ? raw.split(",", -1) : raw.split(":", -1);
                if (cols.length < 2) {
                    stats.failed++;
                    addImportError(stats.errors, lineNumber, "Expected `<player>,<balance>` or `<player>:<balance>`.");
                    continue;
                }

                try {
                    UUID playerUuid = resolvePlayerUuid(source, cols[0].trim());
                    if (playerUuid == null) {
                        throw new IllegalArgumentException("Unknown player/UUID: " + cols[0].trim());
                    }

                    BigDecimal balance = parseFlexibleMoney(cols[1].trim());
                    if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Balance must be a non-negative number.");
                    }

                    upsertImportedAccount(
                            centralBank,
                            playerUuid,
                            "Central Bank",
                            AccountTypes.CheckingAccount,
                            balance,
                            "",
                            true,
                            source.getTextName(),
                            "iConomy " + path,
                            stats
                    );
                } catch (Exception ex) {
                    stats.failed++;
                    addImportError(stats.errors, lineNumber, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            source.sendSystemMessage(Component.literal("§cFailed to read import file: §e" + ex.getMessage()));
            return 1;
        }

        finalizeImport(source, path, "iConomy Import", stats);
        return 1;
    }

    private static void finalizeImport(CommandSourceStack source, Path path, String title, ImportStats stats) {
        BankManager.markDirty();

        MutableComponent summary = Component.empty();
        summary.append(Component.literal("§7Source: §f" + path + "\n"));
        summary.append(Component.literal("§7Created: §a" + stats.created + "\n"));
        summary.append(Component.literal("§7Updated: §e" + stats.updated + "\n"));
        summary.append(Component.literal("§7Imported History Entries: §b" + stats.importedHistoryEntries + "\n"));
        summary.append(Component.literal("§7Failed: §c" + stats.failed + "\n"));
        if (!stats.errors.isEmpty()) {
            summary.append(Component.literal("\n§cFirst errors:\n"));
            for (int i = 0; i < Math.min(5, stats.errors.size()); i++) {
                summary.append(Component.literal("§8- §c" + stats.errors.get(i) + "\n"));
            }
        }

        UltimateBankingSystem.LOGGER.info(
                "[UBS] {} finished. Source={}, created={}, updated={}, importedHistoryEntries={}, failed={}",
                title, path, stats.created, stats.updated, stats.importedHistoryEntries, stats.failed
        );
        if (!stats.errors.isEmpty()) {
            for (int i = 0; i < Math.min(10, stats.errors.size()); i++) {
                UltimateBankingSystem.LOGGER.warn("[UBS] {} warning: {}", title, stats.errors.get(i));
            }
        }

        source.sendSystemMessage(ubsPanel(ChatFormatting.LIGHT_PURPLE, "§d" + title, summary));
    }

    private static AccountHolder upsertImportedAccount(CentralBank centralBank,
                                                       UUID playerUuid,
                                                       String bankName,
                                                       AccountTypes accountType,
                                                       BigDecimal balance,
                                                       String pin,
                                                       boolean primary,
                                                       String actorName,
                                                       String auditDetail,
                                                       ImportStats stats) {
        Bank bank = resolveOrCreateBank(centralBank, bankName, playerUuid);
        AccountHolder existing = findAccountForPlayerAndType(bank, playerUuid, accountType);

        if (existing == null) {
            AccountHolder createdAccount = new AccountHolder(playerUuid, balance, accountType, pin, bank.getBankId(), null);
            if (!bank.AddAccount(createdAccount)) {
                throw new IllegalStateException("Failed to create account.");
            }
            addAdminAuditMarker(createdAccount, actorName, "ADMIN_IMPORT_CREATE", auditDetail);
            if (primary) {
                setPrimaryForPlayer(centralBank, playerUuid, createdAccount.getAccountUUID());
            }
            stats.created++;
            return createdAccount;
        }

        if (!setAccountBalance(existing, balance)) {
            throw new IllegalStateException("Failed to update balance.");
        }
        if (pin != null && !pin.isBlank() && !existing.setPin(pin)) {
            throw new IllegalStateException("Failed to update PIN.");
        }
        addAdminAuditMarker(existing, actorName, "ADMIN_IMPORT_UPDATE", auditDetail);
        if (primary) {
            setPrimaryForPlayer(centralBank, playerUuid, existing.getAccountUUID());
        }
        stats.updated++;
        return existing;
    }

    private static int importHistoryField(AccountHolder account, String historyRaw, String defaultDescription) {
        if (account == null || historyRaw == null || historyRaw.isBlank()) {
            return 0;
        }

        int imported = 0;
        String[] entries = historyRaw.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isEmpty()) {
                continue;
            }

            String[] parts = raw.split("\\|", 3);
            if (parts.length < 2) {
                continue;
            }

            LocalDateTime ts;
            try {
                ts = LocalDateTime.parse(parts[0].trim(), IMPORT_TX_TIME_FMT);
            } catch (DateTimeParseException ignored) {
                ts = LocalDateTime.now();
            }

            BigDecimal signedAmount = parseFlexibleMoney(parts[1].trim());
            if (signedAmount == null || signedAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            String description = parts.length >= 3 && !parts[2].trim().isEmpty()
                    ? parts[2].trim()
                    : defaultDescription;

            BigDecimal amountAbs = signedAmount.abs();
            boolean incoming = signedAmount.compareTo(BigDecimal.ZERO) > 0;
            UUID sender = incoming ? ADMIN_SYSTEM_ID : account.getAccountUUID();
            UUID receiver = incoming ? account.getAccountUUID() : ADMIN_SYSTEM_ID;

            UUID txId = UUID.nameUUIDFromBytes(
                    (account.getAccountUUID() + "|" + ts + "|" + signedAmount.toPlainString() + "|" + description)
                            .getBytes(StandardCharsets.UTF_8)
            );
            if (account.getTransactions().containsKey(txId)) {
                continue;
            }

            UserTransaction tx = new UserTransaction(sender, receiver, amountAbs, ts, description, txId);
            account.addTransaction(tx);
            imported++;
        }
        return imported;
    }

    private static Path resolveImportPath(CommandSourceStack source, String rawPath) {
        try {
            Path path = Path.of(rawPath);
            if (!path.isAbsolute()) {
                path = source.getServer().getFile(".").resolve(path).normalize();
            }
            return path;
        } catch (InvalidPathException ex) {
            source.sendSystemMessage(Component.literal("§cInvalid path: §e" + rawPath));
            return null;
        }
    }

    private static List<Path> listYamlFiles(Path path) {
        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .filter(UBSAdminCommands::isYamlFile)
                            .sorted()
                            .toList();
                }
            }
            return isYamlFile(path) ? List.of(path) : List.of();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static Map<String, String> readSimpleYaml(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String raw = line.trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }

            int idx = raw.indexOf(':');
            if (idx <= 0) {
                continue;
            }

            String key = raw.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = raw.substring(idx + 1).trim();
            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            } else if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            values.putIfAbsent(key, value);
        }
        return values;
    }

    private static UUID resolveUuidFromYaml(CommandSourceStack source, Path file, Map<String, String> yaml) {
        String rawUuid = firstNonBlank(yaml.get("uuid"), yaml.get("player_uuid"));
        if (rawUuid != null) {
            UUID parsed = resolvePlayerUuid(source, rawUuid);
            if (parsed != null) {
                return parsed;
            }
        }

        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        UUID fromName = resolvePlayerUuid(source, stem);
        if (fromName != null) {
            return fromName;
        }

        String playerName = firstNonBlank(yaml.get("name"), yaml.get("player"), yaml.get("username"), yaml.get("last-account-name"));
        return resolvePlayerUuid(source, playerName);
    }

    private static UUID resolvePlayerUuid(CommandSourceStack source, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim();

        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID; try profile cache.
        }

        Optional<GameProfile> profile = source.getServer().getProfileCache().get(token);
        if (profile.isPresent()) {
            return profile.get().getId();
        }

        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + token).getBytes(StandardCharsets.UTF_8));
    }

    private static BigDecimal firstMoneyValue(Map<String, String> values, String... keys) {
        for (String key : keys) {
            BigDecimal parsed = parseFlexibleMoney(values.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static BigDecimal parseFlexibleMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.trim().replace(",", "");
        if (clean.startsWith("$")) {
            clean = clean.substring(1);
        }
        if (clean.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean parseBooleanFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        return clean.equals("true") || clean.equals("yes") || clean.equals("1") || clean.equals("y");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Bank resolveBankByName(CentralBank centralBank, String bankNameRaw) {
        if (centralBank == null || bankNameRaw == null) {
            return null;
        }
        String requested = bankNameRaw.trim();
        if (requested.isBlank()) {
            return null;
        }
        return centralBank.getBanks().values().stream()
                .filter(bank -> bank.getBankName() != null)
                .filter(bank -> bank.getBankName().trim().equalsIgnoreCase(requested))
                .findFirst()
                .orElse(null);
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

    private static void trimTagMap(Map<UUID, CompoundTag> map, int maxSize) {
        if (map == null || map.size() <= maxSize || maxSize < 1) {
            return;
        }
        List<Map.Entry<UUID, CompoundTag>> ordered = map.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getLong("timestampMillis")))
                .toList();
        int removeCount = map.size() - maxSize;
        for (int i = 0; i < removeCount && i < ordered.size(); i++) {
            map.remove(ordered.get(i).getKey());
        }
    }

    private static BigDecimal readDecimalTag(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.contains(key)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(tag.getString(key));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static UUID readUuidTag(CompoundTag tag, String key) {
        if (tag == null || key == null || key.isBlank() || !tag.hasUUID(key)) {
            return null;
        }
        return tag.getUUID(key);
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private static AccountHolder findPrimaryAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        for (AccountHolder account : centralBank.SearchForAccount(playerId).values()) {
            if (account.isPrimaryAccount()) {
                return account;
            }
        }
        return null;
    }

    private static AccountHolder findOrCreateCentralCheckingAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        for (AccountHolder account : centralBank.getBankAccounts().values()) {
            if (playerId.equals(account.getPlayerUUID())
                    && account.getAccountType() == AccountTypes.CheckingAccount) {
                return account;
            }
        }
        AccountHolder created = new AccountHolder(
                playerId,
                BigDecimal.ZERO,
                AccountTypes.CheckingAccount,
                "",
                centralBank.getBankId(),
                null
        );
        if (!centralBank.AddAccount(created)) {
            return null;
        }
        return created;
    }

    private static void notifyBankOwner(net.minecraft.server.MinecraftServer server, Bank bank, String message) {
        if (server == null || bank == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(bank.getBankOwnerId());
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(message));
        }
    }

    private static BigDecimal parsePositiveAmount(CommandSourceStack source, String amountRaw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw);
        } catch (NumberFormatException e) {
            source.sendSystemMessage(Component.literal("§cThe amount '§e" + amountRaw + "§c' is not a valid number."));
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(Component.literal("§cAmount must be greater than zero."));
            return null;
        }
        return amount;
    }

    private static void addAdminAuditTransaction(AccountHolder account, BigDecimal amount, boolean credit, String actorName) {
        String description = credit
                ? "ADMIN_DEPOSIT by " + actorName
                : "ADMIN_WITHDRAW by " + actorName;
        UserTransaction transaction = credit
                ? new UserTransaction(ADMIN_SYSTEM_ID, account.getAccountUUID(), amount, LocalDateTime.now(), description)
                : new UserTransaction(account.getAccountUUID(), ADMIN_SYSTEM_ID, amount, LocalDateTime.now(), description);
        account.addTransaction(transaction);
    }

    private static void addAdminAuditMarker(AccountHolder account, String actorName, String action, String detail) {
        String suffix = (detail == null || detail.isBlank()) ? "" : " (" + detail + ")";
        UserTransaction transaction = new UserTransaction(
                ADMIN_SYSTEM_ID,
                account.getAccountUUID(),
                BigDecimal.ZERO,
                LocalDateTime.now(),
                action + " by " + actorName + suffix
        );
        account.addTransaction(transaction);
    }

    private static void addImportError(List<String> errors, int lineNumber, String message) {
        errors.add("line " + lineNumber + ": " + (message == null ? "unknown error" : message));
    }

    private static AccountTypes parseAccountType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (AccountTypes value : AccountTypes.values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
            if (value.label.equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return null;
    }

    private static Bank resolveOrCreateBank(CentralBank centralBank, String bankName, UUID owner) {
        Bank existing = resolveBankByName(centralBank, bankName);
        if (existing != null) {
            return existing;
        }

        Bank created = new Bank(null, bankName, BigDecimal.ZERO, centralBank.getInterestRate(), owner);
        centralBank.addBank(created);
        return created;
    }

    private static AccountHolder findAccountForPlayerAndType(Bank bank, UUID playerUuid, AccountTypes accountType) {
        for (AccountHolder account : bank.getBankAccounts().values()) {
            if (account.getPlayerUUID().equals(playerUuid) && account.getAccountType() == accountType) {
                return account;
            }
        }
        return null;
    }

    private static boolean setAccountBalance(AccountHolder account, BigDecimal newBalance) {
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        BigDecimal current = account.getBalance();
        int cmp = newBalance.compareTo(current);
        if (cmp == 0) {
            return true;
        }
        if (cmp > 0) {
            return account.forceAddBalance(newBalance.subtract(current));
        }
        return account.forceRemoveBalance(current.subtract(newBalance));
    }

    private static void setPrimaryForPlayer(CentralBank centralBank, UUID playerUuid, UUID primaryAccountId) {
        for (AccountHolder candidate : centralBank.SearchForAccount(playerUuid).values()) {
            candidate.setPrimaryAccount(candidate.getAccountUUID().equals(primaryAccountId));
        }
    }

    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getName().getString();
        }
        String raw = playerId.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private static long currentOverworldGameTime(net.minecraft.server.MinecraftServer server) {
        var overworld = server.getLevel(Level.OVERWORLD);
        return overworld != null ? overworld.getGameTime() : 0L;
    }
}
