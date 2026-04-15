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
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.BankTellerAccountSummary;
import net.austizz.ultimatebankingsystem.network.BankTellerOpenPayload;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.nbt.CompoundTag;
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
                    List.of()
            );
        }

        UUID boundBankId = teller.getBoundBankId();
        String boundBankIdRaw = boundBankId == null ? "" : boundBankId.toString();
        String boundBankName = "Unbound Teller";
        String motto = "";
        String issueFee = "0";
        String replacementFee = "0";

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

        String tellerLabel = teller.getName() == null ? "Bank Teller" : teller.getName().getString();
        return new BankTellerOpenPayload(
                teller.getUUID(),
                tellerLabel,
                boundBankIdRaw,
                boundBankName == null ? "" : boundBankName,
                motto == null ? "" : motto,
                issueFee,
                replacementFee,
                summaries
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

        String action = actionRaw == null ? "" : actionRaw.trim().toUpperCase(Locale.ROOT);
        PaymentMode paymentMode = parsePaymentMode(paymentModeRaw);
        return switch (action) {
            case "ISSUE_CHEQUE" -> handleIssueCheque(server, centralBank, player, accountIdRaw, amountRaw, recipientRaw);
            case "ISSUE_NOTE" -> handleIssueNote(centralBank, player, accountIdRaw, amountRaw);
            case "WITHDRAW_CASH" -> handleWithdrawCash(centralBank, player, accountIdRaw, amountRaw);
            case "CHEQUE_TO_ACCOUNT" -> handleChequeToAccount(centralBank, player, accountIdRaw);
            case "CHEQUE_TO_CASH" -> handleChequeToCash(centralBank, player, accountIdRaw);
            case "NOTE_TO_ACCOUNT" -> handleNoteToAccount(centralBank, player, accountIdRaw);
            case "NOTE_TO_CASH" -> handleNoteToCash(centralBank, player, accountIdRaw);
            case "ISSUE_CARD" -> handleIssueCard(server, centralBank, player, teller, accountIdRaw, paymentMode, externalFeePayment);
            case "REPLACE_CARD" -> handleReplaceCard(server, centralBank, player, teller, accountIdRaw, confirmed, paymentMode, externalFeePayment);
            case "OPEN_ACCOUNT" -> handleOpenAccountAtTeller(server, centralBank, player, teller, accountIdRaw, amountRaw, recipientRaw, paymentMode, externalFeePayment);
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
        if (!source.RemoveBalance(amount)) {
            return ActionResult.fail("Insufficient funds.");
        }

        DollarBills.giveCash(player, plan);
        source.addTransaction(new UserTransaction(
                source.getAccountUUID(),
                BANK_TELLER_TERMINAL_ID,
                amount,
                LocalDateTime.now(),
                "TELLER_CASH_WITHDRAW"
        ));
        return ActionResult.ok("Withdrew $" + amount.toPlainString() + " as cash: " + DollarBills.formatCashPlan(plan));
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
        if (centralBank.isChequeRedeemed(cheque.chequeId())) {
            return ActionResult.fail("This cheque has already been redeemed.");
        }

        if (!destination.AddBalance(cheque.amount())) {
            return ActionResult.fail("Could not deposit into the selected account.");
        }

        heldCheque.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        centralBank.markChequeRedeemed(cheque.chequeId());
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
        if (centralBank.isChequeRedeemed(cheque.chequeId())) {
            return ActionResult.fail("This cheque has already been redeemed.");
        }

        BigDecimal amount = cheque.amount();
        int cents = toCents(amount);
        if (cents <= 0) {
            return ActionResult.fail("Cheque amount is invalid.");
        }

        int[] plan = DollarBills.buildCashWithdrawPlan(cents);
        if (plan == null) {
            return ActionResult.fail("Unable to prepare cash payout for this amount.");
        }

        heldCheque.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        centralBank.markChequeRedeemed(cheque.chequeId());
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
        if (centralBank.isNoteSerialRedeemed(note.serial())) {
            return ActionResult.fail("This bank note has already been redeemed.");
        }

        if (!destination.AddBalance(note.amount())) {
            return ActionResult.fail("Could not deposit into the selected account.");
        }

        heldNote.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        centralBank.markNoteSerialRedeemed(note.serial());
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
        if (centralBank.isNoteSerialRedeemed(note.serial())) {
            return ActionResult.fail("This bank note has already been redeemed.");
        }

        BigDecimal amount = note.amount();
        int cents = toCents(amount);
        if (cents <= 0) {
            return ActionResult.fail("Bank note amount is invalid.");
        }

        int[] plan = DollarBills.buildCashWithdrawPlan(cents);
        if (plan == null) {
            return ActionResult.fail("Unable to prepare cash payout for this amount.");
        }

        heldNote.stack().shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        centralBank.markNoteSerialRedeemed(note.serial());
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

        String status = readBankStatus(centralBank, targetBank.getBankId());
        if ("SUSPENDED".equals(status) || "REVOKED".equals(status) || "RESTRICTED".equals(status)) {
            return ActionResult.fail("This bank is currently " + status.toLowerCase(Locale.ROOT) + " and cannot open new accounts.");
        }

        AccountTypes accountType = parseAccountType(accountTypeRaw);
        if (accountType == null) {
            return ActionResult.fail("Unknown account type. Use checking, saving, moneymarket, or certificate.");
        }

        AccountHolder fundingAccount = resolveOwnedAccount(centralBank, player, fundingAccountIdRaw);
        if (fundingAccount == null) {
            return ActionResult.fail("Select one of your existing accounts to pay the account opening fee.");
        }

        boolean firstAtBank = targetBank.getBankAccounts().values().stream()
                .noneMatch(acc -> acc != null && player.getUUID().equals(acc.getPlayerUUID()));
        BigDecimal fee = TELLER_ACCOUNT_OPEN_BASE_FEE;
        if (firstAtBank) {
            fee = fee.add(TELLER_ACCOUNT_OPEN_FIRST_ACCOUNT_EXTRA_FEE);
        }

        if (fee.compareTo(BigDecimal.ZERO) > 0) {
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
                if (fee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT) {
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
                if (fee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT) {
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
            if (fee.compareTo(BigDecimal.ZERO) > 0 && paymentMode == PaymentMode.ACCOUNT) {
                fundingAccount.AddBalance(fee);
            }
            return ActionResult.fail("You already have this account type at " + targetBank.getBankName() + ".");
        }

        if (findPrimaryAccount(centralBank, player.getUUID()) == null) {
            created.setPrimaryAccount(true);
        }

        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            if (paymentMode == PaymentMode.ACCOUNT) {
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

        String paymentLine = paymentMode == PaymentMode.CASH_OR_CARD
                ? " | Paid via " + safePaymentLabel(externalFeePayment)
                : "";

        String message = "Opened " + created.getAccountType().label + " at " + targetBank.getBankName()
                + ". Account ID: " + created.getAccountUUID()
                + ". Opening fee: $" + fee.toPlainString()
                + paymentLine;
        if (firstAtBank) {
            message += " (includes first-account surcharge)";
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
                false
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
                "Credit card issued. Number: " + CreditCardService.maskCardNumber(issueResult.cardNumber())
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
                true
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
                "Card replaced. Old card has been blocked. New number: "
                        + CreditCardService.maskCardNumber(issueResult.cardNumber())
                        + " | CVC: " + issueResult.cvc()
                        + " | Expires: " + CreditCardService.formatExpiryMonthYear(issueResult.expiryEpochMillis())
                        + (paymentMode == PaymentMode.CASH_OR_CARD
                        ? " | Fee paid via " + safePaymentLabel(externalFeePayment)
                        : "")
        );
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
        UUID recipient = tag.getUUID("ubs_cheque_recipient");
        UUID writer = tag.hasUUID("ubs_cheque_writer") ? tag.getUUID("ubs_cheque_writer") : null;
        return new ChequeData(tag.getString("ubs_cheque_id"), amount, recipient, writer);
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
        return new NoteData(tag.getString("ubs_note_serial"), amount);
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
}
