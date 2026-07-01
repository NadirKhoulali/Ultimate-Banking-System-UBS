package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountAccessMessages;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.ShopSellingTableLargeBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ItemDisplayTransform;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayType;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfTransformBounds;
import net.austizz.ultimatebankingsystem.block.entity.custom.GlassCounterDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ModularWallDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopSellingTableBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity;
import net.austizz.ultimatebankingsystem.command.UBSCommands;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.HandheldPaymentTerminalItem;
import net.austizz.ultimatebankingsystem.npc.BankTellerService;
import net.austizz.ultimatebankingsystem.npc.ShopCashierInteractionManager;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.payrequest.PayRequestManager;
import net.austizz.ultimatebankingsystem.pickpocket.PickpocketService;
import net.austizz.ultimatebankingsystem.phone.SmartphoneService;
import net.austizz.ultimatebankingsystem.shelf.ShelfCartService;
import net.austizz.ultimatebankingsystem.shelf.ShelfBasketSessionService;
import net.austizz.ultimatebankingsystem.shelf.ShelfDisplayRules;
import net.austizz.ultimatebankingsystem.shelf.ShelfPrice;
import net.austizz.ultimatebankingsystem.shelf.ShelfService;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Registers all network payloads (custom packets) for the Ultimate Banking System mod.
 *
 * <p>On Forge 1.20.1 this is invoked explicitly during common setup.</p>
 *
 * <H2>Adding a new payload</H2>
 * <pre>{@code
 * // 1. Create a record implementing CustomPacketPayload:
 * public record ExamplePayload(String data) implements CustomPacketPayload {
 *     public static final Type<ExamplePayload> TYPE = new Type<>(
 *         ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "example"));
 *     public static final StreamCodec<RegistryFriendlyByteBuf, ExamplePayload> STREAM_CODEC =
 *         StreamCodec.composite(
 *             ByteBufCodecs.STRING_UTF8, ExamplePayload::data,
 *             ExamplePayload::new);
 *     @Override
 *     public Type<ExamplePayload> type() { return TYPE; }
 * }
 *
 * // 2. Register in the register() method below:
 * registrar.playToServer(
 *     ExamplePayload.TYPE, ExamplePayload.STREAM_CODEC,
 *     ModPayloads::handleExample);
 *
 * // 3. Add handler method in this class:
 * private static void handleExample(ExamplePayload payload, IPayloadContext context) {
 *     context.enqueueWork(() -> {
 *         // Main-thread work here (access server state safely)
 *     });
 * }
 * }</pre>
 *
 * <p>Direction helpers on {@link PayloadRegistrar}:</p>
 * <ul>
 *   <li>{@code playToServer} — client → server (e.g. GUI button clicks)</li>
 *   <li>{@code playToClient} — server → client (e.g. sync data to GUI)</li>
 *   <li>{@code playBidirectional} — both directions</li>
 * </ul>
 */
public final class ModPayloads {
    private static final int OWNER_PC_DESKTOP_ACTION_RESPONSE_MAX_CHARS = 32_000;

    private static final UUID ATM_TERMINAL_ID = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:atm-terminal".getBytes(StandardCharsets.UTF_8));

    private ModPayloads() {}

    public static void register(final RegisterPayloadHandlersEvent event) {
        UltimateBankingSystem.LOGGER.info("[UBS] Registering network payloads");
        final PayloadRegistrar registrar = event.registrar("1");

        // --- Register payloads below this line ---
        registrar.playToServer(OpenATMPayload.TYPE, OpenATMPayload.STREAM_CODEC, ModPayloads::handleOpenATM);
        registrar.playToClient(AccountListPayload.TYPE, AccountListPayload.STREAM_CODEC, ModPayloads::handleAccountList);
        registrar.playToClient(BankTellerOpenPayload.TYPE, BankTellerOpenPayload.STREAM_CODEC, ModPayloads::handleBankTellerOpen);
        registrar.playToServer(BankTellerActionPayload.TYPE, BankTellerActionPayload.STREAM_CODEC, ModPayloads::handleBankTellerAction);
        registrar.playToClient(BankTellerActionResponsePayload.TYPE, BankTellerActionResponsePayload.STREAM_CODEC, ModPayloads::handleBankTellerActionResponse);
        registrar.playToServer(OpenBankOwnerPcPayload.TYPE, OpenBankOwnerPcPayload.STREAM_CODEC, ModPayloads::handleOpenBankOwnerPc);
        registrar.playToServer(ShopTerminalUsePayload.TYPE, ShopTerminalUsePayload.STREAM_CODEC, ModPayloads::handleShopTerminalUse);
        registrar.playToClient(ShopTerminalOpenPayload.TYPE, ShopTerminalOpenPayload.STREAM_CODEC, ModPayloads::handleShopTerminalOpen);
        registrar.playToServer(ShopTerminalSavePayload.TYPE, ShopTerminalSavePayload.STREAM_CODEC, ModPayloads::handleShopTerminalSave);
        registrar.playToClient(ShopTerminalSaveResponsePayload.TYPE, ShopTerminalSaveResponsePayload.STREAM_CODEC, ModPayloads::handleShopTerminalSaveResponse);
        registrar.playToServer(ShelfUsePayload.TYPE, ShelfUsePayload.STREAM_CODEC, ModPayloads::handleShelfUse);
        registrar.playToClient(ShelfOpenPayload.TYPE, ShelfOpenPayload.STREAM_CODEC, ModPayloads::handleShelfOpen);
        registrar.playToServer(ShelfActionPayload.TYPE, ShelfActionPayload.STREAM_CODEC, ModPayloads::handleShelfAction);
        registrar.playToClient(ShelfActionResponsePayload.TYPE, ShelfActionResponsePayload.STREAM_CODEC, ModPayloads::handleShelfActionResponse);
        registrar.playToClient(HandheldTerminalOpenPayload.TYPE, HandheldTerminalOpenPayload.STREAM_CODEC, ModPayloads::handleHandheldTerminalOpen);
        registrar.playToServer(HandheldTerminalSavePayload.TYPE, HandheldTerminalSavePayload.STREAM_CODEC, ModPayloads::handleHandheldTerminalSave);
        registrar.playToClient(HandheldTerminalSaveResponsePayload.TYPE, HandheldTerminalSaveResponsePayload.STREAM_CODEC, ModPayloads::handleHandheldTerminalSaveResponse);
        registrar.playToClient(OwnerPcBootstrapPayload.TYPE, OwnerPcBootstrapPayload.STREAM_CODEC, ModPayloads::handleOwnerPcBootstrap);
        registrar.playToClient(OwnerPcDesktopDataPayload.TYPE, OwnerPcDesktopDataPayload.STREAM_CODEC, ModPayloads::handleOwnerPcDesktopData);
        registrar.playToServer(OwnerPcDesktopActionPayload.TYPE, OwnerPcDesktopActionPayload.STREAM_CODEC, ModPayloads::handleOwnerPcDesktopAction);
        registrar.playToClient(OwnerPcDesktopActionResponsePayload.TYPE, OwnerPcDesktopActionResponsePayload.STREAM_CODEC, ModPayloads::handleOwnerPcDesktopActionResponse);
        registrar.playToServer(OwnerPcBankDataRequestPayload.TYPE, OwnerPcBankDataRequestPayload.STREAM_CODEC, ModPayloads::handleOwnerPcBankDataRequest);
        registrar.playToClient(OwnerPcBankDataPayload.TYPE, OwnerPcBankDataPayload.STREAM_CODEC, ModPayloads::handleOwnerPcBankData);
        registrar.playToServer(OwnerPcActionPayload.TYPE, OwnerPcActionPayload.STREAM_CODEC, ModPayloads::handleOwnerPcAction);
        registrar.playToClient(OwnerPcActionResponsePayload.TYPE, OwnerPcActionResponsePayload.STREAM_CODEC, ModPayloads::handleOwnerPcActionResponse);
        registrar.playToServer(OwnerPcCreateBankPayload.TYPE, OwnerPcCreateBankPayload.STREAM_CODEC, ModPayloads::handleOwnerPcCreateBank);
        registrar.playToClient(OwnerPcCreateBankResponsePayload.TYPE, OwnerPcCreateBankResponsePayload.STREAM_CODEC, ModPayloads::handleOwnerPcCreateBankResponse);
        registrar.playToServer(PinAuthRequestPayload.TYPE, PinAuthRequestPayload.STREAM_CODEC, ModPayloads::handlePinAuthRequest);
        registrar.playToClient(PinAuthResponsePayload.TYPE, PinAuthResponsePayload.STREAM_CODEC, ModPayloads::handlePinAuthResponse);

        // Balance inquiry
        registrar.playToServer(BalanceRequestPayload.TYPE, BalanceRequestPayload.STREAM_CODEC, ModPayloads::handleBalanceRequest);
        registrar.playToClient(BalanceResponsePayload.TYPE, BalanceResponsePayload.STREAM_CODEC, ModPayloads::handleBalanceResponse);

        // Withdraw
        registrar.playToServer(WithdrawRequestPayload.TYPE, WithdrawRequestPayload.STREAM_CODEC, ModPayloads::handleWithdrawRequest);
        registrar.playToClient(WithdrawResponsePayload.TYPE, WithdrawResponsePayload.STREAM_CODEC, ModPayloads::handleWithdrawResponse);

        // Deposit
        registrar.playToServer(DepositRequestPayload.TYPE, DepositRequestPayload.STREAM_CODEC, ModPayloads::handleDepositRequest);
        registrar.playToClient(DepositResponsePayload.TYPE, DepositResponsePayload.STREAM_CODEC, ModPayloads::handleDepositResponse);

        // Transfer
        registrar.playToServer(TransferRequestPayload.TYPE, TransferRequestPayload.STREAM_CODEC, ModPayloads::handleTransferRequest);
        registrar.playToClient(TransferResponsePayload.TYPE, TransferResponsePayload.STREAM_CODEC, ModPayloads::handleTransferResponse);

        // Transaction history
        registrar.playToServer(TxHistoryRequestPayload.TYPE, TxHistoryRequestPayload.STREAM_CODEC, ModPayloads::handleTxHistoryRequest);
        registrar.playToClient(TxHistoryResponsePayload.TYPE, TxHistoryResponsePayload.STREAM_CODEC, ModPayloads::handleTxHistoryResponse);

        // Account settings
        registrar.playToServer(SetPrimaryPayload.TYPE, SetPrimaryPayload.STREAM_CODEC, ModPayloads::handleSetPrimary);
        registrar.playToClient(SetPrimaryResponsePayload.TYPE, SetPrimaryResponsePayload.STREAM_CODEC, ModPayloads::handleSetPrimaryResponse);
        registrar.playToServer(ChangePinPayload.TYPE, ChangePinPayload.STREAM_CODEC, ModPayloads::handleChangePin);
        registrar.playToClient(ChangePinResponsePayload.TYPE, ChangePinResponsePayload.STREAM_CODEC, ModPayloads::handleChangePinResponse);
        registrar.playToServer(SetTemporaryWithdrawalLimitPayload.TYPE, SetTemporaryWithdrawalLimitPayload.STREAM_CODEC, ModPayloads::handleSetTemporaryWithdrawalLimit);
        registrar.playToClient(SetTemporaryWithdrawalLimitResponsePayload.TYPE, SetTemporaryWithdrawalLimitResponsePayload.STREAM_CODEC, ModPayloads::handleSetTemporaryWithdrawalLimitResponse);

        // Pay requests
        registrar.playToServer(PayRequestInboxRequestPayload.TYPE, PayRequestInboxRequestPayload.STREAM_CODEC, ModPayloads::handlePayRequestInboxRequest);
        registrar.playToClient(PayRequestInboxResponsePayload.TYPE, PayRequestInboxResponsePayload.STREAM_CODEC, ModPayloads::handlePayRequestInboxResponse);
        registrar.playToServer(PayRequestActionPayload.TYPE, PayRequestActionPayload.STREAM_CODEC, ModPayloads::handlePayRequestAction);
        registrar.playToClient(PayRequestActionResponsePayload.TYPE, PayRequestActionResponsePayload.STREAM_CODEC, ModPayloads::handlePayRequestActionResponse);
        registrar.playToServer(PayRequestCreatePayload.TYPE, PayRequestCreatePayload.STREAM_CODEC, ModPayloads::handlePayRequestCreate);
        registrar.playToClient(PayRequestCreateResponsePayload.TYPE, PayRequestCreateResponsePayload.STREAM_CODEC, ModPayloads::handlePayRequestCreateResponse);
        registrar.playToClient(HudStatePayload.TYPE, HudStatePayload.STREAM_CODEC, ModPayloads::handleHudState);
        registrar.playToClient(StockroomLocateRenderPayload.TYPE, StockroomLocateRenderPayload.STREAM_CODEC, ModPayloads::handleStockroomLocateRender);
        registrar.playToClient(DeliveryAlertPayload.TYPE, DeliveryAlertPayload.STREAM_CODEC, ModPayloads::handleDeliveryAlert);
        registrar.playToClient(ShopSetupObjectivePayload.TYPE, ShopSetupObjectivePayload.STREAM_CODEC, ModPayloads::handleShopSetupObjective);
        registrar.playToClient(DeliveryInfoBoardPayload.TYPE, DeliveryInfoBoardPayload.STREAM_CODEC, ModPayloads::handleDeliveryInfoBoard);
        registrar.playToClient(DeliveryPalletLabelsPayload.TYPE, DeliveryPalletLabelsPayload.STREAM_CODEC, ModPayloads::handleDeliveryPalletLabels);
        registrar.playToServer(PickpocketStartPayload.TYPE, PickpocketStartPayload.STREAM_CODEC, ModPayloads::handlePickpocketStart);
        registrar.playToServer(PickpocketCancelPayload.TYPE, PickpocketCancelPayload.STREAM_CODEC, ModPayloads::handlePickpocketCancel);
        registrar.playToClient(PickpocketStatePayload.TYPE, PickpocketStatePayload.STREAM_CODEC, ModPayloads::handlePickpocketState);
        registrar.playToServer(SmartphoneOpenRequestPayload.TYPE, SmartphoneOpenRequestPayload.STREAM_CODEC, ModPayloads::handleSmartphoneOpenRequest);
        registrar.playToServer(SmartphoneActionPayload.TYPE, SmartphoneActionPayload.STREAM_CODEC, ModPayloads::handleSmartphoneAction);
        registrar.playToClient(SmartphoneSnapshotPayload.TYPE, SmartphoneSnapshotPayload.STREAM_CODEC, ModPayloads::handleSmartphoneSnapshot);
    }

    private static void handleHudState(HudStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleHudState", payload));
    }

    private static void handleStockroomLocateRender(StockroomLocateRenderPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleStockroomLocateRender", payload));
    }

    private static void handleDeliveryAlert(DeliveryAlertPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleDeliveryAlert", payload));
    }

    private static void handleShopSetupObjective(ShopSetupObjectivePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleShopSetupObjective", payload));
    }

    private static void handleDeliveryInfoBoard(DeliveryInfoBoardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleDeliveryInfoBoard", payload));
    }

    private static void handleDeliveryPalletLabels(DeliveryPalletLabelsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleDeliveryPalletLabels", payload));
    }

    private static void handlePickpocketStart(PickpocketStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null || payload == null || payload.targetPlayerId() == null) {
                return;
            }
            PickpocketService.handleStartRequest(player, payload.targetPlayerId());
        });
    }

    private static void handlePickpocketCancel(PickpocketCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                return;
            }
            PickpocketService.handleCancelRequest(player);
        });
    }

    private static void handlePickpocketState(PickpocketStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handlePickpocketState", payload));
    }

    private static void handleSmartphoneOpenRequest(SmartphoneOpenRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                SmartphoneService.openPhone(player, payload == null || payload.animate());
            }
        });
    }

    private static void handleSmartphoneAction(SmartphoneActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null || payload == null) {
                return;
            }
            PacketDistributor.sendToPlayer(player, SmartphoneService.handleAction(
                    player,
                    payload.action(),
                    payload.param1(),
                    payload.param2(),
                    payload.param3()
            ));
        });
    }

    private static void handleSmartphoneSnapshot(SmartphoneSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleSmartphoneSnapshot", payload));
    }

    // ─── OpenATM ────────────────────────────────────────────────────────

    private static void handleOpenATM(OpenATMPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) return;
            long gameTime = currentOverworldGameTime(server);

            var playerAccounts = centralBank.SearchForAccount(player.getUUID());
            List<AccountSummary> summaries = new ArrayList<>();

            var cardLookup = CreditCardService.findHeldCard(centralBank, player);
            if (cardLookup.hasCard()) {
                if (!cardLookup.validation().valid()) {
                    player.sendSystemMessage(moneyLiteral("§cCredit card rejected: "
                            + cardLookup.validation().message()));
                    return;
                }

                AccountHolder linked = centralBank.SearchForAccountByAccountId(cardLookup.validation().accountId());
                if (linked == null || !player.getUUID().equals(linked.getPlayerUUID())) {
                    player.sendSystemMessage(moneyLiteral("§cCredit card rejected: linked account is unavailable."));
                    return;
                }
                summaries.add(buildAccountSummary(linked, centralBank, gameTime));
                player.sendSystemMessage(moneyLiteral(
                        "§bATM card mode active for account §f" + shortId(linked.getAccountUUID())
                ));
            } else {
                for (var account : playerAccounts.values()) {
                    summaries.add(buildAccountSummary(account, centralBank, gameTime));
                }
            }

            UltimateBankingSystem.LOGGER.info("[UBS] Sending {} accounts to player {}", summaries.size(), player.getName().getString());
            PacketDistributor.sendToPlayer(player, new AccountListPayload(summaries));

            PacketDistributor.sendToPlayer(player, UBSCommands.buildHudStatePayload(centralBank, player.getUUID()));
        });
    }

    private static void handleAccountList(AccountListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleAccountList", payload));
    }

    private static void handleBankTellerOpen(BankTellerOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleBankTellerOpen", payload));
    }

    private static void handleBankTellerAction(BankTellerActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            if (server == null) {
                return;
            }
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                PacketDistributor.sendToPlayer(player, new BankTellerActionResponsePayload(false, "Bank data is unavailable.", false));
                return;
            }

            BankTellerService.ActionResult result = BankTellerService.executeAction(
                    server,
                    centralBank,
                    player,
                    payload.tellerId(),
                    payload.action(),
                    payload.accountId(),
                    payload.amount(),
                    payload.recipient(),
                    payload.confirmed(),
                    payload.paymentMode()
            );
            PacketDistributor.sendToPlayer(player, new BankTellerActionResponsePayload(
                    result.success(),
                    result.message(),
                    result.closeScreen()
            ));

            if (result.refreshOpenPayload()) {
                BankTellerEntity teller = findBankTeller(server, payload.tellerId());
                if (teller != null && teller.isAlive()) {
                    PacketDistributor.sendToPlayer(player, BankTellerService.buildOpenPayload(server, centralBank, player, teller));
                }
            }
            PacketDistributor.sendToPlayer(player, UBSCommands.buildHudStatePayload(centralBank, player.getUUID()));
        });
    }

    private static void handleBankTellerActionResponse(BankTellerActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleBankTellerActionResponse", payload));
    }

    private static AccountSummary buildAccountSummary(AccountHolder account,
                                                      net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
                                                      long gameTime) {
        var bank = centralBank.getBank(account.getBankId());
        String bankName = bank != null ? bank.getBankName() : "Unknown";
        BigDecimal defaultLimit = account.getConfiguredWithdrawalLimit();
        BigDecimal effectiveLimit = account.getEffectiveWithdrawalLimit(gameTime);
        BigDecimal temporaryLimit = account.getTemporaryWithdrawalLimitIfActive(gameTime);
        BigDecimal dailyLimit = account.getConfiguredDailyWithdrawalLimit();
        BigDecimal dailyWithdrawn = account.getDailyWithdrawnAmount();
        BigDecimal dailyRemaining = account.getRemainingDailyWithdrawalLimit();
        return new AccountSummary(
                account.getAccountUUID(),
                account.getAccountType().label,
                bankName,
                account.getBalance().toPlainString(),
                account.isPrimaryAccount(),
                account.hasPin(),
                defaultLimit.toPlainString(),
                effectiveLimit.toPlainString(),
                temporaryLimit == null ? "" : temporaryLimit.toPlainString(),
                account.getTemporaryWithdrawalLimitExpiresAtGameTime(gameTime),
                dailyLimit.toPlainString(),
                dailyWithdrawn.toPlainString(),
                dailyRemaining.toPlainString(),
                account.getDailyWithdrawalResetEpochMillis()
        );
    }

    // ─── Shop Terminal ─────────────────────────────────────────────────

    private static void handleShopTerminalUse(ShopTerminalUsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            if (server == null) {
                return;
            }

            ServerLevel level = resolveServerLevel(server, payload.dimensionId());
            if (level == null) {
                return;
            }
            BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());
            if (player.level() != level) {
                return;
            }
            double distSq = player.position().distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distSq > 100.0D) {
                return;
            }
            if (!level.getBlockState(pos).is(ModBlocks.PAYMENT_TERMINAL.get())) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof ShopTerminalBlockEntity terminal)) {
                return;
            }
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank != null && ShopService.hasCashierTerminalSelection(player.getUUID())) {
                ShopService.ShopActionResult selectionResult = payload.configureAction()
                        ? ShopService.cancelCashierTerminalSelection(player, "Cashier-terminal link mode cancelled.")
                        : ShopService.applyCashierTerminalSelection(
                                player,
                                centralBank,
                                level,
                                pos,
                                terminal
                        );
                player.sendSystemMessage(moneyLiteral((selectionResult.success() ? "§a" : "§c") + selectionResult.message()));
                ServerActionAlert.send(
                        player,
                        "Cashier Link",
                        selectionResult.message(),
                        selectionResult.success() ? DeliveryAlertPayload.AlertTone.SUCCESS : DeliveryAlertPayload.AlertTone.ERROR,
                        selectionResult.success() ? 4400 : 5600
                );
                return;
            }
            if (terminal.isFeedbackActive()) {
                int remainingTicks = terminal.getFeedbackTicksRemaining();
                int remainingSeconds = Math.max(1, (remainingTicks + 19) / 20);
                player.sendSystemMessage(moneyLiteral("§eTerminal is busy. Try again in §6" + remainingSeconds + "s§e."));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "Terminal is busy. Try again in " + remainingSeconds + "s.",
                        DeliveryAlertPayload.AlertTone.WARNING,
                        5200);
                return;
            }
            if (payload.configureAction()) {
                if (!terminal.canConfigure(player)) {
                    player.sendSystemMessage(moneyLiteral("§cOnly the shop owner or an operator can configure this terminal."));
                    ServerActionAlert.send(player,
                            "Payment Terminal",
                            "Only the shop owner or an operator can configure this terminal.",
                            DeliveryAlertPayload.AlertTone.ERROR,
                            5600);
                    return;
                }
                if (terminal.getOwnerUuid() == null) {
                    terminal.setOwner(player.getUUID(), player.getName().getString());
                }
                PacketDistributor.sendToPlayer(player, buildShopTerminalOpenPayload(level, pos, terminal, centralBank, player.getUUID()));
                return;
            }

            if (ShopCashierInteractionManager.handleTerminalCardUse(player, level, pos, terminal)) {
                return;
            }

            if (ShopService.isTerminalLinkedToAnyCashier(
                    centralBank,
                    level.dimension().location().toString(),
                    pos
            )) {
                player.sendSystemMessage(moneyLiteral(
                        "§eThis terminal is linked to a cashier. Start checkout at that cashier first."
                ));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "This terminal is linked to a cashier. Start checkout at that cashier first.",
                        DeliveryAlertPayload.AlertTone.WARNING,
                        5200);
                return;
            }

            ItemStack basket = ShelfService.findBasketInHands(player);
            if (!basket.isEmpty() && ShelfCartService.getTotalUnits(basket) > 0) {
                player.sendSystemMessage(moneyLiteral(
                        "§eNo active cashier checkout price found for your basket. Start checkout at a cashier first."
                ));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "No active cashier checkout price found for your basket. Start checkout at a cashier first.",
                        DeliveryAlertPayload.AlertTone.WARNING,
                        5200);
                return;
            }

            if (centralBank == null) {
                player.sendSystemMessage(moneyLiteral("§cBank data is unavailable."));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "Bank data is unavailable.",
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                terminal.triggerPulse(false);
                return;
            }

            AccountHolder payer;
            var cardLookup = CreditCardService.findHeldCard(centralBank, player);
            if (!cardLookup.hasCard()) {
                player.sendSystemMessage(moneyLiteral("§cNo credit card in hand. Hold a valid credit card to pay."));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "No credit card in hand. Hold a valid credit card to pay.",
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                return;
            }
            if (!cardLookup.validation().valid()) {
                player.sendSystemMessage(moneyLiteral("§cPayment failed: " + cardLookup.validation().message()));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "Payment failed: " + cardLookup.validation().message(),
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                terminal.triggerPulse(false);
                return;
            }
            payer = centralBank.SearchForAccountByAccountId(cardLookup.validation().accountId());
            if (payer == null || !player.getUUID().equals(payer.getPlayerUUID())) {
                player.sendSystemMessage(moneyLiteral("§cPayment failed: linked card account is unavailable."));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "Payment failed: linked card account is unavailable.",
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                terminal.triggerPulse(false);
                return;
            }
            UUID merchantAccountId = terminal.getMerchantAccountId();
            if (merchantAccountId == null) {
                player.sendSystemMessage(moneyLiteral("§cThis terminal is not configured. Ask the owner to set a merchant account."));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "This terminal is not configured. Ask the owner to set a merchant account.",
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                terminal.triggerPulse(false);
                return;
            }
            UUID shopOwnerId = terminal.getOwnerUuid();
            UUID shopId = null;
            if (shopOwnerId != null) {
                shopId = ShopService.resolveOwnerShopAtPos(
                        centralBank,
                        shopOwnerId,
                        level.dimension().location().toString(),
                        pos
                );
                merchantAccountId = ShopService.resolveSettlementAccountId(
                        centralBank,
                        shopOwnerId,
                        shopId,
                        merchantAccountId
                );
            }
            AccountHolder merchantAccount = centralBank.SearchForAccountByAccountId(merchantAccountId);
            if (merchantAccount == null) {
                player.sendSystemMessage(moneyLiteral("§cMerchant account is missing. Ask the owner to reconfigure this terminal."));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "Merchant account is missing. Ask the owner to reconfigure this terminal.",
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                terminal.triggerPulse(false);
                return;
            }
            long price = terminal.getPriceDollars();
            if (price <= 0L) {
                player.sendSystemMessage(moneyLiteral("§cThis terminal has an invalid price configured."));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "This terminal has an invalid price configured.",
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                terminal.triggerPulse(false);
                return;
            }

            var result = UltimateBankingApiProvider.get().shopPurchase(
                    payer.getAccountUUID(),
                    merchantAccount.getAccountUUID(),
                    price,
                    terminal.getShopName(),
                    "terminal@" + level.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
            );
            if (!result.success()) {
                player.sendSystemMessage(moneyLiteral("§cPayment failed: " + result.reason()));
                ServerActionAlert.send(player,
                        "Payment Terminal",
                        "Payment failed: " + result.reason(),
                        DeliveryAlertPayload.AlertTone.ERROR,
                        5600);
                terminal.triggerPulse(false);
                return;
            }

            terminal.addSale(price);
            if (shopOwnerId != null) {
                ShopService.recordSaleForShop(centralBank, shopOwnerId, shopId, price);
                ShopService.recordPaymentMethod(
                        centralBank,
                        shopOwnerId,
                        shopId,
                        player.getUUID(),
                        false,
                        Math.max(0L, price) * 100L
                );
            } else {
                ShopService.recordSaleForOwner(centralBank, merchantAccount.getPlayerUUID(), price);
            }
            terminal.triggerPulse(true);
            player.sendSystemMessage(moneyLiteral(
                    "§aPayment successful at §b" + terminal.getShopName()
                            + "§a. Charged from account: §6$" + MoneyText.abbreviate(BigDecimal.valueOf(price))
                            + "§a. New balance: §6$" + result.balanceAfter().toPlainString()
            ));
            ServerActionAlert.send(player,
                    "Payment Terminal",
                    "Payment successful at " + terminal.getShopName()
                            + ". Charged: $" + MoneyText.abbreviate(BigDecimal.valueOf(price))
                            + ". New balance: $" + result.balanceAfter().toPlainString(),
                    DeliveryAlertPayload.AlertTone.SUCCESS,
                    5000);

            var merchantPlayer = server.getPlayerList().getPlayer(merchantAccount.getPlayerUUID());
            if (merchantPlayer != null && !merchantPlayer.getUUID().equals(player.getUUID())) {
                merchantPlayer.sendSystemMessage(moneyLiteral(
                        "§aSale received at §b" + terminal.getShopName()
                                + "§a: §6$" + MoneyText.abbreviate(BigDecimal.valueOf(price))
                ));
                ServerActionAlert.send(merchantPlayer,
                        "Payment Terminal",
                        "Sale received at " + terminal.getShopName()
                                + ": $" + MoneyText.abbreviate(BigDecimal.valueOf(price)),
                        DeliveryAlertPayload.AlertTone.SUCCESS,
                        4600);
            }

            PacketDistributor.sendToPlayer(player, UBSCommands.buildHudStatePayload(centralBank, player.getUUID()));
            if (merchantPlayer != null) {
                PacketDistributor.sendToPlayer(merchantPlayer, UBSCommands.buildHudStatePayload(centralBank, merchantPlayer.getUUID()));
            }
        });
    }

    private static void handleShopTerminalOpen(ShopTerminalOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleShopTerminalOpen", payload));
    }

    private static void handleShopTerminalSave(ShopTerminalSavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            if (server == null) {
                return;
            }
            ServerLevel level = resolveServerLevel(server, payload.dimensionId());
            if (level == null) {
                return;
            }
            BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());
            if (!(level.getBlockEntity(pos) instanceof ShopTerminalBlockEntity terminal)) {
                return;
            }
            if (terminal.isFeedbackActive()) {
                int remainingTicks = terminal.getFeedbackTicksRemaining();
                int remainingSeconds = Math.max(1, (remainingTicks + 19) / 20);
                PacketDistributor.sendToPlayer(player, buildSaveResponse(
                        terminal,
                        false,
                        "Terminal is busy. Try again in " + remainingSeconds + "s."
                ));
                return;
            }

            var centralBank = BankManager.getCentralBank(server);
            if (!terminal.canConfigure(player)) {
                PacketDistributor.sendToPlayer(player, buildSaveResponse(
                        terminal,
                        false,
                        "Only the owner or an operator can edit this terminal."
                ));
                return;
            }
            if (centralBank == null) {
                PacketDistributor.sendToPlayer(player, buildSaveResponse(
                        terminal,
                        false,
                        "Bank data is unavailable right now."
                ));
                return;
            }

            long price;
            try {
                price = Long.parseLong(payload.priceDollars().trim());
            } catch (Exception ex) {
                PacketDistributor.sendToPlayer(player, buildSaveResponse(
                        terminal,
                        false,
                        "Price must be a positive whole number."
                ));
                return;
            }
            if (price <= 0L) {
                PacketDistributor.sendToPlayer(player, buildSaveResponse(
                        terminal,
                        false,
                        "Price must be greater than zero."
                ));
                return;
            }

            int successTicks = parsePulseStrength(payload.successPulseTicks(), terminal.getSuccessPulseTicks());
            int failureTicks = parsePulseStrength(payload.failurePulseTicks(), terminal.getFailurePulseTicks());
            int idleStrength = parsePulseStrength(payload.idlePulseStrength(), terminal.getIdlePulseStrength());

            UUID merchantAccountId = null;
            if (payload.merchantAccountId() != null && !payload.merchantAccountId().isBlank()) {
                try {
                    merchantAccountId = UUID.fromString(payload.merchantAccountId().trim());
                } catch (IllegalArgumentException ex) {
                    PacketDistributor.sendToPlayer(player, buildSaveResponse(
                            terminal,
                            false,
                            "Merchant account id is invalid."
                    ));
                    return;
                }
                AccountHolder merchantAccount = centralBank.SearchForAccountByAccountId(merchantAccountId);
                if (merchantAccount == null) {
                    PacketDistributor.sendToPlayer(player, buildSaveResponse(
                            terminal,
                            false,
                            "Merchant account does not exist."
                    ));
                    return;
                }
                if (!player.hasPermissions(3) && !merchantAccount.getPlayerUUID().equals(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, buildSaveResponse(
                            terminal,
                            false,
                            "You can only target your own account unless you are an operator."
                    ));
                    return;
                }
            }

            terminal.updateConfig(
                    payload.shopName(),
                    price,
                    merchantAccountId,
                    payload.pulseOnSuccess(),
                    payload.pulseOnFailure(),
                    payload.pulseOnIdle(),
                    successTicks,
                    failureTicks,
                    idleStrength
            );

            PacketDistributor.sendToPlayer(player, buildSaveResponse(terminal, true, "Terminal configuration saved."));
        });
    }

    private static void handleShopTerminalSaveResponse(ShopTerminalSaveResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleShopTerminalSaveResponse", payload));
    }

    // ─── Shelf / Basket ────────────────────────────────────────────────

    private static void handleShelfUse(ShelfUsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            if (server == null) {
                return;
            }
            if (!payload.mainHand()) {
                // Ignore off-hand duplicate interactions to avoid duplicated feedback/actions.
                return;
            }

            ServerLevel level = resolveServerLevel(server, payload.dimensionId());
            if (level == null || player.level() != level) {
                return;
            }

            BlockPos clickedPos = new BlockPos(payload.x(), payload.y(), payload.z());
            BlockPos lowerPos = ShelfService.toLowerShelfPos(level, clickedPos);
            if (!ShelfService.isShelf(level.getBlockState(lowerPos))) {
                return;
            }
            ShelfDisplayBlockEntity shelfEntity = ShelfService.getDisplayEntity(level, lowerPos);

            double distSq = player.position().distanceToSqr(
                    lowerPos.getX() + 0.5D,
                    lowerPos.getY() + 0.5D,
                    lowerPos.getZ() + 0.5D
            );
            if (distSq > 100.0D) {
                return;
            }

            boolean sessionBasketMode = ShelfBasketSessionService.hasActiveSession(player.getUUID());
            boolean hasBasket = !ShelfService.findBasketInHands(player).isEmpty();
            boolean basketMode = sessionBasketMode || hasBasket;
            boolean shopMode = shelfEntity == null || shelfEntity.isShopMode();

            // Customer shelf shopping is disabled while the linked shop is closed.
            if (shopMode && basketMode && shelfEntity != null
                    && !ShelfService.ensureShopOpenForShopping(player, shelfEntity.getShopId())) {
                return;
            }

            // Regular displays are always reconfigurable via Shift-right-click, even if a basket is held.
            if (payload.configureAction() && shelfEntity != null && !shelfEntity.isShopMode()) {
                if (!ShelfService.canManageShelf(level, lowerPos, player)) {
                    player.sendSystemMessage(moneyLiteral("§cOnly the shop owner or an operator can configure this shelf."));
                    return;
                }
                String preferredKey = preferredShelfKeyForHit(level, lowerPos, payload.hitX(), payload.hitY(), payload.hitZ());
                PacketDistributor.sendToPlayer(player, buildShelfOpenPayload(level, lowerPos, player, preferredKey));
                return;
            }

            // Shift-right opens shelf configuration only when the player is not in shopping basket mode.
            if (payload.configureAction() && !basketMode) {
                if (!ShelfService.canManageShelf(level, lowerPos, player)) {
                    player.sendSystemMessage(moneyLiteral("§cOnly the shop owner or an operator can configure this shelf."));
                    return;
                }
                String preferredKey = preferredShelfKeyForHit(level, lowerPos, payload.hitX(), payload.hitY(), payload.hitZ());
                PacketDistributor.sendToPlayer(player, buildShelfOpenPayload(level, lowerPos, player, preferredKey));
                return;
            }

            if (!shopMode && shelfEntity != null) {
                if (!ShelfService.canManageShelf(level, lowerPos, player)) {
                    player.sendSystemMessage(moneyLiteral("§cOnly the shop owner or an operator can edit this display."));
                    return;
                }
                int slot = ShelfService.resolveSlotByHit(level, lowerPos, payload.hitX(), payload.hitY(), payload.hitZ());
                handleRegularDisplayUse(player, shelfEntity, slot, sessionBasketMode);
                return;
            }

            if (!basketMode) {
                int slot = ShelfService.resolveSlotByHit(level, lowerPos, payload.hitX(), payload.hitY(), payload.hitZ());
                ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(level, lowerPos);
                if (shelf != null) {
                    ItemStack display = shelf.getDisplayItem(slot);
                    long price = shelf.getSlotPrice(slot);
                    if (!display.isEmpty() && price >= 0L) {
                        player.sendSystemMessage(moneyLiteral("§eHold a shopping basket to take items from shelves (Shift + right-click adds a full stack)."));
                    }
                }
                return;
            }

            int slot = ShelfService.resolveSlotByHit(level, lowerPos, payload.hitX(), payload.hitY(), payload.hitZ());
            // Shift + right-click with basket adds a full stack from the selected shelf slot.
            ShelfService.addShelfItemToBasket(player, lowerPos, slot, payload.configureAction());
        });
    }

    private static void handleShelfOpen(ShelfOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleShelfOpen", payload));
    }

    private static void handleShelfAction(ShelfActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            if (server == null) {
                return;
            }

            ServerLevel level = resolveServerLevel(server, payload.dimensionId());
            if (level == null || player.level() != level) {
                return;
            }

            BlockPos rootPos = ShelfService.toLowerShelfPos(level, new BlockPos(payload.rootX(), payload.rootY(), payload.rootZ()));
            // Shelf management UI is restricted to the shop owner (or operators).
            if (!ShelfService.canManageShelf(level, rootPos, player)) {
                PacketDistributor.sendToPlayer(player, new ShelfActionResponsePayload(
                        false,
                        "Only the shop owner or an operator can open this shelf panel."
                ));
                return;
            }
            ShelfSelection targetSelection = parseShelfSelection(payload.shelfPosKey());
            if (targetSelection == null) {
                PacketDistributor.sendToPlayer(player, new ShelfActionResponsePayload(false, "Shelf reference is invalid."));
                PacketDistributor.sendToPlayer(player, buildShelfOpenPayload(level, rootPos, player, rootPos));
                return;
            }
            BlockPos targetPos = ShelfService.toLowerShelfPos(level, targetSelection.pos());

            List<BlockPos> connected = ShelfService.collectConnectedShelves(level, rootPos);
            if (!connected.contains(targetPos)) {
                PacketDistributor.sendToPlayer(player, new ShelfActionResponsePayload(false, "Selected shelf is no longer connected."));
                PacketDistributor.sendToPlayer(player, buildShelfOpenPayload(level, rootPos, player, rootPos));
                return;
            }

            ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(level, targetPos);
            if (shelf == null) {
                PacketDistributor.sendToPlayer(player, new ShelfActionResponsePayload(false, "Shelf data is unavailable."));
                PacketDistributor.sendToPlayer(player, buildShelfOpenPayload(level, rootPos, player, rootPos));
                return;
            }

            boolean canManage = ShelfService.canManageShelf(level, targetPos, player);
            if (!canManage) {
                PacketDistributor.sendToPlayer(player, new ShelfActionResponsePayload(
                        false,
                        "Only the shop owner or an operator can open this shelf panel."
                ));
                return;
            }
            String action = payload.action() == null ? "" : payload.action().trim().toLowerCase();
            if ("positioner_mode".equals(action)) {
                String modeRaw = payload.priceInput() == null ? "" : payload.priceInput().trim().toLowerCase();
                boolean entering = "enter".equals(modeRaw)
                        || "start".equals(modeRaw)
                        || "on".equals(modeRaw)
                        || "1".equals(modeRaw)
                        || "true".equals(modeRaw);
                if (entering) {
                    ShelfService.beginPositionerSpectator(player);
                } else {
                    ShelfService.endPositionerSpectator(player);
                }
                return;
            }

            boolean success = false;
            String message = "Unknown action.";
            int slot = resolveSelectedSlot(shelf, targetSelection.row(), payload.slotIndex());

            switch (action) {
                case "set_slot_inventory", "set_slot" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    int inventorySlot = payload.inventorySlot();
                    if (inventorySlot < 0 || inventorySlot >= player.getInventory().getContainerSize()) {
                        success = false;
                        message = "Select a valid inventory item.";
                        break;
                    }
                    ItemStack selected = player.getInventory().getItem(inventorySlot);
                    if (!ShelfDisplayRules.isAllowedDisplayItem(selected)) {
                        success = false;
                        message = ShelfDisplayRules.blockedReason(selected);
                        break;
                    }
                    long price = shelf.isShopMode()
                            ? parseShelfPrice(payload.priceInput(), shelf.getSlotPrice(slot))
                            : 0L;
                    if (shelf.isShopMode() && price < 0L) {
                        success = false;
                        message = "Price cannot be negative.";
                        break;
                    }
                    int preservedStock = 0;
                    if (!shelf.isCreativeShelf()) {
                        ItemStack existingDisplay = shelf.getDisplayItem(slot);
                        if (!existingDisplay.isEmpty() && ItemStackDataCompat.sameItemSameComponents(existingDisplay, selected)) {
                            preservedStock = Math.max(0, Math.min(64, shelf.getSlotStock(slot)));
                        }
                    }
                    shelf.setSlot(slot, selected, price, shelf.isCreativeShelf() ? Integer.MAX_VALUE : preservedStock);
                    success = true;
                    message = "Shelf slot " + (slot + 1) + " display set to "
                            + selected.getHoverName().getString()
                            + (shelf.isShopMode()
                            ? (shelf.isCreativeShelf() ? "." : " (no items consumed). Use Set Stock to load stock.")
                            : " in regular display mode.");
                }
                case "save_price" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (!shelf.isShopMode()) {
                        success = false;
                        message = "Pricing is disabled in regular display mode. Switch to shop mode first.";
                        break;
                    }
                    if (shelf.getDisplayItem(slot).isEmpty()) {
                        success = false;
                        message = "Set an item for this slot first.";
                        break;
                    }
                    long price = parseShelfPrice(payload.priceInput(), shelf.getSlotPrice(slot));
                    if (price < 0L) {
                        success = false;
                        message = "Price cannot be negative.";
                        break;
                    }
                    shelf.setPriceOnly(slot, price);
                    success = true;
                    message = "Price saved for shelf slot " + (slot + 1) + ".";
                }
                case "save_transform" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (shelf.getDisplayItem(slot).isEmpty()) {
                        success = false;
                        message = "Set an item for this slot first.";
                        break;
                    }
                    float[] parsed = parseShelfTransform(payload.priceInput(), shelf.getSlotTransform(slot));
                    if (parsed == null) {
                        success = false;
                        message = "Invalid transform values.";
                        break;
                    }
                    shelf.setSlotTransform(
                            slot,
                            parsed[0],
                            parsed[1],
                            parsed[2],
                            parsed[3],
                            parsed[4],
                            parsed[5],
                            parsed[6],
                            parsed[7],
                            parsed[8]
                    );
                    success = true;
                    message = "Item position saved for shelf slot " + (slot + 1) + ".";
                }
                case "save_stock" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (!shelf.isShopMode()) {
                        success = false;
                        message = "Stock actions are disabled in regular display mode.";
                        break;
                    }
                    if (shelf.isCreativeShelf()) {
                        success = false;
                        message = "Creative shelf has infinite stock.";
                        break;
                    }
                    if (shelf.getDisplayItem(slot).isEmpty()) {
                        success = false;
                        message = "Set an item for this slot first.";
                        break;
                    }
                    int stock = parseShelfStock(payload.priceInput(), -1);
                    if (stock < 0) {
                        success = false;
                        message = "Stock must be 0 or greater.";
                        break;
                    }
                    stock = Math.min(64, stock);
                    shelf.setStockOnly(slot, stock);
                    success = true;
                    message = "Stock saved for shelf slot " + (slot + 1) + " (max 64).";
                }
                case "save_stock_inventory" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (!shelf.isShopMode()) {
                        success = false;
                        message = "Stock actions are disabled in regular display mode.";
                        break;
                    }
                    if (shelf.isCreativeShelf()) {
                        success = false;
                        message = "Creative shelf has infinite stock.";
                        break;
                    }
                    ItemStack configured = shelf.getDisplayItem(slot);
                    if (configured.isEmpty()) {
                        success = false;
                        message = "Set an item for this slot first.";
                        break;
                    }
                    int inventorySlot = payload.inventorySlot();
                    if (inventorySlot < 0 || inventorySlot >= player.getInventory().getContainerSize()) {
                        success = false;
                        message = "Select a valid inventory stack.";
                        break;
                    }
                    ItemStack selectedStack = player.getInventory().getItem(inventorySlot);
                    if (selectedStack.isEmpty()) {
                        success = false;
                        message = "Selected inventory slot is empty.";
                        break;
                    }
                    if (!ItemStackDataCompat.sameItemSameComponents(configured, selectedStack)) {
                        success = false;
                        message = "Selected stack must match the configured shelf item.";
                        break;
                    }
                    int currentStock = Math.max(0, Math.min(64, shelf.getSlotStock(slot)));
                    if (currentStock >= 64) {
                        success = false;
                        message = "Shelf slot " + (slot + 1) + " is already full (64).";
                        break;
                    }
                    int toAdd = Math.max(0, Math.min(64 - currentStock, selectedStack.getCount()));
                    if (toAdd <= 0) {
                        success = false;
                        message = "Stock must be at least 1.";
                        break;
                    }
                    int newStock = currentStock + toAdd;
                    shelf.setStockOnly(slot, newStock);
                    selectedStack.shrink(toAdd);
                    if (selectedStack.isEmpty()) {
                        player.getInventory().setItem(inventorySlot, ItemStack.EMPTY);
                    }
                    success = true;
                    message = "Added " + toAdd + " stock to shelf slot " + (slot + 1) + ". Now " + newStock + "/64.";
                }
                case "restock_from_stockroom" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (!shelf.isShopMode()) {
                        success = false;
                        message = "Stock actions are disabled in regular display mode.";
                        break;
                    }
                    if (shelf.isCreativeShelf()) {
                        success = false;
                        message = "Creative shelf has infinite stock.";
                        break;
                    }
                    ItemStack configured = shelf.getDisplayItem(slot);
                    if (configured.isEmpty()) {
                        success = false;
                        message = "Set an item for this slot first.";
                        break;
                    }
                    UUID shelfShopId = shelf.getShopId();
                    if (shelfShopId == null) {
                        success = false;
                        message = "This shelf is not linked to a shop stockroom.";
                        break;
                    }
                    var centralBank = BankManager.getCentralBank(server);
                    if (centralBank == null) {
                        success = false;
                        message = "Shop stockroom service is unavailable.";
                        break;
                    }
                    String shelfSlotTarget = level.dimension().location()
                            + ";" + targetPos.getX()
                            + ";" + targetPos.getY()
                            + ";" + targetPos.getZ()
                            + ";" + slot;
                    ShopService.ShopActionResult restock = ShopService.restockShelfSlot(
                            server,
                            centralBank,
                            player.getUUID(),
                            shelfShopId,
                            shelfSlotTarget
                    );
                    success = restock.success();
                    message = restock.message();
                }
                case "take_stock_back" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (!shelf.isShopMode()) {
                        success = false;
                        message = "Stock actions are disabled in regular display mode.";
                        break;
                    }
                    if (shelf.isCreativeShelf()) {
                        success = false;
                        message = "Creative shelf has infinite stock.";
                        break;
                    }
                    ItemStack configured = shelf.getDisplayItem(slot);
                    if (configured.isEmpty()) {
                        success = false;
                        message = "Set an item for this slot first.";
                        break;
                    }
                    int currentStock = Math.max(0, Math.min(64, shelf.getSlotStock(slot)));
                    if (currentStock <= 0) {
                        success = false;
                        message = "This slot has no stock to take back.";
                        break;
                    }

                    int remaining = currentStock;
                    int moved = 0;
                    int maxStack = Math.max(1, configured.getMaxStackSize());
                    while (remaining > 0) {
                        int giveCount = Math.min(remaining, maxStack);
                        ItemStack give = configured.copy();
                        give.setCount(giveCount);
                        player.getInventory().add(give);
                        int inserted = giveCount - give.getCount();
                        if (inserted <= 0) {
                            break;
                        }
                        moved += inserted;
                        remaining -= inserted;
                    }

                    if (moved <= 0) {
                        success = false;
                        message = "No inventory space to take stock back.";
                        break;
                    }

                    shelf.setStockOnly(slot, remaining);
                    success = true;
                    if (remaining > 0) {
                        message = "Took back " + moved + " item(s). " + remaining + " still on shelf (inventory full).";
                    } else {
                        message = "Took back all stock (" + moved + " item(s)).";
                    }
                }
                case "toggle_spin" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (!(shelf instanceof ShopSellingTableBlockEntity table)) {
                        success = false;
                        message = "Spin is only available on display tables.";
                        break;
                    }
                    if (!table.canSpin()) {
                        success = false;
                        message = "Spin is only available on the regular display table.";
                        break;
                    }
                    table.setSpinEnabled(!table.isSpinEnabled());
                    success = true;
                    message = table.isSpinEnabled() ? "Display item spinning enabled." : "Display item spinning disabled.";
                }
                case "toggle_modular_layout" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    if (!(shelf instanceof ModularWallDisplayBlockEntity modular)) {
                        success = false;
                        message = "Layout toggle is only available on modular wall displays.";
                        break;
                    }
                    modular.setFourSlotLayoutEnabled(!modular.isFourSlotLayoutEnabled());
                    success = true;
                    message = modular.isFourSlotLayoutEnabled()
                            ? "Layout set to 4 items (2 per shelf row)."
                            : "Layout set to 2 items (1 centered item per row).";
                }
                case "toggle_shop_mode" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    boolean nextShopMode = !shelf.isShopMode();
                    shelf.setShopMode(nextShopMode);
                    if (!nextShopMode) {
                        // Enforce $0 on every slot when converting to regular display mode.
                        for (int i = 0; i < Math.max(1, shelf.getSlotCount()); i++) {
                            shelf.setPriceOnly(i, 0L);
                        }
                    }
                    success = true;
                    message = nextShopMode
                            ? "Display switched to shop mode (basket + pricing enabled)."
                            : "Display switched to regular mode (direct hand pickup enabled, prices reset to $0).";
                }
                case "clear_slot" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can edit this shelf.";
                        break;
                    }
                    ItemStack display = shelf.getDisplayItem(slot);
                    int returnedToInventory = 0;
                    int droppedOnGround = 0;
                    if (!display.isEmpty()) {
                        int toReturn = shelf.isCreativeShelf()
                                ? 1
                                : Math.max(1, Math.min(64, shelf.getSlotStock(slot)));
                        int maxStack = Math.max(1, display.getMaxStackSize());
                        while (toReturn > 0) {
                            int amount = Math.min(maxStack, toReturn);
                            ItemStack give = display.copy();
                            give.setCount(amount);
                            int before = give.getCount();
                            if (!player.getInventory().add(give) && !give.isEmpty()) {
                                player.drop(give, false);
                            }
                            int remaining = Math.max(0, give.getCount());
                            returnedToInventory += (before - remaining);
                            droppedOnGround += remaining;
                            toReturn -= amount;
                        }
                    }
                    shelf.clearSlot(slot);
                    success = true;
                    if (returnedToInventory > 0 || droppedOnGround > 0) {
                        message = "Shelf slot " + (slot + 1) + " cleared. Returned: " + returnedToInventory
                                + (droppedOnGround > 0 ? " | Dropped: " + droppedOnGround : "") + ".";
                    } else {
                        message = "Shelf slot " + (slot + 1) + " cleared.";
                    }
                }
                case "remove_shelf" -> {
                    if (!canManage) {
                        success = false;
                        message = "Only the shop owner or an operator can remove this shelf.";
                        break;
                    }

                    for (ItemStack drop : shelf.extractDisplayItemsForDrop()) {
                        if (drop == null || drop.isEmpty()) {
                            continue;
                        }
                        if (!player.getInventory().add(drop)) {
                            player.drop(drop, false);
                        }
                    }
                    removeShelfBlock(level, targetPos);
                    success = true;
                    message = "Shelf removed.";
                }
                default -> {
                    // Keep default message.
                }
            }

            PacketDistributor.sendToPlayer(player, new ShelfActionResponsePayload(success, message));
            BlockPos refreshRoot = resolveShelfRefreshRoot(level, rootPos, targetPos);
            PacketDistributor.sendToPlayer(player, buildShelfOpenPayload(level, refreshRoot, player, payload.shelfPosKey()));
        });
    }

    private static void handleShelfActionResponse(ShelfActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleShelfActionResponse", payload));
    }

    private static void handleHandheldTerminalOpen(HandheldTerminalOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleHandheldTerminalOpen", payload));
    }

    private static void handleHandheldTerminalSave(HandheldTerminalSavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            if (server == null) {
                return;
            }
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                PacketDistributor.sendToPlayer(player, new HandheldTerminalSaveResponsePayload(
                        false,
                        "Bank data is unavailable right now.",
                        payload.terminalId(),
                        "",
                        "Handheld Terminal",
                        0L,
                        0L
                ));
                return;
            }

            UUID terminalId;
            try {
                terminalId = UUID.fromString(payload.terminalId().trim());
            } catch (Exception ex) {
                PacketDistributor.sendToPlayer(player, new HandheldTerminalSaveResponsePayload(
                        false,
                        "Terminal session is invalid. Reopen the handheld configuration.",
                        payload.terminalId(),
                        "",
                        "Handheld Terminal",
                        0L,
                        0L
                ));
                return;
            }

            ItemStack stack = HandheldPaymentTerminalItem.findByTerminalId(player, terminalId);
            if (stack.isEmpty() || !HandheldPaymentTerminalItem.isHandheldTerminal(stack)) {
                PacketDistributor.sendToPlayer(player, new HandheldTerminalSaveResponsePayload(
                        false,
                        "Could not find that handheld terminal. Hold it and try again.",
                        payload.terminalId(),
                        "",
                        "Handheld Terminal",
                        0L,
                        0L
                ));
                return;
            }

            if (!HandheldPaymentTerminalItem.canConfigure(stack, player)) {
                PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(
                        stack,
                        false,
                        "Only the owner or an operator can edit this handheld terminal."
                ));
                return;
            }
            HandheldPaymentTerminalItem.ensureOwner(stack, player);

            long price;
            try {
                price = Long.parseLong(payload.priceDollars().trim());
            } catch (Exception ex) {
                PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(
                        stack,
                        false,
                        "Price must be a positive whole number."
                ));
                return;
            }
            if (price <= 0L) {
                PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(
                        stack,
                        false,
                        "Price must be greater than zero."
                ));
                return;
            }
            long maxHandheldPrice = Math.max(1L, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get());
            if (price > maxHandheldPrice) {
                PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(
                        stack,
                        false,
                        "Price exceeds handheld max of $" + MoneyText.abbreviate(BigDecimal.valueOf(maxHandheldPrice))
                                + ". Configure GlobalMaxSingleTransaction to change this limit."
                ));
                return;
            }

            UUID merchantAccountId = null;
            if (payload.merchantAccountId() != null && !payload.merchantAccountId().isBlank()) {
                try {
                    merchantAccountId = UUID.fromString(payload.merchantAccountId().trim());
                } catch (IllegalArgumentException ex) {
                    PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(
                            stack,
                            false,
                            "Merchant account id is invalid."
                    ));
                    return;
                }
                AccountHolder merchantAccount = centralBank.SearchForAccountByAccountId(merchantAccountId);
                if (merchantAccount == null) {
                    PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(
                            stack,
                            false,
                            "Merchant account does not exist."
                    ));
                    return;
                }
                if (!player.hasPermissions(3) && !merchantAccount.getPlayerUUID().equals(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(
                            stack,
                            false,
                            "You can only target your own account unless you are an operator."
                    ));
                    return;
                }
            }

            HandheldPaymentTerminalItem.updateConfig(stack, payload.shopName(), price, merchantAccountId);
            PacketDistributor.sendToPlayer(player, buildHandheldSaveResponse(stack, true, "Handheld terminal saved."));
        });
    }

    private static void handleHandheldTerminalSaveResponse(HandheldTerminalSaveResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleHandheldTerminalSaveResponse", payload));
    }

    // ─── Owner PC ───────────────────────────────────────────────────────

    private static void handleOpenBankOwnerPc(OpenBankOwnerPcPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            boolean hasContextPayload = payload.dimensionId() != null
                    && !payload.dimensionId().isBlank();
            if (hasContextPayload) {
                ResourceLocation dimLoc = ResourceLocation.tryParse(payload.dimensionId().trim());
                if (dimLoc != null) {
                    ResourceKey<Level> levelKey = net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.createValueKey(
                            net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.DIMENSION_REGISTRY_KEY,
                            dimLoc
                    );
                    ServerLevel clickedLevel = server.getLevel(levelKey);
                    if (clickedLevel != null) {
                        BlockPos clickedPos = new BlockPos(payload.x(), payload.y(), payload.z());
                        double distSq = player.level() == clickedLevel
                                ? player.position().distanceToSqr(
                                clickedPos.getX() + 0.5,
                                clickedPos.getY() + 0.5,
                                clickedPos.getZ() + 0.5)
                                : Double.MAX_VALUE;
                        if (distSq <= 100.0D && clickedLevel.getBlockState(clickedPos).is(ModBlocks.BANK_OWNER_PC.get())) {
                            BankOwnerPcService.rememberDesktopContext(
                                    centralBank,
                                    player.getUUID(),
                                    payload.dimensionId().trim(),
                                    payload.x(),
                                    payload.y(),
                                    payload.z()
                            );
                        }
                    }
                }
            }

            boolean includeCentralBankApp = player.hasPermissions(3);
            List<OwnerPcBankAppSummary> apps = BankOwnerPcService.listAccessibleApps(
                    server,
                    centralBank,
                    player.getUUID(),
                    includeCentralBankApp
            );
            int ownedCount = BankOwnerPcService.countOwnedBanks(centralBank, player.getUUID());
            int maxBanks = Math.max(1, Config.PLAYER_BANKS_MAX_BANKS_PER_PLAYER.get());

            // Send desktop machine state first so the client screen opens with the correct per-PC context.
            PacketDistributor.sendToPlayer(player, BankOwnerPcService.buildDesktopData(centralBank, player.getUUID()));
            PacketDistributor.sendToPlayer(player, new OwnerPcBootstrapPayload(apps, ownedCount, maxBanks));
        });
    }

    private static void handleOwnerPcBootstrap(OwnerPcBootstrapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleOwnerPcBootstrap", payload));
    }

    private static void handleOwnerPcDesktopData(OwnerPcDesktopDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleOwnerPcDesktopData", payload));
    }

    private static void handleOwnerPcDesktopAction(OwnerPcDesktopActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                sendOwnerPcDesktopActionResponse(player, payload.action(), false, "Desktop storage is unavailable.");
                return;
            }

            BankOwnerPcService.ActionResult result = BankOwnerPcService.executeDesktopAction(
                    server,
                    centralBank,
                    player,
                    payload.action(),
                    payload.arg1(),
                    payload.arg2(),
                    payload.arg3()
            );
            sendOwnerPcDesktopActionResponse(player, result.action(), result.success(), result.message());
            PacketDistributor.sendToPlayer(player, BankOwnerPcService.buildDesktopData(centralBank, player.getUUID()));
            List<OwnerPcBankAppSummary> apps = BankOwnerPcService.listAccessibleApps(
                    server,
                    centralBank,
                    player.getUUID(),
                    player.hasPermissions(3)
            );
            int ownedCount = BankOwnerPcService.countOwnedBanks(centralBank, player.getUUID());
            int maxBanks = Math.max(1, Config.PLAYER_BANKS_MAX_BANKS_PER_PLAYER.get());
            PacketDistributor.sendToPlayer(player, new OwnerPcBootstrapPayload(apps, ownedCount, maxBanks));
        });
    }

    private static void sendOwnerPcDesktopActionResponse(ServerPlayer player, String action, boolean success, String message) {
        if (player == null) {
            return;
        }
        String safeAction = action == null ? "" : action;
        String safeMessage = message == null ? "" : message;
        if (safeMessage.length() > OWNER_PC_DESKTOP_ACTION_RESPONSE_MAX_CHARS) {
            safeMessage = safeAction.trim().equalsIgnoreCase("ORDER_BOARD_REPORT")
                    ? "Order Board\nOrder Board response exceeded the network string limit (" + safeMessage.length()
                    + " chars). The server refused the oversized payload instead of crashing; clear stale/demo rows or narrow the report and refresh."
                    : "Desktop action response exceeded the network string limit (" + safeMessage.length()
                    + " chars). The server refused the oversized payload instead of crashing.";
            success = false;
        }
        PacketDistributor.sendToPlayer(player, new OwnerPcDesktopActionResponsePayload(safeAction, success, safeMessage));
    }

    private static void handleOwnerPcDesktopActionResponse(OwnerPcDesktopActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleOwnerPcDesktopActionResponse", payload));
    }

    private static void handleOwnerPcBankDataRequest(OwnerPcBankDataRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            boolean allowCentralBankAccess = payload.bankId().equals(centralBank.getBankId()) && player.hasPermissions(3);
            OwnerPcBankDataPayload response = BankOwnerPcService.buildBankDataPayload(
                    server,
                    centralBank,
                    player.getUUID(),
                    payload.bankId(),
                    allowCentralBankAccess
            );
            if (response != null) {
                PacketDistributor.sendToPlayer(player, response);
            }
        });
    }

    private static void handleOwnerPcBankData(OwnerPcBankDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleOwnerPcBankData", payload));
    }

    private static void handleOwnerPcAction(OwnerPcActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                PacketDistributor.sendToPlayer(player, new OwnerPcActionResponsePayload(
                        payload.bankId(), false, "Bank data is unavailable."
                ));
                return;
            }

            BankOwnerPcService.ActionResult result = BankOwnerPcService.executeAction(
                    server,
                    centralBank,
                    player,
                    payload.bankId(),
                    payload.action(),
                    payload.arg1(),
                    payload.arg2(),
                    payload.arg3(),
                    payload.arg4()
            );
            PacketDistributor.sendToPlayer(player, new OwnerPcActionResponsePayload(
                    payload.bankId(),
                    result.success(),
                    result.message()
            ));

            OwnerPcBankDataPayload dataPayload = BankOwnerPcService.buildBankDataPayload(
                    server,
                    centralBank,
                    player.getUUID(),
                    payload.bankId(),
                    payload.bankId().equals(centralBank.getBankId()) && player.hasPermissions(3)
            );
            if (dataPayload != null) {
                PacketDistributor.sendToPlayer(player, dataPayload);
            }
        });
    }

    private static void handleOwnerPcActionResponse(OwnerPcActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleOwnerPcActionResponse", payload));
    }

    private static void handleOwnerPcCreateBank(OwnerPcCreateBankPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                PacketDistributor.sendToPlayer(player, new OwnerPcCreateBankResponsePayload(false, "Bank data is unavailable."));
                return;
            }

            BankOwnerPcService.ActionResult result = BankOwnerPcService.createBank(
                    server,
                    centralBank,
                    player,
                    payload.bankName(),
                    payload.ownershipModel()
            );
            PacketDistributor.sendToPlayer(player, new OwnerPcCreateBankResponsePayload(result.success(), result.message()));

            List<OwnerPcBankAppSummary> apps = BankOwnerPcService.listAccessibleApps(
                    server,
                    centralBank,
                    player.getUUID(),
                    player.hasPermissions(3)
            );
            int ownedCount = BankOwnerPcService.countOwnedBanks(centralBank, player.getUUID());
            int maxBanks = Math.max(1, Config.PLAYER_BANKS_MAX_BANKS_PER_PLAYER.get());
            PacketDistributor.sendToPlayer(player, new OwnerPcBootstrapPayload(apps, ownedCount, maxBanks));
        });
    }

    private static void handleOwnerPcCreateBankResponse(OwnerPcCreateBankResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleOwnerPcCreateBankResponse", payload));
    }

    // ─── PIN Auth ───────────────────────────────────────────────────────

    private static void handlePinAuthRequest(PinAuthRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null || !account.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new PinAuthResponsePayload(false, false, "Account not found."));
                return;
            }

            if (!account.hasPin()) {
                PacketDistributor.sendToPlayer(player, new PinAuthResponsePayload(
                        false, true, "PIN not set. Create a new 4-digit PIN."));
                return;
            }

            String pin = payload.pin() == null ? "" : payload.pin().trim();
            if (!pin.matches("\\d{4}")) {
                PacketDistributor.sendToPlayer(player, new PinAuthResponsePayload(false, false, "PIN must be exactly 4 digits."));
                return;
            }

            boolean success = account.matchesPin(pin);
            PacketDistributor.sendToPlayer(player, new PinAuthResponsePayload(
                    success,
                    false,
                    success ? "" : "Incorrect PIN."
            ));
        });
    }

    private static void handlePinAuthResponse(PinAuthResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handlePinAuthResponse", payload));
    }

    // ─── Balance Inquiry ────────────────────────────────────────────────

    private static void handleBalanceRequest(BalanceRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) return;

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null || !account.getPlayerUUID().equals(player.getUUID())) {
                return;
            }

            Bank bank = centralBank.getBank(account.getBankId());
            String bankName = bank != null ? bank.getBankName() : "Unknown";

            UltimateBankingSystem.LOGGER.info("[UBS] Balance inquiry for account {}", payload.accountId());

            PacketDistributor.sendToPlayer(player, new BalanceResponsePayload(
                account.getAccountType().label,
                bankName,
                account.getAccountUUID().toString(),
                account.getBalance().toPlainString(),
                account.getDateOfCreation().toString()
            ));
        });
    }

    private static void handleBalanceResponse(BalanceResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleBalanceResponse", payload));
    }

    // ─── Withdraw ───────────────────────────────────────────────────────

    private static void handleWithdrawRequest(WithdrawRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) return;

            BigDecimal amount;
            try {
                amount = new BigDecimal(payload.amount());
            } catch (NumberFormatException e) {
                sendWithdrawResponse(player, null, false, "0", "Invalid amount format.");
                return;
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendWithdrawResponse(player, null, false, "0", "Amount must be greater than zero.");
                return;
            }

            int withdrawCents = parseAmountToCents(amount);
            if (withdrawCents <= 0) {
                sendWithdrawResponse(player, null, false, "0", "Amount must be a positive value with up to 2 decimals.");
                return;
            }
            BigDecimal withdrawAmount = BigDecimal.valueOf(withdrawCents, 2);

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null) {
                sendWithdrawResponse(player, null, false, "0", "Account not found.");
                return;
            }

            if (!account.getPlayerUUID().equals(player.getUUID())) {
                sendWithdrawResponse(player, account, false, account.getBalance().toPlainString(), "You do not own this account.");
                return;
            }

            if (account.isFrozen()) {
                sendWithdrawResponse(
                        player,
                        account,
                        false,
                        account.getBalance().toPlainString(),
                        AccountAccessMessages.frozen("ATM withdrawal", account)
                );
                return;
            }

            long gameTime = currentOverworldGameTime(server);
            BigDecimal effectiveLimit = account.getEffectiveWithdrawalLimit(gameTime);
            if (withdrawAmount.compareTo(effectiveLimit) > 0) {
                sendWithdrawResponse(
                        player,
                        account,
                        false,
                        account.getBalance().toPlainString(),
                        "Amount exceeds your active ATM withdrawal limit of $" + effectiveLimit.toPlainString() + "."
                );
                return;
            }

            BigDecimal dailyLimit = account.getConfiguredDailyWithdrawalLimit();
            BigDecimal dailyWithdrawn = account.getDailyWithdrawnAmount();
            BigDecimal remainingToday = account.getRemainingDailyWithdrawalLimit();
            if (withdrawAmount.compareTo(remainingToday) > 0) {
                sendWithdrawResponse(
                        player,
                        account,
                        false,
                        account.getBalance().toPlainString(),
                        "Daily ATM limit exceeded. Limit: $" + dailyLimit.toPlainString()
                                + ", used today: $" + dailyWithdrawn.toPlainString()
                                + ", remaining: $" + remainingToday.toPlainString() + "."
                );
                return;
            }

            if (withdrawCents % 100 != 0) {
                sendWithdrawResponse(
                        player,
                        account,
                        false,
                        account.getBalance().toPlainString(),
                        "ATM dispenses bills only. Use a bank teller for coins."
                );
                return;
            }

            boolean success = account.RemoveBalance(withdrawAmount);

            if (!success) {
                UltimateBankingSystem.LOGGER.info("[UBS] Withdraw ${} from account {} — success: {}",
                    payload.amount(), payload.accountId(), false);
                sendWithdrawResponse(player, account, false, account.getBalance().toPlainString(), "Insufficient funds.");
                return;
            }

            account.registerDailyWithdrawal(withdrawAmount);

            int withdrawDollars = withdrawCents / 100;
            int[] withdrawPlan = DollarBills.buildWithdrawPlan(withdrawDollars);
            if (withdrawPlan == null) {
                account.AddBalance(withdrawAmount);
                account.rollbackDailyWithdrawal(withdrawAmount);
                sendWithdrawResponse(player, account, false, account.getBalance().toPlainString(),
                        "ATM could not dispense the requested bill combination.");
                return;
            }

            DollarBills.giveBills(player, withdrawPlan);
            account.addTransaction(new UserTransaction(
                payload.accountId(),
                ATM_TERMINAL_ID,
                withdrawAmount,
                LocalDateTime.now(),
                "ATM Cash Withdrawal"
            ));
            UltimateBankingSystem.LOGGER.info(
                "[UBS] Withdraw ${} from account {} — dispensed [{}] — success: {}",
                withdrawAmount.toPlainString(), payload.accountId(), DollarBills.formatPlan(withdrawPlan), true);

            sendWithdrawResponse(player, account, true, account.getBalance().toPlainString(), "");
        });
    }

    private static void handleWithdrawResponse(WithdrawResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleWithdrawResponse", payload));
    }

    // ─── Deposit ────────────────────────────────────────────────────────

    private static void handleDepositRequest(DepositRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) return;

            BigDecimal amount;
            try {
                amount = new BigDecimal(payload.amount());
            } catch (NumberFormatException e) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, "0", "Invalid amount format."));
                return;
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, "0", "Amount must be greater than zero."));
                return;
            }

            int depositCents = parseAmountToCents(amount);
            if (depositCents <= 0) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, "0", "Amount must be a positive value with up to 2 decimals."));
                return;
            }
            BigDecimal depositAmount = BigDecimal.valueOf(depositCents, 2);

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, "0", "Account not found."));
                return;
            }

            if (!account.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(), "You do not own this account."));
                return;
            }

            if (account.isFrozen()) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(),
                        AccountAccessMessages.frozen("ATM deposit", account)));
                return;
            }

            int[] availableCash = DollarBills.getAvailableCashCounts(player);
            int availableTotalCents = DollarBills.totalCashValueCents(availableCash);
            if (availableTotalCents < depositCents) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(),
                        "Not enough cash on hand. You have $"
                                + DollarBills.formatCents(availableTotalCents) + " in physical cash."));
                return;
            }

            int[] depositPlan = DollarBills.findCashDepositPlan(depositCents, availableCash);
            if (depositPlan == null) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(),
                        "Cannot form that exact amount with your current bills and coins."));
                return;
            }

            DollarBills.removeCash(player, depositPlan);
            boolean success = account.AddBalance(depositAmount);

            UltimateBankingSystem.LOGGER.info("[UBS] Deposit ${} to account {} — success: {}",
                depositAmount.toPlainString(), payload.accountId(), success);

            if (success) {
                account.addTransaction(new UserTransaction(
                    ATM_TERMINAL_ID,
                    payload.accountId(),
                    depositAmount,
                    LocalDateTime.now(),
                    "ATM Cash Deposit"
                ));
                UltimateBankingSystem.LOGGER.info("[UBS] Deposit cash consumed [{}] from player {}",
                    DollarBills.formatCashPlan(depositPlan), player.getName().getString());
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(true, account.getBalance().toPlainString(), ""));
            } else {
                DollarBills.giveCash(player, depositPlan);
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(), "Deposit failed."));
            }
        });
    }

    private static void handleDepositResponse(DepositResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleDepositResponse", payload));
    }

    // ─── Transfer ───────────────────────────────────────────────────────

    private static void handleTransferRequest(TransferRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) return;

            BigDecimal amount;
            try {
                amount = new BigDecimal(payload.amount());
            } catch (NumberFormatException e) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, "0", "Invalid amount format."));
                return;
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, "0", "Amount must be greater than zero."));
                return;
            }

            AccountHolder sender = centralBank.SearchForAccountByAccountId(payload.senderAccountId());
            if (sender == null) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, "0", "Sender account not found."));
                return;
            }

            if (!sender.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(), "You do not own the sender account."));
                return;
            }

            if (sender.isFrozen()) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(),
                        AccountAccessMessages.frozen("ATM transfer", sender)));
                return;
            }

            AccountHolder recipient = centralBank.SearchForAccountByAccountId(payload.recipientAccountId());
            if (recipient == null) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(), "Recipient account not found."));
                return;
            }

            if (recipient.isFrozen()) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(),
                            AccountAccessMessages.destinationFrozen("ATM transfer", recipient)));
                return;
            }

            if (payload.senderAccountId().equals(payload.recipientAccountId())) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(), "Cannot transfer to the same account."));
                return;
            }

            UserTransaction transaction = new UserTransaction(
                payload.senderAccountId(), payload.recipientAccountId(),
                amount, LocalDateTime.now(), "ATM Transfer"
            );
            boolean success = transaction.makeTransaction(server);

            UltimateBankingSystem.LOGGER.info("[UBS] Transfer ${} from {} to {} — success: {}",
                payload.amount(), payload.senderAccountId(), payload.recipientAccountId(), success);

            // Re-fetch sender balance after transaction
            AccountHolder updatedSender = centralBank.SearchForAccountByAccountId(payload.senderAccountId());
            String newBalance = updatedSender != null ? updatedSender.getBalance().toPlainString() : "0";

            if (success) {
                NeoForge.EVENT_BUS.post(new BalanceChangedEvent(
                    sender,
                    sender.getBalance(),
                    amount,
                    false
                ));
                NeoForge.EVENT_BUS.post(new BalanceChangedEvent(
                    recipient,
                    recipient.getBalance(),
                    amount,
                    true
                ));
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(true, newBalance, ""));
            } else {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, newBalance, "Transfer failed. Check balance or try again later."));
            }
        });
    }

    private static void handleTransferResponse(TransferResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleTransferResponse", payload));
    }

    // ─── Transaction History ────────────────────────────────────────────

    private static void handleTxHistoryRequest(TxHistoryRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null || !account.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new TxHistoryResponsePayload(List.of()));
                return;
            }

            int maxEntries = Math.max(0, Math.min(payload.maxEntries(), 50));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

            List<UserTransaction> orderedTransactions = new ArrayList<>(account.getTransactions().values());
            orderedTransactions.sort(Comparator.comparing(UserTransaction::getTimestamp).reversed());

            List<TransactionSummary> summaries = orderedTransactions.stream()
                .limit(maxEntries)
                .map(tx -> {
                    boolean isIncoming = payload.accountId().equals(tx.getReceiverUUID());
                    UUID counterparty = isIncoming ? tx.getSenderUUID() : tx.getReceiverUUID();
                    String counterpartyShort = counterparty == null
                        ? "unknown"
                        : counterparty.equals(ATM_TERMINAL_ID)
                            ? "ATM"
                        : counterparty.toString().substring(0, Math.min(8, counterparty.toString().length()));
                    return new TransactionSummary(
                        formatter.format(tx.getTimestamp()),
                        tx.getTransactionDescription(),
                        tx.getAmount().toPlainString(),
                        isIncoming,
                        counterpartyShort
                    );
                })
                .toList();

            UltimateBankingSystem.LOGGER.info("[UBS] Tx history for account {}: {} entries",
                payload.accountId(), summaries.size());
            PacketDistributor.sendToPlayer(player, new TxHistoryResponsePayload(summaries));
        });
    }

    private static void handleTxHistoryResponse(TxHistoryResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleTxHistoryResponse", payload));
    }

    // ─── Account Settings ───────────────────────────────────────────────

    private static void handleSetPrimary(SetPrimaryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null || !account.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new SetPrimaryResponsePayload(false, false));
                return;
            }

            centralBank.setPrimaryAccountForPlayer(player.getUUID(), account.getAccountUUID(), payload.setPrimary());
            UltimateBankingSystem.LOGGER.info("[UBS] Set primary={} for account {}",
                payload.setPrimary(), payload.accountId());
            PacketDistributor.sendToPlayer(player, new SetPrimaryResponsePayload(true, account.isPrimaryAccount()));
        });
    }

    private static void handleSetPrimaryResponse(SetPrimaryResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleSetPrimaryResponse", payload));
    }

    private static void handleSetTemporaryWithdrawalLimit(SetTemporaryWithdrawalLimitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null || !account.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new SetTemporaryWithdrawalLimitResponsePayload(
                        false, "0", "0", "", -1L, "Account not found."));
                return;
            }

            String pin = payload.pin() == null ? "" : payload.pin().trim();
            if (!pin.matches("\\d{4}")) {
                sendTemporaryLimitResponse(player, account, server, false, "PIN must be exactly 4 digits.");
                return;
            }

            if (!account.hasPin()) {
                sendTemporaryLimitResponse(player, account, server, false, "PIN not set for this account.");
                return;
            }

            if (!account.matchesPin(pin)) {
                sendTemporaryLimitResponse(player, account, server, false, "Incorrect PIN.");
                return;
            }

            BigDecimal customLimit;
            try {
                customLimit = new BigDecimal(payload.customLimit());
            } catch (NumberFormatException ex) {
                sendTemporaryLimitResponse(player, account, server, false, "Invalid custom limit format.");
                return;
            }

            if (!account.setTemporaryWithdrawalLimit(customLimit, currentOverworldGameTime(server))) {
                sendTemporaryLimitResponse(player, account, server, false,
                        "Custom limit must be a whole dollar amount greater than zero.");
                return;
            }

            sendTemporaryLimitResponse(player, account, server, true, "");
        });
    }

    private static void handleSetTemporaryWithdrawalLimitResponse(SetTemporaryWithdrawalLimitResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleSetTemporaryWithdrawalLimitResponse", payload));
    }

    private static void handleChangePin(ChangePinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null || !account.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "Account not found."));
                UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                    payload.accountId(), false);
                return;
            }

            String newPin = payload.newPin() == null ? "" : payload.newPin().trim();
            if (!newPin.matches("\\d{4}")) {
                PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "PIN must be exactly 4 digits."));
                UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                    payload.accountId(), false);
                return;
            }

            if (account.hasPin()) {
                String currentPin = payload.currentPin() == null ? "" : payload.currentPin().trim();
                if (!currentPin.matches("\\d{4}")) {
                    PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "Current PIN must be 4 digits."));
                    UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                            payload.accountId(), false);
                    return;
                }

                if (!account.matchesPin(currentPin)) {
                    PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "Current PIN is incorrect."));
                    UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                            payload.accountId(), false);
                    return;
                }
            }

            if (!account.setPin(newPin)) {
                PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "PIN must be exactly 4 digits."));
                UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                        payload.accountId(), false);
                return;
            }

            PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(true, ""));
            UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                payload.accountId(), true);
        });
    }

    private static void handleChangePinResponse(ChangePinResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleChangePinResponse", payload));
    }

    private static void handlePayRequestCreate(PayRequestCreatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var requester = (ServerPlayer) context.player();
            var server = requester.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder selected = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (selected == null || !selected.getPlayerUUID().equals(requester.getUUID())) {
                PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(false, "Account mismatch."));
                return;
            }

            String targetPlayerName = payload.targetPlayerName() == null ? "" : payload.targetPlayerName().trim();
            if (targetPlayerName.isEmpty()) {
                PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(false, "Enter a target player name."));
                return;
            }

            ServerPlayer payer = server.getPlayerList().getPlayerByName(targetPlayerName);
            if (payer == null) {
                PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(false, "Player is not online."));
                return;
            }

            if (payer.getUUID().equals(requester.getUUID())) {
                PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(false, "You cannot request money from yourself."));
                return;
            }

            BigDecimal amount;
            try {
                amount = new BigDecimal(payload.amount() == null ? "" : payload.amount().trim());
            } catch (NumberFormatException ex) {
                PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(false, "Invalid amount."));
                return;
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(false, "Amount must be greater than zero."));
                return;
            }

            String destinationRaw = payload.destinationAccountId() == null ? "" : payload.destinationAccountId().trim();
            AccountHolder destination;
            if (destinationRaw.isBlank()) {
                destination = findPreferredReceiverAccount(centralBank, requester.getUUID());
            } else {
                try {
                    destination = findAccountForPlayer(centralBank, requester.getUUID(), UUID.fromString(destinationRaw));
                } catch (IllegalArgumentException ex) {
                    destination = null;
                }
            }

            if (destination == null) {
                PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(
                        false,
                        "No valid destination account. Set primary or choose an account."
                ));
                return;
            }

            PayRequestManager.PayRequest request = PayRequestManager.createRequest(
                    requester.getUUID(),
                    payer.getUUID(),
                    destination.getAccountUUID(),
                    amount
            );

            sendPayRequestPromptChat(payer, requester, request, centralBank);

            PacketDistributor.sendToPlayer(requester, new PayRequestCreateResponsePayload(
                    true,
                    "Pay request sent to " + payer.getName().getString() + ". Destination: " + accountLabel(destination)
            ));
        });
    }

    private static void handlePayRequestCreateResponse(PayRequestCreateResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handlePayRequestCreateResponse", payload));
    }

    private static void handlePayRequestInboxRequest(PayRequestInboxRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null || !account.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new PayRequestInboxResponsePayload(List.of(), "None"));
                return;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm");
            List<PayRequestEntry> entries = new ArrayList<>();
            for (PayRequestManager.PayRequest req : PayRequestManager.getPendingForPayer(player.getUUID())) {
                String requesterName = resolveServerPlayerName(server, req.getRequesterUUID());
                String createdAt = fmt.format(java.time.Instant.ofEpochMilli(req.getCreatedAtMillis()).atZone(java.time.ZoneId.systemDefault()));
                entries.add(new PayRequestEntry(
                        req.getRequestId(),
                        requesterName,
                        req.getAmount().toPlainString(),
                        createdAt
                ));
            }

            PacketDistributor.sendToPlayer(player, new PayRequestInboxResponsePayload(
                    entries,
                    resolvePrimaryAccountLabel(centralBank, player.getUUID())
            ));
        });
    }

    private static void handlePayRequestInboxResponse(PayRequestInboxResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handlePayRequestInboxResponse", payload));
    }

    private static void handlePayRequestAction(PayRequestActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder selected = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (selected == null || !selected.getPlayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "Account mismatch."));
                return;
            }

            PayRequestManager.PayRequest request = PayRequestManager.getRequest(payload.requestId());
            if (request == null) {
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "Pay request expired or missing."));
                return;
            }
            if (!request.getPayerUUID().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "This request is not for you."));
                return;
            }
            if (request.getStatus() != PayRequestManager.Status.PENDING || PayRequestManager.isExpired(request)) {
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "Pay request is no longer pending."));
                return;
            }

            String action = payload.action() == null ? "" : payload.action().trim().toLowerCase();
            if ("decline".equals(action)) {
                PayRequestManager.markDeclined(request.getRequestId());
                ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
                if (requester != null) {
                    requester.sendSystemMessage(moneyLiteral(
                            "§c" + player.getName().getString() + " declined your pay request for $" + request.getAmount().toPlainString() + "."
                    ));
                }
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(true, "Request declined."));
                return;
            }

            AccountHolder sender = null;
            if ("accept_account".equals(action)) {
                String senderRaw = payload.senderAccountId() == null ? "" : payload.senderAccountId().trim();
                if (!senderRaw.isBlank()) {
                    try {
                        UUID senderId = UUID.fromString(senderRaw);
                        AccountHolder candidate = centralBank.SearchForAccountByAccountId(senderId);
                        if (candidate != null && candidate.getPlayerUUID().equals(player.getUUID())) {
                            sender = candidate;
                        }
                    } catch (IllegalArgumentException ignored) {
                        sender = null;
                    }
                }
                if (sender == null) {
                    PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "Choose a valid account."));
                    return;
                }
            } else {
                sender = findPrimaryAccount(centralBank, player.getUUID());
                if (sender == null) {
                    PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "No primary account set. Use Choose Account."));
                    return;
                }
            }

            AccountHolder receiver = findReceiverAccountForRequest(centralBank, request);
            if (receiver == null) {
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "Requester destination account is unavailable."));
                ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
                if (requester != null) {
                    requester.sendSystemMessage(moneyLiteral(
                            "§cYour pay request could not be completed because your destination account is unavailable."
                    ));
                }
                return;
            }

            if (sender.getAccountUUID().equals(receiver.getAccountUUID())) {
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "Cannot pay the same account."));
                return;
            }

            boolean success = new UserTransaction(
                    sender.getAccountUUID(),
                    receiver.getAccountUUID(),
                    request.getAmount(),
                    LocalDateTime.now(),
                    "Pay Request"
            ).makeTransaction(server);

            if (!success) {
                PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(false, "Payment failed. Check balance/account status."));
                ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
                if (requester != null) {
                    requester.sendSystemMessage(moneyLiteral(
                            "§e" + player.getName().getString() + " tried to accept your pay request, but payment failed."
                    ));
                }
                return;
            }

            PayRequestManager.markAccepted(request.getRequestId());

            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(sender, sender.getBalance(), request.getAmount(), false));
            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(receiver, receiver.getBalance(), request.getAmount(), true));

            ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUUID());
            if (requester != null) {
                requester.sendSystemMessage(moneyLiteral(
                        "§a" + player.getName().getString() + " accepted your pay request for $" + request.getAmount().toPlainString() + "."
                ));
            }

            PacketDistributor.sendToPlayer(player, new PayRequestActionResponsePayload(
                    true,
                    MoneyText.abbreviateCurrencyTokens(
                            "Paid $" + request.getAmount().toPlainString() + " using " + sender.getAccountType().label + "."
                    )
            ));
        });
    }

    private static void handlePayRequestActionResponse(PayRequestActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handlePayRequestActionResponse", payload));
    }

    private static ShopTerminalOpenPayload buildShopTerminalOpenPayload(ServerLevel level,
                                                                        BlockPos pos,
                                                                        ShopTerminalBlockEntity terminal,
                                                                        net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
                                                                        UUID playerId) {
        List<ShopTerminalAccountSummary> accountSummaries = new ArrayList<>();
        if (centralBank != null) {
            var accounts = centralBank.SearchForAccount(playerId)
                    .values()
                    .stream()
                    .sorted(Comparator
                            .comparing(AccountHolder::isPrimaryAccount).reversed()
                            .thenComparing(account -> account.getDateOfCreation().toString()))
                    .toList();

            for (AccountHolder account : accounts) {
                Bank bank = centralBank.getBank(account.getBankId());
                String bankName = bank == null ? "Unknown Bank" : bank.getBankName();
                accountSummaries.add(new ShopTerminalAccountSummary(
                        account.getAccountUUID(),
                        account.getAccountType().label,
                        bankName,
                        account.getBalance().toPlainString(),
                        account.isPrimaryAccount()
                ));
            }
        }

        String merchantId = terminal.getMerchantAccountId() == null
                ? ""
                : terminal.getMerchantAccountId().toString();
        return new ShopTerminalOpenPayload(
                level.dimension().location().toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                terminal.getShopName(),
                terminal.getPriceDollars(),
                terminal.getOwnerName(),
                merchantId,
                terminal.isPulseOnSuccess(),
                terminal.isPulseOnFailure(),
                terminal.isPulseOnIdle(),
                terminal.getSuccessPulseTicks(),
                terminal.getFailurePulseTicks(),
                terminal.getIdlePulseStrength(),
                terminal.getTotalSalesDollars(),
                accountSummaries
        );
    }

    private static ShopTerminalSaveResponsePayload buildSaveResponse(ShopTerminalBlockEntity terminal,
                                                                     boolean success,
                                                                     String message) {
        String merchantId = terminal.getMerchantAccountId() == null ? "" : terminal.getMerchantAccountId().toString();
        return new ShopTerminalSaveResponsePayload(
                success,
                message == null ? "" : message,
                merchantId,
                terminal.getShopName(),
                terminal.getPriceDollars(),
                terminal.isPulseOnSuccess(),
                terminal.isPulseOnFailure(),
                terminal.isPulseOnIdle(),
                terminal.getSuccessPulseTicks(),
                terminal.getFailurePulseTicks(),
                terminal.getIdlePulseStrength(),
                terminal.getTotalSalesDollars()
        );
    }

    private static ShelfOpenPayload buildShelfOpenPayload(ServerLevel level,
                                                          BlockPos rootPos,
                                                          ServerPlayer viewer) {
        return buildShelfOpenPayload(level, rootPos, viewer, encodeShelfPosKey(rootPos));
    }

    private static ShelfOpenPayload buildShelfOpenPayload(ServerLevel level,
                                                          BlockPos rootPos,
                                                          ServerPlayer viewer,
                                                          BlockPos preferredSelectedPos) {
        return buildShelfOpenPayload(level, rootPos, viewer, encodeShelfPosKey(preferredSelectedPos));
    }

    private static ShelfOpenPayload buildShelfOpenPayload(ServerLevel level,
                                                          BlockPos rootPos,
                                                          ServerPlayer viewer,
                                                          String preferredSelectionKey) {
        BlockPos resolvedRoot = ShelfService.toLowerShelfPos(level, rootPos);
        List<BlockPos> connected = ShelfService.collectConnectedShelves(level, resolvedRoot);
        List<ShelfUnitSummary> shelfUnits = new ArrayList<>();

        for (BlockPos shelfPos : connected) {
            // Keep management UI scoped to shelves this viewer can actually manage.
            // This prevents adjacent cross-shop displays from appearing in the owner's panel.
            if (!ShelfService.canManageShelf(level, shelfPos, viewer)) {
                continue;
            }
            ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(level, shelfPos);
            if (shelf == null) {
                continue;
            }

            String ownerName = shelf.getOwnerName();
            if (ownerName == null || ownerName.isBlank()) {
                ownerName = "Unknown";
            }
            boolean canManage = true;
            boolean spinCapable = false;
            boolean spinEnabled = false;
            ShelfDisplayType displayType = ShelfTransformBounds.detectType(shelf);
            if (shelf instanceof ShopSellingTableBlockEntity table) {
                spinCapable = table.canSpin();
                spinEnabled = table.isSpinEnabled();
            }
            int slotCount = Math.max(1, shelf.getSlotCount());
            int rowSize = rowSizeForShelf(shelf);
            if (slotCount > rowSize) {
                int rows = (slotCount + rowSize - 1) / rowSize;
                for (int row = 0; row < rows; row++) {
                    List<ShelfSlotSummary> rowSlots = new ArrayList<>();
                    for (int col = 0; col < rowSize; col++) {
                        int slot = row * rowSize + col;
                        if (slot >= slotCount) {
                            break;
                        }
                        rowSlots.add(buildSlotSummary(shelf, slot, col));
                    }
                    shelfUnits.add(new ShelfUnitSummary(
                            encodeShelfPosKey(shelfPos, row),
                            ownerName,
                            canManage,
                            shelf.isCreativeShelf(),
                            shelf.isShopMode(),
                            spinCapable,
                            spinEnabled,
                            displayType.id(),
                            rowSlots
                    ));
                }
            } else {
                List<ShelfSlotSummary> slots = new ArrayList<>();
                for (int slot = 0; slot < slotCount; slot++) {
                    slots.add(buildSlotSummary(shelf, slot, slot));
                }
                shelfUnits.add(new ShelfUnitSummary(
                        encodeShelfPosKey(shelfPos),
                        ownerName,
                        canManage,
                        shelf.isCreativeShelf(),
                        shelf.isShopMode(),
                        spinCapable,
                        spinEnabled,
                        displayType.id(),
                        slots
                ));
            }
        }

        int selectedIndex = shelfUnits.isEmpty() ? -1 : 0;
        String preferredKey = preferredSelectionKey == null || preferredSelectionKey.isBlank()
                ? encodeShelfPosKey(resolvedRoot)
                : normalizeShelfSelectionKey(preferredSelectionKey, level);
        for (int i = 0; i < shelfUnits.size(); i++) {
            if (preferredKey.equalsIgnoreCase(shelfUnits.get(i).posKey())) {
                selectedIndex = i;
                break;
            }
        }

        return new ShelfOpenPayload(
                level.dimension().location().toString(),
                resolvedRoot.getX(),
                resolvedRoot.getY(),
                resolvedRoot.getZ(),
                selectedIndex,
                shelfUnits
        );
    }

    private static ShelfSlotSummary buildSlotSummary(ShelfDisplayBlockEntity shelf, int absoluteSlot, int visibleSlotIndex) {
        ItemStack item = shelf.getDisplayItem(absoluteSlot);
        long price = shelf.getSlotPrice(absoluteSlot);
        boolean configured = !item.isEmpty() && price >= 0L;
        String itemName = configured ? item.getHoverName().getString() : "Empty";
        String itemId = "";
        if (configured) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item.getItem());
            itemId = key == null ? "" : key.toString();
        }
        int stock = shelf.isCreativeShelf() ? Integer.MAX_VALUE : Math.max(0, shelf.getSlotStock(absoluteSlot));
        ItemDisplayTransform transform = shelf.getSlotTransform(absoluteSlot);
        return new ShelfSlotSummary(
                visibleSlotIndex,
                absoluteSlot,
                itemName,
                itemId,
                item.copy(),
                Math.max(0L, price),
                configured,
                stock,
                transform.offsetX(),
                transform.offsetY(),
                transform.offsetZ(),
                transform.rotationX(),
                transform.rotationY(),
                transform.rotationZ(),
                transform.scaleX(),
                transform.scaleY(),
                transform.scaleZ()
        );
    }

    private static String encodeShelfPosKey(BlockPos pos) {
        if (pos == null) {
            return "0,0,0";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String encodeShelfPosKey(BlockPos pos, int coolerRow) {
        return encodeShelfPosKey(pos) + "|r" + Math.max(0, coolerRow);
    }

    private static ShelfSelection parseShelfSelection(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split("\\|", 2);
        String[] split = parts[0].split(",");
        if (split.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(split[0].trim());
            int y = Integer.parseInt(split[1].trim());
            int z = Integer.parseInt(split[2].trim());
            int row = -1;
            if (parts.length == 2) {
                String suffix = parts[1].trim().toLowerCase();
                if (suffix.startsWith("r")) {
                    row = Integer.parseInt(suffix.substring(1));
                }
            }
            return new ShelfSelection(new BlockPos(x, y, z), row);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeShelfSelectionKey(String raw, ServerLevel level) {
        ShelfSelection parsed = parseShelfSelection(raw);
        if (parsed == null) {
            return raw;
        }
        BlockPos lower = ShelfService.toLowerShelfPos(level, parsed.pos());
        if (parsed.row() < 0) {
            return encodeShelfPosKey(lower);
        }
        return encodeShelfPosKey(lower, parsed.row());
    }

    private record ShelfSelection(BlockPos pos, int row) {
    }

    private static BlockPos resolveShelfRefreshRoot(ServerLevel level, BlockPos preferredRoot, BlockPos target) {
        if (ShelfService.isShelf(level.getBlockState(preferredRoot))) {
            return ShelfService.toLowerShelfPos(level, preferredRoot);
        }
        if (ShelfService.isShelf(level.getBlockState(target))) {
            return ShelfService.toLowerShelfPos(level, target);
        }
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos next = target.relative(direction);
            if (ShelfService.isShelf(level.getBlockState(next))) {
                return ShelfService.toLowerShelfPos(level, next);
            }
        }
        return preferredRoot;
    }

    private static void removeShelfBlock(ServerLevel level, BlockPos targetPos) {
        if (level == null || targetPos == null) {
            return;
        }
        BlockState state = level.getBlockState(targetPos);
        if (!ShelfService.isShelf(state)) {
            return;
        }

        if (ShopSellingTableLargeBlock.isLargeTableBlock(state)
                && state.hasProperty(ShopSellingTableLargeBlock.PART)) {
            BlockPos master = ShopSellingTableLargeBlock.getMasterPos(state, targetPos);
            for (BlockPos partPos : ShopSellingTableLargeBlock.footprint(master)) {
                if (ShopSellingTableLargeBlock.isLargeTableBlock(level.getBlockState(partPos))) {
                    level.removeBlock(partPos, false);
                }
            }
            return;
        }

        if (ShelfService.isSellingTable(state)
                && !ShopSellingTableLargeBlock.isLargeTableBlock(state)) {
            level.removeBlock(targetPos, false);
            return;
        }

        level.removeBlock(targetPos.above(), false);
        level.removeBlock(targetPos, false);
    }

    private static void handleRegularDisplayUse(ServerPlayer player,
                                                ShelfDisplayBlockEntity shelf,
                                                int slot,
                                                boolean basketSessionActive) {
        if (player == null || shelf == null) {
            return;
        }
        int resolvedSlot = Math.max(0, Math.min(Math.max(0, shelf.getSlotCount() - 1), slot));
        ItemStack held = player.getMainHandItem();
        if (basketSessionActive || ShelfCartService.isBasketStack(held)) {
            player.sendSystemMessage(moneyLiteral("§eRegular display mode is active. Basket shopping is disabled on this display."));
            return;
        }
        if (held.isEmpty()) {
            takeRegularDisplayItem(player, shelf, resolvedSlot);
            return;
        }
        placeRegularDisplayItem(player, shelf, resolvedSlot, held);
    }

    private static void takeRegularDisplayItem(ServerPlayer player, ShelfDisplayBlockEntity shelf, int slot) {
        ItemStack display = shelf.getDisplayItem(slot);
        if (display.isEmpty()) {
            player.sendSystemMessage(moneyLiteral("§8This display slot is empty."));
            return;
        }
        ItemStack give = display.copy();
        give.setCount(1);
        // Regular display mode behaves like an item frame:
        // taking the item removes the configured display slot entirely.
        shelf.clearSlot(slot);
        player.setItemInHand(InteractionHand.MAIN_HAND, give);
        player.sendSystemMessage(moneyLiteral("Picked up ")
                .withStyle(ChatFormatting.GREEN)
                .append(give.getHoverName())
                .append(moneyLiteral(" from display slot ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(String.valueOf(slot + 1)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
    }

    private static void placeRegularDisplayItem(ServerPlayer player,
                                                ShelfDisplayBlockEntity shelf,
                                                int slot,
                                                ItemStack held) {
        if (held == null || held.isEmpty()) {
            return;
        }
        if (!ShelfDisplayRules.isAllowedDisplayItem(held)) {
            String blocked = ShelfDisplayRules.blockedReason(held);
            player.sendSystemMessage(moneyLiteral(blocked == null ? "This item cannot be displayed." : blocked));
            return;
        }

        ItemStack configured = shelf.getDisplayItem(slot);
        if (configured.isEmpty()) {
            ItemStack placed = held.copy();
            placed.setCount(1);
            // Regular displays keep only a single placed sample item (no stock pooling).
            shelf.setSlot(slot, held, 0L, 1);
            held.shrink(1);
            if (held.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            player.sendSystemMessage(moneyLiteral("Placed ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(placed.getHoverName())
                    .append(moneyLiteral(" into display slot ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(String.valueOf(slot + 1)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
            return;
        }
        player.sendSystemMessage(moneyLiteral("Display slot ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(slot + 1)).withStyle(ChatFormatting.YELLOW))
                .append(moneyLiteral(" already has an item. Take it back first, then place a new one.")
                        .withStyle(ChatFormatting.YELLOW)));
    }

    private static long parseShelfPrice(String raw, long fallback) {
        return ShelfPrice.parseInputToCents(raw, fallback);
    }

    private static int parseShelfStock(String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(raw == null ? "" : raw.trim());
            return Math.max(0, Math.min(64, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float[] parseShelfTransform(String raw, ItemDisplayTransform fallback) {
        if (fallback == null) {
            fallback = ItemDisplayTransform.DEFAULT;
        }
        String input = raw == null ? "" : raw.trim();
        if (input.isEmpty()) {
            return new float[]{
                    fallback.offsetX(),
                    fallback.offsetY(),
                    fallback.offsetZ(),
                    fallback.rotationX(),
                    fallback.rotationY(),
                    fallback.rotationZ(),
                    fallback.scaleX(),
                    fallback.scaleY(),
                    fallback.scaleZ()
            };
        }
        String[] parts = input.split("[,;|]");
        if (parts.length != 5 && parts.length != 9) {
            return null;
        }
        try {
            float x = Float.parseFloat(parts[0].trim());
            float y = Float.parseFloat(parts[1].trim());
            float z = Float.parseFloat(parts[2].trim());
            if (parts.length == 5) {
                // Backward compatibility with legacy transform payloads:
                // x;y;z;rotY;uniformScale
                float rotY = Float.parseFloat(parts[3].trim());
                float scale = Float.parseFloat(parts[4].trim());
                return new float[]{x, y, z, 0.0F, rotY, 0.0F, scale, scale, scale};
            }
            float rotX = Float.parseFloat(parts[3].trim());
            float rotY = Float.parseFloat(parts[4].trim());
            float rotZ = Float.parseFloat(parts[5].trim());
            float scaleX = Float.parseFloat(parts[6].trim());
            float scaleY = Float.parseFloat(parts[7].trim());
            float scaleZ = Float.parseFloat(parts[8].trim());
            return new float[]{x, y, z, rotX, rotY, rotZ, scaleX, scaleY, scaleZ};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int resolveSelectedSlot(ShelfDisplayBlockEntity shelf, int selectedRow, int uiSlotIndex) {
        int rowSize = rowSizeForShelf(shelf);
        int clampedUi = Math.max(0, Math.min(Math.max(0, rowSize - 1), uiSlotIndex));
        if (selectedRow >= 0 && shelf.getSlotCount() > rowSize) {
            int row = Math.max(0, selectedRow);
            int slot = row * rowSize + clampedUi;
            return Math.max(0, Math.min(Math.max(0, shelf.getSlotCount() - 1), slot));
        }
        return Math.max(0, Math.min(Math.max(0, shelf.getSlotCount() - 1), uiSlotIndex));
    }

    private static String preferredShelfKeyForHit(ServerLevel level,
                                                  BlockPos lowerPos,
                                                  double hitX,
                                                  double hitY,
                                                  double hitZ) {
        int slot = ShelfService.resolveSlotByHit(level, lowerPos, hitX, hitY, hitZ);
        ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(level, lowerPos);
        if (shelf != null) {
            int rowSize = rowSizeForShelf(shelf);
            if (shelf.getSlotCount() > rowSize) {
                int row = Math.max(0, slot / rowSize);
                return encodeShelfPosKey(lowerPos, row);
            }
        }
        return encodeShelfPosKey(lowerPos);
    }

    private static int rowSizeForShelf(ShelfDisplayBlockEntity shelf) {
        if (shelf instanceof ModularWallDisplayBlockEntity) {
            return 2;
        }
        if (shelf instanceof GlassCounterDisplayBlockEntity) {
            return GlassCounterDisplayBlockEntity.SHELF_ROWS;
        }
        return 3;
    }

    private static HandheldTerminalSaveResponsePayload buildHandheldSaveResponse(ItemStack stack,
                                                                                  boolean success,
                                                                                  String message) {
        UUID terminalId = HandheldPaymentTerminalItem.readTerminalId(stack);
        UUID merchantId = HandheldPaymentTerminalItem.getMerchantAccountId(stack);
        return new HandheldTerminalSaveResponsePayload(
                success,
                message == null ? "" : message,
                terminalId == null ? "" : terminalId.toString(),
                merchantId == null ? "" : merchantId.toString(),
                HandheldPaymentTerminalItem.getShopName(stack),
                HandheldPaymentTerminalItem.getPriceDollars(stack),
                HandheldPaymentTerminalItem.getTotalSalesDollars(stack)
        );
    }

    private static int parsePulseStrength(String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(raw == null ? "" : raw.trim());
            return Math.max(1, Math.min(15, parsed));
        } catch (NumberFormatException ex) {
            return Math.max(1, Math.min(15, fallback));
        }
    }

    private static int parseAmountToCents(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return -1;
        }
        BigDecimal scaled;
        try {
            scaled = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            return -1;
        }
        try {
            return scaled.movePointRight(2).intValueExact();
        } catch (ArithmeticException ex) {
            return -1;
        }
    }

    private static ServerLevel resolveServerLevel(net.minecraft.server.MinecraftServer server, String dimensionId) {
        if (server == null || dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        ResourceLocation dimLoc = ResourceLocation.tryParse(dimensionId.trim());
        if (dimLoc == null) {
            return null;
        }
        ResourceKey<Level> levelKey = net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.createValueKey(
                net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.DIMENSION_REGISTRY_KEY,
                dimLoc
        );
        return server.getLevel(levelKey);
    }

    private static long currentOverworldGameTime(net.minecraft.server.MinecraftServer server) {
        var overworld = server.getLevel(Level.OVERWORLD);
        return overworld != null ? overworld.getGameTime() : 0L;
    }

    private static void sendTemporaryLimitResponse(net.minecraft.server.level.ServerPlayer player,
                                                   AccountHolder account,
                                                   net.minecraft.server.MinecraftServer server,
                                                   boolean success,
                                                   String errorMessage) {
        long gameTime = currentOverworldGameTime(server);
        BigDecimal defaultLimit = account.getConfiguredWithdrawalLimit();
        BigDecimal effectiveLimit = account.getEffectiveWithdrawalLimit(gameTime);
        BigDecimal temporaryLimit = account.getTemporaryWithdrawalLimitIfActive(gameTime);
        PacketDistributor.sendToPlayer(player, new SetTemporaryWithdrawalLimitResponsePayload(
                success,
                defaultLimit.toPlainString(),
                effectiveLimit.toPlainString(),
                temporaryLimit == null ? "" : temporaryLimit.toPlainString(),
                account.getTemporaryWithdrawalLimitExpiresAtGameTime(gameTime),
                errorMessage == null ? "" : errorMessage
        ));
    }

    private static void sendWithdrawResponse(net.minecraft.server.level.ServerPlayer player,
                                             AccountHolder account,
                                             boolean success,
                                             String newBalance,
                                             String errorMessage) {
        PacketDistributor.sendToPlayer(player, buildWithdrawResponse(
                success,
                newBalance == null ? "0" : newBalance,
                errorMessage == null ? "" : errorMessage,
                account
        ));
    }

    private static WithdrawResponsePayload buildWithdrawResponse(boolean success,
                                                                String newBalance,
                                                                String errorMessage,
                                                                AccountHolder account) {
        if (account == null) {
            return new WithdrawResponsePayload(success, newBalance, errorMessage, "", "", "", -1L);
        }
        return new WithdrawResponsePayload(
                success,
                newBalance,
                errorMessage,
                account.getConfiguredDailyWithdrawalLimit().toPlainString(),
                account.getDailyWithdrawnAmount().toPlainString(),
                account.getRemainingDailyWithdrawalLimit().toPlainString(),
                account.getDailyWithdrawalResetEpochMillis()
        );
    }

    private static AccountHolder findPrimaryAccount(net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
                                                    UUID playerId) {
        for (AccountHolder account : centralBank.SearchForAccount(playerId).values()) {
            if (account.isPrimaryAccount()) {
                return account;
            }
        }
        return null;
    }

    private static BankTellerEntity findBankTeller(net.minecraft.server.MinecraftServer server, UUID tellerId) {
        if (server == null || tellerId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(tellerId);
            if (entity instanceof BankTellerEntity teller) {
                return teller;
            }
        }
        return null;
    }

    private static AccountHolder findPreferredReceiverAccount(net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
                                                              UUID requesterId) {
        var accounts = centralBank.SearchForAccount(requesterId);
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

    private static AccountHolder findReceiverAccountForRequest(net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
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

    private static AccountHolder findAccountForPlayer(net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
                                                      UUID playerId,
                                                      UUID accountId) {
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !account.getPlayerUUID().equals(playerId)) {
            return null;
        }
        return account;
    }

    private static void sendPayRequestPromptChat(ServerPlayer payer,
                                                 ServerPlayer requester,
                                                 PayRequestManager.PayRequest request,
                                                 net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank) {
        AccountHolder destination = findReceiverAccountForRequest(centralBank, request);
        String destinationLabel = destination == null ? "Unavailable" : accountLabel(destination);

        AccountHolder primary = findPrimaryAccount(centralBank, payer.getUUID());
        if (primary == null) {
            payer.sendSystemMessage(moneyLiteral(
                    "§6Pay Request: §e" + requester.getName().getString() + " §7requests §6$"
                            + request.getAmount().toPlainString() + "§7.\n"
                            + "§7Destination: §f" + destinationLabel
            ));
            sendPayRequestAccountChoicesChat(payer, request, centralBank, "No primary account set. Choose account to accept:");
            return;
        }

        String requestId = request.getRequestId().toString();
        MutableComponent body = Component.empty();
        body.append(moneyLiteral("§7From: §e" + requester.getName().getString() + "\n"));
        body.append(moneyLiteral("§7Amount: §6$" + request.getAmount().toPlainString() + "\n"));
        body.append(moneyLiteral("§7Destination: §f" + destinationLabel + "\n"));
        body.append(moneyLiteral("§7Primary account: §f" + accountLabel(primary) + "\n\n"));
        body.append(clickAction("[Accept]", ChatFormatting.GREEN, "/ubs_payrequest accept " + requestId, "Accept with primary account"));
        body.append(moneyLiteral(" "));
        body.append(clickAction("[Decline]", ChatFormatting.RED, "/ubs_payrequest decline " + requestId, "Decline this request"));
        body.append(moneyLiteral(" "));
        body.append(clickAction("[Choose Account]", ChatFormatting.AQUA, "/ubs_payrequest choose " + requestId, "Pay from a different account"));

        payer.sendSystemMessage(ubsMessage(ChatFormatting.GOLD, "§ePay Request", body));
    }

    private static void sendPayRequestAccountChoicesChat(ServerPlayer payer,
                                                         PayRequestManager.PayRequest request,
                                                         net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
                                                         String titleLine) {
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

    private static Component ubsMessage(ChatFormatting accentColor, String title, Component body) {
        return moneyLiteral("§6§lUltimate Banking System §7- ")
                .append(moneyLiteral(title).withStyle(accentColor))
                .append(moneyLiteral("\n§8────────────────────────\n"))
                .append(body);
    }

    private static MutableComponent moneyLiteral(String text) {
        return UbsTranslations.literal(MoneyText.abbreviateCurrencyTokens(text == null ? "" : text));
    }

    private static String accountLabel(AccountHolder account) {
        return account.getAccountType().label + " (" + shortId(account.getAccountUUID()) + ")";
    }

    private static String resolvePrimaryAccountLabel(net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank centralBank,
                                                     UUID playerId) {
        AccountHolder primary = findPrimaryAccount(centralBank, playerId);
        if (primary == null) {
            return "None";
        }
        return primary.getAccountType().label + " (" + shortId(primary.getAccountUUID()) + ")";
    }

    private static String resolveServerPlayerName(net.minecraft.server.MinecraftServer server, UUID playerId) {
        var player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }
        return shortId(playerId);
    }

    private static String shortId(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }
}
