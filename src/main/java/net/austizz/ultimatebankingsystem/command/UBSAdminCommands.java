package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.authlib.GameProfile;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
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

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSAdminCommands {
    private static final int ADMIN_PERMISSION_LEVEL = 3;
    private static final UUID ADMIN_SYSTEM_ID = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:admin-system".getBytes(StandardCharsets.UTF_8)
    );
    private static final DateTimeFormatter ADMIN_TX_TIME_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final DateTimeFormatter IMPORT_TX_TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildUbsRoot());
        event.getDispatcher().register(Commands.literal("bank").then(buildAdminLiteral()));
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
        Bank existing = centralBank.getBankByName(bankName);
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
