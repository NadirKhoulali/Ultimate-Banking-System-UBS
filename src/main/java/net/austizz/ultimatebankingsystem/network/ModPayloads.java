package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.command.UBSCommands;
import net.austizz.ultimatebankingsystem.events.BalanceChangedEvent;
import net.austizz.ultimatebankingsystem.gui.screens.ATMScreenHelper;
import net.austizz.ultimatebankingsystem.gui.screens.BankOwnerPcScreen;
import net.austizz.ultimatebankingsystem.gui.screens.BankScreen;
import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.screens.ClientOwnerPcData;
import net.austizz.ultimatebankingsystem.gui.screens.OwnerPcScreenHelper;
import net.austizz.ultimatebankingsystem.client.HudClientState;
import net.austizz.ultimatebankingsystem.gui.screens.layers.AccountSettingsLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.BalanceInquiryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.CreatePayRequestLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.DepositLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.PayRequestsLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.PinEntryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.TransactionHistoryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.TransferLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.WithdrawLayer;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.payrequest.PayRequestManager;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.math.BigDecimal;
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
 * <p>NeoForge auto-discovers this class via {@link EventBusSubscriber} and fires
 * {@link RegisterPayloadHandlersEvent} on the mod event bus during startup.</p>
 *
 * <h3>Adding a new payload</h3>
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
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class ModPayloads {

    private static final UUID ATM_TERMINAL_ID = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:atm-terminal".getBytes(StandardCharsets.UTF_8));

    private ModPayloads() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        UltimateBankingSystem.LOGGER.info("[UBS] Registering network payloads");
        final PayloadRegistrar registrar = event.registrar("1");

        // --- Register payloads below this line ---
        registrar.playToServer(OpenATMPayload.TYPE, OpenATMPayload.STREAM_CODEC, ModPayloads::handleOpenATM);
        registrar.playToClient(AccountListPayload.TYPE, AccountListPayload.STREAM_CODEC, ModPayloads::handleAccountList);
        registrar.playToServer(OpenBankOwnerPcPayload.TYPE, OpenBankOwnerPcPayload.STREAM_CODEC, ModPayloads::handleOpenBankOwnerPc);
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
    }

    private static void handleHudState(HudStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            HudClientState.setBalanceText(payload.balance());
            HudClientState.setEnabled(payload.enabled());
        });
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
            for (var account : playerAccounts.values()) {
                var bank = centralBank.getBank(account.getBankId());
                String bankName = bank != null ? bank.getBankName() : "Unknown";
                BigDecimal defaultLimit = account.getConfiguredWithdrawalLimit();
                BigDecimal effectiveLimit = account.getEffectiveWithdrawalLimit(gameTime);
                BigDecimal temporaryLimit = account.getTemporaryWithdrawalLimitIfActive(gameTime);
                BigDecimal dailyLimit = account.getConfiguredDailyWithdrawalLimit();
                BigDecimal dailyWithdrawn = account.getDailyWithdrawnAmount();
                BigDecimal dailyRemaining = account.getRemainingDailyWithdrawalLimit();
                summaries.add(new AccountSummary(
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
                ));
            }

            UltimateBankingSystem.LOGGER.info("[UBS] Sending {} accounts to player {}", summaries.size(), player.getName().getString());
            PacketDistributor.sendToPlayer(player, new AccountListPayload(summaries));

            PacketDistributor.sendToPlayer(player, UBSCommands.buildHudStatePayload(centralBank, player.getUUID()));
        });
    }

    private static void handleAccountList(AccountListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientATMData.setAccounts(payload.accounts());
            ClientATMData.setSelectedAccount(null);
            ClientATMData.setAuthenticatedAccountId(null);
            // Auto-select primary account if one exists
            for (var acc : payload.accounts()) {
                if (acc.isPrimary()) {
                    ClientATMData.setSelectedAccount(acc);
                    break;
                }
            }
            ATMScreenHelper.openATMScreen();
        });
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
                    ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
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

            PacketDistributor.sendToPlayer(player, new OwnerPcBootstrapPayload(apps, ownedCount, maxBanks));
            PacketDistributor.sendToPlayer(player, BankOwnerPcService.buildDesktopData(centralBank, player.getUUID()));
        });
    }

    private static void handleOwnerPcBootstrap(OwnerPcBootstrapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientOwnerPcData.setApps(payload.apps(), payload.ownedCount(), payload.maxBanks());
            if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
                ownerScreen.refreshFromNetwork();
            } else {
                OwnerPcScreenHelper.openOwnerPcScreen();
            }
        });
    }

    private static void handleOwnerPcDesktopData(OwnerPcDesktopDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientOwnerPcData.setDesktopData(payload);
            if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
                ownerScreen.refreshFromNetwork();
            }
        });
    }

    private static void handleOwnerPcDesktopAction(OwnerPcDesktopActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                PacketDistributor.sendToPlayer(player, new OwnerPcDesktopActionResponsePayload(
                        payload.action(),
                        false,
                        "Desktop storage is unavailable."
                ));
                return;
            }

            BankOwnerPcService.ActionResult result = BankOwnerPcService.executeDesktopAction(
                    centralBank,
                    player.getUUID(),
                    payload.action(),
                    payload.arg1(),
                    payload.arg2()
            );
            PacketDistributor.sendToPlayer(player, new OwnerPcDesktopActionResponsePayload(
                    result.action(),
                    result.success(),
                    result.message()
            ));
            PacketDistributor.sendToPlayer(player, BankOwnerPcService.buildDesktopData(centralBank, player.getUUID()));
        });
    }

    private static void handleOwnerPcDesktopActionResponse(OwnerPcDesktopActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientOwnerPcData.setToast(payload.success(), payload.message());
            if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
                ownerScreen.handleDesktopActionResponse(payload);
                ownerScreen.refreshFromNetwork();
            }
        });
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
        context.enqueueWork(() -> {
            ClientOwnerPcData.setCurrentBankData(payload);
            if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
                ownerScreen.refreshFromNetwork();
            }
        });
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
        context.enqueueWork(() -> {
            ClientOwnerPcData.setActionOutput(payload.message());
            String raw = payload.message() == null ? "" : payload.message();
            String toastMessage = raw;
            int firstNewline = raw.indexOf('\n');
            if (firstNewline >= 0) {
                toastMessage = payload.success()
                        ? "Action complete. See output panel for details."
                        : raw.substring(0, firstNewline).trim();
            }
            ClientOwnerPcData.setToast(payload.success(), toastMessage);
            if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
                ownerScreen.refreshFromNetwork();
            }
        });
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
        context.enqueueWork(() -> {
            ClientOwnerPcData.setToast(payload.success(), payload.message());
            if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
                ownerScreen.refreshFromNetwork();
            }
        });
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof PinEntryLayer layer) {
                layer.updateAuthResult(payload);
            }
        });
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
        context.enqueueWork(() -> {
            if (!(Minecraft.getInstance().screen instanceof BankScreen bs)) {
                return;
            }

            if (bs.getTopLayer() instanceof BalanceInquiryLayer balanceLayer) {
                balanceLayer.updateData(payload);
            } else if (bs.getTopLayer() instanceof AccountSettingsLayer settingsLayer) {
                settingsLayer.updateAccountInfo(payload);
            }
        });
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

            if (amount.stripTrailingZeros().scale() > 0) {
                sendWithdrawResponse(player, null, false, "0", "ATM withdraw only supports whole-dollar bills.");
                return;
            }

            int dollarAmount;
            try {
                dollarAmount = amount.intValueExact();
            } catch (ArithmeticException e) {
                sendWithdrawResponse(player, null, false, "0", "Amount is too large.");
                return;
            }

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
                String reason = account.getFrozenReason();
                sendWithdrawResponse(
                        player,
                        account,
                        false,
                        account.getBalance().toPlainString(),
                        "This account is frozen." + (reason.isEmpty() ? "" : " Reason: " + reason)
                );
                return;
            }

            long gameTime = currentOverworldGameTime(server);
            BigDecimal effectiveLimit = account.getEffectiveWithdrawalLimit(gameTime);
            if (amount.compareTo(effectiveLimit) > 0) {
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
            if (amount.compareTo(remainingToday) > 0) {
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

            boolean success = account.RemoveBalance(BigDecimal.valueOf(dollarAmount));

            if (!success) {
                UltimateBankingSystem.LOGGER.info("[UBS] Withdraw ${} from account {} — success: {}",
                    payload.amount(), payload.accountId(), false);
                sendWithdrawResponse(player, account, false, account.getBalance().toPlainString(), "Insufficient funds.");
                return;
            }

            account.registerDailyWithdrawal(BigDecimal.valueOf(dollarAmount));

            int[] withdrawPlan = DollarBills.buildWithdrawPlan(dollarAmount);
            if (withdrawPlan == null) {
                account.AddBalance(BigDecimal.valueOf(dollarAmount));
                account.rollbackDailyWithdrawal(BigDecimal.valueOf(dollarAmount));
                sendWithdrawResponse(player, account, false, account.getBalance().toPlainString(),
                        "ATM could not dispense the requested bill combination.");
                return;
            }

            DollarBills.giveBills(player, withdrawPlan);
            account.addTransaction(new UserTransaction(
                payload.accountId(),
                ATM_TERMINAL_ID,
                BigDecimal.valueOf(dollarAmount),
                LocalDateTime.now(),
                "ATM Cash Withdrawal"
            ));
            UltimateBankingSystem.LOGGER.info(
                "[UBS] Withdraw ${} from account {} — dispensed [{}] — success: {}",
                dollarAmount, payload.accountId(), DollarBills.formatPlan(withdrawPlan), true);

            sendWithdrawResponse(player, account, true, account.getBalance().toPlainString(), "");
        });
    }

    private static void handleWithdrawResponse(WithdrawResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof WithdrawLayer layer) {
                layer.updateResult(payload);
            }
        });
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

            if (amount.stripTrailingZeros().scale() > 0) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, "0", "ATM deposit only accepts whole-dollar bills."));
                return;
            }

            int dollarAmount;
            try {
                dollarAmount = amount.intValueExact();
            } catch (ArithmeticException e) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, "0", "Amount is too large."));
                return;
            }

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
                String reason = account.getFrozenReason();
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(),
                        "This account is frozen." + (reason.isEmpty() ? "" : " Reason: " + reason)));
                return;
            }

            int[] availableBills = DollarBills.getAvailableBillCounts(player);
            int availableTotal = DollarBills.totalValue(availableBills);
            if (availableTotal < dollarAmount) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(),
                        "Not enough cash on hand. You have $" + availableTotal + " in bill items."));
                return;
            }

            int[] depositPlan = DollarBills.findDepositPlan(dollarAmount, availableBills);
            if (depositPlan == null) {
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(),
                        "Cannot form that exact amount with your current bill denominations."));
                return;
            }

            DollarBills.removeBills(player, depositPlan);
            boolean success = account.AddBalance(BigDecimal.valueOf(dollarAmount));

            UltimateBankingSystem.LOGGER.info("[UBS] Deposit ${} to account {} — success: {}",
                dollarAmount, payload.accountId(), success);

            if (success) {
                account.addTransaction(new UserTransaction(
                    ATM_TERMINAL_ID,
                    payload.accountId(),
                    BigDecimal.valueOf(dollarAmount),
                    LocalDateTime.now(),
                    "ATM Cash Deposit"
                ));
                UltimateBankingSystem.LOGGER.info("[UBS] Deposit bills consumed [{}] from player {}",
                    DollarBills.formatPlan(depositPlan), player.getName().getString());
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(true, account.getBalance().toPlainString(), ""));
            } else {
                DollarBills.giveBills(player, depositPlan);
                PacketDistributor.sendToPlayer(player,
                    new DepositResponsePayload(false, account.getBalance().toPlainString(), "Deposit failed."));
            }
        });
    }

    private static void handleDepositResponse(DepositResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof DepositLayer layer) {
                layer.updateResult(payload);
            }
        });
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
                String reason = sender.getFrozenReason();
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(),
                        "Sender account is frozen." + (reason.isEmpty() ? "" : " Reason: " + reason)));
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
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(), "Recipient account is frozen."));
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof TransferLayer layer) {
                layer.updateResult(payload);
            }
        });
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof TransactionHistoryLayer layer) {
                layer.updateEntries(payload.entries());
            }
        });
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

            if (payload.setPrimary()) {
                for (AccountHolder candidate : centralBank.SearchForAccount(player.getUUID()).values()) {
                    if (!candidate.getAccountUUID().equals(account.getAccountUUID())) {
                        candidate.setPrimaryAccount(false);
                    }
                }
            }
            account.setPrimaryAccount(payload.setPrimary());
            UltimateBankingSystem.LOGGER.info("[UBS] Set primary={} for account {}",
                payload.setPrimary(), payload.accountId());
            PacketDistributor.sendToPlayer(player, new SetPrimaryResponsePayload(true, account.isPrimaryAccount()));
        });
    }

    private static void handleSetPrimaryResponse(SetPrimaryResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
                layer.updatePrimaryResult(payload);
            }
        });
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
                layer.updateWithdrawalLimitResult(payload);
            }
        });
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
                layer.updatePinResult(payload);
            } else if (Minecraft.getInstance().screen instanceof BankScreen bs2
                    && bs2.getTopLayer() instanceof PinEntryLayer pinLayer) {
                pinLayer.updatePinSetupResult(payload);
            }
        });
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof CreatePayRequestLayer layer) {
                layer.updateResult(payload);
            }
        });
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof PayRequestsLayer layer) {
                layer.updateInbox(payload.requests(), payload.primaryAccountLabel());
            }
        });
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
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BankScreen bs
                    && bs.getTopLayer() instanceof PayRequestsLayer layer) {
                layer.updateActionResult(payload);
            }
        });
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
        return Component.literal(MoneyText.abbreviateCurrencyTokens(text == null ? "" : text));
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
