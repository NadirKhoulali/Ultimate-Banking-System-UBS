package net.austizz.ultimatebankingsystem.npc;

import com.mojang.authlib.GameProfile;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.api.ApiItemResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.owner.setup.BankSafeSetupPayloadBuilder;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAccessLogService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeVaultReadinessOperation;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.BankTellerAccountSummary;
import net.austizz.ultimatebankingsystem.network.BankTellerOpenPayload;
import net.austizz.ultimatebankingsystem.network.BankTellerSafeBoxState;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.payments.WalletBankingCashService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class BankTellerService {

    private static final UUID BANK_TELLER_TERMINAL_ID = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:bank-teller".getBytes(StandardCharsets.UTF_8));
    private static final double MAX_INTERACT_DISTANCE_SQ = 8.0D * 8.0D;
    private static final BigDecimal TELLER_ACCOUNT_OPEN_BASE_FEE = new BigDecimal("25");
    private static final BigDecimal TELLER_ACCOUNT_OPEN_FIRST_ACCOUNT_EXTRA_FEE = new BigDecimal("75");
    private static final BigDecimal DEFAULT_TELLER_WITHDRAWAL_LIMIT = new BigDecimal("250000");
    private static final BigDecimal MAX_TELLER_WITHDRAWAL_LIMIT =
            BigDecimal.valueOf(Integer.MAX_VALUE).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN);

    public record ActionResult(boolean success, String message, boolean refreshOpenPayload, boolean closeScreen) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, MoneyText.abbreviateCurrencyTokens(message == null ? "" : message), true, false);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, MoneyText.abbreviateCurrencyTokens(message == null ? "" : message), true, false);
        }

        public static ActionResult beginExternalPayment(String message) {
            return new ActionResult(true, MoneyText.abbreviateCurrencyTokens(message == null ? "" : message), false, true);
        }

        public static ActionResult beginEscort(String message) {
            return new ActionResult(true, MoneyText.abbreviateCurrencyTokens(message == null ? "" : message), false, true);
        }
    }

    public record ExternalFeePayment(
            BigDecimal amountPaid,
            String methodLabel,
            UUID cardAccountId
    ) {}

    private record RecipientProfile(UUID id, String name) {}

    private record ChequeData(
            String chequeId,
            BigDecimal amount,
            UUID recipientId,
            UUID writerId
    ) {}

    private record HeldCheque(ItemStack stack, ChequeData cheque) {}

    private record NoteData(
            String serial,
            BigDecimal amount
    ) {}

    private record HeldNote(ItemStack stack, NoteData note) {}

    private record SafeBoxAssignment(UUID accountId,
                                     String boxNumber,
                                     String dimension,
                                     int x,
                                     int y,
                                     int z,
                                     int doorIndex,
                                     boolean locked) {}

    private BankTellerService() {}

    private enum PaymentMode {
        ACCOUNT("ACCOUNT"),
        CASH_OR_CARD("CASH_OR_CARD");

        private final String token;

        PaymentMode(String token) {
            this.token = token;
        }
    }

    public static BankTellerOpenPayload buildOpenPayload(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         ServerPlayer player,
                                                         BankTellerEntity teller) {
        if (server == null || centralBank == null || player == null || teller == null) {
            return new BankTellerOpenPayload(
                    UUID.randomUUID(),
                    "Bank Teller",
                    "",
                    "",
                    "",
                    "0",
                    "0",
                    false,
                    List.of(),
                    List.of(),
                    BankTellerSafeBoxState.unavailable(null, null, "Bank teller service is unavailable.")
            );
        }

        UUID boundBankId = teller.getBoundBankId();
        String boundBankIdRaw = boundBankId == null ? "" : boundBankId.toString();
        String boundBankName = "Unbound Teller";
        String motto = "";
        String issueFee = "0";
        String replacementFee = "0";
        List<String> safeBoxPolicies = List.of();

        if (boundBankId != null) {
            Bank boundBank = centralBank.getBank(boundBankId);
            if (boundBank != null && boundBank.getBankName() != null && !boundBank.getBankName().isBlank()) {
                boundBankName = boundBank.getBankName();
            } else {
                boundBankName = shortId(boundBankId);
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(boundBankId);
            motto = metadata.getString("motto");
            issueFee = CreditCardService.getIssueFee(centralBank, boundBankId).toPlainString();
            replacementFee = CreditCardService.getReplacementFee(centralBank, boundBankId).toPlainString();
            safeBoxPolicies = BankOwnerPcService.buildSafePolicyRows(server, centralBank, boundBankId);
        }

        List<AccountHolder> playerAccounts = new ArrayList<>(centralBank.SearchForAccount(player.getUUID()).values());
        playerAccounts.sort(Comparator
                .comparing(AccountHolder::isPrimaryAccount).reversed()
                .thenComparing(AccountHolder::getDateOfCreation));

        List<BankTellerAccountSummary> summaries = new ArrayList<>();
        UUID centralBankId = centralBank.getBankId();
        for (AccountHolder account : playerAccounts) {
            UUID accountBankId = account.getBankId() == null ? centralBankId : account.getBankId();
            Bank bank = centralBank.getBank(accountBankId);
            String bankName = bank == null ? "Unknown Bank" : bank.getBankName();
            boolean centralAccount = centralBankId != null && centralBankId.equals(accountBankId);
            boolean cardEligible = boundBankId == null
                    ? centralAccount
                    : (boundBankId.equals(accountBankId) || centralAccount);
            String accountIssueFee = CreditCardService.getIssueFee(centralBank, accountBankId).toPlainString();
            String accountReplacementFee = CreditCardService.getReplacementFee(centralBank, accountBankId).toPlainString();
            boolean hasActiveCard = CreditCardService.hasActiveCardForAccount(centralBank, account.getAccountUUID());
            summaries.add(new BankTellerAccountSummary(
                    account.getAccountUUID(),
                    accountBankId,
                    bankName,
                    account.getAccountType().label,
                    account.getBalance().toPlainString(),
                    accountIssueFee,
                    accountReplacementFee,
                    account.isPrimaryAccount(),
                    cardEligible,
                    hasActiveCard
            ));
        }

        Bank openTarget = resolveOpenAccountTargetBank(centralBank, teller);
        boolean openAccountFree = isCentralBankTarget(centralBank, openTarget);
        BankTellerSafeBoxState safeBoxState = buildSafeBoxState(
                new SafeBoxStateRequest(server, centralBank, teller, playerAccounts));

        String tellerLabel = teller.getName() == null ? "Bank Teller" : teller.getName().getString();
        return new BankTellerOpenPayload(
                teller.getUUID(),
                tellerLabel,
                boundBankIdRaw,
                boundBankName == null ? "" : boundBankName,
                motto == null ? "" : motto,
                issueFee,
                replacementFee,
                openAccountFree,
                safeBoxPolicies,
                summaries,
                safeBoxState
        );
    }

    public static ActionResult executeAction(MinecraftServer server,
                                             CentralBank centralBank,
                                             ServerPlayer player,
                                             UUID tellerId,
                                             String actionRaw,
                                             String accountIdRaw,
                                             String amountRaw,
                                             String recipientRaw,
                                             boolean confirmed,
                                             String paymentModeRaw) {
        return executeAction(server, centralBank, player, tellerId, actionRaw, accountIdRaw, amountRaw, recipientRaw, confirmed, paymentModeRaw, null);
    }

    static ActionResult executeAction(MinecraftServer server,
                                      CentralBank centralBank,
                                      ServerPlayer player,
                                      UUID tellerId,
                                      String actionRaw,
                                      String accountIdRaw,
                                      String amountRaw,
                                      String recipientRaw,
                                      boolean confirmed,
                                      String paymentModeRaw,
                                      ExternalFeePayment externalFeePayment) {
        if (server == null || centralBank == null || player == null || tellerId == null) {
            return ActionResult.fail("Bank teller service is unavailable.");
        }

        BankTellerEntity teller = findTeller(server, tellerId);
        if (teller == null || !teller.isAlive()) {
            return ActionResult.fail("That bank teller is no longer available.");
        }
        if (player.level() != teller.level() || player.distanceToSqr(teller) > MAX_INTERACT_DISTANCE_SQ) {
            return ActionResult.fail("You are too far away from the bank teller.");
        }
        if (!teller.refreshCustomerUse(player)) {
            return ActionResult.fail("This bank teller is currently assisting another customer.");
        }

        String action = actionRaw == null ? "" : actionRaw.trim().toUpperCase(Locale.ROOT);
        PaymentMode paymentMode = parsePaymentMode(paymentModeRaw);
        return switch (action) {
            case "ISSUE_CHEQUE" -> handleIssueCheque(server, centralBank, player, accountIdRaw, amountRaw, recipientRaw);
            case "ISSUE_NOTE" -> handleIssueNote(centralBank, player, accountIdRaw, amountRaw);
            case "WITHDRAW_CASH" -> handleWithdrawCash(centralBank, player, accountIdRaw, amountRaw);
            case "DEPOSIT_CASH" -> handleDepositCash(centralBank, player, accountIdRaw, amountRaw);
            case "DEPOSIT_ALL_CASH" -> handleDepositAllCash(centralBank, player, accountIdRaw);
            case "CHEQUE_TO_ACCOUNT" -> handleChequeToAccount(centralBank, player, accountIdRaw);
            case "CHEQUE_TO_CASH" -> handleChequeToCash(centralBank, player, accountIdRaw);
            case "NOTE_TO_ACCOUNT" -> handleNoteToAccount(centralBank, player, accountIdRaw);
            case "NOTE_TO_CASH" -> handleNoteToCash(centralBank, player, accountIdRaw);
            case "ISSUE_CARD" -> handleIssueCard(server, centralBank, player, teller, accountIdRaw, paymentMode, externalFeePayment);
            case "REPLACE_CARD" -> handleReplaceCard(server, centralBank, player, teller, accountIdRaw, confirmed, paymentMode, externalFeePayment);
            case "OPEN_ACCOUNT" -> handleOpenAccountAtTeller(server, centralBank, player, teller, accountIdRaw, amountRaw, recipientRaw, paymentMode, externalFeePayment);
            case "REQUEST_SAFE_BOX" -> handleRequestSafeBox(server, centralBank, player, teller, accountIdRaw, amountRaw);
            case "REQUEST_OPEN_SAFE_BOX" -> handleRequestOpenSafeBox(
                    new OpenSafeBoxRequest(server, centralBank, player, teller, accountIdRaw));
            default -> ActionResult.fail("Unknown teller action.");
        };
    }

    public static ActionResult completeManualPaymentAction(MinecraftServer server,
                                                           CentralBank centralBank,
                                                           ServerPlayer player,
                                                           UUID tellerId,
                                                           String actionRaw,
                                                           String accountIdRaw,
                                                           String amountRaw,
                                                           String recipientRaw,
                                                           boolean confirmed,
                                                           ExternalFeePayment externalFeePayment) {
        return executeAction(
                server,
                centralBank,
                player,
                tellerId,
                actionRaw,
                accountIdRaw,
                amountRaw,
                recipientRaw,
                confirmed,
                PaymentMode.CASH_OR_CARD.token,
                externalFeePayment
        );
    }

    private static ActionResult handleIssueCheque(MinecraftServer server,
                                                  CentralBank centralBank,
                                                  ServerPlayer player,
                                                  String accountIdRaw,
                                                  String amountRaw,
                                                  String recipientRaw) {
        AccountHolder source = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (source == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        BigDecimal amount = parsePositiveWholeAmount(amountRaw);
        if (amount == null) {
            return ActionResult.fail("Cheque amount must be a positive whole number.");
        }

        RecipientProfile recipient = resolveRecipient(server, recipientRaw);
        if (recipient == null) {
            return ActionResult.fail("Recipient not found. Use a valid player name.");
        }
        if (player.getUUID().equals(recipient.id())) {
            return ActionResult.fail("You cannot issue a cheque to yourself.");
        }

        long dollars;
        try {
            dollars = amount.longValueExact();
        } catch (ArithmeticException ex) {
            return ActionResult.fail("Amount is too large.");
        }

        ApiItemResult result = UltimateBankingApiProvider.get().issueCheque(
                source.getAccountUUID(),
                recipient.id(),
                dollars,
                player.getUUID(),
                player.getName().getString(),
                recipient.name()
        );
        if (!result.success() || result.itemStack().isEmpty()) {
            return ActionResult.fail("Could not issue cheque: " + result.reason());
        }

        giveItem(player, result.itemStack());
        return ActionResult.ok("Cheque issued for $" + amount.toPlainString()
                + " to " + recipient.name() + ". ID: " + result.referenceId());
    }

    private static ActionResult handleIssueNote(CentralBank centralBank,
                                                ServerPlayer player,
                                                String accountIdRaw,
                                                String amountRaw) {
        AccountHolder source = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (source == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        BigDecimal amount = parsePositiveWholeAmount(amountRaw);
        if (amount == null) {
            return ActionResult.fail("Bank note amount must be a positive whole number.");
        }

        long dollars;
        try {
            dollars = amount.longValueExact();
        } catch (ArithmeticException ex) {
            return ActionResult.fail("Amount is too large.");
        }

        ApiItemResult result = UltimateBankingApiProvider.get().issueBankNote(
                source.getAccountUUID(),
                dollars,
                player.getUUID(),
                player.getName().getString()
        );
        if (!result.success() || result.itemStack().isEmpty()) {
            return ActionResult.fail("Could not issue bank note: " + result.reason());
        }

        giveItem(player, result.itemStack());
        return ActionResult.ok("Bank note issued for $" + amount.toPlainString() + ". Serial: " + result.referenceId());
    }

    private static ActionResult handleWithdrawCash(CentralBank centralBank,
                                                   ServerPlayer player,
                                                   String accountIdRaw,
                                                   String amountRaw) {
        AccountHolder source = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (source == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        BigDecimal amount = parsePositiveCurrencyAmount(amountRaw);
        if (amount == null) {
            return ActionResult.fail("Cash amount must be a positive value with up to 2 decimals.");
        }

        int cents = toCents(amount);
        if (cents <= 0) {
            return ActionResult.fail("Cash amount must be at least $0.01.");
        }

        BigDecimal tellerLimit = resolveTellerWithdrawalLimit(centralBank, source);
        if (amount.compareTo(tellerLimit) > 0) {
            return ActionResult.fail("Amount exceeds this bank teller cash limit of $" + tellerLimit.toPlainString() + ".");
        }

        int[] plan = DollarBills.buildCashWithdrawPlan(cents);
        if (plan == null) {
            return ActionResult.fail("Cannot dispense this amount with available cash denominations.");
        }
        WalletBankingCashService.CashStorage cashStorage = WalletBankingCashService.resolve(player);
        if (!cashStorage.canAdd(plan)) {
            return ActionResult.fail("The held wallet cannot hold this withdrawal.");
        }
        if (!source.RemoveBalance(amount)) {
            return ActionResult.fail("Insufficient funds.");
        }

        if (!cashStorage.add(player, plan)) {
            source.AddBalance(amount);
            return ActionResult.fail("Could not store the withdrawal in " + cashStorage.label() + ".");
        }
        source.addTransaction(new UserTransaction(
                source.getAccountUUID(),
                BANK_TELLER_TERMINAL_ID,
                amount,
                LocalDateTime.now(),
                "TELLER_CASH_WITHDRAW"
        ));
        return ActionResult.ok("Withdrew $" + amount.toPlainString() + " to "
                + cashStorage.label() + ": " + DollarBills.formatCashPlan(plan));
    }

    private static ActionResult handleDepositCash(CentralBank centralBank,
                                                  ServerPlayer player,
                                                  String accountIdRaw,
                                                  String amountRaw) {
        AccountHolder destination = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (destination == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        BigDecimal amount = parsePositiveCurrencyAmount(amountRaw);
        if (amount == null) {
            return ActionResult.fail("Deposit amount must be a positive value with up to 2 decimals.");
        }

        int cents = toCents(amount);
        if (cents <= 0) {
            return ActionResult.fail("Deposit amount must be at least $0.01.");
        }

        WalletBankingCashService.CashStorage cashStorage = WalletBankingCashService.resolve(player);
        int[] available = cashStorage.availableCounts(player);
        int[] plan = DollarBills.findCashDepositPlan(cents, available);
        if (plan == null) {
            return ActionResult.fail("Your " + cashStorage.label()
                    + " cannot form an exact cash combination for $" + amount.toPlainString() + ".");
        }

        if (!cashStorage.remove(player, plan)) {
            return ActionResult.fail("Cash changed before the deposit could finish.");
        }
        if (!destination.AddBalance(amount)) {
            cashStorage.add(player, plan);
            return ActionResult.fail("Could not deposit into the selected account.");
        }

        destination.addTransaction(new UserTransaction(
                BANK_TELLER_TERMINAL_ID,
                destination.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "TELLER_CASH_DEPOSIT"
        ));
        return ActionResult.ok("Deposited $" + amount.toPlainString() + " from "
                + cashStorage.label() + ": " + DollarBills.formatCashPlan(plan));
    }

    private static ActionResult handleDepositAllCash(CentralBank centralBank,
                                                     ServerPlayer player,
                                                     String accountIdRaw) {
        AccountHolder destination = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (destination == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        WalletBankingCashService.CashStorage cashStorage = WalletBankingCashService.resolve(player);
        int[] available = cashStorage.availableCounts(player);
        long totalCents = DollarBills.totalCashValueCentsLong(available);
        if (totalCents <= 0L) {
            return ActionResult.fail("There is no " + cashStorage.label() + " to deposit.");
        }

        BigDecimal amount = BigDecimal.valueOf(totalCents, 2).setScale(2, RoundingMode.UNNECESSARY);
        int[] fullPlan = available.clone();

        if (!cashStorage.remove(player, fullPlan)) {
            return ActionResult.fail("Cash changed before the deposit could finish.");
        }
        if (!destination.AddBalance(amount)) {
            cashStorage.add(player, fullPlan);
            return ActionResult.fail("Could not deposit into the selected account.");
        }

        destination.addTransaction(new UserTransaction(
                BANK_TELLER_TERMINAL_ID,
                destination.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "TELLER_CASH_DEPOSIT_ALL"
        ));
        return ActionResult.ok("Deposited all " + cashStorage.label() + " ($"
                + amount.toPlainString() + "): " + DollarBills.formatCashPlan(fullPlan));
    }

    private static ActionResult handleChequeToAccount(CentralBank centralBank,
                                                      ServerPlayer player,
                                                      String accountIdRaw) {
        AccountHolder destination = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (destination == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        HeldCheque heldCheque = findHeldCheque(player);
        if (heldCheque == null) {
            return ActionResult.fail("Hold a cheque in your hand or inventory.");
        }

        ChequeData cheque = heldCheque.cheque();
        if (!player.getUUID().equals(cheque.recipientId())) {
            return ActionResult.fail("This cheque is not payable to you.");
        }
        if (!centralBank.tryRedeemChequeId(cheque.chequeId())) {
            return ActionResult.fail("This cheque has already been redeemed.");
        }

        if (!destination.AddBalance(cheque.amount())) {
            centralBank.rollbackChequeRedemption(cheque.chequeId());
            return ActionResult.fail("Could not deposit into the selected account.");
        }

        heldCheque.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        destination.addTransaction(new UserTransaction(
                cheque.writerId() == null
                        ? BANK_TELLER_TERMINAL_ID
                        : cheque.writerId(),
                destination.getAccountUUID(),
                cheque.amount(),
                LocalDateTime.now(),
                "CHEQUE_TELLER_DEPOSIT:" + cheque.chequeId()
        ));
        return ActionResult.ok("Cheque deposited into account " + shortId(destination.getAccountUUID()) + ".");
    }

    private static ActionResult handleChequeToCash(CentralBank centralBank,
                                                   ServerPlayer player,
                                                   String remainderAccountIdRaw) {
        HeldCheque heldCheque = findHeldCheque(player);
        if (heldCheque == null) {
            return ActionResult.fail("Hold a cheque in your hand or inventory.");
        }

        ChequeData cheque = heldCheque.cheque();
        if (!player.getUUID().equals(cheque.recipientId())) {
            return ActionResult.fail("This cheque is not payable to you.");
        }
        if (!centralBank.tryRedeemChequeId(cheque.chequeId())) {
            return ActionResult.fail("This cheque has already been redeemed.");
        }

        BigDecimal amount = cheque.amount();
        int cents = toCents(amount);
        if (cents <= 0) {
            return ActionResult.fail("Cheque amount is invalid.");
        }

        int[] plan = DollarBills.buildCashWithdrawPlan(cents);
        if (plan == null) {
            centralBank.rollbackChequeRedemption(cheque.chequeId());
            return ActionResult.fail("Unable to prepare cash payout for this amount.");
        }

        heldCheque.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        BankManager.markDirty();
        DollarBills.giveCash(player, plan);
        return ActionResult.ok("Cheque cashed out as cash: " + DollarBills.formatCashPlan(plan));
    }

    private static ActionResult handleNoteToAccount(CentralBank centralBank,
                                                    ServerPlayer player,
                                                    String accountIdRaw) {
        AccountHolder destination = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (destination == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        HeldNote heldNote = findHeldNote(player);
        if (heldNote == null) {
            return ActionResult.fail("Hold a bank note in your hand or inventory.");
        }

        NoteData note = heldNote.note();
        if (!centralBank.tryRedeemNoteSerial(note.serial())) {
            return ActionResult.fail("This bank note has already been redeemed.");
        }

        if (!destination.AddBalance(note.amount())) {
            centralBank.rollbackNoteSerialRedemption(note.serial());
            return ActionResult.fail("Could not deposit into the selected account.");
        }

        heldNote.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        destination.addTransaction(new UserTransaction(
                BANK_TELLER_TERMINAL_ID,
                destination.getAccountUUID(),
                note.amount(),
                LocalDateTime.now(),
                "NOTE_TELLER_DEPOSIT:" + note.serial()
        ));
        return ActionResult.ok("Bank note deposited into account " + shortId(destination.getAccountUUID()) + ".");
    }

    private static ActionResult handleNoteToCash(CentralBank centralBank,
                                                 ServerPlayer player,
                                                 String remainderAccountIdRaw) {
        HeldNote heldNote = findHeldNote(player);
        if (heldNote == null) {
            return ActionResult.fail("Hold a bank note in your hand or inventory.");
        }

        NoteData note = heldNote.note();
        if (!centralBank.tryRedeemNoteSerial(note.serial())) {
            return ActionResult.fail("This bank note has already been redeemed.");
        }

        BigDecimal amount = note.amount();
        int cents = toCents(amount);
        if (cents <= 0) {
            return ActionResult.fail("Bank note amount is invalid.");
        }

        int[] plan = DollarBills.buildCashWithdrawPlan(cents);
        if (plan == null) {
            centralBank.rollbackNoteSerialRedemption(note.serial());
            return ActionResult.fail("Unable to prepare cash payout for this amount.");
        }

        heldNote.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        BankManager.markDirty();
        DollarBills.giveCash(player, plan);
        return ActionResult.ok("Bank note cashed out as cash: " + DollarBills.formatCashPlan(plan));
    }

    private static ActionResult handleOpenAccountAtTeller(MinecraftServer server,
                                                          CentralBank centralBank,
                                                          ServerPlayer player,
                                                          BankTellerEntity teller,
                                                          String fundingAccountIdRaw,
                                                          String accountTypeRaw,
                                                          String certificateTierRaw,
                                                          PaymentMode paymentMode,
                                                          ExternalFeePayment externalFeePayment) {
        if (centralBank == null || player == null || teller == null) {
            return ActionResult.fail("Bank data is unavailable.");
        }

        Bank targetBank = resolveOpenAccountTargetBank(centralBank, teller);
        if (targetBank == null) {
            return ActionResult.fail("This teller is linked to an unavailable bank.");
        }
        boolean centralTargetBank = isCentralBankTarget(centralBank, targetBank);

        String status = readBankStatus(centralBank, targetBank.getBankId());
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status) || "RESTRICTED".equals(status)) {
            return ActionResult.fail("This bank is currently " + status.toLowerCase(Locale.ROOT) + " and cannot open new accounts.");
        }

        AccountTypes accountType = parseAccountType(accountTypeRaw);
        if (accountType == null) {
            return ActionResult.fail("Unknown account type. Use checking, saving, moneymarket, or certificate.");
        }

        boolean firstAtBank = targetBank.getBankAccounts().values().stream()
                .noneMatch(acc -> acc != null && player.getUUID().equals(acc.getPlayerUUID()));
        BigDecimal fee = BigDecimal.ZERO;
        if (!centralTargetBank) {
            fee = TELLER_ACCOUNT_OPEN_BASE_FEE;
            if (firstAtBank) {
                fee = fee.add(TELLER_ACCOUNT_OPEN_FIRST_ACCOUNT_EXTRA_FEE);
            }
        }

        AccountHolder fundingAccount = null;
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            fundingAccount = resolveOwnedAccount(centralBank, player, fundingAccountIdRaw);
            if (fundingAccount == null) {
                return ActionResult.fail("Select one of your existing accounts to pay the account opening fee.");
            }
            if (paymentMode == PaymentMode.CASH_OR_CARD) {
                if (externalFeePayment == null) {
                    return BankTellerPaymentInteractionManager.beginSession(
                            player,
                            teller,
                            "OPEN_ACCOUNT",
                            fundingAccountIdRaw,
                            accountTypeRaw,
                            certificateTierRaw,
                            false,
                            fee,
                            "Open account fee at " + targetBank.getBankName()
                    );
                }
                if (externalFeePayment.amountPaid() == null
                        || externalFeePayment.amountPaid().compareTo(fee) < 0) {
                    return ActionResult.fail("External payment was incomplete for the opening fee.");
                }
            } else if (!fundingAccount.RemoveBalance(fee)) {
                return ActionResult.fail("Insufficient funds for opening fee ($" + fee.toPlainString() + ").");
            }
        }

        long gameTime = currentOverworldGameTime(server);
        AccountHolder created = new AccountHolder(
                player.getUUID(),
                BigDecimal.ZERO,
                accountType,
                "",
                targetBank.getBankId(),
                null
        );

        if (accountType == AccountTypes.CertificateAccount) {
            String tier = normalizeCertificateTier(certificateTierRaw);
            if (tier.isBlank()) {
                if (fee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT && fundingAccount != null) {
                    fundingAccount.AddBalance(fee);
                }
                return ActionResult.fail("Certificate account requires a tier: short, medium, or long.");
            }
            long maturityTicks = switch (tier) {
                case "short" -> Config.CD_SHORT_TERM_TICKS.get();
                case "medium" -> Config.CD_MEDIUM_TERM_TICKS.get();
                case "long" -> Config.CD_LONG_TERM_TICKS.get();
                default -> -1L;
            };
            if (maturityTicks <= 0L) {
                if (fee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT && fundingAccount != null) {
                    fundingAccount.AddBalance(fee);
                }
                return ActionResult.fail("Invalid certificate tier configuration.");
            }
            double cdRate = switch (tier) {
                case "short" -> Config.CD_SHORT_RATE.get();
                case "medium" -> Config.CD_MEDIUM_RATE.get();
                case "long" -> Config.CD_LONG_RATE.get();
                default -> 0.0D;
            };
            created.configureCertificate(tier, gameTime + maturityTicks, cdRate);
        }

        if (!targetBank.AddAccount(created)) {
            if (fee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT && fundingAccount != null) {
                fundingAccount.AddBalance(fee);
            }
            return ActionResult.fail("You already have this account type at " + targetBank.getBankName() + ".");
        }

        if (findPrimaryAccount(centralBank, player.getUUID()) == null) {
            created.setPrimaryAccount(true);
        }

        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            if (paymentMode == PaymentMode.ACCOUNT && fundingAccount != null) {
                fundingAccount.addTransaction(new UserTransaction(
                        fundingAccount.getAccountUUID(),
                        BANK_TELLER_TERMINAL_ID,
                        fee,
                        LocalDateTime.now(),
                        "TELLER_ACCOUNT_OPEN_FEE:" + targetBank.getBankId()
                ));
            }
            targetBank.setReserve(targetBank.getDeclaredReserve().add(fee));
        }

        String paymentLine = fee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.CASH_OR_CARD
                ? " | Paid via " + safePaymentLabel(externalFeePayment)
                : "";

        String message = "Opened " + created.getAccountType().label + " at " + targetBank.getBankName()
                + ". Account ID: " + created.getAccountUUID()
                + ". Opening fee: $" + fee.toPlainString()
                + paymentLine;
        if (firstAtBank && fee.compareTo(BigDecimal.ZERO) > 0) {
            message += " (includes first-account surcharge)";
        } else if (centralTargetBank) {
            message += " (Central Bank teller account opening is free).";
        }
        if (accountType == AccountTypes.CertificateAccount) {
            message += " | Tier: " + created.getCertificateTier()
                    + " | Maturity Tick: " + created.getCertificateMaturityGameTime()
                    + " | APR: " + created.getCertificateRate() + "%";
        }
        return ActionResult.ok(message);
    }

    private static ActionResult handleIssueCard(MinecraftServer server,
                                                CentralBank centralBank,
                                                ServerPlayer player,
                                                BankTellerEntity teller,
                                                String accountIdRaw,
                                                PaymentMode paymentMode,
                                                ExternalFeePayment externalFeePayment) {
        AccountHolder account = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (account == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        UUID tellerBankId = teller.getBoundBankId();
        UUID centralBankId = centralBank.getBankId();
        UUID accountBankId = account.getBankId() == null ? centralBankId : account.getBankId();
        boolean centralAccount = centralBankId != null && centralBankId.equals(accountBankId);
        if (tellerBankId == null) {
            if (!centralAccount) {
                return ActionResult.fail("This teller is unbound and can only issue cards for Central Bank accounts.");
            }
        } else if (!centralAccount && !tellerBankId.equals(accountBankId)) {
            return ActionResult.fail("This teller can only issue cards for accounts at " + resolveBankName(centralBank, tellerBankId) + ".");
        }
        if (CreditCardService.hasActiveCardForAccount(centralBank, account.getAccountUUID())) {
            return ActionResult.fail("This account already has an active card. Use Replace Card instead.");
        }
        boolean privateEligible = tellerBankId != null
                && tellerBankId.equals(accountBankId)
                && CreditCardService.isPrivateBankCardAccountEligible(centralBank, account, player.getUUID());

        UUID feeBankId = centralAccount ? centralBankId : accountBankId;
        BigDecimal issueFee = CreditCardService.getIssueFee(centralBank, feeBankId);
        if (issueFee.compareTo(BigDecimal.ZERO) > 0) {
            if (paymentMode == PaymentMode.CASH_OR_CARD) {
                if (externalFeePayment == null) {
                    return BankTellerPaymentInteractionManager.beginSession(
                            player,
                            teller,
                            "ISSUE_CARD",
                            accountIdRaw,
                            "",
                            "",
                            false,
                            issueFee,
                            "Credit card issue fee"
                    );
                }
                if (externalFeePayment.amountPaid() == null
                        || externalFeePayment.amountPaid().compareTo(issueFee) < 0) {
                    return ActionResult.fail("External payment was incomplete for card issue.");
                }
            } else if (!account.RemoveBalance(issueFee)) {
                return ActionResult.fail("Insufficient funds for issue fee ($" + issueFee.toPlainString() + ").");
            }
        }

        CreditCardService.CardIssueResult issueResult = CreditCardService.issueCard(
                centralBank,
                account,
                player.getName().getString(),
                false,
                privateEligible
        );
        if (!issueResult.success() || issueResult.cardStack().isEmpty()) {
            if (issueFee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT) {
                account.AddBalance(issueFee);
            }
            return ActionResult.fail("Could not issue card: " + issueResult.message());
        }

        giveItem(player, issueResult.cardStack());
        if (issueFee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT) {
            account.addTransaction(new UserTransaction(
                    account.getAccountUUID(),
                    BANK_TELLER_TERMINAL_ID,
                    issueFee,
                    LocalDateTime.now(),
                    "CARD_ISSUE_FEE"
            ));
        }
        return ActionResult.ok(
                (privateEligible ? "Private bank card issued. Number: " : "Credit card issued. Number: ")
                        + CreditCardService.maskCardNumber(issueResult.cardNumber())
                        + " | CVC: " + issueResult.cvc()
                        + " | Expires: " + CreditCardService.formatExpiryMonthYear(issueResult.expiryEpochMillis())
                        + (paymentMode == PaymentMode.CASH_OR_CARD
                        ? " | Fee paid via " + safePaymentLabel(externalFeePayment)
                        : "")
        );
    }

    private static ActionResult handleReplaceCard(MinecraftServer server,
                                                  CentralBank centralBank,
                                                  ServerPlayer player,
                                                  BankTellerEntity teller,
                                                  String accountIdRaw,
                                                  boolean confirmed,
                                                  PaymentMode paymentMode,
                                                  ExternalFeePayment externalFeePayment) {
        if (!confirmed) {
            return ActionResult.fail("Replacement cancelled. Existing card remains active.");
        }

        AccountHolder account = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (account == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }

        UUID tellerBankId = teller.getBoundBankId();
        UUID centralBankId = centralBank.getBankId();
        UUID accountBankId = account.getBankId() == null ? centralBankId : account.getBankId();
        boolean centralAccount = centralBankId != null && centralBankId.equals(accountBankId);
        if (tellerBankId == null) {
            if (!centralAccount) {
                return ActionResult.fail("This teller is unbound and can only replace cards for Central Bank accounts.");
            }
        } else if (!centralAccount && !tellerBankId.equals(accountBankId)) {
            return ActionResult.fail("This teller can only replace cards for accounts at " + resolveBankName(centralBank, tellerBankId) + ".");
        }
        if (!CreditCardService.hasActiveCardForAccount(centralBank, account.getAccountUUID())) {
            return ActionResult.fail("No active card found for this account. Issue a card first.");
        }
        boolean privateEligible = tellerBankId != null
                && tellerBankId.equals(accountBankId)
                && CreditCardService.isPrivateBankCardAccountEligible(centralBank, account, player.getUUID());

        UUID feeBankId = centralAccount ? centralBankId : accountBankId;
        BigDecimal replacementFee = CreditCardService.getReplacementFee(centralBank, feeBankId);
        if (replacementFee.compareTo(BigDecimal.ZERO) > 0) {
            if (paymentMode == PaymentMode.CASH_OR_CARD) {
                if (externalFeePayment == null) {
                    return BankTellerPaymentInteractionManager.beginSession(
                            player,
                            teller,
                            "REPLACE_CARD",
                            accountIdRaw,
                            "",
                            "",
                            true,
                            replacementFee,
                            "Credit card replacement fee"
                    );
                }
                if (externalFeePayment.amountPaid() == null
                        || externalFeePayment.amountPaid().compareTo(replacementFee) < 0) {
                    return ActionResult.fail("External payment was incomplete for card replacement.");
                }
            } else if (!account.RemoveBalance(replacementFee)) {
                return ActionResult.fail("Insufficient funds for replacement fee ($" + replacementFee.toPlainString() + ").");
            }
        }

        CreditCardService.CardIssueResult issueResult = CreditCardService.issueCard(
                centralBank,
                account,
                player.getName().getString(),
                true,
                privateEligible
        );
        if (!issueResult.success() || issueResult.cardStack().isEmpty()) {
            if (replacementFee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT) {
                account.AddBalance(replacementFee);
            }
            return ActionResult.fail("Could not replace card: " + issueResult.message());
        }

        giveItem(player, issueResult.cardStack());
        if (replacementFee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT) {
            account.addTransaction(new UserTransaction(
                    account.getAccountUUID(),
                    BANK_TELLER_TERMINAL_ID,
                    replacementFee,
                    LocalDateTime.now(),
                    "CARD_REPLACEMENT_FEE"
            ));
        }
        return ActionResult.ok(
                (privateEligible ? "Private bank card replaced. Old card has been blocked. New number: "
                        : "Card replaced. Old card has been blocked. New number: ")
                        + CreditCardService.maskCardNumber(issueResult.cardNumber())
                        + " | CVC: " + issueResult.cvc()
                        + " | Expires: " + CreditCardService.formatExpiryMonthYear(issueResult.expiryEpochMillis())
                        + (paymentMode == PaymentMode.CASH_OR_CARD
                        ? " | Fee paid via " + safePaymentLabel(externalFeePayment)
                        : "")
        );
    }

    private static ActionResult handleRequestSafeBox(MinecraftServer server,
                                                     CentralBank centralBank,
                                                     ServerPlayer player,
                                                     BankTellerEntity teller,
                                                     String accountIdRaw,
                                                     String sizeRaw) {
        AccountHolder account = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (account == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }
        UUID tellerBankId = teller.getBoundBankId();
        if (tellerBankId == null) {
            return ActionResult.fail("This teller is not linked to a bank with safe-deposit boxes.");
        }
        if (!tellerBankId.equals(account.getBankId())) {
            return ActionResult.fail("This teller can only request safety boxes for accounts at "
                    + resolveBankName(centralBank, tellerBankId) + ".");
        }
        SafetyDepositBoxRowBlockEntity.ModuleType requestedType = SafetyDepositBoxService.parseAssignableType(sizeRaw);
        if (requestedType == null) {
            return ActionResult.fail("Choose a safety deposit box size first.");
        }
        SafetyDepositBoxService.ActionResult result =
                SafetyDepositBoxService.assignFirstFreeBox(server, centralBank, tellerBankId, account.getAccountUUID(), requestedType);
        if (result.success()) {
            SafeAccessLogService.record(centralBank, tellerBankId, player,
                    SafeAccessLogService.CATEGORY_ASSIGNMENT, SafeAccessLogService.OUTCOME_SUCCESS,
                    "BOX_RENTED", account.getAccountUUID().toString(), result.message(),
                    teller.level().dimension().location().toString(), teller.blockPosition());
        }
        return result.success() ? ActionResult.ok(result.message()) : ActionResult.fail(result.message());
    }

    private static ActionResult handleRequestOpenSafeBox(OpenSafeBoxRequest request) {
        MinecraftServer server = request.server();
        CentralBank centralBank = request.centralBank();
        ServerPlayer player = request.player();
        BankTellerEntity teller = request.teller();
        String accountIdRaw = request.accountIdRaw();
        AccountHolder account = resolveOwnedAccount(centralBank, player, accountIdRaw);
        if (account == null) {
            return ActionResult.fail("Select one of your accounts first.");
        }
        UUID tellerBankId = teller.getBoundBankId();
        if (tellerBankId == null) {
            return ActionResult.fail("This teller is not linked to a bank with safe-deposit boxes.");
        }
        if (!tellerBankId.equals(account.getBankId())) {
            return ActionResult.fail("This teller can only open safety boxes for accounts at "
                    + resolveBankName(centralBank, tellerBankId) + ".");
        }
        BankTellerSafeBoxState state = buildSafeBoxState(
                new SafeBoxStateRequest(server, centralBank, teller, List.of(account)));
        BankTellerSafeBoxState.OpenRequestResult result =
                state.validateOpenRequest(teller.getUUID(), account.getAccountUUID());
        if (result.success()) {
            SafeBoxViewingCoordinator.StartResult started = SafeBoxViewingCoordinator.start(
                    server, centralBank, player, teller, result.assignment());
            return started.success()
                    ? ActionResult.beginEscort(started.message())
                    : ActionResult.fail(started.message());
        }
        return switch (result.failure()) {
            case INVALID_TELLER -> ActionResult.fail("Bank teller validation failed.");
            case NO_ASSIGNMENT -> ActionResult.fail("Selected account does not have an assigned safety deposit box.");
            case BANK_NOT_READY -> ActionResult.fail("Safety deposit vault is unavailable: " + result.message());
            case ASSIGNMENT_UNAVAILABLE -> ActionResult.fail("Selected safety deposit box is unavailable: " + result.message());
            case QUEUE_BUSY -> ActionResult.fail("Safe-deposit teller is busy. No queue was created.");
            case NONE -> ActionResult.fail("Safe-deposit request could not be started.");
        };
    }

    public static boolean handleSessionControl(MinecraftServer server,
                                               ServerPlayer player,
                                               UUID tellerId,
                                               String actionRaw) {
        String action = actionRaw == null ? "" : actionRaw.trim().toUpperCase(Locale.ROOT);
        if (!"KEEPALIVE".equals(action) && !"CLOSE_SESSION".equals(action)) {
            return false;
        }
        if (server == null || player == null || tellerId == null) {
            return true;
        }
        BankTellerEntity teller = findTeller(server, tellerId);
        if (teller == null || !teller.isAlive()) {
            return true;
        }
        if ("KEEPALIVE".equals(action)) {
            teller.refreshCustomerUse(player);
        } else {
            teller.endCustomerUse(player.getUUID());
        }
        return true;
    }

    private static BankTellerSafeBoxState buildSafeBoxState(SafeBoxStateRequest request) {
        MinecraftServer server = request.server();
        CentralBank centralBank = request.centralBank();
        BankTellerEntity teller = request.teller();
        List<AccountHolder> accounts = request.accounts();
        UUID tellerId = teller == null ? null : teller.getUUID();
        UUID bankId = teller == null ? null : teller.getBoundBankId();
        if (server == null || centralBank == null || teller == null) {
            return BankTellerSafeBoxState.unavailable(tellerId, bankId, "Bank teller service is unavailable.");
        }
        if (bankId == null || centralBank.getBank(bankId) == null) {
            return BankTellerSafeBoxState.unavailable(tellerId, bankId,
                    "This teller is not linked to an available bank.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        BankSafeSetupPayloadBuilder.Result setup = BankSafeSetupPayloadBuilder.build(server, metadata);
        SafeVaultReadinessOperation readinessOperation =
                SafetyDepositBoxService.safeDepositVaultReadinessOperation(server, metadata);
        List<BankTellerSafeBoxState.AccountAssignment> assignmentSummaries = new ArrayList<>();
        if (accounts != null) {
            for (AccountHolder account : accounts) {
                if (account == null || account.getAccountUUID() == null || !bankId.equals(account.getBankId())) {
                    continue;
                }
                SafeBoxAssignment assignment = findSafeBoxAssignment(metadata, account.getAccountUUID());
                if (assignment == null) {
                    continue;
                }
                SafeVaultReadinessResolver.RowReadiness readiness =
                        readinessOperation.resolve(new SafeVaultReadinessResolver.RowLocation(
                                assignment.dimension(),
                                new BlockPos(assignment.x(), assignment.y(), assignment.z())));
                List<String> reasons = new ArrayList<>();
                if (assignment.locked()) {
                    reasons.add("This safety deposit box is locked for overdue rent.");
                }
                if (!readiness.mapped()) {
                    reasons.add("This safety deposit box is not mapped to a configured vault.");
                } else {
                    reasons.addAll(readiness.humanMissingReasons());
                }
                boolean ready = !assignment.locked() && readiness.mapped() && readiness.summary().ready();
                assignmentSummaries.add(new BankTellerSafeBoxState.AccountAssignment(
                        account.getAccountUUID(),
                        assignment.boxNumber(),
                        assignment.dimension(),
                        assignment.x(),
                        assignment.y(),
                        assignment.z(),
                        assignment.doorIndex(),
                        readiness.vault() == null ? "" : readiness.vault().id(),
                        ready,
                        assignment.locked(),
                        reasons
                ));
            }
        }
        return new BankTellerSafeBoxState(
                tellerId,
                bankId,
                setup.objective().ready(),
                setup.objective().missingSteps(),
                assignmentSummaries
        );
    }

    private static SafeBoxAssignment findSafeBoxAssignment(CompoundTag metadata, UUID accountId) {
        if (metadata == null || accountId == null) {
            return null;
        }
        ListTag assignments = metadata.getList(SafetyDepositBoxService.ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < assignments.size(); i++) {
            CompoundTag tag = assignments.getCompound(i);
            if (tag.hasUUID("accountId") && accountId.equals(tag.getUUID("accountId"))) {
                return new SafeBoxAssignment(
                        accountId,
                        tag.getString("boxNumber"),
                        tag.getString("dimension"),
                        tag.getInt("x"),
                        tag.getInt("y"),
                        tag.getInt("z"),
                        tag.getInt("doorIndex"),
                        tag.getBoolean("locked")
                );
            }
        }
        return null;
    }

    private static AccountHolder resolveOwnedAccount(CentralBank centralBank, ServerPlayer player, String accountIdRaw) {
        UUID accountId = parseUuid(accountIdRaw);
        if (accountId == null) {
            return null;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !player.getUUID().equals(account.getPlayerUUID())) {
            return null;
        }
        return account;
    }

    private static HeldCheque findHeldCheque(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        ItemStack main = player.getMainHandItem();
        ChequeData mainCheque = readChequeData(main);
        if (mainCheque != null) {
            return new HeldCheque(main, mainCheque);
        }

        ItemStack off = player.getOffhandItem();
        ChequeData offCheque = readChequeData(off);
        if (offCheque != null) {
            return new HeldCheque(off, offCheque);
        }

        for (ItemStack stack : player.getInventory().items) {
            ChequeData cheque = readChequeData(stack);
            if (cheque != null) {
                return new HeldCheque(stack, cheque);
            }
        }
        return null;
    }

    private static HeldNote findHeldNote(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        ItemStack main = player.getMainHandItem();
        NoteData mainNote = readNoteData(main);
        if (mainNote != null) {
            return new HeldNote(main, mainNote);
        }

        ItemStack off = player.getOffhandItem();
        NoteData offNote = readNoteData(off);
        if (offNote != null) {
            return new HeldNote(off, offNote);
        }

        for (ItemStack stack : player.getInventory().items) {
            NoteData note = readNoteData(stack);
            if (note != null) {
                return new HeldNote(stack, note);
            }
        }
        return null;
    }

    private static ChequeData readChequeData(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItems.CHEQUE.get())) {
            return null;
        }
        CompoundTag tag = readCustomTag(stack);
        if (tag == null
                || !tag.contains("ubs_cheque_id")
                || !tag.contains("ubs_cheque_amount")
                || !tag.contains("ubs_cheque_recipient")) {
            return null;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(tag.getString("ubs_cheque_amount"));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String chequeId = tag.getString("ubs_cheque_id");
        if (chequeId == null || chequeId.isBlank()) {
            return null;
        }
        UUID recipient = tag.getUUID("ubs_cheque_recipient");
        UUID writer = tag.hasUUID("ubs_cheque_writer") ? tag.getUUID("ubs_cheque_writer") : null;
        return new ChequeData(chequeId, amount, recipient, writer);
    }

    private static NoteData readNoteData(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItems.BANK_NOTE.get())) {
            return null;
        }
        CompoundTag tag = readCustomTag(stack);
        if (tag == null || !tag.contains("ubs_note_serial") || !tag.contains("ubs_note_amount")) {
            return null;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(tag.getString("ubs_note_amount"));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String serial = tag.getString("ubs_note_serial");
        if (serial == null || serial.isBlank()) {
            return null;
        }
        return new NoteData(serial, amount);
    }

    private static RecipientProfile resolveRecipient(MinecraftServer server, String rawName) {
        if (server == null || rawName == null || rawName.isBlank()) {
            return null;
        }
        String trimmed = rawName.trim();
        ServerPlayer online = server.getPlayerList().getPlayerByName(trimmed);
        if (online != null) {
            return new RecipientProfile(online.getUUID(), online.getName().getString());
        }
        if (server.getProfileCache() == null) {
            return null;
        }
        Optional<GameProfile> profile = server.getProfileCache().get(trimmed);
        if (profile.isEmpty() || profile.get().getId() == null) {
            return null;
        }
        String resolvedName = profile.get().getName() == null || profile.get().getName().isBlank()
                ? trimmed
                : profile.get().getName();
        return new RecipientProfile(profile.get().getId(), resolvedName);
    }

    private static Bank resolveOpenAccountTargetBank(CentralBank centralBank, BankTellerEntity teller) {
        if (centralBank == null || teller == null) {
            return null;
        }
        UUID tellerBankId = teller.getBoundBankId();
        if (tellerBankId == null) {
            return centralBank.getBank(centralBank.getBankId());
        }
        return centralBank.getBank(tellerBankId);
    }

    private static boolean isCentralBankTarget(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return false;
        }
        UUID centralBankId = centralBank.getBankId();
        UUID bankId = bank.getBankId();
        return centralBankId != null && centralBankId.equals(bankId);
    }

    private static String readBankStatus(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return "UNKNOWN";
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        String status = metadata.getString("status");
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal resolveTellerWithdrawalLimit(CentralBank centralBank, AccountHolder source) {
        if (centralBank == null || source == null) {
            return DEFAULT_TELLER_WITHDRAWAL_LIMIT;
        }
        UUID bankId = source.getBankId() == null ? centralBank.getBankId() : source.getBankId();
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        if (!metadata.contains("limitTeller")) {
            return DEFAULT_TELLER_WITHDRAWAL_LIMIT;
        }
        BigDecimal configured = readBigDecimal(metadata, "limitTeller");
        if (configured.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_TELLER_WITHDRAWAL_LIMIT;
        }
        if (configured.compareTo(MAX_TELLER_WITHDRAWAL_LIMIT) > 0) {
            return MAX_TELLER_WITHDRAWAL_LIMIT;
        }
        return configured.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static long currentOverworldGameTime(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        var overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        return overworld == null ? 0L : overworld.getGameTime();
    }

    private static AccountHolder findPrimaryAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        for (AccountHolder account : centralBank.SearchForAccount(playerId).values()) {
            if (account != null && account.isPrimaryAccount()) {
                return account;
            }
        }
        return null;
    }

    private static AccountTypes parseAccountType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AccountTypes.CheckingAccount;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
        return switch (normalized) {
            case "checking", "checkingaccount", "check" -> AccountTypes.CheckingAccount;
            case "saving", "savings", "savingaccount" -> AccountTypes.SavingAccount;
            case "moneymarket", "moneymarketaccount", "mma" -> AccountTypes.MoneyMarketAccount;
            case "certificate", "certificateaccount", "certificateofdeposit", "cd", "cert" ->
                    AccountTypes.CertificateAccount;
            default -> null;
        };
    }

    private static PaymentMode parsePaymentMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return PaymentMode.ACCOUNT;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (PaymentMode.CASH_OR_CARD.token.equals(normalized)
                || "CASHCARD".equals(normalized)
                || "CASH_OR_CARD".equals(normalized)) {
            return PaymentMode.CASH_OR_CARD;
        }
        return PaymentMode.ACCOUNT;
    }

    private static String safePaymentLabel(ExternalFeePayment payment) {
        if (payment == null || payment.methodLabel() == null || payment.methodLabel().isBlank()) {
            return "cash/card";
        }
        return payment.methodLabel().trim();
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

    private static BigDecimal parsePositiveWholeAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (parsed.stripTrailingZeros().scale() > 0) {
            return null;
        }
        return parsed;
    }

    private static BigDecimal parsePositiveCurrencyAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        try {
            return parsed.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static int toCents(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return -1;
        }
        try {
            BigDecimal scaled = amount.setScale(2, RoundingMode.UNNECESSARY);
            return scaled.movePointRight(2).intValueExact();
        } catch (ArithmeticException ex) {
            return -1;
        }
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

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    private static BankTellerEntity findTeller(MinecraftServer server, UUID tellerId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(tellerId);
            if (entity instanceof BankTellerEntity teller) {
                return teller;
            }
        }
        return null;
    }

    private static String resolveBankName(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return "Unknown Bank";
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null || bank.getBankName() == null || bank.getBankName().isBlank()) {
            return shortId(bankId);
        }
        return bank.getBankName();
    }

    private static CompoundTag readCustomTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag customData = ItemStackDataCompat.getCustomData(stack);
        return customData == null ? null : customData.copy();
    }

    private static String shortId(UUID uuid) {
        if (uuid == null) {
            return "unknown";
        }
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private record SafeBoxStateRequest(MinecraftServer server,
                                       CentralBank centralBank,
                                       BankTellerEntity teller,
                                       List<AccountHolder> accounts) {
    }

    private record OpenSafeBoxRequest(MinecraftServer server,
                                      CentralBank centralBank,
                                      ServerPlayer player,
                                      BankTellerEntity teller,
                                      String accountIdRaw) {
    }
}
