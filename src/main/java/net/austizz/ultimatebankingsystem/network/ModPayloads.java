package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.gui.screens.ATMScreenHelper;
import net.austizz.ultimatebankingsystem.gui.screens.BankScreen;
import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.screens.layers.AccountSettingsLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.BalanceInquiryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.DepositLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.TransactionHistoryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.TransferLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.WithdrawLayer;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.math.BigDecimal;
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

    private ModPayloads() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        UltimateBankingSystem.LOGGER.info("[UBS] Registering network payloads");
        final PayloadRegistrar registrar = event.registrar("1");

        // --- Register payloads below this line ---
        registrar.playToServer(OpenATMPayload.TYPE, OpenATMPayload.STREAM_CODEC, ModPayloads::handleOpenATM);
        registrar.playToClient(AccountListPayload.TYPE, AccountListPayload.STREAM_CODEC, ModPayloads::handleAccountList);

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
    }

    // ─── OpenATM ────────────────────────────────────────────────────────

    private static void handleOpenATM(OpenATMPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) return;

            var playerAccounts = centralBank.SearchForAccount(player.getUUID());
            List<AccountSummary> summaries = new ArrayList<>();
            for (var account : playerAccounts.values()) {
                var bank = centralBank.getBank(account.getBankId());
                String bankName = bank != null ? bank.getBankName() : "Unknown";
                summaries.add(new AccountSummary(
                    account.getAccountUUID(),
                    account.getAccountType().label,
                    bankName,
                    account.getBalance().toPlainString(),
                    account.isPrimaryAccount()
                ));
            }

            UltimateBankingSystem.LOGGER.info("[UBS] Sending {} accounts to player {}", summaries.size(), player.getName().getString());
            PacketDistributor.sendToPlayer(player, new AccountListPayload(summaries));
        });
    }

    private static void handleAccountList(AccountListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientATMData.setAccounts(payload.accounts());
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

    // ─── Balance Inquiry ────────────────────────────────────────────────

    private static void handleBalanceRequest(BalanceRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) return;

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null) return;

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
                PacketDistributor.sendToPlayer(player,
                    new WithdrawResponsePayload(false, "0", "Invalid amount format."));
                return;
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                PacketDistributor.sendToPlayer(player,
                    new WithdrawResponsePayload(false, "0", "Amount must be greater than zero."));
                return;
            }

            if (amount.stripTrailingZeros().scale() > 0) {
                PacketDistributor.sendToPlayer(player,
                    new WithdrawResponsePayload(false, "0", "ATM withdraw only supports whole-dollar bills."));
                return;
            }

            int dollarAmount;
            try {
                dollarAmount = amount.intValueExact();
            } catch (ArithmeticException e) {
                PacketDistributor.sendToPlayer(player,
                    new WithdrawResponsePayload(false, "0", "Amount is too large."));
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null) {
                PacketDistributor.sendToPlayer(player,
                    new WithdrawResponsePayload(false, "0", "Account not found."));
                return;
            }

            boolean success = account.RemoveBalance(BigDecimal.valueOf(dollarAmount));

            if (!success) {
                UltimateBankingSystem.LOGGER.info("[UBS] Withdraw ${} from account {} — success: {}",
                    payload.amount(), payload.accountId(), false);
                PacketDistributor.sendToPlayer(player,
                    new WithdrawResponsePayload(false, account.getBalance().toPlainString(), "Insufficient funds."));
                return;
            }

            int[] withdrawPlan = DollarBills.buildWithdrawPlan(dollarAmount);
            if (withdrawPlan == null) {
                account.AddBalance(BigDecimal.valueOf(dollarAmount));
                PacketDistributor.sendToPlayer(player,
                    new WithdrawResponsePayload(false, account.getBalance().toPlainString(), "ATM could not dispense the requested bill combination."));
                return;
            }

            DollarBills.giveBills(player, withdrawPlan);
            UltimateBankingSystem.LOGGER.info(
                "[UBS] Withdraw ${} from account {} — dispensed [{}] — success: {}",
                dollarAmount, payload.accountId(), DollarBills.formatPlan(withdrawPlan), true);

            PacketDistributor.sendToPlayer(player,
                new WithdrawResponsePayload(true, account.getBalance().toPlainString(), ""));
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

            AccountHolder recipient = centralBank.SearchForAccountByAccountId(payload.recipientAccountId());
            if (recipient == null) {
                PacketDistributor.sendToPlayer(player,
                    new TransferResponsePayload(false, sender.getBalance().toPlainString(), "Recipient account not found."));
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
            if (account == null) {
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
            if (account == null) {
                PacketDistributor.sendToPlayer(player, new SetPrimaryResponsePayload(false, false));
                return;
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

    private static void handleChangePin(ChangePinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.getServer();
            var centralBank = BankManager.getCentralBank(server);
            if (centralBank == null) {
                return;
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(payload.accountId());
            if (account == null) {
                PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "Account not found."));
                UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                    payload.accountId(), false);
                return;
            }

            String newPin = payload.newPin() == null ? "" : payload.newPin().trim();
            if (newPin.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "New PIN cannot be empty."));
                UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                    payload.accountId(), false);
                return;
            }

            if (!account.matchesPassword(payload.currentPin())) {
                PacketDistributor.sendToPlayer(player, new ChangePinResponsePayload(false, "Current PIN is incorrect."));
                UltimateBankingSystem.LOGGER.info("[UBS] PIN change for account {} — success: {}",
                    payload.accountId(), false);
                return;
            }

            account.setPassword(newPin);
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
            }
        });
    }
}
