package net.austizz.ultimatebankingsystem.api.economy;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.AccountPrincipalType;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class UltimateEconomyApiImpl implements UltimateEconomyApi {
    private static final String API_VERSION = "2.1.1";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,160}");
    private static final UUID EXTERNAL_SYSTEM_ACCOUNT = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:economy-api-system".getBytes(StandardCharsets.UTF_8));
    private static final int MAX_REFERENCE_LENGTH = 160;
    private static final int MAX_OPERATION_HISTORY = 50_000;
    private static final BigDecimal PERSONAL_SINGLE_LIMIT = new BigDecimal("10000.00");
    private static final BigDecimal PERSONAL_ROLLING_LIMIT = new BigDecimal("25000.00");
    private static final BigDecimal OFFICER_SINGLE_LIMIT = new BigDecimal("10000.00");
    private static final BigDecimal LEADER_SINGLE_LIMIT = new BigDecimal("25000.00");
    private static final BigDecimal LEADER_ROLLING_LIMIT = new BigDecimal("100000.00");
    private static final BigDecimal WAR_STAKE_LIMIT = new BigDecimal("25000.00");
    private final Supplier<CentralBank> centralBankSupplier;

    public UltimateEconomyApiImpl() {
        this(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server == null ? null : BankManager.getCentralBank(server);
        });
    }

    UltimateEconomyApiImpl(Supplier<CentralBank> centralBankSupplier) {
        this.centralBankSupplier = java.util.Objects.requireNonNull(centralBankSupplier, "centralBankSupplier");
    }

    @Override
    public String getApiVersion() {
        return API_VERSION;
    }

    @Override
    public ApiEconomySnapshot snapshot(ApiEconomySnapshotRequest request) {
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return new ApiEconomySnapshot(API_VERSION, 0L, Instant.now(), List.of(), List.of(), List.of());
        }

        ApiEconomySnapshotRequest query = request == null
                ? new ApiEconomySnapshotRequest(null, false, false, null, 0)
                : request;
        Map<UUID, AccountHolder> selectedAccounts = selectAccounts(centralBank, query);
        List<ApiAccessibleAccountSnapshot> accountSnapshots = selectedAccounts.values().stream()
                .map(account -> accountSnapshot(
                        centralBank, account, query.viewerPlayerId(), query.includeAllAccounts()))
                .sorted(Comparator.comparing(ApiAccessibleAccountSnapshot::primary).reversed()
                        .thenComparing(ApiAccessibleAccountSnapshot::createdAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(snapshot -> snapshot.accountId().toString()))
                .toList();

        List<ApiEconomyTransactionSnapshot> transactions = query.includeTransactions()
                ? transactionSnapshots(selectedAccounts.values(), query.transactionsSince(), query.transactionLimit())
                : List.of();
        List<ApiEscrowSnapshot> escrows = centralBank.getEconomyEscrows().values().stream()
                .map(tag -> readEscrow(centralBank, tag))
                .filter(escrow -> escrow != null)
                .filter(escrow -> query.includeAllAccounts()
                        || escrow.contributorAccountIds().stream().anyMatch(selectedAccounts::containsKey))
                .sorted(Comparator.comparing(ApiEscrowSnapshot::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new ApiEconomySnapshot(
                API_VERSION,
                centralBank.getEconomyRevision(),
                Instant.now(),
                accountSnapshots,
                transactions,
                escrows
        );
    }

    @Override
    public Optional<ApiEconomyOperationResult> findOperation(String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return Optional.empty();
        }
        CompoundTag stored = centralBank.getEconomyOperations().get(key);
        return Optional.ofNullable(stored == null ? null : readOperationResult(stored));
    }

    @Override
    public synchronized ApiEconomyOperationResult execute(ApiEconomyOperationRequest request) {
        String key = request == null ? "" : normalizeIdempotencyKey(request.idempotencyKey());
        if (request == null) {
            return failure("INVALID_REQUEST", "Operation request is required", key, 0L);
        }
        if (key.isEmpty()) {
            return failure("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency key must be 8-160 safe ASCII characters", "", 0L);
        }
        if (request.type() == null) {
            return failure("INVALID_OPERATION_TYPE", "Operation type is required", key, 0L);
        }

        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return failure("SERVER_UNAVAILABLE", "Bank data is unavailable", key, 0L);
        }

        String fingerprint = fingerprint(request);
        CompoundTag existing = centralBank.getEconomyOperations().get(key);
        if (existing != null) {
            if (!fingerprint.equals(existing.getString("requestFingerprint"))) {
                return failure("IDEMPOTENCY_CONFLICT",
                        "Idempotency key was already used for a different operation",
                        key,
                        centralBank.getEconomyRevision());
            }
            ApiEconomyOperationResult replay = readOperationResult(existing);
            return replay == null
                    ? failure("CORRUPT_OPERATION_RECEIPT", "Stored operation receipt is invalid", key,
                    centralBank.getEconomyRevision())
                    : replay.asDuplicate();
        }

        ApiEconomyOperationResult result;
        try {
            result = switch (request.type()) {
                case TRANSFER -> transfer(centralBank, request, false);
                case TRANSFER_TO_PRIMARY -> transfer(centralBank, request, true);
                case SET_PRIMARY_ACCOUNT -> setPrimaryAccount(centralBank, request);
                case PROVISION_INSTITUTION_ACCOUNT -> provisionInstitution(centralBank, request);
                case SET_ACCESS_ROLE -> setAccessRole(centralBank, request);
                case SET_ACCOUNT_FROZEN -> setAccountFrozen(centralBank, request);
                case ADMIN_DEPOSIT -> adminAdjustment(centralBank, request, true);
                case ADMIN_WITHDRAW -> adminAdjustment(centralBank, request, false);
                case CREATE_ESCROW -> createEscrow(centralBank, request);
                case FUND_ESCROW -> fundEscrow(centralBank, request);
                case RELEASE_ESCROW -> completeEscrow(centralBank, request, false);
                case REFUND_ESCROW -> completeEscrow(centralBank, request, true);
            };
        } catch (RuntimeException exception) {
            UltimateBankingSystem.LOGGER.error("UBS economy operation {} failed", key, exception);
            result = failure("OPERATION_ERROR", "Operation could not be completed", key,
                    centralBank.getEconomyRevision());
        }

        storeOperation(centralBank, request, fingerprint, result);
        return result;
    }

    private ApiEconomyOperationResult transfer(CentralBank centralBank,
                                               ApiEconomyOperationRequest request,
                                               boolean toPrimary) {
        AccountHolder sender = account(centralBank, request.accountId());
        if (sender == null) {
            return failure("SENDER_NOT_FOUND", "Sender account was not found", request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        UUID receiverId = request.counterpartyAccountId();
        if (toPrimary) {
            receiverId = primaryAccountId(centralBank, request.targetPlayerId()).orElse(null);
            if (receiverId == null) {
                return failure("RECEIVER_PRIMARY_NOT_FOUND", "Receiver has no primary personal account",
                        request.idempotencyKey(), centralBank.getEconomyRevision());
            }
        }
        AccountHolder receiver = account(centralBank, receiverId);
        if (receiver == null) {
            return failure("RECEIVER_NOT_FOUND", "Receiver account was not found", request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        if (!canWithdraw(request, sender)) {
            return failure("ACCESS_DENIED", "Actor cannot withdraw from the sender account",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        BigDecimal amount = money(request.amount());
        String policyError = validateTransferPolicy(request, sender, amount);
        if (!policyError.isEmpty()) {
            return failure("POLICY_LIMIT", policyError, request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        MovementResult movement = moveMoney(centralBank, request.idempotencyKey(), List.of(
                new ApiEconomyTransferLeg(sender.getAccountUUID(), receiver.getAccountUUID(), amount,
                        request.reference())), "WEB_TRANSFER");
        if (!movement.success()) {
            return failure(movement.code(), movement.message(), request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        return success("TRANSFERRED", "Transfer completed", request.idempotencyKey(), centralBank,
                movement.transactionIds(), movement.accountIds(), null);
    }

    private ApiEconomyOperationResult setPrimaryAccount(CentralBank centralBank,
                                                        ApiEconomyOperationRequest request) {
        UUID playerId = request.targetPlayerId() == null ? request.actorPlayerId() : request.targetPlayerId();
        if (playerId == null || request.accountId() == null) {
            return failure("INVALID_PRIMARY_REQUEST", "Player and account are required",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        if (!isSystem(request) && !playerId.equals(request.actorPlayerId())) {
            return failure("ACCESS_DENIED", "Players may only change their own primary account",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        AccountHolder selected = account(centralBank, request.accountId());
        if (selected == null || selected.isInstitutional() || !selected.isOwnedByPlayer(playerId)) {
            return failure("ACCOUNT_NOT_PERSONAL", "Primary account must be a player-owned personal account",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        if (!centralBank.setPrimaryAccountForPlayer(playerId, selected.getAccountUUID(), true)) {
            return failure("PRIMARY_UPDATE_FAILED", "Primary account could not be changed",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        return success("PRIMARY_ACCOUNT_SET", "Primary account changed", request.idempotencyKey(), centralBank,
                List.of(), List.of(selected.getAccountUUID()), null);
    }

    private ApiEconomyOperationResult provisionInstitution(CentralBank centralBank,
                                                           ApiEconomyOperationRequest request) {
        if (!isSystem(request)) {
            return failure("ACCESS_DENIED", "Institution provisioning requires official-system authority",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        String institutionId = normalizeInstitutionId(request.institutionId());
        Bank bank = centralBank.getBank(request.bankId());
        if (institutionId.isEmpty() || bank == null) {
            return failure("INVALID_INSTITUTION", "Institution id and an existing bank are required",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        AccountHolder existing = findInstitutionAccount(centralBank, institutionId);
        if (existing != null) {
            if (!existing.getBankId().equals(bank.getBankId())) {
                return failure("INSTITUTION_BANK_CONFLICT", "Institution already has an account at another bank",
                        request.idempotencyKey(), centralBank.getEconomyRevision());
            }
            return success("INSTITUTION_EXISTS", "Institution account already exists",
                    request.idempotencyKey(), centralBank, List.of(), List.of(existing.getAccountUUID()), null);
        }

        UUID accountId = deterministicId("institution-account", institutionId + ":" + bank.getBankId());
        AccountHolder created = AccountHolder.createInstitutional(
                institutionId, BigDecimal.ZERO, AccountTypes.CheckingAccount, bank.getBankId(), accountId);
        created.setBusinessLabel(normalizeLabel(request.metadata().get("label"), institutionId));
        if (request.targetPlayerId() != null) {
            ApiAccountRole initialRole = ApiAccountRole.parse(request.role());
            if (initialRole == ApiAccountRole.NONE || initialRole == ApiAccountRole.OWNER) {
                initialRole = ApiAccountRole.MANAGE;
            }
            created.grantAccessRole(request.targetPlayerId(), initialRole.name());
        }
        if (!bank.AddAccount(created)) {
            return failure("ACCOUNT_CREATE_FAILED", "Institution account could not be created",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        return success("INSTITUTION_PROVISIONED", "Institution account created",
                request.idempotencyKey(), centralBank, List.of(), List.of(accountId), null);
    }

    private ApiEconomyOperationResult setAccessRole(CentralBank centralBank,
                                                    ApiEconomyOperationRequest request) {
        AccountHolder target = account(centralBank, request.accountId());
        if (target == null || request.targetPlayerId() == null) {
            return failure("INVALID_ACCESS_REQUEST", "Account and target player are required",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        if (!isSystem(request) && !target.canManage(request.actorPlayerId())) {
            return failure("ACCESS_DENIED", "Actor cannot manage account access",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        ApiAccountRole role = ApiAccountRole.parse(request.role());
        if (role == ApiAccountRole.OWNER && !target.isOwnedByPlayer(request.targetPlayerId())) {
            return failure("INVALID_ROLE", "OWNER is reserved for the personal account principal",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        if (role == ApiAccountRole.NONE) {
            target.revokeAccessRole(request.targetPlayerId());
        } else {
            target.grantAccessRole(request.targetPlayerId(), role.name());
        }
        return success("ACCESS_UPDATED", "Account access changed", request.idempotencyKey(), centralBank,
                List.of(), List.of(target.getAccountUUID()), null);
    }

    private ApiEconomyOperationResult setAccountFrozen(CentralBank centralBank,
                                                       ApiEconomyOperationRequest request) {
        if (!isSystem(request)) {
            return failure("ACCESS_DENIED", "Freezing an account requires official-system authority",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        AccountHolder target = account(centralBank, request.accountId());
        if (target == null) {
            return failure("ACCOUNT_NOT_FOUND", "Account was not found", request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        boolean frozen = Boolean.parseBoolean(request.metadata().getOrDefault("frozen", "true"));
        if (frozen) {
            target.freeze(normalizeReference(request.reference()));
        } else {
            target.unfreeze();
        }
        return success(frozen ? "ACCOUNT_FROZEN" : "ACCOUNT_UNFROZEN",
                frozen ? "Account frozen" : "Account unfrozen", request.idempotencyKey(), centralBank,
                List.of(), List.of(target.getAccountUUID()), null);
    }

    private ApiEconomyOperationResult adminAdjustment(CentralBank centralBank,
                                                      ApiEconomyOperationRequest request,
                                                      boolean deposit) {
        if (!isSystem(request)) {
            return failure("ACCESS_DENIED", "Admin adjustment requires official-system authority",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        AccountHolder target = account(centralBank, request.accountId());
        BigDecimal amount = money(request.amount());
        String reason = normalizeReference(request.reference());
        if (target == null || amount.signum() <= 0 || reason.isEmpty()) {
            return failure("INVALID_ADJUSTMENT", "Exact account, positive amount, and reason are required",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        if (deposit && !target.canCreditForSystem(amount)) {
            return failure("ACCOUNT_UNAVAILABLE", "Account cannot receive the adjustment",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        if (!deposit && !target.canDebitForSystem(amount)) {
            return failure("INSUFFICIENT_OR_UNAVAILABLE", "Account cannot fund the adjustment",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }

        UUID transactionId = deterministicId("adjustment", request.idempotencyKey());
        if (deposit) {
            target.forceAddBalance(amount);
        } else {
            target.forceRemoveBalance(amount);
        }
        UserTransaction transaction = new UserTransaction(
                deposit ? EXTERNAL_SYSTEM_ACCOUNT : target.getAccountUUID(),
                deposit ? target.getAccountUUID() : EXTERNAL_SYSTEM_ACCOUNT,
                amount,
                LocalDateTime.now(),
                (deposit ? "WEB_ADMIN_DEPOSIT:" : "WEB_ADMIN_WITHDRAW:") + reason,
                transactionId
        );
        target.addTransaction(transaction);
        return success(deposit ? "ADJUSTMENT_DEPOSITED" : "ADJUSTMENT_WITHDRAWN",
                "Admin adjustment completed", request.idempotencyKey(), centralBank,
                List.of(transactionId), List.of(target.getAccountUUID()), null);
    }

    private ApiEconomyOperationResult createEscrow(CentralBank centralBank,
                                                   ApiEconomyOperationRequest request) {
        if (!isSystem(request)) {
            return failure("ACCESS_DENIED", "Escrow creation requires official-system authority",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        String escrowId = normalizeEscrowId(request.escrowId());
        Bank bank = centralBank.getBank(request.bankId());
        if (escrowId.isEmpty() || bank == null) {
            return failure("INVALID_ESCROW", "Escrow id and an existing bank are required",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        CompoundTag existing = centralBank.getEconomyEscrows().get(escrowId);
        if (existing != null) {
            return failure("ESCROW_EXISTS", "Escrow already exists", request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        String principalId = "escrow:" + escrowId;
        UUID holdingId = deterministicId("escrow-account", escrowId + ":" + bank.getBankId());
        AccountHolder holding = AccountHolder.createInstitutional(
                principalId, BigDecimal.ZERO, AccountTypes.CheckingAccount, bank.getBankId(), holdingId);
        holding.setBusinessLabel(normalizeLabel(request.metadata().get("label"), "Escrow " + escrowId));
        if (!bank.AddAccount(holding)) {
            return failure("ESCROW_ACCOUNT_FAILED", "Escrow holding account could not be created",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        CompoundTag escrowTag = new CompoundTag();
        escrowTag.putString("escrowId", escrowId);
        escrowTag.putString("purpose", normalizeLabel(request.metadata().get("purpose"), request.reference()));
        escrowTag.putString("status", "OPEN");
        escrowTag.putUUID("holdingAccountId", holdingId);
        escrowTag.putLong("createdAt", System.currentTimeMillis());
        escrowTag.put("contributions", new ListTag());
        centralBank.getEconomyEscrows().put(escrowId, escrowTag);
        BankManager.markDirty();
        ApiEscrowSnapshot snapshot = readEscrow(centralBank, escrowTag);
        return success("ESCROW_CREATED", "Escrow created", request.idempotencyKey(), centralBank,
                List.of(), List.of(holdingId), snapshot);
    }

    private ApiEconomyOperationResult fundEscrow(CentralBank centralBank,
                                                 ApiEconomyOperationRequest request) {
        if (!isSystem(request)) {
            return failure("ACCESS_DENIED", "Escrow funding requires official-system authority",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        CompoundTag escrowTag = escrowTag(centralBank, request.escrowId());
        if (escrowTag == null || !"OPEN".equals(escrowTag.getString("status"))) {
            return failure("ESCROW_NOT_OPEN", "Escrow is missing or not open", request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        if (request.legs().size() != 2) {
            return failure("MATCHED_STAKES_REQUIRED", "War escrow requires exactly two matched stakes",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        BigDecimal first = money(request.legs().get(0).amount());
        BigDecimal second = money(request.legs().get(1).amount());
        if (first.signum() <= 0 || first.compareTo(second) != 0 || first.compareTo(WAR_STAKE_LIMIT) > 0) {
            return failure("INVALID_MATCHED_STAKES", "Stakes must match and may not exceed $25,000.00 per side",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        UUID holdingId = escrowTag.getUUID("holdingAccountId");
        List<ApiEconomyTransferLeg> legs = request.legs().stream()
                .map(leg -> new ApiEconomyTransferLeg(leg.senderAccountId(), holdingId, money(leg.amount()),
                        normalizeReference(leg.reference())))
                .toList();
        if (legs.get(0).senderAccountId() == null
                || legs.get(0).senderAccountId().equals(legs.get(1).senderAccountId())) {
            return failure("DISTINCT_CONTRIBUTORS_REQUIRED", "Escrow contributors must be distinct accounts",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        MovementResult movement = moveMoney(centralBank, request.idempotencyKey(), legs, "WAR_ESCROW_FUND");
        if (!movement.success()) {
            return failure(movement.code(), movement.message(), request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        ListTag contributions = new ListTag();
        for (ApiEconomyTransferLeg leg : legs) {
            CompoundTag contribution = new CompoundTag();
            contribution.putUUID("accountId", leg.senderAccountId());
            contribution.putString("amount", money(leg.amount()).toPlainString());
            contributions.add(contribution);
        }
        escrowTag.put("contributions", contributions);
        escrowTag.putString("status", "FUNDED");
        BankManager.markDirty();
        return success("ESCROW_FUNDED", "Matched stakes deposited", request.idempotencyKey(), centralBank,
                movement.transactionIds(), movement.accountIds(), readEscrow(centralBank, escrowTag));
    }

    private ApiEconomyOperationResult completeEscrow(CentralBank centralBank,
                                                     ApiEconomyOperationRequest request,
                                                     boolean refund) {
        if (!isSystem(request)) {
            return failure("ACCESS_DENIED", "Escrow completion requires official-system authority",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        CompoundTag escrowTag = escrowTag(centralBank, request.escrowId());
        if (escrowTag == null || !"FUNDED".equals(escrowTag.getString("status"))) {
            return failure("ESCROW_NOT_FUNDED", "Escrow is missing or not funded", request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        UUID holdingId = escrowTag.getUUID("holdingAccountId");
        AccountHolder holding = account(centralBank, holdingId);
        if (holding == null || holding.getBalance().signum() <= 0) {
            return failure("ESCROW_BALANCE_MISSING", "Escrow holding balance is unavailable",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }

        List<ApiEconomyTransferLeg> legs;
        if (refund) {
            legs = refundLegs(escrowTag, holdingId);
        } else if (!request.legs().isEmpty()) {
            legs = request.legs().stream()
                    .map(leg -> new ApiEconomyTransferLeg(holdingId, leg.receiverAccountId(),
                            money(leg.amount()), normalizeReference(leg.reference())))
                    .toList();
        } else {
            legs = List.of(new ApiEconomyTransferLeg(
                    holdingId,
                    request.counterpartyAccountId(),
                    holding.getBalance(),
                    request.reference()));
        }
        BigDecimal payout = legs.stream().map(ApiEconomyTransferLeg::amount)
                .map(this::money).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (legs.isEmpty() || payout.compareTo(holding.getBalance()) != 0) {
            return failure("ESCROW_PAYOUT_MISMATCH", "Escrow payout must use the complete holding balance",
                    request.idempotencyKey(), centralBank.getEconomyRevision());
        }
        MovementResult movement = moveMoney(centralBank, request.idempotencyKey(), legs,
                refund ? "WAR_ESCROW_REFUND" : "WAR_ESCROW_RELEASE");
        if (!movement.success()) {
            return failure(movement.code(), movement.message(), request.idempotencyKey(),
                    centralBank.getEconomyRevision());
        }
        escrowTag.putString("status", refund ? "REFUNDED" : "RELEASED");
        escrowTag.putLong("completedAt", System.currentTimeMillis());
        BankManager.markDirty();
        return success(refund ? "ESCROW_REFUNDED" : "ESCROW_RELEASED",
                refund ? "Escrow refunded" : "Escrow released", request.idempotencyKey(), centralBank,
                movement.transactionIds(), movement.accountIds(), readEscrow(centralBank, escrowTag));
    }

    private MovementResult moveMoney(CentralBank centralBank,
                                     String idempotencyKey,
                                     List<ApiEconomyTransferLeg> requestedLegs,
                                     String descriptionPrefix) {
        if (requestedLegs == null || requestedLegs.isEmpty() || requestedLegs.size() > 32) {
            return MovementResult.failure("INVALID_LEGS", "One to 32 transfer legs are required");
        }
        List<ApiEconomyTransferLeg> legs = new ArrayList<>();
        Map<UUID, BigDecimal> debits = new LinkedHashMap<>();
        Map<UUID, BigDecimal> credits = new LinkedHashMap<>();
        Map<UUID, BigDecimal> reserveDeltas = new LinkedHashMap<>();
        Map<UUID, AccountHolder> accounts = new LinkedHashMap<>();

        for (ApiEconomyTransferLeg raw : requestedLegs) {
            if (raw == null || raw.senderAccountId() == null || raw.receiverAccountId() == null
                    || raw.senderAccountId().equals(raw.receiverAccountId())) {
                return MovementResult.failure("INVALID_LEG", "Each transfer leg needs distinct accounts");
            }
            BigDecimal amount = money(raw.amount());
            if (amount.signum() <= 0 || amount.compareTo(globalSingleLimit()) > 0) {
                return MovementResult.failure("INVALID_AMOUNT", "Transfer amount is invalid or exceeds the server limit");
            }
            AccountHolder sender = account(centralBank, raw.senderAccountId());
            AccountHolder receiver = account(centralBank, raw.receiverAccountId());
            if (sender == null || receiver == null) {
                return MovementResult.failure("ACCOUNT_NOT_FOUND", "A transfer account was not found");
            }
            Bank senderBank = centralBank.getBank(sender.getBankId());
            Bank receiverBank = centralBank.getBank(receiver.getBankId());
            if (senderBank == null || receiverBank == null
                    || blocksTransactions(bankStatus(centralBank, senderBank))
                    || blocksTransactions(bankStatus(centralBank, receiverBank))) {
                return MovementResult.failure("BANK_UNAVAILABLE", "A transfer bank is unavailable");
            }
            BigDecimal bankSingleLimit = decimalMetadata(
                    centralBank.getOrCreateBankMetadata(senderBank.getBankId()), "limitSingle", globalSingleLimit());
            if (amount.compareTo(bankSingleLimit) > 0) {
                return MovementResult.failure("BANK_SINGLE_LIMIT", "Transfer exceeds the sender bank limit");
            }
            accounts.put(sender.getAccountUUID(), sender);
            accounts.put(receiver.getAccountUUID(), receiver);
            debits.merge(sender.getAccountUUID(), amount, BigDecimal::add);
            credits.merge(receiver.getAccountUUID(), amount, BigDecimal::add);
            if (!sender.getBankId().equals(receiver.getBankId())) {
                reserveDeltas.merge(sender.getBankId(), amount.negate(), BigDecimal::add);
                reserveDeltas.merge(receiver.getBankId(), amount, BigDecimal::add);
            }
            legs.add(new ApiEconomyTransferLeg(sender.getAccountUUID(), receiver.getAccountUUID(), amount,
                    normalizeReference(raw.reference())));
        }

        for (Map.Entry<UUID, BigDecimal> entry : debits.entrySet()) {
            AccountHolder sender = accounts.get(entry.getKey());
            if (!sender.canDebitForSystem(entry.getValue())) {
                return MovementResult.failure("INSUFFICIENT_OR_UNAVAILABLE",
                        "A sender has insufficient funds or an unavailable account");
            }
            Bank bank = centralBank.getBank(sender.getBankId());
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            BigDecimal dailyAccountLimit = decimalMetadata(metadata, "limitDailyPlayer",
                    BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get()));
            if (sender.getDailyOutgoingTransactionVolume().add(entry.getValue()).compareTo(dailyAccountLimit) > 0) {
                return MovementResult.failure("DAILY_ACCOUNT_LIMIT", "Sender daily transaction limit reached");
            }
        }
        for (Map.Entry<UUID, BigDecimal> entry : credits.entrySet()) {
            if (!accounts.get(entry.getKey()).canCreditForSystem(entry.getValue())) {
                return MovementResult.failure("RECEIVER_UNAVAILABLE", "A receiver account is unavailable");
            }
        }
        for (Map.Entry<UUID, BigDecimal> entry : reserveDeltas.entrySet()) {
            Bank bank = centralBank.getBank(entry.getKey());
            if (bank == null) {
                return MovementResult.failure("BANK_NOT_FOUND", "Settlement bank was not found");
            }
            BigDecimal after = bank.getDeclaredReserve().add(entry.getValue());
            if (entry.getValue().signum() < 0 && after.compareTo(bank.getMinimumRequiredReserve()) < 0) {
                return MovementResult.failure("RESERVE_REQUIREMENT", "Sender bank reserve requirement would be breached");
            }
        }

        debits.forEach((accountId, amount) -> accounts.get(accountId).forceRemoveBalance(amount));
        credits.forEach((accountId, amount) -> accounts.get(accountId).forceAddBalance(amount));
        reserveDeltas.forEach((bankId, delta) -> {
            Bank bank = centralBank.getBank(bankId);
            bank.setReserve(bank.getDeclaredReserve().add(delta));
        });

        List<UUID> transactionIds = new ArrayList<>();
        LinkedHashSet<UUID> affected = new LinkedHashSet<>();
        for (int index = 0; index < legs.size(); index++) {
            ApiEconomyTransferLeg leg = legs.get(index);
            UUID transactionId = deterministicId("economy-transaction", idempotencyKey + ":" + index);
            String description = descriptionPrefix + (leg.reference().isBlank() ? "" : ":" + leg.reference());
            UserTransaction transaction = new UserTransaction(
                    leg.senderAccountId(), leg.receiverAccountId(), leg.amount(), LocalDateTime.now(),
                    description, transactionId);
            accounts.get(leg.senderAccountId()).addTransaction(transaction);
            accounts.get(leg.receiverAccountId()).addTransaction(transaction);
            transactionIds.add(transactionId);
            affected.add(leg.senderAccountId());
            affected.add(leg.receiverAccountId());
        }
        BankManager.markDirty();
        return MovementResult.success(List.copyOf(transactionIds), List.copyOf(affected));
    }

    private String validateTransferPolicy(ApiEconomyOperationRequest request,
                                          AccountHolder sender,
                                          BigDecimal amount) {
        if (amount.signum() <= 0) {
            return "Amount must be greater than zero";
        }
        if (request.actorType() == ApiEconomyActorType.PLAYER && !sender.isInstitutional()) {
            if (amount.compareTo(PERSONAL_SINGLE_LIMIT) > 0) {
                return "Personal transfers may not exceed $10,000.00";
            }
            if (outgoingSince(sender, Instant.now().minusSeconds(24L * 60L * 60L))
                    .add(amount).compareTo(PERSONAL_ROLLING_LIMIT) > 0) {
                return "Personal rolling 24-hour transfer limit reached";
            }
        }
        if (request.actorType() == ApiEconomyActorType.PLAYER && sender.isInstitutional()) {
            ApiAccountRole actorRole = ApiAccountRole.parse(sender.getRole(request.actorPlayerId()));
            if (actorRole == ApiAccountRole.WITHDRAW) {
                return amount.compareTo(OFFICER_SINGLE_LIMIT) > 0
                        ? "Nation officer payouts may not exceed $10,000.00"
                        : "";
            }
            if (actorRole != ApiAccountRole.MANAGE) {
                return "Institutional transfers require WITHDRAW or MANAGE access";
            }
            if (amount.compareTo(LEADER_SINGLE_LIMIT) > 0) {
                return "Nation leader payouts may not exceed $25,000.00";
            }
            if (outgoingSince(sender, Instant.now().minusSeconds(24L * 60L * 60L))
                    .add(amount).compareTo(LEADER_ROLLING_LIMIT) > 0) {
                return "Nation rolling 24-hour payout limit reached";
            }
        }
        return "";
    }

    private BigDecimal outgoingSince(AccountHolder account, Instant cutoff) {
        BigDecimal total = BigDecimal.ZERO;
        for (UserTransaction transaction : account.getTransactions().values()) {
            if (transaction == null || !account.getAccountUUID().equals(transaction.getSenderUUID())
                    || transaction.getAmount() == null || transaction.getTimestamp() == null) {
                continue;
            }
            Instant occurredAt = transaction.getTimestamp().atZone(ZoneId.systemDefault()).toInstant();
            if (!occurredAt.isBefore(cutoff)) {
                total = total.add(transaction.getAmount());
            }
        }
        return total;
    }

    private Map<UUID, AccountHolder> selectAccounts(CentralBank centralBank,
                                                    ApiEconomySnapshotRequest request) {
        Map<UUID, AccountHolder> selected = new LinkedHashMap<>();
        for (Bank bank : uniqueBanks(centralBank)) {
            for (AccountHolder account : bank.getBankAccounts().values()) {
                if (account == null) {
                    continue;
                }
                if (request.includeAllAccounts()
                        || (request.viewerPlayerId() != null && account.canView(request.viewerPlayerId()))) {
                    selected.put(account.getAccountUUID(), account);
                }
            }
        }
        return selected;
    }

    private ApiAccessibleAccountSnapshot accountSnapshot(CentralBank centralBank,
                                                         AccountHolder account,
                                                         UUID viewerPlayerId,
                                                         boolean includeAllGrants) {
        Bank bank = centralBank.getBank(account.getBankId());
        ApiAccountRole viewerRole = ApiAccountRole.parse(account.getRole(viewerPlayerId));
        Map<UUID, ApiAccountRole> grants = new LinkedHashMap<>();
        if (includeAllGrants) {
            account.getAccessRoles().forEach((playerId, role) ->
                    grants.put(playerId, ApiAccountRole.parse(role)));
        } else if (viewerPlayerId != null && viewerRole != ApiAccountRole.NONE) {
            grants.put(viewerPlayerId, viewerRole);
        }
        return new ApiAccessibleAccountSnapshot(
                account.getAccountUUID(),
                account.getPrincipalType() == AccountPrincipalType.INSTITUTION
                        ? ApiAccountPrincipalType.INSTITUTION : ApiAccountPrincipalType.PLAYER,
                account.getPrincipalId(),
                account.isInstitutional() ? null : account.getPlayerUUID(),
                account.getBankId(),
                bank == null ? "" : bank.getBankName(),
                bank == null ? "MISSING" : bankStatus(centralBank, bank),
                account.getAccountType() == null ? "" : account.getAccountType().name(),
                account.getAccountType() == null ? "" : account.getAccountType().label,
                account.getBusinessLabel(),
                displayMoney(account.getBalance()),
                account.isPrimaryAccount(),
                account.isFrozen(),
                account.getFrozenReason(),
                grants,
                viewerRole,
                viewerRole.capabilities(),
                account.getDateOfCreation()
        );
    }

    private List<ApiEconomyTransactionSnapshot> transactionSnapshots(Iterable<AccountHolder> accounts,
                                                                      Instant since,
                                                                      int requestedLimit) {
        int limit = requestedLimit <= 0 ? 10_000 : Math.min(50_000, requestedLimit);
        Map<UUID, ApiEconomyTransactionSnapshot> unique = new HashMap<>();
        for (AccountHolder account : accounts) {
            for (UserTransaction transaction : account.getTransactions().values()) {
                if (transaction == null || transaction.getTransactionUUID() == null) {
                    continue;
                }
                Instant occurredAt = transaction.getTimestamp() == null
                        ? Instant.EPOCH
                        : transaction.getTimestamp().atZone(ZoneId.systemDefault()).toInstant();
                if (since != null && occurredAt.isBefore(since)) {
                    continue;
                }
                unique.putIfAbsent(transaction.getTransactionUUID(), new ApiEconomyTransactionSnapshot(
                        transaction.getTransactionUUID(), transaction.getSenderUUID(), transaction.getReceiverUUID(),
                        displayMoney(transaction.getAmount()), transaction.getTimestamp(),
                        transaction.getTransactionDescription()));
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(ApiEconomyTransactionSnapshot::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private ApiEconomyOperationResult success(String code,
                                              String message,
                                              String key,
                                              CentralBank centralBank,
                                              List<UUID> transactionIds,
                                              List<UUID> accountIds,
                                              ApiEscrowSnapshot escrow) {
        long operationRevision = centralBank.advanceEconomyRevision();
        return new ApiEconomyOperationResult(
                ApiEconomyOperationStatus.SUCCEEDED,
                code,
                message,
                key,
                deterministicId("economy-operation", key),
                false,
                operationRevision,
                Instant.now(),
                transactionIds,
                accountIds,
                escrow
        );
    }

    private ApiEconomyOperationResult failure(String code, String message, String key, long revision) {
        return new ApiEconomyOperationResult(
                ApiEconomyOperationStatus.FAILED,
                code,
                message,
                key,
                deterministicId("economy-operation", key == null ? "invalid" : key),
                false,
                revision,
                Instant.now(),
                List.of(),
                List.of(),
                null
        );
    }

    private void storeOperation(CentralBank centralBank,
                                ApiEconomyOperationRequest request,
                                String fingerprint,
                                ApiEconomyOperationResult result) {
        CompoundTag tag = writeOperationResult(result);
        tag.putString("requestFingerprint", fingerprint);
        tag.putString("operationType", request.type().name());
        centralBank.getEconomyOperations().put(request.idempotencyKey(), tag);
        pruneOperationHistory(centralBank);
        BankManager.markDirty();
    }

    private void pruneOperationHistory(CentralBank centralBank) {
        Map<String, CompoundTag> operations = centralBank.getEconomyOperations();
        int excess = operations.size() - MAX_OPERATION_HISTORY;
        if (excess <= 0) {
            return;
        }
        operations.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getLong("completedAt")))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(operations::remove);
    }

    private CompoundTag writeOperationResult(ApiEconomyOperationResult result) {
        CompoundTag tag = new CompoundTag();
        tag.putString("status", result.status().name());
        tag.putString("code", result.code());
        tag.putString("message", result.message());
        tag.putString("idempotencyKey", result.idempotencyKey());
        tag.putUUID("operationId", result.operationId());
        tag.putLong("revision", result.revision());
        tag.putLong("completedAt", result.completedAt().toEpochMilli());
        tag.put("transactionIds", uuidList(result.transactionIds()));
        tag.put("affectedAccountIds", uuidList(result.affectedAccountIds()));
        if (result.escrow() != null) {
            tag.put("escrow", writeEscrowSnapshot(result.escrow()));
        }
        return tag;
    }

    private ApiEconomyOperationResult readOperationResult(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("operationId")) {
            return null;
        }
        ApiEconomyOperationStatus status;
        try {
            status = ApiEconomyOperationStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException ignored) {
            status = ApiEconomyOperationStatus.FAILED;
        }
        return new ApiEconomyOperationResult(
                status,
                tag.getString("code"),
                tag.getString("message"),
                tag.getString("idempotencyKey"),
                tag.getUUID("operationId"),
                false,
                tag.getLong("revision"),
                Instant.ofEpochMilli(tag.getLong("completedAt")),
                readUuidList(tag.getList("transactionIds", Tag.TAG_COMPOUND)),
                readUuidList(tag.getList("affectedAccountIds", Tag.TAG_COMPOUND)),
                tag.contains("escrow", Tag.TAG_COMPOUND) ? readEscrowSnapshot(tag.getCompound("escrow")) : null
        );
    }

    private CompoundTag writeEscrowSnapshot(ApiEscrowSnapshot escrow) {
        CompoundTag tag = new CompoundTag();
        tag.putString("escrowId", escrow.escrowId());
        tag.putString("purpose", escrow.purpose());
        tag.putString("status", escrow.status());
        if (escrow.holdingAccountId() != null) tag.putUUID("holdingAccountId", escrow.holdingAccountId());
        tag.putString("balance", displayMoney(escrow.balance()).toPlainString());
        tag.put("contributors", uuidList(escrow.contributorAccountIds()));
        if (escrow.createdAt() != null) tag.putLong("createdAt", escrow.createdAt().toEpochMilli());
        if (escrow.completedAt() != null) tag.putLong("completedAt", escrow.completedAt().toEpochMilli());
        return tag;
    }

    private ApiEscrowSnapshot readEscrowSnapshot(CompoundTag tag) {
        return new ApiEscrowSnapshot(
                tag.getString("escrowId"),
                tag.getString("purpose"),
                tag.getString("status"),
                tag.hasUUID("holdingAccountId") ? tag.getUUID("holdingAccountId") : null,
                readMoney(tag.getString("balance")),
                readUuidList(tag.getList("contributors", Tag.TAG_COMPOUND)),
                tag.contains("createdAt") ? Instant.ofEpochMilli(tag.getLong("createdAt")) : null,
                tag.contains("completedAt") ? Instant.ofEpochMilli(tag.getLong("completedAt")) : null
        );
    }

    private ApiEscrowSnapshot readEscrow(CentralBank centralBank, CompoundTag tag) {
        if (tag == null || !tag.hasUUID("holdingAccountId")) {
            return null;
        }
        AccountHolder holding = account(centralBank, tag.getUUID("holdingAccountId"));
        List<UUID> contributors = new ArrayList<>();
        ListTag contributionTags = tag.getList("contributions", Tag.TAG_COMPOUND);
        for (int index = 0; index < contributionTags.size(); index++) {
            CompoundTag contribution = contributionTags.getCompound(index);
            if (contribution.hasUUID("accountId")) {
                contributors.add(contribution.getUUID("accountId"));
            }
        }
        return new ApiEscrowSnapshot(
                tag.getString("escrowId"),
                tag.getString("purpose"),
                tag.getString("status"),
                tag.getUUID("holdingAccountId"),
                holding == null ? BigDecimal.ZERO.setScale(2) : displayMoney(holding.getBalance()),
                contributors,
                tag.contains("createdAt") ? Instant.ofEpochMilli(tag.getLong("createdAt")) : null,
                tag.contains("completedAt") ? Instant.ofEpochMilli(tag.getLong("completedAt")) : null
        );
    }

    private List<ApiEconomyTransferLeg> refundLegs(CompoundTag escrowTag, UUID holdingId) {
        List<ApiEconomyTransferLeg> result = new ArrayList<>();
        ListTag contributions = escrowTag.getList("contributions", Tag.TAG_COMPOUND);
        for (int index = 0; index < contributions.size(); index++) {
            CompoundTag contribution = contributions.getCompound(index);
            if (!contribution.hasUUID("accountId")) {
                continue;
            }
            result.add(new ApiEconomyTransferLeg(
                    holdingId,
                    contribution.getUUID("accountId"),
                    readMoney(contribution.getString("amount")),
                    "matched stake refund"
            ));
        }
        return result;
    }

    private CompoundTag escrowTag(CentralBank centralBank, String rawEscrowId) {
        String escrowId = normalizeEscrowId(rawEscrowId);
        return escrowId.isEmpty() ? null : centralBank.getEconomyEscrows().get(escrowId);
    }

    private AccountHolder findInstitutionAccount(CentralBank centralBank, String institutionId) {
        for (Bank bank : uniqueBanks(centralBank)) {
            for (AccountHolder account : bank.getBankAccounts().values()) {
                if (account != null && account.isInstitutional()
                        && institutionId.equals(account.getPrincipalId())) {
                    return account;
                }
            }
        }
        return null;
    }

    private Optional<UUID> primaryAccountId(CentralBank centralBank, UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return centralBank.SearchForAccount(playerId).values().stream()
                .filter(account -> account != null && !account.isInstitutional() && account.isPrimaryAccount())
                .map(AccountHolder::getAccountUUID)
                .findFirst();
    }

    private boolean canWithdraw(ApiEconomyOperationRequest request, AccountHolder account) {
        return isSystem(request)
                || (request.actorPlayerId() != null && account.canWithdraw(request.actorPlayerId()));
    }

    private boolean isSystem(ApiEconomyOperationRequest request) {
        return request.actorType() == ApiEconomyActorType.OFFICIAL_SYSTEM;
    }

    private AccountHolder account(CentralBank centralBank, UUID accountId) {
        return centralBank == null || accountId == null ? null : centralBank.SearchForAccountByAccountId(accountId);
    }

    private CentralBank resolveCentralBank() {
        return centralBankSupplier.get();
    }

    private List<Bank> uniqueBanks(CentralBank centralBank) {
        Map<UUID, Bank> unique = new LinkedHashMap<>();
        unique.put(centralBank.getBankId(), centralBank);
        centralBank.getBanks().forEach((id, bank) -> {
            if (id != null && bank != null) unique.put(id, bank);
        });
        return List.copyOf(unique.values());
    }

    private String bankStatus(CentralBank centralBank, Bank bank) {
        if (bank == null) return "MISSING";
        if (centralBank.getBankId().equals(bank.getBankId())) return "ACTIVE";
        String status = centralBank.getOrCreateBankMetadata(bank.getBankId()).getString("status");
        return status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blocksTransactions(String status) {
        return Set.of("SUSPENDED", "REVOKED", "LOCKDOWN").contains(status);
    }

    private BigDecimal globalSingleLimit() {
        return BigDecimal.valueOf(Math.max(1, Config.GLOBAL_MAX_SINGLE_TRANSACTION.get())).setScale(2);
    }

    private BigDecimal decimalMetadata(CompoundTag tag, String key, BigDecimal fallback) {
        if (tag == null || !tag.contains(key)) return fallback;
        BigDecimal parsed = readMoney(tag.getString(key));
        return parsed.signum() > 0 ? parsed : fallback;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ignored) {
            return BigDecimal.ZERO.setScale(2);
        }
    }

    private BigDecimal displayMoney(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2)
                : value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private BigDecimal readMoney(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO.setScale(2);
        try {
            return displayMoney(new BigDecimal(value));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO.setScale(2);
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        return IDEMPOTENCY_KEY.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeReference(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= MAX_REFERENCE_LENGTH
                ? normalized : normalized.substring(0, MAX_REFERENCE_LENGTH);
    }

    private String normalizeInstitutionId(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9._:-]{1,160}") ? normalized : "";
    }

    private String normalizeEscrowId(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9._:-]{1,160}") ? normalized : "";
    }

    private String normalizeLabel(String value, String fallback) {
        String selected = value == null || value.isBlank() ? fallback : value;
        if (selected == null) return "";
        String normalized = selected.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private String fingerprint(ApiEconomyOperationRequest request) {
        StringBuilder canonical = new StringBuilder()
                .append(request.type()).append('\n')
                .append(request.actorType()).append('\n')
                .append(request.actorPlayerId()).append('\n')
                .append(request.accountId()).append('\n')
                .append(request.counterpartyAccountId()).append('\n')
                .append(request.bankId()).append('\n')
                .append(request.targetPlayerId()).append('\n')
                .append(request.institutionId()).append('\n')
                .append(request.escrowId()).append('\n')
                .append(request.role()).append('\n')
                .append(request.amount().toPlainString()).append('\n')
                .append(request.reference()).append('\n');
        request.metadata().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        for (ApiEconomyTransferLeg leg : request.legs()) {
            canonical.append(leg.senderAccountId()).append('>')
                    .append(leg.receiverAccountId()).append(':')
                    .append(leg.amount().toPlainString()).append(':')
                    .append(leg.reference()).append('\n');
        }
        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private UUID deterministicId(String namespace, String value) {
        return UUID.nameUUIDFromBytes(("ultimatebankingsystem:" + namespace + ":" + value)
                .getBytes(StandardCharsets.UTF_8));
    }

    private ListTag uuidList(List<UUID> ids) {
        ListTag list = new ListTag();
        if (ids == null) return list;
        for (UUID id : ids) {
            if (id == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            list.add(entry);
        }
        return list;
    }

    private List<UUID> readUuidList(ListTag list) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("id")) ids.add(entry.getUUID("id"));
        }
        return List.copyOf(ids);
    }

    private record MovementResult(boolean success,
                                  String code,
                                  String message,
                                  List<UUID> transactionIds,
                                  List<UUID> accountIds) {
        private static MovementResult success(List<UUID> transactionIds, List<UUID> accountIds) {
            return new MovementResult(true, "OK", "", transactionIds, accountIds);
        }

        private static MovementResult failure(String code, String message) {
            return new MovementResult(false, code, message, List.of(), List.of());
        }
    }
}
