package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.authlib.GameProfile;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.BankLevelService;
import net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremiseAdminService;
import net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremisePayloadBuilder;
import net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremiseService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomNbtStore;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomState;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.MoneyStackBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.MetalPalletBlockEntity;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.loan.LoanService;
import net.austizz.ultimatebankingsystem.market.CommodityMarketService;
import net.austizz.ultimatebankingsystem.heist.HeistSavedData;
import net.austizz.ultimatebankingsystem.heist.HeistService;
import net.austizz.ultimatebankingsystem.heist.HeistSession;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremisePayload;
import net.austizz.ultimatebankingsystem.payments.ScheduledPayment;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.MoneyText;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
import java.util.function.Supplier;

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

    private record AppealComplianceReview(
            boolean bankFound,
            boolean compliancePassed,
            boolean statusChanged,
            String previousStatus,
            String nextStatus,
            String message
    ) {}

    private static MutableComponent moneyLiteral(String text) {
        return Component.literal(MoneyText.abbreviateCurrencyTokens(text == null ? "" : text));
    }

    private static Component ubsPanel(ChatFormatting accentColor, String title, Component body) {
        return moneyLiteral("§6§lUltimate Banking System §7- ")
                .append(moneyLiteral(title).withStyle(accentColor))
                .append(moneyLiteral("\n§8────────────────────────\n"))
                .append(body);
    }

    private static boolean requireAdminPermission(CommandSourceStack source) {
        if (source.getPlayer() != null && !source.getPlayer().hasPermissions(ADMIN_PERMISSION_LEVEL)) {
            source.sendSystemMessage(moneyLiteral("§4You do not have permission to perform this action."));
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
                        .then(buildCentralBankMarketLiteral())
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
                .then(Commands.literal("seed")
                        .then(Commands.literal("banking")
                                .executes(context -> seedBankingDemo(context.getSource()))
                        )
                        .then(Commands.literal("leaderboard")
                                .executes(context -> seedLeaderboardDemo(context.getSource()))
                                .then(Commands.literal("remove")
                                        .executes(context -> removeLeaderboardDemo(context.getSource()))
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
                .then(buildGiveLiteral())
                .then(buildAdminLiteral());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildGiveLiteral() {
        LiteralArgumentBuilder<CommandSourceStack> moneyStacks = Commands.literal("money_stacks");
        for (MoneyStackBlock.BillDenomination denomination : MoneyStackBlock.BillDenomination.values()) {
            moneyStacks.then(palletAmountNodes(
                    Commands.literal(denomination.getSerializedName()),
                    () -> new ItemStack(denomination.stackItem()),
                    true,
                    "$" + denomination.value() + " money stack"
            ));
        }
        LiteralArgumentBuilder<CommandSourceStack> bars = Commands.literal("bars")
                .then(palletAmountNodes(
                        Commands.literal("gold"),
                        () -> new ItemStack(ModBlocks.GOLD_BAR.get().asItem()),
                        false,
                        "gold bar"
                ))
                .then(palletAmountNodes(
                        Commands.literal("silver"),
                        () -> new ItemStack(ModBlocks.SILVER_BAR.get().asItem()),
                        false,
                        "silver bar"
                ));
        return Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.literal("metal_pallet")
                                .then(moneyStacks)
                                .then(bars)
                        )
                );
    }

    /**
     * Shared amount grammar for one pallet content type: the bare type node
     * and the literal "full" both fill the whole grid, "half" fills exactly
     * half (full layers by construction: money 192 = 6x32, bars 36 = 3x12),
     * and an integer fills an exact piece count within the grid capacity.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> palletAmountNodes(
            LiteralArgumentBuilder<CommandSourceStack> typeNode,
            Supplier<ItemStack> contentSupplier,
            boolean moneyGrid,
            String contentLabel
    ) {
        int gridCapacity = moneyGrid
                ? MetalPalletBlockEntity.MONEY_SLOT_COUNT
                : MetalPalletBlockEntity.WIDE_SLOT_COUNT;
        return typeNode
                .executes(context -> adminGiveMetalPallet(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        contentSupplier.get(),
                        moneyGrid,
                        gridCapacity,
                        contentLabel
                ))
                .then(Commands.literal("full")
                        .executes(context -> adminGiveMetalPallet(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                contentSupplier.get(),
                                moneyGrid,
                                gridCapacity,
                                contentLabel
                        ))
                )
                .then(Commands.literal("half")
                        .executes(context -> adminGiveMetalPallet(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                contentSupplier.get(),
                                moneyGrid,
                                gridCapacity / 2,
                                contentLabel
                        ))
                )
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, gridCapacity))
                        .executes(context -> adminGiveMetalPallet(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                contentSupplier.get(),
                                moneyGrid,
                                IntegerArgumentType.getInteger(context, "amount"),
                                contentLabel
                        ))
                );
    }

    private static int adminGiveMetalPallet(CommandSourceStack source,
                                            ServerPlayer target,
                                            ItemStack content,
                                            boolean moneyGrid,
                                            int count,
                                            String contentLabel) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        ItemStack palletStack = MetalPalletBlockEntity.createFilledPalletItem(
                content,
                moneyGrid,
                count,
                source.registryAccess()
        );

        // Vanilla /give delivery: try the inventory first, otherwise drop the
        // item at the target's feet with no pickup delay, locked to them.
        boolean added = target.getInventory().add(palletStack);
        if (added && palletStack.isEmpty()) {
            target.level().playSound(
                    null,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    0.2F,
                    ((target.getRandom().nextFloat() - target.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
            );
            target.containerMenu.broadcastChanges();
        } else {
            ItemEntity dropped = target.drop(palletStack, false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
                dropped.setTarget(target.getUUID());
            }
        }

        int positionsPerLayer = moneyGrid
                ? MetalPalletBlockEntity.MONEY_POSITIONS
                : MetalPalletBlockEntity.WIDE_POSITIONS;
        int fullLayers = count / positionsPerLayer;
        int remainder = count % positionsPerLayer;
        String layersText;
        if (remainder == 0) {
            layersText = fullLayers + (fullLayers == 1 ? " full layer" : " full layers");
        } else if (fullLayers == 0) {
            layersText = remainder + (remainder == 1 ? " piece" : " pieces");
        } else {
            layersText = fullLayers + (fullLayers == 1 ? " layer" : " layers")
                    + " + " + remainder + (remainder == 1 ? " piece" : " pieces");
        }

        source.sendSystemMessage(moneyLiteral(
                "§aGave §e" + target.getName().getString()
                        + " §aa metal pallet with §6" + count + "x " + contentLabel + (count == 1 ? "" : "s")
                        + " §7(" + layersText + ")"
        ));
        return 1;
    }

    private static int seedBankingDemo(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        UBSBankingSeedService.SeedResult result = UBSBankingSeedService.seedBankingDemo(
                source.getServer(),
                source.getPlayer()
        );

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Seeded/updated UBS demo banking data.\n\n"));
        body.append(moneyLiteral("§8- §fBanks: §a" + result.banksCreated() + " created §7/ §e" + result.banksUpdated() + " updated\n"));
        body.append(moneyLiteral("§8- §fAccounts: §b" + result.accountsSeeded() + "\n"));
        body.append(moneyLiteral("§8- §fLoan products: §b" + result.loanProductsSeeded() + "\n"));
        body.append(moneyLiteral("§8- §fInter-bank offers: §b" + result.offersSeeded() + "\n"));
        body.append(moneyLiteral("§8- §fInter-bank loans: §b" + result.loansSeeded() + "\n"));
        body.append(moneyLiteral("§8- §fSettlement rows: §b" + result.settlementsSeeded() + "\n"));
        body.append(moneyLiteral("§8- §fDashboard history points: §b" + result.historyPointsSeeded() + "\n\n"));
        body.append(moneyLiteral("§7Open the Bank Owner PC or web dashboard to inspect the seeded market."));
        if (result.playerSandboxBankName() != null && !result.playerSandboxBankName().isBlank()) {
            body.append(moneyLiteral("\n§7Player sandbox bank: §e" + result.playerSandboxBankName()));
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.GREEN, "Demo Banking Seed", body));
        return Math.max(1, result.totalSeeded());
    }

    private static int seedLeaderboardDemo(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        ShopService.LeaderboardSeedResult result = ShopService.seedOrderBoardLeaderboardDemo(source.getServer());
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Seeded/updated Order Board leaderboard placeholder couriers.\n\n"));
        body.append(moneyLiteral("§8- §fRows created: §a" + result.rowsCreated() + "\n"));
        body.append(moneyLiteral("§8- §fRows updated: §e" + result.rowsUpdated() + "\n"));
        if (result.rowsSkipped() > 0) {
            body.append(moneyLiteral("§8- §fRows skipped: §c" + result.rowsSkipped() + " §7(real courier row used the demo id)\n"));
        }
        body.append(moneyLiteral("\n§7Open Order Board > Ranking to inspect the seeded top 10 and Top 100 panels."));
        source.sendSystemMessage(ubsPanel(ChatFormatting.GREEN, "Demo Leaderboard Seed", body));
        return Math.max(1, result.totalChanged());
    }

    private static int removeLeaderboardDemo(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        ShopService.LeaderboardSeedResult result = ShopService.removeOrderBoardLeaderboardDemo(source.getServer());
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Removed Order Board leaderboard placeholder couriers.\n\n"));
        body.append(moneyLiteral("§8- §fRows removed: §c" + result.rowsRemoved() + "\n"));
        body.append(moneyLiteral("\n§7Real courier delivery history was left untouched."));
        source.sendSystemMessage(ubsPanel(ChatFormatting.RED, "Demo Leaderboard Removed", body));
        return Math.max(1, result.rowsRemoved());
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
                .then(buildCentralBankMarketLiteral())
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

    private static LiteralArgumentBuilder<CommandSourceStack> buildCentralBankMarketLiteral() {
        return Commands.literal("market")
                .executes(context -> centralBankMarketShow(context.getSource()))
                .then(Commands.literal("show")
                        .executes(context -> centralBankMarketShow(context.getSource()))
                )
                .then(Commands.literal("set")
                        .then(Commands.argument("commodity", StringArgumentType.word())
                                .then(Commands.argument("price", StringArgumentType.greedyString())
                                        .executes(context -> centralBankMarketSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "commodity"),
                                                StringArgumentType.getString(context, "price")
                                        ))
                                )
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
                        .then(NumismaticsMigrationCommands.buildNumismaticsLiteral())
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
                .then(Commands.literal("bank")
                        .then(Commands.literal("level")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("bankId", UuidArgument.uuid())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                        .executes(context -> adminBankLevelSet(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "bankId"),
                                                                IntegerArgumentType.getInteger(context, "level")
                                                        ))
                                                )
                                        )
                                )
                                .then(Commands.literal("add")
                                        .then(Commands.argument("bankId", UuidArgument.uuid())
                                                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                                                        .executes(context -> adminBankLevelAdd(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "bankId"),
                                                                IntegerArgumentType.getInteger(context, "levels")
                                                        ))
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("bankId", UuidArgument.uuid())
                                                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                                                        .executes(context -> adminBankLevelRemove(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "bankId"),
                                                                IntegerArgumentType.getInteger(context, "levels")
                                                        ))
                                                )
                                        )
                                )
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("bankId", UuidArgument.uuid())
                                                .executes(context -> adminBankLevelDelete(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "bankId")
                                                ))
                                        )
                                )
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("bankId", UuidArgument.uuid())
                                                .executes(context -> adminBankLevelDelete(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "bankId")
                                                ))
                                        )
                                )
                        )
                        .then(buildAdminBankPremiseLiteral())
                        .then(buildAdminBankViewingRoomLiteral())
                )
                .then(Commands.literal("shop")
                        .then(Commands.literal("view")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminShopViewPlayer(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("level")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("shopId", UuidArgument.uuid())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                        .executes(context -> adminShopLevelSet(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "shopId"),
                                                                IntegerArgumentType.getInteger(context, "level")
                                                        ))
                                                )
                                        )
                                )
                                .then(Commands.literal("add")
                                        .then(Commands.argument("shopId", UuidArgument.uuid())
                                                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                                                        .executes(context -> adminShopLevelAdd(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "shopId"),
                                                                IntegerArgumentType.getInteger(context, "levels")
                                                        ))
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("shopId", UuidArgument.uuid())
                                                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                                                        .executes(context -> adminShopLevelRemove(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "shopId"),
                                                                IntegerArgumentType.getInteger(context, "levels")
                                                        ))
                                                )
                                        )
                                )
                        )
                )
                .then(buildAdminHeistLiteral())
                .then(Commands.literal("flags")
                        .executes(context -> adminListFlags(context.getSource()))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminHeistLiteral() {
        return Commands.literal("heist")
                .then(Commands.literal("list")
                        .executes(context -> adminHeistList(context.getSource())))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("sessionId", UuidArgument.uuid())
                                .executes(context -> adminHeistInspect(context.getSource(),
                                        UuidArgument.getUuid(context, "sessionId")))))
                .then(Commands.literal("abort")
                        .then(Commands.argument("sessionId", UuidArgument.uuid())
                                .executes(context -> adminHeistFinish(context.getSource(),
                                        UuidArgument.getUuid(context, "sessionId"), "Administrator aborted the heist."))))
                .then(Commands.literal("recover")
                        .then(Commands.argument("sessionId", UuidArgument.uuid())
                                .executes(context -> adminHeistFinish(context.getSource(),
                                        UuidArgument.getUuid(context, "sessionId"), "Administrator recovered the heist session."))))
                .then(Commands.literal("clearcooldown")
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> adminHeistClearPlayerCooldown(
                                                context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("bank")
                                .then(Commands.argument("bankId", UuidArgument.uuid())
                                        .executes(context -> adminHeistClearBankCooldown(
                                                context.getSource(), UuidArgument.getUuid(context, "bankId"))))))
                .then(Commands.literal("clearvictimprotection")
                        .then(Commands.argument("playerId", UuidArgument.uuid())
                                .executes(context -> adminHeistClearVictim(context.getSource(),
                                        UuidArgument.getUuid(context, "playerId")))))
                .then(Commands.literal("allowinsider")
                        .executes(context -> adminHeistSetInsiderBypass(
                                context.getSource(), context.getSource().getPlayer(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> adminHeistSetInsiderBypass(
                                        context.getSource(), EntityArgument.getPlayer(context, "player"), true))))
                .then(Commands.literal("disallowinsider")
                        .executes(context -> adminHeistSetInsiderBypass(
                                context.getSource(), context.getSource().getPlayer(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> adminHeistSetInsiderBypass(
                                        context.getSource(), EntityArgument.getPlayer(context, "player"), false))));
    }

    private static int adminHeistList(CommandSourceStack source) {
        if (!requireAdminPermission(source)) return 0;
        var sessions = HeistSavedData.get(source.getServer()).sessions();
        MutableComponent body = Component.empty();
        if (sessions.isEmpty()) body.append(Component.literal("No planned or active heists."));
        for (HeistSession session : sessions) {
            body.append(Component.literal(session.id() + " | " + session.phase() + " | "
                    + (session.bankName().isBlank() ? "No target" : session.bankName())
                    + " | crew " + session.members().size() + "\n"));
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "Heist Sessions", body));
        return 1;
    }

    private static int adminHeistInspect(CommandSourceStack source, UUID sessionId) {
        if (!requireAdminPermission(source)) return 0;
        HeistSession session = HeistSavedData.get(source.getServer()).session(sessionId);
        if (session == null) {
            source.sendSystemMessage(Component.literal("Heist session not found: " + sessionId));
            return 0;
        }
        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "Heist " + session.id(), Component.literal(
                "Phase: " + session.phase() + "\nBank: " + session.bankName() + " (" + session.bankId() + ")"
                        + "\nPremise: " + session.premiseId() + "\nCrew: " + session.members().keySet()
                        + "\nAlarm: " + session.alarmed() + " " + session.alarmReason()
                        + "\nLoot cents: " + session.totalLootCents())));
        return 1;
    }

    private static int adminHeistFinish(CommandSourceStack source, UUID sessionId, String reason) {
        if (!requireAdminPermission(source)) return 0;
        HeistSession session = HeistSavedData.get(source.getServer()).session(sessionId);
        if (session == null) {
            source.sendSystemMessage(Component.literal("Heist session not found: " + sessionId));
            return 0;
        }
        HeistService.finish(source.getServer(), session, false, reason);
        source.sendSystemMessage(Component.literal("Heist assets restored and session closed: " + sessionId));
        return 1;
    }

    private static int adminHeistClearPlayerCooldown(CommandSourceStack source, ServerPlayer player) {
        if (!requireAdminPermission(source)) return 0;
        HeistSavedData.get(source.getServer()).setPlayerCooldown(player.getUUID(), 0L);
        source.sendSystemMessage(Component.literal("Cleared player heist cooldown for "
                + player.getGameProfile().getName() + " (" + player.getUUID() + ")"));
        return 1;
    }

    private static int adminHeistClearBankCooldown(CommandSourceStack source, UUID bankId) {
        if (!requireAdminPermission(source)) return 0;
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        Bank bank = resolveBankByName(centralBank, bankId.toString());
        if (bank == null) {
            source.sendSystemMessage(Component.literal("Bank not found: " + bankId));
            return 0;
        }
        HeistSavedData.get(source.getServer()).setBankCooldown(bankId, 0L);
        source.sendSystemMessage(Component.literal("Cleared bank heist cooldown for "
                + bank.getBankName() + " (" + bankId + ")"));
        return 1;
    }

    private static int adminHeistClearVictim(CommandSourceStack source, UUID playerId) {
        if (!requireAdminPermission(source)) return 0;
        HeistSavedData.get(source.getServer()).setVictimProtection(playerId, 0L);
        source.sendSystemMessage(Component.literal("Cleared heist victim protection for " + playerId));
        return 1;
    }

    private static int adminHeistSetInsiderBypass(CommandSourceStack source, ServerPlayer player, boolean enabled) {
        if (!requireAdminPermission(source)) return 0;
        if (player == null) {
            source.sendSystemMessage(Component.literal("A player target is required when this command is run from console."));
            return 0;
        }
        HeistService.Result result = HeistService.setDevInsiderBypass(player, enabled);
        source.sendSystemMessage(Component.literal(result.message()));
        if (source.getPlayer() != player) {
            player.sendSystemMessage(Component.literal(result.message()));
        }
        return result.success() ? 1 : 0;
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildAdminBankPremiseLiteral() {
        return Commands.literal("premise")
                .executes(context -> adminPremiseHelp(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> adminPremiseList(context.getSource(), ""))
                        .then(Commands.argument("bank", StringArgumentType.greedyString())
                                .executes(context -> adminPremiseList(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bank")))
                        )
                )
                .then(Commands.literal("info")
                        .then(Commands.argument("premiseId", StringArgumentType.word())
                                .executes(context -> adminPremiseInfo(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "premiseId")))
                        )
                )
                .then(Commands.literal("add")
                        .then(Commands.argument("bank", StringArgumentType.greedyString())
                                .executes(context -> adminPremiseAdd(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "bank")))
                        )
                )
                .then(Commands.literal("delete")
                        .then(Commands.argument("premiseId", StringArgumentType.word())
                                .executes(context -> adminPremiseDelete(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "premiseId")))
                        )
                )
                .then(Commands.literal("mode")
                        .then(Commands.argument("premiseId", StringArgumentType.word())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .executes(context -> adminPremiseMode(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "premiseId"),
                                                StringArgumentType.getString(context, "mode")))
                                )
                        )
                )
                .then(Commands.literal("exit")
                        .then(Commands.argument("premiseId", StringArgumentType.word())
                                .executes(context -> adminPremiseExit(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "premiseId")))
                        )
                )
                .then(Commands.literal("cancel")
                        .executes(context -> adminPremiseCancel(context.getSource()))
                );
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildAdminBankViewingRoomLiteral() {
        return Commands.literal("viewingroom")
                .executes(context -> adminViewingRoomHelp(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> adminViewingRoomList(context.getSource(), ""))
                        .then(Commands.argument("bank", StringArgumentType.greedyString())
                                .executes(context -> adminViewingRoomList(context.getSource(),
                                        StringArgumentType.getString(context, "bank")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("roomId", StringArgumentType.word())
                                .executes(context -> adminViewingRoomInfo(context.getSource(),
                                        StringArgumentType.getString(context, "roomId")))))
                .then(Commands.literal("claim")
                        .then(Commands.argument("bank", StringArgumentType.greedyString())
                                .executes(context -> adminViewingRoomClaim(context.getSource(),
                                        StringArgumentType.getString(context, "bank")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("roomId", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> adminViewingRoomRename(context.getSource(),
                                                StringArgumentType.getString(context, "roomId"),
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("anchor")
                        .then(Commands.argument("roomId", StringArgumentType.word())
                                .then(Commands.argument("anchor", StringArgumentType.word())
                                        .executes(context -> adminViewingRoomAnchor(context.getSource(),
                                                StringArgumentType.getString(context, "roomId"),
                                                StringArgumentType.getString(context, "anchor"))))))
                .then(Commands.literal("validate")
                        .then(Commands.argument("roomId", StringArgumentType.word())
                                .executes(context -> adminViewingRoomValidate(context.getSource(),
                                        StringArgumentType.getString(context, "roomId")))))
                .then(Commands.literal("suspend")
                        .then(Commands.argument("roomId", StringArgumentType.word())
                                .executes(context -> adminViewingRoomSuspended(context.getSource(),
                                        StringArgumentType.getString(context, "roomId"), true))))
                .then(Commands.literal("reactivate")
                        .then(Commands.argument("roomId", StringArgumentType.word())
                                .executes(context -> adminViewingRoomSuspended(context.getSource(),
                                        StringArgumentType.getString(context, "roomId"), false))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("roomId", StringArgumentType.word())
                                .executes(context -> adminViewingRoomDelete(context.getSource(),
                                        StringArgumentType.getString(context, "roomId")))))
                .then(Commands.literal("cancel")
                        .executes(context -> adminViewingRoomCancel(context.getSource())));
    }

    private static int adminViewingRoomList(CommandSourceStack source, String bankQuery) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            return premiseError(source, "Central bank data is not available.");
        }
        List<Bank> banks = bankQuery == null || bankQuery.isBlank()
                ? allBanks(centralBank)
                : Optional.ofNullable(resolveBankByName(centralBank, bankQuery)).map(List::of).orElse(List.of());
        if (banks.isEmpty()) {
            return premiseError(source, "Bank not found: " + bankQuery);
        }
        MutableComponent body = Component.empty();
        int total = 0;
        for (Bank bank : banks) {
            List<ViewingRoomState> states = ViewingRoomService.states(source.getServer(), centralBank,
                    bank.getBankId(), SafeBoxViewingCoordinator.activeRoomIds(source.getServer()));
            body.append(Component.literal(bank.getBankName() + " [" + bank.getBankId() + "]\n")
                    .withStyle(ChatFormatting.AQUA));
            for (ViewingRoomState state : states) {
                total++;
                body.append(clickableViewingRoomId(state.room().id().toString()))
                        .append(Component.literal(" | " + state.room().name() + " | " + state.status().name()
                                + " | premise " + state.room().premiseId() + "\n")
                                .withStyle(ChatFormatting.GRAY));
            }
        }
        if (total == 0) {
            body.append(Component.literal("No viewing rooms are configured.").withStyle(ChatFormatting.YELLOW));
        }
        source.sendSystemMessage(adminPremisePanel("Viewing Rooms", body));
        return 1;
    }

    private static int adminViewingRoomInfo(CommandSourceStack source, String roomIdRaw) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        AdminViewingRoomLookup lookup = uniqueViewingRoom(source, roomIdRaw);
        if (lookup == null) {
            return 0;
        }
        ViewingRoomState state = ViewingRoomService.findState(source.getServer(), lookup.centralBank(),
                lookup.bank().getBankId(), lookup.room().id(),
                SafeBoxViewingCoordinator.activeRoomIds(source.getServer())).orElse(null);
        String reasons = state == null || state.reasons().isEmpty()
                ? "None" : String.join("; ", state.reasons());
        MutableComponent body = Component.empty()
                .append(clickableViewingRoomId(lookup.room().id().toString())).append(Component.literal("\n"))
                .append(Component.literal("Name: " + lookup.room().name() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Bank: " + lookup.bank().getBankName() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Premise: " + lookup.room().premiseId() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Bounds: " + lookup.room().bounds() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Status: " + (state == null ? "UNKNOWN" : state.status().name())
                        + "\nReasons: " + reasons).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(adminPremisePanel("Viewing Room", body));
        return 1;
    }

    private static int adminViewingRoomClaim(CommandSourceStack source, String bankQuery) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        Bank bank = resolveBankByName(centralBank, bankQuery);
        if (player == null || centralBank == null || bank == null) {
            return premiseError(source, player == null
                    ? "Run this command as a player to use the viewing-room claim tool."
                    : "Bank not found: " + bankQuery);
        }
        SafetyDepositBoxService.ActionResult result = SafetyDepositBoxService.startViewingRoomClaimToolSession(
                source.getServer(), centralBank, player, bank.getBankId(), true);
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminViewingRoomAnchor(CommandSourceStack source,
                                              String roomIdRaw,
                                              String anchorRaw) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        AdminViewingRoomLookup lookup = uniqueViewingRoom(source, roomIdRaw);
        ViewingRoomService.AnchorKind kind = ViewingRoomService.AnchorKind.parse(anchorRaw);
        if (player == null || lookup == null || kind == null) {
            return premiseError(source, player == null
                    ? "Run this command as a player to capture a viewing-room anchor."
                    : "Anchor must be customer, teller, or display.");
        }
        SafeBoxViewingCoordinator.cancelRoom(source.getServer(), lookup.room().id());
        SafetyDepositBoxService.ActionResult result = SafetyDepositBoxService.startViewingRoomAnchorToolSession(
                source.getServer(), lookup.centralBank(), player, lookup.bank().getBankId(),
                lookup.room().id(), kind, true);
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminViewingRoomRename(CommandSourceStack source,
                                              String roomIdRaw,
                                              String name) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        AdminViewingRoomLookup lookup = uniqueViewingRoom(source, roomIdRaw);
        if (lookup == null) {
            return 0;
        }
        SafeBoxViewingCoordinator.cancelRoom(source.getServer(), lookup.room().id());
        ViewingRoomService.MutationResult result = ViewingRoomService.rename(
                lookup.centralBank(), lookup.bank().getBankId(), lookup.room().id(), name);
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminViewingRoomSuspended(CommandSourceStack source,
                                                 String roomIdRaw,
                                                 boolean suspended) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        AdminViewingRoomLookup lookup = uniqueViewingRoom(source, roomIdRaw);
        if (lookup == null) {
            return 0;
        }
        SafeBoxViewingCoordinator.cancelRoom(source.getServer(), lookup.room().id());
        ViewingRoomService.MutationResult result = ViewingRoomService.setAdminSuspended(
                lookup.centralBank(), lookup.bank().getBankId(), lookup.room().id(), suspended);
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminViewingRoomDelete(CommandSourceStack source, String roomIdRaw) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        AdminViewingRoomLookup lookup = uniqueViewingRoom(source, roomIdRaw);
        if (lookup == null) {
            return 0;
        }
        int cancelled = SafeBoxViewingCoordinator.cancelRoom(source.getServer(), lookup.room().id());
        ViewingRoomService.MutationResult result = ViewingRoomService.delete(
                lookup.centralBank(), lookup.bank().getBankId(), lookup.room().id());
        String message = result.message() + (cancelled > 0 ? " Cancelled " + cancelled + " active session(s)." : "");
        return premiseResult(source, result.success(), message);
    }

    private static int adminViewingRoomValidate(CommandSourceStack source, String roomIdRaw) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        AdminViewingRoomLookup lookup = uniqueViewingRoom(source, roomIdRaw);
        if (lookup == null) {
            return 0;
        }
        ViewingRoomState state = ViewingRoomService.findState(source.getServer(), lookup.centralBank(),
                lookup.bank().getBankId(), lookup.room().id(),
                SafeBoxViewingCoordinator.activeRoomIds(source.getServer())).orElse(null);
        return premiseResult(source, state != null && (state.ready()
                        || state.status() == net.austizz.ultimatebankingsystem.bank.safebox.viewing.ViewingRoomStatus.OCCUPIED),
                state == null ? "Viewing room validation failed."
                        : state.status().name() + (state.reasons().isEmpty() ? "" : ": " + String.join("; ", state.reasons())));
    }

    private static int adminViewingRoomCancel(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return premiseError(source, "Run this command as a player to cancel a viewing-room picker.");
        }
        SafetyDepositBoxService.ActionResult result = SafetyDepositBoxService.finishSafeAreaClaimToolSession(
                player, "Administrator viewing-room picker cancelled.");
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminViewingRoomHelp(CommandSourceStack source) {
        MutableComponent body = Component.empty()
                .append(Component.literal("/ubs admin bank viewingroom list [bank]\n"))
                .append(Component.literal("/ubs admin bank viewingroom info <room ID>\n"))
                .append(Component.literal("/ubs admin bank viewingroom claim <bank>\n"))
                .append(Component.literal("/ubs admin bank viewingroom rename <room ID> <name>\n"))
                .append(Component.literal("/ubs admin bank viewingroom anchor <room ID> <customer|teller|display>\n"))
                .append(Component.literal("/ubs admin bank viewingroom validate <room ID>\n"))
                .append(Component.literal("/ubs admin bank viewingroom suspend|reactivate|delete <room ID>\n"))
                .append(Component.literal("/ubs admin bank viewingroom cancel"));
        source.sendSystemMessage(adminPremisePanel("Viewing Room Commands", body));
        return 1;
    }

    private static AdminViewingRoomLookup uniqueViewingRoom(CommandSourceStack source, String roomIdRaw) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdRaw == null ? "" : roomIdRaw.trim());
        } catch (IllegalArgumentException ignored) {
            premiseError(source, "Invalid viewing-room ID: " + roomIdRaw);
            return null;
        }
        List<AdminViewingRoomLookup> matches = new ArrayList<>();
        for (Bank bank : allBanks(centralBank)) {
            CompoundTag metadata = centralBank.getBankMetadata().get(bank.getBankId());
            if (metadata == null) {
                continue;
            }
            ViewingRoomNbtStore.read(metadata).stream()
                    .filter(room -> room.id().equals(roomId))
                    .forEach(room -> matches.add(new AdminViewingRoomLookup(centralBank, bank, room)));
        }
        if (matches.size() != 1) {
            premiseError(source, matches.isEmpty()
                    ? "Viewing room not found: " + roomIdRaw
                    : "Viewing-room ID is ambiguous: " + roomIdRaw);
            return null;
        }
        return matches.getFirst();
    }

    private static Component clickableViewingRoomId(String roomId) {
        String value = roomId == null ? "" : roomId;
        return Component.literal(value).setStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to copy viewing-room ID"))));
    }

    private record AdminViewingRoomLookup(CentralBank centralBank,
                                          Bank bank,
                                          ViewingRoomSnapshot room) {
    }

    private static int adminPremiseList(CommandSourceStack source, String bankQuery) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            return premiseError(source, "Central bank data is not available.");
        }
        if (bankQuery == null || bankQuery.isBlank()) {
            MutableComponent body = Component.empty();
            int total = 0;
            for (Bank bank : allBanks(centralBank)) {
                int count = premisePayloads(source.getServer(), centralBank, bank).size();
                total += count;
                body.append(Component.literal(bank.getBankName() + " [" + bank.getBankId()
                        + "]: " + count + " premise(s)\n").withStyle(ChatFormatting.GRAY));
            }
            if (total == 0) {
                body.append(Component.literal("No bank premises are configured.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            source.sendSystemMessage(adminPremisePanel("Bank Premise Summary", body));
            return 1;
        }

        Bank bank = resolveBankByName(centralBank, bankQuery);
        if (bank == null) {
            return premiseError(source, "Bank not found: " + bankQuery);
        }
        List<OwnerPcPremisePayload> premises = premisePayloads(
                source.getServer(), centralBank, bank);
        MutableComponent body = Component.empty();
        body.append(Component.literal(bank.getBankName() + " [" + bank.getBankId() + "]\n")
                .withStyle(ChatFormatting.AQUA));
        if (premises.isEmpty()) {
            body.append(Component.literal("No premises configured.")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            for (OwnerPcPremisePayload premise : premises) {
                body.append(clickablePremiseId(premise.premiseId()));
                body.append(Component.literal(" | " + premise.status().name()
                        + " | " + premise.mode().name()
                        + " | areas " + premise.safeAreaCount()
                        + " | vaults " + premise.readyVaultCount() + "/" + premise.vaultCount()
                        + "\n").withStyle(ChatFormatting.GRAY));
            }
        }
        source.sendSystemMessage(adminPremisePanel("Bank Premises", body));
        return 1;
    }

    private static int adminPremiseInfo(CommandSourceStack source, String premiseId) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        AdminPremiseLookup lookup = uniquePremise(source, premiseId);
        if (lookup == null) {
            return 0;
        }
        OwnerPcPremisePayload premise = lookup.premise();
        String blockers = premise.deleteBlockers().isEmpty()
                ? "None"
                : String.join(", ", premise.deleteBlockers().stream().map(Enum::name).toList());
        MutableComponent body = Component.empty()
                .append(Component.literal("Bank: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(lookup.bank().getBankName() + " ["
                        + lookup.bank().getBankId() + "]\n").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("Premise ID: ").withStyle(ChatFormatting.GRAY))
                .append(clickablePremiseId(premise.premiseId()))
                .append(Component.literal("\n"))
                .append(Component.literal("Status: " + premise.status().name()
                        + " | Mode: " + premise.mode().name() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Bounds: " + premise.dimension() + " "
                        + premise.minX() + "," + premise.minY() + "," + premise.minZ()
                        + " -> " + premise.maxX() + "," + premise.maxY() + "," + premise.maxZ()
                        + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Exit: " + premise.exitDimension() + " "
                        + premise.exitX() + "," + premise.exitY() + "," + premise.exitZ()
                        + " yaw " + premise.exitYaw() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Safe areas: " + premise.safeAreaCount()
                        + " | Ready vaults: " + premise.readyVaultCount() + "/"
                        + premise.vaultCount() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Owner-PC blockers: " + blockers)
                        .withStyle(premise.canDelete() ? ChatFormatting.GREEN : ChatFormatting.RED));
        source.sendSystemMessage(adminPremisePanel("Premise Details", body));
        return 1;
    }

    private static int adminPremiseAdd(CommandSourceStack source, String bankQuery) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return premiseError(source, "Run this command as a player to use the premise claim tool.");
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        Bank bank = resolveBankByName(centralBank, bankQuery);
        if (bank == null) {
            return premiseError(source, "Bank not found: " + bankQuery);
        }
        centralBank.getOrCreateBankMetadata(bank.getBankId());
        SafetyDepositBoxService.ActionResult result = OwnerPcPremiseAdminService.startClaimSession(
                source.getServer(), centralBank, player, bank.getBankId(),
                SafeClaimToolPurpose.PREMISE_CREATE, "");
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminPremiseDelete(CommandSourceStack source, String premiseId) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        AdminPremiseLookup lookup = uniquePremise(source, premiseId);
        if (lookup == null) {
            return 0;
        }
        OwnerPcPremiseService.Result result = OwnerPcPremiseAdminService.forceDelete(
                source.getServer(), lookup.centralBank(), source.getPlayer(),
                lookup.bank().getBankId(), lookup.premise().premiseId());
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminPremiseMode(CommandSourceStack source,
                                        String premiseId,
                                        String modeRaw) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        SafePremiseMode mode = SafePremiseMode.parse(
                modeRaw == null ? "" : modeRaw.replace('-', '_'));
        if (mode == null) {
            return premiseError(source, "Mode must be PUBLIC or STAFF_ONLY.");
        }
        AdminPremiseLookup lookup = uniquePremise(source, premiseId);
        if (lookup == null) {
            return 0;
        }
        OwnerPcPremiseService.Result result = OwnerPcPremiseAdminService.execute(
                source.getServer(), lookup.centralBank(), source.getPlayer(),
                new OwnerPcPremiseActionPayload(
                        lookup.bank().getBankId(), OwnerPcPremiseActionPayload.Action.SET_MODE,
                        lookup.premise().premiseId(), mode));
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminPremiseExit(CommandSourceStack source, String premiseId) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return premiseError(source, "Run this command as a player to use the premise exit tool.");
        }
        AdminPremiseLookup lookup = uniquePremise(source, premiseId);
        if (lookup == null) {
            return 0;
        }
        SafetyDepositBoxService.ActionResult result = OwnerPcPremiseAdminService.startClaimSession(
                source.getServer(), lookup.centralBank(), player, lookup.bank().getBankId(),
                SafeClaimToolPurpose.PREMISE_EXIT_EDIT, lookup.premise().premiseId());
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminPremiseCancel(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return premiseError(source, "Run this command as a player to cancel a claim tool.");
        }
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.finishSafeAreaClaimToolSession(
                        player, "Administrator premise claim cancelled.");
        return premiseResult(source, result.success(), result.message());
    }

    private static int adminPremiseHelp(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 0;
        }
        MutableComponent body = Component.empty()
                .append(Component.literal("/ubs admin bank premise list [bank name or ID]\n"))
                .append(Component.literal("/ubs admin bank premise info <premise ID>\n"))
                .append(Component.literal("/ubs admin bank premise add <bank name or ID>\n"))
                .append(Component.literal("/ubs admin bank premise delete <premise ID>\n"))
                .append(Component.literal("/ubs admin bank premise mode <premise ID> <public|staff_only>\n"))
                .append(Component.literal("/ubs admin bank premise exit <premise ID>\n"))
                .append(Component.literal("/ubs admin bank premise cancel"));
        source.sendSystemMessage(adminPremisePanel("Premise Commands", body));
        return 1;
    }

    private static AdminPremiseLookup uniquePremise(CommandSourceStack source, String premiseId) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            premiseError(source, "Central bank data is not available.");
            return null;
        }
        List<AdminPremiseLookup> matches = new ArrayList<>();
        for (Bank bank : allBanks(centralBank)) {
            for (OwnerPcPremisePayload premise : premisePayloads(
                    source.getServer(), centralBank, bank)) {
                if (premise.premiseId().equalsIgnoreCase(premiseId == null ? "" : premiseId.trim())) {
                    matches.add(new AdminPremiseLookup(centralBank, bank, premise));
                }
            }
        }
        if (matches.isEmpty()) {
            premiseError(source, "Premise not found: " + premiseId);
            return null;
        }
        if (matches.size() > 1) {
            premiseError(source, "Premise ID is ambiguous across banks: " + premiseId);
            return null;
        }
        return matches.getFirst();
    }

    private static List<OwnerPcPremisePayload> premisePayloads(MinecraftServer server,
                                                                CentralBank centralBank,
                                                                Bank bank) {
        if (server == null || centralBank == null || bank == null) {
            return List.of();
        }
        CompoundTag metadata = centralBank.getBankMetadata().get(bank.getBankId());
        return metadata == null
                ? List.of()
                : OwnerPcPremisePayloadBuilder.build(server, metadata, bank.getBankId());
    }

    private static List<Bank> allBanks(CentralBank centralBank) {
        if (centralBank == null) {
            return List.of();
        }
        List<Bank> banks = new ArrayList<>();
        banks.add(centralBank);
        centralBank.getBanks().values().stream()
                .filter(bank -> bank != null && !centralBank.getBankId().equals(bank.getBankId()))
                .sorted(Comparator.comparing(Bank::getBankName, String.CASE_INSENSITIVE_ORDER))
                .forEach(banks::add);
        return List.copyOf(banks);
    }

    private static int premiseResult(CommandSourceStack source, boolean success, String message) {
        source.sendSystemMessage(Component.literal(message == null ? "" : message)
                .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
        return success ? 1 : 0;
    }

    private static int premiseError(CommandSourceStack source, String message) {
        return premiseResult(source, false, message);
    }

    private static Component adminPremisePanel(String title, Component body) {
        return Component.literal("Ultimate Banking System - ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(title + "\n").withStyle(ChatFormatting.AQUA))
                .append(body);
    }

    private static Component clickablePremiseId(String premiseId) {
        String value = premiseId == null ? "" : premiseId;
        return Component.literal(value).setStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to copy premise ID"))));
    }

    private record AdminPremiseLookup(CentralBank centralBank,
                                      Bank bank,
                                      OwnerPcPremisePayload premise) {
    }

    private static int showCentralBankPanel(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        int bankCount = centralBank.getBanks() != null ? centralBank.getBanks().size() : 0;

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Name: §e" + centralBank.getBankName() + "\n"));
        body.append(moneyLiteral("§7Bank ID: §f" + centralBank.getBankId() + "\n"));
        body.append(moneyLiteral("§7Reserve: §a" + centralBank.getBankReserve() + "\n"));
        body.append(moneyLiteral("§7Interest Rate: §e" + centralBank.getInterestRate() + "\n"));
        body.append(moneyLiteral("§7Registered Banks: §b" + bankCount + "\n"));

        body.append(moneyLiteral("\n§7Actions:\n"));
        body.append(moneyLiteral("§f§l[§aSave§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ubs bank save"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to save bank data")))));
        body.append(moneyLiteral(" "));
        body.append(moneyLiteral("§f§l[§eRename§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs bank rename "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Suggest /ubs bank rename <name>")))));
        body.append(moneyLiteral("\n"));
        body.append(moneyLiteral("§f§l[§6Set Interest§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs centralbank interest set "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Suggest /ubs centralbank interest set <rate>")))));
        body.append(moneyLiteral("\n"));
        body.append(moneyLiteral("§f§l[§6Spot Market§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs centralbank market set gold "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Seed Central Bank gold or silver spot prices")))));
        body.append(moneyLiteral("\n"));
        body.append(moneyLiteral("§f§l[§2Deposit§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs money deposit <accountId> <amount>"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Suggest deposit to an account")))));
        body.append(moneyLiteral(" "));
        body.append(moneyLiteral("§f§l[§cWithdraw§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs money withdraw <accountId> <amount>"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Suggest withdraw from an account")))));
        body.append(moneyLiteral("\n"));
        body.append(moneyLiteral("§f§l[§bAdmin View§f§l]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ubs admin view "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Suggest /ubs admin view <player>")))));

        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "§eCentral Bank", body));
        return 1;
    }

    private static int centralBankMarketShow(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Global spot quotes published by the Central Bank.\n\n"));
        for (CommodityMarketService.MarketQuote quote : CommodityMarketService.quotes(source.getServer())) {
            String spot = quote.priced() ? MoneyText.abbreviateRoundedWithDollar(quote.spot()) : "Unpriced";
            String bid = quote.priced() ? MoneyText.abbreviateRoundedWithDollar(quote.bid()) : "-";
            String ask = quote.priced() ? MoneyText.abbreviateRoundedWithDollar(quote.ask()) : "-";
            String change = quote.priced()
                    ? (quote.changePercent().signum() >= 0 ? "+" : "")
                    + quote.changePercent().stripTrailingZeros().toPlainString() + "%"
                    : "Seed required";
            body.append(moneyLiteral("§8- §e" + quote.displayName()
                    + " §7Spot: §a" + spot
                    + " §7Bid/Ask: §f" + bid + " / " + ask
                    + " §7Change: §b" + change + "\n"));
            body.append(moneyLiteral("  §8" + quote.formula() + " Source: " + quote.source() + "\n"));
        }
        body.append(moneyLiteral("\n§7Set first spot prices per ingot:\n"
                + "§f/centralbank market set gold <price>\n"
                + "§f/centralbank market set silver <price>"));
        source.sendSystemMessage(ubsPanel(ChatFormatting.GOLD, "Central Bank Spot Market", body));
        return 1;
    }

    private static int centralBankMarketSet(CommandSourceStack source, String commodityRaw, String priceRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        String cleaned = priceRaw == null ? "" : priceRaw.replace("$", "").replace(",", "").trim();
        BigDecimal price = parsePositiveAmount(source, cleaned);
        if (price == null) {
            return 1;
        }
        try {
            CommodityMarketService.MarketQuote quote = CommodityMarketService.setSpot(
                    source.getServer(),
                    commodityRaw,
                    price,
                    source.getTextName()
            );
            String spot = MoneyText.abbreviateRoundedWithDollar(quote.spot());
            source.sendSystemMessage(ubsPanel(ChatFormatting.GREEN, "Central Bank Spot Market",
                    moneyLiteral("§a" + quote.displayName() + " spot set to §6" + spot
                            + "§a per " + quote.unitName() + ".\n§7Phone Spot Market app now reflects this quote.")));
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendSystemMessage(moneyLiteral("§c" + ex.getMessage()));
            return 1;
        }
    }

    private static int setCentralBankInterestRate(CommandSourceStack source, String rateStr) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        double rate;
        try {
            rate = Double.parseDouble(rateStr);
        } catch (NumberFormatException e) {
            source.sendSystemMessage(moneyLiteral("§cThe rate '§e" + rateStr + "§c' is not a valid number."));
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        double before = centralBank.getInterestRate();
        centralBank.setInterestRate(rate);
        double after = centralBank.getInterestRate();

        if (Double.compare(before, after) == 0 && Double.compare(before, rate) != 0) {
            source.sendSystemMessage(moneyLiteral(
                    "§cInterest rate not changed. Rate must be within allowed range. Current: §e" + before
            ));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral(
                "§aCentral Bank interest rate updated: §e" + before + " §7-> §e" + after
        ));
        return 1;
    }

    private static int centralBankRateShow(CommandSourceStack source) {
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        double rate = centralBank.getFederalFundsRate();
        double floor = rate * Config.SAVINGS_RATE_FLOOR_MULTIPLIER.get();
        double ceiling = rate * Config.SAVINGS_RATE_CEILING_MULTIPLIER.get();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Federal Funds Rate: §e" + rate + "%\n"));
        body.append(moneyLiteral("§7Implied Savings Floor: §f" + floor + "%\n"));
        body.append(moneyLiteral("§7Implied Savings Ceiling: §f" + ceiling + "%\n"));
        body.append(moneyLiteral("§7Allowed FFR Range: §f" + Config.MIN_FEDERAL_FUNDS_RATE.get()
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        double requested;
        try {
            requested = Double.parseDouble(rateRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid rate: " + rateRaw));
            return 1;
        }

        double previous = centralBank.getFederalFundsRate();
        if (!centralBank.setFederalFundsRate(requested)) {
            source.sendSystemMessage(moneyLiteral(
                    "§cRate out of bounds. Allowed range: "
                            + Config.MIN_FEDERAL_FUNDS_RATE.get()
                            + "% to " + Config.MAX_FEDERAL_FUNDS_RATE.get() + "%"
            ));
            return 1;
        }

        double next = centralBank.getFederalFundsRate();
        source.sendSystemMessage(moneyLiteral(
                "§aFederal Funds Rate updated: §e" + previous + "% §7-> §e" + next + "%"
        ));
        source.getServer().getPlayerList().broadcastSystemMessage(
                moneyLiteral("§6[UBS] Federal Funds Rate updated to §e" + next + "%§6 by " + source.getTextName()),
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        BigDecimal amount = parsePositiveAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }
        BigDecimal before = centralBank.getDeclaredReserve();
        BigDecimal after = inject ? before.add(amount) : before.subtract(amount);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            source.sendSystemMessage(moneyLiteral("§cOperation rejected: Central Bank reserve cannot go negative."));
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

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        List<CompoundTag> entries = centralBank.getOpenMarketOperations().values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Open Market Operations: §b" + entries.size() + "\n\n"));
        if (entries.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (int i = Math.max(0, entries.size() - Config.OMO_HISTORY_LIMIT.get()); i < entries.size(); i++) {
                CompoundTag tag = entries.get(i);
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
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
                source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
                return 1;
            }
            banks = List.of(bank);
        }

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Audited Banks: §b" + banks.size() + "\n\n"));
        for (Bank bank : banks) {
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            BigDecimal deposits = bank.getTotalDeposits();
            BigDecimal reserve = bank.getDeclaredReserve();
            BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                    ? reserve.divide(deposits, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.valueOf(100);
            long breachStart = metadata.contains("reserveBreachStartTick") ? metadata.getLong("reserveBreachStartTick") : -1L;
            body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
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
        body.append(moneyLiteral("§7Total Circulation: §a$" + totalCirculation.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Total Central+Bank Reserves: §a$" + totalReserves.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Active Player Banks: §b" + activeBanks + "\n"));
        body.append(moneyLiteral("§7Avg Reserve Ratio: §e" + avgReserveRatio.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%\n"));
        body.append(moneyLiteral("§7Banks WARNING/RESTRICTED/SUSPENDED: §c" + warningOrRestricted + "\n"));
        body.append(moneyLiteral("§7Outstanding Loans: §6$" + totalOutstandingLoans.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Federal Funds Rate: §e" + centralBank.getFederalFundsRate() + "%\n"));
        body.append(moneyLiteral("§7Net OMO Since Start: §f$" + netOmo.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Inter-bank Settlements (24h): §f" + settlements24h + "\n"));

        if (history) {
            body.append(moneyLiteral("\n§7Recent Snapshots:\n"));
            List<CompoundTag> snapshots = centralBank.getReportSnapshots().values().stream()
                    .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                    .toList();
            int start = Math.max(0, snapshots.size() - 10);
            for (int i = start; i < snapshots.size(); i++) {
                CompoundTag snap = snapshots.get(i);
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        var map = suspense ? centralBank.getSettlementSuspense() : centralBank.getSettlementLedger();
        List<CompoundTag> entries = map.values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                .toList();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Entries: §b" + entries.size() + "\n\n"));
        int limit = Math.max(1, Config.CLEARING_LEDGER_LIMIT.get());
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            CompoundTag tag = entries.get(i);
            body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        List<CompoundTag> apps = centralBank.getBankApplications().values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("createdMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Applications: §b" + apps.size() + "\n\n"));
        if (apps.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (CompoundTag app : apps) {
                UUID id = readUuidTag(app, "id");
                UUID applicant = readUuidTag(app, "applicant");
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        CompoundTag app = centralBank.getBankApplications().get(applicationId);
        if (app == null) {
            source.sendSystemMessage(moneyLiteral("§cApplication not found: " + applicationId));
            return 1;
        }
        if (!"PENDING".equalsIgnoreCase(app.getString("status"))) {
            source.sendSystemMessage(moneyLiteral("§cApplication is not pending."));
            return 1;
        }

        UUID applicantId = readUuidTag(app, "applicant");
        if (applicantId == null) {
            source.sendSystemMessage(moneyLiteral("§cApplication has no applicant."));
            return 1;
        }
        String bankName = app.getString("bankName");
        if (resolveBankByName(centralBank, bankName) != null) {
            source.sendSystemMessage(moneyLiteral("§cA bank with this name already exists."));
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
            source.sendSystemMessage(moneyLiteral("§cApplicant has no funding account available."));
            return 1;
        }

        BigDecimal creationFee = readDecimalTag(app, "creationFee");
        BigDecimal charterFee = readDecimalTag(app, "charterFee");
        BigDecimal totalFee = creationFee.add(charterFee);
        if (funding.getBalance().compareTo(totalFee) < 0) {
            source.sendSystemMessage(moneyLiteral("§cApplicant cannot afford required fees $" + totalFee.toPlainString()));
            return 1;
        }

        if (!funding.RemoveBalance(totalFee)) {
            source.sendSystemMessage(moneyLiteral("§cFailed to deduct applicant fees."));
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

        source.sendSystemMessage(moneyLiteral(
                "§aApproved application " + applicationId + " and created bank §e" + bankName
        ));
        ServerPlayer applicantOnline = source.getServer().getPlayerList().getPlayer(applicantId);
        if (applicantOnline != null) {
            applicantOnline.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        CompoundTag app = centralBank.getBankApplications().get(applicationId);
        if (app == null) {
            source.sendSystemMessage(moneyLiteral("§cApplication not found: " + applicationId));
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
            applicantOnline.sendSystemMessage(moneyLiteral(
                    "§cYour bank application was denied."
                            + ((reason == null || reason.isBlank()) ? "" : " Reason: " + reason)
                            + " §7You can appeal with /bank appeal <message>."
            ));
        }
        source.sendSystemMessage(moneyLiteral("§eApplication denied: " + applicationId));
        return 1;
    }

    private static int adminListAppeals(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        List<CompoundTag> appeals = centralBank.getBankAppeals().values().stream()
                .sorted(Comparator.comparingLong(tag -> tag.getLong("createdMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Appeals: §b" + appeals.size() + "\n\n"));
        for (CompoundTag appeal : appeals) {
            UUID id = readUuidTag(appeal, "id");
            UUID playerId = readUuidTag(appeal, "playerId");
            body.append(moneyLiteral(
                    "§8- §f" + id + "\n"
                            + "  §7Player: §f" + resolvePlayerName(source.getServer(), playerId) + "\n"
                            + "  §7Status: §f" + appeal.getString("status") + "\n"
                            + "  §7Message: §f" + appeal.getString("message") + "\n"
            ));
        }
        if (appeals.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        CompoundTag appeal = centralBank.getBankAppeals().get(appealId);
        if (appeal == null) {
            source.sendSystemMessage(moneyLiteral("§cAppeal not found: " + appealId));
            return 1;
        }

        AppealComplianceReview complianceReview = null;
        if (approve) {
            UUID bankId = readUuidTag(appeal, "bankId");
            Bank appealedBank = bankId == null ? null : centralBank.getBank(bankId);
            complianceReview = reviewApprovedAppealCompliance(source.getServer(), centralBank, appealedBank);
            appeal.putBoolean("compliancePassed", complianceReview.compliancePassed());
            appeal.putBoolean("statusChangedByReview", complianceReview.statusChanged());
            appeal.putString("statusBeforeReview", complianceReview.previousStatus());
            appeal.putString("statusAfterReview", complianceReview.nextStatus());
            appeal.putString("complianceReviewMessage", complianceReview.message());
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
            String reviewMessage = complianceReview == null ? "" : " " + complianceReview.message();
            player.sendSystemMessage(moneyLiteral(
                    (approve ? "§aYour bank appeal was approved." : "§cYour bank appeal was denied.")
                            + ((reason == null || reason.isBlank()) ? "" : " Reason: " + reason)
                            + reviewMessage
            ));
        }
        source.sendSystemMessage(moneyLiteral("§aAppeal " + appealId + " reviewed: " + (approve ? "APPROVED" : "DENIED")
                + (complianceReview == null ? "" : "\n§7" + complianceReview.message())));
        return 1;
    }

    private static AppealComplianceReview reviewApprovedAppealCompliance(MinecraftServer server,
                                                                         CentralBank centralBank,
                                                                         Bank bank) {
        if (centralBank == null || bank == null) {
            return new AppealComplianceReview(
                    false,
                    false,
                    false,
                    "UNKNOWN",
                    "UNKNOWN",
                    "Compliance review skipped: the appealed bank no longer exists."
            );
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String previousStatus = getBankStatus(centralBank, bank);
        BigDecimal reserve = bank.getDeclaredReserve();
        BigDecimal deposits = bank.getTotalDeposits();
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        boolean reserveCompliant = reserve.compareTo(minReserve) >= 0;

        if ("REVOKED".equals(previousStatus)) {
            return new AppealComplianceReview(
                    true,
                    reserveCompliant,
                    false,
                    previousStatus,
                    previousStatus,
                    "Compliance reviewed, but bank remains REVOKED. Reinstatement must be handled explicitly by an admin."
            );
        }
        if ("LOCKDOWN".equals(previousStatus)) {
            return new AppealComplianceReview(
                    true,
                    reserveCompliant,
                    false,
                    previousStatus,
                    previousStatus,
                    "Compliance reviewed, but bank remains in LOCKDOWN. Use the bank-run unlock flow when appropriate."
            );
        }

        long gameTime = currentOverworldGameTime(server);
        String nextStatus = previousStatus;
        if (!reserveCompliant) {
            long breachTick = metadata.contains("reserveBreachStartTick")
                    ? metadata.getLong("reserveBreachStartTick")
                    : gameTime;
            metadata.putLong("reserveBreachStartTick", breachTick);

            if (!"SUSPENDED".equals(previousStatus)) {
                long graceTicks = Math.max(20L, Config.BANK_RESERVE_GRACE_TICKS.get());
                nextStatus = (gameTime - breachTick) >= graceTicks ? "RESTRICTED" : "WARNING";
                metadata.putString("status", nextStatus);
            }
            centralBank.putBankMetadata(bank.getBankId(), metadata);
            return new AppealComplianceReview(
                    true,
                    false,
                    !nextStatus.equals(previousStatus),
                    previousStatus,
                    nextStatus,
                    "Appeal approved, but compliance still fails: reserve $" + reserve.toPlainString()
                            + " is below minimum $" + minReserve.toPlainString() + ". Status is " + nextStatus + "."
            );
        }

        metadata.remove("reserveBreachStartTick");
        if ("SUSPENDED".equals(previousStatus) && !isAppealClearableSuspension(metadata)) {
            centralBank.putBankMetadata(bank.getBankId(), metadata);
            String reason = metadata.getString("suspendReason");
            return new AppealComplianceReview(
                    true,
                    true,
                    false,
                    previousStatus,
                    previousStatus,
                    "Reserve compliance passes, but bank remains SUSPENDED"
                            + (reason == null || reason.isBlank() ? "." : " because: " + reason)
            );
        }

        if ("WARNING".equals(previousStatus)
                || "RESTRICTED".equals(previousStatus)
                || "SUSPENDED".equals(previousStatus)) {
            nextStatus = "ACTIVE";
            metadata.putString("status", nextStatus);
            metadata.remove("suspendReason");
            metadata.remove("suspendedAtMillis");
        }
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        return new AppealComplianceReview(
                true,
                true,
                !nextStatus.equals(previousStatus),
                previousStatus,
                nextStatus,
                !nextStatus.equals(previousStatus)
                        ? "Compliance passes; bank status restored from " + previousStatus + " to " + nextStatus + "."
                        : "Compliance passes; bank status remains " + nextStatus + "."
        );
    }

    private static boolean isAppealClearableSuspension(CompoundTag metadata) {
        if (metadata == null) {
            return true;
        }
        String reason = metadata.getString("suspendReason");
        if (reason == null || reason.isBlank()) {
            return true;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("reserve")
                || normalized.contains("compliance")
                || normalized.contains("audit")
                || normalized.contains("appeal");
    }

    private static int adminBankReserve(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
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
        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Bank: §e" + bank.getBankName() + "\n"));
        body.append(moneyLiteral("§7Status: §f" + getBankStatus(centralBank, bank) + "\n"));
        body.append(moneyLiteral("§7Owner: §f" + resolvePlayerName(source.getServer(), bank.getBankOwnerId()) + "\n"));
        body.append(moneyLiteral("§7Metadata:\n"));
        for (String key : metadata.getAllKeys()) {
            body.append(moneyLiteral("§8- §7" + key + ": §f" + metadata.get(key) + "\n"));
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "SUSPENDED");
        metadata.putString("suspendReason", reason == null ? "" : reason.trim());
        metadata.putLong("suspendedAtMillis", System.currentTimeMillis());
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral("§eSuspended bank: " + bank.getBankName()));
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "ACTIVE");
        metadata.remove("suspendReason");
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral("§aUnsuspended bank: " + bank.getBankName()));
        notifyBankOwner(source.getServer(), bank, "§aYour bank suspension has been lifted.");
        return 1;
    }

    private static int adminBankUnlock(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "ACTIVE");
        metadata.remove("lockdownUntilTick");
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral("§aLifted bank-run lockdown for " + bank.getBankName()));
        notifyBankOwner(source.getServer(), bank, "§aYour bank lockdown was lifted by an admin.");
        return 1;
    }

    private static int adminBankRunStatus(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found or protected: " + bankNameRaw));
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

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        boolean next = !metadata.getBoolean("rateExempt");
        metadata.putBoolean("rateExempt", next);
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
            return 1;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid amount: " + amountRaw));
            return 1;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            source.sendSystemMessage(moneyLiteral("§cOverride amount must be >= 0."));
            return 1;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("dailyCapOverride", amount.toPlainString());
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        source.sendSystemMessage(moneyLiteral(
                "§aDaily cap override for " + bank.getBankName() + " set to $" + amount.toPlainString()
        ));
        return 1;
    }

    private static int adminWaiveCharterFee(CommandSourceStack source, ServerPlayer player) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CHARTER_FEE_WAIVERS.put(player.getUUID(), System.currentTimeMillis());
        source.sendSystemMessage(moneyLiteral(
                "§aOne-time charter fee waiver granted to §e" + player.getName().getString()
        ));
        player.sendSystemMessage(moneyLiteral("§aYour next bank creation will waive the charter fee."));
        return 1;
    }

    private static int adminDeferLicenseRenewal(CommandSourceStack source, String bankNameRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        Bank bank = resolveBankByName(centralBank, bankNameRaw);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            source.sendSystemMessage(moneyLiteral("§cBank not found: " + bankNameRaw));
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
        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        List<CompoundTag> flags = centralBank.getSettlementSuspense().values().stream()
                .filter(tag -> tag.getString("reason").toUpperCase(Locale.ROOT).contains("FLAG"))
                .sorted(Comparator.comparingLong(tag -> tag.getLong("timestampMillis")))
                .toList();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Flagged events: §b" + flags.size() + "\n\n"));
        if (flags.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (CompoundTag flag : flags) {
                body.append(moneyLiteral(
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

    /**
     * Admin helper for forcing a bank to an explicit level milestone.
     */
    private static int adminBankLevelSet(CommandSourceStack source, UUID bankId, int level) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        BankLevelService.BankLevelResult result = BankLevelService.adminSetBankLevel(centralBank, bankId, level);
        source.sendSystemMessage(moneyLiteral((result.success() ? "§a" : "§c") + result.message()));
        return 1;
    }

    /**
     * Admin helper for incrementing a bank level by a positive amount.
     */
    private static int adminBankLevelAdd(CommandSourceStack source, UUID bankId, int levels) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        BankLevelService.BankLevelResult result = BankLevelService.adminAdjustBankLevel(centralBank, bankId, levels);
        source.sendSystemMessage(moneyLiteral((result.success() ? "§a" : "§c") + result.message()));
        return 1;
    }

    /**
     * Admin helper for decrementing a bank level by a positive amount.
     */
    private static int adminBankLevelRemove(CommandSourceStack source, UUID bankId, int levels) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        BankLevelService.BankLevelResult result = BankLevelService.adminAdjustBankLevel(centralBank, bankId, -levels);
        source.sendSystemMessage(moneyLiteral((result.success() ? "§a" : "§c") + result.message()));
        return 1;
    }

    /**
     * Admin helper for deleting the manual bank level override and returning to earned level.
     */
    private static int adminBankLevelDelete(CommandSourceStack source, UUID bankId) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        BankLevelService.BankLevelResult result = BankLevelService.adminClearBankLevelOverride(centralBank, bankId);
        source.sendSystemMessage(moneyLiteral((result.success() ? "§a" : "§c") + result.message()));
        return 1;
    }

    /**
     * Admin view for all shops owned by a target player, with basic KPI details
     * and clickable shop-id copy actions.
     */
    private static int adminShopViewPlayer(CommandSourceStack source, ServerPlayer target) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        if (target == null) {
            source.sendSystemMessage(moneyLiteral("§cPlayer not found."));
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        List<ShopService.ShopSummary> ownedShops = ShopService.listOwnerShopSummaries(centralBank, target.getUUID())
                .stream()
                .filter(ShopService.ShopSummary::ownerView)
                .sorted(Comparator.comparing(ShopService.ShopSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Player: §f" + target.getName().getString() + " §8(" + target.getUUID() + ")\n"));
        body.append(moneyLiteral("§7Owned Shops: §b" + ownedShops.size() + "\n\n"));

        if (ownedShops.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
            source.sendSystemMessage(ubsPanel(ChatFormatting.AQUA, "§bAdmin Shop View", body));
            return 1;
        }

        long gameTime = source.getServer().overworld() == null ? 0L : source.getServer().overworld().getGameTime();
        for (ShopService.ShopSummary shop : ownedShops) {
            UUID shopId = shop.shopId();
            String shopIdRaw = shopId == null ? "-" : shopId.toString();

            body.append(moneyLiteral("§e" + shop.name() + " §8(" + ShopService.prettyShopType(shop.type()) + ")\n"));
            body.append(moneyLiteral("§7Shop ID: §f" + shopIdRaw + "\n")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, shopIdRaw))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to copy shop ID")))));
            body.append(moneyLiteral("§7Level: §b" + shop.level()
                    + "  §7Revenue: §a$" + MoneyText.abbreviate(BigDecimal.valueOf(shop.revenueDollars()))
                    + "  §7Next Target: §e$" + MoneyText.abbreviate(BigDecimal.valueOf(shop.nextTargetDollars())) + "\n"));
            body.append(moneyLiteral("§7Claims: §f" + shop.claimRegions()
                    + "  §7Used Blocks: §f" + shop.usedClaimBlocks()
                    + " / " + shop.claimCapacityBlocks()
                    + "  §7Stockrooms: §f" + shop.stockroomRegions() + "\n"));
            boolean open = shopId != null && ShopService.isShopOpenForShopping(centralBank, shopId, gameTime);
            body.append(moneyLiteral("§7Status: " + (open ? "§aOPEN" : "§cCLOSED") + "\n"));
            body.append(moneyLiteral("§8────────────────────────\n"));
        }

        source.sendSystemMessage(ubsPanel(ChatFormatting.AQUA, "§bAdmin Shop View", body));
        return 1;
    }

    /**
     * Admin helper for forcing a shop to an explicit level milestone.
     */
    private static int adminShopLevelSet(CommandSourceStack source, UUID shopId, int level) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        ShopService.ShopActionResult result = ShopService.adminSetShopLevel(centralBank, shopId, level);
        if (!result.success()) {
            source.sendSystemMessage(moneyLiteral("§c" + result.message()));
            return 1;
        }
        BankManager.markDirty();
        source.sendSystemMessage(moneyLiteral("§a" + result.message()));
        return 1;
    }

    /**
     * Admin helper for incrementing a shop level by a positive amount.
     */
    private static int adminShopLevelAdd(CommandSourceStack source, UUID shopId, int levels) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        ShopService.ShopActionResult result = ShopService.adminAdjustShopLevel(centralBank, shopId, levels);
        if (!result.success()) {
            source.sendSystemMessage(moneyLiteral("§c" + result.message()));
            return 1;
        }
        BankManager.markDirty();
        source.sendSystemMessage(moneyLiteral("§a" + result.message()));
        return 1;
    }

    /**
     * Admin helper for decrementing a shop level by a positive amount.
     */
    private static int adminShopLevelRemove(CommandSourceStack source, UUID shopId, int levels) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        ShopService.ShopActionResult result = ShopService.adminAdjustShopLevel(centralBank, shopId, -levels);
        if (!result.success()) {
            source.sendSystemMessage(moneyLiteral("§c" + result.message()));
            return 1;
        }
        BankManager.markDirty();
        source.sendSystemMessage(moneyLiteral("§a" + result.message()));
        return 1;
    }

    private static int saveBankData(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        BankManager.markDirty();
        source.sendSystemMessage(moneyLiteral("§aBank data marked dirty for save."));
        return 1;
    }

    private static int renameCentralBank(CommandSourceStack source, String newName) {
        if (!requireAdminPermission(source)) {
            return 1;
        }
        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }
        centralBank.setBankName(newName);
        source.sendSystemMessage(moneyLiteral("§aThe bank name has been updated to: §e" + newName + "§a."));
        return 1;
    }

    private static int depositToAccount(CommandSourceStack source, UUID accountId, String amountRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        BigDecimal amount = parsePositiveAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        if (!account.AddBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cFailed to add amount '§e$" + amount + "§c' to account '§e" + accountId + "§c'."));
            return 1;
        }

        addAdminAuditTransaction(account, amount, true, source.getTextName());
        source.sendSystemMessage(moneyLiteral("§aSuccessfully added '§e$" + amount + "§a' to '§e" + accountId + "§a'. New Balance: §2$" + account.getBalance()));
        NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, true));
        return 1;
    }

    private static int withdrawFromAccount(CommandSourceStack source, UUID accountId, String amountRaw) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        BigDecimal amount = parsePositiveAmount(source, amountRaw);
        if (amount == null) {
            return 1;
        }

        if (!account.RemoveBalance(amount)) {
            source.sendSystemMessage(moneyLiteral("§cFailed to remove amount '§e$" + amount + "§c' from account '§e" + accountId + "§c'."));
            return 1;
        }

        addAdminAuditTransaction(account, amount, false, source.getTextName());
        source.sendSystemMessage(moneyLiteral("§aSuccessfully removed '§e$" + amount + "§a' from '§e" + accountId + "§a'. New Balance: §2$" + account.getBalance()));
        NeoForge.EVENT_BUS.post(new BalanceChangedEvent(account, account.getBalance(), amount, false));
        return 1;
    }

    private static int adminViewPlayer(CommandSourceStack source, ServerPlayer target) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        Map<UUID, AccountHolder> accounts = centralBank.SearchForAccount(target.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§e" + target.getName().getString() + " has no accounts."));
            return 1;
        }

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Player: §f" + target.getName().getString() + " §8(" + target.getUUID() + ")\n"));
        body.append(moneyLiteral("§7Accounts: §b" + accounts.size() + "\n\n"));

        List<AccountHolder> ordered = new ArrayList<>(accounts.values());
        ordered.sort(Comparator.comparing(a -> a.getAccountUUID().toString()));
        for (AccountHolder account : ordered) {
            Bank bank = centralBank.getBank(account.getBankId());
            String bankName = bank != null ? bank.getBankName() : "Unknown";
            String accountId = account.getAccountUUID().toString();
            String shortAccountId = accountId.substring(0, Math.min(8, accountId.length()));

            body.append(moneyLiteral("§e" + shortAccountId + "§7 (" + account.getAccountType().label + ")\n"));
            body.append(moneyLiteral("§7Bank: §f" + bankName + "\n"));
            body.append(moneyLiteral("§7Balance: §a$" + account.getBalance().toPlainString() + "  "));
            body.append(moneyLiteral("§7Primary: §f" + account.isPrimaryAccount() + "\n"));
            body.append(moneyLiteral("§7Frozen: " + (account.isFrozen() ? "§cYes" : "§aNo")));
            if (account.isFrozen() && !account.getFrozenReason().isEmpty()) {
                body.append(moneyLiteral(" §8(" + account.getFrozenReason() + ")"));
            }
            body.append(moneyLiteral("\n"));
            body.append(moneyLiteral("§7PIN Set: §f" + account.hasPin() + "  "));
            body.append(moneyLiteral("§7Used Today: §f$" + account.getDailyWithdrawnAmount().toPlainString() + "\n"));
            body.append(moneyLiteral("§7Account ID: §f" + accountId + "\n")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, accountId))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, moneyLiteral("Click to copy account ID")))));

            List<UserTransaction> recent = account.getTransactions().values().stream()
                    .sorted(Comparator.comparing(UserTransaction::getTimestamp).reversed())
                    .limit(10)
                    .toList();
            body.append(moneyLiteral("§7Recent Transactions: §f" + recent.size() + "\n"));
            if (recent.isEmpty()) {
                body.append(moneyLiteral("§8- none\n"));
            } else {
                for (UserTransaction tx : recent) {
                    boolean incoming = account.getAccountUUID().equals(tx.getReceiverUUID());
                    String sign = incoming ? "+" : "-";
                    UUID cp = incoming ? tx.getSenderUUID() : tx.getReceiverUUID();
                    String cpShort = cp == null ? "unknown" : cp.toString().substring(0, Math.min(8, cp.toString().length()));
                    body.append(moneyLiteral(
                            "§8- §7" + ADMIN_TX_TIME_FMT.format(tx.getTimestamp())
                                    + " §f" + sign + "$" + tx.getAmount().toPlainString()
                                    + " §8[" + tx.getTransactionDescription() + "] §7cp:§f" + cpShort + "\n"
                    ));
                }
            }
            body.append(moneyLiteral("§8────────────────────────\n"));
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        Map<UUID, AccountHolder> accounts = centralBank.SearchForAccount(target.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§e" + target.getName().getString() + " has no accounts to freeze."));
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

        source.sendSystemMessage(moneyLiteral(
                "§aFroze §e" + accounts.size() + "§a account(s) for §e" + target.getName().getString()
                        + "§a. Newly frozen: §e" + changed + "§a."
        ));

        String cleanReason = reason == null ? "" : reason.trim();
        target.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        Map<UUID, AccountHolder> accounts = centralBank.SearchForAccount(target.getUUID());
        if (accounts.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§e" + target.getName().getString() + " has no accounts to unfreeze."));
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

        source.sendSystemMessage(moneyLiteral(
                "§aUnfroze §e" + accounts.size() + "§a account(s) for §e" + target.getName().getString()
                        + "§a. Previously frozen: §e" + changed + "§a."
        ));
        target.sendSystemMessage(moneyLiteral("§aYour banking access has been restored by an administrator."));
        return 1;
    }

    private static int adminFreezeAccount(CommandSourceStack source, UUID accountId, String reason) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        account.freeze(reason);
        addAdminAuditMarker(account, source.getTextName(), "ADMIN_FREEZE", account.getFrozenReason());
        source.sendSystemMessage(moneyLiteral("§aAccount §e" + accountId + "§a is now frozen."));

        ServerPlayer target = source.getServer().getPlayerList().getPlayer(account.getPlayerUUID());
        if (target != null) {
            String cleanReason = account.getFrozenReason();
            target.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            source.sendSystemMessage(moneyLiteral("§cThe account '§e" + accountId + "§c' could not be found."));
            return 1;
        }

        account.unfreeze();
        addAdminAuditMarker(account, source.getTextName(), "ADMIN_UNFREEZE", "");
        source.sendSystemMessage(moneyLiteral("§aAccount §e" + accountId + "§a has been unfrozen."));

        ServerPlayer target = source.getServer().getPlayerList().getPlayer(account.getPlayerUUID());
        if (target != null) {
            target.sendSystemMessage(moneyLiteral("§aYour account " + accountId + " has been unfrozen by an administrator."));
        }
        return 1;
    }

    private static int adminEconomyReport(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
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
        body.append(moneyLiteral("§7Registered Banks: §b" + registeredBanks + "\n"));
        body.append(moneyLiteral("§7Total Accounts: §b" + totalAccounts + "\n"));
        body.append(moneyLiteral("§7Active Accounts: §b" + activeAccounts + "\n"));
        body.append(moneyLiteral("§7Frozen Accounts: §c" + frozenAccounts + "\n"));
        body.append(moneyLiteral("§7Total Circulation: §a$" + totalBalances.toPlainString() + "\n"));
        body.append(moneyLiteral("§7ATM Withdrawn Today: §e$" + totalDailyWithdrawn.toPlainString() + "\n"));
        body.append(moneyLiteral("§7Central Bank Reserve: §a$" + centralBank.getBankReserve().toPlainString() + "\n"));
        body.append(moneyLiteral("§7Central Bank Interest Rate: §e" + centralBank.getInterestRate() + "\n"));
        body.append(moneyLiteral("\n§7Top 10 Richest Players:\n"));
        if (richest.isEmpty()) {
            body.append(moneyLiteral("§8- none\n"));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, BigDecimal> entry : richest) {
                String name = resolvePlayerName(server, entry.getKey());
                body.append(moneyLiteral(
                        "§8" + rank + ". §f" + name + " §8(" + entry.getKey() + ") §7- §a$" + entry.getValue().toPlainString() + "\n"
                ));
                rank++;
            }
        }
        body.append(moneyLiteral("\n§8Generated at world time: §7" + currentOverworldGameTime(server)));

        source.sendSystemMessage(ubsPanel(ChatFormatting.YELLOW, "§eEconomy Report", body));
        return 1;
    }

    private static int adminListPendingLoanApprovals(CommandSourceStack source) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        List<LoanService.LoanQuote> pending = LoanService.listPendingApprovals();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Pending Loan Approvals: §b" + pending.size() + "\n\n"));
        if (pending.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (LoanService.LoanQuote quote : pending) {
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cNo pending loan request for that player."));
            return 1;
        }

        var issued = LoanService.approvePending(source.getServer(), borrower.getUUID());
        if (issued == null) {
            source.sendSystemMessage(moneyLiteral("§cLoan approval failed while issuing funds."));
            borrower.sendSystemMessage(moneyLiteral("§cYour loan request could not be issued after approval."));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral(
                "§aLoan approved for §e" + borrower.getName().getString()
                        + "§a: $" + pending.principal().toPlainString()
        ));
        borrower.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cNo pending loan request for that player."));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral(
                "§eDenied pending loan for §f" + borrower.getName().getString() + "§e."
        ));
        String cleanReason = reason == null ? "" : reason.trim();
        borrower.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is unavailable."));
            return 1;
        }

        AccountHolder sourceAccount = centralBank.SearchForAccountByAccountId(sourceAccountId);
        AccountHolder targetAccount = centralBank.SearchForAccountByAccountId(targetAccountId);
        if (sourceAccount == null || targetAccount == null) {
            source.sendSystemMessage(moneyLiteral("§cSource or target account was not found."));
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

        long frequencyTicks;
        try {
            frequencyTicks = Long.parseLong(frequencyRaw.trim());
        } catch (NumberFormatException ex) {
            source.sendSystemMessage(moneyLiteral("§cInvalid frequency ticks."));
            return 1;
        }

        if (frequencyTicks < 20L) {
            source.sendSystemMessage(moneyLiteral("§cFrequency must be at least 20 ticks."));
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

        source.sendSystemMessage(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is unavailable."));
            return 1;
        }

        var payments = centralBank.getScheduledPayments().values().stream()
                .sorted(Comparator.comparing(ScheduledPayment::getPaymentId))
                .toList();

        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7Scheduled Payments: §b" + payments.size() + "\n\n"));
        if (payments.isEmpty()) {
            body.append(moneyLiteral("§8- none"));
        } else {
            for (ScheduledPayment payment : payments) {
                body.append(moneyLiteral(
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is unavailable."));
            return 1;
        }

        boolean removed = centralBank.removeScheduledPayment(paymentId);
        if (!removed) {
            source.sendSystemMessage(moneyLiteral("§cScheduled payment not found."));
            return 1;
        }

        source.sendSystemMessage(moneyLiteral("§aRemoved scheduled payment §f" + paymentId + "§a."));
        return 1;
    }

    private static int adminImportCsv(CommandSourceStack source, String rawPath) {
        if (!requireAdminPermission(source)) {
            return 1;
        }

        CentralBank centralBank = BankManager.getCentralBank(source.getServer());
        if (centralBank == null) {
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        Path path = resolveImportPath(source, rawPath);
        if (path == null) {
            return 1;
        }
        if (!Files.exists(path)) {
            source.sendSystemMessage(moneyLiteral("§cCSV file not found: §e" + path));
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
            source.sendSystemMessage(moneyLiteral("§cFailed to read CSV: §e" + ex.getMessage()));
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        Path path = resolveImportPath(source, rawPath);
        if (path == null) {
            return 1;
        }
        if (!Files.exists(path)) {
            source.sendSystemMessage(moneyLiteral("§cImport path not found: §e" + path));
            return 1;
        }

        List<Path> files = listYamlFiles(path);
        if (files.isEmpty()) {
            source.sendSystemMessage(moneyLiteral("§cNo .yml/.yaml files found at: §e" + path));
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
            source.sendSystemMessage(moneyLiteral("§cCentral bank data is not available."));
            return 1;
        }

        Path path = resolveImportPath(source, rawPath);
        if (path == null) {
            return 1;
        }
        if (!Files.exists(path)) {
            source.sendSystemMessage(moneyLiteral("§cImport file not found: §e" + path));
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
            source.sendSystemMessage(moneyLiteral("§cFailed to read import file: §e" + ex.getMessage()));
            return 1;
        }

        finalizeImport(source, path, "iConomy Import", stats);
        return 1;
    }

    private static void finalizeImport(CommandSourceStack source, Path path, String title, ImportStats stats) {
        BankManager.markDirty();

        MutableComponent summary = Component.empty();
        summary.append(moneyLiteral("§7Source: §f" + path + "\n"));
        summary.append(moneyLiteral("§7Created: §a" + stats.created + "\n"));
        summary.append(moneyLiteral("§7Updated: §e" + stats.updated + "\n"));
        summary.append(moneyLiteral("§7Imported History Entries: §b" + stats.importedHistoryEntries + "\n"));
        summary.append(moneyLiteral("§7Failed: §c" + stats.failed + "\n"));
        if (!stats.errors.isEmpty()) {
            summary.append(moneyLiteral("\n§cFirst errors:\n"));
            for (int i = 0; i < Math.min(5, stats.errors.size()); i++) {
                summary.append(moneyLiteral("§8- §c" + stats.errors.get(i) + "\n"));
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
            source.sendSystemMessage(moneyLiteral("§cInvalid path: §e" + rawPath));
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
        try {
            UUID requestedId = UUID.fromString(requested);
            if (requestedId.equals(centralBank.getBankId())) {
                return centralBank;
            }
            Bank byId = centralBank.getBank(requestedId);
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
            // Continue with case-insensitive name matching.
        }
        if (centralBank.getBankName() != null
                && centralBank.getBankName().trim().equalsIgnoreCase(requested)) {
            return centralBank;
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
            owner.sendSystemMessage(moneyLiteral(message));
        }
    }

    private static BigDecimal parsePositiveAmount(CommandSourceStack source, String amountRaw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw);
        } catch (NumberFormatException e) {
            source.sendSystemMessage(moneyLiteral("§cThe amount '§e" + amountRaw + "§c' is not a valid number."));
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            source.sendSystemMessage(moneyLiteral("§cAmount must be greater than zero."));
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
        if (centralBank != null && playerUuid != null && primaryAccountId != null) {
            centralBank.setPrimaryAccountForPlayer(playerUuid, primaryAccountId, true);
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
